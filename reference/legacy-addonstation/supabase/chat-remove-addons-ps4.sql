-- AddonStation Chat: remove exactly #addons and #ps4
-- Safe, targeted migration. It never deletes users, profiles, auth data,
-- messages from other channels, Realtime publication, or moderator policies.
-- Run from ONE Supabase SQL Editor tab while the apps are closed.

begin;

create temporary table _addonstation_channels_to_remove (
  id uuid primary key,
  slug text not null
) on commit drop;

insert into _addonstation_channels_to_remove (id, slug)
select id, slug
from public.chat_channels
where slug in ('addons', 'ps4');

-- Remove only dependent records whose message belongs to these two channels.
do $$
declare
  table_name text;
  dependent_tables text[] := array[
    'chat_images',
    'chat_attachments',
    'chat_reactions',
    'chat_reports',
    'chat_translations',
    'chat_notifications'
  ];
begin
  foreach table_name in array dependent_tables loop
    if to_regclass('public.' || table_name) is not null then
      execute format(
        'delete from public.%I where message_id in (select id from public.chat_messages where channel_id in (select id from pg_temp._addonstation_channels_to_remove))',
        table_name
      );
    end if;
  end loop;
end $$;

-- chat_messages.channel_id is ON DELETE RESTRICT in existing migrations, so
-- delete only messages belonging to the two removed channels before channels.
delete from public.chat_messages
where channel_id in (select id from _addonstation_channels_to_remove);

-- Membership rows belong only to the two removed channel IDs.
do $$
begin
  if to_regclass('public.chat_channel_members') is not null then
    execute 'delete from public.chat_channel_members where channel_id in (select id from pg_temp._addonstation_channels_to_remove)';
  end if;
end $$;

-- Delete exactly these two channel rows; no replacement channels are created.
delete from public.chat_channels
where id in (select id from _addonstation_channels_to_remove);

-- Safety check: the target slugs must be gone, while every other channel stays.
do $$
begin
  if exists (select 1 from public.chat_channels where slug in ('addons', 'ps4')) then
    raise exception 'Target chat channels were not fully removed.';
  end if;
end $$;

commit;

-- Realtime remains enabled on chat_messages/chat_reactions. This migration does
-- not alter publications, RLS, moderator roles or any other channel.
