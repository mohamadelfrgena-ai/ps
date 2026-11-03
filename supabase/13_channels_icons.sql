-- ============================================================================
--  AddonStation Mobile — 13) أسماء الغرف بلا إيموجي + أيقونات مرسومة
--
--  الغرف صارت تُرسم أيقوناتها داخل التطبيق من معرّف الغرفة (channel.id)
--  عبر الدالة RoomIcon في ui/components/Common.kt — فلا حاجة لإيموجي في
--  قاعدة البيانات. نفّذ هذا الملف مرة واحدة بعد 06_seed.sql (أو بعد
--  00_ALL_IN_ONE.sql) ليصبح الاسم نظيفاً بلا رموز.
-- ============================================================================

update public.chat_channels set name = 'General',             icon = null where id = 'general';
update public.chat_channels set name = 'Minecraft',           icon = null where id = 'minecraft';
update public.chat_channels set name = 'Creator',             icon = null where id = 'creator';
update public.chat_channels set name = 'Support',             icon = null where id = 'support';
update public.chat_channels set name = 'Social Media Makers', icon = null where id = 'social-media-makers';

update public.chat_channels set name = 'العربية',   icon = null where id = 'lang-ar';
update public.chat_channels set name = 'Русский',   icon = null where id = 'lang-ru';
update public.chat_channels set name = 'Português', icon = null where id = 'lang-pt';
update public.chat_channels set name = '中文',       icon = null where id = 'lang-zh';
update public.chat_channels set name = 'English',   icon = null where id = 'lang-en';
update public.chat_channels set name = 'Español',   icon = null where id = 'lang-es';
update public.chat_channels set name = 'Français',  icon = null where id = 'lang-fr';
update public.chat_channels set name = 'Türkçe',    icon = null where id = 'lang-tr';

-- ---------------------------------------------------------------------------
-- تحقق: يجب أن ترى 13 صفاً بلا أي إيموجي في name، و icon كلها NULL
-- ---------------------------------------------------------------------------
select id, name, category, sort_order
from public.chat_channels
order by category desc, sort_order;
