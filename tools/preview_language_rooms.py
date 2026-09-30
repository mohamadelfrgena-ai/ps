#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة أسماء غرف اللغة في قائمة المجتمع — كل غرفة باسمها بلغتها.

التعيين مأخوذ من ملف التطبيق نفسه (ui/community/ChannelNames.kt) حتى لا يختلف
العرض عن النتيجة الحقيقية، والأيقونات من drawable-nodpi.
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
DRAWABLES = os.path.join(RES, "drawable-nodpi")
KT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                  "addonstation", "mobile", "ui", "community", "ChannelNames.kt")
OUT = os.path.join(ROOT, "language_rooms_preview.png")

BG, CARD, TEXT, MUTED, GREEN, DIM, BORDER = (
    "#080C12", "#151E2A", "#E8EEF5", "#8EA0B3", "#3DDC84", "#5A6B7D", "#263240")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# أيقونات الغرف كما في RoomIcon (channelId → ic_room_*)
ICON_OF = {
    "general": "ic_room_general", "minecraft": "ic_room_minecraft", "creator": "ic_room_creator",
    "support": "ic_room_support", "social-media-makers": "ic_room_social",
    "lang-ar": "ic_room_lang_ar", "lang-en": "ic_room_lang_en", "lang-es": "ic_room_lang_es",
    "lang-fr": "ic_room_lang_fr", "lang-pt": "ic_room_lang_pt", "lang-ru": "ic_room_lang_ru",
    "lang-tr": "ic_room_lang_tr", "lang-zh": "ic_room_lang_zh",
}

MAIN_ROOMS = [
    ("general", "General"), ("minecraft", "Minecraft"), ("creator", "Creator"),
    ("support", "Support"), ("social-media-makers", "Social Media Makers"),
]


def parse_names() -> dict:
    """يقرأ التعيين من ChannelNames.kt."""
    text = io.open(KT, encoding="utf-8").read()
    block = text.split("LANGUAGE_ROOM_NAMES: Map<String, String> = mapOf(", 1)[1].split(")", 1)[0]
    pairs = re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', block)
    return dict(pairs)


def rtl(s: str) -> str:
    return get_display(arabic_reshaper.reshape(s))


def icon(name: str, size: int):
    path = os.path.join(DRAWABLES, f"{name}.png")
    if not os.path.exists(path):
        return None
    return Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)


def main() -> int:
    names = parse_names()
    rooms = [(cid, names.get(cid, name)) for cid, name in MAIN_ROOMS]
    rooms += [(cid, names[cid]) for cid in ("lang-ar", "lang-en", "lang-es", "lang-fr",
                                            "lang-pt", "lang-ru", "lang-tr", "lang-zh")]

    W, H = 760, 120 + len(rooms) * 54 + 30
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_title = ImageFont.truetype(FONT_BOLD, 24)
    f_body = ImageFont.truetype(FONT_REG, 22)
    f_sec = ImageFont.truetype(FONT_BOLD, 17)

    d.rectangle((0, 0, W, 70), fill="#111823")
    d.text((W - 20, 22), rtl("المجتمع — الغرف"), font=f_title, fill=TEXT, anchor="ra")

    y = 92
    d.text((W - 20, y), rtl("الغرف الأساسية"), font=f_sec, fill=GREEN, anchor="ra")
    y += 26

    for index, (cid, name) in enumerate(rooms):
        if index == len(MAIN_ROOMS):
            y += 20
            d.text((W - 20, y), rtl("غرف اللغات — كل غرفة باسمها بلغتها"), font=f_sec, fill=GREEN, anchor="ra")
            y += 26
        d.rounded_rectangle((14, y, W - 14, y + 46), radius=12, fill=CARD, outline=BORDER)
        ic = icon(ICON_OF.get(cid, "ic_room_general"), 26)
        if ic:
            img.paste(ic, (W - 48, y + 10), ic)
        rtl_lang = cid in ("lang-ar",)          # العربية فقط تُقلب للعرض
        shown = rtl(name) if rtl_lang else name
        if cid == "lang-zh" or cid == "lang-ru" or cid == "lang-tr" or cid == "lang-fr" or cid == "lang-es" or cid == "lang-pt":
            shown = name                            # لغات لاتينية/سيريلية/صينية تُعرض كما هي
        d.text((W - 62, y + 12), shown, font=f_body, fill=TEXT, anchor="ra")
        y += 54

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    for cid, name in rooms:
        print(f"   {cid:<22} → {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
