-- ============================================================================
--  AddonStation Mobile — restore readable public profile metadata
--
--  Existing deployments only need this focused, idempotent migration.
--  `visible_profiles` previously called a private helper function. PostgreSQL
--  checks function EXECUTE privileges as the caller even inside an owner-run
--  view, so `select=*` failed while a pruned `select=id` appeared healthy.
--
--  Keep the helper private and evaluate the same redaction directly in the
--  security-barrier view. The view owner may inspect the asset catalog, but
--  callers receive only the already-redacted profile columns.
-- ============================================================================

begin;

revoke all on function public.is_admin_only_profile_asset(text, text) from public;
revoke all on function public.is_admin_only_profile_asset(text, text) from anon, authenticated;

create or replace view public.visible_profiles
with (security_barrier = true)
as
select
  p.id,
  p.username,
  p.display_name,
  case
    when public.is_admin()
      or coalesce(p.avatar_url, '') = ''
      or not exists (
        select 1
          from public.site_assets a
         where a.required_plan = 'admin'
           and a.enabled
           and a.asset_type = 'avatar'
           and (a.file_url = p.avatar_url or a.name = p.avatar_url)
      )
      then p.avatar_url
    else null
  end as avatar_url,
  case
    when public.is_admin()
      or coalesce(p.banner_url, '') = ''
      or not exists (
        select 1
          from public.site_assets a
         where a.required_plan = 'admin'
           and a.enabled
           and a.asset_type = 'banner'
           and (a.file_url = p.banner_url or a.name = p.banner_url)
      )
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
      or coalesce(p.equipped_item_id, '') = ''
      or not exists (
        select 1
          from public.site_assets a
         where a.required_plan = 'admin'
           and a.enabled
           and a.asset_type = 'item'
           and (a.file_url = p.equipped_item_id or a.name = p.equipped_item_id)
      )
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
revoke select on public.profiles from public, anon, authenticated;
revoke update (equipped_item_id) on public.profiles from authenticated;

commit;

notify pgrst, 'reload schema';
