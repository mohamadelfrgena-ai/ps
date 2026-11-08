#!/usr/bin/env python3
"""Verify immediate and monthly staff salary behavior with a disposable user.

The check runs only in protected CI against the reviewed production project. It
creates one confirmed temporary account, records its starting AC balance,
assigns Moderator through the actual ``admin_update_profile`` RPC under an
existing admin identity, and proves that exactly 1500 AC is credited once for
the current Tripoli month.
It then changes roles again in the same month and proves that neither the wallet
nor the salary ledger is credited twice. The temporary account is always
removed, and no identifier or credential is printed.
"""

from __future__ import annotations

import os
import re
import secrets
import subprocess
import urllib.parse
import uuid
from typing import Any

from check_console_link_authenticated import (
    CheckError,
    create_user,
    delete_user,
    read_secret_key,
    request_json,
)

EXPECTED_URL = "https://stbmescmexzcszyyambq.supabase.co"
EXPECTED_AMOUNT = 1500
DB_HOST = "aws-0-eu-west-1.pooler.supabase.com"
DB_USER = "postgres.stbmescmexzcszyyambq"
DB_IMAGE = "postgres:17"


def encoded_id(user_id: str) -> str:
    return urllib.parse.quote(user_id, safe="")


def service_request(
    method: str,
    path: str,
    secret_key: str,
    payload: dict[str, Any] | None = None,
) -> Any:
    return request_json(
        method,
        path,
        api_key=secret_key,
        bearer=secret_key,
        payload=payload,
    )


def as_rows(value: Any, operation: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or any(not isinstance(row, dict) for row in value):
        raise CheckError(f"{operation} did not return a JSON row list")
    return value


def salary_config(secret_key: str) -> dict[str, Any]:
    rows = as_rows(
        service_request(
            "GET",
            "/rest/v1/app_settings?select=value&key=eq.moderator_salary&limit=1",
            secret_key,
        ),
        "salary configuration read",
    )
    if len(rows) != 1 or not isinstance(rows[0].get("value"), dict):
        raise CheckError("Salary configuration is missing")
    return rows[0]["value"]


def wallet_balance(secret_key: str, user_id: str) -> int:
    rows = as_rows(
        service_request(
            "GET",
            "/rest/v1/ac_wallets?select=balance&user_id=eq."
            + encoded_id(user_id)
            + "&limit=1",
            secret_key,
        ),
        "wallet read",
    )
    if not rows:
        return 0
    value = rows[0].get("balance")
    if isinstance(value, bool) or not isinstance(value, int):
        raise CheckError("Wallet returned an invalid balance")
    return value


def salary_payments(secret_key: str, user_id: str) -> list[dict[str, Any]]:
    return as_rows(
        service_request(
            "GET",
            "/rest/v1/staff_salary_payments?select=amount,pay_month,transaction_id"
            "&user_id=eq."
            + encoded_id(user_id)
            + "&order=created_at.asc",
            secret_key,
        ),
        "salary ledger read",
    )


def salary_transactions(secret_key: str, user_id: str) -> list[dict[str, Any]]:
    return as_rows(
        service_request(
            "GET",
            "/rest/v1/ac_transactions?select=amount,kind,reason,balance_after,request_key"
            "&user_id=eq."
            + encoded_id(user_id)
            + "&kind=eq.salary&order=created_at.asc",
            secret_key,
        ),
        "salary transaction read",
    )


def run_private_sql(db_password: str, sql: str, operation: str) -> None:
    """Execute protected SQL while keeping credentials and row IDs out of logs."""
    environment = os.environ.copy()
    environment["PGPASSWORD"] = db_password
    command = [
        "docker",
        "run",
        "--rm",
        "-i",
        "-e",
        "PGPASSWORD",
        DB_IMAGE,
        "psql",
        "-h",
        DB_HOST,
        "-p",
        "5432",
        "-U",
        DB_USER,
        "-d",
        "postgres",
        "-q",
        "-v",
        "ON_ERROR_STOP=1",
    ]
    completed = subprocess.run(
        command,
        input=sql,
        text=True,
        capture_output=True,
        env=environment,
        check=False,
    )
    if completed.returncode != 0:
        # PostgreSQL errors must be useful without leaking either UUID involved.
        detail = (completed.stderr or completed.stdout or "unknown database error").strip()
        detail = re.sub(
            r"(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
            "<redacted-id>",
            detail,
        )
        raise CheckError(f"{operation} failed: {detail[:500]}")


def set_role_via_admin(db_password: str, user_id: str, role: str) -> None:
    """Run the real admin RPC without exposing an admin or disposable user ID."""
    if role not in {"user", "moderator", "head_moderator"}:
        raise CheckError("Refusing an unexpected salary-test role")
    try:
        target_id = str(uuid.UUID(user_id))
    except ValueError as exc:
        raise CheckError("Salary test received an invalid disposable user ID") from exc

    sql = f"""
do $salary_test$
declare
  v_admin uuid;
  v_result jsonb;
begin
  select id into v_admin
    from public.profiles
   where role = 'admin'
   order by created_at, id
   limit 1;
  if v_admin is null then
    raise exception 'salary_test_admin_missing';
  end if;

  perform set_config('request.jwt.claim.sub', v_admin::text, true);
  perform set_config('request.jwt.claim.role', 'authenticated', true);
  perform set_config(
    'request.jwt.claims',
    jsonb_build_object('sub', v_admin::text, 'role', 'authenticated')::text,
    true
  );

  v_result := public.admin_update_profile(
    '{target_id}'::uuid,
    null::text,
    '{role}'::text,
    null::timestamptz,
    null::int
  );
  if coalesce((v_result ->> 'ok')::boolean, false) is not true then
    raise exception 'salary_test_role_update_failed: %',
      coalesce(v_result ->> 'message', 'unknown');
  end if;
end;
$salary_test$;
"""
    run_private_sql(db_password, sql, "Admin role assignment")


def delete_salary_test_audit(db_password: str, user_id: str) -> None:
    try:
        target_id = str(uuid.UUID(user_id))
    except ValueError as exc:
        raise CheckError("Salary cleanup received an invalid disposable user ID") from exc
    sql = f"""
delete from public.admin_audit_log
 where target_type = 'profile'
   and target_id = '{target_id}'
   and action = 'profile_updated';
"""
    run_private_sql(db_password, sql, "Salary-test audit cleanup")


def delete_profile_if_present(secret_key: str, user_id: str) -> None:
    service_request(
        "DELETE",
        "/rest/v1/profiles?id=eq." + encoded_id(user_id),
        secret_key,
    )


def assert_single_salary(secret_key: str, user_id: str, expected_balance: int) -> None:
    balance = wallet_balance(secret_key, user_id)
    if balance != expected_balance:
        raise CheckError(
            f"Salary wallet delta is incorrect: expected balance delta target {expected_balance}, got {balance}"
        )

    payments = salary_payments(secret_key, user_id)
    if len(payments) != 1 or payments[0].get("amount") != EXPECTED_AMOUNT:
        raise CheckError("Salary ledger is not exactly one 1500 AC payment")
    if not payments[0].get("transaction_id"):
        raise CheckError("Salary ledger is missing its AC transaction link")

    transactions = salary_transactions(secret_key, user_id)
    if len(transactions) != 1:
        raise CheckError("AC history does not contain exactly one salary transaction")
    transaction = transactions[0]
    if transaction.get("kind") != "salary" or transaction.get("amount") != EXPECTED_AMOUNT:
        raise CheckError("AC history salary transaction has an invalid kind or amount")
    request_key = transaction.get("request_key")
    if not isinstance(request_key, str) or not request_key.startswith("salary:"):
        raise CheckError("AC history salary transaction has no monthly idempotency key")


def main() -> int:
    configured_url = os.environ.get("SUPABASE_URL", EXPECTED_URL).rstrip("/")
    if configured_url != EXPECTED_URL:
        raise CheckError(f"Refusing unexpected Supabase URL: {configured_url}")

    db_password = os.environ.get("DB_PASSWORD", "").strip()
    if not db_password:
        raise CheckError("DB_PASSWORD is missing for the protected admin-path test")

    secret_key = read_secret_key()
    suffix = secrets.token_hex(8)
    email = f"staff-salary-ci-{suffix}@example.com"
    username = f"salary_ci_{suffix}"
    password = secrets.token_urlsafe(32) + "aA7!"
    user_id: str | None = None
    primary_error: BaseException | None = None

    try:
        config = salary_config(secret_key)
        if config.get("enabled") is not True or config.get("amount") != EXPECTED_AMOUNT:
            raise CheckError("Salary configuration is not enabled at 1500 AC")
        print("PASS: production salary is enabled at 1500 AC")

        user_id = create_user(secret_key, email, password, username)
        print("PASS: disposable salary account created (identity withheld)")

        baseline = wallet_balance(secret_key, user_id)
        set_role_via_admin(db_password, user_id, "moderator")
        expected = baseline + EXPECTED_AMOUNT
        assert_single_salary(secret_key, user_id, expected)
        print("PASS: assigning Moderator immediately credited exactly 1500 AC")

        set_role_via_admin(db_password, user_id, "user")
        set_role_via_admin(db_password, user_id, "moderator")
        assert_single_salary(secret_key, user_id, expected)
        print("PASS: reassigning Moderator in the same month did not pay twice")

        set_role_via_admin(db_password, user_id, "head_moderator")
        assert_single_salary(secret_key, user_id, expected)
        print("PASS: General Supervisor remains eligible without a duplicate payment")
        return 0
    except BaseException as exc:  # cleanup must run for failures and cancellation
        primary_error = exc
        raise
    finally:
        if user_id:
            cleanup_errors: list[BaseException] = []
            try:
                delete_salary_test_audit(db_password, user_id)
            except BaseException as cleanup_error:
                cleanup_errors.append(cleanup_error)
            try:
                delete_user(secret_key, user_id)
            except BaseException as cleanup_error:
                cleanup_errors.append(cleanup_error)
            try:
                delete_profile_if_present(secret_key, user_id)
            except BaseException as cleanup_error:
                cleanup_errors.append(cleanup_error)
            if cleanup_errors:
                print(f"CLEANUP FAIL: {cleanup_errors[0]}")
                if primary_error is None:
                    raise cleanup_errors[0]
            else:
                print("PASS: disposable salary account and test audit entries deleted")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except CheckError as exc:
        print(f"FAIL: {exc}")
        raise SystemExit(1)
