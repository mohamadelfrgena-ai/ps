-- ============================================================================
--  38) كتم عضو — زرّ واحد: تُخفى رسائله · ميزة اشتراك Legendary
-- ============================================================================
--  طلب المالك: «نحتاج زر واحد ... وينفتح في اشتراك ليغسي» — ثم قراره الأخير:
--  «اعني الشعارات رجعها كما كنت» ⇒ **الإشعارات رجعت كما كانت**:  الكتم لا يمسّ
--  الإشعارات إطلاقاً (لا إسقاط صفٍّ ولا مفتاح في الإعدادات ولا عمود جديد)،
--  وإنما يمنع الرسائل: تُخفى رسائل المكتوم في التطبيق من قائمة المكتومين،
--  والقائمة تأتي من هذا الملف (user_mutes) فتسري على كل أجهزة المستخدم.
--
--  ① «زرّ واحد»: صفٌّ واحد في بطاقة السلامة (كتم هذا العضو / إلغاء الكتم) —
--      لا زرّ للرسائل وزرّ للإشعارات.
--  ② «ينفتح في اشتراك Legendary»: الكتم ميزة اشتراك Legendary — الخادم يرفض
--      `legendary_required` لمن لا اشتراك له (والإدارة/المشرفون مستثنون لتجربة
--      الميزة). والحظر والإبلاغ يبقيان للجميع (سياسة Google Play — لا يُقيَّدان).
--  ③ الإشعارات: لا شأن لها بهذا الملف — زناد الإشعارات (34) وطابور الدفع (35)
--      يعملان كما كانا لكل الرسائل، مكتوماً كان المرسل أو غير مكتوم.
--
--  أسماء الكائنات كما يسأل عنها الفاحص: الجدول 'user_mutes' · الدوال
--  'can_mute_members' · 'user_mute' · 'user_unmute' · 'my_muted_users' ·
--  'i_muted_user' · وسياسة 'user_mutes_read'.
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

comment on table public.user_mutes is
  'كتم عضو: صاحب الصفّ (muter_id) لا يرى رسائل (muted_id) في التطبيق — ولا شأن للإشعارات به (قرار المالك: الإشعارات كما كانت).';

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

comment on function public.user_mute(uuid) is
  'كتم عضو (رسائله فقط): يشترط اشتراك Legendary — ويُخزَّن على الخادم فتسري القائمة على كل الأجهزة.';

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

comment on function public.user_unmute(uuid) is
  'إلغاء كتم عضو — متاح للجميع (الإلغاء لا يحتاج اشتراكاً).';

-- نُسقطها أولاً لأن نسخةً سابقة على القاعدة ترجع أعمدةً أكثر (PostgreSQL
-- يرفض تغيير نوع الإرجاع بـ`create or replace` وحده: 42P13).
drop function if exists public.my_muted_users();

create or replace function public.my_muted_users()
returns table (muted_id uuid, created_at timestamptz)
language sql stable security definer set search_path = public
as $fn$
  select m.muted_id, m.created_at
    from public.user_mutes m
   where m.muter_id = auth.uid()
   order by m.created_at desc;
$fn$;

comment on function public.my_muted_users() is
  'قائمة المكتومين عندي — يقرأها التطبيق ليُخفي رسائلهم.';

create or replace function public.i_muted_user(p_user_id uuid)
returns boolean
language sql stable security definer set search_path = public
as $fn$
  select exists (
    select 1 from public.user_mutes
     where muter_id = auth.uid() and muted_id = p_user_id
  );
$fn$;

comment on function public.i_muted_user(uuid) is
  'هل أنا كاتم هذا العضو؟ — لعرض صفّ «إلغاء الكتم» بدل «كتم العضو».';

-- ---------------------------------------------------------------------------
-- 4) المنح
-- ---------------------------------------------------------------------------
revoke all on function public.can_mute_members(uuid) from public, anon;
grant execute on function public.can_mute_members(uuid) to authenticated;
grant execute on function public.user_mute(uuid) to authenticated;
grant execute on function public.user_unmute(uuid) to authenticated;
grant execute on function public.my_muted_users() to authenticated;
grant execute on function public.i_muted_user(uuid) to authenticated;

notify pgrst, 'reload schema';
