# Tier C test plan — PS4 local skin-pack loading

**Current overall result: FAIL — not demonstrated.** No PS4 hardware test has been run. This plan defines the only evidence that can change that result.

## Success criteria

All five must PASS on the same console, local user, Title ID, Minecraft version, and offline condition:

| ID | Criterion | PASS evidence | Current result |
|---|---|---|---|
| C1 | Minecraft recognizes the local skin pack | After full restart, actual client lists `Tier C Local Skin Probe` as a skin pack; screenshot/video plus path/hash evidence | **FAIL — NOT RUN** |
| C2 | Pack appears in an actual in-game selection interface | Real Dressing Room / Classic Skins UI shows the pack and four entries; not a file manager, browser, or mannequin | **FAIL — NOT RUN** |
| C3 | A skin can be selected without account sign-in | Offline select/equip completes with no Microsoft/PSN sign-in prompt; record network/profile state | **FAIL — NOT RUN** |
| C4 | Selected skin visibly applies to the real local player | Third-person or local split-screen view of the actual player shows the selected fiducial texture | **FAIL — NOT RUN** |
| C5 | Result persists after restarting Minecraft | Fully close application, relaunch offline, same local user; selected skin still equipped and visible on real player | **FAIL — NOT RUN** |

Filesystem access and backup are mandatory gates, not success substitutes. A mannequin preview is explicitly excluded.

## Preconditions / stop conditions

Stop before any Minecraft data write if any item is missing:

- Exact PS4 firmware, GoldHEN payload version, Minecraft client version, Title ID, region, local user directory, and network state recorded.
- GoldHEN already installed and authorized by the operator; no exploit installation, firmware update, cheat menu, game patch, plugin, or memory edit.
- Minecraft is Bedrock-era PS4 client. Legacy 4J Console Edition is out of scope.
- No Microsoft sign-in, PSN sign-in, Realms, Marketplace purchase, or account linking.
- Native PS4 savedata backup completed to USB for the Minecraft Title ID.
- Full read-only FTP mirror of the confirmed `com.mojang` directory completed with zero failed/skipped files.
- Local backup hash manifest generated and `verify-backup` passes.
- Operator signs the backup gate in `reports/backup-gate.json` (template provided) only after the above are true.
- Confirmed `com.mojang/skin_packs` already exists as a real directory. If absent: **stop, record C1 FAIL/not demonstrated, do not create it**.
- Target `skin_packs/tier_c_local_probe` does not already exist. If present: stop; do not overwrite/merge.
- No symlinks, dot segments, alternate user/title paths, or broad scans.

## Minimal test material

Use only `pack/tier_c_local_probe/`:

```text
manifest.json
skins.json
c1.png
c2.png
c3.png
c4.png
texts/en_US.lang
texts/languages.json
pack_icon.png
README.txt
```

Four original synthetic classic skins with distinct chest fiducials:

1. C1 Signal Red — one white scan line
2. C2 Beacon Green — two white scan lines
3. C3 Relay Blue — three white scan lines
4. C4 Marker Yellow — four white scan lines

Do not use Tier B’s mannequin, RP/BP, scripts, or browser preview as evidence.

## Procedure

### Phase 0 — identity, native backup, and baseline

1. Record exact environment fields in `reports/hardware-record.json` (template provided).
2. Photograph/record PS4 system software version, GoldHEN version, Minecraft version, Title ID/region, and local profile.
3. Keep the console offline from Microsoft/PSN/Realms. Trusted LAN FTP only. If LAN FTP requires the console network connection to remain enabled, document that WAN/internet services are disconnected/blocked and no sign-in occurs.
4. Complete the native PS4 savedata backup to USB before launching Minecraft where the Title ID can be confirmed from the library/system UI. If the Title ID cannot be confirmed without launching, record that limitation and do not enter a world.
5. Launch Minecraft to the title screen/main menu only to expose the running-title mount and record baseline Dressing Room / Classic Skins screenshots and current equipped skin. Do not enter a world yet unless required; if entered, record that normal game autosaves may occur and never edit save files manually. Launching the game may perform normal game-managed writes; no experiment file write occurs before the backup gate.

### Phase 1 — complete backup before write

1. Confirm the native PS4 savedata backup to USB is complete. Do not use save decrypt/edit/replace tools.
2. Enable GoldHEN FTP only if needed; connect to `<PS4_LAN_IP>:2121`. Stop on unexpected authentication prompts.
3. Discover the exact active path under `/app_tmp` or `/app_temp`:
   ```text
   /<app_tmp|app_temp>/<OBSERVED_TITLE_ID>/<OBSERVED_USER>/games/com.mojang
   ```
4. Verify the Title ID and user directory against the running game/local profile. Reject ambiguity.
5. Read-only inventory `com.mojang`, including `minecraftWorlds`, `skin_packs`, `resource_packs`, `behavior_packs`, and configuration files. Do not open/write saves.
6. Download the entire confirmed `com.mojang` tree to a PC backup directory. Require zero failed/skipped files. Keep no world loaded during the mirror; re-list the remote tree afterward and compare file count/sizes/mtimes where available. If the tree changed during transfer, repeat the mirror and hash step before proceeding.
7. Hash the backup:
   ```sh
   node tier-c/tools/hash-tree.mjs <backup-com.mojang> tier-c/reports/backup-manifest.json
   node tier-c/tools/verify-backup.mjs <backup-com.mojang> tier-c/reports/backup-manifest.json
   ```
8. Complete `reports/backup-gate.json`: native backup path, FTP mirror path, transfer log, zero-failure attestation, hash result, exact target path, operator, timestamp. **No upload before this gate is PASS.**

### Phase 2 — read-only skin directory discovery

1. List `<confirmed com.mojang>/skin_packs`.
2. Record whether it exists, its child folders, and whether `tier_c_local_probe` is absent.
3. If `skin_packs` is absent or inaccessible, stop. Result: C1 FAIL — not demonstrated; no speculative directory creation.
4. Optionally record whether `development_skin_packs` exists, but do not use it for the primary test.

### Phase 3 — single minimal write

1. Re-confirm backup gate PASS and target absence.
2. Create/upload only:
   ```text
   <confirmed com.mojang>/skin_packs/tier_c_local_probe/
   ```
3. Upload the unpacked files, preserving relative paths. Do not upload the `.mcpack` as the installed pack.
4. Read back every uploaded file and compare SHA-256/size against `reports/pack-manifest.json`.
5. If any mismatch occurs, stop. Remove only the newly created probe folder if safe and attested; never touch pre-existing folders or saves.

### Phase 4 — recognition and actual UI

1. Fully close Minecraft from the PS4 application menu; do not merely suspend/rest mode.
2. Relaunch Minecraft offline with the same local user and Title ID.
3. Open the actual Dressing Room / Classic Skins interface. If present, record the state of “Only Allow Trusted Skins”; if changing it is necessary, record before/after and restore afterward. This is a normal setting, not a bypass.
4. Look for `Tier C Local Skin Probe` and the four C1–C4 entries.
5. Record C1 and C2 separately. A file on disk without UI listing is C1 FAIL.

### Phase 5 — offline selection and real player application

1. With no Microsoft/PSN sign-in, select C1 Signal Red and choose Equip if the UI offers it.
2. If any sign-in prompt appears, stop and record C3 FAIL.
3. Observe the real local player in third person or local split-screen. If a world is required, use an existing local world and do not place blocks, run commands, or edit saves; normal game autosaves are game-managed, not experiment writes.
4. Verify the chest fiducial: red base, accent square, one white scan line. Record C4 only if the actual player texture changed.
5. Repeat selection for the other three skins only after criteria C1–C4 have passed for the first skin; otherwise stop at the first failure.

### Phase 6 — restart persistence

1. Record the equipped skin and real-player evidence.
2. Fully close Minecraft, wait for application termination, relaunch offline as the same local user.
3. Re-open Dressing Room and observe the real local player again.
4. C5 PASS only if the same selected skin remains equipped and visibly applied without re-selection/sign-in.

### Phase 7 — cleanup and reporting

1. Restore the original equipped skin and any changed normal game setting.
2. Remove only `skin_packs/tier_c_local_probe` if the operator wants cleanup; never delete other packs, saves, app/patch/addcont files, or executables.
3. Disable FTP.
4. Fill every criterion with PASS or FAIL and attach evidence. Any missing evidence is FAIL — not demonstrated.

## Result rules

- Overall PASS = C1 AND C2 AND C3 AND C4 AND C5 all PASS.
- FTP read/write success alone = overall FAIL.
- Pack folder present but absent from UI = C1/C2 FAIL.
- UI listing but sign-in required = C3 FAIL.
- Dressing Room preview/mannequin only = C4 FAIL.
- Works until restart only = C5 FAIL.
- No hardware run = all criteria **FAIL — NOT RUN**, not “compatible”.
