#!/usr/bin/env python3
"""Static contract guard for General Supervisor, staff salary, and Thursday Epic gift."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        errors.append(f"missing file: {path}")
        return ""
    return file.read_text(encoding="utf-8")


def require(source: str, needle: str, label: str) -> None:
    if needle not in source:
        errors.append(f"missing {label}: {needle}")


migration = text("supabase/50_staff_salary_and_weekly_epic.sql")
require(migration, "'head_moderator'", "General Supervisor database role")
require(migration, "when 'head_moderator' then 4", "General Supervisor rank")
require(migration, "role in ('admin', 'head_moderator', 'moderator')", "staff predicate")
require(migration, "chat_can_moderate_channel", "server room moderation function")
require(migration, "chat_my_moderation_scope", "server moderation scope function")
require(migration, "where role in ('moderator', 'head_moderator')", "salary eligibility")
require(migration, "'{\"enabled\":true,\"amount\":1500}'::jsonb", "default salary configuration")
require(migration, "unique (user_id, pay_month)", "monthly salary idempotency")
require(migration, "'salary'", "salary AC transaction kind")
require(migration, "claim_moderator_salary", "safe salary fallback RPC")
require(migration, "process_moderator_salaries", "scheduled salary processor")
require(migration, "staff_salary_on_role_assignment", "immediate salary role trigger")
require(migration, "after update of role on public.profiles", "role-assignment trigger event")
require(migration, "select public.process_moderator_salaries() as initial_salary_run", "initial salary catch-up")
require(migration, "Africa/Tripoli", "Tripoli calendar authority")
require(migration, "extract(isodow from v_local_date)::integer <> 4", "Thursday gate")
require(migration, "md5(c.id::text", "deterministic weekly randomization")
require(migration, "public.is_weekly_free_epic(p_item_id)", "server-side weekly access decision")
require(migration, "plan = 'epic'", "Epic-only candidate filter")
require(migration, "content_type = 'addon'", "addon-only candidate filter")
if re.search(r"update\s+public\.content_items\s+set\s+plan", migration, re.I | re.S):
    errors.append("weekly gift must not mutate content_items.plan")

models = text("android/app/src/main/java/com/addonstation/mobile/data/model/Models.kt")
require(models, 'role == "head_moderator"', "General Supervisor Kotlin staff role")
require(models, "val weeklyFree: Boolean = false", "weekly gift model flag")
require(models, "val originalPlan: String? = null", "original Epic plan preservation")

content_repo = text("android/app/src/main/java/com/addonstation/mobile/data/repo/ContentRepository.kt")
require(content_repo, 'rpc("current_weekly_free_epic")', "weekly gift RPC lookup")
require(content_repo, 'plan = "free"', "weekly gift UI decoration")
require(content_repo, "weeklyFreeEndsAt", "Thursday expiry handling")

ac_repo = text("android/app/src/main/java/com/addonstation/mobile/data/repo/AcRepository.kt")
require(ac_repo, 'rpc("claim_moderator_salary"', "AC wallet salary fallback")
profile_repo = text("android/app/src/main/java/com/addonstation/mobile/data/repo/ProfileRepository.kt")
require(profile_repo, 'rpc("claim_moderator_salary"', "profile wallet salary fallback")

admin = text("android/app/src/main/java/com/addonstation/mobile/ui/admin/AdminScreens.kt")
require(admin, '"head_moderator" to stringResource(R.string.role_head_moderator_plain)', "admin role assignment")
require(admin, "updateModeratorSalary", "admin salary controls")

chat = text("android/app/src/main/java/com/addonstation/mobile/data/repo/ChatRepository.kt")
require(chat, '"admin", "head_moderator"', "all-room client moderation")

sql_core = text("supabase/02_functions_core.sql")
admin_match = re.search(
    r"create or replace function public\.is_admin\(.*?\n\$\$;",
    sql_core,
    re.I | re.S,
)
if not admin_match or "= 'admin'" not in admin_match.group(0) or "head_moderator" in admin_match.group(0):
    errors.append("is_admin must remain strictly admin-only")

locale_dirs = ["values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"]
required_strings = {
    "role_head_moderator_plain",
    "badge_head_moderator",
    "grp_head_moderators",
    "admin_moderator_salary",
    "admin_moderator_salary_desc",
    "admin_salary_amount",
    "admin_salary_saved",
    "admin_salary_invalid_amount",
    "weekly_epic_free",
    "ac_kind_salary",
}
for locale in locale_dirs:
    strings = text(f"android/app/src/main/res/{locale}/strings_spec_additions.xml")
    missing = sorted(key for key in required_strings if f'name="{key}"' not in strings)
    if missing:
        errors.append(f"{locale} missing strings: {', '.join(missing)}")

if errors:
    print("Staff rewards guard failed:")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("Staff rewards guard passed: role, salary, Thursday gift, access, and 8 locales are wired.")
