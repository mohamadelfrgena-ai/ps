-- ============================================================================
--  AddonStation — first content item: Sulfur Cars (content-001)
--  Paste this file in: Supabase Dashboard -> SQL Editor -> Run
--  Safe to re-run (upsert on slug).
-- ============================================================================

insert into public.content_items
  (title, slug, description, content_type, category, plan, version,
   cover_url, file_path, file_size, ac_price,
   is_published, is_featured, is_exclusive, exclusive_slot,
   ps4_raw_url, pack_targets)
values
  (
    'Sulfur Cars',
    'sulfur-cars',
    'Driveable cars addon (SUV, truck, van, sport car, crossover) by sliver fox.',
    'addon',
    'other',
    'free',
    '1.0.2',
    'https://github.com/foxinteractive-pixel/content/releases/download/content-001/sulfur-cars-cover.jpg',
    'content-001/sulfur-cars_ffe0c865.zip',
    1393437,
    0,
    true, true, true, 1,
    'https://github.com/foxinteractive-pixel/content/releases/download/content-001/sulfur-cars_ff44ec0a.raw',
    '[
      {
        "target": "behavior_packs",
        "dest_folder": "sulfur-cars_ff44ec0a",
        "raw_file": "sulfur-cars_ff44ec0a.raw",
        "ps4_raw_url": "https://github.com/foxinteractive-pixel/content/releases/download/content-001/sulfur-cars_ff44ec0a.raw",
        "sha256": "eb19f9cbbfef7639c16508c6baabdcd4bd4960a0f39540a52f70b487384946fc"
      },
      {
        "target": "resource_packs",
        "dest_folder": "sulfur-cars_ffe0c865",
        "raw_file": "sulfur-cars_ffe0c865.raw",
        "ps4_raw_url": "https://github.com/foxinteractive-pixel/content/releases/download/content-001/sulfur-cars_ffe0c865.raw",
        "sha256": "45b26116a1342a5e8cb21a3beb51a30c459f283f47de3ef713c8e9fc3e6cfc9b"
      }
    ]'::jsonb
  )
on conflict (slug) do update
  set title        = excluded.title,
      description   = excluded.description,
      version       = excluded.version,
      cover_url     = excluded.cover_url,
      file_path     = excluded.file_path,
      file_size     = excluded.file_size,
      ps4_raw_url   = excluded.ps4_raw_url,
      pack_targets  = excluded.pack_targets,
      is_published  = true,
      is_featured   = excluded.is_featured,
      is_exclusive  = excluded.is_exclusive,
      exclusive_slot = excluded.exclusive_slot,
      updated_at    = now();

-- Quick check: the Sulfur Cars row must appear
select slug, title, ps4_raw_url from public.content_items where slug = 'sulfur-cars';
