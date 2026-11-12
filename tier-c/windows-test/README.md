# Windows Bedrock real-skin test kit

Clean skin packs plus exact instructions to test whether a local skin pack changes the **real local player's appearance** in Minecraft Bedrock on Windows.

**The Minecraft client was not launched. All in-client tests are NOT RUN.**

## Start here

1. Read `INSTALL-WINDOWS.md` — install and test steps.
2. Fill in `TEST-CHECKLIST.md` as you go.
3. Current evidence status is in `VALIDATION-RESULTS.md`.

## Important: "Azure Mechanic" is a Tier B skin, not Tier C

The audit found that the Tier C pack contains **C1 Signal Red, C2 Beacon Green, C3 Relay Blue, C4 Marker Yellow**. "Azure Mechanic" lives in Tier B's manual control pack. Both are shipped so nothing is guessed:

| File | Pack | Equip target |
|---|---|---|
| `windows-skin-test.mcpack` | Four **Tier C** skins | C1 Signal Red |
| `windows-skin-test-azure.mcpack` | Four **Tier B** skins incl. **Azure Mechanic** | **Azure Mechanic** |

To follow your steps literally, use `windows-skin-test-azure.mcpack`.

Both are genuine `skin_pack` modules — no resource pack, no mannequin, no scripts. Textures are byte-identical copies of the frozen originals.

## Contents

- `windows-skin-test.mcpack`, `windows-skin-test-azure.mcpack` — importable packs
- `pack/` — unpacked equivalents for manual installation
- `INSTALL-WINDOWS.md`, `TEST-CHECKLIST.md`, `VALIDATION-RESULTS.md`
- `SHA256SUMS.txt`
- `tools/`, `tests/` — build and validation
- `reports/` — audit, validation, freeze checks, and a blank test record

## Local verification performed

| Check | Result |
|---|---|
| Node tests | PASS 8/8 |
| Structural validation | PASS 108/108 |
| Python ZIP CRC | PASS |
| Tier A/B/C unchanged | PASS 147/147 |

Rebuild and re-verify:
```sh
cd tier-c/windows-test
npm test
```

These prove file structure only. They do **not** prove Minecraft imports, displays, equips, or persists the skin.

## Success rule

Overall PASS requires import, four skins visible in the Dressing Room, successful equip, **the real player visibly changed in a world**, and persistence after a full restart. A Dressing Room preview or mannequin does not count.

No Minecraft executable, authentication, or DRM modification is involved.
