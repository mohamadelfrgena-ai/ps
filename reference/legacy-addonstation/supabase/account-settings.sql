-- Account settings support
-- Run once in Supabase SQL Editor.
-- The email address remains in auth.users and is never exposed through profiles.

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if auth.uid() is null then
    raise exception 'Login required.';
  end if;

  -- auth.users cascades to the public profile and the user's related records.
  delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
