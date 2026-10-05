# 07 — بريد AddonStation: الاسم والشعار والروابط (عربي)

هذا الدليل يشرح **ثلاثة أمور في لوحة Supabase** تجعل بريد التطبيق يبدو باسم
**AddonStation** وبشعاره، ويجعل **رابط استعادة كلمة المرور يفتح التطبيق**،
ثم خطوات اختبار سريعة.

---

## ⚡ أهم حقيقة قبل كل شيء: **اسم المُرسِل لا يُغيَّره كود ولا قالب**

الاسم الذي تراه في Gmail (`supabase` مع رابط إلى supabase.com) هو **اسم المُرسِل
في خادم البريد**، وخادم Supabase الافتراضي يرسل دائماً:

```
Supabase <noreply@mail.app.supabase.io>
```

**لا يمكن** تغييره لا من التطبيق ولا من القالب. له **طريقان** فقط:

| الطريق | متى تختاره | ما تحتاجه |
|---|---|---|
| **(أ) SMTP مخصّص** ✅ الأسهل | اختر هذا غالباً | حساب بريد مجاني (Resend) + نطاقك (أو نطاق فرعي) |
| **(ب) Send Email Hook** | إن أردت بريداً كاملاً بـ HTML خاص بدون SMTP | مفتاح API من مزوّد + تفعيل `pg_net` (أجهّزه لك في ملف SQL) |

> ⚠️ **تحذير يخصّ المستخدمين وليس الشكل فقط:** خادم Supabase الافتراضي
> مخصّص للتجربة: سقفه **نحو ٤ رسائل في الساعة**، ويرسل غالباً إلى **عناوين
> فريق المشروع فقط**. أي أن كثيراً من مستخدميك **لا يصلهم رمز التأكيد ولا رابط
> الاستعادة أصلاً**! الطريق (أ) أو (ب) يحلّ هذه المشكلة أيضاً — فهي ليست
> مسألة «شكل» فقط.

---

## ٠) المتطلب الأول (مهم جداً) — السماح برابط التطبيق

بدون هذه الخطوة يظل رابط استعادة كلمة المرور يفتح **صفحة الموقع** لا التطبيق،
مهما عدّلنا في الكود.

1. افتح مشروعك في [supabase.com/dashboard](https://supabase.com/dashboard).
2. **Authentication → URL Configuration → Redirect URLs** → أضف سطراً:

   ```
   addonstation://recovery
   ```

   (يمكنك أيضاً إضافة `addonstation://**` لتغطية أي رابط فرعي مستقبلاً.)
3. اضغط **Save**.

> إن لم يكن الرابط في القائمة، يتجاهل الخادم `redirect_to` ويعود إلى
> **Site URL** — وهذا هو السبب الأشهر لـ «يصل الرابط ولا يفتح التطبيق».

---

## ١) تغيير اسم المُرسِل من `supabase` إلى `AddonStation`

### (أ‑١) **الأسهل مباشرةً: Gmail SMTP** — بلا نطاق ولا حساب جديد

إن كان كل ما تملكه هو `silverfoxinteractive@gmail.com` فهذه أسرع طريقة
(دقيقتان) وستظهر رسائلك باسم **AddonStation**:

1. **فعّل التحقّق بخطوتين** على حساب Google:
   [myaccount.google.com/security](https://myaccount.google.com/security)
   ← *التحقق بخطوتين* ← تفعيل.
2. **أنشئ «كلمة تطبيقات» (App Password)**:
   [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   ← الاسم `AddonStation` ← **إنشاء** ← **انسخ الـ١٦ حرفاً** كما هي (بلا مسافات).
   > كلمة المرور العادية للجيميل **لن تعمل** — لابد من كلمة التطبيقات.
3. في Supabase: **Project Settings → Authentication → SMTP Settings**
   ← فعّل **Enable Custom SMTP** ← املأ حرفياً:

   | الحقل | القيمة |
   |---|---|
   | **Sender name** | `AddonStation` |
   | **Sender email** | `silverfoxinteractive@gmail.com` |
   | **Host** | `smtp.gmail.com` |
   | **Port** | `465` |
   | **Username** | `silverfoxinteractive@gmail.com` |
   | **Password** | كلمة التطبيقات (١٦ حرفاً) |
   | **Minimum TLS version** | `TLS 1.2` |

4. **Save** ← ثم **Authentication → Rate Limits** ← ارفع سقف البريد
   (مثلاً ٣٠ في الساعة — جيميل يتحمّل نحو ٥٠٠ رسالة يومياً، فالرقم آمن).
5. جرّب: التطبيق ← نسيت كلمة المرور؟ ← يجب أن يصلك البريد باسم **AddonStation**.

> النتيجة: يظهر الاسم **AddonStation** والعنوان تحته `silverfoxinteractive@gmail.com`
> (لا يمكن إخفاء العنوان — هو حسابك). متى أردت لاحقاً عنواناً باسم التطبيق،
> خذ نطاقاً وانتقل إلى (أ‑٢).

### (أ‑٢) الأفضل للإنتاج: نطاقك + Resend

إن كنت لا تملك نطاقاً فاذهب مباشرةً إلى **(أ‑١) Gmail SMTP** أعلاه — هي
الأسهل. وإن أردت عنواناً باسم التطبيق (`@addonstation.com`) فاتبع (أ‑٢).

1. في [resend.com](https://resend.com) أنشئ حساباً (٣٠٠٠ رسالة/شهر مجاناً).
2. **Domains → Add Domain** ← أضف نطاقك ← انسخ سجلّات **SPF و DKIM** إلى DNS ← **Verify**.
3. **API Keys → Create** ← انسخ المفتاح.
4. في Supabase: **Project Settings → Authentication → SMTP Settings**
   ← فعّل **Enable Custom SMTP** ← املأ:

   | الحقل | القيمة |
   |---|---|
   | **Sender name** | `AddonStation` |
   | **Sender email** | `no-reply@addonstation.com` (بنطاقك المُوثَّق) |
   | **Host** | `smtp.resend.com` |
   | **Port** | `465` (أو `587`) |
   | **Username** | `resend` |
   | **Password** | مفتاح API من Resend |
   | **Minimum TLS version** | `TLS 1.2` |

5. **Save** ← ثم **Authentication → Rate Limits** ← ارفع سقف البريد.

**فائدة إضافية في الحالتين:** رفع السقف — الخادم الافتراضي لا يرسل إلا لعناوين
فريق المشروع تقريباً، فلا يصل الرابط لمعظم المستخدمين! Custom SMTP يحلّ هذه أيضاً.

### (ب) الطريق الآخر: Send Email Hook (بريد كامل بلا SMTP)

إن لم ترغب بـ SMTP، يمكنني تجهيز ملف SQL ينشئ دالة `hook_send_email` ترسل
البريد من مزوّدك عبر HTTP (Resend) باسم **AddonStation** وبالشعار وبنصّ عربي
كامل — كل ما تحتاجه: مفتاح API + تفعيل `pg_net` مرة واحدة. قل «**جهّز الـ
Email Hook**» وأكتبه لك.

---

## ٢) شعار AddonStation داخل الرسالة

قوالب Supabase **لا تقبل صوراً مضمّنة** (base64) — تحتاج رابطاً عاماً.
رفعنا الشعار إلى مخزنك العام:

```
https://jqjmkssgjdsfdojkuqzq.supabase.co/storage/v1/object/public/site-assets/brand/addonstation-logo.png
```

(مُرفَع فعلاً — معرّف مشروعك `jqjmkssgjdsfdojkuqzq`).

الملف الأصلي في المستودع: `brand/addonstation-logo.png` (٢٥٦×٢٥٦ بشفافية)،
وإعادة رفعه تتم بـ:

```bash
python3 tools/upload_brand.py --dir ./brand \
    --url https://xxxx.supabase.co --key sb_secret_…
```

---

## ٣) القوالب الجاهزة (انسخها كما هي)

**Authentication → Emails** ← اختر القالب ← الصق الكود ← **Save**.

### أ) قالب «Reset password» (استعادة كلمة المرور)

```html
<div dir="rtl" style="background:#0B0F14;padding:24px;font-family:Tahoma,Arial,sans-serif">
  <div style="max-width:480px;margin:auto;background:#131A24;border:1px solid #263240;border-radius:16px;padding:24px;text-align:center">
    <img src="https://jqjmkssgjdsfdojkuqzq.supabase.co/storage/v1/object/public/site-assets/brand/addonstation-logo.png"
         alt="AddonStation" width="72" height="72"
         style="display:block;margin:0 auto 12px;border-radius:16px" />
    <h1 style="margin:0 0 6px;font-size:20px;color:#E8EEF5">AddonStation</h1>
    <p style="margin:0 0 18px;font-size:14px;color:#8EA0B3">استعادة كلمة المرور</p>
    <p style="margin:0 0 18px;font-size:15px;color:#E8EEF5;line-height:1.7">
      تلقّينا طلباً لإعادة تعيين كلمة المرور لحسابك.<br/>
      اضغط الزر أدناه لاختيار كلمة مرور جديدة.
    </p>
    <a href="{{ .ConfirmationURL }}"
       style="display:inline-block;padding:13px 26px;background:#3DDC84;color:#04210F;
              font-size:16px;font-weight:bold;text-decoration:none;border-radius:12px">
      اختيار كلمة مرور جديدة
    </a>
    <p style="margin:18px 0 0;font-size:12px;color:#5C6F82;line-height:1.7">
      إن لم تطلب ذلك فتجاهل هذه الرسالة — كلمة مرورك الحالية كما هي.<br/>
      الرابط صالح لمدة محدودة وللمرّة الواحدة.
    </p>
  </div>
  <p style="margin:16px 0 0;text-align:center;font-size:12px;color:#5C6F82">
    © AddonStation — مجتمع الإضافات
  </p>
</div>
```

### ب) قالب «Confirm signup» (تأكيد الحساب)

```html
<div dir="rtl" style="background:#0B0F14;padding:24px;font-family:Tahoma,Arial,sans-serif">
  <div style="max-width:480px;margin:auto;background:#131A24;border:1px solid #263240;border-radius:16px;padding:24px;text-align:center">
    <img src="https://jqjmkssgjdsfdojkuqzq.supabase.co/storage/v1/object/public/site-assets/brand/addonstation-logo.png"
         alt="AddonStation" width="72" height="72"
         style="display:block;margin:0 auto 12px;border-radius:16px" />
    <h1 style="margin:0 0 6px;font-size:20px;color:#E8EEF5">مرحباً بك في AddonStation 🎮</h1>
    <p style="margin:0 0 18px;font-size:14px;color:#8EA0B3">خطوة واحدة وتكتمل</p>
    <p style="margin:0 0 18px;font-size:15px;color:#E8EEF5;line-height:1.7">
      أكّد بريدك الإلكتروني لتفعيل حسابك والانضمام إلى العائلة.
    </p>
    <a href="{{ .ConfirmationURL }}"
       style="display:inline-block;padding:13px 26px;background:#3DDC84;color:#04210F;
              font-size:16px;font-weight:bold;text-decoration:none;border-radius:12px">
      تأكيد الحساب
    </a>
    <p style="margin:18px 0 0;font-size:12px;color:#5C6F82;line-height:1.7">
      إن لم تكن أنت من أنشأ الحساب فتجاهل هذه الرسالة.
    </p>
  </div>
  <p style="margin:16px 0 0;text-align:center;font-size:12px;color:#5C6F82">
    © AddonStation — مجتمع الإضافات
  </p>
</div>
```

> **مهم:** الرابط في القالبين أعلاه **جاهز كما هو** (شعارك المرفوع).
> اترك المتغيّرَين `{{ .ConfirmationURL }}` كما هما — لا تبدّلهما.

### ج) حقل الموضوع (Subject)

| القالب | الموضوع المقترح |
|---|---|
| Reset password | `AddonStation — استعادة كلمة المرور 🔑` |
| Confirm signup  | `AddonStation — أكّد حسابك 🎮` |

---

## ٤) اختبار سريع بعد الحفظ

1. في التطبيق: **تسجيل الدخول ← «نسيت كلمة المرور؟»** ← أدخل بريدك ← **إرسال**.
2. افتح البريد — يجب أن ترى:
   - اسم المُرسِل **AddonStation** (بعد تفعيل SMTP)،
   - الشعار أعلى الرسالة،
   - زر «اختيار كلمة مرور جديدة».
3. اضغط الزر من **الهاتف نفسه** ⇒ يُفتح **التطبيق** مباشرةً على شاشة
   «كلمة المرور الجديدة» (لا المتصفّح).
4. اكتب كلمة المرور الجديدة ← **حفظ** ⇒ يُنقلك إلى شاشة الدخول، فادخل بها.

**إن فتح المتصفّح بدل التطبيق** ⇒ الرابط `addonstation://recovery` غير مضاف في
قائمة Redirect URLs (خطوة ٠) — أعدها ثم أعد إرسال الرابط.

---

## ٥) ما غيّرناه في التطبيق لهذا الغرض

| الملف | التغيير |
|---|---|
| `data/SupabaseClient.kt` | `sendRecovery` يرسل `redirect_to = addonstation://recovery`؛ و`acceptRecoveryLink` يقبل كل صيغ الرابط ويستخرج المستخدم من الـ JWT |
| `ui/nav/AppNav.kt` | أثر مستقل مفتاحه الرابط (يعمل إن وصل والتطبيق مفتوح)؛ وبعد الدخول/التسجيل ⟵ الصفحة الرئيسية |
| `MainActivity.kt` | `onNewIntent` يحدّث حالة الرابط فتلتقطها الواجهة فوراً |
| `data/repo/AuthRepository.kt` | دخول تلقائي بعد إنشاء الحساب إن سمح الخادم |
| `AndroidManifest.xml` | فلتر نوايا للرابط `addonstation://recovery` |

الفاحص `tools/check_auth_recovery.py` يمنع رجوع أيٍّ من هذه الأعطال.
