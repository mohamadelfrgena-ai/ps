-- AddonStation focused console-link claim schema repair
-- Target project only: stbmescmexzcszyyambq
--
-- The live claim_console_link_code function writes claimed_by and profile, but
-- those two columns were absent from console_link_codes. That made every valid
-- authenticated phone claim fail with PostgreSQL error 42703 while PlayStation
-- remained in the waiting state. This migration adds only the columns required
-- by the already-live function. It does not rename an RPC, alter code expiry,
-- add consumed_at, or change one-time polling behavior.

begin;

do $contract_check$
begin
  if to_regclass('public.console_link_codes') is null then
    raise exception 'Missing required table: public.console_link_codes';
  end if;

  if to_regclass('public.profiles') is null then
    raise exception 'Missing required table: public.profiles';
  end if;

  if to_regprocedure('public.create_console_link_code(text,text)') is null then
    raise exception 'Missing required function: public.create_console_link_code(text,text)';
  end if;

  if to_regprocedure('public.claim_console_link(text,text,text)') is null then
    raise exception 'Missing required function: public.claim_console_link(text,text,text)';
  end if;

  if to_regprocedure('public.poll_console_link(text,uuid)') is null then
    raise exception 'Missing required function: public.poll_console_link(text,uuid)';
  end if;

  if to_regprocedure('public.claim_console_link_code(text,text,text)') is null then
    raise exception 'Missing source function: public.claim_console_link_code(text,text,text)';
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'consumed_at'
  ) then
    raise exception 'Unexpected incompatible column: public.console_link_codes.consumed_at';
  end if;
end;
$contract_check$;

alter table public.console_link_codes
  add column if not exists claimed_by uuid
    references public.profiles(id) on delete cascade,
  add column if not exists profile jsonb;

create index if not exists console_link_codes_claimed_by_idx
  on public.console_link_codes(claimed_by)
  where claimed_by is not null;

comment on column public.console_link_codes.claimed_by is
  'Authenticated profile that claimed the short-lived PlayStation code.';
comment on column public.console_link_codes.profile is
  'Minimal profile snapshot returned once to the polling PlayStation client.';

do $verify_repair$
begin
  if not exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'claimed_by'
      and data_type = 'uuid'
  ) then
    raise exception 'Repair verification failed: claimed_by uuid is missing';
  end if;

  if not exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'profile'
      and data_type = 'jsonb'
  ) then
    raise exception 'Repair verification failed: profile jsonb is missing';
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'consumed_at'
  ) then
    raise exception 'Repair verification failed: consumed_at must remain absent';
  end if;
end;
$verify_repair$;

notify pgrst, 'reload schema';

commit;

-- Read-only verification. Every result must be true.
select
  exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'claimed_by'
      and data_type = 'uuid'
  ) as has_claimed_by,
  exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'profile'
      and data_type = 'jsonb'
  ) as has_profile,
  not exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'console_link_codes'
      and column_name = 'consumed_at'
  ) as has_no_consumed_at;
