# Tier C research — PS4 local skin-pack loading

Date: 2026-11-12. Status: **research and structural artifact only; PS4 hardware NOT RUN.**

## Question

Can Minecraft Bedrock on a GoldHEN-enabled PS4 recognize a locally FTP-installed `skin_pack`, show it in the real in-game skin selector offline, equip it to the real local player without Microsoft/PSN sign-in, and persist it after restarting Minecraft?

## Current answer

**Not demonstrated.** The standard Bedrock skin-pack file format is documented, and GoldHEN can expose PS4 filesystem paths over FTP. Neither fact proves that the PS4 Bedrock client enumerates arbitrary local `skin_packs`, trusts them, exposes them in the offline Dressing Room, or applies them to the local player. Official Minecraft guidance says custom skin import is available on Windows 10/mobile and that there are no plans for console custom-skin import. Therefore the default expectation is **no supported PS4 local custom-skin import**, and the experiment must fail closed unless hardware evidence proves otherwise.

## Layer model

| Layer | Meaning | Evidence required | Current result |
|---|---|---|---|
| L0 Filesystem access | FTP can read the active title/user game-data mount | listings, path evidence, read-only download | FAIL — NOT RUN |
| L1 Backup gate | Complete verified backup before any Minecraft data write | native savedata backup + full `com.mojang` mirror + hash pass + operator attestation | FAIL — NOT RUN |
| L2 Pack recognition | Client scans/registers the local `skin_pack` | pack appears in client content/UI after restart; logs if available | FAIL — NOT RUN |
| L3 Actual selection UI | Real Dressing Room/Classic Skins interface lists the pack offline | screenshots/video of actual client UI, no sign-in | FAIL — NOT RUN |
| L4 Offline selection | Operator can select/equip without Microsoft/PSN sign-in | uninterrupted offline workflow, no account prompt | FAIL — NOT RUN |
| L5 Real player application | The real local player renders the selected texture | third-person/local split-screen evidence; not a mannequin or browser preview | FAIL — NOT RUN |
| L6 Restart persistence | Selection survives full Minecraft close/relaunch offline | before/after restart evidence and same local user/title | FAIL — NOT RUN |

A mannequin, browser canvas, resource-pack entity texture, FTP file existence, or a pack folder on disk is **not** success.

## Bedrock skin-pack architecture

Documented Bedrock skin packs are content packs, not behavior/resource packs:

- Installed location on platforms with local import: `com.mojang/skin_packs/<pack_folder>/`.
- Development location documented in creator docs: `com.mojang/development_skin_packs/`; community Bedrock guidance reports it is unreliable and recommends `skin_packs` plus restart. Tier C does not use the development folder as its primary hypothesis.
- `manifest.json` declares a `skin_pack` module with distinct header/module UUIDs.
- `skins.json` lists skin entries, `geometry.humanoid.custom` or `geometry.humanoid.customSlim`, root-level PNG texture filenames, and `type: "free"`.
- `texts/en_US.lang` maps `skinpack.<localization_name>` and `skin.<localization_name>.<skin_localization_name>` keys. `texts/languages.json` is included for explicit language listing.
- Custom geometry is not part of this probe; only the standard classic humanoid geometry is used.
- `.mcpack` is a ZIP import container for platforms with a file-import handler. FTP copying a `.mcpack` into a directory is not the same as invoking that handler. Tier C’s PS4 candidate is the **unpacked folder**.
- The Dressing Room’s Classic Skins area is the documented selection surface for imported skin packs on supported platforms. The “Only Allow Trusted Skins” setting can hide custom packs; changing it is a normal game setting, not an authentication bypass, and must be recorded/restored.

The internal client likely separates content discovery, skin registry/ownership/trust, UI presentation, and player skin configuration. The exact PS4 implementation is not publicly documented here and was not reverse-engineered. No executable inspection or patching was performed.

## PS4 filesystem architecture relevant to the probe

GoldHEN lists FTP on port **2121**. Community PS4 filesystem documentation distinguishes:

- `/user/app/<TitleID>/` — installed app/game files. **Read-only for this experiment; never replace.**
- `/user/patch/<TitleID>/` — game update files. **Never replace.**
- `/user/addcont/<TitleID>/` — DLC/add-on content. **Never replace.**
- `/user/home/<userid>/savedata/<TitleID>/` — encrypted savedata. **Never edit/replace; back up through native PS4 savedata management.**
- `/app_tmp` or `/app_temp` — community-reported running-title temporary/sandbox mount. The existing AddonStation locator accepts only explicit paths shaped like:
  ```text
  /app_tmp/<CUSA…>/<user…>/games/com.mojang
  /app_temp/<CUSA…>/<user…>/games/com.mojang
  ```
  and requires real `resource_packs` and `behavior_packs` children for its RP/BP importer.

For Tier C the candidate skin directory is:

```text
<confirmed com.mojang>/skin_packs/tier_c_local_probe/
```

The `skin_packs` directory must already exist and be inventoried read-only. If it is absent, the procedure stops; it does not create speculative directories or scan unrelated user/title paths. The presence of `resource_packs`/`behavior_packs` alone does not prove `skin_packs` is scanned by the PS4 client.

## Exact version and Title ID requirements

There is **no universal exact Minecraft version or CUSA ID proven to support this experiment**. The requirement is to record and match the exact installed environment before any write:

| Field | Requirement |
|---|---|
| PS4 model | Record actual model; PS4 only, not PS5 |
| PS4 system software | Record exact firmware version; must already be supported by the installed GoldHEN payload |
| GoldHEN | Record exact payload/release version; FTP server enabled; no cheats/patches/plugins used |
| Minecraft edition | Must be Bedrock-era Minecraft on PS4, not legacy 4J “PlayStation 4 Edition” behavior |
| Minecraft client version | Record exact version shown by the client/title information |
| Title ID | Record exact installed/running Title ID from PS4 UI and observed FTP mount; do not guess from region alone |
| Region/release | Record disc/digital/region evidence; Title IDs vary |
| Local user | Record exact observed user directory and local profile; no PSN sign-in |
| Network | Offline from Microsoft/PSN/Realms; trusted LAN only for FTP |

Known public Title ID examples, **not an allowlist and not compatibility evidence**:

- `CUSA00744` — Americas release family, publisher product `UP4433`.
- `CUSA00265` — Europe/Asia release family, publisher product `EP4433`.
- `CUSA00283` — Japan release, publisher product `JP0127`; disc product code `PCJS-44003`.
- `CUSA17908` — Bedrock Edition physical Europe **disc label**; SerialStation maps the internal PS4 title ID to `CUSA00265`. Do not assume the disc label is the FTP directory name.

SerialStation lists many more Minecraft Title IDs and notes that the Bedrock Edition replaced the original PS4 version in 2019. The operator must use the exact observed running/internal Title ID, not these examples or a disc label as a substitute.

GoldHEN firmware support must be checked against the exact installed payload. The official GoldHEN repository confirms FTP port 2121 and publishes releases such as `v2.4b18`; community-maintained PS4 DevWiki lists `v2.4b18` support for firmware `5.05, 6.71, 6.72, 9.00, 9.60, 10.00, 10.01, 10.50, 10.70, 10.71, 11.00`. That community list is not a hardware test and not a promise that Minecraft skin loading works on those firmwares. Do not update firmware or install exploits as part of Tier C.

## Offline UI and sign-in

The test must remain offline/local. Marketplace ownership, Microsoft account linking, PSN sign-in, and Realms are out of scope. If the Dressing Room requires sign-in to show or equip the candidate, record L3/L4 FAIL and stop. Do not use cheat tables, memory patches, “bypass sign-in” codes, fake account activation, or executable modifications. A local PS4 user profile is not the same as PSN sign-in; record which profile is used.

## Minimal artifact

`pack/tier_c_local_probe/` is a standard-format Bedrock `skin_pack` candidate with four original synthetic 64×64 classic skins: C1 Signal Red, C2 Beacon Green, C3 Relay Blue, C4 Marker Yellow. It contains no scripts, RP/BP, mannequin, save data, or executable content. `dist/tier-c-local-probe.mcpack` is a reference ZIP container only; the FTP test uses the unpacked folder. Structural validation passed, but **PS4 recognition is not supported by this result**.

## Sources

Official / primary:

- Minecraft Character Creator FAQ: https://www.minecraft.net/en-us/article/character-creator-faq — Windows 10/mobile custom import; no console custom-import plans; no Bedrock cross-device custom-skin sync.
- Microsoft Learn skin-pack tutorial: https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable
- Microsoft Learn skin-pack JSON reference: https://learn.microsoft.com/en-us/minecraft/creator/reference/content/skinpacksreference/skinpackjsonreference?view=minecraft-bedrock-stable
- Microsoft Learn getting started: https://learn.microsoft.com/en-us/minecraft/creator/documents/gettingstarted?view=minecraft-bedrock-stable — `skin_packs` and development pack folders.
- GoldHEN official repository: https://github.com/GoldHEN/GoldHEN — FTP port 2121; release metadata copied to `reports/goldhen-releases.json`.
- Repository read-only locator: `ps4/src/minecraft_storage.cpp`, `ps4/src/minecraft_storage.hpp`, `ps4/tests/test_minecraft_storage.cpp`.

Community / secondary, explicitly unverified for this client:

- ConsoleMods Files and Directories: https://consolemods.org/wiki/PS4:Files_and_Directories
- ConsoleMods FTP guide: https://consolemods.org/wiki/PS4:Transferring_Files_with_FTP
- PS4 DevWiki Homebrew Enabler: https://www.psdevwiki.com/ps4/Homebrew_Enabler
- SerialStation Minecraft: https://serialstation.com/games/2ee876ab-dd36-4dbf-8e67-adfd1e2e6083
- Bedrock Wiki skin packs: https://wiki.bedrock.dev/visuals/skin-packs — `development_skin_packs` reliability warning.
- Reddit PS4 add-on guide: https://www.reddit.com/r/ps4homebrew/comments/1nkada7/how_to_add_addons_to_minecraft_ps4_2025_guide/ — running-game `app_temp` mount lead; direct fetch previously returned 403.

No source above constitutes a PS4 local skin-pack loading test. The decisive evidence must be the hardware procedure in `TEST-PLAN.md`.
