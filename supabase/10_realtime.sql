-- ============================================================================
--  AddonStation Mobile — 10) تشغيل المزامنة اللحظية (Realtime) للشات
--  نفّذ هذا الملف بعد 03_functions_chat.sql و 04_rls.sql.
--  آمن للتشغيل أكثر من مرة (يتخطّى الجداول المضافة مسبقاً).
-- ============================================================================

-- 1) تأكيد وجود منشور المزامنة الافتراضي (يوجد في كل مشروع Supabase جديد)
do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
    raise notice 'أُنشئ المنشور supabase_realtime';
  end if;
end $$;

-- 2) إضافة الجداول المطلوبة للشات والإشعارات والملفات الشخصية
do $$
declare
  t text;
  tables text[] := array[
    'chat_messages',      -- الرسائل الجديدة
    'chat_reactions',     -- التفاعلات
    'chat_typing',        -- «يكتب الآن…»
    'chat_read_state',    -- مقروء/غير مقروء
    'chat_notifications', -- إشعارات المنشن والردود
    'profiles'            -- تغيّرات الاسم/الصورة/الرتبة
  ];
begin
  foreach t in array tables loop
    begin
      execute format('alter publication supabase_realtime add table public.%I', t);
      raise notice '✅ أُضيف للـ Realtime: %', t;
    exception
      when duplicate_object then
        raise notice '↺ موجود مسبقاً: %', t;
      when undefined_table then
        raise notice '⚠️ الجدول غير موجود (نفّذ 01_schema.sql أولاً): %', t;
    end;
  end loop;
end $$;

-- 3) تأكيد أن كل الجداول صارت في المنشور (يجب أن تظهر 6 صفوف أو أكثر)
select schemaname, tablename
from pg_publication_tables
where pubname = 'supabase_realtime'
order by tablename;

-- ملاحظة: مفاتيح RLS هي التي تحدّد ما يصل لكل مستخدم لحظياً،
-- ولا تُرسَل أي بيانات مستخدم لغير صاحبها حتى لو كان الاشتراك مفتوحاً.
