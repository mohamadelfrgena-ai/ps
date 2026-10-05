-- ============================================================================
--  AddonStation Mobile — 03) دوال الشات والإشراف والإشعارات والمبدعين
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) إعدادات الفلترة التلقائية (من app_settings أو القيم الافتراضية)
-- ---------------------------------------------------------------------------
create or replace function public.app_setting(p_key text)
returns jsonb
language sql stable security definer set search_path = public
as $$
  select coalesce((select value from public.app_settings where key = p_key), '{}'::jsonb);
$$;

-- فحص محتوى الرسالة قبل الإرسال: روابط، أرقام هواتف، بريد، كلمات محظورة
create or replace function public.chat_content_check(p_content text)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare
  v_cfg jsonb := public.app_setting('automod');
  v_text text := lower(coalesce(p_content,''));
  v_word text;
  v_blocked jsonb := coalesce(v_cfg->'blocked_words', '[]'::jsonb);
begin
  -- الروابط (مفعّل افتراضياً)
  if coalesce((v_cfg->>'block_links')::boolean, true) then
    if v_text ~ '(https?://|www\.|\.com|\.net|\.org|t\.me/|discord\.gg|bit\.ly)' then
      return jsonb_build_object('ok', false, 'reason', 'links_not_allowed');
    end if;
  end if;

  -- الهاتف/البريد (مفعّل افتراضياً)
  if coalesce((v_cfg->>'block_personal')::boolean, true) then
    if v_text ~ '[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}'
       or v_text ~ '(\+?\d[\s\-()]?){9,}' then
      return jsonb_build_object('ok', false, 'reason', 'personal_data_not_allowed');
    end if;
  end if;

  -- الكلمات المحظورة
  for v_word in select jsonb_array_elements_text(v_blocked) loop
    if v_word <> '' and position(lower(v_word) in v_text) > 0 then
      return jsonb_build_object('ok', false, 'reason', 'blocked_word');
    end if;
  end loop;

  -- تكرار الحروف المزعج (مثل aaaaaaa)
  if v_text ~ '(.)\1{9,}' then
    return jsonb_build_object('ok', false, 'reason', 'spam_pattern');
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 1) إرسال رسالة في الشات (كل الفحوص على الخادم)
-- ---------------------------------------------------------------------------
create or replace function public.send_chat_message(
  p_channel_id   text,
  p_content      text default null,
  p_reply_to_id  bigint default null,
  p_thread_root_id bigint default null,
  p_sticker_url  text default null,
  p_image_path   text default null,
  p_message_type text default 'text',
  p_media_path   text default null,
  p_media_mime   text default null,
  p_media_duration int default null,
  p_media_size   bigint default null,
  p_is_announcement boolean default false
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid      uuid := auth.uid();
  v_channel  public.chat_channels;
  v_role     text;
  v_cfg      jsonb;
  v_rate     int;
  v_recent   int;
  v_last     timestamptz;
  v_mute     public.chat_mutes;
  v_mentions uuid[] := '{}';
  v_msg_id   bigint;
  v_check    jsonb;
  v_names    text[];
  v_name     text;
  v_parent   public.chat_messages;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_channel from public.chat_channels where id = p_channel_id;
  if not found then return jsonb_build_object('ok', false, 'message', 'channel_not_found'); end if;

  v_role := public.current_role_of(v_uid);

  -- حظر من الشات
  if exists(select 1 from public.chat_bans where user_id = v_uid and active) then
    return jsonb_build_object('ok', false, 'message', 'banned');
  end if;

  -- كتم مؤقت
  select * into v_mute from public.chat_mutes
   where user_id = v_uid and active and (expires_at is null or expires_at > now())
   order by created_at desc limit 1;
  if found then
    return jsonb_build_object('ok', false, 'message', 'muted', 'until', v_mute.expires_at);
  end if;

  if v_channel.is_locked and v_role not in ('admin','moderator') then
    return jsonb_build_object('ok', false, 'message', 'room_locked');
  end if;

  if v_channel.post_permission = 'creators_admin' and v_role not in ('creator','admin','moderator') then
    return jsonb_build_object('ok', false, 'message', 'creators_only');
  end if;
  if v_channel.post_permission = 'admin_only' and v_role <> 'admin' then
    return jsonb_build_object('ok', false, 'message', 'admins_only');
  end if;

  -- slowmode
  if v_channel.slowmode_seconds > 0 and v_role not in ('admin','moderator') then
    select max(created_at) into v_last from public.chat_messages
     where channel_id = p_channel_id and user_id = v_uid;
    if v_last is not null and v_last + make_interval(secs => v_channel.slowmode_seconds) > now() then
      return jsonb_build_object('ok', false, 'message', 'slowmode',
        'retry_after', ceil(extract(epoch from (v_last + make_interval(secs => v_channel.slowmode_seconds) - now()))));
    end if;
  end if;

  -- حد المعدل: عدد رسائل خلال 10 ثوان
  v_cfg := public.app_setting('automod');
  v_rate := coalesce((v_cfg->>'rate_limit_10s')::int, 5);
  if v_role not in ('admin','moderator') then
    select count(*) into v_recent from public.chat_messages
     where user_id = v_uid and created_at > now() - interval '10 seconds';
    if v_recent >= v_rate then
      return jsonb_build_object('ok', false, 'message', 'rate_limited');
    end if;
  end if;

  if p_message_type = 'text' then
    if coalesce(btrim(p_content),'') = '' then
      return jsonb_build_object('ok', false, 'message', 'empty_message');
    end if;
    if char_length(p_content) > 500 then
      return jsonb_build_object('ok', false, 'message', 'too_long');
    end if;

    v_check := public.chat_content_check(p_content);
    if not (v_check->>'ok')::boolean then
      return jsonb_build_object('ok', false, 'message', v_check->>'reason');
    end if;

    -- استخراج المنشنات @username
    v_names := regexp_matches(p_content, '@([A-Za-z0-9_\.]{3,32})', 'g');
    if v_names is not null then
      foreach v_name in array v_names loop
        v_mentions := v_mentions || (select array_agg(id) from public.profiles where username = v_name);
      end loop;
    end if;
    -- ⚠️ تنبيه: لا يجوز استعمال اسم المخرَج (unnest) داخل عبارة WHERE في
    -- PostgreSQL — أسماء أعمدة المخرجات تُرى في ORDER BY/GROUP BY فقط ⇒ خطأ
    -- 42703: column "unnest" does not exist · وكان يُفشل كل إرسال رسالة نصية.
    -- الصحيح: unnest في عبارة FROM مع اسم مستعار (كما هو مكتوب بالأسفل).
    select coalesce(array_agg(distinct m), '{}'::uuid[])
      into v_mentions
      from unnest(coalesce(v_mentions, '{}'::uuid[])) as m
     where m is not null;
  end if;

  -- ==========================================================================
  --  الوسائط الثقيلة **للأدمن فقط**: مقاطع الفيديو · الملفات الصوتية · التسجيلات
  --  الصوتية. أي مستخدم آخر (حتى المشرف moderator) يُرفض طلبه من الخادم.
  -- ==========================================================================
  if p_message_type in ('audio','video') and v_role <> 'admin' then
    return jsonb_build_object('ok', false, 'message', 'admin_only_media');
  end if;

  if p_message_type = 'image' and coalesce(p_image_path,'') = '' then
    return jsonb_build_object('ok', false, 'message', 'missing_image');
  end if;

  if p_message_type in ('audio','video') and coalesce(p_media_path,'') = '' then
    return jsonb_build_object('ok', false, 'message', 'missing_media');
  end if;

  insert into public.chat_messages(
      channel_id, user_id, content, message_type, sticker_url, image_path,
      media_path, media_mime_type, media_duration, media_size,
      reply_to_id, thread_root_id, mentions, is_announcement)
  values (
      p_channel_id, v_uid, btrim(p_content), coalesce(p_message_type,'text'), p_sticker_url, p_image_path,
      p_media_path, p_media_mime, p_media_duration, p_media_size,
      p_reply_to_id, p_thread_root_id, v_mentions,
      coalesce(p_is_announcement, false) and public.is_admin(v_uid))
  returning id into v_msg_id;

  -- إشعار من ذُكر
  if array_length(v_mentions, 1) > 0 then
    insert into public.chat_notifications(recipient_id, actor_id, type, message_id, channel_id, body)
    select m, v_uid, 'mention', v_msg_id, p_channel_id,
           left(coalesce(p_content,''), 80)
      from unnest(v_mentions) as m
     where m <> v_uid;
  end if;

  -- إشعار صاحب الرسالة عند الرد
  if p_reply_to_id is not null then
    select * into v_parent from public.chat_messages where id = p_reply_to_id;
    if found and v_parent.user_id <> v_uid then
      insert into public.chat_notifications(recipient_id, actor_id, type, message_id, channel_id, body)
      values (v_parent.user_id, v_uid, 'reply', v_msg_id, p_channel_id, left(coalesce(p_content,''), 80));
    end if;
  end if;

  -- إشعار نشاط الموضوع
  if p_thread_root_id is not null then
    select * into v_parent from public.chat_messages where id = p_thread_root_id;
    if found and v_parent.user_id <> v_uid then
      insert into public.chat_notifications(recipient_id, actor_id, type, message_id, channel_id, body)
      values (v_parent.user_id, v_uid, 'thread', v_msg_id, p_channel_id, 'New reply in your thread');
    end if;
  end if;

  -- تحديث حالة القراءة الخاصة بالمرسل
  insert into public.chat_read_state(user_id, channel_id, last_read_id)
  values (v_uid, p_channel_id, v_msg_id)
  on conflict (user_id, channel_id) do update set last_read_id = v_msg_id, updated_at = now();

  return jsonb_build_object('ok', true, 'message_id', v_msg_id,
                            'mentions', to_jsonb(v_mentions));
end;
$$;

-- ---------------------------------------------------------------------------
-- 2) حذف/إخفاء الرسائل
--
--    قاعدة الحذف الذاتي: **٣ دقائق فقط بعد الإرسال** (Source of truth هنا في القاعدة،
--    والتطبيق يخفي الخيار بعد انتهاء المدة ويعرض سبب الرفض لو حاول أحد تجاوزه).
--    بعد المدة: المستخدم لا يستطيع الحذف، ويبقى أمامه الإبلاغ ليتولّى الفريق الإشراف.
-- ---------------------------------------------------------------------------
create or replace function public.chat_delete_window_seconds()
returns integer
language sql immutable
as $$ select 180 $$;   -- 3 دقائق

create or replace function public.chat_delete_my_message(p_message_id bigint)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_owner   uuid;
  v_created timestamptz;
  v_status  text;
  v_window  integer := public.chat_delete_window_seconds();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select user_id, created_at, status
    into v_owner, v_created, v_status
    from public.chat_messages
   where id = p_message_id;

  if v_owner is null then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  if v_owner <> v_uid then return jsonb_build_object('ok', false, 'message', 'forbidden'); end if;
  if v_status = 'deleted' then return jsonb_build_object('ok', false, 'message', 'already_deleted'); end if;

  if now() - v_created > make_interval(secs => v_window) then
    return jsonb_build_object(
      'ok', false,
      'message', 'delete_window_expired',
      'window_seconds', v_window,
      'age_seconds', floor(extract(epoch from (now() - v_created)))::int
    );
  end if;

  update public.chat_messages
     set status = 'deleted', content = null, sticker_url = null, image_path = null,
         media_path = null, deleted_by = v_uid
   where id = p_message_id and user_id = v_uid;

  if not found then return jsonb_build_object('ok', false, 'message', 'forbidden'); end if;
  return jsonb_build_object('ok', true, 'window_seconds', v_window);
end;
$$;

create or replace function public.chat_moderate_message(p_message_id bigint, p_hide boolean default true)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;

  update public.chat_messages
     set status = case when p_hide then 'hidden' else 'published' end,
         deleted_by = auth.uid()
   where id = p_message_id;

  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when p_hide then 'message_hidden' else 'message_restored' end,
          'chat_message', p_message_id::text, '{}'::jsonb);

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
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;

  select * into v_msg from public.chat_messages where id = p_message_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

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
          'chat_message', p_message_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true, 'pinned', not v_msg.is_pinned);
end;
$$;

-- ---------------------------------------------------------------------------
-- 3) التفاعلات
-- ---------------------------------------------------------------------------
create or replace function public.chat_toggle_reaction(p_message_id bigint, p_emoji text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_exists boolean;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if char_length(coalesce(p_emoji,'')) = 0 then
    return jsonb_build_object('ok', false, 'message', 'invalid_emoji');
  end if;

  select exists(select 1 from public.chat_reactions
                where message_id = p_message_id and user_id = v_uid and emoji = p_emoji) into v_exists;

  if v_exists then
    delete from public.chat_reactions
     where message_id = p_message_id and user_id = v_uid and emoji = p_emoji;
    return jsonb_build_object('ok', true, 'reacted', false);
  else
    insert into public.chat_reactions(message_id, user_id, emoji)
    values (p_message_id, v_uid, p_emoji) on conflict do nothing;
    return jsonb_build_object('ok', true, 'reacted', true);
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4) الإشراف: كتم / فك كتم / حظر من الشات / تقارير
-- ---------------------------------------------------------------------------
create or replace function public.chat_mute_user(
  p_user_id uuid, p_minutes int default 60, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;
  if public.is_admin(p_user_id) then
    return jsonb_build_object('ok', false, 'message', 'cannot_mute_admin');
  end if;

  insert into public.chat_mutes(user_id, reason, expires_at, created_by, active)
  values (p_user_id, p_reason,
          case when coalesce(p_minutes,0) > 0 then now() + make_interval(mins => p_minutes) else null end,
          auth.uid(), true);

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'user_muted', 'profile', p_user_id::text,
          jsonb_build_object('minutes', p_minutes, 'reason', p_reason));

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (p_user_id, auth.uid(), 'system',
          case when coalesce(p_minutes,0) > 0
               then 'You were muted for ' || p_minutes || ' minutes'
               else 'You were muted' end,
          jsonb_build_object('reason', p_reason));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_unmute_user(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;

  update public.chat_mutes set active = false where user_id = p_user_id and active;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'user_unmuted', 'profile', p_user_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_ban_user(p_user_id uuid, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;
  if public.is_admin(p_user_id) then
    return jsonb_build_object('ok', false, 'message', 'cannot_ban_admin');
  end if;

  insert into public.chat_bans(user_id, reason, created_by, active)
  values (p_user_id, p_reason, auth.uid(), true);

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'user_chat_banned', 'profile', p_user_id::text, jsonb_build_object('reason', p_reason));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_unban_user(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;

  update public.chat_bans set active = false where user_id = p_user_id and active;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'user_chat_unbanned', 'profile', p_user_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_report_message(
  p_message_id bigint, p_reason text, p_note text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if p_reason not in ('spam','insult','inappropriate','other') then
    return jsonb_build_object('ok', false, 'message', 'invalid_reason');
  end if;

  insert into public.chat_reports(message_id, reporter_id, reason, note)
  values (p_message_id, v_uid, p_reason, p_note);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_close_report(p_report_id bigint, p_action text default 'close')
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_report public.chat_reports;
begin
  if not public.is_staff() then return jsonb_build_object('ok', false, 'message', 'not_staff'); end if;

  select * into v_report from public.chat_reports where id = p_report_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  if p_action = 'hide' then
    update public.chat_messages set status = 'hidden', deleted_by = auth.uid()
     where id = v_report.message_id;
  end if;

  update public.chat_reports
     set status = 'closed', closed_by = auth.uid(), closed_at = now()
   where id = p_report_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'report_' || p_action, 'chat_report', p_report_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5) إدارة الغرف (Admin) + إعلانات + مؤشر الكتابة + حالة القراءة
-- ---------------------------------------------------------------------------
create or replace function public.chat_admin_upsert_room(
  p_id text,
  p_name text,
  p_icon text default null,
  p_category text default 'main',
  p_description text default null,
  p_sort_order int default 0,
  p_is_locked boolean default false,
  p_slowmode_seconds int default 0,
  p_post_permission text default 'members'
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  insert into public.chat_channels(id, name, icon, category, description, sort_order,
                                   is_locked, slowmode_seconds, post_permission)
  values
  ('general'              , 'General'             , NULL, 'main', 'General chat for the community'         , 1, false, 0, 'members'),
  ('minecraft'            , 'Minecraft'           , NULL, 'main', 'Minecraft talk: worlds, addons, servers', 2, false, 0, 'members'),
  ('creator'              , 'Creator'             , NULL, 'main', 'For creators and developers'            , 3, false, 0, 'members'),
  ('support'              , 'Support'             , NULL, 'main', 'Technical help and troubleshooting'     , 4, false, 0, 'members'),
  ('social-media-makers'  , 'Social Media Makers' , NULL, 'main', 'For content makers on social media'     , 5, false, 0, 'members'),
  ('lang-ar'              , 'العربية'             , NULL, 'lang', 'الغرفة العربية'                         , 1, false, 0, 'members'),
  ('lang-ru'              , 'Русский'             , NULL, 'lang', 'Русская комната'                        , 2, false, 0, 'members'),
  ('lang-pt'              , 'Português'           , NULL, 'lang', 'Sala em português'                      , 3, false, 0, 'members'),
  ('lang-zh'              , '中文'                  , NULL, 'lang', '中文聊天室'                                  , 4, false, 0, 'members'),
  ('lang-en'              , 'English'             , NULL, 'lang', 'English room'                           , 5, false, 0, 'members'),
  ('lang-es'              , 'Español'             , NULL, 'lang', 'Sala en español'                        , 6, false, 0, 'members'),
  ('lang-fr'              , 'Français'            , NULL, 'lang', 'Salon français'                         , 7, false, 0, 'members'),
  ('lang-tr'              , 'Türkçe'              , NULL, 'lang', 'Türkçe sohbet odası'                    , 8, false, 0, 'members')
on conflict (id) do update
     set name = excluded.name,
         icon = excluded.icon,
         category = excluded.category,
         description = excluded.description,
         sort_order = excluded.sort_order,
         is_locked = excluded.is_locked,
         slowmode_seconds = excluded.slowmode_seconds,
         post_permission = excluded.post_permission;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'room_upserted', 'chat_channel', p_id, jsonb_build_object('name', p_name));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_admin_delete_room(p_id text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  delete from public.chat_channels where id = p_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'room_deleted', 'chat_channel', p_id, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.chat_send_announcement(
  p_channel_id text default null, p_content text default null, p_as_message boolean default true)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  r record;
  v_count int := 0;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if coalesce(btrim(p_content),'') = '' then
    return jsonb_build_object('ok', false, 'message', 'empty_message');
  end if;
  if char_length(p_content) > 500 then
    return jsonb_build_object('ok', false, 'message', 'too_long');
  end if;

  if p_as_message then
    for r in select id from public.chat_channels
             where p_channel_id is null or id = p_channel_id
    loop
      insert into public.chat_messages(channel_id, user_id, content, message_type, is_announcement)
      values (r.id, v_uid, p_content, 'announcement', true);
      v_count := v_count + 1;
    end loop;
  end if;

  -- إشعار عام داخل التطبيق
  insert into public.chat_notifications(recipient_id, actor_id, type, channel_id, body, meta)
  select p.id, v_uid, 'system', p_channel_id, p_content, jsonb_build_object('announcement', true)
    from public.profiles p;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'announcement_sent', 'chat_channel', coalesce(p_channel_id,'all'),
          jsonb_build_object('rooms', v_count, 'content', left(p_content, 120)));

  return jsonb_build_object('ok', true, 'rooms', v_count);
end;
$$;

create or replace function public.chat_set_typing(p_channel_id text)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return; end if;
  insert into public.chat_typing(channel_id, user_id, updated_at)
  values (p_channel_id, auth.uid(), now())
  on conflict (channel_id, user_id) do update set updated_at = now();
end;
$$;

create or replace function public.chat_mark_read(p_channel_id text, p_last_id bigint)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return; end if;
  insert into public.chat_read_state(user_id, channel_id, last_read_id, updated_at)
  values (auth.uid(), p_channel_id, coalesce(p_last_id,0), now())
  on conflict (user_id, channel_id)
    do update set last_read_id = greatest(public.chat_read_state.last_read_id, coalesce(p_last_id,0)),
                  updated_at = now();
end;
$$;

-- عدّاد غير المقروء + المنشنات لكل غرفة
create or replace function public.chat_unread_counts()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v jsonb;
begin
  if v_uid is null then return '{}'::jsonb; end if;

  select coalesce(jsonb_object_agg(c.id, jsonb_build_object(
           'unread', coalesce((
              select count(*) from public.chat_messages m
              where m.channel_id = c.id
                and m.id > coalesce(rs.last_read_id, 0)
                and m.user_id <> v_uid
                and m.status = 'published'), 0),
           'mentions', coalesce((
              select count(*) from public.chat_messages m
              where m.channel_id = c.id
                and m.id > coalesce(rs.last_read_id, 0)
                and v_uid = any(m.mentions)
                and m.status = 'published'), 0)
         )), '{}'::jsonb)
    into v
    from public.chat_channels c
    left join public.chat_read_state rs on rs.channel_id = c.id and rs.user_id = v_uid;

  return v;
end;
$$;

-- حالة إرسال المستخدم الحالي (كتم/حظر) — تُستخدم لقفل شريط الكتابة
create or replace function public.my_chat_status()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_mute public.chat_mutes;
  v_banned boolean;
  v_accepted boolean;
begin
  if v_uid is null then return jsonb_build_object('signed_in', false); end if;

  select * into v_mute from public.chat_mutes
   where user_id = v_uid and active and (expires_at is null or expires_at > now())
   order by created_at desc limit 1;

  select exists(select 1 from public.chat_bans where user_id = v_uid and active) into v_banned;
  select exists(select 1 from public.chat_rule_acceptances where user_id = v_uid) into v_accepted;

  return jsonb_build_object(
    'signed_in', true,
    'muted', found,
    'muted_until', v_mute.expires_at,
    'banned', v_banned,
    'accepted_rules', v_accepted,
    'role', public.current_role_of(v_uid),
    'plan', public.effective_plan(v_uid)
  );
end;
$$;

create or replace function public.chat_accept_rules(p_version text default 'v1')
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  insert into public.chat_rule_acceptances(user_id, accepted_at, version)
  values (auth.uid(), now(), coalesce(p_version,'v1'))
  on conflict (user_id) do update set accepted_at = now(), version = excluded.version;

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6) الإشعارات والأصدقاء
-- ---------------------------------------------------------------------------
create or replace function public.mark_notifications_read(p_ids bigint[] default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if p_ids is null then
    update public.chat_notifications set is_read = true
     where recipient_id = auth.uid() and not is_read;
  else
    update public.chat_notifications set is_read = true
     where recipient_id = auth.uid() and id = any(p_ids);
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.send_friend_request(p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if v_uid = p_user_id then return jsonb_build_object('ok', false, 'message', 'self_request'); end if;

  if exists(select 1 from public.user_friends
            where (requester_id = v_uid and receiver_id = p_user_id)
               or (requester_id = p_user_id and receiver_id = v_uid)) then
    return jsonb_build_object('ok', false, 'message', 'already_exists');
  end if;

  insert into public.user_friends(requester_id, receiver_id) values (v_uid, p_user_id);

  insert into public.chat_notifications(recipient_id, actor_id, type, body)
  values (p_user_id, v_uid, 'friend_request', 'New friend request', jsonb_build_object('from', v_uid));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.respond_friend_request(p_request_id bigint, p_accept boolean)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_req public.user_friends;
begin
  if auth.uid() is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_req from public.user_friends
   where id = p_request_id and receiver_id = auth.uid() for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  update public.user_friends
     set status = case when p_accept then 'accepted' else 'rejected' end,
         updated_at = now()
   where id = p_request_id;

  return jsonb_build_object('ok', true, 'status', case when p_accept then 'accepted' else 'rejected' end);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) المبدعون: الطلبات، النشر، الدعم (Tips)
-- ---------------------------------------------------------------------------
create or replace function public.submit_creator_application(
  p_country text default null,
  p_content_types text default null,
  p_about text default null,
  p_experience text default null,
  p_publishing_plan text default null,
  p_previous_work_url text default null,
  p_portfolio_urls text default null,
  p_wallet_links text default null,
  p_motivation text default null,
  p_bio text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid(); v_id bigint;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if exists(select 1 from public.creator_applications where user_id = v_uid and status = 'pending') then
    return jsonb_build_object('ok', false, 'message', 'already_pending');
  end if;

  insert into public.creator_applications(user_id, country, content_types, about, experience,
                                          publishing_plan, previous_work_url, portfolio_urls,
                                          wallet_links, motivation, bio)
  values (v_uid, p_country, p_content_types, p_about, p_experience,
          p_publishing_plan, p_previous_work_url, p_portfolio_urls, p_wallet_links, p_motivation, p_bio)
  returning id into v_id;

  return jsonb_build_object('ok', true, 'application_id', v_id);
end;
$$;

create or replace function public.review_creator_application(
  p_application_id bigint, p_action text, p_note text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_app public.creator_applications;
  v_status text;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  v_status := case p_action
                when 'approve' then 'approved'
                when 'reject'  then 'rejected'
                when 'more_info' then 'more_info'
                else null end;
  if v_status is null then return jsonb_build_object('ok', false, 'message', 'invalid_action'); end if;

  select * into v_app from public.creator_applications where id = p_application_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  update public.creator_applications
     set status = v_status, admin_note = p_note,
         reviewed_by = auth.uid(), reviewed_at = now()
   where id = p_application_id;

  if v_status = 'approved' then
    update public.profiles set role = 'creator', updated_at = now() where id = v_app.user_id;

    insert into public.chat_notifications(recipient_id, actor_id, type, body)
    values (v_app.user_id, auth.uid(), 'creator', 'Welcome to AddonStation Creators!',
            jsonb_build_object('application_id', p_application_id));
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'creator_application_' || v_status, 'creator_application',
          p_application_id::text, jsonb_build_object('note', p_note, 'user', v_app.user_id));

  return jsonb_build_object('ok', true, 'status', v_status);
end;
$$;

create or replace function public.toggle_featured_creator(p_user_id uuid, p_featured boolean)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  if p_featured then
    insert into public.featured_creators(user_id, added_by) values (p_user_id, auth.uid())
    on conflict (user_id) do nothing;
  else
    delete from public.featured_creators where user_id = p_user_id;
  end if;

  return jsonb_build_object('ok', true, 'featured', p_featured);
end;
$$;

create or replace function public.creator_submit_content(
  p_title text,
  p_content_type text default 'addon',
  p_description text default null,
  p_category text default null,
  p_version text default null,
  p_plan text default 'free',
  p_cover_url text default null,
  p_file_path text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid(); v_id bigint;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if public.current_role_of(v_uid) not in ('creator','admin') then
    return jsonb_build_object('ok', false, 'message', 'not_creator');
  end if;
  if coalesce(btrim(p_title),'') = '' then
    return jsonb_build_object('ok', false, 'message', 'title_required');
  end if;

  insert into public.creator_submissions(creator_id, title, content_type, description, category,
                                         version, plan, cover_url, file_path)
  values (v_uid, p_title, p_content_type, p_description, p_category, p_version, p_plan, p_cover_url, p_file_path)
  returning id into v_id;

  return jsonb_build_object('ok', true, 'submission_id', v_id);
end;
$$;

create or replace function public.review_creator_submission(
  p_submission_id bigint, p_action text, p_note text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_sub public.creator_submissions;
  v_item_id uuid;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_sub from public.creator_submissions where id = p_submission_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  if p_action = 'approve' then
    insert into public.content_items(title, slug, description, content_type, category, plan, version,
                                     cover_url, file_path, is_published, created_by)
    values (v_sub.title,
            lower(regexp_replace(v_sub.title, '[^a-zA-Z0-9\u0600-\u06FF]+', '-', 'g')),
            v_sub.description, coalesce(v_sub.content_type,'addon'), coalesce(v_sub.category,'other'),
            coalesce(v_sub.plan,'free'), v_sub.version, v_sub.cover_url, v_sub.file_path, true, v_sub.creator_id)
    returning id into v_item_id;

    update public.creator_submissions
       set status = 'approved', admin_note = p_note, content_item_id = v_item_id,
           reviewed_by = auth.uid(), reviewed_at = now()
     where id = p_submission_id;

    insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
    values (v_sub.creator_id, auth.uid(), 'creator', 'Your content was approved and published',
            jsonb_build_object('submission_id', p_submission_id, 'item_id', v_item_id));
  else
    update public.creator_submissions
       set status = 'rejected', admin_note = p_note,
           reviewed_by = auth.uid(), reviewed_at = now()
     where id = p_submission_id;

    insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
    values (v_sub.creator_id, auth.uid(), 'creator', 'Your content was rejected',
            jsonb_build_object('submission_id', p_submission_id, 'note', p_note));
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'creator_submission_' || p_action, 'creator_submission',
          p_submission_id::text, jsonb_build_object('note', p_note));

  return jsonb_build_object('ok', true, 'item_id', v_item_id);
end;
$$;

create or replace function public.creator_submit_tip(
  p_creator_id uuid,
  p_amount numeric,
  p_method text,
  p_message text default null,
  p_receipt_path text default null,
  p_transaction_reference text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_ref text;
  v_id bigint;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if coalesce(p_amount,0) <= 0 then return jsonb_build_object('ok', false, 'message', 'invalid_amount'); end if;
  if p_creator_id = v_uid then return jsonb_build_object('ok', false, 'message', 'self_tip'); end if;

  loop
    v_ref := 'TIP-' || upper(substr(replace(gen_random_uuid()::text,'-',''), 1, 8));
    exit when not exists (select 1 from public.creator_tips where reference_code = v_ref);
  end loop;

  insert into public.creator_tips(creator_id, sender_id, amount_lyd, method, message,
                                  receipt_url, transaction_reference, reference_code)
  values (p_creator_id, v_uid, p_amount, p_method, p_message, p_receipt_path,
          p_transaction_reference, v_ref)
  returning id into v_id;

  return jsonb_build_object('ok', true, 'tip_id', v_id, 'reference_code', v_ref);
end;
$$;

create or replace function public.creator_review_tip(
  p_tip_id bigint, p_approve boolean, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_tip public.creator_tips;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_tip from public.creator_tips where id = p_tip_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  update public.creator_tips
     set status = case when p_approve then 'approved' else 'rejected' end,
         reject_reason = case when p_approve then null else p_reason end,
         reviewed_by = auth.uid(), reviewed_at = now()
   where id = p_tip_id;

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (v_tip.creator_id, auth.uid(), 'creator',
          case when p_approve then 'You received a new tip' else 'A tip was rejected' end,
          jsonb_build_object('tip_id', p_tip_id, 'amount', v_tip.amount_lyd));

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when p_approve then 'tip_approved' else 'tip_rejected' end,
          'creator_tip', p_tip_id::text, jsonb_build_object('amount', v_tip.amount_lyd));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8) تعديل إعدادات الفلترة التلقائية وقواعد المجتمع (Admin)
-- ---------------------------------------------------------------------------
create or replace function public.admin_update_settings(p_key text, p_value jsonb)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  insert into public.app_settings(key, value, updated_at, updated_by)
  values (p_key, p_value, now(), auth.uid())
  on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = auth.uid();

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'settings_updated', 'app_settings', p_key, p_value);

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.admin_update_chat_rules(p_lang text, p_rules jsonb, p_title text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  insert into public.chat_rules(lang, title, rules, updated_at)
  values (p_lang, p_title, p_rules, now())
  on conflict (lang) do update set title = coalesce(excluded.title, public.chat_rules.title),
                                   rules = excluded.rules, updated_at = now();

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9) منح التنفيذ
-- ---------------------------------------------------------------------------
do $$
declare fn record;
begin
  for fn in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'app_setting','chat_content_check','send_chat_message','chat_delete_my_message',
        'chat_moderate_message','chat_toggle_pin','chat_toggle_reaction','chat_mute_user',
        'chat_unmute_user','chat_ban_user','chat_unban_user','chat_report_message',
        'chat_close_report','chat_admin_upsert_room','chat_admin_delete_room',
        'chat_send_announcement','chat_set_typing','chat_mark_read','chat_unread_counts',
        'my_chat_status','chat_accept_rules','mark_notifications_read','send_friend_request',
        'respond_friend_request','submit_creator_application','review_creator_application',
        'toggle_featured_creator','creator_submit_content','review_creator_submission',
        'creator_submit_tip','creator_review_tip','admin_update_settings','admin_update_chat_rules'
      )
  loop
    execute format('grant execute on function %s to authenticated', fn.sig);
    execute format('grant execute on function %s to anon', fn.sig);
  end loop;
end $$;
