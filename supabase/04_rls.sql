-- ============================================================================
--  AddonStation Mobile — 04) أمان الصفوف (Row Level Security)
--  القاعدة: لا يثق التطبيق بشيء — كل صلاحية تُفرض هنا على الخادم
-- ============================================================================

-- تشغيل RLS على كل الجداول
do $$
declare t text;
begin
  foreach t in array array[
    'profiles','subscription_plans','payment_method_settings','payment_orders',
    'content_items','content_favorites','content_downloads',
    'ac_wallets','ac_transactions','ac_tasks','ac_task_claims','ac_events',
    'chat_channels','chat_messages','chat_reactions','chat_mutes','chat_bans','chat_reports',
    'chat_rule_acceptances','chat_rules','chat_typing','chat_read_state','chat_notifications',
    'user_friends','creator_applications','creator_submissions','featured_creators','creator_follows',
    'creator_tips','site_assets','sticker_packs','app_settings','admin_audit_log'
  ]
  loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------
drop policy if exists profiles_select_all on public.profiles;
create policy profiles_select_all on public.profiles
  for select using (true);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
  for update using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists profiles_admin_all on public.profiles;
create policy profiles_admin_all on public.profiles
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- subscription_plans / payment_method_settings
-- ---------------------------------------------------------------------------
drop policy if exists plans_select on public.subscription_plans;
create policy plans_select on public.subscription_plans
  for select using (true);

drop policy if exists plans_admin_write on public.subscription_plans;
create policy plans_admin_write on public.subscription_plans
  for all using (public.is_admin()) with check (public.is_admin());

-- المستخدم العادي يرى الطرق المفعّلة فقط (ليعرف بيانات التحويل)
drop policy if exists pms_select_enabled on public.payment_method_settings;
create policy pms_select_enabled on public.payment_method_settings
  for select using (enabled = true or public.is_admin());

drop policy if exists pms_admin_write on public.payment_method_settings;
create policy pms_admin_write on public.payment_method_settings
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- payment_orders (قراءة فقط من التطبيق — الإنشاء والتعديل عبر الدوال)
-- ---------------------------------------------------------------------------
drop policy if exists orders_select_own on public.payment_orders;
create policy orders_select_own on public.payment_orders
  for select using (user_id = auth.uid() or public.is_admin());

-- ---------------------------------------------------------------------------
-- content_items
-- ---------------------------------------------------------------------------
drop policy if exists content_select_public on public.content_items;
create policy content_select_public on public.content_items
  for select using (
    is_published = true
    or created_by = auth.uid()
    or public.is_admin()
  );

drop policy if exists content_admin_write on public.content_items;
create policy content_admin_write on public.content_items
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists fav_own on public.content_favorites;
create policy fav_own on public.content_favorites
  for all using (user_id = auth.uid() or public.is_admin())
  with check (user_id = auth.uid());

drop policy if exists downloads_own on public.content_downloads;
create policy downloads_own on public.content_downloads
  for select using (user_id = auth.uid() or public.is_admin());

-- ---------------------------------------------------------------------------
-- AC (قراءة فقط — كل الحركات عبر الدوال)
-- ---------------------------------------------------------------------------
drop policy if exists ac_wallet_own on public.ac_wallets;
create policy ac_wallet_own on public.ac_wallets
  for select using (user_id = auth.uid() or public.is_admin());

drop policy if exists ac_tx_own on public.ac_transactions;
create policy ac_tx_own on public.ac_transactions
  for select using (user_id = auth.uid() or public.is_admin());

drop policy if exists ac_tasks_read on public.ac_tasks;
create policy ac_tasks_read on public.ac_tasks
  for select using (active = true or public.is_admin());

drop policy if exists ac_tasks_admin on public.ac_tasks;
create policy ac_tasks_admin on public.ac_tasks
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists ac_claims_own on public.ac_task_claims;
create policy ac_claims_own on public.ac_task_claims
  for select using (user_id = auth.uid() or public.is_admin());

drop policy if exists ac_events_admin on public.ac_events;
create policy ac_events_admin on public.ac_events
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- الشات
-- ---------------------------------------------------------------------------
drop policy if exists channels_read on public.chat_channels;
create policy channels_read on public.chat_channels
  for select using (true);

drop policy if exists channels_admin on public.chat_channels;
create policy channels_admin on public.chat_channels
  for all using (public.is_admin()) with check (public.is_admin());

-- الرسائل: القراءة للجميع (الزائر يقرأ)، الكتابة عبر send_chat_message فقط
drop policy if exists messages_read on public.chat_messages;
create policy messages_read on public.chat_messages
  for select using (true);

drop policy if exists reactions_read on public.chat_reactions;
create policy reactions_read on public.chat_reactions
  for select using (true);

drop policy if exists reactions_own on public.chat_reactions;
create policy reactions_own on public.chat_reactions
  for insert with check (user_id = auth.uid());

drop policy if exists reactions_delete_own on public.chat_reactions;
create policy reactions_delete_own on public.chat_reactions
  for delete using (user_id = auth.uid());

drop policy if exists mutes_read on public.chat_mutes;
create policy mutes_read on public.chat_mutes
  for select using (user_id = auth.uid() or public.is_staff());

drop policy if exists mutes_staff_write on public.chat_mutes;
create policy mutes_staff_write on public.chat_mutes
  for all using (public.is_staff()) with check (public.is_staff());

drop policy if exists bans_read on public.chat_bans;
create policy bans_read on public.chat_bans
  for select using (user_id = auth.uid() or public.is_staff());

drop policy if exists bans_staff_write on public.chat_bans;
create policy bans_staff_write on public.chat_bans
  for all using (public.is_staff()) with check (public.is_staff());

drop policy if exists reports_read on public.chat_reports;
create policy reports_read on public.chat_reports
  for select using (reporter_id = auth.uid() or public.is_staff());

drop policy if exists reports_insert on public.chat_reports;
create policy reports_insert on public.chat_reports
  for insert with check (reporter_id = auth.uid());

drop policy if exists reports_staff_update on public.chat_reports;
create policy reports_staff_update on public.chat_reports
  for update using (public.is_staff()) with check (public.is_staff());

drop policy if exists rule_accept_own on public.chat_rule_acceptances;
create policy rule_accept_own on public.chat_rule_acceptances
  for select using (user_id = auth.uid() or public.is_staff());

drop policy if exists rule_accept_insert on public.chat_rule_acceptances;
create policy rule_accept_insert on public.chat_rule_acceptances
  for insert with check (user_id = auth.uid());

drop policy if exists rules_read on public.chat_rules;
create policy rules_read on public.chat_rules
  for select using (true);

drop policy if exists rules_admin on public.chat_rules;
create policy rules_admin on public.chat_rules
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists typing_read on public.chat_typing;
create policy typing_read on public.chat_typing
  for select using (auth.uid() is not null);

drop policy if exists typing_own on public.chat_typing;
create policy typing_own on public.chat_typing
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists read_state_own on public.chat_read_state;
create policy read_state_own on public.chat_read_state
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists notif_own on public.chat_notifications;
create policy notif_own on public.chat_notifications
  for select using (recipient_id = auth.uid());

drop policy if exists notif_update_own on public.chat_notifications;
create policy notif_update_own on public.chat_notifications
  for update using (recipient_id = auth.uid()) with check (recipient_id = auth.uid());

-- ---------------------------------------------------------------------------
-- الأصدقاء والمبدعون
-- ---------------------------------------------------------------------------
drop policy if exists friends_own on public.user_friends;
create policy friends_own on public.user_friends
  for select using (requester_id = auth.uid() or receiver_id = auth.uid() or public.is_admin());

drop policy if exists apps_own on public.creator_applications;
create policy apps_own on public.creator_applications
  for select using (user_id = auth.uid() or public.is_admin());

drop policy if exists apps_insert on public.creator_applications;
create policy apps_insert on public.creator_applications
  for insert with check (user_id = auth.uid());

drop policy if exists submissions_own on public.creator_submissions;
create policy submissions_own on public.creator_submissions
  for select using (creator_id = auth.uid() or public.is_admin());

drop policy if exists submissions_admin_write on public.creator_submissions;
create policy submissions_admin_write on public.creator_submissions
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists featured_read on public.featured_creators;
create policy featured_read on public.featured_creators
  for select using (true);

drop policy if exists featured_admin on public.featured_creators;
create policy featured_admin on public.featured_creators
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists follows_read on public.creator_follows;
create policy follows_read on public.creator_follows
  for select using (true);

drop policy if exists follows_own on public.creator_follows;
create policy follows_own on public.creator_follows
  for all using (follower_id = auth.uid()) with check (follower_id = auth.uid());

drop policy if exists tips_read on public.creator_tips;
create policy tips_read on public.creator_tips
  for select using (sender_id = auth.uid() or creator_id = auth.uid() or public.is_admin());

drop policy if exists tips_admin_write on public.creator_tips;
create policy tips_admin_write on public.creator_tips
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- الأصول والإعدادات والسجل
-- ---------------------------------------------------------------------------
drop policy if exists assets_read on public.site_assets;
create policy assets_read on public.site_assets
  for select using (
    public.is_admin()
    or (enabled = true and required_plan <> 'admin')
  );

drop policy if exists assets_admin on public.site_assets;
create policy assets_admin on public.site_assets
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists packs_read on public.sticker_packs;
create policy packs_read on public.sticker_packs
  for select using (active = true or public.is_admin());

drop policy if exists packs_admin on public.sticker_packs;
create policy packs_admin on public.sticker_packs
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists settings_read on public.app_settings;
create policy settings_read on public.app_settings
  for select using (auth.uid() is not null);

drop policy if exists settings_admin on public.app_settings;
create policy settings_admin on public.app_settings
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists audit_admin_read on public.admin_audit_log;
create policy audit_admin_read on public.admin_audit_log
  for select using (public.is_admin());

-- ---------------------------------------------------------------------------
-- سحب أي صلاحية كتابة مباشرة على الجداول الحساسة (تتم عبر الدوال فقط)
-- ---------------------------------------------------------------------------
revoke insert, update, delete on public.payment_orders from anon, authenticated;
revoke insert, update, delete on public.ac_wallets from anon, authenticated;
revoke insert, update, delete on public.ac_transactions from anon, authenticated;
revoke insert, update, delete on public.ac_task_claims from anon, authenticated;
revoke insert, update, delete on public.content_downloads from anon, authenticated;
revoke insert, update, delete on public.admin_audit_log from anon, authenticated;
revoke insert, update, delete on public.chat_messages from anon, authenticated;
revoke insert, update, delete on public.chat_notifications from anon, authenticated;
revoke insert, update, delete on public.creator_tips from anon, authenticated;
revoke insert, update, delete on public.creator_submissions from anon, authenticated;
revoke insert, update, delete on public.creator_applications from anon, authenticated;
revoke insert, update, delete on public.user_friends from anon, authenticated;

-- بعد تنفيذ 40_admin_asset_privacy تُقرأ الملفات الشخصية من العرض المنقّح فقط.
-- نبقي توافق الإعداد الأول: إن لم يُنشأ العرض بعد، تعمل النسخ القديمة مؤقتاً؛
-- وإعادة تنفيذ هذا الملف لاحقاً لا تعيد فتح الجدول الأساسي بالخطأ.
do $$
begin
  if to_regclass('public.visible_profiles') is not null then
    revoke select on public.profiles from public, anon, authenticated;
    grant select on public.visible_profiles to anon, authenticated;
  else
    grant select on public.profiles to anon, authenticated;
  end if;
end;
$$;

grant update (bio, custom_status, show_online_status, last_seen_at, country, display_name) on public.profiles to authenticated;
