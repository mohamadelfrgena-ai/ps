# Skin Lab

Independent, offline **Bedrock skin companion**: local library, visual preview, favorites, and conventional `.mcpack` export.

> **Not a PS4 skin installer.** Selecting a skin here changes only the preview. It does not equip a Minecraft player skin. Custom PNG import/equip is not a documented PS4 Bedrock feature. Read [`FEASIBILITY.md`](FEASIBILITY.md) before planning console delivery.

## What this prototype does

- Imports user-owned `.mcpack`/`.zip` skin packs, skin folders containing `skins.json`, or loose 64×64 PNGs.
- Searches/filters by skin or pack name and favorites.
- Shows a texture atlas and a rotatable classic/slim humanoid preview.
- Exports selected skins as a conventional skin pack with fresh UUIDs, localization, and root-level PNGs.
- Validates ZIP paths, CRCs, duplicates, manifest/module shape, UUIDs, skin geometry, PNG headers/CRCs, and texture references.
- Includes two original synthetic demo textures; no upstream or Marketplace artwork is bundled.

## What it does not do

- It does not modify Minecraft, bypass authentication/DRM, or write to console storage.
- It does not install or equip skins on PS4.
- It does not publish to Marketplace or grant redistribution rights.
- It does not convert Legacy Console `.pck`, Persona, capes, emotes, or custom player geometry.
- It is not a Bedrock RP/BP add-on yet. The proposed next experiment is a four-skin, preview-only RP+BP delivered through a Realms-prepared world; see the feasibility report.

## Run locally

No runtime dependencies. Node is only needed for tests/tooling.

```bash
cd skin-lab
npm test
npm run validate
npm run serve
```

Open <http://localhost:4173>. The static server binds to `0.0.0.0` for sandbox/live preview use.

You can also open `index.html` directly from disk in a current browser. A headless Chromium smoke test passed over both `http://127.0.0.1:4173/` and `file://`; browser folder-picker/storage behavior can still vary by browser and privacy settings.

Regenerate the original demo assets:

```bash
npm run examples
```

Validate any pack:

```bash
node tools/validate-pack.js path/to/pack.mcpack
```

## Using your own content

1. Click **Import .mcpack**, **Import skin folder**, or **Import PNG skins**.
2. For loose PNGs, choose classic/wide or slim before importing.
3. Select a card to preview. Use the checkbox to include it in export.
4. Click **Download .mcpack**.
5. Test the exported pack in Windows/mobile Minecraft first. Do not assume PS4 import/equip support.

Files stay in the browser. The library is session-local; re-import after reload. Favorites, last preview, and export name are saved in `localStorage` when available.

## Layout

```text
FEASIBILITY.md            detailed technical verdict and source register
TEST-RESULTS.md           what was actually verified here
index.html / styles.css   local companion UI
src/pack-core.js          shared ZIP/PNG/manifest validator + pack builder
src/preview.js            tiny software humanoid/atlas preview
src/app.js                import/browse/favorite/export UI
examples/                 original synthetic demo PNGs + demo .mcpack
tools/                    demo generator, validator, static server
tests/                    Node pack-core tests
reports/                  generated audit/test evidence
```

## Limits

- Archive: 32 MiB; entries: 512; single file: 8 MiB; total uncompressed: 64 MiB; skins per pack: 256.
- Skin textures: 64×64 PNG only.
- Skin models: `geometry.humanoid.custom` and `geometry.humanoid.customSlim` only.
- ZIP: stored/deflate, no ZIP64, no encryption, no symlinks, no traversal, no duplicate paths.
- Exported skin entries are marked `free`; that is metadata, not a Marketplace entitlement or rights grant.

## License

MIT for this independent prototype code. See `LICENSE`. Imported content remains under its own rights; use only files you own or have permission to process/redistribute.
