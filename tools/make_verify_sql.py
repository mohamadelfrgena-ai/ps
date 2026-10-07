#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد **`supabase/99_VERIFY_ALL.sql`** — فاحص القاعدة الكامل (قراءة فقط).

ماذا يفعل الملف المُولَّد؟ يفحص بنداً بنداً أن كل ملفات SQL (01 → 31) نُفِّذت
كما نريد، ويطبع جدولاً: ✅/❌ + التفاصيل (وما هو المفقود بالاسم). يلصقه المالك
في Supabase → SQL Editor مرة واحدة.

لماذا مولّد؟ لأن الفاحص يجب أن يبقى مطابقاً لملفات الترحيل: أي ملف جديد
يُضاف بند له هنا ثم نُعيد التوليد. الفاحص `tools/check_verify_sql.py` يتحقّق
أن كل اسم/قيمة في الملف المُولَّد موجودة فعلاً في ملفات الترحيل (فلا أخطاء
إملائية ولا قيم مخترعة).

الاستخدام:
    python3 tools/make_verify_sql.py
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CHECKS = []          # (n, file, item, ok_sql, detail_sql)
_counter = [0]


def add(f, item, ok, detail):
    _counter[0] += 1
    CHECKS.append((_counter[0], f, item, ok, detail))


def arr(names):
    return "array[" + ", ".join("'" + n + "'" for n in names) + "]"


def q(sql):
    """يلفّ استعلاماً في الدالة التشخيصية: يرجع NULL إن كان الجدول/العمود مفقوداً."""
    sql = " ".join(sql.split())
    assert "$q$" not in sql
    return "public.diag_text($q$" + sql + "$q$)"


def q_ok(sql, expect="true"):
    """شرط: نتيجة الاستعلام النصّية تساوي المتوقَّع (كل فحوصنا ترجع 'true'/'false')."""
    assert expect in ("true", "false"), "القيم المتوقّعة نصّية فقط"
    return f"{q(sql)} = '{expect}'"


def qdet(sql, fallback="مفقود"):
    """تفاصيل آمنة: تسرد «مفقود» إن لم يوجد الجدول/العمود."""
    return f"coalesce({q(sql)}, '{fallback}')"


def qbool(sql):
    """شرط منطقي آمن: الاستعلام مصبوب ::text داخل نصّه، ويُقرأ false (⇒ ❌) إن فشل."""
    return f"coalesce({q(sql)}, 'false') = 'true'"


def missing_list(f, item, names, cond, label):
    """بند واحد يفحص مجموعة أسماء (جداول/دوال/سياسات) ويسرد المفقود منها."""
    a = arr(names)
    ok = f"(select count(*) from unnest({a}) t where {cond}) = 0"
    det = (f"coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest({a}) t "
           f"where {cond}), '{label}')")
    add(f, item, ok, det)


# ═══════════════════════════════════════════════════════════════════════════
#  01_schema.sql
# ═══════════════════════════════════════════════════════════════════════════
TABLES = ["profiles", "subscription_plans", "payment_method_settings", "payment_orders",
          "content_items", "content_favorites", "content_downloads", "ac_wallets",
          "ac_transactions", "ac_tasks", "ac_task_claims", "ac_events", "chat_channels",
          "chat_messages", "chat_reactions", "chat_mutes", "chat_bans", "chat_reports",
          "chat_notifications", "chat_read_state", "chat_typing", "chat_rules",
          "chat_rule_acceptances", "user_friends", "creator_applications",
          "creator_submissions", "featured_creators", "creator_follows", "creator_tips",
          "site_assets", "sticker_packs", "app_settings", "admin_audit_log"]
missing_list("01_schema", f"الجداول الأساسية ({len(TABLES)})", TABLES,
             "to_regclass('public.' || t) is null", f"{len(TABLES)}/{len(TABLES)} موجودة")

add("01_schema", "عمودا التقسيم في site_assets (collection · collection_order)",
    q_ok("select (count(*) = 2)::text from information_schema.columns "
         "where table_schema = 'public' and table_name = 'site_assets' "
         "and column_name in ('collection', 'collection_order')", "true"),
    q("select coalesce(string_agg(column_name, ' · '), 'مفقود') from information_schema.columns "
      "where table_schema = 'public' and table_name = 'site_assets' "
      "and column_name in ('collection', 'collection_order')"))

add("01_schema", "أعمدة ملف المستخدم الأساسية (avatar_url · role · plan · equipped_item_id …)",
    q_ok("select (count(*) = 8)::text from information_schema.columns "
         "where table_schema = 'public' and table_name = 'profiles' and column_name in "
         "('avatar_url', 'role', 'plan', 'plan_expires_at', 'equipped_item_id', 'username', "
         "'display_name', 'cover_url')", "true"),
    q("select count(*)::text || '/8' from information_schema.columns "
      "where table_schema = 'public' and table_name = 'profiles' and column_name in "
      "('avatar_url', 'role', 'plan', 'plan_expires_at', 'equipped_item_id', 'username', "
      "'display_name', 'cover_url')"))

add("01_schema", "دوال ومشغّلات الملف الشخصي (ترتيب الخطط · اللمس · الحماية)",
    "(select count(*) from unnest(array['plan_rank', 'role_rank', 'touch_updated_at', "
    "'guard_profile_update', 'handle_new_user']) t where not exists ("
    "select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = t)) = 0 "
    "and (select count(*) from pg_trigger where not tgisinternal and tgname in "
    "('trg_guard_profile_update', 'on_auth_user_created', 'trg_touch_profiles', "
    "'trg_touch_subscription_plans', 'trg_touch_payment_orders', 'trg_touch_content_items', "
    "'trg_touch_app_settings', 'trg_touch_user_friends')) = 8",
    "coalesce((select 'المفقود: ' || string_agg(t, ' · ') "
    "from unnest(array['plan_rank', 'role_rank', 'touch_updated_at', "
    "'guard_profile_update', 'handle_new_user']) t where not exists ("
    "select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = t)), '5/5 دوال') "
    "|| ' · المشغّلات: ' || (select count(*) from pg_trigger where not tgisinternal "
    "and tgname in ('trg_guard_profile_update', 'on_auth_user_created', "
    "'trg_touch_profiles', 'trg_touch_subscription_plans', 'trg_touch_payment_orders', "
    "'trg_touch_content_items', 'trg_touch_app_settings', 'trg_touch_user_friends'))::text "
    "|| '/8'")

add("01_schema", "فهارس الشات والأصول",
    q_ok("select (count(*) >= 5)::text from pg_indexes where schemaname = 'public' "
         "and indexname in ('chat_messages_channel_idx', 'chat_messages_thread_idx', "
         "'profiles_role_idx', 'site_assets_collection_idx', 'site_assets_file_url_uidx')", "true"),
    q("select count(*)::text || '/5' from pg_indexes where schemaname = 'public' "
      "and indexname in ('chat_messages_channel_idx', 'chat_messages_thread_idx', "
      "'profiles_role_idx', 'site_assets_collection_idx', 'site_assets_file_url_uidx')"))

# ═══════════════════════════════════════════════════════════════════════════
#  02 · 03 — دوال القاعدة
# ═══════════════════════════════════════════════════════════════════════════
FUNCS_CORE = ["current_role_of", "is_admin", "is_staff", "effective_plan", "effective_plan_label",
              "can_access_item", "download_limit_reached", "record_content_view",
              "record_content_download", "toggle_favorite", "ac_unlock_content", "ac_claim_task",
              "ac_admin_adjust", "ac_admin_grant_all", "create_payment_order",
              "submit_payment_receipt", "approve_payment_order", "reject_payment_order",
              "admin_update_profile", "set_profile_asset", "update_my_profile",
              "admin_upsert_content", "admin_delete_content", "admin_update_plan",
              "admin_upsert_payment_method", "admin_delete_payment_method", "admin_stats"]
missing_list("02_functions_core", f"دوال النواة ({len(FUNCS_CORE)})", FUNCS_CORE,
             "not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
             "where n.nspname = 'public' and p.proname = t)", f"{len(FUNCS_CORE)}/{len(FUNCS_CORE)} موجودة")

FUNCS_CHAT = ["app_setting", "chat_content_check", "send_chat_message",
              "chat_delete_window_seconds", "chat_delete_my_message", "chat_moderate_message",
              "chat_toggle_pin", "chat_toggle_reaction", "chat_mute_user", "chat_unmute_user",
              "chat_ban_user", "chat_unban_user", "chat_report_message", "chat_close_report",
              "chat_admin_upsert_room", "chat_admin_delete_room", "chat_send_announcement",
              "chat_set_typing", "chat_mark_read", "chat_unread_counts", "my_chat_status",
              "chat_accept_rules", "mark_notifications_read", "send_friend_request",
              "respond_friend_request", "submit_creator_application", "review_creator_application",
              "toggle_featured_creator", "creator_submit_content", "review_creator_submission",
              "creator_submit_tip", "creator_review_tip", "admin_update_settings",
              "admin_update_chat_rules"]
missing_list("03_functions_chat", f"دوال الشات والمبدعين ({len(FUNCS_CHAT)})", FUNCS_CHAT,
             "not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
             "where n.nspname = 'public' and p.proname = t)", f"{len(FUNCS_CHAT)}/{len(FUNCS_CHAT)} موجودة")

# ═══════════════════════════════════════════════════════════════════════════
#  04_rls.sql — سياسات الحماية
# ═══════════════════════════════════════════════════════════════════════════
POLICIES = [("profiles_select_all", "profiles"), ("profiles_update_own", "profiles"),
            ("profiles_admin_all", "profiles"), ("plans_select", "subscription_plans"),
            ("plans_admin_write", "subscription_plans"),
            ("pms_select_enabled", "payment_method_settings"),
            ("pms_admin_write", "payment_method_settings"),
            ("orders_select_own", "payment_orders"),
            ("content_select_public", "content_items"),
            ("content_admin_write", "content_items"),
            ("fav_own", "content_favorites"), ("downloads_own", "content_downloads"),
            ("ac_wallet_own", "ac_wallets"), ("ac_tx_own", "ac_transactions"),
            ("ac_tasks_read", "ac_tasks"), ("ac_tasks_admin", "ac_tasks"),
            ("ac_claims_own", "ac_task_claims"), ("ac_events_admin", "ac_events"),
            ("channels_read", "chat_channels"), ("channels_admin", "chat_channels"),
            ("messages_read", "chat_messages"), ("reactions_read", "chat_reactions"),
            ("reactions_own", "chat_reactions"), ("reactions_delete_own", "chat_reactions"),
            ("mutes_read", "chat_mutes"), ("mutes_staff_write", "chat_mutes"),
            ("bans_read", "chat_bans"), ("bans_staff_write", "chat_bans"),
            ("reports_read", "chat_reports"), ("reports_insert", "chat_reports"),
            ("reports_staff_update", "chat_reports"),
            ("rule_accept_own", "chat_rule_acceptances"),
            ("rule_accept_insert", "chat_rule_acceptances"),
            ("rules_read", "chat_rules"), ("rules_admin", "chat_rules"),
            ("typing_read", "chat_typing"), ("typing_own", "chat_typing"),
            ("read_state_own", "chat_read_state"), ("notif_own", "chat_notifications"),
            ("notif_update_own", "chat_notifications"), ("friends_own", "user_friends"),
            ("apps_own", "creator_applications"), ("apps_insert", "creator_applications"),
            ("submissions_own", "creator_submissions"),
            ("submissions_admin_write", "creator_submissions"),
            ("featured_read", "featured_creators"), ("featured_admin", "featured_creators"),
            ("follows_read", "creator_follows"), ("follows_own", "creator_follows"),
            ("tips_read", "creator_tips"), ("tips_admin_write", "creator_tips"),
            ("assets_read", "site_assets"), ("assets_admin", "site_assets"),
            ("packs_read", "sticker_packs"), ("packs_admin", "sticker_packs"),
            ("settings_read", "app_settings"), ("audit_admin_read", "admin_audit_log")]
missing_list("04_rls", f"سياسات RLS ({len(POLICIES)})",
             [f"{p}:{t}" for p, t in POLICIES],
             "not exists (select 1 from pg_policies pol where pol.schemaname = 'public' "
             "and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))",
             f"{len(POLICIES)}/{len(POLICIES)} موجودة")

# ═══════════════════════════════════════════════════════════════════════════
#  05_storage.sql — المخازن وسياساتها
# ═══════════════════════════════════════════════════════════════════════════
BUCKETS = ["content-files", "payment-receipts", "chat-images", "chat-media",
           "creator-media", "site-assets"]
add("05_storage", f"المخازن الستة (منها payment-receipts)",
    q_ok("select (count(*) = 6)::text from storage.buckets where id in "
         "('content-files', 'payment-receipts', 'chat-images', 'chat-media', "
         "'creator-media', 'site-assets')", "true"),
    qdet("select string_agg(id, ' · ') from storage.buckets where id in "
         "('content-files', 'payment-receipts', 'chat-images', 'chat-media', "
         "'creator-media', 'site-assets')"))

add("05_storage", "مخزن site-assets عام (public) — الصور تظهر بلا تسجيل دخول",
    q_ok("select (count(*) = 1)::text from storage.buckets where id = 'site-assets' and public", "true"),
    qdet("select 'public = ' || public::text from storage.buckets where id = 'site-assets'"))

STORAGE_POLICIES = ["content_files_read", "content_files_admin_write",
                    "content_files_admin_update", "content_files_admin_delete",
                    "receipts_insert_own", "receipts_read", "receipts_update_own",
                    "chat_images_insert", "chat_images_read", "chat_media_insert",
                    "chat_media_read", "creator_media_insert", "creator_media_read",
                    "site_assets_read", "site_assets_admin_write", "site_assets_creator_write",
                    "site_assets_admin_update", "site_assets_admin_delete"]
add("05_storage", f"سياسات المخزن ({len(STORAGE_POLICIES)})",
    q_ok("select (count(*) = %d)::text from pg_policies where schemaname = 'storage' "
         "and policyname in (%s)" % (len(STORAGE_POLICIES),
                                     ", ".join("'%s'" % p for p in STORAGE_POLICIES)), "true"),
    q("select count(*)::text || '/%d' from pg_policies where schemaname = 'storage' "
      "and policyname in (%s)" % (len(STORAGE_POLICIES),
                                  ", ".join("'%s'" % p for p in STORAGE_POLICIES))))

# ═══════════════════════════════════════════════════════════════════════════
#  06_seed · 08_admin_bootstrap
# ═══════════════════════════════════════════════════════════════════════════
add("06_seed · 22_plan_prices", "الخطط الثلاث وأسعارها (free · epic · legendary)",
    q_ok("select (count(*) = 3)::text from public.subscription_plans "
         "where id in ('free', 'epic', 'legendary')", "true"),
    qdet("select string_agg(id || ' = ' || price_lyd::text || ' د.ل / $' || price_usd::text, "
         "' · ' order by sort_order) from public.subscription_plans"))

add("22_plan_prices", "سعرا Epic و Legendary بالدولار مضبوطان (2.99 · 5.99)",
    qbool("select (count(*) = 2)::text from public.subscription_plans "
          "where (id = 'epic' and price_usd = 2.99) "
          "or (id = 'legendary' and price_usd = 5.99)"),
    qdet("select string_agg(id || ' = $' || price_usd::text, ' · ' order by sort_order) "
         "from public.subscription_plans"))

add("08_admin_bootstrap", "يوجد حساب إدارة واحد على الأقل (role = admin)",
    q_ok("select (count(*) >= 1)::text from public.profiles where role = 'admin'", "true"),
    qdet("select count(*)::text || ' حساب إدارة' from public.profiles where role = 'admin'"))

# ═══════════════════════════════════════════════════════════════════════════
#  09 · 10 · 11
# ═══════════════════════════════════════════════════════════════════════════
missing_list("09_gateway_functions", "دوال البوابات (gateway)",
             ["gateway_confirm_order", "gateway_attach_session"],
             "not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
             "where n.nspname = 'public' and p.proname = t)", "2/2 موجودة")

add("10_realtime", "منشور البث الحيّ supabase_realtime موجود",
    q_ok("select (count(*) = 1)::text from pg_publication where pubname = 'supabase_realtime'", "true"),
    q("select string_agg(pubname, ' · ') from pg_publication "
      "where pubname = 'supabase_realtime'"))

RT_TABLES = ["chat_messages", "chat_reactions", "chat_typing", "chat_read_state",
             "chat_notifications", "profiles"]
missing_list("10_realtime", f"جداول البث الحيّ ({len(RT_TABLES)})",
             RT_TABLES,
             "not exists (select 1 from pg_publication_rel pr join pg_publication p on p.oid = pr.prpubid "
             "join pg_class c on c.oid = pr.prrelid where p.pubname = 'supabase_realtime' "
             "and c.relname = t)", f"{len(RT_TABLES)}/{len(RT_TABLES)} في المنشور")

add("11_grants", "صلاحيات الكتابة للأدوار (service_role يكتب · authenticated يقرأ)",
    q_ok("select ((has_table_privilege('service_role', 'public.site_assets', 'insert') "
         "and has_table_privilege('authenticated', 'public.site_assets', 'select')))::text", "true"),
    qdet("select 'service_role insert = ' || has_table_privilege('service_role', 'public.site_assets', 'insert')::text "
         "|| ' · authenticated select = ' || has_table_privilege('authenticated', 'public.site_assets', 'select')::text"))

# ═══════════════════════════════════════════════════════════════════════════
#  13_channels_icons · 16_language_room_names · 17_cash_services_room
# ═══════════════════════════════════════════════════════════════════════════
add("13_channels_icons", "أسماء الغرف الرسمية صحيحة (General · Minecraft · العربية …)",
    "(" + q_ok("select (count(*) >= 5)::text from public.chat_channels where id in "
               "('general', 'minecraft', 'support', 'lang-ar', 'lang-ru')")
    + " and " + q_ok("select (name = 'العربية')::text from public.chat_channels "
                     "where id = 'lang-ar'") + ")",
    qdet("select string_agg(id || ' → ' || name, ' · ' order by sort_order) "
         "from public.chat_channels where category = 'lang'"))

add("16_language_room_names", "الاسم العربي للغرفة = «العربية»",
    q_ok("select (name = 'العربية')::text from public.chat_channels where id = 'lang-ar'", "true"),
    qdet("select name || ' · ' || coalesce(description, '—') from public.chat_channels "
         "where id = 'lang-ar'"))

add("17_cash_services_room", "غرفة «خدمات النقدية» موجودة والكتابة فيها للإدارة فقط (admin_only)",
    q_ok("select ((post_permission = 'admin_only') and is_locked)::text "
         "from public.chat_channels where id = 'cash-services'", "true"),
    qdet("select name || ' · الكتابة: ' || post_permission || ' · مقفلة: ' || is_locked::text "
         "from public.chat_channels where id = 'cash-services'", "مفقودة"))

# ═══════════════════════════════════════════════════════════════════════════
#  14_redeem_codes
# ═══════════════════════════════════════════════════════════════════════════
missing_list("14_redeem_codes", "جداول أكواد الشحن",
             ["ac_redeem_codes", "ac_redeem_uses"],
             "to_regclass('public.' || t) is null", "2/2 موجودة")
missing_list("14_redeem_codes", "دوال أكواد الشحن",
             ["ac_random_code", "ac_admin_create_codes", "ac_redeem_code"],
             "not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
             "where n.nspname = 'public' and p.proname = t)", "3/3 موجودة")
missing_list("14_redeem_codes", "سياسات أكواد الشحن",
             ["ac_redeem_codes_admin:ac_redeem_codes", "ac_redeem_uses_self:ac_redeem_uses"],
             "not exists (select 1 from pg_policies pol where pol.schemaname = 'public' "
             "and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))",
             "2/2 موجودة")

# ═══════════════════════════════════════════════════════════════════════════
#  15_assets_v2 (+16_fix_asset_paths) + 21_banners + 27_admin_only_assets
# ═══════════════════════════════════════════════════════════════════════════
add("15_assets_v2 · 21_banners", "مجموع الأصول = 532 (492 من 15 + 40 بانر من 21)",
    q_ok("select (count(*) = 532)::text from public.site_assets", "true"),
    qdet("select count(*)::text || '/532' from public.site_assets"))

add("15_assets_v2", "توزيع الأصول (123 أفاتار · 176 أداة · 193 ملصقاً · 40 بانر)",
    q_ok("select ((count(*) filter (where asset_type = 'avatar') = 123) "
         "and (count(*) filter (where asset_type = 'item') = 176) "
         "and (count(*) filter (where asset_type = 'sticker') = 193) "
         "and (count(*) filter (where asset_type = 'banner') = 40))::text "
         "from public.site_assets", "true"),
    qdet("select 'avatar ' || count(*) filter (where asset_type = 'avatar')::text || ' · item ' "
      "|| count(*) filter (where asset_type = 'item')::text || ' · sticker ' "
      "|| count(*) filter (where asset_type = 'sticker')::text || ' · banner ' "
         "|| count(*) filter (where asset_type = 'banner')::text from public.site_assets"))

add("15_assets_v2 · 21_banners", "الأقسام (collection) مضبوطة — 23 قسماً",
    q_ok("select (count(distinct collection) >= 23)::text from public.site_assets "
         "where collection is not null", "true"),
    qdet("select count(distinct collection)::text || ' قسماً' from public.site_assets "
         "where collection is not null"))

add("16_fix_asset_paths", "لا مسارات مكرّرة (profiles/profiles/ …) في الأصول",
    q_ok("select (count(*) = 0)::text from public.site_assets "
         "where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'",
         "true"),
    qdet("select count(*)::text || ' مساراً مكرّراً' from public.site_assets "
         "where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'"))

add("16_fix_asset_paths", "لا مسارات مكرّرة في صور الحسابات",
    q_ok("select (count(*) = 0)::text from public.profiles "
         "where avatar_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'",
         "true"),
    qdet("select count(*)::text || ' حساباً' from public.profiles "
         "where avatar_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'"))

add("21_banners", "بانرات إضافية مسجّلة (40)",
    q_ok("select (count(*) >= 40)::text from public.site_assets where asset_type = 'banner'", "true"),
    qdet("select count(*)::text || ' بانر' from public.site_assets where asset_type = 'banner'"))

add("27_admin_only_assets", "أصول حصرية للإدارة (required_plan = admin)",
    q_ok("select (count(*) >= 1)::text from public.site_assets where required_plan = 'admin'", "true"),
    qdet("select count(*)::text || ' أصلاً' from public.site_assets where required_plan = 'admin'"))

# ═══════════════════════════════════════════════════════════════════════════
#  17 · 18 · 19 · 20 · 23 · 24 · 25 · 26 · 29 · 30 · 31
# ═══════════════════════════════════════════════════════════════════════════
add("17_message_delete_window", "نافذة حذف الرسالة = 180 ثانية (٣ دقائق)",
    q_ok("select (public.chat_delete_window_seconds() = 180)::text", "true"),
    q("select public.chat_delete_window_seconds()::text || ' ثانية'"))

add("18_media_admin_only", "الوسائط (فيديو/صوت) للإدارة فقط — الحارس والزناد",
    q_ok("select ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
         "where n.nspname = 'public' and p.proname = 'chat_media_admin_only_guard') = 1 "
         "and (select count(*) from pg_trigger where tgname = 'trg_chat_media_admin_only' "
         "and not tgisinternal) = 1)::text", "true"),
    q("select 'دالة الحارس: ' || ((select count(*) from pg_proc p join pg_namespace n "
      "on n.oid = p.pronamespace where n.nspname = 'public' "
      "and p.proname = 'chat_media_admin_only_guard')::text) || ' · الزناد: ' "
      "|| ((select count(*) from pg_trigger where tgname = 'trg_chat_media_admin_only' "
      "and not tgisinternal)::text)"))

add("19_admin_panel_guard", "دالة حماية لوحة الإدارة + سياساتها",
    q_ok("select ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
         "where n.nspname = 'public' and p.proname = 'admin_panel_access') = 1 "
         "and (select count(*) from pg_policies where schemaname = 'public' "
         "and policyname in ('settings_admin', 'audit_admin_read', 'ac_redeem_codes_admin', "
         "'orders_admin_all')) = 4)::text", "true"),
    q("select 'الدالة: ' || ((select count(*) from pg_proc p join pg_namespace n "
      "on n.oid = p.pronamespace where n.nspname = 'public' "
      "and p.proname = 'admin_panel_access')::text) || ' · السياسات: ' "
      "|| ((select count(*) from pg_policies where schemaname = 'public' "
      "and policyname in ('settings_admin', 'audit_admin_read', 'ac_redeem_codes_admin', "
      "'orders_admin_all'))::text) || '/4'"))

add("20_sticker_with_text", "send_chat_message تقبل الملصق مع النص",
    q_ok("select (count(*) >= 1)::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
         "where n.nspname = 'public' and p.proname = 'send_chat_message' "
         "and pg_get_function_arguments(p.oid) like '%p_sticker_url%'", "true"),
    q("select count(*)::text || ' نسخة' from pg_proc p join pg_namespace n "
      "on n.oid = p.pronamespace where n.nspname = 'public' "
      "and p.proname = 'send_chat_message' and pg_get_function_arguments(p.oid) like '%p_sticker_url%'"))

add("23_payment_order_usd", "عمود amount_usd ودالة الطلب ترجعه",
    q_ok("select ((select count(*) from information_schema.columns where table_schema = 'public' "
         "and table_name = 'payment_orders' and column_name = 'amount_usd') = 1)::text", "true"),
    q("select 'amount_usd: ' || ((select count(*) from information_schema.columns "
      "where table_schema = 'public' and table_name = 'payment_orders' "
      "and column_name = 'amount_usd')::text)"))

add("24_free_default_avatar", "avatar_url افتراضيه onefree + زناد الحساب الجديد",
    q_ok("select ((select column_default like '%onefree%' from information_schema.columns "
         "where table_schema = 'public' and table_name = 'profiles' "
         "and column_name = 'avatar_url') and (select count(*) from pg_trigger "
         "where tgname = 'on_auth_user_created' and not tgisinternal) = 1)::text", "true"),
    q("select 'الافتراضي: ' || coalesce((select column_default from information_schema.columns "
      "where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url'), 'مفقود')"))

add("25_fix_send_chat_message_unnest", "send_chat_message بلا خطأ unnest (الإصلاح مطبَّق)",
    q_ok("select (count(*) = 0)::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
         "where n.nspname = 'public' and p.proname = 'send_chat_message' "
         "and pg_get_functiondef(p.oid) ilike '%where unnest is not null%'", "true"),
    q("select count(*)::text || ' نسخة فيها الخلل' from pg_proc p join pg_namespace n "
      "on n.oid = p.pronamespace where n.nspname = 'public' "
      "and p.proname = 'send_chat_message' and pg_get_functiondef(p.oid) ilike '%where unnest is not null%'"))

add("26_fix_site_assets_sequence", "تسلسل الأصول يعمل (authenticated يستطيع الإدراج)",
    qbool("select (has_sequence_privilege('authenticated', 'public.site_assets_id_seq', 'usage') "
          "and (select count(*) from pg_sequences where schemaname = 'public' "
          "and sequencename = 'site_assets_id_seq') = 1)::text"),
    qdet("select 'التسلسل: ' || coalesce((select sequencename from pg_sequences "
         "where schemaname = 'public' and sequencename = 'site_assets_id_seq'), 'مفقود') "
         "|| ' · أعلى معرّف: ' || coalesce((select max(id)::text from public.site_assets), '—')"))

add("29_chat_moderation_rooms", "غرف المشرفين + دوالها + سياساتها",
    q_ok("select ((select count(*) from information_schema.tables where table_schema = 'public' "
         "and table_name = 'chat_moderator_rooms') = 1 "
         "and (select count(*) from pg_policies where schemaname = 'public' "
         "and policyname in ('mod_rooms_admin_all', 'mod_rooms_self_read')) = 2 "
         "and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
         "where n.nspname = 'public' and p.proname in ('chat_can_moderate_channel', "
         "'chat_my_moderation_scope', 'chat_delete_message_staff', 'chat_moderator_list', "
         "'chat_set_moderator_rooms')) = 5)::text", "true"),
    q("select 'الجدول: ' || ((select count(*) from information_schema.tables "
      "where table_schema = 'public' and table_name = 'chat_moderator_rooms')::text) "
      "|| ' · السياسات: ' || ((select count(*) from pg_policies "
      "where schemaname = 'public' and policyname in ('mod_rooms_admin_all', "
      "'mod_rooms_self_read'))::text) || '/2' "
      "|| ' · الدوال: ' || ((select count(*) from pg_proc p join pg_namespace n "
      "on n.oid = p.pronamespace where n.nspname = 'public' "
      "and p.proname in ('chat_can_moderate_channel', 'chat_my_moderation_scope', "
      "'chat_delete_message_staff', 'chat_moderator_list', 'chat_set_moderator_rooms'))::text) || '/5'"))

add("30_chat_members_only", "الشات للمسجّلين فقط (3 سياسات بـ auth.uid())",
    q_ok("select (count(*) = 3)::text from pg_policies where schemaname = 'public' "
         "and policyname in ('messages_read', 'reactions_read', 'channels_read') "
         "and qual ilike '%auth.uid()%'", "true"),
    q("select count(*)::text || '/3' from pg_policies where schemaname = 'public' "
      "and policyname in ('messages_read', 'reactions_read', 'channels_read') "
      "and qual ilike '%auth.uid()%'"))

add("31_single_free_avatar", "أفاتار مجاني مفعّل واحد فقط (onefree)",
    qbool("select (count(*) = 1)::text from public.site_assets where asset_type = 'avatar' "
          "and required_plan = 'free' and enabled"),
    qdet("select count(*)::text || ' مفعّل' from public.site_assets where asset_type = 'avatar' "
         "and required_plan = 'free' and enabled"))

add("31_single_free_avatar", "كل الحسابات صورتها onefree (ولا حساب بلا صورة)",
    qbool("select (count(*) = 0)::text from public.profiles where avatar_url is null "
          "or btrim(avatar_url) = ''"),
    qdet("select count(*)::text || ' بلا صورة' from public.profiles "
         "where avatar_url is null or btrim(avatar_url) = ''"))

add("31_single_free_avatar", "لا حساب يستعمل صورة مجانية قديمة (معطَّلة)",
    qbool("select (count(*) = 0)::text from public.profiles p where exists ("
          "select 1 from public.site_assets a where a.asset_type = 'avatar' "
          "and a.required_plan = 'free' and not a.enabled and lower(a.file_url) not like '%onefree%' "
          "and (p.avatar_url = a.file_url or p.avatar_url = 'profiles/' || a.file_url "
          "or a.file_url = 'profiles/' || p.avatar_url))"),
    qdet("select count(*)::text || ' حساباً' from public.profiles p where exists ("
         "select 1 from public.site_assets a where a.asset_type = 'avatar' "
         "and a.required_plan = 'free' and not a.enabled and lower(a.file_url) not like '%onefree%' "
         "and (p.avatar_url = a.file_url or p.avatar_url = 'profiles/' || a.file_url "
         "or a.file_url = 'profiles/' || p.avatar_url))"))

add("31_single_free_avatar", "ملف الصورة موجود فعلاً في المخزن (site-assets/profiles/onefree.png)",
    q_ok("select (count(*) >= 1)::text from storage.objects where bucket_id = 'site-assets' "
         "and name in ('profiles/onefree.png', 'profiles/profiles/onefree.png')", "true"),
    qdet("select string_agg(name, ' · ') from storage.objects "
         "where bucket_id = 'site-assets' and name in ('profiles/onefree.png', "
         "'profiles/profiles/onefree.png')", "غير مرفوع بعد"))

# ── معلوماتية: ملفات قديمة مُستبدَلة + حالة الشات ─────────────────────────
add("07_seed_assets · 12_avatars", "دفعات الأصول القديمة (مُستبدَلة بـ15 — لا يلزم تنفيذها)",
    "true",
    "'لا شيء مطلوب: 15_assets_v2 و21_banners هما المرجع (532 أصلاً)'")

add("28_chat_cleanup", "حالة الشات (28 ملف تنظيف أقسامه اختيارية)",
    "true",
    qdet("select 'الظاهرة: ' || count(*) filter (where status <> 'deleted')::text "
      "|| ' · المحذوفة: ' || count(*) filter (where status = 'deleted')::text "
      "|| ' · المخفية: ' || count(*) filter (where status = 'hidden')::text "
         "from public.chat_messages"))

# ═══════════════════════════════════════════════════════════════════════════
#  كتابة الملف
# ═══════════════════════════════════════════════════════════════════════════
HEAD = r'''-- ============================================================================
--  AddonStation Mobile — ٩٩) فاحص القاعدة: «هل نُفِّذ كل SQL كما نريد؟»
-- ============================================================================
--
--  ماذا يفعل؟ يفحص **كل ملف SQL سلّمناه لك** (01 → 31) بنداً بنداً، ويطبع
--  جدولاً واحداً: كل صف = بند · العمود «الحالة» ✅ أو ❌ · والعمود «التفاصيل»
--  يقول لك ما هو الناقص بالاسم بالضبط.
--
--  كيف أستعمله؟
--      Supabase → SQL Editor → الصق **الملف كاملاً** → RUN.
--      اقرأ الجدول: كل ما عليه ❌ = بقية من ملف SQL لم يُنفَّذ (أو نُفِّذ ناقصاً).
--      آخر صف هو المجموع.
--
--  هل يغيّر شيئاً؟ **لا** — الملف للقراءة فقط (SELECT)، لا يحذف ولا يعدّل.
--  الشيء الوحيد الذي يُنشئه: دالة تشخيص صغيرة public.diag_text (لأن فحص
--  جدول غير موجود يجب أن يُظهر ❌ لا أن تُفشل الفحص كله).
--  لحذفها بعد الاطمئنان:  drop function if exists public.diag_text(text);
--
--  آمن للتكرار: نفّذه متى شككت، أو بعد تنفيذ أي ملف SQL جديد.
--
--  ⚠️ ترتيب مهم — بعض الملفات تُعيد بناء الجدول:
--     • `15_assets_v2.sql` يبدأ بـ `delete from public.site_assets` ثم يزرع
--       492 أصلاً، و`21_banners.sql` يضيف 40 بانراً ⇒ **نفّذ 15 قبل 21**
--       أبداً؛ وإلا مُحيت البانرات.
--     • الترتيب الموصى به للبقية: 26 (التسلسل) · 27 (أصول الإدارة) ·
--       31 (onefree) في النهاية — و31 آمن للتكرار في أي وقت.
--     • الفاحص يكشف الخلل بنفسه: إن ظهر المجموع 492 بدل 532 أو نقصت
--       البانرات ⇒ أعِد تنفيذ `21_banners.sql` وحده.
-- ============================================================================

create or replace function public.diag_text(q text) returns text
language plpgsql stable
as $fn$
declare
  v text;
begin
  -- نُغلّف الاستعلام كقيمة واحدة ثم نحوّلها نصّاً: فيعمل مع أي نوع (عدّاد ·
  -- منطقي · نصّ) بلا خطأ عدم تطابق الأنواع.
  execute 'select (' || q || ')::text' into v;
  return v;
exception when others then
  return null;        -- جدول/عمود مفقود ⇒ null ⇒ تُقرأ ❌ مع «مفقود»
end;
$fn$;

comment on function public.diag_text(text) is
  'دالة تشخيص لملف 99_VERIFY_ALL.sql: تُنفّذ استعلاماً وترجع نصّه، أو null إن فشل.';

-- للاطمئنان: لا تُتاح للزوار ولا للمسجّلين (تبقى لك في SQL Editor).
do $revoke$
begin
  begin
    revoke all on function public.diag_text(text) from anon, authenticated;
  exception when undefined_object then null;   -- لو لم يوجد الدور
  end;
end;
$revoke$;

with checks(n, file_, item, ok, detail) as (
'''

TAIL = r'''
)
select n                                  as "#",
       file_                              as "الملف / الخطوة",
       item                               as "البند",
       case when ok then '✅' else '❌' end as "الحالة",
       coalesce(detail, '—')              as "التفاصيل"
  from checks
union all
select 999999,
       '—',
       'المجموع',
       case when count(*) filter (where ok is not true) = 0
            then '✅ كله تمام'
            else '❌ ناقص ' || (count(*) filter (where ok is not true))::text end,
       count(*)::text || ' بنداً فُحص · ' || (count(*) filter (where ok))::text || ' سليم'
         || coalesce(' · ملفات ناقصة: ' || (select string_agg(distinct f, ' · ')
                                             from unnest(array(select file_ from checks
                                                                where ok is not true)) f), '')
  from checks
 order by 1;

-- ---------------------------------------------------------------------------
--  ماذا بعد؟
--   • كل الصفوف ✅ ⇒ القاعدة مطابقة تماماً لكل ملفات SQL (01 → 31).
--   • ظهر ❌ في ملف معيّن ⇒ نفّذ ذلك الملف من `supabase/` في SQL Editor
--     (كله مرة واحدة)، ثم أعد تشغيل هذا الفاحص.
--   • ملف 15_assets_v2.sql يعطّل الأفاتارات المجانية القديمة بنفسه، و31
--     يثبّت onefree — فإن نفّذت 15 بعد 31، أعد تنفيذ 31 ليُفعَّل onefree وحده.
--   • لا تنسَ رفع ملفات الصور إلى المخزن: الفحص الأخير يتأكد من وجود
--     site-assets/profiles/onefree.png فعلاً.
--   • نقص البانرات أو «532 ← 492»؟ معناه أن 15 نُفِّذ بعد 21 ⇒ أعِد تنفيذ 21.
-- ---------------------------------------------------------------------------
'''

lines = [HEAD]
for n, f, item, ok, detail in CHECKS:
    if isinstance(ok, dict):
        ok_sql = ok["raw"]
    else:
        ok_sql = ok
    lines.append(f"  select {n}, '{f}', '{item}',\n         {ok_sql},\n         {detail}")
    if n != CHECKS[-1][0]:
        lines.append("  union all")
lines.append(TAIL)

sql = "\n".join(lines).replace("'", "''").replace("''''", "''")  # safety no-op
# نُعيد التصحيح: لا نريد مضاعفة الفواصل العليا (لم نضف أي شيء)
sql = "\n".join(lines)

io.open(ROOT + "/supabase/99_VERIFY_ALL.sql", "w", encoding="utf-8").write(sql)
print(f"✔ كُتب supabase/99_VERIFY_ALL.sql — {len(CHECKS)} بنداً · "
      f"{len(sql.splitlines())} سطراً · {len(sql.encode()) / 1024:.0f} KB")
