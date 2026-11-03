-- ============================================================================
--  AddonStation Mobile — ٣٤) الإشعارات الفورية (Push) — بنية مرتّبة لا مزعجة
--
--  ما يبنيه هذا الملف (وكلّه في القاعدة، مع دالة الإرسال على الخادم):
--    1) جدول الأجهزة        push_devices  — توكن FCM لكل جهاز + لغة الجهاز
--    2) جدول التفضيلات      push_prefs    — مفتاح رئيسي + 5 أقسام + ساعات الهدوء
--    3) طابور الإرسال       push_queue    — كل إشعار يُدخل هنا أولاً ثم يُرسل
--    4) الدوال              enqueue · schedule · reengage · cleanup
--    5) الزنادات            على chat_notifications و content_items
--    6) دوال التطبيق        تسجيل الجهاز · التفضيلات · إشعار تجريبي · نبضة الحضور
--
--  القواعد التي تجعله «مرتّباً» (وهي جوهر الطلب):
--    • 5 أقسام مستقلة: رسائل الشات · محتوى جديد · اجتماعي · الحساب والاشتراك ·
--      تذكير بالعودة — لكل قسم مفتاح تشغيل/إيقاف من الإعدادات.
--    • ساعات الهدوء (افتراضياً 23:00 → 08:00 بتوقيت المستخدم نفسه): كل إشعار
--      غير عاجل يُؤجَّل إلى نهاية ساعات الهدوء — لا رنّ في الليل أبداً.
--    • التجميع: عشر رسائل في غرفة واحدة = **إشعار واحد** يتحدّث (collapse key)
--      بدل عشرة. والمحتوى الجديد إشعار واحد في اليوم كحد أقصى.
--    • الرسائل الشخصية (منشن/رد) عاجلة: تصل فوراً (priority high) ولا تُؤجَّل.
--    • تذكير العودة: مرة واحدة كل 14 يوماً كحد أقصى، ولا يُرسل لمن فتح التطبيق
--      حديثاً، وبعد 7 أيام غياب افتراضياً (قابل للتعديل لكل مستخدم).
--    • لا يُرسل إشعار لصاحب الفعل نفسه، ولا لرسائل من حظرته/حظرك، ولا لمن يُكتم
--      في الغرفة.
--
--  آمن لإعادة التنفيذ بالكامل في أي وقت (create if not exists · create or replace
--  · drop trigger if exists). لا يحذف أي بيانات موجودة.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) أجهزة المستخدم: التوكن + لغته + منطقته الزمنية
-- ---------------------------------------------------------------------------
create table if not exists public.push_devices (
  token        text primary key,
  user_id      uuid not null references public.profiles(id) on delete cascade,
  platform     text not null default 'android',
  app_version  text,
  locale       text not null default 'en',
  tz_offset    int  not null default 0,        -- دقائق شرق UTC (مثال: ليبيا +120)
  enabled      boolean not null default true,
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);
create index if not exists push_devices_user_idx on public.push_devices(user_id) where enabled;

-- ---------------------------------------------------------------------------
-- 2) تفضيلات الإشعارات (صف واحد لكل مستخدم)
-- ---------------------------------------------------------------------------
create table if not exists public.push_prefs (
  user_id        uuid primary key references public.profiles(id) on delete cascade,
  master         boolean not null default true,   -- المفتاح الرئيسي
  chat           boolean not null default true,   -- رسائل الغرف + منشن + رد
  content        boolean not null default true,   -- إضافة/عالم جديد
  social         boolean not null default true,   -- طلبات الصداقة والتعليقات
  account        boolean not null default true,   -- الاشتراك والدفع وAC
  reminders      boolean not null default true,   -- تذكير العودة بعد غياب
  quiet_enabled  boolean not null default true,   -- ساعات الهدوء
  quiet_from     int not null default 23 check (quiet_from between 0 and 23),
  quiet_to       int not null default 8  check (quiet_to between 0 and 23),
  reengage_days  int not null default 7  check (reengage_days between 3 and 60),
  -- تتبّع دائم (عمود لا صف في الطابور): صفوف الطابور تُنظَّف بعد 7 أيام،
  -- فلو اعتمدنا عليها لضاعت قاعدة «مرة كل 14 يوماً» بعد أسبوع.
  last_reengage_at timestamptz,                   -- آخر تذكير عودة أُرسل له
  last_offer_at    timestamptz,                   -- آخر عرض اشتراك أُرسل له
  updated_at     timestamptz not null default now()
);

-- ترقية آمنة لقاعدة سبق تنفيذها قبل إضافة عمودَي التتبّع
do $$ begin
  alter table public.push_prefs add column if not exists last_reengage_at timestamptz;
  alter table public.push_prefs add column if not exists last_offer_at timestamptz;
end $$;

-- ---------------------------------------------------------------------------
-- 3) طابور الإرسال (السجل الكامل: ماذا أُرسل ولمن ومتى — يُنظَّف تلقائياً)
-- ---------------------------------------------------------------------------
create table if not exists public.push_queue (
  id             bigserial primary key,
  user_id        uuid references public.profiles(id) on delete cascade,  -- null = بثّ للجميع
  category       text not null check (category in ('chat','content','social','account','reminders')),
  title_key      text not null,
  body_key       text not null,
  args           jsonb not null default '{}'::jsonb,
  route          text,                       -- أين يُفتح الإشعار داخل التطبيق
  collapse_key   text,                       -- مفتاح التجميع (يستبدل الإشعار السابق)
  priority       text not null default 'normal' check (priority in ('high','normal')),
  scheduled_at   timestamptz not null default now(),
  attempts       int not null default 0,
  status         text not null default 'pending' check (status in ('pending','sent','failed','skipped')),
  error          text,
  created_at     timestamptz not null default now(),
  sent_at        timestamptz,
  image_url      text,                                   -- صورة الإشعار (مسار أو رابط كامل)
  image_kind     text                                    -- cover = صورة كبيرة · avatar = أيكونة
);
-- نوع الصورة: cover تُعرض كبيرة (غلاف إضافة/عالم) · avatar تُعرض أيكونة (صورة المرسل)
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'push_queue_image_kind_chk') then
    alter table public.push_queue
      add constraint push_queue_image_kind_chk
      check (image_kind is null or image_kind in ('cover', 'avatar'));
  end if;
end $$;

create index if not exists push_queue_pending_idx on public.push_queue(status, scheduled_at)
  where status = 'pending';
create index if not exists push_queue_user_recent_idx on public.push_queue(user_id, category, created_at desc);

-- صفوف هذه الجداول لا تُقرأ ولا تُكتب من التطبيق مباشرة: كل شيء عبر الدوال
-- (security definer) — فلا يستطيع أحد إرسال إشعار لغيره ولا التلاعب بجهازه.
alter table public.push_devices enable row level security;
alter table public.push_prefs   enable row level security;
alter table public.push_queue   enable row level security;
revoke all on public.push_devices, public.push_prefs, public.push_queue from anon, authenticated;
grant all on public.push_devices, public.push_prefs, public.push_queue to service_role;

-- ---------------------------------------------------------------------------
-- 4) أدوات داخلية
-- ---------------------------------------------------------------------------

/** صف التفضيلات (يُنشأ بالقيم الافتراضية عند أول استعمال). */
create or replace function public.push_prefs_for(p_user uuid)
returns public.push_prefs
language plpgsql security definer set search_path = public
as $$
declare v public.push_prefs;
begin
  insert into public.push_prefs(user_id) values (p_user)
  on conflict (user_id) do nothing;
  select * into v from public.push_prefs where user_id = p_user;
  return v;
end $$;

/** هل الإشعارات مسموحة لهذا القسم لهذا المستخدم؟ (المفتاح الرئيسي + القسم) */
create or replace function public.push_allowed(p_user uuid, p_category text)
returns boolean
language plpgsql security definer set search_path = public
as $$
declare v public.push_prefs;
begin
  if p_user is null then return false; end if;
  v := public.push_prefs_for(p_user);
  if not v.master then return false; end if;
  return case p_category
    when 'chat'      then v.chat
    when 'content'   then v.content
    when 'social'    then v.social
    when 'account'   then v.account
    when 'reminders' then v.reminders
    else false
  end;
end $$;

/** الوقت الآن بتوقيت المستخدم (دقائق شرق UTC مخزّنة في جهازه). */
create or replace function public.push_local_now(p_user uuid)
returns timestamp
language sql stable security definer set search_path = public
as $$
  select (now() + make_interval(mins => coalesce(
            (select max(tz_offset) from public.push_devices
              where user_id = p_user and enabled), 0)))::timestamp;
$$;

/**
 * متى يُسمح بإرسال هذا الإشعار؟
 *   • عاجل (high): الآن — فوراً (منشن أو ردّ شخصي: تأخيره يفقد معناه).
 *   • غير عاجل: الآن، إلا أن نكون داخل ساعات هدوء المستخدم ⇒ يُؤجَّل إلى
 *     الساعة التي تنتهي فيها ساعات الهدوء بتوقيته هو.
 */
create or replace function public.push_schedule_at(p_user uuid, p_priority text)
returns timestamptz
language plpgsql stable security definer set search_path = public
as $$
declare
  v        public.push_prefs;
  v_offset int;
  v_local  timestamp;
  v_hour   int;
  v_target timestamp;
  v_inside boolean;
begin
  if p_priority = 'high' then return now(); end if;
  v := public.push_prefs_for(p_user);
  if not v.quiet_enabled or v.quiet_from = v.quiet_to then return now(); end if;

  v_offset := coalesce((select max(tz_offset) from public.push_devices
                         where user_id = p_user and enabled), 0);
  v_local  := (now() + make_interval(mins => v_offset))::timestamp;
  v_hour   := extract(hour from v_local)::int;

  -- الهدوء قد يعبر منتصف الليل (23 → 8) أو يكون في النهار (13 → 15)
  v_inside := case
    when v.quiet_from < v.quiet_to then v_hour >= v.quiet_from and v_hour < v.quiet_to
    else v_hour >= v.quiet_from or v_hour < v.quiet_to
  end;
  if not v_inside then return now(); end if;

  v_target := date_trunc('day', v_local) + make_interval(hours => v.quiet_to);
  if v_hour >= v.quiet_from and v.quiet_from > v.quiet_to then
    v_target := v_target + interval '1 day';   -- ننتظر الصباح القادم
  end if;
  return (v_target - make_interval(mins => v_offset))::timestamptz;
end $$;

-- ---------------------------------------------------------------------------
-- 5) إدخال إشعار إلى الطابور — وفيه كل قواعد «الترتيب»
-- ---------------------------------------------------------------------------
create or replace function public.push_enqueue(
  p_user       uuid,
  p_category   text,
  p_title_key  text,
  p_body_key   text,
  p_args       jsonb  default '{}'::jsonb,
  p_route      text   default null,
  p_collapse   text   default null,
  p_priority   text   default 'normal',
  p_cooldown_minutes int default 0,
  -- صورة الإشعار: غلاف الإضافة/العالم (كبيرة) أو صورة المرسل (أيكونة)
  p_image      text   default null,
  p_image_kind text   default null
)
returns bigint
language plpgsql security definer set search_path = public
as $$
declare
  v_id        bigint;
  v_existing  bigint;
  v_scheduled timestamptz;
begin
  if p_user is null or not public.push_allowed(p_user, p_category) then
    return null;
  end if;

  -- تجميع: إن كان في الطابور إشعار **لم يُرسل بعد** بنفس مفتاح التجميع
  -- ولهذا المستخدم ⇒ نُحدّث عدّاده بدل إضافة إشعار جديد (١٠ رسائل = ١ إشعار).
  if p_collapse is not null then
    select id into v_existing from public.push_queue
     where user_id = p_user and collapse_key = p_collapse
       and status = 'pending' and created_at > now() - interval '6 hours'
     order by id desc limit 1;
  end if;

  if v_existing is not null then
    update public.push_queue
       set args = p_args,
           title_key = p_title_key,
           body_key = p_body_key,
           route = coalesce(p_route, route),
           -- صورة أحدث رسالة/إضافة في المجموعة (وإن لم تصل صورة نُبقي السابقة)
           image_url = coalesce(p_image, image_url),
           image_kind = coalesce(p_image_kind, image_kind)
     where id = v_existing;
    return v_existing;
  end if;

  -- تعطيل التكرار: كم مضى على آخر إشعار من نفس المفتاح لهذا المستخدم؟
  if p_collapse is not null and p_cooldown_minutes > 0 then
    if exists (
      select 1 from public.push_queue
       where user_id = p_user and collapse_key = p_collapse
         and sent_at is not null
         and sent_at > now() - make_interval(mins => p_cooldown_minutes)
    ) then
      return null;   -- أُرسل قريباً ⇒ لا تكرار
    end if;
  end if;

  v_scheduled := public.push_schedule_at(p_user, p_priority);

  insert into public.push_queue(user_id, category, title_key, body_key, args, route,
                                collapse_key, priority, scheduled_at, image_url, image_kind)
  values (p_user, p_category, p_title_key, p_body_key, coalesce(p_args, '{}'::jsonb),
          p_route, p_collapse, p_priority, v_scheduled, p_image, p_image_kind)
  returning id into v_id;

  return v_id;
end $$;

/**
 * إشعار جماعي (بثّ): صف واحد في الطابور، ودالة الخادم تنشره على كل الأجهزة
 * المسجّلة لأصحاب القسم — تجميعاً يمنع طوفان الإشعارات.
 */
create or replace function public.push_broadcast(
  p_category  text,
  p_title_key text,
  p_body_key  text,
  p_args      jsonb default '{}'::jsonb,
  p_route     text  default null,
  p_image      text default null,
  p_image_kind text default null
)
returns bigint
language plpgsql security definer set search_path = public
as $$
declare
  v_id bigint;
  v_existing bigint;
begin
  -- إشعار واحد للبثّ في اليوم: يُحدَّث العدّاد إن وُجد صف بثّ لم يُرسل في آخر 20 ساعة
  select id into v_existing from public.push_queue
   where user_id is null and category = p_category and status = 'pending'
     and created_at > now() - interval '20 hours'
   order by id desc limit 1;

  if v_existing is not null then
    update public.push_queue
       set args = p_args, title_key = p_title_key, body_key = p_body_key,
           scheduled_at = least(scheduled_at, now() + interval '5 minutes'),
           -- ملخّص اليوم يحمل صورة **أحدث** إضافة نزلت
           image_url = coalesce(p_image, image_url),
           image_kind = coalesce(p_image_kind, image_kind)
     where id = v_existing;
    return v_existing;
  end if;

  insert into public.push_queue(user_id, category, title_key, body_key, args, route,
                                priority, scheduled_at, image_url, image_kind)
  values (null, p_category, p_title_key, p_body_key, coalesce(p_args, '{}'::jsonb),
          p_route, 'normal', now(), p_image, p_image_kind)
  returning id into v_id;
  return v_id;
end $$;

-- ---------------------------------------------------------------------------
-- 6) الزناد الأول: كل صف في chat_notifications ⇒ إشعار فوري مناسب
--    (هذا وحده يغطّي: الرسائل · المنشن · الردود · طلبات الصداقة · الاشتراك
--     والدفع · رسائل النظام — لأنها كلها تُنشئ صفوفاً في هذا الجدول أصلاً.)
-- ---------------------------------------------------------------------------
create or replace function public.push_on_notification()
returns trigger
language plpgsql security definer set search_path = public
as $$
declare
  v_category text;
  v_priority text := 'normal';
  v_title    text;
  v_body     text;
  v_args     jsonb := '{}'::jsonb;
  v_route    text;
  v_collapse text;
  v_cooldown int := 0;
  v_actor_name text;
  v_actor_avatar text;
begin
  -- لا إشعار لصاحب الفعل نفسه
  if new.actor_id is not null and new.actor_id = new.recipient_id then
    return new;
  end if;

  -- اسم المرسل وصورته: الصورة تظهر كأيكونة بجانب الإشعار (شات/صداقة)
  select coalesce(nullif(username, ''), display_name, '—'),
         nullif(btrim(coalesce(avatar_url, '')), '')
    into v_actor_name, v_actor_avatar
    from public.profiles where id = new.actor_id;
  v_actor_name := coalesce(v_actor_name, '—');

  case new.type
    when 'mention' then
      v_category := 'chat'; v_priority := 'high'; v_title := 'push_mention_title';
      v_body := 'push_mention_body'; v_collapse := 'mention_' || coalesce(new.message_id::text, '0');
      v_route := 'chat:' || coalesce(new.channel_id, '');
      v_args := jsonb_build_object('name', v_actor_name);
    when 'reply' then
      v_category := 'chat'; v_priority := 'high'; v_title := 'push_reply_title';
      v_body := 'push_reply_body'; v_collapse := 'reply_' || coalesce(new.message_id::text, '0');
      v_route := 'chat:' || coalesce(new.channel_id, '');
      v_args := jsonb_build_object('name', v_actor_name);
    when 'thread' then
      v_category := 'chat'; v_title := 'push_thread_title'; v_body := 'push_thread_body';
      v_collapse := 'thread_' || coalesce(new.channel_id, '0');
      v_route := 'chat:' || coalesce(new.channel_id, '');
    when 'friend_request' then
      v_category := 'social'; v_title := 'push_friend_title'; v_body := 'push_friend_body';
      v_collapse := 'friend_req'; v_route := 'notifications';
      v_args := jsonb_build_object('name', v_actor_name);
    when 'plan' then
      v_category := 'account'; v_title := 'push_plan_title'; v_body := 'push_plan_body';
      v_collapse := 'plan'; v_route := 'plans';
    when 'payment' then
      v_category := 'account'; v_title := 'push_payment_title'; v_body := 'push_payment_body';
      v_collapse := 'payment_' || coalesce(new.meta ->> 'order', 'x'); v_route := 'orders';
    when 'creator' then
      v_category := 'content'; v_title := 'push_creator_title'; v_body := 'push_creator_body';
      v_collapse := 'creator'; v_route := 'creators';
    else
      v_category := 'account'; v_title := 'push_system_title'; v_body := 'push_system_body';
      v_collapse := 'system'; v_route := 'notifications';
  end case;

  -- رسائل الغرف: لا إشعار لمن كُتم أو لمن بينه وبين المرسل حظر
  if v_category = 'chat' and new.actor_id is not null then
    if public.is_blocked_pair(new.recipient_id, new.actor_id) then
      return new;
    end if;
    if exists (select 1 from public.chat_mutes m
                where m.user_id = new.recipient_id and m.active
                  and (m.expires_at is null or m.expires_at > now())) then
      return new;
    end if;
    -- في الغرف العامة: إشعار واحد لكل غرفة كل 5 دقائق (البقية تُجمَّع فيه)
    if v_priority <> 'high' then
      v_cooldown := 5;
      v_collapse := 'room_' || coalesce(new.channel_id, '0');
    end if;
  end if;

  perform public.push_enqueue(
    new.recipient_id, v_category, v_title, v_body, v_args, v_route,
    v_collapse, v_priority, v_cooldown,
    -- صورة المرسل أيكونة صغيرة (لا صورة كبيرة) — إلا المحتوى فصورته غلاف كبير
    case when v_category = 'content' then null else v_actor_avatar end,
    case when v_actor_avatar is not null and v_category <> 'content' then 'avatar' end
  );
  return new;
end $$;

drop trigger if exists trg_push_on_notification on public.chat_notifications;
create trigger trg_push_on_notification
  after insert on public.chat_notifications
  for each row execute function public.push_on_notification();

-- ---------------------------------------------------------------------------
-- 7) الزناد الثاني: محتوى جديد (إضافة/عالم) ⇒ إشعار جماعي واحد
-- ---------------------------------------------------------------------------
create or replace function public.push_on_content()
returns trigger
language plpgsql security definer set search_path = public
as $$
declare v_count int;
begin
  if not new.is_published then return new; end if;
  if tg_op = 'UPDATE' and old.is_published then return new; end if;

  -- نعدّ ما نُشر اليوم: العدّاد يظهر في الإشعار («٣ إضافات جديدة»)
  select count(*) into v_count
    from public.content_items
   where is_published and created_at > now() - interval '1 day';

  -- الإشعار يحمل **صورة الإضافة/العالم** نفسها (غلافها) فتُعرض كبيرة على الهاتف.
  -- الصورة قد تكون مساراً داخل مخزن site-assets (والتطبيق يبنيه رابطاً كاملاً)
  -- أو رابطاً كاملاً — الحالتان مدعومتان على الجهاز.
  perform public.push_broadcast(
    'content', 'push_content_title', 'push_content_body',
    jsonb_build_object('n', greatest(v_count, 1), 'name', coalesce(new.title, '')),
    'item:' || coalesce(new.id::text, ''),
    nullif(btrim(coalesce(new.cover_url, '')), ''), 'cover'
  );
  return new;
end $$;

drop trigger if exists trg_push_on_content on public.content_items;
create trigger trg_push_on_content
  after insert or update of is_published on public.content_items
  for each row execute function public.push_on_content();

-- ---------------------------------------------------------------------------
-- 8) تذكير العودة: من غاب فترة طويلة — بحدّ أقصى واحد كل 14 يوماً
--    تُنادى من دالة الخادم المجدولة (مرة يومياً).
-- ---------------------------------------------------------------------------
create or replace function public.push_reengage_scan(p_limit int default 500)
returns int
language plpgsql security definer set search_path = public
as $$
declare
  v_row    record;
  v_count  int := 0;
  v_days   int;
begin
  for v_row in
    select p.id, coalesce(pr.reengage_days, 7) as days
      from public.profiles p
      join public.push_prefs pr on pr.user_id = p.id and pr.master and pr.reminders
     where p.last_seen_at is not null
       and exists (select 1 from public.push_devices d where d.user_id = p.id and d.enabled)
       and p.created_at < now() - interval '3 days'                    -- حساب جديد لا يُذكَّر
       and p.last_seen_at < now() - make_interval(days => coalesce(pr.reengage_days, 7))
       and (pr.last_reengage_at is null                                     -- مرة كل 14 يوماً
            or pr.last_reengage_at < now() - interval '14 days')
       and not exists (                                                     -- وحرصاً مضاعفاً: لا صف حديث
         select 1 from public.push_queue q
          where q.user_id = p.id and q.category = 'reminders'
            and q.created_at > now() - interval '14 days'
       )
     order by p.last_seen_at asc
     limit greatest(coalesce(p_limit, 500), 1)
  loop
    v_days := v_row.days;
    perform public.push_enqueue(
      v_row.id, 'reminders', 'push_missing_title', 'push_missing_body',
      jsonb_build_object('days', v_days),
      'store', 'reminder', 'normal', 0
    );
    update public.push_prefs set last_reengage_at = now() where user_id = v_row.id;
    v_count := v_count + 1;
  end loop;
  return v_count;
end $$;

/** تنظيف السجل: يُبقي آخر 7 أيام فقط (يُنادى من الخادم المجدول). */
create or replace function public.push_cleanup(p_days int default 7)
returns int
language plpgsql security definer set search_path = public
as $$
declare v_deleted int;
begin
  delete from public.push_devices
   where last_seen_at < now() - interval '180 days';        -- أجهزة مهجورة

  delete from public.push_queue
   where status <> 'pending' and created_at < now() - make_interval(days => greatest(coalesce(p_days, 7), 1));
  get diagnostics v_deleted = row_count;
  return v_deleted;
end $$;

/** أجهزة الإرسال: لتذكير مستخدم واحد، أو لكل من فعّل القسم عند البثّ. */
create or replace function public.push_targets(p_category text, p_user uuid default null)
returns table (token text, locale text, user_id uuid)
language sql stable security definer set search_path = public
as $$
  select d.token, d.locale, d.user_id
    from public.push_devices d
   where d.enabled
     and (p_user is null or d.user_id = p_user)
     and exists (
       select 1 from public.push_prefs pr
        where pr.user_id = d.user_id and pr.master
          and case p_category
                when 'chat'      then pr.chat
                when 'content'   then pr.content
                when 'social'    then pr.social
                when 'account'   then pr.account
                when 'reminders' then pr.reminders
                else false
              end
     );
$$;

-- ---------------------------------------------------------------------------
-- 9) دوال التطبيق (للمستخدم نفسه فقط — كل شيء security definer)
-- ---------------------------------------------------------------------------

/** نبضة الحضور: تُنادى عند فتح التطبيق ⇒ عليها يقوم «تذكير الغياب». */
create or replace function public.push_heartbeat()
returns void
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return; end if;
  update public.profiles set last_seen_at = now() where id = auth.uid();
end $$;

/** تسجيل جهاز (عند أول تشغيل وعند كل تغيّر للتوكن). */
create or replace function public.push_register_device(
  p_token       text,
  p_platform    text default 'android',
  p_app_version text default null,
  p_locale      text default 'en',
  p_tz_offset   int  default 0
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if p_token is null or btrim(p_token) = '' then
    return jsonb_build_object('ok', false, 'message', 'bad_token');
  end if;

  insert into public.push_devices(token, user_id, platform, app_version, locale, tz_offset,
                                  enabled, last_seen_at)
  values (btrim(p_token), v_uid, coalesce(p_platform, 'android'), p_app_version,
          coalesce(nullif(btrim(p_locale), ''), 'en'), coalesce(p_tz_offset, 0), true, now())
  on conflict (token) do update
    set user_id      = v_uid,             -- الجهاز انتقل لمستخدم آخر (تبديل حساب)
        app_version  = excluded.app_version,
        locale       = excluded.locale,
        tz_offset    = excluded.tz_offset,
        enabled      = true,
        last_seen_at = now();

  perform public.push_prefs_for(v_uid);   -- تأكيد وجود صف التفضيلات
  return jsonb_build_object('ok', true);
end $$;

/** إلغاء تسجيل جهاز (عند تسجيل الخروج أو تعطيل الإشعارات من النظام). */
create or replace function public.push_unregister_device(p_token text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  delete from public.push_devices
   where token = p_token and user_id = auth.uid();
  return jsonb_build_object('ok', true);
end $$;

/** قراءة تفضيلاتي (لشاشة الإشعارات في الإعدادات). */
create or replace function public.my_push_prefs()
returns public.push_prefs
language plpgsql security definer set search_path = public
as $$
declare v public.push_prefs;
begin
  if auth.uid() is null then return null; end if;
  v := public.push_prefs_for(auth.uid());
  return v;
end $$;

/** حفظ تفضيلاتي — أي وسيط يُترك NULL يبقى على حاله. */
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
as $$
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
end $$;

/** إشعار تجريبي لنفسي: يتجاوز ساعات الهدوء — للتأكد أن كل شيء يعمل. */
create or replace function public.push_test_me()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid(); v_id bigint;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if not exists (select 1 from public.push_devices where user_id = v_uid and enabled) then
    return jsonb_build_object('ok', false, 'message', 'no_device');
  end if;

  insert into public.push_queue(user_id, category, title_key, body_key, args, route,
                                collapse_key, priority, scheduled_at)
  values (v_uid, 'account', 'push_test_title', 'push_test_body', '{}'::jsonb,
          'notifications', 'test_' || v_uid::text, 'high', now())
  returning id into v_id;

  return jsonb_build_object('ok', true, 'queued', v_id);
end $$;

/**
 * كشف «عرض الاشتراك»: لمن هو على **الخطة المجانية** ويريد أفضل وأجدد
 * الإضافات والعوالم — عرض واحد كل 30 يوماً، وبقواعد صارمة تمنع الإزعاج:
 *   • لا يُرسل لحساب عمره أقل من 7 أيام.
 *   • لا يُرسل لغائب (الغياب له تذكير آخر) ولا لصاحب اشتراك ساري.
 *   • لا يُرسل لصاحب اشتراك انتهى للتوّ؟ — بلى يُرسل (اشتراكه انتهى ⇒ يعود مجانياً).
 *   • لا يُرسل لمن وصله أي إشعار آخر خلال 7 أيام (لا نبيع لمن نزعجه).
 *   • عرض واحد كل 30 يوماً لكل مستخدم، ويُحترم فيها المفتاح الرئيسي ومفتاح
 *     قسم «المحتوى» وساعات الهدوء ومهلة 20 صفاً في الدورة.
 */
create or replace function public.push_offer_scan(p_limit int default 200)
returns int
language plpgsql security definer set search_path = public
as $$
declare
  v_row   record;
  v_count int := 0;
begin
  for v_row in
    select p.id
      from public.profiles p
      join public.push_prefs pr on pr.user_id = p.id and pr.master and pr.content
     where
       -- مجاني **فعلاً** — بنفس منطق التطبيق (Profile.effectivePlan):
       --   • plan = 'free' ⇒ مجاني.
       --   • plan مدفوع وانتهى تاريخه ⇒ عاد مجانياً ⇒ يُعرض عليه.
       --   • plan مدفوع بلا تاريخ انتهاء (اشتراك مفتوح/مُهدى) ⇒ **مشترك، لا يُزعج**.
       (
         p.plan = 'free'
         or (p.plan_expires_at is not null and p.plan_expires_at <= now())
       )
       and exists (select 1 from public.push_devices d where d.user_id = p.id and d.enabled)
       and p.created_at < now() - interval '7 days'                       -- لا نعرض على حساب صغير
       and p.last_seen_at > now() - interval '21 days'                    -- فعّال (الغائب له تذكير آخر)
       and (pr.last_offer_at is null
            or pr.last_offer_at < now() - interval '30 days')             -- عرض واحد كل 30 يوماً
       and not exists (                                                   -- لا نبيع لمن أزعجناه هذا الأسبوع
         select 1 from public.push_queue q
          where q.user_id = p.id and q.created_at > now() - interval '7 days'
       )
     order by p.last_seen_at desc
     limit greatest(coalesce(p_limit, 200), 1)
  loop
    perform public.push_enqueue(
      v_row.id, 'content', 'push_offer_title', 'push_offer_body',
      '{}'::jsonb,
      'plans',        -- يفتح شاشة الخطط مباشرة
      'offer', 'normal', 0
    );
    update public.push_prefs set last_offer_at = now() where user_id = v_row.id;
    v_count := v_count + 1;
  end loop;
  return v_count;
end $$;

-- ---------------------------------------------------------------------------
-- 10) الصلاحيات: DML مباشر ممنوع، والدوال للمسجّلين
-- ---------------------------------------------------------------------------
revoke all on function public.push_prefs_for(uuid) from public;
revoke all on function public.push_allowed(uuid, text) from public;
revoke all on function public.push_schedule_at(uuid, text) from public;
revoke all on function public.push_enqueue(uuid, text, text, text, jsonb, text, text, text, int, text, text) from public;
revoke all on function public.push_broadcast(text, text, text, jsonb, text, text, text) from public;
revoke all on function public.push_reengage_scan(int) from public;
revoke all on function public.push_cleanup(int) from public;
revoke all on function public.push_offer_scan(int) from public;

grant execute on function public.push_heartbeat() to authenticated;
grant execute on function public.push_register_device(text, text, text, text, int) to authenticated;
grant execute on function public.push_unregister_device(text) to authenticated;
grant execute on function public.my_push_prefs() to authenticated;
grant execute on function public.push_set_prefs(boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int) to authenticated;
grant execute on function public.push_test_me() to authenticated;
grant execute on function public.push_targets(text, uuid) to service_role;
grant execute on function public.push_reengage_scan(int) to service_role;
grant execute on function public.push_cleanup(int) to service_role;
grant execute on function public.push_offer_scan(int) to service_role;

-- ---------------------------------------------------------------------------
-- 11) التحقق — نفّذ الملف ثم انظر هذا الجدول: المفروض «✅» في كل صف
-- ---------------------------------------------------------------------------
select 'جدول الأجهزة push_devices' as "المكوّن",
       (select count(*) from information_schema.tables
         where table_schema = 'public' and table_name = 'push_devices')::text as "الموجود", '1' as "المتوقّع"
union all select 'جدول التفضيلات push_prefs',
       (select count(*) from information_schema.tables
         where table_schema = 'public' and table_name = 'push_prefs')::text, '1'
union all select 'طابور الإرسال push_queue',
       (select count(*) from information_schema.tables
         where table_schema = 'public' and table_name = 'push_queue')::text, '1'
union all select 'الزناد على إشعارات الشات',
       (select count(*) from pg_trigger where tgname = 'trg_push_on_notification' and not tgisinternal)::text, '1'
union all select 'الزناد على المحتوى الجديد',
       (select count(*) from pg_trigger where tgname = 'trg_push_on_content' and not tgisinternal)::text, '1'
union all select 'دوال الإشعارات (18 دالة)',
       (select count(*) from pg_proc where proname in (
          'push_prefs_for','push_allowed','push_local_now','push_schedule_at','push_enqueue',
          'push_broadcast','push_reengage_scan','push_cleanup','push_offer_scan','push_targets',
          'push_heartbeat','push_register_device','push_unregister_device','my_push_prefs',
          'push_set_prefs','push_test_me','push_on_notification','push_on_content'))::text, '18'
union all select 'أجهزة مسجّلة',
       (select count(*) from public.push_devices)::text, 'حسب أجهزتك'
union all select 'تتبّع دائم لقواعد عدم الإزعاج (عمودان)',
       (select count(*) from information_schema.columns
         where table_schema = 'public' and table_name = 'push_prefs'
           and column_name in ('last_reengage_at', 'last_offer_at'))::text, '2'
union all select 'إشعارات في الطابور',
       (select count(*) from public.push_queue where status = 'pending')::text, '—';

do $$
begin
  raise notice '٣٤) الإشعارات جاهزة: 5 أقسام · ساعات هدوء · تجميع · تذكير غياب كل 14 يوماً · إشعارات بصور الأغلفة وصور المرسلين · عرض اشتراك واحد كل 30 يوماً للمجانيين.';
  raise notice 'الخطوة التالية: انشر دالة send-push على Supabase وضع مفاتيح FCM (docs/11-PUSH-AR.md).';
end $$;
