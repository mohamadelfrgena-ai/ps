-- ============================================================================
--  AddonStation Mobile — 14) أكواد هدايا AC (توليد من الإدارة · استبدال من المحفظة)
--
--  الفكرة باختصار:
--    • الإدارة تولّد الأكواد من داخل التطبيق (لوحة الإدارة → AC Management)
--      أو من الأداة  tools/make_redeem_codes.py  ثم تُلصق النتيجة في SQL Editor.
--    • المستخدم يكتب الكود في:  الإعدادات → المحفظة (AC wallet) → «كود هدية AC»
--      فوق قسم «كيف تكسب AC» مباشرة.
--    • القيم الجاهزة: 5,000 · 15,000 · 30,000 · 100,000 AC (وأي قيمة أخرى تريدها).
--
--  تنسيق الكود: 8 خانات = 6 حروف (كبيرة وصغيرة) + رقمان — مثال: Ac7Kp2Qm
--  (استُبعدت الحروف المتشابهة I/O/l/o والأرقام 0/1 لمنع الخطأ في الكتابة)
--
--  ✅ آمن لإعادة التنفيذ (idempotent) — نفّذه بعد 00_ALL_IN_ONE.sql.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) السماح بنوع معاملة جديد 'code' في سجل AC
--    (نفس القيد المعرَّف في 01_schema.sql مضافاً إليه 'code')
-- ---------------------------------------------------------------------------
alter table public.ac_transactions drop constraint if exists ac_transactions_kind_check;
alter table public.ac_transactions add constraint ac_transactions_kind_check
  check (kind in ('task','daily','streak','admin_reward','admin_adjustment','unlock','event','welcome','code'));

-- ---------------------------------------------------------------------------
-- 2) الجداول
-- ---------------------------------------------------------------------------
create table if not exists public.ac_redeem_codes (
  code       text primary key,                      -- الكود كما هو (حروف كبيرة/صغيرة + أرقام)
  amount     int not null check (amount between 100 and 10000000),
  max_uses   int not null default 1 check (max_uses >= 1),
  used_count int not null default 0 check (used_count >= 0),
  note       text,                                  -- وصف داخلي (حملة، هدية، …)
  active     boolean not null default true,
  expires_at timestamptz,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);
-- منع تكرار الكود بغضّ النظر عن حالة الحروف (Ac7Kp2Qm = ac7kp2qm)
create unique index if not exists ac_redeem_codes_upper_uniq on public.ac_redeem_codes (upper(code));
create index if not exists ac_redeem_codes_active_idx on public.ac_redeem_codes(active, created_at desc);

create table if not exists public.ac_redeem_uses (
  id         bigserial primary key,
  code       text not null references public.ac_redeem_codes(code) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  amount     int not null,
  created_at timestamptz not null default now()
);
-- كل مستخدم يستبدل الكود نفسه مرة واحدة فقط
create unique index if not exists ac_redeem_uses_once_uniq on public.ac_redeem_uses(code, user_id);
create index if not exists ac_redeem_uses_user_idx on public.ac_redeem_uses(user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 3) دالة مساعدة: توليد كود 8 خانات (6 حروف كبيرة/صغيرة + رقمان)
-- ---------------------------------------------------------------------------
create or replace function public.ac_random_code()
returns text
language plpgsql volatile
as $$
declare
  v_upper  text := 'ABCDEFGHJKLMNPQRSTUVWXYZ';   -- 24 حرفاً (بلا I و O)
  v_lower  text := 'abcdefghijkmnpqrstuvwxyz';   -- 24 حرفاً (بلا l و o)
  v_digit  text := '23456789';                   -- 8 أرقام (بلا 0 و 1)
  v_chars  text[] := '{}';
  i        int;
  j        int;
  v_tmp    text;
begin
  -- حرف كبير + حرف صغير (مضمونان) + 4 حروف عشوائية
  v_chars := array[
    substr(v_upper, 1 + floor(random() * 24)::int, 1),
    substr(v_lower, 1 + floor(random() * 24)::int, 1)
  ];
  for i in 1..4 loop
    if random() < 0.5 then
      v_chars := v_chars || substr(v_upper, 1 + floor(random() * 24)::int, 1);
    else
      v_chars := v_chars || substr(v_lower, 1 + floor(random() * 24)::int, 1);
    end if;
  end loop;
  -- رقمان
  for i in 1..2 loop
    v_chars := v_chars || substr(v_digit, 1 + floor(random() * 8)::int, 1);
  end loop;
  -- خلط المواضع (Fisher–Yates)
  for i in reverse 8..2 loop
    j := 1 + floor(random() * i)::int;
    v_tmp := v_chars[i];
    v_chars[i] := v_chars[j];
    v_chars[j] := v_tmp;
  end loop;
  return array_to_string(v_chars, '');
end $$;

-- ---------------------------------------------------------------------------
-- 4) دالة الإدارة: توليد أكواد جديدة (تُستدعى من لوحة الإدارة داخل التطبيق)
--    ac_admin_create_codes(p_amount, p_count, p_max_uses, p_note)
-- ---------------------------------------------------------------------------
create or replace function public.ac_admin_create_codes(
  p_amount   int,
  p_count    int default 1,
  p_max_uses int default 1,
  p_note     text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_codes text[] := '{}';
  v_code  text;
  v_count int := least(greatest(coalesce(p_count, 1), 1), 100);
  i       int;
  tries   int;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;
  if p_amount is null or p_amount < 100 then
    return jsonb_build_object('ok', false, 'message', 'bad_amount');
  end if;

  for i in 1..v_count loop
    tries := 0;
    loop
      v_code := public.ac_random_code();
      tries := tries + 1;
      exit when not exists (select 1 from public.ac_redeem_codes where upper(code) = upper(v_code));
      exit when tries > 20;   -- احتمال نظرياً فقط
    end loop;
    insert into public.ac_redeem_codes(code, amount, max_uses, note, created_by)
    values (v_code, p_amount, greatest(coalesce(p_max_uses, 1), 1), p_note, auth.uid())
    on conflict (code) do nothing;
    v_codes := v_codes || v_code;
  end loop;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (auth.uid(), 'ac_create_codes', 'ac_redeem_codes', null,
          jsonb_build_object('amount', p_amount, 'count', v_count, 'max_uses', p_max_uses));

  return jsonb_build_object('ok', true, 'amount', p_amount, 'count', v_count, 'codes', to_jsonb(v_codes));
end $$;

-- ---------------------------------------------------------------------------
-- 5) دالة المستخدم: استبدال الكود من محفظة AC
--    تُرجع: ok + amount + balance، أو ok=false مع سبب مفهوم:
--    code_invalid · code_used · code_expired · code_inactive · code_exhausted
-- ---------------------------------------------------------------------------
create or replace function public.ac_redeem_code(p_code text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_norm    text := upper(btrim(coalesce(p_code, '')));
  v_code    public.ac_redeem_codes;
  v_balance int;
  v_key     text;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;
  if length(v_norm) < 4 then
    return jsonb_build_object('ok', false, 'message', 'code_invalid');
  end if;

  -- قفل الصف حتى لا يُستهلك الكود مرتين في نفس اللحظة
  select * into v_code from public.ac_redeem_codes where upper(code) = v_norm for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'code_invalid');
  end if;
  if not v_code.active then
    return jsonb_build_object('ok', false, 'message', 'code_inactive');
  end if;
  if v_code.expires_at is not null and v_code.expires_at < now() then
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;
  if exists (select 1 from public.ac_redeem_uses where code = v_code.code and user_id = v_uid) then
    return jsonb_build_object('ok', false, 'message', 'code_used');
  end if;
  if v_code.used_count >= v_code.max_uses then
    return jsonb_build_object('ok', false, 'message', 'code_exhausted');
  end if;

  v_key := 'code:' || v_uid::text || ':' || v_code.code;

  insert into public.ac_redeem_uses(code, user_id, amount)
  values (v_code.code, v_uid, v_code.amount)
  on conflict (code, user_id) do nothing;

  update public.ac_redeem_codes set used_count = used_count + 1 where code = v_code.code;

  insert into public.ac_wallets(user_id) values (v_uid) on conflict (user_id) do nothing;

  update public.ac_wallets
     set balance      = balance + v_code.amount,
         total_earned = total_earned + v_code.amount,
         updated_at   = now()
   where user_id = v_uid
   returning balance into v_balance;

  insert into public.ac_transactions(user_id, amount, kind, reason, request_key, balance_after)
  values (v_uid, v_code.amount, 'code', 'redeem:' || v_code.code, v_key, v_balance)
  on conflict (request_key) do nothing;

  return jsonb_build_object(
    'ok', true,
    'amount', v_code.amount,
    'balance', v_balance,
    'code', v_code.code
  );
end $$;

-- ---------------------------------------------------------------------------
-- 6) الأمان: RLS (لا يرى الأكوادَ غيرُ الإدارة) + المنح الصريحة
-- ---------------------------------------------------------------------------
alter table public.ac_redeem_codes enable row level security;
alter table public.ac_redeem_uses  enable row level security;

drop policy if exists ac_redeem_codes_admin on public.ac_redeem_codes;
create policy ac_redeem_codes_admin on public.ac_redeem_codes
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists ac_redeem_uses_self on public.ac_redeem_uses;
create policy ac_redeem_uses_self on public.ac_redeem_uses
  for select using (user_id = auth.uid() or public.is_admin());

-- الجدولان جديدان: منح القراءة للمسجّلين (وبلا anon حتى لا تُقرأ الأكواد)
grant select on public.ac_redeem_codes, public.ac_redeem_uses to authenticated;

grant execute on function public.ac_redeem_code(text) to authenticated;
grant execute on function public.ac_admin_create_codes(int, int, int, text) to authenticated;
-- الإلغاء من public ضروري: منح EXECUTE الافتراضي في PostgreSQL يشمل كل الأدوار
revoke all on function public.ac_redeem_code(text) from public, anon;
revoke all on function public.ac_admin_create_codes(int, int, int, text) from public, anon;
revoke all on function public.ac_random_code() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 7) أمثلة جاهزة (اختيارية) — علّقها إن لم تُرِدها:
--    لإضافة كود يدوياً بقيمة معيّنة اكتب سطراً مثل هذا:
--      insert into public.ac_redeem_codes (code, amount, note)
--      values ('Ac7Kp2Qm', 15000, 'حملة سبتمبر');
--    للتوليد دفعةً واحدة من SQL مباشرة:
--      select public.ac_admin_create_codes(15000, 5);   -- 5 أكواد × 15,000
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- ✅ تحقق: يجب أن ترى الجدولين (عدد الأكواد = 0 في البداية) والدالتين
-- ---------------------------------------------------------------------------
select
  (select count(*) from public.ac_redeem_codes) as codes_total,
  (select count(*) from public.ac_redeem_uses)  as uses_total,
  (select count(*) from information_schema.routines
    where routine_schema = 'public'
      and routine_name in ('ac_redeem_code', 'ac_admin_create_codes', 'ac_random_code')) as functions_total,
  public.ac_random_code() as sample_code;
