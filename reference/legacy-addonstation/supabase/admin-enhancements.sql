-- Admin dashboard enhancements
create table if not exists public.payment_method_settings (
  id uuid primary key default gen_random_uuid(),
  method text not null unique,
  account_name text,
  account_identifier text,
  instructions text,
  active boolean not null default true,
  updated_at timestamptz not null default now()
);

alter table public.payment_method_settings enable row level security;
alter table public.subscription_plans enable row level security;

create policy "Managers manage payment settings" on public.payment_method_settings
for all to authenticated using(public.is_chat_moderator()) with check(public.is_chat_moderator());
create policy "Managers manage subscription plans" on public.subscription_plans
for all to authenticated using(public.is_chat_moderator()) with check(public.is_chat_moderator());
create policy "Authenticated users view active plans" on public.subscription_plans
for select to authenticated using(active=true or public.is_chat_moderator());

-- Let managers hide, edit and publish submitted content.
create policy "Managers update content" on public.content_items
for update to authenticated using(public.is_chat_moderator()) with check(public.is_chat_moderator());
