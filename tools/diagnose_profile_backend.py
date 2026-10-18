#!/usr/bin/env python3
"""Read-only Supabase profile diagnostics for GitHub Actions.

The script never prints API keys, user IDs, email addresses, profile rows, or
access tokens. It reports only endpoint status codes and aggregate counts.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass
class Response:
    status: int
    body: str
    headers: dict[str, str]


def api_headers(key: str, *, count: bool = False) -> dict[str, str]:
    headers = {
        "apikey": key,
        "Accept": "application/json",
    }
    # Legacy anon/service-role keys are JWTs and may be used as Bearer tokens.
    # New sb_publishable_/sb_secret_ keys belong only in the apikey header.
    if key.startswith("eyJ"):
        headers["Authorization"] = f"Bearer {key}"
    if count:
        headers["Prefer"] = "count=exact"
        headers["Range"] = "0-9999"
    return headers


def get(url: str, key: str, *, count: bool = False) -> Response:
    request = urllib.request.Request(url, headers=api_headers(key, count=count), method="GET")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return Response(
                status=response.status,
                body=response.read().decode("utf-8", "replace"),
                headers={name.lower(): value for name, value in response.headers.items()},
            )
    except urllib.error.HTTPError as error:
        return Response(
            status=error.code,
            body=error.read().decode("utf-8", "replace"),
            headers={name.lower(): value for name, value in error.headers.items()},
        )


def safe_error(body: str) -> str:
    try:
        value = json.loads(body)
    except Exception:
        return "non-JSON response"
    if not isinstance(value, dict):
        return "unexpected response"
    code = str(value.get("code") or value.get("error_code") or "unknown")[:40]
    message = str(value.get("message") or value.get("msg") or value.get("error") or "")
    # Schema errors are useful; redact long/free-form values defensively.
    message = " ".join(message.split())[:160]
    return f"{code}: {message}" if message else code


def parse_rows(response: Response) -> list[dict[str, Any]]:
    if response.status < 200 or response.status >= 300:
        return []
    try:
        value = json.loads(response.body)
    except Exception:
        return []
    return value if isinstance(value, list) else []


def exact_count(response: Response, rows: list[dict[str, Any]]) -> int:
    content_range = response.headers.get("content-range", "")
    if "/" in content_range:
        total = content_range.rsplit("/", 1)[-1]
        if total.isdigit():
            return int(total)
    return len(rows)


def probe_table(base: str, key: str, table: str, label: str) -> tuple[Response, list[dict[str, Any]]]:
    query = urllib.parse.urlencode({"select": "id", "limit": "10000"})
    response = get(f"{base}/rest/v1/{table}?{query}", key, count=True)
    rows = parse_rows(response)
    if 200 <= response.status < 300:
        print(f"{label}: HTTP {response.status}; rows={exact_count(response, rows)}")
    else:
        print(f"{label}: HTTP {response.status}; {safe_error(response.body)}")
    return response, rows


def build_defaults() -> dict[str, str]:
    """Read the public build defaults when equivalent Actions secrets are absent."""
    values: dict[str, str] = {}
    path = os.path.join("android", "gradle.properties")
    try:
        with open(path, encoding="utf-8") as source:
            for raw_line in source:
                line = raw_line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                name, value = line.split("=", 1)
                values[name.strip()] = value.strip()
    except OSError:
        pass
    return values


def main() -> int:
    defaults = build_defaults()
    base = (
        os.environ.get("SUPABASE_URL", "").strip()
        or defaults.get("ADDONSTATION_SUPABASE_URL", "")
    ).rstrip("/")
    public_key = (
        os.environ.get("SUPABASE_PUBLIC_KEY", "").strip()
        or defaults.get("ADDONSTATION_SUPABASE_ANON_KEY", "")
    )
    secret_key = os.environ.get("SUPABASE_SECRET_KEY", "").strip()

    if not base or not public_key:
        print("ERROR: Supabase public build configuration is missing.")
        return 2

    print("Profile backend diagnostic (read-only; aggregate output only)")
    print(f"Project configured: yes; server key available: {'yes' if secret_key else 'no'}")

    public_profiles, public_profile_rows = probe_table(base, public_key, "profiles", "public profiles")
    public_view, public_view_rows = probe_table(base, public_key, "visible_profiles", "public visible_profiles")

    if 200 <= public_profiles.status < 300 and not public_profile_rows:
        print("WARNING: profiles is reachable but returned no profile rows.")
    if 200 <= public_view.status < 300 and not public_view_rows:
        print("WARNING: visible_profiles is reachable but returned no profile rows.")

    if not secret_key:
        print("Server-side comparison skipped: no server key is configured.")
        return 0

    server_profiles, profile_rows = probe_table(base, secret_key, "profiles", "server profiles")
    server_view, view_rows = probe_table(base, secret_key, "visible_profiles", "server visible_profiles")

    profile_ids = {str(row.get("id")) for row in profile_rows if row.get("id")}
    view_ids = {str(row.get("id")) for row in view_rows if row.get("id")}
    if 200 <= server_profiles.status < 300 and 200 <= server_view.status < 300:
        print(f"Profiles absent from visible_profiles: {len(profile_ids - view_ids)}")

    # The Admin Auth endpoint comparison can identify accounts whose profile
    # trigger did not create a row, without disclosing which accounts they are.
    auth_response = get(f"{base}/auth/v1/admin/users?page=1&per_page=1000", secret_key)
    if 200 <= auth_response.status < 300:
        try:
            payload = json.loads(auth_response.body)
            users = payload.get("users", []) if isinstance(payload, dict) else []
        except Exception:
            users = []
        auth_ids = {str(user.get("id")) for user in users if isinstance(user, dict) and user.get("id")}
        # Prefer the server-side base table when available. New opaque server
        # keys may be accepted by Auth Admin while PostgREST still maps them to
        # a role without base-table grants; the public safe view is authoritative
        # enough for this existence-only comparison and exposes no row values.
        if 200 <= server_profiles.status < 300:
            comparable_ids = profile_ids
            comparison_source = "profiles"
        else:
            comparable_ids = {
                str(row.get("id")) for row in public_view_rows if row.get("id")
            }
            comparison_source = "visible_profiles"
        print(f"Auth users sampled: {len(auth_ids)}")
        print(
            "Auth users missing a profile row: "
            f"{len(auth_ids - comparable_ids)} (source={comparison_source})"
        )
    else:
        print(f"Auth users comparison: HTTP {auth_response.status}; {safe_error(auth_response.body)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
