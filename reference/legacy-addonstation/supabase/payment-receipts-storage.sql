insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('payment-receipts','payment-receipts',false,5242880,array['image/png','image/jpeg','image/webp'])
on conflict (id) do nothing;

drop policy if exists "Users upload own payment receipts" on storage.objects;
drop policy if exists "Users view own payment receipts" on storage.objects;
drop policy if exists "Managers view payment receipts" on storage.objects;

create policy "Users upload own payment receipts" on storage.objects for insert to authenticated
with check (bucket_id='payment-receipts' and (storage.foldername(name))[1]=auth.uid()::text);

create policy "Users view own payment receipts" on storage.objects for select to authenticated
using (bucket_id='payment-receipts' and (storage.foldername(name))[1]=auth.uid()::text);

create policy "Managers view payment receipts" on storage.objects for select to authenticated
using (bucket_id='payment-receipts' and public.is_chat_moderator());
