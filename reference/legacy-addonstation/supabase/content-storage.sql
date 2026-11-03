-- AddonStation shared content storage
-- Run after content-schema.sql. The website and mobile admin use these same buckets.

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

drop policy if exists "Managers update content files" on storage.objects;
create policy "Managers update content files"
on storage.objects for update to authenticated
using (bucket_id = 'content-files' and public.is_chat_moderator())
with check (bucket_id = 'content-files' and public.is_chat_moderator());

drop policy if exists "Managers delete content files" on storage.objects;
create policy "Managers delete content files"
on storage.objects for delete to authenticated
using (bucket_id = 'content-files' and public.is_chat_moderator());

drop policy if exists "Users download published content files" on storage.objects;
create policy "Users download published content files"
on storage.objects for select to authenticated
using (
  bucket_id = 'content-files'
  and exists (
    select 1
    from public.content_items
    where content_items.file_path = storage.objects.name
      and content_items.is_published = true
  )
);
