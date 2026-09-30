-- ============================================================================
--  AddonStation Mobile — ملف التجهيز الشامل (All-in-One)
--  يولّده tools/build_all_in_one_sql.py — لا تعدّله يدوياً، عدّل ملفات المصدر.
--
--  الطريقة الأسرع: انسخ هذا الملف كاملاً والصقه في Supabase → SQL Editor → Run.
--  إن ظهر خطأ في أي سطر، نفّذ الملفات الأصلية واحداً واحداً بنفس الترتيب:
--      01_schema → 02_functions_core → 03_functions_chat → 04_rls
--      → 05_storage → 06_seed → 07_seed_assets → 12_avatars → 10_realtime → 11_grants
--      → 13_channels_icons (أسماء الغرف بلا إيموجي)
--      → 14_redeem_codes (أكواد هدايا AC: توليد واستبدال)
--      → 15_assets_v2 (الأصول الجديدة: يستبدل كل الأفاتارات والملصقات والأدوات)
--      → 16_language_room_names (أسماء غرف اللغة بلغتها: العربية · Русский · 中文 …)
--  ثم بعد إنشاء حسابك من التطبيق: 08_admin_bootstrap.sql
--  و(اختيارياً) 09_gateway_functions.sql لبوابات الدفع الإلكتروني.
--
--  كل الملفات آمنة لإعادة التنفيذ (idempotent).
-- ============================================================================



-- ############################################################################
-- ##  القسم: 01_schema.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 01) مخطط قاعدة البيانات (Schema)
--  نفّذ هذا الملف أولاً في Supabase → SQL Editor
--  ملاحظة: الملف آمن للتشغيل أكثر من مرة (idempotent)
-- ============================================================================

create extension if not exists "pgcrypto";
create extension if not exists "citext";

-- ---------------------------------------------------------------------------
-- 1) الرتب والخطط (Helpers)
-- ---------------------------------------------------------------------------
-- ترتيب الخطط: free < epic < legendary
create or replace function public.plan_rank(p_plan text)
returns int
language sql
immutable
as $$
  select case lower(coalesce(p_plan,'free'))
    when 'legendary' then 3
    when 'epic'      then 2
    when 'free'      then 1
    else 0
  end;
$$;

-- ترتيب الرتب (لأغراض العرض فقط — الصلاحيات تُفحص على الخادم)
create or replace function public.role_rank(p_role text)
returns int
language sql
immutable
as $$
  select case lower(coalesce(p_role,'user'))
    when 'admin'     then 4
    when 'moderator' then 3
    when 'creator'   then 2
    when 'user'      then 1
    else 0
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2) الملفات الشخصية (profiles)
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id                 uuid primary key references auth.users(id) on delete cascade,
  username           citext unique,
  display_name       text,
  avatar_url         text default 'profiles/onefree.png',   -- مسار نسبي داخل مخزن site-assets
  banner_url         text,
  custom_status      text check (custom_status is null or char_length(custom_status) <= 60),
  bio                text check (bio is null or char_length(bio) <= 400),
  country            text,
  role               text not null default 'user'    check (role in ('user','creator','moderator','admin')),
  plan               text not null default 'free'    check (plan in ('free','epic','legendary')),
  plan_expires_at    timestamptz,
  user_level         int  not null default 1,
  equipped_item_id   text not null default 'none',
  show_online_status boolean not null default false,
  last_seen_at       timestamptz default now(),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
create index if not exists profiles_plan_idx on public.profiles(plan, plan_expires_at);
create index if not exists profiles_role_idx on public.profiles(role);

-- ---------------------------------------------------------------------------
-- 3) الخطط وأسعارها وحدود التحميل
-- ---------------------------------------------------------------------------
create table if not exists public.subscription_plans (
  id             text primary key check (id in ('free','epic','legendary')),
  name           text not null,
  tagline        text,
  price_lyd      numeric(10,2) not null default 0,
  price_usd      numeric(10,2) not null default 0,
  duration_days  int not null default 30,
  download_limit int,                        -- null = بلا حد
  limit_period   text default 'day' check (limit_period in ('day','month','none')),
  active         boolean not null default true,
  sort_order     int not null default 0,
  updated_at     timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- 4) طرق الدفع وبياناتها (التحويل اليدوي + بوابات مستقبلية)
-- ---------------------------------------------------------------------------
create table if not exists public.payment_method_settings (
  id                 bigserial primary key,
  code               text unique not null,       -- onepay / lpay / edfa3li / mobicash / sadad / tadawul / bank_transfer / stripe
  name               text not null,
  account_name       text,                       -- اسم المستفيد
  account_identifier text,                       -- رقم الحساب أو المحفظة
  instructions       text,
  extra_fields       jsonb not null default '[]'::jsonb,
  provider           text not null default 'manual' check (provider in ('manual','stripe','paymob','tap')),
  enabled            boolean not null default false,
  sort_order         int not null default 0,
  created_at         timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- 5) طلبات الدفع (الاشتراكات)
-- ---------------------------------------------------------------------------
create table if not exists public.payment_orders (
  id                    uuid primary key default gen_random_uuid(),
  user_id               uuid not null references public.profiles(id) on delete cascade,
  plan_id               text not null references public.subscription_plans(id),
  amount_lyd            numeric(10,2) not null,                 -- يحدده الخادم فقط
  currency              text not null default 'LYD',
  payment_method        text not null,
  reference_code        text unique not null,                   -- AS-XXXXXXXX
  transaction_reference text,
  receipt_url           text,                                   -- مسار خاص داخل bucket payment-receipts
  status                text not null default 'pending'
                        check (status in ('pending','submitted','approved','rejected','cancelled')),
  provider              text not null default 'manual',
  provider_payment_id   text,                                   -- لبوابات الدفع الإلكتروني
  reject_reason         text,
  reviewed_by           uuid references public.profiles(id),
  reviewed_at           timestamptz,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now()
);
create index if not exists payment_orders_user_idx on public.payment_orders(user_id, created_at desc);
create index if not exists payment_orders_status_idx on public.payment_orders(status, created_at desc);

-- ---------------------------------------------------------------------------
-- 6) المحتوى (الإضافات والعوالم وحزم الموارد والخرائط)
-- ---------------------------------------------------------------------------
create table if not exists public.content_items (
  id             uuid primary key default gen_random_uuid(),
  title          text not null,
  slug           text unique,
  description    text,
  content_type   text not null default 'addon'
                 check (content_type in ('addon','world','resource_pack','map','skin')),
  category       text default 'other'
                 check (category in ('adventure','survival','pvp','building','skins','other')),
  plan           text not null default 'free' check (plan in ('free','epic','legendary')),
  version        text,
  cover_url      text,
  file_path      text,                                          -- مسار خاص داخل content-files
  file_size      bigint,
  ac_price       int not null default 0,                        -- 0 = بدون تسعير AC
  is_published   boolean not null default false,
  is_featured    boolean not null default false,
  is_exclusive   boolean not null default false,
  downloads_count int not null default 0,
  views_count    int not null default 0,
  rating         numeric(3,2) not null default 0,
  rating_count   int not null default 0,
  created_by     uuid references public.profiles(id) on delete set null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index if not exists content_items_browse_idx on public.content_items(is_published, content_type, created_at desc);
create index if not exists content_items_featured_idx on public.content_items(is_featured, is_published, created_at desc);

create table if not exists public.content_favorites (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  item_id    uuid not null references public.content_items(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, item_id)
);

create table if not exists public.content_downloads (
  id         bigserial primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  item_id    uuid not null references public.content_items(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists content_downloads_user_idx on public.content_downloads(user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 7) اقتصاد AddonCoins (AC)
-- ---------------------------------------------------------------------------
create table if not exists public.ac_wallets (
  user_id      uuid primary key references public.profiles(id) on delete cascade,
  balance      int not null default 0 check (balance >= 0),
  total_earned int not null default 0,
  total_spent  int not null default 0,
  updated_at   timestamptz not null default now()
);

create table if not exists public.ac_transactions (
  id            bigserial primary key,
  user_id       uuid not null references public.profiles(id) on delete cascade,
  amount        int not null,                       -- موجب = مكافأة، سالب = خصم/فتح
  kind          text not null check (kind in ('task','daily','streak','admin_reward','admin_adjustment','unlock','event','welcome')),
  reason        text,
  task_id       text,
  item_id       uuid references public.content_items(id) on delete set null,
  request_key   text unique,                        -- منع التكرار (idempotency)
  balance_after int not null,
  created_by    uuid references public.profiles(id),
  created_at    timestamptz not null default now()
);
create index if not exists ac_transactions_user_idx on public.ac_transactions(user_id, created_at desc);

create table if not exists public.ac_tasks (
  id          text primary key,
  title_ar    text not null,
  title_en    text,
  description text,
  reward      int not null default 10,
  frequency   text not null default 'once' check (frequency in ('once','daily','streak','manual')),
  streak_days jsonb not null default '[]'::jsonb,   -- مثل [3,7,14,30] لمكافآت السلسلة
  max_claims  int,
  active      boolean not null default true,
  sort_order  int not null default 0,
  created_at  timestamptz not null default now()
);

create table if not exists public.ac_task_claims (
  id         bigserial primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  task_id    text not null references public.ac_tasks(id) on delete cascade,
  day        date not null default (now() at time zone 'utc')::date,
  amount     int not null,
  created_at timestamptz not null default now()
);
create unique index if not exists ac_task_claims_daily_uniq on public.ac_task_claims(user_id, task_id, day);
create unique index if not exists ac_task_claims_once_uniq on public.ac_task_claims(user_id, task_id) where task_id in (select id from public.ac_tasks where frequency = 'once');

create table if not exists public.ac_events (
  id         uuid primary key default gen_random_uuid(),
  title      text not null,
  amount     int not null,
  target     text not null default 'all' check (target in ('all','users')),
  target_ids uuid[],
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- 8) الشات (13 غرفة، رسائل، تفاعلات، كتم، بلاغات، إشعارات، أصدقاء)
-- ---------------------------------------------------------------------------
create table if not exists public.chat_channels (
  id               text primary key,
  name             text not null,
  icon             text,
  category         text not null default 'main' check (category in ('main','lang')),
  description      text,
  sort_order       int not null default 0,
  is_locked        boolean not null default false,          -- قراءة فقط
  slowmode_seconds int not null default 0 check (slowmode_seconds between 0 and 21600),
  post_permission  text not null default 'members'
                   check (post_permission in ('everyone','members','creators_admin','admin_only')),
  created_at       timestamptz not null default now()
);

create table if not exists public.chat_messages (
  id                bigserial primary key,
  channel_id        text not null references public.chat_channels(id) on delete cascade,
  user_id           uuid not null references public.profiles(id) on delete cascade,
  content           text,
  message_type      text not null default 'text'
                    check (message_type in ('text','sticker','image','audio','video','system','announcement')),
  sticker_url       text,
  image_path        text,
  media_path        text,
  media_mime_type   text,
  media_duration    int,
  media_size        bigint,
  reply_to_id       bigint references public.chat_messages(id) on delete set null,
  thread_root_id    bigint references public.chat_messages(id) on delete set null,
  status            text not null default 'published' check (status in ('published','hidden','deleted')),
  is_announcement   boolean not null default false,
  is_pinned         boolean not null default false,
  mentions          uuid[] not null default '{}',
  deleted_by        uuid references public.profiles(id),
  created_at        timestamptz not null default now(),
  edited_at         timestamptz,
  constraint chat_messages_len check (content is null or char_length(content) <= 500)
);
create index if not exists chat_messages_channel_idx on public.chat_messages(channel_id, created_at desc);
create index if not exists chat_messages_thread_idx on public.chat_messages(thread_root_id, created_at);
create index if not exists chat_messages_pinned_idx on public.chat_messages(channel_id, is_pinned) where is_pinned;

create table if not exists public.chat_reactions (
  id         bigserial primary key,
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  emoji      text not null,
  created_at timestamptz not null default now(),
  unique (message_id, user_id, emoji)
);
create index if not exists chat_reactions_message_idx on public.chat_reactions(message_id);

create table if not exists public.chat_mutes (
  id         bigserial primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  reason     text,
  expires_at timestamptz,
  created_by uuid references public.profiles(id),
  active     boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists chat_mutes_active_idx on public.chat_mutes(user_id, active);

create table if not exists public.chat_bans (
  id         bigserial primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  reason     text,
  created_by uuid references public.profiles(id),
  active     boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.chat_reports (
  id          bigserial primary key,
  message_id  bigint references public.chat_messages(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason      text not null check (reason in ('spam','insult','inappropriate','other')),
  note        text,
  status      text not null default 'open' check (status in ('open','closed')),
  closed_by   uuid references public.profiles(id),
  closed_at   timestamptz,
  created_at  timestamptz not null default now()
);
create index if not exists chat_reports_status_idx on public.chat_reports(status, created_at desc);

create table if not exists public.chat_rule_acceptances (
  user_id     uuid primary key references public.profiles(id) on delete cascade,
  accepted_at timestamptz not null default now(),
  version     text not null default 'v1'
);

-- قواعد المجتمع قابلة للتعديل من لوحة الإدارة لكل لغة
create table if not exists public.chat_rules (
  lang       text primary key,
  title      text,
  rules      jsonb not null default '[]'::jsonb,
  accept_text text,
  decline_text text,
  updated_at timestamptz not null default now()
);

create table if not exists public.chat_typing (
  channel_id text not null references public.chat_channels(id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  updated_at timestamptz not null default now(),
  primary key (channel_id, user_id)
);

create table if not exists public.chat_read_state (
  user_id     uuid not null references public.profiles(id) on delete cascade,
  channel_id  text not null references public.chat_channels(id) on delete cascade,
  last_read_id bigint not null default 0,
  updated_at  timestamptz not null default now(),
  primary key (user_id, channel_id)
);

create table if not exists public.chat_notifications (
  id           bigserial primary key,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  actor_id     uuid references public.profiles(id) on delete set null,
  type         text not null check (type in ('friend_request','mention','reply','thread','plan','system','payment','creator')),
  message_id   bigint references public.chat_messages(id) on delete cascade,
  channel_id   text references public.chat_channels(id) on delete cascade,
  body         text,
  meta         jsonb not null default '{}'::jsonb,
  is_read      boolean not null default false,
  created_at   timestamptz not null default now()
);
create index if not exists chat_notifications_recipient_idx on public.chat_notifications(recipient_id, is_read, created_at desc);

create table if not exists public.user_friends (
  id          bigserial primary key,
  requester_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id  uuid not null references public.profiles(id) on delete cascade,
  status       text not null default 'pending' check (status in ('pending','accepted','rejected')),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (requester_id, receiver_id),
  check (requester_id <> receiver_id)
);

-- ---------------------------------------------------------------------------
-- 9) المبدعون
-- ---------------------------------------------------------------------------
create table if not exists public.creator_applications (
  id                bigserial primary key,
  user_id           uuid not null references public.profiles(id) on delete cascade,
  country           text,
  content_types     text,
  about             text,
  experience        text,
  publishing_plan   text,
  previous_work_url text,
  portfolio_urls    text,
  wallet_links      text,
  motivation        text,
  bio               text,
  status            text not null default 'pending'
                    check (status in ('pending','approved','rejected','more_info')),
  admin_note        text,
  reviewed_by       uuid references public.profiles(id),
  reviewed_at       timestamptz,
  created_at        timestamptz not null default now()
);
create index if not exists creator_applications_status_idx on public.creator_applications(status, created_at desc);

create table if not exists public.creator_submissions (
  id             bigserial primary key,
  creator_id     uuid not null references public.profiles(id) on delete cascade,
  title          text not null,
  content_type   text not null default 'addon',
  description    text,
  category       text,
  version        text,
  plan           text not null default 'free',
  cover_url      text,
  file_path      text,
  status         text not null default 'pending' check (status in ('pending','approved','rejected')),
  admin_note     text,
  content_item_id uuid references public.content_items(id) on delete set null,
  reviewed_by    uuid references public.profiles(id),
  reviewed_at    timestamptz,
  created_at     timestamptz not null default now()
);
create index if not exists creator_submissions_status_idx on public.creator_submissions(status, created_at desc);

create table if not exists public.featured_creators (
  user_id    uuid primary key references public.profiles(id) on delete cascade,
  sort_order int not null default 0,
  added_by   uuid references public.profiles(id),
  created_at timestamptz not null default now()
);

create table if not exists public.creator_follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  creator_id  uuid not null references public.profiles(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (follower_id, creator_id),
  check (follower_id <> creator_id)
);

create table if not exists public.creator_tips (
  id               bigserial primary key,
  creator_id       uuid not null references public.profiles(id) on delete cascade,
  sender_id        uuid not null references public.profiles(id) on delete cascade,
  amount_lyd       numeric(10,2) not null,
  method           text not null,
  message          text,
  receipt_url      text,
  transaction_reference text,
  reference_code   text unique not null,
  status           text not null default 'pending' check (status in ('pending','approved','rejected')),
  reject_reason    text,
  reviewed_by      uuid references public.profiles(id),
  reviewed_at      timestamptz,
  created_at       timestamptz not null default now()
);
create index if not exists creator_tips_status_idx on public.creator_tips(status, created_at desc);

-- ---------------------------------------------------------------------------
-- 10) الأصول (أفاتارات، أغلفة، ملصقات، أدوات الشات) وحزم الملصقات
-- ---------------------------------------------------------------------------
create table if not exists public.sticker_packs (
  id         text primary key,
  name       text not null,
  icon_url   text,
  sort_order int not null default 0,
  active     boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.site_assets (
  id            bigserial primary key,
  asset_type    text not null check (asset_type in ('avatar','banner','sticker','item')),
  name          text not null,
  file_url      text not null,
  sticker_pack  text references public.sticker_packs(id) on delete set null,
  required_plan text not null default 'free' check (required_plan in ('free','epic','legendary','admin')),
  animated      boolean not null default false,
  sort_order    int not null default 0,
  enabled       boolean not null default true,
  created_at    timestamptz not null default now()
);
create index if not exists site_assets_type_idx on public.site_assets(asset_type, enabled, sort_order);

-- ---------------------------------------------------------------------------
-- 11) إعدادات التطبيق وسجل العمليات الإدارية
-- ---------------------------------------------------------------------------
create table if not exists public.app_settings (
  key        text primary key,
  value      jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now(),
  updated_by uuid references public.profiles(id)
);

create table if not exists public.admin_audit_log (
  id          bigserial primary key,
  actor_id    uuid references public.profiles(id) on delete set null,
  action      text not null,
  target_type text,
  target_id   text,
  details     jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now()
);
create index if not exists admin_audit_log_created_idx on public.admin_audit_log(created_at desc);

-- ---------------------------------------------------------------------------
-- 12) تحديث updated_at تلقائياً
-- ---------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

do $$
declare t text;
begin
  foreach t in array array['profiles','subscription_plans','payment_orders','content_items','app_settings','user_friends']
  loop
    execute format('drop trigger if exists trg_touch_%1$s on public.%1$s', t);
    execute format('create trigger trg_touch_%1$s before update on public.%1$s
                    for each row execute function public.touch_updated_at()', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- 13) إنشاء ملف شخصي + محفظة AC تلقائياً لكل مستخدم جديد
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_username text;
  v_welcome  int := 50;
begin
  v_username := coalesce(
      nullif(new.raw_user_meta_data->>'username', ''),
      nullif(split_part(coalesce(new.email,''), '@', 1), ''),
      'player_' || substr(new.id::text, 1, 8)
  );

  -- ضمان فرادة اسم المستخدم
  while exists (select 1 from public.profiles p where p.username = v_username) loop
    v_username := v_username || floor(random()*10)::int;
  end loop;

  insert into public.profiles (id, username, display_name)
  values (new.id, v_username, v_username)
  on conflict (id) do nothing;

  insert into public.ac_wallets (user_id, balance, total_earned)
  values (new.id, v_welcome, v_welcome)
  on conflict (user_id) do nothing;

  insert into public.ac_transactions (user_id, amount, kind, reason, request_key, balance_after)
  values (new.id, v_welcome, 'welcome', 'Welcome reward', 'welcome:' || new.id::text, v_welcome)
  on conflict (request_key) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 14) حماية أعمدة الملف الشخصي (الخطة/الرتبة/المستوى لا تُعدّل من التطبيق)
-- ---------------------------------------------------------------------------
create or replace function public.guard_profile_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor_role text;
begin
  -- من له دور admin يستطيع تغيير كل شيء
  select role into v_actor_role from public.profiles where id = auth.uid();

  if coalesce(v_actor_role,'') = 'admin' then
    return new;
  end if;

  if new.role            is distinct from old.role
     or new.plan          is distinct from old.plan
     or new.plan_expires_at is distinct from old.plan_expires_at
     or new.user_level    is distinct from old.user_level
     or new.username      is distinct from old.username
     or new.equipped_item_id is distinct from old.equipped_item_id
     or new.avatar_url    is distinct from old.avatar_url
     or new.banner_url    is distinct from old.banner_url
  then
    raise exception 'forbidden_profile_column';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_guard_profile_update on public.profiles;
create trigger trg_guard_profile_update
  before update on public.profiles
  for each row execute function public.guard_profile_update();


-- ############################################################################
-- ##  القسم: 02_functions_core.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 02) دوال الخادم الأساسية
--  المحتوى · الخطط · المدفوعات · AC · الملف الشخصي
--  كل عملية حساسة تتم هنا (SECURITY DEFINER) — التطبيق لا يعدّل الجداول مباشرة
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) مساعدات الصلاحيات
-- ---------------------------------------------------------------------------
create or replace function public.current_role_of(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select coalesce((select role from public.profiles where id = p_user), 'user');
$$;

create or replace function public.is_admin(p_user uuid default auth.uid())
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_role_of(p_user) = 'admin';
$$;

create or replace function public.is_staff(p_user uuid default auth.uid())
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_role_of(p_user) in ('admin','moderator');
$$;

-- الخطة الفعلية مع احتساب انتهاء الصلاحية (تُحسب في كل طلب)
create or replace function public.effective_plan(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select case
    when p_user is null then 'free'
    when (select plan_expires_at from public.profiles where id = p_user) is null
         then coalesce((select plan from public.profiles where id = p_user), 'free')
    when (select plan_expires_at from public.profiles where id = p_user) > now()
         then coalesce((select plan from public.profiles where id = p_user), 'free')
    else 'free'
  end;
$$;

create or replace function public.effective_plan_label(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select public.effective_plan(p_user);
$$;

-- هل يملك المستخدم صلاحية الوصول لهذا المحتوى؟
create or replace function public.can_access_item(p_item_id uuid, p_user uuid default auth.uid())
returns boolean
language plpgsql stable security definer set search_path = public
as $$
declare
  v_item public.content_items;
  v_plan text;
  v_unlocked boolean;
begin
  select * into v_item from public.content_items where id = p_item_id;
  if not found then return false; end if;

  -- المالك والمدير يمكنهما دائماً
  if p_user is not null then
    if v_item.created_by = p_user then return true; end if;
    if public.is_admin(p_user) then return true; end if;
  end if;

  if v_item.plan = 'free' then
    return p_user is not null;
  end if;

  v_plan := public.effective_plan(p_user);
  if public.plan_rank(v_plan) >= public.plan_rank(v_item.plan) then
    return true;
  end if;

  -- فتح مدفوع بالـ AC (مرة واحدة لكل مستخدم/عنصر)
  if v_item.ac_price > 0 and p_user is not null then
    select exists(
      select 1 from public.ac_transactions t
      where t.user_id = p_user and t.item_id = p_item_id and t.kind = 'unlock'
    ) into v_unlocked;
    if v_unlocked then return true; end if;
  end if;

  return false;
end;
$$;

-- هل وصل المستخدم إلى حد التحميلات في خطته؟
create or replace function public.download_limit_reached(p_user uuid default auth.uid())
returns boolean
language plpgsql stable security definer set search_path = public
as $$
declare
  v_plan        text;
  v_limit       int;
  v_period      text;
  v_since       timestamptz;
  v_count       int;
begin
  if p_user is null then return true; end if;
  if public.is_admin(p_user) then return false; end if;

  v_plan := public.effective_plan(p_user);
  select download_limit, limit_period into v_limit, v_period
  from public.subscription_plans where id = v_plan;

  if v_limit is null or coalesce(v_period,'none') = 'none' then
    return false;
  end if;

  v_since := case when v_period = 'month' then date_trunc('month', now()) else date_trunc('day', now()) end;

  select count(*) into v_count
  from public.content_downloads
  where user_id = p_user and created_at >= v_since;

  return v_count >= v_limit;
end;
$$;

-- ---------------------------------------------------------------------------
-- 1) تسجيل مشاهدة المحتوى
-- ---------------------------------------------------------------------------
create or replace function public.record_content_view(p_item_id uuid)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.content_items
     set views_count = views_count + 1
   where id = p_item_id and is_published;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2) تسجيل التحميل (يفحص تسجيل الدخول + الخطة + حد التحميلات)
--    يعيد: { ok, bucket, path, message }
-- ---------------------------------------------------------------------------
create or replace function public.record_content_download(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  v_item public.content_items;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if coalesce(v_item.file_path,'') = '' then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  if not public.can_access_item(p_item_id, v_uid) then
    return jsonb_build_object('ok', false, 'message', 'no_permission');
  end if;

  if public.download_limit_reached(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'download_limit');
  end if;

  insert into public.content_downloads (user_id, item_id) values (v_uid, p_item_id);
  update public.content_items set downloads_count = downloads_count + 1 where id = p_item_id;

  return jsonb_build_object('ok', true, 'bucket', 'content-files', 'path', v_item.file_path,
                            'title', v_item.title);
end;
$$;

-- ---------------------------------------------------------------------------
-- 3) المفضلة
-- ---------------------------------------------------------------------------
create or replace function public.toggle_favorite(p_item_id uuid)
returns boolean
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_exists boolean;
begin
  if v_uid is null then raise exception 'sign_in_required'; end if;

  select exists(select 1 from public.content_favorites where user_id = v_uid and item_id = p_item_id)
    into v_exists;

  if v_exists then
    delete from public.content_favorites where user_id = v_uid and item_id = p_item_id;
    return false;
  else
    insert into public.content_favorites(user_id, item_id) values (v_uid, p_item_id)
    on conflict do nothing;
    return true;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4) الاقتصاد: فتح محتوى بالـ AC / المطالبة بالمهام / تعديل إداري
-- ---------------------------------------------------------------------------
create or replace function public.ac_unlock_content(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_item    public.content_items;
  v_price   int;
  v_balance int;
  v_already boolean;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  v_price := coalesce(v_item.ac_price, 0);
  if v_price <= 0 then
    return jsonb_build_object('ok', false, 'message', 'not_priced');
  end if;

  -- هل فُتح سابقاً؟ (لا خصم مزدوج)
  select exists(select 1 from public.ac_transactions
                where user_id = v_uid and item_id = p_item_id and kind = 'unlock') into v_already;
  if v_already then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_unlocked');
  end if;

  select balance into v_balance from public.ac_wallets where user_id = v_uid for update;
  if coalesce(v_balance,0) < v_price then
    return jsonb_build_object('ok', false, 'message', 'not_enough_ac');
  end if;

  update public.ac_wallets
     set balance = balance - v_price,
         total_spent = total_spent + v_price,
         updated_at = now()
   where user_id = v_uid;

  insert into public.ac_transactions(user_id, amount, kind, reason, item_id, request_key, balance_after)
  values (v_uid, -v_price, 'unlock', coalesce(v_item.title,'Content unlock'), p_item_id,
          'unlock:' || v_uid::text || ':' || p_item_id::text, v_balance - v_price)
  on conflict (request_key) do nothing;

  return jsonb_build_object('ok', true, 'charged', v_price, 'balance', v_balance - v_price);
end;
$$;

create or replace function public.ac_claim_task(p_task_id text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid      uuid := auth.uid();
  v_task     public.ac_tasks;
  v_count    int;
  v_reward   int;
  v_balance  int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_task from public.ac_tasks where id = p_task_id and active;
  if not found then return jsonb_build_object('ok', false, 'message', 'task_not_found'); end if;

  v_reward := v_task.reward;

  -- تحقق الشروط المبدئية لكل مهمة
  if v_task.frequency = 'manual' then
    return jsonb_build_object('ok', false, 'message', 'manual_only');
  end if;

  select count(*) into v_count from public.ac_task_claims where user_id = v_uid and task_id = p_task_id;

  if v_task.frequency = 'once' and v_count > 0 then
    return jsonb_build_object('ok', false, 'message', 'already_claimed');
  end if;

  if v_task.frequency = 'daily' then
    if exists(select 1 from public.ac_task_claims
              where user_id = v_uid and task_id = p_task_id
                and day = (now() at time zone 'utc')::date) then
      return jsonb_build_object('ok', false, 'message', 'already_claimed');
    end if;
  end if;

  if v_task.frequency = 'streak' then
    if exists(select 1 from public.ac_task_claims
              where user_id = v_uid and task_id = p_task_id
                and day = (now() at time zone 'utc')::date) then
      return jsonb_build_object('ok', false, 'message', 'already_claimed');
    end if;
  end if;

  if v_task.max_claims is not null and v_count >= v_task.max_claims then
    return jsonb_build_object('ok', false, 'message', 'max_claims');
  end if;

  insert into public.ac_task_claims(user_id, task_id, amount) values (v_uid, p_task_id, v_reward);

  insert into public.ac_transactions(user_id, amount, kind, reason, task_id, request_key, balance_after)
  select v_uid, v_reward,
         case when v_task.frequency = 'daily' then 'daily' else 'task' end,
         coalesce(v_task.title_en, v_task.title_ar, v_task.id),
         p_task_id,
         'task:' || v_uid::text || ':' || p_task_id || ':' || (now() at time zone 'utc')::date::text,
         0
  on conflict (request_key) do nothing;

  update public.ac_wallets
     set balance = balance + v_reward,
         total_earned = total_earned + v_reward,
         updated_at = now()
   where user_id = v_uid;

  select balance into v_balance from public.ac_wallets where user_id = v_uid;

  update public.ac_transactions
     set balance_after = v_balance
   where user_id = v_uid
     and request_key = 'task:' || v_uid::text || ':' || p_task_id || ':' || (now() at time zone 'utc')::date::text;

  return jsonb_build_object('ok', true, 'reward', v_reward, 'balance', v_balance);
end;
$$;

create or replace function public.ac_admin_adjust(
  p_user_id uuid,
  p_amount  int,
  p_reason  text default 'Admin Adjustment',
  p_request_key text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_balance int;
  v_key text;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;

  v_key := coalesce(p_request_key, 'admin:' || auth.uid()::text || ':' || p_user_id::text || ':' || gen_random_uuid()::text);

  insert into public.ac_wallets(user_id) values (p_user_id) on conflict (user_id) do nothing;

  update public.ac_wallets
     set balance = greatest(balance + p_amount, 0),
         total_earned = total_earned + greatest(p_amount, 0),
         total_spent  = total_spent  + greatest(-p_amount, 0),
         updated_at = now()
   where user_id = p_user_id
   returning balance into v_balance;

  insert into public.ac_transactions(user_id, amount, kind, reason, request_key, balance_after, created_by)
  values (p_user_id, p_amount,
          case when p_amount >= 0 then 'admin_reward' else 'admin_adjustment' end,
          p_reason, v_key, v_balance, auth.uid())
  on conflict (request_key) do nothing;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'ac_adjust', 'profile', p_user_id::text,
          jsonb_build_object('amount', p_amount, 'reason', p_reason));

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (p_user_id, auth.uid(), 'system',
          case when p_amount >= 0
               then 'You received ' || p_amount || ' AC'
               else 'A deduction of ' || abs(p_amount) || ' AC was applied' end,
          jsonb_build_object('amount', p_amount));

  return jsonb_build_object('ok', true, 'balance', v_balance);
end;
$$;

-- منح مكافأة جماعية لكل المستخدمين
create or replace function public.ac_admin_grant_all(p_amount int, p_reason text default 'Admin Reward')
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_count int := 0;
  r record;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if coalesce(p_amount,0) <= 0 then return jsonb_build_object('ok', false, 'message', 'invalid_amount'); end if;

  for r in select id from public.profiles loop
    perform public.ac_admin_adjust(r.id, p_amount, p_reason,
      'grantall:' || auth.uid()::text || ':' || r.id::text || ':' || now()::date::text);
    v_count := v_count + 1;
  end loop;

  insert into public.admin_audit_log(actor_id, action, target_type, details)
  values (auth.uid(), 'ac_grant_all', 'profiles', jsonb_build_object('amount', p_amount, 'users', v_count));

  return jsonb_build_object('ok', true, 'users', v_count);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5) المدفوعات: إنشاء طلب / إرسال إيصال / موافقة / رفض
-- ---------------------------------------------------------------------------
create or replace function public.create_payment_order(p_plan_id text, p_method text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_plan    public.subscription_plans;
  v_method  public.payment_method_settings;
  v_ref     text;
  v_open    int;
  v_order   public.payment_orders;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_plan from public.subscription_plans where id = p_plan_id and active;
  if not found then return jsonb_build_object('ok', false, 'message', 'plan_unavailable'); end if;

  select * into v_method from public.payment_method_settings where code = p_method;
  if not found then return jsonb_build_object('ok', false, 'message', 'method_unavailable'); end if;
  if not v_method.enabled then return jsonb_build_object('ok', false, 'message', 'method_disabled'); end if;

  -- حد أقصى للطلبات المفتوحة لكل مستخدم (مكافحة الإساءة)
  select count(*) into v_open from public.payment_orders
   where user_id = v_uid and status in ('pending','submitted');
  if v_open >= 3 then
    return jsonb_build_object('ok', false, 'message', 'too_many_open_orders');
  end if;

  -- رمز طلب AS-XXXXXXXX
  loop
    v_ref := 'AS-' || upper(substr(replace(gen_random_uuid()::text,'-',''), 1, 8));
    exit when not exists (select 1 from public.payment_orders where reference_code = v_ref);
  end loop;

  insert into public.payment_orders(user_id, plan_id, amount_lyd, payment_method, reference_code, provider)
  values (v_uid, p_plan_id, v_plan.price_lyd, p_method, v_ref, v_method.provider)
  returning * into v_order;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'payment_order_created', 'payment_order', v_order.id::text,
          jsonb_build_object('plan', p_plan_id, 'amount', v_plan.price_lyd, 'method', p_method));

  return jsonb_build_object('ok', true, 'order_id', v_order.id, 'reference_code', v_ref,
                            'amount_lyd', v_plan.price_lyd, 'currency', v_order.currency,
                            'duration_days', v_plan.duration_days,
                            'account_name', v_method.account_name,
                            'account_identifier', v_method.account_identifier,
                            'instructions', v_method.instructions,
                            'method_name', v_method.name,
                            'provider', v_method.provider);
end;
$$;

create or replace function public.submit_payment_receipt(
  p_order_id uuid,
  p_receipt_path text,
  p_transaction_reference text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_order public.payment_orders;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_order from public.payment_orders where id = p_order_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  if v_order.user_id <> v_uid then return jsonb_build_object('ok', false, 'message', 'forbidden'); end if;
  if v_order.status <> 'pending' then
    return jsonb_build_object('ok', false, 'message', 'invalid_status');
  end if;

  -- يجب أن يكون الملف داخل مجلد المستخدم نفسه
  if p_receipt_path is null or position(v_uid::text in p_receipt_path) = 0 then
    return jsonb_build_object('ok', false, 'message', 'invalid_path');
  end if;

  update public.payment_orders
     set receipt_url = p_receipt_path,
         transaction_reference = coalesce(p_transaction_reference, transaction_reference),
         status = 'submitted',
         updated_at = now()
   where id = p_order_id;

  return jsonb_build_object('ok', true, 'status', 'submitted');
end;
$$;

-- تفعيل الاشتراك بعد تأكيد الإيصال (Admin فقط)
create or replace function public.approve_payment_order(p_order_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_order public.payment_orders;
  v_plan  public.subscription_plans;
  v_base  timestamptz;
  v_new_expiry timestamptz;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_order from public.payment_orders where id = p_order_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  if v_order.status = 'approved' then return jsonb_build_object('ok', false, 'message', 'already_approved'); end if;
  if v_order.status not in ('submitted','pending') then
    return jsonb_build_object('ok', false, 'message', 'invalid_status');
  end if;

  select * into v_plan from public.subscription_plans where id = v_order.plan_id;

  -- تمديد من تاريخ الانتهاء الحالي إن كان في المستقبل (تجديد مبكر)
  select greatest(now(), coalesce(plan_expires_at, now())) into v_base
  from public.profiles where id = v_order.user_id;

  v_new_expiry := coalesce(v_base, now()) + make_interval(days => coalesce(v_plan.duration_days, 30));

  update public.payment_orders
     set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
   where id = p_order_id;

  update public.profiles
     set plan = v_order.plan_id,
         plan_expires_at = v_new_expiry,
         updated_at = now()
   where id = v_order.user_id;

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (v_order.user_id, auth.uid(), 'plan',
          'Your ' || v_order.plan_id || ' plan is active until ' || to_char(v_new_expiry, 'YYYY-MM-DD'),
          jsonb_build_object('plan', v_order.plan_id, 'expires_at', v_new_expiry));

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_approved', 'payment_order', p_order_id::text,
          jsonb_build_object('user', v_order.user_id, 'plan', v_order.plan_id,
                             'amount', v_order.amount_lyd, 'expires_at', v_new_expiry));

  return jsonb_build_object('ok', true, 'plan', v_order.plan_id, 'expires_at', v_new_expiry);
end;
$$;

create or replace function public.reject_payment_order(p_order_id uuid, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_order public.payment_orders;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  update public.payment_orders
     set status = 'rejected', reject_reason = p_reason,
         reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
   where id = p_order_id
   returning * into v_order;

  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (v_order.user_id, auth.uid(), 'payment',
          coalesce('Your payment order was rejected: ' || p_reason, 'Your payment order was rejected'),
          jsonb_build_object('order_id', p_order_id));

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_rejected', 'payment_order', p_order_id::text,
          jsonb_build_object('reason', p_reason, 'user', v_order.user_id));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6) تعديل ملف مستخدم بواسطة الإدارة (خطة / رتبة / تاريخ انتهاء)
-- ---------------------------------------------------------------------------
create or replace function public.admin_update_profile(
  p_user_id uuid,
  p_plan text default null,
  p_role text default null,
  p_expires timestamptz default null,
  p_grant_days int default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_target public.profiles;
  v_admin_count int;
  v_new_expiry timestamptz := p_expires;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_target from public.profiles where id = p_user_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  -- حماية: لا يمكن إزالة آخر Admin في النظام
  if p_role is not null and v_target.role = 'admin' and p_role <> 'admin' then
    select count(*) into v_admin_count from public.profiles where role = 'admin';
    if v_admin_count <= 1 then
      return jsonb_build_object('ok', false, 'message', 'last_admin');
    end if;
  end if;

  if p_grant_days is not null then
    v_new_expiry := greatest(now(), coalesce(v_target.plan_expires_at, now()))
                    + make_interval(days => p_grant_days);
  end if;

  update public.profiles
     set plan = coalesce(p_plan, plan),
         role = coalesce(p_role, role),
         plan_expires_at = case
            when p_grant_days is not null then v_new_expiry
            when p_expires is not null then p_expires
            else plan_expires_at
         end,
         updated_at = now()
   where id = p_user_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'profile_updated', 'profile', p_user_id::text,
          jsonb_build_object('plan', p_plan, 'role', p_role, 'expires', v_new_expiry));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) تعيين أفاتار/غلاف/أداة مع التحقق من الخطة على الخادم (إصلاح ثغرة الوثيقة §3.3)
-- ---------------------------------------------------------------------------
create or replace function public.set_profile_asset(p_kind text, p_value text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid       uuid := auth.uid();
  v_asset     public.site_assets;
  v_plan      text;
  v_is_admin  boolean;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if p_kind not in ('avatar','banner','item') then
    return jsonb_build_object('ok', false, 'message', 'invalid_kind');
  end if;

  -- السماح بإعادة التعيين للقيمة الافتراضية
  if p_value is null or p_value = '' or p_value = 'none' then
    if p_kind = 'avatar' then
      update public.profiles set avatar_url = 'profiles/onefree.png' where id = v_uid;
    elsif p_kind = 'banner' then
      update public.profiles set banner_url = null where id = v_uid;
    else
      update public.profiles set equipped_item_id = 'none' where id = v_uid;
    end if;
    return jsonb_build_object('ok', true, 'value', 'none');
  end if;

  select * into v_asset from public.site_assets
   where enabled and asset_type = (case when p_kind = 'item' then 'item' else p_kind end)
     and (file_url = p_value or name = p_value)
   limit 1;

  if not found then return jsonb_build_object('ok', false, 'message', 'asset_not_found'); end if;

  v_plan     := public.effective_plan(v_uid);
  v_is_admin := public.is_admin(v_uid);

  if v_asset.required_plan = 'admin' and not v_is_admin then
    return jsonb_build_object('ok', false, 'message', 'admin_only_asset');
  end if;

  if v_asset.required_plan <> 'admin'
     and not v_is_admin
     and public.plan_rank(v_plan) < public.plan_rank(v_asset.required_plan) then
    return jsonb_build_object('ok', false, 'message', 'plan_too_low',
                              'required_plan', v_asset.required_plan);
  end if;

  if p_kind = 'avatar' then
    update public.profiles set avatar_url = v_asset.file_url, updated_at = now() where id = v_uid;
  elsif p_kind = 'banner' then
    update public.profiles set banner_url = v_asset.file_url, updated_at = now() where id = v_uid;
  else
    update public.profiles set equipped_item_id = coalesce(v_asset.name, v_asset.file_url), updated_at = now() where id = v_uid;
  end if;

  return jsonb_build_object('ok', true, 'value', v_asset.file_url, 'required_plan', v_asset.required_plan);
end;
$$;

-- تحديث الملف الشخصي (الحقول النصية فقط — لا خطة/رتبة)
create or replace function public.update_my_profile(
  p_username text default null,
  p_bio text default null,
  p_custom_status text default null,
  p_show_online boolean default null,
  p_country text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if p_username is not null then
    if char_length(p_username) < 3 then
      return jsonb_build_object('ok', false, 'message', 'username_too_short');
    end if;
    if exists(select 1 from public.profiles where username = p_username and id <> v_uid) then
      return jsonb_build_object('ok', false, 'message', 'username_taken');
    end if;
  end if;

  update public.profiles
     set username = coalesce(p_username, username),
         bio = coalesce(p_bio, bio),
         custom_status = coalesce(p_custom_status, custom_status),
         show_online_status = coalesce(p_show_online, show_online_status),
         country = coalesce(p_country, country),
         last_seen_at = now(),
         updated_at = now()
   where id = v_uid;

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8) إدارة المحتوى (Admin) — إضافة/تعديل/حذف ونشر
-- ---------------------------------------------------------------------------
create or replace function public.admin_upsert_content(
  p_id uuid default null,
  p_title text default null,
  p_description text default null,
  p_content_type text default 'addon',
  p_category text default 'other',
  p_plan text default 'free',
  p_version text default null,
  p_cover_url text default null,
  p_file_path text default null,
  p_file_size bigint default null,
  p_ac_price int default 0,
  p_is_published boolean default false,
  p_is_featured boolean default false,
  p_is_exclusive boolean default false
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_id uuid;
  v_slug text;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if coalesce(p_title,'') = '' then return jsonb_build_object('ok', false, 'message', 'title_required'); end if;

  v_slug := lower(regexp_replace(coalesce(p_title,''), '[^a-zA-Z0-9\u0600-\u06FF]+', '-', 'g'));

  if p_id is null then
    insert into public.content_items(title, slug, description, content_type, category, plan, version,
                                    cover_url, file_path, file_size, ac_price,
                                    is_published, is_featured, is_exclusive, created_by)
    values (p_title, v_slug, p_description, p_content_type, p_category, p_plan, p_version,
            p_cover_url, p_file_path, p_file_size, coalesce(p_ac_price,0),
            coalesce(p_is_published,false), coalesce(p_is_featured,false), coalesce(p_is_exclusive,false), auth.uid())
    returning id into v_id;
  else
    update public.content_items
       set title = coalesce(p_title, title),
           description = coalesce(p_description, description),
           content_type = coalesce(p_content_type, content_type),
           category = coalesce(p_category, category),
           plan = coalesce(p_plan, plan),
           version = coalesce(p_version, version),
           cover_url = coalesce(p_cover_url, cover_url),
           file_path = coalesce(p_file_path, file_path),
           file_size = coalesce(p_file_size, file_size),
           ac_price = coalesce(p_ac_price, ac_price),
           is_published = coalesce(p_is_published, is_published),
           is_featured = coalesce(p_is_featured, is_featured),
           is_exclusive = coalesce(p_is_exclusive, is_exclusive),
           updated_at = now()
     where id = p_id
     returning id into v_id;

    if v_id is null then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when p_id is null then 'content_created' else 'content_updated' end,
          'content_item', v_id::text, jsonb_build_object('title', p_title));

  return jsonb_build_object('ok', true, 'id', v_id, 'slug', v_slug);
end;
$$;

create or replace function public.admin_delete_content(p_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  delete from public.content_items where id = p_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'content_deleted', 'content_item', p_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9) إعدادات الإدارة (خطط، طرق دفع، إعدادات عامة)
-- ---------------------------------------------------------------------------
create or replace function public.admin_update_plan(
  p_plan_id text, p_price_lyd numeric default null, p_price_usd numeric default null,
  p_duration_days int default null, p_download_limit int default null,
  p_limit_period text default null, p_active boolean default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  update public.subscription_plans
     set price_lyd = coalesce(p_price_lyd, price_lyd),
         price_usd = coalesce(p_price_usd, price_usd),
         duration_days = coalesce(p_duration_days, duration_days),
         download_limit = coalesce(p_download_limit, download_limit),
         limit_period = coalesce(p_limit_period, limit_period),
         active = coalesce(p_active, active),
         updated_at = now()
   where id = p_plan_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'plan_updated', 'subscription_plan', p_plan_id,
          jsonb_build_object('price_lyd', p_price_lyd, 'duration_days', p_duration_days,
                             'download_limit', p_download_limit, 'active', p_active));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.admin_upsert_payment_method(
  p_code text, p_name text, p_account_name text default null,
  p_account_identifier text default null, p_instructions text default null,
  p_enabled boolean default true, p_sort_order int default 0, p_provider text default 'manual'
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  insert into public.payment_method_settings(code, name, account_name, account_identifier, instructions,
                                            enabled, sort_order, provider)
  values (p_code, p_name, p_account_name, p_account_identifier, p_instructions,
          coalesce(p_enabled,true), coalesce(p_sort_order,0), coalesce(p_provider,'manual'))
  on conflict (code) do update
     set name = excluded.name,
         account_name = coalesce(excluded.account_name, public.payment_method_settings.account_name),
         account_identifier = coalesce(excluded.account_identifier, public.payment_method_settings.account_identifier),
         instructions = coalesce(excluded.instructions, public.payment_method_settings.instructions),
         enabled = excluded.enabled,
         sort_order = excluded.sort_order,
         provider = excluded.provider;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_method_upserted', 'payment_method', p_code,
          jsonb_build_object('enabled', p_enabled));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.admin_delete_payment_method(p_code text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  delete from public.payment_method_settings where code = p_code;
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 10) إحصائيات لوحة الإدارة
-- ---------------------------------------------------------------------------
create or replace function public.admin_stats()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v jsonb;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select jsonb_build_object(
    'users',        (select count(*) from public.profiles),
    'creators',     (select count(*) from public.profiles where role = 'creator'),
    'published',    (select count(*) from public.content_items where is_published),
    'addons',       (select count(*) from public.content_items where is_published and content_type = 'addon'),
    'worlds',       (select count(*) from public.content_items where is_published and content_type = 'world'),
    'downloads',    (select count(*) from public.content_downloads),
    'paid_users',   (select count(*) from public.profiles
                      where plan <> 'free' and (plan_expires_at is null or plan_expires_at > now())),
    'open_reports', (select count(*) from public.chat_reports where status = 'open'),
    'pending_orders', (select count(*) from public.payment_orders where status = 'submitted'),
    'pending_creator_apps', (select count(*) from public.creator_applications where status = 'pending'),
    'ac_balance_total', (select coalesce(sum(balance),0) from public.ac_wallets)
  ) into v;

  return v;
end;
$$;

-- ---------------------------------------------------------------------------
-- 11) منح صلاحيات التنفيذ للمستخدمين المسجّلين
-- ---------------------------------------------------------------------------
do $$
declare fn record;
begin
  for fn in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'record_content_view','record_content_download','toggle_favorite','ac_unlock_content',
        'ac_claim_task','ac_admin_adjust','ac_admin_grant_all','create_payment_order',
        'submit_payment_receipt','approve_payment_order','reject_payment_order',
        'admin_update_profile','set_profile_asset','update_my_profile','admin_upsert_content',
        'admin_delete_content','admin_update_plan','admin_upsert_payment_method',
        'admin_delete_payment_method','admin_stats','effective_plan','can_access_item',
        'download_limit_reached','plan_rank','role_rank','current_role_of','is_admin','is_staff'
      )
  loop
    execute format('grant execute on function %s to authenticated', fn.sig);
    execute format('grant execute on function %s to anon', fn.sig);
  end loop;
end $$;


-- ############################################################################
-- ##  القسم: 03_functions_chat.sql
-- ############################################################################

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
    v_mentions := coalesce(array(select distinct unnest(v_mentions) where unnest is not null), '{}');
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


-- ############################################################################
-- ##  القسم: 04_rls.sql
-- ############################################################################

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
  for select using (enabled = true or public.is_admin());

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

grant select on public.profiles to anon, authenticated;
grant update (bio, custom_status, show_online_status, last_seen_at, country, display_name) on public.profiles to authenticated;


-- ############################################################################
-- ##  القسم: 05_storage.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 05) مخازن الملفات (Storage) وسياساتها
--  الملفات المدفوعة والتذاكر والأوسائط: خاصة + روابط قصيرة الأجل (60 / 300 ثانية)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) إنشاء المخازن
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('content-files',    'content-files',    false, 536870912,
      array['application/zip','application/x-zip-compressed','application/octet-stream','application/java-archive','application/x-rar-compressed']),
  ('payment-receipts', 'payment-receipts', false, 10485760,
      array['image/png','image/jpeg','image/webp','application/pdf']),
  ('chat-images',      'chat-images',      false, 4194304,
      array['image/png','image/jpeg','image/webp']),
  ('chat-media',       'chat-media',       false, 104857600,
      array['audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/wav','video/mp4','video/webm','video/quicktime']),
  ('creator-media',    'creator-media',    false, 209715200,
      array['image/png','image/jpeg','image/webp','application/zip','application/octet-stream']),
  ('site-assets',      'site-assets',      true,  5242880,
      array['image/png','image/jpeg','image/webp','image/gif','image/svg+xml'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- ---------------------------------------------------------------------------
-- 2) ملفات المحتوى المدفوع: تُقرأ فقط لمن يملك الصلاحية (تحددها RLS حسب خطته)
-- ---------------------------------------------------------------------------
drop policy if exists "content_files_read" on storage.objects;
create policy "content_files_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'content-files'
    and exists (
      select 1 from public.content_items ci
      where ci.file_path = storage.objects.name
        and (ci.created_by = auth.uid()
             or public.is_admin()
             or (ci.is_published and public.can_access_item(ci.id, auth.uid())))
    )
  );

drop policy if exists "content_files_admin_write" on storage.objects;
create policy "content_files_admin_write" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'content-files' and (public.is_admin() or public.current_role_of() = 'creator'));

drop policy if exists "content_files_admin_update" on storage.objects;
create policy "content_files_admin_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'content-files' and (public.is_admin() or public.current_role_of() = 'creator'));

drop policy if exists "content_files_admin_delete" on storage.objects;
create policy "content_files_admin_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'content-files' and public.is_admin());

-- ---------------------------------------------------------------------------
-- 3) إيصالات الدفع: المستخدم يرفع في مجلده فقط ويقرأ ملفاته، والإدارة تقرأ الكل
-- ---------------------------------------------------------------------------
drop policy if exists "receipts_insert_own" on storage.objects;
create policy "receipts_insert_own" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'payment-receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "receipts_read" on storage.objects;
create policy "receipts_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'payment-receipts'
    and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
  );

drop policy if exists "receipts_update_own" on storage.objects;
create policy "receipts_update_own" on storage.objects
  for update to authenticated
  using (bucket_id = 'payment-receipts'
         and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin()));

-- ---------------------------------------------------------------------------
-- 4) صور ومقاطع الشات (≤4MB للصور و ≤100MB للأوسائط)
-- ---------------------------------------------------------------------------
drop policy if exists "chat_images_insert" on storage.objects;
create policy "chat_images_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'chat-images' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "chat_images_read" on storage.objects;
create policy "chat_images_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'chat-images');

drop policy if exists "chat_media_insert" on storage.objects;
create policy "chat_media_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'chat-media' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "chat_media_read" on storage.objects;
create policy "chat_media_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'chat-media');

-- ---------------------------------------------------------------------------
-- 5) ملفات المبدعين
-- ---------------------------------------------------------------------------
drop policy if exists "creator_media_insert" on storage.objects;
create policy "creator_media_insert" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'creator-media'
    and (public.current_role_of() in ('creator','admin'))
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "creator_media_read" on storage.objects;
create policy "creator_media_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'creator-media'
    and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
  );

-- ---------------------------------------------------------------------------
-- 6) أصول الموقع العامة (الأغلفة/الأفاتارات/الملصقات/الأدوات) — قراءة عامة،
--    الكتابة للإدارة في أي مسار، وللمبدع داخل مجلد أغلفته items/<uid>/… فقط
-- ---------------------------------------------------------------------------
drop policy if exists "site_assets_read" on storage.objects;
create policy "site_assets_read" on storage.objects
  for select using (bucket_id = 'site-assets');

drop policy if exists "site_assets_admin_write" on storage.objects;
create policy "site_assets_admin_write" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'site-assets' and public.is_admin());

-- أغلفة المحتوى: المبدع يكتب داخل مجلده فقط (items/<uid>/…) — أما الإدارة فمسارها مفتوح
drop policy if exists "site_assets_creator_write" on storage.objects;
create policy "site_assets_creator_write" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'site-assets'
    and public.current_role_of() in ('creator','admin')
    and (storage.foldername(name))[1] = 'items'
    and (storage.foldername(name))[2] = auth.uid()::text
  );

drop policy if exists "site_assets_admin_update" on storage.objects;
create policy "site_assets_admin_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'site-assets' and public.is_admin());

drop policy if exists "site_assets_admin_delete" on storage.objects;
create policy "site_assets_admin_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'site-assets' and public.is_admin());


-- ############################################################################
-- ##  القسم: 06_seed.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 06) البيانات الأولية (Seed)
--  الخطط · طرق الدفع · مهام AC · حزم الملصقات · الغرف الثلاث عشرة · قواعد المجتمع
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) خطط الاشتراك (الأسعار قابلة للتعديل من لوحة الإدارة)
-- ---------------------------------------------------------------------------
insert into public.subscription_plans (id, name, tagline, price_lyd, price_usd, duration_days,
                                       download_limit, limit_period, active, sort_order)
values
  ('free', 'Free', 'Start exploring free addons and worlds.',
    0, 0, 30, 5, 'day', true, 1),
  ('epic', 'Epic', 'More content and a wider play experience.',
    25, 2.99, 30, 30, 'day', true, 2),
  ('legendary', 'Legendary', 'Unlock premium content and VIP privileges.',
    50, 5.99, 30, null, 'none', true, 3)
on conflict (id) do update
  set name = excluded.name,
      tagline = excluded.tagline,
      price_usd = excluded.price_usd,
      sort_order = excluded.sort_order;

-- ---------------------------------------------------------------------------
-- 2) طرق الدفع (عدّل اسم المستفيد ورقم الحساب من لوحة الإدارة ثم فعّلها)
-- ---------------------------------------------------------------------------
insert into public.payment_method_settings
  (code, name, account_name, account_identifier, instructions, enabled, sort_order, provider)
values
  ('onepay',       'OnePay',        null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 1, 'manual'),
  ('lpay',         'LPay',          null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 2, 'manual'),
  ('edfa3li',      'Edfa3li',       null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 3, 'manual'),
  ('mobicash',     'MobiCash',      null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 4, 'manual'),
  ('sadad',        'Sadad',         null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 5, 'manual'),
  ('tadawul',      'Tadawul',       null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 6, 'manual'),
  ('bank_transfer','Bank transfer', null, null, 'Transfer to the bank account, then upload the receipt.', false, 7, 'manual'),
  ('stripe',       'Card (Stripe)', null, null, 'Pay securely by card. Your plan activates automatically after payment.', false, 8, 'stripe')
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------
-- 3) مهام كسب AC
-- ---------------------------------------------------------------------------
insert into public.ac_tasks (id, title_ar, title_en, description, reward, frequency, streak_days, active, sort_order)
values
  ('daily_login',      'تسجيل الدخول اليومي', 'Daily login',
     'افتح التطبيق يومياً واقبض مكافأتك.', 10, 'streak', '[3,7,14,30]'::jsonb, true, 1),
  ('complete_profile', 'إكمال الملف الشخصي', 'Complete profile',
     'أضف نبذة وحالة وأفاتار.', 25, 'once', '[]'::jsonb, true, 2),
  ('community_post',   'مشاركة مجتمعية', 'Community participation',
     'شارك في غرفة مجتمعية واحدة على الأقل.', 15, 'daily', '[]'::jsonb, true, 3),
  ('first_addon',      'تحميل أول إضافة', 'First addon download',
     'حمّل أول إضافة منشورة.', 20, 'once', '[]'::jsonb, true, 4),
  ('first_world',      'تحميل أول عالم', 'First world download',
     'حمّل أول عالم منشور.', 20, 'once', '[]'::jsonb, true, 5),
  ('useful_post',      'أول منشور مفيد', 'First useful post',
     'منشور حصل على تفاعل من المجتمع.', 30, 'once', '[]'::jsonb, true, 6)
on conflict (id) do update
  set title_ar = excluded.title_ar,
      title_en = excluded.title_en,
      reward = excluded.reward,
      frequency = excluded.frequency,
      streak_days = excluded.streak_days;

-- ---------------------------------------------------------------------------
-- 4) حزم الملصقات الخمس (الملفات ترفع إلى مخزن site-assets/stickers/<pack>/)
-- ---------------------------------------------------------------------------
insert into public.sticker_packs (id, name, icon_url, sort_order, active)
values
  ('meme',              'meme',            '/site-assets/stickers/meme/', 1, true),
  ('minecraft-memes-1', 'Minecraft Memes1','/site-assets/stickers/Minecraft_Memes1/', 2, true),
  ('minecraft-memes-2', 'Minecraft Memes2','/site-assets/stickers/Minecraft_Memes2/', 3, true),
  ('minecraft-memes-3', 'Minecraft Memes3','/site-assets/stickers/Minecraft_Memes3/', 4, true),
  ('minecraft-movie',   'Minecraft Movie','/site-assets/stickers/Minecraft_Movie/', 5, true)
on conflict (id) do update set name = excluded.name, icon_url = excluded.icon_url;

-- ---------------------------------------------------------------------------
-- 5) الغرف الثلاث عشرة (5 أساسية + 8 لغات) — الأسماء كما في الوثيقة تماماً
-- ---------------------------------------------------------------------------
insert into public.chat_channels (id, name, icon, category, description, sort_order, is_locked, slowmode_seconds, post_permission)
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
      post_permission = excluded.post_permission;

-- ---------------------------------------------------------------------------
-- 6) قواعد المجتمع باللغات الثمانية (قابلة للتعديل من لوحة الإدارة)
-- ---------------------------------------------------------------------------
insert into public.chat_rules (lang, title, rules, accept_text, decline_text)
values
  ('en', '⚠️ Community rules',
   '["🤝 Respect all members","🚫 No insults or harassment","📵 No racism or discrimination","🔗 No suspicious links or ads","🔒 Do not share personal information","🛑 No inappropriate content","🏛️ No politics","⚠️ Violations may lead to a mute or a ban"]'::jsonb,
   'I agree and enter the chat', 'Decline and go home'),
  ('ar', '⚠️ قواعد المجتمع',
   '["🤝 احترام جميع الأعضاء","🚫 لا إهانات أو مضايقات","📵 لا عنصرية أو تمييز","🔗 لا روابط أو إعلانات مشبوهة","🔒 لا تشارك معلوماتك الشخصية","🛑 لا محتوى غير لائق","🏛️ لا سياسة","⚠️ قد تؤدي الانتهاكات إلى كتم الصوت أو الحظر"]'::jsonb,
   'أوافق وأدخل الدردشة', 'الرفض والعودة إلى المنزل'),
  ('tr', '⚠️ Topluluk kuralları',
   '["🤝 Herkese saygı göster","🚫 Hakaret ve taciz yok","📵 Irkçılık ve ayrımcılık yok","🔗 Şüpheli bağlantı ve izinsiz reklam yok","🔒 Kişisel bilgi paylaşma","🛑 Uygunsuz içerik yok","🏛️ Siyasi tartışma yok","⚠️ İhlaller susturma veya yasaklanmayla sonuçlanabilir"]'::jsonb,
   'Kabul ediyorum ve Sohbete giriyorum', 'Reddet ve Ana Sayfaya dön'),
  ('pt', '⚠️ Regras da comunidade',
   '["🤝 Respeite todos os membros","🚫 Sem insultos ou assédio","📵 Sem racismo ou discriminação","🔗 Sem links suspeitos ou anúncios","🔒 Não compartilhe informações pessoais","🛑 Sem conteúdo inadequado","🏛️ Sem política","⚠️ Violações podem levar a silenciamento ou banimento"]'::jsonb,
   'Concordo e entrar no chat', 'Recusar e voltar ao início'),
  ('es', '⚠️ Reglas de la comunidad',
   '["🤝 Respeta a todos los miembros","🚫 Sin insultos ni acoso","📵 Sin racismo ni discriminación","🔗 Sin enlaces sospechosos ni anuncios","🔒 No compartas información personal","🛑 Sin contenido inapropiado","🏛️ Sin política","⚠️ Las infracciones pueden provocar silencio o expulsión"]'::jsonb,
   'Acepto y entro al chat', 'Rechazar y volver al inicio'),
  ('ru', '⚠️ Правила сообщества',
   '["🤝 Уважайте всех участников","🚫 Без оскорблений и травли","📵 Без расизма и дискриминации","🔗 Без подозрительных ссылок и рекламы","🔒 Не делитесь личными данными","🛑 Без неприемлемого контента","🏛️ Без политики","⚠️ Нарушения могут привести к мьюту или бану"]'::jsonb,
   'Я согласен и вхожу в чат', 'Отказаться и вернуться домой'),
  ('zh', '⚠️ 社区规则',
   '["🤝 尊重所有成员","🚫 禁止侮辱和骚扰","📵 禁止种族主义和歧视","🔗 禁止可疑链接和广告","🔒 不要分享个人信息","🛑 禁止不当内容","🏛️ 禁止政治话题","⚠️ 违规可能导致禁言或封禁"]'::jsonb,
   '我同意并进入聊天', '拒绝并返回首页'),
  ('fr', '⚠️ Règles de la communauté',
   '["🤝 Respectez tous les membres","🚫 Pas d''insultes ni de harcèlement","📵 Pas de racisme ni de discrimination","🔗 Pas de liens suspects ni de publicités","🔒 Ne partagez pas d''informations personnelles","🛑 Pas de contenu inapproprié","🏛️ Pas de politique","⚠️ Les infractions peuvent entraîner un silence ou un bannissement"]'::jsonb,
   'J''accepte et j''entre dans le chat', 'Refuser et revenir à l''accueil')
on conflict (lang) do update
  set title = excluded.title, rules = excluded.rules,
      accept_text = excluded.accept_text, decline_text = excluded.decline_text,
      updated_at = now();

-- ---------------------------------------------------------------------------
-- 7) إعدادات التطبيق (الفلترة التلقائية + أعلام الميزات)
-- ---------------------------------------------------------------------------
insert into public.app_settings (key, value)
values
  ('automod', '{"block_links": true, "block_personal": true, "rate_limit_10s": 5, "blocked_words": []}'::jsonb),
  ('features', '{"ac_enabled": true, "creator_tips_enabled": true, "online_boost_enabled": false}'::jsonb)
on conflict (key) do nothing;

-- ملاحظة: العدد النهائي للغرف 13 فقط (5 أساسية + 8 لغات) كما في الوثيقة.
-- لإضافة غرفة إعلانات لاحقاً استخدم: select public.chat_admin_upsert_room(...) من لوحة الإدارة.


-- ############################################################################
-- ##  القسم: 07_seed_assets.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 07) أصول الاستخدام (الـ 105 أداة + الملصقات)
--  الملفات نفسها ترفع إلى مخزن site-assets (انظر docs/02-SUPABASE-SETUP-AR.md)
--  مسارات الملفات هنا نسبية لجذر المخزن: items/… , stickers/…
-- ============================================================================

-- ⚠️ [مُستبدَل] أُلغيت هذه الأصول القديمة نهائياً وحلّت محلها الدفعة الجديدة
--    (492 أصلاً في 21 قسماً) عبر الملف: supabase/15_assets_v2.sql
--    هذا الملف باقٍ للتوافق فقط؛ تنفيذه وحده سيُعيد الأصول القديمة — لا تنفّذه بعده.

-- أدوات الشات: 104 أداة بخطة legendary + أداة واحدة حصرية للإدارة (Enchanted Bedrock)
insert into public.site_assets (asset_type, name, file_url, required_plan, animated, sort_order)
values
  ('item', 'Enchanted Bedrock', 'items/Enchanted_Bedrock.gif', 'admin', true, 1),
  ('item', 'Amethyst Shard', 'items/amethyst_shard.png', 'legendary', false, 2),
  ('item', 'Apple', 'items/apple.png', 'legendary', false, 3),
  ('item', 'Armor Stand', 'items/armor_stand.png', 'legendary', false, 4),
  ('item', 'Arms Up Pottery Sherd', 'items/arms_up_pottery_sherd.png', 'legendary', false, 5),
  ('item', 'Arrow', 'items/arrow.png', 'legendary', false, 6),
  ('item', 'Axolotl Bucket', 'items/axolotl_bucket.png', 'legendary', false, 7),
  ('item', 'Bell', 'items/bell.png', 'legendary', false, 8),
  ('item', 'Bone', 'items/bone.png', 'legendary', false, 9),
  ('item', 'Brick', 'items/brick.png', 'legendary', false, 10),
  ('item', 'Charcoal', 'items/charcoal.png', 'legendary', false, 11),
  ('item', 'Clock', 'items/clock.gif', 'legendary', true, 12),
  ('item', 'Compass', 'items/compass.gif', 'legendary', true, 13),
  ('item', 'Crafting Table', 'items/crafting_table.png', 'legendary', false, 14),
  ('item', 'Crossbow Arrow', 'items/crossbow_arrow.png', 'legendary', false, 15),
  ('item', 'Diamond', 'items/diamond.png', 'legendary', false, 16),
  ('item', 'Diamond Axe', 'items/diamond_axe.png', 'legendary', false, 17),
  ('item', 'Diamond Boots', 'items/diamond_boots.png', 'legendary', false, 18),
  ('item', 'Diamond Chestplate', 'items/diamond_chestplate.png', 'legendary', false, 19),
  ('item', 'Diamond Helmet', 'items/diamond_helmet.png', 'legendary', false, 20),
  ('item', 'Diamond Hoe', 'items/diamond_hoe.png', 'legendary', false, 21),
  ('item', 'Diamond Leggings', 'items/diamond_leggings.png', 'legendary', false, 22),
  ('item', 'Diamond Ore', 'items/diamond_ore.png', 'legendary', false, 23),
  ('item', 'Diamond Pickaxe', 'items/diamond_pickaxe.png', 'legendary', false, 24),
  ('item', 'Diamond Shovel', 'items/diamond_shovel.png', 'legendary', false, 25),
  ('item', 'Diamond Spear', 'items/diamond_spear.png', 'legendary', false, 26),
  ('item', 'Diamond Spear In Hand', 'items/diamond_spear_in_hand.png', 'legendary', false, 27),
  ('item', 'Diamond Sword', 'items/diamond_sword.png', 'legendary', false, 28),
  ('item', 'Dragon Breath', 'items/dragon_breath.png', 'legendary', false, 29),
  ('item', 'Echo Shard', 'items/echo_shard.png', 'legendary', false, 30),
  ('item', 'Experience Bottle', 'items/experience_bottle.png', 'legendary', false, 31),
  ('item', 'Firework Rocket', 'items/firework_rocket.png', 'legendary', false, 32),
  ('item', 'Flint And Steel', 'items/flint_and_steel.png', 'legendary', false, 33),
  ('item', 'Flow Armor Trim Smithing Template', 'items/flow_armor_trim_smithing_template.png', 'legendary', false, 34),
  ('item', 'Glistering Melon Slice', 'items/glistering_melon_slice.png', 'legendary', false, 35),
  ('item', 'Glow Berries', 'items/glow_berries.png', 'legendary', false, 36),
  ('item', 'Glow Ink Sac', 'items/glow_ink_sac.png', 'legendary', false, 37),
  ('item', 'Goat Horn', 'items/goat_horn.png', 'legendary', false, 38),
  ('item', 'Honey Bottle', 'items/honey_bottle.png', 'legendary', false, 39),
  ('item', 'Hopper', 'items/hopper.png', 'legendary', false, 40),
  ('item', 'Iron Boots', 'items/iron_boots.png', 'legendary', false, 41),
  ('item', 'Iron Chestplate', 'items/iron_chestplate.png', 'legendary', false, 42),
  ('item', 'Iron Helmet', 'items/iron_helmet.png', 'legendary', false, 43),
  ('item', 'Iron Ingot', 'items/iron_ingot.png', 'legendary', false, 44),
  ('item', 'Iron Leggings', 'items/iron_leggings.png', 'legendary', false, 45),
  ('item', 'Iron Pickaxe', 'items/iron_pickaxe.png', 'legendary', false, 46),
  ('item', 'Iron Shovel', 'items/iron_shovel.png', 'legendary', false, 47),
  ('item', 'Iron Spear', 'items/iron_spear.png', 'legendary', false, 48),
  ('item', 'Iron Spear In Hand', 'items/iron_spear_in_hand.png', 'legendary', false, 49),
  ('item', 'Iron Sword', 'items/iron_sword.png', 'legendary', false, 50),
  ('item', 'Kelp', 'items/kelp.png', 'legendary', false, 51),
  ('item', 'Lantern', 'items/lantern.png', 'legendary', false, 52),
  ('item', 'Lapis Lazuli', 'items/lapis_lazuli.png', 'legendary', false, 53),
  ('item', 'Lava Bucket', 'items/lava_bucket.png', 'legendary', false, 54),
  ('item', 'Lead', 'items/lead.png', 'legendary', false, 55),
  ('item', 'Leather', 'items/leather.png', 'legendary', false, 56),
  ('item', 'Music Disc 13', 'items/music_disc_13.png', 'legendary', false, 57),
  ('item', 'Music Disc Blocks', 'items/music_disc_blocks.png', 'legendary', false, 58),
  ('item', 'Music Disc Pigstep', 'items/music_disc_pigstep.png', 'legendary', false, 59),
  ('item', 'Name Tag', 'items/name_tag.png', 'legendary', false, 60),
  ('item', 'Netherite Axe', 'items/netherite_axe.png', 'legendary', false, 61),
  ('item', 'Netherite Boots', 'items/netherite_boots.png', 'legendary', false, 62),
  ('item', 'Netherite Chestplate', 'items/netherite_chestplate.png', 'legendary', false, 63),
  ('item', 'Netherite Helmet', 'items/netherite_helmet.png', 'legendary', false, 64),
  ('item', 'Netherite Hoe', 'items/netherite_hoe.png', 'legendary', false, 65),
  ('item', 'Netherite Ingot', 'items/netherite_ingot.png', 'legendary', false, 66),
  ('item', 'Netherite Leggings', 'items/netherite_leggings.png', 'legendary', false, 67),
  ('item', 'Netherite Nautilus Armor', 'items/netherite_nautilus_armor.png', 'legendary', false, 68),
  ('item', 'Netherite Pickaxe', 'items/netherite_pickaxe.png', 'legendary', false, 69),
  ('item', 'Netherite Scrap', 'items/netherite_scrap.png', 'legendary', false, 70),
  ('item', 'Netherite Shovel', 'items/netherite_shovel.png', 'legendary', false, 71),
  ('item', 'Netherite Spear', 'items/netherite_spear.png', 'legendary', false, 72),
  ('item', 'Netherite Spear In Hand', 'items/netherite_spear_in_hand.png', 'legendary', false, 73),
  ('item', 'Netherite Sword', 'items/netherite_sword.png', 'legendary', false, 74),
  ('item', 'Potato', 'items/potato.png', 'legendary', false, 75),
  ('item', 'Quartz', 'items/quartz.png', 'legendary', false, 76),
  ('item', 'Rabbit Foot', 'items/rabbit_foot.png', 'legendary', false, 77),
  ('item', 'Rabbit Hide', 'items/rabbit_hide.png', 'legendary', false, 78),
  ('item', 'Raw Copper', 'items/raw_copper.png', 'legendary', false, 79),
  ('item', 'Raw Gold', 'items/raw_gold.png', 'legendary', false, 80),
  ('item', 'Raw Iron', 'items/raw_iron.png', 'legendary', false, 81),
  ('item', 'Resin Clump', 'items/resin_clump.png', 'legendary', false, 82),
  ('item', 'Rotten Flesh', 'items/rotten_flesh.png', 'legendary', false, 83),
  ('item', 'Saddle', 'items/saddle.png', 'legendary', false, 84),
  ('item', 'Shears', 'items/shears.png', 'legendary', false, 85),
  ('item', 'Slime Ball', 'items/slime_ball.png', 'legendary', false, 86),
  ('item', 'Snowball', 'items/snowball.png', 'legendary', false, 87),
  ('item', 'Soul Campfire', 'items/soul_campfire.png', 'legendary', false, 88),
  ('item', 'Spider Eye', 'items/spider_eye.png', 'legendary', false, 89),
  ('item', 'Spire Armor Trim Smithing Template', 'items/spire_armor_trim_smithing_template.png', 'legendary', false, 90),
  ('item', 'Spyglass', 'items/spyglass.png', 'legendary', false, 91),
  ('item', 'Totem Of Undying', 'items/totem_of_undying.png', 'legendary', false, 92),
  ('item', 'Trident', 'items/trident.png', 'legendary', false, 93),
  ('item', 'Tropical Fish', 'items/tropical_fish.png', 'legendary', false, 94),
  ('item', 'Weathered Copper Lantern', 'items/weathered_copper_lantern.png', 'legendary', false, 95),
  ('item', 'Wheat', 'items/wheat.png', 'legendary', false, 96),
  ('item', 'Wheat Seeds', 'items/wheat_seeds.png', 'legendary', false, 97),
  ('item', 'White Harness', 'items/white_harness.png', 'legendary', false, 98),
  ('item', 'Wild Armor Trim Smithing Template', 'items/wild_armor_trim_smithing_template.png', 'legendary', false, 99),
  ('item', 'Wind Charge', 'items/wind_charge.png', 'legendary', false, 100),
  ('item', 'Writable Book', 'items/writable_book.png', 'legendary', false, 101),
  ('item', 'Written Book', 'items/written_book.png', 'legendary', false, 102),
  ('item', 'Yellow Bundle', 'items/yellow_bundle.png', 'legendary', false, 103),
  ('item', 'Yellow Candle', 'items/yellow_candle.png', 'legendary', false, 104),
  ('item', 'Yellow Harness', 'items/yellow_harness.png', 'legendary', false, 105)
on conflict do nothing;

-- الملصقات: كل ملصق مرتبط بحزمته. الحجم المقترح 128–256 بكسل (WEBP/PNG/GIF)
insert into public.site_assets (asset_type, name, file_url, sticker_pack, required_plan, sort_order)
values
  ('sticker', 'Sticker 001', 'stickers/Minecraft_Memes1/sticker-001.webp', 'minecraft-memes-1', 'free', 1),
  ('sticker', 'Sticker 002', 'stickers/Minecraft_Memes1/sticker-002.webp', 'minecraft-memes-1', 'free', 2),
  ('sticker', 'Sticker 003', 'stickers/Minecraft_Memes1/sticker-003.webp', 'minecraft-memes-1', 'free', 3),
  ('sticker', 'Sticker 004', 'stickers/Minecraft_Memes1/sticker-004.webp', 'minecraft-memes-1', 'free', 4),
  ('sticker', 'Sticker 005', 'stickers/Minecraft_Memes1/sticker-005.webp', 'minecraft-memes-1', 'free', 5),
  ('sticker', 'Sticker 006', 'stickers/Minecraft_Memes1/sticker-006.webp', 'minecraft-memes-1', 'free', 6),
  ('sticker', 'Sticker 007', 'stickers/Minecraft_Memes1/sticker-007.webp', 'minecraft-memes-1', 'free', 7),
  ('sticker', 'Sticker 008', 'stickers/Minecraft_Memes1/sticker-008.webp', 'minecraft-memes-1', 'free', 8),
  ('sticker', 'Sticker 009', 'stickers/Minecraft_Memes1/sticker-009.webp', 'minecraft-memes-1', 'free', 9),
  ('sticker', 'Sticker 010', 'stickers/Minecraft_Memes1/sticker-010.webp', 'minecraft-memes-1', 'free', 10),
  ('sticker', 'Sticker 011', 'stickers/Minecraft_Memes1/sticker-011.webp', 'minecraft-memes-1', 'free', 11),
  ('sticker', 'Sticker 012', 'stickers/Minecraft_Memes1/sticker-012.webp', 'minecraft-memes-1', 'free', 12),
  ('sticker', 'Sticker 013', 'stickers/Minecraft_Memes1/sticker-013.webp', 'minecraft-memes-1', 'free', 13),
  ('sticker', 'Sticker 014', 'stickers/Minecraft_Memes1/sticker-014.webp', 'minecraft-memes-1', 'free', 14),
  ('sticker', 'Sticker 015', 'stickers/Minecraft_Memes1/sticker-015.webp', 'minecraft-memes-1', 'free', 15),
  ('sticker', 'Sticker 016', 'stickers/Minecraft_Memes1/sticker-016.webp', 'minecraft-memes-1', 'free', 16),
  ('sticker', 'Sticker 017', 'stickers/Minecraft_Memes1/sticker-017.webp', 'minecraft-memes-1', 'free', 17),
  ('sticker', 'Sticker 018', 'stickers/Minecraft_Memes1/sticker-018.webp', 'minecraft-memes-1', 'free', 18),
  ('sticker', 'Sticker 019', 'stickers/Minecraft_Memes1/sticker-019.webp', 'minecraft-memes-1', 'free', 19),
  ('sticker', 'Sticker 020', 'stickers/Minecraft_Memes1/sticker-020.webp', 'minecraft-memes-1', 'free', 20),
  ('sticker', 'Sticker 021', 'stickers/Minecraft_Memes1/sticker-021.webp', 'minecraft-memes-1', 'free', 21),
  ('sticker', 'Sticker 001', 'stickers/Minecraft_Memes2/sticker-001.webp', 'minecraft-memes-2', 'free', 22),
  ('sticker', 'Sticker 002', 'stickers/Minecraft_Memes2/sticker-002.webp', 'minecraft-memes-2', 'free', 23),
  ('sticker', 'Sticker 003', 'stickers/Minecraft_Memes2/sticker-003.webp', 'minecraft-memes-2', 'free', 24),
  ('sticker', 'Sticker 004', 'stickers/Minecraft_Memes2/sticker-004.webp', 'minecraft-memes-2', 'free', 25),
  ('sticker', 'Sticker 005', 'stickers/Minecraft_Memes2/sticker-005.webp', 'minecraft-memes-2', 'free', 26),
  ('sticker', 'Sticker 006', 'stickers/Minecraft_Memes2/sticker-006.webp', 'minecraft-memes-2', 'free', 27),
  ('sticker', 'Sticker 007', 'stickers/Minecraft_Memes2/sticker-007.webp', 'minecraft-memes-2', 'free', 28),
  ('sticker', 'Sticker 008', 'stickers/Minecraft_Memes2/sticker-008.webp', 'minecraft-memes-2', 'free', 29),
  ('sticker', 'Sticker 009', 'stickers/Minecraft_Memes2/sticker-009.webp', 'minecraft-memes-2', 'free', 30),
  ('sticker', 'Sticker 010', 'stickers/Minecraft_Memes2/sticker-010.webp', 'minecraft-memes-2', 'free', 31),
  ('sticker', 'Sticker 011', 'stickers/Minecraft_Memes2/sticker-011.webp', 'minecraft-memes-2', 'free', 32),
  ('sticker', 'Sticker 012', 'stickers/Minecraft_Memes2/sticker-012.webp', 'minecraft-memes-2', 'free', 33),
  ('sticker', 'Sticker 013', 'stickers/Minecraft_Memes2/sticker-013.webp', 'minecraft-memes-2', 'free', 34),
  ('sticker', 'Sticker 014', 'stickers/Minecraft_Memes2/sticker-014.webp', 'minecraft-memes-2', 'free', 35),
  ('sticker', 'Sticker 015', 'stickers/Minecraft_Memes2/sticker-015.webp', 'minecraft-memes-2', 'free', 36),
  ('sticker', 'Sticker 016', 'stickers/Minecraft_Memes2/sticker-016.webp', 'minecraft-memes-2', 'free', 37),
  ('sticker', 'Sticker 017', 'stickers/Minecraft_Memes2/sticker-017.webp', 'minecraft-memes-2', 'free', 38),
  ('sticker', 'Sticker 018', 'stickers/Minecraft_Memes2/sticker-018.webp', 'minecraft-memes-2', 'free', 39),
  ('sticker', 'Sticker 019', 'stickers/Minecraft_Memes2/sticker-019.webp', 'minecraft-memes-2', 'free', 40),
  ('sticker', 'Sticker 020', 'stickers/Minecraft_Memes2/sticker-020.webp', 'minecraft-memes-2', 'free', 41),
  ('sticker', 'Sticker 021', 'stickers/Minecraft_Memes2/sticker-021.webp', 'minecraft-memes-2', 'free', 42),
  ('sticker', 'Sticker 022', 'stickers/Minecraft_Memes2/sticker-022.webp', 'minecraft-memes-2', 'free', 43),
  ('sticker', 'Sticker 023', 'stickers/Minecraft_Memes2/sticker-023.webp', 'minecraft-memes-2', 'free', 44),
  ('sticker', 'Sticker 024', 'stickers/Minecraft_Memes2/sticker-024.webp', 'minecraft-memes-2', 'free', 45),
  ('sticker', 'Sticker 025', 'stickers/Minecraft_Memes2/sticker-025.webp', 'minecraft-memes-2', 'free', 46),
  ('sticker', 'Sticker 026', 'stickers/Minecraft_Memes2/sticker-026.webp', 'minecraft-memes-2', 'free', 47),
  ('sticker', 'Sticker 027', 'stickers/Minecraft_Memes2/sticker-027.webp', 'minecraft-memes-2', 'free', 48),
  ('sticker', 'Sticker 028', 'stickers/Minecraft_Memes2/sticker-028.webp', 'minecraft-memes-2', 'free', 49),
  ('sticker', 'Sticker 029', 'stickers/Minecraft_Memes2/sticker-029.webp', 'minecraft-memes-2', 'free', 50),
  ('sticker', 'Sticker 030', 'stickers/Minecraft_Memes2/sticker-030.webp', 'minecraft-memes-2', 'free', 51),
  ('sticker', 'Sticker 031', 'stickers/Minecraft_Memes2/sticker-031.webp', 'minecraft-memes-2', 'free', 52),
  ('sticker', 'Sticker 032', 'stickers/Minecraft_Memes2/sticker-032.webp', 'minecraft-memes-2', 'free', 53),
  ('sticker', 'Sticker 033', 'stickers/Minecraft_Memes2/sticker-033.webp', 'minecraft-memes-2', 'free', 54),
  ('sticker', 'Sticker 034', 'stickers/Minecraft_Memes2/sticker-034.webp', 'minecraft-memes-2', 'free', 55),
  ('sticker', 'Sticker 035', 'stickers/Minecraft_Memes2/sticker-035.webp', 'minecraft-memes-2', 'free', 56),
  ('sticker', 'Sticker 036', 'stickers/Minecraft_Memes2/sticker-036.webp', 'minecraft-memes-2', 'free', 57),
  ('sticker', 'Sticker 037', 'stickers/Minecraft_Memes2/sticker-037.webp', 'minecraft-memes-2', 'free', 58),
  ('sticker', 'Sticker 038', 'stickers/Minecraft_Memes2/sticker-038.webp', 'minecraft-memes-2', 'free', 59),
  ('sticker', 'Sticker 039', 'stickers/Minecraft_Memes2/sticker-039.webp', 'minecraft-memes-2', 'free', 60),
  ('sticker', 'Sticker 040', 'stickers/Minecraft_Memes2/sticker-040.webp', 'minecraft-memes-2', 'free', 61),
  ('sticker', 'Sticker 041', 'stickers/Minecraft_Memes2/sticker-041.webp', 'minecraft-memes-2', 'free', 62),
  ('sticker', 'Sticker 042', 'stickers/Minecraft_Memes2/sticker-042.webp', 'minecraft-memes-2', 'free', 63),
  ('sticker', 'Sticker 043', 'stickers/Minecraft_Memes2/sticker-043.webp', 'minecraft-memes-2', 'free', 64),
  ('sticker', 'Sticker 044', 'stickers/Minecraft_Memes2/sticker-044.webp', 'minecraft-memes-2', 'free', 65),
  ('sticker', 'Sticker 045', 'stickers/Minecraft_Memes2/sticker-045.webp', 'minecraft-memes-2', 'free', 66),
  ('sticker', 'Sticker 046', 'stickers/Minecraft_Memes2/sticker-046.webp', 'minecraft-memes-2', 'free', 67),
  ('sticker', 'Sticker 047', 'stickers/Minecraft_Memes2/sticker-047.webp', 'minecraft-memes-2', 'free', 68),
  ('sticker', 'Sticker 048', 'stickers/Minecraft_Memes2/sticker-048.webp', 'minecraft-memes-2', 'free', 69),
  ('sticker', 'Sticker 049', 'stickers/Minecraft_Memes2/sticker-049.webp', 'minecraft-memes-2', 'free', 70),
  ('sticker', 'Sticker 050', 'stickers/Minecraft_Memes2/sticker-050.webp', 'minecraft-memes-2', 'free', 71),
  ('sticker', 'Sticker 051', 'stickers/Minecraft_Memes2/sticker-051.webp', 'minecraft-memes-2', 'free', 72),
  ('sticker', 'Sticker 052', 'stickers/Minecraft_Memes2/sticker-052.webp', 'minecraft-memes-2', 'free', 73),
  ('sticker', 'Sticker 053', 'stickers/Minecraft_Memes2/sticker-053.webp', 'minecraft-memes-2', 'free', 74),
  ('sticker', 'Sticker 054', 'stickers/Minecraft_Memes2/sticker-054.webp', 'minecraft-memes-2', 'free', 75),
  ('sticker', 'Sticker 055', 'stickers/Minecraft_Memes2/sticker-055.webp', 'minecraft-memes-2', 'free', 76),
  ('sticker', 'Sticker 056', 'stickers/Minecraft_Memes2/sticker-056.webp', 'minecraft-memes-2', 'free', 77),
  ('sticker', 'Sticker 057', 'stickers/Minecraft_Memes2/sticker-057.webp', 'minecraft-memes-2', 'free', 78),
  ('sticker', 'Sticker 058', 'stickers/Minecraft_Memes2/sticker-058.webp', 'minecraft-memes-2', 'free', 79),
  ('sticker', 'Sticker 059', 'stickers/Minecraft_Memes2/sticker-059.webp', 'minecraft-memes-2', 'free', 80),
  ('sticker', 'Sticker 060', 'stickers/Minecraft_Memes2/sticker-060.webp', 'minecraft-memes-2', 'free', 81),
  ('sticker', 'Sticker 061', 'stickers/Minecraft_Memes2/sticker-061.webp', 'minecraft-memes-2', 'free', 82),
  ('sticker', 'Sticker 062', 'stickers/Minecraft_Memes2/sticker-062.webp', 'minecraft-memes-2', 'free', 83),
  ('sticker', 'Sticker 063', 'stickers/Minecraft_Memes2/sticker-063.webp', 'minecraft-memes-2', 'free', 84),
  ('sticker', 'Sticker 064', 'stickers/Minecraft_Memes2/sticker-064.webp', 'minecraft-memes-2', 'free', 85),
  ('sticker', 'Sticker 065', 'stickers/Minecraft_Memes2/sticker-065.webp', 'minecraft-memes-2', 'free', 86),
  ('sticker', 'Sticker 066', 'stickers/Minecraft_Memes2/sticker-066.webp', 'minecraft-memes-2', 'free', 87),
  ('sticker', 'Sticker 067', 'stickers/Minecraft_Memes2/sticker-067.webp', 'minecraft-memes-2', 'free', 88),
  ('sticker', 'Sticker 068', 'stickers/Minecraft_Memes2/sticker-068.webp', 'minecraft-memes-2', 'free', 89),
  ('sticker', 'Sticker 069', 'stickers/Minecraft_Memes2/sticker-069.webp', 'minecraft-memes-2', 'free', 90),
  ('sticker', 'Sticker 070', 'stickers/Minecraft_Memes2/sticker-070.webp', 'minecraft-memes-2', 'free', 91),
  ('sticker', 'Sticker 071', 'stickers/Minecraft_Memes2/sticker-071.webp', 'minecraft-memes-2', 'free', 92),
  ('sticker', 'Sticker 072', 'stickers/Minecraft_Memes2/sticker-072.webp', 'minecraft-memes-2', 'free', 93),
  ('sticker', 'Sticker 073', 'stickers/Minecraft_Memes2/sticker-073.webp', 'minecraft-memes-2', 'free', 94),
  ('sticker', 'Sticker 074', 'stickers/Minecraft_Memes2/sticker-074.webp', 'minecraft-memes-2', 'free', 95),
  ('sticker', 'Sticker 075', 'stickers/Minecraft_Memes2/sticker-075.webp', 'minecraft-memes-2', 'free', 96),
  ('sticker', 'Sticker 076', 'stickers/Minecraft_Memes2/sticker-076.webp', 'minecraft-memes-2', 'free', 97),
  ('sticker', 'Sticker 077', 'stickers/Minecraft_Memes2/sticker-077.webp', 'minecraft-memes-2', 'free', 98),
  ('sticker', 'Sticker 078', 'stickers/Minecraft_Memes2/sticker-078.webp', 'minecraft-memes-2', 'free', 99),
  ('sticker', 'Sticker 079', 'stickers/Minecraft_Memes2/sticker-079.webp', 'minecraft-memes-2', 'free', 100),
  ('sticker', 'Sticker 080', 'stickers/Minecraft_Memes2/sticker-080.webp', 'minecraft-memes-2', 'free', 101),
  ('sticker', 'Sticker 081', 'stickers/Minecraft_Memes2/sticker-081.webp', 'minecraft-memes-2', 'free', 102),
  ('sticker', 'Sticker 082', 'stickers/Minecraft_Memes2/sticker-082.webp', 'minecraft-memes-2', 'free', 103),
  ('sticker', 'Sticker 083', 'stickers/Minecraft_Memes2/sticker-083.webp', 'minecraft-memes-2', 'free', 104),
  ('sticker', 'Sticker 084', 'stickers/Minecraft_Memes2/sticker-084.webp', 'minecraft-memes-2', 'free', 105),
  ('sticker', 'Sticker 085', 'stickers/Minecraft_Memes2/sticker-085.webp', 'minecraft-memes-2', 'free', 106),
  ('sticker', 'Sticker 086', 'stickers/Minecraft_Memes2/sticker-086.webp', 'minecraft-memes-2', 'free', 107),
  ('sticker', 'Sticker 087', 'stickers/Minecraft_Memes2/sticker-087.webp', 'minecraft-memes-2', 'free', 108),
  ('sticker', 'Sticker 001', 'stickers/Minecraft_Memes3/sticker-001.webp', 'minecraft-memes-3', 'free', 109),
  ('sticker', 'Sticker 002', 'stickers/Minecraft_Memes3/sticker-002.webp', 'minecraft-memes-3', 'free', 110),
  ('sticker', 'Sticker 003', 'stickers/Minecraft_Memes3/sticker-003.webp', 'minecraft-memes-3', 'free', 111),
  ('sticker', 'Sticker 004', 'stickers/Minecraft_Memes3/sticker-004.webp', 'minecraft-memes-3', 'free', 112),
  ('sticker', 'Sticker 005', 'stickers/Minecraft_Memes3/sticker-005.webp', 'minecraft-memes-3', 'free', 113),
  ('sticker', 'Sticker 006', 'stickers/Minecraft_Memes3/sticker-006.webp', 'minecraft-memes-3', 'free', 114),
  ('sticker', 'Sticker 007', 'stickers/Minecraft_Memes3/sticker-007.webp', 'minecraft-memes-3', 'free', 115),
  ('sticker', 'Sticker 008', 'stickers/Minecraft_Memes3/sticker-008.webp', 'minecraft-memes-3', 'free', 116),
  ('sticker', 'Sticker 009', 'stickers/Minecraft_Memes3/sticker-009.webp', 'minecraft-memes-3', 'free', 117),
  ('sticker', 'Sticker 010', 'stickers/Minecraft_Memes3/sticker-010.webp', 'minecraft-memes-3', 'free', 118),
  ('sticker', 'Sticker 011', 'stickers/Minecraft_Memes3/sticker-011.webp', 'minecraft-memes-3', 'free', 119),
  ('sticker', 'Sticker 012', 'stickers/Minecraft_Memes3/sticker-012.webp', 'minecraft-memes-3', 'free', 120),
  ('sticker', 'Sticker 013', 'stickers/Minecraft_Memes3/sticker-013.webp', 'minecraft-memes-3', 'free', 121),
  ('sticker', 'Sticker 014', 'stickers/Minecraft_Memes3/sticker-014.webp', 'minecraft-memes-3', 'free', 122),
  ('sticker', 'Sticker 015', 'stickers/Minecraft_Memes3/sticker-015.webp', 'minecraft-memes-3', 'free', 123),
  ('sticker', 'Sticker 016', 'stickers/Minecraft_Memes3/sticker-016.webp', 'minecraft-memes-3', 'free', 124),
  ('sticker', 'Sticker 017', 'stickers/Minecraft_Memes3/sticker-017.webp', 'minecraft-memes-3', 'free', 125),
  ('sticker', 'Sticker 018', 'stickers/Minecraft_Memes3/sticker-018.webp', 'minecraft-memes-3', 'free', 126),
  ('sticker', 'Sticker 019', 'stickers/Minecraft_Memes3/sticker-019.webp', 'minecraft-memes-3', 'free', 127),
  ('sticker', 'Sticker 020', 'stickers/Minecraft_Memes3/sticker-020.webp', 'minecraft-memes-3', 'free', 128),
  ('sticker', 'Sticker 001', 'stickers/Minecraft_Movie/sticker-001.webp', 'minecraft-movie', 'free', 129),
  ('sticker', 'Sticker 002', 'stickers/Minecraft_Movie/sticker-002.webp', 'minecraft-movie', 'free', 130),
  ('sticker', 'Sticker 003', 'stickers/Minecraft_Movie/sticker-003.webp', 'minecraft-movie', 'free', 131),
  ('sticker', 'Sticker 004', 'stickers/Minecraft_Movie/sticker-004.webp', 'minecraft-movie', 'free', 132),
  ('sticker', 'Sticker 005', 'stickers/Minecraft_Movie/sticker-005.webp', 'minecraft-movie', 'free', 133),
  ('sticker', 'Sticker 006', 'stickers/Minecraft_Movie/sticker-006.webp', 'minecraft-movie', 'free', 134),
  ('sticker', 'Sticker 001', 'stickers/meme/sticker-001.webp', 'meme', 'free', 135),
  ('sticker', 'Sticker 002', 'stickers/meme/sticker-002.webp', 'meme', 'free', 136),
  ('sticker', 'Sticker 003', 'stickers/meme/sticker-003.webp', 'meme', 'free', 137),
  ('sticker', 'Sticker 004', 'stickers/meme/sticker-004.webp', 'meme', 'free', 138),
  ('sticker', 'Sticker 005', 'stickers/meme/sticker-005.webp', 'meme', 'free', 139),
  ('sticker', 'Sticker 006', 'stickers/meme/sticker-006.webp', 'meme', 'free', 140),
  ('sticker', 'Sticker 007', 'stickers/meme/sticker-007.webp', 'meme', 'free', 141),
  ('sticker', 'Sticker 008', 'stickers/meme/sticker-008.webp', 'meme', 'free', 142),
  ('sticker', 'Sticker 009', 'stickers/meme/sticker-009.webp', 'meme', 'free', 143),
  ('sticker', 'Sticker 010', 'stickers/meme/sticker-010.webp', 'meme', 'free', 144),
  ('sticker', 'Sticker 011', 'stickers/meme/sticker-011.webp', 'meme', 'free', 145),
  ('sticker', 'Sticker 012', 'stickers/meme/sticker-012.webp', 'meme', 'free', 146),
  ('sticker', 'Sticker 013', 'stickers/meme/sticker-013.webp', 'meme', 'free', 147),
  ('sticker', 'Sticker 014', 'stickers/meme/sticker-014.webp', 'meme', 'free', 148),
  ('sticker', 'Sticker 015', 'stickers/meme/sticker-015.webp', 'meme', 'free', 149),
  ('sticker', 'Sticker 016', 'stickers/meme/sticker-016.webp', 'meme', 'free', 150),
  ('sticker', 'Sticker 017', 'stickers/meme/sticker-017.webp', 'meme', 'free', 151),
  ('sticker', 'Sticker 018', 'stickers/meme/sticker-018.webp', 'meme', 'free', 152),
  ('sticker', 'Sticker 019', 'stickers/meme/sticker-019.webp', 'meme', 'free', 153),
  ('sticker', 'Sticker 020', 'stickers/meme/sticker-020.webp', 'meme', 'free', 154),
  ('sticker', 'Sticker 021', 'stickers/meme/sticker-021.webp', 'meme', 'free', 155),
  ('sticker', 'Sticker 022', 'stickers/meme/sticker-022.webp', 'meme', 'free', 156),
  ('sticker', 'Sticker 023', 'stickers/meme/sticker-023.webp', 'meme', 'free', 157),
  ('sticker', 'Sticker 024', 'stickers/meme/sticker-024.webp', 'meme', 'free', 158),
  ('sticker', 'Sticker 025', 'stickers/meme/sticker-025.webp', 'meme', 'free', 159),
  ('sticker', 'Sticker 026', 'stickers/meme/sticker-026.webp', 'meme', 'free', 160),
  ('sticker', 'Sticker 027', 'stickers/meme/sticker-027.webp', 'meme', 'free', 161),
  ('sticker', 'Sticker 028', 'stickers/meme/sticker-028.webp', 'meme', 'free', 162),
  ('sticker', 'Sticker 029', 'stickers/meme/sticker-029.webp', 'meme', 'free', 163),
  ('sticker', 'Sticker 030', 'stickers/meme/sticker-030.webp', 'meme', 'free', 164),
  ('sticker', 'Sticker 031', 'stickers/meme/sticker-031.webp', 'meme', 'free', 165),
  ('sticker', 'Sticker 032', 'stickers/meme/sticker-032.webp', 'meme', 'free', 166),
  ('sticker', 'Sticker 033', 'stickers/meme/sticker-033.webp', 'meme', 'free', 167),
  ('sticker', 'Sticker 034', 'stickers/meme/sticker-034.webp', 'meme', 'free', 168),
  ('sticker', 'Sticker 035', 'stickers/meme/sticker-035.webp', 'meme', 'free', 169),
  ('sticker', 'Sticker 036', 'stickers/meme/sticker-036.webp', 'meme', 'free', 170),
  ('sticker', 'Sticker 037', 'stickers/meme/sticker-037.webp', 'meme', 'free', 171),
  ('sticker', 'Sticker 038', 'stickers/meme/sticker-038.webp', 'meme', 'free', 172),
  ('sticker', 'Sticker 039', 'stickers/meme/sticker-039.webp', 'meme', 'free', 173),
  ('sticker', 'Sticker 040', 'stickers/meme/sticker-040.webp', 'meme', 'free', 174),
  ('sticker', 'Sticker 041', 'stickers/meme/sticker-041.webp', 'meme', 'free', 175),
  ('sticker', 'Sticker 042', 'stickers/meme/sticker-042.webp', 'meme', 'free', 176),
  ('sticker', 'Sticker 043', 'stickers/meme/sticker-043.webp', 'meme', 'free', 177),
  ('sticker', 'Sticker 044', 'stickers/meme/sticker-044.webp', 'meme', 'free', 178),
  ('sticker', 'Sticker 045', 'stickers/meme/sticker-045.webp', 'meme', 'free', 179),
  ('sticker', 'Sticker 046', 'stickers/meme/sticker-046.webp', 'meme', 'free', 180),
  ('sticker', 'Sticker 047', 'stickers/meme/sticker-047.webp', 'meme', 'free', 181)
on conflict do nothing;


-- ############################################################################
-- ##  القسم: 12_avatars.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 12) الأفاتارات الأولية (25 أفاتار)
--  الملفات ترفع إلى مخزن site-assets داخل مجلد profiles/ (انظر docs/02)
--  التوزيع: 1 مجاني (الافتراضي) · 12 لملحمي Epic 💎 · 12 لأسطوري Legendary 👑
--  آمن للتكرار: يعتمد على فهرس فريد على file_url
-- ============================================================================

-- ⚠️ [مُستبدَل] أُلغيت هذه الأصول القديمة نهائياً وحلّت محلها الدفعة الجديدة
--    (492 أصلاً في 21 قسماً) عبر الملف: supabase/15_assets_v2.sql
--    هذا الملف باقٍ للتوافق فقط؛ تنفيذه وحده سيُعيد الأصول القديمة — لا تنفّذه بعده.

-- ---------------------------------------------------------------------------
-- 0) ضمان عدم تكرار الصفوف عند إعادة التنفيذ
--    إن فشل هذا السطر (توجد صفوف مكررة سابقاً) استخدم استعلام التنظيف أدناه.
-- تنظيم (اختياري — إن ظهر خطأ "could not create unique index"):
--   delete from public.site_assets a using public.site_assets b
--    where a.file_url = b.file_url and a.id > b.id;
-- ---------------------------------------------------------------------------
create unique index if not exists site_assets_file_url_uidx on public.site_assets (file_url);

-- ---------------------------------------------------------------------------
-- 1) الأفاتارات (asset_type = 'avatar')
-- ---------------------------------------------------------------------------
insert into public.site_assets (asset_type, name, file_url, required_plan, sort_order)
values
  ('avatar', 'onefree', 'profiles/onefree.png', 'free', 287),
  ('avatar', 'Cute girl', 'profiles/Cute_girl.jpg', 'epic', 288),
  ('avatar', 'Diamond Armor Glow', 'profiles/Diamond_Armor_Glow.jpg', 'epic', 289),
  ('avatar', 'Giant Minecraft Cookie', 'profiles/Giant_Minecraft_Cookie.jpg', 'epic', 290),
  ('avatar', 'Gigachad Doge Minecraft', 'profiles/Gigachad_Doge_Minecraft.jpg', 'epic', 291),
  ('avatar', 'Golden Helmet Steve', 'profiles/Golden_Helmet_Steve.jpg', 'epic', 292),
  ('avatar', 'Lush Cave Axolotls', 'profiles/Lush_Cave_Axolotls.jpg', 'epic', 293),
  ('avatar', 'Minecraft Bed Explosion', 'profiles/Minecraft_Bed_Explosion.jpg', 'epic', 294),
  ('avatar', 'Minecraft Cat Meme', 'profiles/Minecraft_Cat_Meme.jpg', 'epic', 295),
  ('avatar', 'Minecraft Cute Wolf', 'profiles/Minecraft_Cute_Wolf.jpg', 'epic', 296),
  ('avatar', 'Minecraft Music Disc', 'profiles/Minecraft_Music_Disc.jpg', 'epic', 297),
  ('avatar', 'Minecraft Rose Painting', 'profiles/Minecraft_Rose_Painting.jpg', 'epic', 298),
  ('avatar', 'Minecraft Warden Cave', 'profiles/Minecraft_Warden_Cave.jpg', 'epic', 299),
  ('avatar', 'Netherite Armor Steve', 'profiles/Netherite_Armor_Steve.jpg', 'legendary', 300),
  ('avatar', 'Netherite Warrior Spear', 'profiles/Netherite_Warrior_Spear.jpg', 'legendary', 301),
  ('avatar', 'Steve Mining Creeper', 'profiles/Steve_Mining_Creeper.jpg', 'legendary', 302),
  ('avatar', 'Tall Enderman girl', 'profiles/Tall_Enderman_girl.jpg', 'legendary', 303),
  ('avatar', 'Waguri-chan is Minecraft', 'profiles/Waguri-chan is Minecraft.jpg', 'legendary', 304),
  ('avatar', 'Zombie Climbing Ladder', 'profiles/Zombie_Climbing_Ladder.jpg', 'legendary', 305),
  ('avatar', 'Aelx with TheBoat', 'profiles/Aelx_with_TheBoat.jpg', 'legendary', 306),
  ('avatar', 'Axolotl Aquarium', 'profiles/Axolotl_Aquarium.jpg', 'legendary', 307),
  ('avatar', 'Cheems Doge Minecraft', 'profiles/Cheems_Doge_Minecraft.jpg', 'legendary', 308),
  ('avatar', 'Cherry Blossom Biom', 'profiles/Cherry_Blossom_Biom.jpg', 'legendary', 309),
  ('avatar', 'Cozy Furnace Corner', 'profiles/Cozy_Furnace_Corner.jpg', 'legendary', 310),
  ('avatar', 'Cute Enderman Girl', 'profiles/Cute_Enderman_Girl.jpg', 'legendary', 311)
on conflict (file_url) do nothing;

-- ---------------------------------------------------------------------------
-- ✅ تحقّق: المتوقّع avatar = 25 · free = 1 · epic = 12 · legendary = 12
-- ---------------------------------------------------------------------------
select required_plan, count(*) as avatars
  from public.site_assets
 where asset_type = 'avatar'
 group by required_plan order by required_plan;


-- ############################################################################
-- ##  القسم: 10_realtime.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 10) تشغيل المزامنة اللحظية (Realtime) للشات
--  نفّذ هذا الملف بعد 03_functions_chat.sql و 04_rls.sql.
--  آمن للتشغيل أكثر من مرة (يتخطّى الجداول المضافة مسبقاً).
-- ============================================================================

-- 1) تأكيد وجود منشور المزامنة الافتراضي (يوجد في كل مشروع Supabase جديد)
do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
    raise notice 'أُنشئ المنشور supabase_realtime';
  end if;
end $$;

-- 2) إضافة الجداول المطلوبة للشات والإشعارات والملفات الشخصية
do $$
declare
  t text;
  tables text[] := array[
    'chat_messages',      -- الرسائل الجديدة
    'chat_reactions',     -- التفاعلات
    'chat_typing',        -- «يكتب الآن…»
    'chat_read_state',    -- مقروء/غير مقروء
    'chat_notifications', -- إشعارات المنشن والردود
    'profiles'            -- تغيّرات الاسم/الصورة/الرتبة
  ];
begin
  foreach t in array tables loop
    begin
      execute format('alter publication supabase_realtime add table public.%I', t);
      raise notice '✅ أُضيف للـ Realtime: %', t;
    exception
      when duplicate_object then
        raise notice '↺ موجود مسبقاً: %', t;
      when undefined_table then
        raise notice '⚠️ الجدول غير موجود (نفّذ 01_schema.sql أولاً): %', t;
    end;
  end loop;
end $$;

-- 3) تأكيد أن كل الجداول صارت في المنشور (يجب أن تظهر 6 صفوف أو أكثر)
select schemaname, tablename
from pg_publication_tables
where pubname = 'supabase_realtime'
order by tablename;

-- ملاحظة: مفاتيح RLS هي التي تحدّد ما يصل لكل مستخدم لحظياً،
-- ولا تُرسَل أي بيانات مستخدم لغير صاحبها حتى لو كان الاشتراك مفتوحاً.


-- ############################################################################
-- ##  القسم: 11_grants.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 11) منح الصلاحيات (Grants) للـData API
--
--  ⚠️ إلزامي في المشاريع الجديدة:
--     ابتداءً من 30 مايو 2026 لم تعد Supabase تمنح anon / authenticated أي
--     صلاحية تلقائياً على جداول public. بدون هذا الملف يظهر للتطبيق الخطأ:
--         permission denied for table chat_channels
--         hint: GRANT SELECT ON public.chat_channels TO anon;
--
--     القاعدة الذهبية: **RPC + RLS لا يكفيان** — يجب أيضًا:
--         (1) منح الصلاحية على مستوى الجدول/العمود  ← هذا الملف
--         (2) السماح على مستوى الصف عبر سياسة RLS    ← 04_rls.sql
--
--  نفّذ هذا الملف في SQL Editor (المالك: postgres). آمن للتكرار (idempotent).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) القراءة: كل الجداول متاحة للمفتاح العام وللمسجّلين
--    (RLS هي التي تحدد أي صفوف تُرى فعلاً — لا تسريب)
-- ---------------------------------------------------------------------------
grant select on all tables in schema public to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2) الكتابة المباشرة التي يحتاجها التطبيق (بقية الكتابة تتم عبر دوال RPC)
--    RLS تتحقق من الدور: أدمن للوحة، وصاحب الحساب لملفه الشخصي
-- ---------------------------------------------------------------------------
do $$
begin
  -- لوحة الإدارة: أصول الموقع وحزم الملصقات (insert/update/delete مباشرة)
  begin
    grant insert, update, delete on public.site_assets   to authenticated;
    grant insert, update, delete on public.sticker_packs to authenticated;
  exception when undefined_table then null;
  end;

  -- الملف الشخصي: منح على مستوى العمود (المستخدم يعدّل حقوله المسموحة فقط)
  begin
    grant update (display_name, bio, custom_status, show_online_status, country,
                  last_seen_at, equipped_item_id)
      on public.profiles to authenticated;
  exception when undefined_table or undefined_column then null;
  end;

  -- حذف صديق من قائمة الأصدقاء
  begin
    grant delete on public.user_friends to authenticated;
  exception when undefined_table then null;
  end;
end $$;

-- ---------------------------------------------------------------------------
-- 3) المتسلسلات: الإدراج المباشر (لوحة الإدارة) يستهلك nextval على id
-- ---------------------------------------------------------------------------
grant usage, select on all sequences in schema public to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4) حماية دوال بوابة الدفع: تُنفَّذ بـservice_role فقط (لا التطبيق ولا anon)
--    (ينجح حتى لو لم تُنفَّذ 09_gateway_functions.sql بعد)
-- ---------------------------------------------------------------------------
do $$
begin
  begin
    revoke all on function public.gateway_confirm_order(text, text, text) from public, anon, authenticated;
  exception when undefined_function then null;
  end;
  begin
    revoke all on function public.gateway_attach_session(uuid, text, text) from public, anon, authenticated;
  exception when undefined_function then null;
  end;
end $$;

-- ---------------------------------------------------------------------------
-- 4.5) دور service_role: يحتاجه سير الرفع الآلي من GitHub لكتابة الأصول
--      (بلا هذه المنح يرفض PostgREST الكتابة بخطأ 42501 insufficient_privilege)
-- ---------------------------------------------------------------------------
grant usage on schema public to service_role;

do $$
declare t text;
begin
  for t in
    select tablename from pg_tables where schemaname = 'public'
  loop
    execute format('grant select, insert, update, delete on public.%I to service_role', t);
  end loop;
end $$;

grant select, insert, update, delete on public.site_assets to service_role;
grant select, insert, update, delete on public.sticker_packs to service_role;

-- ---------------------------------------------------------------------------
-- 5) للمستقبل: أي جدول/متسلسلة تُنشأ في public تُمنح القراءة تلقائياً
--    (ملاحظة: هذا يخص دور postgres — وهو الدور الذي ينفّذ SQL Editor)
-- ---------------------------------------------------------------------------
alter default privileges for role postgres in schema public
  grant select on tables to anon, authenticated;

alter default privileges for role postgres in schema public
  grant usage, select on sequences to anon, authenticated;

-- ---------------------------------------------------------------------------
-- ✅ تحقّق: المتوقّع  anon = 33  و  authenticated = 33
-- ---------------------------------------------------------------------------
select grantee, count(*) as tables_with_select
  from information_schema.role_table_grants
 where table_schema = 'public'
   and privilege_type = 'SELECT'
   and grantee in ('anon', 'authenticated')
 group by grantee
 order by grantee;


-- ############################################################################
-- ##  القسم: 13_channels_icons.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 13) أسماء الغرف بلا إيموجي + أيقونات مرسومة
--
--  الغرف صارت تُرسم أيقوناتها داخل التطبيق من معرّف الغرفة (channel.id)
--  عبر الدالة RoomIcon في ui/components/Common.kt — فلا حاجة لإيموجي في
--  قاعدة البيانات. نفّذ هذا الملف مرة واحدة بعد 06_seed.sql (أو بعد
--  00_ALL_IN_ONE.sql) ليصبح الاسم نظيفاً بلا رموز.
-- ============================================================================

update public.chat_channels set name = 'General',             icon = null where id = 'general';
update public.chat_channels set name = 'Minecraft',           icon = null where id = 'minecraft';
update public.chat_channels set name = 'Creator',             icon = null where id = 'creator';
update public.chat_channels set name = 'Support',             icon = null where id = 'support';
update public.chat_channels set name = 'Social Media Makers', icon = null where id = 'social-media-makers';

update public.chat_channels set name = 'العربية',   icon = null where id = 'lang-ar';
update public.chat_channels set name = 'Русский',   icon = null where id = 'lang-ru';
update public.chat_channels set name = 'Português', icon = null where id = 'lang-pt';
update public.chat_channels set name = '中文',       icon = null where id = 'lang-zh';
update public.chat_channels set name = 'English',   icon = null where id = 'lang-en';
update public.chat_channels set name = 'Español',   icon = null where id = 'lang-es';
update public.chat_channels set name = 'Français',  icon = null where id = 'lang-fr';
update public.chat_channels set name = 'Türkçe',    icon = null where id = 'lang-tr';

-- ---------------------------------------------------------------------------
-- تحقق: يجب أن ترى 13 صفاً بلا أي إيموجي في name، و icon كلها NULL
-- ---------------------------------------------------------------------------
select id, name, category, sort_order
from public.chat_channels
order by category desc, sort_order;


-- ############################################################################
-- ##  القسم: 14_redeem_codes.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 14) أكواد هدايا AC (توليد من الإدارة · استبدال من المحفظة)
--
--  الفكرة باختصار:
--    • الإدارة تولّد الأكواد من داخل التطبيق (لوحة الإدارة → AC Management)
--      أو من الأداة  tools/make_redeem_codes.py  ثم تُلصق النتيجة في SQL Editor.
--    • المستخدم يكتب الكود في:  الإعدادات → المحفظة (AC wallet) → «كود هدية AC»
--      فوق قسم «كيف تكسب AC» مباشرة.
--    • القيم الجاهزة: 5,000 · 15,000 · 30,000 · 100,000 AC (وأي قيمة أخرى تريدها).
--
--  تنسيق الكود: 8 خانات = 6 حروف (كبيرة وصغيرة) + رقمان — مثال: Ac7Kp2Qm
--  (استُبعدت الحروف المتشابهة I/O/l/o والأرقام 0/1 لمنع الخطأ في الكتابة)
--
--  ✅ آمن لإعادة التنفيذ (idempotent) — نفّذه بعد 00_ALL_IN_ONE.sql.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) السماح بنوع معاملة جديد 'code' في سجل AC
--    (نفس القيد المعرَّف في 01_schema.sql مضافاً إليه 'code')
-- ---------------------------------------------------------------------------
alter table public.ac_transactions drop constraint if exists ac_transactions_kind_check;
alter table public.ac_transactions add constraint ac_transactions_kind_check
  check (kind in ('task','daily','streak','admin_reward','admin_adjustment','unlock','event','welcome','code'));

-- ---------------------------------------------------------------------------
-- 2) الجداول
-- ---------------------------------------------------------------------------
create table if not exists public.ac_redeem_codes (
  code       text primary key,                      -- الكود كما هو (حروف كبيرة/صغيرة + أرقام)
  amount     int not null check (amount between 100 and 10000000),
  max_uses   int not null default 1 check (max_uses >= 1),
  used_count int not null default 0 check (used_count >= 0),
  note       text,                                  -- وصف داخلي (حملة، هدية، …)
  active     boolean not null default true,
  expires_at timestamptz,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);
-- منع تكرار الكود بغضّ النظر عن حالة الحروف (Ac7Kp2Qm = ac7kp2qm)
create unique index if not exists ac_redeem_codes_upper_uniq on public.ac_redeem_codes (upper(code));
create index if not exists ac_redeem_codes_active_idx on public.ac_redeem_codes(active, created_at desc);

create table if not exists public.ac_redeem_uses (
  id         bigserial primary key,
  code       text not null references public.ac_redeem_codes(code) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  amount     int not null,
  created_at timestamptz not null default now()
);
-- كل مستخدم يستبدل الكود نفسه مرة واحدة فقط
create unique index if not exists ac_redeem_uses_once_uniq on public.ac_redeem_uses(code, user_id);
create index if not exists ac_redeem_uses_user_idx on public.ac_redeem_uses(user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 3) دالة مساعدة: توليد كود 8 خانات (6 حروف كبيرة/صغيرة + رقمان)
-- ---------------------------------------------------------------------------
create or replace function public.ac_random_code()
returns text
language plpgsql volatile
as $$
declare
  v_upper  text := 'ABCDEFGHJKLMNPQRSTUVWXYZ';   -- 24 حرفاً (بلا I و O)
  v_lower  text := 'abcdefghijkmnpqrstuvwxyz';   -- 24 حرفاً (بلا l و o)
  v_digit  text := '23456789';                   -- 8 أرقام (بلا 0 و 1)
  v_chars  text[] := '{}';
  i        int;
  j        int;
  v_tmp    text;
begin
  -- حرف كبير + حرف صغير (مضمونان) + 4 حروف عشوائية
  v_chars := array[
    substr(v_upper, 1 + floor(random() * 24)::int, 1),
    substr(v_lower, 1 + floor(random() * 24)::int, 1)
  ];
  for i in 1..4 loop
    if random() < 0.5 then
      v_chars := v_chars || substr(v_upper, 1 + floor(random() * 24)::int, 1);
    else
      v_chars := v_chars || substr(v_lower, 1 + floor(random() * 24)::int, 1);
    end if;
  end loop;
  -- رقمان
  for i in 1..2 loop
    v_chars := v_chars || substr(v_digit, 1 + floor(random() * 8)::int, 1);
  end loop;
  -- خلط المواضع (Fisher–Yates)
  for i in reverse 8..2 loop
    j := 1 + floor(random() * i)::int;
    v_tmp := v_chars[i];
    v_chars[i] := v_chars[j];
    v_chars[j] := v_tmp;
  end loop;
  return array_to_string(v_chars, '');
end $$;

-- ---------------------------------------------------------------------------
-- 4) دالة الإدارة: توليد أكواد جديدة (تُستدعى من لوحة الإدارة داخل التطبيق)
--    ac_admin_create_codes(p_amount, p_count, p_max_uses, p_note)
-- ---------------------------------------------------------------------------
create or replace function public.ac_admin_create_codes(
  p_amount   int,
  p_count    int default 1,
  p_max_uses int default 1,
  p_note     text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_codes text[] := '{}';
  v_code  text;
  v_count int := least(greatest(coalesce(p_count, 1), 1), 100);
  i       int;
  tries   int;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;
  if p_amount is null or p_amount < 100 then
    return jsonb_build_object('ok', false, 'message', 'bad_amount');
  end if;

  for i in 1..v_count loop
    tries := 0;
    loop
      v_code := public.ac_random_code();
      tries := tries + 1;
      exit when not exists (select 1 from public.ac_redeem_codes where upper(code) = upper(v_code));
      exit when tries > 20;   -- احتمال نظرياً فقط
    end loop;
    insert into public.ac_redeem_codes(code, amount, max_uses, note, created_by)
    values (v_code, p_amount, greatest(coalesce(p_max_uses, 1), 1), p_note, auth.uid())
    on conflict (code) do nothing;
    v_codes := v_codes || v_code;
  end loop;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'ac_create_codes', 'ac_redeem_codes', null,
          jsonb_build_object('amount', p_amount, 'count', v_count, 'max_uses', p_max_uses));

  return jsonb_build_object('ok', true, 'amount', p_amount, 'count', v_count, 'codes', to_jsonb(v_codes));
end $$;

-- ---------------------------------------------------------------------------
-- 5) دالة المستخدم: استبدال الكود من محفظة AC
--    تُرجع: ok + amount + balance، أو ok=false مع سبب مفهوم:
--    code_invalid · code_used · code_expired · code_inactive · code_exhausted
-- ---------------------------------------------------------------------------
create or replace function public.ac_redeem_code(p_code text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_norm    text := upper(btrim(coalesce(p_code, '')));
  v_code    public.ac_redeem_codes;
  v_balance int;
  v_key     text;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;
  if length(v_norm) < 4 then
    return jsonb_build_object('ok', false, 'message', 'code_invalid');
  end if;

  -- قفل الصف حتى لا يُستهلك الكود مرتين في نفس اللحظة
  select * into v_code from public.ac_redeem_codes where upper(code) = v_norm for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'code_invalid');
  end if;
  if not v_code.active then
    return jsonb_build_object('ok', false, 'message', 'code_inactive');
  end if;
  if v_code.expires_at is not null and v_code.expires_at < now() then
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;
  if exists (select 1 from public.ac_redeem_uses where code = v_code.code and user_id = v_uid) then
    return jsonb_build_object('ok', false, 'message', 'code_used');
  end if;
  if v_code.used_count >= v_code.max_uses then
    return jsonb_build_object('ok', false, 'message', 'code_exhausted');
  end if;

  v_key := 'code:' || v_uid::text || ':' || v_code.code;

  insert into public.ac_redeem_uses(code, user_id, amount)
  values (v_code.code, v_uid, v_code.amount)
  on conflict (code, user_id) do nothing;

  update public.ac_redeem_codes set used_count = used_count + 1 where code = v_code.code;

  insert into public.ac_wallets(user_id) values (v_uid) on conflict (user_id) do nothing;

  update public.ac_wallets
     set balance      = balance + v_code.amount,
         total_earned = total_earned + v_code.amount,
         updated_at   = now()
   where user_id = v_uid
   returning balance into v_balance;

  insert into public.ac_transactions(user_id, amount, kind, reason, request_key, balance_after)
  values (v_uid, v_code.amount, 'code', 'redeem:' || v_code.code, v_key, v_balance)
  on conflict (request_key) do nothing;

  return jsonb_build_object(
    'ok', true,
    'amount', v_code.amount,
    'balance', v_balance,
    'code', v_code.code
  );
end $$;

-- ---------------------------------------------------------------------------
-- 6) الأمان: RLS (لا يرى الأكوادَ غيرُ الإدارة) + المنح الصريحة
-- ---------------------------------------------------------------------------
alter table public.ac_redeem_codes enable row level security;
alter table public.ac_redeem_uses  enable row level security;

drop policy if exists ac_redeem_codes_admin on public.ac_redeem_codes;
create policy ac_redeem_codes_admin on public.ac_redeem_codes
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists ac_redeem_uses_self on public.ac_redeem_uses;
create policy ac_redeem_uses_self on public.ac_redeem_uses
  for select using (user_id = auth.uid() or public.is_admin());

-- الجدولان جديدان: منح القراءة للمسجّلين (وبلا anon حتى لا تُقرأ الأكواد)
grant select on public.ac_redeem_codes, public.ac_redeem_uses to authenticated;

grant execute on function public.ac_redeem_code(text) to authenticated;
grant execute on function public.ac_admin_create_codes(int, int, int, text) to authenticated;
-- الإلغاء من public ضروري: منح EXECUTE الافتراضي في PostgreSQL يشمل كل الأدوار
revoke all on function public.ac_redeem_code(text) from public, anon;
revoke all on function public.ac_admin_create_codes(int, int, int, text) from public, anon;
revoke all on function public.ac_random_code() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 7) أمثلة جاهزة (اختيارية) — علّقها إن لم تُرِدها:
--    لإضافة كود يدوياً بقيمة معيّنة اكتب سطراً مثل هذا:
--      insert into public.ac_redeem_codes (code, amount, note)
--      values ('Ac7Kp2Qm', 15000, 'حملة سبتمبر');
--    للتوليد دفعةً واحدة من SQL مباشرة:
--      select public.ac_admin_create_codes(15000, 5);   -- 5 أكواد × 15,000
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- ✅ تحقق: يجب أن ترى الجدولين (عدد الأكواد = 0 في البداية) والدالتين
-- ---------------------------------------------------------------------------
select
  (select count(*) from public.ac_redeem_codes) as codes_total,
  (select count(*) from public.ac_redeem_uses)  as uses_total,
  (select count(*) from information_schema.routines
    where routine_schema = 'public'
      and routine_name in ('ac_redeem_code', 'ac_admin_create_codes', 'ac_random_code')) as functions_total,
  public.ac_random_code() as sample_code;


-- ############################################################################
-- ##  القسم: 15_assets_v2.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 15) أصول الواجهة الجديدة (استبدال كامل)
--
--  يولّده tools/build_assets_sql.py من assets-manifest.json — لا تعدّله يدوياً.
--
--  المحتوى: 123 أفاتاراً · 193 ملصقاً · 176 أداة  =  492 أصلاً
--  منها 214 ملفاً متحرّكاً (GIF) يعمل داخل التطبيق مع المعاينات.
--
--  ⚠️ هذا الملف **يحذف كل الأصول القديمة** (أفاتارات/ملصقات/أدوات) ثم يزرع الجديدة.
--     الملفات نفسها تُرفع إلى مخزن site-assets عبر سير عمل GitHub:
--        Actions → Upload new assets → Run workflow → mode = upload
--     (السير ينزّل الأرشيف من المستودع العام ويوزّعه على الأقسام ثم يرفعه)
--
--  الأقسام: كل مجلد من مجلداتك صار قسماً مستقلاً في التطبيق.
--  آمن لإعادة التنفيذ (idempotent).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) عمودا القسم: يجعل ورقة الأصول تُعرض في فقرات منفصلة
-- ---------------------------------------------------------------------------
alter table public.site_assets add column if not exists collection text;
alter table public.site_assets add column if not exists collection_order int not null default 0;
create index if not exists site_assets_collection_idx
  on public.site_assets(asset_type, collection_order, sort_order);

-- ---------------------------------------------------------------------------
-- 2) حذف كل الأصول القديمة وحزم الملصقات القديمة
-- ---------------------------------------------------------------------------
delete from public.site_assets;
delete from public.sticker_packs;

-- فهرس يحمي من التكرار عند إعادة التنفيذ (يُنشأ بعد التنظيف كي لا يفشل)
create unique index if not exists site_assets_file_url_uidx on public.site_assets (file_url);

-- ---------------------------------------------------------------------------
-- 2.5) صلاحيات دور service_role — حتى يستطيع سير الرفع الآلي كتابة الأصول
--      (يقابل الخطوة 4.5 في supabase/11_grants.sql)
-- ---------------------------------------------------------------------------
grant usage on schema public to service_role;
grant select, insert, update, delete on public.site_assets to service_role;
grant select, insert, update, delete on public.sticker_packs to service_role;

-- ---------------------------------------------------------------------------
-- 3) حزم الملصقات الجديدة (كل قسم ملصقات = حزمة)
-- ---------------------------------------------------------------------------
insert into public.sticker_packs (id, name, icon_url, sort_order, active)
values
  ('memes', 'Memes', '/site-assets/stickers/memes/file-2346084.gif', 12, true),
  ('minecraft-memes-1', 'Minecraft Memes 1', '/site-assets/stickers/minecraft-memes-1/file-2345951.gif', 13, true),
  ('minecraft-memes-2', 'Minecraft Memes 2', '/site-assets/stickers/minecraft-memes-2/file-2346211.gif', 14, true),
  ('minecraft-memes-3', 'Minecraft Memes 3', '/site-assets/stickers/minecraft-memes-3/file-2346190.png', 15, true),
  ('minecraft-movie', 'Minecraft Movie', '/site-assets/stickers/minecraft-movie/file-2346184.gif', 16, true),
  ('top', 'Top', '/site-assets/stickers/top/1164-estudia-sonso.png', 17, true)
on conflict (id) do update
  set name = excluded.name,
      icon_url = excluded.icon_url,
      sort_order = excluded.sort_order,
      active = excluded.active;

-- ---------------------------------------------------------------------------
-- 4) الأفاتارات — 123 أصلاً في 11 قسماً
-- ---------------------------------------------------------------------------
insert into public.site_assets
  (asset_type, name, file_url, sticker_pack, required_plan, animated, collection, collection_order, sort_order)
values
  ('avatar', 'Messi X Minecraft', 'profiles/pfp/messi-x-minecraft.jpg', null, 'free', false, 'PFP', 1, 1),
  ('avatar', 'Ronaldo In Every Timeline And Somehow Still The Main', 'profiles/pfp/ronaldo-in-every-timeline-and-somehow-still-the-.png', null, 'free', false, 'PFP', 1, 2),
  ('avatar', 'Ronaldo In Every Timeline And Somehow Still The Main 2', 'profiles/pfp/ronaldo-in-every-timeline-and-somehow-still--2.png', null, 'free', false, 'PFP', 1, 3),
  ('avatar', 'PFP 4', 'profiles/pfp/download-1.jpg', null, 'free', false, 'PFP', 1, 4),
  ('avatar', 'PFP 5', 'profiles/pfp/download-11.jpg', null, 'free', false, 'PFP', 1, 5),
  ('avatar', 'PFP 6', 'profiles/pfp/download-12.jpg', null, 'free', false, 'PFP', 1, 6),
  ('avatar', 'PFP 7', 'profiles/pfp/download-121.jpg', null, 'free', false, 'PFP', 1, 7),
  ('avatar', 'PFP 8', 'profiles/pfp/download-13.jpg', null, 'free', false, 'PFP', 1, 8),
  ('avatar', 'PFP 9', 'profiles/pfp/download-14.jpg', null, 'free', false, 'PFP', 1, 9),
  ('avatar', 'PFP 10', 'profiles/pfp/download-6.jpg', null, 'free', false, 'PFP', 1, 10),
  ('avatar', 'PFP 11', 'profiles/pfp/download-7.jpg', null, 'free', false, 'PFP', 1, 11),
  ('avatar', 'PFP 12', 'profiles/pfp/download-8.jpg', null, 'free', false, 'PFP', 1, 12),
  ('avatar', 'PFP 13', 'profiles/pfp/download-9.jpg', null, 'free', false, 'PFP', 1, 13),
  ('avatar', 'PFP 14', 'profiles/pfp/img.jpg', null, 'free', false, 'PFP', 1, 14),
  ('avatar', 'Aelx With Theboat', 'profiles/profiles/aelx-with-theboat.jpg', null, 'free', false, 'Profiles', 2, 15),
  ('avatar', 'Axolotl Aquarium', 'profiles/profiles/axolotl-aquarium.jpg', null, 'free', false, 'Profiles', 2, 16),
  ('avatar', 'Cheems Doge Minecraft', 'profiles/profiles/cheems-doge-minecraft.jpg', null, 'free', false, 'Profiles', 2, 17),
  ('avatar', 'Cherry Blossom Biom', 'profiles/profiles/cherry-blossom-biom.jpg', null, 'free', false, 'Profiles', 2, 18),
  ('avatar', 'Cozy Furnace Corner', 'profiles/profiles/cozy-furnace-corner.jpg', null, 'free', false, 'Profiles', 2, 19),
  ('avatar', 'Cute Enderman Girl', 'profiles/profiles/cute-enderman-girl.jpg', null, 'free', false, 'Profiles', 2, 20),
  ('avatar', 'Cute Girl', 'profiles/profiles/cute-girl.jpg', null, 'free', false, 'Profiles', 2, 21),
  ('avatar', 'Diamond Armor Glow', 'profiles/profiles/diamond-armor-glow.jpg', null, 'free', false, 'Profiles', 2, 22),
  ('avatar', 'Giant Minecraft Cookie', 'profiles/profiles/giant-minecraft-cookie.jpg', null, 'free', false, 'Profiles', 2, 23),
  ('avatar', 'Gigachad Doge Minecraft', 'profiles/profiles/gigachad-doge-minecraft.jpg', null, 'free', false, 'Profiles', 2, 24),
  ('avatar', 'Golden Helmet Steve', 'profiles/profiles/golden-helmet-steve.jpg', null, 'free', false, 'Profiles', 2, 25),
  ('avatar', 'Lush Cave Axolotls', 'profiles/profiles/lush-cave-axolotls.jpg', null, 'free', false, 'Profiles', 2, 26),
  ('avatar', 'Minecraft Bed Explosion', 'profiles/profiles/minecraft-bed-explosion.jpg', null, 'free', false, 'Profiles', 2, 27),
  ('avatar', 'Minecraft Cat Meme', 'profiles/profiles/minecraft-cat-meme.jpg', null, 'free', false, 'Profiles', 2, 28),
  ('avatar', 'Minecraft Cute Wolf', 'profiles/profiles/minecraft-cute-wolf.jpg', null, 'free', false, 'Profiles', 2, 29),
  ('avatar', 'Minecraft Music Disc', 'profiles/profiles/minecraft-music-disc.jpg', null, 'free', false, 'Profiles', 2, 30),
  ('avatar', 'Minecraft Rose Painting', 'profiles/profiles/minecraft-rose-painting.jpg', null, 'free', false, 'Profiles', 2, 31),
  ('avatar', 'Minecraft Warden Cave', 'profiles/profiles/minecraft-warden-cave.jpg', null, 'free', false, 'Profiles', 2, 32),
  ('avatar', 'Netherite Armor Steve', 'profiles/profiles/netherite-armor-steve.jpg', null, 'free', false, 'Profiles', 2, 33),
  ('avatar', 'Netherite Warrior Spear', 'profiles/profiles/netherite-warrior-spear.jpg', null, 'free', false, 'Profiles', 2, 34),
  ('avatar', 'Steve Mining Creeper', 'profiles/profiles/steve-mining-creeper.jpg', null, 'free', false, 'Profiles', 2, 35),
  ('avatar', 'Tall Enderman Girl', 'profiles/profiles/tall-enderman-girl.jpg', null, 'free', false, 'Profiles', 2, 36),
  ('avatar', 'Waguri Chan Is Minecraft', 'profiles/profiles/waguri-chan-is-minecraft.jpg', null, 'free', false, 'Profiles', 2, 37),
  ('avatar', 'Zombie Climbing Ladder', 'profiles/profiles/zombie-climbing-ladder.jpg', null, 'free', false, 'Profiles', 2, 38),
  ('avatar', 'Default Profile', 'profiles/profiles/default-profile.jpg', null, 'free', false, 'Profiles', 2, 39),
  ('avatar', 'Onefree', 'profiles/profiles/onefree.png', null, 'free', false, 'Profiles', 2, 40),
  ('avatar', 'Steve Minecraft', 'profiles/pack-one/1001-steve-minecraft.png', null, 'free', false, 'Pack One', 3, 41),
  ('avatar', 'Betty Grof', 'profiles/pack-one/1566-betty-grof.png', null, 'free', false, 'Pack One', 3, 42),
  ('avatar', 'Bear', 'profiles/pack-one/21980-198233-bear.jpg', null, 'free', false, 'Pack One', 3, 43),
  ('avatar', 'Gta San Andreas Profile Icon', 'profiles/pack-one/2670-gta-san-andreas-profile-icon.png', null, 'free', false, 'Pack One', 3, 44),
  ('avatar', 'Jake The Dog', 'profiles/pack-one/3426-jake-the-dog.png', null, 'free', false, 'Pack One', 3, 45),
  ('avatar', 'Dont Come To School Tmr', 'profiles/pack-one/38092-dont-come-to-school-tmr.gif', null, 'free', true, 'Pack One', 3, 46),
  ('avatar', 'Wrench', 'profiles/pack-one/42118-wrench.png', null, 'free', false, 'Pack One', 3, 47),
  ('avatar', 'Kilua', 'profiles/pack-one/50466-262878-kilua.jpg', null, 'free', false, 'Pack One', 3, 48),
  ('avatar', 'Homer', 'profiles/pack-one/50524-329821-homer.jpg', null, 'free', false, 'Pack One', 3, 49),
  ('avatar', 'Newminecraftmoviesticker', 'profiles/pack-one/54945-newminecraftmoviesticker.png', null, 'free', false, 'Pack One', 3, 50),
  ('avatar', 'Gunter', 'profiles/pack-one/5812-gunter.png', null, 'free', false, 'Pack One', 3, 51),
  ('avatar', 'Sleeping Knight', 'profiles/pack-one/6430-788111-sleeping-knight.jpg', null, 'free', false, 'Pack One', 3, 52),
  ('avatar', 'Steve Minecraft Profile', 'profiles/pack-one/6434-steve-minecraft-profile-image.png', null, 'free', false, 'Pack One', 3, 53),
  ('avatar', 'Adesivo W', 'profiles/pack-one/67695-93653-adesivo-w.png', null, 'free', false, 'Pack One', 3, 54),
  ('avatar', 'Wild', 'profiles/pack-one/7062-101939-wild.png', null, 'free', false, 'Pack One', 3, 55),
  ('avatar', 'Earl Of Lemongrab', 'profiles/pack-one/7522-earl-of-lemongrab.png', null, 'free', false, 'Pack One', 3, 56),
  ('avatar', 'Eridan Ampora', 'profiles/pack-one/8260-eridan-ampora.png', null, 'free', false, 'Pack One', 3, 57),
  ('avatar', 'Flame Princess', 'profiles/pack-one/8723-flame-princess.png', null, 'free', false, 'Pack One', 3, 58),
  ('avatar', 'Huntress Wizard', 'profiles/pack-one/8921-huntress-wizard.png', null, 'free', false, 'Pack One', 3, 59),
  ('avatar', 'Best Cartoon', 'profiles/best-cartoon/1566-best-cartoon-pfp.png', null, 'epic', false, 'Best Cartoon', 4, 60),
  ('avatar', 'Best Cartoon 2', 'profiles/best-cartoon/3280-best-cartoon.png', null, 'epic', false, 'Best Cartoon', 4, 61),
  ('avatar', 'Best Cartoon 3', 'profiles/best-cartoon/3531-best-cartoon.png', null, 'epic', false, 'Best Cartoon', 4, 62),
  ('avatar', 'Best Cartoon 4', 'profiles/best-cartoon/3929-best-cartoon.png', null, 'epic', false, 'Best Cartoon', 4, 63),
  ('avatar', 'Best Cartoon 5', 'profiles/best-cartoon/5800-best-cartoon.png', null, 'epic', false, 'Best Cartoon', 4, 64),
  ('avatar', 'Best Cartoon User Picture', 'profiles/best-cartoon/6994-best-cartoon-user-picture.png', null, 'epic', false, 'Best Cartoon', 4, 65),
  ('avatar', 'Best Cartoon Display Picture', 'profiles/best-cartoon/7730-best-cartoon-display-picture.png', null, 'epic', false, 'Best Cartoon', 4, 66),
  ('avatar', 'Cartoon Meme Icon', 'profiles/cartoon-meme/1080-cartoon-meme-icon.png', null, 'epic', false, 'Cartoon Meme', 5, 67),
  ('avatar', 'Cartoon Meme Profile', 'profiles/cartoon-meme/1587-cartoon-meme-profile-avatar.png', null, 'epic', false, 'Cartoon Meme', 5, 68),
  ('avatar', 'Cartoon Meme', 'profiles/cartoon-meme/1879-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 69),
  ('avatar', 'Cartoon Meme 2', 'profiles/cartoon-meme/4379-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 70),
  ('avatar', 'Cartoon Meme 3', 'profiles/cartoon-meme/4408-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 71),
  ('avatar', 'Cartoon Meme User Icon', 'profiles/cartoon-meme/6186-cartoon-meme-user-icon.png', null, 'epic', false, 'Cartoon Meme', 5, 72),
  ('avatar', 'Cartoon Meme 4', 'profiles/cartoon-meme/6727-cartoon-meme-avatar.png', null, 'epic', false, 'Cartoon Meme', 5, 73),
  ('avatar', 'Cartoon Meme 5', 'profiles/cartoon-meme/8532-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 74),
  ('avatar', 'Cartoon Meme 6', 'profiles/cartoon-meme/9478-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 75),
  ('avatar', 'Cartoon Meme Profile Picture', 'profiles/cartoon-meme/9489-cartoon-meme-profile-picture.png', null, 'epic', false, 'Cartoon Meme', 5, 76),
  ('avatar', 'Cartoon Meme 7', 'profiles/cartoon-meme/9609-cartoon-meme.png', null, 'epic', false, 'Cartoon Meme', 5, 77),
  ('avatar', 'Cool Cartoon', 'profiles/cool-cartoon/2959-cool-cartoon.png', null, 'epic', false, 'Cool Cartoon', 6, 78),
  ('avatar', 'Tuff', 'profiles/cool-cartoon/30580-574982-tuff.gif', null, 'epic', true, 'Cool Cartoon', 6, 79),
  ('avatar', 'Tuff 2', 'profiles/cool-cartoon/34082-945666-tuff.jpg', null, 'epic', false, 'Cool Cartoon', 6, 80),
  ('avatar', 'Cool Cartoon 2', 'profiles/cool-cartoon/4676-cool-cartoon.png', null, 'epic', false, 'Cool Cartoon', 6, 81),
  ('avatar', 'Cool Cartoon 3', 'profiles/cool-cartoon/6283-cool-cartoon.png', null, 'epic', false, 'Cool Cartoon', 6, 82),
  ('avatar', 'Cool Cartoon 4', 'profiles/cool-cartoon/6813-cool-cartoon.png', null, 'epic', false, 'Cool Cartoon', 6, 83),
  ('avatar', 'Alone', 'profiles/call-of-duty/2170-alone.gif', null, 'epic', true, 'Call Of Duty', 7, 84),
  ('avatar', 'Call Of Duty Profile Icon', 'profiles/call-of-duty/2957-call-of-duty-profile-icon.png', null, 'epic', false, 'Call Of Duty', 7, 85),
  ('avatar', 'Call Of Duty', 'profiles/call-of-duty/4439-call-of-duty.png', null, 'epic', false, 'Call Of Duty', 7, 86),
  ('avatar', 'Alone 2', 'profiles/call-of-duty/5256-alone.gif', null, 'epic', true, 'Call Of Duty', 7, 87),
  ('avatar', 'Cashi', 'profiles/call-of-duty/5258-cashi.png', null, 'epic', false, 'Call Of Duty', 7, 88),
  ('avatar', 'Call Of Duty 2', 'profiles/call-of-duty/5740-call-of-duty.png', null, 'epic', false, 'Call Of Duty', 7, 89),
  ('avatar', 'Call Of Duty 3', 'profiles/call-of-duty/5971-call-of-duty.png', null, 'epic', false, 'Call Of Duty', 7, 90),
  ('avatar', 'Call Of Duty Profile', 'profiles/call-of-duty/6804-call-of-duty-profile-image.png', null, 'epic', false, 'Call Of Duty', 7, 91),
  ('avatar', 'Spooky', 'profiles/call-of-duty/8892-spooky.png', null, 'epic', false, 'Call Of Duty', 7, 92),
  ('avatar', 'Call Of Duty 4', 'profiles/call-of-duty/9893-call-of-duty.png', null, 'epic', false, 'Call Of Duty', 7, 93),
  ('avatar', 'Default', 'profiles/sad/22162-default.jpeg', null, 'epic', false, 'Sad', 8, 94),
  ('avatar', 'Matching', 'profiles/sad/25792-matching-pfp.png', null, 'epic', false, 'Sad', 8, 95),
  ('avatar', 'Supra', 'profiles/sad/28112-supra.jpeg', null, 'epic', false, 'Sad', 8, 96),
  ('avatar', 'This Bitch Bites', 'profiles/sad/36619-this-bitch-bites-sticker.png', null, 'epic', false, 'Sad', 8, 97),
  ('avatar', 'Sad 5', 'profiles/sad/40541-pfp.jpeg', null, 'epic', false, 'Sad', 8, 98),
  ('avatar', 'Spectacular Spider Man Icon', 'profiles/sad/46321-spectacular-spider-man-icon-pfp.jpeg', null, 'epic', false, 'Sad', 8, 99),
  ('avatar', 'Smoking Dog', 'profiles/sad/48232-smoking-dog.jpeg', null, 'epic', false, 'Sad', 8, 100),
  ('avatar', 'Xmas Spiderman Black Amp White Spiderman', 'profiles/sad/51539-xmas-spiderman-black-amp-white-spiderman.jpeg', null, 'epic', false, 'Sad', 8, 101),
  ('avatar', 'Lady Art Hd', 'profiles/sad/88067-lady-art-hd.jpeg', null, 'epic', false, 'Sad', 8, 102),
  ('avatar', 'Spectacular Spider Man Icon 2', 'profiles/sad/98325-spectacular-spider-man-icon-pfp.jpeg', null, 'epic', false, 'Sad', 8, 103),
  ('avatar', 'Kay Flock Money', 'profiles/ahh/3295-kay-flock-money.png', null, 'epic', false, 'Ahh', 9, 104),
  ('avatar', 'Holding Money', 'profiles/ahh/4013-holding-money.png', null, 'epic', false, 'Ahh', 9, 105),
  ('avatar', 'Money And Guns', 'profiles/ahh/5808-money-and-guns.png', null, 'epic', false, 'Ahh', 9, 106),
  ('avatar', 'Aesthetic Money', 'profiles/ahh/5831-aesthetic-money.png', null, 'epic', false, 'Ahh', 9, 107),
  ('avatar', 'Juice Wrld', 'profiles/ahh/79202-juice-wrld-gif.gif', null, 'epic', true, 'Ahh', 9, 108),
  ('avatar', 'Walter Money', 'profiles/ahh/8564-walter-money.png', null, 'epic', false, 'Ahh', 9, 109),
  ('avatar', 'Com', 'profiles/ahh/98118-com-pfp.jpg', null, 'epic', false, 'Ahh', 9, 110),
  ('avatar', 'Louie Racks', 'profiles/ahh/9876-louie-racks.gif', null, 'epic', true, 'Ahh', 9, 111),
  ('avatar', 'Bolsonaro Rebaixado', 'profiles/moumen/19753-bolsonaro-rebaixado.gif', null, 'legendary', true, 'Moumen', 10, 112),
  ('avatar', 'Alx', 'profiles/moumen/36802-alx.gif', null, 'legendary', true, 'Moumen', 10, 113),
  ('avatar', 'Xmas Spiderman Black Amp White Spiderman 2', 'profiles/moumen/51539-xmas-spiderman-black-amp-white-spiderman.jpeg', null, 'legendary', false, 'Moumen', 10, 114),
  ('avatar', 'Cesar', 'profiles/moumen/6253-cesar.gif', null, 'legendary', true, 'Moumen', 10, 115),
  ('avatar', 'Hey There Chill Vibes', 'profiles/animated/30270-hey-there-chill-vibes.gif', null, 'legendary', true, 'Animated', 11, 116),
  ('avatar', 'Batman Animated', 'profiles/animated/3406-batman-animated.png', null, 'legendary', false, 'Animated', 11, 117),
  ('avatar', 'Angel', 'profiles/animated/34802-angel.jpeg', null, 'legendary', false, 'Animated', 11, 118),
  ('avatar', 'Anime Animated Profile Icon', 'profiles/animated/3602-anime-animated-profile-icon.png', null, 'legendary', false, 'Animated', 11, 119),
  ('avatar', 'Batman Animated 2', 'profiles/animated/5883-batman-animated.png', null, 'legendary', false, 'Animated', 11, 120),
  ('avatar', 'Batman Animated Series', 'profiles/animated/5887-batman-animated-series.png', null, 'legendary', false, 'Animated', 11, 121),
  ('avatar', 'Spokeishere Animation', 'profiles/animated/7515-spokeishere-animation.gif', null, 'legendary', true, 'Animated', 11, 122),
  ('avatar', 'Batman Animated 3', 'profiles/animated/8638-batman-animated.png', null, 'legendary', false, 'Animated', 11, 123)
on conflict (file_url) do update
  set name = excluded.name,
      asset_type = excluded.asset_type,
      sticker_pack = excluded.sticker_pack,
      required_plan = excluded.required_plan,
      animated = excluded.animated,
      collection = excluded.collection,
      collection_order = excluded.collection_order,
      sort_order = excluded.sort_order,
      enabled = true;

-- ---------------------------------------------------------------------------
-- 4) الملصقات — 193 أصلاً في 6 قسماً
-- ---------------------------------------------------------------------------
insert into public.site_assets
  (asset_type, name, file_url, sticker_pack, required_plan, animated, collection, collection_order, sort_order)
values
  ('sticker', 'Memes 1', 'stickers/memes/file-2346084.gif', 'memes', 'free', true, 'Memes', 12, 1),
  ('sticker', 'Memes 2', 'stickers/memes/file-2346085.gif', 'memes', 'free', true, 'Memes', 12, 2),
  ('sticker', 'Memes 3', 'stickers/memes/file-2346086.gif', 'memes', 'free', true, 'Memes', 12, 3),
  ('sticker', 'Memes 4', 'stickers/memes/file-2346087.gif', 'memes', 'free', true, 'Memes', 12, 4),
  ('sticker', 'Memes 5', 'stickers/memes/file-2346088.gif', 'memes', 'free', true, 'Memes', 12, 5),
  ('sticker', 'Memes 6', 'stickers/memes/file-2346089.gif', 'memes', 'free', true, 'Memes', 12, 6),
  ('sticker', 'Memes 7', 'stickers/memes/file-2346090.gif', 'memes', 'free', true, 'Memes', 12, 7),
  ('sticker', 'Memes 8', 'stickers/memes/file-2346091.gif', 'memes', 'free', true, 'Memes', 12, 8),
  ('sticker', 'Memes 9', 'stickers/memes/file-2346093.gif', 'memes', 'free', true, 'Memes', 12, 9),
  ('sticker', 'Memes 10', 'stickers/memes/file-2346094.gif', 'memes', 'free', true, 'Memes', 12, 10),
  ('sticker', 'Memes 11', 'stickers/memes/file-2346095.gif', 'memes', 'free', true, 'Memes', 12, 11),
  ('sticker', 'Memes 12', 'stickers/memes/file-2346097.gif', 'memes', 'free', true, 'Memes', 12, 12),
  ('sticker', 'Memes 13', 'stickers/memes/file-2346098.gif', 'memes', 'free', true, 'Memes', 12, 13),
  ('sticker', 'Memes 14', 'stickers/memes/file-2346099.gif', 'memes', 'free', true, 'Memes', 12, 14),
  ('sticker', 'Memes 15', 'stickers/memes/file-2346100.gif', 'memes', 'free', true, 'Memes', 12, 15),
  ('sticker', 'Memes 16', 'stickers/memes/file-2346101.gif', 'memes', 'free', true, 'Memes', 12, 16),
  ('sticker', 'Memes 17', 'stickers/memes/file-2346102.gif', 'memes', 'free', true, 'Memes', 12, 17),
  ('sticker', 'Memes 18', 'stickers/memes/file-2346103.gif', 'memes', 'free', true, 'Memes', 12, 18),
  ('sticker', 'Memes 19', 'stickers/memes/file-2346104.gif', 'memes', 'free', true, 'Memes', 12, 19),
  ('sticker', 'Memes 20', 'stickers/memes/file-2346105.gif', 'memes', 'free', true, 'Memes', 12, 20),
  ('sticker', 'Memes 21', 'stickers/memes/file-2346106.gif', 'memes', 'free', true, 'Memes', 12, 21),
  ('sticker', 'Memes 22', 'stickers/memes/file-2346107.gif', 'memes', 'free', true, 'Memes', 12, 22),
  ('sticker', 'Memes 23', 'stickers/memes/file-2346108.gif', 'memes', 'free', true, 'Memes', 12, 23),
  ('sticker', 'Memes 24', 'stickers/memes/file-2346109.gif', 'memes', 'free', true, 'Memes', 12, 24),
  ('sticker', 'Memes 25', 'stickers/memes/file-2346110.gif', 'memes', 'free', true, 'Memes', 12, 25),
  ('sticker', 'Memes 26', 'stickers/memes/file-2346111.gif', 'memes', 'free', true, 'Memes', 12, 26),
  ('sticker', 'Memes 27', 'stickers/memes/file-2346112.gif', 'memes', 'free', true, 'Memes', 12, 27),
  ('sticker', 'Memes 28', 'stickers/memes/file-2346113.gif', 'memes', 'free', true, 'Memes', 12, 28),
  ('sticker', 'Memes 29', 'stickers/memes/file-2346114.gif', 'memes', 'free', true, 'Memes', 12, 29),
  ('sticker', 'Memes 30', 'stickers/memes/file-2346115.gif', 'memes', 'free', true, 'Memes', 12, 30),
  ('sticker', 'Memes 31', 'stickers/memes/file-2346116.gif', 'memes', 'free', true, 'Memes', 12, 31),
  ('sticker', 'Memes 32', 'stickers/memes/file-2346117.gif', 'memes', 'free', true, 'Memes', 12, 32),
  ('sticker', 'Memes 33', 'stickers/memes/file-2346118.gif', 'memes', 'free', true, 'Memes', 12, 33),
  ('sticker', 'Memes 34', 'stickers/memes/file-2346119.gif', 'memes', 'free', true, 'Memes', 12, 34),
  ('sticker', 'Memes 35', 'stickers/memes/file-2346120.gif', 'memes', 'free', true, 'Memes', 12, 35),
  ('sticker', 'Memes 36', 'stickers/memes/file-2346121.gif', 'memes', 'free', true, 'Memes', 12, 36),
  ('sticker', 'Memes 37', 'stickers/memes/file-2346122.gif', 'memes', 'free', true, 'Memes', 12, 37),
  ('sticker', 'Memes 38', 'stickers/memes/file-2346124.gif', 'memes', 'free', true, 'Memes', 12, 38),
  ('sticker', 'Memes 39', 'stickers/memes/file-2346125.gif', 'memes', 'free', true, 'Memes', 12, 39),
  ('sticker', 'Memes 40', 'stickers/memes/file-2346126.gif', 'memes', 'free', true, 'Memes', 12, 40),
  ('sticker', 'Memes 41', 'stickers/memes/file-2346127.gif', 'memes', 'free', true, 'Memes', 12, 41),
  ('sticker', 'Memes 42', 'stickers/memes/file-2346128.gif', 'memes', 'free', true, 'Memes', 12, 42),
  ('sticker', 'Memes 43', 'stickers/memes/file-2346129.gif', 'memes', 'free', true, 'Memes', 12, 43),
  ('sticker', 'Memes 44', 'stickers/memes/file-2346130.gif', 'memes', 'free', true, 'Memes', 12, 44),
  ('sticker', 'Memes 45', 'stickers/memes/file-2346131.gif', 'memes', 'free', true, 'Memes', 12, 45),
  ('sticker', 'Memes 46', 'stickers/memes/file-2346132.gif', 'memes', 'free', true, 'Memes', 12, 46),
  ('sticker', 'Memes 47', 'stickers/memes/file-2346133.gif', 'memes', 'free', true, 'Memes', 12, 47),
  ('sticker', 'Minecraft Memes 1 1', 'stickers/minecraft-memes-1/file-2345951.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 48),
  ('sticker', 'Minecraft Memes 1 2', 'stickers/minecraft-memes-1/file-2345952.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 49),
  ('sticker', 'Minecraft Memes 1 3', 'stickers/minecraft-memes-1/file-2345953.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 50),
  ('sticker', 'Minecraft Memes 1 4', 'stickers/minecraft-memes-1/file-2345954.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 51),
  ('sticker', 'Minecraft Memes 1 5', 'stickers/minecraft-memes-1/file-2345955.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 52),
  ('sticker', 'Minecraft Memes 1 6', 'stickers/minecraft-memes-1/file-2345956.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 53),
  ('sticker', 'Minecraft Memes 1 7', 'stickers/minecraft-memes-1/file-2345957.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 54),
  ('sticker', 'Minecraft Memes 1 8', 'stickers/minecraft-memes-1/file-2345958.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 55),
  ('sticker', 'Minecraft Memes 1 9', 'stickers/minecraft-memes-1/file-2345959.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 56),
  ('sticker', 'Minecraft Memes 1 10', 'stickers/minecraft-memes-1/file-2345960.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 57),
  ('sticker', 'Minecraft Memes 1 11', 'stickers/minecraft-memes-1/file-2345961.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 58),
  ('sticker', 'Minecraft Memes 1 12', 'stickers/minecraft-memes-1/file-2345962.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 59),
  ('sticker', 'Minecraft Memes 1 13', 'stickers/minecraft-memes-1/file-2345963.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 60),
  ('sticker', 'Minecraft Memes 1 14', 'stickers/minecraft-memes-1/file-2345964.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 61),
  ('sticker', 'Minecraft Memes 1 15', 'stickers/minecraft-memes-1/file-2345965.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 62),
  ('sticker', 'Minecraft Memes 1 16', 'stickers/minecraft-memes-1/file-2345966.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 63),
  ('sticker', 'Minecraft Memes 1 17', 'stickers/minecraft-memes-1/file-2345967.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 64),
  ('sticker', 'Minecraft Memes 1 18', 'stickers/minecraft-memes-1/file-2345968.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 65),
  ('sticker', 'Minecraft Memes 1 19', 'stickers/minecraft-memes-1/file-2345969.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 66),
  ('sticker', 'Minecraft Memes 1 20', 'stickers/minecraft-memes-1/file-2345970.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 67),
  ('sticker', 'Minecraft Memes 1 21', 'stickers/minecraft-memes-1/file-2345971.gif', 'minecraft-memes-1', 'free', true, 'Minecraft Memes 1', 13, 68),
  ('sticker', 'Minecraft Memes 2 1', 'stickers/minecraft-memes-2/file-2346211.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 69),
  ('sticker', 'Minecraft Memes 2 2', 'stickers/minecraft-memes-2/file-2346214.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 70),
  ('sticker', 'Minecraft Memes 2 3', 'stickers/minecraft-memes-2/file-2346215.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 71),
  ('sticker', 'Minecraft Memes 2 4', 'stickers/minecraft-memes-2/file-2346216.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 72),
  ('sticker', 'Minecraft Memes 2 5', 'stickers/minecraft-memes-2/file-2346218.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 73),
  ('sticker', 'Minecraft Memes 2 6', 'stickers/minecraft-memes-2/file-2346219.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 74),
  ('sticker', 'Minecraft Memes 2 7', 'stickers/minecraft-memes-2/file-2346221.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 75),
  ('sticker', 'Minecraft Memes 2 8', 'stickers/minecraft-memes-2/file-2346222.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 76),
  ('sticker', 'Minecraft Memes 2 9', 'stickers/minecraft-memes-2/file-2346223.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 77),
  ('sticker', 'Minecraft Memes 2 10', 'stickers/minecraft-memes-2/file-2346225.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 78),
  ('sticker', 'Minecraft Memes 2 11', 'stickers/minecraft-memes-2/file-2346226.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 79),
  ('sticker', 'Minecraft Memes 2 12', 'stickers/minecraft-memes-2/file-2346227.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 80),
  ('sticker', 'Minecraft Memes 2 13', 'stickers/minecraft-memes-2/file-2346228.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 81),
  ('sticker', 'Minecraft Memes 2 14', 'stickers/minecraft-memes-2/file-2346229.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 82),
  ('sticker', 'Minecraft Memes 2 15', 'stickers/minecraft-memes-2/file-2346230.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 83),
  ('sticker', 'Minecraft Memes 2 16', 'stickers/minecraft-memes-2/file-2346231.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 84),
  ('sticker', 'Minecraft Memes 2 17', 'stickers/minecraft-memes-2/file-2346232.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 85),
  ('sticker', 'Minecraft Memes 2 18', 'stickers/minecraft-memes-2/file-2346234.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 86),
  ('sticker', 'Minecraft Memes 2 19', 'stickers/minecraft-memes-2/file-2346235.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 87),
  ('sticker', 'Minecraft Memes 2 20', 'stickers/minecraft-memes-2/file-2346236.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 88),
  ('sticker', 'Minecraft Memes 2 21', 'stickers/minecraft-memes-2/file-2346238.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 89),
  ('sticker', 'Minecraft Memes 2 22', 'stickers/minecraft-memes-2/file-2346239.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 90),
  ('sticker', 'Minecraft Memes 2 23', 'stickers/minecraft-memes-2/file-2346240.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 91),
  ('sticker', 'Minecraft Memes 2 24', 'stickers/minecraft-memes-2/file-2346241.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 92),
  ('sticker', 'Minecraft Memes 2 25', 'stickers/minecraft-memes-2/file-2346242.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 93),
  ('sticker', 'Minecraft Memes 2 26', 'stickers/minecraft-memes-2/file-2346243.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 94),
  ('sticker', 'Minecraft Memes 2 27', 'stickers/minecraft-memes-2/file-2346244.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 95),
  ('sticker', 'Minecraft Memes 2 28', 'stickers/minecraft-memes-2/file-2346246.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 96),
  ('sticker', 'Minecraft Memes 2 29', 'stickers/minecraft-memes-2/file-2346247.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 97),
  ('sticker', 'Minecraft Memes 2 30', 'stickers/minecraft-memes-2/file-2346248.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 98),
  ('sticker', 'Minecraft Memes 2 31', 'stickers/minecraft-memes-2/file-2346249.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 99),
  ('sticker', 'Minecraft Memes 2 32', 'stickers/minecraft-memes-2/file-2346250.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 100),
  ('sticker', 'Minecraft Memes 2 33', 'stickers/minecraft-memes-2/file-2346251.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 101),
  ('sticker', 'Minecraft Memes 2 34', 'stickers/minecraft-memes-2/file-2346252.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 102),
  ('sticker', 'Minecraft Memes 2 35', 'stickers/minecraft-memes-2/file-2346253.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 103),
  ('sticker', 'Minecraft Memes 2 36', 'stickers/minecraft-memes-2/file-2346254.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 104),
  ('sticker', 'Minecraft Memes 2 37', 'stickers/minecraft-memes-2/file-2346255.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 105),
  ('sticker', 'Minecraft Memes 2 38', 'stickers/minecraft-memes-2/file-2346257.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 106),
  ('sticker', 'Minecraft Memes 2 39', 'stickers/minecraft-memes-2/file-2346260.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 107),
  ('sticker', 'Minecraft Memes 2 40', 'stickers/minecraft-memes-2/file-2346261.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 108),
  ('sticker', 'Minecraft Memes 2 41', 'stickers/minecraft-memes-2/file-2346262.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 109),
  ('sticker', 'Minecraft Memes 2 42', 'stickers/minecraft-memes-2/file-2346267.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 110),
  ('sticker', 'Minecraft Memes 2 43', 'stickers/minecraft-memes-2/file-2346269.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 111),
  ('sticker', 'Minecraft Memes 2 44', 'stickers/minecraft-memes-2/file-2346270.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 112),
  ('sticker', 'Minecraft Memes 2 45', 'stickers/minecraft-memes-2/file-2346271.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 113),
  ('sticker', 'Minecraft Memes 2 46', 'stickers/minecraft-memes-2/file-2346272.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 114),
  ('sticker', 'Minecraft Memes 2 47', 'stickers/minecraft-memes-2/file-2346273.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 115),
  ('sticker', 'Minecraft Memes 2 48', 'stickers/minecraft-memes-2/file-2346274.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 116),
  ('sticker', 'Minecraft Memes 2 49', 'stickers/minecraft-memes-2/file-2346275.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 117),
  ('sticker', 'Minecraft Memes 2 50', 'stickers/minecraft-memes-2/file-2346276.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 118),
  ('sticker', 'Minecraft Memes 2 51', 'stickers/minecraft-memes-2/file-2346277.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 119),
  ('sticker', 'Minecraft Memes 2 52', 'stickers/minecraft-memes-2/file-2346278.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 120),
  ('sticker', 'Minecraft Memes 2 53', 'stickers/minecraft-memes-2/file-2346279.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 121),
  ('sticker', 'Minecraft Memes 2 54', 'stickers/minecraft-memes-2/file-2346280.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 122),
  ('sticker', 'Minecraft Memes 2 55', 'stickers/minecraft-memes-2/file-2346281.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 123),
  ('sticker', 'Minecraft Memes 2 56', 'stickers/minecraft-memes-2/file-2346282.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 124),
  ('sticker', 'Minecraft Memes 2 57', 'stickers/minecraft-memes-2/file-2346283.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 125),
  ('sticker', 'Minecraft Memes 2 58', 'stickers/minecraft-memes-2/file-2346284.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 126),
  ('sticker', 'Minecraft Memes 2 59', 'stickers/minecraft-memes-2/file-2346285.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 127),
  ('sticker', 'Minecraft Memes 2 60', 'stickers/minecraft-memes-2/file-2346286.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 128),
  ('sticker', 'Minecraft Memes 2 61', 'stickers/minecraft-memes-2/file-2346287.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 129),
  ('sticker', 'Minecraft Memes 2 62', 'stickers/minecraft-memes-2/file-2346288.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 130),
  ('sticker', 'Minecraft Memes 2 63', 'stickers/minecraft-memes-2/file-2346289.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 131),
  ('sticker', 'Minecraft Memes 2 64', 'stickers/minecraft-memes-2/file-2346290.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 132),
  ('sticker', 'Minecraft Memes 2 65', 'stickers/minecraft-memes-2/file-2346291.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 133),
  ('sticker', 'Minecraft Memes 2 66', 'stickers/minecraft-memes-2/file-2346292.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 134),
  ('sticker', 'Minecraft Memes 2 67', 'stickers/minecraft-memes-2/file-2346293.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 135),
  ('sticker', 'Minecraft Memes 2 68', 'stickers/minecraft-memes-2/file-2346294.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 136),
  ('sticker', 'Minecraft Memes 2 69', 'stickers/minecraft-memes-2/file-2346295.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 137),
  ('sticker', 'Minecraft Memes 2 70', 'stickers/minecraft-memes-2/file-2346296.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 138),
  ('sticker', 'Minecraft Memes 2 71', 'stickers/minecraft-memes-2/file-2346297.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 139),
  ('sticker', 'Minecraft Memes 2 72', 'stickers/minecraft-memes-2/file-2346298.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 140),
  ('sticker', 'Minecraft Memes 2 73', 'stickers/minecraft-memes-2/file-2346299.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 141),
  ('sticker', 'Minecraft Memes 2 74', 'stickers/minecraft-memes-2/file-2346300.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 142),
  ('sticker', 'Minecraft Memes 2 75', 'stickers/minecraft-memes-2/file-2346301.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 143),
  ('sticker', 'Minecraft Memes 2 76', 'stickers/minecraft-memes-2/file-2346302.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 144),
  ('sticker', 'Minecraft Memes 2 77', 'stickers/minecraft-memes-2/file-2346303.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 145),
  ('sticker', 'Minecraft Memes 2 78', 'stickers/minecraft-memes-2/file-2346304.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 146),
  ('sticker', 'Minecraft Memes 2 79', 'stickers/minecraft-memes-2/file-2346305.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 147),
  ('sticker', 'Minecraft Memes 2 80', 'stickers/minecraft-memes-2/file-2346306.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 148),
  ('sticker', 'Minecraft Memes 2 81', 'stickers/minecraft-memes-2/file-2346307.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 149),
  ('sticker', 'Minecraft Memes 2 82', 'stickers/minecraft-memes-2/file-2346308.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 150),
  ('sticker', 'Minecraft Memes 2 83', 'stickers/minecraft-memes-2/file-2346312.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 151),
  ('sticker', 'Minecraft Memes 2 84', 'stickers/minecraft-memes-2/file-2346313.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 152),
  ('sticker', 'Minecraft Memes 2 85', 'stickers/minecraft-memes-2/file-2346314.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 153),
  ('sticker', 'Minecraft Memes 2 86', 'stickers/minecraft-memes-2/file-2346319.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 154),
  ('sticker', 'Minecraft Memes 2 87', 'stickers/minecraft-memes-2/file-2346320.gif', 'minecraft-memes-2', 'free', true, 'Minecraft Memes 2', 14, 155),
  ('sticker', 'Minecraft Memes 3 1', 'stickers/minecraft-memes-3/file-2346190.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 156),
  ('sticker', 'Minecraft Memes 3 2', 'stickers/minecraft-memes-3/file-2346191.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 157),
  ('sticker', 'Minecraft Memes 3 3', 'stickers/minecraft-memes-3/file-2346192.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 158),
  ('sticker', 'Minecraft Memes 3 4', 'stickers/minecraft-memes-3/file-2346193.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 159),
  ('sticker', 'Minecraft Memes 3 5', 'stickers/minecraft-memes-3/file-2346194.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 160),
  ('sticker', 'Minecraft Memes 3 6', 'stickers/minecraft-memes-3/file-2346195.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 161),
  ('sticker', 'Minecraft Memes 3 7', 'stickers/minecraft-memes-3/file-2346196.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 162),
  ('sticker', 'Minecraft Memes 3 8', 'stickers/minecraft-memes-3/file-2346197.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 163),
  ('sticker', 'Minecraft Memes 3 9', 'stickers/minecraft-memes-3/file-2346198.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 164),
  ('sticker', 'Minecraft Memes 3 10', 'stickers/minecraft-memes-3/file-2346199.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 165),
  ('sticker', 'Minecraft Memes 3 11', 'stickers/minecraft-memes-3/file-2346200.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 166),
  ('sticker', 'Minecraft Memes 3 12', 'stickers/minecraft-memes-3/file-2346201.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 167),
  ('sticker', 'Minecraft Memes 3 13', 'stickers/minecraft-memes-3/file-2346202.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 168),
  ('sticker', 'Minecraft Memes 3 14', 'stickers/minecraft-memes-3/file-2346203.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 169),
  ('sticker', 'Minecraft Memes 3 15', 'stickers/minecraft-memes-3/file-2346204.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 170),
  ('sticker', 'Minecraft Memes 3 16', 'stickers/minecraft-memes-3/file-2346205.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 171),
  ('sticker', 'Minecraft Memes 3 17', 'stickers/minecraft-memes-3/file-2346206.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 172),
  ('sticker', 'Minecraft Memes 3 18', 'stickers/minecraft-memes-3/file-2346207.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 173),
  ('sticker', 'Minecraft Memes 3 19', 'stickers/minecraft-memes-3/file-2346208.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 174),
  ('sticker', 'Minecraft Memes 3 20', 'stickers/minecraft-memes-3/file-2346209.png', 'minecraft-memes-3', 'free', false, 'Minecraft Memes 3', 15, 175),
  ('sticker', 'Minecraft Movie 1', 'stickers/minecraft-movie/file-2346184.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 176),
  ('sticker', 'Minecraft Movie 2', 'stickers/minecraft-movie/file-2346185.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 177),
  ('sticker', 'Minecraft Movie 3', 'stickers/minecraft-movie/file-2346186.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 178),
  ('sticker', 'Minecraft Movie 4', 'stickers/minecraft-movie/file-2346187.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 179),
  ('sticker', 'Minecraft Movie 5', 'stickers/minecraft-movie/file-2346188.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 180),
  ('sticker', 'Minecraft Movie 6', 'stickers/minecraft-movie/file-2346189.gif', 'minecraft-movie', 'free', true, 'Minecraft Movie', 16, 181),
  ('sticker', 'Estudia Sonso', 'stickers/top/1164-estudia-sonso.png', 'top', 'free', false, 'Top', 17, 182),
  ('sticker', 'Turn The Lights Of', 'stickers/top/2218-turn-the-lights-of.gif', 'top', 'free', true, 'Top', 17, 183),
  ('sticker', 'Paul Surprised', 'stickers/top/2731-paul-surprised.png', 'top', 'free', false, 'Top', 17, 184),
  ('sticker', 'Chillmyguy', 'stickers/top/3729-chillmyguy.png', 'top', 'free', false, 'Top', 17, 185),
  ('sticker', 'Goofy Dog Swing', 'stickers/top/5341-goofy-dog-swing.png', 'top', 'free', false, 'Top', 17, 186),
  ('sticker', 'Cute Cat', 'stickers/top/5855-cute-cat-24.png', 'top', 'free', false, 'Top', 17, 187),
  ('sticker', 'Top 7', 'stickers/top/7181.png', 'top', 'free', false, 'Top', 17, 188),
  ('sticker', 'Cat Smirk', 'stickers/top/7372-cat-smirk.png', 'top', 'free', false, 'Top', 17, 189),
  ('sticker', 'Gal', 'stickers/top/8625-gal.png', 'top', 'free', false, 'Top', 17, 190),
  ('sticker', 'Cute Cat 2', 'stickers/top/8721-cute-cat-4.png', 'top', 'free', false, 'Top', 17, 191),
  ('sticker', 'Puppet Dance', 'stickers/top/88180-puppet-dance.png', 'top', 'free', false, 'Top', 17, 192),
  ('sticker', 'Shocker', 'stickers/top/9037-shocker.png', 'top', 'free', false, 'Top', 17, 193)
on conflict (file_url) do update
  set name = excluded.name,
      asset_type = excluded.asset_type,
      sticker_pack = excluded.sticker_pack,
      required_plan = excluded.required_plan,
      animated = excluded.animated,
      collection = excluded.collection,
      collection_order = excluded.collection_order,
      sort_order = excluded.sort_order,
      enabled = true;

-- ---------------------------------------------------------------------------
-- 4) الأدوات — 176 أصلاً في 4 قسماً
-- ---------------------------------------------------------------------------
insert into public.site_assets
  (asset_type, name, file_url, sticker_pack, required_plan, animated, collection, collection_order, sort_order)
values
  ('item', 'Minecraft', 'items/minecraft-item/1491-minecraft.png', null, 'legendary', false, 'Minecraft Items', 18, 1),
  ('item', 'Minecraft Wayfinder Armour Trim', 'items/minecraft-item/1571-minecraft-wayfinder-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 2),
  ('item', 'Cake', 'items/minecraft-item/174095-cake.png', null, 'legendary', false, 'Minecraft Items', 18, 3),
  ('item', 'Minecraft Vex Armour Trim', 'items/minecraft-item/1953-minecraft-vex-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 4),
  ('item', 'Minecraft Coast Armour Trim', 'items/minecraft-item/1958-minecraft-coast-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 5),
  ('item', 'Irongolem', 'items/minecraft-item/20756-irongolem.gif', null, 'legendary', true, 'Minecraft Items', 18, 6),
  ('item', 'Minecraft Sentry Armour Trim', 'items/minecraft-item/2323-minecraft-sentry-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 7),
  ('item', 'Villager', 'items/minecraft-item/241123-villager.png', null, 'legendary', false, 'Minecraft Items', 18, 8),
  ('item', 'Enchanted Netherite Shovel', 'items/minecraft-item/2431-enchanted-netherite-shovel.gif', null, 'legendary', true, 'Minecraft Items', 18, 9),
  ('item', 'Minecraftbee', 'items/minecraft-item/2449-minecraftbee.gif', null, 'legendary', true, 'Minecraft Items', 18, 10),
  ('item', 'Dancingminecraftgrassblock', 'items/minecraft-item/266514-dancingminecraftgrassblock.gif', null, 'legendary', true, 'Minecraft Items', 18, 11),
  ('item', 'Minecraftcat', 'items/minecraft-item/278356-minecraftcat.png', null, 'legendary', false, 'Minecraft Items', 18, 12),
  ('item', 'Enchanted Netherite Hoe', 'items/minecraft-item/3070-enchanted-netherite-hoe.gif', null, 'legendary', true, 'Minecraft Items', 18, 13),
  ('item', 'Minecraft Rib Armour Trim', 'items/minecraft-item/3582-minecraft-rib-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 14),
  ('item', 'Minecraft Ward Armour Trim', 'items/minecraft-item/3869-minecraft-ward-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 15),
  ('item', 'Happy Ghast Mini', 'items/minecraft-item/3953-happy-ghast-mini.png', null, 'legendary', false, 'Minecraft Items', 18, 16),
  ('item', 'Minecraft Spire Armour Trim', 'items/minecraft-item/4652-minecraft-spire-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 17),
  ('item', 'Verity', 'items/minecraft-item/493762-verity.png', null, 'legendary', false, 'Minecraft Items', 18, 18),
  ('item', 'Totem Waddle', 'items/minecraft-item/497753-totem-waddle.gif', null, 'legendary', true, 'Minecraft Items', 18, 19),
  ('item', 'Minecraft Silence Armour Trim', 'items/minecraft-item/5101-minecraft-silence-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 20),
  ('item', 'Minecraft Eye Armour Trim', 'items/minecraft-item/5256-minecraft-eye-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 21),
  ('item', 'Minecraft Snout Armour Trim', 'items/minecraft-item/5437-minecraft-snout-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 22),
  ('item', 'Minecraft Raiser Armour Trim', 'items/minecraft-item/5658-minecraft-raiser-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 23),
  ('item', 'Minecraft Tide Armour Trim', 'items/minecraft-item/5877-minecraft-tide-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 24),
  ('item', 'Netheritesword', 'items/minecraft-item/5942-netheritesword.gif', null, 'legendary', true, 'Minecraft Items', 18, 25),
  ('item', 'Dog Pat', 'items/minecraft-item/616373-dog-pat.gif', null, 'legendary', true, 'Minecraft Items', 18, 26),
  ('item', 'Minecraft Host Armour Trim', 'items/minecraft-item/6202-minecraft-host-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 27),
  ('item', 'Furnace', 'items/minecraft-item/621779-furnace.png', null, 'legendary', false, 'Minecraft Items', 18, 28),
  ('item', 'Netheriteaxe', 'items/minecraft-item/6389-netheriteaxe.gif', null, 'legendary', true, 'Minecraft Items', 18, 29),
  ('item', 'Jeb Spinning', 'items/minecraft-item/64587-jeb-spinning.gif', null, 'legendary', true, 'Minecraft Items', 18, 30),
  ('item', 'Chicken Jockey', 'items/minecraft-item/65598-chicken-jockey.png', null, 'legendary', false, 'Minecraft Items', 18, 31),
  ('item', 'Minecraft Bedrock', 'items/minecraft-item/6928-minecraft-bedrock.png', null, 'legendary', false, 'Minecraft Items', 18, 32),
  ('item', 'Minecraftcamperfire', 'items/minecraft-item/711628-minecraftcamperfire.gif', null, 'legendary', true, 'Minecraft Items', 18, 33),
  ('item', 'Minecraft Wild Armour Trim', 'items/minecraft-item/7672-minecraft-wild-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 34),
  ('item', 'Pet The Frog', 'items/minecraft-item/77742-pet-the-frog.gif', null, 'legendary', true, 'Minecraft Items', 18, 35),
  ('item', 'Netheritechestplate', 'items/minecraft-item/8309-netheritechestplate.png', null, 'legendary', false, 'Minecraft Items', 18, 36),
  ('item', 'Piglindance', 'items/minecraft-item/83137-piglindance.gif', null, 'legendary', true, 'Minecraft Items', 18, 37),
  ('item', 'Netheritepickaxe', 'items/minecraft-item/8394-netheritepickaxe.gif', null, 'legendary', true, 'Minecraft Items', 18, 38),
  ('item', 'Minecraft Shaper Armour Trim', 'items/minecraft-item/8485-minecraft-shaper-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 39),
  ('item', 'Minecraft Dune Armour Trim', 'items/minecraft-item/8730-minecraft-dune-armour-trim.png', null, 'legendary', false, 'Minecraft Items', 18, 40),
  ('item', 'Parrotvibe', 'items/minecraft-item/8969-parrotvibe.gif', null, 'legendary', true, 'Minecraft Items', 18, 41),
  ('item', 'Villagehero', 'items/minecraft-item/960733-villagehero.gif', null, 'legendary', true, 'Minecraft Items', 18, 42),
  ('item', 'Trashyaxolotl', 'items/minecraft-item/9648-trashyaxolotl.png', null, 'legendary', false, 'Minecraft Items', 18, 43),
  ('item', 'Steve', 'items/minecraft-item/971370-steve.png', null, 'legendary', false, 'Minecraft Items', 18, 44),
  ('item', 'Minecraftdiamond', 'items/minecraft-item/97567-minecraftdiamond.png', null, 'legendary', false, 'Minecraft Items', 18, 45),
  ('item', 'Enchanted Bedrock', 'items/minecraft-item/enchanted-bedrock.gif', null, 'legendary', true, 'Minecraft Items', 18, 46),
  ('item', 'Amethyst Shard', 'items/minecraft-item/amethyst-shard.png', null, 'legendary', false, 'Minecraft Items', 18, 47),
  ('item', 'Apple', 'items/minecraft-item/apple.png', null, 'legendary', false, 'Minecraft Items', 18, 48),
  ('item', 'Arrow', 'items/minecraft-item/arrow.png', null, 'legendary', false, 'Minecraft Items', 18, 49),
  ('item', 'Axolotl Bucket', 'items/minecraft-item/axolotl-bucket.png', null, 'legendary', false, 'Minecraft Items', 18, 50),
  ('item', 'Bone', 'items/minecraft-item/bone.png', null, 'legendary', false, 'Minecraft Items', 18, 51),
  ('item', 'Brick', 'items/minecraft-item/brick.png', null, 'legendary', false, 'Minecraft Items', 18, 52),
  ('item', 'Charcoal', 'items/minecraft-item/charcoal.png', null, 'legendary', false, 'Minecraft Items', 18, 53),
  ('item', 'Clock', 'items/minecraft-item/clock.gif', null, 'legendary', true, 'Minecraft Items', 18, 54),
  ('item', 'Compass', 'items/minecraft-item/compass.gif', null, 'legendary', true, 'Minecraft Items', 18, 55),
  ('item', 'Crossbow Arrow', 'items/minecraft-item/crossbow-arrow.png', null, 'legendary', false, 'Minecraft Items', 18, 56),
  ('item', 'Diamond', 'items/minecraft-item/diamond.png', null, 'legendary', false, 'Minecraft Items', 18, 57),
  ('item', 'Diamond Axe', 'items/minecraft-item/diamond-axe.png', null, 'legendary', false, 'Minecraft Items', 18, 58),
  ('item', 'Diamond Hoe', 'items/minecraft-item/diamond-hoe.png', null, 'legendary', false, 'Minecraft Items', 18, 59),
  ('item', 'Diamond Ore', 'items/minecraft-item/diamond-ore.png', null, 'legendary', false, 'Minecraft Items', 18, 60),
  ('item', 'Diamond Pickaxe', 'items/minecraft-item/diamond-pickaxe.png', null, 'legendary', false, 'Minecraft Items', 18, 61),
  ('item', 'Diamond Shovel', 'items/minecraft-item/diamond-shovel.png', null, 'legendary', false, 'Minecraft Items', 18, 62),
  ('item', 'Diamond Spear', 'items/minecraft-item/diamond-spear.png', null, 'legendary', false, 'Minecraft Items', 18, 63),
  ('item', 'Diamond Sword', 'items/minecraft-item/diamond-sword.png', null, 'legendary', false, 'Minecraft Items', 18, 64),
  ('item', 'Dragon Breath', 'items/minecraft-item/dragon-breath.png', null, 'legendary', false, 'Minecraft Items', 18, 65),
  ('item', 'Echo Shard', 'items/minecraft-item/echo-shard.png', null, 'legendary', false, 'Minecraft Items', 18, 66),
  ('item', 'Experience Bottle', 'items/minecraft-item/experience-bottle.png', null, 'legendary', false, 'Minecraft Items', 18, 67),
  ('item', 'Firework Rocket', 'items/minecraft-item/firework-rocket.png', null, 'legendary', false, 'Minecraft Items', 18, 68),
  ('item', 'Flint And Steel', 'items/minecraft-item/flint-and-steel.png', null, 'legendary', false, 'Minecraft Items', 18, 69),
  ('item', 'Glistering Melon Slice', 'items/minecraft-item/glistering-melon-slice.png', null, 'legendary', false, 'Minecraft Items', 18, 70),
  ('item', 'Glow Berries', 'items/minecraft-item/glow-berries.png', null, 'legendary', false, 'Minecraft Items', 18, 71),
  ('item', 'Honey Bottle', 'items/minecraft-item/honey-bottle.png', null, 'legendary', false, 'Minecraft Items', 18, 72),
  ('item', 'Hopper', 'items/minecraft-item/hopper.png', null, 'legendary', false, 'Minecraft Items', 18, 73),
  ('item', 'Iron Ingot', 'items/minecraft-item/iron-ingot.png', null, 'legendary', false, 'Minecraft Items', 18, 74),
  ('item', 'Iron Pickaxe', 'items/minecraft-item/iron-pickaxe.png', null, 'legendary', false, 'Minecraft Items', 18, 75),
  ('item', 'Iron Shovel', 'items/minecraft-item/iron-shovel.png', null, 'legendary', false, 'Minecraft Items', 18, 76),
  ('item', 'Iron Spear', 'items/minecraft-item/iron-spear.png', null, 'legendary', false, 'Minecraft Items', 18, 77),
  ('item', 'Iron Sword', 'items/minecraft-item/iron-sword.png', null, 'legendary', false, 'Minecraft Items', 18, 78),
  ('item', 'Kelp', 'items/minecraft-item/kelp.png', null, 'legendary', false, 'Minecraft Items', 18, 79),
  ('item', 'Lantern', 'items/minecraft-item/lantern.png', null, 'legendary', false, 'Minecraft Items', 18, 80),
  ('item', 'Lapis Lazuli', 'items/minecraft-item/lapis-lazuli.png', null, 'legendary', false, 'Minecraft Items', 18, 81),
  ('item', 'Lava Bucket', 'items/minecraft-item/lava-bucket.png', null, 'legendary', false, 'Minecraft Items', 18, 82),
  ('item', 'Music Disc', 'items/minecraft-item/music-disc-13.png', null, 'legendary', false, 'Minecraft Items', 18, 83),
  ('item', 'Music Disc Blocks', 'items/minecraft-item/music-disc-blocks.png', null, 'legendary', false, 'Minecraft Items', 18, 84),
  ('item', 'Music Disc Pigstep', 'items/minecraft-item/music-disc-pigstep.png', null, 'legendary', false, 'Minecraft Items', 18, 85),
  ('item', 'Name Tag', 'items/minecraft-item/name-tag.png', null, 'legendary', false, 'Minecraft Items', 18, 86),
  ('item', 'Potato', 'items/minecraft-item/potato.png', null, 'legendary', false, 'Minecraft Items', 18, 87),
  ('item', 'Raw Gold', 'items/minecraft-item/raw-gold.png', null, 'legendary', false, 'Minecraft Items', 18, 88),
  ('item', 'Raw Iron', 'items/minecraft-item/raw-iron.png', null, 'legendary', false, 'Minecraft Items', 18, 89),
  ('item', 'Resin Clump', 'items/minecraft-item/resin-clump.png', null, 'legendary', false, 'Minecraft Items', 18, 90),
  ('item', 'Rotten Flesh', 'items/minecraft-item/rotten-flesh.png', null, 'legendary', false, 'Minecraft Items', 18, 91),
  ('item', 'Shears', 'items/minecraft-item/shears.png', null, 'legendary', false, 'Minecraft Items', 18, 92),
  ('item', 'Slime Ball', 'items/minecraft-item/slime-ball.png', null, 'legendary', false, 'Minecraft Items', 18, 93),
  ('item', 'Snowball', 'items/minecraft-item/snowball.png', null, 'legendary', false, 'Minecraft Items', 18, 94),
  ('item', 'Spider Eye', 'items/minecraft-item/spider-eye.png', null, 'legendary', false, 'Minecraft Items', 18, 95),
  ('item', 'Spyglass', 'items/minecraft-item/spyglass.png', null, 'legendary', false, 'Minecraft Items', 18, 96),
  ('item', 'Totem Of Undying', 'items/minecraft-item/totem-of-undying.png', null, 'legendary', false, 'Minecraft Items', 18, 97),
  ('item', 'Trident', 'items/minecraft-item/trident.png', null, 'legendary', false, 'Minecraft Items', 18, 98),
  ('item', 'Tropical Fish', 'items/minecraft-item/tropical-fish.png', null, 'legendary', false, 'Minecraft Items', 18, 99),
  ('item', 'Wheat', 'items/minecraft-item/wheat.png', null, 'legendary', false, 'Minecraft Items', 18, 100),
  ('item', 'Wheat Seeds', 'items/minecraft-item/wheat-seeds.png', null, 'legendary', false, 'Minecraft Items', 18, 101),
  ('item', 'Wind Charge', 'items/minecraft-item/wind-charge.png', null, 'legendary', false, 'Minecraft Items', 18, 102),
  ('item', 'Writable Book', 'items/minecraft-item/writable-book.png', null, 'legendary', false, 'Minecraft Items', 18, 103),
  ('item', 'Games 1', 'items/games/11160-cs2.png', null, 'legendary', false, 'Games', 19, 104),
  ('item', 'Gtaonlinegreen', 'items/games/121683-gtaonlinegreen.gif', null, 'legendary', true, 'Games', 19, 105),
  ('item', 'Carabine', 'items/games/143795-carabine.png', null, 'legendary', false, 'Games', 19, 106),
  ('item', 'Gamesfortnite', 'items/games/1537-gamesfortnite.png', null, 'legendary', false, 'Games', 19, 107),
  ('item', 'Busted', 'items/games/158215-busted.gif', null, 'legendary', true, 'Games', 19, 108),
  ('item', 'Icono Wow', 'items/games/16458-icono-wow.png', null, 'legendary', false, 'Games', 19, 109),
  ('item', 'Vaultboy', 'items/games/1734-vaultboy.png', null, 'legendary', false, 'Games', 19, 110),
  ('item', 'Rocketleague', 'items/games/1942-rocketleague.png', null, 'legendary', false, 'Games', 19, 111),
  ('item', 'Gtav', 'items/games/2345-gtav.png', null, 'legendary', false, 'Games', 19, 112),
  ('item', 'Deltaforcemobile Logo', 'items/games/288565-deltaforcemobile-logo.png', null, 'legendary', false, 'Games', 19, 113),
  ('item', 'Doginalgamer', 'items/games/331404-doginalgamer.gif', null, 'legendary', true, 'Games', 19, 114),
  ('item', 'Terraria', 'items/games/37237-terraria.png', null, 'legendary', false, 'Games', 19, 115),
  ('item', 'Gamingkitty', 'items/games/410719-gamingkitty.png', null, 'legendary', false, 'Games', 19, 116),
  ('item', 'Los Santos Police Department', 'items/games/44825-los-santos-police-department.png', null, 'legendary', false, 'Games', 19, 117),
  ('item', 'Roblox', 'items/games/4680-roblox.png', null, 'legendary', false, 'Games', 19, 118),
  ('item', 'Gta6', 'items/games/47687-gta6.png', null, 'legendary', false, 'Games', 19, 119),
  ('item', 'Michaellol', 'items/games/501292-michaellol.png', null, 'legendary', false, 'Games', 19, 120),
  ('item', 'Gtabruh', 'items/games/52214-gtabruh.png', null, 'legendary', false, 'Games', 19, 121),
  ('item', 'Valorant', 'items/games/54052-valorant.png', null, 'legendary', false, 'Games', 19, 122),
  ('item', 'Plastic Foods', 'items/games/55178-plastic-foods.png', null, 'legendary', false, 'Games', 19, 123),
  ('item', 'Repo Robot', 'items/games/59526-repo-robot.png', null, 'legendary', false, 'Games', 19, 124),
  ('item', 'Hevysnipermk2', 'items/games/596804-hevysnipermk2.png', null, 'legendary', false, 'Games', 19, 125),
  ('item', 'Gtaonline', 'items/games/600122-gtaonline.gif', null, 'legendary', true, 'Games', 19, 126),
  ('item', 'Gtaonlineheist', 'items/games/600122-gtaonlineheist.gif', null, 'legendary', true, 'Games', 19, 127),
  ('item', 'Lossantoscoffee', 'items/games/613222-lossantoscoffee.png', null, 'legendary', false, 'Games', 19, 128),
  ('item', 'Gta Online', 'items/games/6359-gta-online.png', null, 'legendary', false, 'Games', 19, 129),
  ('item', 'Games 27', 'items/games/6443-7d2d.png', null, 'legendary', false, 'Games', 19, 130),
  ('item', 'Rdo', 'items/games/6517-rdo.png', null, 'legendary', false, 'Games', 19, 131),
  ('item', 'Gtav 2', 'items/games/69927-gtav.png', null, 'legendary', false, 'Games', 19, 132),
  ('item', 'Blackroblox', 'items/games/73590-blackroblox.png', null, 'legendary', false, 'Games', 19, 133),
  ('item', 'Roblox 2', 'items/games/7562-roblox.png', null, 'legendary', false, 'Games', 19, 134),
  ('item', 'Crine', 'items/games/762474-crine.gif', null, 'legendary', true, 'Games', 19, 135),
  ('item', 'Rust Logo', 'items/games/7746-rust-logo.png', null, 'legendary', false, 'Games', 19, 136),
  ('item', 'Sims4', 'items/games/7874-sims4.png', null, 'legendary', false, 'Games', 19, 137),
  ('item', 'Wasted', 'items/games/848019-wasted.png', null, 'legendary', false, 'Games', 19, 138),
  ('item', 'Fobfranklin', 'items/games/860741-fobfranklin.gif', null, 'legendary', true, 'Games', 19, 139),
  ('item', 'Lol', 'items/games/87298-lol.png', null, 'legendary', false, 'Games', 19, 140),
  ('item', 'Gtaemoji', 'items/games/949180-gtaemoji.png', null, 'legendary', false, 'Games', 19, 141),
  ('item', 'Gtav Online Rank', 'items/games/9627-gtav-online-rank-8000.png', null, 'legendary', false, 'Games', 19, 142),
  ('item', 'Lethalcompany', 'items/games/9703-lethalcompany.png', null, 'legendary', false, 'Games', 19, 143),
  ('item', 'Gtav 3', 'items/games/gtav.png', null, 'legendary', false, 'Games', 19, 144),
  ('item', 'Playstation', 'items/playstation/129955-playstation.gif', null, 'legendary', true, 'PlayStation', 20, 145),
  ('item', 'Playstation 2', 'items/playstation/17023-playstation.png', null, 'legendary', false, 'PlayStation', 20, 146),
  ('item', 'Gold Trophy', 'items/playstation/1967-gold-trophy.png', null, 'legendary', false, 'PlayStation', 20, 147),
  ('item', 'Platinum Playstation', 'items/playstation/37302-platinum-playstation.png', null, 'legendary', false, 'PlayStation', 20, 148),
  ('item', 'Bronze Trophy', 'items/playstation/3833-bronze-trophy.png', null, 'legendary', false, 'PlayStation', 20, 149),
  ('item', 'Platinum Trophy', 'items/playstation/4858-platinum-trophy.png', null, 'legendary', false, 'PlayStation', 20, 150),
  ('item', 'Silver Trophy', 'items/playstation/5120-silver-trophy.png', null, 'legendary', false, 'PlayStation', 20, 151),
  ('item', 'Youtubegif', 'items/playstation/6409-youtubegif.png', null, 'legendary', false, 'PlayStation', 20, 152),
  ('item', 'Playstation 3', 'items/playstation/86272-playstation.png', null, 'legendary', false, 'PlayStation', 20, 153),
  ('item', 'Silver Trophy 2', 'items/playstation/8825-silver-trophy.png', null, 'legendary', false, 'PlayStation', 20, 154),
  ('item', 'Jakethedog', 'items/random-things/13155-jakethedog.gif', null, 'legendary', true, 'Random Things', 21, 155),
  ('item', 'Gamers Only Zone', 'items/random-things/1396-gamers-only-zone.png', null, 'legendary', false, 'Random Things', 21, 156),
  ('item', 'Pubgdrop', 'items/random-things/1460-pubgdrop.png', null, 'legendary', false, 'Random Things', 21, 157),
  ('item', 'Gray Lego Coin', 'items/random-things/1496-gray-lego-coin.gif', null, 'legendary', true, 'Random Things', 21, 158),
  ('item', 'Twitterverified', 'items/random-things/16841-twitterverified.png', null, 'legendary', false, 'Random Things', 21, 159),
  ('item', 'Gold Lego Coin', 'items/random-things/1743-gold-lego-coin.gif', null, 'legendary', true, 'Random Things', 21, 160),
  ('item', 'Jake Dancing', 'items/random-things/1856-jake-dancing.gif', null, 'legendary', true, 'Random Things', 21, 161),
  ('item', 'Lego Minikit', 'items/random-things/2453-lego-minikit.gif', null, 'legendary', true, 'Random Things', 21, 162),
  ('item', 'Jake Coffee', 'items/random-things/24871-jake-coffee.png', null, 'legendary', false, 'Random Things', 21, 163),
  ('item', 'Blue Lego Coin', 'items/random-things/2549-blue-lego-coin.gif', null, 'legendary', true, 'Random Things', 21, 164),
  ('item', 'Confusedlegoguy', 'items/random-things/2655-confusedlegoguy.png', null, 'legendary', false, 'Random Things', 21, 165),
  ('item', 'Crown', 'items/random-things/293484-crown.gif', null, 'legendary', true, 'Random Things', 21, 166),
  ('item', 'Gold Brick', 'items/random-things/3360-gold-brick.gif', null, 'legendary', true, 'Random Things', 21, 167),
  ('item', 'Tatatasahur', 'items/random-things/336938-tatatasahur.png', null, 'legendary', false, 'Random Things', 21, 168),
  ('item', 'Power Brick', 'items/random-things/4410-power-brick.gif', null, 'legendary', true, 'Random Things', 21, 169),
  ('item', 'Purple Lego Coin', 'items/random-things/5015-purple-lego-coin.gif', null, 'legendary', true, 'Random Things', 21, 170),
  ('item', 'Jake Slam', 'items/random-things/5339-jake-slam.png', null, 'legendary', false, 'Random Things', 21, 171),
  ('item', 'Jake The Dog Smirking', 'items/random-things/7757-jake-the-dog-smirking.gif', null, 'legendary', true, 'Random Things', 21, 172),
  ('item', 'Verified Blue', 'items/random-things/7762-verified-blue.gif', null, 'legendary', true, 'Random Things', 21, 173),
  ('item', 'Rbg Lego Stud', 'items/random-things/8570-rbg-lego-stud.gif', null, 'legendary', true, 'Random Things', 21, 174),
  ('item', 'Jakewiggle', 'items/random-things/9858-jakewiggle.gif', null, 'legendary', true, 'Random Things', 21, 175),
  ('item', 'Jakewiggle 2', 'items/random-things/9947-jakewiggle.png', null, 'legendary', false, 'Random Things', 21, 176)
on conflict (file_url) do update
  set name = excluded.name,
      asset_type = excluded.asset_type,
      sticker_pack = excluded.sticker_pack,
      required_plan = excluded.required_plan,
      animated = excluded.animated,
      collection = excluded.collection,
      collection_order = excluded.collection_order,
      sort_order = excluded.sort_order,
      enabled = true;

-- ---------------------------------------------------------------------------
-- تحقق: يجب أن ترى 21 قسماً والأعداد كما في الجدول أدناه
-- ---------------------------------------------------------------------------
select asset_type as "النوع", collection as "القسم", count(*) as "العدد",
       count(*) filter (where animated) as "متحرك"
  from public.site_assets
 group by asset_type, collection, collection_order
 order by asset_type, collection_order;

-- الإجمالي المتوقّع: avatar = 123 · sticker = 193 · item = 176
select asset_type, count(*) as total, count(*) filter (where animated) as animated
  from public.site_assets group by asset_type order by asset_type;


-- ############################################################################
-- ##  القسم: 16_language_room_names.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 16) أسماء غرف اللغة بلغتها
--
--  الغرض: كل غرفة لغة تحمل اسمها **بلغة أهلها** لا بلغة الواجهة:
--      العربية · English · Español · Français · Português · Русский · Türkçe · 中文
--
--  التطبيق يضمن هذه الأسماء من معرّف الغرفة (ui/community/ChannelNames.kt)
--  فتعمل فوراً بلا تنفيذ SQL — وهذا الملف يجعل قاعدة البيانات مطابقة لها،
--  ويصلح أي اسم قديم أو مختلط (إيموجي · اسم إنجليزي لغرفة غير إنجليزية).
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor. آمن لإعادة التنفيذ.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) غرف اللغات الثماني — الاسم بلغتها، والوصف بلغتها أيضاً
-- ---------------------------------------------------------------------------
update public.chat_channels
   set name = 'العربية', description = 'غرفة الدردشة العربية', icon = null, category = 'lang', sort_order = 1
 where id = 'lang-ar';

update public.chat_channels
   set name = 'Русский', description = 'Русскоязычный чат', icon = null, category = 'lang', sort_order = 2
 where id = 'lang-ru';

update public.chat_channels
   set name = 'Português', description = 'Sala de bate-papo em português', icon = null, category = 'lang', sort_order = 3
 where id = 'lang-pt';

update public.chat_channels
   set name = '中文', description = '中文聊天室', icon = null, category = 'lang', sort_order = 4
 where id = 'lang-zh';

update public.chat_channels
   set name = 'English', description = 'English chat room', icon = null, category = 'lang', sort_order = 5
 where id = 'lang-en';

update public.chat_channels
   set name = 'Español', description = 'Sala de chat en español', icon = null, category = 'lang', sort_order = 6
 where id = 'lang-es';

update public.chat_channels
   set name = 'Français', description = 'Salon de discussion en français', icon = null, category = 'lang', sort_order = 7
 where id = 'lang-fr';

update public.chat_channels
   set name = 'Türkçe', description = 'Türkçe sohbet odası', icon = null, category = 'lang', sort_order = 8
 where id = 'lang-tr';

-- ---------------------------------------------------------------------------
-- 2) الغرف الأساسية الخمس (أسماء ثابتة بلا إيموجي — الأيقونة يرسمها التطبيق)
-- ---------------------------------------------------------------------------
update public.chat_channels set name = 'General',             icon = null, category = 'main', sort_order = 1 where id = 'general';
update public.chat_channels set name = 'Minecraft',           icon = null, category = 'main', sort_order = 2 where id = 'minecraft';
update public.chat_channels set name = 'Creator',             icon = null, category = 'main', sort_order = 3 where id = 'creator';
update public.chat_channels set name = 'Support',             icon = null, category = 'main', sort_order = 4 where id = 'support';
update public.chat_channels set name = 'Social Media Makers', icon = null, category = 'main', sort_order = 5 where id = 'social-media-makers';

-- ---------------------------------------------------------------------------
-- 3) أي غرفة بمعرّف غير قياسي: يُعاد اسمها إلى لغتها بحسب ما هو مكتوب فيها
--    (مثال: غرفة اسمها «Russian» أو «الروسية» → تصبح «Русский»)
-- ---------------------------------------------------------------------------
update public.chat_channels set name = 'العربية' where lower(name) in ('arabic', 'ar', 'arab', 'العربية') and id <> 'lang-ar';
update public.chat_channels set name = 'English' where lower(name) in ('english', 'en')                       and id <> 'lang-en';
update public.chat_channels set name = 'Español' where lower(name) in ('spanish', 'es', 'espanol', 'الإسبانية', 'الاسبانية') and id <> 'lang-es';
update public.chat_channels set name = 'Français' where lower(name) in ('french', 'fr', 'francais', 'الفرنسية') and id <> 'lang-fr';
update public.chat_channels set name = 'Português' where lower(name) in ('portuguese', 'pt', 'portugues', 'البرتغالية') and id <> 'lang-pt';
update public.chat_channels set name = 'Русский' where lower(name) in ('russian', 'ru', 'الروسية')            and id <> 'lang-ru';
update public.chat_channels set name = 'Türkçe' where lower(name) in ('turkish', 'tr', 'turkce', 'التركية')    and id <> 'lang-tr';
update public.chat_channels set name = '中文' where lower(name) in ('chinese', 'zh', 'الصينية', '中文')          and id <> 'lang-zh';

-- ---------------------------------------------------------------------------
-- 3.1) أي غرفة لغة بمعرّف لم يُذكر أعلاه: توحيد التصنيف فقط
-- ---------------------------------------------------------------------------
update public.chat_channels set category = 'lang', icon = null where id like 'lang-%';

-- ---------------------------------------------------------------------------
-- تحقق: يجب أن ترى 13 غرفة، غرف اللغات بأسمائها الأصلية وكلها بلا إيموجي
-- ---------------------------------------------------------------------------
select id, name, category, sort_order
  from public.chat_channels
 order by category desc, sort_order;


-- ############################################################################
-- ##  القسم: 17_message_delete_window.sql
-- ############################################################################

-- ============================================================================
--  AddonStation Mobile — 17) نافذة حذف الرسالة (٣ دقائق)
--
--  الغرض المطلوب حرفياً:
--      «حذف المستخدم لرسالته التي أرسلها في شات خلال ثلاث دقائق،
--       بعدها لا يستطيع أن يحذفها.»
--
--  ما يفعله هذا الملف:
--      1) يعرّف مدة الحذف في مكان واحد: public.chat_delete_window_seconds() = 180.
--      2) يعيد تعريف public.chat_delete_my_message بحيث **ترفض** أي طلب بعد انتهاء
--         المدة، وتُعيد سبباً واضحاً: delete_window_expired مع المدة وعمر الرسالة.
--      3) يُبقي الإدارة كما هي: chat_moderate_message بلا أي حدّ زمني (إخفاء/حذف).
--
--  سلوك الواجهة المطابق (منفَّذ في التطبيق):
--      • زر «حذف رسالتي» يظهر داخل الدقائق الثلاث فقط، ومعه «متبقٍ س دقيقة للحذف».
--      • بعدها يختفي الزر ويظهر نص «انتهت مدة الحذف…».
--      • أي محاولة تجاوز (عميل قديم أو تعديل يدوي) يرفضها هذا الملف من جهة الخادم.
--
--  ملاحظة: الرسائل المرسلة قبل تنفيذ هذا الملف تصبح غير قابلة للحذف فوراً إن كانت
--          أقدم من ٣ دقائق — وهو المطلوب. أما من أرسل قبل ثوانٍ فيبقى قادراً.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor. آمن لإعادة التنفيذ بلا حدود.
-- ============================================================================

-- 1) المدة في مكان واحد (بالثواني) — تغييرها هنا يغيّر القاعدة كاملة.
create or replace function public.chat_delete_window_seconds()
returns integer
language sql
immutable
as $$
  select 180::integer   -- ٣ دقائق بالثواني
$$;

comment on function public.chat_delete_window_seconds() is
  'مدة السماح للمستخدم بحذف رسالته في الشات (بالثواني) — 180 = ثلاث دقائق.';

-- 2) إعادة تعريف دالة الحذف الذاتي مع النافذة الزمنية.
create or replace function public.chat_delete_my_message(p_message_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_owner   uuid;
  v_created timestamptz;
  v_status  text;
  v_window  integer := public.chat_delete_window_seconds();
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select user_id, created_at, status
    into v_owner, v_created, v_status
    from public.chat_messages
   where id = p_message_id;

  if v_owner is null then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  -- الحذف الذاتي لصاحب الرسالة فقط (الإدارة عبر chat_moderate_message).
  if v_owner <> v_uid then
    return jsonb_build_object('ok', false, 'message', 'forbidden');
  end if;

  if v_status = 'deleted' then
    return jsonb_build_object('ok', false, 'message', 'already_deleted');
  end if;

  -- القاعدة الحاسمة: بعد ثلاث دقائق من الإرسال لا يستطيع المستخدم الحذف.
  if now() - v_created > make_interval(secs => v_window) then
    return jsonb_build_object(
      'ok', false,
      'message', 'delete_window_expired',
      'window_seconds', v_window,
      'age_seconds', floor(extract(epoch from (now() - v_created)))::int
    );
  end if;

  update public.chat_messages
     set status = 'deleted',
         content = null,
         sticker_url = null,
         image_path = null,
         media_path = null,
         deleted_by = v_uid
   where id = p_message_id
     and user_id = v_uid;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'forbidden');
  end if;

  return jsonb_build_object('ok', true, 'window_seconds', v_window);
end;
$$;

-- 3) صلاحيات التنفيذ (كما في 11_grants) — للمسجَّلين بشكل أساسي.
grant execute on function public.chat_delete_window_seconds() to authenticated, anon, service_role;
grant execute on function public.chat_delete_my_message(bigint) to authenticated, service_role;

-- 4) تحقّق سريع بعد التنفيذ: يجب أن يطبع 180.
do $$
declare v_secs integer;
begin
  v_secs := public.chat_delete_window_seconds();
  raise notice 'مدة حذف الرسالة = % ثانية (% دقيقة)', v_secs, v_secs / 60;
  if v_secs <> 180 then
    raise warning 'المدة ليست 180 ثانية! القيمة الحالية: %', v_secs;
  end if;
end;
$$;

-- ============================================================================
--  خُلاصة السلوك النهائي:
--    • رسالة عمرها أقل من ٣ دقائق  → الحذف ينجح ويتحول status إلى deleted.
--    • رسالة عمرها ٣ دقائق أو أكثر → الرفض: delete_window_expired (بلا حذف).
--    • رسالة ليست للمستخدم          → الرفض: forbidden.
--    • الإدارة (chat_moderate_message) → إخفاء/حذف بلا حدّ زمني إطلاقاً.
-- ============================================================================
