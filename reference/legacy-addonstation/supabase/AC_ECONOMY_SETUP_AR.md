# إعداد نظام AC — AddonCoins

## قبل التشغيل

أوقف الموقع والتطبيق مؤقتًا لتجنب تعارضات قفل قاعدة البيانات، ثم افتح:

```text
Supabase Dashboard → SQL Editor → New query
```

شغّل ملف:

```text
ac-economy.sql
```

بعد تشغيل ملفات الأساس التالية:

```text
000-foundation.sql
content-schema.sql
```

ويُفضّل تشغيله بعد تجهيز الشات أيضًا حتى تعمل مكافآت المشاركة والإنجازات:

```text
CHAT_SETUP_ALL.sql
```

لتفعيل دعم المبدعين والأرباح من التبرعات، شغّل بعدها:

```text
creator-tips.sql
```

## ماذا ينشئ الملف؟

- محفظة AC آمنة لكل مستخدم تبدأ برصيد صفر.
- مكافأة الدخول الأول بقيمة 25 AC مرة واحدة.
- الدخول اليومي وسلاسل الدخول.
- مكافأة إكمال الملف الشخصي.
- مكافآت المشاركة والإنجازات والفعاليات.
- سجل معاملات كامل مع الرصيد بعد كل عملية.
- فتح الإضافة مقابل 200 AC والعالم مقابل 300 AC.
- إدارة الأدمن وإعطاء/خصم AC.
- توزيع AC لجميع المستخدمين مع Batch ID ومنع التكرار.
- مهام وإنجازات وفعاليات قابلة للإدارة.

## فحص سريع بعد التنفيذ

```sql
select count(*) as wallets from public.ac_wallets;
select slug, reward_ac, frequency, active from public.ac_tasks order by created_at;
select id, status, eligible_count, total_ac from public.ac_admin_batches order by created_at desc limit 5;
```

> لا يوجد شراء AC أو تحويل بين المستخدمين أو سحب نقدي. كل تغيير في الرصيد يمر عبر دوال آمنة ويسجل في `ac_transactions`.
