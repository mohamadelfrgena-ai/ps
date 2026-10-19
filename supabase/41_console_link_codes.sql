-- ============================================================================
-- AddonStation Mobile - 41) Secure PlayStation Console Linking
--
-- This migration implements the database half of the secure device-code flow:
--   1. A trusted Edge Function creates a six-character code for the console.
--   2. The signed-in phone looks up the code and explicitly approves it.
--   3. The Edge Function proves possession of the poll secret and consumes the
--      approval.
--   4. The Edge Function creates a separate, restricted console credential and
--      records only an opaque credential reference through console_code_complete.
--
-- Security properties:
--   * console_link_codes is never readable from anon/authenticated REST.
--   * The raw poll secret is returned once and only its SHA-256 hash is stored.
--   * Access tokens and refresh tokens are never accepted or stored by SQL.
--   * Phone lookups are limited to five failed attempts per minute per user.
--   * Approval requires a recent successful lookup by the same signed-in user.
--   * Console credentials must be issued and revoked by a trusted Edge Function.
--     Never put the service-role key in the Android or PlayStation application.
--
-- Deployment:
--   Run this migration first, then deploy the console-session Edge Function.
--   Do not expose the service-role-only RPCs directly to either client.
-- ============================================================================

begin;

create extension if not exists "pgcrypto";

-- Remove the legacy RPC that accepted the phone's reusable session tokens.
-- Deploy the updated Android client and console-session Edge Function as one
-- coordinated release because old clients cannot use the secure contract.
drop function if exists public.claim_console_link_code(text, text, text);

-- ---------------------------------------------------------------------------
-- 1) Linked consoles visible to their owner through console_list()
-- ---------------------------------------------------------------------------
create table if not exists public.linked_consoles (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  device_name       text not null,
  platform          text not null default 'ps4',
  app_version       text,
  created_at        timestamptz not null default now(),
  activated_at      timestamptz,
  last_seen_at      timestamptz,
  revoked_at        timestamptz,
  session_ref       text,
  constraint linked_consoles_device_name_length
    check (char_length(device_name) between 1 and 80),
  constraint linked_consoles_platform_length
    check (char_length(platform) between 2 and 24),
  constraint linked_consoles_app_version_length
    check (app_version is null or char_length(app_version) <= 40),
  constraint linked_consoles_session_ref_length
    check (session_ref is null or char_length(session_ref) between 16 and 200)
);

-- Upgrade an earlier linked_consoles table without replacing user data.
alter table public.linked_consoles
  add column if not exists app_version text,
  add column if not exists activated_at timestamptz,
  add column if not exists last_seen_at timestamptz,
  add column if not exists revoked_at timestamptz,
  add column if not exists session_ref text;

create index if not exists linked_consoles_user_active_idx
  on public.linked_consoles(user_id, created_at desc)
  where revoked_at is null;

create unique index if not exists linked_consoles_session_ref_uidx
  on public.linked_consoles(session_ref)
  where session_ref is not null;

-- ---------------------------------------------------------------------------
-- 2) Private, short-lived device codes
-- ---------------------------------------------------------------------------
create table if not exists public.console_link_codes (
  id                       uuid primary key default gen_random_uuid(),
  code                     text not null unique,
  device_name              text not null,
  platform                 text not null default 'ps4',
  app_version              text,
  poll_secret_hash         text not null,
  request_fingerprint_hash text,
  created_at               timestamptz not null default now(),
  expires_at               timestamptz not null,
  approved_by              uuid references auth.users(id) on delete cascade,
  approved_at              timestamptz,
  exchange_id              uuid,
  exchange_started_at      timestamptz,
  used_at                  timestamptz,
  linked_console_id        uuid references public.linked_consoles(id) on delete set null,
  constraint console_link_codes_code_format
    check (code ~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$'),
  constraint console_link_codes_device_name_length
    check (char_length(device_name) between 1 and 80),
  constraint console_link_codes_platform_length
    check (char_length(platform) between 2 and 24),
  constraint console_link_codes_app_version_length
    check (app_version is null or char_length(app_version) <= 40),
  constraint console_link_codes_expiry_order
    check (expires_at > created_at),
  constraint console_link_codes_approval_pair
    check (
      (approved_by is null and approved_at is null)
      or (approved_by is not null and approved_at is not null)
    ),
  constraint console_link_codes_exchange_pair
    check (
      (exchange_id is null and exchange_started_at is null)
      or (exchange_id is not null and exchange_started_at is not null)
    )
);

-- Upgrade an earlier code table without exposing or replacing valid pending
-- codes. New columns are populated by the secure functions below.
alter table public.console_link_codes
  add column if not exists id uuid default gen_random_uuid(),
  add column if not exists platform text default 'ps4',
  add column if not exists poll_secret_hash text,
  add column if not exists request_fingerprint_hash text,
  add column if not exists approved_by uuid references auth.users(id) on delete cascade,
  add column if not exists approved_at timestamptz,
  add column if not exists exchange_id uuid,
  add column if not exists exchange_started_at timestamptz,
  add column if not exists used_at timestamptz,
  add column if not exists linked_console_id uuid references public.linked_consoles(id) on delete set null;

update public.console_link_codes
   set id = gen_random_uuid()
 where id is null;

alter table public.console_link_codes
  alter column id set default gen_random_uuid(),
  alter column id set not null;

create unique index if not exists console_link_codes_id_uidx
  on public.console_link_codes(id);
create unique index if not exists console_link_codes_code_uidx
  on public.console_link_codes(code);

-- Normalize legacy bytea hashes to lowercase hexadecimal text.
do $normalize_console_hashes$
declare
  v_type text;
begin
  select data_type into v_type
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'console_link_codes'
     and column_name = 'poll_secret_hash';
  if v_type = 'bytea' then
    execute 'alter table public.console_link_codes alter column poll_secret_hash type text using encode(poll_secret_hash, ''hex'')';
  end if;

  select data_type into v_type
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'console_link_codes'
     and column_name = 'request_fingerprint_hash';
  if v_type = 'bytea' then
    execute 'alter table public.console_link_codes alter column request_fingerprint_hash type text using encode(request_fingerprint_hash, ''hex'')';
  end if;
end;
$normalize_console_hashes$;

-- Remove any legacy columns that stored reusable phone credentials. CASCADE is
-- intentional: old polling functions depending on those columns must not stay
-- callable after the secure Edge Function replaces them.
do $remove_legacy_console_tokens$
begin
  if exists (
    select 1
      from information_schema.columns
     where table_schema = 'public'
       and table_name = 'console_link_codes'
       and column_name = 'access_token'
  ) then
    execute 'alter table public.console_link_codes drop column access_token cascade';
  end if;

  if exists (
    select 1
      from information_schema.columns
     where table_schema = 'public'
       and table_name = 'console_link_codes'
       and column_name = 'refresh_token'
  ) then
    execute 'alter table public.console_link_codes drop column refresh_token cascade';
  end if;
end;
$remove_legacy_console_tokens$;

create index if not exists console_link_codes_expiry_idx
  on public.console_link_codes(expires_at);

create index if not exists console_link_codes_fingerprint_idx
  on public.console_link_codes(request_fingerprint_hash, created_at desc)
  where request_fingerprint_hash is not null;

-- ---------------------------------------------------------------------------
-- 3) Private audit/rate-limit log; raw codes and poll secrets are never logged
-- ---------------------------------------------------------------------------
create table if not exists public.console_link_audit (
  id                bigint generated always as identity primary key,
  event_type        text not null,
  actor_user_id     uuid references auth.users(id) on delete set null,
  link_code_id      uuid references public.console_link_codes(id) on delete set null,
  linked_console_id uuid references public.linked_consoles(id) on delete set null,
  created_at        timestamptz not null default now(),
  constraint console_link_audit_event_type
    check (event_type in (
      'code_created',
      'lookup_succeeded',
      'lookup_failed',
      'approval_succeeded',
      'approval_failed',
      'exchange_started',
      'console_activated',
      'console_revoked',
      'console_seen'
    ))
);

create index if not exists console_link_audit_rate_idx
  on public.console_link_audit(actor_user_id, event_type, created_at desc)
  where actor_user_id is not null;

create index if not exists console_link_audit_console_idx
  on public.console_link_audit(linked_console_id, created_at desc)
  where linked_console_id is not null;

-- ---------------------------------------------------------------------------
-- 4) RLS and table privileges
--
-- All client operations use narrowly scoped RPCs. Even a direct REST request
-- cannot list codes, hashes, fingerprints, audit rows, or session references.
-- ---------------------------------------------------------------------------
alter table public.linked_consoles enable row level security;
alter table public.console_link_codes enable row level security;
alter table public.console_link_audit enable row level security;

drop policy if exists linked_consoles_select_own on public.linked_consoles;
create policy linked_consoles_select_own on public.linked_consoles
  for select to authenticated
  using (user_id = auth.uid() and revoked_at is null);

revoke all on table public.linked_consoles from public, anon, authenticated;
revoke all on table public.console_link_codes from public, anon, authenticated;
revoke all on table public.console_link_audit from public, anon, authenticated;

grant all on table public.linked_consoles to service_role;
grant all on table public.console_link_codes to service_role;
grant all on table public.console_link_audit to service_role;

-- ---------------------------------------------------------------------------
-- 5) Internal helpers
-- ---------------------------------------------------------------------------
create or replace function public.console_private_normalize_code(p_code text)
returns text
language sql
immutable
set search_path = public
as $$
  select regexp_replace(upper(coalesce(p_code, '')), '[[:space:]-]+', '', 'g');
$$;

create or replace function public.console_private_random_code()
returns text
language plpgsql
volatile
set search_path = public, extensions
as $$
declare
  v_alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  v_bytes bytea := gen_random_bytes(6);
  v_code text := '';
  v_index integer;
begin
  for v_index in 0..5 loop
    v_code := v_code || substr(
      v_alphabet,
      (get_byte(v_bytes, v_index) % 32) + 1,
      1
    );
  end loop;
  return v_code;
end;
$$;

revoke all on function public.console_private_normalize_code(text) from public, anon, authenticated;
revoke all on function public.console_private_random_code() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 6) Edge Function: create a code
--
-- p_request_fingerprint must be an opaque, server-generated HMAC of the remote
-- address and installation identifier. Never pass a raw IP address here.
-- ---------------------------------------------------------------------------
create or replace function public.console_code_create(
  p_device_name text default 'PlayStation 4',
  p_app_version text default null,
  p_platform text default 'ps4',
  p_request_fingerprint text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_device_name text := nullif(btrim(p_device_name), '');
  v_app_version text := nullif(btrim(p_app_version), '');
  v_platform text := lower(coalesce(nullif(btrim(p_platform), ''), 'ps4'));
  v_fingerprint_hash text;
  v_code text;
  v_poll_secret text;
  v_code_id uuid;
  v_expires_at timestamptz := now() + interval '10 minutes';
  v_try integer;
begin
  if v_device_name is null or char_length(v_device_name) > 80 then
    return jsonb_build_object('ok', false, 'message', 'invalid_device_name');
  end if;
  if v_app_version is not null and char_length(v_app_version) > 40 then
    return jsonb_build_object('ok', false, 'message', 'invalid_app_version');
  end if;
  if v_platform !~ '^[a-z0-9_-]{2,24}$' then
    return jsonb_build_object('ok', false, 'message', 'invalid_platform');
  end if;

  if nullif(btrim(p_request_fingerprint), '') is not null then
    v_fingerprint_hash := encode(
      digest(convert_to(btrim(p_request_fingerprint), 'UTF8'), 'sha256'),
      'hex'
    );

    if (
      select count(*)
        from public.console_link_codes c
       where c.request_fingerprint_hash = v_fingerprint_hash
         and c.created_at > now() - interval '10 minutes'
    ) >= 5 then
      return jsonb_build_object('ok', false, 'message', 'too_many_attempts');
    end if;
  end if;

  -- A global ceiling protects the database if the public Edge endpoint is
  -- attacked. Per-client limiting must also be enforced by the Edge Function.
  if (
    select count(*)
      from public.console_link_codes c
     where c.created_at > now() - interval '1 minute'
  ) >= 1000 then
    return jsonb_build_object('ok', false, 'message', 'service_busy');
  end if;

  v_poll_secret := encode(gen_random_bytes(32), 'hex');

  for v_try in 1..20 loop
    v_code := public.console_private_random_code();
    begin
      insert into public.console_link_codes (
        code,
        device_name,
        platform,
        app_version,
        poll_secret_hash,
        request_fingerprint_hash,
        expires_at
      ) values (
        v_code,
        v_device_name,
        v_platform,
        v_app_version,
        encode(digest(convert_to(v_poll_secret, 'UTF8'), 'sha256'), 'hex'),
        v_fingerprint_hash,
        v_expires_at
      )
      returning id into v_code_id;
      exit;
    exception when unique_violation then
      if v_try = 20 then
        return jsonb_build_object('ok', false, 'message', 'code_generation_failed');
      end if;
    end;
  end loop;

  insert into public.console_link_audit(event_type, link_code_id)
  values ('code_created', v_code_id);

  return jsonb_build_object(
    'ok', true,
    'code', v_code,
    'expires_at', v_expires_at,
    'poll_secret', v_poll_secret
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) Phone: look up a code before showing the approval sheet
-- ---------------------------------------------------------------------------
create or replace function public.console_code_lookup(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_code text := public.console_private_normalize_code(p_code);
  v_row public.console_link_codes%rowtype;
  v_failed_attempts integer;
begin
  if v_user_id is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select count(*)::integer
    into v_failed_attempts
    from public.console_link_audit a
   where a.actor_user_id = v_user_id
     and a.event_type in ('lookup_failed', 'approval_failed')
     and a.created_at > now() - interval '1 minute';

  if v_failed_attempts >= 5 then
    return jsonb_build_object('ok', false, 'message', 'too_many_attempts');
  end if;

  if v_code !~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$' then
    insert into public.console_link_audit(event_type, actor_user_id)
    values ('lookup_failed', v_user_id);
    return jsonb_build_object('ok', false, 'message', 'invalid_code');
  end if;

  select *
    into v_row
    from public.console_link_codes c
   where c.code = v_code;

  if not found then
    insert into public.console_link_audit(event_type, actor_user_id)
    values ('lookup_failed', v_user_id);
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;

  if v_row.expires_at <= now() then
    insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
    values ('lookup_failed', v_user_id, v_row.id);
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;

  if v_row.used_at is not null
     or (v_row.approved_by is not null and v_row.approved_by <> v_user_id) then
    insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
    values ('lookup_failed', v_user_id, v_row.id);
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;

  insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
  values ('lookup_succeeded', v_user_id, v_row.id);

  return jsonb_build_object(
    'ok', true,
    'message', 'code_found',
    'code', v_row.code,
    'device_name', v_row.device_name,
    'platform', v_row.platform,
    'app_version', v_row.app_version,
    'requested_at', v_row.created_at,
    'expires_at', v_row.expires_at
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 8) Phone: explicitly approve the code
-- ---------------------------------------------------------------------------
create or replace function public.console_code_approve(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_code text := public.console_private_normalize_code(p_code);
  v_row public.console_link_codes%rowtype;
  v_link_id uuid;
  v_failed_attempts integer;
begin
  if v_user_id is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select count(*)::integer
    into v_failed_attempts
    from public.console_link_audit a
   where a.actor_user_id = v_user_id
     and a.event_type in ('lookup_failed', 'approval_failed')
     and a.created_at > now() - interval '1 minute';

  if v_failed_attempts >= 5 then
    return jsonb_build_object('ok', false, 'message', 'too_many_attempts');
  end if;

  if v_code !~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$' then
    insert into public.console_link_audit(event_type, actor_user_id)
    values ('approval_failed', v_user_id);
    return jsonb_build_object('ok', false, 'message', 'invalid_code');
  end if;

  select *
    into v_row
    from public.console_link_codes c
   where c.code = v_code
   for update;

  if not found then
    insert into public.console_link_audit(event_type, actor_user_id)
    values ('approval_failed', v_user_id);
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;

  if v_row.expires_at <= now() then
    insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
    values ('approval_failed', v_user_id, v_row.id);
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;

  if v_row.used_at is not null
     or (v_row.approved_by is not null and v_row.approved_by <> v_user_id) then
    insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
    values ('approval_failed', v_user_id, v_row.id);
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;

  -- Approval is idempotent for the same user and the same active link.
  if v_row.approved_by = v_user_id and v_row.linked_console_id is not null then
    select l.id
      into v_link_id
      from public.linked_consoles l
     where l.id = v_row.linked_console_id
       and l.user_id = v_user_id
       and l.revoked_at is null;

    if v_link_id is not null then
      return jsonb_build_object(
        'ok', true,
        'message', 'approved',
        'platform', v_row.platform,
        'device_name', v_row.device_name
      );
    end if;
  end if;

  -- A direct approve call is not accepted: the same user must have completed a
  -- successful lookup recently, which is what drives the confirmation sheet.
  if not exists (
    select 1
      from public.console_link_audit a
     where a.actor_user_id = v_user_id
       and a.link_code_id = v_row.id
       and a.event_type = 'lookup_succeeded'
       and a.created_at > now() - interval '10 minutes'
  ) then
    insert into public.console_link_audit(event_type, actor_user_id, link_code_id)
    values ('approval_failed', v_user_id, v_row.id);
    return jsonb_build_object('ok', false, 'message', 'lookup_required');
  end if;

  insert into public.linked_consoles (
    user_id,
    device_name,
    platform,
    app_version
  ) values (
    v_user_id,
    v_row.device_name,
    v_row.platform,
    v_row.app_version
  )
  returning id into v_link_id;

  update public.console_link_codes
     set approved_by = v_user_id,
         approved_at = now(),
         linked_console_id = v_link_id
   where id = v_row.id;

  insert into public.console_link_audit(
    event_type,
    actor_user_id,
    link_code_id,
    linked_console_id
  ) values (
    'approval_succeeded',
    v_user_id,
    v_row.id,
    v_link_id
  );

  return jsonb_build_object(
    'ok', true,
    'message', 'approved',
    'platform', v_row.platform,
    'device_name', v_row.device_name
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 9) Edge Function: consume an approved code using the poll secret
--
-- This function does not mint or return a Supabase session. The Edge Function
-- must create a separate restricted console credential. The five-minute
-- exchange window makes a network retry safe without exposing the poll secret.
-- ---------------------------------------------------------------------------
create or replace function public.console_code_consume(
  p_code text,
  p_poll_secret text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_code text := public.console_private_normalize_code(p_code);
  v_secret_hash text;
  v_row public.console_link_codes%rowtype;
  v_exchange_id uuid;
begin
  if v_code !~ '^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$'
     or nullif(p_poll_secret, '') is null
     or char_length(p_poll_secret) > 256 then
    return jsonb_build_object('ok', false, 'status', 'invalid');
  end if;

  v_secret_hash := encode(
    digest(convert_to(p_poll_secret, 'UTF8'), 'sha256'),
    'hex'
  );

  select *
    into v_row
    from public.console_link_codes c
   where c.code = v_code
   for update;

  if not found or v_row.poll_secret_hash <> v_secret_hash then
    return jsonb_build_object('ok', false, 'status', 'invalid');
  end if;

  if v_row.used_at is not null then
    return jsonb_build_object('ok', false, 'status', 'used');
  end if;

  if v_row.expires_at <= now()
     and (v_row.exchange_started_at is null
          or v_row.exchange_started_at <= now() - interval '5 minutes') then
    return jsonb_build_object('ok', false, 'status', 'expired');
  end if;

  if v_row.approved_by is null or v_row.linked_console_id is null then
    return jsonb_build_object('ok', true, 'status', 'pending');
  end if;

  if not exists (
    select 1
      from public.linked_consoles l
     where l.id = v_row.linked_console_id
       and l.user_id = v_row.approved_by
       and l.revoked_at is null
  ) then
    return jsonb_build_object('ok', false, 'status', 'revoked');
  end if;

  if v_row.exchange_id is null
     or v_row.exchange_started_at <= now() - interval '5 minutes' then
    v_exchange_id := gen_random_uuid();
    update public.console_link_codes
       set exchange_id = v_exchange_id,
           exchange_started_at = now()
     where id = v_row.id;

    insert into public.console_link_audit(
      event_type,
      actor_user_id,
      link_code_id,
      linked_console_id
    ) values (
      'exchange_started',
      v_row.approved_by,
      v_row.id,
      v_row.linked_console_id
    );
  else
    v_exchange_id := v_row.exchange_id;
  end if;

  return jsonb_build_object(
    'ok', true,
    'status', 'approved',
    'exchange_id', v_exchange_id,
    'user_id', v_row.approved_by,
    'linked_console_id', v_row.linked_console_id,
    'device_name', v_row.device_name,
    'platform', v_row.platform,
    'app_version', v_row.app_version
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 10) Edge Function: finish credential issuance
--
-- p_session_ref is an opaque identifier only. It must not contain an access
-- token, refresh token, authorization header, or other reusable credential.
-- ---------------------------------------------------------------------------
create or replace function public.console_code_complete(
  p_exchange_id uuid,
  p_linked_console_id uuid,
  p_session_ref text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.console_link_codes%rowtype;
  v_session_ref text := nullif(btrim(p_session_ref), '');
begin
  if p_exchange_id is null
     or p_linked_console_id is null
     or v_session_ref is null
     or char_length(v_session_ref) not between 16 and 200 then
    return jsonb_build_object('ok', false, 'message', 'invalid_exchange');
  end if;

  select *
    into v_row
    from public.console_link_codes c
   where c.exchange_id = p_exchange_id
     and c.linked_console_id = p_linked_console_id
   for update;

  if not found
     or v_row.used_at is not null
     or v_row.exchange_started_at <= now() - interval '5 minutes' then
    return jsonb_build_object('ok', false, 'message', 'invalid_exchange');
  end if;

  update public.linked_consoles
     set session_ref = v_session_ref,
         activated_at = now(),
         last_seen_at = now()
   where id = p_linked_console_id
     and user_id = v_row.approved_by
     and revoked_at is null;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'console_revoked');
  end if;

  update public.console_link_codes
     set used_at = now()
   where id = v_row.id;

  insert into public.console_link_audit(
    event_type,
    actor_user_id,
    link_code_id,
    linked_console_id
  ) values (
    'console_activated',
    v_row.approved_by,
    v_row.id,
    p_linked_console_id
  );

  return jsonb_build_object('ok', true, 'message', 'activated');
end;
$$;

-- ---------------------------------------------------------------------------
-- 11) Phone: list and revoke consoles
--
-- Revocation is durable in SQL. The Edge Function must reject the session_ref
-- immediately and revoke/delete its corresponding external credential.
-- ---------------------------------------------------------------------------
create or replace function public.console_list()
returns table (
  id uuid,
  device_name text,
  platform text,
  app_version text,
  created_at timestamptz,
  last_seen_at timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
  select
    l.id,
    l.device_name,
    l.platform,
    l.app_version,
    l.created_at,
    l.last_seen_at
  from public.linked_consoles l
  where l.user_id = auth.uid()
    and l.revoked_at is null
  order by l.created_at desc;
$$;

create or replace function public.console_unlink(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_link_id uuid;
begin
  if v_user_id is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  update public.linked_consoles
     set revoked_at = now()
   where id = p_id
     and user_id = v_user_id
     and revoked_at is null
  returning id into v_link_id;

  if v_link_id is null then
    return jsonb_build_object('ok', false, 'message', 'console_not_found');
  end if;

  insert into public.console_link_audit(
    event_type,
    actor_user_id,
    linked_console_id
  ) values (
    'console_revoked',
    v_user_id,
    v_link_id
  );

  return jsonb_build_object('ok', true, 'message', 'revoked');
end;
$$;

create or replace function public.console_unlink_all()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_count integer;
begin
  if v_user_id is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  with revoked as (
    update public.linked_consoles
       set revoked_at = now()
     where user_id = v_user_id
       and revoked_at is null
    returning id
  ), audited as (
    insert into public.console_link_audit(
      event_type,
      actor_user_id,
      linked_console_id
    )
    select 'console_revoked', v_user_id, id
      from revoked
    returning 1
  )
  select count(*)::integer into v_count from audited;

  return jsonb_build_object(
    'ok', true,
    'message', 'revoked',
    'count', coalesce(v_count, 0)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 12) Edge Function: heartbeat and credential-status check
-- ---------------------------------------------------------------------------
create or replace function public.console_session_touch(
  p_linked_console_id uuid,
  p_session_ref text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link_id uuid;
  v_user_id uuid;
begin
  if p_linked_console_id is null or nullif(btrim(p_session_ref), '') is null then
    return jsonb_build_object('ok', false, 'message', 'invalid_session');
  end if;

  update public.linked_consoles
     set last_seen_at = now()
   where id = p_linked_console_id
     and session_ref = p_session_ref
     and activated_at is not null
     and revoked_at is null
  returning id, user_id into v_link_id, v_user_id;

  if v_link_id is null then
    return jsonb_build_object('ok', false, 'message', 'session_revoked');
  end if;

  return jsonb_build_object(
    'ok', true,
    'message', 'active',
    'user_id', v_user_id
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 13) Maintenance cleanup (run from a trusted scheduled Edge Function)
-- ---------------------------------------------------------------------------
create or replace function public.console_link_cleanup(
  p_code_retention interval default interval '24 hours',
  p_audit_retention interval default interval '90 days'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_codes integer;
  v_audit integer;
begin
  delete from public.console_link_codes
   where expires_at < now() - greatest(p_code_retention, interval '1 hour');
  get diagnostics v_codes = row_count;

  delete from public.console_link_audit
   where created_at < now() - greatest(p_audit_retention, interval '7 days');
  get diagnostics v_audit = row_count;

  return jsonb_build_object(
    'ok', true,
    'deleted_codes', v_codes,
    'deleted_audit_rows', v_audit
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 14) Function privileges
-- ---------------------------------------------------------------------------
revoke all on function public.console_code_create(text, text, text, text)
  from public, anon, authenticated;
revoke all on function public.console_code_consume(text, text)
  from public, anon, authenticated;
revoke all on function public.console_code_complete(uuid, uuid, text)
  from public, anon, authenticated;
revoke all on function public.console_session_touch(uuid, text)
  from public, anon, authenticated;
revoke all on function public.console_link_cleanup(interval, interval)
  from public, anon, authenticated;

grant execute on function public.console_code_create(text, text, text, text)
  to service_role;
grant execute on function public.console_code_consume(text, text)
  to service_role;
grant execute on function public.console_code_complete(uuid, uuid, text)
  to service_role;
grant execute on function public.console_session_touch(uuid, text)
  to service_role;
grant execute on function public.console_link_cleanup(interval, interval)
  to service_role;

revoke all on function public.console_code_lookup(text) from public, anon;
revoke all on function public.console_code_approve(text) from public, anon;
revoke all on function public.console_list() from public, anon;
revoke all on function public.console_unlink(uuid) from public, anon;
revoke all on function public.console_unlink_all() from public, anon;

grant execute on function public.console_code_lookup(text) to authenticated;
grant execute on function public.console_code_approve(text) to authenticated;
grant execute on function public.console_list() to authenticated;
grant execute on function public.console_unlink(uuid) to authenticated;
grant execute on function public.console_unlink_all() to authenticated;

comment on table public.console_link_codes is
  'Private one-time PlayStation link codes. Clients must use RPCs; raw poll secrets and session tokens are never stored.';
comment on table public.linked_consoles is
  'User-owned console links. session_ref is an opaque Edge Function credential reference, never a token.';
comment on function public.console_code_create(text, text, text, text) is
  'Service role only: creates a short-lived code and returns its raw poll secret once.';
comment on function public.console_code_lookup(text) is
  'Authenticated phone: rate-limited lookup before explicit approval.';
comment on function public.console_code_approve(text) is
  'Authenticated phone: approves a recently looked-up console code.';
comment on function public.console_code_consume(text, text) is
  'Service role only: validates the poll secret and starts a retry-safe credential exchange.';
comment on function public.console_code_complete(uuid, uuid, text) is
  'Service role only: activates a linked console using an opaque credential reference.';

commit;
