-- ============================================================================
--  AddonStation Mobile — 19) تثبيت حماية لوحة الإدارة
--
--  الطلب: «تأمين لوحة إدارة التحكم بحيث تصبح للأدمن فقط وتكون محمية.»
--
--  ما يفعله هذا الملف (كلّه آمن لإعادة التنفيذ):
--      1) بوابة دخول رسمية: admin_panel_access() — يتحقق الخادم من الدور، ويُسجّل
--         كل فتح للوحة في سجلّ التدقيق (admin_audit_log) بمن ومتى.
--      2) سحب التنفيذ من الزائر (anon): لا يستطيع الزائر — وإن كان غير مسجَّل —
--         استدعاء أي دالة إدارية إطلاقاً، حتى لو كانت الرسالة «not_admin».
--      3) تثبيت سياسات RLS على جداول اللوحة الحساسة (للأدمن فقط).
--      4) فحص تلقائي: يتأكد أن **كل** دوال الإدارة (admin_*) تحتوي فحص is_admin،
--         وأن جداول اللوحة محمية بسياسة أدمن — ويطلق تحذيراً واضحاً إن اختل شيء.
--
--  التطبيق مطابق (AdminGate):
--      • لا يفتح اللوحة إلا لحساب دوره admin.
--      • يستدعي admin_panel_access() ويُغلق الباب إن لم يوافق الخادم.
--      • يطلب كلمة مرور الحساب لفتح اللوحة، ويقفلها تلقائياً بعد ١٥ دقيقة،
--        مع إيقاف مؤقت بعد ٥ محاولات خاطئة.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) بوابة الدخول الرسمية للوحة + سجلّ تدقيق لكل فتح
-- ---------------------------------------------------------------------------
create or replace function public.admin_panel_access()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  v_role text;
  v_ok   boolean;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required', 'is_admin', false);
  end if;

  v_role := public.current_role_of(v_uid);
  v_ok := public.is_admin(v_uid);

  if not v_ok then
    -- محاولة فتح من غير أدمن: تُسجَّل أيضاً (للأمن) ثم تُرفض
    insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
    values (v_uid, 'panel_denied', 'admin_panel', v_uid::text,
            jsonb_build_object('role', v_role, 'at', now()));

    return jsonb_build_object('ok', false, 'message', 'not_admin',
                              'is_admin', false, 'role', v_role);
  end if;

  -- أدمن: يُسجَّل فتح اللوحة بمن ومتى
  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'panel_opened', 'admin_panel', v_uid::text,
          jsonb_build_object('role', v_role, 'at', now()));

  return jsonb_build_object('ok', true, 'is_admin', true, 'role', v_role,
                            'verified_at', now());
end;
$$;

comment on function public.admin_panel_access() is
  'بوابة لوحة الإدارة: تتحقق أن المستدعي أدمن وتُسجّل الفتح/الرفض في سجلّ التدقيق.';

-- ---------------------------------------------------------------------------
-- 2) الزائر (anon) لا يستدعي أي دالة إدارية — سحب التنفيذ منها كلها
--    (المسجَّل يبقى قادراً على النداء، وداخلها فحص is_admin يقرّر).
-- ---------------------------------------------------------------------------
do $$
declare fn record;
begin
  for fn in
    select p.oid::regprocedure as sig
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prokind = 'f'
       and (p.proname like 'admin\_%' or p.proname like '%\_admin' or p.proname like 'chat\_admin\_%')
  loop
    execute format('revoke execute on function %s from anon', fn.sig);
    execute format('grant execute on function %s to authenticated, service_role', fn.sig);
  end loop;
end;
$$;

revoke execute on function public.admin_panel_access() from anon;
grant execute on function public.admin_panel_access() to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 3) تثبيت سياسات RLS على جداول اللوحة (للأدمن فقط) — نفس سياسات 04_rls بلا تغيير
-- ---------------------------------------------------------------------------
drop policy if exists settings_admin on public.app_settings;
create policy settings_admin on public.app_settings
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists audit_admin_read on public.admin_audit_log;
create policy audit_admin_read on public.admin_audit_log
  for select using (public.is_admin());

drop policy if exists ac_redeem_codes_admin on public.ac_redeem_codes;
create policy ac_redeem_codes_admin on public.ac_redeem_codes
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists orders_admin_all on public.payment_orders;
create policy orders_admin_all on public.payment_orders
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- 4) فحص تلقائي: كل دوال الإدارة تفحص is_admin + جداول اللوحة محمية
-- ---------------------------------------------------------------------------
do $$
declare
  r record;
  v_bad text[] := '{}';
  t text;
  v_missing text[] := '{}';
begin
  -- 4أ) دوال الإدارة
  for r in
    select p.proname as name, pg_get_functiondef(p.oid) as def
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prokind = 'f'
       and (p.proname like 'admin\_%' or p.proname like 'chat\_admin\_%')
  loop
    if r.def not ilike '%is_admin%' then
      v_bad := v_bad || r.name;
    end if;
  end loop;

  if array_length(v_bad, 1) > 0 then
    raise warning 'دوال إدارية بلا فحص is_admin: %', array_to_string(v_bad, ', ');
  else
    raise notice '✅ كل دوال الإدارة تحتوي فحص is_admin.';
  end if;

  -- 4ب) جداول اللوحة
  foreach t in array array[
    'app_settings','admin_audit_log','ac_redeem_codes','payment_orders','content_items',
    'creator_applications','creator_submissions','creator_tips','site_assets','sticker_packs'
  ] loop
    if not exists (
      select 1 from pg_policies
       where schemaname = 'public' and tablename = t
         and (coalesce(qual,'') ilike '%is_admin%' or coalesce(with_check,'') ilike '%is_admin%')
    ) then
      v_missing := v_missing || t;
    end if;
  end loop;

  if array_length(v_missing, 1) > 0 then
    raise warning 'جداول بلا سياسة أدمن: %', array_to_string(v_missing, ', ');
  else
    raise notice '✅ كل جداول اللوحة محمية بسياسات أدمن.';
  end if;

  raise notice 'حماية لوحة الإدارة مُثبَّتة — البوابة: admin_panel_access()';
end;
$$;

-- ============================================================================
--  الخُلاصة:
--    • الأدمن المسجَّل   → admin_panel_access() = ok:true  + تُسجَّل زيارة panel_opened
--    • غير الأدمن       → ok:false / not_admin + تُسجَّل محاولة panel_denied
--    • الزائر (anon)    → لا يستطيع نداء أي دالة إدارية (revoke)
--    • الواجهة          → بوابة AdminGate: الدور + الخادم + كلمة المرور + قفل تلقائي
--    • الوصول           → مقصور على من يملك كلمة مرور حساب الأدمن على الجهاز نفسه
-- ============================================================================
