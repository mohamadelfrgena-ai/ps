-- ============================================================================
-- AddonStation Mobile — 47) Safe, atomic bulk content import
--
-- The Android admin panel streams a ZIP (up to 1 GiB) locally, uploads each
-- validated cover/content object with an admin JWT, then calls the RPC below.
-- This migration provides the atomic database commit, idempotent batch key,
-- Storage existence/size checks, and an auditable import history.
-- ============================================================================

begin;

create table if not exists public.content_import_batches (
  id              uuid primary key,
  actor_id        uuid not null references public.profiles(id) on delete restrict,
  archive_name    text not null check (char_length(archive_name) between 1 and 255),
  archive_size    bigint not null check (archive_size between 1 and 1073741824),
  archive_sha256  text not null check (archive_sha256 ~ '^[0-9a-f]{64}$'),
  content_type    text not null check (content_type in ('addon', 'world')),
  category        text not null check (category in ('adventure','survival','pvp','building','skins','other')),
  plan            text not null check (plan in ('free','epic','legendary')),
  item_count      int not null check (item_count between 1 and 200),
  content_ids     uuid[] not null,
  manifest        jsonb not null,
  result          jsonb not null,
  created_at      timestamptz not null default now()
);

create index if not exists content_import_batches_actor_idx
  on public.content_import_batches(actor_id, created_at desc);

alter table public.content_import_batches enable row level security;

drop policy if exists content_import_batches_admin_read on public.content_import_batches;
create policy content_import_batches_admin_read
  on public.content_import_batches
  for select
  to authenticated
  using (public.is_admin());

revoke all on table public.content_import_batches from public, anon, authenticated;
grant select on table public.content_import_batches to authenticated;

create or replace function public.admin_bulk_import_content(
  p_batch_id uuid,
  p_archive_name text,
  p_archive_size bigint,
  p_archive_sha256 text,
  p_content_type text,
  p_category text,
  p_plan text,
  p_ac_price int,
  p_is_published boolean,
  p_items jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
  v_uid             uuid := auth.uid();
  v_batch_text      text;
  v_count           int;
  v_existing        public.content_import_batches%rowtype;
  v_item            jsonb;
  v_ordinal         bigint;
  v_source          text;
  v_title           text;
  v_description     text;
  v_version         text;
  v_cover_path      text;
  v_file_path       text;
  v_file_size_text  text;
  v_file_size       bigint;
  v_total_file_size bigint := 0;
  v_file_sha256     text;
  v_storage_size    bigint;
  v_slug_base       text;
  v_slug            text;
  v_content_id      uuid;
  v_content_ids     uuid[] := array[]::uuid[];
  v_seen_sources    text[] := array[]::text[];
  v_seen_files      text[] := array[]::text[];
  v_seen_covers     text[] := array[]::text[];
  v_result_items    jsonb := '[]'::jsonb;
  v_result          jsonb;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;
  if not public.is_admin() then
    return jsonb_build_object('ok', false, 'message', 'not_admin');
  end if;
  if p_batch_id is null then
    return jsonb_build_object('ok', false, 'message', 'bulk_batch_id_required');
  end if;

  v_batch_text := p_batch_id::text;
  perform pg_advisory_xact_lock(hashtextextended(v_batch_text, 0));

  select * into v_existing
    from public.content_import_batches
   where id = p_batch_id;
  if found then
    if v_existing.actor_id <> v_uid
       or v_existing.archive_sha256 <> lower(coalesce(p_archive_sha256, '')) then
      return jsonb_build_object('ok', false, 'message', 'bulk_batch_conflict');
    end if;
    return v_existing.result;
  end if;

  if coalesce(char_length(btrim(p_archive_name)), 0) not between 1 and 255 then
    return jsonb_build_object('ok', false, 'message', 'bulk_archive_name_invalid');
  end if;
  if coalesce(p_archive_size, 0) not between 1 and 1073741824 then
    return jsonb_build_object('ok', false, 'message', 'bulk_archive_size_invalid');
  end if;
  if lower(coalesce(p_archive_sha256, '')) !~ '^[0-9a-f]{64}$' then
    return jsonb_build_object('ok', false, 'message', 'bulk_archive_hash_invalid');
  end if;
  if p_content_type is null or p_content_type not in ('addon', 'world') then
    return jsonb_build_object('ok', false, 'message', 'bulk_content_type_invalid');
  end if;
  if p_category is null or p_category not in ('adventure','survival','pvp','building','skins','other') then
    return jsonb_build_object('ok', false, 'message', 'bulk_category_invalid');
  end if;
  if p_plan is null or p_plan not in ('free','epic','legendary') then
    return jsonb_build_object('ok', false, 'message', 'bulk_plan_invalid');
  end if;
  if coalesce(p_ac_price, 0) < 0 then
    return jsonb_build_object('ok', false, 'message', 'bulk_ac_price_invalid');
  end if;
  if coalesce(jsonb_typeof(p_items), 'null') <> 'array'
     or pg_column_size(p_items) > 1048576 then
    return jsonb_build_object('ok', false, 'message', 'bulk_items_invalid');
  end if;

  v_count := jsonb_array_length(p_items);
  if v_count not between 1 and 200 then
    return jsonb_build_object('ok', false, 'message', 'bulk_item_count_invalid');
  end if;

  for v_item, v_ordinal in
    select value, ordinality
      from jsonb_array_elements(p_items) with ordinality
  loop
    if jsonb_typeof(v_item) <> 'object' then
      return jsonb_build_object('ok', false, 'message', 'bulk_item_invalid', 'item', v_ordinal);
    end if;

    v_source := btrim(coalesce(v_item ->> 'source_folder', ''));
    v_title := btrim(coalesce(v_item ->> 'title', ''));
    v_description := nullif(left(btrim(coalesce(v_item ->> 'description', '')), 5000), '');
    v_version := nullif(left(btrim(coalesce(v_item ->> 'version', '')), 40), '');
    v_cover_path := btrim(coalesce(v_item ->> 'cover_path', ''));
    v_file_path := btrim(coalesce(v_item ->> 'file_path', ''));
    v_file_size_text := btrim(coalesce(v_item ->> 'file_size', ''));
    v_file_sha256 := lower(btrim(coalesce(v_item ->> 'file_sha256', '')));

    if char_length(v_source) not between 1 and 300
       or v_source like '/%'
       or v_source ~ '(^|/)\.\.(/|$)'
       or position(chr(92) in v_source) > 0
       or position(chr(0) in v_source) > 0 then
      return jsonb_build_object('ok', false, 'message', 'bulk_source_folder_invalid', 'item', v_ordinal);
    end if;
    if lower(v_source) = any(v_seen_sources) then
      return jsonb_build_object('ok', false, 'message', 'bulk_source_folder_duplicate', 'item', v_ordinal);
    end if;
    v_seen_sources := array_append(v_seen_sources, lower(v_source));

    if char_length(v_title) not between 1 and 120 then
      return jsonb_build_object('ok', false, 'message', 'bulk_title_invalid', 'item', v_ordinal);
    end if;
    if v_file_size_text !~ '^[0-9]{1,12}$' then
      return jsonb_build_object('ok', false, 'message', 'bulk_file_size_invalid', 'item', v_ordinal);
    end if;
    v_file_size := v_file_size_text::bigint;
    if v_file_size not between 1 and 536870912 then
      return jsonb_build_object('ok', false, 'message', 'bulk_file_size_invalid', 'item', v_ordinal);
    end if;
    v_total_file_size := v_total_file_size + v_file_size;
    if v_total_file_size > 8589934592 then
      return jsonb_build_object('ok', false, 'message', 'bulk_total_size_invalid', 'item', v_ordinal);
    end if;
    if v_file_sha256 !~ '^[0-9a-f]{64}$' then
      return jsonb_build_object('ok', false, 'message', 'bulk_file_hash_invalid', 'item', v_ordinal);
    end if;

    if char_length(v_file_path) not between 45 and 500
       or v_file_path not like ('bulk/' || v_batch_text || '/%')
       or v_file_path like '%..%'
       or v_file_path like '%//%'
       or position(chr(92) in v_file_path) > 0
       or position(chr(0) in v_file_path) > 0 then
      return jsonb_build_object('ok', false, 'message', 'bulk_file_path_invalid', 'item', v_ordinal);
    end if;
    if char_length(v_cover_path) not between 51 and 500
       or v_cover_path not like ('items/bulk/' || v_batch_text || '/%')
       or v_cover_path like '%..%'
       or v_cover_path like '%//%'
       or position(chr(92) in v_cover_path) > 0
       or position(chr(0) in v_cover_path) > 0 then
      return jsonb_build_object('ok', false, 'message', 'bulk_cover_path_invalid', 'item', v_ordinal);
    end if;
    if lower(v_file_path) = any(v_seen_files)
       or lower(v_cover_path) = any(v_seen_covers) then
      return jsonb_build_object('ok', false, 'message', 'bulk_object_path_duplicate', 'item', v_ordinal);
    end if;
    if (p_content_type = 'addon' and lower(v_file_path) !~ '\.(mcaddon|mcpack|zip)$')
       or (p_content_type = 'world' and lower(v_file_path) !~ '\.(mcworld|zip)$')
       or lower(v_cover_path) !~ '/cover\.jpg$' then
      return jsonb_build_object('ok', false, 'message', 'bulk_object_extension_invalid', 'item', v_ordinal);
    end if;
    v_seen_files := array_append(v_seen_files, lower(v_file_path));
    v_seen_covers := array_append(v_seen_covers, lower(v_cover_path));

    select (o.metadata ->> 'size')::bigint into v_storage_size
      from storage.objects o
     where o.bucket_id = 'content-files'
       and o.name = v_file_path;
    if not found or v_storage_size is distinct from v_file_size then
      return jsonb_build_object('ok', false, 'message', 'bulk_content_object_mismatch', 'item', v_ordinal);
    end if;

    if not exists (
      select 1 from storage.objects o
       where o.bucket_id = 'site-assets'
         and o.name = v_cover_path
         and coalesce((o.metadata ->> 'size')::bigint, 0) between 1 and 5242880
    ) then
      return jsonb_build_object('ok', false, 'message', 'bulk_cover_object_missing', 'item', v_ordinal);
    end if;

  end loop;

  -- Validation is deliberately completed for every manifest entry before the
  -- first content row is inserted. Structured validation failures therefore
  -- cannot leave a partially imported batch behind.
  for v_item, v_ordinal in
    select value, ordinality
      from jsonb_array_elements(p_items) with ordinality
  loop
    v_source := btrim(v_item ->> 'source_folder');
    v_title := btrim(v_item ->> 'title');
    v_description := nullif(left(btrim(coalesce(v_item ->> 'description', '')), 5000), '');
    v_version := nullif(left(btrim(coalesce(v_item ->> 'version', '')), 40), '');
    v_cover_path := btrim(v_item ->> 'cover_path');
    v_file_path := btrim(v_item ->> 'file_path');
    v_file_size := (btrim(v_item ->> 'file_size'))::bigint;
    v_file_sha256 := lower(btrim(v_item ->> 'file_sha256'));

    v_slug_base := trim(both '-' from lower(regexp_replace(v_title, '[^[:alnum:]]+', '-', 'g')));
    if v_slug_base = '' then v_slug_base := 'content'; end if;
    v_slug := left(v_slug_base, 70) || '-' || left(replace(v_batch_text, '-', ''), 10) || '-' || v_ordinal::text;

    insert into public.content_items(
      title, slug, description, content_type, category, plan, version,
      cover_url, file_path, file_size, ac_price,
      is_published, is_featured, is_exclusive, created_by
    ) values (
      v_title, v_slug, v_description, p_content_type, p_category, p_plan, v_version,
      v_cover_path, v_file_path, v_file_size, coalesce(p_ac_price, 0),
      coalesce(p_is_published, false), false, false, v_uid
    )
    returning id into v_content_id;

    v_content_ids := array_append(v_content_ids, v_content_id);
    v_result_items := v_result_items || jsonb_build_array(jsonb_build_object(
      'id', v_content_id,
      'title', v_title,
      'source_folder', v_source,
      'file_path', v_file_path,
      'cover_path', v_cover_path,
      'file_size', v_file_size,
      'file_sha256', v_file_sha256
    ));
  end loop;

  v_result := jsonb_build_object(
    'ok', true,
    'batch_id', p_batch_id,
    'count', v_count,
    'items', v_result_items
  );

  insert into public.content_import_batches(
    id, actor_id, archive_name, archive_size, archive_sha256,
    content_type, category, plan, item_count, content_ids, manifest, result
  ) values (
    p_batch_id, v_uid, btrim(p_archive_name), p_archive_size, lower(p_archive_sha256),
    p_content_type, p_category, p_plan, v_count, v_content_ids, p_items, v_result
  );

  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (
    v_uid,
    'content_bulk_imported',
    'content_import_batch',
    p_batch_id::text,
    jsonb_build_object(
      'archive_name', btrim(p_archive_name),
      'archive_size', p_archive_size,
      'archive_sha256', lower(p_archive_sha256),
      'content_type', p_content_type,
      'category', p_category,
      'plan', p_plan,
      'count', v_count,
      'published', coalesce(p_is_published, false)
    )
  );

  return v_result;
end;
$$;

revoke all on function public.admin_bulk_import_content(
  uuid, text, bigint, text, text, text, text, int, boolean, jsonb
) from public, anon;
grant execute on function public.admin_bulk_import_content(
  uuid, text, bigint, text, text, text, text, int, boolean, jsonb
) to authenticated;

commit;

notify pgrst, 'reload schema';
