-- ============================================================================
-- AddonStation Mobile - 50) General Supervisor, staff salary, weekly Epic gift
--
-- This migration is idempotent. It keeps the General Supervisor out of the
-- admin panel while granting full-room chat moderation, pays eligible staff at
-- most once per Tripoli calendar month, and makes one deterministic Epic addon
-- free for the duration of each Thursday without changing its stored plan.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) General Supervisor role
-- ---------------------------------------------------------------------------

alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles
  add constraint profiles_role_check
  check (role in ('user', 'creator', 'moderator', 'head_moderator', 'admin'));

create or replace function public.role_rank(p_role text)
returns integer
language sql
immutable
as $$
  select case p_role
    when 'admin'          then 5
    when 'head_moderator' then 4
    when 'moderator'      then 3
    when 'creator'        then 2
    else 1
  end;
$$;

create or replace function public.is_staff(p_user uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists(
    select 1
      from public.profiles
     where id = p_user
       and role in ('admin', 'head_moderator', 'moderator')
  );
$$;

-- A General Supervisor moderates every room. A regular moderator still uses
-- chat_moderator_rooms, and an admin remains the only role with admin access.
create or replace function public.chat_can_moderate_channel(p_channel_id text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_role text;
begin
  if v_uid is null or coalesce(btrim(p_channel_id), '') = '' then
    return false;
  end if;

  v_role := public.current_role_of(v_uid);
  if v_role in ('admin', 'head_moderator') then
    return true;
  end if;
  if v_role <> 'moderator' then
    return false;
  end if;

  return exists(
    select 1
      from public.chat_moderator_rooms mr
     where mr.user_id = v_uid
       and (mr.all_rooms or mr.channel_id = p_channel_id)
  );
end;
$$;

create or replace function public.chat_my_moderation_scope()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_all boolean := false;
  v_channels text[] := '{}'::text[];
begin
  if v_uid is null then
    return jsonb_build_object('all_rooms', false, 'channels', '[]'::jsonb);
  end if;

  v_role := public.current_role_of(v_uid);
  if v_role in ('admin', 'head_moderator') then
    v_all := true;
  elsif v_role = 'moderator' then
    select coalesce(bool_or(all_rooms), false),
           coalesce(array_agg(channel_id) filter (where channel_id is not null), '{}'::text[])
      into v_all, v_channels
      from public.chat_moderator_rooms
     where user_id = v_uid;
  end if;

  return jsonb_build_object('all_rooms', v_all, 'channels', to_jsonb(v_channels));
end;
$$;

-- PostgreSQL cannot CREATE OR REPLACE a function when its TABLE return shape
-- changes. Drop the no-argument RPC first, then recreate it with the role field.
drop function if exists public.chat_moderator_list();
create or replace function public.chat_moderator_list()
returns table(user_id uuid, display_name text, username text, role text, all_rooms boolean, room_ids text[])
language sql
stable
security definer
set search_path = public
as $$
  select p.id,
         p.display_name,
         p.username,
         p.role,
         case
           when p.role in ('admin', 'head_moderator') then true
           else coalesce(bool_or(mr.all_rooms), false)
         end,
         case
           when p.role in ('admin', 'head_moderator') then '{}'::text[]
           else coalesce(array_agg(mr.channel_id) filter (where mr.channel_id is not null), '{}'::text[])
         end
    from public.profiles p
    left join public.chat_moderator_rooms mr on mr.user_id = p.id
   where public.is_admin()
     and p.role in ('moderator', 'head_moderator', 'admin')
   group by p.id, p.display_name, p.username, p.role
   order by public.role_rank(p.role) desc,
            lower(coalesce(p.display_name, p.username, p.id::text));
$$;

-- Patch the latest send_chat_message body from migration 25. This deliberately
-- adds head_moderator only to staff exemptions and creators_admin posting; the
-- admin_only room and admin-only heavy-media checks remain admin-only.
do $patch_send_chat_message$
declare
  v_signature regprocedure := to_regprocedure(
    'public.send_chat_message(text,text,bigint,bigint,text,text,text,text,text,integer,bigint,boolean)'
  );
  v_definition text;
  v_patched text;
begin
  if v_signature is null then
    raise exception 'send_chat_message signature is missing';
  end if;

  select pg_get_functiondef(v_signature::oid) into v_definition;
  if position('head_moderator' in v_definition) > 0 then
    return;
  end if;

  v_patched := replace(
    v_definition,
    '(''creator'',''admin'',''moderator'')',
    '(''creator'',''admin'',''moderator'',''head_moderator'')'
  );
  v_patched := replace(
    v_patched,
    '(''admin'',''moderator'')',
    '(''admin'',''moderator'',''head_moderator'')'
  );

  if v_patched = v_definition then
    raise exception 'send_chat_message role checks could not be patched safely';
  end if;

  execute v_patched;

  select pg_get_functiondef(v_signature::oid) into v_definition;
  if position('head_moderator' in v_definition) = 0 then
    raise exception 'send_chat_message patch verification failed';
  end if;
end;
$patch_send_chat_message$;

-- ---------------------------------------------------------------------------
-- 2) Monthly moderator salary
-- ---------------------------------------------------------------------------

insert into public.app_settings(key, value)
values ('moderator_salary', '{"enabled":true,"amount":1500}'::jsonb)
on conflict (key) do nothing;

alter table public.ac_transactions drop constraint if exists ac_transactions_kind_check;
alter table public.ac_transactions
  add constraint ac_transactions_kind_check
  check (kind in (
    'daily', 'task', 'unlock', 'streak', 'event', 'welcome', 'admin_reward',
    'admin_adjustment', 'code', 'salary'
  ));

create table if not exists public.staff_salary_payments (
  id           bigserial primary key,
  user_id      uuid not null references public.profiles(id) on delete cascade,
  pay_month    date not null,
  amount       integer not null check (amount > 0),
  transaction_id bigint references public.ac_transactions(id) on delete set null,
  paid_at      timestamptz not null default now(),
  created_at   timestamptz not null default now(),
  unique (user_id, pay_month)
);

create index if not exists staff_salary_payments_user_paid_idx
  on public.staff_salary_payments(user_id, paid_at desc);

alter table public.staff_salary_payments enable row level security;

drop policy if exists salary_payments_own_read on public.staff_salary_payments;
create policy salary_payments_own_read on public.staff_salary_payments
  for select using (user_id = auth.uid() or public.is_admin());

revoke all on public.staff_salary_payments from public, anon, authenticated;
grant select on public.staff_salary_payments to authenticated;
grant select, insert, update, delete on public.staff_salary_payments to service_role;
grant usage, select on sequence public.staff_salary_payments_id_seq to service_role;

create or replace function public.moderator_salary_config()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_raw jsonb;
  v_enabled boolean := true;
  v_amount integer := 1500;
begin
  select value into v_raw
    from public.app_settings
   where key = 'moderator_salary';

  if jsonb_typeof(v_raw -> 'enabled') = 'boolean' then
    v_enabled := (v_raw ->> 'enabled')::boolean;
  end if;

  if jsonb_typeof(v_raw -> 'amount') = 'number' then
    v_amount := least(
      1000000::numeric,
      greatest(1::numeric, (v_raw ->> 'amount')::numeric)
    )::integer;
  end if;

  return jsonb_build_object('enabled', v_enabled, 'amount', v_amount);
end;
$$;

create or replace function public.admin_update_moderator_salary(p_enabled boolean, p_amount integer)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_config jsonb;
begin
  if not public.is_admin(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'forbidden');
  end if;
  if p_amount is null or p_amount < 1 or p_amount > 1000000 then
    return jsonb_build_object('ok', false, 'message', 'invalid_amount');
  end if;

  v_config := jsonb_build_object('enabled', coalesce(p_enabled, false), 'amount', p_amount);
  insert into public.app_settings(key, value, updated_at)
  values ('moderator_salary', v_config, now())
  on conflict (key) do update
    set value = excluded.value,
        updated_at = excluded.updated_at;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'moderator_salary_updated', 'app_setting', 'moderator_salary', v_config);

  return jsonb_build_object('ok', true, 'data', v_config);
end;
$$;

create or replace function public.staff_salary_apply(p_user_id uuid, p_pay_month date default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_config jsonb := public.moderator_salary_config();
  v_enabled boolean;
  v_amount integer;
  v_role text;
  v_month date := date_trunc(
    'month',
    coalesce(p_pay_month, timezone('Africa/Tripoli', now())::date)::timestamp
  )::date;
  v_payment_id bigint;
  v_transaction_id bigint;
  v_balance integer;
  v_request_key text;
begin
  v_enabled := (v_config ->> 'enabled')::boolean;
  v_amount := (v_config ->> 'amount')::integer;

  if not v_enabled then
    return jsonb_build_object('ok', true, 'paid', false, 'reason', 'disabled');
  end if;

  select role into v_role
    from public.profiles
   where id = p_user_id;

  if v_role is null or v_role not in ('moderator', 'head_moderator') then
    return jsonb_build_object('ok', true, 'paid', false, 'reason', 'not_eligible');
  end if;

  insert into public.staff_salary_payments(user_id, pay_month, amount)
  values (p_user_id, v_month, v_amount)
  on conflict (user_id, pay_month) do nothing
  returning id into v_payment_id;

  if v_payment_id is null then
    return jsonb_build_object('ok', true, 'paid', false, 'reason', 'already_paid');
  end if;

  insert into public.ac_wallets(user_id, balance, total_earned)
  values (p_user_id, 0, 0)
  on conflict (user_id) do nothing;

  select balance into v_balance
    from public.ac_wallets
   where user_id = p_user_id
   for update;

  v_balance := v_balance + v_amount;
  update public.ac_wallets
     set balance = v_balance,
         total_earned = total_earned + v_amount,
         updated_at = now()
   where user_id = p_user_id;

  v_request_key := 'salary:' || p_user_id::text || ':' || to_char(v_month, 'YYYY-MM');
  insert into public.ac_transactions(user_id, amount, kind, reason, balance_after, request_key, created_at)
  values (
    p_user_id,
    v_amount,
    'salary',
    'Monthly moderator salary',
    v_balance,
    v_request_key,
    now()
  )
  returning id into v_transaction_id;

  update public.staff_salary_payments
     set transaction_id = v_transaction_id,
         paid_at = now()
   where id = v_payment_id;

  return jsonb_build_object(
    'ok', true,
    'paid', true,
    'amount', v_amount,
    'balance', v_balance,
    'month', to_char(v_month, 'YYYY-MM')
  );
end;
$$;

create or replace function public.claim_moderator_salary()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'not_authenticated');
  end if;

  return public.staff_salary_apply(v_uid, null);
end;
$$;

create or replace function public.process_moderator_salaries()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile record;
  v_result jsonb;
  v_paid integer := 0;
  v_seen integer := 0;
begin
  for v_profile in
    select id
      from public.profiles
     where role in ('moderator', 'head_moderator')
     order by id
  loop
    v_seen := v_seen + 1;
    v_result := public.staff_salary_apply(v_profile.id, null);
    if coalesce((v_result ->> 'paid')::boolean, false) then
      v_paid := v_paid + 1;
    end if;
  end loop;

  return jsonb_build_object('ok', true, 'eligible', v_seen, 'paid', v_paid);
end;
$$;

-- A newly assigned Moderator or General Supervisor receives the current
-- month's salary immediately. Reassigning or changing between the two staff
-- roles cannot pay twice because staff_salary_payments is unique per month.
create or replace function public.staff_salary_on_role_assignment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.role in ('moderator', 'head_moderator')
     and old.role is distinct from new.role then
    perform public.staff_salary_apply(new.id, null);
  end if;
  return new;
end;
$$;

drop trigger if exists trg_staff_salary_on_role_assignment on public.profiles;
create trigger trg_staff_salary_on_role_assignment
  after update of role on public.profiles
  for each row
  execute function public.staff_salary_on_role_assignment();

revoke all on function public.moderator_salary_config() from public, anon, authenticated;
revoke all on function public.staff_salary_apply(uuid, date) from public, anon, authenticated;
revoke all on function public.process_moderator_salaries() from public, anon, authenticated;
revoke all on function public.staff_salary_on_role_assignment() from public, anon, authenticated;
revoke all on function public.admin_update_moderator_salary(boolean, integer) from public, anon;
revoke all on function public.claim_moderator_salary() from public, anon;

grant execute on function public.admin_update_moderator_salary(boolean, integer) to authenticated, service_role;
grant execute on function public.claim_moderator_salary() to authenticated;
grant execute on function public.process_moderator_salaries() to service_role;

-- Pay any already-assigned eligible staff for the current month when this
-- migration is first applied. The same unique key makes reruns harmless.
select public.process_moderator_salaries() as initial_salary_run;

-- Schedule shortly after midnight on the first day of each month when pg_cron
-- is available. The authenticated claim RPC remains a safe fallback.
do $salary_cron$
begin
  if to_regprocedure('cron.schedule(text,text,text)') is not null then
    execute 'select cron.unschedule(jobid) from cron.job where jobname = $1'
      using 'addonstation_moderator_salary';
    execute $schedule$
      select cron.schedule(
        'addonstation_moderator_salary',
        '5 0 1 * *',
        'select public.process_moderator_salaries();'
      )
    $schedule$;
  end if;
exception
  when insufficient_privilege or undefined_table or undefined_function then
    null;
end;
$salary_cron$;

-- ---------------------------------------------------------------------------
-- 3) One Epic addon is free throughout each Thursday in Africa/Tripoli
-- ---------------------------------------------------------------------------

insert into public.app_settings(key, value)
values ('weekly_epic_free', '{"enabled":true,"timezone":"Africa/Tripoli"}'::jsonb)
on conflict (key) do nothing;

create or replace function public.current_weekly_free_epic()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_local_now timestamp := timezone('Africa/Tripoli', now());
  v_local_date date := v_local_now::date;
  v_enabled boolean := true;
  v_item public.content_items%rowtype;
  v_start timestamptz;
  v_end timestamptz;
  v_raw jsonb;
begin
  select value into v_raw
    from public.app_settings
   where key = 'weekly_epic_free';

  if jsonb_typeof(v_raw -> 'enabled') = 'boolean' then
    v_enabled := (v_raw ->> 'enabled')::boolean;
  end if;

  if not v_enabled or extract(isodow from v_local_date)::integer <> 4 then
    return jsonb_build_object(
      'ok', true,
      'active', false,
      'timezone', 'Africa/Tripoli'
    );
  end if;

  select c.* into v_item
    from public.content_items c
   where c.is_published
     and c.content_type = 'addon'
     and c.plan = 'epic'
     and coalesce(c.file_path, '') <> ''
   order by md5(c.id::text || ':' || v_local_date::text || ':addonstation-weekly-epic'), c.id
   limit 1;

  if not found then
    return jsonb_build_object(
      'ok', true,
      'active', false,
      'reason', 'no_eligible_item',
      'timezone', 'Africa/Tripoli'
    );
  end if;

  v_start := v_local_date::timestamp at time zone 'Africa/Tripoli';
  v_end := (v_local_date + 1)::timestamp at time zone 'Africa/Tripoli';

  return jsonb_build_object(
    'ok', true,
    'active', true,
    'item_id', v_item.id,
    'title', v_item.title,
    'original_plan', v_item.plan,
    'starts_at', v_start,
    'ends_at', v_end,
    'ends_at_epoch_ms', floor(extract(epoch from v_end) * 1000)::bigint,
    'timezone', 'Africa/Tripoli'
  );
end;
$$;

create or replace function public.is_weekly_free_epic(p_item_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (public.current_weekly_free_epic() ->> 'active')::boolean
    and (public.current_weekly_free_epic() ->> 'item_id')::uuid = p_item_id,
    false
  );
$$;

-- Weekly-free is evaluated before AC and plan requirements, so the selected
-- addon is truly free for downloads as well as in the user interface.
create or replace function public.can_access_item(p_item_id uuid, p_user uuid default auth.uid())
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_item public.content_items;
  v_plan text;
  v_unlocked boolean;
begin
  select * into v_item
    from public.content_items
   where id = p_item_id and is_published = true;
  if not found then
    return false;
  end if;

  if p_user is not null then
    if v_item.created_by = p_user then
      return true;
    end if;
    if public.is_admin(p_user) then
      return true;
    end if;
  end if;

  if public.is_weekly_free_epic(p_item_id) then
    return p_user is not null;
  end if;

  if v_item.plan = 'free' then
    return p_user is not null;
  end if;

  v_plan := public.effective_plan(p_user);
  if public.plan_rank(v_plan) >= public.plan_rank(v_item.plan) then
    return true;
  end if;

  if v_item.ac_price > 0 and p_user is not null then
    select exists(
      select 1
        from public.ac_transactions t
       where t.user_id = p_user
         and t.item_id = p_item_id
         and t.kind = 'unlock'
    ) into v_unlocked;
    if v_unlocked then
      return true;
    end if;
  end if;

  return false;
end;
$$;

create or replace function public.ac_unlock_content(p_item_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_item public.content_items;
  v_price integer;
  v_balance integer;
  v_already boolean;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item
    from public.content_items
   where id = p_item_id and is_published = true;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if public.is_weekly_free_epic(p_item_id) then
    return jsonb_build_object('ok', true, 'charged', 0, 'weekly_free', true);
  end if;

  v_price := coalesce(v_item.ac_price, 0);
  if v_price <= 0 then
    return jsonb_build_object('ok', false, 'message', 'not_priced');
  end if;

  select exists(
    select 1
      from public.ac_transactions
     where user_id = v_uid
       and item_id = p_item_id
       and kind = 'unlock'
  ) into v_already;
  if v_already then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_unlocked');
  end if;

  select balance into v_balance
    from public.ac_wallets
   where user_id = v_uid
   for update;
  if coalesce(v_balance, 0) < v_price then
    return jsonb_build_object('ok', false, 'message', 'not_enough_ac');
  end if;

  update public.ac_wallets
     set balance = balance - v_price,
         total_spent = total_spent + v_price,
         updated_at = now()
   where user_id = v_uid;

  insert into public.ac_transactions(
    user_id, amount, kind, reason, item_id, request_key, balance_after
  )
  values (
    v_uid,
    -v_price,
    'unlock',
    coalesce(v_item.title, 'Content unlock'),
    p_item_id,
    'unlock:' || v_uid::text || ':' || p_item_id::text,
    v_balance - v_price
  )
  on conflict (request_key) do nothing;

  return jsonb_build_object('ok', true, 'charged', v_price, 'balance', v_balance - v_price);
end;
$$;

revoke all on function public.current_weekly_free_epic() from public;
revoke all on function public.is_weekly_free_epic(uuid) from public, anon, authenticated;
grant execute on function public.current_weekly_free_epic() to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 4) Final privilege refresh for replaced public RPCs
-- ---------------------------------------------------------------------------

grant execute on function public.role_rank(text) to anon, authenticated, service_role;
grant execute on function public.is_staff(uuid) to authenticated, service_role;
grant execute on function public.chat_can_moderate_channel(text) to authenticated, service_role;
grant execute on function public.chat_my_moderation_scope() to authenticated, service_role;
grant execute on function public.chat_moderator_list() to authenticated, service_role;
grant execute on function public.can_access_item(uuid, uuid) to authenticated, service_role;
grant execute on function public.ac_unlock_content(uuid) to authenticated, service_role;

notify pgrst, 'reload schema';

-- ---------------------------------------------------------------------------
-- Verification output
-- ---------------------------------------------------------------------------

select key, value
  from public.app_settings
 where key in ('moderator_salary', 'weekly_epic_free')
 order by key;

select public.current_weekly_free_epic() as weekly_epic_status;
