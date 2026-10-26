#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة جولة الأداء: أين كان الثِقَل/اللاق، وما صار بعد الإصلاح.

يُنتج `performance_preview.png` في الجذر: مقارنة زمن الإقلاع (قبل/بعد) +
جدول الإصلاحات الستّة التي كانت تسبب التقطيع — بلغة المالك (العربية).

الاستخدام:
    python3 tools/preview_performance.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "performance_preview.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

BG = "#080C12"
CARD = "#141C27"
EDGE = "#263240"
TXT = "#E8EEF5"
MUTED = "#8EA0B3"
DIM = "#5A6B7D"
RED = "#E5484D"
GREEN = "#2E9E5B"
BLUE = "#2F7FD8"
GOLD = "#D6A93A"


def rtl(t: str) -> str:
    return get_display(arabic_reshaper.reshape(t))


def font(size: int):
    return ImageFont.truetype(FONT_BOLD, size)


def main() -> int:
    W, H = 1480, 1080
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # ترويسة
    d.rectangle((0, 0, W, 78), fill="#111823")
    d.text((W - 26, 22), rtl("جولة إصلاح الأداء — التطبيق كان يطول ويتقطّع وثقيل، وهذا ما تغيّر"),
           font=font(25), fill=TXT, anchor="ra")
    d.text((26, 26), "v1.3 · performance", font=font(15), fill=DIM, anchor="la")

    # ============================ لوحة الإقلاع ============================
    top = 108
    d.rounded_rectangle((26, top, W - 26, top + 300), radius=16, fill=CARD, outline=EDGE, width=1)
    d.text((W - 46, top + 18), rtl("١) زمن فتح التطبيق — كان الانتظار مضاعفاً"),
           font=font(20), fill=GOLD, anchor="ra")

    # شريط زمني للقبل
    bar_x, bar_w = 300, W - 300 - 60
    y_before = top + 84
    y_after = top + 208

    def timeline(y: int, total_s: float, segments, label: str, label_color: str):
        d.text((bar_x - 18, y + 8), rtl(label), font=font(17), fill=label_color, anchor="ra")
        d.rounded_rectangle((bar_x, y, bar_x + bar_w, y + 34), radius=8, fill="#0E1620", outline=EDGE, width=1)
        x = bar_x
        for sec, color, name, sub in segments:
            wpx = int(bar_w * sec / total_s)
            if wpx <= 0:
                continue
            d.rounded_rectangle((x, y, x + wpx, y + 34), radius=8, fill=color)
            if wpx > 150:
                d.text((x + wpx / 2, y + 10), rtl(name), font=font(14), fill="#0B1015", anchor="ma")
            x += wpx
        # علامة كل ثانية
        for s in range(1, int(total_s) + 1):
            xx = bar_x + int(bar_w * s / total_s)
            d.line((xx, y + 34, xx, y + 40), fill=DIM)
            d.text((xx, y + 42), f"{s}s", font=font(11), fill=DIM, anchor="ma")

    timeline(
        y_before, 6.0,
        [
            (4.1, "#B4525A", "شاشة البداية — ٤.١ ثانية إلزامية", ""),
            (1.3, "#7A3F44", "بناء الشاشة يبدأ بعدها", ""),
        ],
        "قبل", RED,
    )
    timeline(
        y_after, 6.0,
        [
            (2.3, "#2E9E5B", "شاشة البداية — ٢.٣ ثانية أو نقرة للتخطّي", ""),

        ],
        "بعد", GREEN,
    )
    # ملاحظة البناء الموازي (بلا طبقة تُغطّي الشريط)
    d.text((bar_x + int(bar_w * 2.3 / 6.0) + 12, y_after + 8),
           rtl("الشاشات تُبنى خلفها من اللحظة ٠ (كانت لا تبدأ إلا بعد انتهائها)"),
           font=font(14), fill=GREEN, anchor="la")

    # ============================ جدول الإصلاحات ============================
    y0 = top + 328
    d.text((W - 46, y0), rtl("٢) ستّة أسباب كانت تُنتج اللاق والتقطيع — وأُصلحت كلها"),
           font=font(20), fill=GOLD, anchor="ra")

    rows = [
        ("قراءة الجلسة مشفّرة", "كل صورة/رسالة كانت تفكّ تشفير Keystore",
         "من الذاكرة · قراءة القرص مرّة واحدة", RED),
        ("بحث الأداة المجهّزة", "مسح ٤٩٢ أصلاً لكل رسالة في كل إطار",
         "فهرس جاهز O(1)", RED),
        ("صور القوائم", "توليف فرعي SubcomposeAsyncImage في كل صورة",
         "AsyncImage + مقاس مضبوط", RED),
        ("الصفحة الرئيسية", "كل الأقسام تُبنى مع أول إطار",
         "قائمة كسولة LazyColumn", RED),
        ("فاصل تاريخ الشات", "تاريخ يُحسب مرّتين لكل رسالة في كل إطار",
         "مفتاح يوم محسوب مرّة واحدة", RED),
        ("لوحة الملصقات", "١٩٣ ملصقاً تُصنَّف مع كل لمسة",
         "تصنيف محفوظ (remember)", RED),
    ]
    ry = y0 + 40
    for title, before, after, _c in rows:
        d.rounded_rectangle((26, ry, W - 26, ry + 74), radius=12, fill="#111823", outline=EDGE, width=1)
        d.text((W - 46, ry + 12), rtl(before), font=font(15), fill="#C98A8E", anchor="ra")
        d.text((W - 46, ry + 40), rtl(after), font=font(17), fill="#7FD8A0", anchor="ra")
        d.text((46, ry + 24), rtl(title), font=font(18), fill=TXT, anchor="la")
        ry += 82

    d.text((W - 46, ry + 4),
           rtl("حارس آلي (tools/check_jank_guard.py) يمنع عودة أي سبب من هذه الأسباب في أي بناء قادم."),
           font=font(15), fill=BLUE, anchor="ra")

    img = img.filter(ImageFilter.SMOOTH)
    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
