-- ============================================================================
--  AddonStation Mobile — ٢٥) إصلاح خطأ إرسال الرسالة
--                          ERROR: column "unnest" does not exist  (SQLSTATE 42703)
--
--  الأعراض: كل إرسال رسالة نصية يفشل ويرجع الخطأ:
--              column "unnest" does not exist
--
--  السبب: سطر استخراج المنشنات كان هكذا
--              v_mentions := coalesce(array(select distinct unnest(v_mentions)
--                                             where unnest is not null), '{}');
--         وفي PostgreSQL **أسماء أعمدة المخرجات غير مرئية في WHERE** (تُرى في
--         ORDER BY/GROUP BY فقط) ⇒ يُفسَّر `unnest` على أنه عمود غير موجود.
--         والخطأ يظهر عند أول تنفيذ للسطر (PL/pgSQL يترجم كل جملة عند تشغيلها
--         أول مرة) ⇒ يفشل إرسال أي رسالة نصية، حتى بلا منشن.
--
--  الإصلاح: unnest في عبارة FROM مع اسم مستعار:
--              select coalesce(array_agg(distinct m), '{}'::uuid[])
--                into v_mentions
--                from unnest(coalesce(v_mentions, '{}'::uuid[])) as m
--               where m is not null;
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor (آمن للتكرار).
-- ============================================================================

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
-- تحقّق ١: لا يبقى أي تعريف يحتوي العبارة المكسورة
-- ---------------------------------------------------------------------------
do $$
declare
  v_broken int := 0;
begin
  select count(*) into v_broken
    from pg_proc
   where pronamespace = 'public'::regnamespace
     and proname in ('send_chat_message')
     and prosrc ~* 'where\s+unnest\s+is\s+not\s+null';

  if v_broken > 0 then
    raise exception '✗ ما زال هناك % تعريف/تعريفات للدالة يحوي العبارة المكسورة', v_broken;
  end if;

  if not exists (
      select 1 from pg_proc
       where pronamespace = 'public'::regnamespace
         and proname = 'send_chat_message'
         and pg_get_function_identity_arguments(oid) like 'p_channel_id%') then
    raise exception '✗ دالة send_chat_message غير موجودة بالتوقيع المتوقع';
  end if;

  raise notice '✅ 25_fix_send_chat_message_unnest: send_chat_message سليمة (بلا where unnest).';
end;
$$;

-- ---------------------------------------------------------------------------
-- تحقّق ٢: إثبات أن العبارة القديمة هي سبب الخطأ، وأن الجديدة تعمل
-- ---------------------------------------------------------------------------
do $$
begin
  begin
    -- العبارة القديمة (يجب أن تفشل بـ undefined_column = 42703)
    perform array(select distinct unnest(array[1, 2]) where unnest is not null);
    raise notice 'ℹ️ العبارة القديمة قُبلت في هذا الإصدار (لا ضرر — الإصلاح مطبَّق على أي حال)';
  exception when undefined_column then
    raise notice '✔ هذا هو سبب الخطأ الأصلي: %', sqlerrm;
  end;
end;
$$;

do $$
declare
  v uuid[] := array[gen_random_uuid(), gen_random_uuid(), gen_random_uuid()];
  r uuid[];
begin
  -- نفس العبارة المُصلحة المستخدمة داخل send_chat_message
  select coalesce(array_agg(distinct m), '{}'::uuid[])
    into r
    from unnest(coalesce(v, '{}'::uuid[])) as m
   where m is not null;

  if r is null then
    raise exception '✗ العبارة المُصلحة أعادت NULL';
  end if;

  raise notice '✅ العبارة المُصلحة تعمل: % عنصراً بعد إزالة التكرار', coalesce(array_length(r, 1), 0);

  -- مصفوفة فارغة ⇒ '{}' لا NULL (حتى لا تنكسر مقارنة array_length لاحقاً)
  select coalesce(array_agg(distinct m), '{}'::uuid[])
    into r
    from unnest('{}'::uuid[]) as m
   where m is not null;

  if r is null or coalesce(array_length(r, 1), 0) <> 0 then
    raise exception '✗ المصفوفة الفارغة لم تُعالَج correctly: %', r;
  end if;

  raise notice '✅ الحالة الحدّية (مصفوفة فارغة) تُعالَج بلا NULL — الإرسال سيعمل';
end;
$$;

-- ---------------------------------------------------------------------------
-- ملخّص للمالك
-- ---------------------------------------------------------------------------
select p.proname                                   as "الدالة",
       pg_get_function_identity_arguments(p.oid)   as "المعاملات",
       case when p.prosrc ~* 'where\s+unnest\s+is\s+not\s+null'
            then '✗ ما زالت مكسورة' else '✅ سليمة' end as "الحالة"
  from pg_proc p
 where p.pronamespace = 'public'::regnamespace
   and p.proname = 'send_chat_message';
