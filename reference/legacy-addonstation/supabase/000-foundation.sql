-- AddonStation foundation
-- Run this file before the chat/content/admin migrations.
-- It creates the user profile used by Auth, the marketplace and Community Chat.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null,
  avatar_url text not null default '/imgs/profiles/onefree.png',
  banner_url text,
  bio text not null default '' check (char_length(bio) <= 160),
  plan text not null default 'free' check (plan in ('free','epic','legendary')),
  role text not null default 'user' check (role in ('user','creator','moderator','admin')),
  is_admin boolean not null default false,
  plan_expires_at timestamptz,
  equipped_item_id text not null default 'none',
  linked_ip inet,
  linked_platform text check (linked_platform in ('ps4','ps5')),
  show_online_status boolean not null default false,
  user_level integer not null default 1 check (user_level >= 1),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Add columns when a partial profiles table already exists.
alter table public.profiles add column if not exists avatar_url text not null default '/imgs/profiles/onefree.png';
alter table public.profiles add column if not exists banner_url text;
alter table public.profiles add column if not exists bio text not null default '';
alter table public.profiles add column if not exists plan text not null default 'free';
alter table public.profiles add column if not exists role text not null default 'user';
alter table public.profiles add column if not exists is_admin boolean not null default false;
alter table public.profiles add column if not exists plan_expires_at timestamptz;
alter table public.profiles add column if not exists equipped_item_id text not null default 'none';
alter table public.profiles add column if not exists linked_ip inet;
alter table public.profiles add column if not exists linked_platform text;
alter table public.profiles add column if not exists show_online_status boolean not null default false;
alter table public.profiles add column if not exists user_level integer not null default 1;
alter table public.profiles add column if not exists created_at timestamptz not null default now();
alter table public.profiles add column if not exists updated_at timestamptz not null default now();

create unique index if not exists profiles_username_lower_idx on public.profiles (lower(username));

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at
before update on public.profiles
for each row execute procedure public.touch_updated_at();

create or replace function public.is_chat_moderator()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles
    where id = auth.uid()
      and (role in ('admin','moderator') or is_admin = true)
  );
$$;

create or replace function public.protect_profile_security()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- A normal member may edit profile presentation only. Subscription, role,
  -- expiry, level and device binding cannot be self-assigned from the browser.
  if auth.uid() is distinct from old.id and not public.is_chat_moderator() then
    raise exception 'You may only update your own profile.';
  end if;

  if not public.is_chat_moderator() then
    new.plan := old.plan;
    new.role := old.role;
    new.is_admin := old.is_admin;
    new.plan_expires_at := old.plan_expires_at;
    new.user_level := old.user_level;
    new.linked_ip := old.linked_ip;
    new.linked_platform := old.linked_platform;
  end if;

  -- Keep the legacy flag consistent with the role for manager updates too.
  new.is_admin := (new.role = 'admin');
  return new;
end;
$$;

drop trigger if exists profiles_protect_security on public.profiles;
create trigger profiles_protect_security
before update on public.profiles
for each row execute procedure public.protect_profile_security();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  requested_name text;
  safe_name text;
  candidate text;
  suffix integer := 0;
begin
  requested_name := coalesce(new.raw_user_meta_data ->> 'username', split_part(coalesce(new.email,''), '@', 1), 'Player');
  safe_name := regexp_replace(requested_name, '[^[:alnum:]_-]', '', 'g');
  if char_length(safe_name) < 3 then safe_name := 'Player'; end if;
  candidate := left(safe_name, 24);

  while exists (select 1 from public.profiles where lower(username) = lower(candidate)) loop
    suffix := suffix + 1;
    candidate := left(safe_name, greatest(3, 24 - char_length(suffix::text) - 1)) || '_' || suffix::text;
  end loop;

  insert into public.profiles (id, username, avatar_url)
  values (
    new.id,
    candidate,
    coalesce(nullif(new.raw_user_meta_data ->> 'avatar_url',''), '/imgs/profiles/onefree.png')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute procedure public.handle_new_user();

alter table public.profiles enable row level security;
drop policy if exists "Authenticated users can view profiles" on public.profiles;
drop policy if exists "Users update own profile" on public.profiles;
drop policy if exists "Managers manage profiles" on public.profiles;

create policy "Authenticated users can view profiles"
on public.profiles for select to authenticated
using (true);

create policy "Users update own profile"
on public.profiles for update to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

create policy "Managers manage profiles"
on public.profiles for all to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());

-- Allow the browser to update only presentation fields; the trigger above
-- still protects privileged values if a client attempts to send them.
grant select on public.profiles to authenticated;
grant update (username, avatar_url, banner_url, bio, equipped_item_id, show_online_status, updated_at) on public.profiles to authenticated;
