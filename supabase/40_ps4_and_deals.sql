-- ============================================================================
--  AddonStation — 40) دعم تطبيق PS4/PS5 + العروض والبانرات + ربط الكونسول بالكود
--
--  يربط تطبيق الهاتف + الكمبيوتر + البلايستيشن (PS4/PS5) بقاعدة بيانات واحدة:
--    1) أعمدة العروض والحصريات وملفات PS4 (.raw على GitHub Releases) في content_items.
--    2) جدول بانرات العروض الرئيسية (store_banners) بـ 8 لغات مع العدّاد التنازلي.
--    3) جدول ودوال ربط جهاز السوني بالكود المكون من 6 خانات (Sign in with a code).
--    4) تحديث ac_unlock_content لاحتساب سعر الخصم (sale_ac_price) تلقائياً من الخادم،
--       وتحديث record_content_download لإرجاع رابط ps4_raw_url و pack_targets.
--
--  آمن لإعادة التنفيذ (Idempotent) في Supabase -> SQL Editor.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) توسيع جدول المحتوى (content_items) لدعم التخفيضات والحصريات وملفات PS4
-- ---------------------------------------------------------------------------
alter table public.content_items
  add column if not exists sale_ac_price  int check (sale_ac_price is null or sale_ac_price >= 0),
  add column if not exists sale_ends_at   timestamptz,
  add column if not exists exclusive_slot smallint check (exclusive_slot is null or exclusive_slot in (1, 2, 3)),
  add column if not exists ps4_raw_url    text,
  add column if not exists pack_targets   jsonb not null default '[]'::jsonb;

create index if not exists content_items_deals_idx
  on public.content_items(is_published, sale_ac_price, sale_ends_at);

create index if not exists content_items_exclusives_idx
  on public.content_items(is_published, is_exclusive, exclusive_slot);

-- دالة مساعدة ترجع السعر الفعلي الحالي بالـ AC (تراعي الخصم النشط)
create or replace function public.effective_item_ac_price(p_item public.content_items)
returns int
language sql stable set search_path = public
as $$
  select case
    when p_item.sale_ac_price is not null
         and p_item.sale_ac_price > 0
         and p_item.sale_ac_price < coalesce(p_item.ac_price, 0)
         and (p_item.sale_ends_at is null or p_item.sale_ends_at > now())
    then p_item.sale_ac_price
    else coalesce(p_item.ac_price, 0)
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2) جدول بانرات العروض (Home Offer Banners & Deals Header)
-- ---------------------------------------------------------------------------
create table if not exists public.store_banners (
  id                   uuid primary key default gen_random_uuid(),
  slug                 text unique not null,
  theme                text not null default 'winter'
                       check (theme in ('winter', 'sunset', 'night')),
  kicker_i18n          jsonb not null default '{}'::jsonb,
  title_i18n           jsonb not null default '{}'::jsonb,
  subtitle_i18n        jsonb not null default '{}'::jsonb,
  primary_label_i18n   jsonb not null default '{}'::jsonb,
  secondary_label_i18n jsonb not null default '{}'::jsonb,
  badge_text           text,                                    -- مثال: '-50%' أو 'NEW' أو 'TOP'
  discount_percent     int not null default 0,
  cover_url            text,
  target_type          text not null default 'deals'
                       check (target_type in ('deals', 'item', 'creators', 'store')),
  target_item_id       uuid references public.content_items(id) on delete set null,
  ends_at              timestamptz,
  sort_order           int not null default 0,
  enabled              boolean not null default true,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create index if not exists store_banners_active_idx
  on public.store_banners(enabled, sort_order, created_at desc);

alter table public.store_banners enable row level security;

drop policy if exists "store_banners_read_all" on public.store_banners;
create policy "store_banners_read_all"
  on public.store_banners for select
  using (enabled = true or public.is_admin(auth.uid()));

drop policy if exists "store_banners_admin_write" on public.store_banners;
create policy "store_banners_admin_write"
  on public.store_banners for all
  using (public.is_admin(auth.uid()))
  with check (public.is_admin(auth.uid()));

-- بيانات مبدئية للبانرات الثلاثة المطابقة للتصميم (Winter Sale / Sunset Village / Creator Spotlight)
insert into public.store_banners
  (slug, theme, kicker_i18n, title_i18n, subtitle_i18n, primary_label_i18n, secondary_label_i18n,
   badge_text, discount_percent, target_type, ends_at, sort_order, enabled)
values
  (
    'winter-sale',
    'winter',
    '{"en":"LIMITED TIME","ar":"لفترة محدودة","fr":"TEMPS LIMITÉ","tr":"SINIRLI SÜRE","es":"TIEMPO LIMITADO","ru":"ОГРАНИЧЕННОЕ ВРЕМЯ","pt":"TEMPO LIMITADO","zh":"限时特惠"}'::jsonb,
    '{"en":"WINTER SALE","ar":"تخفيضات الشتاء","fr":"SOLDES D''HIVER","tr":"KIŞ İNDİRİMİ","es":"OFERTAS DE INVIERNO","ru":"ЗИМНЯЯ РАСПРОДАЖА","pt":"PROMOÇÃO DE INVERNO","zh":"冬季特卖"}'::jsonb,
    '{"en":"Up to 50% off the best addons and worlds","ar":"خصم حتى ٥٠٪ على أفضل الإضافات والعوالم","fr":"Jusqu''à -50% sur les meilleurs addons et mondes","tr":"En iyi eklenti ve dünyalarda %50''ye varan indirim","es":"Hasta un 50% de descuento en los mejores addons y mundos","ru":"Скидки до 50% на лучшие аддоны и миры","pt":"Até 50% de desconto nos melhores addons e mundos","zh":"精选模组与世界低至五折"}'::jsonb,
    '{"en":"Shop the sale","ar":"تسوق التخفيضات","fr":"Voir les offres","tr":"İndirimleri gör","es":"Ver ofertas","ru":"К распродаже","pt":"Ver ofertas","zh":"立即抢购"}'::jsonb,
    '{"en":"Details","ar":"التفاصيل","fr":"Détails","tr":"Detaylar","es":"Detalles","ru":"Подробнее","pt":"Detalhes","zh":"详情"}'::jsonb,
    '-50%',
    50,
    'deals',
    now() + interval '3 days 12 hours 45 minutes',
    1,
    true
  ),
  (
    'sunset-village',
    'sunset',
    '{"en":"NEW WORLD","ar":"عالم جديد","fr":"NOUVEAU MONDE","tr":"YENİ DÜNYA","es":"NUEVO MUNDO","ru":"НОВЫЙ МИР","pt":"NOVO MUNDO","zh":"全新世界"}'::jsonb,
    '{"en":"SUNSET VILLAGE","ar":"قرية الغروب","fr":"VILLAGE DU COUCHANT","tr":"GÜN BATIMI KÖYÜ","es":"ALDEA DEL OCASO","ru":"ДЕРЕВНЯ ЗАКАТА","pt":"VILA DO PÔR DO SOL","zh":"落日村庄"}'::jsonb,
    '{"en":"Explore the new world by AddonStation","ar":"استكشف العالم الجديد من AddonStation","fr":"Explorez le nouveau monde par AddonStation","tr":"AddonStation tarafından yapılan yeni dünyayı keşfedin","es":"Explora el nuevo mundo de AddonStation","ru":"Исследуйте новый мир от AddonStation","pt":"Explore o novo mundo por AddonStation","zh":"探索 AddonStation 打造的全新世界"}'::jsonb,
    '{"en":"View world","ar":"عرض العالم","fr":"Voir le monde","tr":"Dünyayı gör","es":"Ver mundo","ru":"Открыть мир","pt":"Ver mundo","zh":"查看世界"}'::jsonb,
    '{"en":"Save","ar":"حفظ","fr":"Enregistrer","tr":"Kaydet","es":"Guardar","ru":"Сохранить","pt":"Salvar","zh":"收藏"}'::jsonb,
    'NEW',
    0,
    'store',
    null,
    2,
    true
  ),
  (
    'creator-spotlight',
    'night',
    '{"en":"COMMUNITY","ar":"المجتمع","fr":"COMMUNAUTÉ","tr":"TOPLULUK","es":"COMUNIDAD","ru":"СООБЩЕСТВО","pt":"COMUNIDADE","zh":"社区"}'::jsonb,
    '{"en":"CREATOR SPOTLIGHT","ar":"مبدع الأسبوع","fr":"CRÉATEURS À LA UNE","tr":"HAFTANIN ÜRETİCİSİ","es":"CREADOR DESTACADO","ru":"АВТОР НЕДЕЛИ","pt":"CRIADOR EM DESTAQUE","zh":"本周创作者"}'::jsonb,
    '{"en":"Meet this week''s featured creators","ar":"تعرّف على مبدعي هذا الأسبوع","fr":"Découvrez les créateurs vedettes de la semaine","tr":"Bu haftanın öne çıkan üreticileriyle tanışın","es":"Conoce a los creadores destacados de esta semana","ru":"Познакомьтесь с лучшими авторами недели","pt":"Conheça os criadores em destaque desta semana","zh":"认识本周精选创作者"}'::jsonb,
    '{"en":"Meet them","ar":"تعرّف عليهم","fr":"Les découvrir","tr":"Tanış","es":"Conócelos","ru":"Познакомиться","pt":"Conhecer","zh":"了解他们"}'::jsonb,
    '{"en":"Follow","ar":"متابعة","fr":"Suivre","tr":"Takip et","es":"Seguir","ru":"Подписаться","pt":"Seguir","zh":"关注"}'::jsonb,
    'TOP',
    0,
    'creators',
    null,
    3,
    true
  )
on conflict (slug) do update
  set theme                = excluded.theme,
      kicker_i18n          = excluded.kicker_i18n,
      title_i18n           = excluded.title_i18n,
      subtitle_i18n        = excluded.subtitle_i18n,
      primary_label_i18n   = excluded.primary_label_i18n,
      secondary_label_i18n = excluded.secondary_label_i18n,
      badge_text           = excluded.badge_text,
      discount_percent     = excluded.discount_percent,
      target_type          = excluded.target_type,
      ends_at              = excluded.ends_at,
      enabled              = true,
      updated_at           = now();

-- ---------------------------------------------------------------------------
-- 3) ربط جهاز السوني بالكود (Sign in with a code / Link a console)
-- ---------------------------------------------------------------------------
create table if not exists public.console_link_codes (
  code          text primary key,                               -- 6 خانات مثل K7P4QX
  poll_token    uuid not null default gen_random_uuid(),        -- سر استعلام خاص بجهاز السوني
  platform      text not null default 'ps4'
                check (platform in ('ps4', 'ps5')),
  device_name   text not null default 'PlayStation Console',
  status        text not null default 'waiting'
                check (status in ('waiting', 'linked', 'expired', 'consumed')),
  user_id       uuid references public.profiles(id) on delete cascade,
  access_token  text,
  refresh_token text,
  expires_at    timestamptz not null default (now() + interval '10 minutes'),
  linked_at     timestamptz,
  created_at    timestamptz not null default now()
);

create index if not exists console_link_codes_expires_idx
  on public.console_link_codes(status, expires_at);

create table if not exists public.linked_consoles (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles(id) on delete cascade,
  platform      text not null default 'ps4' check (platform in ('ps4', 'ps5')),
  device_name   text not null default 'PlayStation Console',
  linked_code   text,
  last_seen_at  timestamptz not null default now(),
  created_at    timestamptz not null default now()
);

create index if not exists linked_consoles_user_idx
  on public.linked_consoles(user_id, created_at desc);

alter table public.console_link_codes enable row level security;
alter table public.linked_consoles enable row level security;

drop policy if exists "linked_consoles_own_select" on public.linked_consoles;
create policy "linked_consoles_own_select"
  on public.linked_consoles for select
  using (user_id = auth.uid() or public.is_admin(auth.uid()));

drop policy if exists "linked_consoles_own_delete" on public.linked_consoles;
create policy "linked_consoles_own_delete"
  on public.linked_consoles for delete
  using (user_id = auth.uid() or public.is_admin(auth.uid()));

-- 3.أ) توليد كود ربط جديد من جهاز السوني (صالح 10 دقائق)
create or replace function public.create_console_link_code(
  p_platform    text default 'ps4',
  p_device_name text default 'PlayStation 4'
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  v_code     text := '';
  v_token    uuid := gen_random_uuid();
  v_expires  timestamptz := now() + interval '10 minutes';
  v_try      int := 0;
  v_i        int;
begin
  -- تنظيف الأكواد المنتهية القديمة
  delete from public.console_link_codes
   where expires_at < now() - interval '1 hour';

  loop
    v_code := '';
    for v_i in 1..6 loop
      v_code := v_code || substr(v_alphabet, 1 + floor(random() * length(v_alphabet))::int, 1);
    end loop;

    exit when not exists (
      select 1 from public.console_link_codes where code = v_code
    );
    v_try := v_try + 1;
    if v_try > 20 then
      raise exception 'could_not_generate_code';
    end if;
  end loop;

  insert into public.console_link_codes(code, poll_token, platform, device_name, status, expires_at)
  values (
    v_code,
    v_token,
    case when lower(coalesce(p_platform, 'ps4')) = 'ps5' then 'ps5' else 'ps4' end,
    coalesce(nullif(trim(p_device_name), ''), 'PlayStation 4'),
    'waiting',
    v_expires
  );

  return jsonb_build_object(
    'ok', true,
    'code', v_code,
    'formatted_code', substr(v_code, 1, 3) || ' ' || substr(v_code, 4, 3),
    'poll_token', v_token,
    'expires_at', v_expires,
    'expires_in_seconds', 600
  );
end;
$$;

-- 3.ب) تأكيد الكود من تطبيق الهاتف أو الكمبيوتر (Settings -> Link a console)
create or replace function public.claim_console_link_code(
  p_code          text,
  p_access_token  text default null,
  p_refresh_token text default null
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid   uuid := auth.uid();
  v_clean text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_row   public.console_link_codes;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  if length(v_clean) <> 6 then
    return jsonb_build_object('ok', false, 'message', 'invalid_code');
  end if;

  select * into v_row
    from public.console_link_codes
   where code = v_clean
   for update;

  if not found then
    return jsonb_build_object('ok', false, 'message', 'code_not_found');
  end if;

  if v_row.expires_at <= now() or v_row.status in ('expired', 'consumed') then
    update public.console_link_codes set status = 'expired' where code = v_clean;
    return jsonb_build_object('ok', false, 'message', 'code_expired');
  end if;

  update public.console_link_codes
     set status        = 'linked',
         user_id       = v_uid,
         access_token  = p_access_token,
         refresh_token = p_refresh_token,
         linked_at     = now()
   where code = v_clean;

  insert into public.linked_consoles(user_id, platform, device_name, linked_code)
  values (v_uid, v_row.platform, v_row.device_name, v_clean);

  return jsonb_build_object(
    'ok', true,
    'platform', v_row.platform,
    'device_name', v_row.device_name
  );
end;
$$;

-- 3.ج) استعلام جهاز السوني كل 3 ثوانٍ عن حالة الكود
create or replace function public.poll_console_link_code(
  p_code       text,
  p_poll_token uuid
)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_clean   text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_row     public.console_link_codes;
  v_profile public.profiles;
  v_plan    text;
  v_balance int := 0;
begin
  select * into v_row
    from public.console_link_codes
   where code = v_clean and poll_token = p_poll_token
   for update;

  if not found then
    return jsonb_build_object('ok', false, 'status', 'invalid');
  end if;

  if v_row.status = 'waiting' and v_row.expires_at <= now() then
    update public.console_link_codes set status = 'expired' where code = v_clean;
    return jsonb_build_object('ok', true, 'status', 'expired');
  end if;

  if v_row.status = 'expired' then
    return jsonb_build_object('ok', true, 'status', 'expired');
  end if;

  if v_row.status = 'waiting' then
    return jsonb_build_object(
      'ok', true,
      'status', 'waiting',
      'expires_in_seconds', greatest(0, extract(epoch from (v_row.expires_at - now()))::int)
    );
  end if;

  -- استهلاك مرة واحدة: إن كانت الرموز مسحوبة سابقاً لا نعيد تسليمها
  if v_row.status = 'consumed' then
    return jsonb_build_object('ok', true, 'status', 'consumed');
  end if;

  if v_row.status = 'linked' and v_row.user_id is not null then
    select * into v_profile from public.profiles where id = v_row.user_id;
    v_plan := public.effective_plan(v_row.user_id);
    select coalesce(balance, 0) into v_balance from public.ac_wallets where user_id = v_row.user_id;

    -- نمسح التوكن بعد استلامه لأول مرة للأمان
    update public.console_link_codes
       set status        = 'consumed',
           access_token  = null,
           refresh_token = null
     where code = v_clean;

    return jsonb_build_object(
      'ok', true,
      'status', 'linked',
      'user_id', v_profile.id,
      'username', v_profile.username,
      'display_name', coalesce(v_profile.display_name, v_profile.username),
      'avatar_url', v_profile.avatar_url,
      'plan', v_plan,
      'ac_balance', v_balance,
      'access_token', v_row.access_token,
      'refresh_token', v_row.refresh_token
    );
  end if;

  return jsonb_build_object('ok', true, 'status', v_row.status);
end;
$$;

-- ---------------------------------------------------------------------------
-- 4) تحديث ac_unlock_content و record_content_download لدعم الخصم وملفات PS4
-- ---------------------------------------------------------------------------
create or replace function public.ac_unlock_content(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v_item    public.content_items;
  v_price   int;
  v_balance int;
  v_already boolean;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then return jsonb_build_object('ok', false, 'message', 'not_found'); end if;

  -- الخادم يحدد السعر الفعلي (بما في ذلك سعر الخصم sale_ac_price إن كان سارياً)
  v_price := public.effective_item_ac_price(v_item);
  if v_price <= 0 then
    return jsonb_build_object('ok', false, 'message', 'not_priced');
  end if;

  select exists(select 1 from public.ac_transactions
                where user_id = v_uid and item_id = p_item_id and kind = 'unlock') into v_already;
  if v_already then
    return jsonb_build_object('ok', true, 'charged', 0, 'message', 'already_unlocked');
  end if;

  select balance into v_balance from public.ac_wallets where user_id = v_uid for update;
  if coalesce(v_balance, 0) < v_price then
    return jsonb_build_object(
      'ok', false,
      'message', 'not_enough_ac',
      'needed_ac', v_price,
      'current_ac', coalesce(v_balance, 0)
    );
  end if;

  update public.ac_wallets
     set balance = balance - v_price,
         total_spent = total_spent + v_price,
         updated_at = now()
   where user_id = v_uid;

  insert into public.ac_transactions(user_id, amount, kind, reason, item_id, request_key, balance_after)
  values (v_uid, -v_price, 'unlock', coalesce(v_item.title, 'Content unlock'), p_item_id,
          'unlock:' || v_uid::text || ':' || p_item_id::text, v_balance - v_price)
  on conflict (request_key) do nothing;

  return jsonb_build_object('ok', true, 'charged', v_price, 'balance', v_balance - v_price);
end;
$$;

create or replace function public.record_content_download(p_item_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_uid  uuid := auth.uid();
  v_item public.content_items;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'message', 'sign_in_required');
  end if;

  select * into v_item from public.content_items where id = p_item_id;
  if not found then
    return jsonb_build_object('ok', false, 'message', 'not_found');
  end if;

  if coalesce(v_item.file_path, '') = '' and coalesce(v_item.ps4_raw_url, '') = '' then
    return jsonb_build_object('ok', false, 'message', 'file_not_uploaded');
  end if;

  if not public.can_access_item(p_item_id, v_uid) then
    return jsonb_build_object('ok', false, 'message', 'no_permission');
  end if;

  if public.download_limit_reached(v_uid) then
    return jsonb_build_object('ok', false, 'message', 'download_limit');
  end if;

  insert into public.content_downloads (user_id, item_id) values (v_uid, p_item_id);
  update public.content_items set downloads_count = downloads_count + 1 where id = p_item_id;

  return jsonb_build_object(
    'ok', true,
    'bucket', 'content-files',
    'path', v_item.file_path,
    'ps4_raw_url', v_item.ps4_raw_url,
    'pack_targets', v_item.pack_targets,
    'content_type', v_item.content_type,
    'title', v_item.title
  );
end;
$$;

-- منح الصلاحيات للأدوار (anon يحتاج استدعاء توليد الكود والاستعلام عنه على السوني قبل تسجيل الدخول)
grant select on public.store_banners to anon, authenticated;
grant select, delete on public.linked_consoles to authenticated;
grant execute on function public.effective_item_ac_price(public.content_items) to anon, authenticated;
grant execute on function public.create_console_link_code(text, text) to anon, authenticated;
grant execute on function public.poll_console_link_code(text, uuid) to anon, authenticated;
grant execute on function public.claim_console_link_code(text, text, text) to authenticated;

do $$
begin
  raise notice '✅ 40_ps4_and_deals: تم تفعيل دعم PS4/PS5 والعروض وربط الكونسول بالكود بنجاح.';
end;
$$;
