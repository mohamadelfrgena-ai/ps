-- ============================================================================
--  AddonStation Mobile — 12) الأفاتارات الأولية (25 أفاتار)
--  الملفات ترفع إلى مخزن site-assets داخل مجلد profiles/ (انظر docs/02)
--  التوزيع: 1 مجاني (الافتراضي) · 12 لملحمي Epic 💎 · 12 لأسطوري Legendary 👑
--  آمن للتكرار: يعتمد على فهرس فريد على file_url
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) ضمان عدم تكرار الصفوف عند إعادة التنفيذ
--    إن فشل هذا السطر (توجد صفوف مكررة سابقاً) استخدم استعلام التنظيف أدناه.
-- تنظيم (اختياري — إن ظهر خطأ "could not create unique index"):
--   delete from public.site_assets a using public.site_assets b
--    where a.file_url = b.file_url and a.id > b.id;
-- ---------------------------------------------------------------------------
create unique index if not exists site_assets_file_url_uidx on public.site_assets (file_url);

-- ---------------------------------------------------------------------------
-- 1) الأفاتارات (asset_type = 'avatar')
-- ---------------------------------------------------------------------------
insert into public.site_assets (asset_type, name, file_url, required_plan, sort_order)
values
  ('avatar', 'onefree', 'profiles/onefree.png', 'free', 287),
  ('avatar', 'Cute girl', 'profiles/Cute_girl.jpg', 'epic', 288),
  ('avatar', 'Diamond Armor Glow', 'profiles/Diamond_Armor_Glow.jpg', 'epic', 289),
  ('avatar', 'Giant Minecraft Cookie', 'profiles/Giant_Minecraft_Cookie.jpg', 'epic', 290),
  ('avatar', 'Gigachad Doge Minecraft', 'profiles/Gigachad_Doge_Minecraft.jpg', 'epic', 291),
  ('avatar', 'Golden Helmet Steve', 'profiles/Golden_Helmet_Steve.jpg', 'epic', 292),
  ('avatar', 'Lush Cave Axolotls', 'profiles/Lush_Cave_Axolotls.jpg', 'epic', 293),
  ('avatar', 'Minecraft Bed Explosion', 'profiles/Minecraft_Bed_Explosion.jpg', 'epic', 294),
  ('avatar', 'Minecraft Cat Meme', 'profiles/Minecraft_Cat_Meme.jpg', 'epic', 295),
  ('avatar', 'Minecraft Cute Wolf', 'profiles/Minecraft_Cute_Wolf.jpg', 'epic', 296),
  ('avatar', 'Minecraft Music Disc', 'profiles/Minecraft_Music_Disc.jpg', 'epic', 297),
  ('avatar', 'Minecraft Rose Painting', 'profiles/Minecraft_Rose_Painting.jpg', 'epic', 298),
  ('avatar', 'Minecraft Warden Cave', 'profiles/Minecraft_Warden_Cave.jpg', 'epic', 299),
  ('avatar', 'Netherite Armor Steve', 'profiles/Netherite_Armor_Steve.jpg', 'legendary', 300),
  ('avatar', 'Netherite Warrior Spear', 'profiles/Netherite_Warrior_Spear.jpg', 'legendary', 301),
  ('avatar', 'Steve Mining Creeper', 'profiles/Steve_Mining_Creeper.jpg', 'legendary', 302),
  ('avatar', 'Tall Enderman girl', 'profiles/Tall_Enderman_girl.jpg', 'legendary', 303),
  ('avatar', 'Waguri-chan is Minecraft', 'profiles/Waguri-chan is Minecraft.jpg', 'legendary', 304),
  ('avatar', 'Zombie Climbing Ladder', 'profiles/Zombie_Climbing_Ladder.jpg', 'legendary', 305),
  ('avatar', 'Aelx with TheBoat', 'profiles/Aelx_with_TheBoat.jpg', 'legendary', 306),
  ('avatar', 'Axolotl Aquarium', 'profiles/Axolotl_Aquarium.jpg', 'legendary', 307),
  ('avatar', 'Cheems Doge Minecraft', 'profiles/Cheems_Doge_Minecraft.jpg', 'legendary', 308),
  ('avatar', 'Cherry Blossom Biom', 'profiles/Cherry_Blossom_Biom.jpg', 'legendary', 309),
  ('avatar', 'Cozy Furnace Corner', 'profiles/Cozy_Furnace_Corner.jpg', 'legendary', 310),
  ('avatar', 'Cute Enderman Girl', 'profiles/Cute_Enderman_Girl.jpg', 'legendary', 311)
on conflict (file_url) do nothing;

-- ---------------------------------------------------------------------------
-- ✅ تحقّق: المتوقّع avatar = 25 · free = 1 · epic = 12 · legendary = 12
-- ---------------------------------------------------------------------------
select required_plan, count(*) as avatars
  from public.site_assets
 where asset_type = 'avatar'
 group by required_plan order by required_plan;
