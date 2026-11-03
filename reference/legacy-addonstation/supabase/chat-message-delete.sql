grant delete on public.chat_messages to authenticated;

drop policy if exists "Users delete own chat messages" on public.chat_messages;
drop policy if exists "Moderators delete chat messages" on public.chat_messages;

create policy "Users delete own chat messages"
on public.chat_messages
for delete
to authenticated
using (auth.uid() = user_id);

create policy "Moderators delete chat messages"
on public.chat_messages
for delete
to authenticated
using (public.is_chat_moderator());
