-- ============================================================================
--  AddonStation Mobile — ٢٦) إصلاح:
--      permission denied for sequence site_assets_id_seq   (SQLSTATE 42501)
--
--  متى يظهر؟ عند **تسجيل الأصول عبر REST** (سير عمل رفع الأغلفة/الأصول):
--  رفع الصور إلى المخزن ينجح، ثم يفشل إدراج الصفوف بـ:
--      HTTP 403 {"code":"42501","message":
--                "permission denied for sequence site_assets_id_seq"}
--  ⇒ تبقى الأصول بلا صفوف في `site_assets` فلا تظهر داخل التطبيق.
--
--  السبب: عمود `id` في `site_assets` متسلسل (bigserial)، والصلاحيات على
--  تسلسله لا تشمل أدوار `anon` / `authenticated` / `service_role` — فالإدراج
--  يحتاج USAGE على التسلسل ولا يجده.
--
--  الحل: منح الصلاحية على التسلسل الآن + جعلها افتراضية لكل تسلسل جديد.
--
--  ⚠️ نفّذه مرة واحدة بصلاحية المالك (SQL Editor يعمل بصلاحية postgres).
-- ============================================================================

-- ١) تسلسل site_assets الحالي
do $$
begin
    if exists (
        select 1 from pg_sequences
         where schemaname = 'public' and sequencename = 'site_assets_id_seq'
    ) then
        execute 'grant usage, select, update on sequence public.site_assets_id_seq '
                'to anon, authenticated, service_role';
        raise notice '✅ مُنحت صلاحية التسلسل site_assets_id_seq';
    else
        raise notice 'ℹ️ لا يوجد تسلسل site_assets_id_seq (الجدول بلا serial؟)';
    end if;
end;
$$;

-- ٢) كل التسلسلات الحالية في public (يمنع تكرار الخطأ في جداول أخرى)
do $$
declare
    seq text;
begin
    for seq in
        select format('%I.%I', schemaname, sequencename)
          from pg_sequences
         where schemaname = 'public'
    loop
        execute format('grant usage, select, update on sequence %s to anon, authenticated, service_role', seq);
    end loop;
    raise notice '✅ صلاحيات التسلسلات الحالية محدَّثة';
end;
$$;

-- ٣) افتراضياً لكل تسلسل يُنشأ لاحقاً
alter default privileges in schema public
  grant usage, select, update on sequences to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- تحقّق
-- ---------------------------------------------------------------------------
select sequencename,
       has_sequence_privilege('anon', 'public.site_assets_id_seq', 'usage')          as "anon",
       has_sequence_privilege('authenticated', 'public.site_assets_id_seq', 'usage') as "authenticated",
       has_sequence_privilege('service_role', 'public.site_assets_id_seq', 'usage')  as "service_role"
  from pg_sequences
 where schemaname = 'public' and sequencename = 'site_assets_id_seq';

do $$
declare
    ok_anon  boolean;
    ok_auth  boolean;
    ok_svc   boolean;
begin
    select has_sequence_privilege('anon', 'public.site_assets_id_seq', 'usage'),
           has_sequence_privilege('authenticated', 'public.site_assets_id_seq', 'usage'),
           has_sequence_privilege('service_role', 'public.site_assets_id_seq', 'usage')
      into ok_anon, ok_auth, ok_svc;

    if not (ok_anon and ok_auth and ok_svc) then
        raise exception '✗ الصلاحية لم تُمنح: anon=% · authenticated=% · service_role=%',
            ok_anon, ok_auth, ok_svc;
    end if;

    raise notice '✅ 26_fix_site_assets_sequence: التسلسل متاح لكل الأدوار — أعد تشغيل سير الرفع';
end;
$$;
