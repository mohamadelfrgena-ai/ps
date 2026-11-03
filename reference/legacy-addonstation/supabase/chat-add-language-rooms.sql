-- AddonStation Chat: add the eight language/community rooms
-- Safe additive migration.
-- It does not delete, rename or reorder any existing room.
-- Run from one Supabase SQL Editor tab while the apps are closed.

begin;

with new_rooms(slug, name, description, room_order) as (
  values
    ('arabic', '🇸🇦 العربية', 'Arabic community chat', 1),
    ('russian', '🇷🇺 Русский', 'Russian community chat', 2),
    ('portuguese', '🇧🇷 Português', 'Portuguese community chat', 3),
    ('chinese', '🇨🇳 中文', 'Chinese community chat', 4),
    ('english', '🇬🇧 English', 'English community chat', 5),
    ('espanol', '🇪🇸 Español', 'Spanish community chat', 6),
    ('francais', '🇫🇷 Français', 'French community chat', 7),
    ('social-media-makers', '📱 Social Media Makers', 'Social media makers community chat', 8)
  ),
  current_max as (
    select coalesce(max(position), 0)::integer as position
    from public.chat_channels
  )
insert into public.chat_channels (slug, name, description, position, is_public)
select r.slug, r.name, r.description, current_max.position + r.room_order, true
from new_rooms r
cross join current_max
on conflict (slug) do update
set name = excluded.name,
    description = excluded.description;

-- Keep the existing authenticated read policy/privileges and make the result
-- auditable. Each row's id is the independent room_id used by messages.
select id as room_id, slug, name, position
from public.chat_channels
where slug in (
  'arabic', 'russian', 'portuguese', 'chinese',
  'english', 'espanol', 'francais', 'social-media-makers'
)
order by position;

commit;
