-- ============================================================================
--  AddonStation Mobile — 22) أسعار الاشتراكات (Epic 2.99$ · Legendary 5.99$)
--
--  الطلب: «غيّر أسعار الاشتراكات: epic = 2.99$ وlegendary = 5.99$»
--
--  هذا الملف يفرض السعرين بالدولار في قاعدة البيانات مباشرة (بلا انتظار بذرة
--  جديدة)، وهو **آمن لإعادة التنفيذ** (idempotent):
--      • Epic      💎 = 2.99 USD  لمدة 30 يوماً
--      • Legendary 👑 = 5.99 USD  لمدة 30 يوماً
--      • Free      🍃 = 0
--
--  ⚠️ هذا الملف **يفرض** السعرين معاً (الدينار والدولار). فإن عدّلت السعر
--     من لوحة الإدارة (الأدمن → الخطط والدفع) فلا تُعد تنفيذ هذا الملف،
--     وإلا أعاد القيم المكتوبة هنا (20 / 30 دينار · 2.99 / 5.99 دولار).
--     وإن كانت الخطة بلا سعر دينار (صفر) نضع له قيمة تقديرية مقابلة
--      (Epic 20 د.ل · Legendary 30 د.ل) — ويُفرض الآن صراحةً بلا شرط.
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

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
