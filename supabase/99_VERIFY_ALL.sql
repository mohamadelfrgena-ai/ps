-- ============================================================================
--  AddonStation Mobile — ٩٩) فاحص القاعدة: «هل نُفِّذ كل SQL كما نريد؟»
-- ============================================================================
--
--  ماذا يفعل؟ يفحص **كل ملف SQL سلّمناه لك** (01 → 45) بنداً بنداً، ويطبع
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
--       الدفعة، و`21_banners.sql` يضيف 40 بانراً ⇒ **نفّذ 15 قبل 21** دائماً؛
--       وإلا مُحيت البانرات.
--     • الترتيب الموصى به للبقية: 26 (التسلسل) · 27 (أصول الإدارة) ·
--       31 ثم 45 (صورة onefree الوحيدة) في النهاية؛ وكلاهما آمن للتكرار.
--     • النتيجة المرجعية بعد حذف 57 سجلاً مجانياً قديماً: 475 أصلاً مفعّلاً؛
--       إن نقصت البانرات فأعِد تنفيذ `21_banners.sql` وحده.
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

  select 1, '01_schema', 'الجداول الأساسية (33)',
         (select count(*) from unnest(array['profiles', 'subscription_plans', 'payment_method_settings', 'payment_orders', 'content_items', 'content_favorites', 'content_downloads', 'ac_wallets', 'ac_transactions', 'ac_tasks', 'ac_task_claims', 'ac_events', 'chat_channels', 'chat_messages', 'chat_reactions', 'chat_mutes', 'chat_bans', 'chat_reports', 'chat_notifications', 'chat_read_state', 'chat_typing', 'chat_rules', 'chat_rule_acceptances', 'user_friends', 'creator_applications', 'creator_submissions', 'featured_creators', 'creator_follows', 'creator_tips', 'site_assets', 'sticker_packs', 'app_settings', 'admin_audit_log']) t where to_regclass('public.' || t) is null) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['profiles', 'subscription_plans', 'payment_method_settings', 'payment_orders', 'content_items', 'content_favorites', 'content_downloads', 'ac_wallets', 'ac_transactions', 'ac_tasks', 'ac_task_claims', 'ac_events', 'chat_channels', 'chat_messages', 'chat_reactions', 'chat_mutes', 'chat_bans', 'chat_reports', 'chat_notifications', 'chat_read_state', 'chat_typing', 'chat_rules', 'chat_rule_acceptances', 'user_friends', 'creator_applications', 'creator_submissions', 'featured_creators', 'creator_follows', 'creator_tips', 'site_assets', 'sticker_packs', 'app_settings', 'admin_audit_log']) t where to_regclass('public.' || t) is null), '33/33 موجودة')
  union all
  select 2, '01_schema', 'عمودا التقسيم في site_assets (collection · collection_order)',
         public.diag_text($q$select (count(*) = 2)::text from information_schema.columns where table_schema = 'public' and table_name = 'site_assets' and column_name in ('collection', 'collection_order')$q$) = 'true',
         public.diag_text($q$select coalesce(string_agg(column_name, ' · '), 'مفقود') from information_schema.columns where table_schema = 'public' and table_name = 'site_assets' and column_name in ('collection', 'collection_order')$q$)
  union all
  select 3, '01_schema', 'أعمدة ملف المستخدم الأساسية (avatar_url · banner_url · role · plan · equipped_item_id …)',
         public.diag_text($q$select (count(*) = 8)::text from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name in ('avatar_url', 'role', 'plan', 'plan_expires_at', 'equipped_item_id', 'username', 'display_name', 'banner_url')$q$) = 'true',
         public.diag_text($q$select count(*)::text || '/8' from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name in ('avatar_url', 'role', 'plan', 'plan_expires_at', 'equipped_item_id', 'username', 'display_name', 'banner_url')$q$)
  union all
  select 4, '01_schema', 'دوال ومشغّلات الملف الشخصي (ترتيب الخطط · اللمس · الحماية)',
         (select count(*) from unnest(array['plan_rank', 'role_rank', 'touch_updated_at', 'guard_profile_update', 'handle_new_user']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)) = 0 and (select count(*) from pg_trigger where not tgisinternal and tgname in ('trg_guard_profile_update', 'on_auth_user_created', 'trg_touch_profiles', 'trg_touch_subscription_plans', 'trg_touch_payment_orders', 'trg_touch_content_items', 'trg_touch_app_settings', 'trg_touch_user_friends')) = 8,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['plan_rank', 'role_rank', 'touch_updated_at', 'guard_profile_update', 'handle_new_user']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)), '5/5 دوال') || ' · المشغّلات: ' || (select count(*) from pg_trigger where not tgisinternal and tgname in ('trg_guard_profile_update', 'on_auth_user_created', 'trg_touch_profiles', 'trg_touch_subscription_plans', 'trg_touch_payment_orders', 'trg_touch_content_items', 'trg_touch_app_settings', 'trg_touch_user_friends'))::text || '/8'
  union all
  select 5, '01_schema', 'فهارس الشات والأصول',
         public.diag_text($q$select (count(*) >= 5)::text from pg_indexes where schemaname = 'public' and indexname in ('chat_messages_channel_idx', 'chat_messages_thread_idx', 'profiles_role_idx', 'site_assets_collection_idx', 'site_assets_file_url_uidx')$q$) = 'true',
         public.diag_text($q$select count(*)::text || '/5' from pg_indexes where schemaname = 'public' and indexname in ('chat_messages_channel_idx', 'chat_messages_thread_idx', 'profiles_role_idx', 'site_assets_collection_idx', 'site_assets_file_url_uidx')$q$)
  union all
  select 6, '02_functions_core', 'دوال النواة (27)',
         (select count(*) from unnest(array['current_role_of', 'is_admin', 'is_staff', 'effective_plan', 'effective_plan_label', 'can_access_item', 'download_limit_reached', 'record_content_view', 'record_content_download', 'toggle_favorite', 'ac_unlock_content', 'ac_claim_task', 'ac_admin_adjust', 'ac_admin_grant_all', 'create_payment_order', 'submit_payment_receipt', 'approve_payment_order', 'reject_payment_order', 'admin_update_profile', 'set_profile_asset', 'update_my_profile', 'admin_upsert_content', 'admin_delete_content', 'admin_update_plan', 'admin_upsert_payment_method', 'admin_delete_payment_method', 'admin_stats']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['current_role_of', 'is_admin', 'is_staff', 'effective_plan', 'effective_plan_label', 'can_access_item', 'download_limit_reached', 'record_content_view', 'record_content_download', 'toggle_favorite', 'ac_unlock_content', 'ac_claim_task', 'ac_admin_adjust', 'ac_admin_grant_all', 'create_payment_order', 'submit_payment_receipt', 'approve_payment_order', 'reject_payment_order', 'admin_update_profile', 'set_profile_asset', 'update_my_profile', 'admin_upsert_content', 'admin_delete_content', 'admin_update_plan', 'admin_upsert_payment_method', 'admin_delete_payment_method', 'admin_stats']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)), '27/27 موجودة')
  union all
  select 7, '03_functions_chat', 'دوال الشات والمبدعين (34)',
         (select count(*) from unnest(array['app_setting', 'chat_content_check', 'send_chat_message', 'chat_delete_window_seconds', 'chat_delete_my_message', 'chat_moderate_message', 'chat_toggle_pin', 'chat_toggle_reaction', 'chat_mute_user', 'chat_unmute_user', 'chat_ban_user', 'chat_unban_user', 'chat_report_message', 'chat_close_report', 'chat_admin_upsert_room', 'chat_admin_delete_room', 'chat_send_announcement', 'chat_set_typing', 'chat_mark_read', 'chat_unread_counts', 'my_chat_status', 'chat_accept_rules', 'mark_notifications_read', 'send_friend_request', 'respond_friend_request', 'submit_creator_application', 'review_creator_application', 'toggle_featured_creator', 'creator_submit_content', 'review_creator_submission', 'creator_submit_tip', 'creator_review_tip', 'admin_update_settings', 'admin_update_chat_rules']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['app_setting', 'chat_content_check', 'send_chat_message', 'chat_delete_window_seconds', 'chat_delete_my_message', 'chat_moderate_message', 'chat_toggle_pin', 'chat_toggle_reaction', 'chat_mute_user', 'chat_unmute_user', 'chat_ban_user', 'chat_unban_user', 'chat_report_message', 'chat_close_report', 'chat_admin_upsert_room', 'chat_admin_delete_room', 'chat_send_announcement', 'chat_set_typing', 'chat_mark_read', 'chat_unread_counts', 'my_chat_status', 'chat_accept_rules', 'mark_notifications_read', 'send_friend_request', 'respond_friend_request', 'submit_creator_application', 'review_creator_application', 'toggle_featured_creator', 'creator_submit_content', 'review_creator_submission', 'creator_submit_tip', 'creator_review_tip', 'admin_update_settings', 'admin_update_chat_rules']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)), '34/34 موجودة')
  union all
  select 8, '04_rls', 'سياسات RLS (57)',
         (select count(*) from unnest(array['profiles_select_all:profiles', 'profiles_update_own:profiles', 'profiles_admin_all:profiles', 'plans_select:subscription_plans', 'plans_admin_write:subscription_plans', 'pms_select_enabled:payment_method_settings', 'pms_admin_write:payment_method_settings', 'orders_select_own:payment_orders', 'content_select_public:content_items', 'content_admin_write:content_items', 'fav_own:content_favorites', 'downloads_own:content_downloads', 'ac_wallet_own:ac_wallets', 'ac_tx_own:ac_transactions', 'ac_tasks_read:ac_tasks', 'ac_tasks_admin:ac_tasks', 'ac_claims_own:ac_task_claims', 'ac_events_admin:ac_events', 'channels_read:chat_channels', 'channels_admin:chat_channels', 'messages_read:chat_messages', 'reactions_read:chat_reactions', 'reactions_own:chat_reactions', 'reactions_delete_own:chat_reactions', 'mutes_read:chat_mutes', 'mutes_staff_write:chat_mutes', 'bans_read:chat_bans', 'bans_staff_write:chat_bans', 'reports_read:chat_reports', 'reports_insert:chat_reports', 'reports_staff_update:chat_reports', 'rule_accept_own:chat_rule_acceptances', 'rule_accept_insert:chat_rule_acceptances', 'rules_read:chat_rules', 'rules_admin:chat_rules', 'typing_read:chat_typing', 'typing_own:chat_typing', 'read_state_own:chat_read_state', 'notif_own:chat_notifications', 'notif_update_own:chat_notifications', 'friends_own:user_friends', 'apps_own:creator_applications', 'apps_insert:creator_applications', 'submissions_own:creator_submissions', 'submissions_admin_write:creator_submissions', 'featured_read:featured_creators', 'featured_admin:featured_creators', 'follows_read:creator_follows', 'follows_own:creator_follows', 'tips_read:creator_tips', 'tips_admin_write:creator_tips', 'assets_read:site_assets', 'assets_admin:site_assets', 'packs_read:sticker_packs', 'packs_admin:sticker_packs', 'settings_read:app_settings', 'audit_admin_read:admin_audit_log']) t where not exists (select 1 from pg_policies pol where pol.schemaname = 'public' and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['profiles_select_all:profiles', 'profiles_update_own:profiles', 'profiles_admin_all:profiles', 'plans_select:subscription_plans', 'plans_admin_write:subscription_plans', 'pms_select_enabled:payment_method_settings', 'pms_admin_write:payment_method_settings', 'orders_select_own:payment_orders', 'content_select_public:content_items', 'content_admin_write:content_items', 'fav_own:content_favorites', 'downloads_own:content_downloads', 'ac_wallet_own:ac_wallets', 'ac_tx_own:ac_transactions', 'ac_tasks_read:ac_tasks', 'ac_tasks_admin:ac_tasks', 'ac_claims_own:ac_task_claims', 'ac_events_admin:ac_events', 'channels_read:chat_channels', 'channels_admin:chat_channels', 'messages_read:chat_messages', 'reactions_read:chat_reactions', 'reactions_own:chat_reactions', 'reactions_delete_own:chat_reactions', 'mutes_read:chat_mutes', 'mutes_staff_write:chat_mutes', 'bans_read:chat_bans', 'bans_staff_write:chat_bans', 'reports_read:chat_reports', 'reports_insert:chat_reports', 'reports_staff_update:chat_reports', 'rule_accept_own:chat_rule_acceptances', 'rule_accept_insert:chat_rule_acceptances', 'rules_read:chat_rules', 'rules_admin:chat_rules', 'typing_read:chat_typing', 'typing_own:chat_typing', 'read_state_own:chat_read_state', 'notif_own:chat_notifications', 'notif_update_own:chat_notifications', 'friends_own:user_friends', 'apps_own:creator_applications', 'apps_insert:creator_applications', 'submissions_own:creator_submissions', 'submissions_admin_write:creator_submissions', 'featured_read:featured_creators', 'featured_admin:featured_creators', 'follows_read:creator_follows', 'follows_own:creator_follows', 'tips_read:creator_tips', 'tips_admin_write:creator_tips', 'assets_read:site_assets', 'assets_admin:site_assets', 'packs_read:sticker_packs', 'packs_admin:sticker_packs', 'settings_read:app_settings', 'audit_admin_read:admin_audit_log']) t where not exists (select 1 from pg_policies pol where pol.schemaname = 'public' and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))), '57/57 موجودة')
  union all
  select 9, '05_storage · 40_admin_asset_privacy', 'المخازن السبعة (منها admin-assets الخاص)',
         public.diag_text($q$select (count(*) = 7)::text from storage.buckets where id in ('content-files', 'payment-receipts', 'chat-images', 'chat-media', 'creator-media', 'site-assets', 'admin-assets')$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(id, ' · ') from storage.buckets where id in ('content-files', 'payment-receipts', 'chat-images', 'chat-media', 'creator-media', 'site-assets', 'admin-assets')$q$), 'مفقود')
  union all
  select 10, '05_storage', 'مخزن site-assets عام (public) — الصور العادية تظهر بلا تسجيل دخول',
         public.diag_text($q$select (count(*) = 1)::text from storage.buckets where id = 'site-assets' and public$q$) = 'true',
         coalesce(public.diag_text($q$select 'public = ' || public::text from storage.buckets where id = 'site-assets'$q$), 'مفقود')
  union all
  select 11, '40_admin_asset_privacy', 'مخزن admin-assets خاص (public = false)',
         public.diag_text($q$select (count(*) = 1)::text from storage.buckets where id = 'admin-assets' and not public$q$) = 'true',
         coalesce(public.diag_text($q$select 'public = ' || public::text from storage.buckets where id = 'admin-assets'$q$), 'مفقود')
  union all
  select 12, '05_storage · 40_admin_asset_privacy', 'سياسات المخزن (22)',
         public.diag_text($q$select (count(*) = 22)::text from pg_policies where schemaname = 'storage' and policyname in ('content_files_read', 'content_files_admin_write', 'content_files_admin_update', 'content_files_admin_delete', 'receipts_insert_own', 'receipts_read', 'receipts_update_own', 'chat_images_insert', 'chat_images_read', 'chat_media_insert', 'chat_media_read', 'creator_media_insert', 'creator_media_read', 'site_assets_read', 'site_assets_admin_write', 'site_assets_creator_write', 'site_assets_admin_update', 'site_assets_admin_delete', 'admin_assets_read', 'admin_assets_insert', 'admin_assets_update', 'admin_assets_delete')$q$) = 'true',
         public.diag_text($q$select count(*)::text || '/22' from pg_policies where schemaname = 'storage' and policyname in ('content_files_read', 'content_files_admin_write', 'content_files_admin_update', 'content_files_admin_delete', 'receipts_insert_own', 'receipts_read', 'receipts_update_own', 'chat_images_insert', 'chat_images_read', 'chat_media_insert', 'chat_media_read', 'creator_media_insert', 'creator_media_read', 'site_assets_read', 'site_assets_admin_write', 'site_assets_creator_write', 'site_assets_admin_update', 'site_assets_admin_delete', 'admin_assets_read', 'admin_assets_insert', 'admin_assets_update', 'admin_assets_delete')$q$)
  union all
  select 13, '06_seed · 22_plan_prices', 'الخطط الثلاث وأسعارها (free · epic · legendary)',
         public.diag_text($q$select (count(*) = 3)::text from public.subscription_plans where id in ('free', 'epic', 'legendary')$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(id || ' = ' || price_lyd::text || ' د.ل / $' || price_usd::text, ' · ' order by sort_order) from public.subscription_plans$q$), 'مفقود')
  union all
  select 14, '22_plan_prices', 'سعرا Epic و Legendary بالدولار مضبوطان (2.99 · 5.99)',
         coalesce(public.diag_text($q$select (count(*) = 2)::text from public.subscription_plans where (id = 'epic' and price_usd = 2.99) or (id = 'legendary' and price_usd = 5.99)$q$), 'false') = 'true',
         coalesce(public.diag_text($q$select string_agg(id || ' = $' || price_usd::text, ' · ' order by sort_order) from public.subscription_plans$q$), 'مفقود')
  union all
  select 15, '08_admin_bootstrap', 'يوجد حساب إدارة واحد على الأقل (role = admin)',
         public.diag_text($q$select (count(*) >= 1)::text from public.profiles where role = 'admin'$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' حساب إدارة' from public.profiles where role = 'admin'$q$), 'مفقود')
  union all
  select 16, '09_gateway_functions', 'دوال البوابات (gateway)',
         (select count(*) from unnest(array['gateway_confirm_order', 'gateway_attach_session']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['gateway_confirm_order', 'gateway_attach_session']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)), '2/2 موجودة')
  union all
  select 17, '10_realtime', 'منشور البث الحيّ supabase_realtime موجود',
         public.diag_text($q$select (count(*) = 1)::text from pg_publication where pubname = 'supabase_realtime'$q$) = 'true',
         public.diag_text($q$select string_agg(pubname, ' · ') from pg_publication where pubname = 'supabase_realtime'$q$)
  union all
  select 18, '10_realtime', 'جداول البث الحيّ (6)',
         (select count(*) from unnest(array['chat_messages', 'chat_reactions', 'chat_typing', 'chat_read_state', 'chat_notifications', 'profiles']) t where not exists (select 1 from pg_publication_rel pr join pg_publication p on p.oid = pr.prpubid join pg_class c on c.oid = pr.prrelid where p.pubname = 'supabase_realtime' and c.relname = t)) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['chat_messages', 'chat_reactions', 'chat_typing', 'chat_read_state', 'chat_notifications', 'profiles']) t where not exists (select 1 from pg_publication_rel pr join pg_publication p on p.oid = pr.prpubid join pg_class c on c.oid = pr.prrelid where p.pubname = 'supabase_realtime' and c.relname = t)), '6/6 في المنشور')
  union all
  select 19, '11_grants', 'صلاحيات الكتابة للأدوار (service_role يكتب · authenticated يقرأ)',
         public.diag_text($q$select ((has_table_privilege('service_role', 'public.site_assets', 'insert') and has_table_privilege('authenticated', 'public.site_assets', 'select')))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'service_role insert = ' || has_table_privilege('service_role', 'public.site_assets', 'insert')::text || ' · authenticated select = ' || has_table_privilege('authenticated', 'public.site_assets', 'select')::text$q$), 'مفقود')
  union all
  select 20, '13_channels_icons', 'أسماء الغرف الرسمية صحيحة (General · Minecraft · العربية …)',
         (public.diag_text($q$select (count(*) >= 5)::text from public.chat_channels where id in ('general', 'minecraft', 'support', 'lang-ar', 'lang-ru')$q$) = 'true' and public.diag_text($q$select (name = 'العربية')::text from public.chat_channels where id = 'lang-ar'$q$) = 'true'),
         coalesce(public.diag_text($q$select string_agg(id || ' → ' || name, ' · ' order by sort_order) from public.chat_channels where category = 'lang'$q$), 'مفقود')
  union all
  select 21, '16_language_room_names', 'الاسم العربي للغرفة = «العربية»',
         public.diag_text($q$select (name = 'العربية')::text from public.chat_channels where id = 'lang-ar'$q$) = 'true',
         coalesce(public.diag_text($q$select name || ' · ' || coalesce(description, '—') from public.chat_channels where id = 'lang-ar'$q$), 'مفقود')
  union all
  select 22, '17_cash_services_room', 'غرفة «خدمات النقدية» موجودة والكتابة فيها للإدارة فقط (admin_only)',
         public.diag_text($q$select ((post_permission = 'admin_only') and is_locked)::text from public.chat_channels where id = 'cash-services'$q$) = 'true',
         coalesce(public.diag_text($q$select name || ' · الكتابة: ' || post_permission || ' · مقفلة: ' || is_locked::text from public.chat_channels where id = 'cash-services'$q$), 'مفقودة')
  union all
  select 23, '14_redeem_codes', 'جداول أكواد الشحن',
         (select count(*) from unnest(array['ac_redeem_codes', 'ac_redeem_uses']) t where to_regclass('public.' || t) is null) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['ac_redeem_codes', 'ac_redeem_uses']) t where to_regclass('public.' || t) is null), '2/2 موجودة')
  union all
  select 24, '14_redeem_codes', 'دوال أكواد الشحن',
         (select count(*) from unnest(array['ac_random_code', 'ac_admin_create_codes', 'ac_redeem_code']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['ac_random_code', 'ac_admin_create_codes', 'ac_redeem_code']) t where not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = t)), '3/3 موجودة')
  union all
  select 25, '14_redeem_codes', 'سياسات أكواد الشحن',
         (select count(*) from unnest(array['ac_redeem_codes_admin:ac_redeem_codes', 'ac_redeem_uses_self:ac_redeem_uses']) t where not exists (select 1 from pg_policies pol where pol.schemaname = 'public' and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))) = 0,
         coalesce((select 'المفقود: ' || string_agg(t, ' · ') from unnest(array['ac_redeem_codes_admin:ac_redeem_codes', 'ac_redeem_uses_self:ac_redeem_uses']) t where not exists (select 1 from pg_policies pol where pol.schemaname = 'public' and pol.tablename = split_part(t, ':', 2) and pol.policyname = split_part(t, ':', 1))), '2/2 موجودة')
  union all
  select 26, '15_assets_v2 · 21_banners · 45', 'مجموع الأصول >= 475 (435 بعد حذف المجاني القديم + 40 بانر)',
         public.diag_text($q$select (count(*) >= 475)::text from public.site_assets$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || '/475 مصدر' || ' · إضافاتك: ' || greatest(count(*) - 475, 0)::text from public.site_assets$q$), 'مفقود')
  union all
  select 27, '15_assets_v2 · 45', 'توزيع الأصول كاملًا (66 أفاتاراً · 176 أداة · 193 ملصقاً · 40 بانراً)',
         public.diag_text($q$select ((count(*) filter (where asset_type = 'avatar') >= 66) and (count(*) filter (where asset_type = 'item') >= 176) and (count(*) filter (where asset_type = 'sticker') >= 193) and (count(*) filter (where asset_type = 'banner') >= 40))::text from public.site_assets$q$) = 'true',
         coalesce(public.diag_text($q$select 'avatar ' || count(*) filter (where asset_type = 'avatar')::text || ' · item ' || count(*) filter (where asset_type = 'item')::text || ' · sticker ' || count(*) filter (where asset_type = 'sticker')::text || ' · banner ' || count(*) filter (where asset_type = 'banner')::text from public.site_assets$q$), 'مفقود')
  union all
  select 28, '15_assets_v2 · 21_banners', 'لا نقص في أي قسم من أقسام الدفعات (22 قسماً)',
         public.diag_text($q$(select (count(*) = 0)::text from (values ('PFP', 1), ('Profiles', 1), ('Best Cartoon', 7), ('Cartoon Meme', 11), ('Cool Cartoon', 6), ('Call Of Duty', 10), ('Sad', 10), ('Ahh', 8), ('Moumen', 4), ('Animated', 8), ('Memes', 47), ('Minecraft Memes 1', 21), ('Minecraft Memes 2', 87), ('Minecraft Memes 3', 20), ('Minecraft Movie', 6), ('Top', 12), ('Minecraft Items', 103), ('Games', 41), ('PlayStation', 10), ('Random Things', 22), ('Nature 🌿', 13), ('Random 🎲', 27)) as e(collection, want) left join (select collection, count(*) as have from public.site_assets where collection is not null group by collection) a on a.collection = e.collection where coalesce(a.have, 0) < e.want)$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(e.collection || ': ' || coalesce(a.have, 0) || '/' || e.want, ' · ') from (values ('PFP', 1), ('Profiles', 1), ('Best Cartoon', 7), ('Cartoon Meme', 11), ('Cool Cartoon', 6), ('Call Of Duty', 10), ('Sad', 10), ('Ahh', 8), ('Moumen', 4), ('Animated', 8), ('Memes', 47), ('Minecraft Memes 1', 21), ('Minecraft Memes 2', 87), ('Minecraft Memes 3', 20), ('Minecraft Movie', 6), ('Top', 12), ('Minecraft Items', 103), ('Games', 41), ('PlayStation', 10), ('Random Things', 22), ('Nature 🌿', 13), ('Random 🎲', 27)) as e(collection, want) left join (select collection, count(*) as have from public.site_assets where collection is not null group by collection) a on a.collection = e.collection where coalesce(a.have, 0) < e.want$q$), 'لا نقص في أي قسم')
  union all
  select 29, '15_assets_v2 · 21_banners · 40', 'كل أصل مفعّل ملفه موجود في المخزن (لا صورة مكسورة)',
         public.diag_text($q$select (count(*) = 0)::text from public.site_assets a where a.enabled and not exists (select 1 from storage.objects o where o.bucket_id = case when a.file_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(a.file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/'))$q$) = 'true',
         coalesce(public.diag_text($q$select 'أول المفقود: ' || string_agg(a.file_url, ' · ') from (select a.file_url from public.site_assets a where a.enabled and not exists (select 1 from storage.objects o where o.bucket_id = case when a.file_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(a.file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')) limit 6) a$q$), 'كل الملفات موجودة')
  union all
  select 30, '15_assets_v2', 'كل أفاتار مفعّل ملفه موجود في المخزن',
         public.diag_text($q$select (count(*) = 0)::text from public.site_assets a where a.enabled and a.asset_type = 'avatar' and not exists (select 1 from storage.objects o where o.bucket_id = case when a.file_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(a.file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/'))$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(a.file_url, ' · ') from (select a.file_url from public.site_assets a where a.enabled and a.asset_type = 'avatar' and not exists (select 1 from storage.objects o where o.bucket_id = case when a.file_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(a.file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')) limit 6) a$q$), 'كل الأفاتارات موجودة')
  union all
  select 31, '24_free_default_avatar · 32', 'صور الحسابات موجودة في المخزن (لا حساب بصورة مكسورة)',
         public.diag_text($q$select (count(*) = 0)::text from public.profiles p where p.avatar_url is not null and btrim(p.avatar_url) <> '' and lower(btrim(p.avatar_url)) <> 'none' and not exists (select 1 from storage.objects o where o.bucket_id = case when p.avatar_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(p.avatar_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/'))$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' حساباً' from public.profiles p where p.avatar_url is not null and btrim(p.avatar_url) <> '' and lower(btrim(p.avatar_url)) <> 'none' and not exists (select 1 from storage.objects o where o.bucket_id = case when p.avatar_url like 'admin-private/%' then 'admin-assets' else 'site-assets' end and regexp_replace(o.name, '^(profiles|banners|items|stickers)/(\1)/', '\1/') = regexp_replace(p.avatar_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/'))$q$), 'كل صور الحسابات موجودة')
  union all
  select 32, '21_banners', 'بانرات إضافية مسجّلة (40)',
         public.diag_text($q$select (count(*) >= 40)::text from public.site_assets where asset_type = 'banner'$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' بانر' from public.site_assets where asset_type = 'banner'$q$), 'مفقود')
  union all
  select 33, '27_admin_only_assets', 'أصول حصرية للإدارة (required_plan = admin)',
         public.diag_text($q$select (count(*) >= 1)::text from public.site_assets where required_plan = 'admin'$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' أصلاً' from public.site_assets where required_plan = 'admin'$q$), 'مفقود')
  union all
  select 34, '40_admin_asset_privacy', 'عرض visible_profiles ودالة تنقيح أصل الأدمن موجودان',
         public.diag_text($q$select (to_regclass('public.visible_profiles') is not null and exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'is_admin_only_profile_asset'))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'view=' || (to_regclass('public.visible_profiles') is not null)::text || ' · function=' || exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'is_admin_only_profile_asset')::text$q$), 'مفقود')
  union all
  select 35, '40_admin_asset_privacy', 'لا قراءة REST مباشرة لـ profiles؛ القراءة من العرض المنقّح',
         public.diag_text($q$select ((not has_table_privilege('anon', 'public.profiles', 'select')) and (not has_table_privilege('authenticated', 'public.profiles', 'select')) and has_table_privilege('anon', 'public.visible_profiles', 'select') and has_table_privilege('authenticated', 'public.visible_profiles', 'select') and not has_column_privilege('authenticated', 'public.profiles', 'equipped_item_id', 'update'))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'base anon=' || has_table_privilege('anon', 'public.profiles', 'select')::text || ' · base auth=' || has_table_privilege('authenticated', 'public.profiles', 'select')::text || ' · view anon=' || has_table_privilege('anon', 'public.visible_profiles', 'select')::text || ' · view auth=' || has_table_privilege('authenticated', 'public.visible_profiles', 'select')::text || ' · equipped direct update=' || has_column_privilege('authenticated', 'public.profiles', 'equipped_item_id', 'update')::text$q$), 'مفقود')
  union all
  select 36, '40_admin_asset_privacy', 'سياسة site_assets تخفي required_plan=admin عن غير الأدمن',
         public.diag_text($q$select coalesce((select (qual ilike '%required_plan%' and qual ilike '%is_admin%')::text from pg_policies where schemaname = 'public' and tablename = 'site_assets' and policyname = 'assets_read'), 'false')$q$) = 'true',
         coalesce(public.diag_text($q$select qual from pg_policies where schemaname = 'public' and tablename = 'site_assets' and policyname = 'assets_read'$q$), 'مفقود')
  union all
  select 37, '40_admin_asset_privacy', 'كل ملفات admin في المخزن الخاص ولا نسخة عامة منها',
         public.diag_text($q$select (count(*) >= 1 and count(*) filter (where file_url not like 'admin-private/%' or not exists (select 1 from storage.objects o where o.bucket_id = 'admin-assets' and o.name = a.file_url) or exists (select 1 from storage.objects o where o.bucket_id = 'site-assets' and o.name = regexp_replace(a.file_url, '^admin-private/', ''))) = 0)::text from public.site_assets a where required_plan = 'admin'$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' admin · ينتظر النقل/التنظيف: ' || count(*) filter (where file_url not like 'admin-private/%' or not exists (select 1 from storage.objects o where o.bucket_id = 'admin-assets' and o.name = a.file_url) or exists (select 1 from storage.objects o where o.bucket_id = 'site-assets' and o.name = regexp_replace(a.file_url, '^admin-private/', '')))::text from public.site_assets a where required_plan = 'admin'$q$), 'مفقود')
  union all
  select 38, '17_message_delete_window', 'نافذة حذف الرسالة = 180 ثانية (٣ دقائق)',
         public.diag_text($q$select (public.chat_delete_window_seconds() = 180)::text$q$) = 'true',
         public.diag_text($q$select public.chat_delete_window_seconds()::text || ' ثانية'$q$)
  union all
  select 39, '18_media_admin_only', 'الوسائط (فيديو/صوت) للإدارة فقط — الحارس والزناد',
         public.diag_text($q$select ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'chat_media_admin_only_guard') = 1 and (select count(*) from pg_trigger where tgname = 'trg_chat_media_admin_only' and not tgisinternal) = 1)::text$q$) = 'true',
         public.diag_text($q$select 'دالة الحارس: ' || ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'chat_media_admin_only_guard')::text) || ' · الزناد: ' || ((select count(*) from pg_trigger where tgname = 'trg_chat_media_admin_only' and not tgisinternal)::text)$q$)
  union all
  select 40, '19_admin_panel_guard', 'دالة حماية لوحة الإدارة + سياساتها',
         public.diag_text($q$select ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'admin_panel_access') = 1 and (select count(*) from pg_policies where schemaname = 'public' and policyname in ('settings_admin', 'audit_admin_read', 'ac_redeem_codes_admin', 'orders_admin_all')) = 4)::text$q$) = 'true',
         public.diag_text($q$select 'الدالة: ' || ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'admin_panel_access')::text) || ' · السياسات: ' || ((select count(*) from pg_policies where schemaname = 'public' and policyname in ('settings_admin', 'audit_admin_read', 'ac_redeem_codes_admin', 'orders_admin_all'))::text) || '/4'$q$)
  union all
  select 41, '20_sticker_with_text', 'send_chat_message تقبل الملصق مع النص',
         public.diag_text($q$select (count(*) >= 1)::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'send_chat_message' and pg_get_function_arguments(p.oid) like '%p_sticker_url%'$q$) = 'true',
         public.diag_text($q$select count(*)::text || ' نسخة' from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'send_chat_message' and pg_get_function_arguments(p.oid) like '%p_sticker_url%'$q$)
  union all
  select 42, '23_payment_order_usd', 'عمود amount_usd ودالة الطلب ترجعه',
         public.diag_text($q$select ((select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'payment_orders' and column_name = 'amount_usd') = 1)::text$q$) = 'true',
         public.diag_text($q$select 'amount_usd: ' || ((select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'payment_orders' and column_name = 'amount_usd')::text)$q$)
  union all
  select 43, '24_free_default_avatar · 32', 'افتراضي avatar_url = المسار المعتمد + زناد الحساب الجديد',
         public.diag_text($q$select ((select column_default like '%profiles/profiles/onefree.png%' from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url') and (select count(*) from pg_trigger where tgname = 'on_auth_user_created' and not tgisinternal) = 1)::text$q$) = 'true',
         public.diag_text($q$select 'الافتراضي: ' || coalesce((select column_default from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url'), 'مفقود')$q$)
  union all
  select 44, '25_fix_send_chat_message_unnest', 'send_chat_message بلا خطأ unnest (الإصلاح مطبَّق)',
         public.diag_text($q$select (count(*) = 0)::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'send_chat_message' and pg_get_functiondef(p.oid) ilike '%where unnest is not null%'$q$) = 'true',
         public.diag_text($q$select count(*)::text || ' نسخة فيها الخلل' from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'send_chat_message' and pg_get_functiondef(p.oid) ilike '%where unnest is not null%'$q$)
  union all
  select 45, '26_fix_site_assets_sequence', 'تسلسل الأصول يعمل (authenticated يستطيع الإدراج)',
         coalesce(public.diag_text($q$select (has_sequence_privilege('authenticated', 'public.site_assets_id_seq', 'usage') and (select count(*) from pg_sequences where schemaname = 'public' and sequencename = 'site_assets_id_seq') = 1)::text$q$), 'false') = 'true',
         coalesce(public.diag_text($q$select 'التسلسل: ' || coalesce((select sequencename from pg_sequences where schemaname = 'public' and sequencename = 'site_assets_id_seq'), 'مفقود') || ' · أعلى معرّف: ' || coalesce((select max(id)::text from public.site_assets), '—')$q$), 'مفقود')
  union all
  select 46, '29_chat_moderation_rooms', 'غرف المشرفين + دوالها + سياساتها',
         public.diag_text($q$select ((select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'chat_moderator_rooms') = 1 and (select count(*) from pg_policies where schemaname = 'public' and policyname in ('mod_rooms_admin_all', 'mod_rooms_self_read')) = 2 and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('chat_can_moderate_channel', 'chat_my_moderation_scope', 'chat_delete_message_staff', 'chat_moderator_list', 'chat_set_moderator_rooms')) = 5)::text$q$) = 'true',
         public.diag_text($q$select 'الجدول: ' || ((select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'chat_moderator_rooms')::text) || ' · السياسات: ' || ((select count(*) from pg_policies where schemaname = 'public' and policyname in ('mod_rooms_admin_all', 'mod_rooms_self_read'))::text) || '/2' || ' · الدوال: ' || ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('chat_can_moderate_channel', 'chat_my_moderation_scope', 'chat_delete_message_staff', 'chat_moderator_list', 'chat_set_moderator_rooms'))::text) || '/5'$q$)
  union all
  select 47, '30_chat_members_only', 'الشات للمسجّلين فقط (3 سياسات بـ auth.uid())',
         public.diag_text($q$select (count(*) = 3)::text from pg_policies where schemaname = 'public' and policyname in ('messages_read', 'reactions_read', 'channels_read') and qual ilike '%auth.uid()%'$q$) = 'true',
         public.diag_text($q$select count(*)::text || '/3' from pg_policies where schemaname = 'public' and policyname in ('messages_read', 'reactions_read', 'channels_read') and qual ilike '%auth.uid()%'$q$)
  union all
  select 48, '31_single_free_avatar · 45', 'سجل أفاتار مجاني واحد فقط وملف onefree موجود',
         coalesce(public.diag_text($q$select (count(*) = 1 and bool_and(a.enabled) and bool_and(a.file_url = 'profiles/profiles/onefree.png') and bool_and(exists (select 1 from storage.objects o where o.bucket_id = 'site-assets' and o.name = a.file_url)))::text from public.site_assets a where a.asset_type = 'avatar' and a.required_plan = 'free'$q$), 'false') = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' سجل مجاني · ' || coalesce(string_agg(file_url, ' · '), '—') from public.site_assets where asset_type = 'avatar' and required_plan = 'free'$q$), 'مفقود')
  union all
  select 49, '32_onefree_canonical_path', 'المسار المعتمد موحّد (الأصل المفعّل · الافتراضي · كل الحسابات)',
         public.diag_text($q$select ((select count(*) from public.site_assets where asset_type = 'avatar' and required_plan = 'free' and enabled and file_url = 'profiles/profiles/onefree.png') = 1 and (select column_default like '%profiles/profiles/onefree.png%' from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url') and (select count(*) from public.profiles where avatar_url = 'profiles/onefree.png') = 0)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'المفعّل: ' || coalesce((select string_agg(file_url, ' · ') from public.site_assets where asset_type = 'avatar' and required_plan = 'free' and enabled), '—') || ' · الافتراضي: ' || coalesce((select column_default from information_schema.columns where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url'), '—') || ' · حسابات على مسار قديم: ' || (select count(*) from public.profiles where avatar_url = 'profiles/onefree.png')::text$q$), 'مفقود')
  union all
  select 50, '33_user_block', 'الحظر بين المستخدمين (سياسة جوجل UGC): الجدول + الدوال + إخفاء الرسائل',
         public.diag_text($q$select ((select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'user_blocks') = 1 and (select count(*) from pg_proc where proname in ('user_block', 'user_unblock', 'is_blocked_pair', 'my_blocked_users', 'i_blocked_user')) = 5 and (select count(*) from pg_policies where schemaname = 'public' and tablename = 'chat_messages' and policyname = 'messages_read' and qual like '%is_blocked_pair%') = 1 and (select count(*) from pg_policies where schemaname = 'public' and tablename = 'user_blocks' and policyname = 'user_blocks_read') = 1)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'الجدول: ' || (select case when count(*) = 1 then 'موجود' else 'مفقود' end from information_schema.tables where table_schema = 'public' and table_name = 'user_blocks') || ' · الدوال: ' || (select count(*) from pg_proc where proname in ('user_block', 'user_unblock', 'is_blocked_pair', 'my_blocked_users', 'i_blocked_user'))::text || '/5 ' || '· إخفاء الرسائل: ' || (select case when count(*) = 1 then 'مفعّل' else 'غير مفعّل' end from pg_policies where schemaname = 'public' and tablename = 'chat_messages' and policyname = 'messages_read' and qual like '%is_blocked_pair%') || ' · حظرات مسجّلة: ' || (select count(*) from public.user_blocks)::text$q$), 'مفقود')
  union all
  select 51, '31_single_free_avatar · 45', 'لا يوجد حساب بلا صورة افتراضية أو مخصّصة',
         coalesce(public.diag_text($q$select (count(*) = 0)::text from public.profiles where avatar_url is null or btrim(avatar_url) = ''$q$), 'false') = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' بلا صورة' from public.profiles where avatar_url is null or btrim(avatar_url) = ''$q$), 'مفقود')
  union all
  select 52, '45_single_attached_free_avatar', 'لا توجد سجلات أفاتار مجانية قديمة',
         coalesce(public.diag_text($q$select (count(*) = 0)::text from public.site_assets where asset_type = 'avatar' and required_plan = 'free' and file_url <> 'profiles/profiles/onefree.png'$q$), 'false') = 'true',
         coalesce(public.diag_text($q$select count(*)::text || ' سجل مجاني قديم' from public.site_assets where asset_type = 'avatar' and required_plan = 'free' and file_url <> 'profiles/profiles/onefree.png'$q$), 'مفقود')
  union all
  select 53, '45_single_attached_free_avatar', 'ملف صورة المالك موجود على المسار الوحيد المعتمد',
         public.diag_text($q$select (count(*) = 1)::text from storage.objects where bucket_id = 'site-assets' and name = 'profiles/profiles/onefree.png'$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(name || ' · ' || coalesce(metadata->>'size', '?') || ' bytes', ' · ') from storage.objects where bucket_id = 'site-assets' and name = 'profiles/profiles/onefree.png'$q$), 'غير مرفوع بعد')
  union all
  select 54, '07_seed_assets · 12_avatars', 'دفعات الأصول القديمة (مُستبدَلة بـ15 — لا يلزم تنفيذها)',
         true,
         'لا شيء مطلوب: 15_assets_v2 و21_banners و45 هي المرجع (475 أصلاً مفعّلاً)'
  union all
  select 55, '16_fix_asset_paths', 'توحيد المسارات المكرّرة (نسخة آمنة — لا تلزمك الآن)',
         true,
         coalesce(public.diag_text($q$select 'مسارات مكرّرة: ' || count(*) filter (where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/')::text || ' · منها ملفه على المسار النظيف: ' || count(*) filter (where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/' and exists (select 1 from storage.objects o where o.bucket_id = 'site-assets' and o.name = regexp_replace(file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')))::text || ' (المكرّر مقصود: هكذا رُفعت الملفات)' from public.site_assets$q$), 'لا شيء مطلوب')
  union all
  select 56, '28_chat_cleanup', 'حالة الشات (28 ملف تنظيف أقسامه اختيارية)',
         true,
         coalesce(public.diag_text($q$select 'الظاهرة: ' || count(*) filter (where status <> 'deleted')::text || ' · المحذوفة: ' || count(*) filter (where status = 'deleted')::text || ' · المخفية: ' || count(*) filter (where status = 'hidden')::text from public.chat_messages$q$), 'مفقود')
  union all
  select 57, '34_push_notifications', 'الإشعارات الفورية: 3 جداول + 18 دالة + زنادان + إعدادات المستخدم + إشعارات بصور + عرض اشتراك',
         public.diag_text($q$select ((select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('push_devices', 'push_prefs', 'push_queue')) = 3 and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('push_prefs_for', 'push_allowed', 'push_local_now', 'push_schedule_at', 'push_enqueue', 'push_broadcast', 'push_reengage_scan', 'push_cleanup', 'push_offer_scan', 'push_targets', 'push_heartbeat', 'push_register_device', 'push_unregister_device', 'my_push_prefs', 'push_set_prefs', 'push_test_me', 'push_on_notification', 'push_on_content')) = 18 and (select count(*) from pg_trigger where not tgisinternal and tgname in ('trg_push_on_notification', 'trg_push_on_content')) = 2 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'push_prefs' and column_name in ('master', 'chat', 'content', 'social', 'account', 'reminders', 'quiet_enabled', 'quiet_from', 'quiet_to', 'reengage_days', 'last_reengage_at', 'last_offer_at')) = 12 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'push_devices' and column_name in ('token', 'user_id', 'platform', 'locale', 'tz_offset', 'enabled')) = 6 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'push_queue' and column_name in ('title_key', 'body_key', 'route', 'collapse_key', 'priority', 'scheduled_at', 'status', 'args', 'image_url', 'image_kind')) = 10)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'الجداول: ' || (select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('push_devices', 'push_prefs', 'push_queue'))::text || '/3 ' || '· الدوال: ' || (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and (p.proname like 'push\_%' or p.proname = 'my_push_prefs'))::text || '/18 ' || '· الزنادات: ' || (select count(*) from pg_trigger where not tgisinternal and tgname in ('trg_push_on_notification', 'trg_push_on_content'))::text || '/2 ' || '· الأجهزة المسجّلة: ' || (select count(*) from public.push_devices)::text || ' · في الطابور: ' || (select count(*) from public.push_queue where status = 'pending')::text || ' · إشعارات بصور: ' || (select count(*) from public.push_queue where image_url is not null)::text || ' · عروض اشتراك أُرسلت: ' || (select count(*) from public.push_prefs where last_offer_at is not null)::text$q$), 'مفقود')
  union all
  select 58, '35_push_live', 'تشغيل الإشعارات الحقيقية: جدولة كل دقيقة + فوري للمنشن + طبيب التشخيص',
         public.diag_text($q$select ((select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'push_config') = 1 and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('push_dispatch_tick', 'push_daily_tick', 'push_live_enable', 'push_live_disable', 'push_doctor', 'push_on_queue_urgent')) = 6 and (select count(*) from pg_trigger where not tgisinternal and tgname = 'trg_push_queue_urgent') = 1 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'push_config' and column_name in ('function_url', 'cron_secret', 'enabled')) = 3)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'الجدولة: ' || (select count(*) from pg_extension where extname in ('pg_cron', 'pg_net'))::text || '/2 إضافة' || ' · الرابط محفوظ: ' || coalesce((select case when coalesce(btrim(function_url), '') <> '' then 'نعم' else 'لا' end from public.push_config where id = 1), 'لا') || ' · الأجهزة: ' || (select count(*) from public.push_devices where enabled)::text || ' · مُرسل 24 ساعة: ' || (select count(*) from public.push_queue where status = 'sent' and sent_at > now() - interval '24 hours')::text$q$), 'مفقود')
  union all
  select 59, '36_profile_stats', 'إحصاءات الملف الشخصي للجميع: أصدقاء · تنزيلات · مفضلة · مستوى · سلسلة',
         public.diag_text($q$select ((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'profile_stats') = 1 and (select count(*) from information_schema.parameters where specific_name in (select specific_name from information_schema.routines where routine_schema = 'public' and routine_name = 'profile_stats') and parameter_name in ('friends', 'downloads', 'favorites', 'level', 'xp_in_level', 'xp_to_next', 'streak', 'daily_claimed', 'daily_reward')) = 9 and (select prosecdef from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'profile_stats' limit 1))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'الدالة: ' || (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'profile_stats')::text || '/1 ' || '· security definer: ' || coalesce((select case when prosecdef then 'نعم' else 'لا' end from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'profile_stats' limit 1), 'لا')$q$), 'مفقود')
  union all
  select 60, '37_profile_reports', 'الإبلاغ عن عضو: عمود الهدف + دالة محمية + فهرس',
         public.diag_text($q$select ((select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'chat_reports' and column_name = 'target_user_id') = 1 and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'chat_report_user') = 1 and (select count(*) from pg_indexes where schemaname = 'public' and indexname = 'chat_reports_target_idx') = 1)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'العمود: ' || (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'chat_reports' and column_name = 'target_user_id')::text || '/1 ' || '· الدالة: ' || (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'chat_report_user')::text || '/1'$q$), 'مفقود')
  union all
  select 61, '38_user_mute · 39_notif_revert', 'كتم عضو بزر واحد والإشعارات كما كانت بلا إسقاط',
         public.diag_text($q$select ((select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'user_mutes') = 1 and (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('can_mute_members', 'user_mute', 'user_unmute', 'my_muted_users', 'i_muted_user')) = 5 and (select count(*) from pg_trigger where not tgisinternal and tgname = 'trg_push_drop_muted_actor') = 0 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'push_prefs' and column_name = 'muted_users') = 0 and (select count(*) from pg_policies where schemaname = 'public' and tablename = 'user_mutes' and policyname = 'user_mutes_read') = 1)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'الجدول: ' || (select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'user_mutes')::text || '/1 ' || '· الدوال: ' || (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('can_mute_members', 'user_mute', 'user_unmute', 'my_muted_users', 'i_muted_user'))::text || '/5 ' || '· المكتومون: ' || (select count(*) from public.user_mutes)::text$q$), 'مفقود')
  union all
  select 62, '41_console_link_codes', 'Secure console-link tables exist (3/3)',
         public.diag_text($q$select (count(*) = 3)::text from information_schema.tables where table_schema = 'public' and table_name in ('linked_consoles', 'console_link_codes', 'console_link_audit')$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || '/3' from information_schema.tables where table_schema = 'public' and table_name in ('linked_consoles', 'console_link_codes', 'console_link_audit')$q$), 'مفقود')
  union all
  select 63, '41_console_link_codes', 'Secure console-link RPCs exist (12/12)',
         public.diag_text($q$select (count(distinct p.proname) = 12)::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('console_private_normalize_code', 'console_private_random_code', 'console_code_create', 'console_code_lookup', 'console_code_approve', 'console_code_consume', 'console_code_complete', 'console_list', 'console_unlink', 'console_unlink_all', 'console_session_touch', 'console_link_cleanup')$q$) = 'true',
         coalesce(public.diag_text($q$select count(distinct p.proname)::text || '/12' from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname in ('console_private_normalize_code', 'console_private_random_code', 'console_code_create', 'console_code_lookup', 'console_code_approve', 'console_code_consume', 'console_code_complete', 'console_list', 'console_unlink', 'console_unlink_all', 'console_session_touch', 'console_link_cleanup')$q$), 'مفقود')
  union all
  select 64, '41_console_link_codes', 'Codes store only a poll-secret hash, never session tokens',
         public.diag_text($q$select (((select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'console_link_codes' and column_name = 'poll_secret_hash') = 1 and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'console_link_codes' and (column_name = concat('access', '_token') or column_name = concat('refresh', '_token'))) = 0))::text$q$) = 'true',
         coalesce(public.diag_text($q$select string_agg(column_name, ' · ' order by column_name) from information_schema.columns where table_schema = 'public' and table_name = 'console_link_codes' and (column_name = 'poll_secret_hash' or column_name = concat('access', '_token') or column_name = concat('refresh', '_token'))$q$), 'مفقود')
  union all
  select 65, '41_console_link_codes', 'Owner-only linked-console RLS policy exists',
         public.diag_text($q$select (count(*) = 1)::text from pg_policies where schemaname = 'public' and tablename = concat('linked', '_consoles') and policyname = 'linked_consoles_select_own'$q$) = 'true',
         coalesce(public.diag_text($q$select count(*)::text || '/1' from pg_policies where schemaname = 'public' and tablename = concat('linked', '_consoles') and policyname = 'linked_consoles_select_own'$q$), 'مفقود')
  union all
  select 66, '42_visible_profiles_access', 'visible_profiles readable while raw profiles stays private',
         public.diag_text($q$select (has_table_privilege('anon', 'public.visible_profiles', 'select') and has_table_privilege('authenticated', 'public.visible_profiles', 'select') and not has_table_privilege('anon', 'public.profiles', 'select') and not has_table_privilege('authenticated', 'public.profiles', 'select'))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'visible anon/auth: ' || has_table_privilege('anon', 'public.visible_profiles', 'select')::text || '/' || has_table_privilege('authenticated', 'public.visible_profiles', 'select')::text || ' · raw profiles anon/auth: ' || has_table_privilege('anon', 'public.profiles', 'select')::text || '/' || has_table_privilege('authenticated', 'public.profiles', 'select')::text$q$), 'مفقود')
  union all
  select 67, '43_content_download_integrity', 'downloads require an existing Storage object before recording',
         public.diag_text($q$select ((position('storage.objects' in pg_get_functiondef('public.record_content_download(uuid)'::regprocedure)) > 0) and (select count(*) from pg_trigger where not tgisinternal and tgname = 'content_items_require_uploaded_file') = 1 and to_regprocedure('public.ensure_published_content_file()') is not null)::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'record guard: ' || (position('storage.objects' in pg_get_functiondef('public.record_content_download(uuid)'::regprocedure)) > 0)::text || ' · publish trigger: ' || (select count(*) from pg_trigger where not tgisinternal and tgname = 'content_items_require_uploaded_file')::text$q$), 'مفقود')
  union all
  select 68, '44_fix_profile_asset_guard', 'trusted profile RPCs pass the strict protected-column trigger',
         public.diag_text($q$select ((position('app.allow_profile_write' in pg_get_functiondef('public.guard_profile_update()'::regprocedure)) > 0) and (position('app.allow_profile_write' in pg_get_functiondef('public.set_profile_asset(text,text)'::regprocedure)) > 0) and (position('app.allow_profile_write' in pg_get_functiondef('public.update_my_profile(text,text,text,boolean,text)'::regprocedure)) > 0))::text$q$) = 'true',
         coalesce(public.diag_text($q$select 'guard/RPCs: ' || (position('app.allow_profile_write' in pg_get_functiondef('public.guard_profile_update()'::regprocedure)) > 0)::text || '/' || (position('app.allow_profile_write' in pg_get_functiondef('public.set_profile_asset(text,text)'::regprocedure)) > 0)::text || '/' || (position('app.allow_profile_write' in pg_get_functiondef('public.update_my_profile(text,text,text,boolean,text)'::regprocedure)) > 0)::text$q$), 'مفقود')

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
         || coalesce(' · راجع بنداً في هذه الملفات: ' || (select string_agg(distinct f, ' · ')
                                             from unnest(array(select file_ from checks
                                                                where ok is not true)) f), '')
  from checks
 order by 1;

-- ---------------------------------------------------------------------------
--  ماذا بعد؟
--   • كل الصفوف ✅ ⇒ القاعدة مطابقة تماماً لكل ملفات SQL (01 → 41).
--   • الصف الأخير (المجموع) يسرد أسماء الملفات التي فيها بند ❌ — لا يعني أن
--     الملفات نفسها ناقصة، بل أن بنداً واحداً فيها لم يتحقّق: راجع عموده.
--   • ظهر ❌ في ملف معيّن ⇒ نفّذ ذلك الملف من `supabase/` في SQL Editor
--     (كله مرة واحدة)، ثم أعد تشغيل هذا الفاحص.
--   • ملف 15_assets_v2.sql يعطّل الأفاتارات المجانية القديمة بنفسه، و31
--     يثبّت onefree — فإن نفّذت 15 بعد 31، أعد تنفيذ 31 ليُفعَّل onefree وحده.
--   • لا تنسَ رفع ملفات الصور إلى المخزن: الفحص الأخير يتأكد من وجود
--     site-assets/profiles/onefree.png فعلاً.
--   • نقص البانرات أو «532 ← 492»؟ معناه أن 15 نُفِّذ بعد 21 ⇒ أعِد تنفيذ 21.
-- ---------------------------------------------------------------------------
