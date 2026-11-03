-- ============================================================================
--  36) إحصاءات الملف الشخصي — دالة قراءة واحدة للجميع (أرقام فقط)
-- ============================================================================
--  لماذا؟
--    شاشات الملف الشخصي الجديدة (وثيقة AddonStation_profiles) تعرض:
--      • شريط إحصاءات: أصدقاء · تنزيلات · مفضلة      (لقسم ٢٫٤)
--      • بطاقة المستوى: «المستوى [N] · [x]/100»       (لقسم ٢٫٥)
--      • محفظة AC: «[N] يوم متتالٍ» (سلسلة الاستلام)   (لقسم ٢٫٧)
--    وهذه الأرقام **محميّة** بسياسات RLS: الأصدقاء والمفضّلة والتنزيلات
--    والمحفظة تُقرأ لصاحبها فقط. فمن يفتح ملف عضو آخر (شاشة B) لا يستطيع
--    حسابها من التطبيق. الحلّ: دالة `security definer` تُرجِع **أعداداً فقط**
--    (بلا أي بيانات شخصية) لأي عضو.
--
--  قاعدة المستوى (متفق عليها مع المالك):
--      xp = إجمالي AC المكتسَبة (ac_wallets.total_earned)
--      كل 100 AC مكتسَبة = مستوى واحد
--      level = greatest(user_level المحفوظ, 1 + xp/100)
--        ⇒ تعديل الإدارة للمستوى يبقى فوق القاعدة، والنشاط يرفعه من نفسه.
--      xp_in_level = xp % 100  (الرقم الذي يظهر «[x]/100 للمستوى التالي»)
--
--  مكافأة اليوم: المهمّة اليومية (أول مهمّة نشطة تكرارها daily أو streak مرتّبة
--  بـ sort_order) — نُرجِع مقدارها وهل استُلمت اليوم، فتُعطَّل زرّ «مكافأة اليوم»
--  في الواجهة بلا محاولة فاشلة.
--
--  سلسلة الأيام (streak): أطول سلسلة منتهية باليوم أو بالأمس من أيام استلام
--  المهامّ (ac_task_claims) — تُحسب بطريقة «الفواصل والجزر» على التواريخ.
--
--  اسم الدالة في فهرس PostgREST: 'profile_stats'
--      POST /rest/v1/rpc/profile_stats   { "p_user_id": "<uuid>" }
--      وإذا نُسيت الدالة على الخادم يردّ 404 ⇒ التطبيق يعرض ما يعرفه محلياً بلا كسر.
--
--  آمن للتكرار (create or replace) · لا يُغيّر أي بيانات · للقراءة فقط.
-- ============================================================================

create or replace function public.profile_stats(p_user_id uuid default null)
returns table (
  friends     int,
  downloads   int,
  favorites   int,
  level       int,
  xp_in_level int,
  xp_to_next  int,
  streak      int,
  daily_claimed boolean,
  daily_reward  int
)
language sql
stable
security definer
set search_path = public
as $fn$
  with me as (
    select coalesce(p_user_id, auth.uid()) as uid
  ),
  numbers as (
    select
      (select count(*)::int from public.user_friends f
        where f.status = 'accepted'
          and (f.requester_id = m.uid or f.receiver_id = m.uid)) as friends,
      (select count(*)::int from public.content_downloads d
        where d.user_id = m.uid) as downloads,
      (select count(*)::int from public.content_favorites v
        where v.user_id = m.uid) as favorites,
      coalesce((select w.total_earned from public.ac_wallets w
                 where w.user_id = m.uid), 0) as earned,
      coalesce((select p.user_level from public.profiles p
                 where p.id = m.uid), 1) as stored_level,
      -- المهمّة اليومية (daily_login في البذرة): مقدارها وهل استُلمت اليوم
      (select t.reward from public.ac_tasks t
        where t.active and t.frequency in ('daily', 'streak')
        order by t.sort_order, t.id
        limit 1) as daily_reward,
      coalesce((select true from public.ac_task_claims c
                 join public.ac_tasks t on t.id = c.task_id
                where c.user_id = m.uid
                  and t.active and t.frequency in ('daily', 'streak')
                  and c.day = (now() at time zone 'utc')::date
                limit 1), false) as daily_claimed
    from me m
  ),
  -- أيام الاستلام (متميّزة) ثم ترقيم لتكوين الجزر المتتالية
  days as (
    select distinct c.day
      from public.ac_task_claims c
      join me m on m.uid = c.user_id
  ),
  numbered as (
    select d.day, (row_number() over (order by d.day))::int as rn
      from days d
  ),
  runs as (
    -- (اليوم − رقمه) يبقى ثابتاً داخل السلسلة المتتالية
    select n.day - n.rn as grp, count(*)::int as len, max(n.day) as last_day
      from numbered n
     group by n.day - n.rn
  ),
  streak_run as (
    select coalesce((
      select r.len
        from runs r
       where r.last_day >= (now() at time zone 'utc')::date - 1
       order by r.last_day desc
       limit 1
    ), 0) as streak
  )
  select
    n.friends,
    n.downloads,
    n.favorites,
    greatest(n.stored_level, 1 + (n.earned / 100)) as level,
    (n.earned % 100)                                as xp_in_level,
    100                                              as xp_to_next,
    s.streak,
    n.daily_claimed,
    coalesce(n.daily_reward, 0)                      as daily_reward
  from numbers n, streak_run s;
$fn$;

comment on function public.profile_stats(uuid) is
  'إحصاءات الملف الشخصي (أعداد فقط): أصدقاء · تنزيلات · مفضلة · المستوى · تقدّم المستوى · سلسلة الأيام · مكافأة اليوم (مقدارها وهل استُلمت). المستوى = max(user_level, 1 + إجمالي AC/100).';

-- الأمان: لا يقرأها إلا مستخدم مسجَّل (الزائر لا يحتاج أرقام الأعضاء)
revoke all on function public.profile_stats(uuid) from public, anon;
grant execute on function public.profile_stats(uuid) to authenticated;

-- تحديث فهرس PostgREST فوراً حتى لا يردّ 404 لدالة جديدة (درس سابق)
notify pgrst, 'reload schema';
