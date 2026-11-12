# Tier B documented-feature investigation

Date: 2026-11-12. Scope: four original skins, local/offline delivery, no Realms, no Microsoft/PSN sign-in requirement, no executable modification, and no authentication/DRM/account bypass.

## Decision

**Build the RP+BP as a preview-only mannequin experiment. Do not claim it equips the local player.** The documented stable Bedrock scripting surface inspected here does not expose an arbitrary PNG skin setter for the local human player. A separate standard `skin_pack` can be manually equipped through the Dressing Room on documented Windows/mobile import platforms; that is a different mechanism and is included only as an optional control.

## Evidence matrix

| Candidate | Documented behavior | Result for this experiment |
|---|---|---|
| Stable `@minecraft/server` Player | Pinned 1.17.0 and current stable 2.11.0 declarations were inspected. No local-player arbitrary-texture/skin setter was found. | Cannot implement requested automatic equip from the four PNGs. |
| `EntitySkinIdComponent` (`minecraft:skin_id`) | Read-only numeric component used to differentiate entity skins such as villager variants. | Not a player PNG setter; not used. |
| `minecraft:variant` + RP render controller | Variant selects among declared entity textures, e.g. `Array.skins[query.variant]`. | Used only on custom `tierb:skin_preview`, never on `minecraft:player`. |
| `ActionFormData` | Stable form buttons with optional RP icon paths. | Used for the four-skin selector and remove button. |
| `/scriptevent` | Game-director command; requires cheats. | Used as `/scriptevent tierb:preview` in a disposable local world. |
| Standard `skin_pack` + Dressing Room | Microsoft documents import into `skin_packs`, Classic Skins selection, and **Equip**. | Optional Windows/Android control only; manual, not script-driven. |
| Overriding `minecraft:player` / player skin in an Add-On | Microsoft cooperative Add-On guidance says not to override `minecraft:player` and that Add-Ons should not override a player's skin or Character Creator. | Rejected; not implemented. |
| GameTest `SimulatedPlayer.setSkin(PlayerSkinData)` | Official experimental/pre-release docs list this method for simulated GameTest players. `PlayerSkinData` describes Persona pieces, arm size, and skin color; it is not an arbitrary local-player PNG upload API. | Interesting but out of scope: simulated player, pre-release module, not the local human player. Not used. |
| Attachables / worn-item renderers | Can render items/armor on a player. | Would be a costume/accessory, not equipping the base player skin; not used. |
| Realms world transfer | A console delivery route discussed in earlier Tier A research. | Explicitly excluded by the user; not used or required. |
| Custom skin import on consoles | Official Character Creator FAQ says there are no plans for importing custom skins on consoles and no Bedrock cross-device custom-skin sync. | No PS4 custom-skin compatibility claim. |

## What “applied” means here

1. **Browser preview applied:** the canvas draws a PNG on a software humanoid. No Minecraft state changes.
2. **In-game preview applied:** the BP spawns a separate custom entity and triggers a variant event; the RP render controller selects one of four textures. The local player's skin data is untouched.
3. **Actual player skin applied:** the player’s configured skin changes and the player renders with it. The RP+BP does not do this. The optional standard skin pack tests the documented manual Dressing Room route on Windows/Android only.

The mocked controller tests intentionally provide a player object with no skin-related method. Static validation also rejects player-skin setter names and network modules in the shipped BP. These checks support the design boundary; they are not a substitute for client testing.

## API/version notes

- Prototype pins: `@minecraft/server 1.17.0`, `@minecraft/server-ui 1.3.0`, TypeScript 5.9.3. Microsoft maps server 1.17.0 to Minecraft 1.21.60.
- Current-registry audit additionally inspected `@minecraft/server 2.11.0` and `@minecraft/server-ui 2.3.0` declarations. The stable Player class still had no arbitrary skin setter in that static audit.
- The GameTest finding is deliberately not hidden: it is a real documented `setSkin`, but on `SimulatedPlayer` in a pre-release testing module, not a supported route for this local-player PNG prototype.
- No beta/internal script dependency or experimental capability is shipped.

## Platform limits

Windows and Android are the first required client tests, but no Minecraft client exists in this sandbox, so both are **NOT RUN**. PS4 hardware is also absent; GoldHEN FTP deployment is investigated in [PS4-FTP.md](PS4-FTP.md) but **NOT RUN**. File placement, pack activation, rendering, script execution, and actual skin equip are separate outcomes and must be recorded separately.

## Sources

Official documentation and declarations:

- Microsoft Learn, [Script Module Versioning](https://learn.microsoft.com/en-us/minecraft/creator/documents/scripting/versioning?view=minecraft-bedrock-stable) — stable 1.17.0 ↔ Minecraft 1.21.60; beta APIs require experiments.
- Microsoft Learn, [manifest.json reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/packmanifest?view=minecraft-bedrock-stable) — format versions, modules, dependencies.
- Microsoft Learn, [Creating New Entity Types](https://learn.microsoft.com/en-us/minecraft/creator/documents/introductiontoaddentity?view=minecraft-bedrock-stable) — BP entity plus RP client entity.
- Microsoft Learn, [Render Controllers](https://learn.microsoft.com/en-us/minecraft/creator/documents/animations/animationrendercontroller?view=minecraft-bedrock-stable) — texture arrays indexed by `query.variant`.
- Microsoft Learn, [minecraft:variant](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/entityreference/examples/entitycomponents/minecraftcomponent_variant?view=minecraft-bedrock-stable).
- Microsoft Learn, [scriptevent command](https://learn.microsoft.com/en-us/minecraft/creator/commands/commands/scriptevent?view=minecraft-bedrock-stable) — game directors, cheats required.
- Microsoft Learn, [Introduction to Skin Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable) and [Skin Pack JSON Reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/skinpacksreference/skinpackjsonreference?view=minecraft-bedrock-stable).
- Microsoft Learn, [Cooperative Add-On Guidelines](https://learn.microsoft.com/en-us/minecraft/creator/documents/practices/guidelinesforbuildingcooperativeaddons?view=minecraft-bedrock-stable) — do not override `minecraft:player`, player skin, or Character Creator.
- Microsoft Learn, experimental [SimulatedPlayer](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-gametest/simulatedplayer?view=minecraft-bedrock-experimental), [PlayerSkinData](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-gametest/playerskindata?view=minecraft-bedrock-experimental), and [server-gametest module](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-gametest/minecraft-server-gametest?view=minecraft-bedrock-experimental). See also [Building your first GameTest](https://learn.microsoft.com/en-us/minecraft/creator/documents/gametestbuildyourfirstgametest?view=minecraft-bedrock-stable) for the experimental/beta-module context.
- Minecraft official [Character Creator FAQ](https://www.minecraft.net/en-us/article/character-creator-faq) — Windows 10/mobile custom import; no console custom-import plans; no Bedrock cross-device custom-skin sync.
- npm official type packages: `@minecraft/server@1.17.0`, `@minecraft/server-ui@1.3.0`, `@minecraft/server@2.11.0`, `@minecraft/server-ui@2.3.0`. Local findings: `reports/current-api-audit.json` and the pinned declarations in `package-lock.json`.

Deployment/path sources are listed in [INSTALL.md](INSTALL.md) and [PS4-FTP.md](PS4-FTP.md). Community PS4 observations are treated as unverified leads, not compatibility evidence.
