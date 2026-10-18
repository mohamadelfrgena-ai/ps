-- ============================================================================
--  AddonStation Mobile — 40) خصوصية أصول الأدمن
--
--  القاعدة:
--    • الأصل ذو required_plan = 'admin' لا يظهر في كتالوج أي مستخدم عادي.
--    • إن اختار الأدمن أفاتاراً/غلافاً/أداة خاصة، تُحجب قيمتها عن بقية الحسابات.
--    • ملفات الأدمن الجديدة تُحفظ في admin-assets الخاص، ولا تُقرأ إلا بدور admin.
--    • الملفات الشخصية وصور المستخدمين العاديين تبقى ظاهرة كما كانت.
--
--  ترتيب النشر:
--    وزّع أولاً نسخة التطبيق التي تقرأ visible_profiles (وفيها fallback للقاعدة
--    القديمة)، ثم نفّذ هذا الملف؛ فالنسخ الأقدم كانت تقرأ profiles مباشرة.
--
--  مهم للأصول القديمة:
--    هذا الملف يحجب الصفوف والقيم فوراً، لكنه لا يستطيع نقل بايتات Storage بين
--    المخازن. شغّل tools/migrate_admin_assets_private.py بعده لنقل الملفات القديمة
--    من site-assets العام إلى admin-assets الخاص وحذف النسخة العامة بأمان.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) كتالوج الأصول: صفوف admin لا يقرأها إلا الأدمن
-- ---------------------------------------------------------------------------
drop policy if exists assets_read on public.site_assets;
create policy assets_read on public.site_assets
  for select using (
    public.is_admin()
    or (enabled = true and required_plan <> 'admin')
  );

-- ---------------------------------------------------------------------------
-- 2) مسار قانوني ثابت للأصول الخاصة
--
--    نحدّث مراجع الحساب أولاً ثم صف الكتالوج. هكذا تبقى إعادة 00_ALL_IN_ONE
--    آمنة حتى بعد نقل الملفات: 15/27 قد يعيدان المسار العام، و40 يعيده دائماً
--    إلى admin-private/... بينما يبقى الملف الفعلي في admin-assets.
-- ---------------------------------------------------------------------------
update public.profiles p
   set avatar_url = 'admin-private/' || ltrim(p.avatar_url, '/')
 where p.avatar_url not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'avatar'
        and a.file_url = p.avatar_url
   );

update public.profiles p
   set banner_url = 'admin-private/' || ltrim(p.banner_url, '/')
 where p.banner_url is not null
   and p.banner_url not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'banner'
        and a.file_url = p.banner_url
   );

-- نسخ قديمة جداً كانت تحفظ مسار الأداة بدلاً من اسمها.
update public.profiles p
   set equipped_item_id = 'admin-private/' || ltrim(p.equipped_item_id, '/')
 where p.equipped_item_id not like 'admin-private/%'
   and exists (
     select 1 from public.site_assets a
      where a.required_plan = 'admin'
        and a.asset_type = 'item'
        and a.file_url = p.equipped_item_id
   );

update public.site_assets
   set file_url = 'admin-private/' || ltrim(file_url, '/')
 where required_plan = 'admin'
   and file_url not like 'admin-private/%';

-- ---------------------------------------------------------------------------
-- 3) دالة داخلية تعرف هل القيمة أفاتار/غلاف/أداة خاصة بالأدمن
--    SECURITY DEFINER مطلوب لأن غير الأدمن لا يستطيع قراءة صف admin نفسه.
--    لا تُمنح للمستخدمين مباشرة؛ يستعملها العرض الآمن فقط.
-- ---------------------------------------------------------------------------
create or replace function public.is_admin_only_profile_asset(
  p_kind text,
  p_value text
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(p_value, '') <> ''
     and exists (
       select 1
         from public.site_assets a
        where a.required_plan = 'admin'
          and a.enabled
          and a.asset_type = case when p_kind = 'item' then 'item' else p_kind end
          and (a.file_url = p_value or a.name = p_value)
     );
$$;

revoke all on function public.is_admin_only_profile_asset(text, text) from public;
revoke all on function public.is_admin_only_profile_asset(text, text) from anon, authenticated;

comment on function public.is_admin_only_profile_asset(text, text) is
  'داخلي: يفحص إن كانت قيمة ملف شخصي تشير إلى أصل admin؛ يستعمله visible_profiles لحجبها.';

-- ---------------------------------------------------------------------------
-- 4) عرض الملفات الشخصية العام: نفس الأعمدة، مع تنقيح أصول admin فقط
--
--    العرض يعمل بصلاحية مالكه حتى يستطيع قراءة profiles بعد سحب SELECT المباشر،
--    لكنه يقرر الإظهار بهوية المستدعي الفعلية عبر auth.uid()/public.is_admin().
-- ---------------------------------------------------------------------------
create or replace view public.visible_profiles
with (security_barrier = true)
as
select
  p.id,
  p.username,
  p.display_name,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('avatar', p.avatar_url)
      then p.avatar_url
    else null
  end as avatar_url,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('banner', p.banner_url)
      then p.banner_url
    else null
  end as banner_url,
  p.custom_status,
  p.bio,
  p.country,
  p.role,
  p.plan,
  p.plan_expires_at,
  p.user_level,
  case
    when public.is_admin()
      or not public.is_admin_only_profile_asset('item', p.equipped_item_id)
      then p.equipped_item_id
    else 'none'
  end as equipped_item_id,
  p.show_online_status,
  p.last_seen_at,
  p.created_at,
  p.updated_at
from public.profiles p;

comment on view public.visible_profiles is
  'واجهة القراءة العامة للملفات الشخصية؛ تحجب أفاتار/غلاف/أداة admin عن غير الأدمن.';

revoke all on public.visible_profiles from public;
grant select on public.visible_profiles to anon, authenticated;

-- لا نسمح بتجاوز العرض عبر REST إلى الجدول الأساسي، ولا بتعيين أداة مباشرة
-- متجاوزاً set_profile_asset وفحص required_plan.
revoke select on public.profiles from public, anon, authenticated;
revoke update (equipped_item_id) on public.profiles from authenticated;

-- ---------------------------------------------------------------------------
-- 5) مخزن خاص لأصول الأدمن (المسار المحجوز داخل التطبيق: admin-private/...)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'admin-assets',
  'admin-assets',
  false,
  5242880,
  array['image/png','image/jpeg','image/webp','image/gif','image/svg+xml']
)
on conflict (id) do update
  set public = false,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "admin_assets_read" on storage.objects;
create policy "admin_assets_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_insert" on storage.objects;
create policy "admin_assets_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_update" on storage.objects;
create policy "admin_assets_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin())
  with check (bucket_id = 'admin-assets' and public.is_admin());

drop policy if exists "admin_assets_delete" on storage.objects;
create policy "admin_assets_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'admin-assets' and public.is_admin());

-- اجعل PostgREST يرى visible_profiles مباشرة بعد التنفيذ.
notify pgrst, 'reload schema';

-- ---------------------------------------------------------------------------
-- 6) تحقق وصفي (لا يفشل إن كانت الأصول القديمة لم تُنقل بعد؛ يعرض عددها)
-- ---------------------------------------------------------------------------
do $$
declare
  v_hidden int;
  v_pending int;
begin
  select count(*) into v_hidden
    from public.site_assets
   where required_plan = 'admin';

  select count(*) into v_pending
    from public.site_assets a
   where a.required_plan = 'admin'
     and (
       not exists (
         select 1 from storage.objects o
          where o.bucket_id = 'admin-assets' and o.name = a.file_url
       )
       or exists (
         select 1 from storage.objects o
          where o.bucket_id = 'site-assets'
            and o.name = regexp_replace(a.file_url, '^admin-private/', '')
       )
     );

  raise notice '✅ خصوصية كتالوج الأدمن مفعلة: % أصل مخفي عن غير الأدمن.', v_hidden;
  if v_pending > 0 then
    raise notice 'ℹ % ملف admin قديم ينتظر النقل للمخزن الخاص عبر tools/migrate_admin_assets_private.py.', v_pending;
  else
    raise notice '✅ كل ملفات admin مسجلة بمسارات المخزن الخاص.';
  end if;
end;
$$;
