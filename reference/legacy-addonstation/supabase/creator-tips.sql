-- AddonStation Creator Tips
-- Manual tips in LYD. AC is never involved and cannot be exchanged for money.
-- Run after 000-foundation.sql, payments-schema.sql and the storage setup.

create table if not exists public.creator_tip_orders (
  id uuid primary key default gen_random_uuid(),
  supporter_id uuid not null references public.profiles(id) on delete cascade,
  creator_id uuid not null references public.profiles(id) on delete cascade,
  amount_lyd numeric(10,2) not null check (amount_lyd > 0),
  payment_method text not null check (payment_method in ('onepay','lpay','edfali','mobicash','sadad','tadawul','bank_transfer','card','global')),
  reference_code text not null unique,
  transaction_reference text,
  receipt_path text,
  message text not null default '' check (char_length(message) <= 240),
  status text not null default 'pending' check (status in ('pending','submitted','approved','rejected','cancelled')),
  admin_note text,
  creator_amount_lyd numeric(10,2),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  approved_at timestamptz,
  check (supporter_id <> creator_id)
);

create index if not exists creator_tip_orders_creator_idx
  on public.creator_tip_orders(creator_id, created_at desc);
create index if not exists creator_tip_orders_status_idx
  on public.creator_tip_orders(status, created_at desc);

create table if not exists public.creator_wallets (
  creator_id uuid primary key references public.profiles(id) on delete cascade,
  available_lyd numeric(12,2) not null default 0 check (available_lyd >= 0),
  lifetime_earned_lyd numeric(12,2) not null default 0 check (lifetime_earned_lyd >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.creator_earnings (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  tip_id uuid unique references public.creator_tip_orders(id) on delete set null,
  amount_lyd numeric(12,2) not null check (amount_lyd > 0),
  earning_type text not null default 'tip',
  reason text not null,
  balance_after_lyd numeric(12,2) not null check (balance_after_lyd >= 0),
  created_at timestamptz not null default now()
);

create index if not exists creator_earnings_creator_idx
  on public.creator_earnings(creator_id, created_at desc);

create or replace function public.creator_create_wallet_for_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.role = 'creator' then
    insert into public.creator_wallets(creator_id) values(new.id) on conflict do nothing;
  end if;
  return new;
end;
$$;

drop trigger if exists creator_wallet_after_profile on public.profiles;
create trigger creator_wallet_after_profile
after insert or update of role on public.profiles
for each row execute procedure public.creator_create_wallet_for_profile();

insert into public.creator_wallets(creator_id)
select id from public.profiles where role = 'creator'
on conflict (creator_id) do nothing;

create or replace function public.creator_create_tip(
  p_creator_id uuid,
  p_amount_lyd numeric,
  p_payment_method text,
  p_message text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  supporter uuid := auth.uid();
  tip_id uuid;
  reference text;
begin
  if supporter is null then raise exception 'Login required'; end if;
  if p_creator_id is null or p_creator_id = supporter then raise exception 'Invalid creator.'; end if;
  if not exists(select 1 from public.profiles where id=p_creator_id and role='creator') then raise exception 'Creator not found.'; end if;
  if p_amount_lyd is null or p_amount_lyd < 1 or p_amount_lyd > 10000 then raise exception 'Tip amount must be between 1 and 10000 LYD.'; end if;
  if p_payment_method not in ('onepay','lpay','edfali','mobicash','sadad','tadawul','bank_transfer','card','global') then raise exception 'Invalid payment method.'; end if;
  reference := 'TIP-' || upper(substr(replace(gen_random_uuid()::text,'-',''),1,10));
  insert into public.creator_tip_orders(supporter_id,creator_id,amount_lyd,payment_method,reference_code,message)
  values(supporter,p_creator_id,p_amount_lyd,p_payment_method,reference,left(coalesce(p_message,''),240))
  returning id into tip_id;
  return jsonb_build_object('tip_id',tip_id,'reference_code',reference,'amount_lyd',p_amount_lyd,'status','pending');
end;
$$;

grant execute on function public.creator_create_tip(uuid,numeric,text,text) to authenticated;

create or replace function public.creator_submit_tip_receipt(
  p_tip_id uuid,
  p_receipt_path text,
  p_transaction_reference text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare tip record;
begin
  select * into tip from public.creator_tip_orders where id=p_tip_id and supporter_id=auth.uid() for update;
  if not found then raise exception 'Tip order not found.'; end if;
  if tip.status not in ('pending','submitted') then raise exception 'This tip order cannot be updated.'; end if;
  update public.creator_tip_orders
  set receipt_path=p_receipt_path, transaction_reference=nullif(trim(coalesce(p_transaction_reference,'')),''), status='submitted'
  where id=p_tip_id;
  return jsonb_build_object('tip_id',p_tip_id,'status','submitted');
end;
$$;

grant execute on function public.creator_submit_tip_receipt(uuid,text,text) to authenticated;

create or replace function public.creator_approve_tip(p_tip_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  tip record;
  wallet record;
  earning_id uuid;
  next_balance numeric(12,2);
begin
  if not public.is_chat_moderator() then raise exception 'Administrator permission required.'; end if;
  select * into tip from public.creator_tip_orders where id=p_tip_id for update;
  if not found then raise exception 'Tip order not found.'; end if;
  if tip.status = 'approved' then return jsonb_build_object('approved',true,'duplicate',true,'amount_lyd',tip.creator_amount_lyd); end if;
  if tip.status not in ('pending','submitted') then raise exception 'Tip order is not reviewable.'; end if;
  insert into public.creator_wallets(creator_id) values(tip.creator_id) on conflict do nothing;
  select * into wallet from public.creator_wallets where creator_id=tip.creator_id for update;
  next_balance := wallet.available_lyd + tip.amount_lyd;
  update public.creator_wallets
  set available_lyd=next_balance, lifetime_earned_lyd=lifetime_earned_lyd+tip.amount_lyd, updated_at=now()
  where creator_id=tip.creator_id;
  insert into public.creator_earnings(creator_id,tip_id,amount_lyd,earning_type,reason,balance_after_lyd)
  values(tip.creator_id,tip.id,tip.amount_lyd,'tip','Creator Tip',next_balance)
  returning id into earning_id;
  update public.creator_tip_orders
  set status='approved', creator_amount_lyd=tip.amount_lyd, reviewed_at=now(), approved_at=now()
  where id=tip.id;
  return jsonb_build_object('approved',true,'duplicate',false,'earning_id',earning_id,'amount_lyd',tip.amount_lyd,'balance_lyd',next_balance);
end;
$$;

grant execute on function public.creator_approve_tip(uuid) to authenticated;

create or replace function public.creator_reject_tip(p_tip_id uuid, p_admin_note text default '')
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_chat_moderator() then raise exception 'Administrator permission required.'; end if;
  update public.creator_tip_orders
  set status='rejected', admin_note=left(coalesce(p_admin_note,''),500), reviewed_at=now()
  where id=p_tip_id and status in ('pending','submitted');
end;
$$;

grant execute on function public.creator_reject_tip(uuid,text) to authenticated;

alter table public.creator_tip_orders enable row level security;
alter table public.creator_wallets enable row level security;
alter table public.creator_earnings enable row level security;

grant select on public.creator_tip_orders to authenticated;
grant select on public.creator_wallets to authenticated;
grant select on public.creator_earnings to authenticated;

drop policy if exists "Users view own creator tips" on public.creator_tip_orders;
create policy "Users view own creator tips" on public.creator_tip_orders
for select to authenticated
using (supporter_id=auth.uid() or creator_id=auth.uid() or public.is_chat_moderator());

drop policy if exists "Creators view own wallet" on public.creator_wallets;
create policy "Creators view own wallet" on public.creator_wallets
for select to authenticated
using (creator_id=auth.uid() or public.is_chat_moderator());

drop policy if exists "Creators view own earnings" on public.creator_earnings;
create policy "Creators view own earnings" on public.creator_earnings
for select to authenticated
using (creator_id=auth.uid() or public.is_chat_moderator());

-- Tips are created and updated through the secure functions above.
revoke insert, update, delete on public.creator_tip_orders from anon, authenticated;
revoke insert, update, delete on public.creator_wallets from anon, authenticated;
revoke insert, update, delete on public.creator_earnings from anon, authenticated;
