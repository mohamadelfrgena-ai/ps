-- ============================================================================
--  AddonStation Mobile — 31) أفاتار مجاني واحد: onefree لكل الحسابات
--
--  الطلب (حرفياً): «أنا أنشئ حساباً جديداً — أريد هذه الصورة المجانية
--  الافتراضية لكل الحساب [onefree.png]، والصور التي معيّنة مجانية
--  والافتراضية القديمة أزلها.»
--
--  القرار المتّفق عليه:
--      • الأفاتارات المجانية القديمة (58 صورة: PFP · Profiles · Pack One
--        ومنها «Default Profile») **تُعطَّل** (enabled = false) لا تُحذف:
--        تبقى صفوفها في القاعدة مخفيّة عن التطبيق، وإرجاعها سطر واحد.
--      • الصورة المجانية المفعّلة تبقى **واحدة فقط**: profiles/onefree.png.
--      • كل حساب يستعمل صورة مجانية قديمة يُحوَّل إلى onefree
--        («لكل الحساب»).
--
--  لماذا التعطيل يكفي؟ التطبيق يقرأ الأصول بـ
--      select=*&asset_type=eq.avatar&enabled=eq.true
--  (AssetsRepository.byType) ⇒ المعطَّل لا يظهر في تبويب الأفاتار أصلاً.
--
--  آمن لإعادة التنفيذ (idempotent).
--
--  ⚠️ الملف نفسه يجب أن يكون مرفوعاً في المخزن:
--        site-assets/profiles/onefree.png
--     (نسخة محفوظة في المستودع: supabase/site-assets/profiles/onefree.png)
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ٠) فهرس فريد على file_url (يحتاجه الـ upsert أدناه) + تنظيف أي تكرار قديم
-- ---------------------------------------------------------------------------
delete from public.site_assets a
 using public.site_assets b
 where a.file_url = b.file_url
   and a.id > b.id;

do $$
begin
  if not exists (
        select 1 from pg_indexes
         where schemaname = 'public' and indexname = 'site_assets_file_url_uidx'
     ) then
    execute 'create unique index site_assets_file_url_uidx on public.site_assets (file_url)';
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- ١) تعطيل كل الأفاتارات المجانية القديمة (تبقى في القاعدة · مخفيّة عن التطبيق)
-- ---------------------------------------------------------------------------
update public.site_assets
   set enabled = false
 where asset_type = 'avatar'
   and required_plan = 'free'
   and lower(file_url) not like '%onefree%';

-- النسخة المزدوجة المسار (profiles/profiles/onefree.png) تُعطَّل هي الأخرى:
-- الصورة نفسها، ولا داعي لظهورها مرّتين في تبويب الأفاتار.
update public.site_assets
   set enabled = false
 where asset_type = 'avatar'
   and lower(file_url) like '%onefree%'
   and file_url <> 'profiles/onefree.png';

-- ---------------------------------------------------------------------------
-- ٢) تثبيت الأصل المجاني الوحيد: profiles/onefree.png (اسمه onefree · مفعّل)
-- ---------------------------------------------------------------------------
insert into public.site_assets (asset_type, name, file_url, required_plan, animated, sort_order, enabled)
values ('avatar', 'Onefree', 'profiles/onefree.png', 'free', false, 1, true)
on conflict (file_url) do update
   set asset_type    = excluded.asset_type,
       name          = excluded.name,
       required_plan = excluded.required_plan,
       animated      = excluded.animated,
       sort_order    = excluded.sort_order,
       enabled       = true;

-- ---------------------------------------------------------------------------
-- ٣) توحيد مسار onefree في الحسابات: profiles/profiles/onefree.png ⇒ profiles/…
-- ---------------------------------------------------------------------------
update public.profiles
   set avatar_url = 'profiles/onefree.png',
       updated_at = now()
 where lower(avatar_url) like '%onefree%'
   and avatar_url <> 'profiles/onefree.png';

-- ---------------------------------------------------------------------------
-- ٤) كل حساب يستعمل صورة مجانية قديمة (معطَّلة الآن) ⇒ onefree
--    المطابقة من الجهتين لأن المسار قد يكون مسبوقاً بـ profiles/
-- ---------------------------------------------------------------------------
update public.profiles p
   set avatar_url = 'profiles/onefree.png',
       updated_at = now()
 where exists (
        select 1
          from public.site_assets a
         where a.asset_type    = 'avatar'
           and a.required_plan = 'free'
           and a.enabled       = false
           and lower(a.file_url) not like '%onefree%'
           and (   p.avatar_url = a.file_url
                or p.avatar_url = 'profiles/' || a.file_url
                or a.file_url   = 'profiles/' || p.avatar_url )
       );

-- ---------------------------------------------------------------------------
-- ٥) تحقّق: أفاتار مجاني مفعّل واحد فقط · ولا حساب بلا صورة
-- ---------------------------------------------------------------------------
select count(*)                                    as "كل الأفاتارات",
       count(*) filter (where required_plan = 'free' and enabled)     as "مجاني مفعّل",
       count(*) filter (where required_plan = 'free' and not enabled)  as "مجاني معطّل (قديم)",
       count(*) filter (where required_plan = 'epic')                  as "epic",
       count(*) filter (where required_plan = 'legendary')             as "legendary"
  from public.site_assets
 where asset_type = 'avatar';

select count(*)                                       as "كل الحسابات",
       count(*) filter (where avatar_url is null
                           or btrim(avatar_url) = '')  as "بلا صورة",
       count(*) filter (where lower(avatar_url) like '%onefree%') as "يستعمل onefree"
  from public.profiles;

do $$
declare
  v_enabled int;
  v_missing int;
  v_default text;
  v_name    text;
begin
  select count(*) into v_enabled
    from public.site_assets
   where asset_type = 'avatar' and required_plan = 'free' and enabled;

  select count(*) into v_missing
    from public.profiles
   where avatar_url is null or btrim(avatar_url) = '';

  select column_default into v_default
    from information_schema.columns
   where table_schema = 'public' and table_name = 'profiles' and column_name = 'avatar_url';

  select name into v_name
    from public.site_assets
   where asset_type = 'avatar' and required_plan = 'free' and enabled
   limit 1;

  if v_enabled <> 1 then
    raise exception '✗ عدد الأفاتارات المجانية المفعّلة = % (المطلوب 1 فقط)', v_enabled;
  end if;

  if v_missing > 0 then
    raise exception '✗ ما زال هناك % حساباً بلا صورة', v_missing;
  end if;

  if v_default is null or v_default not like '%onefree%' then
    raise exception '✗ افتراضي avatar_url ليس onefree: %', coalesce(v_default, '(بلا)');
  end if;

  raise notice '✅ 31_single_free_avatar: الأفاتار المجاني المفعّل = % (واحد فقط) · '
               'وكل الحسابات صورتها onefree', v_name;
end;
$$;
