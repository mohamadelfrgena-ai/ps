-- ============================================================================
--  AddonStation Mobile — 18) الوسائط الثقيلة للأدمن فقط
--
--  الطلب: «لا يمكن لأحد إرسال مقطع فيديو وصوتي وتسجيل صوت إلا الأدمن.»
--
--  المقصود:
--      • مقاطع الفيديو        message_type = 'video'
--      • الملفات الصوتية      message_type = 'audio'
--      • التسجيلات الصوتية    message_type = 'audio' (نفس النوع — تسجيل من الميكروفون)
--    كلها **للأدمن فقط** (role = 'admin')، ولا يستطيعها المستخدم العادي ولا
--    المشرف (moderator). أما الصور والنصوص والملصقات فتبقى للجميع كما هي.
--
--  ملف 03_functions_chat.sql يمنعها داخل send_chat_message رسالةً واضحة:
--      { "ok": false, "message": "admin_only_media" }
--  وهذا الملف يضيف **شبكة أمان** على مستوى الجدول: مشغّل قبل الإدراج يرفض أي
--  صف وسائط من غير الأدمن مهما كانت طريقة الإدراج (حتى المباشرة أو من عميل قديم).
--
--  التطبيق مطابق: خيار «ملف صوتي / مرئي» لا يظهر لغير الأدمن في نافذة الإرفاق.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor. آمن لإعادة التنفيذ.
-- ============================================================================

-- 1) دالة المشغّل: ترفض إدراج رسالة صوتية أو مرئية من غير الأدمن.
create or replace function public.chat_media_admin_only_guard()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  if new.message_type in ('audio','video') then
    v_role := public.current_role_of(new.user_id);
    if v_role is distinct from 'admin' then
      raise exception 'admin_only_media'
        using errcode = 'P0001',
              hint = 'الوسائط (فيديو · صوت · تسجيل صوتي) يرسلها الأدمن فقط';
    end if;
  end if;
  return new;
end;
$$;

comment on function public.chat_media_admin_only_guard() is
  'يمنع إدراج رسائل الفيديو والصوت والتسجيلات الصوتية إلا من حساب بدور admin.';

-- 2) المشغّل على جدول الرسائل (يُسقط أولاً حتى يكون التنفيذ قابلاً للتكرار).
drop trigger if exists trg_chat_media_admin_only on public.chat_messages;
create trigger trg_chat_media_admin_only
  before insert on public.chat_messages
  for each row
  execute function public.chat_media_admin_only_guard();

-- 3) صلاحيات (الدالة تعمل ضمن الجدول، لكن نمنح التنفيذ صراحةً كما في 11_grants).
grant execute on function public.chat_media_admin_only_guard() to authenticated, service_role;

-- 4) تحقّق: يعرض عدد رسائل الفيديو/الصوت الموجودة لكل مرسل (للمراجعة فقط).
do $$
declare
  v_bad integer;
begin
  select count(*) into v_bad
    from public.chat_messages m
   where m.message_type in ('audio','video')
     and coalesce(public.current_role_of(m.user_id), '') <> 'admin';

  if v_bad = 0 then
    raise notice 'الحرس مُثبَّت ✅ — لا توجد رسائل صوتية/مرئية من غير الأدمن.';
  else
    raise warning 'يوجد % رسالة صوتية/مرئية قديمة من غير الأدمن (تبقى ظاهرة، والمنع يسري على الجديد).', v_bad;
  end if;
end;
$$;

-- ============================================================================
--  الخُلاصة:
--    الأدمن (admin)      → يرسل فيديو · صوت · تسجيل صوتي ✅
--    المستخدم العادي     → يُرفض: admin_only_media ❌ (ولا يرى الخيار في التطبيق)
--    المشرف (moderator)  → يُرفض: admin_only_media ❌
--    النص · الصورة · الملصق · الإيموجي → للجميع كما كان ✅
-- ============================================================================
