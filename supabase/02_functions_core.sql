-- ============================================================================
--  AddonStation Mobile — 02) دوال الخادم الأساسية
--  المحتوى · الخطط · المدفوعات · AC · الملف الشخصي
--  كل عملية حساسة تتم هنا (SECURITY DEFINER) — التطبيق لا يعدّل الجداول مباشرة
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) مساعدات الصلاحيات
-- ---------------------------------------------------------------------------
create or replace function public.current_role_of(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select coalesce((select role from public.profiles where id = p_user), 'user');
$$;

create or replace function public.is_admin(p_user uuid default auth.uid())
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_role_of(p_user) = 'admin';
$$;

create or replace function public.is_staff(p_user uuid default auth.uid())
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_role_of(p_user) in ('admin','moderator');
$$;

-- الخطة الفعلية مع احتساب انتهاء الصلاحية (تُحسب في كل طلب)
create or replace function public.effective_plan(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select case
    when p_user is null then 'free'
    when (select plan_expires_at from public.profiles where id = p_user) is null
         then coalesce((select plan from public.profiles where id = p_user), 'free')
    when (select plan_expires_at from public.profiles where id = p_user) > now()
         then coalesce((select plan from public.profiles where id = p_user), 'free')
    else 'free'
  end;
$$;

create or replace function public.effective_plan_label(p_user uuid default auth.uid())
returns text
language sql stable security definer set search_path = public
as $$
  select public.effective_plan(p_user);
$$;

-- هل يملك المستخدم صلاحية الوصول لهذا المحتوى؟
create or replace function public.can_access_item(p_item_id uuid, p_user uuid default auth.uid())
returns boolean
language plpgsql stable security definer set search_path = public
as $$
declare
  v_item public.content_items;
  v_plan text;
  v_unlocked boolean;
begin
  select * into v_item from public.content_items where id = p_item_id;
  if not found then return false; end if;

  -- المالك والمدير يمكنهما دائماً
  if p_user is not null then
    if v_item.created_by = p_user then return true; end if;
    if public.is_admin(p_user) then return true; end if;
  end if;

  if v_item.plan = 'free' then
    return p_user is not null;
  end if;

  v_plan := public.effective_plan(p_user);
  if public.plan_rank(v_plan) >= public.plan_rank(v_item.plan) then
    return true;
  end if;

  -- فتح مدفوع بالـ AC (مرة واحدة لكل مستخدم/عنصر)
  if v_item.ac_price > 0 and p_user is not null then
    select exists(
      select 1 from public.ac_transactions t
      where t.user_id = p_user and t.item_id = p_item_id and t.kind = 'unlock'
    ) into v_unlocked;
    if v_unlocked then return true; end if;
  end if;

  return false;
end;
$$;

-- هل وصل المستخدم إلى حد التحميلات في خطته؟
create or replace function public.download_limit_reached(p_user uuid default auth.uid())
returns boolean
language plpgsql stable security definer set search_path = public
as $$
declare
  v_plan        text;
  v_limit       int;
  v_period      text;
  v_since       timestamptz;
  v_count       int;
begin
  if p_user is null then return true; end if;
  if public.is_admin(p_user) then return false; end if;

  v_plan := public.effective_plan(p_user);
  select download_limit, limit_period into v_limit, v_period
  from public.subscription_plans where id = v_plan;

  if v_limit is null or coalesce(v_period,'none') = 'none' then
    return false;
  end if;

  v_since := case when v_period = 'month' then date_trunc('month', now()) else date_trunc('day', now()) end;

  select count(*) into v_count
  from public.content_downloads
  where user_id = p_user and created_at >= v_since;

  return v_count >= v_limit;
end;
$$;

-- ---------------------------------------------------------------------------
-- 1) تسجيل مشاهدة المحتوى
-- ---------------------------------------------------------------------------
create or replace function public.record_content_view(p_item_id uuid)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.content_items
     set views_count = views_count + 1
   where id = p_item_id and is_published;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2) تسجيل التحميل (يفحص تسجيل الدخول + الخطة + حد التحميلات)
--    يعيد: { ok, bucket, path, message }
-- ---------------------------------------------------------------------------
create or replace function public.record_content_download(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid         uuid := auth.uid();
  v_item        public.content_items;
  v_object_size bigint;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if coalesce(v_item.file_path, '') = '' then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  select coalesce((o.metadata ->> 'size')::bigint, v_item.file_size)
    into v_object_size
    from storage.objects o
   where o.bucket_id = 'content-files'
     and o.name = v_item.file_path;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  if not public.can_access_item(p_item_id, v_uid) then
    return jsonb_build_object('ok', false, 'message', 'no_permission');
  end if;

  if public.download_limit_reached(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'download_limit');
  end if;

  insert into public.content_downloads (user_id, item_id) values (v_uid, p_item_id);
  update public.content_items set downloads_count = downloads_count + 1 where id = p_item_id;

  return jsonb_build_object('ok', true, 'bucket', 'content-files', 'path', v_item.file_path,
                            'size', v_object_size, 'title', v_item.title);
end;
$$;

-- Never publish metadata that points to a missing protected Storage object.
-- This guard runs only for inserts and publication/path changes, so ordinary
-- view/download counter updates do not pay for the Storage lookup.
create or replace function public.ensure_published_content_file()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if new.is_published and (
    coalesce(new.file_path, '') = ''
    or not exists (
      select 1
        from storage.objects o
       where o.bucket_id = 'content-files'
         and o.name = new.file_path
    )
  ) then
    raise exception using errcode = '23514', message = 'file_not_uploaded';
  end if;
  return new;
end;
$$;

revoke all on function public.ensure_published_content_file() from public;

drop trigger if exists content_items_require_uploaded_file on public.content_items;
create trigger content_items_require_uploaded_file
  before insert or update of is_published, file_path on public.content_items
  for each row execute function public.ensure_published_content_file();

-- ---------------------------------------------------------------------------
-- 3) المفضلة
-- ---------------------------------------------------------------------------
create or replace function public.toggle_favorite(p_item_id uuid)
returns boolean
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_exists boolean;
begin
  if v_uid is null then raise exception 'sign_in_required'; end if;

  select exists(select 1 from public.content_favorites where user_id = v_uid and item_id = p_item_id)
    into v_exists;

  if v_exists then
    delete from public.content_favorites where user_id = v_uid and item_id = p_item_id;
    return false;
  else
    insert into public.content_favorites(user_id, item_id) values (v_uid, p_item_id)
    on conflict do nothing;
    return true;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4) الاقتصاد: فتح محتوى بالـ AC / المطالبة بالمهام / تعديل إداري
-- ---------------------------------------------------------------------------
create or replace function public.ac_unlock_content(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_item    public.content_items;
  v_price   int;
  v_balance int;
  v_already boolean;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  v_price := coalesce(v_item.ac_price, 0);
  if v_price <= 0 then
    return jsonb_build_object('ok', false, 'message', 'not_priced');
  end if;

  -- هل فُتح سابقاً؟ (لا خصم مزدوج)
  select exists(select 1 from public.ac_transactions
                where user_id = v_uid and item_id = p_item_id and kind = 'unlock') into v_already;
  if v_already then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_unlocked');
  end if;

  select balance into v_balance from public.ac_wallets where user_id = v_uid for update;
  if coalesce(v_balance,0) < v_price then
    return jsonb_build_object('ok', false, 'message', 'not_enough_ac');
  end if;

  update public.ac_wallets
     set balance = balance - v_price,
         total_spent = total_spent + v_price,
         updated_at = now()
   where user_id = v_uid;

  insert into public.ac_transactions(user_id, amount, kind, reason, item_id, request_key, balance_after)
  values (v_uid, -v_price, 'unlock', coalesce(v_item.title,'Content unlock'), p_item_id,
          'unlock:' || v_uid::text || ':' || p_item_id::text, v_balance - v_price)
  on conflict (request_key) do nothing;

  return jsonb_build_object('ok', true, 'charged', v_price, 'balance', v_balance - v_price);
end;
$$;

create or replace function public.ac_claim_task(p_task_id text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid      uuid := auth.uid();
  v_task     public.ac_tasks;
  v_count    int;
  v_reward   int;
  v_balance  int;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_task from public.ac_tasks where id = p_task_id and active;
  if not found then return jsonb_build_object('ok', false, 'message', 'task_not_found'); end if;

  v_reward := v_task.reward;

  -- تحقق الشروط المبدئية لكل مهمة
  if v_task.frequency = 'manual' then
    return jsonb_build_object('ok', false, 'message', 'manual_only');
  end if;

  select count(*) into v_count from public.ac_task_claims where user_id = v_uid and task_id = p_task_id;

  if v_task.frequency = 'once' and v_count > 0 then
    return jsonb_build_object('ok', false, 'message', 'already_claimed');
  end if;

  if v_task.frequency = 'daily' then
    if exists(select 1 from public.ac_task_claims
              where user_id = v_uid and task_id = p_task_id
                and day = (now() at time zone 'utc')::date) then
      return jsonb_build_object('ok', false, 'message', 'already_claimed');
    end if;
  end if;

  if v_task.frequency = 'streak' then
    if exists(select 1 from public.ac_task_claims
              where user_id = v_uid and task_id = p_task_id
                and day = (now() at time zone 'utc')::date) then
      return jsonb_build_object('ok', false, 'message', 'already_claimed');
    end if;
  end if;

  if v_task.max_claims is not null and v_count >= v_task.max_claims then
    return jsonb_build_object('ok', false, 'message', 'max_claims');
  end if;

  insert into public.ac_task_claims(user_id, task_id, amount) values (v_uid, p_task_id, v_reward);

  insert into public.ac_transactions(user_id, amount, kind, reason, task_id, request_key, balance_after)
  select v_uid, v_reward,
         case when v_task.frequency = 'daily' then 'daily' else 'task' end,
         coalesce(v_task.title_en, v_task.title_ar, v_task.id),
         p_task_id,
         'task:' || v_uid::text || ':' || p_task_id || ':' || (now() at time zone 'utc')::date::text,
         0
  on conflict (request_key) do nothing;

  update public.ac_wallets
     set balance = balance + v_reward,
         total_earned = total_earned + v_reward,
         updated_at = now()
   where user_id = v_uid;

  select balance into v_balance from public.ac_wallets where user_id = v_uid;

  update public.ac_transactions
     set balance_after = v_balance
   where user_id = v_uid
     and request_key = 'task:' || v_uid::text || ':' || p_task_id || ':' || (now() at time zone 'utc')::date::text;

  return jsonb_build_object('ok', true, 'reward', v_reward, 'balance', v_balance);
end;
$$;

create or replace function public.ac_admin_adjust(
  p_user_id uuid,
  p_amount  int,
  p_reason  text default 'Admin Adjustment',
  p_request_key text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_balance int;
  v_key text;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;

  v_key := coalesce(p_request_key, 'admin:' || auth.uid()::text || ':' || p_user_id::text || ':' || gen_random_uuid()::text);

  insert into public.ac_wallets(user_id) values (p_user_id) on conflict (user_id) do nothing;

  update public.ac_wallets
     set balance = greatest(balance + p_amount, 0),
         total_earned = total_earned + greatest(p_amount, 0),
         total_spent  = total_spent  + greatest(-p_amount, 0),
         updated_at = now()
   where user_id = p_user_id
   returning balance into v_balance;

  insert into public.ac_transactions(user_id, amount, kind, reason, request_key, balance_after, created_by)
  values (p_user_id, p_amount,
          case when p_amount >= 0 then 'admin_reward' else 'admin_adjustment' end,
          p_reason, v_key, v_balance, auth.uid())
  on conflict (request_key) do nothing;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'ac_adjust', 'profile', p_user_id::text,
          jsonb_build_object('amount', p_amount, 'reason', p_reason));

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (p_user_id, auth.uid(), 'system',
          case when p_amount >= 0
               then 'You received ' || p_amount || ' AC'
               else 'A deduction of ' || abs(p_amount) || ' AC was applied' end,
          jsonb_build_object('amount', p_amount));

  return jsonb_build_object('ok', true, 'balance', v_balance);
end;
$$;

-- منح مكافأة جماعية لكل المستخدمين
create or replace function public.ac_admin_grant_all(p_amount int, p_reason text default 'Admin Reward')
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_count int := 0;
  r record;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if coalesce(p_amount,0) <= 0 then return jsonb_build_object('ok', false, 'message', 'invalid_amount'); end if;

  for r in select id from public.profiles loop
    perform public.ac_admin_adjust(r.id, p_amount, p_reason,
      'grantall:' || auth.uid()::text || ':' || r.id::text || ':' || now()::date::text);
    v_count := v_count + 1;
  end loop;

  insert into public.admin_audit_log(actor_id, action, target_type, details)
  values (auth.uid(), 'ac_grant_all', 'profiles', jsonb_build_object('amount', p_amount, 'users', v_count));

  return jsonb_build_object('ok', true, 'users', v_count);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5) المدفوعات: إنشاء طلب / إرسال إيصال / موافقة / رفض
-- ---------------------------------------------------------------------------
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

  insert into public.payment_orders(user_id, plan_id, amount_lyd, payment_method, reference_code, provider)
  values (v_uid, p_plan_id, v_plan.price_lyd, p_method, v_ref, v_method.provider)
  returning * into v_order;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'payment_order_created', 'payment_order', v_order.id::text,
          jsonb_build_object('plan', p_plan_id, 'amount', v_plan.price_lyd, 'method', p_method));

  return jsonb_build_object('ok', true, 'order_id', v_order.id, 'reference_code', v_ref,
                            'amount_lyd', v_plan.price_lyd, 'currency', v_order.currency,
                            'duration_days', v_plan.duration_days,
                            'account_name', v_method.account_name,
                            'account_identifier', v_method.account_identifier,
                            'instructions', v_method.instructions,
                            'method_name', v_method.name,
                            'provider', v_method.provider);
end;
$$;

create or replace function public.submit_payment_receipt(
  p_order_id uuid,
  p_receipt_path text,
  p_transaction_reference text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_order public.payment_orders;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  select * into v_order from public.payment_orders where id = p_order_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  if v_order.user_id <> v_uid then return jsonb_build_object('ok', false, 'message', 'forbidden'); end if;
  if v_order.status <> 'pending' then
    return jsonb_build_object('ok', false, 'message', 'invalid_status');
  end if;

  -- يجب أن يكون الملف داخل مجلد المستخدم نفسه
  if p_receipt_path is null or position(v_uid::text in p_receipt_path) = 0 then
    return jsonb_build_object('ok', false, 'message', 'invalid_path');
  end if;

  update public.payment_orders
     set receipt_url = p_receipt_path,
         transaction_reference = coalesce(p_transaction_reference, transaction_reference),
         status = 'submitted',
         updated_at = now()
   where id = p_order_id;

  return jsonb_build_object('ok', true, 'status', 'submitted');
end;
$$;

-- تفعيل الاشتراك بعد تأكيد الإيصال (Admin فقط)
create or replace function public.approve_payment_order(p_order_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_order public.payment_orders;
  v_plan  public.subscription_plans;
  v_base  timestamptz;
  v_new_expiry timestamptz;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_order from public.payment_orders where id = p_order_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  if v_order.status = 'approved' then return jsonb_build_object('ok', false, 'message', 'already_approved'); end if;
  if v_order.status not in ('submitted','pending') then
    return jsonb_build_object('ok', false, 'message', 'invalid_status');
  end if;

  select * into v_plan from public.subscription_plans where id = v_order.plan_id;

  -- تمديد من تاريخ الانتهاء الحالي إن كان في المستقبل (تجديد مبكر)
  select greatest(now(), coalesce(plan_expires_at, now())) into v_base
  from public.profiles where id = v_order.user_id;

  v_new_expiry := coalesce(v_base, now()) + make_interval(days => coalesce(v_plan.duration_days, 30));

  update public.payment_orders
     set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
   where id = p_order_id;

  update public.profiles
     set plan = v_order.plan_id,
         plan_expires_at = v_new_expiry,
         updated_at = now()
   where id = v_order.user_id;

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (v_order.user_id, auth.uid(), 'plan',
          'Your ' || v_order.plan_id || ' plan is active until ' || to_char(v_new_expiry, 'YYYY-MM-DD'),
          jsonb_build_object('plan', v_order.plan_id, 'expires_at', v_new_expiry));

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_approved', 'payment_order', p_order_id::text,
          jsonb_build_object('user', v_order.user_id, 'plan', v_order.plan_id,
                             'amount', v_order.amount_lyd, 'expires_at', v_new_expiry));

  return jsonb_build_object('ok', true, 'plan', v_order.plan_id, 'expires_at', v_new_expiry);
end;
$$;

create or replace function public.reject_payment_order(p_order_id uuid, p_reason text default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_order public.payment_orders;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  update public.payment_orders
     set status = 'rejected', reject_reason = p_reason,
         reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
   where id = p_order_id
   returning * into v_order;

  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  insert into public.chat_notifications(recipient_id, actor_id, type, body, meta)
  values (v_order.user_id, auth.uid(), 'payment',
          coalesce('Your payment order was rejected: ' || p_reason, 'Your payment order was rejected'),
          jsonb_build_object('order_id', p_order_id));

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_rejected', 'payment_order', p_order_id::text,
          jsonb_build_object('reason', p_reason, 'user', v_order.user_id));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6) تعديل ملف مستخدم بواسطة الإدارة (خطة / رتبة / تاريخ انتهاء)
-- ---------------------------------------------------------------------------
create or replace function public.admin_update_profile(
  p_user_id uuid,
  p_plan text default null,
  p_role text default null,
  p_expires timestamptz default null,
  p_grant_days int default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_target public.profiles;
  v_admin_count int;
  v_new_expiry timestamptz := p_expires;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select * into v_target from public.profiles where id = p_user_id for update;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  -- حماية: لا يمكن إزالة آخر Admin في النظام
  if p_role is not null and v_target.role = 'admin' and p_role <> 'admin' then
    select count(*) into v_admin_count from public.profiles where role = 'admin';
    if v_admin_count <= 1 then
      return jsonb_build_object('ok', false, 'message', 'last_admin');
    end if;
  end if;

  if p_grant_days is not null then
    v_new_expiry := greatest(now(), coalesce(v_target.plan_expires_at, now()))
                    + make_interval(days => p_grant_days);
  end if;

  update public.profiles
     set plan = coalesce(p_plan, plan),
         role = coalesce(p_role, role),
         plan_expires_at = case
            when p_grant_days is not null then v_new_expiry
            when p_expires is not null then p_expires
            else plan_expires_at
         end,
         updated_at = now()
   where id = p_user_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'profile_updated', 'profile', p_user_id::text,
          jsonb_build_object('plan', p_plan, 'role', p_role, 'expires', v_new_expiry));

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) تعيين أفاتار/غلاف/أداة مع التحقق من الخطة على الخادم (إصلاح ثغرة الوثيقة §3.3)
-- ---------------------------------------------------------------------------
create or replace function public.set_profile_asset(p_kind text, p_value text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid       uuid := auth.uid();
  v_asset     public.site_assets;
  v_plan      text;
  v_is_admin  boolean;
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if p_kind not in ('avatar','banner','item') then
    return jsonb_build_object('ok', false, 'message', 'invalid_kind');
  end if;

  -- السماح بإعادة التعيين للقيمة الافتراضية
  if p_value is null or p_value = '' or p_value = 'none' then
    if p_kind = 'avatar' then
      -- المسار من صف الأفاتار المجاني المفعّل نفسه (لا مسار مكتوب يدوياً):
      -- فلا يتعطّل شيء إن تغيّر مسار الصورة في المخزن (راجع 32).
      update public.profiles
         set avatar_url = coalesce(
               (select a.file_url from public.site_assets a
                 where a.asset_type = 'avatar' and a.required_plan = 'free' and a.enabled
                 order by a.sort_order, a.id limit 1),
               'profiles/profiles/onefree.png'),
             updated_at = now()
       where id = v_uid;
    elsif p_kind = 'banner' then
      update public.profiles set banner_url = null where id = v_uid;
    else
      update public.profiles set equipped_item_id = 'none' where id = v_uid;
    end if;
    return jsonb_build_object('ok', true, 'value', 'none');
  end if;

  select * into v_asset from public.site_assets
   where enabled and asset_type = (case when p_kind = 'item' then 'item' else p_kind end)
     and (file_url = p_value or name = p_value)
   limit 1;

  if not found then return jsonb_build_object('ok', false, 'message', 'asset_not_found'); end if;

  v_plan     := public.effective_plan(v_uid);
  v_is_admin := public.is_admin(v_uid);

  if v_asset.required_plan = 'admin' and not v_is_admin then
    return jsonb_build_object('ok', false, 'message', 'admin_only_asset');
  end if;

  if v_asset.required_plan <> 'admin'
     and not v_is_admin
     and public.plan_rank(v_plan) < public.plan_rank(v_asset.required_plan) then
    return jsonb_build_object('ok', false, 'message', 'plan_too_low',
                              'required_plan', v_asset.required_plan);
  end if;

  if p_kind = 'avatar' then
    update public.profiles set avatar_url = v_asset.file_url, updated_at = now() where id = v_uid;
  elsif p_kind = 'banner' then
    update public.profiles set banner_url = v_asset.file_url, updated_at = now() where id = v_uid;
  else
    update public.profiles set equipped_item_id = coalesce(v_asset.name, v_asset.file_url), updated_at = now() where id = v_uid;
  end if;

  return jsonb_build_object('ok', true, 'value', v_asset.file_url, 'required_plan', v_asset.required_plan);
end;
$$;

-- تحديث الملف الشخصي (الحقول النصية فقط — لا خطة/رتبة)
create or replace function public.update_my_profile(
  p_username text default null,
  p_bio text default null,
  p_custom_status text default null,
  p_show_online boolean default null,
  p_country text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then return jsonb_build_object('ok', false, 'message', 'sign_in_required'); end if;

  if p_username is not null then
    if char_length(p_username) < 3 then
      return jsonb_build_object('ok', false, 'message', 'username_too_short');
    end if;
    if exists(select 1 from public.profiles where username = p_username and id <> v_uid) then
      return jsonb_build_object('ok', false, 'message', 'username_taken');
    end if;
  end if;

  update public.profiles
     set username = coalesce(p_username, username),
         bio = coalesce(p_bio, bio),
         custom_status = coalesce(p_custom_status, custom_status),
         show_online_status = coalesce(p_show_online, show_online_status),
         country = coalesce(p_country, country),
         last_seen_at = now(),
         updated_at = now()
   where id = v_uid;

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8) إدارة المحتوى (Admin) — إضافة/تعديل/حذف ونشر
-- ---------------------------------------------------------------------------
create or replace function public.admin_upsert_content(
  p_id uuid default null,
  p_title text default null,
  p_description text default null,
  p_content_type text default 'addon',
  p_category text default 'other',
  p_plan text default 'free',
  p_version text default null,
  p_cover_url text default null,
  p_file_path text default null,
  p_file_size bigint default null,
  p_ac_price int default 0,
  p_is_published boolean default false,
  p_is_featured boolean default false,
  p_is_exclusive boolean default false
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_id uuid;
  v_slug text;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  if coalesce(p_title,'') = '' then return jsonb_build_object('ok', false, 'message', 'title_required'); end if;

  v_slug := lower(regexp_replace(coalesce(p_title,''), '[^a-zA-Z0-9\u0600-\u06FF]+', '-', 'g'));

  if p_id is null then
    insert into public.content_items(title, slug, description, content_type, category, plan, version,
                                    cover_url, file_path, file_size, ac_price,
                                    is_published, is_featured, is_exclusive, created_by)
    values (p_title, v_slug, p_description, p_content_type, p_category, p_plan, p_version,
            p_cover_url, p_file_path, p_file_size, coalesce(p_ac_price,0),
            coalesce(p_is_published,false), coalesce(p_is_featured,false), coalesce(p_is_exclusive,false), auth.uid())
    returning id into v_id;
  else
    update public.content_items
       set title = coalesce(p_title, title),
           description = coalesce(p_description, description),
           content_type = coalesce(p_content_type, content_type),
           category = coalesce(p_category, category),
           plan = coalesce(p_plan, plan),
           version = coalesce(p_version, version),
           cover_url = coalesce(p_cover_url, cover_url),
           file_path = coalesce(p_file_path, file_path),
           file_size = coalesce(p_file_size, file_size),
           ac_price = coalesce(p_ac_price, ac_price),
           is_published = coalesce(p_is_published, is_published),
           is_featured = coalesce(p_is_featured, is_featured),
           is_exclusive = coalesce(p_is_exclusive, is_exclusive),
           updated_at = now()
     where id = p_id
     returning id into v_id;

    if v_id is null then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), case when p_id is null then 'content_created' else 'content_updated' end,
          'content_item', v_id::text, jsonb_build_object('title', p_title));

  return jsonb_build_object('ok', true, 'id', v_id, 'slug', v_slug);
end;
$$;

create or replace function public.admin_delete_content(p_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  delete from public.content_items where id = p_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'content_deleted', 'content_item', p_id::text, '{}'::jsonb);

  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 9) إعدادات الإدارة (خطط، طرق دفع، إعدادات عامة)
-- ---------------------------------------------------------------------------
create or replace function public.admin_update_plan(
  p_plan_id text, p_price_lyd numeric default null, p_price_usd numeric default null,
  p_duration_days int default null, p_download_limit int default null,
  p_limit_period text default null, p_active boolean default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  update public.subscription_plans
     set price_lyd = coalesce(p_price_lyd, price_lyd),
         price_usd = coalesce(p_price_usd, price_usd),
         duration_days = coalesce(p_duration_days, duration_days),
         download_limit = coalesce(p_download_limit, download_limit),
         limit_period = coalesce(p_limit_period, limit_period),
         active = coalesce(p_active, active),
         updated_at = now()
   where id = p_plan_id;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'plan_updated', 'subscription_plan', p_plan_id,
          jsonb_build_object('price_lyd', p_price_lyd, 'duration_days', p_duration_days,
                             'download_limit', p_download_limit, 'active', p_active));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.admin_upsert_payment_method(
  p_code text, p_name text, p_account_name text default null,
  p_account_identifier text default null, p_instructions text default null,
  p_enabled boolean default true, p_sort_order int default 0, p_provider text default 'manual'
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  insert into public.payment_method_settings(code, name, account_name, account_identifier, instructions,
                                            enabled, sort_order, provider)
  values (p_code, p_name, p_account_name, p_account_identifier, p_instructions,
          coalesce(p_enabled,true), coalesce(p_sort_order,0), coalesce(p_provider,'manual'))
  on conflict (code) do update
     set name = excluded.name,
         account_name = coalesce(excluded.account_name, public.payment_method_settings.account_name),
         account_identifier = coalesce(excluded.account_identifier, public.payment_method_settings.account_identifier),
         instructions = coalesce(excluded.instructions, public.payment_method_settings.instructions),
         enabled = excluded.enabled,
         sort_order = excluded.sort_order,
         provider = excluded.provider;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'payment_method_upserted', 'payment_method', p_code,
          jsonb_build_object('enabled', p_enabled));

  return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.admin_delete_payment_method(p_code text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;
  delete from public.payment_method_settings where code = p_code;
  return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 10) إحصائيات لوحة الإدارة
-- ---------------------------------------------------------------------------
create or replace function public.admin_stats()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v jsonb;
begin
  if not public.is_admin() then return jsonb_build_object('ok', false, 'message', 'not_admin'); end if;

  select jsonb_build_object(
    'users',        (select count(*) from public.profiles),
    'creators',     (select count(*) from public.profiles where role = 'creator'),
    'published',    (select count(*) from public.content_items where is_published),
    'addons',       (select count(*) from public.content_items where is_published and content_type = 'addon'),
    'worlds',       (select count(*) from public.content_items where is_published and content_type = 'world'),
    'downloads',    (select count(*) from public.content_downloads),
    'paid_users',   (select count(*) from public.profiles
                      where plan <> 'free' and (plan_expires_at is null or plan_expires_at > now())),
    'open_reports', (select count(*) from public.chat_reports where status = 'open'),
    'pending_orders', (select count(*) from public.payment_orders where status = 'submitted'),
    'pending_creator_apps', (select count(*) from public.creator_applications where status = 'pending'),
    'ac_balance_total', (select coalesce(sum(balance),0) from public.ac_wallets)
  ) into v;

  return v;
end;
$$;

-- ---------------------------------------------------------------------------
-- 11) منح صلاحيات التنفيذ للمستخدمين المسجّلين
-- ---------------------------------------------------------------------------
do $$
declare fn record;
begin
  for fn in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in (
        'record_content_view','record_content_download','toggle_favorite','ac_unlock_content',
        'ac_claim_task','ac_admin_adjust','ac_admin_grant_all','create_payment_order',
        'submit_payment_receipt','approve_payment_order','reject_payment_order',
        'admin_update_profile','set_profile_asset','update_my_profile','admin_upsert_content',
        'admin_delete_content','admin_update_plan','admin_upsert_payment_method',
        'admin_delete_payment_method','admin_stats','effective_plan','can_access_item',
        'download_limit_reached','plan_rank','role_rank','current_role_of','is_admin','is_staff'
      )
  loop
    execute format('grant execute on function %s to authenticated', fn.sig);
    execute format('grant execute on function %s to anon', fn.sig);
  end loop;
end $$;
