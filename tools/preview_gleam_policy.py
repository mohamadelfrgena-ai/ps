# -*- coding: utf-8 -*-
"""preview_gleam_policy — v0.35 gleam policy:
   rows / lists / catalog cards / settings tiles  -> gold frame ONLY (no gleam, static icons)
   action buttons (download CTA, sign-in dialog)  -> gold frame + the quiet gleam
   Saves gleam_policy_preview.png (1920x1120)."""
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

BG      = (11, 15, 20)
BG_ALT  = (19, 26, 36)
CARD    = (26, 35, 48)
ELEV    = (34, 45, 62)
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

img = Image.new('RGB', (1920, 1120), BG)
d = ImageDraw.Draw(img)

text(d, 1860, 44, 'سياسة اللمعة — v0.35', 38, TEXT, True)
text(d, 1860, 92, 'القوائم والأيقونات: إطار فقط وساكنة • اللمعة على أزرار التحميل ونافذة الدخول', 24, MUTED)

GOLD_HI = (255, 214, 90)
GOLD_LO = (184, 128, 8)

def frame(x, y, w, h, r):
    d.rounded_rectangle([x, y, x + w, y + h], r, outline=GOLD_HI, width=3)
    d.rounded_rectangle([x + 3, y + 3, x + w - 3, y + h - 3], max(1, r - 3), outline=GOLD_LO, width=1)

def gleam(x, y, w, h, r):
    d.rounded_rectangle([x - 1, y - 1, x + w + 1, y + h + 1], r + 1, outline=(255, 246, 214, 120), width=4)

def tile(x, y, s, acc, spin=False):
    d.rounded_rectangle([x, y, x + s, y + s], s * 28 // 100, fill=acc)
    cx, cy = x + s / 2, y + s / 2
    if spin:   # before: spinning globe (motion lines)
        d.ellipse([cx - s * 0.22, cy - s * 0.22, cx + s * 0.22, cy + s * 0.22], outline=(255, 255, 255), width=3)
        for k in range(3):
            d.arc([cx - s * 0.22 + k * 6, cy - s * 0.22, cx + s * 0.22, cy + s * 0.22 - k * 6], 40, 320, fill=(255, 255, 255), width=2)
        d.line([cx - s * 0.34, cy - s * 0.30, cx - s * 0.14, cy - s * 0.14], fill=(255, 255, 255, 120), width=2)
    else:      # after: static globe
        d.ellipse([cx - s * 0.22, cy - s * 0.22, cx + s * 0.22, cy + s * 0.22], outline=(255, 255, 255), width=3)
        d.line([cx - s * 0.22, cy, cx + s * 0.22, cy], fill=(255, 255, 255), width=2)
        d.line([cx, cy - s * 0.22, cx, cy + s * 0.22], fill=(255, 255, 255), width=2)

def row(x, y, w, before, label, val):
    d.rounded_rectangle([x, y, x + w, y + 104], 22, fill=CARD, outline=BORDER2, width=2)
    tile(x + w - 108, y + 14, 76, BLU, spin=before)
    text(d, x + w - 132, y + 52, label, 26, TEXT, True)
    text(d, x + 30, y + 52, val, 22, MUTED, anchor='lm')
    if before:
        frame(x, y, w, 104, 22)
        gleam(x, y, w, 104, 22)
        for i in range(3):
            xa = x + 200 + i * 52
            d.line([xa, y + 92, xa + 48, y + 12], fill=(255, 245, 210), width=11)
    else:
        frame(x, y, w, 104, 22)

# ---- rows section ----
text(d, 1860, 168, 'القوائم وصفوف الإعدادات', 28, TEXT, True)
row(950, 200, 900, True, 'لغة التطبيق', 'English')
text(d, 1850, 336, 'قبل: لمعة وخطوط متحركة والأيقونة تدور', 21, RED, True)
row(50, 200, 900, False, 'لغة التطبيق', 'English')
text(d, 950, 336, 'بعد: إطار ذهبي فقط — الأيقونة ساكنة', 21, GREEN, True)

# ---- buttons section ----
text(d, 1860, 420, 'أزرار التحميل ونافذة الدخول — اللمعة تبقى عليها', 28, TEXT, True)

def button(x, y, w, h, label, accent):
    d.rounded_rectangle([x, y, x + w, y + h], 20, fill=(22, 66, 42) if accent == 'g' else (40, 34, 16))
    frame(x, y, w, h, 20)
    gleam(x, y, w, h, 20)
    text(d, x + w / 2, y + h / 2, label, 28, GRN if accent == 'g' else GOLD, True, anchor='mm')

button(1150, 460, 700, 92, 'افتح مقابل 120 AC', 'g')
button(200, 460, 700, 92, 'إنشاء رمز الدخول', 'y')
text(d, 1860, 600, 'زر التحميل في صفحة التفاصيل ✓   •   زر نافذة الدخول ✓', 22, MUTED)

# ---- icon strip: settings icons before/after ----
sy = 650
d.rounded_rectangle([50, sy, 1870, sy + 330], 20, fill=BG_ALT, outline=BORDER2, width=2)
text(d, 1836, sy + 44, 'أيقونات الإعدادات: قبل تدور/تطفو/تتأرجح — بعد ساكنة', 26, TEXT, True)
accs = [(88, 156, 255), (176, 132, 255), (250, 196, 64), (52, 211, 125), (255, 96, 112)]
for i, acc in enumerate(accs):
    tile(1500 - i * 110, sy + 100, 84, acc, spin=True)
    tile(700 - i * 110, sy + 100, 84, acc, spin=False)
text(d, 1520, sy + 240, 'قبل (متحركة)', 22, RED, True)
text(d, 720, sy + 240, 'بعد (ساكنة)', 22, GREEN, True)
text(d, 1836, sy + 292, 'التوهج النابض أُزيل أيضاً — هالة ثابتة خفيفة عند التركيز', 20, MUTED)

text(d, 1860, 1080, 'PS4 AddonStation v0.35 — by sliver fox', 20, MUTED)
img.save('gleam_policy_preview.png')
print('saved gleam_policy_preview.png')
