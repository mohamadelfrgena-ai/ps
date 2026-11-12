# Observed client results — first real evidence

Date: 2026-11-12, 23:20 local (from the Windows taskbar in the screenshot)
Source: operator screenshot `evidence/01-dressing-room-pack-visible.png` (1920×1080)
Track: **A** — `windows-skin-test-azure.mcpack`

## What the screenshot actually shows

- Minecraft window titled **Minecraft**, screen header **Classic Skins**.
- A pack row labelled **Azure Mechanic Skin Test** — the exact pack name this kit generates.
- **Four** skin thumbnails in that row: blue, orange, green, purple.
- The purple entry is highlighted with a green selection border.
- Info panel reads **VIOLET NAVIGATOR**, **4 Skins**, **0 Texture Packs**, **0 Worlds**, **✔ Usable everywhere**.
- An **Equip** button is present at the bottom.
- The right-hand pane shows the Dressing Room character preview wearing the purple skin.
- Left sidebar shows an **OWNED SKINS** default/grey slot above the pack.

## Results

| ID | Condition | Result | Basis |
|---|---|---|---|
| W1 | Import into Windows Bedrock | **PASS** | The pack tile appears in the live client. The client must have parsed and registered the manifest for it to be listed. |
| W2 | Four skins in the real Dressing Room | **PASS** | Four thumbnails render and the info panel independently reports "4 Skins". |
| W3 | Select and equip | **PARTIAL** | Selection is confirmed (green border + preview wears it). **Equip is not confirmed** — the button is still shown and no post-equip state was captured. |
| W4 | **Real player changed in a world** | **NOT RUN** | The figure shown is the Dressing Room preview pane. No HUD, hotbar, hearts, or crosshair. Preview is explicitly excluded. |
| W5 | Persists after restart | **NOT RUN** | No restart performed in the evidence. |
| W6 | Offline / no sign-in | **NOT RUN** | No sign-in prompt visible and coin balance is 0, but neither establishes a signed-out or offline state. |

**Overall: NOT PASS.** The main criterion (W4) is still unproven.

## Two deviations from the requested test

1. **The selected skin is Violet Navigator, not Azure Mechanic.** Both are in the same pack, so this does not invalidate W1/W2 — but the requested equip target was Azure Mechanic. Either select Azure Mechanic, or record Violet Navigator as the accepted target.
2. **Equip has not been confirmed.** The Equip button is still on screen.

## What this evidence does establish

This is meaningful progress and settles several open questions:

- The package **is** accepted by a real Windows Bedrock client as a skin pack. The structural audit is corroborated by the client itself.
- **0 Texture Packs / 0 Worlds** confirms it is a pure `skin_pack`, not a resource pack — exactly as intended.
- **✔ Usable everywhere** means the client applied no usage restriction to the pack.
- All four skins are enumerated and rendered distinctly.
- No sign-in or purchase prompt blocked reaching this screen.

So the earlier concern that a custom pack might not surface at all on this platform is **resolved for Windows**: it does surface. What remains unproven is whether equipping actually changes the real player.

## Still needed

1. Exact **Minecraft version number** (bottom corner of the title screen).
2. Whether you are **signed in** to a Microsoft/Xbox account.
3. Click **Equip**, enter a world, press **F5**, and screenshot the **real player** with the HUD visible.
4. Fully close Minecraft, relaunch, re-enter the world, and screenshot again **without re-selecting** the skin.

Steps 3 and 4 are the only things that can turn this into a PASS.
