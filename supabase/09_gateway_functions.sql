-- ============================================================================
--  AddonStation Mobile — 09) دوال بوابات الدفع الإلكتروني (اختياري)
--  تُستدعى من Supabase Edge Functions بـ service_role فقط،
--  ولا يمكن لأي مستخدم عادي تنفيذها.
-- ============================================================================

-- تأكيد طلب دفع قادم من بوابة إلكترونية (Stripe / Paymob / Tap ...)
create or replace function public.gateway_confirm_order(
  p_reference text,
  p_provider_payment_id text default null,
  p_provider text default 'stripe'
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_order public.payment_orders;
  v_plan  public.subscription_plans;
  v_base  timestamptz;
  v_new_expiry timestamptz;
begin
  select * into v_order from public.payment_orders where reference_code = p_reference for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'order_not_found');
  end if;

  if v_order.status = 'approved' then
    return jsonb_build_object('ok', true, 'message', 'already_approved');
  end if;

  select * into v_plan from public.subscription_plans where id = v_order.plan_id;

  select greatest(now(), coalesce(plan_expires_at, now())) into v_base
    from public.profiles where id = v_order.user_id;

  v_new_expiry := coalesce(v_base, now()) + make_interval(days => coalesce(v_plan.duration_days, 30));

  update public.payment_orders
     set status = 'approved',
         provider = coalesce(p_provider, 'stripe'),
         provider_payment_id = p_provider_payment_id,
         reviewed_at = now(),
         updated_at = now()
   where id = v_order.id;

  update public.profiles
     set plan = v_order.plan_id,
         plan_expires_at = v_new_expiry,
         updated_at = now()
   where id = v_order.user_id;

  insert into public.chat_notifications(recipient_id, type, body, meta)
  values (v_order.user_id, 'plan',
          'Your ' || v_order.plan_id || ' plan is active until ' || to_char(v_new_expiry, 'YYYY-MM-DD'),
          jsonb_build_object('plan', v_order.plan_id, 'expires_at', v_new_expiry,
                             'gateway', p_provider, 'payment_id', p_provider_payment_id));

  insert into public.admin_audit_log(action, target_type, target_id, details)
  values ('gateway_payment_approved', 'payment_order', v_order.id::text,
          jsonb_build_object('provider', p_provider, 'payment_id', p_provider_payment_id,
                             'user', v_order.user_id, 'plan', v_order.plan_id));

  return jsonb_build_object('ok', true, 'plan', v_order.plan_id, 'expires_at', v_new_expiry);
end;
$$;

-- تسجيل طلب دفع إلكتروني وربطه بجلسة الدفع في البوابة
create or replace function public.gateway_attach_session(
  p_order_id uuid,
  p_provider text,
  p_session_id text
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  update public.payment_orders
     set provider = coalesce(p_provider,'stripe'),
         provider_payment_id = p_session_id,
         updated_at = now()
   where id = p_order_id;

  return jsonb_build_object('ok', true);
end;
$$;

-- لا يُمنح أي مستخدم تنفيذ هذه الدوال: فقط service_role (يعبرها تلقائياً)
revoke all on function public.gateway_confirm_order(text, text, text) from public, anon, authenticated;
revoke all on function public.gateway_attach_session(uuid, text, text) from public, anon, authenticated;
grant execute on function public.gateway_confirm_order(text, text, text) to service_role;
grant execute on function public.gateway_attach_session(uuid, text, text) to service_role;
