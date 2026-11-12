# Audit of `tier-b/PS4-FTP.md` for Tier C

Read-only audit. Tier B was not modified.

## Findings retained

- Correctly labels PS4 work **NOT RUN** and refuses compatibility claims.
- Correctly identifies GoldHEN FTP port **2121** and trusted-LAN-only use.
- Correctly refuses hardcoded CUSA/user IDs and broad filesystem scans.
- Correctly separates file transfer from pack discovery, script execution, rendering, and player-skin change.
- Correctly forbids executable/account/DRM/auth bypass and explicitly says not to transfer the Tier B manual skin pack as a presumed PS4 solution.
- Repository path pattern is corroborated by `ps4/src/minecraft_storage.cpp`: `/app_tmp|/app_temp/<CUSA…>/<user…>/games/com.mojang`, with symlink rejection and explicit selection.

## Gaps for Tier C

1. **Backup is not a complete write gate.** Tier B says “back up saves first” but does not require a verified full `com.mojang` mirror plus native savedata backup, zero failed/skipped transfers, hashes, and an explicit operator go/no-go before the first upload.
2. **Wrong pack class for this objective.** Tier B routes only `resource_packs` and `behavior_packs`. Tier C must investigate `skin_packs` and the `skin_pack` manifest module, not RP/BP activation.
3. **No skin-pack recognition path.** It does not require an existing `skin_packs` directory, read-only inventory of that directory, or a unique unpacked skin-pack folder.
4. **No offline selection/persistence criteria.** It does not test Dressing Room visibility, selection without sign-in, real local-player application, or full Minecraft restart persistence.
5. **World entry before transfer.** Tier B tells the operator to enter a disposable world to expose the mount. For Tier C, prefer title screen/main menu if sufficient; if a world is needed for real-player observation, treat normal game autosaves as game-managed activity and never edit save files manually.
6. **No read-back integrity check.** It shows example `curl` reads but does not require hash comparison of every uploaded file against the local pack manifest.
7. **Version requirements are not recorded.** It does not require exact PS4 firmware, GoldHEN payload version, Minecraft client version, Title ID, region, and user-directory evidence.
8. **Anonymous FTP is conditional, not guaranteed.** Tier B correctly says “if the server accepts it”; Tier C must stop on unexpected auth prompts rather than guessing.

## Tier C corrections

- Backup gate before any Minecraft data write.
- Existing `skin_packs` directory required; if absent, stop and record FAIL/not demonstrated rather than creating speculative directories.
- Upload only a new unique unpacked `skin_pack` folder; never upload `.mcpack` as if FTP were an import handler.
- Read back and hash every uploaded file.
- Separate gates: filesystem access → pack recognition → actual UI → offline selection → real player application → restart persistence.
- Current hardware result for every success criterion: **FAIL — not demonstrated (NOT RUN)**.
