# Install and test on Windows Bedrock — real player skin

**Status: the Minecraft client has NOT been launched by the agent. Every in-client step below is NOT RUN.**
These are exact instructions for you to perform on your Windows PC. Do not record a PASS until you have actually seen the result.

No executable, authentication, or DRM modification is involved. This is a normal skin-pack import through Minecraft's own importer.

---

## 0. Read this first: "Azure Mechanic" is not in the Tier C pack

You asked to equip **Azure Mechanic**, but the audit found a naming mismatch:

| Pack | Skins it actually contains |
|---|---|
| **Tier C** (`tier-c/pack/tier_c_local_probe`) | C1 Signal Red, C2 Beacon Green, C3 Relay Blue, C4 Marker Yellow |
| **Tier B control** (`tier-b/packs/tier_b_skins`) | **Azure Mechanic**, Ember Explorer, Moss Gardener, Violet Navigator |

"Azure Mechanic" comes from Tier B's *manual skin pack*, not Tier C. Rather than silently renaming a skin or guessing, this directory ships **two clean packs** so both readings of your request are covered:

| File | Contains | Equip target |
|---|---|---|
| `windows-skin-test.mcpack` | The four **Tier C** skins | **C1 Signal Red** |
| `windows-skin-test-azure.mcpack` | The four **Tier B** skins, incl. **Azure Mechanic** | **Azure Mechanic** |

Both are genuine `skin_pack` modules. Neither contains the mannequin. The Tier B *mannequin* was a separate resource+behavior pack pair and is **not** included here.

**If you only want to follow your steps literally, import `windows-skin-test-azure.mcpack` and equip Azure Mechanic.**
Textures in both packs are byte-identical copies of the frozen originals; nothing in Tier A, B, or C was modified.

---

## 1. Record your environment first

Fill in `reports/windows-test-record.json` (template provided) before importing:

- Exact Minecraft version shown at the bottom of the title screen (e.g. `1.21.xxx`).
- Whether it is **Minecraft for Windows** or **Minecraft Preview**.
- Whether you are signed in to an Xbox/Microsoft account, or playing signed out.
- Windows version.

The version matters because the data folder moved (next section).

---

## 2. Know which data folder your build uses

Minecraft Bedrock on Windows switched from UWP to GDK at **1.21.120**, which changed where `com.mojang` lives.

| Build | `com.mojang` location |
|---|---|
| **1.21.120 and newer (GDK)** — packs, add-ons, skin packs (shared by all players, **including signed-out players**) | `%appdata%\Minecraft Bedrock\Users\Shared\games\com.mojang` |
| **1.21.120 and newer (GDK)** — worlds and settings (per signed-in user) | `%appdata%\Minecraft Bedrock\Users\<user ID numbers>\games\com.mojang` |
| **Older than 1.21.120 (UWP)** | `%localappdata%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\com.mojang` |
| **Preview (GDK)** | `%appdata%\Minecraft Bedrock Preview\Users\...` |

Press `Win + R`, paste the path, press Enter. Use whichever one actually exists on your machine.

> Because signed-out players use the **Shared** directory, that is the folder to inspect for the offline test in section 7.

---

## 3. Back up before importing

An import only adds a new folder, but back up first anyway:

1. Close Minecraft completely.
2. Open your `com.mojang` folder from section 2.
3. Copy the whole `com.mojang` folder to somewhere safe (Desktop or external drive).
4. Confirm the copy finished with no errors.

Do not delete or edit `minecraftWorlds`. The test never needs to touch a save file.

---

## 4. Import the pack

### Method A — normal import (use this first)

1. Close Minecraft if it is running.
2. Copy `windows-skin-test-azure.mcpack` (and/or `windows-skin-test.mcpack`) to your Windows PC.
3. **Double-click the `.mcpack` file.**
4. Minecraft launches and shows **"Import started…"** then **"Successfully imported <pack name>"**.
5. Write down the exact message you see. If you see an error, copy its exact text into the checklist.

If Windows asks which app to use, choose **Minecraft for Windows**. If the file is named `.mcpack.zip`, remove the trailing `.zip` so it ends in exactly `.mcpack`.

### Method B — manual folder copy (only if Method A fails)

1. Close Minecraft.
2. Rename a copy of the `.mcpack` to `.zip` and extract it.
3. You should get a folder whose **contents** are `manifest.json`, `skins.json`, the PNGs, and `texts\`.
   `manifest.json` must sit directly inside the folder you copy — not one level deeper.
4. Copy that folder into:
   ```
   <your com.mojang>\skin_packs\
   ```
   You can use the ready-made unpacked folders in `pack/tier_b_azure_windows/` or `pack/tier_c_windows/` instead of extracting.
5. Result should look like:
   ```
   com.mojang\skin_packs\tier_b_azure_windows\manifest.json
   com.mojang\skin_packs\tier_b_azure_windows\azure.png
   ```
6. Launch Minecraft.

Record which method you used. **A file sitting in the folder is not proof the game loaded it** — continue to the Dressing Room.

---

## 5. Turn off "Only Allow Trusted Skins"

This is the single most common reason a valid custom pack does not appear.

1. In Minecraft, go to **Settings → General**.
2. Find **Only Allow Trusted Skins**.
3. Toggle it **OFF**.
4. Record its original state so you can restore it afterwards.

This is a normal in-game setting, not a bypass or a patch.

---

## 6. Find and equip the skin

1. From the title screen, open the **Dressing Room**.
2. If asked to choose between Character Creator and Classic Skin, choose **Classic Skin**.
3. Open the **Classic Skins** section. Depending on your version the pack list may be under **Owned**, or behind the green **clothes-hanger / Featured Skins** button.
4. Look for the pack tile:
   - **Azure Mechanic Skin Test** (contains Azure Mechanic), or
   - **Tier C Windows Skin Test** (contains C1–C4).
5. Expand it and confirm all **four** skins are listed.
6. Select **Azure Mechanic** (or **C1 Signal Red** for the Tier C pack).
7. Click **Equip**.
8. Confirm the character in the Dressing Room now shows that skin.

> The Dressing Room model alone is **not** the success criterion. Continue to section 6b.

### 6b. Verify the real local player in a world — this is the actual test

1. Enter any local single-player world (an existing one is fine, or create a new test world).
2. Switch to third-person view: press **F5** to cycle camera, or set **Settings → Video → Camera Perspective** to third person.
3. Look at your actual player character.
4. Confirm it is wearing the equipped skin:
   - **Azure Mechanic** — blue/teal clothing.
   - **C1 Signal Red** — red clothing with a bright chest square and **one** white scan line.
5. Take a screenshot (`Win + PrtScn`, or `F2` if your build supports it).
6. Optionally open inventory to see the player model rendered there too.

**Only this step counts as the skin actually being applied.** A Dressing Room preview, a menu thumbnail, or a mannequin does not count.

---

## 7. Offline / no-sign-in test

This is a separate condition and must be recorded separately.

1. Note whether you are currently signed in. If signed in, sign out of your Xbox/Microsoft account **inside Minecraft** (Settings → Profile → Sign out).
2. Disconnect the PC from the internet (disable Wi-Fi or unplug Ethernet).
3. Launch Minecraft. If the Microsoft Store requires a cached licence, the game may still start from a previously verified install; if it refuses to launch, **record that as the blocker** — do not attempt any workaround.
4. Repeat sections 4–6b while signed out and offline:
   - Does the pack still appear in the Dressing Room?
   - Can you equip it with no sign-in prompt?
   - Does the real player in a world still show it?
5. If any sign-in prompt appears, stop and record it. Do not create accounts or bypass anything.

Signed-out players use the `Users\Shared\games\com.mojang` folder on GDK builds, so a pack imported there should be visible without an account. **This is the expectation, not a verified result** — that is exactly what you are testing.

Note: cloud/Marketplace skins and account-bound skins normally need a connection. A **local skin pack** is a file on disk, which is why it may behave differently. Record what actually happens.

---

## 8. Restart persistence test

1. With the skin equipped and confirmed on the real player, **fully close Minecraft** (not minimise, not sleep).
2. Relaunch it, keeping the same account state and the same offline/online condition.
3. Do **not** re-select the skin.
4. Enter the same world and check third-person view again.
5. PASS only if the same skin is still equipped and visible on the real player.

---

## 9. Clean up

- To remove: delete only the pack folder you added from `com.mojang\skin_packs\`, or use the in-game delete option if present.
- Restore **Only Allow Trusted Skins** to its original state if you want.
- Re-equip your original skin.
- Your backup from section 3 remains untouched.

---

## 10. What to send back

Fill in `TEST-CHECKLIST.md` and `reports/windows-test-record.json` with:

- Exact Minecraft version and signed-in/out state.
- Import message text.
- Screenshots of: the Dressing Room pack list, the four skins, and **the real player in a world in third person**.
- A screenshot after restart.
- PASS / FAIL for each of W1–W5, plus the offline condition.

I will then update `VALIDATION-RESULTS.md` with your observed results. Until then every client-side row stays **NOT RUN**.
