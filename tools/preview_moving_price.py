#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة «السعر المتحرك» في المتجر (ps4/src/screens.cpp — بطاقة المتجر + زر السعر).

الحركة المقترحة (0.4 ثانية، إطاران فقط):
  1) الأرقام تتدحرج رأسياً إلى قيمتها الجديدة (ghost/streak خفيف)
  2) لمعة ذهبية مائلة تمرّ على الشارة مرة واحدة
  3) ارتفاع/انضغاط خفيف (pop) + شارة ▼ التوفير

الإخراج: moving_price_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "moving_price_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, BAR, ELEV, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#334152"
TEXT, MUTED, DIM = "#E8EEF5", "#8EA0B3", "#5C6F82"
GREEN, RED = "#3DDC84", "#FF5A5A"
GOLD, GOLD_HI, GOLD_DK, ON_GOLD = "#E9C46A", "#F7E3A1", "#8A6D2F", "#241A05"

W, H = 960, 720
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def text(x, y, s, size=13, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)


def gleam(x, y, w, h, alpha=110):
    """لمعة مائلة عبر مستطيل (طبقة شفافة)."""
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.polygon([(x + w * 0.30, y), (x + w * 0.46, y),
                (x + w * 0.30, y + h), (x + w * 0.14, y + h)],
               fill=(255, 250, 225, alpha))
    od.polygon([(x + w * 0.52, y), (x + w * 0.60, y),
                (x + w * 0.44, y + h), (x + w * 0.36, y + h)],
               fill=(255, 250, 225, alpha // 2))
    img.paste(Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB"), (0, 0))


def price_chip(cx, cy, price, state="static"):
    """شارة السعر الذهبية — static | roll | settle"""
    w, h = 132, 54
    x, y = cx - w // 2, cy - h // 2
    fill = GOLD
    if state == "roll":
        fill = "#D9B45C"
    d.rounded_rectangle((x, y, x + w, y + h), radius=14, fill=fill,
                        outline=GOLD_HI, width=2)
    if state == "roll":
        # أرقام متدحرجة: شبحان رأسيان أعلى وأسفل + الرقم الحالي واضح
        for dy, col in ((-13, "#A8873F"), (13, "#A8873F")):
            latin(cx, cy + dy, "215", 22, col, True, "mm")
        latin(cx, cy, "238", 25, ON_GOLD, True, "mm")
        # خطوط سرعة رأسية
        for lx in (x + 16, x + w - 18):
            d.line((lx, y + 12, lx, y + h - 12), fill="#C9A651", width=2)
    else:
        latin(cx, cy, price, 25, ON_GOLD, True, "mm")
    if state == "settle":
        gleam(x, y, w, h, 120)
        # pop arrows
        for ax in (x - 16, x + w + 16):
            d.polygon([(ax, y + 12), (ax - 6, y + 24), (ax + 6, y + 24)],
                      fill=GOLD_HI)
    return x, y, w, h


def store_card(y0, title, price, anim=False):
    """بطاقة متجر RTL مصغّرة (الغلاف + الاسم + شارة السعر + زر الفتح)."""
    x0, x1 = 48, W - 48
    d.rounded_rectangle((x0, y0, x1, y0 + 128), radius=18, fill=BAR, outline=BORDER2, width=1)
    # غلاف
    d.rounded_rectangle((x1 - 128, y0 + 16, x1 - 16, y0 + 112), radius=12, fill="#20304A")
    d.rounded_rectangle((x1 - 118, y0 + 26, x1 - 26, y0 + 102), radius=9, fill="#2C4366")
    text(x1 - 72, y0 + 64, "◈", 26, "#5E7EA8", anchor="mm")
    # الاسم (F77-style bold) + by sliver fox
    text(x1 - 148, y0 + 42, title, 20, TEXT, True, "rm")
    latin(x1 - 148, y0 + 72, "by sliver fox", 13, MUTED, False, "rm")
    text(x1 - 148, y0 + 100, "تحديث الأسعار · واجهة المتجر", 12, DIM, anchor="rm")
    # شارة السعر + زر «افتح مقابل»
    if anim:
        price_chip(x0 + 220, y0 + 52, price, "roll")
        text(x0 + 322, y0 + 52, "السعر يتغيّر", 12, GOLD_HI, anchor="lm")
    else:
        price_chip(x0 + 220, y0 + 52, price, "static")
    bw, bh = 190, 52
    bx, by = x0 + 22, y0 + 74
    if anim:
        d.rounded_rectangle((bx, by - 3, bx + bw, by + bh - 3), radius=14, fill="#DDB85F", outline=GOLD_HI, width=2)
    else:
        d.rounded_rectangle((bx, by, bx + bw, by + bh), radius=14, fill=GOLD, outline=GOLD_HI, width=2)
    latin(bx + bw // 2, by + bh // 2 - (2 if anim else 0),
          "250 AC" if not anim else "180 AC", 20, ON_GOLD, True, "mm")
    text(bx + bw // 2, by + bh + 16, "اضغط ✕ للفتح" if not anim else "الزر يتحدث مع السعر", 10, DIM, anchor="mm")


# ---------- الترويسة ----------
text(W - 44, 28, "السعر المتحرك — عرض الأسعار في المتجر", 21, GOLD, True, "rm")
text(W - 44, 58, "الأرقام تتدحرج · لمعة ذهبية · ارتفاع خفيف", 15, "#B8C7D6", anchor="rm")
text(W - 44, 84, "حركة 0.4 ثانية — بلا تأثير على السلاسة", 15, "#B8C7D6", anchor="rm")
d.line((44, 106, W - 44, 106), fill=ELEV)

# ---------- قبل / بعد ----------
text(W - 44, 132, "قبل — سعر ثابت بلا حركة", 15, RED, True, "rm")
store_card(150, "دياموند باك", "250 AC", anim=False)

text(W - 44, 308, "بعد — السعر متحرك (اللحظة التي يتغيّر فيها)", 15, GREEN, True, "rm")
store_card(326, "دياموند باك", "238 AC", anim=True)

# ---------- خطوات الحركة ----------
d.line((44, 470, W - 44, 470), fill=ELEV)
text(W - 44, 498, "خطوات الحركة خلال 0.4 ثانية", 15, GOLD, True, "rm")

steps = (
    ("البداية", "250", "static", "يُضغط الزر"),
    ("التدحرج", "238", "roll", "الأرقام تدور رأسياً"),
    ("الاستقرار", "180", "settle", "اللمعة + ارتفاع خفيف"),
)
for i, (label, price, state, cap) in enumerate(steps):
    cx = W - 190 - i * 280
    price_chip(cx, 572, price, state)
    text(cx, 622, label, 13, TEXT, True, "mm")
    text(cx, 646, cap, 11, MUTED, anchor="mm")
    if i < 2:
        ax = cx - 140
        d.line((ax - 18, 572, ax + 18, 572), fill=DIM, width=2)
        d.polygon([(ax - 26, 572), (ax - 12, 564), (ax - 12, 580)], fill=DIM)

# ملاحظة أداء
d.rounded_rectangle((44, 672, W - 44, 706), radius=12, fill=ELEV, outline=BORDER2, width=1)
text(W - 60, 689, "الأداء: إطاران فقط على خيط الرسم · لا اهتزاز · لا تغيير في منطق الأسعار (effective_item_ac_price)", 12, GREEN, anchor="rm")

img.save(OUT)
print("saved", OUT, img.size)
