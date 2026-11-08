#!/usr/bin/env python3
"""Static contract guard for the PS4 console-link session handoff.

This intentionally checks source invariants that the host unit test cannot reach:
the exact production RPC names, the no-console_code_* rule, account hydration,
and the rule that UI success comes only after durable session persistence.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "src/main.cpp").read_text(encoding="utf-8")
CLIENT = (ROOT / "src/supabase_client.cpp").read_text(encoding="utf-8")
HEADER = (ROOT / "src/supabase_client.hpp").read_text(encoding="utf-8")
TEST = (ROOT / "tests/test_installer.cpp").read_text(encoding="utf-8")


def function_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        return ""
    brace = source.find("{", start)
    if brace < 0:
        return ""
    depth = 0
    in_string = False
    escaped = False
    for index in range(brace, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    return ""


poll = function_body(CLIENT, "RpcResult callPollLinkCode")
create = function_body(CLIENT, "RpcResult callCreateLinkCode")
parse = function_body(CLIENT, "LinkSession parseLinkSessionResponse")
save = function_body(CLIENT, "bool saveLinkSession")
finish = function_body(MAIN, "static bool finishSignIn")
apply_account = function_body(MAIN, "static bool applyAccountFromSession")

checks = [
    ("PS4 creates codes with the shared RPC", '"create_console_link_code"' in create),
    ("PS4 polls with the shared wrapper RPC", '"poll_console_link"' in poll),
    ("PS4 never calls a console_code_* RPC", not re.search(r'"console_code_[^"]*"', CLIENT + MAIN)),
    ("current nested profile payload is parsed", 'jsonGetRaw(body, "profile")' in parse and 'jsonGetString(profile, "id")' in parse),
    ("both access and refresh tokens are mandatory", all(value in parse for value in ("s.accessToken", "s.refreshToken", "linkSessionHasCredentials"))),
    ("profile reads use the supported privacy view", "/rest/v1/visible_profiles" in CLIENT and "/rest/v1/profiles?" not in CLIENT),
    ("wallet hydration distinguishes real zero from failure", "walletOk" in CLIENT and "balanceKnown" in apply_account),
    ("session persistence is atomic", 'path + ".tmp"' in save and "std::rename" in save),
    ("half sessions cannot be persisted", "!s.profileKnown" in save and "!s.balanceKnown" in save),
    ("fresh sign-in requires hydration and persistence", "applyAccountFromSession(needsLiveHydration, true)" in finish and "loadWalletHistory" in finish),
    ("linked UI is entered only when finishSignIn succeeds", re.search(r"if \(finishSignIn\(\)\).*?A\.codeStatus = 1", MAIN, re.S) is not None),
    ("avatar paths resolve through public Storage", "publicStorageUrl" in apply_account and "account_avatar_" in apply_account),
    ("host tests cover the production nested payload", "Production returns credentials at the root" in TEST and "assert(current.valid)" in TEST),
    ("public API documents only the shared three-RPC contract", "poll_console_link(p_code" in HEADER and "claim_console_link(p_code" in HEADER),
]

failed = [label for label, ok in checks if not ok]
for label, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + label)
if failed:
    print(f"Console session guard failed: {len(failed)} invariant(s)", file=sys.stderr)
    raise SystemExit(1)
print(f"Console session guard passed: {len(checks)}/{len(checks)} invariants")
