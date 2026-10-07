-- ============================================================================
--  AddonStation Mobile — ٣٣) حظر المستخدمين (استيفاء سياسة Google Play للنصوص)
--
--  لماذا هذا الملف إلزامي قبل النشر؟
--      سياسة Google Play للمحتوى الذي ينشئه المستخدمون (User Generated Content)
--      تشترط على كل تطبيق فيه شات أو محتوى عام:
--        • نظام إبلاغ داخل التطبيق عن المحتوى والمستخدمين  ✅ موجود (chat_reports)
--        • **وسيلة لحظر المستخدمين**                        ❌ كان ناقصاً — وهو هذا الملف
--        • قبول الشروط/القواعد قبل النشر                    ✅ (chat_rule_acceptances
--          و send_chat_message يرفض بلاها برسالة rules_required)
--        • إشراف فعّال (حذف/كتم/منع/سجل)                    ✅ موجود (03 و29)
--
--  كيف يعمل الحظر هنا؟ (وبأقل كود ممكن، وبلا تعديل استعلامات التطبيق)
--      التطبيق يقرأ الرسائل من الجدول مباشرةً عبر PostgREST، والقراءة تمرّ
--      بسياسة RLS واحدة: `messages_read`. فأضفنا إلى هذه السياسة شرطاً واحداً:
--      «لا تظهر رسائل أي طرف بينك وبينه حظر — في الاتجاهين». وبذلك:
--        ✓ كل مسارات القراءة تُفلتَر تلقائياً: الرسائل · messagesSince (الاستقصاء)
--          · البحث · المثبّتة · وحتى بثّ Realtime (يحترم RLS أيضاً).
--        ✓ صفر تغيير في استعلامات التطبيق ⇒ لا احتمال خطأ في الصياغة.
--      والحظر **متقابل**: لا تراه ولا يراك — وهو ما يتوقّعه المستخدم.
--
--  آمن لإعادة التنفيذ في أي وقت (create table if not exists · create or replace
--  · drop policy if exists + create policy). لا يحذف أي رسالة ولا يمسّ أي محتوى.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) جدول الحظر: من حظر من
-- ---------------------------------------------------------------------------
create table if not exists public.user_blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  reason     text,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint user_blocks_not_self check (blocker_id <> blocked_id)
);
-- البحث العكسي («هل حظرني أحد؟») يستعمل هذا الفهرس، وبه تبقى سياسة القراءة رخيصة
create index if not exists user_blocks_blocked_idx on public.user_blocks(blocked_id);

comment on table public.user_blocks is
  'حظر بين مستخدمَين: الحظر متقابل في الشات (لا يرى أيٌّ منهما رسائل الآخر) — سياسة messages_read.';

-- ---------------------------------------------------------------------------
-- 2) دالة الفحص: هل بين هذين المستخدمَين حظر (بأي اتجاه)؟
--    security definer: تُستدعى داخل سياسة RLS فلا تتأثر بسياسات user_blocks
--    نفسها (لا تعاود ولا تدور)، وstable حتى لا تُعاد لكل سطر بلا داعٍ.
-- ---------------------------------------------------------------------------
create or replace function public.is_blocked_pair(p_a uuid, p_b uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select p_a is not null and p_b is not null and exists (
    select 1 from public.user_blocks b
     where (b.blocker_id = p_a and b.blocked_id = p_b)
        or (b.blocker_id = p_b and b.blocked_id = p_a)
  );
$$;

-- ---------------------------------------------------------------------------
-- 3) سياسات القراءة على جدول الحظر: صاحب الحظر يرى حظراته هو فقط.
--    (المحظور لا يعلم أنه محظور — فلا يفتح باب انتقام ولا يشعر بالإهانة.)
-- ---------------------------------------------------------------------------
alter table public.user_blocks enable row level security;

drop policy if exists user_blocks_read on public.user_blocks;
create policy user_blocks_read on public.user_blocks
  for select using (blocker_id = auth.uid());

-- الكتابة **بلا سياسة** عمداً: لا insert/update/delete مباشرة من التطبيق إطلاقاً؛
-- الحظر وإلغاؤه عبر دالتي security definer أدناه فقط (فلا يستطيع أحد حظر نفسه
-- ولا حظر غير موجود ولا التلاعب بالجدول).
revoke all on public.user_blocks from anon, authenticated;
grant select on public.user_blocks to authenticated;
grant all on public.user_blocks to service_role;

-- ---------------------------------------------------------------------------
-- 4) القلب: سياسة قراءة الرسائل تُخفي كل طرف بينك وبينه حظر
--    (هذه نسخة ٣٠ `messages_read` + سطر الحظر. من أراد التراجع: §٥ أدناه.)
-- ---------------------------------------------------------------------------
drop policy if exists messages_read on public.chat_messages;
create policy messages_read on public.chat_messages
  for select using (
    auth.uid() is not null
    and not public.is_blocked_pair(auth.uid(), chat_messages.user_id)
  );

-- الاختيار الصارم: الجدول أعلى سياسة `30_chat_members_only` مباشرة — وهذا
-- التعريف هو السائد (create policy لا تتراكم؛ آخر تعريف يُطبَّق).
-- الزائر (anon) لا يقرأ رسالة كما كان: auth.uid() is not null شرط باقٍ.

-- ---------------------------------------------------------------------------
-- 5) دالتا الحظر والإلغاء (security definer — كل التحقق في الخادم)
--    user_block:    يحظر مستخدماً · user_unblock: يلغي الحظر
--    وكلاهما يُرجع jsonb بلغة موحّدة كنمط المشروع: {ok, message}
-- ---------------------------------------------------------------------------
create or replace function public.user_block(p_user_id uuid, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_target public.profiles;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;
  if p_user_id is null or p_user_id = v_uid then
    return jsonb_build_object('ok', false, 'message', 'self_block');
  end if;

  select * into v_target from public.profiles where id = p_user_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'user_not_found');
  end if;
  -- لا يُحظر حساب إدارة (حماية الإشراف من انتقام مُشرف محظور)
  if v_target.role = 'admin' then
    return jsonb_build_object('ok', false, 'message', 'cannot_block_admin');
  end if;

  insert into public.user_blocks(blocker_id, blocked_id, reason)
  values (v_uid, p_user_id, nullif(btrim(coalesce(p_reason, '')), ''))
  on conflict (blocker_id, blocked_id) do nothing;

  -- تنظيف العلاقة: أي طلب صداقة قائم بينهما يُحذف في الاتجاهين
  -- (فلا يبقى زر «قبول الصداقة» لحساب محظور).
  delete from public.user_friends
   where (requester_id = v_uid and receiver_id = p_user_id)
      or (requester_id = p_user_id and receiver_id = v_uid);

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'user_block', 'profiles', p_user_id::text,
          jsonb_build_object('reason', p_reason));

  return jsonb_build_object('ok', true);
end $$;

create or replace function public.user_unblock(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  delete from public.user_blocks
   where blocker_id = v_uid and blocked_id = p_user_id;

  return jsonb_build_object('ok', true);
end $$;

-- قائمة المحظورين عندي (بأسمائهم وصورهم لعرضها في الإعدادات/الملف الشخصي)
create or replace function public.my_blocked_users()
returns table (
  user_id    uuid,
  username   text,
  full_name  text,
  avatar_url text,
  blocked_at timestamptz
)
language sql stable security definer set search_path = public
as $$
  select b.blocked_id, p.username, p.full_name, p.avatar_url, b.created_at
    from public.user_blocks b
    join public.profiles p on p.id = b.blocked_id
   where b.blocker_id = auth.uid()
   order by b.created_at desc;
$$;

-- (اختياري للتطبيق) هل أنا حاظر هذا المستخدم؟ — لعرض زر «إلغاء الحظر» بدل «حظر»
create or replace function public.i_blocked_user(p_user_id uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.user_blocks
     where blocker_id = auth.uid() and blocked_id = p_user_id
  );
$$;

-- ---------------------------------------------------------------------------
-- 6) حماية طلب الصداقة: لا يُقبل ولا يُرسل بين طرفَين بينهما حظر
--    (نسخة كاملة من `send_friend_request` في 03 + سطر الفحص.)
-- ---------------------------------------------------------------------------
create or replace function public.send_friend_request(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if v_uid = p_user_id then return jsonb_build_object('ok', false, 'message', 'self_request'); end if;
  if public.is_blocked_pair(v_uid, p_user_id) then
    return jsonb_build_object('ok', false, 'message', 'blocked');
  end if;

  if exists(select 1 from public.user_friends
            where (requester_id = v_uid and receiver_id = p_user_id)
               or (requester_id = p_user_id and receiver_id = v_uid)) then
    return jsonb_build_object('ok', false, 'message', 'already_exists');
  end if;

  insert into public.user_friends(requester_id, receiver_id) values (v_uid, p_user_id);

  insert into public.chat_notifications(recipient_id, actor_id, type, body)
  values (p_user_id, v_uid, 'friend_request', 'New friend request', jsonb_build_object('from', v_uid));

  return jsonb_build_object('ok', true);
end $$;

-- ---------------------------------------------------------------------------
-- 7) الصلاحيات: الدوال الجديدة للمسجّلين فقط
-- ---------------------------------------------------------------------------
revoke all on function public.is_blocked_pair(uuid, uuid) from public;
grant execute on function public.is_blocked_pair(uuid, uuid) to anon, authenticated;
grant execute on function public.user_block(uuid, text) to authenticated;
grant execute on function public.user_unblock(uuid) to authenticated;
grant execute on function public.my_blocked_users() to authenticated;
grant execute on function public.i_blocked_user(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 8) التحقق — نفّذ هذا الملف ثم انظر إلى الجدولين أدناه
-- ---------------------------------------------------------------------------
select 'user_blocks' as "المكوّن",
       (select count(*) from information_schema.tables
         where table_schema = 'public' and table_name = 'user_blocks')::text as "الموجود",
       '1' as "المتوقّع"
union all
select 'دالة is_blocked_pair',
       (select count(*) from pg_proc where proname = 'is_blocked_pair')::text, '1'
union all
select 'دالة user_block',
       (select count(*) from pg_proc where proname = 'user_block')::text, '1'
union all
select 'دالة user_unblock',
       (select count(*) from pg_proc where proname = 'user_unblock')::text, '1'
union all
select 'دالة my_blocked_users',
       (select count(*) from pg_proc where proname = 'my_blocked_users')::text, '1'
union all
select 'دالة i_blocked_user',
       (select count(*) from pg_proc where proname = 'i_blocked_user')::text, '1'
union all
select 'سياسة messages_read تحجب المحظور',
       (select count(*)::text from pg_policies
         where schemaname = 'public' and tablename = 'chat_messages'
           and policyname = 'messages_read' and qual like '%is_blocked_pair%'), '1'
union all
select 'سياسة user_blocks_read',
       (select count(*)::text from pg_policies
         where schemaname = 'public' and tablename = 'user_blocks'
           and policyname = 'user_blocks_read'), '1';

do $$
begin
  raise notice '٣٣) الحظر جاهز: الشات متقابل بين الحاظر والمحظور، وطلبات الصداقة تُحذف بينهما.';
end $$;
