-- ============================================================================
--  AddonStation Mobile — ٢٩) إشراف الشات: حذف أي رسالة + نطاق غرف المشرف
--
--  طلب المالك (حرفياً):
--      «تمكين الأدمن والمشرف من حذف أي رسالة في الشات مهما كانت مدة إرسالها،
--       ويتم منح المشرفين بالوصول للإشراف إلى غرف معينة — أقدر أخليه مشرفاً
--       على غرفة واحدة أو كل الغرف.»
--
--  ما يفعله هذا الملف:
--   ١) جدول public.chat_moderator_rooms: نطاق إشراف كل مشرف
--      • صفّ بـ all_rooms = true        ⇒ مشرف على **كل الغرف**
--      • صفوف بـ channel_id محدَّد      ⇒ مشرف على **غرف بعينها**
--      • بلا صفوف                       ⇒ لا إشراف له على أي غرفة.
--   ٢) دالة public.chat_can_moderate_channel(channel) — الحَكَم الوحيد:
--      الأدمن = كل الغرف دائماً، والمشرف = غرفه فقط (أو كلها إن عُمِّم).
--   ٣) دالة public.chat_delete_message_staff(message) — **حذف أي رسالة**
--      بلا أي شرط زمني (نافذة الثلاث دقائق تبقى على المستخدم العادي فقط،
--      والملف 17_message_delete_window.sql لا يُمسّ).
--   ٤) تقييد «إخفاء/إظهار» و«تثبيت» بنطاق غرف المشرف (كانا مفتوحين لكل
--      المشرفين على كل الغرف).
--   ٥) دوال للوحة الإدارة: قائمة المشرفين ونطاقاتهم + تعيين النطاق.
--   ٦) my_chat_status تُرجع نطاق إشراف المستخدم (لتخفي/تظهر الأزرار في
--      الواجهة قبل أن يرفض الخادم).
--
--  آمن لإعادة التنفيذ في أي وقت (create if not exists + create or replace).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) جدول نطاق إشراف المشرفين
-- ---------------------------------------------------------------------------
create table if not exists public.chat_moderator_rooms (
  id          bigserial primary key,
  user_id     uuid not null references public.profiles(id) on delete cascade,
  channel_id  text references public.chat_channels(id) on delete cascade,
  all_rooms   boolean not null default false,
  created_by  uuid references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now(),
  -- إمّا «كل الغرف» أو غرفة محدَّدة بالاسم
  constraint chat_moderator_rooms_scope_chk
    check (all_rooms = true or channel_id is not null),
  unique (user_id, channel_id)
);

create index if not exists chat_moderator_rooms_user_idx
  on public.chat_moderator_rooms(user_id);
create index if not exists chat_moderator_rooms_channel_idx
  on public.chat_moderator_rooms(channel_id);

comment on table public.chat_moderator_rooms is
  'نطاق إشراف المشرفين على غرف الشات: all_rooms=true ⇒ كل الغرف، وإلا الغرف المدرجة.';

-- ---------------------------------------------------------------------------
-- 2) هل يستطيع المستخدم الحالي الإشراف على هذه الغرفة؟ (الحَكَم الوحيد)
-- ---------------------------------------------------------------------------
create or replace function public.chat_can_moderate_channel(p_channel_id text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null or coalesce(btrim(p_channel_id), '') = '' then
    return false;
  end if;

  -- الأدمن يشرف على كل الغرف دائماً
  if public.is_admin(v_uid) then
    return true;
  end if;

  -- غير المشرف لا إشراف له
  if public.current_role_of(v_uid) <> 'moderator' then
    return false;
  end if;

  return exists (
    select 1
      from public.chat_moderator_rooms r
     where r.user_id = v_uid
       and (r.all_rooms or r.channel_id = p_channel_id)
  );
end;
$$;

comment on function public.chat_can_moderate_channel(text) is
  'هل يملك المستخدم الحالي حق الإشراف على هذه الغرفة؟ (الأدمن: دائماً · المشرف: غرفه فقط)';

-- غرف المستخدم (تستعملها my_chat_status لتُخبر التطبيق بنطاقه)
create or replace function public.chat_my_moderation_scope()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid    uuid := auth.uid();
  v_role   text;
  v_all    boolean := false;
  v_ids    text[] := '{}';
begin
  if v_uid is null then
    return jsonb_build_object('all_rooms', false, 'channels', '[]'::jsonb);
  end if;

  v_role := public.current_role_of(v_uid);
  if v_role = 'admin' then
    return jsonb_build_object('all_rooms', true, 'channels', '[]'::jsonb);
  end if;

  if v_role = 'moderator' then
    select bool_or(r.all_rooms), coalesce(array_agg(r.channel_id) filter (where r.channel_id is not null), '{}')
      into v_all, v_ids
      from public.chat_moderator_rooms r
     where r.user_id = v_uid;
  end if;

  return jsonb_build_object(
    'all_rooms', coalesce(v_all, false),
    'channels', to_jsonb(coalesce(v_ids, '{}'::text[]))
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3) حذف أي رسالة مهما كان عمرها — للأدمن ولمشرف الغرفة فقط
--    (النافذة الزمنية في chat_delete_my_message تبقى على صاحب الرسالة وحده)
-- ---------------------------------------------------------------------------
create or replace function public.chat_delete_message_staff(p_message_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_msg public.chat_messages;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_msg from public.chat_messages where id = p_message_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  -- الحَكَم: الأدمن مطلقاً، والمشرف داخل غرفه فقط
  if not public.chat_can_moderate_channel(v_msg.channel_id) then
    return jsonb_build_object('ok', false, 'message', 'room_not_assigned');
  end if;

  if v_msg.status = 'deleted' then
    return jsonb_build_object('ok', false, 'message', 'already_deleted');
  end if;

  update public.chat_messages
     set status = 'deleted',
         content = null,
         sticker_url = null,
         image_path = null,
         media_path = null,
         media_mime_type = null,
         media_duration = null,
         media_size = null,
         is_pinned = false,
         deleted_by = v_uid
   where id = p_message_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'message_deleted_staff', 'chat_message', p_message_id::text,
          jsonb_build_object('channel_id', v_msg.channel_id, 'author', v_msg.user_id));

  return jsonb_build_object('ok', true, 'message_id', p_message_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 4) إخفاء/إظهار وتثبيت: داخل نطاق غرف المشرف (لا كل الغرف بلا حساب)
-- ---------------------------------------------------------------------------
create or replace function public.chat_moderate_message(p_message_id bigint, p_hide boolean default true)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_msg public.chat_messages;
begin
  select * into v_msg from public.chat_messages where id = p_message_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if not public.chat_can_moderate_channel(v_msg.channel_id) then
    return jsonb_build_object('ok', false, 'message', 'not_staff');
  end if;

  update public.chat_messages
     set status = case when p_hide then 'hidden' else 'published' end,
         deleted_by = auth.uid()
   where id = p_message_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when p_hide then 'message_hidden' else 'message_restored' end,
          'chat_message', p_message_id::text,
          jsonb_build_object('channel_id', v_msg.channel_id));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_toggle_pin(p_message_id bigint)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_msg public.chat_messages;
  v_pins int;
begin
  select * into v_msg from public.chat_messages where id = p_message_id for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if not public.chat_can_moderate_channel(v_msg.channel_id) then
    return jsonb_build_object('ok', false, 'message', 'not_staff');
  end if;

  if not v_msg.is_pinned then
    select count(*) into v_pins from public.chat_messages
     where channel_id = v_msg.channel_id and is_pinned;
    if v_pins >= 50 then
      return jsonb_build_object('ok', false, 'message', 'too_many_pins');
    end if;
  end if;

  update public.chat_messages set is_pinned = not is_pinned where id = p_message_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when v_msg.is_pinned then 'message_unpinned' else 'message_pinned' end,
          'chat_message', p_message_id::text, jsonb_build_object('channel_id', v_msg.channel_id));

  return jsonb_build_object('ok', true, 'pinned', not v_msg.is_pinned);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5) لوحة الإدارة: قائمة المشرفين ونطاقاتهم + تعيين النطاق
-- ---------------------------------------------------------------------------

/** قائمة المشرفين (role = moderator) مع الغرف التي يشرفون عليها — للأدمن فقط. */
create or replace function public.chat_moderator_list()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;

  -- ملاحظة: التجميع يجري في استعلام داخلي ثم يُغلَّف بمصفوفة — لأن PostgreSQL
  -- لا يقبل تجميعاً داخل تجميع في نفس المستوى (aggregate calls cannot be nested).
  return coalesce((
    select jsonb_agg(x.row)
      from (
        select jsonb_build_object(
                 'user_id', p.id,
                 'username', p.username,
                 'display_name', p.display_name,
                 'avatar_url', p.avatar_url,
                 'all_rooms', coalesce(bool_or(r.all_rooms), false),
                 'channels', coalesce(
                   jsonb_agg(r.channel_id) filter (where r.channel_id is not null),
                   '[]'::jsonb)
               ) as row
          from public.profiles p
          left join public.chat_moderator_rooms r on r.user_id = p.id
         where p.role = 'moderator'
         group by p.id, p.username, p.display_name, p.avatar_url
         order by p.username
      ) x
  ), '[]'::jsonb);
end;
$$;

/**
 * تعيين نطاق إشراف مشرف: كل الغرف (p_all_rooms) أو غرف بعينها (p_channel_ids).
 * يستبدل النطاق السابق كاملاً (حذف ثم إدراج) — للأدمن فقط.
 */
create or replace function public.chat_set_moderator_rooms(
  p_user_id      uuid,
  p_channel_ids  text[] default null,
  p_all_rooms    boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id text;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;
  if p_user_id is null then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  delete from public.chat_moderator_rooms where user_id = p_user_id;

  if coalesce(p_all_rooms, false) or public.current_role_of(p_user_id) = 'admin' then
    insert into public.chat_moderator_rooms(user_id, all_rooms, created_by)
    values (p_user_id, true, auth.uid());
  else
    foreach v_id in array coalesce(p_channel_ids, '{}'::text[]) loop
      if exists (select 1 from public.chat_channels where id = v_id) then
        insert into public.chat_moderator_rooms(user_id, channel_id, all_rooms, created_by)
        values (p_user_id, v_id, false, auth.uid())
        on conflict do nothing;
      end if;
    end loop;
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'moderator_rooms_updated', 'profile', p_user_id::text,
          jsonb_build_object('all_rooms', coalesce(p_all_rooms, false),
                             'channels', to_jsonb(coalesce(p_channel_ids, '{}'::text[]))));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6) my_chat_status تُرجع نطاق الإشراف (لتظهر أزرار الإشراف في مكانها فقط)
-- ---------------------------------------------------------------------------
create or replace function public.my_chat_status()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid      uuid := auth.uid();
  v_mute     public.chat_mutes;
  v_muted    boolean := false;
  v_banned   boolean;
  v_accepted boolean;
  v_scope    jsonb;
begin
  if v_uid is null then return jsonb_build_object('signed_in', false); end if;

  select * into v_mute from public.chat_mutes
   where user_id = v_uid and active and (expires_at is null or expires_at > now())
   order by created_at desc limit 1;
  -- ⚠️ تُقرأ النتيجة **فوراً**: أي SELECT بعدها يبدّل FOUND (وهو ما كان يجعل
  -- «مكتوم» تُقرأ من نتيجة استعلام لاحق بدل نتيجة هذا البحث).
  v_muted := found;

  select exists(select 1 from public.chat_bans where user_id = v_uid and active) into v_banned;
  select exists(select 1 from public.chat_rule_acceptances where user_id = v_uid) into v_accepted;

  if public.is_admin(v_uid) then
    v_scope := jsonb_build_object('all_rooms', true, 'channels', '[]'::jsonb);
  else
    v_scope := public.chat_my_moderation_scope();
  end if;

  return jsonb_build_object(
    'signed_in', true,
    'muted', v_muted,
    'muted_until', v_mute.expires_at,
    'banned', v_banned,
    'accepted_rules', v_accepted,
    'role', public.current_role_of(v_uid),
    'plan', public.effective_plan(v_uid),
    'moderates_all', coalesce((v_scope->>'all_rooms')::boolean, false),
    'moderated_channels', coalesce(v_scope->'channels', '[]'::jsonb)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) حماية الجدول (RLS) وصلاحيات التنفيذ
-- ---------------------------------------------------------------------------
alter table public.chat_moderator_rooms enable row level security;

drop policy if exists "mod_rooms_admin_all" on public.chat_moderator_rooms;
create policy "mod_rooms_admin_all" on public.chat_moderator_rooms
  for all to authenticated
  using (public.is_admin())
  with check (public.is_admin());

drop policy if exists "mod_rooms_self_read" on public.chat_moderator_rooms;
create policy "mod_rooms_self_read" on public.chat_moderator_rooms
  for select to authenticated
  using (user_id = auth.uid());

grant select on public.chat_moderator_rooms to authenticated;
grant execute on function public.chat_can_moderate_channel(text) to authenticated, service_role;
grant execute on function public.chat_my_moderation_scope() to authenticated, service_role;
grant execute on function public.chat_delete_message_staff(bigint) to authenticated, service_role;
grant execute on function public.chat_moderator_list() to authenticated, service_role;
grant execute on function public.chat_set_moderator_rooms(uuid, text[], boolean) to authenticated, service_role;
grant execute on function public.chat_moderate_message(bigint, boolean) to authenticated, service_role;
grant execute on function public.chat_toggle_pin(bigint) to authenticated, service_role;
grant execute on function public.my_chat_status() to authenticated, anon, service_role;

-- ---------------------------------------------------------------------------
-- 8) تحقّق بعد التنفيذ: يطبع نطاق إشرافك وعدد المشرفين
-- ---------------------------------------------------------------------------
do $$
declare
  v_scope jsonb;
  v_mods  int;
begin
  v_scope := public.chat_my_moderation_scope();
  select count(*) into v_mods from public.profiles where role = 'moderator';
  raise notice 'نطاق إشرافك: % · عدد المشرفين: %', v_scope, v_mods;
end;
$$;

-- ============================================================================
--  خُلاصة السلوك بعد هذا الملف:
--    • المستخدم العادي  → يحذف رسالته خلال ٣ دقائق فقط (الملف 17 كما هو).
--    • الأدمن            → يحذف/يخفي/يثبّت أي رسالة في أي غرفة بلا حدّ زمني.
--    • المشرف            → يحذف/يخفي/يثبّت في **غرفه فقط** (أو كلها إن عُمِّم)
--                          بلا حدّ زمني، ويبقى الكتم/الحظرGlobal كما كان.
--    • لوحة الإدارة      → تعيين: مشرف على غرفة واحدة · عدّة غرف · كل الغرف.
-- ============================================================================
