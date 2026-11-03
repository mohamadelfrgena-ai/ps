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
