#!/usr/bin/env python3
"""Builds assets/ui.pak for AddonStation UI v7.3.

Pak format (ASUI0001): magic 8 bytes + uint32 count + per entry
  uint16 name_len + uint32 data_len + name + data      (little endian <HI)

Every file in assets/v73/ becomes one pak entry named after the file
without its extension (font_ar.ttf -> "font_ar", bg_cave.jpg -> "bg_cave").

Usage:
  python3 tools/build_ui_pak.py assets/ui.pak
"""
import os
import struct
import sys


def build(out_path: str, src_dir: str) -> None:
    entries = []
    for fname in sorted(os.listdir(src_dir)):
        path = os.path.join(src_dir, fname)
        if not os.path.isfile(path):
            continue
        name = os.path.splitext(fname)[0]
        with open(path, "rb") as f:
            data = f.read()
        entries.append((name, data))

    if not entries:
        raise SystemExit(f"no assets found in {src_dir}")

    # fonts first (engine loads them at init), then the rest alphabetically
    fonts = [e for e in entries if e[0].startswith("font_")]
    rest = [e for e in entries if not e[0].startswith("font_")]
    ordered = fonts + rest

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(b"ASUI0001")
        f.write(struct.pack("<I", len(ordered)))
        for name, data in ordered:
            nb = name.encode("utf-8")
            f.write(struct.pack("<HI", len(nb), len(data)))
            f.write(nb)
            f.write(data)
    total = os.path.getsize(out_path)
    print(f"wrote {out_path}: {len(ordered)} entries, {total:,} bytes")
    for name, data in ordered:
        print(f"  {len(data):>9,}  {name}")


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "assets/ui.pak"
    src = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "assets", "v73")
    build(out, src)
