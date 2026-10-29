# -*- coding: utf-8 -*-
"""preview_search_bar — search field v0.35: NO focus frame and NO gleam on the
   search bar (the gleam belongs to buttons only). The caret marks focus.
   Saves search_bar_preview.png (1920x760)."""
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

BG      = (11, 15, 20)
BG_ALT  = (19, 26, 36)
BORDER2 = (51, 65, 82)
TEXT    = (232, 238, 245)
MUTED   = (142, 160, 179)
GREEN   = (61, 220, 132)
GOLD    = (233, 196, 106)
RED     = (232, 90, 90)
BLU     = (88, 156, 255)
GRN     = (43, 214, 123)

def t(s):
    return get_display(arabic_reshaper.reshape(s))

F = {}

def font(size, bold=False):
    key = (size, bold)
    if key not in F:
        path = ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold
                else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
        F[key] = ImageFont.truetype(path, size)
    return F[key]

def text(d, x, y, s, size=22, color=TEXT, bold=False, anchor='rm'):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)

img = Image.new('RGB', (1920, 760), BG)
d = ImageDraw.Draw(img)

text(d, 1860, 44, 'شريط البحث — v0.35', 38, TEXT, True)
text(d, 1860, 92, 'بدون إطار تحديد ولا لمعة — اللمعة على الأزرار فقط', 24, MUTED)

FIELD = (8, 10, 13)

def field(y, before):
    x0, x1, h = 420, 1876, 84
    d.rounded_rectangle([x0, y, x1, y + h], 26, fill=FIELD, outline=BORDER2, width=2)
    # search glyph + placeholder at the RTL start edge (right)
    ix = x1 - 52
    d.ellipse([ix - 12, y + h / 2 - 12, ix + 12, y + h / 2 + 12], outline=BLU, width=3)
    d.line([ix + 8, y + h / 2 + 8, ix + 18, y + h / 2 + 18], fill=BLU, width=3)
    text(d, ix - 34, y + h / 2, 'اضغط لكتابة اسم أو صانع أو تصنيف', 25, MUTED)
    if before:
        # gold rim + gleam (focusRing)
        d.rounded_rectangle([x0 + 8, y + 8, x1 - 8, y + h - 8], 20, outline=GOLD, width=3)
        d.rounded_rectangle([x0, y, x1, y + h], 26, outline=(250, 220, 140), width=2)
        for i in range(4):
            xa = x0 + 260 + i * 46
            d.line([xa, y + h - 16, xa + 44, y + 16], fill=(255, 245, 210, 60), width=10)
    else:
        # caret only
        cx0 = x1 - 52 - 34 - 30
        d.rectangle([cx0, y + 22, cx0 + 4, y + 62], fill=BLU)

field(170, True)
text(d, 392, 212, 'قبل', 30, RED, True)
text(d, 392, 292, 'إطار ذهبي ولمعة', 21, RED, True)

field(420, False)
text(d, 392, 462, 'بعد', 30, GREEN, True)
text(d, 392, 542, 'مؤشر الكتابة فقط', 21, GREEN, True)

# button keeps the gleam
by = 610
d.rounded_rectangle([1300, by, 1876, by + 92], 22, fill=BG_ALT, outline=BORDER2, width=2)
d.rounded_rectangle([1308, by + 8, 1868, by + 84], 16, outline=GOLD, width=3)
for i in range(3):
    xa = 1360 + i * 46
    d.line([xa, by + 76, xa + 44, by + 16], fill=(255, 245, 210), width=10)
text(d, 1588, by + 46, 'الزر يبقى بإطاره ولمعته ✓', 24, GOLD, True, anchor='mm')
text(d, 1260, by + 46, 'القاعدة: اللمعة على الأزرار فقط', 22, MUTED, True)

text(d, 1860, 726, 'PS4 AddonStation v0.35 — by sliver fox', 20, MUTED)
img.save('search_bar_preview.png')
print('saved search_bar_preview.png')
