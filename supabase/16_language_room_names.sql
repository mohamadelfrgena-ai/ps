-- ============================================================================
--  AddonStation Mobile — 16) أسماء غرف اللغة بلغتها
--
--  الغرض: كل غرفة لغة تحمل اسمها **بلغة أهلها** لا بلغة الواجهة:
--      العربية · English · Español · Français · Português · Русский · Türkçe · 中文
--
--  التطبيق يضمن هذه الأسماء من معرّف الغرفة (ui/community/ChannelNames.kt)
--  فتعمل فوراً بلا تنفيذ SQL — وهذا الملف يجعل قاعدة البيانات مطابقة لها،
--  ويصلح أي اسم قديم أو مختلط (إيموجي · اسم إنجليزي لغرفة غير إنجليزية).
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor. آمن لإعادة التنفيذ.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) غرف اللغات الثماني — الاسم بلغتها، والوصف بلغتها أيضاً
-- ---------------------------------------------------------------------------
update public.chat_channels
   set name = 'العربية', description = 'غرفة الدردشة العربية', icon = null, category = 'lang', sort_order = 1
 where id = 'lang-ar';

update public.chat_channels
   set name = 'Русский', description = 'Русскоязычный чат', icon = null, category = 'lang', sort_order = 2
 where id = 'lang-ru';

update public.chat_channels
   set name = 'Português', description = 'Sala de bate-papo em português', icon = null, category = 'lang', sort_order = 3
 where id = 'lang-pt';

update public.chat_channels
   set name = '中文', description = '中文聊天室', icon = null, category = 'lang', sort_order = 4
 where id = 'lang-zh';

update public.chat_channels
   set name = 'English', description = 'English chat room', icon = null, category = 'lang', sort_order = 5
 where id = 'lang-en';

update public.chat_channels
   set name = 'Español', description = 'Sala de chat en español', icon = null, category = 'lang', sort_order = 6
 where id = 'lang-es';

update public.chat_channels
   set name = 'Français', description = 'Salon de discussion en français', icon = null, category = 'lang', sort_order = 7
 where id = 'lang-fr';

update public.chat_channels
   set name = 'Türkçe', description = 'Türkçe sohbet odası', icon = null, category = 'lang', sort_order = 8
 where id = 'lang-tr';

-- ---------------------------------------------------------------------------
-- 2) الغرف الأساسية الخمس (أسماء ثابتة بلا إيموجي — الأيقونة يرسمها التطبيق)
-- ---------------------------------------------------------------------------
update public.chat_channels set name = 'General',             icon = null, category = 'main', sort_order = 1 where id = 'general';
update public.chat_channels set name = 'Minecraft',           icon = null, category = 'main', sort_order = 2 where id = 'minecraft';
update public.chat_channels set name = 'Creator',             icon = null, category = 'main', sort_order = 3 where id = 'creator';
update public.chat_channels set name = 'Support',             icon = null, category = 'main', sort_order = 4 where id = 'support';
update public.chat_channels set name = 'Social Media Makers', icon = null, category = 'main', sort_order = 5 where id = 'social-media-makers';

-- ---------------------------------------------------------------------------
-- 3) أي غرفة لغة بمعرّف لم يُذكر أعلاه: توحيد التصنيف فقط
-- ---------------------------------------------------------------------------
update public.chat_channels set category = 'lang', icon = null where id like 'lang-%';

-- ---------------------------------------------------------------------------
-- تحقق: يجب أن ترى 13 غرفة، غرف اللغات بأسمائها الأصلية وكلها بلا إيموجي
-- ---------------------------------------------------------------------------
select id, name, category, sort_order
  from public.chat_channels
 order by category desc, sort_order;
