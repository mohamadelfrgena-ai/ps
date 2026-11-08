-- ============================================================================
--  39) إرجاع الإشعارات كما كانت — تنظيف ما نُفِّذ سابقاً
-- ============================================================================
--  قرار المالك: «اعني الشعارات رجعها كما كنت» ⇒ تُزال آثار «كتم إشعارات
--  المكتومين» التي نُفِّذت على القاعدة، فيرجع الإشعار يجي من أي عضو مهما كان
--  مكتوماً، وترجع دالة `push_set_prefs` بعشرة وسائط كما في 34، ويختفي عمود
--  `push_prefs.muted_users` (لم يبقَ له مستعمل في التطبيق ولا في الخادم).
--
--  الكتم نفسه باقٍ كما هو (`user_mutes` في 38): رسائل المكتوم تُخفى في التطبيق،
--  والإشعارات لا شأن لها به.
--
--  آمن للتكرار · على قاعدةٍ جديدة لا يجد شيئاً فيُكمل بلا أثر.
--  أسماء الكائنات كما يسأل عنها الفاحص: الزناد 'trg_push_drop_muted_actor' ·
--  الدالة 'push_drop_muted_actor' · 'is_muted_by' · العمود 'muted_users'.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) زناد إسقاط إشعارات المكتومين — يُزال
-- ---------------------------------------------------------------------------
drop trigger if exists trg_push_drop_muted_actor on public.chat_notifications;
drop function if exists public.push_drop_muted_actor();
drop function if exists public.is_muted_by(uuid, uuid);

-- ---------------------------------------------------------------------------
-- 2) عمود المفتاح في الإعدادات — يُزال
-- ---------------------------------------------------------------------------
alter table public.push_prefs drop column if exists muted_users;

-- ---------------------------------------------------------------------------
-- 3) push_set_prefs: نسخة 34 بعشرة وسائط (بلا p_muted_users)
--    (نُسقط النسخة الأحد عشرية أولاً وإلا صار لدينا نسختان يتحيّر PostgREST)
-- ---------------------------------------------------------------------------
drop function if exists public.push_set_prefs(
  boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int, boolean);

create or replace function public.push_set_prefs(
  p_master        boolean default null,
  p_chat          boolean default null,
  p_content       boolean default null,
  p_social        boolean default null,
  p_account       boolean default null,
  p_reminders     boolean default null,
  p_quiet_enabled boolean default null,
  p_quiet_from    int default null,
  p_quiet_to      int default null,
  p_reengage_days int default null
)
returns public.push_prefs
language plpgsql security definer set search_path = public
as $fn$
declare v_uid uuid := auth.uid(); v public.push_prefs;
begin
  if v_uid is null then return null; end if;
  perform public.push_prefs_for(v_uid);

  update public.push_prefs
     set master        = coalesce(p_master, master),
         chat          = coalesce(p_chat, chat),
         content       = coalesce(p_content, content),
         social        = coalesce(p_social, social),
         account       = coalesce(p_account, account),
         reminders     = coalesce(p_reminders, reminders),
         quiet_enabled = coalesce(p_quiet_enabled, quiet_enabled),
         quiet_from    = coalesce(p_quiet_from, quiet_from),
         quiet_to      = coalesce(p_quiet_to, quiet_to),
         reengage_days = coalesce(p_reengage_days, reengage_days),
         updated_at    = now()
   where user_id = v_uid
   returning * into v;
  return v;
end $fn$;

revoke all on function public.push_set_prefs(
  boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int) from public, anon;
grant execute on function public.push_set_prefs(
  boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int) to authenticated;

-- ---------------------------------------------------------------------------
-- 4) تحقّق سريع: لا زناد إسقاط · لا عمود muted_users · والعشر وسائط قائمة
-- ---------------------------------------------------------------------------
select 'زناد إسقاط إشعارات المكتومين' as "المكوّن",
       (select count(*) from pg_trigger where tgname = 'trg_push_drop_muted_actor'
          and not tgisinternal)::text as "الموجود", '0' as "المتوقّع"
union all select 'عمود push_prefs.muted_users',
       (select count(*) from information_schema.columns
         where table_schema = 'public' and table_name = 'push_prefs'
           and column_name = 'muted_users')::text, '0'
union all select 'وسائط push_set_prefs',
       (select count(*) from information_schema.parameters p
          join information_schema.routines r on r.specific_name = p.specific_name
         where r.routine_schema = 'public' and r.routine_name = 'push_set_prefs')::text, '10';

notify pgrst, 'reload schema';
