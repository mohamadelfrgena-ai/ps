#!/usr/bin/env python3
"""Static guard for the centred, determinate, low-overhead app splash."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPLASH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/splash/SplashScreen.kt"
APP_NAV = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/nav/AppNav.kt"
DRAWABLE = ROOT / "android/app/src/main/res/drawable-nodpi/splash_cave_chase.jpg"
RES = ROOT / "android/app/src/main/res"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"splash guard: {message}")


def block_between(source: str, start: str, end: str) -> str:
    require(start in source and end in source, f"missing block markers: {start!r}, {end!r}")
    return source.split(start, 1)[1].split(end, 1)[0]


def main() -> None:
    splash = SPLASH.read_text(encoding="utf-8")
    nav = APP_NAV.read_text(encoding="utf-8")

    require(DRAWABLE.is_file(), "the approved splash artwork is missing")
    require(DRAWABLE.stat().st_size <= 512_000, "splash artwork unexpectedly exceeds 512 KiB")
    require("painterResource(R.drawable.splash_cave_chase)" in splash,
            "splash no longer renders the approved artwork")

    # Splash cannot be bypassed: neither a tap handler/state nor a translated
    # prompt may quietly reintroduce the removed Skip option.
    for token in ("skipRequested", "skipEnabled", "splash_skip", ".clickable(", "detectTapGestures"):
        require(token not in splash, f"removed splash bypass returned: {token}")
    for strings_file in RES.glob("values*/strings.xml"):
        require('name="splash_skip"' not in strings_file.read_text(encoding="utf-8"),
                f"removed skip prompt returned in {strings_file.relative_to(ROOT)}")

    # The three permanent lines must share one centred group, not three absolute
    # positions that can drift or clip independently.
    require("SplashSlogans(" in splash and ".align(Alignment.Center)" in splash,
            "the three slogan lines are not grouped at screen centre")
    slogan_block = block_between(splash, "private fun SplashSlogans(", "private fun SplashSloganLine(")
    require(slogan_block.count("SplashSloganLine(") == 3,
            "the centred slogan group must contain exactly three lines")
    require("maxLines = 1" in splash and "lineHeightDp + 8.dp" in splash,
            "single-line slogan clipping protection is missing")

    # A single bounded entrance is allowed. Perpetual animation and the old
    # disappear/reappear loop caused visible stutter and missing words.
    forbidden_animation_tokens = (
        "rememberInfiniteTransition",
        "infiniteRepeatable",
        "RepeatMode.Restart",
        "while (isActive)",
        "introCycle",
        "cycleProgress",
        "sparkleSpecs",
    )
    for token in forbidden_animation_tokens:
        require(token not in splash, f"perpetual/heavy animation token returned: {token}")
    require("entrance.animateTo(" in splash and "targetValue = 1f" in splash,
            "bounded one-shot entrance animation is missing")

    # Progress must originate only from AppRoot bootstrap milestones. A short
    # interpolation is permitted, but no elapsed-time minimum/fake target is.
    require("loadingProgress: Float" in splash, "SplashScreen has no real progress input")
    require(
        "val targetProgress = if (startupReady) 1f else loadingProgress.coerceIn(0f, 1f)" in splash,
        "loading bar target is not derived directly from bootstrap progress",
    )
    require("ProgressBarRangeInfo(progress, 0f..1f)" in splash,
            "determinate progress accessibility metadata is missing")
    for token in ("timeMinimum", "elapsed /", "fakeProgress", "animatedProgress.value.coerceAtLeast"):
        require(token not in splash, f"fake time-driven splash progress returned: {token}")

    # The expensive graph is composed only after the entrance has settled and
    # must render under the splash before it is allowed to leave.
    require("delay(850L)" in nav and "navigationReady = true" in nav,
            "navigation graph is no longer delayed past the entrance")
    underlay_block = block_between(nav, "navigationReady = true", "com.addonstation.mobile.ui.theme.AddonStationTheme")
    require(underlay_block.count("withFrameNanos { }") >= 2,
            "destination underlay is not given two rendered frames")
    require("underlayReady = true" in underlay_block,
            "destination readiness state is never completed")
    require("if (configured && navigationReady)" in nav,
            "main navigation is not precomposed behind the splash")
    require("startupReady = startupReady && (!configured || underlayReady)" in nav,
            "splash exit is not gated on the rendered destination")

    milestones = [float(value) for value in re.findall(r"startupProgress\s*=\s*([0-9.]+)f", nav)]
    require(milestones, "AppRoot no longer publishes bootstrap milestones")
    require(min(milestones) >= 0.0 and max(milestones) == 1.0,
            "bootstrap milestones must stay in 0..1 and finish at 1")
    require(any(0.0 < value < 1.0 for value in milestones),
            "bootstrap progress has no intermediate real stage")

    print("splash progress guard: OK")


if __name__ == "__main__":
    main()
