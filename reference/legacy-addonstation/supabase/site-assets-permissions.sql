-- Fix: permission denied for table public.site_assets
-- Run once in Supabase SQL Editor.

grant select on public.site_assets to anon, authenticated;
grant insert, update, delete on public.site_assets to authenticated;

alter table public.site_assets enable row level security;

drop policy if exists "Public can view enabled assets" on public.site_assets;
drop policy if exists "Managers can manage site assets" on public.site_assets;

create policy "Public can view enabled assets"
on public.site_assets
for select
to anon, authenticated
using (enabled = true or public.is_chat_moderator());

create policy "Managers can manage site assets"
on public.site_assets
for all
to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());
