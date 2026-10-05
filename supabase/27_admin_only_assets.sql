-- ============================================================================
--  AddonStation Mobile — ٢٧) أصول **حصرية للإدارة** (required_plan = 'admin')
--
--  الطلب:
--    ١) الأداة «Enchanted Bedrock»  ⇒ للأدمن فقط
--    ٢) صورة البروفايل «PFP 4»      ⇒ للأدمن فقط
--    ٣) غلافا الأدمن downloa1d.gif و Animation Reference.gif ⇒ للأدمن فقط
--
--  `required_plan = 'admin'` يعني: لا يراها ولا يختارها إلا من كان دوره admin
--  (القاعدة ترفضها لغير الأدمن داخل set_profile_asset بـ admin_only_asset).
--
--  آمن لإعادة التنفيذ: يحدّث ما كان مسجّلاً بأي مسار، ويضيف الصفوف إن كانت
--  ناقصة بالمسار الرسمي.
-- ============================================================================

-- ١) الأداة: Enchanted Bedrock
update public.site_assets
   set required_plan = 'admin',
       enabled       = true
 where asset_type = 'item'
   and (name ilike '%enchanted%bedrock%'
        or file_url ilike '%enchanted%bedrock%'
        or file_url ilike '%enchanted-bedrock%');

-- ٢) صورة البروفايل: PFP 4
update public.site_assets
   set required_plan = 'admin',
       enabled       = true
 where asset_type = 'avatar'
   and (lower(btrim(name)) in ('pfp 4', 'pfp4')
        or file_url ilike '%pfp/download-1.jpg'
        or file_url ilike '%pfp4%');

-- ٣) غلافا الأدمن (إن كانا مسجّلين بأي مسار)
update public.site_assets
   set required_plan = 'admin',
       asset_type    = 'banner',
       enabled       = true,
       animated      = true
 where file_url ilike '%downloa1d.gif'
    or file_url ilike '%animation-reference.gif'
    or file_url ilike '%animation%reference.gif';

-- ٤) إضافتهما بالمسار الرسمي إن لم يكونا مسجّلين
insert into public.site_assets
    (asset_type, name, file_url, sticker_pack, required_plan, animated, enabled, sort_order)
values
    ('banner', 'Downloa1d',           'banners/admin/downloa1d.gif',           null, 'admin', true, true, 1),
    ('banner', 'Animation Reference', 'banners/admin/Animation-Reference.gif', null, 'admin', true, true, 2)
on conflict (file_url) do update
    set required_plan = 'admin',
        asset_type    = excluded.asset_type,
        enabled       = true,
        animated      = true,
        sort_order    = excluded.sort_order;

-- ٥) القسم (collection) — العمود اختياري (يُضاف في 15_assets_v2.sql)
do $$
begin
    if exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'site_assets'
           and column_name = 'collection'
    ) then
        update public.site_assets
           set collection = 'Admin 👑'
         where file_url in ('banners/admin/downloa1d.gif',
                            'banners/admin/Animation-Reference.gif');
    end if;

    if exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'site_assets'
           and column_name = 'collection_order'
    ) then
        update public.site_assets
           set collection_order = 24
         where file_url in ('banners/admin/downloa1d.gif',
                            'banners/admin/Animation-Reference.gif');
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- تحقّق
-- ---------------------------------------------------------------------------
select asset_type, name, file_url, required_plan, collection
  from public.site_assets
 where required_plan = 'admin'
 order by asset_type, name;

do $$
declare
    v_item    int;
    v_avatar  int;
    v_banner  int;
begin
    select count(*) into v_item
      from public.site_assets
     where asset_type = 'item' and name ilike '%enchanted%bedrock%' and required_plan = 'admin';

    select count(*) into v_avatar
      from public.site_assets
     where asset_type = 'avatar'
       and (lower(btrim(name)) in ('pfp 4', 'pfp4') or file_url ilike '%pfp/download-1.jpg')
       and required_plan = 'admin';

    select count(*) into v_banner
      from public.site_assets
     where asset_type = 'banner' and required_plan = 'admin';

    if v_item < 1 then
        raise exception '✗ الأداة Enchanted Bedrock ليست admin (تحقّق من اسمها المسجّل)';
    end if;
    if v_avatar < 1 then
        raise exception '✗ صورة البروفايل PFP 4 ليست admin';
    end if;
    if v_banner < 2 then
        raise exception '✗ أغلفة الأدمن أقل من اثنين (الموجود: %)', v_banner;
    end if;

    raise notice '✅ 27_admin_only_assets: أداة=% · صورة=% · أغلفة=% كلها required_plan=admin',
        v_item, v_avatar, v_banner;
end;
$$;
