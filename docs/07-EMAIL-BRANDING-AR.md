# 07 — بريد AddonStation: الاسم والشعار والروابط (عربي)

هذا الدليل يشرح **ثلاثة أمور في لوحة Supabase** تجعل بريد التطبيق يبدو باسم
**AddonStation** وبشعاره، ويجعل **رابط استعادة كلمة المرور يفتح التطبيق**،
ثم خطوات اختبار سريعة.

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

الاسم الذي يظهر في Gmail بجوار البريد (مثل `supabase`) يأتي من **خادم البريد**،
لا من القالب. لتغييره تحتاج **SMTP مخصّصاً**:

1. **Project Settings → Authentication → SMTP Settings** (أو
   Authentication → Emails → SMTP).
2. فعّل **Enable Custom SMTP**.
3. املأ:

   | الحقل | القيمة المقترحة |
   |---|---|
   | **Sender name** | `AddonStation` |
   | **Sender email** | `no-reply@addonstation.com` (بعد تملّك النطاق وإضافته لدى المزوّد) |
   | Host / Port / User / Pass | بيانات مزوّد SMTP (Resend · SendGrid · Mailgun · Brevo …) |

4. **Save**، ثم أرسل رسالة اختبار.

**مزوّد مجاني مناسب للبداية:** Resend — `smtp.resend.com` · المنفذ `465`
(أو 587) · المستخدم `resend` · كلمة المرور = مفتاح API. أضف نطاقك therein
وسجّلات SPF/DKIM حتى لا يقع البريد في «السبام».

> ⚠️ بلا SMTP مخصّص **لا يمكن** تغيير اسم المُرسِل: خادم Supabase الافتراضي
> يرسل باسمه، وهو مقصود منه التجربة فقط (وسقفه منخفض: بضع رسائل في الساعة).

**فائدة إضافية:** رفع السقف — الخادم الافتراضي لا يرسل إلا لعناوين فريق
المشروع تقريباً، فلا يصل الرابط لمعظم المستخدمين! Custom SMTP يحلّ هذه أيضاً.

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
