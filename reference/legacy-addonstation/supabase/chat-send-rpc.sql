-- Reliable chat sender
-- Uses a small security-definer RPC so inserting a message does not depend on
-- the client being allowed to SELECT the inserted row back.

create or replace function public.send_chat_message(
  p_channel_id uuid,
  p_content text default '',
  p_sticker_url text default null,
  p_reply_to_id bigint default null,
  p_thread_root_id bigint default null
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  new_message_id bigint;
  clean_content text := trim(coalesce(p_content, ''));
  is_muted boolean := false;
begin
  if auth.uid() is null then
    raise exception 'Login required.';
  end if;

  if not exists (
    select 1
    from public.chat_channels
    where id = p_channel_id
      and is_public = true
  ) then
    raise exception 'This chat channel is not available.';
  end if;

  if clean_content = '' and p_sticker_url is null then
    raise exception 'Message cannot be empty.';
  end if;

  if char_length(clean_content) > 500 then
    raise exception 'Message is too long.';
  end if;

  -- Moderation is optional. The sender still works when only the basic chat
  -- migration has been installed and chat_mutes does not exist yet.
  if to_regclass('public.chat_mutes') is not null then
    execute 'select exists (select 1 from public.chat_mutes where user_id = $1 and expires_at > now())'
      into is_muted
      using auth.uid();
  end if;
  if is_muted then
    raise exception 'You are temporarily muted.';
  end if;

  insert into public.chat_messages (
    user_id,
    channel_id,
    content,
    sticker_url,
    reply_to_id,
    thread_root_id,
    status
  )
  values (
    auth.uid(),
    p_channel_id,
    clean_content,
    p_sticker_url,
    p_reply_to_id,
    p_thread_root_id,
    'published'
  )
  returning id into new_message_id;

  return new_message_id;
end;
$$;

revoke all on function public.send_chat_message(uuid,text,text,bigint,bigint) from public;
grant execute on function public.send_chat_message(uuid,text,text,bigint,bigint) to authenticated;
