# ملفات Supabase — ترتيب التنفيذ

نفّذ الملفات بهذا الترتيب من **Supabase → SQL Editor** (انسخ الملف كاملاً ثم Run):

| # | الملف | ملاحظات |
|---|-------|---------|
| 1 | `01_schema.sql` | الجداول والفهارس والمشغلات ودالة إنشاء الملف الشخصي تلقائياً |
| 2 | `02_functions_core.sql` | دوال الخطة/التحميل/المفضلة/AC/المدفوعات/الملف الشخصي/المحتوى/الإحصاءات |
| 3 | `03_functions_chat.sql` | الشات والإشراف والإشعارات والأصدقاء والمبدعين |
| 4 | `04_rls.sql` | الأمان: من يقرأ ومن يكتب + سحب الكتابة المباشرة عن الجداول الحساسة |
| 5 | `05_storage.sql` | المخازن الستة وسياسات الملفات |
| 6 | `06_seed.sql` | الخطط، طرق الدفع، مهام AC، 5 حزم ملصقات، **13 غرفة**، قواعد المجتمع 8 لغات |
| 7 | `07_seed_assets.sql` | 105 أداة شات + 181 ملصقاً |
| 8 | `09_gateway_functions.sql` | اختياري — دوال بوابات الدفع الإلكتروني |
| 9 | `08_admin_bootstrap.sql` | **بعد** إنشاء حسابك من التطبيق — عدّل البريد داخله أولاً |

## نتائج متوقعة للتحقق

```sql
-- عدد الجداول
select count(*) from information_schema.tables
 where table_schema = 'public' and table_type = 'BASE TABLE';       -- 33

-- عدد الغرف
select count(*) from public.chat_channels;                          -- 13

-- الأدوات والملصقات
select asset_type, count(*) from public.site_assets group by 1;     -- item=105 , sticker=181

-- الخطط
select id, price_lyd, price_usd, duration_days, download_limit from public.subscription_plans;

-- الطرق
select code, name, enabled from public.payment_method_settings order by sort_order;
```

## مخازن الملفات

| المخزن | عام؟ | الحد | الاستخدام |
|---|---|---|---|
| `content-files` | خاص | 512MB | ملفات الإضافات والعوالم (روابط موقّعة 60 ثانية) |
| `payment-receipts` | خاص | 10MB | إيصالات التحويل (كل مستخدم في مجلده) |
| `chat-images` | خاص | 4MB | صور الشات (PNG/JPEG/WEBP فقط) |
| `chat-media` | خاص | 100MB | صوت وفيديو الشات (رابط 300 ثانية) |
| `creator-media` | خاص | 200MB | ملفات المبدعين |
| `site-assets` | **عام** | 5MB | الأفاتارات والأغلفة والملصقات والأدوات |

## تنبيهات

- كل الملفات آمنة لإعادة التنفيذ، لكن **لا تُعِد تنفيذ `08` بعد تغيير البريد** إلا عن قصد.
- الأسعار الافتراضية: Epic = 25 د.ل (2.99$) و Legendary = 50 د.ل (5.99$) لمدة 30 يوماً — عدّلها من لوحة الإدارة.
- طرق الدفع مشحونة **معطّلة** حتى تدخل بيانات مستفيدك الحقيقية (اسم + رقم حساب + تعليمات) ثم تفعّلها.
