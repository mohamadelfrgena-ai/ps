-- AddonStation Admin Chat Media
-- Audio/video are Admin-only. Uses existing chat_messages and Realtime.
-- Run once after chat tables are installed, while apps are closed.

begin;

alter table public.chat_messages add column if not exists message_type text not null default 'text';
alter table public.chat_messages add column if not exists media_bucket text not null default 'chat-media';
alter table public.chat_messages add column if not exists media_path text;
alter table public.chat_messages add column if not exists media_mime_type text;
alter table public.chat_messages add column if not exists media_duration integer not null default 0;
alter table public.chat_messages add column if not exists media_size bigint not null default 0;

alter table public.chat_messages drop constraint if exists chat_messages_message_type_check;
alter table public.chat_messages add constraint chat_messages_message_type_check
  check (message_type in ('text','sticker','audio','video'));

create or replace function public.is_chat_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid()
      and (role = 'admin' or is_admin = true)
  );
$$;

grant execute on function public.is_chat_admin() to authenticated;

-- Replace known chat insert policies so a normal member cannot insert media
-- even if they bypass the Android UI.
drop policy if exists "addon_chat_messages_insert" on public.chat_messages;
drop policy if exists "Users can send own messages" on public.chat_messages;
drop policy if exists "Users can send messages when not muted" on public.chat_messages;
drop policy if exists chat_complete_messages_insert on public.chat_messages;

create policy chat_admin_media_messages_insert
on public.chat_messages for insert to authenticated
with check (
  auth.uid() = user_id
  and status = 'published'
  and exists (select 1 from public.chat_channels c where c.id = channel_id and c.is_public = true)
  and (message_type in ('text','sticker') or public.is_chat_admin())
  and not exists (
    select 1 from public.chat_mutes m
    where m.user_id = auth.uid() and m.expires_at > now()
  )
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'chat-media', 'chat-media', false, 104857600,
  array['audio/webm','audio/ogg','audio/oga','audio/wav','audio/x-wav','audio/mpeg','audio/mp4','video/mp4','video/webm','video/quicktime']
)
on conflict (id) do nothing;

-- Supabase Storage already enables RLS on storage.objects. Do not ALTER that
-- managed table here; hosted projects can reject ownership-changing ALTER TABLE.
drop policy if exists chat_media_admin_upload on storage.objects;
create policy chat_media_admin_upload on storage.objects
for insert to authenticated
with check (bucket_id = 'chat-media' and public.is_chat_admin());

drop policy if exists chat_media_read_published on storage.objects;
create policy chat_media_read_published on storage.objects
for select to authenticated
using (
  bucket_id = 'chat-media'
  and exists (select 1 from public.chat_messages m where m.media_path = storage.objects.name and m.status = 'published')
);

drop policy if exists chat_media_admin_delete on storage.objects;
create policy chat_media_admin_delete on storage.objects
for delete to authenticated
using (bucket_id = 'chat-media' and public.is_chat_admin());

-- The RPC performs the Admin check again; the UI is never the authority.
create or replace function public.send_chat_media_message(
  p_channel_id uuid,
  p_message_type text,
  p_media_path text,
  p_media_mime_type text,
  p_media_duration integer default 0,
  p_file_size bigint default 0
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  new_id bigint;
begin
  if auth.uid() is null or not public.is_chat_admin() then
    raise exception 'Administrator permission required.';
  end if;
  if p_message_type not in ('audio','video') then
    raise exception 'Only audio or video media is accepted.';
  end if;
  if p_file_size <= 0 or p_file_size > 104857600 then
    raise exception 'Media file size is not allowed.';
  end if;
  if not exists (select 1 from public.chat_channels where id = p_channel_id and is_public = true) then
    raise exception 'This chat channel is not available.';
  end if;
  if p_media_path is null or position((p_channel_id::text || '/') in p_media_path) <> 1 then
    raise exception 'Invalid media path.';
  end if;
  insert into public.chat_messages(
    user_id, channel_id, content, message_type, media_bucket, media_path,
    media_mime_type, media_duration, media_size, status
  ) values (
    auth.uid(), p_channel_id, '', p_message_type, 'chat-media', p_media_path,
    p_media_mime_type, greatest(0, p_media_duration), p_file_size, 'published'
  ) returning id into new_id;
  return new_id;
end;
$$;

revoke all on function public.send_chat_media_message(uuid,text,text,text,integer,bigint) from public;
grant execute on function public.send_chat_media_message(uuid,text,text,text,integer,bigint) to authenticated;

create or replace function public.finalize_chat_media(
  p_message_id bigint,
  p_media_path text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null or not public.is_chat_admin() then
    raise exception 'Administrator permission required.';
  end if;
  if p_media_path is null or position('/' in p_media_path) = 0 then
    raise exception 'Invalid media path.';
  end if;
  update public.chat_messages
  set media_path = p_media_path
  where id = p_message_id and user_id = auth.uid() and message_type in ('audio','video');
end;
$$;

revoke all on function public.finalize_chat_media(bigint,text) from public;
grant execute on function public.finalize_chat_media(bigint,text) to authenticated;

commit;
