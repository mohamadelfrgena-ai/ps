-- AddonStation remaining storage setup
-- Run this in Supabase SQL Editor after the existing table schemas.
-- The current database already contains the main tables and content metadata;
-- this file creates the missing storage buckets and their RLS policies.
-- Safe to run more than once.

-- =========================================================
-- CONTENT MEDIA AND FILES
-- =========================================================
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

drop policy if exists "Managers view content files" on storage.objects;
create policy "Managers view content files"
on storage.objects for select to authenticated
using (bucket_id = 'content-files' and public.is_chat_moderator());

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
  and exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'creator'
  )
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

-- =========================================================
-- CREATOR MEDIA (old creator-files is retained for old submissions)
-- =========================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('creator-media', 'creator-media', true, 5242880, array['image/png','image/jpeg','image/webp']),
  ('creator-files', 'creator-files', false, null, null)
on conflict (id) do nothing;

drop policy if exists "Creators upload own preview media" on storage.objects;
create policy "Creators upload own preview media"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'creator-media'
  and (storage.foldername(name))[1] = auth.uid()::text
  and exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'creator'
  )
);

drop policy if exists "Public views creator media" on storage.objects;
create policy "Public views creator media"
on storage.objects for select to anon, authenticated
using (bucket_id = 'creator-media');

drop policy if exists "Creators upload own submission files" on storage.objects;
create policy "Creators upload own submission files"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'creator-files'
  and (storage.foldername(name))[1] = auth.uid()::text
  and exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'creator'
  )
);

drop policy if exists "Managers view creator submission files" on storage.objects;
create policy "Managers view creator submission files"
on storage.objects for select to authenticated
using (bucket_id = 'creator-files' and public.is_chat_moderator());

-- =========================================================
-- SITE ASSETS
-- =========================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'site-assets',
  'site-assets',
  true,
  5242880,
  array['image/png','image/jpeg','image/webp','image/gif']
)
on conflict (id) do nothing;

drop policy if exists "Public can view site assets" on storage.objects;
create policy "Public can view site assets"
on storage.objects for select to anon, authenticated
using (bucket_id = 'site-assets');

drop policy if exists "Managers can upload site assets" on storage.objects;
create policy "Managers can upload site assets"
on storage.objects for insert to authenticated
with check (bucket_id = 'site-assets' and public.is_chat_moderator());

drop policy if exists "Managers can update site assets" on storage.objects;
create policy "Managers can update site assets"
on storage.objects for update to authenticated
using (bucket_id = 'site-assets' and public.is_chat_moderator())
with check (bucket_id = 'site-assets' and public.is_chat_moderator());

drop policy if exists "Managers can delete site assets" on storage.objects;
create policy "Managers can delete site assets"
on storage.objects for delete to authenticated
using (bucket_id = 'site-assets' and public.is_chat_moderator());

-- =========================================================
-- PAYMENT RECEIPTS
-- =========================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'payment-receipts',
  'payment-receipts',
  false,
  5242880,
  array['image/png','image/jpeg','image/webp']
)
on conflict (id) do nothing;

drop policy if exists "Users upload own payment receipts" on storage.objects;
create policy "Users upload own payment receipts"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'payment-receipts'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users view own payment receipts" on storage.objects;
create policy "Users view own payment receipts"
on storage.objects for select to authenticated
using (
  bucket_id = 'payment-receipts'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Managers view payment receipts" on storage.objects;
create policy "Managers view payment receipts"
on storage.objects for select to authenticated
using (bucket_id = 'payment-receipts' and public.is_chat_moderator());

-- =========================================================
-- COMMUNITY CHAT FILES AND IMAGES
-- =========================================================
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('chat-uploads', 'chat-uploads', false, 10485760, null),
  ('chat-images', 'chat-images', false, 4194304, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

drop policy if exists "Users upload own chat files" on storage.objects;
create policy "Users upload own chat files"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'chat-uploads'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users view chat files" on storage.objects;
create policy "Users view chat files"
on storage.objects for select to authenticated
using (bucket_id = 'chat-uploads');

drop policy if exists "Users delete own chat files" on storage.objects;
create policy "Users delete own chat files"
on storage.objects for delete to authenticated
using (
  bucket_id = 'chat-uploads'
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or public.is_chat_moderator()
  )
);

drop policy if exists "Users upload own chat images" on storage.objects;
create policy "Users upload own chat images"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'chat-images'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users view chat images" on storage.objects;
create policy "Users view chat images"
on storage.objects for select to authenticated
using (bucket_id = 'chat-images');

drop policy if exists "Users delete own chat images" on storage.objects;
create policy "Users delete own chat images"
on storage.objects for delete to authenticated
using (
  bucket_id = 'chat-images'
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or public.is_chat_moderator()
  )
);
