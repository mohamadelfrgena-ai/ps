-- AddonStation — Community Chat complete setup
-- Run this file once in Supabase SQL Editor.
-- It combines the migrations in the required order.
-- 1) foundation  2) chat schema  3) moderation  4) channels/replies 5) reliable sender 6) images

-- =============================================================

-- 1. FOUNDATION / PROFILES

-- =============================================================
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

-- =============================================================

-- 2. BASIC CHAT TABLES

-- =============================================================
-- AddonStation public chat: run once in Supabase SQL Editor

create table if not exists public.chat_rule_acceptances (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  accepted_at timestamptz not null default now()
);

create table if not exists public.chat_messages (
  id bigint generated by default as identity primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  content text not null default '',
  sticker_url text,
  status text not null default 'published' check (status in ('published', 'hidden', 'flagged')),
  created_at timestamptz not null default now(),
  constraint chat_message_has_content check (char_length(trim(content)) between 1 and 500 or sticker_url is not null)
);

create index if not exists chat_messages_created_at_idx on public.chat_messages (created_at desc);

alter table public.chat_rule_acceptances enable row level security;
alter table public.chat_messages enable row level security;

drop policy if exists "Users can read their chat acceptance" on public.chat_rule_acceptances;
drop policy if exists "Users can accept chat rules" on public.chat_rule_acceptances;
drop policy if exists "Users can update chat acceptance" on public.chat_rule_acceptances;
drop policy if exists "Authenticated users can view published messages" on public.chat_messages;
drop policy if exists "Users can send own messages" on public.chat_messages;

create policy "Users can read their chat acceptance"
on public.chat_rule_acceptances for select to authenticated
using (auth.uid() = user_id);

create policy "Users can accept chat rules"
on public.chat_rule_acceptances for insert to authenticated
with check (auth.uid() = user_id);

create policy "Users can update chat acceptance"
on public.chat_rule_acceptances for update to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "Authenticated users can view published messages"
on public.chat_messages for select to authenticated
using (status = 'published');

create policy "Users can send own messages"
on public.chat_messages for insert to authenticated
with check (auth.uid() = user_id and status = 'published');

-- PostgREST also needs table/sequence privileges in addition to RLS.
grant select, insert on public.chat_messages to authenticated;
grant select, insert, update on public.chat_rule_acceptances to authenticated;
do $$
declare
  sequence_name text;
begin
  select pg_get_serial_sequence('public.chat_messages', 'id') into sequence_name;
  if sequence_name is not null then
    execute format('grant usage, select on sequence %s to authenticated', sequence_name);
  end if;
end $$;

-- Enables instant new messages for website, mobile and PS4 later.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (
       select 1
       from pg_publication_rel pr
       join pg_publication p on p.oid = pr.prpubid
       join pg_class c on c.oid = pr.prrelid
       join pg_namespace n on n.oid = c.relnamespace
       where p.pubname = 'supabase_realtime'
         and n.nspname = 'public'
         and c.relname = 'chat_messages'
     ) then
    alter publication supabase_realtime add table public.chat_messages;
  end if;
end $$;

-- =============================================================

-- 3. MODERATION / ANTI-SPAM

-- =============================================================
-- AddonStation chat moderation, reports, mute and anti-spam
-- Run this once in Supabase SQL Editor after chat-schema.sql

create or replace function public.is_chat_moderator()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid()
      and (role in ('admin', 'moderator') or is_admin = true)
  );
$$;

create table if not exists public.chat_reports (
  id bigint generated by default as identity primary key,
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null check (char_length(trim(reason)) between 3 and 300),
  status text not null default 'open' check (status in ('open', 'reviewed', 'dismissed', 'actioned')),
  created_at timestamptz not null default now(),
  unique (message_id, reporter_id)
);

create table if not exists public.chat_mutes (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  reason text,
  expires_at timestamptz not null,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);

alter table public.chat_reports enable row level security;
alter table public.chat_mutes enable row level security;

grant select, insert, update on public.chat_reports to authenticated;
grant select, insert, update, delete on public.chat_mutes to authenticated;

-- Let moderators hide or flag a message and store the reason.
alter table public.chat_messages add column if not exists moderation_reason text;

-- Replace the simple send policy with one that checks temporary mutes.
drop policy if exists "Users can send own messages" on public.chat_messages;
drop policy if exists "Users can send messages when not muted" on public.chat_messages;
create policy "Users can send messages when not muted"
on public.chat_messages for insert to authenticated
with check (
  auth.uid() = user_id
  and status = 'published'
  and not exists (
    select 1 from public.chat_mutes
    where user_id = auth.uid() and expires_at > now()
  )
);

drop policy if exists "Users can create chat reports" on public.chat_reports;
drop policy if exists "Users can view own chat reports" on public.chat_reports;
drop policy if exists "Moderators can manage reports" on public.chat_reports;
drop policy if exists "Moderators can manage messages" on public.chat_messages;
drop policy if exists "Moderators can manage mutes" on public.chat_mutes;

create policy "Users can create chat reports"
on public.chat_reports for insert to authenticated
with check (auth.uid() = reporter_id);

create policy "Users can view own chat reports"
on public.chat_reports for select to authenticated
using (auth.uid() = reporter_id);

create policy "Moderators can manage reports"
on public.chat_reports for all to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());

create policy "Moderators can manage messages"
on public.chat_messages for update to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());

grant delete on public.chat_messages to authenticated;
drop policy if exists "Users delete own chat messages" on public.chat_messages;
drop policy if exists "Moderators delete chat messages" on public.chat_messages;
create policy "Users delete own chat messages"
on public.chat_messages for delete to authenticated
using (auth.uid() = user_id);
create policy "Moderators delete chat messages"
on public.chat_messages for delete to authenticated
using (public.is_chat_moderator());

create policy "Moderators can manage mutes"
on public.chat_mutes for all to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());

-- Database-level safety: links, emails, phone-like numbers, and spam are rejected even if someone bypasses the website UI.
create or replace function public.enforce_chat_safety()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  recent_count integer;
begin
  if new.content ~* '(https?://|www\.)' then
    raise exception 'Links are not allowed in public chat.';
  end if;
  if new.content ~* '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' then
    raise exception 'Do not share email addresses in public chat.';
  end if;
  if new.content ~ '(^|[^0-9])[0-9]{8,}([^0-9]|$)' then
    raise exception 'Do not share phone numbers in public chat.';
  end if;
  select count(*) into recent_count
  from public.chat_messages
  where user_id = new.user_id and created_at > now() - interval '60 seconds';
  if recent_count >= 6 then
    raise exception 'Please wait before sending more messages.';
  end if;
  return new;
end;
$$;

drop trigger if exists before_chat_message_safety on public.chat_messages;
create trigger before_chat_message_safety
before insert on public.chat_messages
for each row execute procedure public.enforce_chat_safety();

-- =============================================================

-- 4. CHANNELS / REPLIES / NOTIFICATIONS

-- =============================================================
-- AddonStation Community Chat — Phase 1
-- Safe additive migration: does not delete existing tables, messages, or policies.

create table if not exists public.chat_channels (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9-]+$'),
  name text not null,
  description text not null default '',
  position integer not null default 0,
  is_public boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.chat_channel_members (
  channel_id uuid not null references public.chat_channels(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  member_role text not null default 'member' check (member_role in ('member','moderator','manager')),
  last_read_at timestamptz,
  joined_at timestamptz not null default now(),
  primary key (channel_id, user_id)
);

-- Add only missing columns to the existing message table.
alter table public.chat_messages add column if not exists channel_id uuid references public.chat_channels(id) on delete restrict;
alter table public.chat_messages add column if not exists reply_to_id bigint references public.chat_messages(id) on delete set null;
alter table public.chat_messages add column if not exists thread_root_id bigint references public.chat_messages(id) on delete set null;
alter table public.chat_messages add column if not exists edited_at timestamptz;

create table if not exists public.chat_reactions (
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  emoji text not null check (char_length(emoji) between 1 and 32),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id, emoji)
);

create table if not exists public.chat_attachments (
  id uuid primary key default gen_random_uuid(),
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  uploader_id uuid not null references public.profiles(id) on delete cascade,
  file_path text not null,
  file_name text not null,
  mime_type text,
  file_size bigint,
  created_at timestamptz not null default now()
);

create table if not exists public.chat_notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  actor_id uuid references public.profiles(id) on delete set null,
  notification_type text not null check (notification_type in ('mention','reply','thread','friend_request')),
  message_id bigint references public.chat_messages(id) on delete cascade,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.user_friends (
  requester_id uuid not null references public.profiles(id) on delete cascade,
  receiver_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','blocked','declined')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (requester_id, receiver_id),
  check (requester_id <> receiver_id)
);

-- Official AddonStation community channels.
insert into public.chat_channels (slug, name, description, position) values
('general','general','General community discussion',1),
('minecraft','minecraft','Minecraft gameplay and ideas',2),
('addons','addons','Add-ons and installation discussion',3),
('ps4','ps4','PS4 community and connection help',4),
('creators','creators','Creator Program and content publishing',5),
('support','support','Help and support questions',6)
on conflict (slug) do nothing;

-- Move old single-channel messages to #general safely.
update public.chat_messages
set channel_id = (select id from public.chat_channels where slug = 'general')
where channel_id is null;

-- Keep the old current chat page working: new messages without a channel use #general.
create or replace function public.default_general_channel()
returns uuid language sql stable security definer set search_path = public as $$
  select id from public.chat_channels where slug = 'general' limit 1;
$$;

alter table public.chat_messages alter column channel_id set default public.default_general_channel();
alter table public.chat_messages alter column channel_id set not null;

create index if not exists chat_messages_channel_created_idx on public.chat_messages(channel_id, created_at asc);
create index if not exists chat_messages_thread_idx on public.chat_messages(thread_root_id, created_at asc);
create index if not exists chat_notifications_recipient_idx on public.chat_notifications(recipient_id, is_read, created_at desc);

-- RLS for newly added tables only. Existing policies remain untouched.
alter table public.chat_channels enable row level security;
alter table public.chat_channel_members enable row level security;
alter table public.chat_reactions enable row level security;
alter table public.chat_attachments enable row level security;
alter table public.chat_notifications enable row level security;
alter table public.user_friends enable row level security;

-- RLS does not replace PostgreSQL table privileges for PostgREST.
grant select on public.chat_channels to authenticated;
grant select, insert, update, delete on public.chat_channel_members to authenticated;
grant select, insert, update, delete on public.chat_reactions to authenticated;
grant select, insert, delete on public.chat_attachments to authenticated;
grant select, insert, update on public.chat_notifications to authenticated;
grant select, insert, update, delete on public.user_friends to authenticated;

drop policy if exists "Authenticated users view public channels" on public.chat_channels;
create policy "Authenticated users view public channels" on public.chat_channels
for select to authenticated using (is_public = true or public.is_chat_moderator());

drop policy if exists "Users view own channel membership" on public.chat_channel_members;
create policy "Users view own channel membership" on public.chat_channel_members
for select to authenticated using (user_id = auth.uid() or public.is_chat_moderator());
drop policy if exists "Managers manage channel members" on public.chat_channel_members;
create policy "Managers manage channel members" on public.chat_channel_members
for all to authenticated using (public.is_chat_moderator()) with check (public.is_chat_moderator());

drop policy if exists "Authenticated users view reactions" on public.chat_reactions;
create policy "Authenticated users view reactions" on public.chat_reactions
for select to authenticated using (true);
drop policy if exists "Users manage own reactions" on public.chat_reactions;
create policy "Users manage own reactions" on public.chat_reactions
for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "Users view attachments on visible messages" on public.chat_attachments;
create policy "Users view attachments on visible messages" on public.chat_attachments
for select to authenticated using (true);
drop policy if exists "Users add own attachments" on public.chat_attachments;
create policy "Users add own attachments" on public.chat_attachments
for insert to authenticated with check (uploader_id = auth.uid());
drop policy if exists "Users delete own attachments" on public.chat_attachments;
create policy "Users delete own attachments" on public.chat_attachments
for delete to authenticated using (uploader_id = auth.uid() or public.is_chat_moderator());

drop policy if exists "Users view own notifications" on public.chat_notifications;
create policy "Users view own notifications" on public.chat_notifications
for select to authenticated using (recipient_id = auth.uid());
drop policy if exists "Users update own notifications" on public.chat_notifications;
create policy "Users update own notifications" on public.chat_notifications
for update to authenticated using (recipient_id = auth.uid()) with check (recipient_id = auth.uid());
drop policy if exists "Users create notifications" on public.chat_notifications;
create policy "Users create notifications" on public.chat_notifications
for insert to authenticated with check (actor_id = auth.uid());

drop policy if exists "Users view own friend relations" on public.user_friends;
create policy "Users view own friend relations" on public.user_friends
for select to authenticated using (requester_id = auth.uid() or receiver_id = auth.uid());
drop policy if exists "Users create friend requests" on public.user_friends;
create policy "Users create friend requests" on public.user_friends
for insert to authenticated with check (requester_id = auth.uid());
drop policy if exists "Users update received friend requests" on public.user_friends;
create policy "Users update received friend requests" on public.user_friends
for update to authenticated using (receiver_id = auth.uid() or requester_id = auth.uid()) with check (receiver_id = auth.uid() or requester_id = auth.uid());
drop policy if exists "Users delete own friend relations" on public.user_friends;
create policy "Users delete own friend relations" on public.user_friends
for delete to authenticated using (requester_id = auth.uid() or receiver_id = auth.uid());

-- Storage for chat image/file uploads.
insert into storage.buckets (id, name, public, file_size_limit)
values ('chat-uploads','chat-uploads',false,10485760)
on conflict (id) do nothing;

drop policy if exists "Users upload own chat files" on storage.objects;
create policy "Users upload own chat files" on storage.objects
for insert to authenticated
with check (bucket_id = 'chat-uploads' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users view chat files" on storage.objects;
create policy "Users view chat files" on storage.objects
for select to authenticated
using (bucket_id = 'chat-uploads');

drop policy if exists "Users delete own chat files" on storage.objects;
create policy "Users delete own chat files" on storage.objects
for delete to authenticated
using (bucket_id = 'chat-uploads' and ((storage.foldername(name))[1] = auth.uid()::text or public.is_chat_moderator()));

-- Realtime tables for the new community features.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (
       select 1 from pg_publication_rel pr
       join pg_publication p on p.oid = pr.prpubid
       join pg_class c on c.oid = pr.prrelid
       join pg_namespace n on n.oid = c.relnamespace
       where p.pubname = 'supabase_realtime'
         and n.nspname = 'public'
         and c.relname = 'chat_reactions'
     ) then
    alter publication supabase_realtime add table public.chat_reactions;
  end if;
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (
       select 1 from pg_publication_rel pr
       join pg_publication p on p.oid = pr.prpubid
       join pg_class c on c.oid = pr.prrelid
       join pg_namespace n on n.oid = c.relnamespace
       where p.pubname = 'supabase_realtime'
         and n.nspname = 'public'
         and c.relname = 'chat_notifications'
     ) then
    alter publication supabase_realtime add table public.chat_notifications;
  end if;
end $$;

-- =============================================================

-- 5. RELIABLE MESSAGE SENDER

-- =============================================================
-- Reliable chat sender
-- Uses a small security-definer RPC so inserting a message does not depend on
-- the client being allowed to SELECT the inserted row back.

create or replace function public.send_chat_message(
  p_channel_id uuid,
  p_content text default '',
  p_sticker_url text default null,
  p_reply_to_id bigint default null,
  p_thread_root_id bigint default null
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  new_message_id bigint;
  clean_content text := trim(coalesce(p_content, ''));
  is_muted boolean := false;
begin
  if auth.uid() is null then
    raise exception 'Login required.';
  end if;

  if not exists (
    select 1
    from public.chat_channels
    where id = p_channel_id
      and is_public = true
  ) then
    raise exception 'This chat channel is not available.';
  end if;

  if clean_content = '' and p_sticker_url is null then
    raise exception 'Message cannot be empty.';
  end if;

  if char_length(clean_content) > 500 then
    raise exception 'Message is too long.';
  end if;

  -- Moderation is optional. The sender still works when only the basic chat
  -- migration has been installed and chat_mutes does not exist yet.
  if to_regclass('public.chat_mutes') is not null then
    execute 'select exists (select 1 from public.chat_mutes where user_id = $1 and expires_at > now())'
      into is_muted
      using auth.uid();
  end if;
  if is_muted then
    raise exception 'You are temporarily muted.';
  end if;

  insert into public.chat_messages (
    user_id,
    channel_id,
    content,
    sticker_url,
    reply_to_id,
    thread_root_id,
    status
  )
  values (
    auth.uid(),
    p_channel_id,
    clean_content,
    p_sticker_url,
    p_reply_to_id,
    p_thread_root_id,
    'published'
  )
  returning id into new_message_id;

  return new_message_id;
end;
$$;

revoke all on function public.send_chat_message(uuid,text,text,bigint,bigint) from public;
grant execute on function public.send_chat_message(uuid,text,text,bigint,bigint) to authenticated;

-- =============================================================

-- 6. CHAT IMAGES

-- =============================================================
-- Community Chat images only: JPG, PNG and WEBP; no video or arbitrary files.
create table if not exists public.chat_images (
  id uuid primary key default gen_random_uuid(),
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  uploader_id uuid not null references public.profiles(id) on delete cascade,
  image_path text not null,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  image_size integer not null check (image_size > 0 and image_size <= 4194304),
  created_at timestamptz not null default now()
);
alter table public.chat_images enable row level security;
grant select, insert, delete on public.chat_images to authenticated;
drop policy if exists "Users view chat images" on public.chat_images;
create policy "Users view chat images" on public.chat_images for select to authenticated using(true);
drop policy if exists "Users add own chat images" on public.chat_images;
create policy "Users add own chat images" on public.chat_images for insert to authenticated with check(uploader_id=auth.uid());
drop policy if exists "Users delete own chat images" on public.chat_images;
create policy "Users delete own chat images" on public.chat_images for delete to authenticated using(uploader_id=auth.uid() or public.is_chat_moderator());

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('chat-images','chat-images',false,4194304,array['image/jpeg','image/png','image/webp'])
on conflict(id) do nothing;
drop policy if exists "Users upload own chat images" on storage.objects;
create policy "Users upload own chat images" on storage.objects for insert to authenticated with check(bucket_id='chat-images' and (storage.foldername(name))[1]=auth.uid()::text);
drop policy if exists "Users view chat images" on storage.objects;
create policy "Users view chat images" on storage.objects for select to authenticated using(bucket_id='chat-images');
drop policy if exists "Users delete own chat images" on storage.objects;
create policy "Users delete own chat images" on storage.objects for delete to authenticated using(bucket_id='chat-images' and ((storage.foldername(name))[1]=auth.uid()::text or public.is_chat_moderator()));
