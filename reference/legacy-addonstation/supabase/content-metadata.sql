-- AddonStation shared content metadata
-- Run after supabase/content-schema.sql when upgrading an existing project.
-- The mobile admin and the website both read these fields from content_items.

alter table public.content_items
  add column if not exists category text not null default 'general';

alter table public.content_items
  add column if not exists version text not null default '1.0.0';

create index if not exists content_items_category_idx
  on public.content_items(category);

-- Existing installations also need these privileges for the website and app
-- to read from the same source of truth through PostgREST.
grant select on public.content_items to anon, authenticated;
grant insert, update, delete on public.content_items to authenticated;
grant select, insert, update, delete on public.content_favorites to authenticated;
grant select on public.content_reviews to anon, authenticated;
grant insert, update, delete on public.content_reviews to authenticated;
grant select, insert on public.content_downloads to authenticated;
