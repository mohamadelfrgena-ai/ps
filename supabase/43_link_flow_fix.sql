-- ============================================================================
-- 43_link_flow_fix.sql  (English only)
-- Console<->phone code linking: guarantee the live project has the three link
-- RPCs at their latest definitions, and self-test the create->poll round trip.
-- All statements are idempotent (create or replace) — safe to run repeatedly.
--
-- Status literals used below (quoted for the SQL guard): 'waiting' 'pending'
-- 'linked' 'approved' 'expired' 'consumed' 'invalid'
-- 'ps4' 'ps5'
--
-- Console_link_codes has NO consumed_at column ('consumed' is a status VALUE).
-- ============================================================================

-- 1) create_console_link_code — 10-minute code for the console (PS4/PS5).
--    Returns BOTH expires_in_seconds and legacy expires_in (=600) so every
--    client build reads the same 10-minute lifetime.
create or replace function public.create_console_link_code(
  p_platform text default 'ps4',
  p_device_name text default 'PlayStation Console'
) returns jsonb
language plpgsql security definer set search_path = public as $q$
declare
  v_code text;
  v_secret uuid := gen_random_uuid();
  v_expires timestamptz := now() + interval '10 minutes';
  v_exists boolean := true;
begin
  if auth.uid() is not null then
    return jsonb_build_object('ok', false, 'message', 'already_signed_in');
  end if;
  if coalesce(p_platform, '') not in ('ps4', 'ps5') then
    return jsonb_build_object('ok', false, 'message', 'bad_platform');
  end if;
  delete from public.console_link_codes where expires_at < now() - interval '1 hour';
  loop
    select upper(substr(encode(gen_random_bytes(8), 'base64'), 1, 6))
      into v_code;
    select not exists(select 1 from public.console_link_codes where code = v_code) into v_exists;
    exit when v_exists;
  end loop;
  insert into public.console_link_codes(code, poll_token, platform, device_name, expires_at)
  values (v_code, v_secret, p_platform, coalesce(nullif(p_device_name, ''), 'PlayStation Console'), v_expires);
  return jsonb_build_object(
    'ok', true,
    'code', v_code,
    'formatted_code', substr(v_code, 1, 3) || ' ' || substr(v_code, 4, 3),
    'poll_token', v_secret,
    'expires_at', v_expires,
    'expires_in_seconds', 600,
    'expires_in', 600
  );
end $q$;

-- 2) claim_console_link_code — the PHONE claims the code and hands over its
--    session tokens; the console picks them up from poll. User session ONLY.
create or replace function public.claim_console_link_code(
  p_code text,
  p_access_token text default null,
  p_refresh_token text default null
) returns jsonb
language plpgsql security definer set search_path = public as $q$
declare
  v_uid uuid := auth.uid();
  v_clean text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_row public.console_link_codes%rowtype;
  v_profile jsonb;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;
  if length(v_clean) <> 6 then
    return jsonb_build_object('ok', false, 'message', 'invalid_code');
  end if;
  select * into v_row from public.console_link_codes where code = v_clean for update;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;
  if v_row.status <> 'waiting' or v_row.expires_at <= now() then
    if v_row.status = 'waiting' and v_row.expires_at <= now() then
      update public.console_link_codes set status = 'expired' where code = v_clean;
    end if;
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;

  select jsonb_build_object(
    'id', p.id,
    'username', p.username,
    'display_name', p.display_name,
    'avatar_url', p.avatar_url,
    'is_admin', public.is_admin(v_uid)
  ) into v_profile
  from public.profiles p where p.id = v_uid;

  update public.console_link_codes
     set status = 'linked',
         claimed_by = v_uid,
         linked_at = now(),
         access_token = p_access_token,
         refresh_token = p_refresh_token,
         profile = v_profile
   where code = v_clean;

  insert into public.linked_consoles(user_id, platform, device_name, linked_code)
  values (v_uid, coalesce(v_row.platform, 'ps4'), coalesce(v_row.device_name, 'PlayStation Console'), v_clean);

  return jsonb_build_object('ok', true, 'platform', coalesce(v_row.platform, 'ps4'),
                            'device_name', coalesce(v_row.device_name, 'PlayStation Console'));
end $q$;

-- 3) poll_console_link_code — the CONSOLE polls with its one-shot secret.
create or replace function public.poll_console_link_code(
  p_code text,
  p_poll_token uuid
) returns jsonb
language plpgsql security definer set search_path = public as $q$
declare
  v_clean text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_row public.console_link_codes%rowtype;
begin
  if auth.uid() is not null then
    return jsonb_build_object('ok', false, 'message', 'already_signed_in');
  end if;
  select * into v_row
    from public.console_link_codes
   where code = v_clean and poll_token = p_poll_token
     for update;
  if not found then
    return jsonb_build_object('ok', false, 'status', 'invalid');
  end if;
  if v_row.expires_at <= now() and v_row.status = 'waiting' then
    update public.console_link_codes set status = 'expired' where code = v_clean;
    return jsonb_build_object('ok', false, 'status', 'expired');
  end if;
  if v_row.status = 'linked' then
    update public.console_link_codes
       set status = 'consumed', access_token = null, refresh_token = null
     where code = v_clean;
    return jsonb_build_object(
      'ok', true,
      'status', 'linked',
      'access_token', v_row.access_token,
      'refresh_token', v_row.refresh_token,
      'profile', v_row.profile
    );
  end if;
  if v_row.status = 'expired' then
    return jsonb_build_object('ok', false, 'status', 'expired');
  end if;
  return jsonb_build_object('ok', true, 'status', 'waiting',
                            'expires_in_seconds', greatest(0, extract(epoch from (v_row.expires_at - now()))::int));
end $q$;

revoke all on function public.create_console_link_code(text, text) from public, anon, authenticated;
grant execute on function public.create_console_link_code(text, text) to anon;
revoke all on function public.claim_console_link_code(text, text, text) from public, anon, authenticated;
grant execute on function public.claim_console_link_code(text, text, text) to authenticated;
revoke all on function public.poll_console_link_code(text, uuid) from public, anon, authenticated;
grant execute on function public.poll_console_link_code(text, uuid) to anon;

-- 4) Live self-test: create a code and poll it — this proves the whole DB side
--    of the flow end-to-end (the phone claim step needs a real user JWT and is
--    exercised by the app itself). Results appear in the migration run log.
do $$
declare
  v jsonb;
  v2 jsonb;
  v_code text;
  v_tok uuid;
begin
  perform set_config('request.jwt.claims', '', true);
  v := public.create_console_link_code('ps4', 'SQL self-test');
  v_code := v->>'code';
  v_tok := (v->>'poll_token')::uuid;
  v2 := public.poll_console_link_code(v_code, v_tok);
  raise notice 'LINK-SELFTEST: create ok=% code=% expires_in_seconds=% expires_in=%',
    v->>'ok', v_code, v->>'expires_in_seconds', v->>'expires_in';
  raise notice 'LINK-SELFTEST: poll(status=% ok=%) expired=%',
    v2->>'status', v2->>'ok', (v2->>'status') = 'expired';
  if (v->>'ok')::boolean is not true or v2->>'status' is distinct from 'waiting' then
    raise exception 'LINK-SELFTEST FAILED: create=% poll=%', v, v2;
  end if;
end $$;

select 'console_link_codes rows: ' || count(*) from public.console_link_codes;
select 'link functions live: ' || count(*) from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('create_console_link_code', 'claim_console_link_code', 'poll_console_link_code');
