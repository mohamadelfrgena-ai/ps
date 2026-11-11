-- 44_free_content_seed.sql — AddonStation: seed the FREE tier content (10 addons)
-- محتوى الخطة المجانية (free): 10 إضافات — روابط GitHub Releases + pack_targets.
-- آمن لإعادة التنفيذ (upsert على slug). العلامة التجارية المعتمدة: silverfoxstudio
-- والاسم المعروض ينتهي بـ "by sliver fox".
-- literals: plan = 'free'  content_type = 'addon'

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'A Minecraft Movie by sliver fox',
    'a-minecraft-movie',
    'Step into a movie-inspired adventure with new characters, locations and surprises around every corner. Explore themed scenes and relive the story in your own world.',
    'addon', 'other', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-a-minecraft-movie-1.0.0/a-minecraft-movie-cover.jpg',
    'content-a-minecraft-movie-1.0.0/a-minecraft-movie_4022586e.raw',
    474978,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-a-minecraft-movie-1.0.0/a-minecraft-movie_4022586e.raw',
    '[{"target": "behavior_packs", "dest_folder": "a-minecraft-movie_4022586e", "raw_file": "a-minecraft-movie_4022586e.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-a-minecraft-movie-1.0.0/a-minecraft-movie_4022586e.raw", "sha256": "50541159c294fdb80e2eab7171822846ab6218ce8d292b9568c77d81d5f7c086"}, {"target": "resource_packs", "dest_folder": "a-minecraft-movie_8e3af75d", "raw_file": "a-minecraft-movie_8e3af75d.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-a-minecraft-movie-1.0.0/a-minecraft-movie_8e3af75d.raw", "sha256": "98d35f41a801530a3708ec412efe683ead68fc25b120c3df10e14bbe15e6f44f"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Aether Moa by sliver fox',
    'aether-moa',
    'Soar above the clouds on the back of a mighty Moa! Tame these giant birds, craft their saddles and glide across the Aether in style. A sky-high adventure awaits.',
    'addon', 'adventure', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-aether-moa-1.0.0/aether-moa-cover.jpg',
    'content-aether-moa-1.0.0/aether-moa_fd53f531.raw',
    256462,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-aether-moa-1.0.0/aether-moa_fd53f531.raw',
    '[{"target": "behavior_packs", "dest_folder": "aether-moa_fd53f531", "raw_file": "aether-moa_fd53f531.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-aether-moa-1.0.0/aether-moa_fd53f531.raw", "sha256": "df94f8d81fe59aaa990916c27ed8993e24224aa0694eca89e67eb79869d0b7a9"}, {"target": "resource_packs", "dest_folder": "aether-moa_07c5e2b0", "raw_file": "aether-moa_07c5e2b0.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-aether-moa-1.0.0/aether-moa_07c5e2b0.raw", "sha256": "196e0401e18ddfc674a288e28e58bc5d4a55872e1ab5aa349502151ab0b415fa"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Backpacks and Jetpacks by sliver fox',
    'backpacks-and-jetpacks',
    'Carry more and fly further! Craft roomy backpacks to hold all your loot, then strap on a jetpack and blast off over your world. The ultimate survival upgrade.',
    'addon', 'survival', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-backpacks-and-jetpacks-1.0.0/backpacks-and-jetpacks-cover.jpg',
    'content-backpacks-and-jetpacks-1.0.0/backpacks-and-jetpacks_0e1fb703.raw',
    712267,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-backpacks-and-jetpacks-1.0.0/backpacks-and-jetpacks_0e1fb703.raw',
    '[{"target": "behavior_packs", "dest_folder": "backpacks-and-jetpacks_0e1fb703", "raw_file": "backpacks-and-jetpacks_0e1fb703.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-backpacks-and-jetpacks-1.0.0/backpacks-and-jetpacks_0e1fb703.raw", "sha256": "4b665c6be3173cf6549da800d9819bf4169712cf1ab4edf5bb8f855f3a3840b4"}, {"target": "resource_packs", "dest_folder": "backpacks-and-jetpacks_f0ca0736", "raw_file": "backpacks-and-jetpacks_f0ca0736.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-backpacks-and-jetpacks-1.0.0/backpacks-and-jetpacks_f0ca0736.raw", "sha256": "d487c38e698de7844945e2fd830b13e8508d1479b4aa85b449c47c9549a7d70a"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Block Furniture 1.0 by sliver fox',
    'block-furniture-1-0',
    'Furnish your dream home with dozens of new furniture blocks - sofas, tables, shelves, lamps and more. Every piece is craftable and fits right into your builds.',
    'addon', 'building', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-block-furniture-1-0-1.0.0/block-furniture-1-0-cover.jpg',
    'content-block-furniture-1-0-1.0.0/block-furniture-1-0_91022fc4.raw',
    4657236,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-block-furniture-1-0-1.0.0/block-furniture-1-0_91022fc4.raw',
    '[{"target": "behavior_packs", "dest_folder": "block-furniture-1-0_91022fc4", "raw_file": "block-furniture-1-0_91022fc4.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-block-furniture-1-0-1.0.0/block-furniture-1-0_91022fc4.raw", "sha256": "59e5c8fba12107e80323386fafd060565c23ec5bbda168263f1fa96553e98966"}, {"target": "resource_packs", "dest_folder": "block-furniture-1-0_379b13eb", "raw_file": "block-furniture-1-0_379b13eb.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-block-furniture-1-0-1.0.0/block-furniture-1-0_379b13eb.raw", "sha256": "3a56ce904d0308b2123d839da68ded4ec2c50652f7e0ea5640189ea8e8ddbfc8"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'COMBINE ITEMS! Add-On by sliver fox',
    'combine-items-add-on',
    'Combine ordinary items into amazing new tools! Mix and match materials to unlock powerful weapons, handy gadgets and unique blocks. Find every hidden recipe.',
    'addon', 'other', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-combine-items-add-on-1.0.0/combine-items-add-on-cover.jpg',
    'content-combine-items-add-on-1.0.0/combine-items-add-on_59a62de5.raw',
    1019443,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-combine-items-add-on-1.0.0/combine-items-add-on_59a62de5.raw',
    '[{"target": "behavior_packs", "dest_folder": "combine-items-add-on_59a62de5", "raw_file": "combine-items-add-on_59a62de5.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-combine-items-add-on-1.0.0/combine-items-add-on_59a62de5.raw", "sha256": "5fd4bad92da5b3628086e70189108301e893fabffb383391277fce7776bad01b"}, {"target": "resource_packs", "dest_folder": "combine-items-add-on_1b275a14", "raw_file": "combine-items-add-on_1b275a14.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-combine-items-add-on-1.0.0/combine-items-add-on_1b275a14.raw", "sha256": "752733e60406c413a2ded998884bda296c45d0453212b1a0493a97ac892a86a3"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Gravestones by sliver fox',
    'gravestones',
    'Never lose your gear again! When you fall, a gravestone keeps your items safe until you return. Find it, break it and reclaim everything you carried.',
    'addon', 'survival', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-gravestones-1.0.0/gravestones-cover.jpg',
    'content-gravestones-1.0.0/gravestones_ecc614ca.raw',
    103383,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-gravestones-1.0.0/gravestones_ecc614ca.raw',
    '[{"target": "behavior_packs", "dest_folder": "gravestones_ecc614ca", "raw_file": "gravestones_ecc614ca.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-gravestones-1.0.0/gravestones_ecc614ca.raw", "sha256": "886a0f4daf4863a374dd788bf825f1e48ec4ec55fbe58796acbc1f16570f5797"}, {"target": "resource_packs", "dest_folder": "gravestones_0f5172fb", "raw_file": "gravestones_0f5172fb.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-gravestones-1.0.0/gravestones_0f5172fb.raw", "sha256": "10a601e4ec37bfdf2e9b22fb03476a8375bc90ebd5b67cb052919b2f3f391220"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Owls by sliver fox',
    'owls',
    'Meet your new night companions! Tame adorable owls that follow you through the dark, perch on your shoulder and hoot you to sleep. Many colors to collect.',
    'addon', 'survival', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-owls-1.0.0/owls-cover.jpg',
    'content-owls-1.0.0/owls_707d1b97.raw',
    495068,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-owls-1.0.0/owls_707d1b97.raw',
    '[{"target": "behavior_packs", "dest_folder": "owls_707d1b97", "raw_file": "owls_707d1b97.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-owls-1.0.0/owls_707d1b97.raw", "sha256": "c9e0d5ce5339e3474a248e75074479cb033937a74787ceee568400b640714e50"}, {"target": "resource_packs", "dest_folder": "owls_47d41fb7", "raw_file": "owls_47d41fb7.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-owls-1.0.0/owls_47d41fb7.raw", "sha256": "5dc42d24d7844de861f8f8a2faa7dc078fee1632480f3f427a95cdbf6b3446cf"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Spark Pets by sliver fox',
    'spark-pets',
    'Adopt a pack of cute, sparky pets that light up your world! Feed them, play with them and watch them grow. Each pet has its own personality and special trick.',
    'addon', 'survival', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-spark-pets-1.0.0/spark-pets-cover.jpg',
    'content-spark-pets-1.0.0/spark-pets_4641073a.raw',
    2885218,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-spark-pets-1.0.0/spark-pets_4641073a.raw',
    '[{"target": "behavior_packs", "dest_folder": "spark-pets_4641073a", "raw_file": "spark-pets_4641073a.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-spark-pets-1.0.0/spark-pets_4641073a.raw", "sha256": "4fccca18b2cb0f4af217041e353b6298007abd32d3fc27e418a2d0b8353aebdf"}, {"target": "resource_packs", "dest_folder": "spark-pets_61aa0a28", "raw_file": "spark-pets_61aa0a28.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-spark-pets-1.0.0/spark-pets_61aa0a28.raw", "sha256": "f18f9e6d84e663db10db5b6d23d283b3d082d32a81e778cb4ef21486e1e5e3c2"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Weapons Armors by sliver fox',
    'weapons-armors',
    'Gear up for battle with new weapons and armor sets - sharp blades, heavy hammers and strong shields. Everything you need to win the toughest fights.',
    'addon', 'pvp', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-weapons-armors-1.0.0/weapons-armors-cover.jpg',
    'content-weapons-armors-1.0.0/weapons-armors_f1cd6f75.raw',
    556442,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-weapons-armors-1.0.0/weapons-armors_f1cd6f75.raw',
    '[{"target": "behavior_packs", "dest_folder": "weapons-armors_f1cd6f75", "raw_file": "weapons-armors_f1cd6f75.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-weapons-armors-1.0.0/weapons-armors_f1cd6f75.raw", "sha256": "4da3271fa54392bfa744d57f67d2c65563e8516a84d36e7aaf733c33d5c0ffca"}, {"target": "resource_packs", "dest_folder": "weapons-armors_6a7d8764", "raw_file": "weapons-armors_6a7d8764.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-weapons-armors-1.0.0/weapons-armors_6a7d8764.raw", "sha256": "467288a5de0cc200c1eeb459868301010e47ba13d2ef060548a22f9937285334"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive,
   ps4_raw_url, pack_targets)
values
  (
    'Tiny Takeover Mini Mania by sliver fox',
    'tiny-takeover-mini-mania',
    'Think small, fight big! Shrink down and battle in a tiny world of mini arenas, pocket-sized weapons and fast challenges. A mad little PvP party.',
    'addon', 'pvp', 'free', '1.0.0',
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-tiny-takeover-mini-mania-1.0.0/tiny-takeover-mini-mania-cover.jpg',
    'content-tiny-takeover-mini-mania-1.0.0/tiny-takeover-mini-mania_65846f66.raw',
    2180387,
    0,
    true, false, false,
    'https://github.com/mohamadelfrgena-ai/ps/releases/download/content-tiny-takeover-mini-mania-1.0.0/tiny-takeover-mini-mania_65846f66.raw',
    '[{"target": "behavior_packs", "dest_folder": "tiny-takeover-mini-mania_65846f66", "raw_file": "tiny-takeover-mini-mania_65846f66.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-tiny-takeover-mini-mania-1.0.0/tiny-takeover-mini-mania_65846f66.raw", "sha256": "81ef67ca880bd26fcf52ee722eb14a35862e31fe5d22b25f22be0ae6921205a2"}, {"target": "resource_packs", "dest_folder": "tiny-takeover-mini-mania_6e743591", "raw_file": "tiny-takeover-mini-mania_6e743591.raw", "ps4_raw_url": "https://github.com/mohamadelfrgena-ai/ps/releases/download/content-tiny-takeover-mini-mania-1.0.0/tiny-takeover-mini-mania_6e743591.raw", "sha256": "f7a2365e3ad0d61f83e6c3e2c458eb8bb4a35940a4ce32ec5d7386d061d2f49c"}]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description  = excluded.description,
      category     = excluded.category,
      version      = excluded.version,
      cover_url    = excluded.cover_url,
      file_path    = excluded.file_path,
      file_size    = excluded.file_size,
      ac_price     = excluded.ac_price,
      ps4_raw_url  = excluded.ps4_raw_url,
      pack_targets = excluded.pack_targets,
      is_published = true,
      updated_at   = now();
