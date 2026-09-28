# إصلاح عدم إرسال رسائل الشات

إذا ضغطت على زر الإرسال ولا تظهر الرسالة، يجب تجهيز Supabase بالترتيب التالي.

## 1. إعداد الموقع

في مجلد المشروع:

```bash
cp .env.example .env
```

ثم ضع بيانات المشروع العامة فقط:

```env
PUBLIC_SUPABASE_URL=https://your-project.supabase.co
PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-public-key
```

بعد ذلك أعد تشغيل Astro:

```bash
npm run dev
```

إذا كان الموقع مبنيًا مسبقًا، شغّل `npm run build` من جديد بعد إنشاء `.env`.

## 2. تشغيل ملفات قاعدة البيانات

من Supabase افتح **SQL Editor** وشغّل الملفات بالترتيب:

1. `000-foundation.sql`
2. `chat-schema.sql`
3. `chat-moderation.sql`
4. `community-chat-phase1.sql`
5. `chat-send-rpc.sql`
6. `community-chat-images.sql`

الملف `000-foundation.sql` ينشئ جدول `profiles` ويضيف Trigger لإنشاء ملف المستخدم تلقائيًا بعد التسجيل.

## 3. اختبار الحساب

- أنشئ حسابًا من الموقع.
- تأكد من تأكيد البريد إذا كان تفعيل البريد مطلوبًا في Supabase.
- سجّل الدخول.
- افتح `/community`.
- اختر قناة، اكتب رسالة قصيرة، ثم اضغط إرسال.

## 4. إذا ظهر خطأ

النسخة المعدلة تعرض رسالة مفهومة بدل بقاء الصفحة صامتة. المعاني الشائعة:

- **إعداد Supabase مطلوب:** ملف `.env` ناقص أو لم تتم إعادة تشغيل الموقع.
- **جدول الدردشة غير موجود:** لم يتم تشغيل ملفات SQL.
- **لا تملك صلاحية إرسال الرسائل:** مشكلة في RLS أو لم يتم تسجيل الدخول.
- **إعداد القناة غير مكتمل:** لم يتم تشغيل `community-chat-phase1.sql`.

لا تضع `SUPABASE_SERVICE_ROLE_KEY` داخل الموقع أو في ملف `.env` الذي سيُرفع إلى الاستضافة.
