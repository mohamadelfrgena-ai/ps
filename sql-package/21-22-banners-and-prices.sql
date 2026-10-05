-- ============================================================================
--  AddonStation Mobile — حزمة جاهزة: الأغلفة (21) + أسعار الاشتراكات (22)
--
--  ملف واحد يجمع ملفّين بالترتيب الصحيح — انسخه كاملاً والصقه في
--  Supabase → SQL Editor → Run (آمن لإعادة التنفيذ).
--
--      1) 22_plan_prices  → Epic = 2.99$ / 20 د.ل · Legendary = 5.99$ / 30 د.ل
--      2) 21_banners      → 40 غلافاً في فقرتَين (Nature 🌿 · Random 🎲)
--                            كلها مقفولة باشتراك · gif متحرّك
--
--  قبل التنفيذ: ارفع صور الأغلفة إلى المخزن
--       (Actions → Upload profile banners → Run workflow بـ upload)
--       وإلا سُجّلت الأغلفة بأسمائها بلا صور.
--
--  يُنفَّذ مرة واحدة.
-- ============================================================================

-- ############################################################################
-- #  22_plan_prices.sql
-- ############################################################################
-- ٠) تأكيد وجود الخطط الثلاث (إن نُفِّذ الملف على قاعدة فارغة)
insert into public.subscription_plans (id, name, tagline, price_lyd, price_usd, duration_days,
                                       download_limit, limit_period, active, sort_order)
values
  ('free', 'Free', 'Start exploring free addons and worlds.',
     0, 0, 30, 5, 'day', true, 1),
  ('epic', 'Epic', 'More content and a wider play experience.',
     20, 2.99, 30, 30, 'day', true, 2),
  ('legendary', 'Legendary', 'Unlock premium content and VIP privileges.',
     30, 5.99, 30, null, 'none', true, 3)
on conflict (id) do update
  set name = excluded.name,
      tagline = excluded.tagline,
      duration_days = excluded.duration_days,
      download_limit = excluded.download_limit,
      limit_period = excluded.limit_period,
      sort_order = excluded.sort_order,
      active = true;

-- ١) السعر بالدولار هو المطلوب الآن
update public.subscription_plans set price_usd = 2.99, updated_at = now() where id = 'epic';
update public.subscription_plans set price_usd = 5.99, updated_at = now() where id = 'legendary';
update public.subscription_plans set price_usd = 0,    updated_at = now() where id = 'free';

-- ٢) السعر بالدينار: Epic = 20 د.ل · Legendary = 30 د.ل (يُفرض صراحةً)
update public.subscription_plans set price_lyd = 20, updated_at = now() where id = 'epic';
update public.subscription_plans set price_lyd = 30, updated_at = now() where id = 'legendary';
update public.subscription_plans set price_lyd = 0,  updated_at = now() where id = 'free';

-- ٣) سعر دينار تقديري إن كانت الخطة بلا سعر (حتى لا تُعرض مجانية بالخطأ)
update public.subscription_plans
   set price_lyd = 20, updated_at = now()
 where id = 'epic' and coalesce(price_lyd, 0) <= 0;
update public.subscription_plans
   set price_lyd = 30, updated_at = now()
 where id = 'legendary' and coalesce(price_lyd, 0) <= 0;

-- ---------------------------------------------------------------------------
-- تحقّق: اطّلع على الجدول بعد التنفيذ (يجب: epic 2.99 · legendary 5.99)
-- ---------------------------------------------------------------------------
select id            as "الخطة",
       price_usd     as "بالدولار",
       price_lyd     as "بالدينار",
       duration_days as "المدة (يوم)",
       active        as "مفعّلة"
  from public.subscription_plans
 order by sort_order;

do $$
declare
  v_epic      numeric;
  v_legend    numeric;
  v_epic_lyd  numeric;
  v_leg_lyd   numeric;
begin
  select price_usd, price_lyd into v_epic,   v_epic_lyd from public.subscription_plans where id = 'epic';
  select price_usd, price_lyd into v_legend, v_leg_lyd  from public.subscription_plans where id = 'legendary';

  if v_epic is null or v_legend is null then
    raise exception '✗ خطتا epic/legendary غير موجودتين — نفّذ 01_schema.sql أولاً.';
  end if;

  if v_epic <> 2.99 or v_legend <> 5.99 then
    raise exception '✗ أسعار الدولار لم تُحدَّث: epic = % وlegendary = %', v_epic, v_legend;
  end if;

  if v_epic_lyd <> 20 or v_leg_lyd <> 30 then
    raise exception '✗ أسعار الدينار لم تُحدَّث: epic = % وlegendary = %', v_epic_lyd, v_leg_lyd;
  end if;

  raise notice '✅ 22_plan_prices: Epic = %$ / % د.ل · Legendary = %$ / % د.ل (30 يوماً).',
               v_epic, v_epic_lyd, v_legend, v_leg_lyd;
end;
$$;

-- ############################################################################
-- #  21_banners.sql
-- ############################################################################
insert into public.site_assets
  (asset_type, name, file_url, sticker_pack, required_plan, animated,
   collection, collection_order, sort_order)
values
  ('banner', 'o2rigbig', 'banners/nature/o2rigbig.png', null, 'epic', false, 'Nature 🌿', 22, 1),
  ('banner', 'o36rigbig', 'banners/nature/o36rigbig.png', null, 'epic', false, 'Nature 🌿', 22, 2),
  ('banner', 'o3rigbig', 'banners/nature/o3rigbig.png', null, 'epic', false, 'Nature 🌿', 22, 3),
  ('banner', 'orig5big', 'banners/nature/orig5big.png', null, 'epic', false, 'Nature 🌿', 22, 4),
  ('banner', 'origbig', 'banners/nature/origbig.png', null, 'epic', false, 'Nature 🌿', 22, 5),
  ('banner', 'Summer1', 'banners/nature/Summer1.png', null, 'epic', false, 'Nature 🌿', 22, 6),
  ('banner', 'Summer2', 'banners/nature/Summer2.png', null, 'epic', false, 'Nature 🌿', 22, 7),
  ('banner', 'Summer3', 'banners/nature/Summer3.png', null, 'epic', false, 'Nature 🌿', 22, 8),
  ('banner', 'Summer4', 'banners/nature/Summer4.png', null, 'epic', false, 'Nature 🌿', 22, 9),
  ('banner', 'Summer5', 'banners/nature/Summer5.png', null, 'epic', false, 'Nature 🌿', 22, 10),
  ('banner', 'Summer6', 'banners/nature/Summer6.png', null, 'epic', false, 'Nature 🌿', 22, 11),
  ('banner', 'Summer7', 'banners/nature/Summer7.png', null, 'epic', false, 'Nature 🌿', 22, 12),
  ('banner', 'Summer8', 'banners/nature/Summer8.png', null, 'epic', false, 'Nature 🌿', 22, 13),
  ('banner', 'Banner 1', 'banners/random/16349-480013.jpg', null, 'epic', false, 'Random 🎲', 23, 1),
  ('banner', 'gothicbanner', 'banners/random/34808-494692-gothicbanner.gif', null, 'epic', true, 'Random 🎲', 23, 2),
  ('banner', 'siren head', 'banners/random/42438-438954-siren-head.jpg', null, 'epic', false, 'Random 🎲', 23, 3),
  ('banner', 'exzy turu', 'banners/random/4298-exzy-turu.png', null, 'epic', false, 'Random 🎲', 23, 4),
  ('banner', 'patrick jane', 'banners/random/45402-295484-patrick-jane.gif', null, 'epic', true, 'Random 🎲', 23, 5),
  ('banner', 'none', 'banners/random/49705-114558-none.gif', null, 'epic', true, 'Random 🎲', 23, 6),
  ('banner', 'night', 'banners/random/53568-997286-night.jpg', null, 'epic', false, 'Random 🎲', 23, 7),
  ('banner', 'baby girl', 'banners/random/60529-527207-baby-girl.gif', null, 'epic', true, 'Random 🎲', 23, 8),
  ('banner', 'drip', 'banners/random/63256-612217-drip.jpg', null, 'epic', false, 'Random 🎲', 23, 9),
  ('banner', 'simple', 'banners/random/73372-642531-simple.jpg', null, 'epic', false, 'Random 🎲', 23, 10),
  ('banner', 'kawaii gothic', 'banners/random/75143-329583-kawaii-gothic.png', null, 'epic', false, 'Random 🎲', 23, 11),
  ('banner', 'all roads lead to rome', 'banners/random/89195-572283-all-roads-lead-to-rome.gif', null, 'epic', true, 'Random 🎲', 23, 12),
  ('banner', 'vinland saga', 'banners/random/89295-797032-vinland-saga.jpg', null, 'epic', false, 'Random 🎲', 23, 13),
  ('banner', 'bmw', 'banners/random/97427-337568-bmw.gif', null, 'epic', true, 'Random 🎲', 23, 14),
  ('banner', 'clarence outro', 'banners/random/clarence-outro.jpg', null, 'epic', false, 'Random 🎲', 23, 15),
  ('banner', 'download (1)', 'banners/random/download-1.jpg', null, 'epic', false, 'Random 🎲', 23, 16),
  ('banner', 'download (12)', 'banners/random/download-12.jpg', null, 'epic', false, 'Random 🎲', 23, 17),
  ('banner', 'download (3)', 'banners/random/download-3.jpg', null, 'epic', false, 'Random 🎲', 23, 18),
  ('banner', 'download', 'banners/random/download.gif', null, 'epic', true, 'Random 🎲', 23, 19),
  ('banner', 'e1feaf1251c78d0f48a198d8b453592a', 'banners/random/e1feaf1251c78d0f48a198d8b453592a.jpg', null, 'epic', false, 'Random 🎲', 23, 20),
  ('banner', 'fondo playyy', 'banners/random/fondo-playyy.jpg', null, 'epic', false, 'Random 🎲', 23, 21),
  ('banner', 'Gumball and Darwin Minecraft icon', 'banners/random/Gumball-and-Darwin-Minecraft-icon.jpg', null, 'epic', false, 'Random 🎲', 23, 22),
  ('banner', 'Java Edition Technically Updated', 'banners/random/Java-Edition-Technically-Updated.jpg', null, 'epic', false, 'Random 🎲', 23, 23),
  ('banner', 'Minecraft sky with clouds wallpaper', 'banners/random/Minecraft-sky-with-clouds-wallpaper.jpg', null, 'epic', false, 'Random 🎲', 23, 24),
  ('banner', 'orig4big', 'banners/random/orig4big.png', null, 'epic', false, 'Random 🎲', 23, 25),
  ('banner', 'Pin by the strawberry fairy on ♡ minecraft…', 'banners/random/Pin-by-the-strawberry-fairy-on-minecraft-_-Minecraft-wallpaper-Minecraft-images-Background-529.gif', null, 'epic', true, 'Random 🎲', 23, 26),
  ('banner', 'wp15310214 minecraft platformer wallpapers', 'banners/random/wp15310214-minecraft-platformer-wallpapers.png', null, 'epic', false, 'Random 🎲', 23, 27)
on conflict (file_url) do update
  set name = excluded.name,
      asset_type = excluded.asset_type,
      sticker_pack = excluded.sticker_pack,
      required_plan = excluded.required_plan,
      animated = excluded.animated,
      collection = excluded.collection,
      collection_order = excluded.collection_order,
      sort_order = excluded.sort_order,
      enabled = true;

-- ---------------------------------------------------------------------------
-- تحقّق: عدد البانرات في كل قسم + هل هي مقفولة
-- ---------------------------------------------------------------------------
select collection   as "القسم",
       count(*)     as "العدد",
       count(*) filter (where required_plan <> 'free') as "المقفول",
       count(*) filter (where animated)                as "متحرك"
  from public.site_assets
 where asset_type = 'banner'
 group by collection, collection_order
 order by collection_order;

do $$
declare
  v_total int;
begin
  select count(*) into v_total from public.site_assets where asset_type = 'banner';
  if v_total = 0 then
    raise exception '✗ لا توجد بانرات مسجّلة — نفّذ هذا الملف بعد رفع الصور.';
  end if;
  raise notice '✅ 21_banners: % بانراً مسجّلاً (مقفولة باشتراك · gif متحرّك).', v_total;
end;
$$;
