#!/usr/bin/env python3
"""Guard the device-wide Android smoothness pass.

This intentionally checks architecture-level invariants rather than animation
speeds: lazy store composition, lifecycle-aware state collection, bounded image
memory/concurrency, startup precompilation, debounced searches, and background
chat suspension.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/com/addonstation/mobile"


def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


ui_files = list((SRC / "ui").rglob("*.kt"))
ui = "\n".join(read(path) for path in ui_files)
store = read(SRC / "ui/store/StoreScreens.kt")
store_screen = store.partition("fun StoreScreen(")[2].partition("private fun StoreHeader(")[0]
app = read(SRC / "AddonStationApp.kt")
client = read(SRC / "data/SupabaseClient.kt")
container = read(SRC / "data/AppContainer.kt")
chat = read(SRC / "ui/chat/ChatScreen.kt")
content = read(SRC / "data/repo/ContentRepository.kt")
models = read(SRC / "data/model/Models.kt")
device = read(SRC / "util/DevicePerformance.kt")
gradle = read(ROOT / "android/app/build.gradle.kts")
baseline = read(ROOT / "android/app/src/main/baseline-prof.txt")
workflow = read(ROOT / ".github/workflows/build-apk.yml")

lifecycle_collectors = ui.count("collectAsStateWithLifecycle")
old_collectors = re.findall(r"(?<!WithLifecycle)\.collectAsState\s*\(", ui)

checks: list[tuple[str, bool]] = [
    ("All UI flows are lifecycle-aware", not old_collectors and lifecycle_collectors >= 70),
    ("Store owns one lazy vertical scroller", store_screen.count("LazyVerticalGrid(") == 1 and "verticalScroll(" not in store_screen),
    ("Store cards are keyed and typed", "key = { it.id }" in store_screen and 'contentType = { "store-card" }' in store_screen),
    ("Search requests are debounced", store_screen.count("320L") >= 1 and "debouncedQuery" in store_screen),
    ("Recent store queries use a bounded TTL cache", "browseCaches" in content and "size > 8" in content and "45_000L" in content),
    ("Low-RAM devices have a distinct render policy", "isLowRamDevice" in device and "allowDecorativeMotion = false" in device),
    ("Image cache and decode concurrency are device-adaptive", "profile.imageMemoryCachePercent" in app and "profile.imageMaxRequestsPerHost" in app),
    ("API startup requests are not queued at OkHttp default five", "maxRequestsPerHost" in client and "Dispatcher().apply" in client),
    ("Notification channel setup is off the first-frame path", "startupScope.launch" in app and "ensureChannels" in app),
    ("Chat pauses work when the app stops", "Lifecycle.Event.ON_STOP -> vm.setScreenActive(false)" in chat),
    ("Hot immutable models are skippable by Compose", models.count("@Immutable") >= 20 and "@Stable\ndata class ChatMessageUi" in models),
    ("Baseline profile is bundled and installed", "androidx.profileinstaller:profileinstaller" in gradle and baseline.count("HSPL") >= 20),
    ("APK workflow runs this guard", "python3 tools/check_android_smoothness.py" in workflow),
    ("Container passes the device API policy", "performanceProfile.apiMaxRequestsPerHost" in container),
]

print("Android smoothness guard")
print("-" * 64)
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL':4}  {name}")

failed = [name for name, ok in checks if not ok]
print()
print(f"Lifecycle-aware collectors: {lifecycle_collectors}")
if failed:
    for name in failed:
        print("ERROR:", name)
    sys.exit(1)
print(f"PASS: {len(checks)}/{len(checks)} smoothness invariants")
