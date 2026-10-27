-- تثبيتات: قيد المخطط أضيق من البيانات الحية
alter table public.site_assets drop constraint if exists site_assets_required_plan_check;
