# -*- coding: utf-8 -*-
"""preview_settings_cta — settings account card v0.35:
   the green CTA button used to reach/cross the card's bottom edge (its rounded
   corners poked outside) and covered step 3; its label showed a tofu box for ✕.
   After: everything fits inside with padding + the ✕ is the pixel button icon.
   Saves settings_cta_preview.png (1920x1180)."""
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

img = Image.new('RGB', (1920, 1180), BG)
d = ImageDraw.Draw(img)

text(d, 1860, 46, 'بطاقة الحساب في الإعدادات — v0.35', 40, TEXT, True)
text(d, 1860, 96, 'الزر الأخضر كان يخرج خارج البطاقة ويغطي الخطوة 3 — صار كله بداخلها', 24, MUTED)

def draw_card(px, py, before):
    """1:1 account card: 836x684 at (px,py). before=True reproduces the bug."""
    d.rounded_rectangle([px, py, px + 836, py + 684], 30, fill=CARD, outline=BORDER2, width=2)
    # header: gold icon + title
    d.rounded_rectangle([px + 760 - 36, py + 32, px + 836 - 36, py + 108], 18, fill=(70, 56, 16))
    gx0, gy0 = px + 798, py + 70
    d.ellipse([gx0 - 12, gy0 - 20, gx0 + 12, gy0 + 4], fill=GOLD)
    d.rounded_rectangle([gx0 - 20, gy0 + 10, gx0 + 20, gy0 + 26], 10, fill=GOLD)
    text(d, px + 760 - 36 - 24, py + 70, 'الحساب', 32, TEXT, True)
    # avatar + guest
    ax, ay = px + 836 - 36 - 120, py + 150
    d.ellipse([ax, ay, ax + 120, ay + 120], fill=ELEV, outline=BORDER2, width=2)
    d.ellipse([ax + 42, ay + 26, ax + 78, ay + 62], fill=(120, 138, 158))
    d.rounded_rectangle([ax + 26, ay + 72, ax + 94, ay + 112], 20, fill=(120, 138, 158))
    text(d, ax - 32, ay + 34, 'زائر', 34, TEXT, True)
    text(d, ax - 32, ay + 88, 'سجّل الدخول بالرمز المخفّض واشترك وترتّبتك.', 19, MUTED)
    cy = 150   # content top offset inside card (real code: cy = panY + 150)
    steps = ['افتح تطبيق AddonStation على هاتفك', 'الإعدادات  >  ربط جهاز', 'أدخل هذا الرمز']
    if before:
        ys = [cy + 170 + i * 92 for i in range(3)]
        h = 78
    else:
        ys = [cy + 158 + i * 86 for i in range(3)]
        h = 72
    for i, s in enumerate(steps):
        yy = py + ys[i]
        d.rounded_rectangle([px + 36, yy, px + 800, yy + h], 20, fill=BG_ALT, outline=BORDER2)
        d.rounded_rectangle([px + 800 - 20 - 46, yy + (h - 46) // 2, px + 800 - 20, yy + (h - 46) // 2 + 46], 14, fill=(30, 120, 200))
        text(d, px + 800 - 20 - 23, yy + h // 2, str(i + 1), 26, (6, 24, 14), True, anchor='mm')
        text(d, px + 800 - 20 - 46 - 20, yy + h // 2, s, 24, (180, 200, 220), False)
    # green CTA
    by = py + (cy + 436 if before else cy + 418)
    bh = 96 if before else 88
    d.rounded_rectangle([px + 36, by, px + 800, by + bh], 24, fill=(22, 66, 42), outline=(43, 214, 123), width=3)
    bmid = by + bh // 2
    fa, fb = font(28, True), font(28, True)
    sa, sb = t('اضغط'), t('للحصول على رمز')
    wa, wb = fa.getlength(sa), fb.getlength(sb)
    iw, gp = 36, 12
    total = wa + gp + iw + gp + wb
    gx = px + 418 - total / 2          # card content center ≈ px+418
    d.text((gx + total, bmid), sa, font=fa, fill=GRN, anchor='rm')
    d.text((gx, bmid), sb, font=fb, fill=GRN, anchor='lm')
    ix = gx + total - wa - gp - iw / 2
    if before:
        d.rectangle([ix - 16, bmid - 16, ix + 16, bmid + 16], outline=(200, 200, 200), width=2)
    else:
        d.rounded_rectangle([ix - 18, bmid - 18, ix + 18, bmid + 18], 8, fill=(20, 26, 34))
        d.line([ix - 9, bmid - 9, ix + 9, bmid + 9], fill=(235, 240, 245), width=4)
        d.line([ix - 9, bmid + 9, ix + 9, bmid - 9], fill=(235, 240, 245), width=4)
    # overflow marker
    if before:
        d.line([px + 30, by + bh, px + 806, by + bh], fill=RED, width=3)
        d.line([px + 30, py + 684, px + 806, py + 684], fill=RED, width=3)

# BEFORE (right) / AFTER (left) — RTL reading
draw_card(1032, 150, True)
text(d, 1868, 132, 'قبل', 30, RED, True)
text(d, 1868, 872, 'الزر يلامس حافة البطاقة ويخرج بزواياه — والخطوة 3 متداخلة', 21, RED, True)

draw_card(52, 150, False)
text(d, 888, 132, 'بعد', 30, GREEN, True)
text(d, 888, 872, 'كل شيء داخل البطاقة ✓ + أيقونة ✕ الحقيقية بدل المربع الفارغ', 21, GREEN, True)

# zoom strip: the two button labels
zy = 930
d.rounded_rectangle([52, zy, 1868, zy + 170], 20, fill=BG_ALT, outline=BORDER2, width=2)
text(d, 1836, zy + 44, 'الزر قبل: مربع فارغ (توfu) مكان علامة ✕', 24, RED, True)

def zoom_label(cx0, before):
    fa, fb = font(24, True), font(24, True)
    sa, sb = t('اضغط'), t('للحصول على رمز')
    wa, wb = fa.getlength(sa), fb.getlength(sb)
    iw, gp = 32, 10
    total = wa + gp + iw + gp + wb
    gx = cx0 - total / 2
    bmid = zy + 114
    d.text((gx + total, bmid), sa, font=fa, fill=GRN, anchor='rm')
    d.text((gx, bmid), sb, font=fb, fill=GRN, anchor='lm')
    ix = gx + total - wa - gp - iw / 2
    if before:
        d.rectangle([ix - 14, bmid - 14, ix + 14, bmid + 14], outline=(200, 200, 200), width=2)
    else:
        d.rounded_rectangle([ix - 17, bmid - 17, ix + 17, bmid + 17], 8, fill=(20, 26, 34))
        d.line([ix - 8, bmid - 8, ix + 8, bmid + 8], fill=(235, 240, 245), width=4)
        d.line([ix - 8, bmid + 8, ix + 8, bmid - 8], fill=(235, 240, 245), width=4)

d.rounded_rectangle([1240, zy + 78, 1620, zy + 150], 18, fill=(22, 66, 42), outline=(43, 214, 123), width=3)
zoom_label(1430, True)
text(d, 1130, zy + 44, 'الزر بعد: أيقونة ✕ البكسلية (من نفس شيت الأزرار)', 24, GREEN, True)
d.rounded_rectangle([360, zy + 78, 740, zy + 150], 18, fill=(22, 66, 42), outline=(43, 214, 123), width=3)
zoom_label(550, False)

text(d, 1860, 1140, 'PS4 AddonStation v0.35 — by sliver fox', 20, MUTED)
img.save('settings_cta_preview.png')
print('saved settings_cta_preview.png')
