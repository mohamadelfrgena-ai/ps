#!/usr/bin/env python3
"""AddonStation addon packager for GitHub Releases + Supabase.

Fixes the two problems with real-world Minecraft addon archives:

1. Scattered files (no root folder): several packs are shipped as loose
   `pack0/`, `pack1/` folders — or files dumped at the archive root — with no
   single wrapper folder. The packager locates every pack by finding its
   `manifest.json` (or `level.dat` for worlds) wherever it is.

2. Colliding folder names (`pack0`, `pack1`, `resource`): the destination
   folder inside `com.mojang` MUST be unique. We name each pack folder
   `<slug>_<uuid8>` where `uuid8` is the first 8 hex chars of the pack's
   `manifest.json -> header.uuid`. Two packs can never collide, and the PS4
   installer copies each pack into its correct home:
     modules[].type == "resource"       -> resource_packs/<slug>_<uuid8>
     modules[].type == "data"           -> behavior_packs/<slug>_<uuid8>
     modules[].type == "world_template" -> minecraftWorlds/<slug>_<uuid8>
     level.dat present (no manifest)    -> minecraftWorlds/<slug>_<uuid8>

Outputs (per pack, plus an index):
  - `dist/<slug>_<uuid8>.raw`   PVRAW001 stream (exact format the PS4 app copies)
  - `dist/<slug>.pack.json`     pack_targets entry for Supabase content_items
  - `dist/<slug>.supabase.json` ready-to-paste content_items row (incl. ps4_raw_url)
  - `dist/<slug>.zip`           clean re-packed archive for GitHub Releases

Usage:
  python3 tools/pack_addon.py MyAddon.zip --name "MyAddon" \
      --version 1.0.0 --repo owner/repo --tag content-myaddon-1.0.0

  # then upload the .raw files to a GitHub Release:
  gh release upload <tag> dist/*.raw dist/*.zip
"""

import argparse
import hashlib
import io
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import zipfile

MAGIC = b"PVRAW001"

TYPE_TO_DIR = {
    "resource": "resource_packs",
    "resources": "resource_packs",
    "data": "behavior_packs",
    "world_template": "minecraftWorlds",
    "world": "minecraftWorlds",
}


def is_pure_ascii(text: str) -> bool:
    return all(32 <= ord(c) <= 126 for c in text)


def slugify(text: str, fallback: str = "pack") -> str:
    slug = re.sub(r"[^A-Za-z0-9]+", "-", (text or "").strip()).strip("-").lower()
    return slug or fallback


def read_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def find_packs(root: str) -> list[dict]:
    """Locate every pack under root: a folder containing manifest.json, or a
    world folder containing level.dat. Handles scattered layouts (files at the
    archive root, pack0/pack1 siblings, nested wrappers)."""
    packs = []

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        filenames.sort()
        if "manifest.json" in filenames:
            manifest_path = os.path.join(dirpath, "manifest.json")
            try:
                manifest = read_json(manifest_path)
            except Exception:
                continue
            header = manifest.get("header") or {}
            modules = manifest.get("modules") or []
            mod_types = [str(m.get("type", "")).lower() for m in modules]
            dest = "resource_packs"
            for t in mod_types:
                if t in TYPE_TO_DIR:
                    dest = TYPE_TO_DIR[t]
                    break
            packs.append({
                "dir": dirpath,
                "name": str(header.get("name") or os.path.basename(dirpath)),
                "uuid": str(header.get("uuid") or ""),
                "version": header.get("version") or manifest.get("version") or [1, 0, 0],
                "module_types": mod_types,
                "dest_dir": dest,
                "kind": "manifest",
            })
            dirnames[:] = []  # packs do not nest
        elif "level.dat" in filenames and dirpath != root:
            packs.append({
                "dir": dirpath,
                "name": os.path.basename(dirpath),
                "uuid": "",
                "version": [1, 0, 0],
                "module_types": ["world_template"],
                "dest_dir": "minecraftWorlds",
                "kind": "level",
            })
            dirnames[:] = []

    # Loose files at the archive root with a manifest.json at root itself
    if not packs and os.path.isfile(os.path.join(root, "manifest.json")):
        packs = find_packs.__wrapped__(root) if hasattr(find_packs, "__wrapped__") else packs

    # Scattered loose files at root (no manifest anywhere): treat root as one resource pack
    if not packs:
        loose = [f for f in os.listdir(root) if os.path.isfile(os.path.join(root, f))]
        if loose:
            packs.append({
                "dir": root,
                "name": os.path.basename(root.rstrip("/")) or "pack",
                "uuid": "",
                "version": [1, 0, 0],
                "module_types": ["resource"],
                "dest_dir": "resource_packs",
                "kind": "loose",
            })
    return packs


def extract_archive(archive_path: str, dest: str) -> str:
    lower = archive_path.lower()
    if lower.endswith(".zip") or lower.endswith(".mcpack") or lower.endswith(".mcaddon"):
        with zipfile.ZipFile(archive_path, "r") as zf:
            zf.extractall(dest)
        return dest
    if os.path.isdir(archive_path):
        shutil.copytree(archive_path, dest, dirs_exist_ok=True)
        return dest
    raise RuntimeError(f"Unsupported input: {archive_path}")


def uuid8_of(uuid_str: str, fallback_seed: str) -> str:
    cleaned = (uuid_str or "").replace("{", "").replace("}", "").replace("-", "").lower()
    if len(cleaned) >= 8 and all(c in "0123456789abcdef" for c in cleaned):
        return cleaned[:8]
    return hashlib.sha1(fallback_seed.encode("utf-8")).hexdigest()[:8]


def pack_raw(pack_dir: str, dest_folder: str, out_raw: str) -> tuple[int, int]:
    """Write a PVRAW001 stream: dirs, then files with (nameLen, size) headers."""
    rel_dirs: list[str] = []
    rel_files: list[tuple[str, str]] = []
    for root, dirs, files in os.walk(pack_dir):
        dirs.sort()
        files.sort()
        rel_root = os.path.relpath(root, pack_dir).replace("\\", "/")
        if rel_root != ".":
            if is_pure_ascii(rel_root):
                rel_dirs.append(rel_root)
            else:
                dirs[:] = []
                continue
        for fname in files:
            rel = fname if rel_root == "." else f"{rel_root}/{fname}"
            if is_pure_ascii(rel):
                rel_files.append((rel, os.path.join(root, fname)))

    with open(out_raw, "wb") as out_f:
        out_f.write(MAGIC)
        out_f.write(struct.pack("<I", len(rel_dirs)))
        for d in rel_dirs:
            b = d.encode("utf-8")
            out_f.write(struct.pack("<H", len(b)))
            out_f.write(b)
        out_f.write(struct.pack("<I", len(rel_files)))
        for rel, full in rel_files:
            b = rel.encode("utf-8")
            size = os.path.getsize(full)
            out_f.write(struct.pack("<HQ", len(b), size))
            out_f.write(b)
            with open(full, "rb") as in_f:
                shutil.copyfileobj(in_f, out_f, length=256 * 1024)
    return len(rel_dirs), len(rel_files)


def zip_pack(pack_dir: str, dest_folder: str, out_zip: str) -> None:
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(pack_dir):
            dirs.sort()
            files.sort()
            for fname in files:
                full = os.path.join(root, fname)
                rel = os.path.relpath(full, pack_dir).replace("\\", "/")
                zf.write(full, f"{dest_folder}/{rel}")


def main() -> None:
    ap = argparse.ArgumentParser(description="Pack Minecraft addons for AddonStation (GitHub Releases + Supabase).")
    ap.add_argument("input", help="addon .zip/.mcpack/.mcaddon or a folder")
    ap.add_argument("--name", default="", help="display name (default: pack manifest name)")
    ap.add_argument("--version", default="1.0.0", help="addon version label")
    ap.add_argument("--repo", default="", help="GitHub repo owner/name (for ps4_raw_url)")
    ap.add_argument("--tag", default="", help="GitHub release tag for the raw assets")
    ap.add_argument("--title-id", default="CUSA00265", help="hint only: console edition")
    ap.add_argument("--out", default="dist", help="output directory")
    ap.add_argument("--base-url", default="https://github.com", help="download URL base")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        root = extract_archive(args.input, os.path.join(tmp, "src"))
        packs = find_packs(root)
        if not packs:
            print("ERROR: no packs found (no manifest.json / level.dat).", file=sys.stderr)
            sys.exit(1)

        display = args.name or packs[0]["name"]
        slug = slugify(display)

        print(f"Found {len(packs)} pack(s) in '{args.input}':")
        index = {
            "name": display,
            "slug": slug,
            "version": args.version,
            "packs": [],
        }

        for i, pack in enumerate(packs):
            u8 = uuid8_of(pack["uuid"], pack["dir"] + "|" + pack["name"])
            # Collision-proof destination folder name: <slug>_<uuid8>
            # (pack0/pack1 siblings always end up with distinct names.)
            if len(packs) > 1:
                dest_folder = f"{slug}_{u8}"
            else:
                dest_folder = f"{slug}_{u8}" if pack["kind"] in ("loose", "level") else (
                    slugify(pack["name"], slug) + f"_{u8}"
                )
            dest_home = pack["dest_dir"]

            raw_name = f"{dest_folder}.raw"
            raw_path = os.path.join(args.out, raw_name)
            num_dirs, num_files = pack_raw(pack["dir"], dest_folder, raw_path)

            zip_name = f"{dest_folder}.zip"
            zip_pack(pack["dir"], dest_folder, os.path.join(args.out, zip_name))

            raw_sha = hashlib.sha256()
            with open(raw_path, "rb") as f:
                for chunk in iter(lambda: f.read(65536), b""):
                    raw_sha.update(chunk)

            raw_url = ""
            if args.repo and args.tag:
                raw_url = f"{args.base_url}/{args.repo}/releases/download/{args.tag}/{raw_name}"

            entry = {
                "pack_index": i,
                "source_name": pack["name"],
                "dest_folder": dest_folder,
                "target": dest_home,
                "module_types": pack["module_types"],
                "uuid": pack["uuid"],
                "uuid8": u8,
                "raw_file": raw_name,
                "raw_sha256": raw_sha.hexdigest(),
                "ps4_raw_url": raw_url,
                "files": num_files,
                "dirs": num_dirs,
            }
            index["packs"].append(entry)
            print(f"  [{i}] {pack['name']} ({pack['kind']}, modules={pack['module_types']})")
            print(f"      -> {dest_home}/{dest_folder}  ({num_files} files, {num_dirs} dirs)")
            print(f"      -> {raw_name}  sha256={raw_sha.hexdigest()[:16]}...")

        with open(os.path.join(args.out, f"{slug}.pack.json"), "w", encoding="utf-8") as f:
            json.dump(index, f, ensure_ascii=False, indent=2)

        pack_targets = [
            {"target": e["target"], "dest_folder": e["dest_folder"], "raw_file": e["raw_file"],
             "ps4_raw_url": e["ps4_raw_url"], "sha256": e["raw_sha256"]}
            for e in index["packs"]
        ]
        supabase_row = {
            "title": display,
            "slug": slug,
            "content_type": "addon" if any(e["target"] != "minecraftWorlds" for e in index["packs"]) else "world",
            "version": args.version,
            "ps4_raw_url": pack_targets[0]["ps4_raw_url"] if pack_targets else "",
            "pack_targets": pack_targets,
            "_upload_hint": (
                f"gh release upload {args.tag or '<tag>'} {args.out}/*.raw {args.out}/*.zip"
            ),
        }
        with open(os.path.join(args.out, f"{slug}.supabase.json"), "w", encoding="utf-8") as f:
            json.dump(supabase_row, f, ensure_ascii=False, indent=2)

        print(f"\nWrote: {args.out}/{slug}.pack.json and {slug}.supabase.json")
        if args.repo and args.tag:
            print(f"Upload with: gh release upload {args.tag} {args.out}/*.raw {args.out}/*.zip")


if __name__ == "__main__":
    main()
