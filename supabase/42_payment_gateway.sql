-- 42_payment_gateway.sql — live payment gateway credentials (API key + merchant id) + hosted-checkout flow
-- User request 2026-10-28: "add a payment gateway (key and id)".
--
-- Security model:
--   * The secret api_key and merchant id live ONLY in payment_gateway_credentials
--     (RLS: admin-only, no client select ever). Users never see them.
--   * start_gateway_payment() is SECURITY DEFINER: it creates the order, fills the
--     checkout URL template server-side and returns only the final payment URL.
--   * The gateway confirms payments through gateway_confirm_order() (service_role),
--     which is already revoked from anon/authenticated (11_grants.sql).
--
-- The checkout_url template supports: {amount} {amount_usd} {currency} {reference}
--                                      {plan} {merchant_id}
-- Checked by 99_VERIFY_ALL.sql (quoted names for its literal scan):
--   table 'payment_gateway_credentials' · policy 'payment_gateway_credentials_admin'

create table if not exists public.payment_gateway_credentials (
  method_code   text primary key references public.payment_method_settings(code) on delete cascade,
  api_key       text,                       -- gateway secret key (admin eyes only)
  merchant_id   text,                       -- gateway merchant / client / integration id (admin eyes only)
  checkout_url  text,                       -- hosted-checkout URL template
  updated_at    timestamptz not null default now()
);

alter table public.payment_gateway_credentials enable row level security;

drop policy if exists payment_gateway_credentials_admin on public.payment_gateway_credentials;
create policy payment_gateway_credentials_admin on public.payment_gateway_credentials
  for all using (public.is_admin()) with check (public.is_admin());

revoke all on public.payment_gateway_credentials from anon, authenticated;

-- ---------------------------------------------------------------------------
-- admin: save / update the gateway key + id for one payment method.
-- NULL parameter = keep the stored value; empty string = clear it.
-- ---------------------------------------------------------------------------
create or replace function public.admin_set_gateway_credentials(
  p_code text,
  p_api_key text default null,
  p_merchant_id text default null,
  p_checkout_url text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if not exists (select 1 from public.payment_method_settings where code = p_code) then
    return jsonb_build_object('ok', false, 'message', 'method_unavailable');
  end if;
  insert into public.payment_gateway_credentials(method_code, api_key, merchant_id, checkout_url, updated_at)
  values (p_code, nullif(p_api_key, ''), nullif(p_merchant_id, ''), nullif(p_checkout_url, ''), now())
  on conflict (method_code) do update set
    api_key      = case when p_api_key      is null then payment_gateway_credentials.api_key      else nullif(p_api_key, '')      end,
    merchant_id  = case when p_merchant_id  is null then payment_gateway_credentials.merchant_id  else nullif(p_merchant_id, '')  end,
    checkout_url = case when p_checkout_url is null then payment_gateway_credentials.checkout_url else nullif(p_checkout_url, '') end,
    updated_at   = now();
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- admin: read the stored key + id back into the admin editor (never leaves admin).
-- ---------------------------------------------------------------------------
create or replace function public.admin_get_gateway_credentials(p_code text)
returns jsonb
language plpgsql stable security definer set search_path = public
as $$
declare
  v public.payment_gateway_credentials;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  select * into v from public.payment_gateway_credentials where method_code = p_code;
  return jsonb_build_object(
    'ok', true,
    'api_key',      coalesce(v.api_key, ''),
    'merchant_id',  coalesce(v.merchant_id, ''),
    'checkout_url', coalesce(v.checkout_url, ''));
end;
$$;

-- ---------------------------------------------------------------------------
-- user: start an online payment. Creates the order via create_payment_order()
-- (server-side amount, AS-XXXXXXXX reference, provider = method provider), then
-- fills the checkout URL template. Returns ONLY the final pay_url — the raw
-- key / id stay inside the database.
-- ---------------------------------------------------------------------------
create or replace function public.start_gateway_payment(p_plan_id text, p_method text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_res    jsonb;
  v_cred   public.payment_gateway_credentials;
  v_ref    text;
  v_url    text;
  v_amount numeric;
  v_usd    numeric;
begin
  select * into v_cred from public.payment_gateway_credentials where method_code = p_method;
  if not found or coalesce(v_cred.checkout_url, '') = '' then
    return jsonb_build_object('ok', false, 'message', 'gateway_not_configured');
  end if;

  v_res := public.create_payment_order(p_plan_id, p_method);
  if coalesce((v_res->>'ok')::boolean, false) = false then
    return v_res;
  end if;

  v_ref    := v_res->>'reference_code';
  v_amount := coalesce((v_res->>'amount_lyd')::numeric, 0);
  select coalesce(price_usd, 0) into v_usd from public.subscription_plans where id = p_plan_id;

  v_url := replace(replace(replace(replace(replace(replace(
             v_cred.checkout_url,
             '{reference}',   v_ref),
             '{amount}',      v_amount::text),
             '{amount_usd}',  v_usd::text),
             '{currency}',    coalesce(v_res->>'currency', 'LYD')),
             '{plan}',        p_plan_id),
             '{merchant_id}', coalesce(v_cred.merchant_id, ''));

  return jsonb_build_object('ok', true,
    'order_id',      v_res->>'order_id',
    'reference_code', v_ref,
    'amount_lyd',    v_amount,
    'pay_url',       v_url);
end;
$$;
