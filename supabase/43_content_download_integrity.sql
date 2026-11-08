-- ============================================================================
-- AddonStation Mobile — protected content download integrity
--
-- 1. Point Sulfur Cars at the verified .mcaddon object rebuilt from the two
--    immutable PVRAW001 release assets.
-- 2. Remove download attempts that could not have transferred a file while the
--    bucket was empty.
-- 3. Refuse to record future attempts when the referenced Storage object does
--    not exist.
-- 4. Prevent publishing content metadata before its protected object exists.
-- ============================================================================

begin;

do $$
begin
  if not exists (
    select 1
      from storage.objects o
     where o.bucket_id = 'content-files'
       and o.name = 'content-001/sulfur-cars_ffe0c865.mcaddon'
       and (o.metadata ->> 'size')::bigint = 1053329
  ) then
    raise exception 'verified_sulfur_cars_object_missing';
  end if;
end;
$$;

update public.content_items
   set file_path = 'content-001/sulfur-cars_ffe0c865.mcaddon',
       file_size = 1053329,
       updated_at = now()
 where id = '660889b8-2b98-4f4e-bfc8-afc578bb880d';

do $$
begin
  if not exists (
    select 1
      from public.content_items i
     where i.id = '660889b8-2b98-4f4e-bfc8-afc578bb880d'
       and i.file_path = 'content-001/sulfur-cars_ffe0c865.mcaddon'
       and i.file_size = 1053329
  ) then
    raise exception 'sulfur_cars_item_missing';
  end if;
end;
$$;

-- The protected bucket had no objects, so every historical row for this item
-- represents an unsuccessful attempt. Removing these rows also restores any
-- plan quota they incorrectly consumed.
delete from public.content_downloads
 where item_id = '660889b8-2b98-4f4e-bfc8-afc578bb880d';

update public.content_items i
   set downloads_count = (
     select count(*)::integer
       from public.content_downloads d
      where d.item_id = i.id
   )
 where i.id = '660889b8-2b98-4f4e-bfc8-afc578bb880d';

create or replace function public.record_content_download(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid         uuid := auth.uid();
  v_item        public.content_items;
  v_object_size bigint;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if coalesce(v_item.file_path, '') = '' then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  select coalesce((o.metadata ->> 'size')::bigint, v_item.file_size)
    into v_object_size
    from storage.objects o
   where o.bucket_id = 'content-files'
     and o.name = v_item.file_path;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  if not public.can_access_item(p_item_id, v_uid) then
    return jsonb_build_object('ok', false, 'message', 'no_permission');
  end if;

  if public.download_limit_reached(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'download_limit');
  end if;

  insert into public.content_downloads (user_id, item_id)
  values (v_uid, p_item_id);

  update public.content_items
     set downloads_count = coalesce(downloads_count, 0) + 1
   where id = p_item_id;

  return jsonb_build_object(
    'ok', true,
    'bucket', 'content-files',
    'path', v_item.file_path,
    'size', v_object_size,
    'title', v_item.title
  );
end;
$$;

create or replace function public.ensure_published_content_file()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if new.is_published and (
    coalesce(new.file_path, '') = ''
    or not exists (
      select 1
        from storage.objects o
       where o.bucket_id = 'content-files'
         and o.name = new.file_path
    )
  ) then
    raise exception using errcode = '23514', message = 'file_not_uploaded';
  end if;
  return new;
end;
$$;

revoke all on function public.ensure_published_content_file() from public;

drop trigger if exists content_items_require_uploaded_file on public.content_items;
create trigger content_items_require_uploaded_file
  before insert or update of is_published, file_path on public.content_items
  for each row execute function public.ensure_published_content_file();

commit;

notify pgrst, 'reload schema';
