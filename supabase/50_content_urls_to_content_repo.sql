-- 50_content_urls_to_content_repo.sql
-- AddonStation: point every content download URL at the PUBLIC content repository.
--
-- TARGET REPOSITORY (agreed location for all addon assets):
--   https://github.com/silverfoxyactive67-beep/content  (PUBLIC, releases hold the assets)
--
-- WHAT THIS DOES:
--   Rewrites the three content_items URL fields from their old homes to the new one:
--     1) cover_url           (pack cover art)
--     2) ps4_raw_url         (main PS4 pack file)
--     3) pack_targets        (JSONB array, each element has "ps4_raw_url")
--
--   Two old shapes are handled (both map onto the same asset file names):
--     A) https://github.com/mohamadelfrgena-ai/ps/releases/download/content-<slug>-1.0.0/<asset>
--        (original GitHub Releases location, repo is PRIVATE -> 404 for anonymous clients)
--     B) https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/<slug>/<asset>
--        (interim Supabase Storage location from migration 45, if it was applied)
--   Both become:
--        https://github.com/silverfoxyactive67-beep/content/releases/download/content-<slug>-1.0.0/<asset>
--
--   rows already pointing at the content repository are left untouched (idempotent).
--
-- !!! RUN THIS ONLY AFTER the release assets have been transferred into the
--     silverfoxyactive67-beep/content repository releases !!!
--     (each tag content-<slug>-1.0.0 must contain the same asset file names
--      before you run this, or downloads will 404.)
--
-- VERIFY FIRST (optional): run the SELECT at the bottom before and after.

begin;

-- ---------------------------------------------------------------------------
-- 1) cover_url + ps4_raw_url
-- ---------------------------------------------------------------------------
update public.content_items
set cover_url   = replace(cover_url,
                          'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                          'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')
where cover_url like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%';

update public.content_items
set cover_url   = replace(cover_url,
                          'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/',
                          'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')
where cover_url like 'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/%';

update public.content_items
set ps4_raw_url = replace(ps4_raw_url,
                          'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                          'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')
where ps4_raw_url like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%';

update public.content_items
set ps4_raw_url = replace(ps4_raw_url,
                          'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/',
                          'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')
where ps4_raw_url like 'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/%';

-- ---------------------------------------------------------------------------
-- 2) pack_targets (JSONB array: [{"target":..., "dest_folder":..., "raw_file":...,
--    "ps4_raw_url":..., "sha256":...}, ...])
--    A plain text replace is safe here: the old URL prefix never occurs inside
--    sha256/dest_folder/raw_file values.
-- ---------------------------------------------------------------------------
update public.content_items
set pack_targets = replace(pack_targets::text,
                           'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                           'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')::jsonb
where pack_targets::text like '%https://github.com/mohamadelfrgena-ai/ps/releases/download/content-%';

update public.content_items
set pack_targets = replace(pack_targets::text,
                           'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/',
                           'https://github.com/silverfoxyactive67-beep/content/releases/download/content-' || slug || '-1.0.0/')::jsonb
where pack_targets::text like '%https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/%';

commit;

-- ---------------------------------------------------------------------------
-- VERIFICATION (run after the transaction):
--   every published row must show ONLY
--   https://github.com/silverfoxyactive67-beep/content/... URLs
-- ---------------------------------------------------------------------------
select
  slug,
  plan,
  cover_url,
  ps4_raw_url,
  pack_targets
from public.content_items
where is_published
order by plan, slug;

-- This query MUST return 0 rows after the update:
select slug
from public.content_items
where cover_url    like '%mohamadelfrgena-ai/ps%'
   or ps4_raw_url  like '%mohamadelfrgena-ai/ps%'
   or pack_targets::text like '%mohamadelfrgena-ai/ps%'
   or cover_url    like '%supabase.co/storage%'
   or ps4_raw_url  like '%supabase.co/storage%'
   or pack_targets::text like '%supabase.co/storage%';
