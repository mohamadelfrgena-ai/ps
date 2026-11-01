# -*- coding: utf-8 -*-
"""preview_color_ideas — لوحة اقتراحات ألوان التطبيق (8 مقترحات في صورة واحدة).
Saves color_ideas_preview.png (1920x1120)."""
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

TEXT = (232, 238, 245)
MUTED = (142, 160, 179)

def t(s):
    return get_display(arabic_reshaper.reshape(s))

F = {}
def font(size, bold=False):
    k = (size, bold)
    if k not in F:
        path = ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold
                else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
        F[k] = ImageFont.truetype(path, size)
    return F[k]

def text(d, x, y, s, size=22, color=TEXT, bold=False, anchor='rm'):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)

def latin(d, x, y, s, size=18, color=TEXT, bold=False, anchor='ma'):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)

# (letter, name, mood, bg, card, border, accent, accent_text_on, chipbg, hexes[4])
T = [
    ('أ', 'ذهبي ملكي', 'هوية الحجر الذهبي وعملة AC — فاخر', (20, 16, 7), (34, 27, 14), (74, 59, 26),
     (242, 193, 78), (26, 20, 8), (66, 51, 18), ('141007', '221B0E', 'F2C14E', 'C99A2E')),
    ('ب', 'سماوي جليدي', 'كونسول حديث ونقي', (8, 19, 26), (18, 34, 46), (34, 71, 92),
     (53, 214, 234), (6, 16, 22), (16, 56, 68), ('08131A', '12222E', '35D6EA', '1FA8BC')),
    ('ج', 'بنفسجي ملكي', 'فخم وعصري', (16, 10, 27), (30, 21, 48), (62, 46, 99),
     (167, 139, 250), (14, 8, 24), (48, 33, 82), ('100A1B', '1E1530', 'A78BFA', '7C5CE0')),
    ('د', 'أحمر قرمزي', 'طاقة الألعاب والقتال', (22, 9, 11), (42, 18, 21), (88, 36, 40),
     (240, 90, 96), (20, 6, 8), (92, 34, 40), ('16090B', '2A1215', 'F05A60', 'B8424A')),
    ('هـ', 'زمردي عميق', 'الأخضر الحالي لكن أعمق وأغنى', (7, 18, 14), (15, 36, 27), (30, 74, 54),
     (52, 211, 153), (6, 16, 12), (24, 68, 48), ('07120E', '0F241B', '34D399', '229B72')),
    ('و', 'نار برتقالية', 'دفء وحماس', (24, 13, 6), (44, 25, 12), (90, 52, 24),
     (251, 146, 60), (22, 11, 4), (96, 54, 22), ('180D06', '2C190C', 'FB923C', 'C2702C')),
    ('ز', 'وردي بنفسجي', 'عصري وجريء', (22, 8, 20), (42, 16, 38), (88, 36, 78),
     (240, 110, 190), (20, 6, 18), (92, 34, 80), ('160814', '2A1026', 'F06EBE', 'BC4E92')),
    ('ح', 'فضي رمادي', 'أناقة بسيطة بلا ألوان صاخبة', (16, 17, 20), (32, 34, 40), (64, 68, 78),
     (200, 208, 218), (14, 15, 18), (60, 64, 74), ('101114', '202228', 'C8D0DA', '8A929E')),
]

img = Image.new('RGB', (1920, 1210), (9, 12, 17))
d = ImageDraw.Draw(img)

text(d, 1880, 44, 'لوحة اقتراحات ألوان التطبيق — 8 مقترحات', 38, TEXT, True)
text(d, 1880, 92, 'نفس التصميم — اللون فقط. اختر حرفًا واحدًا (أ ب ج د هـ و ز ح) وأطبّقه.', 23, MUTED)

CW, CH, GAPX, GAPY, X0, Y0 = 436, 430, 24, 84, 1880 - 436, 130

for i, (letter, name, mood, bg, card, border, acc, onacc, chipbg, hexes) in enumerate(T):
    col, row = i % 4, i // 4
    x = X0 - col * (CW + GAPX)
    y = Y0 + row * (CH + GAPY)
    d.rounded_rectangle([x, y, x + CW, y + CH], 20, fill=bg, outline=border, width=2)

    # letter badge
    d.ellipse([x + CW - 74, y + 18, x + CW - 22, y + 70], fill=acc)
    latin(d, x + CW - 48, y + 44, letter, 26, onacc, True, anchor='mm')

    # name + mood
    text(d, x + CW - 92, y + 40, name, 26, TEXT, True)
    text(d, x + CW - 92, y + 74, mood, 15, MUTED)

    # swatches
    sw = 88
    for k, hx in enumerate(hexes):
        sx = x + CW - 24 - (k + 1) * (sw + 10) + 10
        c = tuple(int(hx[j:j+2], 16) for j in (0, 2, 4))
        d.rounded_rectangle([sx, y + 100, sx + sw, y + 100 + sw], 14, fill=c, outline=border, width=2)
        latin(d, sx + sw / 2, y + 100 + sw + 16, '#' + hx.upper(), 12, MUTED, anchor='ma')

    # mini UI strip: button + chip + card
    by = y + 232
    d.rounded_rectangle([x + 24, by, x + 250, by + 62], 18, fill=acc)
    text(d, x + 137, by + 31, 'عرض الإضافة', 20, onacc, True, anchor='mm')
    d.rounded_rectangle([x + CW - 130, by + 8, x + CW - 24, by + 54], 18, fill=chipbg, outline=acc, width=2)
    text(d, x + CW - 77, by + 31, 'مجاني', 17, acc, True, anchor='mm')

    # mini card
    cy = by + 86
    d.rounded_rectangle([x + 24, cy, x + CW - 24, cy + 88], 14, fill=card, outline=border, width=2)
    c = tuple(int(bg[j] + (acc[j] - bg[j]) * 0.22) for j in range(3))
    d.rounded_rectangle([x + 38, cy + 12, x + 110, cy + 76], 10, fill=c)
    latin(d, x + CW - 40, cy + 34, 'Owls by sliver fox', 14, TEXT, True, anchor='ra')
    d.rounded_rectangle([x + CW - 108, cy + 52, x + CW - 38, cy + 76], 10, outline=acc, width=1)
    text(d, x + CW - 73, cy + 64, 'جديد', 11, acc, True, anchor='mm')

    # label
    lab = ('★ التوصية — ' + name) if letter == 'أ' else name
    lab_col = (255, 214, 90) if letter == 'أ' else TEXT
    text(d, x + CW / 2, y + CH + 30, lab, 21, lab_col, True, anchor='mm')

text(d, 1880, 1165, 'الوضع الفاتح يتبع نفس النبرة (خلفية فاتحة + نفس لون التمييز). أخبرني بالحرف.', 22, MUTED)

img.save('color_ideas_preview.png')
print('saved')
