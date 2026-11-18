#!/usr/bin/env python3
"""Static guard for the shared plan-wide AC pricing contract.

This verifies source invariants only. It does not claim that a migration was applied
or that Android / PlayStation binaries ran successfully.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


sql = read("supabase/52_plan_ac_discounts.sql")
low = sql.lower()
require("create table if not exists public.plan_ac_pricing" in low, "pricing table missing")
require("check (plan in ('epic', 'legendary'))" in low, "paid-plan constraint missing")
require("public.plan_ac_pricing_snapshot()" in low, "public snapshot RPC missing")
require("'server_now_epoch_ms'" in low and "'sale_ends_at_epoch_ms'" in low, "server-time contract missing")
require("public.admin_set_plan_ac_pricing" in low, "admin pricing RPC missing")
require("not public.is_admin(v_uid)" in low, "admin server authorization missing")
require("alter publication supabase_realtime add table public.plan_ac_pricing" in low, "Realtime publication missing")
require("create or replace function public.ac_unlock_content(p_item_id uuid)" in low, "item-only purchase RPC missing")
require("for update" in low and "for share" in low, "purchase locks missing")
require("on conflict (request_key) do nothing" in low, "idempotent unlock missing")
require("public.is_weekly_free_epic(p_item_id)" in low, "Thursday Epic gift regression")
require("public.effective_plan(v_uid)" in low, "subscription entitlement regression")

purchase_match = re.search(
    r"create or replace function public\.ac_unlock_content\(p_item_id uuid\)(.*?)revoke all on function public\.plan_ac_pricing_snapshot",
    low,
    re.S,
)
require(purchase_match is not None, "could not isolate purchase function")
purchase = purchase_match.group(1)
require("p_price" not in purchase and "p_sale" not in purchase, "client price parameter leaked into purchase")
require("v_now < v_sale_ends" in purchase, "server-side strict offer expiry missing")

builder = read("tools/build_all_in_one_sql.py")
require('"52_plan_ac_discounts.sql"' in builder, "migration 52 absent from all-in-one order")
all_in_one = read("supabase/00_ALL_IN_ONE.sql")
section = "-- ##  القسم: 52_plan_ac_discounts.sql"
require(section in all_in_one, "all-in-one was not regenerated")
require(all_in_one.rfind("-- ##  القسم:") == all_in_one.rfind(section), "migration 52 is not last in all-in-one")
require(all_in_one.rstrip().endswith("select public.plan_ac_pricing_snapshot() as plan_ac_pricing;"), "migration 52 tail is incomplete")

models = read("android/app/src/main/java/com/addonstation/mobile/data/model/Models.kt")
repo = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ContentRepository.kt")
admin_repo = read("android/app/src/main/java/com/addonstation/mobile/data/repo/AdminRepository.kt")
admin_ui = read("android/app/src/main/java/com/addonstation/mobile/ui/admin/AdminScreens.kt")
store_ui = read("android/app/src/main/java/com/addonstation/mobile/ui/store/StorePhoneDesign.kt")
nav = read("android/app/src/main/java/com/addonstation/mobile/ui/nav/AppNav.kt")
require("data class PlanAcPricing" in models and "estimatedServerNowEpochMs" in models, "Android monotonic model missing")
require("plan_ac_pricing_snapshot" in read("android/app/src/main/java/com/addonstation/mobile/data/repo/PlanAcPricingSupport.kt"), "Android snapshot parser missing")
require("15_000L" in repo and "decoratePlanAcPricing" in repo, "Android pricing refresh/decorator missing")
require("admin_set_plan_ac_pricing" in admin_repo, "Android admin write RPC missing")
require("AdminPlanAcPricingScreen" in admin_ui and "Africa/Tripoli" in admin_ui, "Libya-time Admin screen missing")
require("ADMIN_PLAN_AC_PRICING" in nav, "Admin pricing route missing")
require("acAlternativeAvailable" in store_ui and "item.effectiveAcPrice" in store_ui, "AC alternative gate missing")
mode_start = store_ui.index("val mode = when")
require(store_ui.index("unlocked -> DetailMode.FREE", mode_start) < store_ui.index("acAlternativeAvailable -> DetailMode.PAID_AC", mode_start), "permanent Android unlock is gated again")
require(
    store_ui.index("acAlternativeAvailable -> DetailMode.PAID_AC", mode_start)
    < store_ui.index("access is AccessState.RequiresLegendary", mode_start),
    "subscription gate still hides AC alternative",
)
require("TextDecoration.LineThrough" in store_ui and "phone_item_ac_offer_countdown" in store_ui, "old/new price or countdown missing")
require('params("p_item_id" to p(itemId))' in repo, "Android purchase sends more than item id")

for folder in ["values", "values-ar", "values-fr", "values-tr", "values-es", "values-ru", "values-pt", "values-zh"]:
    strings = read(f"android/app/src/main/res/{folder}/strings.xml")
    require("admin_plan_ac_pricing_title" in strings, f"pricing strings missing in {folder}")
    require("phone_item_ac_offer_countdown" in strings, f"countdown string missing in {folder}")

ps_header = read("ps4-addon-test/src/supabase_client.hpp")
ps_client = read("ps4-addon-test/src/supabase_client.cpp")
ps_main = read("ps4-addon-test/src/main.cpp")
ps_screens = read("ps4-addon-test/src/screens.cpp")
ps_tests = read("ps4-addon-test/tests/test_installer.cpp")
require("struct PlanAcPrice" in ps_header and "serverNowEpochMs" in ps_header, "PlayStation pricing model missing")
require('supabaseRpc(cfg, "plan_ac_pricing_snapshot", "{}")' in ps_client, "PlayStation snapshot fetch missing")
require("applyPlanAcPricing" in ps_client and "estimatedServerNow" in ps_client, "PlayStation overlay missing")
require("refreshPlanPricing(true)" in ps_main and "std::chrono::seconds(5)" in ps_main, "five-second PlayStation polling missing")
require("acSaleRemainingMs" in ps_screens and "SDL_GetTicks()" in ps_screens, "monotonic PlayStation expiry missing")
require("callAcUnlock(gCfg, it.id, gSession.accessToken)" in ps_main, "PlayStation purchase is not item-only")
require("acSaleCountdown(it)" in ps_screens and "acPriceOld" in ps_screens, "PlayStation old/new/countdown UI missing")
require("applyPlanAcPricing(items, prices, 2'001)" in ps_tests and "saleAcPrice == -1" in ps_tests, "PlayStation expiry test missing")

handoff = read("docs/PS4-PS5-Plan-AC-Pricing-Handoff.md")
for phrase in [
    "plan_ac_pricing_snapshot",
    "every **5 seconds**",
    "Realtime invalidation",
    "monotonic",
    "only `p_item_id`",
    "Error handling",
    "Acceptance test matrix",
]:
    require(phrase in handoff, f"handoff section missing: {phrase}")

print("Plan-wide AC pricing static guard passed")
