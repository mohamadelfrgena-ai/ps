#!/usr/bin/env python3
"""Static regression guard for validated profile and asset writes."""

from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "supabase/01_schema.sql"
FUNCTIONS = ROOT / "supabase/02_functions_core.sql"
GRANTS = ROOT / "supabase/11_grants.sql"
BUNDLE = ROOT / "supabase/00_ALL_IN_ONE.sql"
MIGRATION = ROOT / "supabase/44_fix_profile_asset_guard.sql"

PROTECTED_COLUMNS = (
    "role",
    "plan",
    "plan_expires_at",
    "user_level",
    "username",
    "equipped_item_id",
    "avatar_url",
    "banner_url",
)
TRUSTED_FLAG = "perform set_config('app.allow_profile_write', '1', true);"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"profile asset guard: {message}")


def function_body(source: str, signature: str, next_signature: str | None = None) -> str:
    require(signature in source, f"missing function {signature}")
    body = source.split(signature, 1)[1]
    if next_signature and next_signature in body:
        body = body.split(next_signature, 1)[0]
    return body


def main() -> None:
    schema = SCHEMA.read_text(encoding="utf-8")
    functions = FUNCTIONS.read_text(encoding="utf-8")
    grants = GRANTS.read_text(encoding="utf-8")
    bundle = BUNDLE.read_text(encoding="utf-8")
    migration = MIGRATION.read_text(encoding="utf-8")

    guard = function_body(schema, "create or replace function public.guard_profile_update()", "drop trigger if exists trg_guard_profile_update")
    require("current_setting('app.allow_profile_write', true)" in guard,
            "guard has no transaction-local trusted-RPC bypass")
    for column in PROTECTED_COLUMNS:
        require(f"new.{column}" in guard and f"old.{column}" in guard,
                f"guard no longer protects {column}")

    set_asset = function_body(
        functions,
        "create or replace function public.set_profile_asset",
        "create or replace function public.update_my_profile",
    )
    update_profile = function_body(
        functions,
        "create or replace function public.update_my_profile",
        "create or replace function public.set_my_online_status",
    )
    require(TRUSTED_FLAG in set_asset,
            "set_profile_asset cannot pass the protected-column trigger")
    require(TRUSTED_FLAG in update_profile,
            "update_my_profile cannot pass the protected username trigger")
    require("v_uid       uuid := auth.uid()" in set_asset,
            "asset RPC no longer binds writes to the authenticated user")
    require("public.plan_rank(v_plan) < public.plan_rank(v_asset.required_plan)" in set_asset,
            "asset RPC no longer checks plan rank")
    require("v_asset.required_plan = 'admin' and not v_is_admin" in set_asset,
            "asset RPC no longer enforces admin-only assets")

    # UPDATE(column-list) grants are the database boundary that keeps the bypass
    # reachable only through validated SECURITY DEFINER functions.
    grant_match = re.search(
        r"grant\s+update\s*\((.*?)\)\s*on\s+public\.profiles\s+to\s+authenticated\s*;",
        grants,
        flags=re.IGNORECASE | re.DOTALL,
    )
    require(grant_match is not None, "column-limited authenticated profile grant is missing")
    granted_columns = {
        token.strip().lower()
        for token in grant_match.group(1).split(",")
        if token.strip()
    }
    for column in ("username", "avatar_url", "banner_url", "equipped_item_id"):
        require(column not in granted_columns,
                f"authenticated users were granted direct UPDATE on {column}")
    for safe_column in ("display_name", "bio", "custom_status", "show_online_status", "country", "last_seen_at"):
        require(safe_column in granted_columns,
                f"safe direct profile column grant disappeared: {safe_column}")

    require(migration.count(TRUSTED_FLAG) == 2,
            "focused migration must update both trusted profile RPCs")
    require("raise exception 'forbidden_profile_column'" in migration,
            "focused migration no longer preserves the profile guard")
    require(bundle.count(TRUSTED_FLAG) >= 3,
            "all-in-one bundle is not synchronized with trusted RPC writes")
    require("current_setting('app.allow_profile_write', true)" in bundle,
            "all-in-one bundle is not synchronized with the guard")

    print("profile asset guard: OK")


if __name__ == "__main__":
    main()
