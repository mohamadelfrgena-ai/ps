-- ============================================================================
--  AddonStation Mobile — 32) توحيد مسار onefree على المسار الموجود فعلاً
-- ============================================================================
--
--  لماذا هذا الملف؟ بعد تنفيذ 31 ظهر من فاحص القاعدة (99) فرقٌ صغير لكن مهم:
--      • صف الأفاتار المجاني كان مساره `profiles/profiles/onefree.png`
--        (وهو **المسار الحقيقي** الذي رُفعت إليه الصورة في المخزن)،
--      • و31 أنشأ صفاً **جديداً** على مسار `profiles/onefree.png` وفعّله —
--        والملف ليس موجوداً هناك ⇒ أفاتار مجاني مفعّل بلا صورة (يعمل بالاحتياط
--        فقط)، وعدد الأفاتار صار 124 بدل 123.
--
--  الحل (هذا الملف): نجعل **المسار المعتمد = المسار الموجود في المخزن**
--  `profiles/profiles/onefree.png`، ونوحّد عليه:
--      1) صف مكتبة الأصول (المفعّل الوحيد المجاني)؛
--      2) افتراضي عمود `profiles.avatar_url`؛
--      3) دالة `handle_new_user()` ⇒ كل حساب جديد؛
--      4) وكل حساب يشير إلى المسار غير الموجود (`profiles/onefree.png`).
--
--  التطبيق الآن يستخدم المسار المعتمد وحده، ثم نسخة محلية مطابقة عند تعذر
--  الشبكة؛ لذلك لا يوجد طلب 404 لمسار قديم ولا يظهر بديل بصري مختلف.
--  النتيجة النهائية بعد ملف 45: 66 أفاتاراً · وسجل مجاني واحد فقط · وملفه موجود فعلاً.
--
--  آمن لإعادة التنفيذ (idempotent) · يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ١) حذف الصف المكرّر الذي أنشأه 31 على مسار غير موجود
--    (لا نحذفه إن لم يكن صف onefree — حماية من أي حذف غير مقصود)
-- ---------------------------------------------------------------------------
delete from public.site_assets
 where asset_type = 'avatar'
   and file_url = 'profiles/onefree.png'
   and lower(name) = 'onefree'
   and exists (select 1 from public.site_assets b
                where b.file_url = 'profiles/profiles/onefree.png');

-- ---------------------------------------------------------------------------
-- ٢) تثبيت الصف المعتمد: المسار الموجود في المخزن · مجاني · مفعّل
-- ---------------------------------------------------------------------------
update public.site_assets
   set asset_type       = 'avatar',
       required_plan    = 'free',
       enabled          = true,
       collection       = 'Default',
       collection_order = 1,
       sort_order       = 1
 where file_url = 'profiles/profiles/onefree.png';

-- وإن لم يوجد الصف أصلاً (تنصيب جديد) أدرجه
insert into public.site_assets
  (asset_type, name, file_url, required_plan, animated, collection,
   collection_order, sort_order, enabled)
values
  ('avatar', 'Onefree', 'profiles/profiles/onefree.png', 'free', false,
   'Default', 1, 1, true)
on conflict (file_url) do update
   set asset_type       = excluded.asset_type,
       required_plan    = excluded.required_plan,
       collection       = excluded.collection,
       collection_order = excluded.collection_order,
       sort_order       = excluded.sort_order,
       enabled          = true;

-- ---------------------------------------------------------------------------
-- ٣) الأفاتار المجاني المفعّل = onefree وحده (ولا شيء غيره)
-- ---------------------------------------------------------------------------
update public.site_assets
   set enabled = false
 where asset_type = 'avatar'
   and required_plan = 'free'
   and lower(file_url) not like '%onefree%';

update public.site_assets
   set enabled = false
 where asset_type = 'avatar'
   and lower(file_url) like '%onefree%'
   and file_url <> 'profiles/profiles/onefree.png';

-- ---------------------------------------------------------------------------
-- ٤) توحيد مسار الحسابات على المسار الموجود
-- ---------------------------------------------------------------------------
alter table public.profiles
  alter column avatar_url set default 'profiles/profiles/onefree.png';

update public.profiles
   set avatar_url = 'profiles/profiles/onefree.png',
       updated_at = now()
 where avatar_url is null
    or btrim(avatar_url) = ''
    or lower(btrim(avatar_url)) = 'none'
    or avatar_url = 'profiles/onefree.png';

-- ---------------------------------------------------------------------------
-- ٥) دالة إنشاء الحساب: نفس المسار (كل حساب جديد)
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_username text;
  v_welcome  int := 50;
begin
  v_username := coalesce(
      nullif(new.raw_user_meta_data->>'username', ''),
      nullif(split_part(coalesce(new.email,''), '@', 1), ''),
      'player_' || substr(new.id::text, 1, 8)
  );

  -- ضمان فرادة اسم المستخدم
  while exists (select 1 from public.profiles p where p.username = v_username) loop
    v_username := v_username || floor(random()*10)::int;
  end loop;

  -- الصورة الافتراضية المجانية onefree (المسار الموجود في المخزن)
  insert into public.profiles (id, username, display_name, avatar_url)
  values (new.id, v_username, v_username, 'profiles/profiles/onefree.png')
  on conflict (id) do nothing;

  insert into public.ac_wallets (user_id, balance, total_earned)
  values (new.id, v_welcome, v_welcome)
  on conflict (user_id) do nothing;

  insert into public.ac_transactions (user_id, amount, kind, reason, request_key, balance_after)
  values (new.id, v_welcome, 'welcome', 'Welcome reward', 'welcome:' || new.id::text, v_welcome)
  on conflict (request_key) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- ٥ب) دالة اختيار الأصول: إعادة التعيين تأخذ المسار من صف الأفاتار المجاني
--      المفعّل نفسه (بدل مسار مكتوب يدوياً قد لا يكون موجوداً في المخزن).
--      — نفس النسخة الموجودة في 02_functions_core.sql (محدَّثة).
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

  -- This validated RPC is allowed through guard_profile_update; direct writes
  -- to protected asset columns remain unavailable to authenticated clients.
  perform set_config('app.allow_profile_write', '1', true);

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
      update public.profiles
         set banner_url = null,
             updated_at = now()
       where id = v_uid;
    else
      update public.profiles
         set equipped_item_id = 'none',
             updated_at = now()
       where id = v_uid;
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

-- ---------------------------------------------------------------------------
-- ٦) تحقّق
-- ---------------------------------------------------------------------------
select count(*)                                              as "كل الأفاتارات",
       count(*) filter (where required_plan = 'free' and enabled)  as "مجاني مفعّل",
       count(*) filter (where required_plan = 'free' and not enabled) as "مجاني معطّل (قديم)"
  from public.site_assets
 where asset_type = 'avatar';

select count(*)                                                as "كل الحسابات",
       count(*) filter (where avatar_url is null
                           or btrim(avatar_url) = '')          as "بلا صورة",
       count(*) filter (where avatar_url = 'profiles/profiles/onefree.png') as "على المسار المعتمد",
       count(*) filter (where avatar_url = 'profiles/onefree.png')          as "على مسار غير موجود"
  from public.profiles;

do $$
declare
  v_enabled int;
  v_missing int;
  v_wrong   int;
  v_path    text;
  v_default text;
begin
  select count(*) into v_enabled
    from public.site_assets
   where asset_type = 'avatar' and required_plan = 'free' and enabled;

  select count(*) into v_missing
    from public.profiles
   where avatar_url is null or btrim(avatar_url) = '';

  select count(*) into v_wrong
    from public.profiles
   where avatar_url = 'profiles/onefree.png';

  select count(*) into v_path
    from public.site_assets
   where asset_type = 'avatar' and required_plan = 'free' and enabled
     and file_url = 'profiles/profiles/onefree.png';

  select column_default into v_default
    from information_schema.columns
   where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url';

  if v_enabled <> 1 then
    raise exception '✗ عدد الأفاتارات المجانية المفعّلة = % (المطلوب 1)', v_enabled;
  end if;
  if v_path <> 1 then
    raise exception '✗ الأفاتار المفعّل ليس على المسار المعتمد profiles/profiles/onefree.png';
  end if;
  if v_missing > 0 then
    raise exception '✗ ما زال هناك % حساباً بلا صورة', v_missing;
  end if;
  if v_wrong > 0 then
    raise exception '✗ ما زال % حساباً على المسار غير الموجود profiles/onefree.png', v_wrong;
  end if;
  if v_default is null or v_default not like '%profiles/profiles/onefree.png%' then
    raise exception '✗ افتراضي avatar_url ليس المسار المعتمد: %', coalesce(v_default, '(بلا)');
  end if;

  raise notice '✅ 32: أفاتار مجاني مفعّل واحد على المسار الموجود في المخزن · '
               'وكل الحسابات عليه · والافتراضي صحيح.';
end;
$$;
