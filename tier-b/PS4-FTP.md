# PS4 GoldHEN FTP deployment investigation — NOT RUN

**No PS4 hardware test was performed. Do not describe Tier B as PS4-compatible.** This document describes a narrow, content-file-only procedure to investigate on hardware you own and are authorized to modify. It does not provide an exploit, bypass authentication/DRM/account restrictions, or modify Minecraft executables.

## What is established versus unverified

- The GoldHEN project lists an FTP server on **port 2121**. ConsoleMods lists GoldHEN FTP and says it can be enabled in GoldHEN settings; availability depends on the installed GoldHEN/firmware environment.
- The existing AddonStation PS4 storage code in this repository accepts an explicit path shaped like `/app_tmp/<CUSA…>/<user…>/games/com.mojang` or `/app_temp/...`, requires both `resource_packs` and `behavior_packs`, and rejects symlinks/broad scans. That is repository safety logic, not proof that a particular console exposes the path.
- Community instructions report that Minecraft must be running for its title-specific temporary directory to appear, and that packs go under the active user's `games/com.mojang/resource_packs` and `behavior_packs`. This is an **unverified community lead**. The Reddit guide could not be fetched directly here (HTTP 403); only search-index material and the repository path pattern were available.
- CUSA/title IDs vary by region/release. **Discover the actual directory; never hardcode a guessed CUSA or user ID.**
- Whether the PS4 client enumerates these packs, loads stable Script API modules, renders the mannequin, or permits any skin-pack control is **unknown until tested on that exact hardware/client**.

## Preconditions

1. Use a disposable local test world and back up saves first.
2. The console already has a working, authorized GoldHEN environment. This project does not install or supply an exploit.
3. Minecraft is legitimately installed and can launch locally. No PSN sign-in step is part of this procedure; if the platform requires one, stop and record the blocker.
4. PC and PS4 are on the same trusted LAN. FTP is plaintext; do not expose port 2121 to the Internet.
5. Transfer only the two Tier B pack folders. **Do not transfer the optional manual skin pack as a presumed PS4 skin solution.**

## Exact hardware procedure

### A. Enable and connect

1. On the PS4, enable GoldHEN's FTP server in its server settings.
2. Note the PS4 LAN IP address from network settings.
3. In FileZilla/WinSCP on the PC, connect to:
   ```text
   Host: <PS4_LAN_IP>
   Port: 2121
   Protocol: FTP
   Encryption: plain FTP (only if this is the console's local GoldHEN service)
   Logon: anonymous / no credentials if the server accepts it
   ```
   If authentication is requested unexpectedly, stop and verify the service; do not guess or bypass credentials.
4. Launch Minecraft and enter the disposable local world so the title-specific mount is present. Keep it running while discovering/transferring.

A read-only command-line connectivity check from a PC with curl is:

```sh
curl --connect-timeout 10 ftp://<PS4_LAN_IP>:2121/
```

This lists the FTP root only; it does not install anything.

### B. Discover the active storage path

1. List `/app_tmp` and `/app_temp`. Use whichever exists; do not create a replacement root.
2. Identify the Minecraft title directory `CUSA` followed by digits. If multiple exist, match the running installed title rather than choosing the first or copying to all.
3. Inside it, identify the active user directory (user identifier followed by digits in the repository's expected pattern).
4. Continue to `games/com.mojang`.
5. Require **both** existing child directories:
   ```text
   resource_packs
   behavior_packs
   ```
6. Reject symlinks, dot segments, unrelated directories, and any path that does not match the active title/user. If the expected path is absent, stop and record the observed listing. Do not broad-scan or write elsewhere.

Record the confirmed path as:

```text
/app_tmp_or_app_temp/<OBSERVED_CUSA>/<OBSERVED_USER>/games/com.mojang
```

### C. Transfer content folders only

From the unpacked `tier-b/packs/` directory, upload:

```text
tier_b_resource  →  <confirmed com.mojang>/resource_packs/tier_b_resource
tier_b_behavior  →  <confirmed com.mojang>/behavior_packs/tier_b_behavior
```

The remote result must contain:

```text
resource_packs/tier_b_resource/manifest.json
behavior_packs/tier_b_behavior/manifest.json
behavior_packs/tier_b_behavior/scripts/main.js
```

Do not upload a nested `packs` folder, do not overwrite an existing unrelated pack, and do not touch `eboot.bin`, executable modules, package files, save data, or account files. If a same-name folder already exists, inspect it first and use a clean test copy only after confirming ownership; do not blindly merge.

Example read-only verification after confirming the exact path:

```sh
curl --connect-timeout 10 'ftp://<PS4_LAN_IP>:2121/app_tmp/<OBSERVED_CUSA>/<OBSERVED_USER>/games/com.mojang/resource_packs/tier_b_resource/manifest.json'
curl --connect-timeout 10 'ftp://<PS4_LAN_IP>:2121/app_tmp/<OBSERVED_CUSA>/<OBSERVED_USER>/games/com.mojang/behavior_packs/tier_b_behavior/manifest.json'
```

Use `/app_temp` instead if that was the observed root. These commands read back files; they are not a compatibility test.

### D. Observe without assuming success

1. Return to Minecraft and open the disposable world's pack settings.
2. Record whether **Tier B Preview Resources** and **Tier B Preview Behavior** are enumerated at all.
3. If enumerated, activate both in the disposable world, enable cheats for the command, and try `/scriptevent tierb:preview`.
4. Record separately: pack discovery, activation, script execution, form display, mannequin rendering, and actual player skin unchanged.
5. If anything fails, capture the exact client version, GoldHEN version, firmware, observed path, screenshots, and available content/error logs. Do not patch the executable or infer that a Windows result applies.
6. Remove only the two Tier B folders after testing, deactivate them first, and disable the FTP server when finished.

## Required hardware result label

Until every relevant step above is executed and recorded on real PS4 hardware, the only valid label is:

```text
PS4 GoldHEN FTP deployment: NOT RUN
PS4 pack discovery/activation: NOT RUN
PS4 in-game preview rendering: NOT RUN
PS4 compatibility: UNPROVEN
```

A successful FTP copy alone proves only file transfer. It does not prove Minecraft imports the packs, scripts run, or a player skin can be changed.

## Sources and confidence

- GoldHEN official repository: https://github.com/GoldHEN/GoldHEN — feature list includes FTP on 2121.
- ConsoleMods FTP guide: https://consolemods.org/wiki/PS4:Transferring_Files_with_FTP — GoldHEN FTP enablement/port and client examples; firmware table is community-maintained, not a guarantee for every setup.
- Repository read-only reference: `ps4/src/minecraft_storage.cpp` and `ps4/tests/test_minecraft_storage.cpp` — explicit `/app_tmp|/app_temp/CUSA…/user…/games/com.mojang` validation and required pack children.
- Community lead: https://www.reddit.com/r/ps4homebrew/comments/1nkada7/how_to_add_addons_to_minecraft_ps4_2025_guide/ — running-game mount and pack-folder placement; direct fetch returned 403, so treat details as unverified.
- Title-ID variation lead: https://serialstation.com/games/2ee876ab-dd36-4dbf-8e67-adfd1e2e6083 — multiple Minecraft PS4 CUSA releases; discover rather than hardcode.
- Official Minecraft Character Creator FAQ: https://www.minecraft.net/en-us/article/character-creator-faq — no plans for console custom-skin import. This limits skin-equip expectations independently of FTP file access.
