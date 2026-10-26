-- ============================================================================
-- AddonStation Mobile — allow validated profile RPC writes through the guard
--
-- Direct authenticated UPDATE privileges remain limited to safe text columns.
-- Protected username/avatar/banner/item writes continue to require these
-- SECURITY DEFINER RPCs, which validate ownership, plan rank, and admin assets
-- before setting a transaction-local flag consumed by guard_profile_update().
-- ============================================================================

begin;

create or replace function public.guard_profile_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor_role text;
begin
  if auth.uid() is null then
    return new;
  end if;

  if coalesce(current_setting('app.allow_profile_write', true), '') = '1' then
    return new;
  end if;

  select role into v_actor_role
    from public.profiles
   where id = auth.uid();

  if coalesce(v_actor_role, '') = 'admin' then
    return new;
  end if;

  if new.role is distinct from old.role
     or new.plan is distinct from old.plan
     or new.plan_expires_at is distinct from old.plan_expires_at
     or new.user_level is distinct from old.user_level
     or new.username is distinct from old.username
     or new.equipped_item_id is distinct from old.equipped_item_id
     or new.avatar_url is distinct from old.avatar_url
     or new.banner_url is distinct from old.banner_url
  then
    raise exception 'forbidden_profile_column';
  end if;

  return new;
end;
$$;

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

  select * into v_asset
    from public.site_assets
   where enabled
     and asset_type = case when p_kind = 'item' then 'item' else p_kind end
     and (file_url = p_value or name = p_value)
   limit 1;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'asset_not_found');
  end if;

  v_plan := public.effective_plan(v_uid);
  v_is_admin := public.is_admin(v_uid);

  if v_asset.required_plan = 'admin' and not v_is_admin then
    return jsonb_build_object('ok', false, 'message', 'admin_only_asset');
  end if;

  if v_asset.required_plan <> 'admin'
     and not v_is_admin
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
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  if p_username is not null then
    if char_length(p_username) < 3 then
      return jsonb_build_object('ok', false, 'message', 'username_too_short');
    end if;
    if exists (
      select 1
        from public.profiles
       where username = p_username
         and id <> v_uid
    ) then
      return jsonb_build_object('ok', false, 'message', 'username_taken');
    end if;
  end if;

  perform set_config('app.allow_profile_write', '1', true);

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

commit;

notify pgrst, 'reload schema';
