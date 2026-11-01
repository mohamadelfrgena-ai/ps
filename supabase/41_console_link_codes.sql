-- ============================================================================
-- AddonStation Mobile - 41) Shared PS4 / phone link-contract verification
--
-- IMPORTANT: the device-code schema and RPCs are owned by the shared
-- PlayStation/phone production project. This file is intentionally read-only.
-- It must never replace or migrate that server contract from the Android repo.
--
-- Expected RPCs:
--   create_console_link_code(text, text)
--   claim_console_link(text, text, text)
--   poll_console_link(text, uuid)
--
-- The phone calls only claim_console_link. The PS4 creates and polls codes.
-- ============================================================================

begin;

DO $verify_shared_console_link_contract$
DECLARE
  v_forbidden_count integer;
BEGIN
  IF to_regprocedure('public.create_console_link_code(text,text)') IS NULL THEN
    RAISE EXCEPTION 'missing shared RPC: create_console_link_code(text,text)';
  END IF;

  IF to_regprocedure('public.claim_console_link(text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'missing shared RPC: claim_console_link(text,text,text)';
  END IF;

  IF to_regprocedure('public.poll_console_link(text,uuid)') IS NULL THEN
    RAISE EXCEPTION 'missing shared RPC: poll_console_link(text,uuid)';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'console_link_codes'
       AND column_name = 'consumed_at'
  ) THEN
    RAISE EXCEPTION 'unexpected console_link_codes.consumed_at column';
  END IF;

  SELECT count(*)::integer
    INTO v_forbidden_count
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public'
     AND p.proname LIKE 'console_code_%';

  IF v_forbidden_count <> 0 THEN
    RAISE EXCEPTION 'unexpected console_code_* RPCs: %', v_forbidden_count;
  END IF;
END;
$verify_shared_console_link_contract$;

rollback;
