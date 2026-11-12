# Independent Bedrock skin library: technical feasibility report

**Decision date:** 12 November 2026  
**Target:** Minecraft: Bedrock Edition on the user's modded PS4  
**Inspiration:** [BrandonItaly/Bedrock-Skins](https://github.com/BrandonItaly/Bedrock-Skins), inspected at commit `1cc5523d5f9b2d15b2a78f3fc2a38d74d7c265cb`  
**Status:** feasibility analysis and a deliberately small packaging/browser proof of concept; **not a PS4 skin installer and not hardware-certified**.

> ## Bottom line
>
> **An offline library, visual browser, skin previews, and a standards-based skin-pack exporter are feasible as an independent companion application.**
>
> **An in-game catalog and a separately rendered preview character are plausible using documented Bedrock add-on interfaces.** Delivery to a normal PS4 can use a world prepared on another device and uploaded through Realms. The actual add-on still requires a PS4 test; documentation is not a console compatibility guarantee.
>
> **Equipping an arbitrary locally supplied PNG as the real PS4 player's skin is not a documented, supported feature.** A resource pack, behavior pack, script, world upload, or account login on another device does not turn that PNG into an imported console player skin. Approved Marketplace content and the built-in character/default-skin choices are different distribution paths, not a private local-import mechanism.
>
> Therefore the project must never label a preview selection, favorite, or preview entity as **“Equip skin”** on PS4.

The console's modification status does not create new documented Minecraft APIs. This project does not need, propose, or use executable patches, authentication/DRM bypasses, DNS tricks, native hooks, or writes to protected game storage. The existing AddonStation Android, Supabase, and PS4-homebrew code remains separate and unchanged.

---

## 1. Precise meanings used in this report

| Term | Meaning here | What it does **not** establish |
|---|---|---|
| Library | An external catalog of files the user supplies and has permission to use | That Minecraft has imported those files |
| Preview | A texture atlas, a rendered humanoid, or a separate in-world display entity | That the local player's actual skin changed |
| Skin pack | A pack with a `skin_pack` manifest module, `skins.json`, and skin PNGs | That every Bedrock platform accepts direct import |
| Resource pack / RP | World/client assets such as textures, entity definitions, geometry, and UI assets | A player-skin assignment API |
| Behavior pack / BP | World-side data and optionally JavaScript using the Script API | Access to the console filesystem or native renderer |
| Equipping | Changing the actual local player's appearance through Minecraft's supported skin system | Saving a selection in our own catalog |
| Supported path | A documented interface or distribution workflow, with platform-specific limits stated | A claim that an untested combination has passed on this PS4 |

**Scope assumption:** “PS4 Minecraft” means the current Bedrock-based game, not the separate, older **Minecraft: PlayStation 4 Edition**. Legacy Console `.pck` support in the Java mod is not evidence that Bedrock on PS4 can import `.pck` files. The user's exact Bedrock version, console software version, and modification setup have not been supplied or tested.

## 2. What was inspected, and how far the inspection goes

The complete upstream repository tree was downloaded as a pinned source archive, inventoried, and inspected. This was a static source audit, not a claim to have executed every branch or manually read every line of all third-party integrations.

| Measurement | Result |
|---|---:|
| Snapshot files | 1,060 |
| Unpacked snapshot size | 6,708,415 bytes |
| Production Java files | 144 |
| Production Java lines | 16,824 |
| Test Java files | 5 |
| Build-helper Java files / lines | 2 / 252 |
| JSON files parsed | 137; no JSON syntax errors |
| PNG files / valid PNG headers | 388 / 388 |
| TGA files | 20 |
| Localization `.lang` files | 337 |
| Persona metadata JSON files | 62 |
| Bundled skin-pack `skins.json` files | 14 |
| Entries across those skin-pack files | 287, including vanilla/import placeholders |

The archive SHA-256 is `07c7699909ed7593019200c823586cce483b13e6f413d4137ec3bb231bc133dc`.

Method:

1. Inventory all files, build descriptors, resources, tests, and language files.
2. Parse all JSON files and inspect PNG signatures/dimensions without running repository code.
3. Scan Java declarations, imports, and cross-references across the production tree.
4. Read the loader, skin manager, client/common initialization, networking, renderer/model conversion, persistence, import UI, preview UI, and Legacy Console conversion paths in detail.
5. Compare those operations with Microsoft's pack documentation, official sample archives, and published Script API declarations.
6. Inspect the existing repository only to establish project boundaries and reusable validation ideas—not to couple the new project to its backend or homebrew runtime.

**Execution limitation:** Java/Gradle were not run; Java is not installed in this workspace. There is no Minecraft client, Realms subscription, or PS4 hardware available here. Static source findings, package checks, browser checks, and actual console acceptance are reported separately.

The upstream repository declares an MIT license. That does not establish redistribution rights for every bundled or imported third-party texture. This project does **not** copy the mod's Java implementation or redistribute its skin/Persona assets. Users supply their own permitted content.

## 3. How BrandonItaly/Bedrock-Skins actually works

### 3.1 Its direction is the opposite of the requested project

Despite its name, this is a **Java Edition mod that consumes Bedrock-style skin content**. It is not a Bedrock add-on and does not install skins into a PS4.

At the inspected revision, `gradle.properties` declares mod version `2.2.1`. Stonecutter targets Java Edition `1.21.11`, `26.1`, `26.2`, `26.3`, and a `26.4` snapshot, with Fabric and NeoForge variants where configured. The build selects Java release 21 for the older target and 25 for newer unobfuscated targets. Loader descriptors, mixins, access widening, and build logic are specific to Java Edition.

### 3.2 Loading and conversion

Key files:

- `pack/loader/SkinPackLoader.java`
- `pack/model/{SkinPackManifest,SkinEntry,LoadedSkin,SkinId,AssetSource}.java`
- `pack/pck/{PckFileParser,PckImporter,PckModelConverter,PckLocalizationSupport}.java`

The loader discovers local packs under `.minecraft/skin_packs` and also exposes packs bundled in the mod's resources. It reads `skins.json`, optional geometry, localized names, and PNG assets. It resolves the selected model and registers textures with the Java client's texture manager.

Legacy Console `.pck` files are parsed as binary containers and converted into skin metadata, PNGs, localization, and Bedrock-style geometry. The converter handles model flags, pivots/cubes, armor-related data, cape associations, and legacy texture layout conversion. This is substantial format-conversion code running inside a Java client—not a capability granted by the `.pck` extension.

### 3.3 Selection, preview, persistence, and actual rendering

Key files:

- `gui/screen/SkinSelectionScreen.java`
- `gui/widget/SkinPreviewPanel.java`
- `client/appearance/skin/SkinManager.java`
- `client/persistence/{StateManager,LocalSkinConfig}.java`
- `mixin/render/PlayerEntityRendererMixin.java`
- `client/render/model/{BedrockModelBuilder,BedrockGeometryBaker,BedrockPlayerModel}.java`

The interface provides pack navigation, a skin grid, favorites, search, and a live dummy-player preview. Preview and selected appearance are separate concepts in the manager. A selection is persisted locally and applied through Java-client appearance overrides.

The decisive operation is not merely reading a PNG. The mod injects into Java's player rendering and skin lookup paths. For example, `PlayerEntityRendererMixin` overrides texture selection and first-person arm rendering; related mixins affect player models, armor, capes, tab-list skins, and other presentation surfaces. Custom geometry is translated into Java model parts, including per-face UVs and polygon meshes.

Those injection points are unavailable to a Bedrock RP/BP. Copying the GUI alone would not reproduce skin application.

### 3.4 Multiplayer and account operations

`BedrockSkins.java`, `BedrockSkinsClient.java`, `network/BedrockSkinsNetworking.java`, and `server/ServerSkinManager.java` implement eight custom Java-network payloads. Clients announce skin-content hashes; peers request missing data. The server keeps an active, hash-deduplicated registry and broadcasts appearance updates. The implementation includes texture/geometry size checks and disconnect cleanup.

This makes appearance sharing work between compatible Java mod participants. It does **not** register the skin with Mojang's account skin service or make it visible to an unmodified Bedrock console as an equipped skin.

There are also optional authenticated Java-account skin/cape operations, Persona loading, emotes, and third-party integrations. An optional upload to Mojang's Java profile service is distinct from the local renderer override. **Neither operation establishes a supported Bedrock/PS4 custom-skin synchronization path.**

### 3.5 What can be recreated independently

| Upstream idea | Independent Bedrock-era companion | Real PS4 player skin? |
|---|---|---|
| Discover several local packs | Yes: local file/directory/archive import outside Minecraft | No |
| Names, packs, search, favorites | Yes: external UI; limited forms inside a world | No |
| Skin atlas and humanoid preview | Yes: browser/canvas preview; limited RP icons or a preview entity in-game | No |
| Remember a selection | Yes: browser storage or a world/player dynamic property | Only remembers our catalog selection |
| Export classic/slim PNG packs | Yes: documented skin-pack structure | Import/equip remains platform-dependent |
| Arbitrary player geometry/armor fitting | Not through skin-pack JSON or a documented player-skin API | No |
| Java networking/mixins/native file dialogs | No equivalent in the proposed Bedrock interfaces | No |
| Persona/emote/Java-account parity | Out of scope; not needed for the requested library | No |

## 4. Official Bedrock interfaces and their limits

### 4.1 Skin packs: the correct format for genuine player skins on importing platforms

Microsoft documents a skin pack with a manifest, root-level `skins.json`, root-level PNG textures, and localization files. The skin-pack reference restricts the model field to the classic/wide and slim humanoid identifiers. Microsoft explicitly states that custom models cannot be added through the skin-pack JSON capability. [2](https://learn.microsoft.com/en-us/minecraft/creator/documents/packagingaskinpack?view=minecraft-bedrock-stable) [3](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/skinpacksreference/skinpackjsonreference?view=minecraft-bedrock-stable)

A minimal conceptual layout is:

```text
manifest.json          # skin_pack module; distinct header/module UUIDs
skins.json             # serialize_name, localization_name, skins[]
classic.png            # owned 64×64 skin texture
slim.png               # owned 64×64 skin texture
texts/
  languages.json
  en_US.lang
```

The packaging guide shows manifest `format_version: 1`; Microsoft's introductory tutorial and official sample use `format_version: 2`. The small prototype follows the newer tutorial/sample convention rather than pretending the documentation is perfectly uniform. [2](https://learn.microsoft.com/en-us/minecraft/creator/documents/packagingaskinpack?view=minecraft-bedrock-stable) [4](https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable)

On a platform with skin-pack import, the documented workflow is to import the `.mcpack`, open the Dressing Room/classic-skin selection, and explicitly equip a skin. Microsoft's tutorial describes automatic import into `skin_packs` under `com.mojang`. **That desktop workflow must not be generalized to PS4.** [4](https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable)

`type: "free"` describes a skin entry; it is not a Marketplace publication, signature, entitlement, or console-import bypass. A `skin_pack` module is not a script host. Adding unrelated script or resource modules to that archive is not a supported way to turn a skin pack into an executable installer.

### 4.2 Resource packs: useful for previews, not player-skin assignment

RPs can supply textures, models, entity render definitions, animations, and UI assets. Microsoft's custom-entity workflow uses a BP entity definition and an RP client-entity definition to create a separately rendered entity. This is a documented way to build a **display mannequin**, not to replace the local player's account skin. [5](https://learn.microsoft.com/en-us/minecraft/creator/documents/resourcepack?view=minecraft-bedrock-stable) [6](https://learn.microsoft.com/en-us/minecraft/creator/documents/introductiontoaddentity?view=minecraft-bedrock-stable)

An RP alone cannot offer the Java mod's local file picker, arbitrary native rendering hooks, or per-player skin switching. Overriding a shared player-render asset would also be a different, global resource-override experiment—not a reliable multi-skin library—and is not part of this prototype.

### 4.3 Behavior packs and stable Script API: a catalog is possible

A BP can contain a JavaScript script module and depend on published Minecraft modules. `ActionFormData` provides a title, body, and buttons; button icons can reference RP textures. It is a native form, not an arbitrary HTML view. [7](https://learn.microsoft.com/en-us/minecraft/creator/documents/scripting/introduction?view=minecraft-bedrock-stable) [8](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-ui/actionformdata?view=minecraft-bedrock-stable)

The published stable declarations inspected for a future, conservative in-game candidate were:

- `@minecraft/server` **1.17.0**: player/world dynamic properties, script events, entity spawning, and related world logic.
- `@minecraft/server-ui` **1.3.0**: `ActionFormData`, icon-bearing buttons, and player forms.

These are type-surface checks, not a claim that every API version runs on every installed game. A future implementation must pin and verify dependencies against the exact target client. Beta APIs are not needed for the proposed small form-based design.

The inspected `Player` declaration has no `setSkin`, `equipSkin`, `setTexture`, or `setGeometry` method. `EntitySkinIdComponent.value` is a **read-only entity variant identifier**, with examples such as villager skins; it is not a setter for a player's skin texture. [9](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server/entityskinidcomponent?view=minecraft-bedrock-stable)

The HTTP module `@minecraft/server-net` is explicitly limited to Bedrock Dedicated Server and does not function in the game client or Realms. It cannot be used to fetch a skin library inside a PS4-hosted world or a Realm. [10](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-net/minecraft-server-net)

Consequences for an in-game catalog:

- Bundle the catalog and approved assets at build time; do not promise runtime downloads or console-folder scanning.
- Use forms for pack/skin navigation and a small number of entries per page.
- Use pre-rendered icons for lightweight previews, or a documented custom display entity for a larger preview.
- Store only selection/favorite metadata. Calling that operation “equip” would be misleading.
- Treat controller navigation, form cancellation, resource download, memory use, and reconnection as explicit PS4 acceptance tests.

### 4.4 World templates and Realms: a delivery route, not a skin loophole

A world can carry its activated RP/BP packs. Microsoft documents the world-template layout, including `world_behavior_packs.json`, `world_resource_packs.json`, and bundled pack folders. [11](https://learn.microsoft.com/en-us/minecraft/creator/documents/createaworldtemplate?view=minecraft-bedrock-stable)

The proposed normal-console workflow is:

```text
User-owned files
  → independent offline library / pack builder
  → prepare and test a world on Windows or mobile Bedrock
  → activate the preview RP + BP in that world
  → upload/replace a Realms world using the official UI
  → join that Realm from PS4
  → let Minecraft deliver the world's required packs
```

Microsoft documents world upload/replacement and cross-device transfer through Realms. It also documents Realms availability on PlayStation; online multiplayer there requires PlayStation Plus. [12](https://help.minecraft.net/hc/en-us/articles/20686744908429) [13](https://help.minecraft.net/hc/en-us/articles/4409165790605) [14](https://www.minecraft.net/en-us/article/servers-and-realms-are-coming-playstation)

This is an **online provisioning step**. Subsequent use of already-delivered content may be possible in a downloaded/local world, but offline caching and re-entry on this PS4 must be tested. A Realm itself is not an offline service. Uploading a world does not install its PNGs into the console's player-skin picker.

A separate Marketplace/Partner submission is a publishing route for approved content, with review—not a guaranteed acceptance route for arbitrary private packs. [15](https://www.minecraft.net/en-us/partner)

## 5. PS4-specific verdicts

Mojang's Character Creator FAQ says importing custom skins is possible on Windows 10 and mobile, that there are no plans for custom-skin synchronization between Bedrock devices/games, and that there are no plans for importing custom skins on consoles. The page contains older product references, so it is evidence of the documented policy—not a substitute for testing the user's current game. No newer official API or workflow found in this audit reverses that limitation. [1](https://www.minecraft.net/en-us/article/character-creator-faq)

Current PlayStation account-linking documentation describes access to eligible Marketplace purchases and cross-platform Realms. It does not describe importing a custom PNG or synchronizing an imported Windows/mobile skin. [16](https://help.minecraft.net/hc/en-us/articles/360037725571-Minecraft-for-PlayStation-4-FAQ)

| Requested capability | PS4 verdict | Conditions / boundary |
|---|---|---|
| Local arbitrary PNG import into the PS4 skin picker | **No documented supported path** | Modding the console does not change the game's documented API |
| Upload a skin on Windows/mobile and expect account sync to PS4 | **Not supported by the cited policy** | Do not design around this assumption |
| Direct USB/browser `.mcpack` skin import on PS4 | **No documented supported path** | A valid archive is necessary but not sufficient |
| Official Marketplace/default/built-in character choices | **Existing supported choices** | Not arbitrary local content; eligibility/platform rules still apply |
| Private custom skin-pack publication without review | **Not established** | Partner/Marketplace is a separate reviewed publishing process |
| Offline external library and visual browser | **Feasible on a companion device** | Not a claim that a local HTML app is a supported native PS4 app |
| In-game pack/skin catalog via RP + BP | **Technically plausible; PS4 untested** | Prepare elsewhere; Realms delivery; stable forms; embedded assets |
| In-game visual preview | **Plausible; PS4 untested** | RP icon or separate display entity, not the player's skin |
| Remember a catalog choice | **Plausible; PS4 untested** | Dynamic property/browser storage; not equipping |
| Change the actual PS4 player's skin from that choice | **No documented API/path for arbitrary owned PNGs** | This is the hard stop |
| Arbitrary custom player geometry through a skin pack | **Unsupported by skin-pack JSON** | A custom preview entity is a different feature |
| Offline use after content provisioning | **External app: yes; in-game: test required** | No runtime HTTP dependency; console world/pack caching must be verified |

### Three capability tiers

1. **Tier A — offline companion:** import owned packs/PNGs, browse, preview, favorite, validate, and export `.mcpack`. This is the smallest useful product and can be built without console privileges.
2. **Tier B — in-game preview only:** a small embedded catalog with native forms and optional display entities, provisioned through a tested world/Realms workflow. This is the only proposed in-game PS4 experiment.
3. **Tier C — genuine equippable custom player skins:** supported skin-pack import on platforms that allow it; on PS4, use Minecraft's existing supported content choices or pursue approved Marketplace publication. **Tier B cannot be upgraded into Tier C by changing a button label or adding another script.**

## 6. Smallest useful prototype

### 6.1 Implemented in this first pass: Tier A, intentionally small

The accompanying `skin-lab` prototype is a dependency-free local web application plus a shared Node-tested pack core:

- Import multiple user-owned skin folders or `.mcpack` archives; also import individual PNGs.
- Search by skin/pack name and filter by pack.
- Display the original atlas and a rotatable classic/slim humanoid preview.
- Remember favorites/last preview locally where browser storage is available.
- Export selected owned skins as a conventional skin pack, with new UUIDs, localization, and a ZIP-based `.mcpack`.
- Reject unsafe archive paths, duplicate members, invalid metadata, missing textures, and unsupported skin models.
- Include two tiny, original synthetic example textures—not upstream or Marketplace artwork.

This is **not** a Java-mod port, a native PS4 application, a cloud service, or a real-skin equip button. It runs from local files or a static server. Exported packs are intended for testing on Windows/mobile first. File permission is the user's responsibility; the application does not grant rights to third-party content.

### 6.2 Deferred until Tier A is accepted: one small Tier B experiment

Only after the external library and package tests are accepted:

1. Build one RP and one BP containing at most four owned example skins.
2. Pin stable module versions compatible with the exact authoring client.
3. Add a pack menu and skin menu using `ActionFormData`; use generated front-view icons.
4. Add one non-player preview entity, with no AI, spawning rules, loot, or persistence beyond the preview session.
5. Save `selectedPreview`, never `equippedSkin`.
6. Show “Preview only — your player skin has not changed” in every selection result.
7. Test on Windows/mobile, then provision through Realms and test on the actual PS4.

Do not begin with Persona, emotes, capes, arbitrary geometry conversion, thousands of skins, a backend, or a native homebrew payload. None is needed to answer the core feasibility question.

## 7. Verification: what passing a structural test means

### 7.1 Official reference archive

The official Microsoft sample at `microsoft/minecraft-samples/skinpack/skins.mcpack`, Git blob `deed1636153ff597bbe264698013c82ade6b4de0`, was downloaded and inspected. It is 1,267 bytes with SHA-256 `789a47607d9e1d58122b81d7800c361fd0ff740dfb9bf3e8d7efdfe4f735f3ea`. [17](https://github.com/microsoft/minecraft-samples/tree/main/skinpack)

The reference check found:

- Four members, valid ZIP CRCs, no duplicate names, and no traversal/absolute/backslash paths.
- Root-level manifest and skin metadata.
- Manifest format 2, one `skin_pack` module, distinct UUIDv4 identities.
- One free classic-model skin with an existing root-level 64×64 PNG.
- A valid pack-name localization key.

Two documentation/sample discrepancies were recorded rather than hidden:

1. The sample's per-skin key uses the texture basename, while the packaging guide derives it from the entry's `localization_name`.
2. The minimal sample omits `texts/languages.json`, although the packaging guide describes it.

The prototype uses matching, safe localization identifiers and includes `languages.json`. These choices still require an actual game-import test. The audit recorded **18 passing checks and 2 warnings** across the reference archive, upstream inventory, and candidate API declarations. Warnings are not failures, but neither they nor passing syntax checks establish Minecraft acceptance.

### 7.2 Test layers must remain separate

| Layer | Can be checked here? | What a pass proves |
|---|---|---|
| Upstream static inventory/source analysis | Yes | What the inspected code is designed to do |
| JSON/PNG/manifest/reference validation | Yes | Structural consistency under our stated checks |
| Shared pack-core unit tests | Yes | Export/import logic and rejection cases in Node |
| Local browser UI | Yes, where a browser runner is available | UI behavior, not console behavior |
| Windows/mobile Minecraft import and equip | **Not run here** | Actual skin-pack acceptance on that client |
| RP/BP form/entity behavior | **Deferred / not run here** | Script and rendering behavior on a real client |
| Realms delivery and PS4 controller/UI behavior | **Not run here** | The proposed Tier B workflow on the user's setup |
| Actual PS4 player-skin change | **Not claimed** | No supported arbitrary-PNG path was identified |

The checks actually run in this workspace are recorded in [`TEST-RESULTS.md`](TEST-RESULTS.md) and `reports/`: 9 Node pack-core tests passed, 13 headless-browser smoke checks passed, and both the generated demo pack and the browser-exported pack passed structural validation with zero warnings. The reference audit recorded 18 passing checks and 2 documentation/sample warnings. None of these results is a Minecraft import, Realms, or PS4 hardware certification.

### 7.3 Manual acceptance checklist for the next stage

**Windows/mobile, using a disposable world and owned test skins:**

- Import the generated `.mcpack`; confirm the pack and both model variants appear.
- Confirm names and textures; explicitly equip one through the Dressing Room.
- Restart the game and verify the actual selection persists.
- Import a newly exported revision with fresh UUIDs; check conflict behavior.
- Confirm malformed test archives fail without extracting unsafe paths.

**PS4, only for the future preview add-on:**

- Record the game edition/version and test the same unmodified add-on files used on the authoring device.
- Provision through the official Realms UI; confirm both packs download and the world opens.
- Navigate every form with the controller; test Back/cancel and repeated opening.
- Confirm icons and/or the display entity render correctly.
- Confirm the real player's skin remains unchanged before and after selection.
- Rejoin, restart, and test a previously downloaded local copy without network access; record what remains available.
- Check content logs where accessible and watch memory/frame time with the intended small catalog before increasing its size.

No credentials, account tokens, console exploits, or protected save files are needed for these tests.

## 8. Recommendation and stop conditions

**Proceed with Tier A.** It satisfies the useful offline-library requirement without overstating console support. Treat `.mcpack` export as a interoperability feature, not a promise of PS4 installation.

**Consider Tier B only as a preview experiment.** It requires another Bedrock device for preparation, an appropriate Realms setup for normal-console delivery, and explicit hardware testing. If the requirement is “fully offline, PS4-only, with no external preparation device or initial connection,” the proposed in-game delivery workflow does not meet it.

**Stop any design that claims to equip arbitrary local PNGs on PS4 through RP/BP/scripting.** That claim is unsupported. Likewise, do not infer compatibility from a Windows tutorial, a YouTube demonstration, a Marketplace listing, or the presence of homebrew on the console.

The next decision is therefore narrow: accept the offline companion and its structural tests, then optionally authorize the four-skin, preview-only Realms experiment. The hard limitation is now explicit rather than hidden behind a large implementation.

## Source register

Official policy and format documentation:

1. [Mojang — Character Creator FAQ](https://www.minecraft.net/en-us/article/character-creator-faq)
2. [Microsoft Learn — Packaging a Skin Pack](https://learn.microsoft.com/en-us/minecraft/creator/documents/packagingaskinpack?view=minecraft-bedrock-stable)
3. [Microsoft Learn — skins.json reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/skinpacksreference/skinpackjsonreference?view=minecraft-bedrock-stable)
4. [Microsoft Learn — Introduction to Skin Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/skinpack?view=minecraft-bedrock-stable)
5. [Microsoft Learn — Resource Packs](https://learn.microsoft.com/en-us/minecraft/creator/documents/resourcepack?view=minecraft-bedrock-stable)
6. [Microsoft Learn — Creating New Entity Types](https://learn.microsoft.com/en-us/minecraft/creator/documents/introductiontoaddentity?view=minecraft-bedrock-stable)
7. [Microsoft Learn — Introduction to Scripting](https://learn.microsoft.com/en-us/minecraft/creator/documents/scripting/introduction?view=minecraft-bedrock-stable)
8. [Microsoft Learn — ActionFormData](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-ui/actionformdata?view=minecraft-bedrock-stable)
9. [Microsoft Learn — EntitySkinIdComponent](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server/entityskinidcomponent?view=minecraft-bedrock-stable)
10. [Microsoft Learn — server-net restrictions](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/minecraft/server-net/minecraft-server-net)
11. [Microsoft Learn — World Templates](https://learn.microsoft.com/en-us/minecraft/creator/documents/createaworldtemplate?view=minecraft-bedrock-stable)
12. [Minecraft Help — Upload a World to a Bedrock Realm](https://help.minecraft.net/hc/en-us/articles/20686744908429)
13. [Minecraft Help — Transfer a World to Another Device](https://help.minecraft.net/hc/en-us/articles/4409165790605)
14. [Mojang — Servers and Realms on PlayStation](https://www.minecraft.net/en-us/article/servers-and-realms-are-coming-playstation)
15. [Minecraft Partner Program](https://www.minecraft.net/en-us/partner)
16. [Minecraft Help — PlayStation Microsoft-account linking](https://help.minecraft.net/hc/en-us/articles/360037725571-Minecraft-for-PlayStation-4-FAQ)
17. [Microsoft official skin-pack sample](https://github.com/microsoft/minecraft-samples/tree/main/skinpack)

Primary code and published type surfaces:

- [Bedrock-Skins at the inspected commit](https://github.com/BrandonItaly/Bedrock-Skins/tree/1cc5523d5f9b2d15b2a78f3fc2a38d74d7c265cb)
- [`@minecraft/server` 1.17.0 published package](https://registry.npmjs.org/@minecraft/server/-/server-1.17.0.tgz)
- [`@minecraft/server-ui` 1.3.0 published package](https://registry.npmjs.org/@minecraft/server-ui/-/server-ui-1.3.0.tgz)

Pages were checked on 12 November 2026. “Latest” API versions were not used as a proxy for compatibility with the user's unknown PS4 client.
