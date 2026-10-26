-- ============================================================================
--  AddonStation Mobile — ٣٥) تشغيل الإشعارات **الحقيقية** (تصل في ثوانٍ لا في يوم)
--
--  المشكلة التي يحلّها هذا الملف:
--    الملف 34 بنى كل شيء: الأجهزة · التفضيلات · الطابور · الزنادات · ساعات
--    الهدوء · التجميع. لكن **مَن يُفرّغ الطابور؟** كان الجواب: نداء يدوي
--    أو مهمة يومية واحدة — أي أن رسالة شات تصل بعد ساعات، وهذا ما يجعل
--    الإشعار «تجريبياً» في الإحساس. هنا نُشغّله فعلاً:
--
--      ١) كل **دقيقة**: إن كان في الطابور شيء مستحق ⇒ نداء دالة الإرسال.
--      ٢) **فوراً** لِما لا يحتمل التأخير (منشن/ردّ): نداء عند الإدراج نفسه.
--      ٣) مرة يومياً: تذكير الغياب · عرض الاشتراك · تنظيف السجل.
--
--  لماذا من القاعدة مباشرة (pg_cron + pg_net)؟
--    • لا خدمة جدولة خارجية، ولا حساب إضافي، ولا شيء يعتمد على جهازك.
--    • مجاني تماماً، ويعمل والهاتف مغلق.
--
--  مفتاح الإرسال السري (FCM) يبقى في **دالة الإرسال على Supabase** ولا يدخل
--  التطبيق ولا القاعدة. أما سرّ النداء (x-push-secret) فيبقى في جدول إعدادات
--  محجوب تماماً عن التطبيق (RLS بلا سياسات + سحب كل الصلاحيات).
--
--  التشغيل بخطوة واحدة (بعد نشر دالة الإرسال — انظر docs/11-PUSH-AR.md):
--      select public.push_live_enable(
--        'https://<مَعْرِف-مشروعك>.supabase.co/functions/v1/send-push',
--        'السرّ-الذي-اخترته-في-أسرار-الدالة');
--
--  ثم للفحص في أي وقت:
--      select * from public.push_doctor();
--
--  آمن لإعادة التنفيذ بالكامل في أي وقت (create if not exists · create or
--  replace · drop trigger if exists). لا يحذف أي بيانات ولا يُرسل شيئاً بنفسه.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) إعدادات التشغيل (صف واحد) — محجوب عن التطبيق تماماً
-- ---------------------------------------------------------------------------
create table if not exists public.push_config (
  id           int primary key default 1 check (id = 1),
  function_url text,                              -- رابط دالة الإرسال على Supabase
  cron_secret  text,                              -- السرّ الذي في أسرار الدالة (x-push-secret)
  enabled      boolean not null default true,
  updated_at   timestamptz not null default now()
);

-- RLS بلا أي سياسة ⇒ لا يقرأ ولا يكتب من التطبيق، وسحب الصلاحيات صراحةً
-- (لأن سوبابيز تمنح الجداول الجديدة لـ anon تلقائياً).
alter table public.push_config enable row level security;
revoke all on public.push_config from anon, authenticated;
grant all on public.push_config to service_role;

insert into public.push_config(id) values (1) on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 2) الإضافات: pg_cron (الجدولة) و pg_net (النداء المتجه للخارج)
--    كل واحدة في كتلة مستقلة: إن فشلت واحدة لا يسقط الملف كله، ويُطبع تنبيه
--    واضح بالعربية يقول ما يجب فعله من لوحة سوبابيز.
-- ---------------------------------------------------------------------------
do $$ begin
  begin
    create extension if not exists pg_cron;
    raise notice '✓ pg_cron مفعّلة (جدولة المهام).';
  exception when others then
    raise notice '! pg_cron غير مفعّلة — فعّلها من Supabase → Database → Extensions → pg_cron ثم أعد تنفيذ هذا الملف.';
  end;
  begin
    create extension if not exists pg_net;
    raise notice '✓ pg_net مفعّلة (النداء المتجه للخارج).';
  exception when others then
    raise notice '! pg_net غير مفعّلة — فعّلها من Supabase → Database → Extensions → pg_net ثم أعد تنفيذ هذا الملف.';
  end;
end $$;

-- ---------------------------------------------------------------------------
-- 3) نداء دالة الإرسال — فقط إن كان في الطابور شيء مستحق فعلاً
--
--    • قفل واحد (advisory xact) ⇒ لا تشغيلان متوازيان يرسلان الصف نفسه مرتين.
--    • لا نداء شبكة بلا داعٍ: نفحص الطابور أولاً فتبقى المهمة الدقيقة رخيصة.
--    • أي فشل هنا **يُسكَت** ولا يُسقط الإدراج الذي استدعانا (رسالة الشات
--      تُكتب في القاعدة مهما حدث للشبكة — الإشعار يُعاد عليه في الدقيقة القادمة).
-- ---------------------------------------------------------------------------
create or replace function public.push_dispatch_tick(p_limit int default 200)
returns bigint
language plpgsql security definer set search_path = public
as $$
declare
  v_url    text;
  v_secret text;
  v_due    int;
  v_req    bigint;
begin
  -- قفل على مستوى المعاملة: يُحرَّر تلقائياً عند نهايتها (لا يتراكم)
  if not pg_try_advisory_xact_lock(918273645) then
    return null;
  end if;

  begin
    select c.function_url, c.cron_secret into v_url, v_secret
      from public.push_config c where c.id = 1 and c.enabled;

    if coalesce(btrim(v_url), '') = '' or coalesce(btrim(v_secret), '') = '' then
      return null;                                  -- لم يُشغَّل بعد (push_live_enable)
    end if;

    select count(*) into v_due
      from public.push_queue q
     where q.status = 'pending' and q.scheduled_at <= now();

    if v_due = 0 then return null; end if;          -- لا شيء مستحق ⇒ بلا نداء

    select net.http_post(
      url  := v_url,
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-push-secret', v_secret
      ),
      body := jsonb_build_object(
        'action', 'dispatch',
        'limit', least(greatest(coalesce(p_limit, 200), 1), 500)
      ),
      timeout_milliseconds := 15000
    ) into v_req;

    return v_req;
  exception when others then
    return null;
  end;
end $$;

revoke all on function public.push_dispatch_tick(int) from public, anon, authenticated;
grant execute on function public.push_dispatch_tick(int) to service_role;

-- ---------------------------------------------------------------------------
-- 4) الدورة اليومية: تذكير الغياب + عرض الاشتراك + التنظيف (كلها في القاعدة)
-- ---------------------------------------------------------------------------
create or replace function public.push_daily_tick()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_re  int;
  v_off int;
  v_cl  int;
begin
  v_re  := public.push_reengage_scan(500);
  v_off := public.push_offer_scan(200);
  v_cl  := public.push_cleanup(7);
  -- ثم نُفرّغ ما تراكم (تذكيرات مؤجّلة خارج ساعات الهدوء وغيرها)
  perform public.push_dispatch_tick(500);
  return jsonb_build_object('reengage', v_re, 'offer', v_off, 'cleanup', v_cl, 'at', now());
end $$;

revoke all on function public.push_daily_tick() from public, anon, authenticated;
grant execute on function public.push_daily_tick() to service_role;

-- ---------------------------------------------------------------------------
-- 5) الفوري: منشن أو ردّ لا يحتمل التأخير ⇒ نداء عند الإدراج نفسه (ثوانٍ)
--    المهمة الدقيقة تبقى شبكة أمان إن فشل هذا النداء.
-- ---------------------------------------------------------------------------
create or replace function public.push_on_queue_urgent()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if new.priority = 'high' then
    perform public.push_dispatch_tick(50);
  end if;
  return new;
end $$;

drop trigger if exists trg_push_queue_urgent on public.push_queue;
create trigger trg_push_queue_urgent
  after insert on public.push_queue
  for each row execute function public.push_on_queue_urgent();

-- ---------------------------------------------------------------------------
-- 6) التشغيل بخطوة واحدة + الإيقاف
-- ---------------------------------------------------------------------------
create or replace function public.push_live_enable(p_url text, p_secret text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_cron   boolean := false;
  v_jobs   int := 0;
begin
  if coalesce(btrim(p_url), '') = '' then
    return jsonb_build_object('ok', false, 'message', 'url_required',
      'hint', 'مرّر رابط دالة الإرسال: https://<ref>.supabase.co/functions/v1/send-push');
  end if;
  if coalesce(btrim(p_secret), '') = '' then
    return jsonb_build_object('ok', false, 'message', 'secret_required',
      'hint', 'مرّر نفس قيمة PUSH_CRON_SECRET التي وضعتها في أسرار دالة الإرسال');
  end if;

  update public.push_config
     set function_url = btrim(p_url),
         cron_secret  = btrim(p_secret),
         enabled      = true,
         updated_at   = now()
   where id = 1;

  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    v_cron := true;
    perform cron.schedule(
      'addonstation-push-dispatch', '* * * * *',
      $cron$select public.push_dispatch_tick(200)$cron$
    );
    perform cron.schedule(
      'addonstation-push-daily', '17 3 * * *',
      $cron$select public.push_daily_tick()$cron$
    );
    select count(*) into v_jobs from cron.job
     where jobname in ('addonstation-push-dispatch', 'addonstation-push-daily');
  end if;

  return jsonb_build_object(
    'ok', true, 'cron', v_cron, 'jobs', v_jobs,
    'message', case when v_cron then 'تم التشغيل: الطابور يُفرَّغ كل دقيقة، والصيانة يومياً 03:17 UTC.'
                    else 'حُفظ الرابط والسرّ، لكن pg_cron غير مفعّلة ⇒ فعّلها ثم أعد التنفيذ.' end
  );
end $$;

create or replace function public.push_live_disable()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_off int := 0;
begin
  update public.push_config set enabled = false, updated_at = now() where id = 1;
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule('addonstation-push-dispatch');
    perform cron.unschedule('addonstation-push-daily');
    select count(*) into v_off from cron.job
     where jobname in ('addonstation-push-dispatch', 'addonstation-push-daily');
  end if;
  return jsonb_build_object('ok', true, 'remaining_jobs', v_off);
end $$;

-- ---------------------------------------------------------------------------
-- 7) الطبيب: سؤال واحد يجيب «لماذا لا تصل إشعاراتي؟»
--    select * from public.push_doctor();
-- ---------------------------------------------------------------------------
create or replace function public.push_doctor()
returns table (check_ar text, ok boolean, detail text)
language plpgsql security definer set search_path = public
as $$
declare
  v_ext_cron boolean := exists (select 1 from pg_extension where extname = 'pg_cron');
  v_ext_net  boolean := exists (select 1 from pg_extension where extname = 'pg_net');
  v_url      text;
  v_secret   text;
  v_enabled  boolean;
  v_devices  int;
  v_pending  int;
  v_stuck    int;
  v_sent24   int;
  v_failed   int;
  v_jobs     int := 0;
  v_last     jsonb;
begin
  select c.function_url, c.cron_secret, c.enabled into v_url, v_secret, v_enabled
    from public.push_config c where c.id = 1;

  select count(*) into v_devices from public.push_devices where enabled;
  select count(*) into v_pending from public.push_queue
   where status = 'pending' and scheduled_at <= now();
  select count(*) into v_stuck  from public.push_queue
   where status = 'pending' and scheduled_at <= now() - interval '15 minutes';
  select count(*) into v_sent24 from public.push_queue
   where status = 'sent' and sent_at > now() - interval '24 hours';
  select count(*) into v_failed from public.push_queue
   where status = 'failed' and created_at > now() - interval '24 hours';

  if v_ext_cron then
    select count(*) into v_jobs from cron.job
     where jobname in ('addonstation-push-dispatch', 'addonstation-push-daily');
  end if;

  return query
  select 'إضافة pg_cron (الجدولة)'::text, v_ext_cron,
         case when v_ext_cron then 'موجودة' else 'فعّلها من Database → Extensions' end;
  return query
  select 'إضافة pg_net (النداء للخارج)'::text, v_ext_net,
         case when v_ext_net then 'موجودة' else 'فعّلها من Database → Extensions' end;
  return query
  select 'رابط دالة الإرسال محفوظ'::text, coalesce(btrim(v_url), '') <> '',
         coalesce(v_url, 'ناقص — نفّذ push_live_enable');
  return query
  select 'سرّ التشغيل محفوظ'::text, coalesce(btrim(v_secret), '') <> '',
         case when coalesce(btrim(v_secret), '') <> '' then 'موجود' else 'ناقص — نفس PUSH_CRON_SECRET' end;
  return query
  select 'التشغيل مفعّل'::text, coalesce(v_enabled, false),
         case when coalesce(v_enabled, false) then 'نعم' else 'موقوف — نفّذ push_live_enable' end;
  return query
  select 'المهام المجدولة (دقيقة + يومية)'::text, v_jobs >= 2,
         case when v_jobs >= 2 then 'مجدولة (2)'
              when v_ext_cron then 'ناقصة — نفّذ push_live_enable مرة أخرى'
              else 'تحتاج pg_cron أولاً' end;
  return query
  select 'أجهزة مسجّلة (توكن FCM)'::text, v_devices > 0,
         case when v_devices > 0 then v_devices::text || ' جهاز'
              else 'لا جهاز — التطبيق المثبّت يحتاج google-services.json وبناءً جديداً' end;
  return query
  select 'إشعارات مُرسلة آخر 24 ساعة'::text, true, v_sent24::text;
  return query
  select 'مستحق ولم يُرسل (عالق)'::text, v_stuck = 0,
         case when v_stuck = 0 then 'لا شيء عالق'
              else v_stuck::text || ' صفاً — انظر آخر ردّ من الدالة' end;
  return query
  select 'إشعارات فشلت آخر 24 ساعة'::text, v_failed = 0,
         case when v_failed = 0 then 'لا فشل' else v_failed::text || ' صفاً — غالباً مفتاح FCM' end;

  -- آخر ردّ من دالة الإرسال (pg_net تحفظه) — هنا يظهر «missing_secrets» أو «401»
  if v_ext_net then
    begin
      execute $q$select jsonb_build_object('code', status_code, 'body', left(coalesce(content, ''), 400),
                                           'at', created)
                  from net._http_response order by created desc limit 1$q$ into v_last;
    exception when others then
      v_last := null;
    end;
  end if;
  return query
  select 'آخر ردّ من دالة الإرسال'::text,
         (v_last is not null and (v_last ->> 'code') = '200'),
         case
           when v_last is null then 'لا ردّ محفوظ بعد (لم تُشغَّل)'
           when (v_last ->> 'code') = '200' then 'ناجح (200) · ' || coalesce(v_last ->> 'at', '')
           when position('unauthorized' in coalesce(v_last ->> 'body', '')) > 0
             then '401 — السرّ في push_live_enable لا يطابق PUSH_CRON_SECRET'
           when position('missing_secrets' in coalesce(v_last ->> 'body', '')) > 0
             then 'الدالة تعمل لكن أسرارها ناقصة (FCM_SERVICE_ACCOUNT)'
           when position('bad_service_account' in coalesce(v_last ->> 'body', '')) > 0
             then 'مفتاح FCM غير سليم — أعد توليده من Firebase'
           else 'الرمز ' || coalesce(v_last ->> 'code', '?') || ' · ' || coalesce(left(v_last ->> 'body', 120), '')
         end;

  return query
  select 'إجمالي المستحق الآن'::text, true, v_pending::text;
end $$;

revoke all on function public.push_live_enable(text, text) from public, anon, authenticated;
revoke all on function public.push_live_disable() from public, anon, authenticated;
revoke all on function public.push_doctor() from public, anon, authenticated;
grant execute on function public.push_live_enable(text, text) to service_role;
grant execute on function public.push_live_disable() to service_role;
grant execute on function public.push_doctor() to service_role;

-- ---------------------------------------------------------------------------
-- 7ب) تحديث فهرس PostgREST فوراً
--
-- بدونه قد يردّ نداء `push_live_enable` عبر REST بـ404 لثوانٍ (الفهرس القديم
-- لم يعرف الدالة الجديدة بعد) — وهو ما حدث فعلاً في أول تشغيل آلي. هذا السطر
-- يجعل الدوال الجديدة مرئية للفور.
-- ---------------------------------------------------------------------------
notify pgrst, 'reload schema';

-- ---------------------------------------------------------------------------
-- 8) تحقق أخير
-- ---------------------------------------------------------------------------
do $$
declare v_cron boolean := exists (select 1 from pg_extension where extname = 'pg_cron');
         v_net  boolean := exists (select 1 from pg_extension where extname = 'pg_net');
         v_fn   int;
begin
  select count(*) into v_fn from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in ('push_dispatch_tick', 'push_daily_tick', 'push_live_enable',
                       'push_live_disable', 'push_doctor', 'push_on_queue_urgent');
  raise notice '✓ دالة الإرسال بالطابور: %/6 · pg_cron: % · pg_net: %', v_fn, v_cron, v_net;
  if v_cron and v_net then
    raise notice 'الخطوة الأخيرة: select public.push_live_enable(''رابط-الدالة'', ''السرّ'') ثم select * from public.push_doctor();';
  else
    raise notice 'فعّل pg_cron و pg_net من لوحة سوبابيز ثم أعد تنفيذ هذا الملف.';
  end if;
end $$;
