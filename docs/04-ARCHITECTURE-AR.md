# بنية المشروع — خريطة الملفات وكيف تتصل

## 1) شكل المودع

```
ps/
├── android/                     ← تطبيق الأندرويد (Kotlin + Jetpack Compose)
│   ├── settings.gradle.kts · build.gradle.kts · gradle.properties
│   └── app/
│       ├── build.gradle.kts     ← الاعتماديات + BuildConfig للمفاتيح
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml      ← الأذونات + الرابط العميق addonstation://recovery
│           ├── java/com/addonstation/mobile/   ← كود التطبيق (يُبنى تدريجياً)
│           └── res/
│               ├── values/  values-ar/  values-pt/ …   ← 8 لغات (الإنجليزية هي الافتراضية)
│               ├── font/pixel_regular.ttf             ← خط البكسل (OFL)
│               ├── mipmap-*/ic_launcher.png            ← الأيقونة الرسمية
│               ├── xml/network_security_config.xml     ← HTTPS فقط
│               └── xml/data_extraction_rules.xml
├── ps4/                         ← عميل PS4 تجريبي منفصل (C++17 + SDL2 + OpenOrbis)
│   ├── Makefile                 ← إعداد ELF/SELF/PKG محلياً عبر OpenOrbis
│   ├── config.local.example.hpp ← مثال إعدادات بلا مفاتيح حقيقية
│   ├── src/                     ← واجهة SDL2 + SceHttp + REST read-only + بيانات وهمية + JSON
│   ├── tests/                   ← اختبارات طبقة API والكتالوج التجريبي على الحاسوب
│   └── README-AR.md             ← إعداد المفتاح العام وبناء PKG والقيود
├── supabase/                    ← كل شيء على الخادم
│   ├── 01_schema.sql            ← 33 جدولاً
│   ├── 02_functions_core.sql    ← الخطط · المحتوى · AC · المدفوعات · الملف الشخصي
│   ├── 03_functions_chat.sql    ← الشات · الإشراف · الإشعارات · المبدعون
│   ├── 04_rls.sql               ← سياسات الأمان لكل جدول
│   ├── 05_storage.sql           ← 6 مخازن + سياساتها
│   ├── 06_seed.sql              ← الخطط · طرق الدفع · المهام · 13 غرفة · القواعد · الإعدادات
│   ├── 07_seed_assets.sql       ← 105 أداة + 181 ملصقاً
│   ├── 08_admin_bootstrap.sql   ← تعيين حسابك Admin
│   ├── 09_gateway_functions.sql ← دوال بوابات الدفع (service_role)
│   └── functions/               ← Edge Functions (Deno) لـ Stripe
│       ├── stripe-checkout/index.ts
│       └── stripe-webhook/index.ts
├── seed-assets/                 ← أصول مشروعك الأصلي الجاهزة للرفع إلى Supabase
│   ├── items/ (105) · stickers/ (181) · blocks/ · covers/ · fonts/
├── reference/legacy-addonstation/  ← كود المشروع القديم (مرجع فقط): 43 ملف SQL + قوائم الأدوات/الملصقات
├── docs/
│   ├── specs/                   ← الوثائق الثلاث الملزمة
│   ├── HANDOVER_DOCUMENT.md     ← وثيقة تسليم المشروع الأصلي
│   ├── 01-ROADMAP-AR.md         ← خطة المراحل
│   ├── 02-SUPABASE-SETUP-AR.md  ← إنشاء Supabase خطوة بخطوة
│   ├── 03-PAYMENTS-AR.md        ← الدفع اليدوي + Stripe + تحذير Play
│   └── 04-ARCHITECTURE-AR.md    ← هذا الملف
└── tools/check_i18n.py          ← يتحقق أن كل مفتاح نصي موجود في اللغات الثمانية
```

## 2) طبقات التطبيق (المعمارية)

```
        Compose UI (شاشات + مكوّنات)
                 │  StateFlow
                 ▼
        ViewModels  (حالة كل شاشة + منطق العرض)
                 │
                 ▼
        Repositories (المصادقة · المحتوى · الشات · الخطط …)
                 │
                 ▼
        SupabaseClient  ── HTTP/WebSocket (OkHttp) ──►  Supabase
          ├── auth    : /auth/v1/*        (تسجيل · دخول · استعادة · تجديد الجلسة)
          ├── rest    : /rest/v1/*        (قراءة الجداول) + /rest/v1/rpc/*  (كل العمليات الحساسة)
          ├── storage : /storage/v1/*     (رفع · روابط موقّعة 60/300 ثانية)
          └── realtime: /realtime/v1      (رسائل الشات · التفاعلات · «يكتب الآن» · الإشعارات)
```

العملاء مستقلون عن بعضهم، لكنهم يتشاركون **مشروع Supabase واحداً**:

```text
Android (موجود) ──┐
PS4 C++ (تجريبي) ──┼── HTTPS/REST ──► قاعدة Supabase واحدة
iOS (مخطط) ────────┤
PC (مخطط) ─────────┘
```

نسخة PS4 الأولية في `ps4/` تقرأ `content_items` المنشورة و`subscription_plans` المفعّلة فقط، مستخدمةً مفتاحاً عاماً. إن لم يُضبط المشروع، تعرض 6 عناصر و3 خطط وهمية محلياً ولا ترسلها للخادم. لا توجد قاعدة بيانات على الجهاز ولا تغييرات على تطبيق Android. iOS وPC خطط عملاء مستقبلية وليسا تطبيقين موجودين حالياً في هذا المودع.

**قواعد ثابتة في هذه المعمارية:**
1. لا يُكتب في الجداول الحساسة (`payment_orders`, `ac_*`, `chat_messages`, `content_downloads`…) إلا عبر دوال RPC — ولذلك سُحقت صلاحيات الكتابة المباشرة في `04_rls.sql`.
2. كل عميل يحمل المفتاح العام فقط (`anon` القديم أو `sb_publishable_…`)؛ لا يُضمَّن `sb_secret_…` أو `service_role` في أي تطبيق.
3. الجلسة تُحفظ مشفّرة (Android Keystore) ولا تُرسل لغير `supabase.co`.
4. الخطة الفعلية تُحسب دائماً على الخادم بدالة `effective_plan()` — التطبيق لا يقرر الصلاحيات.

## 3) التقنيات والإصدارات

| الطبقة | الاختيار |
|---|---|
| اللغة | Kotlin 2.0.21 · `minSdk 24` · `targetSdk 35` · JVM 17 |
| الواجهة | Jetpack Compose (BOM 2024.10.01) + Material3 كأساس + ثيم داكن مخصص |
| التنقل | Navigation-Compose (5 تبويبات + شاشات فرعية) |
| الشبكة | OkHttp 4.12 (REST + WebSocket) |
| الترميز | kotlinx.serialization 1.7.3 |
| الصور | Coil 2.7 (مع دعم GIF للأداة المتحركة) |
| الوسائط | Media3 ExoPlayer 1.4 (صوت/فيديو في الشات) |
| التخزين المحلي | DataStore (اللغة/المظهر) + EncryptedSharedPreferences (الجلسة) |
| الخادم | Supabase (PostgreSQL + RLS + RPC + Storage + Realtime) |
| عميل PS4 التجريبي | C++17 + SDL2 · OpenOrbis · SceNet/SceSsl/SceHttp · REST read-only |

## 4) كيف تُختبر الميزات سريعاً (بدون انتظار بناء APK)

- **قاعدة البيانات وحدها:** من SQL Editor يمكنك تنفيذ أي دالة واختبار نتيجتها مباشرة، مثال:
  ```sql
  select public.create_payment_order('epic', 'bank_transfer');
  select public.ac_claim_task('daily_login');
  select public.chat_unread_counts();
  select public.effective_plan();
  ```
- **التحقق من اللغات:** `python3 tools/check_i18n.py`
- **التحقق من RLS:** من جدول `profiles` جرّب `update` لحقل `plan` من حساب عادي — يجب أن يفشل بخطأ `forbidden_profile_column`.
