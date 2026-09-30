#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة أسماء غرف اللغة: كل غرفة باسمها بلغتها.

يعرض الجدول الحالات التي قد تكون في قاعدة البيانات (اسم إنجليزي · اسم عربي ·
اسم مع إيموجي/علم) وما سيظهر في التطبيق بعد المعالجة — والقاعدة النهائية مأخوذة
من ملف التطبيق نفسه (ui/community/ChannelNames.kt).
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

BG, CARD, TEXT, MUTED, GREEN, DIM, BORDER, BLUE = (
    "#080C12", "#151E2A", "#E8EEF5", "#8EA0B3", "#3DDC84", "#5A6B7D", "#263240", "#7CB7FF")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

ARABIC = "\u0600-\u06FF\u0750-\u077F"

def _needs_rtl(text: str) -> bool:
    return any("\u0600" <= ch <= "\u06FF" for ch in text)


def wrap(text: str) -> str:
    return get_display(arabic_reshaper.reshape(text)) if _needs_rtl(text) else text


def parse_native_names() -> dict:
    """يقرأ التعيين من ChannelNames.kt (قسم المعرّفات)."""
    text = io.open(KT, encoding="utf-8").read()
    block = text.split("LANGUAGE_ROOM_NAMES: Map<String, String> = mapOf(", 1)[1].split(")\n", 1)[0]
    return dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', block))


def parse_aliases() -> dict:
    """يقرأ جدول الأسماء البديلة: الاسم الأصلي ← قائمة ما قد يوجد في القاعدة."""
    text = io.open(KT, encoding="utf-8").read()
    out = {}
    for native, names in re.findall(r'register\("([^"]+)"((?:\s*,\s*"[^"]*")*)\)', text):
        out[native] = re.findall(r'"([^"]+)"', names)
    return out


def icon(name: str, size: int):
    path = os.path.join(DRAWABLES, f"{name}.png")
    if not os.path.exists(path):
        return None
    return Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)


ICON_OF = {
    "lang-ar": "ic_room_lang_ar", "lang-en": "ic_room_lang_en", "lang-ru": "ic_room_lang_ru",
    "lang-zh": "ic_room_lang_zh", "lang-es": "ic_room_lang_es", "lang-fr": "ic_room_lang_fr",
    "lang-pt": "ic_room_lang_pt", "lang-tr": "ic_room_lang_tr",
}

# حالات شائعة كما قد تكون في قاعدة البيانات فعلاً
DB_CASES = [
    ("lang-ar", "ar", "🇱🇾 العربية"),
    ("lang-ru", "ru", "Russian"),
    ("lang-zh", "zh", "Chinese"),
    ("lang-en", "en", "english"),
    ("lang-es", "es", "الأسبانية"),
    ("lang-fr", "fr", "Français"),
    ("lang-pt", "pt", "البرتغالية"),
    ("lang-tr", "tr", "turkish"),
]


def main() -> int:
    natives = parse_native_names()
    aliases = parse_aliases()

    W, H = 900, 150 + len(DB_CASES) * 56 + 60
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_title = ImageFont.truetype(FONT_BOLD, 24)
    f_head = ImageFont.truetype(FONT_BOLD, 18)
    f_body = ImageFont.truetype(FONT_REG, 21)
    f_small = ImageFont.truetype(FONT_REG, 17)

    d.rectangle((0, 0, W, 72), fill="#111823")
    d.text((W - 22, 22), wrap("المجتمع — غرف اللغات"), font=f_title, fill=TEXT, anchor="ra")

    y = 96
    d.text((W - 22, y), wrap("الاسم في قاعدة البيانات"), font=f_head, fill=DIM, anchor="ra")
    d.text((452, y), wrap("ما يظهر في التطبيق"), font=f_head, fill=GREEN, anchor="ra")
    y += 30

    for cid, _code, stored in DB_CASES:
        native = natives[cid]
        d.rounded_rectangle((14, y, W - 14, y + 48), radius=12, fill=CARD, outline=BORDER)
        # يمين: الاسم في قاعدة البيانات — يسار: ما يظهر في التطبيق — وبينهما سهم التحويل
        d.text((W - 30, y + 12), wrap(stored), font=f_body, fill=MUTED, anchor="ra")
        d.text((452, y + 12), wrap(native), font=f_body, fill=TEXT, anchor="ra")
        d.text((486, y + 10), "←", font=f_body, fill=GREEN, anchor="mm")
        ic = icon(ICON_OF.get(cid, "ic_room_lang_en"), 26)
        if ic:
            img.paste(ic, (24, y + 11), ic)
        y += 56

    y += 6
    d.text((W - 22, y), wrap("جدول الأسماء البديلة المقروء من ChannelNames.kt:"), font=f_head,
           fill=BLUE, anchor="ra")
    y += 28
    for native in ("العربية", "English", "Español", "Français", "Português", "Русский", "Türkçe", "中文"):
        names = aliases.get(native, [])
        line = f"{native}:  " + " · ".join(names[:6])
        d.text((W - 30, y), wrap(line), font=f_small, fill=MUTED, anchor="ra")
        y += 24

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    for cid, _c, stored in DB_CASES:
        print(f"   القاعدة: {stored:<16} → التطبيق: {natives[cid]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
