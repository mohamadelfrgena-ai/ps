#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة انطباق واجهة التطبيق على مقاسات شاشات الهواتف.

يحاكي نفس معادلة Kotlin في ui/theme/Sizing.kt (نسبة التحجيم + عدد الأعمدة + ارتفاع الخلية)
ويرسم ثلاث شاشات بجانب بعضها:
    • هاتف صغير 360×780      (نسبة أقل من 1 ⇒ كل شيء أصغر قليلاً فلا يزدحم)
    • هاتف معتاد 392×830     (المرجع ⇒ 1.00)
    • هاتف كبير 480×1000     (نسبة أكبر ⇒ يملأ الشاشة بلا فراغ)

يرسم الأيقونات الحقيقية من drawable-nodpi والنصوص من ملف الموارد العربي.
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
STRINGS = os.path.join(RES, "values-ar", "strings.xml")
DRAWABLES = os.path.join(RES, "drawable-nodpi")
OUT = os.path.join(ROOT, "phone_fit_preview.png")

REF_WIDTH = 392.0
BG, FRAME, CARD, TEXT, MUTED, GREEN, DIM, BORDER = (
    "#080C12", "#151E2A", "#1B2634", "#E8EEF5", "#8EA0B3", "#3DDC84", "#5A6B7D", "#263240")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

PHONES = [("هاتف صغير", 360, 780), ("هاتف معتاد", 392, 830), ("هاتف كبير", 480, 1000)]
K = 1.55  # بكسل المعاينة لكل dp (للعرض فقط)


def rtl(s: str) -> str:
    return get_display(arabic_reshaper.reshape(s))


def resource(name: str) -> str:
    xml = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(name), xml, re.S)
    return m.group(1).replace("\\'", "'") if m else name


def icon(name: str, size: int):
    path = os.path.join(DRAWABLES, f"{name}.png")
    if not os.path.exists(path):
        return None
    return Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------- معادلة Kotlin نفسها
def scale_for(width: float, height: float) -> float:
    if width >= 600:
        return 1.0
    guard = min(max(height / 760.0, 0.9), 1.0) if 1 <= height <= 700 else 1.0
    return min(max((width / REF_WIDTH) * guard, 0.86), 1.16)


def columns_for(width: float, height: float, min_cell: float, max_cell: float,
                max_cols: int, padding: float, spacing: float):
    scale = scale_for(width, height)
    available = width / scale - padding

    def cell(cols):
        return (available - (cols - 1) * spacing) / cols

    cols = min(max(int((available + spacing) / (min_cell + spacing)), 1), max_cols)
    while cols > 1 and cell(cols) < min_cell:
        cols -= 1
    while cols < max_cols and cell(cols) > max_cell and cell(cols + 1) >= min_cell:
        cols += 1
    return cols, cell(cols), scale


def draw_phone(img: Image.Image, draw: ImageDraw.ImageDraw, x0: int, y0: int,
               label: str, width_dp: int, height_dp: int):
    scale = scale_for(width_dp, height_dp)
    cols, cell_w, _ = columns_for(width_dp, height_dp, 62, 86, 6, 28, 6)
    cell_h = min(max(cell_w + 30, 74), 104)

    w, h = int(width_dp * K), int(height_dp * K)
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=int(26 * K), fill=BG,
                           outline=BORDER, width=2)
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + int(34 * K)), radius=int(26 * K), fill=FRAME)
    draw.rectangle((x0, y0 + int(20 * K), x0 + w, y0 + int(34 * K)), fill=FRAME)

    f_title = ImageFont.truetype(FONT, max(10, int(22 * K * scale)))
    f_section = ImageFont.truetype(FONT, max(9, int(13 * K * scale)))
    f_body = ImageFont.truetype(FONT, max(9, int(13 * K * scale)))
    f_tiny = ImageFont.truetype(FONT, max(8, int(9 * K * scale)))

    pad = int(14 * K * scale)
    y = y0 + int(46 * K * scale)
    draw.text((x0 + w - pad, y), rtl("الإعدادات"), font=f_title, fill=TEXT, anchor="ra")
    y += int(40 * K * scale)

    # قسم «حول التطبيق»
    draw.text((x0 + w - pad, y), rtl(resource("set_about")), font=f_section, fill=GREEN, anchor="ra")
    y += int(16 * K * scale)

    rows = [("ic_set_terms", resource("set_terms"), True),
            ("ic_set_privacy", resource("set_privacy"), True),
            ("ic_set_version", resource("set_version"), False)]
    row_h = int(66 * K * scale)
    card_h = row_h * len(rows) + int(12 * K * scale)
    draw.rounded_rectangle((x0 + pad, y, x0 + w - pad, y + card_h), radius=int(16 * K * scale),
                           fill=CARD, outline=BORDER, width=1)
    ry = y + int(6 * K * scale)
    for icon_name, title, chevron in rows:
        ic = icon(icon_name, int(30 * K * scale))
        if ic:
            img.paste(ic, (x0 + w - pad - int(12 * K * scale) - ic.width, ry + int(16 * K * scale)), ic)
        draw.text((x0 + w - pad - int(50 * K * scale), ry + int(22 * K * scale)),
                  rtl(title), font=f_body, fill=TEXT, anchor="ra")
        if chevron:
            draw.text((x0 + pad + int(12 * K * scale), ry + int(18 * K * scale)),
                      "›", font=f_body, fill=DIM)
        ry += row_h
    y += card_h + int(18 * K * scale)

    # ورقة الأصول: أعمدة محسوبة من الشاشة
    draw.text((x0 + w - pad, y), rtl("PFP"), font=f_section, fill=GREEN, anchor="ra")
    draw.text((x0 + w - pad - int(60 * K * scale), y), "14", font=f_tiny, fill=DIM, anchor="ra")
    y += int(16 * K * scale)

    icons = ["ic_user", "coin_ac", "ic_room_minecraft", "ic_room_general", "ic_room_social",
             "ic_room_creator", "ic_set_appearance", "ic_set_email", "ic_set_help", "ic_set_report",
             "ic_set_notifications", "ic_set_language", "ic_room_lang_en", "ic_room_lang_ar", "ic_set_contact"]
    spacing = int(6 * K * scale)
    cw, ch = int(cell_w * K * scale), int(cell_h * K * scale)
    for index in range(cols * 3):
        col, row = index % cols, index // cols
        cx = x0 + w - pad - cw - col * (cw + spacing)
        cy = y + row * (ch + spacing)
        draw.rounded_rectangle((cx, cy, cx + cw, cy + ch), radius=int(12 * K * scale),
                               fill=CARD, outline=BORDER, width=1)
        ic = icon(icons[index % len(icons)], int(38 * K * scale))
        if ic:
            img.paste(ic, (cx + (cw - ic.width) // 2, cy + int(8 * K * scale)), ic)
        draw.text((cx + cw / 2, cy + ch - int(20 * K * scale)), f"PFP {index + 1}",
                  font=f_tiny, fill=MUTED, anchor="mm")

    draw.text((x0 + w / 2, y0 + h + int(16 * K)),
              rtl(f"{label} — {width_dp}×{height_dp} · نسبة {scale:.2f} · {cols} أعمدة · خلية {cell_w:.0f}dp"),
              font=ImageFont.truetype(FONT, 17), fill=MUTED, anchor="ma")


def main() -> int:
    margin, gap = 40, 46
    width = margin * 2 + gap * (len(PHONES) - 1) + int(sum(p[1] for p in PHONES) * K)
    height = 96 + int(max(p[2] for p in PHONES) * K) + 70
    img = Image.new("RGB", (width, height), "#05080D")
    draw = ImageDraw.Draw(img)
    draw.text((width / 2, 34), rtl("AddonStation — مقاسات الواجهة تنطبق على كل شاشة هاتف"),
              font=ImageFont.truetype(FONT, 26), fill=GREEN, anchor="ma")

    x = margin
    for label, w_dp, h_dp in PHONES:
        draw_phone(img, draw, x, 96, label, w_dp, h_dp)
        x += int(w_dp * K) + gap

    img.save(OUT)
    print(f"✓ {OUT} ({width}×{height})")
    for label, w_dp, h_dp in PHONES:
        cols, cell_w, scale = columns_for(w_dp, h_dp, 62, 86, 6, 28, 6)
        print(f"   {label:<12} {w_dp}×{h_dp} · نسبة {scale:.2f} · {cols} أعمدة · خلية {cell_w:.0f}dp")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
