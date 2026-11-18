-- ============================================================================
-- AddonStation Mobile — 52) Plan-wide AC alternative prices and timed discounts
--
-- Canonical pricing is stored once per paid plan (Epic / Legendary). Android,
-- PS4 and PS5 read the same snapshot. Purchase price and discount expiry are
-- always decided by PostgreSQL server time, never by a client clock.
-- ============================================================================

begin;

create table if not exists public.plan_ac_pricing (
  plan                 text primary key,
  base_ac_price        integer not null default 0,
  sale_ac_price        integer,
  sale_starts_at       timestamptz,
  sale_ends_at         timestamptz,
  updated_at           timestamptz not null default now(),
  updated_by           uuid references public.profiles(id) on delete set null
);

alter table public.plan_ac_pricing
  add column if not exists base_ac_price integer not null default 0,
  add column if not exists sale_ac_price integer,
  add column if not exists sale_starts_at timestamptz,
  add column if not exists sale_ends_at timestamptz,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists updated_by uuid references public.profiles(id) on delete set null;

alter table public.plan_ac_pricing
  drop constraint if exists plan_ac_pricing_plan_check,
  drop constraint if exists plan_ac_pricing_base_check,
  drop constraint if exists plan_ac_pricing_sale_check;

alter table public.plan_ac_pricing
  add constraint plan_ac_pricing_plan_check
    check (plan in ('epic', 'legendary')),
  add constraint plan_ac_pricing_base_check
    check (base_ac_price between 0 and 10000000),
  add constraint plan_ac_pricing_sale_check
    check (
      (sale_ac_price is null and sale_starts_at is null and sale_ends_at is null)
      or
      (
        sale_ac_price between 1 and 9999999
        and sale_ac_price < base_ac_price
        and sale_starts_at is not null
        and sale_ends_at is not null
        and sale_ends_at > sale_starts_at
      )
    );

-- Zero means that the AC alternative is disabled until the owner configures it.
insert into public.plan_ac_pricing(plan, base_ac_price)
values ('epic', 0), ('legendary', 0)
on conflict (plan) do nothing;

create or replace function public.touch_plan_ac_pricing()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at := clock_timestamp();
  return new;
end;
$$;

drop trigger if exists trg_touch_plan_ac_pricing on public.plan_ac_pricing;
create trigger trg_touch_plan_ac_pricing
before update on public.plan_ac_pricing
for each row execute function public.touch_plan_ac_pricing();

alter table public.plan_ac_pricing enable row level security;

drop policy if exists plan_ac_pricing_read on public.plan_ac_pricing;
create policy plan_ac_pricing_read on public.plan_ac_pricing
  for select using (true);

-- Clients may read prices (and receive Realtime changes), but every write goes
-- through the server-validated admin RPC below.
revoke all on table public.plan_ac_pricing from public, anon, authenticated;
grant select on table public.plan_ac_pricing to anon, authenticated, service_role;
revoke insert, update, delete on table public.plan_ac_pricing from anon, authenticated;

-- Publish plan price changes so console clients can refetch the canonical
-- snapshot immediately. Polling remains a required fallback in the handoff doc.
do $publication$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;

  if not exists (
    select 1
      from pg_publication_rel pr
      join pg_publication p on p.oid = pr.prpubid
      join pg_class c on c.oid = pr.prrelid
      join pg_namespace n on n.oid = c.relnamespace
     where p.pubname = 'supabase_realtime'
       and n.nspname = 'public'
       and c.relname = 'plan_ac_pricing'
  ) then
    alter publication supabase_realtime add table public.plan_ac_pricing;
  end if;
end;
$publication$;

alter table public.plan_ac_pricing replica identity full;

-- Public cross-platform contract. It includes server time so a console can turn
-- the absolute sale end into a monotonic local countdown without trusting its
-- wall clock.
create or replace function public.plan_ac_pricing_snapshot()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  with timing as (
    select statement_timestamp() as server_now
  ), rows as (
    select
      p.*,
      t.server_now,
      (
        p.sale_ac_price is not null
        and p.sale_starts_at is not null
        and p.sale_ends_at is not null
        and t.server_now >= p.sale_starts_at
        and t.server_now < p.sale_ends_at
      ) as discount_active
    from public.plan_ac_pricing p
    cross join timing t
  )
  select jsonb_build_object(
    'ok', true,
    'version', 1,
    'timezone', 'Africa/Tripoli',
    'server_now', t.server_now,
    'server_now_epoch_ms', floor(extract(epoch from t.server_now) * 1000)::bigint,
    'revision', coalesce(max(r.updated_at)::text, ''),
    'plans', coalesce(
      jsonb_agg(
        jsonb_build_object(
          'plan', r.plan,
          'base_ac_price', r.base_ac_price,
          'sale_ac_price', r.sale_ac_price,
          'effective_ac_price', case when r.discount_active then r.sale_ac_price else r.base_ac_price end,
          'discount_active', r.discount_active,
          'discount_percent', case
            when r.discount_active and r.base_ac_price > 0
              then round(((r.base_ac_price - r.sale_ac_price) * 100.0) / r.base_ac_price)::integer
            else 0
          end,
          'sale_starts_at', r.sale_starts_at,
          'sale_ends_at', r.sale_ends_at,
          'sale_ends_at_epoch_ms', case
            when r.sale_ends_at is null then null
            else floor(extract(epoch from r.sale_ends_at) * 1000)::bigint
          end,
          'updated_at', r.updated_at
        )
        order by case r.plan when 'epic' then 1 else 2 end
      ) filter (where r.plan is not null),
      '[]'::jsonb
    )
  )
  from timing t
  left join rows r on true
  group by t.server_now;
$$;

create or replace function public.effective_plan_ac_price(
  p_plan text,
  p_at timestamptz default statement_timestamp()
)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select case
        when sale_ac_price is not null
         and sale_starts_at is not null
         and sale_ends_at is not null
         and p_at >= sale_starts_at
         and p_at < sale_ends_at
          then sale_ac_price
        else base_ac_price
      end
      from public.plan_ac_pricing
      where plan = lower(trim(p_plan))
    ),
    0
  );
$$;

create or replace function public.admin_set_plan_ac_pricing(
  p_plan text,
  p_base_ac_price integer,
  p_sale_ac_price integer default null,
  p_sale_ends_at timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_plan text := lower(trim(coalesce(p_plan, '')));
  v_now timestamptz := clock_timestamp();
begin
  if v_uid is null or not public.is_admin(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;

  if v_plan not in ('epic', 'legendary') then
    return jsonb_build_object('ok', false, 'message', 'invalid_plan');
  end if;

  if p_base_ac_price is null or p_base_ac_price < 0 or p_base_ac_price > 10000000 then
    return jsonb_build_object('ok', false, 'message', 'invalid_base_ac_price');
  end if;

  if (p_sale_ac_price is null) <> (p_sale_ends_at is null) then
    return jsonb_build_object('ok', false, 'message', 'incomplete_discount');
  end if;

  if p_sale_ac_price is not null then
    if p_base_ac_price <= 1
       or p_sale_ac_price < 1
       or p_sale_ac_price >= p_base_ac_price then
      return jsonb_build_object('ok', false, 'message', 'invalid_sale_ac_price');
    end if;
    if p_sale_ends_at <= v_now then
      return jsonb_build_object('ok', false, 'message', 'discount_end_must_be_future');
    end if;
  end if;

  insert into public.plan_ac_pricing(
    plan,
    base_ac_price,
    sale_ac_price,
    sale_starts_at,
    sale_ends_at,
    updated_at,
    updated_by
  ) values (
    v_plan,
    p_base_ac_price,
    p_sale_ac_price,
    case when p_sale_ac_price is null then null else v_now end,
    p_sale_ends_at,
    v_now,
    v_uid
  )
  on conflict (plan) do update
    set base_ac_price = excluded.base_ac_price,
        sale_ac_price = excluded.sale_ac_price,
        sale_starts_at = excluded.sale_starts_at,
        sale_ends_at = excluded.sale_ends_at,
        updated_at = excluded.updated_at,
        updated_by = excluded.updated_by;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (
    v_uid,
    'plan_ac_pricing_updated',
    'plan_ac_pricing',
    v_plan,
    jsonb_build_object(
      'base_ac_price', p_base_ac_price,
      'sale_ac_price', p_sale_ac_price,
      'sale_starts_at', case when p_sale_ac_price is null then null else v_now end,
      'sale_ends_at', p_sale_ends_at,
      'timezone', 'Africa/Tripoli'
    )
  );

  return public.plan_ac_pricing_snapshot();
end;
$$;

-- AC unlocks are permanent. A plan subscriber gets the item without paying;
-- otherwise Epic/Legendary use the global plan price while free AC-only items
-- continue to use content_items.ac_price.
create or replace function public.can_access_item(
  p_item_id uuid,
  p_user uuid default auth.uid()
)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_item public.content_items;
  v_plan text;
  v_unlocked boolean := false;
begin
  select * into v_item
    from public.content_items
   where id = p_item_id and is_published = true;
  if not found then
    return false;
  end if;

  if p_user is null then
    return false;
  end if;

  if v_item.created_by = p_user or public.is_admin(p_user) then
    return true;
  end if;

  if public.is_weekly_free_epic(p_item_id) then
    return true;
  end if;

  v_plan := public.effective_plan(p_user);
  if v_item.plan in ('epic', 'legendary')
     and public.plan_rank(v_plan) >= public.plan_rank(v_item.plan) then
    return true;
  end if;

  -- Preserve the existing catalog rule: published Free content remains free.
  if v_item.plan = 'free' then
    return true;
  end if;

  select exists(
    select 1
      from public.ac_transactions t
     where t.user_id = p_user
       and t.item_id = p_item_id
       and t.kind = 'unlock'
  ) into v_unlocked;

  return v_unlocked;
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
  v_user_plan text;
  v_base_price integer := 0;
  v_sale_price integer;
  v_price integer := 0;
  v_sale_starts timestamptz;
  v_sale_ends timestamptz;
  v_discount_active boolean := false;
  v_balance integer;
  v_already boolean := false;
  v_tx_id bigint;
  v_now timestamptz;
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

  if v_item.created_by = v_uid or public.is_admin(v_uid) then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_entitled');
  end if;

  if public.is_weekly_free_epic(p_item_id) then
    return jsonb_build_object('ok', true, 'charged', 0, 'weekly_free', true);
  end if;

  v_user_plan := public.effective_plan(v_uid);
  if v_item.plan in ('epic', 'legendary')
     and public.plan_rank(v_user_plan) >= public.plan_rank(v_item.plan) then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_entitled');
  end if;

  if v_item.plan in ('epic', 'legendary') then
    select base_ac_price, sale_ac_price, sale_starts_at, sale_ends_at
      into v_base_price, v_sale_price, v_sale_starts, v_sale_ends
      from public.plan_ac_pricing
     where plan = v_item.plan
     for share;

    if not found then
      return jsonb_build_object('ok', false, 'message', 'not_priced');
    end if;

    v_now := clock_timestamp();
    v_discount_active := v_sale_price is not null
      and v_sale_starts is not null
      and v_sale_ends is not null
      and v_now >= v_sale_starts
      and v_now < v_sale_ends;
    v_price := case when v_discount_active then v_sale_price else v_base_price end;
  else
    v_now := clock_timestamp();
    v_base_price := coalesce(v_item.ac_price, 0);
    v_price := v_base_price;
  end if;

  if coalesce(v_price, 0) <= 0 then
    return jsonb_build_object('ok', false, 'message', 'not_priced');
  end if;

  -- The wallet row serializes all purchases for this account. Rechecking the
  -- unique unlock after this lock prevents two concurrent requests charging
  -- the same item twice.
  select balance into v_balance
    from public.ac_wallets
   where user_id = v_uid
   for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'wallet_not_found');
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

  if v_balance < v_price then
    return jsonb_build_object(
      'ok', false,
      'message', 'not_enough_ac',
      'required', v_price,
      'balance', v_balance
    );
  end if;

  insert into public.ac_transactions(
    user_id,
    amount,
    kind,
    reason,
    item_id,
    request_key,
    balance_after
  ) values (
    v_uid,
    -v_price,
    'unlock',
    coalesce(v_item.title, 'Content unlock'),
    p_item_id,
    'unlock:' || v_uid::text || ':' || p_item_id::text,
    v_balance - v_price
  )
  on conflict (request_key) do nothing
  returning id into v_tx_id;

  if v_tx_id is null then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_unlocked');
  end if;

  update public.ac_wallets
     set balance = v_balance - v_price,
         total_spent = total_spent + v_price,
         updated_at = v_now
   where user_id = v_uid;

  return jsonb_build_object(
    'ok', true,
    'charged', v_price,
    'balance', v_balance - v_price,
    'plan', v_item.plan,
    'base_ac_price', v_base_price,
    'sale_ac_price', v_sale_price,
    'discount_active', v_discount_active,
    'sale_ends_at', v_sale_ends,
    'server_now', v_now
  );
end;
$$;

revoke all on function public.plan_ac_pricing_snapshot() from public;
revoke all on function public.effective_plan_ac_price(text, timestamptz) from public;
revoke all on function public.admin_set_plan_ac_pricing(text, integer, integer, timestamptz) from public;
revoke all on function public.can_access_item(uuid, uuid) from public;
revoke all on function public.ac_unlock_content(uuid) from public;

grant execute on function public.plan_ac_pricing_snapshot() to anon, authenticated, service_role;
grant execute on function public.effective_plan_ac_price(text, timestamptz) to anon, authenticated, service_role;
grant execute on function public.admin_set_plan_ac_pricing(text, integer, integer, timestamptz) to authenticated, service_role;
grant execute on function public.can_access_item(uuid, uuid) to authenticated, service_role;
grant execute on function public.ac_unlock_content(uuid) to authenticated, service_role;

notify pgrst, 'reload schema';

commit;

-- Read-only result for SQL Editor. Both plans exist but remain disabled until
-- base_ac_price is set above zero from the Android admin panel.
select public.plan_ac_pricing_snapshot() as plan_ac_pricing;
