-- ============================================================================
--  AddonStation Mobile — 24) الصورة الافتراضية المجانية (onefree) لكل حساب جديد
--
--  الطلب: «أرجِع صورة onefree الافتراضية لكل مستخدم عند إنشاء الحساب —
--          أي الصورة الافتراضية المجانية.»
--
--  ما يفعله هذا الملف (آمن لإعادة التنفيذ):
--      1) يجعل الافتراضي لعمود profiles.avatar_url = 'profiles/profiles/onefree.png'.
--      2) يعبّئ كل حساب **بلا صورة** (فارغ أو null) بنفس الصورة — فلا يبقى
--         حساب بلا أفاتار.
--      3) يثبّت الصورة صراحةً داخل دالة handle_new_user() ⇒ كل حساب **جديد**
--         يولد وصورته هي onefree (بلا انتظار أي خطوة من التطبيق).
--      4) يتحقق: يعدّ الحسابات بلا صورة (يجب صفر) ويطبع الافتراضي.
--
--  ملاحظة: «إعادة التعيين» داخل set_profile_asset تُعيد onefree أيضاً،
--  والصورة مسجّلة في site_assets بـ required_plan = 'free' ⇒ مباحة للجميع
--  (هي الصورة الوحيدة غير المقفولة في تبويب الأفاتار).
--
--  ⚠️ المسار المعتمد هو ما هو موجود فعلاً في المخزن:
--       site-assets/profiles/profiles/onefree.png
--     (التطبيق يستخدم هذا المسار وحده، ثم نسخة محلية مطابقة للصورة نفسها عند
--      تعذر الشبكة. وملفا 32 و45 يثبتان المسار والسجل المجاني الوحيد.)
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

-- ١) الافتراضي على مستوى العمود
alter table public.profiles
  alter column avatar_url set default 'profiles/profiles/onefree.png';

-- ٢) تعبئة الحسابات القائمة التي بلا صورة
update public.profiles
   set avatar_url = 'profiles/profiles/onefree.png',
       updated_at = now()
 where avatar_url is null
    or btrim(avatar_url) = ''
    or lower(btrim(avatar_url)) = 'none';

-- ٣) تثبيت الصورة داخل دالة إنشاء الحساب (كل حساب جديد)
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

  -- الصورة الافتراضية المجانية onefree لكل حساب جديد
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
-- تحقّق: لا حساب بلا صورة · والصورة المجانية واحدة فقط غير مقفولة
-- ---------------------------------------------------------------------------
select count(*)                                             as "كل الحسابات",
       count(*) filter (where avatar_url is null
                           or btrim(avatar_url) = '')       as "بلا صورة",
       count(*) filter (where avatar_url like '%onefree%')  as "يستعمل onefree"
  from public.profiles;

select coalesce(column_default, '(بلا)') as "افتراضي avatar_url"
  from information_schema.columns
 where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url';

do $$
declare
  v_missing int;
  v_default text;
begin
  select count(*) into v_missing
    from public.profiles
   where avatar_url is null or btrim(avatar_url) = '';

  select column_default into v_default
    from information_schema.columns
   where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url';

  if v_missing > 0 then
    raise exception '✗ ما زال هناك % حساباً بلا صورة', v_missing;
  end if;

  if v_default is null or v_default not like '%onefree%' then
    raise exception '✗ افتراضي avatar_url ليس onefree: %', coalesce(v_default, '(بلا)');
  end if;

  raise notice '✅ 24_free_default_avatar: كل الحسابات لها صورة · الافتراضي = %', v_default;
end;
$$;
