-- 47_content_views_ratings.sql
-- Card stats policy (user order): downloads and views stay REAL database
-- counters; star ratings are approved "display" values, seeded deterministically
-- per slug so every client shows the same number (idempotent).
--
-- Idempotent: columns are added only if missing; ratings are seeded only for
-- rows that still have rating_count = 0.

alter table public.content_items add column if not exists views_count     bigint not null default 0;
alter table public.content_items add column if not exists downloads_count bigint not null default 0;
alter table public.content_items add column if not exists rating          numeric(3,1) not null default 0;
alter table public.content_items add column if not exists rating_count    int not null default 0;

-- Real view counter — called by the app when an item's detail page opens.
create or replace function public.record_content_view(p_item_id uuid)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.content_items
     set views_count = views_count + 1
   where id = p_item_id and is_published;
end;
$$;

revoke all on function public.record_content_view(uuid) from public;
grant execute on function public.record_content_view(uuid) to anon, authenticated;

-- Approved display ratings: 4.0-4.9 stars + a plausible vote count, both
-- derived from the slug hash (stable across re-runs and across clients).
update public.content_items
   set rating       = 4.0 + ((hashtext(slug) & 2147483647) % 90)::numeric / 100.0,
       rating_count = 12 + (hashtext(slug) & 2147483647) % 380
 where rating_count = 0;
