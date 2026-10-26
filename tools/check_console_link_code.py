#!/usr/bin/env python3
"""Regression guard for PlayStation link-code input and schema upgrades."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/data/repo/ConsoleLinkRepository.kt"
SCREEN_PATH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/settings/ConsoleLinkScreens.kt"
SQL_PATH = ROOT / "supabase/41_console_link_codes.sql"
REPAIR_PATH = ROOT / "supabase/49_fix_console_link_code_upgrade.sql"

repo = REPO_PATH.read_text(encoding="utf-8")
screen = SCREEN_PATH.read_text(encoding="utf-8")
sql = SQL_PATH.read_text(encoding="utf-8")
repair = REPAIR_PATH.read_text(encoding="utf-8")

alphabet_match = re.search(r'CODE_ALPHABET\s*=\s*"([A-Z0-9]+)"', repo)
length_match = re.search(r"CODE_LENGTH\s*=\s*(\d+)", repo)
alphabet = alphabet_match.group(1) if alphabet_match else ""
code_length = int(length_match.group(1)) if length_match else 0
separators = {"-", "‐", "‑", "‒", "–", "—"}


def normalize(value: str) -> str:
    """Python model of the deliberately simple Kotlin normalization contract."""
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

checks = [
    ("six-character unambiguous alphabet is present", code_length == 6 and alphabet == "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"),
    ("Kotlin normalization is locale-independent uppercase", ".uppercase(Locale.ROOT)" in repo),
    ("Kotlin normalization accepts displayed separators", "CODE_SEPARATORS" in repo and all(mark in repo for mark in separators)),
    ("lowercase, mixed-case, spaced, and formatted examples normalize identically", all(normalize(raw) == expected for raw, expected in normalization_cases.items())),
    ("all normalized examples pass the production alphabet contract", all(len(normalize(raw)) == code_length and set(normalize(raw)) <= set(alphabet) for raw in normalization_cases)),
    ("typing and submission both use the same normalizer", "code = ConsoleLinkRepository.normalizeCode(input)" in screen and "val normalized = ConsoleLinkRepository.normalizeCode(code)" in screen),
    ("code flow never falls back to the generic something-went-wrong state", "ConsoleCodeError.GENERIC" not in screen and "phone_console_service_error" in screen),
    ("unexpected RPC payloads have a console-specific error", repo.count("MESSAGE_SERVICE_UNAVAILABLE") >= 3),
    ("base schema upgrade adds app_version", "add column if not exists app_version text" in sql),
    ("focused production repair adds app_version", "add column if not exists app_version text" in repair),
    ("server normalization remains case-insensitive", "regexp_replace(upper(coalesce(p_code, ''))" in sql),
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
print("\nPlayStation link-code regression guard passed.")
