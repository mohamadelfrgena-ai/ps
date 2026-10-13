#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static checks for splash readiness and its progress indicator (not a runtime test)."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP_NAV = (ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/nav/AppNav.kt").read_text(encoding="utf-8")
SPLASH = (ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/splash/SplashScreen.kt").read_text(encoding="utf-8")

checks = {
    "bootstrap progress and ready state are passed to SplashScreen": all(
        token in APP_NAV
        for token in (
            "var startupProgress by remember { mutableStateOf(0f) }",
            "var startupReady by remember { mutableStateOf(false) }",
            "loadingProgress = startupProgress",
            "startupReady = startupReady",
        )
    ),
    "the first Compose frames hand off to splash before the nav graph is composed": all(
        token in APP_NAV
        for token in (
            "var navigationReady by remember { mutableStateOf(false) }",
            "withFrameNanos { }",
            "if (configured && navigationReady)",
        )
    ) and APP_NAV.count("withFrameNanos { }") >= 2,
    "bootstrap has a bounded wait and reports completion after its work": all(
        token in APP_NAV
        for token in (
            "withTimeoutOrNull(10_000L)",
            "startupProgress = 1f",
            "startupReady = true",
        )
    ),
    "splash completion waits for readiness unless the user skips": all(
        token in SPLASH
        for token in (
            "LaunchedEffect(introComplete, skipRequested, startupReady)",
            "(!startupReady && !skipRequested)",
        )
    ),
    "loading bar uses staged progress and the intro timeline": all(
        token in SPLASH
        for token in (
            "ProgressBarRangeInfo(semanticProgress, 0f..1f)",
            "ramp(p, 0.6143f, 0.8714f)",
            "maxOf(safeProgress, timeMinimum).coerceAtMost(0.80f)",
        )
    ),
    "the pending portion pulses and fills when startup is ready": all(
        token in SPLASH
        for token in (
            "while (true)",
            "waitingPulse.animateTo(",
            "startupReady || skipRequested -> 1f",
        )
    ),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'✅' if passed else '❌'} {name}")
if failed:
    print(f"\n❌ {len(failed)} splash checks need attention")
    sys.exit(1)
print("\n🟢 Splash progress/readiness static checks passed (runtime behavior not verified).")
