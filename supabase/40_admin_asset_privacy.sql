-- ============================================================================
--  AddonStation Mobile - 40) Admin Asset Privacy
--
--  Policy:
--    * Assets with required_plan = 'admin' are hidden from regular users.
--    * If an admin selects a private avatar, banner, or item, its value is
--      redacted from every non-admin account.
--    * New admin files are stored in the private admin-assets bucket and can
--      only be read by users with the admin role.
--    * Regular user profiles and assets remain visible as before.
--
--  Deployment order:
--    First distribute an app version that reads visible_profiles (with its
--    legacy-schema fallback), and then run this file. Older app versions read
--    profiles directly.
--
--  Important for legacy assets:
--    This file hides rows and profile values immediately, but SQL cannot move
--    Storage object bytes between buckets. Run
--    tools/migrate_admin_assets_private.py afterward to move legacy files from
--    the public site-assets bucket to the private admin-assets bucket and
--    safely remove the public copies.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Asset catalog: only admins can read admin rows
-- ---------------------------------------------------------------------------
drop policy if exists assets_read on public.site_assets;
create policy assets_read on public.site_assets
  for select using (
    public.is_admin()
    or (enabled = true and required_plan <> 'admin')
  );

-- ---------------------------------------------------------------------------
-- 2) Stable canonical path for private assets
--
--    Update profile references before the catalog row. This keeps rerunning
--    00_ALL_IN_ONE safe after file migration: migrations 15/27 may restore the
--    public path, while migration 40 always restores admin-private/... and the
--    physical file remains in admin-assets.
-- ---------------------------------------------------------------------------
update public.profiles p
   set avatar_url = 'admin-private/' || ltrim(p.avatar_url, '/')
 where p.avatar_url not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'avatar'
        and a.file_url = p.avatar_url
   );

update public.profiles p
   set banner_url = 'admin-private/' || ltrim(p.banner_url, '/')
 where p.banner_url is not null
   and p.banner_url not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'banner'
        and a.file_url = p.banner_url
   );

-- Very old app versions stored the item path instead of its name.
update public.profiles p
   set equipped_item_id = 'admin-private/' || ltrim(p.equipped_item_id, '/')
 where p.equipped_item_id not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'item'
        and a.file_url = p.equipped_item_id
   );

update public.site_assets
   set file_url = 'admin-private/' || ltrim(file_url, '/')
 where required_plan = 'admin'
   and file_url not like 'admin-private/%';

-- ---------------------------------------------------------------------------
-- 3) Internal function that detects an admin-only avatar, banner, or item
--    SECURITY DEFINER is required because non-admin users cannot read the
--    underlying admin asset row. The function is not granted directly to
--    users; only the safe view calls it.
-- ---------------------------------------------------------------------------
create or replace function public.is_admin_only_profile_asset(
  p_kind text,
  p_value text
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(p_value, '') <> ''
     and exists (
       select 1
         from public.site_assets a
        where a.required_plan = 'admin'
          and a.enabled
          and a.asset_type = case when p_kind = 'item' then 'item' else p_kind end
          and (a.file_url = p_value or a.name = p_value)
     );
$$;

revoke all on function public.is_admin_only_profile_asset(text, text) from public;
revoke all on function public.is_admin_only_profile_asset(text, text) from anon, authenticated;

comment on function public.is_admin_only_profile_asset(text, text) is
  'Internal: detects whether a profile value references an admin-only asset; visible_profiles uses it for redaction.';

-- ---------------------------------------------------------------------------
-- 4) Public profile view: the same columns with admin-only assets redacted
--
--    The view runs with owner privileges so it can read profiles after direct
--    SELECT is revoked, but visibility is evaluated for the actual caller via
--    auth.uid()/public.is_admin().
-- ---------------------------------------------------------------------------
create or replace view public.visible_profiles
with (security_barrier = true)
as
select
  p.id,
  p.username,
  p.display_name,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('avatar', p.avatar_url)
      then p.avatar_url
    else null
  end as avatar_url,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('banner', p.banner_url)
      then p.banner_url
    else null
  end as banner_url,
  p.custom_status,
  p.bio,
  p.country,
  p.role,
  p.plan,
  p.plan_expires_at,
  p.user_level,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('item', p.equipped_item_id)
      then p.equipped_item_id
    else 'none'
  end as equipped_item_id,
  p.show_online_status,
  p.last_seen_at,
  p.created_at,
  p.updated_at
from public.profiles p;

comment on view public.visible_profiles is
  'Public profile read interface; redacts admin-only avatars, banners, and items from non-admin users.';

revoke all on public.visible_profiles from public;
grant select on public.visible_profiles to anon, authenticated;

-- Prevent REST clients from bypassing the view through the base table, and
-- prevent direct item assignment that bypasses set_profile_asset and its
-- required_plan validation.
revoke select on public.profiles from public, anon, authenticated;
revoke update (equipped_item_id) on public.profiles from authenticated;

-- ---------------------------------------------------------------------------
-- 5) Private admin asset bucket (reserved app path: admin-private/...)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'admin-assets',
  'admin-assets',
  false,
  5242880,
  array['image/png','image/jpeg','image/webp','image/gif','image/svg+xml']
)
on conflict (id) do update
  set public = false,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "admin_assets_read" on storage.objects;
create policy "admin_assets_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_insert" on storage.objects;
create policy "admin_assets_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_update" on storage.objects;
create policy "admin_assets_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin())
  with check (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_delete" on storage.objects;
create policy "admin_assets_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin());

-- Make visible_profiles available to PostgREST immediately after execution.
notify pgrst, 'reload schema';

-- ---------------------------------------------------------------------------
-- 6) Informational verification
--    This does not fail while legacy assets await migration; it reports them.
-- ---------------------------------------------------------------------------
do $$
declare
  v_hidden int;
  v_pending int;
begin
  select count(*) into v_hidden
    from public.site_assets
   where required_plan = 'admin';

  select count(*) into v_pending
    from public.site_assets a
   where a.required_plan = 'admin'
     and (
       not exists (
         select 1 from storage.objects o
          where o.bucket_id = 'admin-assets' and o.name = a.file_url
       )
       or exists (
         select 1 from storage.objects o
          where o.bucket_id = 'site-assets'
            and o.name = regexp_replace(a.file_url, '^admin-private/', '')
       )
     );

  raise notice 'Admin catalog privacy is active: % asset(s) hidden from non-admin users.', v_hidden;
  if v_pending > 0 then
    raise notice '% legacy admin file(s) await migration to private storage via tools/migrate_admin_assets_private.py.', v_pending;
  else
    raise notice 'All admin files are registered with private storage paths.';
  end if;
end;
$$;
