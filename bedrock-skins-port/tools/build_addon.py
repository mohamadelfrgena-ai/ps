#!/usr/bin/env python3
"""Bedrock Skins -- offline port builder.

    python3 tools/build_addon.py --source "/path/to/Bedrock-Skins-master"

Reads the *Java mod's own assets* (skin_packs/, persona/, animations/,
textures/) and emits:

    dist/
      BedrockSkins_BP/            behavior pack  (.mcpack)
      BedrockSkins_RP/            resource pack  (.mcpack)
      BedrockSkins.mcaddon        both packs, one file
      skins/<pack>/*              skin packs    (.mcskinpack)
      REPORT.txt

No network access is required or performed.
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bsport import (addon, emotes as emotes_mod, geometry as geo,
                    layering, persona as persona_mod, skinpacks)
from bsport.util import (ensure_dir, human_size, log, read_json, slug, stable_uuid,
                         warn, write_json)

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)


def find_assets(source: str) -> str:
    candidates = [
        os.path.join(source, "src", "main", "resources", "assets", "bedrockskins"),
        os.path.join(source, "assets", "bedrockskins"),
        source,
    ]
    for candidate in candidates:
        if os.path.isdir(os.path.join(candidate, "skin_packs")):
            return candidate
    raise SystemExit(
        f"could not find assets/bedrockskins under {source!r}\n"
        "expected .../src/main/resources/assets/bedrockskins/skin_packs"
    )


def load_cosmetics(assets: str) -> list[persona_mod.PersonaPiece]:
    root = os.path.join(assets, "persona")
    pieces = []
    if not os.path.isdir(root):
        return pieces
    for name in sorted(os.listdir(root)):
        directory = os.path.join(root, name)
        if not os.path.isdir(directory):
            continue
        piece = persona_mod.load_piece(directory)
        if piece:
            pieces.append(piece)
    return pieces


def collect_capes(packs: list[skinpacks.SkinPack]) -> list[tuple[str, str]]:
    """Distinct cape files referenced by skins.json (for dynamic-cap e items)."""
    seen: dict[str, str] = {}
    for pack in packs:
        for entry in pack.entries:
            if not entry.cape or ":" in entry.cape:
                continue
            path = os.path.join(pack.directory, entry.cape)
            if os.path.isfile(path):
                seen.setdefault(slug(os.path.splitext(os.path.basename(path))[0]), path)
    return sorted(seen.items())


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Build the Bedrock Skins offline add-on")
    parser.add_argument("--source", required=True,
                        help="Bedrock-Skins-master folder (or its assets/bedrockskins)")
    parser.add_argument("--out", default=os.path.join(PROJECT, "dist"))
    parser.add_argument("--name", default="Bedrock Skins (Offline)")
    parser.add_argument("--cosmetics", action="store_true",
                        help="bake persona pieces into extra skin entries (skin + piece)")
    parser.add_argument("--cosmetics-per-skin", type=int, default=0,
                        help="cap merged cosmetic variants per skin (0 = all)")
    parser.add_argument("--cosmetics-combos", type=int, default=1, metavar="N",
                        help="max pieces stacked into one merged skin (1 = singles, 2-3 = loadouts)")
    parser.add_argument("--combo-limit", type=int, default=12,
                        help="max multi-piece combos generated per pack")
    parser.add_argument("--server-module", default="1.13.0",
                        help="@minecraft/server version the behavior pack depends on")
    parser.add_argument("--server-ui-module", default="1.2.0",
                        help="@minecraft/server-ui version the behavior pack depends on")
    parser.add_argument("--no-ui", action="store_true",
                        help="skip the pause-screen JSON-UI overlay")
    parser.add_argument("--dynamic-capes", action="store_true",
                        help="also emit wearable cape items + attachables")
    parser.add_argument("--only", choices=("all", "skins", "addon"), default="all")
    parser.add_argument("--min-engine", default="1.21.0",
                        help="minimum engine version written into both manifests")
    parser.add_argument("--game-version", default=None,
                        help="target client version (e.g. 1.21.90); also pins the "
                             "player.entity.json format_version to match")
    parser.add_argument("--zip/--no-zip", dest="zip", default=True,
                        action=argparse.BooleanOptionalAction)
    args = parser.parse_args(argv)

    assets = find_assets(os.path.abspath(args.source))
    out = ensure_dir(os.path.abspath(args.out))
    log(f"assets: {assets}")
    log(f"output: {out}")

    vanilla_geometry = geo.load_geometry_file(
        os.path.join(assets, "skin_packs", "vanilla", "geometry.json"))
    log(f"vanilla geometry: {len(vanilla_geometry)} definitions")

    report: list[str] = [f"source: {assets}", f"output: {out}", ""]

    # ---------------------------------------------------------------- skin packs
    packs = skinpacks.discover(os.path.join(assets, "skin_packs"))
    cosmetics = load_cosmetics(assets) if args.cosmetics else []
    combos: list = []
    if cosmetics and args.cosmetics_combos > 1:
        combos = layering.generate_combos(cosmetics,
                                          max_layers=args.cosmetics_combos,
                                          limit=args.combo_limit)
        log(f"cosmetic layering: {args.cosmetics_combos} layers max -> {layering.describe(combos)}")
    log(f"skin packs: {len(packs)} | persona pieces: {len(cosmetics)} "
        f"({persona_mod.summarize(cosmetics) or 'none'})")

    skin_root = ensure_dir(os.path.join(out, "skins"))
    total_skins = 0
    if args.only in ("all", "skins"):
        for pack in packs:
            target = ensure_dir(os.path.join(skin_root, pack.key))
            stats = skinpacks.build_pack(
                pack, target, cosmetics=cosmetics,
                bake_cosmetics=args.cosmetics,
                geometry_fallback=vanilla_geometry,
                max_cosmetics_per_skin=args.cosmetics_per_skin,
                combos=combos,
            )
            addon.write_skin_pack_manifest(
                target, pack.localization_name or pack.key,
                f"{stats['skins']} skins, {stats['capes']} capes, {stats['cosmetics']} cosmetic variants",
                f"skinpack.{pack.key}",
                os.path.join(pack.directory, "pack_icon.png"),
            )
            total_skins += stats["skins"]
            report.append(
                f"[skin pack] {pack.key}: {stats['skins']} skins, {stats['capes']} capes, "
                f"{stats['cosmetics']} merged cosmetics (max {stats['max_layers']} layers), "
                f"{stats['geometries']} geometries")
            if args.zip:
                addon.zip_dir(target, os.path.join(out, f"{pack.key}.mcskinpack"))

    # ------------------------------------------------------------------- emotes
    emote_list = emotes_mod.discover(os.path.join(assets, "persona"))
    dressing_room = emotes_mod.load_dressing_room(assets)
    log(f"emotes: {emotes_mod.summary(emote_list)} | dressing-room reactions: {len(dressing_room)}")

    if args.only in ("all", "addon"):
        target = args.game_version or args.min_engine
        min_engine = tuple(int(part) for part in target.split("."))
        min_engine = (min_engine + (0, 0, 0))[:3]
        options = {
            "name": args.name,
            "description": "Skins, capes, cosmetics and emotes -- runs fully offline.",
            "min_engine": min_engine,
            "entity_format_version": ".".join(str(part) for part in min_engine),
            "server_module": args.server_module,
            "server_ui_module": args.server_ui_module,
            "ui_overlay": not args.no_ui,
        }
        capes = collect_capes(packs) if args.dynamic_capes else []
        bp_dir = ensure_dir(os.path.join(out, "BedrockSkins_BP"))
        rp_dir = ensure_dir(os.path.join(out, "BedrockSkins_RP"))
        bp_stats = addon.build_bp(bp_dir, emotes=emote_list,
                                  template_dir=os.path.join(PROJECT, "packs", "src_bp"),
                                  options=options, capes=capes)
        rp_stats = addon.build_rp(rp_dir, emotes=emote_list, dressing_room=dressing_room,
                                  template_dir=os.path.join(PROJECT, "packs", "src_rp"),
                                  options=options, capes=capes)
        report.append(f"[addon] BP: {bp_stats}")
        report.append(f"[addon] RP: {rp_stats}")

        if args.zip:
            addon.zip_dir(bp_dir, os.path.join(out, "BedrockSkins_BP.mcpack"))
            addon.zip_dir(rp_dir, os.path.join(out, "BedrockSkins_RP.mcpack"))
            addon_files = [os.path.join(out, "BedrockSkins_BP"),
                           os.path.join(out, "BedrockSkins_RP")]
            import zipfile
            target = os.path.join(out, "BedrockSkins.mcaddon")
            with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
                for folder in addon_files:
                    base = os.path.basename(folder)
                    for root, _dirs, files in os.walk(folder):
                        for name in sorted(files):
                            full = os.path.join(root, name)
                            archive.write(full, f"{base}/{os.path.relpath(full, folder)}")
            log(f"wrote {target} ({os.path.getsize(target) // 1024} KB)")

    report.append("")
    report.append(f"skins emitted: {total_skins}")
    report.append(f"emotes emitted: {len(emote_list)}")
    with open(os.path.join(out, "REPORT.txt"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(report) + "\n")
    log("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
