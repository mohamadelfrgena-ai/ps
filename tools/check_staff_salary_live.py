#!/usr/bin/env python3
"""Verify immediate and monthly staff salary behavior with a disposable user.

The check runs only in protected CI against the reviewed production project. It
creates one confirmed temporary account, records its starting AC balance,
assigns Moderator through the same profiles.role update used by the admin RPC,
and proves that exactly 1500 AC is credited once for the current Tripoli month.
It then changes roles again in the same month and proves that neither the wallet
nor the salary ledger is credited twice. The temporary account is always
removed, and no identifier or credential is printed.
"""

from __future__ import annotations

import secrets
import urllib.parse
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


def set_role(secret_key: str, user_id: str, role: str) -> None:
    service_request(
        "PATCH",
        "/rest/v1/profiles?id=eq." + encoded_id(user_id),
        secret_key,
        {"role": role},
    )


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
    import os

    configured_url = os.environ.get("SUPABASE_URL", EXPECTED_URL).rstrip("/")
    if configured_url != EXPECTED_URL:
        raise CheckError(f"Refusing unexpected Supabase URL: {configured_url}")

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
        set_role(secret_key, user_id, "moderator")
        expected = baseline + EXPECTED_AMOUNT
        assert_single_salary(secret_key, user_id, expected)
        print("PASS: assigning Moderator immediately credited exactly 1500 AC")

        set_role(secret_key, user_id, "user")
        set_role(secret_key, user_id, "moderator")
        assert_single_salary(secret_key, user_id, expected)
        print("PASS: reassigning Moderator in the same month did not pay twice")

        set_role(secret_key, user_id, "head_moderator")
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
                print("PASS: disposable salary account deleted")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except CheckError as exc:
        print(f"FAIL: {exc}")
        raise SystemExit(1)
