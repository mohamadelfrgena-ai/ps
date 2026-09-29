# حزمة SQL الجاهزة — AddonStation Mobile

هذا المجلد يوفر **رابط تحميل مباشر** لحزمة SQL كاملة، تُنفَّذ على مشروع Supabase الجديد.

## ⬇️ الرابط المباشر

```
https://github.com/mohamadelfrgena-ai/ps/raw/arena/01a0e97c-ps/sql-package/AddonStation-Supabase-SQL.zip
```

أو من صفحة الملف (ثم زر **Download raw file**):

```
https://github.com/mohamadelfrgena-ai/ps/blob/arena/01a0e97c-ps/sql-package/AddonStation-Supabase-SQL.zip
```

> ⚠️ المودع **خاص (Private)** — الرابط يعمل بعد تسجيل الدخول بحسابك على GitHub في نفس المتصفح.

## 📦 محتويات الحزمة (13 ملفاً)

| الملف | الوصف |
|---|---|
| `00_ALL_IN_ONE.sql` | **الملف الأساسي** — 178 KB · 33 جدولاً · 65 دالة · 76 سياسة · 6 مخازن · البيانات الأولية · المزامنة اللحظية |
| `01_schema.sql` … `07_seed_assets.sql` | الملفات المنفصلة (خطة بديلة بنفس الترتيب) |
| `10_realtime.sql` | تشغيل المزامنة اللحظية (مشمولة في الجامع أيضاً) |
| `08_admin_bootstrap_READY.sql` | تعيين الأدمن — البريد مكتوب داخله |
| `08_admin_bootstrap_example.sql` | النسخة العامة (`you@example.com`) |
| `09_gateway_functions.sql` | اختياري: بوابات الدفع الإلكتروني |
| `README-AR.txt` | دليل التنفيذ + استعلام التحقق + حل الأخطاء الشائعة |

## 🚀 ترتيب التنفيذ

1. `00_ALL_IN_ONE.sql` → Supabase → **SQL Editor** → لصق كامل → **RUN** (المتوقّع: `Success. No rows returned`).
2. استعلام التحقق (في `README-AR.txt`) — المتوقّع: `33 · 6 · 76 · 3 · 13 · 6 · 5 · 286`.
3. **Authentication → Users → Add user** بالبريد المذكور في ملف الأدمن + ✅ Auto Confirm User.
4. `08_admin_bootstrap_READY.sql` → **RUN** (المتوقّع: `تم تعيين ... مديراً (Admin) ✅`).

## 🛠️ إعادة بناء الحزمة

```bash
python3 tools/serve_sql_package.py --email your@email.com --port 8000
# ثم: http://localhost:8000
```

## 🔐 ملاحظتان

- الحزمة **لا تحتوي أي مفتاح سري** (لا `sb_secret_` ولا `service_role`) — المفتاح العام وحده يدخل التطبيق.
- إن جعلت المودع **عاماً** يوماً، احذف ملف `08_admin_bootstrap_READY.sql` من هذه الحزمة أولاً (يحتوي بريداً إلكترونياً).
