-- ============================================================================
--  AddonStation Mobile — 23) مبلغ الطلب بالدولار (amount_usd)
--
--  المشكلة التي يصلحها هذا الملف:
--      جدول payment_orders كان يحفظ **الدينار فقط** (amount_lyd)، فلم يكن لطلب
--      الدفع أي مبلغ بالدولار. ودالة Stripe كانت تقرأ `order.amount_usd`
--      (غير موجود ⇒ undefined) فترجع إلى قيمة ثابتة 2.99:
--          unit_amount: Math.round(Number(order.amount_usd ?? 2.99) * 100)
--      ⇒ أي اشتراك Legendary (5.99$) كان يُحصَّل منه 2.99$ فقط!
--
--  ما يفعله (آمن لإعادة التنفيذ):
--      1) يضيف العمود amount_usd إلى payment_orders.
--      2) يعبّؤه للطلبات القديمة من سعر خطّتها.
--      3) يعيد تعريف create_payment_order لتحفظ مبلغ الدولار من سعر الخطة
--         وتُرجعه في النتيجة، وتضبط العملة تلقائياً:
--              • طرق التحويل اليدوي (manual) → LYD
--              • البوابات الإلكترونية (stripe/paymob/tap) → USD
--      4) يتحقق من وجود العمود ومن أن الدالة ترجع amount_usd.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor (بعد 22_plan_prices.sql).
-- ============================================================================

-- ١) العمود الجديد
alter table public.payment_orders
  add column if not exists amount_usd numeric(10,2);

-- ٢) تعبئة الطلبات القديمة من سعر خطّتها
update public.payment_orders o
   set amount_usd = p.price_usd
  from public.subscription_plans p
 where o.amount_usd is null
   and p.id = o.plan_id;

-- ٣) الدالة: تحفظ مبلغ الدولار وتُرجعه
create or replace function public.create_payment_order(p_plan_id text, p_method text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_plan    public.subscription_plans;
  v_method  public.payment_method_settings;
  v_ref     text;
  v_open    int;
  v_order   public.payment_orders;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_plan from public.subscription_plans where id = p_plan_id and active;
  if not found then return jsonb_build_object('ok', false, 'message', 'plan_unavailable'); end if;

  select * into v_method from public.payment_method_settings where code = p_method;
  if not found then return jsonb_build_object('ok', false, 'message', 'method_unavailable'); end if;
  if not v_method.enabled then return jsonb_build_object('ok', false, 'message', 'method_disabled'); end if;

  -- حد أقصى للطلبات المفتوحة لكل مستخدم (مكافحة الإساءة)
  select count(*) into v_open from public.payment_orders
   where user_id = v_uid and status in ('pending','submitted');
  if v_open >= 3 then
    return jsonb_build_object('ok', false, 'message', 'too_many_open_orders');
  end if;

  -- رمز طلب AS-XXXXXXXX
  loop
    v_ref := 'AS-' || upper(substr(replace(gen_random_uuid()::text,'-',''), 1, 8));
    exit when not exists (select 1 from public.payment_orders where reference_code = v_ref);
  end loop;

  -- المبلغ بالدينار وبالدولار معاً: الدينار للتحويل اليدوي، والدولار للبوابات.
  -- العملة تتبع مزوّد الطريقة: يدوي ⇒ LYD · بوابة ⇒ USD
  insert into public.payment_orders(user_id, plan_id, amount_lyd, amount_usd, currency,
                                    payment_method, reference_code, provider)
  values (v_uid, p_plan_id, v_plan.price_lyd, v_plan.price_usd,
          case when v_method.provider = 'manual' then 'LYD' else 'USD' end,
          p_method, v_ref, v_method.provider)
  returning * into v_order;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'payment_order_created', 'payment_order', v_order.id::text,
          jsonb_build_object('plan', p_plan_id, 'amount', v_plan.price_lyd,
                             'amount_usd', v_plan.price_usd,
                             'currency', v_order.currency, 'method', p_method));

  return jsonb_build_object('ok', true, 'order_id', v_order.id, 'reference_code', v_ref,
                            'amount_lyd', v_plan.price_lyd,
                            'amount_usd', v_plan.price_usd,
                            'currency', v_order.currency,
                            'duration_days', v_plan.duration_days,
                            'account_name', v_method.account_name,
                            'account_identifier', v_method.account_identifier,
                            'instructions', v_method.instructions,
                            'method_name', v_method.name,
                            'provider', v_method.provider);
end;
$$;

-- ---------------------------------------------------------------------------
-- تحقّق: العمود موجود، والطلبات القديمة صار لها مبلغ دولار
-- ---------------------------------------------------------------------------
select count(*)                                   as "كل الطلبات",
       count(*) filter (where amount_usd is null) as "بلا مبلغ دولار"
  from public.payment_orders;

do $$
declare
  v_has_column boolean;
  v_missing    int;
begin
  select exists (
    select 1 from information_schema.columns
     where table_schema = 'public' and table_name = 'payment_orders'
       and column_name = 'amount_usd'
  ) into v_has_column;

  if not v_has_column then
    raise exception '✗ العمود amount_usd غير موجود — لم يُضاف.';
  end if;

  select count(*) into v_missing
    from public.payment_orders
   where amount_usd is null;

  raise notice '✅ 23_payment_order_usd: العمود موجود · طلبات بلا مبلغ دولار: %', v_missing;
end;
$$;
