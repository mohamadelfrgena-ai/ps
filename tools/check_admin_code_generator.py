#!/usr/bin/env python3
"""Regression guard for admin batch TXT exports and creator campaign codes."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android/app/src/main"
SQL = ROOT / "supabase/51_admin_creator_codes.sql"
ALL_IN_ONE = ROOT / "supabase/00_ALL_IN_ONE.sql"
VERIFY = ROOT / "supabase/99_VERIFY_ALL.sql"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


screen = read(ANDROID / "java/com/addonstation/mobile/ui/admin/AdminScreens.kt")
repo = read(ANDROID / "java/com/addonstation/mobile/data/repo/AdminRepository.kt")
wallet = read(ANDROID / "java/com/addonstation/mobile/ui/account/WalletScreens.kt")
sql = read(SQL)
all_in_one = read(ALL_IN_ONE)
verify = read(VERIFY)
base_sql = read(ROOT / "supabase/14_redeem_codes.sql")

checks: list[tuple[str, bool]] = [
    ("batch generation keeps one total redemption per generated code",
     ".acCreateCodes(codeAmount, codeCount.coerceAtLeast(1), 1, null)" in screen),
    ("batch output can be saved through Android's TXT document picker",
     'ActivityResultContracts.CreateDocument("text/plain")' in screen
     and 'addonstation-ac-${codeAmount}-${generated.size}.txt' in screen
     and 'generated.joinToString(separator = "\\n", postfix = "\\n")' in screen),
    ("creator form captures channel, amount, result field, and copy action",
     all(token in screen for token in (
         "creatorChannel", "creatorAmountText", "creatorCode",
         "admin_creator_code_result", "android.content.ClipData.newPlainText",
     ))),
    ("creator generation is delegated to an admin-only server RPC",
     "acCreateCreatorCode" in repo
     and '"ac_admin_create_creator_code"' in repo
     and "if not public.is_admin()" in sql),
    ("creator codes remain compatible with the eight-character wallet input",
     "v_code text := 'CR'" in sql
     and "for i in 1..6 loop" in sql
     and "codeInput.length != 8" in wallet),
    ("creator codes are visibly distinguished and tagged with their channel",
     "code_kind" in sql and "channel_name" in sql
     and "'creator:' || v_channel" in sql),
    ("every member may redeem a creator code once",
     "2147483647" in sql
     and "ac_redeem_uses_once_uniq" in base_sql
     and "unique index" in base_sql.lower()),
    ("creator RPC is granted only to signed-in users after explicit revocation",
     "revoke all on function public.ac_admin_create_creator_code(text, int) from public, anon" in sql
     and "grant execute on function public.ac_admin_create_creator_code(text, int) to authenticated" in sql),
    ("migration is idempotent and included in the all-in-one installer",
     sql.count("if not exists") >= 4
     and "51_admin_creator_codes.sql" in all_in_one),
    ("database verifier covers the new functions and columns",
     "ac_admin_create_creator_code" in verify
     and "ac_random_creator_code" in verify
     and "channel_name" in verify),
    ("new SQL migration is English-only", not re.search(r"[\u0600-\u06ff]", sql)),
]

keys = {
    "admin_codes_save_txt", "admin_codes_file_saved", "admin_codes_file_failed",
    "admin_codes_bad_count", "admin_creator_code_title", "admin_creator_code_hint",
    "admin_creator_channel", "admin_creator_code_generate", "admin_creator_code_result",
    "admin_creator_code_copy", "admin_creator_code_copied",
    "admin_creator_code_bad_channel", "admin_creator_code_bad_amount",
}
locale_sets = []
for folder in ("values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"):
    path = ANDROID / "res" / folder / "strings_admin_codes_v2.xml"
    if not path.exists():
        locale_sets.append(set())
        continue
    root = ET.parse(path).getroot()
    locale_sets.append({node.attrib["name"] for node in root.findall("string")})
checks.append(("admin code controls are translated in all eight locales",
               all(found == keys for found in locale_sets)))

print("Admin AC code generator guard")
print("-" * 68)
for label, ok in checks:
    print(f"{'PASS' if ok else 'FAIL':4}  {label}")

failed = [label for label, ok in checks if not ok]
if failed:
    print()
    for label in failed:
        print("ERROR:", label)
    sys.exit(1)
print(f"\nPASS: {len(checks)}/{len(checks)} admin code invariants")
