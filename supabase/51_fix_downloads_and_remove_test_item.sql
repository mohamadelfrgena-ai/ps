-- 51_fix_downloads_and_remove_test_item.sql
-- AddonStation: (1) remove the test item "Sulfur Cars", (2) make every content
-- download URL work again (fixes "HTTP 404" downloads and missing covers).
--
-- WHAT THIS DOES:
--   1) DELETES the "Sulfur Cars" test row from content_items.
--   2) REBUILDS the three URL fields from the item's own slug + pack file names:
--        cover_url    = https://github.com/mohamadelfrgena-ai/ps/releases/download/content-<slug>-1.0.0/<slug>-cover.jpg
--        ps4_raw_url  = https://github.com/mohamadelfrgena-ai/ps/releases/download/content-<slug>-1.0.0/<raw_file>
--        pack_targets = same, one URL per pack element (keyed by its raw_file)
--      The URLs are rebuilt FROM SCRATCH (not string-replaced), so this fixes rows
--      no matter what the old links pointed at (private repo / empty storage / a
--      deleted host). The repository is PUBLIC and the assets are uploaded there.
--
--   Later, when the assets are transferred to silverfoxyactive67-beep/content,
--   run 50_content_urls_to_content_repo.sql to re-point everything there.
--
-- SAFE TO RUN TWICE (idempotent).

begin;

-- ---------------------------------------------------------------------------
-- 1) remove the test item
-- ---------------------------------------------------------------------------
delete from public.content_items
where slug = 'sulfur-cars'
   or lower(title) like '%sulfur%';

-- ---------------------------------------------------------------------------
-- 2) rebuild pack_targets[].ps4_raw_url from each pack's own raw_file name
-- ---------------------------------------------------------------------------
update public.content_items
set pack_targets = (
    select coalesce(jsonb_agg(
        case
            when elem ? 'raw_file' and elem->>'raw_file' <> ''
            then jsonb_set(elem, '{ps4_raw_url}',
                 to_jsonb('https://github.com/mohamadelfrgena-ai/ps/releases/download/content-'
                          || slug || '-1.0.0/' || (elem->>'raw_file')))
            else elem
        end), '[]'::jsonb)
    from jsonb_array_elements(pack_targets) as elem)
where pack_targets is not null
  and jsonb_array_length(pack_targets) > 0;

-- ---------------------------------------------------------------------------
-- 3) rebuild the main ps4_raw_url from the first pack's raw_file
-- ---------------------------------------------------------------------------
update public.content_items
set ps4_raw_url = 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-'
                  || slug || '-1.0.0/' || (pack_targets->0->>'raw_file')
where pack_targets is not null
  and jsonb_array_length(pack_targets) > 0
  and coalesce(pack_targets->0->>'raw_file', '') <> '';

-- ---------------------------------------------------------------------------
-- 4) rebuild cover_url from the naming convention <slug>-cover.jpg
-- ---------------------------------------------------------------------------
update public.content_items
set cover_url = 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-'
                || slug || '-1.0.0/' || slug || '-cover.jpg';

commit;

-- ---------------------------------------------------------------------------
-- VERIFICATION — run after the transaction.
--   The first query lists every item with its rebuilt URLs (they must all start
--   with https://github.com/mohamadelfrgena-ai/ps/...).
--   The second query MUST return 0 rows.
-- ---------------------------------------------------------------------------
select slug, plan, cover_url, ps4_raw_url, pack_targets
from public.content_items
order by plan, slug;

select slug
from public.content_items
where cover_url   not like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%'
   or ps4_raw_url not like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%'
   or pack_targets::text not like '%https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%';
