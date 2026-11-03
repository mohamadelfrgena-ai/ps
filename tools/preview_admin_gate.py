#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة حماية لوحة الإدارة: الحالات الأربع + سجلّ التدقيق في القاعدة.

النصوص مأخوذة من موارد التطبيق الفعلية (values-ar/strings.xml).
المصدر: AdminGate.kt · AppNav.kt · supabase/19_admin_panel_guard.sql
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "android", "app", "src", "main", "res", "values-ar", "strings.xml")
OUT = os.path.join(ROOT, "admin_panel_guard_preview.png")

BG, CARD, CARD2, TEXT, MUTED, GREEN, RED, DIM, BORDER, BLUE, GOLD = (
    "#080C12", "#151E2A", "#1A2534", "#E8EEF5", "#8EA0B3", "#3DDC84", "#FF6B6B",
    "#5A6B7D", "#263240", "#6FB8FF", "#FFD479")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def s(key):
    txt = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(key), txt)
    if not m:
        raise SystemExit("مفتاح مفقود: " + key)
    return m.group(1).replace("\\'", "'")


def lock_icon(size=34, color="#FFD479"):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.arc((int(size * .2), 1, int(size * .8), int(size * .62)), start=180, end=360, fill=color, width=3)
    d.rectangle((int(size * .14), int(size * .42), int(size * .86), size - 2), outline=color, width=3)
    d.ellipse((size * .45, size * .58, size * .55, size * .68), fill=color)
    return im


def shield_icon(size=34, color="#6FB8FF"):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(size // 2, 1), (size - 4, int(size * .22)), (size - 6, int(size * .7)),
               (size // 2, size - 2), (6, int(size * .7)), (4, int(size * .22))], outline=color, width=3)
    return im


def main() -> int:
    W, H = 1300, 880
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_big = ImageFont.truetype(FONT_BOLD, 26)
    f_h = ImageFont.truetype(FONT_BOLD, 20)
    f_body = ImageFont.truetype(FONT_REG, 19)
    f_small = ImageFont.truetype(FONT_REG, 15)
    f_mono = ImageFont.truetype(FONT_MONO, 13)

    d.rectangle((0, 0, W, 68), fill="#111823")
    d.text((W - 22, 20), rtl("حماية لوحة الإدارة — أربعة أقفال"), font=f_big, fill=TEXT, anchor="ra")

    # ---------- الحالات الأربع ----------
    cards = [
        ("① غير مسجَّل أو ليس أدمن", s("admin_not_allowed"), s("admin_gate_denied_body"),
         RED, "shield", "الحساب: role ≠ admin"),
        ("② إعادة إدخال كلمة المرور", s("admin_gate_title"), s("admin_gate_hint"),
         GOLD, "lock", "كلمة المرور عبر Supabase"),
        ("③ محاولات خاطئة", s("admin_gate_too_many"), s("admin_gate_cooldown").replace("%1$d", "١٢٠"),
         RED, "lock", "5 محاولات ← إيقاف دقيقتين"),
        ("④ داخل اللوحة", s("admin_lock_now"), s("admin_gate_locked_note"),
         GREEN, "shield", "قفل تلقائي بعد ١٥ دقيقة"),
    ]
    x, y, cw, ch = 20, 92, (W - 60) // 2, 190
    for i, (label, title, body, accent, icon, note) in enumerate(cards):
        cx = x + (i % 2) * (cw + 20)
        cy = y + (i // 2) * (ch + 18)
        d.text((cx + cw - 10, cy - 26), rtl(label), font=f_h, fill=accent, anchor="ra")
        d.rounded_rectangle((cx, cy, cx + cw, cy + ch), radius=18, fill=CARD2, outline=BORDER)
        ic = lock_icon() if icon == "lock" else shield_icon()
        img.paste(ic, (cx + cw - 52, cy + 16), ic)
        d.text((cx + cw - 66, cy + 20), rtl(title), font=f_body, fill=TEXT, anchor="ra")
        # لفّ النص الطويل على سطرين
        words, line, lines = body.split(), "", []
        for w in words:
            if len(line) + len(w) > 46:
                lines.append(line)
                line = w
            else:
                line = (line + " " + w).strip()
        lines.append(line)
        yy = cy + 60
        for ln in lines[:3]:
            d.text((cx + cw - 20, yy), rtl(ln), font=f_small, fill=MUTED, anchor="ra")
            yy += 24
        d.text((cx + 18, cy + ch - 28), rtl(note), font=f_small, fill=accent, anchor="la")

    # ---------- سجلّ التدقيق ----------
    y = 520
    d.text((W - 22, y), rtl("ما يُسجَّل في القاعدة (admin_audit_log) عبر admin_panel_access()"),
           font=f_h, fill=TEXT, anchor="ra")
    y += 38
    rows = [
        ("أدمن", "panel_opened", '{"ok": true, "is_admin": true, "role": "admin"}', GREEN, "يُفتح ويُسجَّل ✓"),
        ("مستخدم/مشرف", "panel_denied", '{"ok": false, "message": "not_admin"}', RED, "يُرفض ويُسجَّل ✗"),
        ("زائر (anon)", "—", "revoke execute → 42501", RED, "لا يستطيع النداء"),
    ]
    for who, action, res, color, verdict in rows:
        d.rounded_rectangle((20, y, W - 20, y + 56), radius=12, fill=CARD, outline=BORDER)
        d.text((W - 36, y + 8), rtl(who), font=f_body, fill=TEXT, anchor="ra")
        d.text((36, y + 12), rtl(verdict), font=f_small, fill=color, anchor="la")
        d.text((W - 36, y + 32), action, font=f_mono, fill=GOLD, anchor="ra")
        d.text((36, y + 32), res, font=f_mono, fill=color, anchor="la")
        y += 62

    y += 4
    d.text((W - 22, y), rtl("التطبيق: AdminGate ملفوفة على كل مسارات الإدارة السبعة في AppNav — لا فتح برابط ولا رجوع."),
           font=f_small, fill=DIM, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
