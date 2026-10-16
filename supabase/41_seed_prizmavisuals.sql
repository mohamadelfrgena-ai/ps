-- ============================================================================
--  AddonStation — 41) ربط المحتوى بملفات GitHub Releases (seed + upsert)
--
--  بعد تنفيذ 40_ps4_and_deals.sql، هذا الملف يسجّل/يحدّث صف المحتوى بحيث
--  يشير ps4_raw_url و pack_targets إلى ملفات الـ Release المرفوعة فعلياً:
--    https://github.com/mohamadelfrgena-ai/ps/releases/download/<tag>/<file>
--
--  طريقة الرفع (اختار واحدة):
--    أ) أوتوماتيكية: ضع content-staging/<slug>.publish.json ثم ادفع التغييرات
--       ⇒ ورشة «Publish content release» تولّد .raw/.zip وتربطها بالـ Release.
--    ب) يدوية بحسابك (للمستودع العام vae — تحميل بلا توكن):
--       python3 tools/publish_content.py <zip> --name "..." --repo mohamadelfrgena-ai/vae --direct
--
--  ثم غيّر الروابط في الأسفل لو لزم ونفّذ هذا الملف في Supabase → SQL Editor.
--  آمن لإعادة التنفيذ (upsert على slug).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) PrizmaVisuals 1.3.10222 — منتج حقيقي مرفوع على الـ Release:
--    tag: content-prizmavisuals-1.3.10222
--    أسماء الملفات مولّدة من manifest header.uuid (لا تصادم pack0/pack1 أبداً):
--      prizmavisuals-1-3-10_3bcccbf1.raw -> resource_packs/prizmavisuals-1-3-10_3bcccbf1
-- ---------------------------------------------------------------------------
insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive, exclusive_slot,
   ps4_raw_url, pack_targets)
values
  (
    'PrizmaVisuals',
    'prizmavisuals',
    'PrizmaVisuals 1.3.10222 — حزمة موارد بصرية لماينكرافت PS4/PS5. تُثبَّت تلقائياً في resource_packs مع تفعيل تلقائي بعد فتح ماينكرافت.',
    'addon',
    'other',
    'free',
    '1.3.10222',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-prizmavisuals-1.3.10222/prizmavisuals-cover.jpg',
    'content-prizmavisuals-1.3.10222/prizmavisuals-1-3-10_3bcccbf1.raw',
    21580000,
    0,
    true,
    true,
    true,
    1,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-prizmavisuals-1.3.10222/prizmavisuals-1-3-10_3bcccbf1.raw',
    '[
      {
        "target": "resource_packs",
        "dest_folder": "prizmavisuals-1-3-10_3bcccbf1",
        "raw_file": "prizmavisuals-1-3-10_3bcccbf1.raw",
        "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-prizmavisuals-1.3.10222/prizmavisuals-1-3-10_3bcccbf1.raw",
        "sha256": "a0e4c2b662adc1ca6202f08da2c6288bae28e1e27c831c17184e4cc039ca63d4"
      }
    ]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      is_featured  = excluded.is_featured,
      is_exclusive = excluded.is_exclusive,
      exclusive_slot = excluded.exclusive_slot,
      updated_at   = now();

-- ---------------------------------------------------------------------------
-- 2) مثال جاهز للنسخ عند إضافة أي محتوى جديد (عالم بحزمتين pack0 + pack1):
--    لاحظ pack_targets بمدخلين: resource + behavior، و dest_folder فريد لكل حزمة.
-- ---------------------------------------------------------------------------
-- insert into public.content_items (title, slug, description, content_type, plan, version,
--   cover_url, file_path, ps4_raw_url, pack_targets, is_published)
-- values (
--   'Sunset Village', 'sunset-village', 'عالم جديد', 'world', 'free', '1.0.0',
--   'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-sunset-village-1.0.0/cover.jpg',
--   'content-sunset-village-1.0.0/sunset-village_world_a1b2c3d4.raw',
--   'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-sunset-village-1.0.0/sunset-village_world_a1b2c3d4.raw',
--   '[{"target":"minecraftWorlds","dest_folder":"sunset-village_world_a1b2c3d4",
--      "raw_file":"sunset-village_world_a1b2c3d4.raw",
--      "ps4_raw_url":"https://github.com/mohamadelfrgena-ai/ps/releases/download/content-sunset-village-1.0.0/sunset-village_world_a1b2c3d4.raw",
--      "sha256":"..."}]'::jsonb,
--   true
-- ) on conflict (slug) do update set updated_at = now();

-- ---------------------------------------------------------------------------
-- 3) تحقّق: كل المحتوى المنشور مع روابطه ووجهاته
-- ---------------------------------------------------------------------------
select slug                                   as "المحتوى",
       title                                  as "الاسم",
       version                                as "الإصدار",
       ps4_raw_url                            as "رابط .raw",
       jsonb_array_length(pack_targets)       as "عدد الحزم",
       (select string_agg(p->>'target', ' + ')
          from jsonb_array_elements(pack_targets) p) as "الوجهات"
  from public.content_items
 where is_published
 order by created_at desc;

do $$
begin
  raise notice '✅ 41_seed_github_releases: تم ربط المحتوى بملفات GitHub Releases.';
end;
$$;
