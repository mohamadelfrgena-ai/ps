#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة تبويب **الأغلفة (البانرات)** في شاشة «تعديل الملف الشخصي»:

    • فقرتان من مجلدَي المالك: Nature وRandom
    • خلايا عريضة 16:9 (عمودان) بدل مربّعات الأفاتار
    • GIF يتحرّك في المعاينة والشبكة (شارة GIF على الخلية)
    • كل الأغلفة مقفولة 🔒 إلا مع اشتراك Epic 💎 أو Legendary 👑

المصدر: ui/profile/ProfileScreens.kt (EditProfileScreen + BannerPreviewCard)
        ui/components/Common.kt (BannerThumb)
الإخراج: banners_preview.png
التشغيل: python3 tools/preview_banners.py --zip banners/Banner.zip
"""
import argparse
import os
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "banners_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SCREEN, ELEV, ELEV2, BORDER, BORDER2 = (
    "#070A0E", "#0B1017", "#151D28", "#1B2532", "#26333F", "#334152")
TEXT, MUTED, DIM, GREEN, ONGREEN, GOLDL = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#F5C451")

W, H = 980, 1105
PANEL_W, PANEL_H = 420, 950

# صور حقيقية من ملف المالك تُستخدم داخل المعاينة
SAMPLE_NATURE = ["Summer1.png", "Summer2.png", "Summer3.png", "Summer5.png"]
SAMPLE_RANDOM = [
    ("89295-797032-vinland-saga.jpg", False),
    ("97427-337568-bmw.gif", True),
    ("75143-329583-kawaii-gothic.png", False),
    ("60529-527207-baby-girl.gif", True),
]


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def load_samples(zip_path):
    """يستخرج عيّنات حقيقية من ملف المالك إلى مجلد مؤقت ويرجع (nature, random)."""
    tmp = tempfile.mkdtemp(prefix="banner-preview-")
    nature, random_ = [], []
    with zipfile.ZipFile(zip_path) as z:
        names = z.namelist()
        for want in SAMPLE_NATURE:
            hit = next((n for n in names if n.endswith("/" + want)), None)
            if hit:
                out = os.path.join(tmp, want)
                with open(out, "wb") as fh:
                    fh.write(z.read(hit))
                nature.append((want.rsplit(".", 1)[0], out))
        for want, animated in SAMPLE_RANDOM:
            hit = next((n for n in names if n.endswith("/" + want)), None)
            if hit:
                out = os.path.join(tmp, want)
                with open(out, "wb") as fh:
                    fh.write(z.read(hit))
                label = want.rsplit(".", 1)[0]
                label = label.split("-")[-1].replace("-", " ")
                random_.append((label, out, animated))
    return nature, random_


def crop169(path, size):
    """يقصّ الصورة إلى 16:9 ويصغّرها (بدقة أعلى للمعاينة)."""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    target = size[0] / size[1]
    if w / h > target:
        nw = int(h * target)
        im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(w / target)
        im = im.crop((0, max(0, (h - nh) // 2), w, max(0, (h - nh) // 2) + nh))
    return im.resize((size[0] * 2, size[1] * 2), Image.LANCZOS).resize(size, Image.LANCZOS)


def padlock(d, cx, cy, s, color):
    """قفل مرسوم بالأشكال (لا خط إيموجي في بيئة المعاينة)."""
    d.rounded_rectangle((cx - s, cy - s * 0.1, cx + s, cy + s * 1.3),
                        radius=s * 0.28, fill=color)
    d.arc((cx - s * 0.62, cy - s * 1.25, cx + s * 0.62, cy + s * 0.35),
          180, 360, fill=color, width=max(2, int(s * 0.34)))


def gif_badge(d, x0, y0, text="GIF"):
    """شارة «GIF» فوق الأغلفة المتحرّكة."""
    f = font(9, True)
    tw = d.textlength(text, font=f)
    d.rounded_rectangle((x0 - tw - 10, y0, x0, y0 + 15), radius=4, fill="#000000")
    d.text((x0 - 5, y0 + 7.5), text, font=f, fill=GOLDL, anchor="rm")


def draw_panel(img, ox, oy, title, locked, nature, random_):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((ox, oy, ox + PANEL_W, oy + PANEL_H), radius=18,
                        fill=SCREEN, outline=BORDER2, width=1)
    d.text((ox + PANEL_W - 18, oy + 22), t(title), font=font(14, True),
           fill=GREEN if not locked else MUTED, anchor="rm")

    x0, x1 = ox + 16, ox + PANEL_W - 16
    y = oy + 44

    # شريط التبويبات: الأفاتار / الأداة · الأغلفة (مختار) · الأداة بجانب الاسم
    chips = [("الأفاتار", False), ("الأغلفة", True), ("الأداة", False)]
    cx = x1
    for label, sel in chips:
        f = font(11, True)
        tw = d.textlength(t(label), font=f)
        cw2 = tw + 22
        d.rounded_rectangle((cx - cw2, y, cx, y + 28), radius=9,
                            fill=GREEN if sel else ELEV, outline=BORDER if not sel else GREEN, width=1)
        d.text((cx - cw2 / 2, y + 14), t(label), font=f,
               fill=ONGREEN if sel else MUTED, anchor="mm")
        cx -= cw2 + 8

    y += 40
    # ---------------- المعاينة (الغلاف المختار حالياً) ----------------
    d.text((x1, y), t("معاينة الغلاف"), font=font(10), fill=MUTED, anchor="rm")
    y += 16
    card_w = x1 - x0
    card_h = int(card_w * 9 / 16)
    d.rounded_rectangle((x0, y, x0 + card_w, y + card_h), radius=14,
                        fill=ELEV2, outline=BORDER, width=1)
    if nature:
        d_image = crop169(nature[0][1], (card_w - 4, card_h - 4))
        img.paste(d_image, (x0 + 2, y + 2))
    if locked:
        scrim = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(scrim).rounded_rectangle(
            (x0, y, x0 + card_w, y + card_h), radius=14, fill=(11, 16, 23, 120))
        img.paste(Image.alpha_composite(img.convert("RGBA"), scrim).convert("RGB"), (0, 0))
        padlock(d, x0 + card_w // 2, y + card_h // 2 - 14, 13, TEXT)
        d.text((x0 + card_w // 2, y + card_h // 2 + 22),
               t("الأغلفة مقفولة — باشتراك فقط"), font=font(10, True), fill=TEXT, anchor="mm")
    else:
        d.text((x1 - 8, y + card_h - 20), t("إزالة الغلاف"), font=font(9),
               fill=TEXT, anchor="rm")
    y += card_h + 14
    if locked:
        d.text((x1, y), t("كلها مقفولة — تفتح مع Epic أو Legendary"),
               font=font(9), fill=DIM, anchor="rm")
        y += 18

    # ---------------- الفقرات ----------------
    cell_gap = 10
    cell_w = (card_w - cell_gap) // 2
    cell_h = int(cell_w * 9 / 16)

    def section(title_ar, title_latin, count, items, animated_flags=None):
        nonlocal y
        d.text((x1, y), f"{count}", font=font(9), fill=DIM, anchor="rm")
        tw = d.textlength(f"{count}", font=font(9))
        d.text((x1 - tw - 8, y), title_latin, font=font(9, True), fill=GREEN, anchor="rm")
        y += 16
        for row in range(2):
            for col in range(2):
                i = row * 2 + col
                if i >= len(items):
                    continue
                cxx = x0 + col * (cell_w + cell_gap)
                cyy = y
                sel = (not locked and row == 0 and col == 0)
                d.rounded_rectangle((cxx, cyy, cxx + cell_w, cyy + cell_h + 20), radius=11,
                                    fill=ELEV, outline=GREEN if sel else BORDER, width=1)
                thumb = crop169(items[i], (cell_w - 8, cell_h - 2))
                img.paste(thumb, (cxx + 4, cyy + 4))
                if animated_flags and animated_flags[i]:
                    gif_badge(d, cxx + cell_w - 8, cyy + 8)
                if locked:
                    scrim = Image.new("RGBA", img.size, (0, 0, 0, 0))
                    ImageDraw.Draw(scrim).rounded_rectangle(
                        (cxx + 4, cyy + 4, cxx + cell_w - 4, cyy + cell_h + 2),
                        radius=8, fill=(21, 29, 40, 130))
                    img.paste(Image.alpha_composite(img.convert("RGBA"), scrim).convert("RGB"), (0, 0))
                    padlock(d, cxx + cell_w // 2, cyy + cell_h // 2 - 2, 9, MUTED)
                name = os.path.splitext(os.path.basename(items[i]))[0]
                name = name.split("-")[-1].replace("_", " ").replace("-", " ")
                d.text((cxx + cell_w // 2, cyy + cell_h + 12), name[:18],
                       font=font(8), fill=GREEN if sel else MUTED, anchor="mm")
            y += cell_h + 20 + cell_gap
        y += 6

    section("nature", "NATURE", 13, [p for _, p in nature])
    section("random", "RANDOM", 27,
            [p for _, p, _ in random_],
            [a for _, _, a in random_])
    return y


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", default=os.path.join(ROOT, "banners", "Banner.zip"))
    args = ap.parse_args()

    nature, random_ = load_samples(args.zip)
    if not nature and not random_:
        print("✗ لم تُوجد صور العيّنة داخل الملف:", args.zip)
        return 2

    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((W // 2, 34), t("تبويب «الأغلفة» في تعديل الملف الشخصي — فقرتان · مقفولة 🔒 · GIF متحرّك"),
           font=font(16, True), fill=TEXT, anchor="mm")
    end_a = draw_panel(img, 40, 70, "١) بلا اشتراك — مقفولة", True, nature, random_)
    end_b = draw_panel(img, W - 40 - PANEL_W, 70, "٢) باشتراك Epic/Legendary — مفتوحة", False, nature, random_)
    print(f"   نهاية المحتوى: {max(end_a, end_b)}  (حدّ اللوحة: {70 + PANEL_H})")
    d.text((W // 2, H - 18),
           t("الصور من ملف المالك Banner.zip نفسها · القفل يُرسم كقفل حقيقي داخل التطبيق"),
           font=font(10), fill=DIM, anchor="mm")
    img.save(OUT)
    print(f"✓ {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
