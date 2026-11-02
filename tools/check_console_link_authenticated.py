#!/usr/bin/env python3
"""Exercise the authenticated phone/PS4 link contract with a disposable user.

This production integration check is opt-in and must run only in protected CI.
It creates one confirmed temporary user through the Supabase Auth Admin API,
signs in through the public API, creates/claims/polls one short-lived console
code, then deletes the temporary user in a ``finally`` block. It never prints
an email, password, user id, code, poll token, access token, refresh token, or
secret key.
"""

from __future__ import annotations

import base64
import json
import os
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any
from uuid import UUID

EXPECTED_URL = "https://stbmescmexzcszyyambq.supabase.co"
ROOT = Path(__file__).resolve().parents[1]
CODE_RE = re.compile(r"^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$")
SAFE_VALUE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{0,63}$")
SENSITIVE_KEYS = {
    "access_token",
    "refresh_token",
    "poll_token",
    "code",
    "formatted_code",
    "email",
    "id",
    "user_id",
}


class CheckError(RuntimeError):
    pass


def read_public_key() -> str:
    path = ROOT / "android" / "gradle.properties"
    match = re.search(
        r"^ADDONSTATION_SUPABASE_ANON_KEY=(\S+)$",
        path.read_text(encoding="utf-8"),
        re.MULTILINE,
    )
    key = match.group(1).strip() if match else ""
    if not key:
        raise CheckError("Android public key is missing")
    if key.startswith("sb_secret_") or key.startswith("service_role"):
        raise CheckError("Android key is not public")
    return key


def read_secret_key() -> str:
    key = os.environ.get("SUPABASE_SECRET_KEY", "").strip()
    if not key:
        raise CheckError("SUPABASE_SECRET_KEY is missing")
    if key.startswith("sb_secret_"):
        return key
    if key.count(".") == 2:
        try:
            payload_part = key.split(".")[1]
            payload_part += "=" * (-len(payload_part) % 4)
            payload = json.loads(base64.urlsafe_b64decode(payload_part))
        except Exception as exc:  # noqa: BLE001 - fail closed for credentials
            raise CheckError("Configured server JWT is malformed") from exc
        if payload.get("role") == "service_role":
            return key
    raise CheckError("A Supabase secret/service-role key is required")


def safe_error_body(raw: bytes) -> str:
    """Return only a bounded API error code/message with identifiers redacted."""
    text = raw.decode("utf-8", "replace")
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        value = None
    parts: list[str] = []
    if isinstance(value, dict):
        for name in ("code", "message", "msg", "error", "error_description", "hint"):
            candidate = value.get(name)
            if isinstance(candidate, str) and candidate:
                parts.append(f"{name}={candidate}")
    summary = "; ".join(parts) if parts else "non-JSON error response"
    summary = re.sub(r"[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}", "<redacted-id>", summary)
    summary = re.sub(r"[\w.+-]+@[\w.-]+", "<redacted-email>", summary)
    summary = re.sub(r"eyJ[A-Za-z0-9_.-]{30,}", "<redacted-token>", summary)
    return summary[:500]


def request_json(
    method: str,
    path: str,
    *,
    api_key: str,
    bearer: str | None = None,
    payload: dict[str, Any] | None = None,
) -> Any:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {
        "apikey": api_key,
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": "AddonStation-console-link-authenticated-check/1",
    }
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    request = urllib.request.Request(
        EXPECTED_URL + path,
        data=data,
        method=method,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
    except urllib.error.HTTPError as exc:
        detail = safe_error_body(exc.read())
        raise CheckError(f"{method} {path} returned HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise CheckError(f"{method} {path} could not reach production: {exc.reason}") from exc
    if not raw.strip():
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise CheckError(f"{method} {path} returned invalid JSON") from exc


def as_object(value: Any, operation: str) -> dict[str, Any]:
    if isinstance(value, list) and len(value) == 1:
        value = value[0]
    if not isinstance(value, dict):
        raise CheckError(f"{operation} did not return a JSON object")
    return value


def response_marker(value: dict[str, Any]) -> str | None:
    for name in ("status", "message", "error", "code"):
        candidate = value.get(name)
        if isinstance(candidate, str) and SAFE_VALUE_RE.fullmatch(candidate):
            return candidate.lower()
    return None


def safe_shape(value: dict[str, Any]) -> str:
    keys = sorted(key for key in value if key not in SENSITIVE_KEYS)
    marker = response_marker(value)
    ok = value.get("ok") if isinstance(value.get("ok"), bool) else None
    return f"keys={','.join(keys) or 'none'}; ok={ok}; marker={marker or 'none'}"


def create_user(secret_key: str, email: str, password: str, username: str) -> str:
    created = as_object(
        request_json(
            "POST",
            "/auth/v1/admin/users",
            api_key=secret_key,
            bearer=secret_key,
            payload={
                "email": email,
                "password": password,
                "email_confirm": True,
                "user_metadata": {
                    "username": username,
                    "display_name": "Console Link CI",
                },
            },
        ),
        "temporary user creation",
    )
    user_id = created.get("id")
    try:
        UUID(str(user_id))
    except (TypeError, ValueError, AttributeError) as exc:
        raise CheckError("Auth Admin API returned an invalid temporary user id") from exc
    return str(user_id)


def delete_user(secret_key: str, user_id: str) -> None:
    encoded = urllib.parse.quote(user_id, safe="")
    request_json(
        "DELETE",
        f"/auth/v1/admin/users/{encoded}?should_soft_delete=false",
        api_key=secret_key,
        bearer=secret_key,
    )


def main() -> int:
    configured_url = os.environ.get("SUPABASE_URL", EXPECTED_URL).rstrip("/")
    if configured_url != EXPECTED_URL:
        raise CheckError(f"Refusing unexpected Supabase URL: {configured_url}")

    public_key = read_public_key()
    secret_key = read_secret_key()
    suffix = secrets.token_hex(8)
    email = f"console-link-ci-{suffix}@example.com"
    username = f"console_ci_{suffix}"
    password = secrets.token_urlsafe(32) + "aA7!"
    user_id: str | None = None
    primary_error: BaseException | None = None

    try:
        user_id = create_user(secret_key, email, password, username)
        print("PASS: disposable confirmed user created (identity withheld)")

        signed_in = as_object(
            request_json(
                "POST",
                "/auth/v1/token?grant_type=password",
                api_key=public_key,
                payload={"email": email, "password": password},
            ),
            "temporary user sign-in",
        )
        access_token = signed_in.get("access_token")
        refresh_token = signed_in.get("refresh_token")
        if not isinstance(access_token, str) or not access_token:
            raise CheckError("Temporary sign-in returned no access token")
        if not isinstance(refresh_token, str) or not refresh_token:
            raise CheckError("Temporary sign-in returned no refresh token")
        print("PASS: disposable user obtained a complete session (tokens withheld)")

        created = as_object(
            request_json(
                "POST",
                "/rest/v1/rpc/create_console_link_code",
                api_key=public_key,
                bearer=public_key,
                payload={"p_platform": "ps4", "p_device_name": "AddonStation CI"},
            ),
            "console code creation",
        )
        code = created.get("code")
        poll_token = created.get("poll_token")
        if not isinstance(code, str) or not CODE_RE.fullmatch(code):
            raise CheckError("Code creation returned an invalid code")
        try:
            UUID(str(poll_token))
        except (TypeError, ValueError, AttributeError) as exc:
            raise CheckError("Code creation returned an invalid poll token") from exc
        print("PASS: fresh PS4 code created (code and poll token withheld)")

        claimed = as_object(
            request_json(
                "POST",
                "/rest/v1/rpc/claim_console_link",
                api_key=public_key,
                bearer=access_token,
                payload={
                    "p_code": code.lower(),
                    "p_access_token": access_token,
                    "p_refresh_token": refresh_token,
                },
            ),
            "authenticated console claim",
        )
        print("CLAIM: " + safe_shape(claimed))

        polled = as_object(
            request_json(
                "POST",
                "/rest/v1/rpc/poll_console_link",
                api_key=public_key,
                bearer=public_key,
                payload={"p_code": code.lower(), "p_poll_token": str(poll_token)},
            ),
            "console poll after claim",
        )
        print("POLL: " + safe_shape(polled))

        marker = response_marker(polled)
        if marker == "waiting":
            raise CheckError("Authenticated claim returned, but PlayStation still reports waiting")
        if marker not in {"linked", "claimed", "approved", "success", "complete", "completed"}:
            # Some implementations signal completion only through session fields.
            has_session = any(
                isinstance(polled.get(name), str) and bool(polled.get(name))
                for name in ("access_token", "refresh_token")
            )
            if not has_session:
                raise CheckError("Poll response did not prove that the console link completed")
        print("PASS: authenticated phone claim completed the PlayStation poll flow")
        return 0
    except BaseException as exc:  # cleanup must run for failures and cancellation
        primary_error = exc
        raise
    finally:
        if user_id:
            try:
                delete_user(secret_key, user_id)
                print("PASS: disposable user deleted")
            except BaseException as cleanup_error:
                print(f"CLEANUP FAIL: {cleanup_error}")
                if primary_error is None:
                    raise


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except CheckError as exc:
        print(f"FAIL: {exc}")
        raise SystemExit(1)
