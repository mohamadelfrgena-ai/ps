-- AddonStation shared backend sync (one-time setup)
-- Run in Supabase SQL Editor after:
--   1) 000-foundation.sql
--   2) content-schema.sql
--   3) creator-program.sql (if Creator submissions are enabled)
--
-- This file is safe to run more than once. It does not create a second
-- content system: website and mobile both use public.content_items.

-- Shared content metadata and PostgREST privileges
alter table public.content_items
  add column if not exists category text not null default 'general';
alter table public.content_items
  add column if not exists version text not null default '1.0.0';
create index if not exists content_items_category_idx on public.content_items(category);

-- Creator submissions carry the same metadata before approval when that
-- optional module has already been installed.
do $$
begin
  if to_regclass('public.creator_submissions') is not null then
    alter table public.creator_submissions add column if not exists category text not null default 'general';
    alter table public.creator_submissions add column if not exists version text not null default '1.0.0';
  end if;
end
$$;

grant select on public.content_items to anon, authenticated;
grant insert, update, delete on public.content_items to authenticated;
grant select, insert, update, delete on public.content_favorites to authenticated;
grant select on public.content_reviews to anon, authenticated;
grant insert, update, delete on public.content_reviews to authenticated;
grant select, insert on public.content_downloads to authenticated;
grant execute on function public.record_content_view(uuid) to anon, authenticated;
grant execute on function public.record_content_download(uuid) to authenticated;

-- Shared content media and file buckets
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('content-media', 'content-media', true, 10485760, array['image/png','image/jpeg','image/webp','image/gif']),
  ('content-files', 'content-files', false, null, null)
on conflict (id) do nothing;

drop policy if exists "Public can view content media" on storage.objects;
create policy "Public can view content media"
on storage.objects for select to anon, authenticated
using (bucket_id = 'content-media');

drop policy if exists "Managers upload content media" on storage.objects;
create policy "Managers upload content media"
on storage.objects for insert to authenticated
with check (bucket_id = 'content-media' and public.is_chat_moderator());

drop policy if exists "Managers update content media" on storage.objects;
create policy "Managers update content media"
on storage.objects for update to authenticated
using (bucket_id = 'content-media' and public.is_chat_moderator())
with check (bucket_id = 'content-media' and public.is_chat_moderator());

drop policy if exists "Managers delete content media" on storage.objects;
create policy "Managers delete content media"
on storage.objects for delete to authenticated
using (bucket_id = 'content-media' and public.is_chat_moderator());

drop policy if exists "Managers upload content files" on storage.objects;
create policy "Managers upload content files"
on storage.objects for insert to authenticated
with check (bucket_id = 'content-files' and public.is_chat_moderator());

drop policy if exists "Managers update content files" on storage.objects;
create policy "Managers update content files"
on storage.objects for update to authenticated
using (bucket_id = 'content-files' and public.is_chat_moderator())
with check (bucket_id = 'content-files' and public.is_chat_moderator());

drop policy if exists "Managers delete content files" on storage.objects;
create policy "Managers delete content files"
on storage.objects for delete to authenticated
using (bucket_id = 'content-files' and public.is_chat_moderator());

drop policy if exists "Creators upload submitted content files" on storage.objects;
create policy "Creators upload submitted content files"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'content-files'
  and (storage.foldername(name))[1] = 'creators'
  and (storage.foldername(name))[2] = auth.uid()::text
  and exists (select 1 from public.profiles where id = auth.uid() and role = 'creator')
);

drop policy if exists "Users download published content files" on storage.objects;
create policy "Users download published content files"
on storage.objects for select to authenticated
using (
  bucket_id = 'content-files'
  and exists (
    select 1 from public.content_items
    where content_items.file_path = storage.objects.name
      and content_items.is_published = true
  )
);

-- Public creator directory and follow state
grant select on public.profiles to anon;
drop policy if exists "Public can view creator profiles" on public.profiles;
create policy "Public can view creator profiles"
on public.profiles for select to anon
using (role = 'creator');

create table if not exists public.creator_follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  creator_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, creator_id),
  check (follower_id <> creator_id)
);
create index if not exists creator_follows_creator_idx
  on public.creator_follows (creator_id, created_at desc);
alter table public.creator_follows enable row level security;
grant select on public.creator_follows to authenticated;
grant insert, delete on public.creator_follows to authenticated;

drop policy if exists "Users view own creator follows" on public.creator_follows;
create policy "Users view own creator follows"
on public.creator_follows for select to authenticated
using (follower_id = auth.uid());

drop policy if exists "Users follow creators" on public.creator_follows;
create policy "Users follow creators"
on public.creator_follows for insert to authenticated
with check (
  follower_id = auth.uid()
  and exists (select 1 from public.profiles where id = creator_id and role = 'creator')
);

drop policy if exists "Users unfollow creators" on public.creator_follows;
create policy "Users unfollow creators"
on public.creator_follows for delete to authenticated
using (follower_id = auth.uid());

create or replace function public.creator_followers_count(p_creator_id uuid)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
  select count(*)
  from public.creator_follows f
  join public.profiles p on p.id = f.creator_id
  where f.creator_id = p_creator_id and p.role = 'creator';
$$;
grant execute on function public.creator_followers_count(uuid) to anon, authenticated;
