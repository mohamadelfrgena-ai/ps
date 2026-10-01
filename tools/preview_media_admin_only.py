#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة «الوسائط للأدمن فقط»: نافذة الإرفاق للأدمن مقابل المستخدم + قرار القاعدة.

النصوص مأخوذة من موارد التطبيق الفعلية (values-ar/strings.xml) لا من تأليف.
المصدر: ChatSheets.kt · ChatScreen.kt · supabase/03_functions_chat.sql · 18_media_admin_only.sql
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "android", "app", "src", "main", "res", "values-ar", "strings.xml")
OUT = os.path.join(ROOT, "media_admin_only_preview.png")

BG, CARD, CARD2, TEXT, MUTED, GREEN, RED, DIM, BORDER, BLUE, GOLD = (
    "#080C12", "#151E2A", "#1A2534", "#E8EEF5", "#8EA0B3", "#3DDC84", "#FF6B6B",
    "#5A6B7D", "#263240", "#7CB7FF", "#E8C36B")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def rtl(text: str) -> str:
    return get_display(arabic_reshaper.reshape(text))


def s(key: str) -> str:
    """يقرأ نصاً حقيقياً من موارد التطبيق."""
    txt = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(key), txt)
    if not m:
        raise SystemExit(f"مفتاح مفقود: {key}")
    return m.group(1).replace("\\'", "'")


def ic_photo(size=16):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((1, 3, size - 2, size - 2), outline="#8EA0B3", width=2)
    d.ellipse((4, 6, 8, 10), fill="#8EA0B3")
    return im


def ic_film(size=16, color="#8EA0B3"):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((1, 3, size - 2, size - 2), outline=color, width=2)
    for x in (3, size - 6):
        d.rectangle((x, 5, x + 2, size - 4), fill=color)
    return im


def ic_lock(size=16, color="#E8C36B"):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.arc((3, 0, size - 4, size - 5), start=180, end=360, fill=color, width=2)
    d.rectangle((2, size // 2 - 1, size - 3, size - 2), outline=color, width=2)
    return im


def panel(img, d, fonts, x, y, w, title, subtitle, rows, note, note_color):
    f_h, f_row, f_small = fonts
    h = 84 + len(rows) * 42 + (46 if note else 15)
    d.rounded_rectangle((x, y, x + w, y + h), radius=20, fill=CARD2, outline=BORDER)
    d.text((x + w - 16, y + 12), rtl(title), font=f_h, fill=TEXT, anchor="ra")
    d.text((x + w - 16, y + 42), rtl(subtitle), font=f_small, fill=note_color, anchor="ra")
    yy = y + 72
    for emoji, text, tint, enabled in rows:
        d.rounded_rectangle((x + 12, yy, x + w - 12, yy + 36), radius=10,
                            fill=CARD if enabled else "#121A23", outline=BORDER)
        ic = ic_photo() if emoji == "🖼" else (ic_film() if enabled else ic_lock())
        img.paste(ic, (x + 24, yy + 10), ic)
        d.text((x + w - 26, yy + 8), rtl(text), font=f_row, fill=tint, anchor="ra")
        yy += 42
    if note:
        d.rounded_rectangle((x + 12, yy - 4, x + w - 12, yy + 36), radius=10,
                            fill="#131A24", outline="#3A2F17")
        d.text((x + w - 26, yy + 2), rtl(note), font=f_small, fill=GOLD, anchor="ra")
    return h


def main() -> int:
    W, H = 1240, 800
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_big = ImageFont.truetype(FONT_BOLD, 27)
    f_h = ImageFont.truetype(FONT_BOLD, 21)
    f_row = ImageFont.truetype(FONT_REG, 22)
    f_small = ImageFont.truetype(FONT_REG, 16)
    f_mono = ImageFont.truetype(FONT_MONO, 13)

    photo = s("chat_attach_photo")
    media = s("chat_attach_media")
    deny = s("chat_media_admin_only")

    d.rectangle((0, 0, W, 70), fill="#111823")
    d.text((W - 22, 20), rtl("نافذة الإرفاق في الشات (+) — الوسائط للأدمن فقط"), font=f_big, fill=TEXT, anchor="ra")

    f_panel = (f_h, f_row, f_small)
    panel(img, d, f_panel, W // 2 + 10, 96, W // 2 - 30, "حساب الأدمن (admin)",
          "يرى الخيارين ويكتب «فيديو / صوت» بلا قيود", [
              ("🖼", photo, TEXT, True),
              ("🎬", media, TEXT, True),
          ], None, GREEN)
    panel(img, d, f_panel, 20, 96, W // 2 - 30, "مستخدم عادي / مشرف",
          "لا يرى خيار الوسائط إطلاقاً", [
              ("🖼", photo, TEXT, True),
          ], deny, GOLD)

    # قرار القاعدة
    y = 300
    d.text((W - 24, y), rtl("قرار قاعدة البيانات عند الإرسال (send_chat_message + مشغّل الحماية)"), font=f_h, fill=TEXT, anchor="ra")
    y += 40
    rows = [
        ("الأدمن (admin)", "video · audio · تسجيل صوتي", '{"ok": true}', GREEN, "يُرسل"),
        ("مستخدم عادي", "video · audio · تسجيل صوتي", '{"ok": false, "message": "admin_only_media"}', RED, "يُرفض"),
        ("مشرف (moderator)", "video · audio · تسجيل صوتي", '{"ok": false, "message": "admin_only_media"}', RED, "يُرفض"),
        ("الجميع", "نص · صورة · ملصق · إيموجي", '{"ok": true}', BLUE, "بلا تغيير"),
    ]
    for who, what, res, color, verdict in rows:
        d.rounded_rectangle((20, y, W - 20, y + 56), radius=12, fill=CARD, outline=BORDER)
        d.text((W - 40, y + 8), rtl(who), font=f_row, fill=TEXT, anchor="ra")
        d.text((40, y + 12), rtl(verdict), font=f_small, fill=color, anchor="la")
        d.text((W - 40, y + 32), rtl(what), font=f_small, fill=MUTED, anchor="ra")
        d.text((40, y + 32), res, font=f_mono, fill=color, anchor="la")
        y += 62

    y += 4
    d.text((W - 24, y), rtl("ملاحظة: التسجيل الصوتي داخل التطبيق يُرسل كرسالة صوتية (audio) — فتشملها نفس القاعدة."),
           font=f_small, fill=DIM, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    print("   للأدمن:", media, "· لغيره:", deny[:60] + "…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
