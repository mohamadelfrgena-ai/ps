-- AddonStation AC Economy
-- Secure AddonCoins ledger. Run after 000-foundation.sql, content-schema.sql,
-- and the chat schema files. Do not grant clients direct wallet writes.
-- AC is an internal reward balance: there is no purchase, transfer, cash-out,
-- or frontend balance editing.

-- =========================================================
-- TABLES
-- =========================================================

create table if not exists public.ac_tasks (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  description text not null default '',
  reward_ac bigint not null check (reward_ac > 0),
  frequency text not null default 'once' check (frequency in ('once','daily','streak','event','manual')),
  max_claims integer,
  conditions jsonb not null default '{}'::jsonb,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ac_achievements (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  description text not null default '',
  reward_ac bigint not null check (reward_ac > 0),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ac_events (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  description text not null default '',
  reward_ac bigint not null check (reward_ac > 0),
  starts_at timestamptz,
  ends_at timestamptz,
  conditions jsonb not null default '{}'::jsonb,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ac_admin_batches (
  id uuid primary key default gen_random_uuid(),
  initiated_by uuid not null references public.profiles(id) on delete restrict,
  amount_per_user bigint not null check (amount_per_user > 0),
  reason text not null,
  eligible_count integer not null default 0,
  total_ac bigint not null default 0,
  status text not null default 'processing' check (status in ('processing','completed','failed')),
  request_key text not null,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  unique (initiated_by, request_key)
);

create table if not exists public.ac_wallets (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  balance bigint not null default 0 check (balance >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.ac_transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  amount bigint not null check (amount <> 0),
  transaction_type text not null,
  reason text not null,
  balance_after bigint not null check (balance_after >= 0),
  actor_user_id uuid references public.profiles(id) on delete set null,
  task_id uuid references public.ac_tasks(id) on delete set null,
  batch_id uuid references public.ac_admin_batches(id) on delete set null,
  idempotency_key text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create unique index if not exists ac_transactions_user_idempotency_idx
  on public.ac_transactions(user_id, idempotency_key)
  where idempotency_key is not null;

create index if not exists ac_transactions_user_created_idx
  on public.ac_transactions(user_id, created_at desc);

create index if not exists ac_transactions_type_created_idx
  on public.ac_transactions(transaction_type, created_at desc);

create table if not exists public.ac_task_claims (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  task_id uuid not null references public.ac_tasks(id) on delete cascade,
  claim_key text not null,
  transaction_id uuid references public.ac_transactions(id) on delete set null,
  claimed_at timestamptz not null default now(),
  unique(user_id, task_id, claim_key)
);

create table if not exists public.ac_achievement_claims (
  user_id uuid not null references public.profiles(id) on delete cascade,
  achievement_id uuid not null references public.ac_achievements(id) on delete cascade,
  transaction_id uuid references public.ac_transactions(id) on delete set null,
  claimed_at timestamptz not null default now(),
  primary key(user_id, achievement_id)
);

create table if not exists public.ac_event_claims (
  user_id uuid not null references public.profiles(id) on delete cascade,
  event_id uuid not null references public.ac_events(id) on delete cascade,
  transaction_id uuid references public.ac_transactions(id) on delete set null,
  claimed_at timestamptz not null default now(),
  primary key(user_id, event_id)
);

create table if not exists public.ac_login_state (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  last_login_date date,
  streak_days integer not null default 0 check (streak_days >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.ac_login_milestones (
  user_id uuid not null references public.profiles(id) on delete cascade,
  streak_days integer not null check (streak_days in (3,7,14,30)),
  transaction_id uuid references public.ac_transactions(id) on delete set null,
  claimed_at timestamptz not null default now(),
  primary key(user_id, streak_days)
);

create table if not exists public.content_unlocks (
  user_id uuid not null references public.profiles(id) on delete cascade,
  content_id uuid not null references public.content_items(id) on delete cascade,
  transaction_id uuid references public.ac_transactions(id) on delete set null,
  unlocked_at timestamptz not null default now(),
  primary key(user_id, content_id)
);

-- Existing content rows remain valid. NULL means use the system price by type.
alter table public.content_items
  add column if not exists ac_price bigint;

-- Backfill wallets for existing profiles and create one for every new profile.
insert into public.ac_wallets(user_id)
select id from public.profiles
on conflict (user_id) do nothing;

create or replace function public.ac_create_wallet_for_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.ac_wallets(user_id) values(new.id) on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists ac_create_wallet_after_profile on public.profiles;
create trigger ac_create_wallet_after_profile
after insert on public.profiles
for each row execute procedure public.ac_create_wallet_for_profile();

-- =========================================================
-- SECURITY HELPERS AND CENTRAL LEDGER
-- =========================================================

create or replace function public.is_ac_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists(
    select 1 from public.profiles
    where id = auth.uid()
      and (role = 'admin' or is_admin = true)
  );
$$;

create or replace function public.ac_content_price(p_content_id uuid)
returns bigint
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  price bigint;
  kind text;
begin
  select content_type, ac_price into kind, price
  from public.content_items
  where id = p_content_id and is_published = true;
  if not found then raise exception 'Content not found.'; end if;
  if price is null or price <= 0 then
    price := case when kind = 'world' then 300 else 200 end;
  end if;
  return price;
end;
$$;

grant execute on function public.ac_content_price(uuid) to anon, authenticated;

-- Only other security-definer functions in this file call this function.
create or replace function public.ac_apply_transaction(
  p_user_id uuid,
  p_amount bigint,
  p_transaction_type text,
  p_reason text,
  p_idempotency_key text default null,
  p_actor_user_id uuid default null,
  p_task_id uuid default null,
  p_batch_id uuid default null,
  p_metadata jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  current_balance bigint;
  next_balance bigint;
  existing_id uuid;
  existing_balance bigint;
  new_id uuid;
begin
  if p_user_id is null or p_amount = 0 then
    raise exception 'Invalid AC transaction.';
  end if;
  if coalesce(length(trim(p_reason)), 0) = 0 then
    raise exception 'AC transaction reason is required.';
  end if;

  insert into public.ac_wallets(user_id, balance)
  values(p_user_id, 0)
  on conflict (user_id) do nothing;

  select balance into current_balance
  from public.ac_wallets
  where user_id = p_user_id
  for update;

  if p_idempotency_key is not null then
    select id, balance_after into existing_id, existing_balance
    from public.ac_transactions
    where user_id = p_user_id
      and idempotency_key = p_idempotency_key
    limit 1;
    if found then
      return jsonb_build_object(
        'granted', false,
        'duplicate', true,
        'transaction_id', existing_id,
        'amount', 0,
        'balance', existing_balance
      );
    end if;
  end if;

  next_balance := current_balance + p_amount;
  if next_balance < 0 then
    raise exception 'Not enough AC' using errcode = 'P0001';
  end if;

  update public.ac_wallets
  set balance = next_balance, updated_at = now()
  where user_id = p_user_id;

  insert into public.ac_transactions(
    user_id, amount, transaction_type, reason, balance_after,
    actor_user_id, task_id, batch_id, idempotency_key, metadata
  ) values(
    p_user_id, p_amount, p_transaction_type, p_reason, next_balance,
    p_actor_user_id, p_task_id, p_batch_id, p_idempotency_key,
    coalesce(p_metadata, '{}'::jsonb)
  ) returning id into new_id;

  return jsonb_build_object(
    'granted', true,
    'duplicate', false,
    'transaction_id', new_id,
    'amount', p_amount,
    'balance', next_balance
  );
end;
$$;

revoke all on function public.ac_apply_transaction(uuid,bigint,text,text,text,uuid,uuid,uuid,jsonb) from public, anon, authenticated;

-- =========================================================
-- USER REWARDS
-- =========================================================

insert into public.ac_tasks(slug,title,description,reward_ac,frequency,max_claims,conditions)
values
  ('welcome_reward','Welcome Reward','Your first successful login reward.',25,'once',1,'{}'),
  ('daily_login','Daily Login','Log in once per day.',10,'daily',null,'{}'),
  ('login_streak_3','3 Day Login Streak','Keep a three-day login streak.',15,'streak',1,'{"days":3}'),
  ('login_streak_7','7 Day Login Streak','Keep a seven-day login streak.',40,'streak',1,'{"days":7}'),
  ('login_streak_14','14 Day Login Streak','Keep a fourteen-day login streak.',80,'streak',1,'{"days":14}'),
  ('login_streak_30','30 Day Login Streak','Keep a thirty-day login streak.',150,'streak',1,'{"days":30}'),
  ('profile_complete','Complete Profile','Complete the required profile fields.',25,'once',1,'{}'),
  ('community_participation','Community Participation','Participate constructively in Community Chat.',5,'daily',null,'{"minimum_messages":3}'),
  ('first_addon_download','First Addon Download','Download your first published addon.',10,'once',1,'{}'),
  ('first_world_download','First World Download','Download your first published world.',10,'once',1,'{}'),
  ('first_useful_post','First Useful Post','Write your first useful community post.',10,'once',1,'{"minimum_characters":20}')
on conflict (slug) do nothing;

insert into public.ac_achievements(slug,title,description,reward_ac)
values
  ('first_addon_download','First Addon Download','Download your first addon.',10),
  ('first_world_download','First World Download','Download your first world.',10),
  ('first_useful_post','First Useful Post','Write your first useful community post.',10)
on conflict (slug) do nothing;

create or replace function public.ac_claim_login_rewards()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  today date := current_date;
  last_day date;
  streak integer := 0;
  reward bigint;
  result jsonb;
  welcome_amount bigint := 0;
  daily_amount bigint := 0;
  streak_amount bigint := 0;
  milestone integer;
  milestone_inserted integer;
  balance bigint := 0;
  tx_id uuid;
begin
  if uid is null then raise exception 'Login required'; end if;

  select balance into balance from public.ac_wallets where user_id = uid;
  if balance is null then
    insert into public.ac_wallets(user_id) values(uid) on conflict do nothing;
  end if;

  select reward_ac into reward from public.ac_tasks where slug='welcome_reward' and active=true;
  if reward is not null then
    result := public.ac_apply_transaction(uid, reward, 'welcome_reward', 'Welcome Reward', 'welcome:'||uid::text, null, (select id from public.ac_tasks where slug='welcome_reward'), null, '{}'::jsonb);
    if coalesce((result->>'granted')::boolean, false) then welcome_amount := reward; end if;
  end if;

  select last_login_date, streak_days into last_day, streak
  from public.ac_login_state where user_id = uid for update;

  if not found then
    streak := 1;
    insert into public.ac_login_state(user_id,last_login_date,streak_days)
    values(uid,today,streak);
    last_day := null;
  elsif last_day = today then
    select balance into balance from public.ac_wallets where user_id=uid;
    return jsonb_build_object('welcome_amount',welcome_amount,'daily_amount',0,'streak_amount',0,'streak',streak,'balance',balance,'already_logged_in',true);
  else
    streak := case when last_day = today - 1 then streak + 1 else 1 end;
    update public.ac_login_state set last_login_date=today, streak_days=streak, updated_at=now() where user_id=uid;
  end if;

  select reward_ac into reward from public.ac_tasks where slug='daily_login' and active=true;
  if reward is not null then
    result := public.ac_apply_transaction(uid, reward, 'daily_login', 'Daily Login', 'daily-login:'||today::text, null, (select id from public.ac_tasks where slug='daily_login'), null, jsonb_build_object('date',today));
    if coalesce((result->>'granted')::boolean, false) then daily_amount := reward; end if;
  end if;

  foreach milestone in array ARRAY[3,7,14,30] loop
    if streak = milestone then
      insert into public.ac_login_milestones(user_id,streak_days)
      values(uid,milestone)
      on conflict do nothing;
      get diagnostics milestone_inserted = row_count;
      if milestone_inserted > 0 then
        select reward_ac into reward from public.ac_tasks where slug=('login_streak_'||milestone::text) and active=true;
        if reward is not null then
          result := public.ac_apply_transaction(uid, reward, 'login_streak', milestone||' Day Login Streak', 'streak:'||milestone::text, null, (select id from public.ac_tasks where slug=('login_streak_'||milestone::text)), null, jsonb_build_object('streak_days',milestone));
          if coalesce((result->>'granted')::boolean, false) then streak_amount := reward; end if;
        end if;
      end if;
    end if;
  end loop;

  select balance into balance from public.ac_wallets where user_id=uid;
  return jsonb_build_object('welcome_amount',welcome_amount,'daily_amount',daily_amount,'streak_amount',streak_amount,'streak',streak,'balance',balance,'already_logged_in',false);
end;
$$;

grant execute on function public.ac_claim_login_rewards() to authenticated;

create or replace function public.ac_claim_profile_complete()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  task record;
  claim_id uuid;
  result jsonb;
begin
  if uid is null then raise exception 'Login required'; end if;
  if not exists(
    select 1 from public.profiles
    where id=uid
      and length(trim(username)) >= 3
      and length(trim(coalesce(bio,''))) > 0
      and length(trim(coalesce(avatar_url,''))) > 0
  ) then
    return jsonb_build_object('granted',false,'complete',false);
  end if;
  select * into task from public.ac_tasks where slug='profile_complete' and active=true;
  if not found then return jsonb_build_object('granted',false,'complete',true); end if;
  insert into public.ac_task_claims(user_id,task_id,claim_key)
  values(uid,task.id,'once') on conflict do nothing returning id into claim_id;
  if claim_id is null then return jsonb_build_object('granted',false,'complete',true,'already_claimed',true); end if;
  result := public.ac_apply_transaction(uid,task.reward_ac,'profile_complete','Complete Profile','task:profile_complete',null,task.id,null,'{}'::jsonb);
  return result || jsonb_build_object('complete',true);
end;
$$;

grant execute on function public.ac_claim_profile_complete() to authenticated;

create or replace function public.ac_claim_community_participation(p_message_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  task record;
  claim_id uuid;
  message_count integer;
  result jsonb;
  claim_key text := current_date::text;
begin
  if uid is null then raise exception 'Login required'; end if;
  if p_message_id is not null and not exists(select 1 from public.chat_messages where id=p_message_id and user_id=uid and status='published' and length(trim(content)) >= 8) then
    return jsonb_build_object('granted',false,'eligible',false);
  end if;
  select * into task from public.ac_tasks where slug='community_participation' and active=true;
  if not found then return jsonb_build_object('granted',false,'eligible',false); end if;
  select count(*) into message_count from public.chat_messages
  where user_id=uid and status='published' and length(trim(content)) >= 8 and created_at >= now() - interval '24 hours';
  if message_count < coalesce((task.conditions->>'minimum_messages')::integer,3) then
    return jsonb_build_object('granted',false,'eligible',false,'message_count',message_count);
  end if;
  insert into public.ac_task_claims(user_id,task_id,claim_key)
  values(uid,task.id,claim_key) on conflict do nothing returning id into claim_id;
  if claim_id is null then return jsonb_build_object('granted',false,'eligible',true,'already_claimed',true); end if;
  result := public.ac_apply_transaction(uid,task.reward_ac,'community_participation','Community Participation', 'task:community:'||claim_key,null,task.id,null,jsonb_build_object('message_count',message_count));
  return result || jsonb_build_object('eligible',true,'message_count',message_count);
end;
$$;

grant execute on function public.ac_claim_community_participation(bigint) to authenticated;

create or replace function public.ac_claim_event(p_event_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  event_row record;
  result jsonb;
  tx_id uuid;
begin
  if uid is null then raise exception 'Login required'; end if;
  select * into event_row from public.ac_events
  where id=p_event_id and active=true
    and (starts_at is null or starts_at <= now())
    and (ends_at is null or ends_at >= now());
  if not found then raise exception 'Event is not active.'; end if;
  insert into public.ac_event_claims(user_id,event_id)
  values(uid,p_event_id) on conflict do nothing returning transaction_id into tx_id;
  if tx_id is not null then return jsonb_build_object('granted',false,'already_claimed',true); end if;
  result := public.ac_apply_transaction(uid,event_row.reward_ac,'event_reward',event_row.title,'event:'||p_event_id::text,null,null,null,jsonb_build_object('event_id',p_event_id));
  update public.ac_event_claims set transaction_id=(result->>'transaction_id')::uuid where user_id=uid and event_id=p_event_id;
  return result;
end;
$$;

grant execute on function public.ac_claim_event(uuid) to authenticated;

create or replace function public.ac_claim_task(p_task_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  task record;
  claim_key text;
  claim_id uuid;
  current_claims integer;
  message_count integer;
  download_count integer;
  result jsonb;
begin
  if uid is null then raise exception 'Login required'; end if;
  select * into task from public.ac_tasks where id=p_task_id and active=true;
  if not found then raise exception 'Task is not active.'; end if;
  if task.slug in ('welcome_reward','daily_login','login_streak_3','login_streak_7','login_streak_14','login_streak_30','profile_complete','community_participation') then
    raise exception 'This task is claimed automatically.';
  end if;
  if task.frequency not in ('once','daily') then raise exception 'This task cannot be claimed by users.'; end if;
  claim_key := case when task.frequency='daily' then current_date::text else 'once' end;
  if task.max_claims is not null then
    select count(*) into current_claims from public.ac_task_claims where user_id=uid and task_id=task.id;
    if current_claims >= task.max_claims then return jsonb_build_object('granted',false,'already_claimed',true); end if;
  end if;
  if coalesce((task.conditions->>'minimum_messages')::integer,0) > 0 then
    select count(*) into message_count from public.chat_messages where user_id=uid and status='published';
    if message_count < (task.conditions->>'minimum_messages')::integer then return jsonb_build_object('granted',false,'eligible',false,'message_count',message_count); end if;
  end if;
  if coalesce((task.conditions->>'minimum_downloads')::integer,0) > 0 then
    select count(*) into download_count from public.content_downloads where user_id=uid;
    if download_count < (task.conditions->>'minimum_downloads')::integer then return jsonb_build_object('granted',false,'eligible',false,'download_count',download_count); end if;
  end if;
  insert into public.ac_task_claims(user_id,task_id,claim_key)
  values(uid,task.id,claim_key) on conflict do nothing returning id into claim_id;
  if claim_id is null then return jsonb_build_object('granted',false,'already_claimed',true); end if;
  result := public.ac_apply_transaction(uid,task.reward_ac,'task_reward',task.title,'task:'||task.id::text||':'||claim_key,null,task.id,null,jsonb_build_object('task_id',task.id));
  update public.ac_task_claims set transaction_id=(result->>'transaction_id')::uuid where id=claim_id;
  return result;
end;
$$;

grant execute on function public.ac_claim_task(uuid) to authenticated;

-- =========================================================
-- AC UNLOCKS
-- =========================================================

create or replace function public.ac_unlock_content(p_content_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  item record;
  price bigint;
  result jsonb;
  existing_tx uuid;
  kind text;
  label text;
begin
  if uid is null then raise exception 'Login required'; end if;
  select * into item from public.content_items where id=p_content_id and is_published=true;
  if not found then raise exception 'Content not found.'; end if;
  if exists(select 1 from public.content_unlocks where user_id=uid and content_id=p_content_id) then
    select balance into price from public.ac_wallets where user_id=uid;
    return jsonb_build_object('unlocked',true,'charged',0,'balance',price,'price',public.ac_content_price(p_content_id));
  end if;
  price := public.ac_content_price(p_content_id);
  kind := case when item.content_type='world' then 'world_unlock' else 'addon_unlock' end;
  label := case when item.content_type='world' then 'World Unlock' else 'Addon Unlock' end;
  result := public.ac_apply_transaction(uid,-price,kind,label,'unlock:'||p_content_id::text,null,null,null,jsonb_build_object('content_id',p_content_id,'title',item.title));
  if coalesce((result->>'duplicate')::boolean, false) then
    return result || jsonb_build_object('unlocked',true,'charged',0,'price',price);
  end if;
  existing_tx := (result->>'transaction_id')::uuid;
  insert into public.content_unlocks(user_id,content_id,transaction_id)
  values(uid,p_content_id,existing_tx)
  on conflict (user_id,content_id) do nothing;
  return result || jsonb_build_object('unlocked',true,'charged',price,'price',price);
end;
$$;

grant execute on function public.ac_unlock_content(uuid) to authenticated;

-- =========================================================
-- ACHIEVEMENTS FROM REAL DATABASE EVENTS
-- =========================================================

create or replace function public.ac_handle_content_download_achievement()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  kind text;
  achievement record;
  claim_id uuid;
  task_id uuid;
  result jsonb;
  total_for_type integer;
begin
  select content_type into kind from public.content_items where id=new.content_id;
  if kind is null then return new; end if;
  select count(*) into total_for_type
  from public.content_downloads d
  join public.content_items c on c.id=d.content_id
  where d.user_id=new.user_id and c.content_type=kind;
  if total_for_type <> 1 then return new; end if;
  select * into achievement from public.ac_achievements
  where slug=case when kind='world' then 'first_world_download' else 'first_addon_download' end and active=true;
  if not found then return new; end if;
  insert into public.ac_achievement_claims(user_id,achievement_id)
  values(new.user_id,achievement.id) on conflict do nothing returning achievement_id into claim_id;
  if claim_id is not null then
    result := public.ac_apply_transaction(new.user_id,achievement.reward_ac,'achievement',achievement.title,'achievement:'||achievement.slug,null,null,null,jsonb_build_object('content_id',new.content_id));
    update public.ac_achievement_claims set transaction_id=(result->>'transaction_id')::uuid where user_id=new.user_id and achievement_id=achievement.id;
  end if;
  return new;
end;
$$;

drop trigger if exists ac_download_achievement on public.content_downloads;
create trigger ac_download_achievement
after insert on public.content_downloads
for each row execute procedure public.ac_handle_content_download_achievement();

create or replace function public.ac_handle_first_useful_post()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  achievement record;
  result jsonb;
  user_count integer;
begin
  if coalesce(length(trim(new.content)),0) < 20 or coalesce(new.status,'published') <> 'published' then return new; end if;
  select count(*) into user_count from public.chat_messages
  where user_id=new.user_id and status='published' and length(trim(content)) >= 20;
  if user_count <> 1 then return new; end if;
  select * into achievement from public.ac_achievements where slug='first_useful_post' and active=true;
  if not found then return new; end if;
  insert into public.ac_achievement_claims(user_id,achievement_id)
  values(new.user_id,achievement.id) on conflict do nothing;
  if found then
    result := public.ac_apply_transaction(new.user_id,achievement.reward_ac,'achievement',achievement.title,'achievement:'||achievement.slug,null,null,null,jsonb_build_object('message_id',new.id));
    update public.ac_achievement_claims set transaction_id=(result->>'transaction_id')::uuid where user_id=new.user_id and achievement_id=achievement.id;
  end if;
  return new;
end;
$$;

drop trigger if exists ac_first_useful_post on public.chat_messages;
create trigger ac_first_useful_post
after insert on public.chat_messages
for each row execute procedure public.ac_handle_first_useful_post();

-- =========================================================
-- ADMIN FUNCTIONS
-- =========================================================

create or replace function public.ac_admin_preview()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare count_users integer;
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  select count(*) into count_users from public.profiles;
  return jsonb_build_object('eligible_users',count_users);
end;
$$;

grant execute on function public.ac_admin_preview() to authenticated;

create or replace function public.ac_admin_adjust(
  p_user_id uuid,
  p_amount bigint,
  p_reason text,
  p_action text,
  p_request_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  signed_amount bigint;
  tx_type text;
  result jsonb;
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  if p_amount <= 0 or coalesce(length(trim(p_reason)),0)=0 or coalesce(length(trim(p_request_key)),0)=0 then raise exception 'Amount, reason and request key are required.'; end if;
  if p_action not in ('give','remove') then raise exception 'Invalid AC action.'; end if;
  if not exists(select 1 from public.profiles where id=p_user_id) then raise exception 'User not found.'; end if;
  signed_amount := case when p_action='give' then p_amount else -p_amount end;
  tx_type := case when p_action='give' then 'admin_reward' else 'admin_remove' end;
  result := public.ac_apply_transaction(p_user_id,signed_amount,tx_type,p_reason,'admin:'||uid::text||':'||coalesce(p_request_key,gen_random_uuid()::text),uid,null,null,jsonb_build_object('admin_action',p_action));
  return result;
end;
$$;

grant execute on function public.ac_admin_adjust(uuid,bigint,text,text,text) to authenticated;

create or replace function public.ac_admin_global_reward(
  p_amount bigint,
  p_reason text,
  p_request_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  admin_id uuid := auth.uid();
  batch_id uuid;
  existing jsonb;
  user_row record;
  eligible integer := 0;
  total bigint := 0;
  result jsonb;
  batch_status text;
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  if p_amount <= 0 or coalesce(length(trim(p_reason)),0)=0 or coalesce(length(trim(p_request_key)),0)=0 then
    raise exception 'Amount, reason and request key are required.';
  end if;

  select id,status,eligible_count,total_ac into batch_id,batch_status,eligible,total
  from public.ac_admin_batches
  where initiated_by=admin_id and request_key=p_request_key
  for update;
  if found then
    return jsonb_build_object('batch_id',batch_id,'status',batch_status,'eligible_users',eligible,'total_ac',total,'duplicate',true);
  end if;

  batch_id := gen_random_uuid();
  select count(*) into eligible from public.profiles;
  total := eligible * p_amount;
  insert into public.ac_admin_batches(id,initiated_by,amount_per_user,reason,eligible_count,total_ac,status,request_key)
  values(batch_id,admin_id,p_amount,p_reason,eligible,total,'processing',p_request_key);

  for user_row in select id from public.profiles order by id loop
    result := public.ac_apply_transaction(user_row.id,p_amount,'global_reward',p_reason,'global:'||batch_id::text||':'||user_row.id::text,admin_id,null,batch_id,jsonb_build_object('batch_id',batch_id,'amount_per_user',p_amount));
  end loop;

  update public.ac_admin_batches
  set status='completed', completed_at=now()
  where id=batch_id;

  return jsonb_build_object('batch_id',batch_id,'status','completed','eligible_users',eligible,'total_ac',total,'duplicate',false);
end;
$$;

grant execute on function public.ac_admin_global_reward(bigint,text,text) to authenticated;

create or replace function public.ac_admin_search_users(p_query text default '')
returns table(user_id uuid, username text, email text, ac_balance bigint)
language plpgsql
security definer
set search_path = public
as $$
declare q text := lower(trim(coalesce(p_query,'')));
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  return query
  select p.id,p.username,au.email,coalesce(w.balance,0)
  from public.profiles p
  left join auth.users au on au.id=p.id
  left join public.ac_wallets w on w.user_id=p.id
  where q='' or lower(p.username) like '%'||q||'%'
    or lower(coalesce(au.email,'')) like '%'||q||'%'
    or p.id::text like '%'||q||'%'
  order by p.created_at desc
  limit 50;
end;
$$;

grant execute on function public.ac_admin_search_users(text) to authenticated;

create or replace function public.ac_admin_history(p_query text default '', p_type text default '', p_limit integer default 200)
returns table(transaction_id uuid,user_id uuid,username text,email text,amount bigint,transaction_type text,reason text,actor text,batch_id uuid,created_at timestamptz,balance_after bigint)
language plpgsql
security definer
set search_path = public
as $$
declare q text := lower(trim(coalesce(p_query,'')));
 t text := lower(trim(coalesce(p_type,'')));
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  return query
  select tx.id,p.id,p.username,au.email,tx.amount,tx.transaction_type,tx.reason,
    coalesce(actor.username,'System'),tx.batch_id,tx.created_at,tx.balance_after
  from public.ac_transactions tx
  join public.profiles p on p.id=tx.user_id
  left join auth.users au on au.id=p.id
  left join public.profiles actor on actor.id=tx.actor_user_id
  where (q='' or lower(p.username) like '%'||q||'%' or lower(coalesce(au.email,'')) like '%'||q||'%' or tx.user_id::text like '%'||q||'%' or lower(tx.reason) like '%'||q||'%')
    and (t='' or lower(tx.transaction_type)=t)
  order by tx.created_at desc
  limit greatest(1,least(p_limit,500));
end;
$$;

grant execute on function public.ac_admin_history(text,text,integer) to authenticated;

create or replace function public.ac_admin_stats()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  earned bigint;
  spent bigint;
  held bigint;
  users_with_ac integer;
  admin_given bigint;
  top_tasks jsonb;
  active_users jsonb;
begin
  if not public.is_ac_admin() then raise exception 'Administrator permission required.'; end if;
  select coalesce(sum(case when amount>0 then amount else 0 end),0), coalesce(sum(case when amount<0 then abs(amount) else 0 end),0) into earned,spent from public.ac_transactions;
  select coalesce(sum(balance),0), count(*) filter(where balance>0) into held,users_with_ac from public.ac_wallets;
  select coalesce(sum(amount),0) into admin_given from public.ac_transactions where transaction_type in ('admin_reward','global_reward');
  select coalesce(jsonb_agg(x order by x.claims desc),'[]'::jsonb) into top_tasks from (
    select t.title,count(*)::integer as claims from public.ac_task_claims c join public.ac_tasks t on t.id=c.task_id group by t.title order by count(*) desc limit 5
  ) x;
  select coalesce(jsonb_agg(x order by x.earned desc),'[]'::jsonb) into active_users from (
    select p.username,coalesce(sum(case when tx.amount>0 then tx.amount else 0 end),0)::bigint as earned from public.ac_transactions tx join public.profiles p on p.id=tx.user_id group by p.username order by earned desc limit 5
  ) x;
  return jsonb_build_object('total_earned',earned,'total_spent',spent,'total_held',held,'users_with_ac',users_with_ac,'admin_given',admin_given,'top_tasks',top_tasks,'active_users',active_users);
end;
$$;

grant execute on function public.ac_admin_stats() to authenticated;

-- =========================================================
-- RLS AND PRIVILEGES
-- =========================================================

alter table public.ac_wallets enable row level security;
alter table public.ac_transactions enable row level security;
alter table public.ac_tasks enable row level security;
alter table public.ac_task_claims enable row level security;
alter table public.ac_achievements enable row level security;
alter table public.ac_achievement_claims enable row level security;
alter table public.ac_events enable row level security;
alter table public.ac_event_claims enable row level security;
alter table public.ac_login_state enable row level security;
alter table public.ac_login_milestones enable row level security;
alter table public.ac_admin_batches enable row level security;
alter table public.content_unlocks enable row level security;

grant select on public.ac_wallets to authenticated;
grant select on public.ac_transactions to authenticated;
grant select on public.ac_tasks to anon, authenticated;
grant select on public.ac_task_claims to authenticated;
grant select on public.ac_achievements to anon, authenticated;
grant select on public.ac_achievement_claims to authenticated;
grant select on public.ac_events to anon, authenticated;
grant select on public.ac_event_claims to authenticated;
grant select on public.ac_login_state to authenticated;
grant select on public.ac_login_milestones to authenticated;
grant select on public.ac_admin_batches to authenticated;
grant select on public.content_unlocks to authenticated;

grant insert, update, delete on public.ac_tasks to authenticated;
grant insert, update, delete on public.ac_achievements to authenticated;
grant insert, update, delete on public.ac_events to authenticated;

drop policy if exists "Users view own AC wallet" on public.ac_wallets;
create policy "Users view own AC wallet" on public.ac_wallets for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Users view own AC transactions" on public.ac_transactions;
create policy "Users view own AC transactions" on public.ac_transactions for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Public view active AC tasks" on public.ac_tasks;
create policy "Public view active AC tasks" on public.ac_tasks for select to anon,authenticated using(active=true or public.is_ac_admin());
drop policy if exists "Admins manage AC tasks" on public.ac_tasks;
create policy "Admins manage AC tasks" on public.ac_tasks for all to authenticated using(public.is_ac_admin()) with check(public.is_ac_admin());

drop policy if exists "Users view own task claims" on public.ac_task_claims;
create policy "Users view own task claims" on public.ac_task_claims for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Public view active AC achievements" on public.ac_achievements;
create policy "Public view active AC achievements" on public.ac_achievements for select to anon,authenticated using(active=true or public.is_ac_admin());
drop policy if exists "Admins manage AC achievements" on public.ac_achievements;
create policy "Admins manage AC achievements" on public.ac_achievements for all to authenticated using(public.is_ac_admin()) with check(public.is_ac_admin());

drop policy if exists "Users view own achievement claims" on public.ac_achievement_claims;
create policy "Users view own achievement claims" on public.ac_achievement_claims for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Public view active AC events" on public.ac_events;
create policy "Public view active AC events" on public.ac_events for select to anon,authenticated using(active=true or public.is_ac_admin());
drop policy if exists "Admins manage AC events" on public.ac_events;
create policy "Admins manage AC events" on public.ac_events for all to authenticated using(public.is_ac_admin()) with check(public.is_ac_admin());

drop policy if exists "Users view own event claims" on public.ac_event_claims;
create policy "Users view own event claims" on public.ac_event_claims for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Users view own login state" on public.ac_login_state;
create policy "Users view own login state" on public.ac_login_state for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());
drop policy if exists "Users view own login milestones" on public.ac_login_milestones;
create policy "Users view own login milestones" on public.ac_login_milestones for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

drop policy if exists "Admins view AC batches" on public.ac_admin_batches;
create policy "Admins view AC batches" on public.ac_admin_batches for select to authenticated using(public.is_ac_admin());

drop policy if exists "Users view own content unlocks" on public.content_unlocks;
create policy "Users view own content unlocks" on public.content_unlocks for select to authenticated using(user_id=auth.uid() or public.is_ac_admin());

-- No client role can insert/update/delete wallet rows or transaction rows directly.
revoke insert, update, delete on public.ac_wallets from anon, authenticated;
revoke insert, update, delete on public.ac_transactions from anon, authenticated;
revoke insert, update, delete on public.ac_task_claims from anon, authenticated;
revoke insert, update, delete on public.ac_achievement_claims from anon, authenticated;
revoke insert, update, delete on public.ac_event_claims from anon, authenticated;
revoke insert, update, delete on public.ac_login_state from anon, authenticated;
revoke insert, update, delete on public.ac_login_milestones from anon, authenticated;
revoke insert, update, delete on public.ac_admin_batches from anon, authenticated;
revoke insert, update, delete on public.content_unlocks from anon, authenticated;
