# 05 — بناء ملف APK خطوة بخطوة 🛠️

> **مهم:** هذا الدليل جاهز للتنفيذ في أي وقت، لكن **لا تبنِ الآن** إلا إذا أردت فعلاً.
> التطبيق مكتوب بالكامل (Kotlin + Jetpack Compose)، والبناء يحتاج فقط جهاز فيه JDK 17 + Android SDK.

---

## 0) قبل أن تبدأ — قائمة سريعة

| الشرط | الحد الأدنى | كيف تتحقق |
|---|---|---|
| JDK | 17 (أو 21) | `java -version` |
| Android SDK | Platform 35 + Build-Tools 35 | داخل Android Studio أو `sdkmanager` |
| Gradle | 8.7 أو أحدث (أو استخدم الغلاف `gradlew`) | `gradle -v` |
| ذاكرة | 8 GB رام مستحسنة | — |
| إنترنت | مطلوب أول مرة لتنزيل الاعتماديات | — |

أسهل مسار على الإطلاق: **افتح المجلد `android/` في Android Studio** — سيتولى JDK و SDK و Gradle تلقائياً.

---

## 1) احصل على المشروع

```bash
git clone https://github.com/mohamadelfrgena-ai/ps.git
cd ps
```

مجلد العمل هو `android/` — افتحه هو (لا الجذر) في Android Studio:
`File ▸ Open ▸ ps/android`

---

## 2) غلاف Gradle (Gradle Wrapper) — ✅ موجود داخل المشروع

المشروع يحمل الغلاف كاملاً وليس بحاجة لأي خطوة إضافية:

```
android/gradlew                                  ← سكربت لينكس/ماك
android/gradlew.bat                              ← سكربت ويندوز
android/gradle/wrapper/gradle-wrapper.jar        ← (Gradle 8.9 — مطابق لبصمة Gradle الرسمية)
android/gradle/wrapper/gradle-wrapper.properties ← distributionUrl → gradle-8.9-bin.zip
```

**أمر البناء على ويندوز** (من مجلد `android` — لاحظ `.bat` أو `.\`):

```bat
cd android
gradlew.bat assembleDebug          :: أو:  .\gradlew.bat assembleDebug
```

وتثبيت على جهاز موصول: `gradlew.bat installDebug` · وتنظيف: `gradlew.bat clean`

**إن أردت إعادة توليد الغلاف لاحقاً** (اختياري — يحتاج Gradle مثبتاً):

```bash
cd android
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

> ملاحظة: إذا نُسخ المشروع إلى جهاز بلا إنترنت لتنزيل Gradle، فسيُحمّل التوزيع `gradle-8.9-bin.zip` مرة واحدة عند أول تشغيل.

---

## 3) اربط التطبيق بمشروع Supabase

لديك خياران — كلاهما يعمل:

**الخيار الأول: ملف `local.properties`** (لا يُرفع إلى Git، وهو الأنسب للأسرار):

```properties
# android/local.properties
sdk.dir=/home/you/Android/Sdk
ADDONSTATION_SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
ADDONSTATION_SUPABASE_ANON_KEY=eyJhbGciOi...   # المفتاح العام anon فقط
```

**الخيار الثاني: `android/gradle.properties`**:

```properties
ADDONSTATION_SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
ADDONSTATION_SUPABASE_ANON_KEY=eyJhbGciOi...
```

> 🔐 **قاعدة أمنية ثابتة:** التطبيق يحمل المفتاح **العام (anon)** فقط. لا تضع أبداً `service_role` أو `sb_secret_…` في التطبيق.
> البناء نفسه يرفضهما: `app/build.gradle.kts` يوقف البناء فوراً إن وُجد أحدهما في `local.properties` أو `gradle.properties`، وشاشة الإعداد الأول ترفضهما أيضاً.
>
> إن نسيت هذه الخطوة، لن يفشل البناء — سيظهر داخل التطبيق **«شاشة الإعداد الأول»** تطلب الرابط والمفتاح وتخزّنهما محلياً.

---

## 4) شغّل التطبيق على جهازك (Debug)

1. من جوالك: `الإعدادات ▸ خيارات المطور ▸ تصحيح USB` = مفعّل.
2. وصّل الكابل واقبل بصمة المفتاح (RSA).
3. تأكد أن الجهاز ظاهر:

```bash
adb devices          # يجب أن يظهر جهازك بـ device وليس unauthorized
```

4. شغّل:

```bash
cd android
./gradlew installDebug        # يبني ويثبّت على الجهاز المتصل
# أو
./gradlew assembleDebug       # بناء فقط بدون تثبيت
```

الملف الناتج:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

> ملاحظة: نسخة الـ debug تحمل اسم حزمة إضافياً `.debug` حتى تستطيع تثبيتها جنباً إلى جنب مع نسخة الإصدار.

---

## 5) ابنِ نسخة الإصدار (Release) موقّعة

### 5.1 أنشئ مفتاح التوقيع (مرة واحدة فقط، واحفظه في مكان آمن)

```bash
keytool -genkeypair -v \
  -keystore addonstation-release.jks \
  -alias addonstation \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "كلمة_سر_قوية" -keypass "كلمة_سر_قوية"
```

### 5.2 أضف إعدادات التوقيع

في `android/app/build.gradle.kts` أضف قبل `android { ... }`:

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
```

وداخل بلوك `android { }`:

```kotlin
signingConfigs {
    create("release") {
        if (keystoreProps.isNotEmpty()) {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
buildTypes {
    getByName("release") {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
    }
}
```

وأنشئ `android/keystore.properties` (لا يُرفع إلى Git أبداً):

```properties
storeFile=../addonstation-release.jks
storePassword=كلمة_سر_قوية
keyAlias=addonstation
keyPassword=كلمة_سر_قوية
```

تأكد أن `.gitignore` يتضمن:
```
*.jks
*.keystore
keystore.properties
```

### 5.3 ابنِ

```bash
cd android
./gradlew clean assembleRelease
```

الناتج:

```
android/app/build/outputs/apk/release/app-release.apk
```

تحقق من التوقيع:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  android/app/build/outputs/apk/release/app-release.apk
```

---

## 6) بناء APK من GitHub بدون جهاز (اختياري)

أضف الملف `.github/workflows/build-apk.yml` وستحصل على APK جاهز للتنزيل من تبويب **Actions** بعد كل دفعة:

```yaml
name: Build APK
on:
  workflow_dispatch:
  push:
    branches: [ main ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Build debug APK
        working-directory: android
        run: |
          gradle wrapper --gradle-version 8.9
          ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: addonstation-debug-apk
          path: android/app/build/outputs/apk/debug/*.apk
```

> نسخة هذا الملف جاهزة في المودع: `.github/workflows/build-apk.yml` — شغّلها من `Actions ▸ Build APK ▸ Run workflow`.

---

## 7) حلّ المشكلات الشائعة

| المشكلة | السبب | الحل |
|---|---|---|
| `Unsupported class file major version` | JDK قديم/جديد جداً | استخدم JDK 17: `File ▸ Settings ▸ Build Tools ▸ Gradle ▸ Gradle JDK` |
| `SDK location not found` | لا يوجد `local.properties` | أضف `sdk.dir=...` أو افتح المشروع من Android Studio |
| `Could not resolve androidx...` | لا إنترنت أو مستودعات محجوبة | استخدم شبكة أخرى أو VPN، ثم `./gradlew --refresh-dependencies` |
| أيقونة/خط مفقود | لم تُنسخ ملفات `res/` | تأكد أنك بنيت من مجلد `android/` وليس الجذر |
| `R8: Missing class ...` | ProGuard في الإصدار | الملف `app/proguard-rules.pro` يحتوي القواعد اللازمة — لا تحذفه |
| التطبيق يفتح ويقول «غير مُهيّأ» | مفاتيح Supabase ناقصة | أضفها في `local.properties` أو من شاشة الإعداد الأول |
| الشات لا يحدّث الرسائل | Realtime غير مفعّل في Supabase | `Database ▸ Replication ▸ supabase_realtime` ثم فعّل: chat_messages, chat_reactions, chat_typing, chat_read_state, chat_notifications |
| الدفع يُرفض دائماً | جداول الدفع أو طرق الدفع غير مهيّأة | نفّذ `supabase/01_schema.sql` ثم `06_seed.sql` وراجع `docs/03-PAYMENTS-AR.md` |

---

## 8) قائمة تحقق قبل توزيع الملف

- [ ] سجّلت حساباً جديداً ثم دخلت وخرجت (Auth يعمل)
- [ ] ظهرت الخطط الثلاث في شاشة الاشتراكات وطلبت اشتراكاً ورأيته في «طلباتي»
- [ ] الأدمن وافق على الطلب فرُقّيت الخطة فوراً بعد `refreshAll`
- [ ] نزّلت أداة مجانية، ثم جرّبت أداة «Epic» وظهرت رسالة الترقية
- [ ] تحميل أداة AC نجح وخصم الرصيد وظهر في سجل المعاملات
- [ ] الشات: أرسلت رسالة، تفاعلت بإيموجي، ثبّت رسالة، أبلغت عن رسالة
- [ ] لوحة الإدارة: عدّلت خطة مستخدم، أنشأت أداة جديدة، وافقت على إيصال
- [ ] غيّرت اللغة إلى العربية/الإنجليزية/التركية وتأكدت أن التخطيط RTL/LTR صحيح
- [ ] فعّلت الوضع الفاتح وتأكدت من الألوان

---

## 9) ملاحظات فنية مهمة

* **اسم الحزمة:** `com.addonstation.mobile` (النسخة التجريبية `…mobile.debug`)، رقم الإصدار 1.2.
* **أقل إصدار مدعوم:** Android 7.0 (API 24) — **الهدف:** Android 15 (API 35).
* **اللغات المدمجة:** ar · en · pt · es · ru · zh · fr · tr (420 مفتاح ترجمة متطابق في كل لغة — يُفحص بأمر `python3 tools/check_i18n.py`).
* **الخطوط:** خط البكسل داخل `res/font/pixel_regular.ttf` (مبني من Press Start 2P، رخصة OFL — النص الأصلي في `res/font/OFL.txt`).
* **حجم المتوقع:** ~12–16 MB لنسخة الإصدار (بعد R8 و shrinkResources).
* **لا يحتوي التطبيق على أي مفتاح خدمة أو سر** — كل العمليات الحساسة تمرّ بدوال SQL محمية داخل Supabase.

---

## 10) ماذا بعد البناء؟

1. ارفع `app-release.apk` إلى التحويل المباشر (أو Google Play داخلي).
2. من لوحة الإدارة: أضف الأصول (أفاتار/بانر/أدوات/ملصقات) وارفعها كما في `docs/02-SUPABASE-SETUP-AR.md`.
3. للمدفوعات الدولية لاحقاً: انشر دالتَي Stripe الموجودتين في `supabase/functions/` واتبع `docs/03-PAYMENTS-AR.md`.

---

_آخر تحديث: هذا الملف جزء من سلسلة `docs/` — راجع أيضاً `01-ROADMAP-AR.md` (الخطة) و`02-SUPABASE-SETUP-AR.md` (الخادم) و`03-PAYMENTS-AR.md` (الدفع) و`04-ARCHITECTURE-AR.md` (بنية الكود)._
