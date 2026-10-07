-- ============================================================================
--  38) كتم عضو — زرّ واحد: تُخفى رسائله **وإشعاراته** · ميزة اشتراك Legendary
-- ============================================================================
--  المطلوب من المالك (حرفياً): «نحتاج زر واحد وكتم الإشعارات وينفتح في اشتراك
--  ليغسي وايضا يكون في الاعدادت التطبيق زر اطفاء وتشغيل» — فيُنفَّذ هنا بند بند:
--
--  ① «زرّ واحد»: صفٌّ واحد في بطاقة السلامة (كتم هذا العضو / إلغاء الكتم)
--      يقوم بالأمرين معاً — لا زرّ للرسائل وزرّ للإشعارات.
--  ② «كتم الإشعارات»: المكتوم لا يصل منه **أي إشعار** (لا جرس في التطبيق ولا
--      دفع للهاتف). التنفيذ زناد BEFORE INSERT على `chat_notifications` يُلغي
--      صفّ الإشعار كاملاً حين يكون صاحبه مكتوماً ⇒ زناد الدفع (بعد الإدراج)
--      لا يعمل أصلاً، ولا يبقى شيء في الجرس. (ولا يمسّ رسائل الغرفة نفسها:
--      إخفاؤها يجري في التطبيق من قائمة المكتومين.)
--  ③ «ينفتح في اشتراك Legendary»: الكتم ميزة اشتراك Legendary — الخادم يرفض
--      `legendary_required` لمن لا اشتراك له (والإدارة/المشرفون مستثنون لتجربة
--      الميزة). والحظر والإبلاغ يبقيان للجميع (سياسة Google Play — لا يُقيَّدان).
--  ④ «زرّ اطفاء وتشغيل في الإعدادات»: عمود `push_prefs.muted_users` — أطفئه
--      فيبقى الكتم على الرسائل وحدها وتصل إشعارات المكتوم.
--
--  أسماء الكائنات كما يسأل عنها الفاحص: الجدول 'user_mutes' · العمود 'muted_users'
--  في push_prefs · الزناد 'trg_push_drop_muted_actor'.
--
--  آمن للتكرار · لا يحذف شيئاً · ويعيد تفعيل RLS على `user_blocks` (كانت
--  مُعطَّلة فيه، فيقرأ أيُّ مستخدمٍ كلَّ الحظرات — إصلاح أمني بسيط هنا).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) جدول الكتم: من كتم من
-- ---------------------------------------------------------------------------
create table if not exists public.user_mutes (
  muter_id   uuid not null references public.profiles(id) on delete cascade,
  muted_id   uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (muter_id, muted_id),
  constraint user_mutes_not_self check (muter_id <> muted_id)
);
create index if not exists user_mutes_muted_idx on public.user_mutes(muted_id);

comment on table public.user_mutes is
  'كتم عضو (ميزة Legendary): تُخفى رسائله في التطبيق **ولا يصل منه أي إشعار** — بلا أثر على الطرف الآخر.';

alter table public.user_mutes enable row level security;
drop policy if exists user_mutes_read on public.user_mutes;
create policy user_mutes_read on public.user_mutes
  for select using (muter_id = auth.uid());

revoke all on public.user_mutes from anon, authenticated;
grant select on public.user_mutes to authenticated;
grant all on public.user_mutes to service_role;

-- إصلاح أمني: الحظر كان بلا RLS مفعّلة (سياسة بلا تفعيل = كل الصفوف مقروءة).
-- مغلّف بشرطٍ لأن جدول `user_blocks` يأتي من ملف الحظر (33) وقد لا يكون على القاعدة بعد؛
-- فحينها لا تُسقط هذه الجملة بقية الملف.
do $do$
begin
  if to_regclass('public.user_blocks') is not null then
    execute 'alter table public.user_blocks enable row level security';
  end if;
end
$do$;

-- ---------------------------------------------------------------------------
-- 2) من يحقّ له الكتم؟ اشتراك Legendary (أو إدارة/مشرف للتجربة)
-- ---------------------------------------------------------------------------
create or replace function public.can_mute_members(p_user uuid default auth.uid())
returns boolean
language sql stable security definer set search_path = public
as $fn$
  select public.effective_plan(p_user) = 'legendary' or public.is_staff(p_user);
$fn$;

comment on function public.can_mute_members(uuid) is
  'هل يملك هذا المستخدم ميزة كتم الأعضاء؟ اشتراك Legendary (والإدارة/المشرف مستثنون للتجربة).';

-- ---------------------------------------------------------------------------
-- 3) دوال الاستعمال (للمستخدم نفسه فقط)
-- ---------------------------------------------------------------------------
create or replace function public.user_mute(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $fn$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if p_user_id is null or p_user_id = v_uid then
    return jsonb_build_object('ok', false, 'message', 'invalid_user');
  end if;
  if not public.can_mute_members(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'legendary_required');
  end if;
  if not exists (select 1 from public.profiles p where p.id = p_user_id) then
    return jsonb_build_object('ok', false, 'message', 'invalid_user');
  end if;

  insert into public.user_mutes(muter_id, muted_id)
  values (v_uid, p_user_id)
  on conflict (muter_id, muted_id) do nothing;

  return jsonb_build_object('ok', true);
end;
$fn$;

create or replace function public.user_unmute(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $fn$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  delete from public.user_mutes
   where muter_id = v_uid and muted_id = p_user_id;
  return jsonb_build_object('ok', true);
end;
$fn$;

/** من كتمتُهم أنا (يقرأها التطبيق ليخفي رسائلهم في الشات). */
create or replace function public.my_muted_users()
returns table (muted_id uuid)
language sql stable security definer set search_path = public
as $fn$
  select m.muted_id
    from public.user_mutes m
   where m.muter_id = auth.uid();
$fn$;

/** هل كتمتُ هذا العضو؟ (لبطاقة السلامة: «كتم» أم «إلغاء الكتم») */
create or replace function public.i_muted_user(p_user_id uuid)
returns boolean
language sql stable security definer set search_path = public
as $fn$
  select exists (
    select 1 from public.user_mutes m
     where m.muter_id = auth.uid() and m.muted_id = p_user_id
  );
$fn$;

/**
 * هل يُكتم هذا المرسل عند هذا المستلم الآن؟
 *   • صفّ في `user_mutes` (المستلم كاتم المرسل)، **و**
 *   • مفتاح «كتم إشعارات المكتومين» في push_prefs مُشغَّل (الافتراضي: مُشغَّل).
 */
create or replace function public.is_muted_by(p_recipient uuid, p_actor uuid)
returns boolean
language sql stable security definer set search_path = public
as $fn$
  select p_recipient is not null and p_actor is not null
     and exists (
       select 1 from public.user_mutes m
        where m.muter_id = p_recipient and m.muted_id = p_actor
     )
     and coalesce(
       (select pr.muted_users from public.push_prefs pr where pr.user_id = p_recipient),
       true
     );
$fn$;

comment on function public.is_muted_by(uuid, uuid) is
  'هل المستلم كاتم المرسل (ومفتاح كتم الإشعارات مُشغَّل)؟ يستعمله زناد إسقاط الإشعارات.';

-- ---------------------------------------------------------------------------
-- 4) زرّ الإطفاء/التشغيل في الإعدادات: عمود push_prefs.muted_users
--    + زناد يُسقط إشعارات المكتومين (لا جرس ولا دفع)
-- ---------------------------------------------------------------------------
alter table public.push_prefs
  add column if not exists muted_users boolean not null default true;

comment on column public.push_prefs.muted_users is
  'مفتاح الإعدادات: هل يوقف الكتمُ إشعارات المكتوم أيضاً؟ (أطفئه ⇒ الكتم على الرسائل وحدها).';

create or replace function public.push_drop_muted_actor()
returns trigger
language plpgsql security definer set search_path = public
as $fn$
begin
  -- BEFORE INSERT: إرجاع null يُلغي صفّ الإشعار ⇒ لا جرس ولا دفع،
  -- ولا يعمل زناد الدفع (وهو AFTER INSERT) أصلاً.
  if new.actor_id is not null and public.is_muted_by(new.recipient_id, new.actor_id) then
    return null;
  end if;
  return new;
end;
$fn$;

drop trigger if exists trg_push_drop_muted_actor on public.chat_notifications;
create trigger trg_push_drop_muted_actor
  before insert on public.chat_notifications
  for each row execute function public.push_drop_muted_actor();

-- ---------------------------------------------------------------------------
-- 5) حفظ المفتاح: push_set_prefs + وسيط واحد
--    (نُسقط النسخة القديمة أولاً وإلا صار لدينا نسختان يتحيّر PostgREST بينهما)
-- ---------------------------------------------------------------------------
drop function if exists public.push_set_prefs(
  boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int);

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
  p_reengage_days int default null,
  p_muted_users   boolean default null
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
         muted_users   = coalesce(p_muted_users, muted_users),
         updated_at    = now()
   where user_id = v_uid
   returning * into v;
  return v;
end;
$fn$;

-- ---------------------------------------------------------------------------
-- 6) المنح (بعد الإسقاط تُفقد المنح القديمة ⇒ نُعيدها)
-- ---------------------------------------------------------------------------
revoke all on function public.can_mute_members(uuid) from public, anon;
grant execute on function public.can_mute_members(uuid) to authenticated;
grant execute on function public.user_mute(uuid) to authenticated;
grant execute on function public.user_unmute(uuid) to authenticated;
grant execute on function public.my_muted_users() to authenticated;
grant execute on function public.i_muted_user(uuid) to authenticated;
grant execute on function public.push_set_prefs(boolean, boolean, boolean, boolean, boolean,
                                                boolean, boolean, int, int, int, boolean) to authenticated;

-- فهرس PostgREST يتحدّث فوراً (وإلا ردّ 404 لدالة جديدة — درس سابق)
notify pgrst, 'reload schema';
