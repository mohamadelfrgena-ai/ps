#!/usr/bin/env python3
"""Unpacks PrizmaVisuals-1.3.10222.zip at build time into 'PrizmaVisuals 1.3.10222/' and writes pkg.gp4."""
import os
import shutil
import struct
import sys
import tempfile
import zipfile
from xml.sax.saxutils import quoteattr

MAGIC = b"PVRAW001"
ADDON_FOLDER_NAME = "PrizmaVisuals 1.3.10222"


def build_preextracted_folder(zip_path: str, out_folder: str) -> tuple[int, int]:
    if os.path.exists(out_folder):
        shutil.rmtree(out_folder)
    os.makedirs(out_folder, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmpdir:
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(tmpdir)

        extracted_root = os.path.join(tmpdir, ADDON_FOLDER_NAME)
        if not os.path.isdir(extracted_root):
            raise RuntimeError(f"Expected folder '{ADDON_FOLDER_NAME}' inside {zip_path}")

        rel_dirs: list[str] = []
        rel_files: list[tuple[str, str]] = []

        for root, dirs, files in os.walk(extracted_root):
            dirs.sort()
            files.sort()
            rel_root = os.path.relpath(root, extracted_root).replace("\\", "/")
            if rel_root != ".":
                rel_dirs.append(rel_root)
            for fname in files:
                full_path = os.path.join(root, fname)
                rel_path = fname if rel_root == "." else f"{rel_root}/{fname}"
                rel_files.append((rel_path, full_path))

        # Copy manifest.json and pack_icon.png directly into /app0/PrizmaVisuals 1.3.10222/
        for preview_file in ("manifest.json", "pack_icon.png"):
            src_preview = os.path.join(extracted_root, preview_file)
            if os.path.isfile(src_preview):
                shutil.copy2(src_preview, os.path.join(out_folder, preview_file))

        # Pack the entire pre-extracted folder structure and raw file bytes into folder.raw
        raw_bundle_path = os.path.join(out_folder, "folder.raw")
        with open(raw_bundle_path, "wb") as out_f:
            out_f.write(MAGIC)
            out_f.write(struct.pack("<I", len(rel_dirs)))
            for d in rel_dirs:
                b = d.encode("utf-8")
                out_f.write(struct.pack("<H", len(b)))
                out_f.write(b)

            out_f.write(struct.pack("<I", len(rel_files)))
            for rel_path, full_path in rel_files:
                b = rel_path.encode("utf-8")
                size = os.path.getsize(full_path)
                out_f.write(struct.pack("<HQ", len(b), size))
                out_f.write(b)
                with open(full_path, "rb") as in_f:
                    shutil.copyfileobj(in_f, out_f, length=256 * 1024)

        return len(rel_dirs), len(rel_files)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make_gp4.py <content_id> <output_gp4>")
    content_id = sys.argv[1]
    out_gp4 = sys.argv[2]

    zip_path = os.path.join("assets", "PrizmaVisuals-1.3.10222.zip")
    num_dirs, num_files = build_preextracted_folder(zip_path, ADDON_FOLDER_NAME)
    print(
        f"Pre-extracted '{ADDON_FOLDER_NAME}': {num_dirs} dirs, {num_files} files -> {ADDON_FOLDER_NAME}/folder.raw"
    )

    all_files = [
        "eboot.bin",
        "sce_sys/param.sfo",
        "sce_sys/icon0.png",
        "sce_sys/about/right.sprx",
        "sce_module/libc.prx",
        "sce_module/libSceFios2.prx",
    ]
    for fname in sorted(os.listdir(ADDON_FOLDER_NAME)):
        full = os.path.join(ADDON_FOLDER_NAME, fname)
        if os.path.isfile(full):
            all_files.append(f"{ADDON_FOLDER_NAME}/{fname}")

    dir_tree: dict = {}
    for path in all_files:
        parts = path.split("/")[:-1]
        node = dir_tree
        for p in parts:
            node = node.setdefault(p, {})

    def render_dirs(tree: dict, indent: str) -> list[str]:
        lines: list[str] = []
        for name in sorted(tree.keys()):
            children = tree[name]
            if children:
                lines.append(f"{indent}<dir targ_name={quoteattr(name)}>")
                lines.extend(render_dirs(children, indent + "  "))
                lines.append(f"{indent}</dir>")
            else:
                lines.append(f"{indent}<dir targ_name={quoteattr(name)} />")
        return lines

    xml_lines = [
        '<?xml version="1.0" encoding="utf-8" standalone="yes"?>',
        '<psproject fmt="gp4" version="1000">',
        '  <volume>',
        '    <volume_type>pkg_ps4_app</volume_type>',
        '    <volume_id>PS4VOLUME</volume_id>',
        '    <volume_ts>2026-10-15 12:00:00</volume_ts>',
        f'    <package content_id={quoteattr(content_id)} passcode="00000000000000000000000000000000" storage_type="digital50" app_type="full" />',
        '    <chunk_info chunk_count="1" scenario_count="1">',
        '      <chunks>',
        '        <chunk id="0" layer_no="0" label="Chunk #0" />',
        '      </chunks>',
        '      <scenarios default_id="0">',
        '        <scenario id="0" type="sp" initial_chunk_count="1" label="Scenario #0">0</scenario>',
        '      </scenarios>',
        '    </chunk_info>',
        '  </volume>',
        '  <files img_no="0">',
    ]
    for path in all_files:
        q = quoteattr(path)
        xml_lines.append(f"    <file targ_path={q} orig_path={q} />")
    xml_lines.append("  </files>")
    xml_lines.append("  <rootdir>")
    xml_lines.extend(render_dirs(dir_tree, "    "))
    xml_lines.append("  </rootdir>")
    xml_lines.append("</psproject>")

    with open(out_gp4, "w", encoding="utf-8") as f:
        f.write("\n".join(xml_lines) + "\n")


if __name__ == "__main__":
    main()
