# Tier C test results — 2026-11-12

## Overall

**FAIL — not demonstrated.** No PS4 hardware test was run. The local artifact is a structurally valid Bedrock `skin_pack` candidate, but PS4 recognition, offline selection, real-player application, and restart persistence are **NOT RUN**.

## Hardware success criteria

| ID | Criterion | Result | Status | Evidence |
|---|---|---|---|---|
| C1 | Minecraft recognizes the local skin pack | **FAIL** | NOT RUN | None |
| C2 | Pack appears in an actual in-game selection interface | **FAIL** | NOT RUN | None |
| C3 | Skin can be selected without account sign-in | **FAIL** | NOT RUN | None |
| C4 | Selected skin visibly applies to the real local player | **FAIL** | NOT RUN | None |
| C5 | Result persists after restarting Minecraft | **FAIL** | NOT RUN | None |

Overall PASS requires C1 AND C2 AND C3 AND C4 AND C5. A mannequin preview, browser preview, FTP file existence, or RP/BP activation is not success.

## Mandatory gates

| Gate | Result | Status |
|---|---|---|
| Filesystem access to active PS4 Minecraft mount | **FAIL** | NOT RUN |
| Complete verified backup before any experiment write | **FAIL** | NOT RUN |

## Local checks actually run

| Check | Result | Evidence |
|---|---|---|
| Node test suite | **PASS 9/9** | `reports/final-test-run.txt` |
| Backup helper tests | **PASS 4/4** included above | `tests/backup.test.mjs` |
| Structural pack validation | **PASS 23/23** | `reports/structural-validation.json` |
| Python ZIP CRC readback | **PASS** | `reports/python-zip-check.json` |
| Deterministic reference `.mcpack` SHA-256 | `d4b35dc2544c6abb2086d96ca12f973010feb08435bcff36ced9012143b976f2` | `dist/SHA256SUMS.txt`, `reports/pack-manifest.json` |
| Tier A/B preservation | **UNCHANGED 109/109 files** | `reports/tier-ab-before.json`, `reports/tier-ab-after.json` |

Local checks prove only artifact structure and helper behavior. They do not prove PS4 loading, UI visibility, equip, or persistence.

## Research conclusions

- Official Minecraft guidance still limits custom skin import to Windows 10/mobile and states no plans for console custom-skin import.
- GoldHEN FTP can expose filesystem paths, but filesystem access is not pack recognition.
- The candidate PS4 path is `<confirmed com.mojang>/skin_packs/tier_c_local_probe/`, only if `skin_packs` already exists and the exact active Title ID/user mount is confirmed.
- No positive PS4 FTP local skin-pack loading evidence was found; an Xbox FTP negative prior exists but is not PS4 proof.
- Exact firmware/GoldHEN/Minecraft version/Title ID must be recorded on hardware; no universal compatible version is asserted.

## Machine-readable record

`reports/results.json`
