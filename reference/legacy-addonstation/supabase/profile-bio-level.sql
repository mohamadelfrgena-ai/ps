alter table public.profiles add column if not exists bio text default '' check (char_length(bio) <= 160);
alter table public.profiles add column if not exists user_level integer not null default 1 check (user_level >= 1);
