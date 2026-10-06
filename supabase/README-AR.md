# ملفات Supabase — ترتيب التنفيذ

> 🚀 **الدليل الكامل خطوة بخطوة (من التسجيل حتى تشغيل التطبيق): [`../docs/02-SUPABASE-SETUP-AR.md`](../docs/02-SUPABASE-SETUP-AR.md)**

## الطريقة الأسرع: ملف واحد جامع ✨

انسخ **`00_ALL_IN_ONE.sql`** كاملاً والصقه في **Supabase → SQL Editor → Run** مرة واحدة.
يحتوي: 33 جدولاً · 65 دالة · 76 سياسة أمنية · 6 مخازن · البيانات الأولية (الخطط، 8 طرق دفع، 6 مهام AC، 5 حزم ملصقات، 13 غرفة، قواعد بـ8 لغات، 311 أصلاً (منها 25 أفاتاراً)) · تشغيل المزامنة اللحظية.

> يُعاد توليد الملف بالأمر: `python3 tools/build_all_in_one_sql.py` (لا تعدّله يدوياً).

## أو ملفاً بملف (نفس الترتيب)

| # | الملف | ملاحظات |
|---|-------|---------|
| 1 | `01_schema.sql` | الجداول والفهارس والمشغلات ودالة إنشاء الملف الشخصي تلقائياً |
| 2 | `02_functions_core.sql` | دوال الخطة/التحميل/المفضلة/AC/المدفوعات/الملف الشخصي/المحتوى/الإحصاءات |
| 3 | `03_functions_chat.sql` | الشات والإشراف والإشعارات والأصدقاء والمبدعون |
| 4 | `04_rls.sql` | الأمان: من يقرأ ومن يكتب + سحب الكتابة المباشرة عن الجداول الحساسة |
| 5 | `05_storage.sql` | المخازن الستة وسياسات الملفات |
| 6 | `06_seed.sql` | الخطط، طرق الدفع، مهام AC، 5 حزم ملصقات، **13 غرفة**، قواعد المجتمع 8 لغات |
| 7 | `07_seed_assets.sql` | 105 أداة شات + 181 ملصقاً |
| 8 | `12_avatars.sql` | 25 أفاتاراً (1 مجاني · 12 Epic · 12 Legendary) في `site_assets` |
| 9 | `10_realtime.sql` | تشغيل المزامنة اللحظية لستة جداول |
| 10 | `11_grants.sql` | **إلزامي للمشاريع الجديدة** (بعد 30 مايو 2026): منح `anon`/`authenticated` صلاحية الجداول — بدونه يظهر `permission denied for table …` |
| 11 | `08_admin_bootstrap.sql` | **بعد** إنشاء حسابك من التطبيق — عدّل البريد داخله أولاً |
| 12 | `09_gateway_functions.sql` | اختياري — دوال بوابات الدفع الإلكتروني (Stripe) |

### ملفات لاحقة (تُطبَّق بعد التجهيز الأساسي، بنفس الترتيب)

`13_channels_icons` · `14_redeem_codes` · `15_assets_v2` · `16_language_room_names`
· `17_message_delete_window` (نافذة حذف ٣ دقائق) · `18_media_admin_only` ·
`19_admin_panel_guard` · `20_sticker_with_text` · `21_banners` · `22_plan_prices` ·
`23_payment_order_usd` · `24_free_default_avatar` · `25_fix_send_chat_message_unnest` ·
`26_fix_site_assets_sequence` · `27_admin_only_assets` · `28_chat_cleanup` (تنظيف اختياري) ·
**`29_chat_moderation_rooms`** — إشراف الشات: حذف أي رسالة للأدمن والمشرف مهما كان
عمرها، ونطاق غرف المشرف (غرفة واحدة · عدّة غرف · كل الغرف) ·
**`30_chat_members_only`** — الشات للمسجّلين فقط: الزائر (مفتاح anon) لا يقرأ
رسالةً ولا غرفةً ولا تفاعلاً.

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

-- الطرق (كلها معطّلة افتراضياً حتى تُفعّلها)
select code, name, enabled from public.payment_method_settings order by sort_order;

-- المزامنة اللحظية
select tablename from pg_publication_tables where pubname = 'supabase_realtime' order by 1;
```

## رفع الأصول (311 ملفاً)

```bash
python3 tools/upload_seed_assets.py --url https://xxxx.supabase.co --key sb_secret_xxxx
```
*(جرّب أولاً مع `--dry-run`.)* التفاصيل في `docs/02-SUPABASE-SETUP-AR.md` المرحلة 6.

## مخازن الملفات

| المخزن | عام؟ | الحد | الاستخدام |
|---|---|---|---|
| `content-files` | خاص | 512MB | ملفات الإضافات والعوالم (روابط موقّعة 60 ثانية) |
| `payment-receipts` | خاص | 10MB | إيصالات التحويل (كل مستخدم في مجلده) |
| `chat-images` | خاص | 4MB | صور الشات (PNG/JPEG/WEBP فقط) |
| `chat-media` | خاص | 100MB | صوت وفيديو الشات (رابط 300 ثانية) |
| `creator-media` | خاص | 200MB | ملفات المبدعين |
| `site-assets` | **عام** | 5MB | الأفاتارات والأغلفة والملصقات والأدوات |

> 💡 في الخطة المجانية حد الرفع الفعلي للملف الواحد **50 MB** من جهة Supabase نفسها، مهما كان الحد داخل السياسة.

## تنبيهات

- كل الملفات آمنة لإعادة التنفيذ (idempotent)، لكن **لا تُعِد تنفيذ `08` بعد تغيير البريد** إلا عن قصد.
- الأسعار الافتراضية: Epic = 20 د.ل (2.99$) و Legendary = 30 د.ل (5.99$) لمدة 30 يوماً — عدّلها من لوحة الإدارة.
- طرق الدفع مشحونة **معطّلة** حتى تدخل بيانات مستفيدك الحقيقية (اسم + رقم حساب + تعليمات) ثم تفعّلها.
- المفتاح العام (`sb_publishable_…` أو `anon`) هو الوحيد الذي يدخل التطبيق. المفتاح السري (`sb_secret_…`) للوحة والسكربتات فقط.
