# Tier C installation instructions — backup-gated PS4 FTP probe

**Do not run this casually.** Current PS4 result is **FAIL — not demonstrated / NOT RUN**. These instructions are for an operator with an authorized GoldHEN PS4, exact version records, and a complete verified backup. No Microsoft/PSN sign-in, Realms, executable modification, auth/DRM bypass, save editing, or original game-file replacement is permitted.

## 0. What is installed

The PS4 candidate is the **unpacked folder**:

```text
tier-c/pack/tier_c_local_probe/
```

The file `tier-c/dist/tier-c-local-probe.mcpack` is a reference ZIP container for supported import platforms; **do not assume FTP + `.mcpack` invokes a PS4 import handler**.

Target, only after discovery and backup:

```text
/app_tmp/<OBSERVED_TITLE_ID>/<OBSERVED_USER>/games/com.mojang/skin_packs/tier_c_local_probe
```

or the same path under `/app_temp` if that is the observed root. Never hardcode the examples `CUSA00744`, `CUSA00265`, or `CUSA00283`; use the exact running/internal Title ID. `CUSA17908` is a Bedrock physical Europe disc label mapped by SerialStation to internal `CUSA00265`, not a safe FTP directory guess.

## 1. Record exact environment

Create `tier-c/reports/hardware-record.json` from the template and fill:

- PS4 model and exact system software/firmware version.
- Exact GoldHEN payload/release version; FTP enabled; cheats/patches/plugins disabled.
- Exact Minecraft Bedrock client version and edition evidence.
- Exact Title ID, region/release, and observed FTP title directory.
- Exact observed local user directory/profile.
- Offline/network state: no Microsoft/PSN/Realms; trusted LAN FTP only.

If any field is unknown, stop.

## 2. Complete backup before any Minecraft data write

### 2.1 Native savedata backup

On the PS4, use native savedata management to copy Minecraft savedata for the exact Title ID to USB. Do not decrypt, edit, rename, or replace save files. Record USB backup location and completion evidence.

### 2.2 Read-only FTP discovery

1. Launch Minecraft to expose the running-title mount; prefer title screen/main menu.
2. Enable GoldHEN FTP if needed. Connect from the PC:
   ```text
   Host: <PS4_LAN_IP>
   Port: 2121
   Protocol: FTP
   Logon: anonymous only if the local GoldHEN service accepts it
   ```
   Unexpected auth prompt → stop.
3. Read-only list `/app_tmp` and `/app_temp`. Identify the exact running Title ID directory and local user directory. Reject ambiguity/symlinks.
4. Confirm:
   ```text
   <root>/<TITLE_ID>/<USER>/games/com.mojang
   ```
5. Confirm `com.mojang/skin_packs` already exists. If absent, stop; do not create it.

Read-only connectivity example:

```sh
curl --connect-timeout 10 ftp://<PS4_LAN_IP>:2121/
```

### 2.3 Full `com.mojang` mirror

Download the entire confirmed `com.mojang` directory to the PC, including `minecraftWorlds`, `skin_packs`, `resource_packs`, `behavior_packs`, and config files. Use an FTP client that reports failures. Require zero failed/skipped files. Keep no world loaded during the mirror; re-list the remote tree afterward and compare file count/sizes/mtimes where available. If the tree changed during transfer, repeat the mirror and hash step. This is a read operation on the PS4; do not modify saves.

### 2.4 Hash and gate

From the repository root:

```sh
node tier-c/tools/hash-tree.mjs <backup-com.mojang> tier-c/reports/backup-manifest.json
node tier-c/tools/verify-backup.mjs <backup-com.mojang> tier-c/reports/backup-manifest.json
```

Fill `tier-c/reports/backup-gate.json` from the template. Set `result: "PASS"` only when native backup, full mirror, zero-failure transfer log, hash verification, exact target identity, and operator attestation are all complete. **If the gate is not PASS, do not upload.**

## 3. Validate local probe

```sh
cd tier-c
npm test
```

Expected local result: 9/9 Node tests and `STRUCTURAL_PASS: 23/23`. This is not PS4 recognition.

Review `reports/pack-manifest.json`; it contains the exact expected file sizes and SHA-256 hashes.

## 4. Single minimal upload

1. Re-confirm `skin_packs/tier_c_local_probe` is absent.
2. Upload the contents of `pack/tier_c_local_probe/` into that new remote folder only.
3. Preserve this layout:
   ```text
   skin_packs/tier_c_local_probe/manifest.json
   skin_packs/tier_c_local_probe/skins.json
   skin_packs/tier_c_local_probe/c1.png
   skin_packs/tier_c_local_probe/c2.png
   skin_packs/tier_c_local_probe/c3.png
   skin_packs/tier_c_local_probe/c4.png
   skin_packs/tier_c_local_probe/texts/en_US.lang
   skin_packs/tier_c_local_probe/texts/languages.json
   skin_packs/tier_c_local_probe/pack_icon.png
   skin_packs/tier_c_local_probe/README.txt
   ```
4. Do not upload into `resource_packs`, `behavior_packs`, `development_skin_packs`, `minecraftWorlds`, `/user/app`, `/user/patch`, `/user/addcont`, or savedata directories.
5. Read back every file and compare hashes/sizes. Example read-only check:
   ```sh
   curl --connect-timeout 10 'ftp://<PS4_LAN_IP>:2121/app_tmp/<TITLE_ID>/<USER>/games/com.mojang/skin_packs/tier_c_local_probe/manifest.json' -o manifest.readback.json
   sha256sum manifest.readback.json
   ```
   Repeat for all files or use an FTP client with verify/download-back. Any mismatch → stop.

## 5. Restart and observe

1. Fully close Minecraft; do not use rest mode as a restart.
2. Relaunch offline with the same local user.
3. Open Dressing Room / Classic Skins. Record whether `Tier C Local Skin Probe` appears.
4. If visible, select C1 Signal Red and Equip without sign-in. If sign-in appears, stop and record FAIL.
5. Verify the real local player in third person/local split-screen. A mannequin or Dressing Room-only preview is not C4 PASS.
6. Fully close and relaunch Minecraft again. Verify the same skin remains equipped and visible on the real player.
7. Record C1–C5 exactly as defined in `TEST-PLAN.md`.

## 6. Cleanup

Restore the original skin and any normal game setting changed for observation. Remove only the new `tier_c_local_probe` folder if desired. Never delete existing packs, saves, app/patch/addcont files, or executables. Disable FTP.

## 7. Current criterion results

| Criterion | Result |
|---|---|
| C1 pack recognition | **FAIL — NOT RUN** |
| C2 actual in-game selection UI | **FAIL — NOT RUN** |
| C3 selection without sign-in | **FAIL — NOT RUN** |
| C4 real local-player application | **FAIL — NOT RUN** |
| C5 restart persistence | **FAIL — NOT RUN** |

Overall: **FAIL — not demonstrated**. Filesystem access, pack recognition, skin selection, and actual player skin application are separate outcomes; none may be inferred from another.
