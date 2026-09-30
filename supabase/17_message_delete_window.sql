-- ============================================================================
--  AddonStation Mobile — 17) نافذة حذف الرسالة (٣ دقائق)
--
--  الغرض المطلوب حرفياً:
--      «حذف المستخدم لرسالته التي أرسلها في شات خلال ثلاث دقائق،
--       بعدها لا يستطيع أن يحذفها.»
--
--  ما يفعله هذا الملف:
--      1) يعرّف مدة الحذف في مكان واحد: public.chat_delete_window_seconds() = 180.
--      2) يعيد تعريف public.chat_delete_my_message بحيث **ترفض** أي طلب بعد انتهاء
--         المدة، وتُعيد سبباً واضحاً: delete_window_expired مع المدة وعمر الرسالة.
--      3) يُبقي الإدارة كما هي: chat_moderate_message بلا أي حدّ زمني (إخفاء/حذف).
--
--  سلوك الواجهة المطابق (منفَّذ في التطبيق):
--      • زر «حذف رسالتي» يظهر داخل الدقائق الثلاث فقط، ومعه «متبقٍ س دقيقة للحذف».
--      • بعدها يختفي الزر ويظهر نص «انتهت مدة الحذف…».
--      • أي محاولة تجاوز (عميل قديم أو تعديل يدوي) يرفضها هذا الملف من جهة الخادم.
--
--  ملاحظة: الرسائل المرسلة قبل تنفيذ هذا الملف تصبح غير قابلة للحذف فوراً إن كانت
--          أقدم من ٣ دقائق — وهو المطلوب. أما من أرسل قبل ثوانٍ فيبقى قادراً.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor. آمن لإعادة التنفيذ بلا حدود.
-- ============================================================================

-- 1) المدة في مكان واحد (بالثواني) — تغييرها هنا يغيّر القاعدة كاملة.
create or replace function public.chat_delete_window_seconds()
returns integer
language sql
immutable
as $$
  select 180::integer   -- ٣ دقائق بالثواني
$$;

comment on function public.chat_delete_window_seconds() is
  'مدة السماح للمستخدم بحذف رسالته في الشات (بالثواني) — 180 = ثلاث دقائق.';

-- 2) إعادة تعريف دالة الحذف الذاتي مع النافذة الزمنية.
create or replace function public.chat_delete_my_message(p_message_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_owner   uuid;
  v_created timestamptz;
  v_status  text;
  v_window  integer := public.chat_delete_window_seconds();
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select user_id, created_at, status
    into v_owner, v_created, v_status
    from public.chat_messages
   where id = p_message_id;

  if v_owner is null then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  -- الحذف الذاتي لصاحب الرسالة فقط (الإدارة عبر chat_moderate_message).
  if v_owner <> v_uid then
    return jsonb_build_object('ok', false, 'message', 'forbidden');
  end if;

  if v_status = 'deleted' then
    return jsonb_build_object('ok', false, 'message', 'already_deleted');
  end if;

  -- القاعدة الحاسمة: بعد ثلاث دقائق من الإرسال لا يستطيع المستخدم الحذف.
  if now() - v_created > make_interval(secs => v_window) then
    return jsonb_build_object(
      'ok', false,
      'message', 'delete_window_expired',
      'window_seconds', v_window,
      'age_seconds', floor(extract(epoch from (now() - v_created)))::int
    );
  end if;

  update public.chat_messages
     set status = 'deleted',
         content = null,
         sticker_url = null,
         image_path = null,
         media_path = null,
         deleted_by = v_uid
   where id = p_message_id
     and user_id = v_uid;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'forbidden');
  end if;

  return jsonb_build_object('ok', true, 'window_seconds', v_window);
end;
$$;

-- 3) صلاحيات التنفيذ (كما في 11_grants) — للمسجَّلين بشكل أساسي.
grant execute on function public.chat_delete_window_seconds() to authenticated, anon, service_role;
grant execute on function public.chat_delete_my_message(bigint) to authenticated, service_role;

-- 4) تحقّق سريع بعد التنفيذ: يجب أن يطبع 180.
do $$
declare v_secs integer;
begin
  v_secs := public.chat_delete_window_seconds();
  raise notice 'مدة حذف الرسالة = % ثانية (% دقيقة)', v_secs, v_secs / 60;
  if v_secs <> 180 then
    raise warning 'المدة ليست 180 ثانية! القيمة الحالية: %', v_secs;
  end if;
end;
$$;

-- ============================================================================
--  خُلاصة السلوك النهائي:
--    • رسالة عمرها أقل من ٣ دقائق  → الحذف ينجح ويتحول status إلى deleted.
--    • رسالة عمرها ٣ دقائق أو أكثر → الرفض: delete_window_expired (بلا حذف).
--    • رسالة ليست للمستخدم          → الرفض: forbidden.
--    • الإدارة (chat_moderate_message) → إخفاء/حذف بلا حدّ زمني إطلاقاً.
-- ============================================================================
