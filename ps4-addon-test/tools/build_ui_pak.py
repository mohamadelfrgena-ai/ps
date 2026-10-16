#!/usr/bin/env python3
"""Builds assets/ui.pak (fonts + art extracted from AddonStation_PS4_complete_design.zip).

If assets/ui.pak already exists and has a valid ASUI0001 header, this is a no-op
(the prebuilt pak is committed to the repo so CI does not need the design zip).

To rebuild from scratch:
  1. Extract AddonStation_PS4_complete_design.zip to /tmp/ps4_design
  2. python3 tools/build_ui_pak.py assets/ui.pak --force
"""
import base64
import os
import re
import struct
import sys


def build(out_path: str, design_dir: str, font_cjk: str) -> None:
    html_path = os.path.join(design_dir, "AddonStation_PS4_design.html")
    with open(html_path, "r", encoding="utf-8") as f:
        text = f.read()

    css_bgs = {}
    for m in re.finditer(r"\.([a-zA-Z0-9_-]+)\{background-image:url\((data:image/[^)]+)\)\}", text):
        css_bgs[m.group(1)] = base64.b64decode(m.group(2).split(",", 1)[1])
    imgs = {}
    for m in re.finditer(r"\"(i\d+)\":\s*\"(data:image/[^\"]+)\"", text):
        imgs[m.group(1)] = base64.b64decode(m.group(2).split(",", 1)[1])

    def read(path: str) -> bytes:
        with open(path, "rb") as f:
            return f.read()

    entries = [
        ("font_f77", read(os.path.join(design_dir, "assets/fonts/F77-Minecraft.ttf"))),
        ("font_ar", read("android/app/src/main/res/font/tajawal_extrabold.ttf")),
        ("font_body", read("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")),
        ("font_cjk", read(font_cjk)),
        ("bg_splash", css_bgs["a1"]),
        ("bg_winter", css_bgs["bgw"]),
        ("bg_sunset", css_bgs["bgs"]),
        ("bg_creator", css_bgs["bgp"]),
        ("cover_0", css_bgs["i0"]),
        ("cover_1", css_bgs["i1"]),
        ("cover_2", css_bgs["i2"]),
        ("cover_3", css_bgs["i3"]),
        ("cover_4", css_bgs["i4"]),
        ("icon_app", imgs["i0"]),
        ("icon_coin", imgs["i3"]),
        ("room_general", imgs["i4"]),
        ("room_mc", imgs["i5"]),
        ("room_creator", imgs["i6"]),
        ("room_support", imgs["i7"]),
        ("room_social", imgs["i8"]),
        ("icon_avatar", imgs["i10"]),
        ("flag_en", imgs["i16"]),
        ("flag_ar", imgs["i17"]),
        ("flag_fr", imgs["i18"]),
        ("flag_tr", imgs["i19"]),
        ("flag_es", imgs["i20"]),
        ("flag_ru", imgs["i21"]),
        ("flag_pt", imgs["i22"]),
        ("flag_zh", imgs["i23"]),
    ]

    # PS legacy button icons (cropped from 17220652-pslegacy_xl.png) — packed
    # from assets/design_art so a --force rebuild keeps them.
    art_dir = os.path.join(os.path.dirname(os.path.abspath(out_path)), "design_art")
    for btn in ("btn_cross", "btn_circle", "btn_square", "btn_triangle",
                "btn_l1", "btn_r1", "btn_l2", "btn_r2", "btn_options"):
        bp = os.path.join(art_dir, btn + ".png")
        if os.path.exists(bp):
            entries.append((btn, read(bp)))

    with open(out_path, "wb") as out:
        out.write(b"ASUI0001")
        out.write(struct.pack("<I", len(entries)))
        for name, data in entries:
            nb = name.encode("utf-8")
            out.write(struct.pack("<HI", len(nb), len(data)))
            out.write(nb)
            out.write(data)
    print(f"ui.pak built: {len(entries)} entries -> {out_path} ({os.path.getsize(out_path)} bytes)")


def main() -> None:
    out_path = sys.argv[1] if len(sys.argv) > 1 else "assets/ui.pak"
    force = "--force" in sys.argv

    if not force and os.path.exists(out_path):
        with open(out_path, "rb") as f:
            if f.read(8) == b"ASUI0001":
                print(f"ui.pak already present and valid ({os.path.getsize(out_path)} bytes) — keeping it.")
                return

    design_dir = "/tmp/ps4_design"
    if not os.path.isfile(os.path.join(design_dir, "AddonStation_PS4_design.html")):
        print("ERROR: design zip not extracted to /tmp/ps4_design and no valid ui.pak exists.")
        sys.exit(1)

    build(out_path, design_dir, "assets/font_cjk.ttf")


if __name__ == "__main__":
    main()
