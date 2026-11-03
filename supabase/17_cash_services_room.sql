-- -------------------------------------------------------------------
-- 17) غرفة «خدمات النقدية» — للقراءة فقط (يكتب فيها الأدمن وحده)
-- -------------------------------------------------------------------
-- شغّل هذا مرة واحدة في Supabase → SQL Editor ⇒ تنتهي المسألة:
--   • ينشئ الغرفة إن لم تكن موجودة، ويضبطها (مقفلة + صلاحية الأدمن وحده).
--   • لا يغيّر أي جدول ولا أي دالة — إدراج صف واحد فقط، قابل للتكرار.
--   • في آخر السكربت نتيجة التحقّق: يجب أن تقرأ  1  و  admin_only  و  true.
--
-- ملاحظة: التطبيق نفسه (نسخة 12:51 وما بعدها) ينشئ الغرفة تلقائياً عند
-- فتحه بحساب الأدمن، فتشغيل هذا السكربت اختياري للتحقّق أو للإصلاح الفوري.
--
-- المنع مفروض في الخادم أصلاً: دالة الإرسال تفحص is_locked و post_permission
-- قبل قبول الرسالة، فلا يستطيع مستخدم إرسال رسالة في هذه الغرفة حتى لو
-- أرسلها من برنامج آخر — والإدارة وحدها تنشر فيها (عبر «إعلان» في الأدمن).

insert into public.chat_channels (
  id, name, icon, category, description, sort_order, is_locked, slowmode_seconds, post_permission
)
values (
  'cash-services',    -- معرّف ثابت لا يتغيّر
  'خدمات النقدية',    -- الاسم الظاهر
  '💵',
  'main',             -- قسم الغرف الرئيسية
  'مقالات وخدمات نقدية — القراءة للجميع، والكتابة للإدارة فقط.',
  2,                  -- بعد الغرفة الرئيسية
  true,               -- مقفلة ⇐ لا يكتب إلا الأدمن/المشرف
  0,                  -- بلا بطء إرسال
  'admin_only'        -- صلاحية النشر: الأدمن وحده
)
on conflict (id) do update
   set name        = excluded.name,
       icon        = excluded.icon,
       description = excluded.description,
       is_locked   = true,
       post_permission = 'admin_only';

-- ── التحقّق: النتيجة المطلوبة ─────────────────────────────────────
--  نعم | 1 | true | admin_only
select
  case
    when c.id is not null and c.is_locked and c.post_permission = 'admin_only'
      then 'نعم ✅ الغرفة جاهزة للقراءة فقط'
    else 'لا ❌ لم تُضبط'
  end as النتيجة,
  c.id,
  c.name,
  c.is_locked,
  c.post_permission
  from (select 1) x
  left join public.chat_channels c on c.id = 'cash-services';

-- عدد غرف التطبيق كما هي (للتأكد أن الغرفة ضمن قائمة الغرف)
select id, name, category, is_locked, post_permission
  from public.chat_channels
 order by sort_order;
