-- ============================================================================
-- AddonStation Mobile — creator verification pack and featured profile assets
--
-- This migration:
--   * adds a creator-only catalog tier and enforces it in RLS and profile RPCs;
--   * removes every Nature banner and replaces missing/Nature profile banners
--     with the single free default banner;
--   * registers the new Featured banners and three Animated profile pictures;
--   * replaces legacy verification duplicates with one creator-only pack,
--     except Twitterverified and Verified Blue, which are admin-only;
--   * raises the public image object limit to 10 MiB for the supplied GIFs.
--
-- Upload the matching files with tools/deploy_requested_profile_assets.py.
-- The SQL is idempotent and can be rerun safely.
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1) Creator-only catalog tier and read policy
-- ---------------------------------------------------------------------------
alter table public.site_assets
  drop constraint if exists site_assets_required_plan_check;

alter table public.site_assets
  add constraint site_assets_required_plan_check
  check (required_plan in ('free', 'epic', 'legendary', 'creator', 'admin'));

drop policy if exists assets_read on public.site_assets;
create policy assets_read on public.site_assets
  for select using (
    public.is_admin()
    or (
      enabled = true
      and required_plan not in ('admin', 'creator')
    )
    or (
      enabled = true
      and required_plan = 'creator'
      and public.current_role_of() = 'creator'
    )
  );

-- The largest supplied animated profile picture is about 7.5 MiB.
update storage.buckets
   set file_size_limit = 10485760
 where id = 'site-assets';

-- ---------------------------------------------------------------------------
-- 2) Remove Nature and establish one canonical free/default banner
-- ---------------------------------------------------------------------------
do $$
declare
  v_default_banner constant text := 'banners/featured/06aa4e3ff6eab5eecad52378a694114a.jpg';
begin
  update public.profiles
     set banner_url = v_default_banner,
         updated_at = now()
   where banner_url is null
      or btrim(banner_url) = ''
      or banner_url like 'banners/nature/%'
      or exists (
        select 1
          from public.site_assets a
         where a.asset_type = 'banner'
           and (a.file_url = public.profiles.banner_url or a.name = public.profiles.banner_url)
           and (
             a.file_url like 'banners/nature/%'
             or lower(coalesce(a.collection, '')) like 'nature%'
           )
      );

  delete from public.site_assets
   where asset_type = 'banner'
     and (
       file_url like 'banners/nature/%'
       or lower(coalesce(collection, '')) like 'nature%'
     );

  -- No other banner may be free.
  update public.site_assets
     set required_plan = 'epic'
   where asset_type = 'banner'
     and required_plan = 'free'
     and file_url <> v_default_banner;

  alter table public.profiles
    alter column banner_url set default 'banners/featured/06aa4e3ff6eab5eecad52378a694114a.jpg';
end
$$;

-- ---------------------------------------------------------------------------
-- 3) Featured banners: exactly one free default, every other supplied banner
--    requires Epic. Animated formats remain marked animated.
-- ---------------------------------------------------------------------------
insert into public.site_assets (
  asset_type, name, file_url, required_plan, animated,
  sort_order, enabled, collection, collection_order
)
values
  ('banner', 'Default Banner', 'banners/featured/06aa4e3ff6eab5eecad52378a694114a.jpg', 'free', false, 1, true, 'Featured', 22),
  ('banner', 'Featured Banner 01', 'banners/featured/033e72294c7c0ceb6913f47570f6dbc1.jpg', 'epic', false, 2, true, 'Featured', 22),
  ('banner', 'Featured Banner 02', 'banners/featured/0559559ec27203deaaae999c685e4e34.jpg', 'epic', false, 3, true, 'Featured', 22),
  ('banner', 'Featured Banner 03', 'banners/featured/0d5cf7852c973310b52d27caebbc3a0e.jpg', 'epic', false, 4, true, 'Featured', 22),
  ('banner', 'Featured Banner 04', 'banners/featured/0e7bcc376ea3b644dae38e19b04cc867.jpg', 'epic', false, 5, true, 'Featured', 22),
  ('banner', 'Featured Banner 05', 'banners/featured/146072c42f732ede7d30af1be84d01c0.jpg', 'epic', false, 6, true, 'Featured', 22),
  ('banner', 'Featured Banner 06', 'banners/featured/15d168d25632cfaa51fab80b586ff7b2.jpg', 'epic', false, 7, true, 'Featured', 22),
  ('banner', 'Featured Banner 07', 'banners/featured/16bec9a832a1b90ca555c363c4837401.jpg', 'epic', false, 8, true, 'Featured', 22),
  ('banner', 'Featured Banner 08', 'banners/featured/2107e6d162a8bd983e45612eab08822f.jpg', 'epic', false, 9, true, 'Featured', 22),
  ('banner', 'Featured Banner 09', 'banners/featured/246e40182865cb70d43fa69f79576f5a.jpg', 'epic', false, 10, true, 'Featured', 22),
  ('banner', 'Featured Banner 10', 'banners/featured/26fe63c20193c74b57183a058ee321e5.jpg', 'epic', false, 11, true, 'Featured', 22),
  ('banner', 'Featured Banner 11', 'banners/featured/404d66f8b76c24057325a49b64427ef5.jpg', 'epic', false, 12, true, 'Featured', 22),
  ('banner', 'Featured Banner 12', 'banners/featured/60cb2c6d2bb3ece37fa9f7e1fdccdef2.jpg', 'epic', false, 13, true, 'Featured', 22),
  ('banner', 'Featured Banner 13', 'banners/featured/688e447b12909607c988197eb8e2e819.jpg', 'epic', false, 14, true, 'Featured', 22),
  ('banner', 'Featured Banner 14', 'banners/featured/7103078dcc0550fb85ad3126b17686f4.jpg', 'epic', false, 15, true, 'Featured', 22),
  ('banner', 'Featured Banner 15', 'banners/featured/71e0eefd21a78b260924bfb6c055c2b2.jpg', 'epic', false, 16, true, 'Featured', 22),
  ('banner', 'Featured Banner 16', 'banners/featured/77e3e3a1802f5c8ade684d03eb28e8a2.jpg', 'epic', false, 17, true, 'Featured', 22),
  ('banner', 'Featured Banner 17', 'banners/featured/933f45462954966f2af3fed2053c70e2.jpg', 'epic', false, 18, true, 'Featured', 22),
  ('banner', 'Featured Banner 18', 'banners/featured/a22a441f7a71c1d0d517a4c1caed12e8.jpg', 'epic', false, 19, true, 'Featured', 22),
  ('banner', 'Featured Banner 19', 'banners/featured/b4c9daa33946ff3f4f285a46c3398980.png', 'epic', false, 20, true, 'Featured', 22),
  ('banner', 'Featured Banner 20', 'banners/featured/bbfa6c2a24e75048585431229846cba6.jpg', 'epic', false, 21, true, 'Featured', 22),
  ('banner', 'Featured Banner 21', 'banners/featured/c9cba4a4fe7c3790f347853d8eceb5ca.jpg', 'epic', false, 22, true, 'Featured', 22),
  ('banner', 'Featured Banner 22', 'banners/featured/cdefeac0a46e4eb3a2f1db4f05f5ab1a.jpg', 'epic', false, 23, true, 'Featured', 22),
  ('banner', 'Featured Banner 23', 'banners/featured/d32ef64138ed622d6c8ed3b3f7b1096a.jpg', 'epic', false, 24, true, 'Featured', 22),
  ('banner', 'Featured Banner 24', 'banners/featured/dc3ecdab0fa15f3bd29d1e20718648e6.gif', 'epic', true, 25, true, 'Featured', 22),
  ('banner', 'Featured Banner 25', 'banners/featured/e25aaf29ef9d1f5cb5a122c240d75e28.jpg', 'epic', false, 26, true, 'Featured', 22),
  ('banner', 'Featured Banner 26', 'banners/featured/ec81643ed29ad01f9fb29035a5cd7901.jpg', 'epic', false, 27, true, 'Featured', 22),
  ('banner', 'Featured Banner 27', 'banners/featured/fb49068c8de7e8232613b4996aeb477e.jpg', 'epic', false, 28, true, 'Featured', 22),
  ('banner', 'Featured Banner 28', 'banners/featured/fec1f9dcc3e7cd68f38ec231a3f33b72.jpg', 'epic', false, 29, true, 'Featured', 22),
  ('banner', 'Featured Banner 29', 'banners/featured/giphy-1.gif', 'epic', true, 30, true, 'Featured', 22),
  ('banner', 'Featured Banner 30', 'banners/featured/giphy-1.webp', 'epic', true, 31, true, 'Featured', 22),
  ('banner', 'Featured Banner 31', 'banners/featured/giphy_s-1.gif', 'epic', true, 32, true, 'Featured', 22)
on conflict (file_url) do update set
  asset_type = excluded.asset_type,
  name = excluded.name,
  required_plan = excluded.required_plan,
  animated = excluded.animated,
  sort_order = excluded.sort_order,
  enabled = true,
  collection = excluded.collection,
  collection_order = excluded.collection_order;

-- ---------------------------------------------------------------------------
-- 4) Three supplied GIFs belong to the existing Animated profile collection
-- ---------------------------------------------------------------------------
insert into public.site_assets (
  asset_type, name, file_url, required_plan, animated,
  sort_order, enabled, collection, collection_order
)
values
  ('avatar', 'Animated Giphy 1', 'profiles/animated/giphy.gif', 'legendary', true, 124, true, 'Animated', 11),
  ('avatar', 'Animated Giphy 2', 'profiles/animated/giphy-2.gif', 'legendary', true, 125, true, 'Animated', 11),
  ('avatar', 'Animated Giphy 3', 'profiles/animated/giphy-3.gif', 'legendary', true, 126, true, 'Animated', 11)
on conflict (file_url) do update set
  asset_type = excluded.asset_type,
  name = excluded.name,
  required_plan = excluded.required_plan,
  animated = true,
  sort_order = excluded.sort_order,
  enabled = true,
  collection = excluded.collection,
  collection_order = excluded.collection_order;

-- ---------------------------------------------------------------------------
-- 5) Verification pack. Delete legacy rows by filename/name first so an old
--    unrestricted copy can never remain visible beside the restricted row.
-- ---------------------------------------------------------------------------
delete from public.site_assets
 where asset_type = 'item'
   and (
     lower(regexp_replace(file_url, '^.*/', '')) = any (array[
       '1422-black-verify.gif', '14586-babypinkcheck.gif',
       '16841-twitterverified.png', '2872_purpleverify.png',
       '3488-brown-checkmark.gif', '4166-orange-verification.gif',
       '4166-yellow-verification.gif', '4650_pinkverify.png',
       '4731-verified-red.gif', '4869-verify-light-pink.gif',
       '5647-verifyed-pink.gif', '5707-lightpurplecheck.gif',
       '60530-metaverified.png', '6390-verifyed-yellow.gif',
       '6600-discord-verify-green.gif', '7041-verify-pastel-pink.png',
       '7137-white-verify.gif', '7762-verified-blue.gif',
       '8393-verify-yellow.gif', '9434-lightblue-verification.gif',
       '9434-purple-verification.gif'
     ])
     or lower(name) in ('verified blue', 'twitterverified', 'twitter verified')
   );

insert into public.site_assets (
  asset_type, name, file_url, required_plan, animated,
  sort_order, enabled, collection, collection_order
)
values
  ('item', 'Black Verify', 'items/verification/1422-black-verify.gif', 'creator', true, 177, true, 'Verification', 22),
  ('item', 'Baby Pink Check', 'items/verification/14586-babypinkcheck.gif', 'creator', true, 178, true, 'Verification', 22),
  ('item', 'Twitterverified', 'admin-private/items/verification/16841-twitterverified.png', 'admin', false, 179, true, 'Verification', 22),
  ('item', 'Purple Verify', 'items/verification/2872_purpleverify.png', 'creator', false, 180, true, 'Verification', 22),
  ('item', 'Brown Checkmark', 'items/verification/3488-brown-checkmark.gif', 'creator', true, 181, true, 'Verification', 22),
  ('item', 'Orange Verification', 'items/verification/4166-orange-verification.gif', 'creator', true, 182, true, 'Verification', 22),
  ('item', 'Yellow Verification', 'items/verification/4166-yellow-verification.gif', 'creator', true, 183, true, 'Verification', 22),
  ('item', 'Pink Verify', 'items/verification/4650_pinkverify.png', 'creator', false, 184, true, 'Verification', 22),
  ('item', 'Verified Red', 'items/verification/4731-verified-red.gif', 'creator', true, 185, true, 'Verification', 22),
  ('item', 'Verify Light Pink', 'items/verification/4869-verify-light-pink.gif', 'creator', true, 186, true, 'Verification', 22),
  ('item', 'Verified Pink', 'items/verification/5647-verifyed-pink.gif', 'creator', true, 187, true, 'Verification', 22),
  ('item', 'Light Purple Check', 'items/verification/5707-lightpurplecheck.gif', 'creator', true, 188, true, 'Verification', 22),
  ('item', 'Meta Verified', 'items/verification/60530-metaverified.png', 'creator', false, 189, true, 'Verification', 22),
  ('item', 'Verified Yellow', 'items/verification/6390-verifyed-yellow.gif', 'creator', true, 190, true, 'Verification', 22),
  ('item', 'Discord Verify Green', 'items/verification/6600-discord-verify-green.gif', 'creator', true, 191, true, 'Verification', 22),
  ('item', 'Verify Pastel Pink', 'items/verification/7041-verify-pastel-pink.png', 'creator', false, 192, true, 'Verification', 22),
  ('item', 'White Verify', 'items/verification/7137-white-verify.gif', 'creator', true, 193, true, 'Verification', 22),
  ('item', 'Verified Blue', 'admin-private/items/verification/7762-verified-blue.gif', 'admin', true, 194, true, 'Verification', 22),
  ('item', 'Verify Yellow', 'items/verification/8393-verify-yellow.gif', 'creator', true, 195, true, 'Verification', 22),
  ('item', 'Light Blue Verification', 'items/verification/9434-lightblue-verification.gif', 'creator', true, 196, true, 'Verification', 22),
  ('item', 'Purple Verification', 'items/verification/9434-purple-verification.gif', 'creator', true, 197, true, 'Verification', 22)
on conflict (file_url) do update set
  asset_type = excluded.asset_type,
  name = excluded.name,
  required_plan = excluded.required_plan,
  animated = excluded.animated,
  sort_order = excluded.sort_order,
  enabled = true,
  collection = excluded.collection,
  collection_order = excluded.collection_order;

-- Reassert the one-free-banner invariant after every upsert.
update public.site_assets
   set required_plan = 'epic'
 where asset_type = 'banner'
   and file_url <> 'banners/featured/06aa4e3ff6eab5eecad52378a694114a.jpg'
   and required_plan = 'free';

-- ---------------------------------------------------------------------------
-- 6) Server-side selection guard. Creator assets are usable only by creators;
--    admin assets remain usable only by admins. Catalog visibility alone is
--    never treated as authorization.
-- ---------------------------------------------------------------------------
create or replace function public.set_profile_asset(p_kind text, p_value text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid        uuid := auth.uid();
  v_asset      public.site_assets;
  v_plan       text;
  v_actor_role text;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  if p_kind not in ('avatar', 'banner', 'item') then
    return jsonb_build_object('ok', false, 'message', 'invalid_kind');
  end if;

  perform set_config('app.allow_profile_write', '1', true);

  if p_value is null or p_value = '' or p_value = 'none' then
    if p_kind = 'avatar' then
      update public.profiles
         set avatar_url = coalesce(
               (
                 select a.file_url
                   from public.site_assets a
                  where a.asset_type = 'avatar'
                    and a.required_plan = 'free'
                    and a.enabled
                  order by a.sort_order, a.id
                  limit 1
               ),
               'profiles/profiles/onefree.png'
             ),
             updated_at = now()
       where id = v_uid;
    elsif p_kind = 'banner' then
      update public.profiles
         set banner_url = 'banners/featured/06aa4e3ff6eab5eecad52378a694114a.jpg',
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

  select * into v_asset
    from public.site_assets
   where enabled
     and asset_type = case when p_kind = 'item' then 'item' else p_kind end
     and (file_url = p_value or name = p_value)
   limit 1;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'asset_not_found');
  end if;

  select role into v_actor_role
    from public.profiles
   where id = v_uid;
  v_actor_role := coalesce(v_actor_role, 'user');
  v_plan := public.effective_plan(v_uid);

  if v_asset.required_plan = 'admin' and v_actor_role <> 'admin' then
    return jsonb_build_object('ok', false, 'message', 'admin_only_asset');
  end if;

  if v_asset.required_plan = 'creator' and v_actor_role <> 'creator' then
    return jsonb_build_object('ok', false, 'message', 'creator_only_asset');
  end if;

  if v_asset.required_plan not in ('admin', 'creator')
     and v_actor_role <> 'admin'
     and public.plan_rank(v_plan) < public.plan_rank(v_asset.required_plan)
  then
    return jsonb_build_object(
      'ok', false,
      'message', 'plan_too_low',
      'required_plan', v_asset.required_plan
    );
  end if;

  if p_kind = 'avatar' then
    update public.profiles
       set avatar_url = v_asset.file_url,
           updated_at = now()
     where id = v_uid;
  elsif p_kind = 'banner' then
    update public.profiles
       set banner_url = v_asset.file_url,
           updated_at = now()
     where id = v_uid;
  else
    update public.profiles
       set equipped_item_id = coalesce(v_asset.name, v_asset.file_url),
           updated_at = now()
     where id = v_uid;
  end if;

  return jsonb_build_object(
    'ok', true,
    'value', v_asset.file_url,
    'required_plan', v_asset.required_plan
  );
end;
$$;

revoke all on function public.set_profile_asset(text, text) from public, anon;
grant execute on function public.set_profile_asset(text, text) to authenticated, service_role;

notify pgrst, 'reload schema';

commit;
