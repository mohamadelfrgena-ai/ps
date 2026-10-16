-- ============================================================================
--  AddonStation — 41) قالب ربط المحتوى بملفات GitHub Releases (انسخ وعدّل)
--
--  يُستخدم عند إضافة أي إضافة/عالم جديد للمتجر. انسخ الكتلة، استبدل
--  القيم بين [أقواس]، ثم نفّذ الملف في Supabase → SQL Editor.
--
--  مصدر الروابط: ورشة «Publish content release» تولّد الملفات وتربطها بالـ
--  Release تلقائياً، وتُنتج ملف <slug>.supabase.json جاهزاً بالقيم الحقيقية
--  (أو: python3 tools/publish_content.py <zip> --name "..." --trigger).
--
--  آمن لإعادة التنفيذ (upsert على slug).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) صف محتوى جاهز للنسخ — استبدل القيم بين [أقواس]
-- ---------------------------------------------------------------------------
-- insert into public.content_items
--   (title, slug, description, content_type, category, plan, version,
--    cover_url, file_path, file_size, ac_price,
--    is_published, is_featured, is_exclusive, exclusive_slot,
--    ps4_raw_url, pack_targets)
-- values
--   (
--     '[الاسم المعروض]',                          -- title
--     '[slug]',                                   -- slug (حروف صغيرة + شرطات)
--     '[الوصف]',                                  -- description
--     'addon',                                    -- addon | world | resource_pack | map | skin
--     'other',                                    -- adventure|survival|pvp|building|skins|other
--     'free',                                     -- free | epic | legendary
--     '[1.0.0]',                                  -- version
--     'https://github.com/mohamadelfrgena-ai/ps/releases/download/[TAG]/[cover].jpg',
--     '[TAG]/[file].raw',                         -- file_path (مسار فهرسي)
--     [22000000],                                 -- file_size (بايت)
--     0,                                          -- ac_price (0 = مجاني)
--     true, true, true, 1,                        -- منشور/مميز/حصري/مكان الحصري
--     'https://github.com/mohamadelfrgena-ai/ps/releases/download/[TAG]/[file].raw',
--     '[{"target":"resource_packs","dest_folder":"[slug]_[uuid8]",
--        "raw_file":"[file].raw",
--        "ps4_raw_url":"https://github.com/mohamadelfrgena-ai/ps/releases/download/[TAG]/[file].raw",
--        "sha256":"[sha256]"}]'::jsonb
--   )
-- on conflict (slug) do update
--   set title        = excluded.title,
--      description   = excluded.description,
--      version       = excluded.version,
--      cover_url     = excluded.cover_url,
--      file_path     = excluded.file_path,
--      file_size     = excluded.file_size,
--      ps4_raw_url   = excluded.ps4_raw_url,
--      pack_targets  = excluded.pack_targets,
--      is_published  = true,
--      is_featured   = excluded.is_featured,
--      is_exclusive  = excluded.is_exclusive,
--      exclusive_slot = excluded.exclusive_slot,
--      updated_at    = now();

-- ---------------------------------------------------------------------------
-- 2) ملاحظات مهمة
-- ---------------------------------------------------------------------------
-- * pack_targets بمدخل لكل حزمة (عند وجود pack0 + pack1 مثلًا):
--   resource  -> "target": "resource_packs"
--   data      -> "target": "behavior_packs"
--   عالم      -> "target": "minecraftWorlds"
--   و dest_folder لكل حزمة = <slug>_<uuid8> (يولّده tools/pack_addon.py تلقائياً
--   من manifest header.uuid — لا تصادم بين pack0/pack1 أبداً).
--
-- * لو كانت الملفات على مستودع عام (مثل mohamadelfrgena-ai/vae) فالتحميل
--   مباشر بلا توكن؛ وإن كانت على مستودع خاص فدالة download-content تتولى
--   التحميل بمفتاح GITHUB_TOKEN في أسرار الدالة.

-- ---------------------------------------------------------------------------
-- 3) تحقّق: كل المحتوى المنشور مع روابطه ووجهاته
-- ---------------------------------------------------------------------------
select slug                             as "المحتوى",
       title                            as "الاسم",
       version                          as "الإصدار",
       ps4_raw_url                      as "رابط .raw",
       jsonb_array_length(pack_targets) as "عدد الحزم",
       (select string_agg(p->>'target', ' + ')
          from jsonb_array_elements(pack_targets) p) as "الوجهات"
  from public.content_items
 where is_published
 order by created_at desc;

do $$
begin
  raise notice '✅ 41_seed_content_template: قالب ربط المحتوى بـ GitHub Releases جاهز.';
end;
$$;
