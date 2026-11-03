-- ============================================================================
--  37) الإبلاغ عن عضو من ملفّه الشخصي — بند الوثيقة §4٫٦ («الإبلاغ»)
-- ============================================================================
--  لماذا؟
--    بطاقة السلامة في شاشة «ملف عضو» فيها ثلاثة: كتم · حظر · إبلاغ. الحظر
--    والكتم مُنفَّذان (33_user_block · chat_mute_user)، أمّا الإبلاغ فكان
--    **عن رسالة** فقط (`chat_report_message` يأخذ `message_id`)، وملف العضو
--    لا يعرض رسالة بعينها ⇒ كان زرّ الإبلاغ سيبقى بلا أثر.
--
--  ماذا يفعل هذا الملف؟
--    ١) يضيف عمود `target_user_id` إلى `chat_reports` (الإبلاغ عن عضو لا رسالة).
--    ٢) يضيف دالة `chat_report_user(p_user_id, p_reason, p_note)`:
--         • تسجيل دخول إلزامي · سبب من الأسباب الأربعة نفسها
--         • **لا يمكن الإبلاغ عن نفسك**، ولا عن عضو غير موجود
--         • **إبلاغ واحد كل ٢٤ ساعة** لنفس العضو (يمنع الإغراق) — والثاني
--           يُرجع `ok = true, message = 'already_reported'` بلا تكرار صفّ
--           (فلا يرى المستخدم خطأً على عمل فعله أصلاً).
--    ٣) تقرأه لوحة الأدمن كما هي تقرأ بقية الإبلاغات (نفس الجدول والعمود
--       `status = 'open'`)، ويعرض العمود الجديد العضو المقصود.
--
--  آمن للتكرار · لا يحذف شيئاً · الإبلاغات القديمة (عن رسائل) تبقى سليمة
--  وقيمة `target_user_id` فيها `null`.
-- ============================================================================

alter table public.chat_reports
  add column if not exists target_user_id uuid references public.profiles(id) on delete cascade;

comment on column public.chat_reports.target_user_id is
  'العضو المُبلَّغ عنه من ملفّه الشخصي (null إذا كان الإبلاغ عن رسالة بعينها).';

create index if not exists chat_reports_target_idx
  on public.chat_reports(target_user_id, created_at desc);

create or replace function public.chat_report_user(
  p_user_id uuid,
  p_reason  text,
  p_note    text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $fn$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;
  if p_user_id is null then return jsonb_build_object('ok', false, 'message', 'invalid_user'); end if;
  if p_user_id = v_uid then return jsonb_build_object('ok', false, 'message', 'cannot_report_self'); end if;
  if p_reason not in ('spam', 'insult', 'inappropriate', 'other') then
    return jsonb_build_object('ok', false, 'message', 'invalid_reason');
  end if;
  if not exists(select 1 from public.profiles p where p.id = p_user_id) then
    return jsonb_build_object('ok', false, 'message', 'invalid_user');
  end if;

  -- إبلاغ واحد كل 24 ساعة لنفس العضو: لا نُغرقه ولا نُغرِق لوحة الإدارة
  if exists(
    select 1 from public.chat_reports r
     where r.reporter_id = v_uid
       and r.target_user_id = p_user_id
       and r.created_at > now() - interval '24 hours'
  ) then
    return jsonb_build_object('ok', true, 'message', 'already_reported');
  end if;

  insert into public.chat_reports(reporter_id, target_user_id, reason, note)
  values (v_uid, p_user_id, p_reason, nullif(btrim(coalesce(p_note, '')), ''));

  return jsonb_build_object('ok', true, 'message', 'reported');
end;
$fn$;

comment on function public.chat_report_user(uuid, text, text) is
  'الإبلاغ عن عضو من ملفّه الشخصي: سبب من (spam · insult · inappropriate · other) · بلا إبلاغ عن النفس · إبلاغ واحد كل 24 ساعة.';

revoke all on function public.chat_report_user(uuid, text, text) from public, anon;
grant execute on function public.chat_report_user(uuid, text, text) to authenticated;

-- تحديث فهرس PostgREST فوراً حتى لا تردّ الدالة الجديدة 404 (درس سابق)
notify pgrst, 'reload schema';
