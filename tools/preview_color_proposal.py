# -*- coding: utf-8 -*-
"""preview_color_proposal — اقتراح تغيير ألوان التطبيق.
الحالي (أسود+أخضر) مقابل ثلاثة مقترحات: ذهبي ملكي ★ / سماوي جليدي / بنفسجي ملكي.
Saves color_proposal_preview.png (1920x1240)."""
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

# ---- four themes: (key, label, bg, card, border, accent, accent2, text, muted, chipbg) ----
THEMES = [
    ('now', 'الحالي (للمرجع)', (11, 15, 20), (26, 35, 48), (51, 65, 82),
     (61, 220, 132), (43, 160, 96), (232, 238, 245), (142, 160, 179), (22, 66, 42)),
    ('gold', 'أ — ذهبي ملكي ★ التوصية', (20, 16, 7), (34, 27, 14), (74, 59, 26),
     (242, 193, 78), (201, 154, 46), (246, 239, 223), (179, 162, 127), (66, 51, 18)),
    ('cyan', 'ب — سماوي جليدي', (8, 19, 26), (18, 34, 46), (34, 71, 92),
     (53, 214, 234), (31, 168, 188), (227, 244, 248), (134, 169, 181), (16, 56, 68)),
    ('violet', 'ج — بنفسجي ملكي', (16, 10, 27), (30, 21, 48), (62, 46, 99),
     (167, 139, 250), (124, 92, 224), (239, 234, 251), (162, 148, 196), (48, 33, 82)),
]

img = Image.new('RGB', (1920, 1240), (9, 12, 17))
d = ImageDraw.Draw(img)

text(d, 1880, 46, 'اقتراح تغيير ألوان التطبيق', 40, TEXT, True)
text(d, 1880, 96, 'اختر بضغطة واحدة: أ / ب / ج — نفس التصميم تماماً، الفرق في الألوان فقط', 24, MUTED)

PW, GAP, X0, Y0, PH = 436, 24, 1880 - 436, 150, 960

for i, (key, label, bg, card, border, acc, acc2, tx, mu, chipbg) in enumerate(THEMES):
    x = X0 - i * (PW + GAP)
    y = Y0
    # panel base
    d.rounded_rectangle([x, y, x + PW, y + PH], 22, fill=bg, outline=border, width=2)

    # --- header strip ---
    text(d, x + PW - 22, y + 40, 'المتجر', 26, acc, True)
    d.rounded_rectangle([x + 22, y + 22, x + 128, y + 58], 18, fill=card, outline=border, width=2)
    d.ellipse([x + 34, y + 30, x + 58, y + 54], fill=acc2)
    latin(d, x + 96, y + 40, 'AC 0', 16, tx, True)

    # --- hero band ---
    hx, hy, hw, hh = x + 22, y + 84, PW - 44, 300
    d.rounded_rectangle([hx, hy, hx + hw, hy + hh], 18, fill=card, outline=border, width=2)
    # gradient-ish hero wash (two bands)
    for k in range(3):
        f = 0.16 - k * 0.05
        c = tuple(int(bg[j] + (acc2[j] - bg[j]) * f) for j in range(3))
        d.rounded_rectangle([hx + 10 + k * 6, hy + 10 + k * 6, hx + hw - 10 - k * 6, hy + 150], 14, fill=c)
    latin(d, hx + hw / 2, hy + 70, 'Sulfur Cars by sliver fox', 22, tx, True)
    text(d, hx + hw - 24, hy + 122, 'سيارات قابلة للقيادة — by sliver fox', 17, mu)

    # price chip + CTA
    d.rounded_rectangle([hx + hw - 118, hy + 178, hx + hw - 20, hy + 216], 18, fill=chipbg, outline=acc, width=2)
    text(d, hx + hw - 69, hy + 197, 'مجاني', 17, acc, True, anchor='mm')
    d.rounded_rectangle([hx + 20, hy + 170, hx + hw - 136, hy + 234], 20, fill=acc)
    text(d, hx + 20 + (hw - 156) / 2, hy + 202, 'عرض الإضافة', 22, bg, True, anchor='mm')
    # gold CTA frame echo (v0.35 policy)
    d.rounded_rectangle([hx + 16, hy + 166, hx + hw - 132, hy + 238], 22, outline=(255, 214, 90) if key == 'now' else acc, width=3)
    # downloads mini row
    text(d, hx + hw - 24, hy + 268, '6 تنزيلات', 16, mu)

    # --- 2x2 cards ---
    cw, ch = (hw - 3 * 12) // 2, 118
    names = [('Owls', 'by sliver fox'), ('Gravestones', 'by sliver fox'), ('Spark Pets', 'by sliver fox'), ('Weapons', 'by sliver fox')]
    for n in range(4):
        col, row = n % 2, n // 2
        cx0 = hx + 12 + col * (cw + 12)
        cy0 = hy + hh + 18 + row * (ch + 12)
        d.rounded_rectangle([cx0, cy0, cx0 + cw, cy0 + ch], 14, fill=card, outline=border, width=2)
        # cover placeholder block (accent wash)
        c = tuple(int(bg[j] + (acc2[j] - bg[j]) * 0.22) for j in range(3))
        d.rounded_rectangle([cx0 + 10, cy0 + 10, cx0 + 74, cy0 + ch - 10], 10, fill=c)
        # new chip
        d.rounded_rectangle([cx0 + cw - 66, cy0 + 10, cx0 + cw - 12, cy0 + 36], 12, fill=chipbg, outline=acc, width=1)
        text(d, cx0 + cw - 39, cy0 + 23, 'جديد', 12, acc, True, anchor='mm')
        latin(d, cx0 + cw - 16, cy0 + 56, names[n][0], 14, tx, True, anchor='ra')
        latin(d, cx0 + cw - 16, cy0 + 78, names[n][1], 11, mu, anchor='ra')
        d.rounded_rectangle([cx0 + cw - 78, cy0 + ch - 36, cx0 + cw - 12, cy0 + ch - 10], 12, outline=acc, width=1)
        text(d, cx0 + cw - 45, cy0 + ch - 23, 'مجاني', 11, acc, True, anchor='mm')

    # --- bottom pill ---
    d.rounded_rectangle([hx + 20, y + PH - 66, hx + hw - 20, y + PH - 22], 16, fill=card, outline=border, width=2)
    text(d, hx + hw - 40, y + PH - 44, 'ترتيب: الأحدث', 16, acc, anchor='mm')

    # panel label (annotations last)
    lab_col = (255, 214, 90) if key == 'gold' else TEXT
    text(d, x + PW / 2, y + PH + 40, label, 24, lab_col, True, anchor='mm')

text(d, 1880, 1180, 'التصميم والتخطيط واللمعة كما هو — يتغيّر اللون الأساسي ودرجة الخلفية فقط. أخبرني بالحرف (أ / ب / ج) وأطبّقه.', 23, MUTED)

img.save('color_proposal_preview.png')
print('saved')
