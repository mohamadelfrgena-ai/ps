-- 46_storage_bucket_mime.sql
-- Allow content pack file types in the public site-assets bucket.
--
-- Why: the site-assets bucket restricts allowed_mime_types to images
-- (uploads of .raw / .zip / .json return 415 invalid_mime_type). Content
-- packs must live next to their covers under content/<slug>/, so the
-- restriction is lifted (NULL = no mime restriction). Public read access
-- is unchanged.

update storage.buckets
set allowed_mime_types = null
where name = 'site-assets'
  and allowed_mime_types is not null;
