# Validation results — Windows Bedrock skin test

Date: 2026-11-12
Agent environment: Linux sandbox, Node v22.22.3, Python 3. **The agent did not launch Minecraft.**
Client evidence: an operator-run Windows session, supplied as a screenshot.

## Headline

**Update 2026-11-12 23:20:** the operator ran the pack on a real Windows Bedrock client and supplied a screenshot. **W1 (import) and W2 (four skins in the Dressing Room) now PASS on observed evidence.** W3 is PARTIAL, and **W4/W5 remain NOT RUN**, so the overall verdict is still **NOT PASS**.

See `reports/observed-results.md` for the evidence analysis and `evidence/01-dressing-room-pack-visible.png` for the screenshot.

Everything labelled PASS in section 2 below is a file-level check performed in this sandbox, not a client behaviour.

---

## 1. Audit of the existing Tier C package

| Question | Finding |
|---|---|
| Is it a real Bedrock skin pack? | **Yes.** `manifest.json` declares exactly one module of type `skin_pack`. Not `resources`, not `data`. |
| Is it a Resource Pack preview? | **No.** No `entity/`, no `render_controllers/`, no `scripts/`, no textures tree. The Tier B mannequin was a separate RP+BP pair and is not part of this. |
| `manifest.json` valid? | Yes. `format_version: 2`, header + module UUIDs both valid v4 and different, version triples present. |
| `skins.json` valid? | Yes. `serialize_name` = `localization_name` = `tier_c_local_probe`; four entries, each `geometry.humanoid.custom`, `type: free`, texture at pack root. |
| Textures valid? | Yes. All four fully decoded (not just header-checked): 64×64, 8-bit, RGBA colour type 6, non-interlaced, all PNG chunk CRCs correct, 1632 opaque pixels each, head and torso regions painted. |
| Are the four skins distinct? | Yes. Four different chest colours confirmed by pixel sampling. |
| Localization correct? | Yes. `skinpack.tier_c_local_probe` and `skin.tier_c_local_probe.c1…c4` keys all present and matching. |
| ZIP structure correct? | Yes. `manifest.json` at archive root, no wrapper folder, no traversal paths, UTF-8 flag set, all CRCs valid, Python `zipfile.testzip()` clean. |
| **Naming issue found** | **The Tier C pack does NOT contain "Azure Mechanic".** Its skins are C1 Signal Red, C2 Beacon Green, C3 Relay Blue, C4 Marker Yellow. "Azure Mechanic" belongs to Tier B's manual control pack (`tier-b/packs/tier_b_skins`). Both tracks are therefore provided. |

### Windows compatibility assessment

The format used is the documented Microsoft skin-pack format, and Windows Bedrock supports local `.mcpack` skin-pack import. On that basis the package is **expected to be compatible**.

**Expected is not verified.** No client was run. Actual compatibility is **NOT RUN** until you perform the checklist.

One environment factor materially affects the instructions: Bedrock on Windows moved from UWP to GDK at **1.21.120**, relocating `com.mojang` from `%localappdata%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\` to `%appdata%\Minecraft Bedrock\Users\...`. `INSTALL-WINDOWS.md` covers both.

---

## 2. Checks actually executed in this sandbox

| Check | Result | Evidence |
|---|---|---|
| Node test suite | **PASS 8/8** | `reports/test-run.txt` |
| Deep structural validation (both packs) | **PASS 108/108** | `reports/structural-validation.json` |
| Python `zipfile` CRC verification | **PASS** (both archives) | `reports/python-zip-check.json` |
| Full PNG decode of all 8 textures | **PASS** | included in the 108 checks |
| Textures byte-identical to frozen sources | **PASS** | `reports/source-audit.json` + test 4 |
| Rebuild determinism | **PASS** | test 7 |
| UUIDs collide with no existing tier pack | **PASS** | validator scans all frozen manifests |
| Tier A/B/C files unchanged | **PASS 147/147** | `reports/freeze-before.json`, `reports/freeze-after.json` |

### What these checks prove

The archives are well-formed, standard Bedrock skin packs that a Windows client should be able to parse, containing four valid distinct classic skins and no mannequin or executable content.

### What these checks do NOT prove

They say nothing about whether Minecraft actually imports the pack, lists it, lets you equip it, renders it on the real player, or keeps it after restart. Structure passing is not behaviour passing.

---

## 3. Required client tests — operator evidence received

Evidence: `evidence/01-dressing-room-pack-visible.png`, operator Windows session, 2026-11-12 23:20.

| ID | Condition | Result | Basis |
|---|---|---|---|
| W1 | Import into Minecraft Bedrock for Windows | **PASS** | Pack tile "Azure Mechanic Skin Test" is listed in the live client's Classic Skins screen |
| W2 | Four skins found in the real Dressing Room | **PASS** | Four thumbnails render; info panel independently reports "4 Skins" |
| W3 | Select **Azure Mechanic** and equip | **PARTIAL** | Selection confirmed, but the operator selected **Violet Navigator**, and **Equip was not confirmed** |
| W4 | **Real local player model changed in a world** | **NOT RUN** | Only the Dressing Room preview pane is visible — no HUD/hotbar. Preview is excluded |
| W5 | Persists after restarting Minecraft | **NOT RUN** | No restart in the evidence |
| W6 | Works signed out / offline | **NOT RUN** | Sign-in and network state not established by the screenshot |

**Overall Windows verdict: NOT PASS — main criterion W4 unproven.**

Corroborated by the client itself: the pack registers as a skin pack, reports **0 Texture Packs / 0 Worlds** (so it is not a resource pack), and shows **✔ Usable everywhere**. The pack-format question is settled for Windows; the equip-and-apply question is not.

Explicitly excluded as evidence: the Dressing Room preview on its own, menu thumbnails, the Tier B mannequin, browser renders, and the mere presence of files on disk.

---

## 4. Offline / no-sign-in investigation (research only)

What the sources support:

- Windows Bedrock imports local `.mcpack` skin packs through the game's own importer; no account purchase is involved for a `type: free` local pack.
- On GDK builds, signed-out players use the shared directory `%appdata%\Minecraft Bedrock\Users\Shared\games\com.mojang`, which is where add-ons and skin packs live for all users.
- The game may still require a previously cached Microsoft Store licence to launch offline. If it refuses, that is a platform licensing blocker, not a pack defect.
- Cloud/Marketplace and account-bound skins behave differently from a local skin pack; general advice about "skins reverting to Steve offline" mostly concerns account skins, not locally imported packs.

**Conclusion: plausible but unverified.** W6 in the checklist is designed to settle it. No bypass, patch, or authentication change is proposed or permitted.

---

## 5. Artifacts

| File | Pack name | Equip target | Bytes | SHA-256 |
|---|---|---|---|---|
| `windows-skin-test.mcpack` | Tier C Windows Skin Test | C1 Signal Red | 14655 | `b0fbcd6f6f125780e3a467edd6cc905cd95ae1611b081bf67b707b195edafc5e` |
| `windows-skin-test-azure.mcpack` | Azure Mechanic Skin Test | **Azure Mechanic** | 14639 | `85c5ef3470f5bed5b796865fb776079cece767d61c8b1b53f122d2487712d19a` |

Unpacked equivalents for manual installation: `pack/tier_c_windows/`, `pack/tier_b_azure_windows/`.

Verify on Windows with:
```powershell
Get-FileHash .\windows-skin-test-azure.mcpack -Algorithm SHA256
```

---

## 6. Sources

- Microsoft Learn, *Introduction to Skin Packs* — pack structure, `.mcpack` double-click import, Dressing Room, and the "Only Allow Trusted Skins" fix: https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable
- Minecraft Wiki, *com.mojang* — current Windows paths, per-user vs `Users\Shared` for signed-out players: https://minecraft.wiki/w/Com.mojang
- Bedrock GDK migration at 1.21.120 and the resulting folder move: https://jaylydev.github.io/posts/bedrock-gdk/
- Custom skin pack storage location on Windows: https://foxynotail.com/skin-packs/how-to-install

No source above is a substitute for running the client. Your checklist results are the deciding evidence.
