-- Manual payment workflow (Libya first, global gateways can be connected later)
create table if not exists public.subscription_plans (
  id text primary key check (id in ('epic','legendary')),
  name text not null,
  price_lyd numeric(10,2) not null,
  duration_days integer not null default 30,
  active boolean not null default true
);
insert into public.subscription_plans(id,name,price_lyd,duration_days) values
('epic','Epic 💎',25,30),('legendary','Legendary 👑',50,30)
on conflict (id) do nothing;

create table if not exists public.payment_orders (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references public.profiles(id) on delete cascade,
 plan_id text not null references public.subscription_plans(id),
 amount_lyd numeric(10,2) not null,
 payment_method text not null check (payment_method in ('onepay','lpay','edfali','mobicash','sadad','tadawul','bank_transfer','card','global')),
 reference_code text not null unique,
 transaction_reference text,
 receipt_url text,
 status text not null default 'pending' check (status in ('pending','submitted','approved','rejected','cancelled')),
 admin_note text,
 created_at timestamptz not null default now(),
 reviewed_at timestamptz
);
alter table public.payment_orders enable row level security;
create policy "Users view own payment orders" on public.payment_orders for select to authenticated using(auth.uid()=user_id);
create policy "Users create own payment orders" on public.payment_orders for insert to authenticated with check(auth.uid()=user_id);
create policy "Users update own pending orders" on public.payment_orders for update to authenticated using(auth.uid()=user_id and status in ('pending','submitted')) with check(auth.uid()=user_id);
create policy "Managers manage payment orders" on public.payment_orders for all to authenticated using(public.is_chat_moderator()) with check(public.is_chat_moderator());

create or replace function public.approve_payment_order(order_id uuid)
returns void language plpgsql security definer set search_path=public as $$
declare o record; p record;
begin
 if not public.is_chat_moderator() then raise exception 'Administrator permission required.'; end if;
 select * into o from public.payment_orders where id=order_id and status in ('pending','submitted');
 if not found then raise exception 'Order not available.'; end if;
 select * into p from public.subscription_plans where id=o.plan_id;
 update public.payment_orders set status='approved',reviewed_at=now() where id=order_id;
 update public.profiles set plan=o.plan_id, plan_expires_at=greatest(coalesce(plan_expires_at,now()),now()) + make_interval(days=>p.duration_days) where id=o.user_id;
end; $$;
