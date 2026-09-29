# 02 — من التسجيل في Supabase إلى تشغيل التطبيق كاملاً (دليل تنفيذ حرفي) 🚀

> هذا الدليل يأخذك من **صفر** (لا حساب ولا مشروع) إلى **تطبيق يعمل بالكامل**: قاعدة بيانات، مصادقة، مخازن ملفات، شات لحظي، محفظة AC، اشتراكات ودفع، ولوحة إدارة.
>
> ⏱️ **الوقت:** 25–40 دقيقة (معظمها انتظار). 💻 **المتصفح على حاسوب أفضل**، ويمكن إتمام كل شيء من الجوال.
> 💳 **لا تحتاج بطاقة بنكية** — الخطة المجانية تكفي تماماً للتطوير والاختبار.

---

## 📋 قائمة التحقق السريعة (اطبعها ذهنياً وتابع)

| # | المرحلة | الوقت | الناتج |
|---|---------|-------|--------|
| 1 | إنشاء حساب Supabase | 3 د | حساب مسجّل |
| 2 | إنشاء المشروع | 3 د | مشروع جاهز (Active) |
| 3 | نسخ **الرابط + المفتاح العام** | 2 د | `https://xxx.supabase.co` + `sb_publishable_…` |
| 4 | تنفيذ ملف SQL الشامل | 5 د | 33 جدولاً + 65 دالة + 6 مخازن + بيانات أولية |
| 5 | ضبط المصادقة (Auth) | 4 د | تسجيل/دخول/استعادة كلمة مرور تعمل |
| 6 | رفع الأصول 288 ملفاً | 10 د | أدوات + ملصقات تظهر في التطبيق |
| 7 | تشغيل المزامنة اللحظية | 1 د | شات لحظي مثل Discord |
| 8 | تعيين حسابك **Admin** | 2 د | لوحة الإدارة تظهر |
| 9 | ربط التطبيق بالمفتاح | 1 د | شاشة رئيسية ببيانات حقيقية |
| 10 | الاختبار الشامل (16 بنداً) | 10 د | تطبيق جاهز للاستخدام الفعلي |

---

## المرحلة 1 — إنشاء حساب Supabase

1. افتح **https://supabase.com** واضغط **Start your project** (أعلى اليمين).
2. اختر طريقة التسجيل:
   - **Continue with GitHub** ← الأسرع (لا رسالة تأكيد).
   - أو **Continue with Email** ← أدخل بريدك وكلمة مرور قوية، ثم افتح رسالة التأكيد واضغط الرابط.
3. إن طلب منك اسم منظمة (Organization): اكتب `AddonStation` واحفظ الخطوة.
4. ✅ علامة النجاح: تصل إلى لوحة التحكم `Dashboard` وفيها زر **New project** ولا يوجد أي تنبيه عن الدفع.

> ⛔️ لا تُدخل أي بطاقة بنكية. الخطة المجانية لا تطلبها.

---

## المرحلة 2 — إنشاء المشروع

1. اضغط **New project** (المربع الأخضر).
2. املأ الحقول:

| الحقل | القيمة المقترحة | لماذا |
|---|---|---|
| **Organization** | `AddonStation` | الإطار الذي يحمل مشاريعك |
| **Project name** | `addonstation` | اسم واضح داخل اللوحة |
| **Database Password** | اضغط **Generate a password** ثم **انسخه واحفظه** | يُطلب عند الاتصال المباشر بقاعدة البيانات؛ لن يُعرض مرة أخرى |
| **Region** | **Europe (Frankfurt)** أو **Europe (Paris)** | الأقرب لليبيا/شمال إفريقيا = استجابة أسرع |
| **Pricing Plan** | **Free** | كافية للبداية |

3. اضغط **Create new project**.
4. ⏳ انتظر **1–3 دقائق** حتى تجهَز القاعدة (ستتحول الحالة إلى 🟢 **Active / Healthy**). لا تنفّذ أي شيء قبل ذلك.

> 📌 **احفظ معلوماتك في مكان آمن:**
> * رابط المشروع: `https://<معرّف-المشروع>.supabase.co` (تجد المعرّف في **Settings → General → Reference ID**)
> * كلمة مرور القاعدة (من الخطوة 2)

---

## المرحلة 3 — نسخ الرابط والمفتاح العام (⚠️ تغيّر نظام المفاتيح في 2026)

### أسرع طريقة (موصى بها)
اضغط زر **Connect** في أعلى صفحة المشروع → ستظهر لك مباشرة:
* **Project URL** → `https://xxxx.supabase.co`
* **Publishable key** → `sb_publishable_…`

### الطريقة التفصيلية
**Settings** (⚙️ أسفل القائمة الجانبية) → **API Keys** → تبويب **Publishable and secret API keys**.

| المفتاح | الشكل | أين يُستخدم | هل يوضع في التطبيق؟ |
|---|---|---|---|
| **Project URL** | `https://xxxx.supabase.co` | التطبيق | ✅ نعم |
| **Publishable key** | `sb_publishable_…` | التطبيق (الجوال) | ✅ **نعم — هذا المطلوب** |
| **Secret key** | `sb_secret_…` | اللوحة والسكربتات والسيرفر فقط | ⛔ **ممنوع تماماً** |
| **anon** (قديم) | `eyJhbGciOi…` (JWT طويل) | بديل قديم للمفتاح العام | ✅ يعمل لو لم تجد publishable |
| **service_role** (قديم) | `eyJhbGciOi…` (JWT طويل) | بديل قديم للمفتاح السري | ⛔ **ممنوع تماماً** |

> 🔐 **قاعدتان لا تُنقضان:**
> 1. التطبيق يأخذ **المفتاح العام فقط**. أي شخص يستطيع استخراجه من ملف APK — وهذا طبيعي ومقصود، لأن الحماية الحقيقية في RLS ودوال الخادم.
> 2. المفتاح السري (`sb_secret_`) يتجاوز كل الحماية (RLS). لو وضعته في التطبيق فكأنك سلّمت مفاتيح كل بياناتك. لو حاولت لصقه في شاشة الإعداد داخل التطبيق، **سيرفضه التطبيق ويظهر تحذيراً**.
>
> 💡 إن ظهرت لك عبارة "Legacy API keys" فالمشروع قديم: استخدم `anon` (العام) و`service_role` (السري) كما هي. التطبيق يدعم النوعين تلقائياً.

---

## المرحلة 4 — تنفيذ قاعدة البيانات (أهم خطوة)

### الطريقة (أ) — الأسرع: ملف واحد جامع ✨
1. من القائمة الجانبية اضغط **SQL Editor** → **New query**.
2. افتح ملف **`supabase/00_ALL_IN_ONE.sql`** (173 KB — يحتوي 33 جدولاً و65 دالة و76 سياسة أمنية و6 مخازن والبيانات الأولية):
   - على الحاسوب: افتح الملف من المودع، اضغط **Raw** في GitHub ثم **Ctrl+A** ثم **Ctrl+C**.
   - على الجوال: افتح الملف في تطبيق GitHub أو المتصفح بنمط **Raw** ثم علّم الكل وانسخ.
3. الصق في نافذة SQL Editor ثم اضغط **Run** (أو `Ctrl+Enter`).
4. ✅ النتيجة المتوقعة: **`Success. No rows returned`** (قد تأخذ 5–15 ثانية).

### الطريقة (ب) — ملفاً بملف (لو فشل الملف الجامع)
نفّذ بنفس هذا الترتيب، وبعد كل ملف اضغط **Run** ثم انتقل للتالي:

| # | الملف | ما الذي يفعله |
|---|-------|----------------|
| 1 | `supabase/01_schema.sql` | 33 جدولاً (ملفات، خطط، مدفوعات، محتوى، AC، شات، مبدعون، إدارة) |
| 2 | `supabase/02_functions_core.sql` | دوال المحتوى والخطط والدفع وAC والملف الشخصي |
| 3 | `supabase/03_functions_chat.sql` | دوال الشات: إرسال، فلترة تلقائية، كتم/حظر/بلاغات، إشعارات، مبدعون |
| 4 | `supabase/04_rls.sql` | سياسات الأمان (من يقرأ/يكتب ماذا) وسحب الكتابة المباشرة |
| 5 | `supabase/05_storage.sql` | 6 مخازن + سياسات الملفات (مدفوع، إيصالات، صور/أوسائط، مبدعون، أصول عامة) |
| 6 | `supabase/06_seed.sql` | الخطط، 8 طرق دفع، 6 مهام AC، 5 حزم ملصقات، **13 غرفة**، القواعد بـ8 لغات |
| 7 | `supabase/07_seed_assets.sql` | **105 أداة** و**181 ملصقاً** (286 صفاً في `site_assets`) |
| 8 | `supabase/10_realtime.sql` | تشغيل المزامنة اللحظية لستة جداول |
| 9 | `supabase/11_grants.sql` | **إلزامي في المشاريع الجديدة:** منح صلاحيات `anon`/`authenticated` على الجداول (بدونه: `permission denied for table …`) |

> الملفان `08_admin_bootstrap.sql` (المدير) و`09_gateway_functions.sql` (بوابات الدفع) لهما توقيت خاص — انظر المرحلتين 8 و12.

### تحقق فوري: هل نجح كل شيء؟ (الصق هذه الاستعلامات في SQL Editor)

```sql
-- 1) كم جدولاً أُنشئ؟
select count(*) as tables_created
from information_schema.tables where table_schema = 'public';        -- المتوقّع: 33

-- 2) الخطط الثلاث
select id, name, price_lyd, duration_days, download_limit
from public.subscription_plans order by sort_order;                   -- المتوقّع: free / epic / legendary

-- 3) الغرف الثلاث عشرة
select count(*) as chat_rooms from public.chat_channels;              -- المتوقّع: 13

-- 4) المهام وحزم الملصقات
select (select count(*) from public.ac_tasks)      as tasks,         -- 6
       (select count(*) from public.sticker_packs) as packs,         -- 5
       (select count(*) from public.chat_rules)    as rule_langs,    -- 8
       (select count(*) from public.site_assets)   as assets;        -- 286

-- 5) المخازن الستة
select id, public, file_size_limit from storage.buckets order by id;
```

---

## المرحلة 5 — ضبط المصادقة (Authentication)

### 5.1 تفعيل البريد
1. **Authentication** → **Sign In / Providers** (قد يظهر باسم **Providers**) → تأكد أن **Email** مفعّل (Enabled).
2. **Confirm email** في نفس الصفحة:
   - 🧪 **أثناء الاختبار:** اجعله **OFF** → يسجّل المستخدم ويدخل فوراً بلا رسالة بريد.
   - 🚀 **قبل النشر الحقيقي:** اجعله **ON** → يتأكد من بريده (أأمن ضد الحسابات الوهمية).
   > ⚠️ الخطة المجانية ترسل عدداً محدوداً من رسائل البريد في الساعة (2–4). مع **Confirm email = ON** ستحتاج بريداً حقيقياً لكل حساب تجريبي. الحل النهائي: **Project Settings → Auth → SMTP Settings** وربط مزوّد مثل Resend أو SendGrid.

### 5.2 روابط العودة إلى التطبيق (مهمة لاستعادة كلمة المرور)
**Authentication** → **URL Configuration**:

| الحقل | القيمة |
|---|---|
| **Site URL** | `addonstation://recovery` |
| **Redirect URLs** | أضف سطرين منفصلين: `addonstation://recovery` و `addonstation://**` |

### 5.3 قالب رسالة استعادة كلمة المرور
**Authentication** → **Email Templates** → **Reset password** → أبقِ الرابط كما هو `{{ .ConfirmationURL }}` وبدّل النص العربي فقط، مثال:

```html
<h2>إعادة ضبط كلمة المرور — AddonStation</h2>
<p>اضغط الزر التالي لفتح التطبيق وتعيين كلمة مرور جديدة:</p>
<p><a href="{{ .ConfirmationURL }}">إعادة ضبط كلمة المرور</a></p>
```
بهذا يفتح الرابط التطبيق مباشرة على شاشة «كلمة مرور جديدة» (بفضل Site URL الذي ضبطناه في 5.2).

### 5.4 (اختياري) قوة كلمة المرور
**Authentication** → **Settings** → **Password strength**: الحد الأدنى **6 أحرف** — وهو المطابق لما يتحقق منه التطبيق (`MIN_PASSWORD = 6`).

---

## المرحلة 6 — رفع الأصول (الأدوات والملصقات) 🖼️

المخازن الستة أُنشئت تلقائياً من `05_storage.sql`:

| المخزن | عام؟ | الحد | الاستخدام |
|---|---|---|---|
| `site-assets` | ✅ عام | 5 MB/ملف | الأدوات، الملصقات، الأفاتارات، الأغلفة |
| `content-files` | ⛔ خاص | 512 MB | ملفات الإضافات/العوالم (رابط موقّع 60 ثانية) |
| `payment-receipts` | ⛔ خاص | 10 MB | صور إيصالات الدفع |
| `chat-images` | ⛔ خاص | 4 MB | صور الشات |
| `chat-media` | ⛔ خاص | 100 MB | صوت/فيديو الشات |
| `creator-media` | ⛔ خاص | 200 MB | رفع المبدعين |

### 6.0 الطريقة الأسهل (بلا حاسوب ولا Python) — عبر GitHub Actions
إن لم يكن لديك حاسوب أو لا تريد تنصيب Python:

1. أضف «سرّين» مرة واحدة: المستودع → **Settings → Secrets and variables → Actions**:
   - `ADDONSTATION_SUPABASE_URL` = `https://<معرّف-مشروعك>.supabase.co`
   - `ADDONSTATION_SUPABASE_SECRET_KEY` = `sb_secret_…`
2. تبويب **Actions** → **Upload seed assets** → **Run workflow**:
   - اختر `dry-run` أولاً للتجربة، ثم `upload` للرفع الفعلي.
   - يمكنك رفع قسم واحد (`items` أو `stickers` أو `profiles`) عبر قائمة `only`.

> 🔐 المفتاح السري يبقى داخل إعدادات GitHub ولا يظهر في السجل ولا يدخل التطبيق أبداً.
> يرفع السير عمل 288 ملفاً إلى مخزن `site-assets`، ثم يتحقق تلقائياً أن أول ملف يُقرأ عامّاً (HTTP 200).

### 6.1 الطريقة الثانية: سكربت الرفع من حاسوبك (288 ملفاً في دقيقة)
على **حاسوبك** (يحتاج Python 3 فقط، بلا أي مكتبة إضافية):

```bash
# 1) نزّل المودع
git clone https://github.com/mohamadelfrgena-ai/ps.git && cd ps

# 2) تجربة بدون رفع (تتأكد أن كل شيء صحيح)
python3 tools/upload_seed_assets.py \
  --url https://xxxx.supabase.co \
  --key sb_secret_xxxxxxxx \
  --dry-run

# 3) الرفع الفعلي
python3 tools/upload_seed_assets.py \
  --url https://xxxx.supabase.co \
  --key sb_secret_xxxxxxxx
```
* استخدم هنا **المفتاح السري** `sb_secret_…` (أو `service_role` القديم) لأنه يرفع ملفات عامة دفعة واحدة.
* الناتج المتوقع: `✅ نجح: 288  ❌ فشل: 0`.
* لو أردت قسماً واحداً: أضف `--only items` أو `--only stickers` أو `--only profiles`.

### 6.2 أو يدوياً من اللوحة (مناسب إن لم يكن لديك حاسوب)
**Storage** → `site-assets` → **Create folder** ثم ارفع داخل كل مجلد:

```
site-assets/
├── profiles/   ← onefree.png (الأفاتار الافتراضي)
├── items/      ← 105 أداة (من مجلد seed-assets/items)
└── stickers/
    ├── meme/               (47)
    ├── Minecraft_Memes1/   (21)
    ├── Minecraft_Memes2/   (87)
    ├── Minecraft_Memes3/   (20)
    └── Minecraft_Movie/    (6)
```
> ⚠️ **المسار داخل المخزن يجب أن يطابق ما في قاعدة البيانات حرفياً**: `items/apple.png` وليس `site-assets/items/apple.png`.
> 💡 على الحاسوب يمكنك سحب المجلد كاملاً وإفلاته في صفحة الرفع.

### 6.3 تحقق
```sql
select asset_type, count(*) from public.site_assets group by 1 order by 1;
-- المتوقّع: sticker = 181 ، item = 105

-- اختبار رابط عام مباشر لعنصر (يجب أن يفتح صورة في المتصفح):
select 'https://<معرّف-المشروع>.supabase.co/storage/v1/object/public/site-assets/' || file_url as url
from public.site_assets where file_url like 'items/%' limit 1;
```

### 6.4 (اختياري) محتوى مدفوع للتجربة
**Storage** → `content-files` → ارفع ملف `.zip` صغيراً، ثم سجّل مساره عند إنشاء عنصر من لوحة الإدارة (`file_path` = `اسم-الملف.zip`). لو لم ترفع ملفاً، سيظهر للمستخدم خطأ واضح: **«الملف لم يُرفع بعد»** بدل تنزيل فاشل.

---

## المرحلة 7 — تشغيل المزامنة اللحظية (Realtime) ⚡

**إما** نفّذ `supabase/10_realtime.sql` (مضمّن في الملف الجامع — نفّذه فقط إن استخدمت الطريقة ب)،
**أو** من اللوحة: **Database** → **Replication** → اختر المنشور `supabase_realtime` → فعّل الجداول:

```
chat_messages · chat_reactions · chat_typing · chat_read_state · chat_notifications · profiles
```

**تحقق:**
```sql
select tablename from pg_publication_tables where pubname = 'supabase_realtime' order by 1;
```
> بدون هذه الخطوة يعمل الشات أيضاً، لكن بتحديث كل ثانيتين (خطة بديلة مبرمجة في التطبيق) بدل اللحظية.

---

## المرحلة 8 — اجعل حسابك Admin 👑

الترتيب مهم: **أنشئ حسابك أولاً، ثم نفّذ ملف المدير.**

1. أنشئ حسابك من داخل التطبيق (**حساب جديد**) — أو من اللوحة: **Authentication → Users → Add user → Create new user** (مع `Auto Confirm`).
2. افتح `supabase/08_admin_bootstrap.sql` وعدّل السطر:
   ```sql
   v_email text := 'you@example.com';   -- ← ضع بريدك هنا
   ```
3. الصقه في **SQL Editor** واضغط **Run** — ستظهر رسالة: `تم تعيين ... مديراً (Admin) ✅`
4. **تحقق:**
   ```sql
   select id, username, role, plan from public.profiles where role = 'admin';
   ```
5. (اختياري للاختبار) خطة أسطورية وطريقة دفع مفعّلة:
   ```sql
   -- خطة legendary لك سنة كاملة
   update public.profiles
      set plan = 'legendary', plan_expires_at = now() + interval '365 days'
    where role = 'admin';

   -- فعّل تحويلاً بنكياً للتجربة (كل الطرق معطّلة افتراضياً!)
   update public.payment_method_settings
      set enabled = true,
          account_name = 'AddonStation',
          account_identifier = '091-0000000',
          instructions = 'حوّل المبلغ ثم ارفع صورة الإيصال ورقم العملية.'
    where code = 'bank_transfer';
   ```
   > 💡 يمكنك فعل نفس الشيء من داخل التطبيق: **لوحة الإدارة → المزيد → إعدادات الخطط والدفع**.

> ⚠️ **بدون المرحلة 8 لن ترى لوحة الإدارة أبداً** — وهذا مقصود: الزر يظهر فقط لمن `role = 'admin'`، وحتى لو خمّن أحدهم الشاشة، يرفضه الخادم.

---

## المرحلة 9 — ربط التطبيق بمشروعك 📱

اختر واحداً:

### الخيار (أ) — من داخل التطبيق (لا يحتاج إعادة بناء) ⭐ الأسرع
1. ثبّت التطبيق (أو شغّله من Android Studio).
2. أول تشغيل تظهر شاشة **«الاتصال بـ Supabase»**.
3. الصق **Project URL** و**Publishable key** → اضغط **اتصال**.
4. يُخزَّنان محلياً على الجهاز (مشفّرين) ولا تحتاجهما مرة أخرى.

### الخيار (ب) — داخل ملفات المشروع (ثابت في البناء)
أنشئ/عدّل `android/local.properties` (لا يُرفع إلى Git):
```properties
sdk.dir=/home/you/Android/Sdk
ADDONSTATION_SUPABASE_URL=https://xxxx.supabase.co
ADDONSTATION_SUPABASE_ANON_KEY=sb_publishable_xxxxxxxx
```
أو في `android/gradle.properties` بنفس الأسماء. ثم ابنِ التطبيق (انظر `docs/05-BUILD-APK-AR.md`).

> ✅ **الدليل على نجاح الربط:** تظهر الخطط الثلاث في **المنزل/الملف الشخصي → الترقية**، وتظهر الغرف في تبويب **المجتمع**.

---

## المرحلة 10 — الاختبار الشامل (16 بنداً) ✅

| # | الإجراء في التطبيق | ما يجب أن ترى | أين تتحقق في Supabase |
|---|---|---|---|
| 1 | حساب جديد | دخول مباشر (أو رسالة «تم إنشاء الحساب، أكّد بريدك») | Authentication → Users |
| 2 | الملف الشخصي | اسمك + شارة **Free** | Table Editor → `profiles` |
| 3 | محفظة AC | رصيد **50 AC** (مكافأة الترحيب) | `ac_wallets`, `ac_transactions` |
| 4 | المحفظة → مهمة يومية | «مطالبة» تزيد الرصيد | `ac_task_claims` |
| 5 | المتجر | تظهر الأدوات (بعد رفع الأصول) | `content_items` (فارغ حتى تضيف محتوى) |
| 6 | لوحة الإدارة → إضافة محتوى | ينشأ عنصر جديد | `content_items` |
| 7 | تفاصيل عنصر مجاني → تنزيل | تُنزَّل نسخة داخل تخزين التطبيق | `content_downloads` |
| 8 | عنصر Epic كمستخدم Free | زر **«🔒 يتطلب Epic 💎»** | — (منطق الخطة) |
| 9 | تفاصيل عنصر → حفظ | أيقونة القلب تتلوّن | `content_favorites` |
| 10 | المجتمع → 🌐 General | ترسل رسالة وتظهر فوراً | `chat_messages` |
| 11 | ضغط مطوّل على رسالة | قائمة (رد، نسخ، تفاعل، إبلاغ) | `chat_reactions`, `chat_reports` |
| 12 | الإعدادات → اللغة → العربية/التركية | الواجهة تنقلب RTL/LTR فوراً | — |
| 13 | الإعدادات → المظهر | فاتح/داكن يُحفظ بعد إغلاق التطبيق | — |
| 14 | الخطط → اشتراك Epic | «طلبك جاهز» + رفع إيصال | `payment_orders` (status=pending) |
| 15 | لوحة الإدارة → المدفوعات → تأكيد | خطة المستخدم تتحول إلى epic فوراً | `profiles.plan` + `plan_expires_at` |
| 16 | لوحة الإدارة → الإشراف | البلاغات تظهر ويمكن إخفاء الرسالة/كتم المستخدم | `chat_reports`, `chat_mutes` |

> 🧪 لو فشل بند: انزل إلى **استكشاف الأخطاء** في آخر هذا الملف — كل خطأ شائع له حل مكتوب.

---

## المرحلة 11 — قبل الإطلاق الحقيقي 🚦

| الخطوة | التفاصيل |
|---|---|
| **Confirm email = ON** | من المرحلة 5.1، بعد ربط SMTP حقيقي |
| **SMTP خاص** | Project Settings → Auth → SMTP (Resend/SendGrid/Amazon SES) لتجاوز حد الرسائل |
| **قيود الخطة المجانية** | 500 MB قاعدة بيانات · 1 GB ملفات · 5 GB نقل · حد **50 MB للملف الواحد** · مشروعان بحد أقصى · ⏸️ **يتوقف المشروع بعد أسبوع من عدم النشاط** (اضغط Restore من اللوحة) |
| **النسخ الاحتياطي** | الخطة المجانية لا توفّر نسخاً قابلة للتنزيل. صدّر بياناتك دورياً: `Database → Backups` أو `pg_dump` |
| **الأسعار والطرق** | من لوحة الإدارة: فعّل طرق الدفع الحقيقية وعدّل الأسعار وأرقام الحسابات |
| **المحتوى** | أضف أول 20–50 أداة/عالم، وصورة غلاف لكل عنصر (Cover) لتظهر البطاقات بشكل جميل |
| **حسابات وهمية** | احذف الحسابات التجريبية من Authentication → Users قبل الإطلاق |
| **المراقبة** | `Reports` و `Logs` في اللوحة + `admin_audit_log` داخل التطبيق |
| **التوثيق** | احفظ رابط المشروع + المفتاح السري في مدير كلمات مرور، لا في رسالة أو ملف مشترك |

---

## المرحلة 12 (اختياري) — بوابة الدفع الإلكتروني 💳

الدفع اليدوي (تحويل + إيصال + موافقة الأدمن) يعمل من المرحلة 4 مباشرة. لتشغيل بطاقات الدفع عبر Stripe:

1. نفّذ `supabase/09_gateway_functions.sql` في SQL Editor.
2. أنشئ حساب Stripe واحصل على `STRIPE_SECRET_KEY` ومفتاح الـ Webhook.
3. انشر الدالتين الجاهزتين:
   ```bash
   supabase functions deploy stripe-checkout --project-ref <ref> --no-verify-jwt
   supabase functions deploy stripe-webhook  --project-ref <ref> --no-verify-jwt
   ```
4. أضف الأسرار (Edge Functions → Secrets):
   ```
   STRIPE_SECRET_KEY=sk_live_…
   STRIPE_WEBHOOK_SECRET=whsec_…
   SITE_SUCCESS_URL=addonstation://payment/success
   SITE_CANCEL_URL=addonstation://payment/cancel
   ```
5. من لوحة الإدارة → إعدادات الخطط والدفع → فعّل طريقة `stripe` وأدخل أسعار الدولار (`price_usd`).
6. اربط الـ Webhook في Stripe على:
   `https://<ref>.supabase.co/functions/v1/stripe-webhook`

📖 التفاصيل الكاملة والتحقق: **`docs/03-PAYMENTS-AR.md`**.

---

## استكشاف الأخطاء (Common Errors) 🔧

| الرسالة / المشكلة | السبب | الحل |
|---|---|---|
| `relation "public.xxx" does not exist` | نفّذت ملفاً قبل ملف | نفّذ بالترتيب: 01 → 02 → 03 → 04 → 05 → 06 → 07 |
| `policy already exists` / `function already exists` | أعدت تنفيذ ملف | كل الملفات idempotent — تجاهل التحذير أو استخدم الملف الجامع مرة واحدة |
| `permission denied for table xxx` **مع** `hint: GRANT SELECT ON public.xxx TO anon` | مشروع جديد (بعد 30 مايو 2026): Supabase لم تعد تمنح الصلاحيات تلقائياً | نفّذ `supabase/11_grants.sql` (أو الملف الجامع الجديد) — RLS وحدها لا تكفي |
| `permission denied for table xxx` بدون hint عند الكتابة | RLS/الصلاحيات تعمل كما يجب | العمليات الحساسة تُنفَّذ عبر دوال RPC لا كتابة مباشرة — سلوك مقصود |
| `new row violates row-level security policy` | تكتب من التطبيق مباشرة بلا دالة | استخدم واجهات التطبيق (التي تنادي الدوال)، لا SQL من الخارج بصلاحية anon |
| «Email not confirmed» عند الدخول | تأكيد البريد مفعّل | أوقف **Confirm email** للاختبار أو أكّد البريد من صندوقك |
| التطبيق يقول **«تحقق من الرابط والمفتاح»** | مفتاح خطأ/ناقص | تأكد أنك الصقت `sb_publishable_…` (أو `anon`) وليس المفتاح السري، وأن الرابط يبدأ بـ `https://` وينتهي بـ `.supabase.co` |
| التطبيق يعرض تحذير **«هذا يبدو مفتاحاً سرياً»** | حاولت لصق `sb_secret_…` | استخدم المفتاح العام — السري للسكربتات فقط |
| الشات لا يتحدّث لحظياً | Realtime غير مفعّل | نفّذ `10_realtime.sql` أو فعّل الجداول من Database → Replication |
| الصور لا تظهر (مربعات فارغة) | مسار خاطئ أو مخزن غير عام | المسار بلا سابقة `site-assets/`، والمخزن **Public** |
| رفع الأصول يفشل بـ `403` أو `Invalid Compact JWS` | مفتاح غير مناسب | استخدم `sb_secret_…`، وتأكد أنك نفّذت `05_storage.sql` |
| `تجاوزت حد الرفع` | حد الخطة المجانية | حّول الملفات إلى أقل من 50 MB أو ارقِ الخطة |
| المشروع **Paused** | أسبوع بلا نشاط | من اللوحة: **Restore project** (استمرارية الخطة المجانية ليست مضمونة) |
| لوحة الإدارة غير ظاهرة | لم تعيّن نفسك Admin | نفّذ `08_admin_bootstrap.sql` ببريدك الصحيح |
| «لا توجد نتائج» في المتجر | لا يوجد محتوى منشور | أضف عنصراً من لوحة الإدارة وفعّل **نشر (is_published)** |
| طلب الاشتراك لا يظهر | كل الطرق معطّلة افتراضياً | فعّل طريقة دفع من لوحة الإدارة → إعدادات الخطط والدفع |
| «رابط الاستعادة لا يفتح التطبيق» | Site URL/Redirect خاطئ | اضبطهما كما في المرحلة 5.2 وقد يكون التطبيق يحتاج إعادة تثبيت لتفعيل رابط العمق |

---

## خريطة اللوحة: أين تجد كل شيء؟ 🗺️

| ما تريده | المسار في Supabase |
|---|---|
| رابط المشروع + المفتاح العام | زر **Connect** أو **Settings → API Keys** |
| تنفيذ SQL | **SQL Editor → New query** |
| الجداول والبيانات | **Table Editor** (أو **Database → Tables**) |
| المستخدمون والبريد | **Authentication → Users** |
| إعدادات الدخول والبريد والقوالب | **Authentication → Providers / Settings / Email Templates** |
| روابط العودة للتطبيق | **Authentication → URL Configuration** |
| المزامنة اللحظية | **Database → Replication** |
| المخازن والملفات | **Storage** |
| دوال Edge | **Edge Functions** |
| الأسرار | **Edge Functions → Secrets** (أو **Project Settings → Edge Functions**) |
| السجلات | **Logs** و **Reports** |
| الاستخدام والحدود | **Organization → Usage** |

---

## ملحق (أ) — تنفيذ كل الملفات بأمر واحد (للمتقدمين) 🧰

إن كنت تستخدم حاسوباً وتفضّل الطرفية بدل النسخ واللصق، استخدم `psql`:

1. في اللوحة: زر **Connect** → تبويب **Session pooler** (أو **Direct connection**) → انسخ **Connection string** واستبدل `[YOUR-PASSWORD]` بكلمة مرور القاعدة.
2. نفّذ:

```bash
cd ps
psql "postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres" \
     -v ON_ERROR_STOP=1 -f supabase/00_ALL_IN_ONE.sql

psql "…نفس الرابط…" -f supabase/08_admin_bootstrap.sql   # بعد تعديل بريدك داخله
```

3. للتحقق السريع من أي جدول:
```bash
psql "…" -c "select asset_type, count(*) from public.site_assets group by 1;"
```
> 🔐 لا تشارك رابط الاتصال ولا المفتاح السري مع أي أحد، ولا تضعهما في مودع Git.

## ملحق (ب) — ماذا يفعل كل ملف بالتفصيل؟

| الملف | الجداول/الدوال | أمثلة |
|---|---|---|
| `01_schema.sql` | 33 جدولاً: `profiles` · `subscription_plans` · `payment_orders` · `content_items` · `ac_wallets/ac_transactions/ac_tasks` · `chat_channels/messages/reactions/mutes/bans/reports/rules/typing/read_state/notifications` · `user_friends` · `creator_applications/submissions/tips/follows` · `featured_creators` · `sticker_packs` · `site_assets` · `app_settings` · `admin_audit_log` | + مشغّل (trigger) ينشئ الملف الشخصي والمحفظة تلقائياً لكل مستخدم جديد |
| `02_functions_core.sql` | 40 دالة: `effective_plan` · `can_access_item` · `record_content_download` · `toggle_favorite` · `ac_claim_task` · `ac_unlock_content` · `create_payment_order` · `submit_payment_receipt` · `update_my_profile` · `set_profile_asset` · `admin_*` … | كل عملية حساسة تُنفَّذ هنا بـ`SECURITY DEFINER` مع تحقق الدور والخطة |
| `03_functions_chat.sql` | 25 دالة: `send_chat_message` (مع فلترة تلقائية) · `chat_toggle_reaction` · `chat_mute_user/ban` · `chat_report_message` · `chat_mark_read` · `chat_set_typing` · `chat_unread_counts` · `my_chat_status` · `admin_upsert_content/plan/settings` · `submit_creator_application/tip/content` … | تحدد الأدوار والصلاحيات والإعلانات و«يكتب الآن…» |
| `04_rls.sql` | 58 سياسة | أمثلة: `content_select_public` (المحتوى المنشور للجميع) · `orders_select_own` (الطلب لصاحبه فقط) · `ac_wallet_own` · `messages_read` (حسب عضوية الغرفة) · `audit_admin_read` (سجل الإدارة للأدمن فقط) |
| `05_storage.sql` | 6 مخازن + سياسات الملفات | إيصال كل مستخدم في مجلده · المحتوى المدفوع بصلاحية الخطة · الأصول العامة للقراءة للجميع · أغلفة المبدعين في `items/<uid>/` داخل `site-assets` |
| `06_seed.sql` | بيانات أولية | 3 خطط · 8 طرق دفع · 6 مهام AC · 5 حزم ملصقات · 13 غرفة · قواعد بـ8 لغات · إعدادات الفلترة التلقائية |
| `07_seed_assets.sql` | 286 صفاً في `site_assets` | 105 أدوات (104 أسطورية + Enchanted Bedrock للإدارة) و181 ملصقاً موزعة على 5 حزم |

## ما بعد هذا الدليل

* 💻 لبناء وتوقيع ملف APK: **`docs/05-BUILD-APK-AR.md`** (أو سير عمل GitHub الجاهز: `.github/workflows/build-apk.yml`).
* 💳 لتفصيل الدفع وبوابة Stripe: **`docs/03-PAYMENTS-AR.md`**.
* 🧱 لفهم بنية الكود والملفات: **`docs/04-ARCHITECTURE-AR.md`**.
* 🗺️ لحالة كل مرحلة من مراحل التطبيق: **`docs/01-ROADMAP-AR.md`**.

> 🤝 **أرسل لي أي رسالة خطأ تظهر لك حرفياً** (من اللوحة أو من التطبيق) وسأخبرك بالحل المباشر وخطوة التصحيح بالتفصيل.
