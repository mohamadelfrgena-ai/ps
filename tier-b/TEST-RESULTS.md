# Tier B test results

Date: 2026-11-12. Environment: Linux sandbox, Node v22.22.3, npm 10.9.8. **No Minecraft client, Windows Bedrock instance, Android device/emulator, Realms account, or PS4 hardware was available.**

## Executive result

| Layer | Result | Evidence |
|---|---|---|
| TypeScript build | PASS | `npx tsc -p tsconfig.json`, strict, `noEmitOnError` |
| Mocked controller logic | **17/17 PASS** | `reports/node-tests.txt`, `reports/final-test-run.txt` |
| Pack/archive structure | **58/58 PASS — STRUCTURAL_PASS** | `reports/structural-validation.json` |
| Independent Python ZIP CRC check | **4/4 archives PASS** | `reports/python-zip-check.json` |
| Headless browser UI | **16/16 PASS, 0 console/page errors** | `reports/browser-smoke.json`, screenshots |
| Artifact hash verification | **4/4 OK** | `dist/SHA256SUMS.txt`, final run log |
| Tier A preservation | **UNCHANGED — 28/28 files** | `reports/tier-a-before.json`, `reports/tier-a-after.json` |
| Windows Bedrock import/activation/render/script | **NOT RUN** | Requires actual Windows client |
| Android Bedrock import/activation/render/script | **NOT RUN** | Requires actual Android client/device |
| Optional manual skin-pack equip | **NOT RUN** | Requires Windows/Android Dressing Room |
| PS4 GoldHEN FTP transfer/discovery/activation/render | **NOT RUN** | Requires actual PS4 hardware |
| PS4 compatibility | **UNPROVEN** | No hardware test; do not claim compatibility |

Structural/mock/browser success is not Minecraft runtime success. The custom validator checks this prototype's expected structure; it is not Mojang's client importer or an official certification tool.

## Executed commands

From `tier-b/`:

```sh
npm ci
npm test
LD_LIBRARY_PATH=/tmp/al2023/lib:/tmp node tools/browser-smoke.mjs
node --check packs/tier_b_behavior/scripts/main.js
node --check packs/tier_b_behavior/scripts/controller.js
node --check packs/tier_b_behavior/scripts/catalog.js
node --check src/app.js
node --check src/preview.js
(cd dist && sha256sum -c SHA256SUMS.txt)
```

`npm test` rebuilds assets/scripts/archives, runs all Node tests, then runs structural validation. The final combined run passed. The browser test exercised HTTP and `file://`, selection, canvas changes, rotation, image decoding, all four downloads, mobile overflow, and the preview/equip boundary. It ran in headless Chromium on Linux, **not** in Minecraft and not on Windows/Android.

## What the 17 Node tests cover

- Four skin buttons plus remove; preview-only title/body and icon paths.
- Selection spawns only `tierb:skin_preview` and triggers the correct variant event.
- All four variant mappings; replacement removes the previous owned preview.
- Remove button, cancel/busy, invalid selection, unrelated event, and non-player source handling.
- Blocked placement preserves an existing preview; leave cleanup; invalid player after form close; form rejection; ownership mismatch protection.
- ZIP round-trip/CRC, unsafe/duplicate path rejection, corruption rejection, and shipped archive parsing.

The player mock deliberately has no skin setter. Static validation also checks that the shipped BP contains no player-skin setter/network/executable-hook names. These are design-boundary checks, not proof about every possible Minecraft API.

## Artifacts verified

| File | Bytes | SHA-256 |
|---|---:|---|
| `tier-b-preview-rp.mcpack` | 17,060 | `a1c8858f5b5611580ed65d98eec63b4f917fc06d32f6cf84c47c716ca945572e` |
| `tier-b-preview-bp.mcpack` | 10,368 | `6d5b5a2451174bcb18c8afe23ea3a566ae2f1aa568677736a86f2dc6392a4b75` |
| `tier-b-manual-skins-CONTROL.mcpack` | 15,087 | `f9a748ef7d4f806ee30c5d57282cd741423020e123a8dde6d391058f92a698f5` |
| `tier-b-preview.mcaddon` | 27,698 | `6f4a499f1933263ac6d03203da5a331a3f876a2624550c14978f4870317f9aa0` |

The `.mcaddon` contains exactly the RP and BP `.mcpack` files. The optional manual skin pack is separate and is not included in the preview `.mcaddon`.

## Documented-feature investigation result

- Pinned stable declarations (`@minecraft/server 1.17.0`, `server-ui 1.3.0`) and current stable declarations audited (`2.11.0`, `2.3.0`) exposed no arbitrary-PNG setter on the local human `Player` class. See `reports/current-api-audit.json` and `RESEARCH.md`.
- `EntitySkinIdComponent.value` is read-only and villager-style; it is not a player texture setter.
- Official experimental GameTest docs do list `SimulatedPlayer.setSkin(PlayerSkinData)`. That is a simulated test-player/Persona-data route in a pre-release module, not the requested local-player PNG equip route. It was not used.
- The RP+BP therefore previews on a separate entity. The optional standard skin pack is only a manual Windows/Android Dressing Room control; it has not been tested here and is not a PS4 solution.

## Remaining required hardware/client tests

Run [INSTALL.md](INSTALL.md) on Windows first, then Android. Record import, activation, command/form, mannequin rendering, **actual player unchanged**, removal, Content Log errors, exact versions, and any sign-in/platform blocker separately. Only after those results should the optional PS4 procedure in [PS4-FTP.md](PS4-FTP.md) be attempted on authorized hardware. A successful FTP copy alone is not pack activation or compatibility.

No Realms or sign-in was used in the executed sandbox checks. No executable files were modified. Tier A hashes remained unchanged.
