#!/usr/bin/env python3
"""Unpacks PrizmaVisuals-1.3.10222.zip and generates pkg.gp4 with the unpacked folder."""
import os
import sys
import zipfile
from xml.sax.saxutils import quoteattr


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make_gp4.py <content_id> <output_gp4>")
    content_id = sys.argv[1]
    out_gp4 = sys.argv[2]

    zip_path = os.path.join("assets", "PrizmaVisuals-1.3.10222.zip")
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(".")

    base_files = [
        "eboot.bin",
        "sce_sys/param.sfo",
        "sce_sys/icon0.png",
        "sce_sys/about/right.sprx",
        "sce_module/libc.prx",
        "sce_module/libSceFios2.prx",
    ]
    all_files = list(base_files)
    addon_dir = "PrizmaVisuals 1.3.10222"
    for root, dirs, files in os.walk(addon_dir):
        dirs.sort()
        files.sort()
        for f in files:
            rel = os.path.join(root, f).replace("\\", "/")
            all_files.append(rel)

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
