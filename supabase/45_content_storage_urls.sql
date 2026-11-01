-- 45_content_storage_urls.sql
-- Move content file URLs from private GitHub Releases to public Supabase Storage.
--
-- Why: the GitHub repo (mohamadelfrgena-ai/ps) is PRIVATE, so PS4/phone clients
-- cannot fetch https://github.com/.../releases/download/... assets (404 without a
-- GitHub session). The Upload content storage workflow mirrors every release
-- asset (covers + raws + zips) into the public site-assets bucket under
-- content/<slug>/<same-file-name>.
--
-- This migration rewrites the three URL fields to the public storage prefix.
-- Idempotent: rows already pointing at storage are left untouched.

update content_items
set cover_url    = replace(cover_url,
                           'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                           'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/'),
    ps4_raw_url  = replace(ps4_raw_url,
                           'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                           'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/'),
    pack_targets = replace(pack_targets::text,
                           'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-' || slug || '-1.0.0/',
                           'https://stbmescmexzcszyyambq.supabase.co/storage/v1/object/public/site-assets/content/' || slug || '/')::jsonb
where cover_url like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/%'
   or ps4_raw_url like 'https://github.com/mohamadelfrgena-ai/ps/releases/download/%'
   or pack_targets::text like '%https://github.com/mohamadelfrgena-ai/ps/releases/download/%';

-- Note for the phone team: `file_path` keeps its release-relative shape
-- (content-<slug>-1.0.0/<file>). Prefer the full https URLs above
-- (cover_url / ps4_raw_url / pack_targets[].ps4_raw_url) for downloads.
