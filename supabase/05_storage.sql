-- ============================================================================
--  AddonStation Mobile — 05) مخازن الملفات (Storage) وسياساتها
--  الملفات المدفوعة والتذاكر والأوسائط: خاصة + روابط قصيرة الأجل (60 / 300 ثانية)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) إنشاء المخازن
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('content-files',    'content-files',    false, 536870912,
      array['application/zip','application/x-zip-compressed','application/octet-stream','application/java-archive','application/x-rar-compressed']),
  ('payment-receipts', 'payment-receipts', false, 10485760,
      array['image/png','image/jpeg','image/webp','application/pdf']),
  ('chat-images',      'chat-images',      false, 4194304,
      array['image/png','image/jpeg','image/webp']),
  ('chat-media',       'chat-media',       false, 104857600,
      array['audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/wav','video/mp4','video/webm','video/quicktime']),
  ('creator-media',    'creator-media',    false, 209715200,
      array['image/png','image/jpeg','image/webp','application/zip','application/octet-stream']),
  ('site-assets',      'site-assets',      true,  5242880,
      array['image/png','image/jpeg','image/webp','image/gif','image/svg+xml'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- ---------------------------------------------------------------------------
-- 2) ملفات المحتوى المدفوع: تُقرأ فقط لمن يملك الصلاحية (تحددها RLS حسب خطته)
-- ---------------------------------------------------------------------------
drop policy if exists "content_files_read" on storage.objects;
create policy "content_files_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'content-files'
    and exists (
      select 1 from public.content_items ci
      where ci.file_path = storage.objects.name
        and (ci.created_by = auth.uid()
             or public.is_admin()
             or (ci.is_published and public.can_access_item(ci.id, auth.uid())))
    )
  );

drop policy if exists "content_files_admin_write" on storage.objects;
create policy "content_files_admin_write" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'content-files' and (public.is_admin() or public.current_role_of() = 'creator'));

drop policy if exists "content_files_admin_update" on storage.objects;
create policy "content_files_admin_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'content-files' and (public.is_admin() or public.current_role_of() = 'creator'));

drop policy if exists "content_files_admin_delete" on storage.objects;
create policy "content_files_admin_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'content-files' and public.is_admin());

-- ---------------------------------------------------------------------------
-- 3) إيصالات الدفع: المستخدم يرفع في مجلده فقط ويقرأ ملفاته، والإدارة تقرأ الكل
-- ---------------------------------------------------------------------------
drop policy if exists "receipts_insert_own" on storage.objects;
create policy "receipts_insert_own" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'payment-receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "receipts_read" on storage.objects;
create policy "receipts_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'payment-receipts'
    and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
  );

drop policy if exists "receipts_update_own" on storage.objects;
create policy "receipts_update_own" on storage.objects
  for update to authenticated
  using (bucket_id = 'payment-receipts'
         and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin()));

-- ---------------------------------------------------------------------------
-- 4) صور ومقاطع الشات (≤4MB للصور و ≤100MB للأوسائط)
-- ---------------------------------------------------------------------------
drop policy if exists "chat_images_insert" on storage.objects;
create policy "chat_images_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'chat-images' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "chat_images_read" on storage.objects;
create policy "chat_images_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'chat-images');

-- الوسائط (فيديو · صوت · تسجيل صوتي) للأدمن فقط: لا يقبل المخزن رفعها من غيره،
-- فيُمنع الإرسال من أصله حتى لو تجاوز أحد الواجهة. (طلب المستخدم)
drop policy if exists "chat_media_insert" on storage.objects;
create policy "chat_media_insert" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'chat-media'
    and (storage.foldername(name))[1] = auth.uid()::text
    and public.is_admin(auth.uid())
  );

drop policy if exists "chat_media_read" on storage.objects;
create policy "chat_media_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'chat-media');

-- ---------------------------------------------------------------------------
-- 5) ملفات المبدعين
-- ---------------------------------------------------------------------------
drop policy if exists "creator_media_insert" on storage.objects;
create policy "creator_media_insert" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'creator-media'
    and (public.current_role_of() in ('creator','admin'))
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "creator_media_read" on storage.objects;
create policy "creator_media_read" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'creator-media'
    and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
  );

-- ---------------------------------------------------------------------------
-- 6) أصول الموقع العامة (الأغلفة/الأفاتارات/الملصقات/الأدوات) — قراءة عامة،
--    الكتابة للإدارة في أي مسار، وللمبدع داخل مجلد أغلفته items/<uid>/… فقط
-- ---------------------------------------------------------------------------
drop policy if exists "site_assets_read" on storage.objects;
create policy "site_assets_read" on storage.objects
  for select using (bucket_id = 'site-assets');

drop policy if exists "site_assets_admin_write" on storage.objects;
create policy "site_assets_admin_write" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'site-assets' and public.is_admin());

-- أغلفة المحتوى: المبدع يكتب داخل مجلده فقط (items/<uid>/…) — أما الإدارة فمسارها مفتوح
drop policy if exists "site_assets_creator_write" on storage.objects;
create policy "site_assets_creator_write" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'site-assets'
    and public.current_role_of() in ('creator','admin')
    and (storage.foldername(name))[1] = 'items'
    and (storage.foldername(name))[2] = auth.uid()::text
  );

drop policy if exists "site_assets_admin_update" on storage.objects;
create policy "site_assets_admin_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'site-assets' and public.is_admin());

drop policy if exists "site_assets_admin_delete" on storage.objects;
create policy "site_assets_admin_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'site-assets' and public.is_admin());
