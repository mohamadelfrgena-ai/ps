-- 48_test_account.sql
-- Isolated "2121" test build account: test@gmail.com, email confirmed, so the
-- 2121 pkg signs in at launch (AS_BUILD_2121 in ps4-addon-test/src/main.cpp).
-- The profile row is created by the handle_new_user trigger.
-- Credentials appear ONLY here and in that code block — never in chat. Idempotent.

do $$
declare
  v_id uuid;
begin
  select id into v_id from auth.users where email = 'test@gmail.com';
  if v_id is null then
    insert into auth.users
      (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
       raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    values
      ('00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
       'test@gmail.com', crypt('Test-2121-PS4', gen_salt('bf')), now(),
       '{"provider":"email","providers":["email"]}', '{}', now(), now())
    returning id into v_id;
  end if;
  if not exists (select 1 from auth.identities where user_id = v_id and provider = 'email') then
    insert into auth.identities
      (id, user_id, provider, provider_id, identity_data, last_sign_in_at, created_at, updated_at)
    values
      (gen_random_uuid(), v_id, 'email', v_id::text,
       jsonb_build_object('sub', v_id::text, 'email', 'test@gmail.com', 'email_verified', true),
       now(), now(), now());
  end if;
end $$;
