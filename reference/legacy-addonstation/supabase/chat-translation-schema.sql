-- Translation cache: ready for a self-hosted Argos Translate service later.
create table if not exists public.chat_translations (
  message_id bigint not null references public.chat_messages(id) on delete cascade,
  target_language text not null,
  source_language text not null,
  translated_text text not null,
  created_at timestamptz not null default now(),
  primary key (message_id, target_language)
);

alter table public.chat_translations enable row level security;

create policy "Authenticated users can read chat translations"
on public.chat_translations for select to authenticated
using (true);

-- Only the future server-side translation function may create translations.
