-- Only Legendary users (and the admin) can equip items beside their chat name.
create or replace function public.enforce_equipped_item_access()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.equipped_item_id is distinct from old.equipped_item_id then
    if new.equipped_item_id <> 'none'
      and new.plan <> 'legendary'
      and new.role <> 'admin' then
      raise exception 'Chat items require Legendary subscription.';
    end if;
    if new.equipped_item_id = 'Enchanted_Bedrock'
      and new.role <> 'admin' then
      raise exception 'This item is reserved for the administrator.';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists before_profile_item_access on public.profiles;
create trigger before_profile_item_access
before update on public.profiles
for each row execute procedure public.enforce_equipped_item_access();
