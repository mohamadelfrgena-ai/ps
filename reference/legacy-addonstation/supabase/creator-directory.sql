-- AddonStation Creators directory and follows
-- Run this file in Supabase SQL Editor after 000-foundation.sql and content-schema.sql.
-- No service-role key is needed.

-- Public directory: only profiles marked as creators are visible without login.
grant select on public.profiles to anon;
drop policy if exists "Public can view creator profiles" on public.profiles;
create policy "Public can view creator profiles"
on public.profiles for select to anon
using (role = 'creator');

create table if not exists public.creator_follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  creator_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, creator_id),
  check (follower_id <> creator_id)
);

create index if not exists creator_follows_creator_idx
  on public.creator_follows (creator_id, created_at desc);

alter table public.creator_follows enable row level security;
grant select on public.creator_follows to authenticated;
grant insert, delete on public.creator_follows to authenticated;

drop policy if exists "Users view own creator follows" on public.creator_follows;
create policy "Users view own creator follows"
on public.creator_follows for select to authenticated
using (follower_id = auth.uid());

drop policy if exists "Users follow creators" on public.creator_follows;
create policy "Users follow creators"
on public.creator_follows for insert to authenticated
with check (
  follower_id = auth.uid()
  and exists (
    select 1 from public.profiles
    where id = creator_id and role = 'creator'
  )
);

drop policy if exists "Users unfollow creators" on public.creator_follows;
create policy "Users unfollow creators"
on public.creator_follows for delete to authenticated
using (follower_id = auth.uid());

-- Return only an aggregate, not the private follower ids.
create or replace function public.creator_followers_count(p_creator_id uuid)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
  select count(*)
  from public.creator_follows f
  join public.profiles p on p.id = f.creator_id
  where f.creator_id = p_creator_id
    and p.role = 'creator';
$$;

grant execute on function public.creator_followers_count(uuid) to anon, authenticated;
