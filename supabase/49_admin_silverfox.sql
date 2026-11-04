-- ============================================================================
-- 49) Grant the admin role to the owner account (user request 2026-11-04)
-- ============================================================================
-- The app's public.is_admin() reads public.profiles.role = 'admin'.
-- This migration promotes the owner email to admin. Idempotent: re-running
-- keeps the account admin and touches nothing else.
-- NOTE: the profiles row is created by the handle_new_user trigger on signup;
-- if the account has not signed up yet this statement is a no-op and must be
-- re-run after the account exists.

update public.profiles
   set role = 'admin'
 where id in (
       select id
         from auth.users
        where lower(email) = lower('silverfoxyactive67@gmail.com')
   );
