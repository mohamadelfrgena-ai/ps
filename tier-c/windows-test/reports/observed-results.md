# Observed client results — Windows Bedrock

**Overall: PASS** — the real local player's appearance was changed by a locally imported skin pack, and it survived a full restart.

Date: 2026-11-12
Operator: ran the test on their own Windows PC
Agent: did **not** launch Minecraft; recorded and validated the operator's evidence

## Session conditions

| Field | Value |
|---|---|
| Pack | `windows-skin-test-azure.mcpack` (Track A) |
| Signed in to Microsoft account | **Yes** |
| Minecraft version | Operator reports "latest"; **exact build number not supplied** |
| Network | Presumed online (signed in) |
| Skin equipped | **Azure Mechanic** |

## Results

| ID | Condition | Result | Evidence basis |
|---|---|---|---|
| W1 | Import into Minecraft Bedrock for Windows | **PASS** | Screenshot |
| W2 | Four skins in the real Dressing Room | **PASS** | Screenshot |
| W3 | Select **Azure Mechanic** and equip | **PASS** | Operator attestation |
| W4 | **Real local player changed in a world** | **PASS** | Operator attestation — in-world third person (F5), HUD visible |
| W5 | Persists after full restart | **PASS** | Operator attestation — closed, relaunched, still applied, not re-selected |
| W6 | Offline / no Microsoft sign-in | **NOT RUN** | Operator was signed in; separate test required |

All five required conditions are met, so the overall verdict is **PASS**.

## Evidence detail

### Screenshot — supports W1 and W2

`evidence/01-dressing-room-pack-visible.png` (1920×1080, SHA-256 `1b3049b2…46af8e9f7fe3`)

Shows the live client's **Classic Skins** screen with:
- a pack row named **Azure Mechanic Skin Test** — the exact name this kit generates;
- **four** skin thumbnails (blue, orange, green, purple);
- an info panel reporting **4 Skins**, **0 Texture Packs**, **0 Worlds**, **✔ Usable everywhere**;
- an **Equip** button.

The "0 Texture Packs / 0 Worlds" line independently confirms the pack registered as a pure `skin_pack`, not a resource pack — matching the structural audit.

### Operator attestation — supports W3, W4, W5

Confirmed through a structured questionnaire on 2026-11-12:

- **Equipped skin:** Azure Mechanic (the requested target, not the Violet Navigator seen selected in the earlier screenshot).
- **Where observed:** inside a world, third-person view via F5, with the hotbar visible — i.e. the actual player character, not the Dressing Room preview.
- **Restart:** Minecraft fully closed and relaunched, world re-entered, skin still applied **without re-selecting it**.

No screenshots were supplied for W4/W5. These rows are therefore recorded as **operator-verified**, not documentarily verified. Adding screenshots later would upgrade the evidence class without changing the verdict.

## What this settles

- A conventional local Bedrock `skin_pack` **is** imported, listed, and equipable on Windows.
- Equipping it **does** change the real local player's appearance in a world.
- The selection **does** persist across a full client restart.
- The pack contains no mannequin, resource pack, behavior pack, or script — the result comes from a plain skin pack.

The earlier open question "does a locally supplied skin pack actually change the player, or only show a preview?" is now answered for Windows: **it changes the player.**

## What this does NOT settle

1. **Offline / no account (W6).** The operator was signed in throughout. The original Tier C requirement of no Microsoft/PSN sign-in is **still unproven**. To settle it: sign out via Settings → Profile, disconnect the internet, and repeat W1–W5. On GDK builds, signed-out players use `%appdata%\Minecraft Bedrock\Users\Shared\games\com.mojang`.
2. **PS4.** A Windows result does not transfer to console. All Tier C PS4 criteria remain **NOT RUN**.
3. **Exact version.** "Latest" is not a recordable build identifier. The result cannot be pinned to a specific release until the number is supplied (title-screen corner or Settings → Profile).

## Optional strengthening

- Screenshot of the real player in-world with the HUD visible (W4).
- Screenshot after restart, same world, skin still applied (W5).
- Exact Minecraft build number.

None of these change the current verdict; they only make the record more robust.
