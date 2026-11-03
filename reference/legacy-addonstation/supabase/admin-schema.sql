-- Admin dashboard support

create table if not exists public.site_assets (
  id uuid primary key default gen_random_uuid(),
  asset_type text not null check (asset_type in ('avatar', 'sticker', 'chat_item')),
  name text not null,
  file_url text not null,
  required_plan text not null default 'free' check (required_plan in ('free', 'epic', 'legendary', 'admin')),
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);

alter table public.site_assets enable row level security;

create policy "Public can view enabled assets"
on public.site_assets for select to anon, authenticated
using (enabled = true or public.is_chat_moderator());

create policy "Managers can manage site assets"
on public.site_assets for all to authenticated
using (public.is_chat_moderator())
with check (public.is_chat_moderator());

-- PostgREST requires table privileges in addition to RLS policies.
grant select on public.site_assets to anon, authenticated;
grant insert, update, delete on public.site_assets to authenticated;

create or replace function public.admin_update_profile(
  target_user uuid,
  target_plan text,
  target_role text,
  target_expires timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_chat_moderator() then
    raise exception 'Administrator permission required.';
  end if;
  if target_plan not in ('free','epic','legendary') then
    raise exception 'Invalid plan.';
  end if;
  if target_role not in ('user','creator','moderator','admin') then
    raise exception 'Invalid role.';
  end if;
  update public.profiles
  set plan = target_plan, role = target_role, plan_expires_at = target_expires, updated_at = now()
  where id = target_user;
end;
$$;

-- Administrators can add images to the avatars bucket.
drop policy if exists "Managers can upload avatars" on storage.objects;
drop policy if exists "Managers can update avatars" on storage.objects;
drop policy if exists "Managers can delete avatars" on storage.objects;

create policy "Managers can upload avatars"
on storage.objects for insert to authenticated
with check (bucket_id = 'avatars' and public.is_chat_moderator());

create policy "Managers can update avatars"
on storage.objects for update to authenticated
using (bucket_id = 'avatars' and public.is_chat_moderator())
with check (bucket_id = 'avatars' and public.is_chat_moderator());

create policy "Managers can delete avatars"
on storage.objects for delete to authenticated
using (bucket_id = 'avatars' and public.is_chat_moderator());
