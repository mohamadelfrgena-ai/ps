-- ============================================================================
--  AddonStation Mobile — 06) البيانات الأولية (Seed)
--  الخطط · طرق الدفع · مهام AC · حزم الملصقات · الغرف الثلاث عشرة · قواعد المجتمع
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) خطط الاشتراك (الأسعار قابلة للتعديل من لوحة الإدارة)
-- ---------------------------------------------------------------------------
insert into public.subscription_plans (id, name, tagline, price_lyd, price_usd, duration_days,
                                       download_limit, limit_period, active, sort_order)
values
  ('free', 'Free', 'Start exploring free addons and worlds.',
    0, 0, 30, 5, 'day', true, 1),
  ('epic', 'Epic', 'More content and a wider play experience.',
    25, 2.99, 30, 30, 'day', true, 2),
  ('legendary', 'Legendary', 'Unlock premium content and VIP privileges.',
    50, 5.99, 30, null, 'none', true, 3)
on conflict (id) do update
  set name = excluded.name,
      tagline = excluded.tagline,
      price_usd = excluded.price_usd,
      sort_order = excluded.sort_order;

-- ---------------------------------------------------------------------------
-- 2) طرق الدفع (عدّل اسم المستفيد ورقم الحساب من لوحة الإدارة ثم فعّلها)
-- ---------------------------------------------------------------------------
insert into public.payment_method_settings
  (code, name, account_name, account_identifier, instructions, enabled, sort_order, provider)
values
  ('onepay',       'OnePay',        null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 1, 'manual'),
  ('lpay',         'LPay',          null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 2, 'manual'),
  ('edfa3li',      'Edfa3li',       null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 3, 'manual'),
  ('mobicash',     'MobiCash',      null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 4, 'manual'),
  ('sadad',        'Sadad',         null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 5, 'manual'),
  ('tadawul',      'Tadawul',       null, null, 'Transfer the amount, then upload the receipt and the transaction number.', false, 6, 'manual'),
  ('bank_transfer','Bank transfer', null, null, 'Transfer to the bank account, then upload the receipt.', false, 7, 'manual'),
  ('stripe',       'Card (Stripe)', null, null, 'Pay securely by card. Your plan activates automatically after payment.', false, 8, 'stripe')
on conflict (code) do nothing;

-- ---------------------------------------------------------------------------
-- 3) مهام كسب AC
-- ---------------------------------------------------------------------------
insert into public.ac_tasks (id, title_ar, title_en, description, reward, frequency, streak_days, active, sort_order)
values
  ('daily_login',      'تسجيل الدخول اليومي', 'Daily login',
     'افتح التطبيق يومياً واقبض مكافأتك.', 10, 'streak', '[3,7,14,30]'::jsonb, true, 1),
  ('complete_profile', 'إكمال الملف الشخصي', 'Complete profile',
     'أضف نبذة وحالة وأفاتار.', 25, 'once', '[]'::jsonb, true, 2),
  ('community_post',   'مشاركة مجتمعية', 'Community participation',
     'شارك في غرفة مجتمعية واحدة على الأقل.', 15, 'daily', '[]'::jsonb, true, 3),
  ('first_addon',      'تحميل أول إضافة', 'First addon download',
     'حمّل أول إضافة منشورة.', 20, 'once', '[]'::jsonb, true, 4),
  ('first_world',      'تحميل أول عالم', 'First world download',
     'حمّل أول عالم منشور.', 20, 'once', '[]'::jsonb, true, 5),
  ('useful_post',      'أول منشور مفيد', 'First useful post',
     'منشور حصل على تفاعل من المجتمع.', 30, 'once', '[]'::jsonb, true, 6)
on conflict (id) do update
  set title_ar = excluded.title_ar,
      title_en = excluded.title_en,
      reward = excluded.reward,
      frequency = excluded.frequency,
      streak_days = excluded.streak_days;

-- ---------------------------------------------------------------------------
-- 4) حزم الملصقات الخمس (الملفات ترفع إلى مخزن site-assets/stickers/<pack>/)
-- ---------------------------------------------------------------------------
insert into public.sticker_packs (id, name, icon_url, sort_order, active)
values
  ('meme',              'meme',            '/site-assets/stickers/meme/', 1, true),
  ('minecraft-memes-1', 'Minecraft Memes1','/site-assets/stickers/Minecraft_Memes1/', 2, true),
  ('minecraft-memes-2', 'Minecraft Memes2','/site-assets/stickers/Minecraft_Memes2/', 3, true),
  ('minecraft-memes-3', 'Minecraft Memes3','/site-assets/stickers/Minecraft_Memes3/', 4, true),
  ('minecraft-movie',   'Minecraft Movie','/site-assets/stickers/Minecraft_Movie/', 5, true)
on conflict (id) do update set name = excluded.name, icon_url = excluded.icon_url;

-- ---------------------------------------------------------------------------
-- 5) الغرف الثلاث عشرة (5 أساسية + 8 لغات) — الأسماء كما في الوثيقة تماماً
-- ---------------------------------------------------------------------------
insert into public.chat_channels (id, name, icon, category, description, sort_order, is_locked, slowmode_seconds, post_permission)
values
  ('general'              , 'General'             , NULL, 'main', 'General chat for the community'         , 1, false, 0, 'members'),
  ('minecraft'            , 'Minecraft'           , NULL, 'main', 'Minecraft talk: worlds, addons, servers', 2, false, 0, 'members'),
  ('creator'              , 'Creator'             , NULL, 'main', 'For creators and developers'            , 3, false, 0, 'members'),
  ('support'              , 'Support'             , NULL, 'main', 'Technical help and troubleshooting'     , 4, false, 0, 'members'),
  ('social-media-makers'  , 'Social Media Makers' , NULL, 'main', 'For content makers on social media'     , 5, false, 0, 'members'),
  ('lang-ar'              , 'العربية'             , NULL, 'lang', 'الغرفة العربية'                         , 1, false, 0, 'members'),
  ('lang-ru'              , 'Русский'             , NULL, 'lang', 'Русская комната'                        , 2, false, 0, 'members'),
  ('lang-pt'              , 'Português'           , NULL, 'lang', 'Sala em português'                      , 3, false, 0, 'members'),
  ('lang-zh'              , '中文'                  , NULL, 'lang', '中文聊天室'                                  , 4, false, 0, 'members'),
  ('lang-en'              , 'English'             , NULL, 'lang', 'English room'                           , 5, false, 0, 'members'),
  ('lang-es'              , 'Español'             , NULL, 'lang', 'Sala en español'                        , 6, false, 0, 'members'),
  ('lang-fr'              , 'Français'            , NULL, 'lang', 'Salon français'                         , 7, false, 0, 'members'),
  ('lang-tr'              , 'Türkçe'              , NULL, 'lang', 'Türkçe sohbet odası'                    , 8, false, 0, 'members')
on conflict (id) do update
  set name = excluded.name,
      icon = excluded.icon,
      category = excluded.category,
      description = excluded.description,
      sort_order = excluded.sort_order,
      post_permission = excluded.post_permission;

-- ---------------------------------------------------------------------------
-- 6) قواعد المجتمع باللغات الثمانية (قابلة للتعديل من لوحة الإدارة)
-- ---------------------------------------------------------------------------
insert into public.chat_rules (lang, title, rules, accept_text, decline_text)
values
  ('en', '⚠️ Community rules',
   '["🤝 Respect all members","🚫 No insults or harassment","📵 No racism or discrimination","🔗 No suspicious links or ads","🔒 Do not share personal information","🛑 No inappropriate content","🏛️ No politics","⚠️ Violations may lead to a mute or a ban"]'::jsonb,
   'I agree and enter the chat', 'Decline and go home'),
  ('ar', '⚠️ قواعد المجتمع',
   '["🤝 احترام جميع الأعضاء","🚫 لا إهانات أو مضايقات","📵 لا عنصرية أو تمييز","🔗 لا روابط أو إعلانات مشبوهة","🔒 لا تشارك معلوماتك الشخصية","🛑 لا محتوى غير لائق","🏛️ لا سياسة","⚠️ قد تؤدي الانتهاكات إلى كتم الصوت أو الحظر"]'::jsonb,
   'أوافق وأدخل الدردشة', 'الرفض والعودة إلى المنزل'),
  ('tr', '⚠️ Topluluk kuralları',
   '["🤝 Herkese saygı göster","🚫 Hakaret ve taciz yok","📵 Irkçılık ve ayrımcılık yok","🔗 Şüpheli bağlantı ve izinsiz reklam yok","🔒 Kişisel bilgi paylaşma","🛑 Uygunsuz içerik yok","🏛️ Siyasi tartışma yok","⚠️ İhlaller susturma veya yasaklanmayla sonuçlanabilir"]'::jsonb,
   'Kabul ediyorum ve Sohbete giriyorum', 'Reddet ve Ana Sayfaya dön'),
  ('pt', '⚠️ Regras da comunidade',
   '["🤝 Respeite todos os membros","🚫 Sem insultos ou assédio","📵 Sem racismo ou discriminação","🔗 Sem links suspeitos ou anúncios","🔒 Não compartilhe informações pessoais","🛑 Sem conteúdo inadequado","🏛️ Sem política","⚠️ Violações podem levar a silenciamento ou banimento"]'::jsonb,
   'Concordo e entrar no chat', 'Recusar e voltar ao início'),
  ('es', '⚠️ Reglas de la comunidad',
   '["🤝 Respeta a todos los miembros","🚫 Sin insultos ni acoso","📵 Sin racismo ni discriminación","🔗 Sin enlaces sospechosos ni anuncios","🔒 No compartas información personal","🛑 Sin contenido inapropiado","🏛️ Sin política","⚠️ Las infracciones pueden provocar silencio o expulsión"]'::jsonb,
   'Acepto y entro al chat', 'Rechazar y volver al inicio'),
  ('ru', '⚠️ Правила сообщества',
   '["🤝 Уважайте всех участников","🚫 Без оскорблений и травли","📵 Без расизма и дискриминации","🔗 Без подозрительных ссылок и рекламы","🔒 Не делитесь личными данными","🛑 Без неприемлемого контента","🏛️ Без политики","⚠️ Нарушения могут привести к мьюту или бану"]'::jsonb,
   'Я согласен и вхожу в чат', 'Отказаться и вернуться домой'),
  ('zh', '⚠️ 社区规则',
   '["🤝 尊重所有成员","🚫 禁止侮辱和骚扰","📵 禁止种族主义和歧视","🔗 禁止可疑链接和广告","🔒 不要分享个人信息","🛑 禁止不当内容","🏛️ 禁止政治话题","⚠️ 违规可能导致禁言或封禁"]'::jsonb,
   '我同意并进入聊天', '拒绝并返回首页'),
  ('fr', '⚠️ Règles de la communauté',
   '["🤝 Respectez tous les membres","🚫 Pas d''insultes ni de harcèlement","📵 Pas de racisme ni de discrimination","🔗 Pas de liens suspects ni de publicités","🔒 Ne partagez pas d''informations personnelles","🛑 Pas de contenu inapproprié","🏛️ Pas de politique","⚠️ Les infractions peuvent entraîner un silence ou un bannissement"]'::jsonb,
   'J''accepte et j''entre dans le chat', 'Refuser et revenir à l''accueil')
on conflict (lang) do update
  set title = excluded.title, rules = excluded.rules,
      accept_text = excluded.accept_text, decline_text = excluded.decline_text,
      updated_at = now();

-- ---------------------------------------------------------------------------
-- 7) إعدادات التطبيق (الفلترة التلقائية + أعلام الميزات)
-- ---------------------------------------------------------------------------
insert into public.app_settings (key, value)
values
  ('automod', '{"block_links": true, "block_personal": true, "rate_limit_10s": 5, "blocked_words": []}'::jsonb),
  ('features', '{"ac_enabled": true, "creator_tips_enabled": true, "online_boost_enabled": false}'::jsonb)
on conflict (key) do nothing;

-- ملاحظة: العدد النهائي للغرف 13 فقط (5 أساسية + 8 لغات) كما في الوثيقة.
-- لإضافة غرفة إعلانات لاحقاً استخدم: select public.chat_admin_upsert_room(...) من لوحة الإدارة.
