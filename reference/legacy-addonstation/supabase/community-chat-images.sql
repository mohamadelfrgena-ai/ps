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
