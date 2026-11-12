# Exact installation and first-test instructions

**Read first:** the paired RP+BP is a **mannequin preview**. It does not change the local player's skin. The optional `tier-b-manual-skins-CONTROL.mcpack` is a separate skin-pack control for Windows/Android manual Dressing Room testing. Neither route has been executed on a Minecraft client in this sandbox.

Use an already installed, legitimately obtained Minecraft Bedrock client and a disposable **local** world. Do not create a Realm, join a server, or add a Microsoft/PSN sign-in step. If the platform itself refuses to launch or create a local world without an account, record that as a platform blocker; do not bypass it.

## 0. Files and version target

From `tier-b/dist/`:

- `tier-b-preview.mcaddon` contains exactly `tier-b-preview-rp.mcpack` and `tier-b-preview-bp.mcpack`.
- `tier-b-preview-rp.mcpack` and `tier-b-preview-bp.mcpack` are also available separately.
- `tier-b-manual-skins-CONTROL.mcpack` is optional and separate.

The manifests target `min_engine_version [1,21,60]`, `@minecraft/server 1.17.0`, and `@minecraft/server-ui 1.3.0`. Microsoft maps server API 1.17.0 to Minecraft 1.21.60. This is a documented minimum, **not** a tested compatibility range. Record the exact client version before testing.

Verify hashes if desired:

```sh
cd tier-b/dist
sha256sum -c SHA256SUMS.txt
```

On Windows PowerShell, compare individually:

```powershell
Get-FileHash .\tier-b-preview.mcaddon -Algorithm SHA256
Get-Content .\SHA256SUMS.txt
```

## 1. Windows Bedrock — test first

### Preferred: import the paired add-on

1. Close Minecraft.
2. Copy `tier-b-preview.mcaddon` to the Windows machine.
3. Double-click it and choose Minecraft if prompted. Wait for both import-success messages.
4. Launch Minecraft and create a new local Creative world named `TierB preview test`.
5. In world settings, enable **Cheats** (required for `/scriptevent`). Do not enable Beta APIs; this prototype uses stable modules only.
6. Under **Resource Packs**, activate **Tier B Preview Resources**.
7. Under **Behavior Packs / Add-Ons**, activate **Tier B Preview Behavior**. Accept its resource-pack dependency if prompted.
8. Create/enter the world. Stand in an open flat area with at least two air blocks in front of you.
9. Open chat and run exactly:
   ```text
   /scriptevent tierb:preview
   ```
10. Confirm the form title says **Tier B | PREVIEW ONLY** and shows four skin buttons plus **Remove preview**.
11. Select each skin. Confirm a separate labeled mannequin appears and changes texture. Confirm your own player in third person remains unchanged.
12. Select **Remove preview** and confirm the mannequin disappears.

### Developer-folder alternative

Current Windows Bedrock stores shared data at:

```text
%appdata%\Minecraft Bedrock\Users\Shared\games\com.mojang
```

With Minecraft closed, copy the unpacked folders so the result is exactly:

```text
com.mojang\development_resource_packs\tier_b_resource\manifest.json
com.mojang\development_behavior_packs\tier_b_behavior\manifest.json
```

Do not nest an extra `packs` or `tier_b_resource` directory. Restart Minecraft and activate both packs in the disposable world as above. Imported `.mcpack` files instead land in `resource_packs` / `behavior_packs`; do not keep duplicate development and imported copies active during the test.

Legacy UWP installs may use `%LocalAppData%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\com.mojang`, but Microsoft's samples report that this old development-pack path is no longer detected by current builds. Prefer the current shared path above or `.mcaddon` import.

## 2. Android Bedrock — test second

1. Transfer `tier-b-preview.mcaddon` to the device, for example into `Download`.
2. Launch Minecraft once and, in **Settings → Profile / Storage**, choose **External** file storage if that option is available. Do not move or delete existing worlds.
3. Open the file with a file manager capable of handing `.mcaddon` files to Minecraft. Choose **Open with Minecraft**. Wait for both import-success messages.
4. Create a new local Creative world named `TierB preview test`, enable **Cheats**, and activate both Tier B packs exactly as on Windows.
5. Enter the world, stand in a clear area, and run `/scriptevent tierb:preview`.
6. Repeat the four-button, player-unchanged, and remove-preview checks.

Modern Android commonly exposes external Minecraft data at:

```text
Internal storage/Android/data/com.mojang.minecraftpe/files/games/com.mojang
```

Older versions may expose `games/com.mojang` at shared-storage root. Android 11+ scoped storage can block ordinary file managers from `Android/data`. Use the normal `.mcaddon` open-with import first. If direct folder access is required, use a file manager that legitimately supports the system document picker or an authorized USB/ADB workflow; do not root or bypass Android permissions for this experiment. The manual development-folder layout is the same as Windows (`development_resource_packs/tier_b_resource`, `development_behavior_packs/tier_b_behavior`) under the actual active `com.mojang` directory.

## 3. Optional actual-player control — Windows/Android only

This is **not** the RP+BP preview and is not a PS4 procedure.

1. Import `tier-b-manual-skins-CONTROL.mcpack` separately.
2. Open **Dressing Room → Classic Skins** and locate **Tier B Manual Skins (control)** under owned/classic packs (menu wording varies by version).
3. Select one of the four skins and press **Equip**.
4. Enter a local world and verify the actual player texture changed.
5. Record whether any sign-in prompt appeared. The intended test is offline/local; if the client requires an account, stop and record the blocker rather than bypassing it.
6. Restore the original skin afterward.

Microsoft documents this manual skin-pack route and custom import on Windows 10/mobile. Its console FAQ says there are no plans for custom-skin import on consoles. This control therefore cannot establish PS4 compatibility.

## 4. Test record template

Copy this into the issue/report for each device:

```text
Platform: Windows / Android
Minecraft exact version:
OS version:
Install method: mcaddon / development folders
RP import result:
BP import result:
Both packs active:
Cheats enabled:
/scriptevent menu opened:
Four buttons + Remove preview visible:
Azure / Ember / Moss / Violet mannequin rendered:
Actual player skin unchanged during RP+BP test:
Remove preview worked:
Content Log errors (paste exact lines):
Optional manual skin-pack equip result (separate test):
Any sign-in prompt or platform blocker:
Screenshots/video:
Tester / date:
```

## 5. Cleanup and troubleshooting

- Use **Remove preview** before leaving. The script also attempts cleanup on player leave. If a mannequin remains after a crash, remove only the custom entity: `/kill @e[type=tierb:skin_preview]` in the disposable test world.
- Deactivate both packs before deleting them. Remove only the Tier B folders/imports; do not delete `minecraftWorlds` or unrelated packs.
- If the command does nothing: confirm both packs are active, cheats are on, the event is exactly `tierb:preview`, and the client meets the API minimum. Check the Content Log for script/manifest errors.
- If the form opens but the mannequin is invisible/missing: confirm the RP is active and the BP dependency resolved. Record the exact client version and Content Log; do not infer console behavior from Windows.
- If placement fails: move to a clear flat area. The script intentionally refuses to spawn inside blocks.
- If a skin-pack control is hidden on Windows/Android, Microsoft's skin-pack troubleshooting mentions the **Only Allow Trusted Skins** setting. Change it only for this local test if you understand the trust implication, and restore it afterward. This is not a console workaround.

PS4 deployment is deliberately separated into [PS4-FTP.md](PS4-FTP.md) and remains **NOT RUN**.

## Path/instruction sources

- Microsoft Learn, [Getting Started with Add-On Development](https://learn.microsoft.com/en-us/minecraft/creator/documents/gettingstarted?view=minecraft-bedrock-stable) — Windows/Android `.mcpack` import and development-pack folders.
- Microsoft Learn, [Resource Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/resourcepack?view=minecraft-bedrock-stable) and [Behavior Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/behaviorpack?view=minecraft-bedrock-stable) — current Windows shared `com.mojang` path and pack activation.
- Microsoft samples, [issue #131](https://github.com/microsoft/minecraft-samples/issues/131) — old UWP LocalState development-pack path no longer detected; use the current shared development folders.
- Microsoft Learn, [scriptevent command](https://learn.microsoft.com/en-us/minecraft/creator/commands/commands/scriptevent?view=minecraft-bedrock-stable) — cheats required.
- Bedrock.dev community guide, [Project Setup Android](https://wiki.bedrock.dev/guide/project-setup-android) — scoped-storage `Android/data/com.mojang.minecraftpe/files/games/com.mojang` path and access limitations. Prefer the normal import route; this community path is not a hardware-test result.
- Microsoft Learn, [Introduction to Skin Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable) — manual Dressing Room equip and trusted-skins troubleshooting.

These sources guide the procedure; none means this prototype was imported or executed on Windows or Android here.
