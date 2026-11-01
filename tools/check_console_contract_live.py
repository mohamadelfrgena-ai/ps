#!/usr/bin/env python3
"""Safely verify the shared PS4/phone link contract against production.

This diagnostic uses only the public publishable/anon key. It creates one
short-lived PS4 code, verifies that polling reports ``waiting``, and confirms
that an anonymous phone claim is rejected without consuming the code. It never
prints the public key, code, poll token, or any reusable user credential.
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

EXPECTED_URL = "https://stbmescmexzcszyyambq.supabase.co"
EXPECTED_RPC_PARAMETERS = {
    "create_console_link_code": {"p_platform", "p_device_name"},
    "claim_console_link": {"p_code", "p_access_token", "p_refresh_token"},
    "poll_console_link": {"p_code", "p_poll_token"},
}
CODE_RE = re.compile(r"^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$")
ROOT = Path(__file__).resolve().parents[1]


class ContractError(RuntimeError):
    pass


def public_key() -> str:
    key = os.environ.get("SUPABASE_PUBLIC_KEY", "").strip()
    if not key:
        properties = (ROOT / "android/gradle.properties").read_text(encoding="utf-8")
        match = re.search(r"^ADDONSTATION_SUPABASE_ANON_KEY=(\S+)$", properties, re.MULTILINE)
        key = match.group(1) if match else ""
    if not key:
        raise ContractError("SUPABASE_PUBLIC_KEY is missing")
    if key.startswith("sb_secret_") or key.startswith("service_role"):
        raise ContractError("refusing to use a secret/service-role key")

    # Legacy public JWTs are accepted, but service-role JWTs are not.
    if key.count(".") == 2:
        try:
            payload_part = key.split(".")[1]
            payload_part += "=" * (-len(payload_part) % 4)
            payload = json.loads(base64.urlsafe_b64decode(payload_part))
        except Exception as exc:  # noqa: BLE001 - malformed credentials must fail closed
            raise ContractError("public JWT is malformed") from exc
        if payload.get("role") == "service_role":
            raise ContractError("refusing to use a service-role JWT")
    return key


def request_json(
    method: str,
    path: str,
    key: str,
    payload: dict[str, Any] | None = None,
    *,
    accept: str = "application/json",
) -> Any:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    request = urllib.request.Request(
        f"{EXPECTED_URL}{path}",
        data=data,
        method=method,
        headers={
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Accept": accept,
            "Content-Type": "application/json",
            "User-Agent": "AddonStation-contract-check/1",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            raw = response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:800]
        raise ContractError(f"{method} {path} returned HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise ContractError(f"{method} {path} could not reach production: {exc.reason}") from exc
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ContractError(f"{method} {path} returned invalid JSON") from exc


def as_object(value: Any, rpc: str) -> dict[str, Any]:
    if isinstance(value, list) and len(value) == 1:
        value = value[0]
    if not isinstance(value, dict):
        raise ContractError(f"{rpc} did not return a JSON object")
    return value


def parse_timestamp(value: Any) -> datetime:
    if not isinstance(value, str) or not value:
        raise ContractError("create_console_link_code returned an invalid expires_at")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ContractError("create_console_link_code returned an invalid expires_at") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def response_status(value: dict[str, Any]) -> str | None:
    for field in ("status", "message", "error", "code"):
        candidate = value.get(field)
        if isinstance(candidate, str):
            return candidate
    return None


def verify_openapi(key: str) -> None:
    document = request_json(
        "GET",
        "/rest/v1/",
        key,
        accept="application/openapi+json",
    )
    if not isinstance(document, dict) or not isinstance(document.get("paths"), dict):
        raise ContractError("PostgREST did not return an OpenAPI paths object")
    paths: dict[str, Any] = document["paths"]

    for rpc, parameters in EXPECTED_RPC_PARAMETERS.items():
        route = f"/rpc/{rpc}"
        if route not in paths:
            raise ContractError(f"production is missing {route}")
        serialized = json.dumps(paths[route], sort_keys=True)
        missing = sorted(parameter for parameter in parameters if parameter not in serialized)
        if missing:
            raise ContractError(f"{route} is missing parameters: {', '.join(missing)}")

    forbidden = sorted(path for path in paths if path.startswith("/rpc/console_code_"))
    if forbidden:
        raise ContractError(f"obsolete console_code_* RPCs are exposed: {', '.join(forbidden)}")
    if "consumed_at" in json.dumps(document):
        raise ContractError("obsolete consumed_at is exposed by the production REST schema")
    print("PASS: OpenAPI exposes only the exact shared create/claim/poll RPC contract")


def verify_live_flow(key: str) -> None:
    created = as_object(
        request_json(
            "POST",
            "/rest/v1/rpc/create_console_link_code",
            key,
            {"p_platform": "ps4", "p_device_name": "AddonStation CI Contract Check"},
        ),
        "create_console_link_code",
    )
    required = {"code", "formatted_code", "poll_token", "expires_at", "expires_in_seconds"}
    missing = sorted(required - created.keys())
    if missing:
        raise ContractError(f"create_console_link_code is missing fields: {', '.join(missing)}")

    code = created.get("code")
    formatted = created.get("formatted_code")
    poll_token = created.get("poll_token")
    if not isinstance(code, str) or not CODE_RE.fullmatch(code):
        raise ContractError("create_console_link_code returned an invalid six-character code")
    if not isinstance(formatted, str) or re.sub(r"[^A-Za-z0-9]", "", formatted).upper() != code:
        raise ContractError("formatted_code does not represent the returned code")
    try:
        UUID(str(poll_token))
    except (ValueError, TypeError, AttributeError) as exc:
        raise ContractError("create_console_link_code returned an invalid poll_token") from exc
    if created.get("expires_in_seconds") != 600:
        raise ContractError("create_console_link_code must return expires_in_seconds = 600")
    seconds_until_expiry = (parse_timestamp(created.get("expires_at")) - datetime.now(timezone.utc)).total_seconds()
    if not 540 <= seconds_until_expiry <= 660:
        raise ContractError("expires_at is not approximately ten minutes in the future")
    print("PASS: create_console_link_code returns the exact ten-minute response shape")

    poll_payload = {"p_code": code.lower(), "p_poll_token": str(poll_token)}
    waiting = as_object(
        request_json("POST", "/rest/v1/rpc/poll_console_link", key, poll_payload),
        "poll_console_link",
    )
    if response_status(waiting) != "waiting":
        raise ContractError("a fresh code did not poll as waiting")
    remaining = waiting.get("expires_in_seconds")
    if not isinstance(remaining, int) or not 0 < remaining <= 600:
        raise ContractError("waiting did not return a valid expires_in_seconds")
    print("PASS: poll_console_link accepts normalized case and reports waiting")

    rejected = as_object(
        request_json(
            "POST",
            "/rest/v1/rpc/claim_console_link",
            key,
            {
                "p_code": code.lower(),
                "p_access_token": "contract-check-invalid-access-token",
                "p_refresh_token": "contract-check-invalid-refresh-token",
            },
        ),
        "claim_console_link",
    )
    if rejected.get("ok") is not False or response_status(rejected) != "sign_in_required":
        raise ContractError("anonymous claim must return {ok:false, message:'sign_in_required'}")

    still_waiting = as_object(
        request_json("POST", "/rest/v1/rpc/poll_console_link", key, poll_payload),
        "poll_console_link",
    )
    if response_status(still_waiting) != "waiting":
        raise ContractError("a rejected claim consumed or changed the one-time code")
    print("PASS: anonymous claim is rejected and does not consume the code")


def main() -> int:
    configured_url = os.environ.get("SUPABASE_URL", EXPECTED_URL).rstrip("/")
    if configured_url != EXPECTED_URL:
        raise ContractError(f"refusing unexpected Supabase project URL: {configured_url}")
    key = public_key()
    verify_openapi(key)
    verify_live_flow(key)
    print("PASS: production PS4/phone link contract is live and compatible")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
