#!/usr/bin/env python3
"""Regression guard for the shared PS4/phone link-code contract."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/data/repo/ConsoleLinkRepository.kt"
SCREEN_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/settings/ConsoleLinkScreens.kt"
ERROR_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/data/ApiError.kt"
PLANS_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/data/repo/PlansRepository.kt"
SQL_PATH = ROOT / "supabase/41_console_link_codes.sql"
CLAIM_FIX_PATH = ROOT / "supabase/APPLY_CONSOLE_LINK_CLAIM_SCHEMA_FIX.sql"
CONFIG_PATH = ROOT / "supabase/config.toml"
GRADLE_PROPERTIES = ROOT / "android/gradle.properties"

repo = REPO_PATH.read_text(encoding="utf-8")
screen = SCREEN_PATH.read_text(encoding="utf-8")
errors = ERROR_PATH.read_text(encoding="utf-8")
plans = PLANS_PATH.read_text(encoding="utf-8")
sql = SQL_PATH.read_text(encoding="utf-8")
claim_fix = CLAIM_FIX_PATH.read_text(encoding="utf-8")
config = CONFIG_PATH.read_text(encoding="utf-8")
gradle = GRADLE_PROPERTIES.read_text(encoding="utf-8")
workflow_text = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / ".github/workflows").glob("*.yml"))
)

alphabet_match = re.search(r'CODE_ALPHABET\s*=\s*"([A-Z0-9]+)"', repo)
length_match = re.search(r"CODE_LENGTH\s*=\s*(\d+)", repo)
alphabet = alphabet_match.group(1) if alphabet_match else ""
code_length = int(length_match.group(1)) if length_match else 0
separators = {"-", "‐", "‑", "‒", "–", "—"}


def normalize(value: str) -> str:
    return "".join(
        character
        for character in value.upper()
        if not character.isspace() and character not in separators
    )


normalization_cases = {
    "abc234": "ABC234",
    "AbC-234": "ABC234",
    " a b c 2 3 4 ": "ABC234",
    "abc–234": "ABC234",
    "abc—234": "ABC234",
}

forbidden_schema_statements = (
    "create table", "alter table", "drop table", "create or replace function",
    "insert into", "update public", "delete from",
)

checks = [
    ("production project URL is pinned", "ADDONSTATION_SUPABASE_URL=https://stbmescmexzcszyyambq.supabase.co" in gradle),
    ("CI always targets the transferred production project", "jqjmkssgjdsfdojkuqzq" not in workflow_text and workflow_text.count("https://stbmescmexzcszyyambq.supabase.co") >= 10),
    ("stale Actions secrets cannot override the reviewed Android public key", 'echo "ADDONSTATION_SUPABASE_ANON_KEY=' not in workflow_text),
    ("Android configuration contains only a publishable key", "ADDONSTATION_SUPABASE_ANON_KEY=sb_publishable_" in gradle and "service_role" not in gradle.lower() and "sb_secret_" not in gradle.lower()),
    ("six-character unambiguous alphabet is present", code_length == 6 and alphabet == "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"),
    ("Kotlin normalization is locale-independent uppercase", ".uppercase(Locale.ROOT)" in repo),
    ("Kotlin normalization accepts displayed separators", "CODE_SEPARATORS" in repo and all(mark in repo for mark in separators)),
    ("lowercase, mixed-case, spaced, and formatted examples normalize identically", all(normalize(raw) == expected for raw, expected in normalization_cases.items())),
    ("all normalized examples pass the production alphabet contract", all(len(normalize(raw)) == code_length and set(normalize(raw)) <= set(alphabet) for raw in normalization_cases)),
    ("typing and submission both use the same normalizer", "code = ConsoleLinkRepository.normalizeCode(input)" in screen and "val normalized = ConsoleLinkRepository.normalizeCode(code)" in screen),
    ("local confirmation happens before the server claim", 0 <= screen.find("prepareCode(normalized)") < screen.find("pendingApproval = prepared") < screen.find("claimCode(approvedCode)")),
    ("phone calls only the shared claim RPC", 'function = "claim_console_link"' in repo and re.search(r'"console_code_[a-z_]+"', repo + screen) is None),
    ("claim sends the exact required parameter names", all(name in repo for name in ('"p_code"', '"p_access_token"', '"p_refresh_token"'))),
    ("claim uses one consistent refreshed session", "supabase.refreshSession()" in repo and "rpcWithToken(" in repo and "accessToken = session.accessToken" in repo),
    ("unexpected RPC payloads have a console-specific error", repo.count("MESSAGE_SERVICE_UNAVAILABLE") >= 2),
    ("code flow never falls back to the generic something-went-wrong state", "ConsoleCodeError.GENERIC" not in screen and "phone_console_service_error" in screen),
    ("read-only SQL verifier names the exact shared RPCs", all(signature in sql for signature in (
        "create_console_link_code(text,text)", "claim_console_link(text,text,text)", "poll_console_link(text,uuid)",
    ))),
    ("SQL verifier never mutates the shared PS4/phone schema", not any(statement in sql.lower() for statement in forbidden_schema_statements)),
    ("read-only verifier requires the claim columns used by production", all(fragment in sql for fragment in ("column_name = 'claimed_by'", "column_name = 'profile'", "data_type = 'uuid'", "data_type = 'jsonb'"))),
    ("contract rejects consumed_at and incompatible console_code RPCs", "consumed_at" in sql and "console_code_%" in sql),
    ("focused repair targets only the reviewed project", "stbmescmexzcszyyambq" in claim_fix and "jqjmkssgjdsfdojkuqzq" not in claim_fix),
    ("focused repair adds exactly the missing claim payload columns", "add column if not exists claimed_by uuid" in claim_fix.lower() and "add column if not exists profile jsonb" in claim_fix.lower()),
    ("focused repair never adds consumed_at or replaces shared RPCs", "add column if not exists consumed_at" not in claim_fix.lower() and "create or replace function" not in claim_fix.lower()),
    ("obsolete console-session Edge Function is gone", "[functions.console-session]" not in config and not (ROOT / "supabase/functions/console-session").exists()),
    ("gateway_not_configured is classified without exposing api_key", "gateway_not_configured" in errors and '"api_key"' not in plans),
]

resource_sets = ["values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"]
for folder in resource_sets:
    path = ROOT / "android/app/src/main/res" / folder / "strings_phone_design.xml"
    names = {node.attrib.get("name") for node in ET.parse(path).getroot().findall("string")}
    checks.append((f"{folder} has the console service error text", "phone_console_service_error" in names))

failed = False
for label, passed in checks:
    print(("PASS" if passed else "FAIL") + f": {label}")
    failed |= not passed

if failed:
    sys.exit(1)
print("\nShared PlayStation link-code regression guard passed.")
