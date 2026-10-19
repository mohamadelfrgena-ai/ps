-- ============================================================================
-- AddonStation Mobile — make the owner's attached image the only free avatar
--
-- Canonical Storage object:
--   bucket: site-assets
--   path:   profiles/profiles/onefree.png
--   bytes:  271089
--   SHA-256 of the repository/attached image:
--   6e2e27187e757d8f213d6dd29c6afe9667e960447e752d3ae2c82b66bb33b7a5
--
-- This migration removes obsolete free avatar database records, preserves all
-- paid/admin avatars, repairs legacy profile references, and guarantees that a
-- newly created free account receives the canonical image.
-- ============================================================================

begin;

create unique index if not exists site_assets_file_url_uidx
  on public.site_assets (file_url);

insert into public.site_assets
  (asset_type, name, file_url, required_plan, animated, collection,
   collection_order, sort_order, enabled)
values
  ('avatar', 'Default Free Avatar', 'profiles/profiles/onefree.png', 'free',
   false, 'Profiles', 2, 1, true)
on conflict (file_url) do update
   set asset_type       = excluded.asset_type,
       name             = excluded.name,
       required_plan    = excluded.required_plan,
       animated         = excluded.animated,
       collection       = excluded.collection,
       collection_order = excluded.collection_order,
       sort_order       = excluded.sort_order,
       enabled          = true;

-- Repair profile references while the obsolete rows are still available for
-- exact matching.
update public.profiles p
   set avatar_url = 'profiles/profiles/onefree.png',
       updated_at = now()
 where p.avatar_url is null
    or btrim(p.avatar_url) = ''
    or lower(btrim(p.avatar_url)) = 'none'
    or p.avatar_url = 'profiles/onefree.png'
    or exists (
         select 1
           from public.site_assets a
          where a.asset_type = 'avatar'
            and a.required_plan = 'free'
            and a.file_url <> 'profiles/profiles/onefree.png'
            and (
                 p.avatar_url = a.file_url
              or p.avatar_url = 'profiles/' || a.file_url
              or a.file_url = 'profiles/' || p.avatar_url
            )
       );

-- The owner's canonical image is now the only free avatar record. Storage files
-- are left untouched so this database-only migration cannot remove unrelated
-- archival objects.
delete from public.site_assets
 where asset_type = 'avatar'
   and required_plan = 'free'
   and file_url <> 'profiles/profiles/onefree.png';

alter table public.profiles
  alter column avatar_url set default 'profiles/profiles/onefree.png';

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_username text;
  v_welcome  integer := 50;
begin
  v_username := coalesce(
    nullif(new.raw_user_meta_data->>'username', ''),
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'player_' || substr(new.id::text, 1, 8)
  );

  while exists (select 1 from public.profiles p where p.username = v_username) loop
    v_username := v_username || floor(random() * 10)::integer;
  end loop;

  insert into public.profiles (id, username, display_name, avatar_url)
  values (new.id, v_username, v_username, 'profiles/profiles/onefree.png')
  on conflict (id) do nothing;

  insert into public.ac_wallets (user_id, balance, total_earned)
  values (new.id, v_welcome, v_welcome)
  on conflict (user_id) do nothing;

  insert into public.ac_transactions
    (user_id, amount, kind, reason, request_key, balance_after)
  values
    (new.id, v_welcome, 'welcome', 'Welcome reward',
     'welcome:' || new.id::text, v_welcome)
  on conflict (request_key) do nothing;

  return new;
end;
$$;

do $$
declare
  v_free_count integer;
  v_canonical_count integer;
  v_bad_profiles integer;
  v_default text;
begin
  select count(*) into v_free_count
    from public.site_assets
   where asset_type = 'avatar' and required_plan = 'free';

  select count(*) into v_canonical_count
    from public.site_assets
   where asset_type = 'avatar'
     and required_plan = 'free'
     and enabled
     and file_url = 'profiles/profiles/onefree.png';

  select count(*) into v_bad_profiles
    from public.profiles
   where avatar_url is null
      or btrim(avatar_url) = ''
      or lower(btrim(avatar_url)) = 'none'
      or avatar_url = 'profiles/onefree.png';

  select column_default into v_default
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'profiles'
     and column_name = 'avatar_url';

  if v_free_count <> 1 or v_canonical_count <> 1 then
    raise exception 'Expected one canonical free avatar; free=%, canonical=%',
      v_free_count, v_canonical_count;
  end if;
  if v_bad_profiles <> 0 then
    raise exception 'Found % missing or legacy profile avatar paths', v_bad_profiles;
  end if;
  if v_default is null
     or v_default not like '%profiles/profiles/onefree.png%'
  then
    raise exception 'Unexpected profiles.avatar_url default: %', coalesce(v_default, '(none)');
  end if;
  if position(
       'profiles/profiles/onefree.png'
       in pg_get_functiondef('public.handle_new_user()'::regprocedure)
     ) = 0
  then
    raise exception 'handle_new_user does not assign the canonical free avatar';
  end if;
end;
$$;

commit;

notify pgrst, 'reload schema';
