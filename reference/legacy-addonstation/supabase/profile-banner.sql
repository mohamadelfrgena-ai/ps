alter table public.profiles add column if not exists banner_url text;

alter table public.site_assets drop constraint if exists site_assets_asset_type_check;
alter table public.site_assets add constraint site_assets_asset_type_check
check (asset_type in ('avatar', 'banner', 'sticker', 'chat_item'));
