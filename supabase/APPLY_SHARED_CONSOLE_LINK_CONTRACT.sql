-- AddonStation shared PS4/phone console-link compatibility patch
-- Target project only: stbmescmexzcszyyambq
--
-- The production project currently exposes claim_console_link_code and
-- poll_console_link_code, while the shared mobile/PS4 contract requires the
-- exact names claim_console_link and poll_console_link. These wrappers preserve
-- the existing implementation and response JSON without changing code storage,
-- expiry, one-time consumption, or the PS4 client.

begin;

do $contract_check$
begin
  if current_database() is null then
    raise exception 'Unable to identify the target database';
  end if;

  if to_regprocedure('public.create_console_link_code(text,text)') is null then
    raise exception 'Missing required function: public.create_console_link_code(text,text)';
  end if;

  if to_regprocedure('public.claim_console_link_code(text,text,text)') is null then
    raise exception 'Missing source function: public.claim_console_link_code(text,text,text)';
  end if;

  if to_regprocedure('public.poll_console_link_code(text,uuid)') is null then
    raise exception 'Missing source function: public.poll_console_link_code(text,uuid)';
  end if;
end;
$contract_check$;

create or replace function public.claim_console_link(
  p_code text,
  p_access_token text,
  p_refresh_token text
)
returns jsonb
language sql
security invoker
set search_path = public
as $function$
  select public.claim_console_link_code(
    p_code,
    p_access_token,
    p_refresh_token
  )::jsonb;
$function$;

create or replace function public.poll_console_link(
  p_code text,
  p_poll_token uuid
)
returns jsonb
language sql
security invoker
set search_path = public
as $function$
  select public.poll_console_link_code(
    p_code,
    p_poll_token
  )::jsonb;
$function$;

revoke all on function public.claim_console_link(text, text, text) from public;
revoke all on function public.poll_console_link(text, uuid) from public;

grant execute on function public.claim_console_link(text, text, text) to authenticated;
grant execute on function public.poll_console_link(text, uuid) to anon, authenticated;

comment on function public.claim_console_link(text, text, text) is
  'Shared phone contract wrapper. Claims one PS4 code with the signed-in user session.';
comment on function public.poll_console_link(text, uuid) is
  'Shared PS4 contract wrapper. Polls a one-time code without exposing user credentials.';

notify pgrst, 'reload schema';

commit;

-- Read-only verification. Expected result: true / true / true.
select
  to_regprocedure('public.create_console_link_code(text,text)') is not null as has_create,
  to_regprocedure('public.claim_console_link(text,text,text)') is not null as has_claim,
  to_regprocedure('public.poll_console_link(text,uuid)') is not null as has_poll;
