# إعداد منظومة AddonStation الموحدة

الملف الأساسي:

```text
unified-ecosystem.sql
```

هذا الملف يضيف طبقة مزامنة مشتركة بدون إنشاء قاعدة بيانات ثانية:

- `content_versions`
- `user_library`
- `download_history`
- `installation_history`
- `user_cloud_settings`
- `device_sync_state`
- `user_notifications`

## التشغيل

1. أوقف Website وDesktop وMobile أثناء تشغيل التعديلات.
2. افتح Supabase Dashboard ثم SQL Editor.
3. أغلق تبويبات SQL الأخرى.
4. شغّل `000-foundation.sql` و`content-schema.sql` إذا لم يكونا منفذين.
5. شغّل `unified-ecosystem.sql` مرة واحدة.
6. تحقق من الجداول والصلاحيات قبل النشر.

لا تستخدم `SUPABASE_SERVICE_ROLE_KEY` داخل أي تطبيق. تنفيذ DDL يحتاج SQL Editor بصلاحية مالك المشروع؛ مفتاح `anon/public` مخصص لطلبات العميل ولا ينفذ إنشاء الجداول.

## قاعدة المزامنة

- `auth.users.id` هو هوية المستخدم الوحيدة.
- `content_items.id` هو المحتوى المركزي الوحيد.
- `device_id` و`platform` يميزان حالة الجهاز ولا يحولان الحالة المحلية إلى تثبيت على جهاز آخر.
- بيانات الخادم هي المصدر عند التعارض.
- فشل المزامنة لا يحذف بيانات الجهاز ولا ملفات Minecraft.
