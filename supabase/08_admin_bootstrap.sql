-- ============================================================================
--  AddonStation Mobile — 08) تعيين حسابك مديراً (Admin / TheKing ✦)
--  نفّذ هذا الملف **بعد** إنشاء حسابك من داخل التطبيق أو من Supabase Auth.
--  بدون هذه الخطوة لن تجد لوحة الإدارة في التطبيق أبداً (وهذا مقصود).
-- ============================================================================

-- 1) استبدل البريد الإلكتروني أدناه ببريد حسابك ثم نفّذ السطر:
-- ---------------------------------------------------------------------------
-- select id, username, role, plan from public.profiles where username = 'اسمك';
-- update public.profiles set role = 'admin' where username = 'اسمك';

-- 2) أو مباشرة بالبريد الإلكتروني (الطريقة الأدق):
-- ---------------------------------------------------------------------------
do $$
declare
  v_email text := 'you@example.com';   -- ← عدّل هذا السطر
  v_uid   uuid;
begin
  select id into v_uid from auth.users where email = v_email;

  if v_uid is null then
    raise notice 'لا يوجد مستخدم بهذا البريد: %', v_email;
    return;
  end if;

  -- إنشاء الملف الشخصي إن لم يكن موجوداً (في حال أُنشئ المستخدم من لوحة Supabase مباشرة)
  insert into public.profiles (id, username, display_name)
  values (v_uid,
          coalesce(nullif(split_part(v_email,'@',1),''), 'player_' || substr(v_uid::text,1,8)),
          split_part(v_email,'@',1))
  on conflict (id) do nothing;

  insert into public.ac_wallets (user_id) values (v_uid) on conflict (user_id) do nothing;

  update public.profiles set role = 'admin' where id = v_uid;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'admin_bootstrapped', 'profile', v_uid::text, jsonb_build_object('email', v_email));

  raise notice 'تم تعيين % مديراً (Admin) ✅', v_email;
end $$;

-- 3) تحقّق:
-- ---------------------------------------------------------------------------
-- select id, username, role, plan, plan_expires_at from public.profiles where role = 'admin';

-- 4) (اختياري) إعطاء نفسك خطة أسطورية مع تاريخ انتهاء بعيد للاختبار:
-- ---------------------------------------------------------------------------
-- update public.profiles
--    set plan = 'legendary', plan_expires_at = now() + interval '365 days'
--  where role = 'admin';

-- 5) (اختياري) تفعيل طرق دفع للاختبار:
-- ---------------------------------------------------------------------------
-- update public.payment_method_settings
--    set enabled = true,
--        account_name = 'AddonStation',
--        account_identifier = '091-0000000',
--        instructions = 'حوّل المبلغ ثم ارفع صورة الإيصال ورقم العملية.'
--  where code = 'bank_transfer';
