# Skin Lab — test results

**Date:** 12 November 2026  
**Scope:** static upstream audit, reference-pack checks, Node pack-core tests, structural pack validation, and headless browser smoke tests.  
**Not tested:** Minecraft client import/equip, Realms delivery, PS4 hardware, Java mod execution.

## Summary

| Layer | Result | Evidence |
|---|---:|---|
| Upstream static inventory/source audit | Completed | `reports/upstream-inventory.json`, `FEASIBILITY.md` |
| Official reference archive + API declaration checks | 18 PASS, 2 WARN | `reports/reference-audit.json` |
| Node pack-core tests | 9 PASS, 0 FAIL | `reports/node-tests.txt` |
| Demo `.mcpack` structural validation | STRUCTURAL_PASS, 0 warnings | `reports/demo-validation.json` |
| Browser-exported `.mcpack` structural validation | STRUCTURAL_PASS, 0 warnings | `reports/browser-export-validation.json` |
| Headless browser UI smoke | 13 PASS, 0 console/page errors | `reports/browser-smoke.json`, `reports/browser-ui.png` |
| Windows/mobile Minecraft import/equip | NOT RUN | requires a real Bedrock client |
| RP/BP in-game preview add-on | NOT BUILT / NOT RUN | deferred Tier B experiment |
| Realms delivery / PS4 behavior | NOT RUN | requires subscription, authoring device, and console |

A structural pass means the archive satisfies this prototype's stated checks. It is **not** a Minecraft acceptance, Marketplace, Realms, or PS4 compatibility certificate.

## Commands run

From `skin-lab/`:

```bash
node tools/make-examples.js
npm test
npm run validate
node tools/validate-pack.js reports/browser-export.mcpack
node tools/serve.js
```

Browser smoke used a temporary headless Chromium runner outside the project (`playwright-core` + `@sparticuz/chromium`) against:

- `http://127.0.0.1:4173/`
- `file:///home/user/ps/skin-lab/index.html`

The static preview server was started on `0.0.0.0:4173`.

## Node pack-core tests

`npm test` passed 9 tests:

1. `safePath` rejects traversal, absolute, backslash, drive, control, and empty segments.
2. `makeZip`/`readZip` round-trip stored entries; `validatePack` accepts a generated pack.
3. `readZip` accepts deflate entries.
4. `makeZip` rejects duplicate and unsafe paths.
5. `readZip` rejects traversal, duplicate, encrypted, symlink, and CRC-mismatch fixtures.
6. `inspectPng` accepts demo textures and rejects truncation/corruption.
7. `validatePack` rejects missing files, unsupported geometry, and extra script modules.
8. `buildPack` creates fresh UUIDs and rejects empty/oversized/invalid input.
9. The generated demo `.mcpack` passes structural validation.

## Browser smoke checks

Passed:

- Demo gallery renders two original synthetic skins.
- Preview panel is visible and selects the first demo skin.
- Selecting the second skin shows slim metadata.
- Favorite toggle and favorites-only filter work.
- Search filter works.
- Select-visible updates the export selection count.
- Export downloads a `.mcpack` file.
- Export status explicitly says it is not a PS4 install.
- Yaw reset returns to the default view.
- `file://` loading renders the demo gallery.
- No page errors or console errors were recorded.

The browser-exported archive was saved as `reports/browser-export.mcpack` and revalidated:

- 2 skins: classic + slim
- 64×64 PNG textures
- fresh UUIDs
- 0 structural warnings

## Reference warnings preserved

The official Microsoft sample is minimal and differs from the packaging guide in two places:

1. Its per-skin localization key uses the texture basename rather than the entry's `localization_name`.
2. It omits `texts/languages.json`.

The prototype exporter uses matching safe localization identifiers and includes `languages.json`. Those choices still need a real Windows/mobile import test.

## Explicit non-results

- The Java mod was **not** compiled or run; Java is unavailable in this workspace.
- No `.mcpack` was imported into Minecraft here.
- No player skin was equipped here.
- No Realms world was uploaded or joined here.
- No PS4 test was performed.
- No claim is made that arbitrary local PNGs can be equipped on PS4 through RP/BP/scripting.
