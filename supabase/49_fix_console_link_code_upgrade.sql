-- ============================================================================
-- AddonStation Mobile - 49) Repair upgraded PlayStation link-code schemas
--
-- Older production installations already had console_link_codes when the
-- secure phone lookup/approval flow was installed. CREATE TABLE IF NOT EXISTS
-- preserved that legacy table, but the upgrade path did not add app_version.
-- Both console_code_lookup() and console_code_approve() read app_version from
-- the table row, so a valid code raised a database error instead of returning a
-- normal result. This focused, idempotent migration adds the missing field and
-- its length bound without modifying existing codes.
-- ============================================================================

begin;

alter table public.console_link_codes
  add column if not exists app_version text;

do $add_console_code_app_version_constraint$
begin
  if not exists (
    select 1
      from pg_constraint
     where conrelid = 'public.console_link_codes'::regclass
       and conname = 'console_link_codes_app_version_length'
  ) then
    alter table public.console_link_codes
      add constraint console_link_codes_app_version_length
      check (app_version is null or char_length(app_version) <= 40);
  end if;
end;
$add_console_code_app_version_constraint$;

comment on column public.console_link_codes.app_version is
  'Optional PlayStation application version shown during code approval.';

-- Make PostgREST refresh its schema cache immediately after an incremental
-- production deployment.
notify pgrst, 'reload schema';

commit;
