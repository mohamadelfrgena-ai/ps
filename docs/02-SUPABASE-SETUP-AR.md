# إنشاء مشروع Supabase وربطه بالتطبيق — خطوة بخطوة

> هذا الدليل ينفّذ كل ما تحتاجه الوثائق الثلاث من جهة الخادم: قاعدة البيانات، الأمان (RLS)، الدوال، التخزين، والبيانات الأولية.
> الوقت المتوقع: 20–30 دقيقة.

---

## الجزء 1 — إنشاء الحساب والمشروع

1. افتح **https://supabase.com** واضغط **Start your project**.
2. سجّل الدخول بحساب GitHub أو Google أو بالبريد الإلكتروني (الخطة المجانية تكفي للتطوير والاختبار).
3. اضغط **New project** واختر **Organization** (أنشئ واحدة إن لم توجد، الاسم مثلاً `AddonStation`).
4. املأ:
   - **Name:** `addonstation`
   - **Database Password:** اضغط **Generate a password** ثم **احفظه في مكان آمن** (لن يظهر مرة أخرى).
   - **Region:** اختر الأقرب لك جغرافياً — لليبيا وشمال إفريقيا الأنسب عادة **West EU (Paris)** أو **Central EU (Frankfurt)**.
   - **Plan:** Free.
5. اضغط **Create new project** وانتظر 1–3 دقائق حتى تجهز القاعدة.

---

## الجزء 2 — نسخ مفاتيح الاتصال

1. من القائمة الجانبية: **Project Settings** (⚙️) → **API**.
2. انسخ القيمتين:
   - **Project URL** — مثال: `https://abcdefghijklm.supabase.co`
   - **anon public** (المفتاح العام) — سلسلة طويلة تبدأ بـ `eyJ...`
3. ⛔ **لا تنسخ** `service_role` أبداً إلى التطبيق. هذا المفتاح يلغي كل الحماية؛ يُستخدم فقط داخل Edge Functions على الخادم.

---

## الجزء 3 — تنفيذ ملفات SQL (بالترتيب)

افتح **SQL Editor** في Supabase، ثم نفّذ الملفات واحداً تلو الآخر بنفس هذا الترتيب (انسخ محتوى الملف والصقه ثم اضغط Run):

| # | الملف | ماذا يفعل |
|---|-------|-----------|
| 1 | `supabase/01_schema.sql` | 33 جدولاً: الملفات الشخصية، الخطط، المدفوعات، المحتوى، AC، الشات، المبدعون، الإدارة |
| 2 | `supabase/02_functions_core.sql` | دوال الخطة الفعلية، التحميل، المفضلة، AC، المدفوعات، تعديل الملف، إدارة المحتوى والإحصاءات |
| 3 | `supabase/03_functions_chat.sql` | الشات: إرسال الرسائل + الفلترة التلقائية + الكتم/الحظر/البلاغات + الإشعارات + المبدعون |
| 4 | `supabase/04_rls.sql` | سياسات الأمان (من يقرأ/يكتب ماذا) + سحب صلاحيات الكتابة المباشرة |
| 5 | `supabase/05_storage.sql` | المخازن الستة + سياسات الملفات (محتوى مدفوع، إيصالات، صور/أوسائط الشات، ملفات المبدعين، أصول عامة) |
| 6 | `supabase/06_seed.sql` | الخطط، طرق الدفع، مهام AC، حزم الملصقات الخمس، **13 غرفة**، قواعد المجتمع بـ8 لغات، إعدادات الفلترة |
| 7 | `supabase/07_seed_assets.sql` | **105 أداة شات** (104 أسطورية + Enchanted Bedrock للإدارة) و**181 ملصقاً** موزعة على الحزم الخمس |
| 8 | `supabase/09_gateway_functions.sql` | دوال بوابات الدفع الإلكتروني (اختياري — للاستخدام لاحقاً مع Stripe) |

بعد ذلك — وبعد أن تنشئ حسابك من داخل التطبيق — نفّذ `supabase/08_admin_bootstrap.sql` (بعد تعديل البريد داخله) لتصبح **Admin** وتظهر لك لوحة الإدارة.

> 💡 نصيحة: إن ظهر خطأ في أحد الملفات، فقد يكون بسبب تشغيله مرتين؛ كل الملفات مكتوبة لتكون آمنة لإعادة التنفيذ (idempotent).

---

## الجزء 4 — ضبط المصادقة (Auth)

من **Authentication → Providers → Email**:
- تأكد أن **Email** مفعّل.
- **Confirm email**:
  - أثناء الاختبار: أوقفه (**OFF**) لتسجيل الدخول فوراً بعد التسجيل.
  - قبل النشر الحقيقي: شغّله (**ON**) ليتحقق المستخدم من بريده.

من **Authentication → URL Configuration**:
- **Site URL:** `addonstation://` (أو رابط موقعك إن نشرت الويب لاحقاً).
- **Redirect URLs:** أضف السطرين:
  ```
  addonstation://recovery
  addonstation://**
  ```

**تعديل قالب استعادة كلمة المرور** (Authentication → Email Templates → Reset password) — استبدل الرابط بهذا:
```html
<a href="addonstation://recovery?token={{ .TokenHash }}&type=recovery&email={{ .Email }}">إعادة ضبط كلمة المرور</a>
```
بهذه الطريقة يفتح الضغط على زر في البريد التطبيق مباشرة على شاشة إنشاء كلمة مرور جديدة.

---

## الجزء 5 — رفع الأصول (Avatars / Items / Stickers)

المخازن تُنشأ تلقائياً من `05_storage.sql`. تحتاج فقط رفع الملفات إلى المخزن العام **site-assets** بهذا الشكل:

```
site-assets/
├── profiles/      ← الأفاتارات والأغلفة (onefree.png افتراضياً)
├── items/         ← 105 أداة شات (من مجلد المودع seed-assets/items)
└── stickers/
    ├── meme/
    ├── Minecraft_Memes1/
    ├── Minecraft_Memes2/
    ├── Minecraft_Memes3/
    └── Minecraft_Movie/
```

**الطريقة من لوحة Supabase (الأسهل):**
1. **Storage** → `site-assets` → **Create folder**: `items` ثم `profiles` ثم `stickers`.
2. داخل كل مجلد اضغط **Upload files** واسحب الملفات.
3. داخل `stickers` أنشئ المجلدات الخمسة بأسماء الحزم أعلاه وارفع كل حزمة في مجلدها.

**ملفات المحتوى المدفوع (الإضافات/العوالم):** ترفع إلى مخزن **content-files** (خاص) ويمكن رفعها من لوحة الإدارة داخل التطبيق، ثم يُخزَّن مسار الملف في `content_items.file_path`.

**الأفاتارات الجاهزة (29 صورة في المشروع الأصلي) غير مضمّنة في هذا المودع** — ارفع أفاتاراتك/أغلفتك بنفسك إلى `site-assets/profiles/` ثم أضف لها صفوفاً في `site_assets`:

```sql
insert into public.site_assets (asset_type, name, file_url, required_plan, sort_order)
values
  ('avatar', 'Steve Mining', 'profiles/steve_mining.jpg', 'epic', 1),
  ('banner', 'Cherry Valley', 'profiles/cherry_valley.jpg', 'legendary', 1);
```

---

## الجزء 6 — تشغيل المزامنة اللحظية (Realtime) للشات

من **Database → Replication** (أو **Database → Publications → `supabase_realtime`**) فعّل الجداول التالية:

- `chat_messages`
- `chat_reactions`
- `chat_typing`
- `chat_read_state`
- `chat_notifications`
- `profiles`

هذه الخطوة ضرورية ليظهر الشات لحظياً مثل Discord (رسائل، تفاعلات، «يكتب الآن…»).

---

## الجزء 7 — ربط التطبيق بالمفاتيح

لديك خياران (اختر واحداً):

**الخيار أ — داخل المشروع (ثابت):** افتح `android/gradle.properties` وضع:
```properties
ADDONSTATION_SUPABASE_URL=https://xxxxxxxx.supabase.co
ADDONSTATION_SUPABASE_ANON_KEY=eyJhbGciOi...
```
سيقرأها Gradle ويضعها في `BuildConfig` عند البناء.

**الخيار ب — من داخل التطبيق:** أول تشغيل للتطبيق يعرض شاشة **«الاتصال بـ Supabase»** تلصق فيها القيمتين، وتُحفظ مشفّرة على الجهاز (لا حاجة لإعادة البناء).

---

## الجزء 8 — الاختبار والتأكد

1. شغّل التطبيق → **حساب جديد** → أنشئ حساباً.
2. في Supabase → **Table Editor → profiles** يجب أن ترى صفاً جديداً بنفس اسم المستخدم، وفي `ac_wallets` رصيد **50 AC** (مكافأة الترحيب).
3. نفّذ `08_admin_bootstrap.sql` بعد تعديل بريدك → ستظهر لك **لوحة الإدارة** في التطبيق.
4. أنشئ محتوى تجريبياً: لوحة الإدارة → **إضافة محتوى** (أو من SQL):
   ```sql
   insert into public.content_items (title, slug, description, content_type, plan, is_published, created_by)
   select 'Test Addon', 'test-addon', 'محتوى تجريبي', 'addon', 'free', true, id
   from public.profiles where role = 'admin' limit 1;
   ```
5. أرسل رسالة في **المجتمع → 🌐 General** وتأكد أنها تظهر لحظياً.
6. أنشئ طلب دفع تجريبي: **الملف الشخصي → خطتي → شراء اشتراك** ثم ارفع أي صورة كإيصال، وأكّده من لوحة الإدارة → **المدفوعات** → **تأكيد الدفع**، وتأكد أن `plan` و`plan_expires_at` تغيّرا في جدول `profiles`.

---

## ملاحظات أمنية مهمة (من الوثيقة §3.4)

- التطبيق يحتوي **فقط** على `anon key` — كل العمليات الحساسة تمر بدوال `SECURITY DEFINER` تتحقق من الجلسة والدور.
- الأسعار تُقرأ من `subscription_plans` على الخادم، وطلب الدفع يُنشأ بدالة تُرفض فيها أي قيمة يرسلها التطبيق.
- الخطة المنتهية تُعالج كـ `free` لحظياً عبر `effective_plan()` في كل طلب (لا وظيفة ليلية).
- الملفات المدفوعة في مخزن خاص، والرابط الموقّع قصير الأجل: **60 ثانية** للمحتوى والإيصالات، **300 ثانية** لوسائط الشات.
- الإيصالات: المستخدم يرفع/يقرأ داخل مجلده فقط، والإدارة تقرأ الكل.
- كل عملية إدارية تُسجَّل في `admin_audit_log` (من فعل ماذا ومتى).

---

## استكشاف الأخطاء الشائعة

| المشكلة | الحل |
|---|---|
| `new row violates row-level security policy` | تأكد أنك نفّذت `04_rls.sql`، وأن العملية تحتاج دالة RPC لا كتابة مباشرة |
| `permission denied for function ...` | أعد تنفيذ كتلة `grant execute` في نهاية `02`/`03` |
| تسجيل الدخول يقول «Email not confirmed» | أوقف **Confirm email** أثناء الاختبار (الجزء 4) |
| الشات لا يتحدّث لحظياً | فعّل الجداول في **Realtime** (الجزء 6) |
| الصور لا تظهر | تأكد أن المخزن `site-assets` **public** وأن المسار يبدأ مثل `items/apple.png` بلا `/site-assets/` في البداية |
| رابط الاستعادة لا يفتح التطبيق | أضف `addonstation://recovery` في Redirect URLs وحدّث قالب البريد |

---

## ما بعد ذلك

- لتفعيل **بوابة دفع إلكتروني** (Stripe أو غيرها) راجع: `docs/03-PAYMENTS-AR.md`.
- لمعرفة ترتيب بقية البناء في التطبيق: `docs/01-ROADMAP-AR.md`.
