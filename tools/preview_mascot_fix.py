# -*- coding: utf-8 -*-
"""preview_mascot_fix — mascot fixes v0.35:
   1) the white flying creature is DELETED from the mascot icon (blue allay + coin stay)
   2) the mascot no longer crosses the sidebar rail (القائمة) in Arabic
   3) empty-state circle keeps ONE creature (the moving one removed)
   Before/after panels. Saves mascot_fix_preview.png (1920x1180)."""
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

BG      = (11, 15, 20)
BG_ALT  = (19, 26, 36)
BAR     = (26, 35, 48)
ELEV    = (34, 45, 62)
BORDER2 = (51, 65, 82)
TEXT    = (232, 238, 245)
MUTED   = (142, 160, 179)
GREEN   = (61, 220, 132)
GOLD    = (233, 196, 106)
RED     = (232, 90, 90)

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

# ---- the OLD mascot icon (two creatures) straight from git HEAD ----
old_png = subprocess.check_output(['git', 'show', 'HEAD:ps4-addon-test/assets/v73/icon_allay.png'])
import io
OLD = Image.open(io.BytesIO(old_png)).convert('RGBA')
NEW = Image.open('ps4-addon-test/assets/v73/icon_allay.png').convert('RGBA')
NEW_L = Image.open('ps4-addon-test/assets/v73/icon_allay_light.png').convert('RGBA')

img = Image.new('RGB', (1920, 1180), BG)
d = ImageDraw.Draw(img)

# ---- title ----
text(d, 1860, 46, 'تعديلات المخلوق — v0.35', 40, TEXT, True)
text(d, 1860, 96, 'كائن أزرق + عملة يبقيان • المخلوق الأبيض الطائر يُحذف • لا اختراق للقائمة', 24, MUTED)

def panel(px, py, title, ok):
    """880x430 header-corner panel at 1:1 scale of the real screen region x 1040..1920."""
    d.rectangle([px, py, px + 880, py + 430], fill=BG_ALT, outline=BORDER2, width=2)
    text(d, px + 846, py + 34, title, 28, GREEN if ok else RED, True)
    # sidebar rail (real x 1788..1920 -> panel 748..880)
    rx = px + 748
    d.rectangle([rx, py + 70, px + 880, py + 430], fill=BAR)
    for i, (gl, lb) in enumerate((('⌂', 'الرئيسية'), ('◈', 'المتجر'), ('⬡', 'الصفقات'),
                                  ('⚙', 'الإعدادات'), ('⏻', 'إغلاق'))):
        yy = py + 120 + i * 62
        d.rectangle([rx + 8, yy - 22, px + 872, yy + 22], fill=ELEV, outline=BORDER2)
        text(d, px + 866, yy, lb, 17, MUTED, False)
        text(d, rx + 30, yy, gl, 20, GOLD, True, anchor='mm')
    # rail edge marker (the line that must NOT be crossed)
    d.line([rx, py + 70, rx, py + 430], fill=RED if not ok else GREEN, width=3)
    # kicker «المتجر» (real kCR-246,96 -> panel 530)
    text(d, px + 530, py + 116, 'المتجر', 24, MUTED)
    # mascot: BEFORE center panel x 758 (crosses rail) / AFTER 653 (clear + 20px gap)
    return px, py, rx

def mascot(d, img_, cx, cy, size):
    ic = img_.resize((size, size), Image.LANCZOS)
    img.paste(ic, (int(cx - size / 2), int(cy - size / 2)), ic)

# ================= BEFORE panel (right, RTL reading) =================
px, py, rx = panel(1000, 140, 'قبل', False)
ccx, ccy, r = px + 300, py + 300, 70
d.ellipse([ccx - r - 10, ccy - r - 10, ccx + r + 10, ccy + r + 10], fill=(22, 30, 42), outline=BORDER2, width=2)
mascot(d, OLD, ccx, ccy, 116)
mascot(d, OLD, px + 758, py + 150, 150)
d.line([px + 758 - 75, py + 250, px + 758 + 75, py + 250], fill=RED, width=2)
text(d, px + 758, py + 276, 'يقطع الشريط الأحمر (القائمة)', 19, RED, True)
d.ellipse([ccx + 26, ccy - 64, ccx + 62, ccy - 28], outline=RED, width=3)
text(d, ccx + 90, ccy - 46, 'كائنان!', 19, RED, True)

# ================= AFTER panel (left) =================
px, py, rx = panel(40, 140, 'بعد', True)
ccx, ccy, r = px + 300, py + 300, 70
d.ellipse([ccx - r - 10, ccy - r - 10, ccx + r + 10, ccy + r + 10], fill=(22, 30, 42), outline=BORDER2, width=2)
mascot(d, NEW, ccx, ccy, 116)
mascot(d, NEW, px + 653, py + 150, 150)
d.line([px + 653 - 75, py + 250, px + 653 + 75, py + 250], fill=GREEN, width=2)
text(d, px + 653, py + 276, 'داخل المحتوى — لا يلامس القائمة', 19, GREEN, True)
text(d, ccx, ccy + r + 28, 'الكائن المتحرك حُذف — الأزرق محفوظ', 19, GREEN, True, anchor='mm')

# ================= icon strip (both variants) =================
sy = 640
d.rectangle([40, sy, 1880, sy + 420], fill=BG_ALT, outline=BORDER2, width=2)
text(d, 1846, sy + 40, 'الصورتان بعد التنظيف (يُبقيان في كل الأماكن الزرقاء)', 28, TEXT, True)
text(d, 1846, sy + 86, 'الشعار الأساسي (داكن) — الأزرق + العملة فقط', 22, MUTED)
mascot(d, NEW, 1600, sy + 240, 220)
text(d, 1200, sy + 86, 'النسخة الفاتحة — الكائن الأبيض الطائر حُذف', 22, MUTED)
mascot(d, NEW_L, 960, sy + 240, 220)
text(d, 620, sy + 86, 'قبل: كائنان (الأبيض الطائر كان السبب)', 22, MUTED)
mascot(d, OLD, 380, sy + 240, 220)
d.line([380 + 110, sy + 130, 380 + 110, sy + 350], fill=RED, width=4)
text(d, 380, sy + 372, 'هذا الكائن الأبيض حُذف نهائياً', 20, RED, True)

# footer
text(d, 1860, 1120, 'المخلوق الأزرق + العملة يبقيان في المتجر والصفقات ومساحة فارغة • الأبيض متحرك حُذف', 24, GOLD)
text(d, 40, 1120, 'PS4 AddonStation v0.35 — by sliver fox', 20, MUTED, False, anchor='lm')

img.save('mascot_fix_preview.png')
print('saved mascot_fix_preview.png')
