#!/usr/bin/env python3
"""Static guard for the phone item/store and PS4-link design integration."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android/app/src/main"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


store = read("android/app/src/main/java/com/addonstation/mobile/ui/store/StorePhoneDesign.kt")
store_entry = read("android/app/src/main/java/com/addonstation/mobile/ui/store/StoreScreens.kt")
console = read("android/app/src/main/java/com/addonstation/mobile/ui/settings/ConsoleLinkScreens.kt")
settings = read("android/app/src/main/java/com/addonstation/mobile/ui/settings/SettingsScreen.kt")
settings_section = read("android/app/src/main/java/com/addonstation/mobile/ui/settings/ConsoleSettingsSection.kt")
repo = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ConsoleLinkRepository.kt")
content_repo = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ContentRepository.kt")
manifest = read("android/app/src/main/AndroidManifest.xml")
main_tab = read("android/app/src/main/java/com/addonstation/mobile/ui/nav/MainTab.kt")
console_sql = read("supabase/41_console_link_codes.sql")
supabase_config = read("supabase/config.toml")

checks = [
    ("redesigned two-column store cards are wired", "DesignedStoreContentCard(" in store_entry),
    ("262dp item cover and sticky action bar exist", "height(262.dp)" in store and "DetailActionBar(" in store),
    ("all ten item action modes are represented", all(name in store for name in (
        "FREE", "PAID_AC", "NEEDS_EPIC", "NEEDS_LEGENDARY", "SIGNED_OUT",
        "DOWNLOADING", "DOWNLOADED", "LIMIT", "FAILED", "NO_FILE",
    ))),
    ("supplied vector stat icons replace emoji", all(name in store for name in (
        "ic_phone_stat_downloads", "ic_phone_stat_views", "ic_phone_stat_rating", "ic_phone_stat_size",
    )) and not any(glyph in store for glyph in ("📥", "👁"))),
    ("Devices section appears before Account", settings.find("ConsoleSettingsSection(") < settings.find("// 1 · ACCOUNT")),
    ("Devices link entry remains directly actionable", ".clickable(onClick = onOpenLink)" in settings_section),
    ("existing Settings navigation icon is preserved", "Icons.Filled.Settings, Icons.Outlined.Settings" in main_tab),
    ("local confirmation precedes the one-time server claim", 0 <= console.find("prepareCode(normalized)") < console.find("pendingApproval = prepared") < console.find("claimCode(approvedCode)")),
    ("console code stays six-character LTR", "CODE_LENGTH" in console and "LayoutDirection.Ltr" in console),
    ("Android never reads console_link_codes directly", "console_link_codes" not in console + repo),
    ("phone calls the exact shared claim RPC", 'function = "claim_console_link"' in repo),
    ("claim sends exactly the required user-session fields", all(name in repo for name in (
        '"p_code"', '"p_access_token"', '"p_refresh_token"',
    )) and "rpcWithToken" in repo),
    ("forbidden console_code RPC names are absent from Android", re.search(r'"console_code_[a-z_]+"', console + repo) is None),
    ("console claim remains session-gated and refresh-aware", "supabase.session()" in repo and "supabase.refreshSession()" in repo),
    ("linked consoles use list and revocation RPCs", all(name in repo for name in (
        '"console_list"', '"console_unlink"', '"console_unlink_all"',
    ))),
    ("server contract verifier has the exact three shared RPCs", all(name in console_sql for name in (
        "create_console_link_code(text,text)", "claim_console_link(text,text,text)",
        "poll_console_link(text,uuid)",
    ))),
    ("server contract rejects consumed_at and incompatible RPCs", "consumed_at" in console_sql and "console_code_%" in console_sql),
    ("obsolete console-session Edge Function is not configured", "[functions.console-session]" not in supabase_config),
    ("new console SQL is English-only", not re.search(r"[\u0600-\u06ff]", console_sql)),
    ("download failures retain server error kinds", "apiErrorFromMessage(outcome.message)" in content_repo),
    ("downloaded files use a read-only FileProvider", "androidx.core.content.FileProvider" in manifest and "android:grantUriPermissions=\"true\"" in manifest),
]

vectors = {
    "ic_phone_stat_downloads.xml", "ic_phone_stat_views.xml", "ic_phone_stat_rating.xml",
    "ic_phone_stat_favorites.xml", "ic_phone_stat_size.xml", "ic_phone_stat_version.xml",
    "ic_phone_stat_added.xml", "ic_phone_stat_folder.xml", "ic_phone_pad.xml",
    "ic_phone_phone.xml", "ic_phone_tv.xml", "ic_phone_link.xml",
}
vector_dir = ANDROID / "res/drawable"
checks.append(("all supplied design vectors are present", all((vector_dir / name).exists() for name in vectors)))

resource_sets = ["values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"]
key_sets = []
for folder in resource_sets:
    path = ANDROID / "res" / folder / "strings_phone_design.xml"
    root = ET.parse(path).getroot()
    key_sets.append({node.attrib["name"] for node in root.findall("string")})
checks.append(("phone-design strings cover all eight languages", len(key_sets[0]) >= 74 and all(keys == key_sets[0] for keys in key_sets[1:])))

failed = False
for label, ok in checks:
    print(("PASS" if ok else "FAIL") + f": {label}")
    failed |= not ok

if failed:
    sys.exit(1)
print("\nPhone item/store and PS4-link design guards passed.")
