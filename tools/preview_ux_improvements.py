#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة تحسينات تجربة المستخدم: لوحة «تعذّر التحميل» + شاشة بداية أسرع."""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
OUT = os.path.join(ROOT, "ux_improvements_preview.png")
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def s(loc, key):
    t = io.open(os.path.join(RES, loc, "strings.xml"), encoding="utf-8").read()
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(key), t)
    return m.group(1).replace("\\'", "'") if m else key


def main() -> int:
    W, H = 1120, 720
    img = Image.new("RGB", (W, H), "#080C12")
    d = ImageDraw.Draw(img)
    f_h = ImageFont.truetype(FB, 24)
    f_t = ImageFont.truetype(FB, 17)
    f_s = ImageFont.truetype(FR, 14)
    d.rectangle((0, 0, W, 58), fill="#111823")
    d.text((W - 20, 16), rtl("تحسينات تجربة المستخدم — جولة جديدة"), font=f_h, fill="#E8EEF5", anchor="ra")

    # (1) لوحة تعذّر التحميل
    x, y, w = 24, 80, W - 48
    d.rounded_rectangle((x, y, x + w, y + 250), radius=16, fill="#151E2A", outline="#263240")
    d.text((x + w - 18, y + 12), rtl("1) الرئيسية: لا تُخفي الخطأ بعد الآن"), font=f_t, fill="#6FB8FF", anchor="ra")
    # شريط علوي + محتوى باهت
    d.rounded_rectangle((x + 14, y + 46, x + w - 14, y + 96), radius=10, fill="#1A2330", outline="#263240")
    d.text((x + w - 30, y + 60), rtl("الرئيسية"), font=f_s, fill="#8EA0B3", anchor="ra")
    d.text((x + 30, y + 60), "1024 AC", font=f_s, fill="#E8EEF5", anchor="lm")
    cy = y + 116
    d.text((x + w // 2, cy), "⚠", font=ImageFont.truetype(FR, 30), fill="#FFC44D", anchor="mm")
    d.text((x + w // 2, cy + 34), rtl(s("values-ar", "msg_network_error") + "\n" + s("values-ar", "ux_load_failed")),
           font=f_s, fill="#8EA0B3", anchor="ma", align="center")
    d.rounded_rectangle((x + w // 2 - 90, cy + 74, x + w // 2 + 90, cy + 118), radius=13,
                        fill="#1A2330", outline="#FFC44D")
    d.text((x + w // 2, cy + 96), rtl(s("values-ar", "ux_retry")), font=f_t, fill="#E8EEF5", anchor="mm")

    # (2) تسلسل شاشة البداية
    y2 = y + 270
    d.rounded_rectangle((x, y2, x + w, y2 + 240), radius=16, fill="#151E2A", outline="#263240")
    d.text((x + w - 18, y2 + 12), rtl("2) شاشة البداية أسرع: 2.4 ثانية بدل 3.6"), font=f_t, fill="#6FB8FF", anchor="ra")
    # شريط زمني
    tx0, tx1 = x + 60, x + w - 60
    ty = y2 + 78
    d.line((tx0, ty, tx1, ty), fill="#263240", width=4)
    for ms, label, color in ((0, "الشعار", "#3DDC84"), (260, "DREAM IT.", "#6FB8FF"),
                             (820, "BUILD IT.", "#6FB8FF"), (1380, "CREATE IT.", "#6FB8FF"),
                             (1900, "العلامة", "#FFC44D"), (2350, "الخروج", "#FF6B6B")):
        px = tx0 + (tx1 - tx0) * (ms / 3600.0)   # 3.6 ثانية = كامل الشريط
        d.ellipse((px - 7, ty - 7, px + 7, ty + 7), fill=color)
        d.text((px, ty - 18), f"{ms}ms", font=ImageFont.truetype(FR, 11), fill=color, anchor="mb")
    d.text((tx0, ty + 26), rtl("الكلمات الثلاث تظهر متتالية كما هي — أقصر فقط"), font=f_s, fill="#5A6B7D", anchor="la")
    d.text((x + w - 18, y2 + 146), rtl("3) النصوص الجديدة بالثماني لغات (ux_retry · ux_load_failed)"),
           font=f_s, fill="#8EA0B3", anchor="ra")
    locs = [("EN", "values"), ("AR", "values-ar"), ("ES", "values-es"), ("FR", "values-fr"),
            ("PT", "values-pt"), ("RU", "values-ru"), ("TR", "values-tr"), ("ZH", "values-zh")]
    col_w = (w - 48) // 4
    for i, (tag, loc) in enumerate(locs):
        col, row = i % 4, i // 4
        bx = x + 24 + col * col_w
        by = y2 + 170 + row * 34
        d.rounded_rectangle((bx, by, bx + col_w - 10, by + 28), radius=8, fill="#101720", outline="#263240")
        d.text((bx + col_w - 20, by + 14), tag, font=ImageFont.truetype(FB, 12), fill="#5A6B7D", anchor="rm")
        d.text((bx + col_w - 48, by + 14), rtl(s(loc, "ux_retry")), font=ImageFont.truetype(FR, 13),
               fill="#E8EEF5", anchor="rm")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
