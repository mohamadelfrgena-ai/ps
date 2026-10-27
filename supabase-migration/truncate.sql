begin;
set local session_replication_role = replica;
truncate table public.site_assets, public.sticker_packs, public.console_link_codes, public.console_link_audit, public.push_config, public.push_devices, public.push_prefs, public.push_queue cascade;
commit;
