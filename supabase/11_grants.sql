-- ============================================================================
--  AddonStation Mobile — 11) منح الصلاحيات (Grants) للـData API
--
--  ⚠️ إلزامي في المشاريع الجديدة:
--     ابتداءً من 30 مايو 2026 لم تعد Supabase تمنح anon / authenticated أي
--     صلاحية تلقائياً على جداول public. بدون هذا الملف يظهر للتطبيق الخطأ:
--         permission denied for table chat_channels
--         hint: GRANT SELECT ON public.chat_channels TO anon;
--
--     القاعدة الذهبية: **RPC + RLS لا يكفيان** — يجب أيضًا:
--         (1) منح الصلاحية على مستوى الجدول/العمود  ← هذا الملف
--         (2) السماح على مستوى الصف عبر سياسة RLS    ← 04_rls.sql
--
--  نفّذ هذا الملف في SQL Editor (المالك: postgres). آمن للتكرار (idempotent).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) القراءة: كل الجداول متاحة للمفتاح العام وللمسجّلين
--    (RLS هي التي تحدد أي صفوف تُرى فعلاً — لا تسريب)
-- ---------------------------------------------------------------------------
grant select on all tables in schema public to anon, authenticated;

-- إن كانت خصوصية أصول الأدمن (40) مطبقة، لا تعِد فتح profiles عند إعادة هذا
-- الملف لاحقاً؛ القراءة تكون من العرض المنقّح فقط.
do $$
begin
  if to_regclass('public.visible_profiles') is not null then
    revoke select on public.profiles from public, anon, authenticated;
    grant select on public.visible_profiles to anon, authenticated;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2) الكتابة المباشرة التي يحتاجها التطبيق (بقية الكتابة تتم عبر دوال RPC)
--    RLS تتحقق من الدور: أدمن للوحة، وصاحب الحساب لملفه الشخصي
-- ---------------------------------------------------------------------------
do $$
begin
  -- لوحة الإدارة: أصول الموقع وحزم الملصقات (insert/update/delete مباشرة)
  begin
    grant insert, update, delete on public.site_assets   to authenticated;
    grant insert, update, delete on public.sticker_packs to authenticated;
  exception when undefined_table then null;
  end;

  -- الملف الشخصي: منح على مستوى العمود (المستخدم يعدّل حقوله المسموحة فقط)
  begin
    -- اختيار الأفاتار/الغلاف/الأداة يمر حصراً عبر set_profile_asset كي تُفحص
    -- الخطة وadmin على الخادم؛ لا نمنح equipped_item_id مباشرةً.
    grant update (display_name, bio, custom_status, show_online_status, country,
                  last_seen_at)
      on public.profiles to authenticated;
    revoke update (equipped_item_id) on public.profiles from authenticated;
  exception when undefined_table or undefined_column then null;
  end;

  -- حذف صديق من قائمة الأصدقاء
  begin
    grant delete on public.user_friends to authenticated;
  exception when undefined_table then null;
  end;
end $$;

-- ---------------------------------------------------------------------------
-- 3) المتسلسلات: الإدراج المباشر (لوحة الإدارة) يستهلك nextval على id
-- ---------------------------------------------------------------------------
grant usage, select on all sequences in schema public to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4) حماية دوال بوابة الدفع: تُنفَّذ بـservice_role فقط (لا التطبيق ولا anon)
--    (ينجح حتى لو لم تُنفَّذ 09_gateway_functions.sql بعد)
-- ---------------------------------------------------------------------------
do $$
begin
  begin
    revoke all on function public.gateway_confirm_order(text, text, text) from public, anon, authenticated;
  exception when undefined_function then null;
  end;
  begin
    revoke all on function public.gateway_attach_session(uuid, text, text) from public, anon, authenticated;
  exception when undefined_function then null;
  end;
end $$;

-- ---------------------------------------------------------------------------
-- 4.5) دور service_role: يحتاجه سير الرفع الآلي من GitHub لكتابة الأصول
--      (بلا هذه المنح يرفض PostgREST الكتابة بخطأ 42501 insufficient_privilege)
-- ---------------------------------------------------------------------------
grant usage on schema public to service_role;

do $$
declare t text;
begin
  for t in
    select tablename from pg_tables where schemaname = 'public'
  loop
    execute format('grant select, insert, update, delete on public.%I to service_role', t);
  end loop;
end $$;

grant select, insert, update, delete on public.site_assets to service_role;
grant select, insert, update, delete on public.sticker_packs to service_role;

-- ---------------------------------------------------------------------------
-- 5) للمستقبل: أي جدول/متسلسلة تُنشأ في public تُمنح القراءة تلقائياً
--    (ملاحظة: هذا يخص دور postgres — وهو الدور الذي ينفّذ SQL Editor)
-- ---------------------------------------------------------------------------
alter default privileges for role postgres in schema public
  grant select on tables to anon, authenticated;

alter default privileges for role postgres in schema public
  grant usage, select on sequences to anon, authenticated;

-- ---------------------------------------------------------------------------
-- ✅ تحقّق: المتوقّع  anon = 33  و  authenticated = 33
-- ---------------------------------------------------------------------------
select grantee, count(*) as tables_with_select
  from information_schema.role_table_grants
 where table_schema = 'public'
   and privilege_type = 'SELECT'
   and grantee in ('anon', 'authenticated')
 group by grantee
 order by grantee;
