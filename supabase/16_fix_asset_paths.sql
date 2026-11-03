-- ============================================================================
--  AddonStation Mobile — 16) توحيد مسارات الأصول (نسخة **آمنة**)
-- ============================================================================
--
--  ⚠️ اقرأ هذا قبل التنفيذ (مهم):
--
--  في مخزنك، ملفات قسم «Profiles» الـ26 مرفوعة فعلاً على مسار **مكرّر**:
--        site-assets/profiles/profiles/onefree.png   ✅ موجود
--        site-assets/profiles/onefree.png            ❌ غير موجود
--  وهذا ما تؤكّده السجلات (`assets-manifest.json`) وفاحص القاعدة (99).
--
--  ⇒ لذلك **لا يجوز** تحويل `profiles/profiles/x.jpg` إلى `profiles/x.jpg`
--    تحويلاً أعمى: الصور الـ26 ستبقى بلا ملف وتظهر الصورة الافتراضية بدلها.
--
--  هذه النسخة تُصحّح المسار **فقط** إذا كان الملف موجوداً فعلاً في المخزن
--  على المسار النظيف، ولا تلمس غيره ⇒ **آمنة تماماً** (لا تغيّر شيئاً في
--  حالتك الحالية، لأن الملفات على المسار المكرّر).
--
--  ⚠️ لا تُشغّل هذه النسخة القديمة إن وُجدت في مكان آخر — هذه هي المعتمدة.
--
--  آمن لإعادة التنفيذ · يُنفَّذ مرة واحدة (اختياري — لا شيء مطلوب منك الآن).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ١) أصول المكتبة: صُحّح المسار المكرّر إن كان الملف موجوداً على المسار النظيف
-- ---------------------------------------------------------------------------
update public.site_assets a
   set file_url = regexp_replace(a.file_url,
                                 '^(profiles|banners|items|stickers)/(\1)/', '\1/')
 where a.file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'
   -- الملف موجود فعلاً على المسار النظيف في المخزن
   and exists (
        select 1 from storage.objects o
         where o.bucket_id = 'site-assets'
           and o.name = regexp_replace(a.file_url,
                                       '^(profiles|banners|items|stickers)/(\1)/', '\1/'))
   -- ولا يوجد صف آخر بنفس المسار النظيف (حتى لا نكسر الفهرس الفريد)
   and not exists (
        select 1 from public.site_assets b
         where b.id <> a.id
           and b.file_url = regexp_replace(a.file_url,
                                           '^(profiles|banners|items|stickers)/(\1)/', '\1/'));

-- ---------------------------------------------------------------------------
-- ٢) صور الحسابات: نفس القاعدة
-- ---------------------------------------------------------------------------
update public.profiles p
   set avatar_url = regexp_replace(p.avatar_url,
                                   '^(profiles|banners|items|stickers)/(\1)/', '\1/'),
       updated_at = now()
 where p.avatar_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'
   and exists (
        select 1 from storage.objects o
         where o.bucket_id = 'site-assets'
           and o.name = regexp_replace(p.avatar_url,
                                       '^(profiles|banners|items|stickers)/(\1)/', '\1/'));

update public.profiles p
   set banner_url = regexp_replace(p.banner_url,
                                   '^(profiles|banners|items|stickers)/(\1)/', '\1/'),
       updated_at = now()
 where p.banner_url is not null
   and p.banner_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'
   and exists (
        select 1 from storage.objects o
         where o.bucket_id = 'site-assets'
           and o.name = regexp_replace(p.banner_url,
                                       '^(profiles|banners|items|stickers)/(\1)/', '\1/'));

-- ---------------------------------------------------------------------------
-- ٣) تحقّق: كم مساراً مكرّراً بقي؟ (الطبيعي في حالتك: يبقى كما هو لأن الملفات
--    على المسار المكرّر — وهذا **صحيح** ومقصود)
-- ---------------------------------------------------------------------------
select count(*) filter (where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/')
         as "مسارات مكرّرة في الأصول",
       count(*) filter (where file_url ~ '^(profiles|banners|items|stickers)/(profiles|banners|items|stickers)/'
                          and exists (select 1 from storage.objects o
                                       where o.bucket_id = 'site-assets'
                                         and o.name = regexp_replace(file_url,
                                             '^(profiles|banners|items|stickers)/(\1)/', '\1/')))
         as "منها ملفه موجود على المسار النظيف"
  from public.site_assets;
