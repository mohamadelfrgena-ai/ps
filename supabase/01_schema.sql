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
  avatar_url         text default 'profiles/profiles/onefree.png', -- the single free avatar in site-assets
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

  insert into public.profiles (id, username, display_name, avatar_url)
  values (new.id, v_username, v_username, 'profiles/profiles/onefree.png')
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
  -- Server-side maintenance has no end-user JWT and remains allowed.
  if auth.uid() is null then
    return new;
  end if;

  -- Validated SECURITY DEFINER profile RPCs set this transaction-local flag.
  -- End-user REST clients still lack UPDATE grants on protected columns.
  if coalesce(current_setting('app.allow_profile_write', true), '') = '1' then
    return new;
  end if;

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
