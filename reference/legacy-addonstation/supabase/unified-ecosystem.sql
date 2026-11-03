-- AddonStation Unified Ecosystem migration
-- One Supabase data contract for Website, Desktop and Mobile.
-- Safe/additive: it does not delete content, profiles, payments or chat data.
-- Run after 000-foundation.sql and content-schema.sql, from one SQL Editor tab.

begin;

create extension if not exists pgcrypto;

-- Expand the central catalogue without creating a second addon database.
alter table public.content_items add column if not exists file_bucket text not null default 'content-files';
alter table public.content_items add column if not exists file_size bigint not null default 0;
alter table public.content_items add column if not exists min_engine_version text;
alter table public.content_items add column if not exists changelog text not null default '';
alter table public.content_items add column if not exists tags text[] not null default '{}';

alter table public.content_items drop constraint if exists content_items_content_type_check;
alter table public.content_items add constraint content_items_content_type_check
  check (content_type in ('addon','world','map','resource_pack','behavior_pack','texture_pack'));

create table if not exists public.content_versions (
  id uuid primary key default gen_random_uuid(),
  content_id uuid references public.content_items(id) on delete cascade,
  version text not null check (char_length(version) between 1 and 32),
  file_bucket text not null default 'content-files',
  file_path text,
  file_size bigint not null default 0,
  min_engine_version text,
  changelog text not null default '',
  is_published boolean not null default false,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (content_id, version)
);
create index if not exists content_versions_content_idx on public.content_versions(content_id, created_at desc);

-- Central cloud library. device_id keeps installation state device-aware.
create table if not exists public.user_library (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  content_id uuid not null references public.content_items(id) on delete cascade,
  version_id uuid references public.content_versions(id) on delete set null,
  device_id text not null default 'cloud' check (char_length(device_id) between 1 and 120),
  platform text not null default 'unknown' check (platform in ('website','desktop','mobile','unknown')),
  status text not null default 'owned' check (status in ('owned','downloaded','installed','removed')),
  install_path text,
  installed_at timestamptz,
  last_seen_at timestamptz,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, content_id, device_id)
);
create index if not exists user_library_user_updated_idx on public.user_library(user_id, updated_at desc);
create index if not exists user_library_device_idx on public.user_library(user_id, device_id, status);

-- Central download history; local managers may additionally keep a queue.
create table if not exists public.download_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  content_id uuid references public.content_items(id) on delete set null,
  version_id uuid references public.content_versions(id) on delete set null,
  platform text not null check (platform in ('website','desktop','mobile','unknown')),
  device_id text,
  status text not null default 'started' check (status in ('queued','started','paused','completed','failed','cancelled')),
  file_path text,
  bytes bigint not null default 0,
  total_bytes bigint not null default 0,
  error_message text,
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
create index if not exists download_history_user_created_idx on public.download_history(user_id, created_at desc);

-- Extend the existing download RPC so Website/Mobile downloads also appear in
-- the central cross-platform history without removing legacy content_downloads.
drop function if exists public.record_content_download(uuid);
create or replace function public.record_content_download(item_id uuid, p_platform text default 'website')
returns void language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Login required'; end if;
  insert into public.content_downloads(user_id, content_id) values(uid, item_id) on conflict do nothing;
  if found then
    update public.content_items set download_count = download_count + 1 where id = item_id;
  end if;
  insert into public.download_history(user_id, content_id, platform, device_id, status, completed_at)
  values(uid, item_id, case when p_platform in ('website','desktop','mobile','unknown') then p_platform else 'website' end, p_platform, 'completed', now());
end;
$$;
grant execute on function public.record_content_download(uuid, text) to authenticated;

-- Hybrid local/cloud installation audit trail.
create table if not exists public.installation_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  content_id uuid references public.content_items(id) on delete set null,
  version_id uuid references public.content_versions(id) on delete set null,
  platform text not null check (platform in ('desktop','mobile','website','unknown')),
  device_id text,
  action text not null check (action in ('installed','removed','updated','restored','detected')),
  status text not null default 'success' check (status in ('success','failed','rolled_back')),
  install_path text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists installation_history_user_created_idx on public.installation_history(user_id, created_at desc);

-- Preferences that are safe to synchronize across devices.
create table if not exists public.user_cloud_settings (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  language text not null default 'ar',
  profile_preferences jsonb not null default '{}'::jsonb,
  notification_preferences jsonb not null default '{}'::jsonb,
  privacy_preferences jsonb not null default '{}'::jsonb,
  subscription_preferences jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

-- Cursor/state for an idempotent device sync process.
create table if not exists public.device_sync_state (
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  platform text not null check (platform in ('website','desktop','mobile','unknown')),
  status text not null default 'synced' check (status in ('synced','syncing','offline','failed')),
  last_sync_at timestamptz,
  last_error text,
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id)
);

-- One cloud notification stream. Existing chat_notifications remains supported
-- for older installations; new ecosystem events use this table.
create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null default 'system',
  title text not null default 'AddonStation',
  body text not null default '',
  content_id uuid references public.content_items(id) on delete set null,
  actor_id uuid references public.profiles(id) on delete set null,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists user_notifications_user_idx on public.user_notifications(user_id, is_read, created_at desc);

-- Shared timestamp trigger.
create or replace function public.ecosystem_touch_updated_at()
returns trigger language plpgsql security invoker set search_path = public as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists user_library_touch_updated_at on public.user_library;
create trigger user_library_touch_updated_at before update on public.user_library
for each row execute procedure public.ecosystem_touch_updated_at();
drop trigger if exists cloud_settings_touch_updated_at on public.user_cloud_settings;
create trigger cloud_settings_touch_updated_at before update on public.user_cloud_settings
for each row execute procedure public.ecosystem_touch_updated_at();
drop trigger if exists device_sync_touch_updated_at on public.device_sync_state;
create trigger device_sync_touch_updated_at before update on public.device_sync_state
for each row execute procedure public.ecosystem_touch_updated_at();

-- RLS: each user sees/changes only their own ecosystem rows.
alter table public.content_versions enable row level security;
alter table public.user_library enable row level security;
alter table public.download_history enable row level security;
alter table public.installation_history enable row level security;
alter table public.user_cloud_settings enable row level security;
alter table public.device_sync_state enable row level security;
alter table public.user_notifications enable row level security;

grant select on public.content_versions to anon, authenticated;
grant select, insert, update, delete on public.user_library to authenticated;
grant select, insert, update on public.download_history to authenticated;
grant select, insert on public.installation_history to authenticated;
grant select, insert, update on public.user_cloud_settings to authenticated;
grant select, insert, update on public.device_sync_state to authenticated;
grant select, update on public.user_notifications to authenticated;

drop policy if exists ecosystem_public_versions_read on public.content_versions;
create policy ecosystem_public_versions_read on public.content_versions
for select to anon, authenticated using (is_published = true or created_by = auth.uid());

drop policy if exists ecosystem_library_owner on public.user_library;
create policy ecosystem_library_owner on public.user_library
for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists ecosystem_download_history_owner on public.download_history;
create policy ecosystem_download_history_owner on public.download_history
for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists ecosystem_install_history_owner on public.installation_history;
create policy ecosystem_install_history_owner on public.installation_history
for select to authenticated using (user_id = auth.uid());
drop policy if exists ecosystem_install_history_insert on public.installation_history;
create policy ecosystem_install_history_insert on public.installation_history
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists ecosystem_cloud_settings_owner on public.user_cloud_settings;
create policy ecosystem_cloud_settings_owner on public.user_cloud_settings
for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists ecosystem_sync_state_owner on public.device_sync_state;
create policy ecosystem_sync_state_owner on public.device_sync_state
for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists ecosystem_notifications_owner on public.user_notifications;
create policy ecosystem_notifications_owner on public.user_notifications
for select to authenticated using (user_id = auth.uid());
drop policy if exists ecosystem_notifications_mark_read on public.user_notifications;
create policy ecosystem_notifications_mark_read on public.user_notifications
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Realtime for cloud notifications and library changes where enabled.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    if not exists (
      select 1 from pg_publication_rel pr
      join pg_publication p on p.oid = pr.prpubid
      join pg_class c on c.oid = pr.prrelid
      join pg_namespace n on n.oid = c.relnamespace
      where p.pubname = 'supabase_realtime' and n.nspname = 'public' and c.relname = 'user_notifications'
    ) then alter publication supabase_realtime add table public.user_notifications; end if;
    if not exists (
      select 1 from pg_publication_rel pr
      join pg_publication p on p.oid = pr.prpubid
      join pg_class c on c.oid = pr.prrelid
      join pg_namespace n on n.oid = c.relnamespace
      where p.pubname = 'supabase_realtime' and n.nspname = 'public' and c.relname = 'user_library'
    ) then alter publication supabase_realtime add table public.user_library; end if;
  end if;
end $$;

commit;
