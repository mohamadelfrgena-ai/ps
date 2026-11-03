-- Creator Program
alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles add constraint profiles_role_check check (role in ('user','creator','moderator','admin'));

create table if not exists public.creator_submissions (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  content_type text not null check (content_type in ('addon','world')),
  title text not null,
  description text not null default '',
  category text not null default 'general',
  version text not null default '1.0.0',
  preview_url text,
  video_url text,
  file_path text,
  requested_plan text not null default 'free' check (requested_plan in ('free','epic','legendary')),
  status text not null default 'pending' check (status in ('pending','approved','rejected','published')),
  admin_note text,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);
alter table public.creator_submissions add column if not exists category text not null default 'general';
alter table public.creator_submissions add column if not exists version text not null default '1.0.0';
alter table public.creator_submissions enable row level security;
create policy "Creators view own submissions" on public.creator_submissions for select to authenticated using (auth.uid()=creator_id or public.is_chat_moderator());
create policy "Creators submit content" on public.creator_submissions for insert to authenticated with check (auth.uid()=creator_id and exists(select 1 from public.profiles where id=auth.uid() and role='creator'));
create policy "Managers review creator submissions" on public.creator_submissions for update to authenticated using(public.is_chat_moderator()) with check(public.is_chat_moderator());

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
('creator-media','creator-media',true,5242880,array['image/png','image/jpeg','image/webp']),
('creator-files','creator-files',false,null,null),
('content-files','content-files',false,null,null)
on conflict (id) do nothing;

create policy "Creators upload own preview media" on storage.objects for insert to authenticated with check (bucket_id='creator-media' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.profiles where id=auth.uid() and role='creator'));
create policy "Public views creator media" on storage.objects for select to anon,authenticated using(bucket_id='creator-media');
create policy "Creators upload own submission files" on storage.objects for insert to authenticated with check (bucket_id='creator-files' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.profiles where id=auth.uid() and role='creator'));
create policy "Managers view creator submission files" on storage.objects for select to authenticated using(bucket_id='creator-files' and public.is_chat_moderator());

create or replace function public.admin_update_profile(target_user uuid,target_plan text,target_role text,target_expires timestamptz default null)
returns void language plpgsql security definer set search_path=public as $$
begin
 if not public.is_chat_moderator() then raise exception 'Administrator permission required.'; end if;
 if target_plan not in ('free','epic','legendary') then raise exception 'Invalid plan.'; end if;
 if target_role not in ('user','creator','moderator','admin') then raise exception 'Invalid role.'; end if;
 update public.profiles set plan=target_plan,role=target_role,plan_expires_at=target_expires,updated_at=now() where id=target_user;
end; $$;
