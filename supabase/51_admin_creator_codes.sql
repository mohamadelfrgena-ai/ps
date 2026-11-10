-- ============================================================================
-- AddonStation Mobile — 51) Admin code exports and creator campaign codes
--
-- Creator codes are visibly distinguished by the "CR" prefix, stay eight
-- characters long for the existing wallet input, and may be redeemed once by
-- every signed-in user. The unique (code, user_id) index remains the final
-- one-redemption-per-user guarantee.
--
-- Safe to run repeatedly after 14_redeem_codes.sql and 19_admin_panel_guard.sql.
-- ============================================================================

alter table public.ac_redeem_codes
  add column if not exists code_kind text not null default 'single';

alter table public.ac_redeem_codes
  add column if not exists channel_name text;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conrelid = 'public.ac_redeem_codes'::regclass
      and conname = 'ac_redeem_codes_kind_check'
  ) then
    alter table public.ac_redeem_codes
      add constraint ac_redeem_codes_kind_check
      check (code_kind in ('single', 'creator'));
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conrelid = 'public.ac_redeem_codes'::regclass
      and conname = 'ac_redeem_codes_creator_channel_check'
  ) then
    alter table public.ac_redeem_codes
      add constraint ac_redeem_codes_creator_channel_check
      check (
        code_kind <> 'creator'
        or (
          channel_name is not null
          and char_length(btrim(channel_name)) between 2 and 80
        )
      );
  end if;
end $$;

create index if not exists ac_redeem_codes_kind_created_idx
  on public.ac_redeem_codes(code_kind, created_at desc);

comment on column public.ac_redeem_codes.code_kind is
  'single = one total redemption; creator = one redemption per signed-in user';
comment on column public.ac_redeem_codes.channel_name is
  'Display name of the creator channel associated with a creator campaign code';

create or replace function public.ac_random_creator_code()
returns text
language plpgsql volatile
as $$
declare
  v_chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_code text := 'CR';
  i int;
begin
  for i in 1..6 loop
    v_code := v_code || substr(v_chars, 1 + floor(random() * length(v_chars))::int, 1);
  end loop;
  return v_code;
end $$;

create or replace function public.ac_admin_create_creator_code(
  p_channel_name text,
  p_amount int
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_channel text := nullif(regexp_replace(btrim(coalesce(p_channel_name, '')), '[[:space:]]+', ' ', 'g'), '');
  v_code text;
  v_candidate text;
  v_inserted int := 0;
  i int;
begin
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;

  if v_channel is null or char_length(v_channel) not between 2 and 80 then
    return jsonb_build_object('ok', false, 'message', 'bad_channel_name');
  end if;

  if p_amount is null or p_amount not between 100 and 10000000 then
    return jsonb_build_object('ok', false, 'message', 'bad_amount');
  end if;

  for i in 1..30 loop
    v_candidate := public.ac_random_creator_code();
    insert into public.ac_redeem_codes(
      code,
      amount,
      max_uses,
      note,
      created_by,
      code_kind,
      channel_name
    )
    values (
      v_candidate,
      p_amount,
      2147483647,
      'creator:' || v_channel,
      auth.uid(),
      'creator',
      v_channel
    )
    on conflict do nothing;

    get diagnostics v_inserted = row_count;
    if v_inserted = 1 then
      v_code := v_candidate;
      exit;
    end if;
  end loop;

  if v_code is null then
    raise exception 'creator_code_generation_failed';
  end if;

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (
    auth.uid(),
    'ac_create_creator_code',
    'ac_redeem_codes',
    v_code,
    jsonb_build_object(
      'channel_name', v_channel,
      'amount', p_amount,
      'code_kind', 'creator'
    )
  );

  return jsonb_build_object(
    'ok', true,
    'code', v_code,
    'amount', p_amount,
    'channel_name', v_channel,
    'code_kind', 'creator'
  );
end $$;

revoke all on function public.ac_random_creator_code() from public, anon, authenticated;
revoke all on function public.ac_admin_create_creator_code(text, int) from public, anon;
grant execute on function public.ac_admin_create_creator_code(text, int) to authenticated;
