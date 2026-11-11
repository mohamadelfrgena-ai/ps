-- 52_descriptions_free_pack.sql
-- Approximate marketplace-style descriptions for the 10 free-pack addons.
-- Replaces the placeholder text "<title> — by sliver fox (silverfoxstudio)." shown on the
-- addon detail page. Idempotent (safe to re-run). Run in project stbmescmexzcszyyambq.

update content_items set description = 'Step into a movie-inspired adventure with new characters, locations and surprises around every corner. Explore themed scenes and relive the story in your own world.' where slug = 'a-minecraft-movie';
update content_items set description = 'Soar above the clouds on the back of a mighty Moa! Tame these giant birds, craft their saddles and glide across the Aether in style. A sky-high adventure awaits.' where slug = 'aether-moa';
update content_items set description = 'Carry more and fly further! Craft roomy backpacks to hold all your loot, then strap on a jetpack and blast off over your world. The ultimate survival upgrade.' where slug = 'backpacks-and-jetpacks';
update content_items set description = 'Furnish your dream home with dozens of new furniture blocks - sofas, tables, shelves, lamps and more. Every piece is craftable and fits right into your builds.' where slug = 'block-furniture-1-0';
update content_items set description = 'Combine ordinary items into amazing new tools! Mix and match materials to unlock powerful weapons, handy gadgets and unique blocks. Find every hidden recipe.' where slug = 'combine-items-add-on';
update content_items set description = 'Never lose your gear again! When you fall, a gravestone keeps your items safe until you return. Find it, break it and reclaim everything you carried.' where slug = 'gravestones';
update content_items set description = 'Meet your new night companions! Tame adorable owls that follow you through the dark, perch on your shoulder and hoot you to sleep. Many colors to collect.' where slug = 'owls';
update content_items set description = 'Adopt a pack of cute, sparky pets that light up your world! Feed them, play with them and watch them grow. Each pet has its own personality and special trick.' where slug = 'spark-pets';
update content_items set description = 'Gear up for battle with new weapons and armor sets - sharp blades, heavy hammers and strong shields. Everything you need to win the toughest fights.' where slug = 'weapons-armors';
update content_items set description = 'Think small, fight big! Shrink down and battle in a tiny world of mini arenas, pocket-sized weapons and fast challenges. A mad little PvP party.' where slug = 'tiny-takeover-mini-mania';

-- verify
select slug, left(description, 60) as descr from content_items where slug in (
  'a-minecraft-movie','aether-moa','backpacks-and-jetpacks','block-furniture-1-0',
  'combine-items-add-on','gravestones','owls','spark-pets','weapons-armors','tiny-takeover-mini-mania'
) order by slug;
