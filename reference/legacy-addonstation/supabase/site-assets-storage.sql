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
drop policy if exists "Managers can upload site assets" on storage.objects;
drop policy if exists "Managers can update site assets" on storage.objects;
drop policy if exists "Managers can delete site assets" on storage.objects;

create policy "Public can view site assets"
on storage.objects for select to anon, authenticated
using (bucket_id = 'site-assets');

create policy "Managers can upload site assets"
on storage.objects for insert to authenticated
with check (bucket_id = 'site-assets' and public.is_chat_moderator());

create policy "Managers can update site assets"
on storage.objects for update to authenticated
using (bucket_id = 'site-assets' and public.is_chat_moderator())
with check (bucket_id = 'site-assets' and public.is_chat_moderator());

create policy "Managers can delete site assets"
on storage.objects for delete to authenticated
using (bucket_id = 'site-assets' and public.is_chat_moderator());
