#!/usr/bin/env python3
"""Read-only diagnosis for the authenticated console-link claim path.

The public contract check can prove that PlayStation code creation and polling
work, but it cannot execute the authenticated phone claim. This tool uses the
Supabase Management API only to inspect function signatures, security modes,
and grants in the one reviewed production project. It never reads rows, user
sessions, link codes, poll tokens, or credentials from the database.
"""

from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from typing import Any

EXPECTED_PROJECT_REF = "stbmescmexzcszyyambq"

QUERY = r"""
with functions as (
  select
    p.oid,
    p.proname,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    pg_get_function_result(p.oid) as result_type,
    p.prosecdef as security_definer,
    pg_get_userbyid(p.proowner) as owner_name,
    coalesce(array_to_string(p.proconfig, ','), '') as function_config,
    pg_get_functiondef(p.oid) as definition
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname in (
      'create_console_link_code',
      'claim_console_link',
      'claim_console_link_code',
      'poll_console_link',
      'poll_console_link_code'
    )
)
select
  proname,
  identity_arguments,
  result_type,
  security_definer,
  owner_name,
  function_config,
  has_function_privilege('anon', oid, 'execute') as anon_execute,
  has_function_privilege('authenticated', oid, 'execute') as authenticated_execute,
  definition
from functions
order by proname, identity_arguments;
"""


def management_query(query: str) -> Any:
    token = os.environ.get("SUPABASE_ACCESS_TOKEN", "").strip()
    configured_ref = os.environ.get("PROJECT_REF", EXPECTED_PROJECT_REF).strip()
    if configured_ref != EXPECTED_PROJECT_REF:
        raise RuntimeError(f"Refusing unexpected project ref: {configured_ref}")
    if not token:
        raise RuntimeError("SUPABASE_ACCESS_TOKEN is missing")

    request = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{EXPECTED_PROJECT_REF}/database/query",
        data=json.dumps({"query": query}).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "AddonStation-console-claim-diagnostic/1",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:700]
        raise RuntimeError(f"Management API HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Management API request failed: {exc.reason}") from exc
    return json.loads(body) if body.strip() else None


def signature(row: dict[str, Any]) -> str:
    return f"{row.get('proname')}({row.get('identity_arguments')})"


def safe_literals(definition: str) -> list[str]:
    """Return only harmless response-like literals; never print SQL or tokens."""
    literals = {
        match.group(1).lower()
        for match in re.finditer(r"'([A-Za-z][A-Za-z0-9_]{1,48})'", definition)
    }
    interesting = {
        value
        for value in literals
        if value in {
            "ok",
            "message",
            "status",
            "error",
            "code",
            "platform",
            "device_name",
            "linked",
            "claimed",
            "approved",
            "success",
            "waiting",
            "sign_in_required",
            "code_not_found",
            "invalid_code",
            "code_expired",
            "too_many_attempts",
        }
        or value.endswith(("_required", "_expired", "_invalid", "_failed", "_found"))
    }
    return sorted(interesting)


def main() -> int:
    result = management_query(QUERY)
    if not isinstance(result, list):
        raise RuntimeError("Unexpected Management API response")

    rows = [row for row in result if isinstance(row, dict)]
    by_signature = {signature(row): row for row in rows}
    required = {
        "create_console_link_code(p_platform text, p_device_name text)",
        "claim_console_link(p_code text, p_access_token text, p_refresh_token text)",
        "claim_console_link_code(p_code text, p_access_token text, p_refresh_token text)",
        "poll_console_link(p_code text, p_poll_token uuid)",
        "poll_console_link_code(p_code text, p_poll_token uuid)",
    }
    missing = sorted(required - set(by_signature))
    if missing:
        raise RuntimeError("Missing console function signatures: " + ", ".join(missing))

    print(f"Project: {EXPECTED_PROJECT_REF} (read-only function metadata)")
    for name in sorted(required):
        row = by_signature[name]
        mode = "definer" if row.get("security_definer") is True else "invoker"
        print(
            f"{name}: security={mode}; anon_execute={str(row.get('anon_execute')).lower()}; "
            f"authenticated_execute={str(row.get('authenticated_execute')).lower()}; "
            f"result={row.get('result_type')}"
        )

    wrapper = by_signature[
        "claim_console_link(p_code text, p_access_token text, p_refresh_token text)"
    ]
    source = by_signature[
        "claim_console_link_code(p_code text, p_access_token text, p_refresh_token text)"
    ]
    source_literals = safe_literals(str(source.get("definition") or ""))
    print("Claim source response markers: " + (", ".join(source_literals) or "none detected"))

    problems: list[str] = []
    if wrapper.get("authenticated_execute") is not True:
        problems.append("authenticated cannot execute claim_console_link")
    if wrapper.get("anon_execute") is not False:
        problems.append("anon can execute claim_console_link")
    if (
        wrapper.get("security_definer") is not True
        and source.get("authenticated_execute") is not True
    ):
        problems.append(
            "claim wrapper is security-invoker while authenticated cannot execute its source"
        )

    create = by_signature["create_console_link_code(p_platform text, p_device_name text)"]
    poll = by_signature["poll_console_link(p_code text, p_poll_token uuid)"]
    if create.get("anon_execute") is not True:
        problems.append("anon cannot create a PlayStation link code")
    if poll.get("anon_execute") is not True:
        problems.append("anon cannot poll the shared link code")

    if problems:
        print("BROKEN: " + "; ".join(problems))
        return 2

    print("PASS: database grants permit the authenticated shared claim path")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(f"FAIL: {exc}")
        raise SystemExit(1)
