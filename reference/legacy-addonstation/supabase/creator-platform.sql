-- AddonStation Creator Platform
-- Applications, approved creator profiles, featured/exclusive content and review messages.
-- Run after 000-foundation.sql, content-schema.sql and chat setup.
-- This uses content_items as the single content source; creator_content is a view.

-- Support future creator upload types without creating a second content table.
alter table public.content_items add column if not exists is_exclusive boolean not null default false;
alter table public.content_items drop constraint if exists content_items_content_type_check;
alter table public.content_items add constraint content_items_content_type_check
  check (content_type in ('addon','world','resource_pack','map'));

alter table public.creator_submissions drop constraint if exists creator_submissions_content_type_check;
alter table public.creator_submissions add constraint creator_submissions_content_type_check
  check (content_type in ('addon','world','resource_pack','map'));

create or replace view public.creator_content as
select * from public.content_items
where created_by is not null;

create table if not exists public.creator_profiles (
  creator_id uuid primary key references public.profiles(id) on delete cascade,
  creator_name text not null,
  country text not null default '',
  content_types text[] not null default '{}',
  bio text not null default '',
  experience text not null default '',
  publish_plan text not null default '',
  previous_work text not null default '',
  portfolio_links text not null default '',
  motivation text not null default '',
  joined_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.creator_applications (
  id uuid primary key default gen_random_uuid(),
  applicant_id uuid not null references public.profiles(id) on delete cascade,
  creator_name text not null check (char_length(trim(creator_name)) between 2 and 80),
  country text not null check (char_length(trim(country)) between 2 and 80),
  content_types text[] not null check (cardinality(content_types) > 0),
  bio text not null check (char_length(trim(bio)) between 20 and 1000),
  experience text not null check (char_length(trim(experience)) between 10 and 2000),
  publish_plan text not null check (char_length(trim(publish_plan)) between 10 and 2000),
  previous_work text not null default '',
  portfolio_links text not null default '',
  motivation text not null check (char_length(trim(motivation)) between 10 and 2000),
  agreed_guidelines boolean not null check (agreed_guidelines = true),
  owns_content boolean not null check (owns_content = true),
  status text not null default 'pending' check (status in ('pending','approved','rejected','more_info')),
  admin_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create unique index if not exists creator_applications_one_active_idx
  on public.creator_applications(applicant_id)
  where status in ('pending','more_info');
create index if not exists creator_applications_status_idx
  on public.creator_applications(status, created_at desc);

create table if not exists public.creator_application_messages (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.creator_applications(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  message text not null check (char_length(trim(message)) between 1 and 2000),
  message_type text not null default 'note' check (message_type in ('note','request_info','status')),
  created_at timestamptz not null default now()
);
create index if not exists creator_application_messages_idx
  on public.creator_application_messages(application_id, created_at asc);

create table if not exists public.featured_creators (
  creator_id uuid primary key references public.profiles(id) on delete cascade,
  position integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.featured_content (
  content_id uuid primary key references public.content_items(id) on delete cascade,
  position integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.exclusive_content (
  content_id uuid primary key references public.content_items(id) on delete cascade,
  position integer not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

-- Keep the notification stream compatible with creator status messages.
alter table public.chat_notifications add column if not exists notification_text text;
alter table public.chat_notifications drop constraint if exists chat_notifications_notification_type_check;
alter table public.chat_notifications add constraint chat_notifications_notification_type_check
  check (notification_type in ('mention','reply','thread','friend_request','creator_application','creator_tip'));

create or replace function public.is_creator_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists(
    select 1 from public.profiles
    where id = auth.uid() and (role in ('admin','moderator') or is_admin = true)
  );
$$;

-- Submit only through this function so one applicant cannot create two active applications.
create or replace function public.submit_creator_application(
  p_creator_name text,
  p_country text,
  p_content_types text[],
  p_bio text,
  p_experience text,
  p_publish_plan text,
  p_previous_work text default '',
  p_portfolio_links text default '',
  p_motivation text default '',
  p_agreed_guidelines boolean default false,
  p_owns_content boolean default false
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  application_id uuid;
begin
  if uid is null then raise exception 'Login required'; end if;
  if exists(select 1 from public.creator_applications where applicant_id=uid and status in ('pending','more_info')) then
    raise exception 'You already have an active creator application.';
  end if;
  if not p_agreed_guidelines or not p_owns_content then raise exception 'Creator agreements are required.'; end if;
  insert into public.creator_applications(applicant_id,creator_name,country,content_types,bio,experience,publish_plan,previous_work,portfolio_links,motivation,agreed_guidelines,owns_content)
  values(uid,trim(p_creator_name),trim(p_country),p_content_types,trim(p_bio),trim(p_experience),trim(p_publish_plan),trim(coalesce(p_previous_work,'')),trim(coalesce(p_portfolio_links,'')),trim(p_motivation),true,true)
  returning id into application_id;
  return jsonb_build_object('application_id',application_id,'status','pending');
end;
$$;
grant execute on function public.submit_creator_application(text,text,text[],text,text,text,text,text,text,boolean,boolean) to authenticated;

create or replace function public.review_creator_application(
  p_application_id uuid,
  p_status text,
  p_note text default ''
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp
as $$
declare
  app_row record;
  admin_id uuid := auth.uid();
  notification text;
begin
  if not public.is_creator_admin() then raise exception 'Administrator permission required.'; end if;
  if p_status not in ('approved','rejected','more_info') then raise exception 'Invalid application status.'; end if;
  select * into app_row from public.creator_applications where id=p_application_id for update;
  if not found then raise exception 'Application not found.'; end if;
  update public.creator_applications set status=p_status,admin_note=nullif(trim(coalesce(p_note,'')),''),reviewed_at=now(),updated_at=now() where id=p_application_id;
  if p_status='approved' then
    update public.profiles set role='creator',updated_at=now() where id=app_row.applicant_id;
    insert into public.creator_profiles(creator_id,creator_name,country,content_types,bio,experience,publish_plan,previous_work,portfolio_links,motivation)
    values(app_row.applicant_id,app_row.creator_name,app_row.country,app_row.content_types,app_row.bio,app_row.experience,app_row.publish_plan,app_row.previous_work,app_row.portfolio_links,app_row.motivation)
    on conflict (creator_id) do update set creator_name=excluded.creator_name,country=excluded.country,content_types=excluded.content_types,bio=excluded.bio,experience=excluded.experience,publish_plan=excluded.publish_plan,previous_work=excluded.previous_work,portfolio_links=excluded.portfolio_links,motivation=excluded.motivation,updated_at=now();
    notification := '🎉 Congratulations! Your Creator application has been approved. Welcome to AddonStation Creators!';
  elsif p_status='rejected' then
    notification := 'Your Creator application was not approved at this time.';
  else
    notification := 'More information is required for your Creator application.';
  end if;
  insert into public.creator_application_messages(application_id,sender_id,message,message_type)
  values(p_application_id,admin_id,coalesce(nullif(trim(p_note),''),notification),case when p_status='more_info' then 'request_info' else 'status' end);
  insert into public.chat_notifications(recipient_id,actor_id,notification_type,notification_text)
  values(app_row.applicant_id,admin_id,'creator_application',notification);
  return jsonb_build_object('application_id',p_application_id,'status',p_status);
end;
$$;
grant execute on function public.review_creator_application(uuid,text,text) to authenticated;

create or replace function public.resubmit_creator_application(
  p_application_id uuid,
  p_creator_name text,
  p_country text,
  p_content_types text[],
  p_bio text,
  p_experience text,
  p_publish_plan text,
  p_previous_work text,
  p_portfolio_links text,
  p_motivation text,
  p_agreed_guidelines boolean,
  p_owns_content boolean
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp
as $$
declare app_row record;
begin
  if auth.uid() is null then raise exception 'Login required'; end if;
  if not p_agreed_guidelines or not p_owns_content then raise exception 'Creator agreements are required.'; end if;
  select * into app_row from public.creator_applications where id=p_application_id and applicant_id=auth.uid() for update;
  if not found or app_row.status <> 'more_info' then raise exception 'This application cannot be resubmitted.'; end if;
  update public.creator_applications set creator_name=trim(p_creator_name),country=trim(p_country),content_types=p_content_types,bio=trim(p_bio),experience=trim(p_experience),publish_plan=trim(p_publish_plan),previous_work=trim(coalesce(p_previous_work,'')),portfolio_links=trim(coalesce(p_portfolio_links,'')),motivation=trim(p_motivation),agreed_guidelines=true,owns_content=true,status='pending',admin_note=null,updated_at=now() where id=p_application_id;
  return jsonb_build_object('application_id',p_application_id,'status','pending');
end;
$$;
grant execute on function public.resubmit_creator_application(uuid,text,text,text[],text,text,text,text,text,text,boolean,boolean) to authenticated;

create or replace function public.send_creator_application_message(p_application_id uuid,p_message text,p_message_type text default 'note')
returns uuid
language plpgsql security definer set search_path = public
as $$
declare app_owner uuid; message_id uuid;
begin
  select applicant_id into app_owner from public.creator_applications where id=p_application_id;
  if app_owner is null then raise exception 'Application not found.'; end if;
  if auth.uid() <> app_owner and not public.is_creator_admin() then raise exception 'Not allowed.'; end if;
  insert into public.creator_application_messages(application_id,sender_id,message,message_type) values(p_application_id,auth.uid(),trim(p_message),p_message_type) returning id into message_id;
  return message_id;
end;
$$;
grant execute on function public.send_creator_application_message(uuid,text,text) to authenticated;

-- Featured and exclusive flags are admin-only and always reference existing records.
create or replace function public.admin_set_featured_creator(p_creator_id uuid,p_active boolean,p_position integer default 0)
returns void language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_creator_admin() then raise exception 'Administrator permission required.'; end if;
  if p_active then insert into public.featured_creators(creator_id,position,active) values(p_creator_id,p_position,true) on conflict (creator_id) do update set position=excluded.position,active=true;
  else update public.featured_creators set active=false where creator_id=p_creator_id; end if;
end;
$$;
grant execute on function public.admin_set_featured_creator(uuid,boolean,integer) to authenticated;

create or replace function public.admin_set_content_flags(p_content_id uuid,p_featured boolean,p_exclusive boolean)
returns void language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_creator_admin() then raise exception 'Administrator permission required.'; end if;
  update public.content_items set is_featured=p_featured,is_exclusive=p_exclusive,updated_at=now() where id=p_content_id;
  if p_featured then insert into public.featured_content(content_id,active) values(p_content_id,true) on conflict(content_id) do update set active=true; else update public.featured_content set active=false where content_id=p_content_id; end if;
  if p_exclusive then insert into public.exclusive_content(content_id,active) values(p_content_id,true) on conflict(content_id) do update set active=true; else update public.exclusive_content set active=false where content_id=p_content_id; end if;
end;
$$;
grant execute on function public.admin_set_content_flags(uuid,boolean,boolean) to authenticated;

-- RLS
alter table public.creator_profiles enable row level security;
alter table public.creator_applications enable row level security;
alter table public.creator_application_messages enable row level security;
alter table public.featured_creators enable row level security;
alter table public.featured_content enable row level security;
alter table public.exclusive_content enable row level security;

grant select on public.creator_profiles to anon,authenticated;
grant select,insert,update on public.creator_applications to authenticated;
grant select,insert on public.creator_application_messages to authenticated;
grant select on public.featured_creators to anon,authenticated;
grant select on public.featured_content to anon,authenticated;
grant select on public.exclusive_content to anon,authenticated;
grant insert,update,delete on public.creator_profiles to authenticated;
grant insert,update,delete on public.featured_creators to authenticated;
grant insert,update,delete on public.featured_content to authenticated;
grant insert,update,delete on public.exclusive_content to authenticated;

drop policy if exists "Public view approved creator profiles" on public.creator_profiles;
create policy "Public view approved creator profiles" on public.creator_profiles for select to anon,authenticated using (exists(select 1 from public.profiles p where p.id=creator_id and p.role='creator'));
drop policy if exists "Creators update own creator profile" on public.creator_profiles;
create policy "Creators update own creator profile" on public.creator_profiles for update to authenticated using(creator_id=auth.uid()) with check(creator_id=auth.uid());
drop policy if exists "Managers manage creator profiles" on public.creator_profiles;
create policy "Managers manage creator profiles" on public.creator_profiles for all to authenticated using(public.is_creator_admin()) with check(public.is_creator_admin());

drop policy if exists "Applicants view own applications" on public.creator_applications;
create policy "Applicants view own applications" on public.creator_applications for select to authenticated using(applicant_id=auth.uid() or public.is_creator_admin());
drop policy if exists "Applicants create applications" on public.creator_applications;
create policy "Applicants create applications" on public.creator_applications for insert to authenticated with check(applicant_id=auth.uid());
drop policy if exists "Applicants update more info applications" on public.creator_applications;
create policy "Applicants update more info applications" on public.creator_applications for update to authenticated using(applicant_id=auth.uid() and status='more_info') with check(applicant_id=auth.uid());
drop policy if exists "Managers manage applications" on public.creator_applications;
create policy "Managers manage applications" on public.creator_applications for all to authenticated using(public.is_creator_admin()) with check(public.is_creator_admin());

drop policy if exists "Application participants view messages" on public.creator_application_messages;
create policy "Application participants view messages" on public.creator_application_messages for select to authenticated using(exists(select 1 from public.creator_applications a where a.id=application_id and (a.applicant_id=auth.uid() or public.is_creator_admin())));
drop policy if exists "Application participants send messages" on public.creator_application_messages;
create policy "Application participants send messages" on public.creator_application_messages for insert to authenticated with check(sender_id=auth.uid() and exists(select 1 from public.creator_applications a where a.id=application_id and (a.applicant_id=auth.uid() or public.is_creator_admin())));

drop policy if exists "Public view active featured creators" on public.featured_creators;
create policy "Public view active featured creators" on public.featured_creators for select to anon,authenticated using(active=true);
drop policy if exists "Managers manage featured creators" on public.featured_creators;
create policy "Managers manage featured creators" on public.featured_creators for all to authenticated using(public.is_creator_admin()) with check(public.is_creator_admin());
drop policy if exists "Public view active featured content" on public.featured_content;
create policy "Public view active featured content" on public.featured_content for select to anon,authenticated using(active=true);
drop policy if exists "Managers manage featured content" on public.featured_content;
create policy "Managers manage featured content" on public.featured_content for all to authenticated using(public.is_creator_admin()) with check(public.is_creator_admin());
drop policy if exists "Public view active exclusive content" on public.exclusive_content;
create policy "Public view active exclusive content" on public.exclusive_content for select to anon,authenticated using(active=true);
drop policy if exists "Managers manage exclusive content" on public.exclusive_content;
create policy "Managers manage exclusive content" on public.exclusive_content for all to authenticated using(public.is_creator_admin()) with check(public.is_creator_admin());
