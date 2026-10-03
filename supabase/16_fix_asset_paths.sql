-- -------------------------------------------------------------------
-- 16) إصلاح مسارات الأصول المكرّرة المجلد
-- -------------------------------------------------------------------
-- المشكلة: سكربت التعبئة أضاف اسم المجلد مرّتين، فصارت المسارات هكذا
--   profiles/profiles/onefree.png
--   profiles/profiles/default-profile.jpg
--   profiles/profiles/minecraft-rose-painting.jpg
-- بدل
--   profiles/onefree.png
-- النتيجة: رابط الصورة يفشل (404) ⇒ تبقى خانة الصورة فارغة حتى ينتهي وقت
-- الطلب، فيبدو للمستخدم أن «تحميل صورة البروفايل والبانر والأدوات بطيء» أو ثقيل.
--
-- هذا السكربت ينظّف المسارات في قاعدة البيانات. (التطبيق أيضاً يطبّع المسار
-- عند بناء الرابط كشبكة أمان، فلو لم تُشغّل هذا السكربت بقي التطبيق يعمل.)
--
-- آمن للتكرار: لا يمسّ المسارات الصحيحة، ولا يغيّر أي جدول أو دالة.

-- 1) أصول الموقع
update public.site_assets
   set file_url = regexp_replace(file_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')
 where file_url ~ '^(profiles/profiles/|banners/banners/|items/items/|stickers/stickers/)';

-- 2) صورة البروفايل والبانر المختاران لكل مستخدم
update public.profiles
   set avatar_url = regexp_replace(avatar_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')
 where avatar_url is not null
   and avatar_url ~ '^(profiles/profiles/|banners/banners/|items/items/|stickers/stickers/)';

update public.profiles
   set banner_url = regexp_replace(banner_url, '^(profiles|banners|items|stickers)/(\1)/', '\1/')
 where banner_url is not null
   and banner_url ~ '^(profiles/profiles/|banners/banners/|items/items/|stickers/stickers/)';

-- 3) التحقّق: يجب أن يخرج صفر صفوف
select count(*) as remaining_broken_paths
  from public.site_assets
 where file_url ~ '^(profiles/profiles/|banners/banners/|items/items/|stickers/stickers/)';

select asset_type, count(*) as total,
       count(*) filter (where file_url like '%/%/%' ) as in_subfolder
  from public.site_assets
 where enabled = true
 group by asset_type
 order by asset_type;
