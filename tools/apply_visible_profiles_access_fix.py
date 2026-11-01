#!/usr/bin/env python3
"""Apply and verify the focused visible_profiles access migration.

This tool is intentionally opt-in: it performs no write unless ``--apply`` is
present. It uses Supabase's Management API for one idempotent SQL migration,
then verifies Android's anonymous ``select=*`` query without printing profile
values or identifiers.
"""
from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_PATH = ROOT / "supabase" / "42_visible_profiles_access.sql"
REQUIRED_COLUMNS = {
    "id",
    "username",
    "display_name",
    "avatar_url",
    "banner_url",
    "custom_status",
    "bio",
    "country",
    "role",
    "plan",
    "plan_expires_at",
    "user_level",
    "equipped_item_id",
    "show_online_status",
    "last_seen_at",
    "created_at",
    "updated_at",
}


def public_build_defaults() -> dict[str, str]:
    """Read non-secret Android defaults when equivalent Actions secrets are absent."""
    values: dict[str, str] = {}
    path = ROOT / "android" / "gradle.properties"
    try:
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            name, value = line.split("=", 1)
            values[name.strip()] = value.strip()
    except OSError:
        pass
    return values


def project_ref(url: str, explicit: str) -> str:
    if explicit.strip():
        return explicit.strip()
    host = urllib.parse.urlparse(url).hostname or ""
    ref = host.split(".", 1)[0]
    if not ref:
        raise RuntimeError("Cannot derive the Supabase project ref from SUPABASE_URL")
    return ref


def management_query(ref: str, token: str, query: str) -> object:
    request = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{ref}/database/query",
        data=json.dumps({"query": query}).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:700]
        raise RuntimeError(f"Management API HTTP {exc.code}: {detail}") from exc
    return json.loads(body) if body.strip() else None


def anonymous_get(base: str, key: str, query: str) -> tuple[int, bytes]:
    request = urllib.request.Request(
        f"{base.rstrip('/')}/rest/v1/visible_profiles?{query}",
        headers={
            "apikey": key,
            "Accept": "application/json",
            "Prefer": "count=exact",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def verify_database_privileges(ref: str, token: str) -> None:
    result = management_query(
        ref,
        token,
        """
        select
          has_table_privilege('anon', 'public.visible_profiles', 'select') as anon_view_select,
          has_table_privilege('authenticated', 'public.visible_profiles', 'select') as auth_view_select,
          has_table_privilege('anon', 'public.profiles', 'select') as anon_base_select,
          has_table_privilege('authenticated', 'public.profiles', 'select') as auth_base_select,
          has_function_privilege(
            'anon',
            'public.is_admin_only_profile_asset(text,text)',
            'execute'
          ) as anon_helper_execute,
          has_function_privilege(
            'authenticated',
            'public.is_admin_only_profile_asset(text,text)',
            'execute'
          ) as auth_helper_execute
        """,
    )
    if not isinstance(result, list) or len(result) != 1 or not isinstance(result[0], dict):
        raise RuntimeError("Unexpected privilege verification response")
    row = result[0]
    expected = {
        "anon_view_select": True,
        "auth_view_select": True,
        "anon_base_select": False,
        "auth_base_select": False,
        "anon_helper_execute": False,
        "auth_helper_execute": False,
    }
    wrong = [name for name, value in expected.items() if row.get(name) is not value]
    if wrong:
        raise RuntimeError("Unexpected profile privilege state: " + ", ".join(sorted(wrong)))
    print("Database privileges: safe view readable; base table and helper remain private")


def verify_android_queries(base: str, key: str) -> None:
    query = urllib.parse.urlencode({"select": "*", "order": "created_at.asc"})
    last_status = 0
    last_body = b""
    for attempt in range(1, 7):
        last_status, last_body = anonymous_get(base, key, query)
        if 200 <= last_status < 300:
            break
        if attempt < 6:
            time.sleep(3)
    if not 200 <= last_status < 300:
        detail = last_body.decode("utf-8", "replace")[:500]
        raise RuntimeError(f"Anonymous visible_profiles select=* failed: HTTP {last_status}: {detail}")

    try:
        rows = json.loads(last_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("Anonymous visible_profiles response is not valid JSON") from exc
    if not isinstance(rows, list) or not rows:
        raise RuntimeError("Anonymous visible_profiles select=* returned no rows")

    missing = sorted(REQUIRED_COLUMNS - set(rows[0])) if isinstance(rows[0], dict) else sorted(REQUIRED_COLUMNS)
    if missing:
        raise RuntimeError("visible_profiles is missing Android columns: " + ", ".join(missing))

    invalid_rows = 0
    filtered_success = 0
    for row in rows:
        valid = (
            isinstance(row, dict)
            and isinstance(row.get("id"), str)
            and (row.get("username") is None or isinstance(row.get("username"), str))
            and (row.get("display_name") is None or isinstance(row.get("display_name"), str))
            and isinstance(row.get("user_level"), int)
            and not isinstance(row.get("user_level"), bool)
            and isinstance(row.get("show_online_status"), bool)
        )
        if not valid:
            invalid_rows += 1
            continue
        filtered_query = urllib.parse.urlencode(
            {"select": "*", "id": f"eq.{row['id']}", "limit": "1"}
        )
        status, body = anonymous_get(base, key, filtered_query)
        if 200 <= status < 300:
            try:
                filtered_rows = json.loads(body.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                filtered_rows = None
            if isinstance(filtered_rows, list) and len(filtered_rows) == 1:
                filtered_success += 1

    if invalid_rows:
        raise RuntimeError(f"visible_profiles returned {invalid_rows} row(s) with incompatible Android types")
    if filtered_success != len(rows):
        raise RuntimeError(
            "Android-equivalent filtered reads failed: "
            f"{filtered_success}/{len(rows)} succeeded"
        )
    print(
        "Android profile queries: "
        f"select=* rows={len(rows)}; filtered reads={filtered_success}/{len(rows)}; types=valid"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="Apply the idempotent SQL migration")
    args = parser.parse_args()
    if not args.apply:
        print("Dry run only: pass --apply to execute the focused migration")
        return 0

    defaults = public_build_defaults()
    base = (
        os.environ.get("SUPABASE_URL", "").strip()
        or defaults.get("ADDONSTATION_SUPABASE_URL", "")
    )
    public_key = (
        os.environ.get("SUPABASE_PUBLIC_KEY", "").strip()
        or defaults.get("ADDONSTATION_SUPABASE_ANON_KEY", "")
    )
    access_token = os.environ.get("SUPABASE_ACCESS_TOKEN", "").strip()
    if not base.startswith("https://"):
        raise RuntimeError("SUPABASE_URL is missing or invalid")
    if not public_key:
        raise RuntimeError("SUPABASE_PUBLIC_KEY is missing")
    if not access_token:
        raise RuntimeError("SUPABASE_ACCESS_TOKEN is missing")

    ref = project_ref(base, os.environ.get("PROJECT_REF", ""))
    sql = SQL_PATH.read_text(encoding="utf-8")
    management_query(ref, access_token, sql)
    print("Applied focused visible_profiles access migration")
    verify_database_privileges(ref, access_token)
    verify_android_queries(base, public_key)
    print("Visible profile access fix: verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
