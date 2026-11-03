#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""استيراد أيقونات المالك من ملف «logo and thing» (مستودع vae) بلا أي رسم أو توليد.

المبدأ: قصّ + تحجيم بنِسَب محفوظة فقط — لا تمديد ولا تصغير غير متساوٍ، ولا إنشاء صور.
المصدر: 1.png (شعار الترحيب) · 2.png (عملة AC) · 3.png (ورقة أيقونات الإعدادات)
        4.png (صورة المستخدم) · 5.jpeg (غرفة السوشيال) · 6.jpeg (ورقة غرف الشات) · 7.png (English)
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont
import numpy as np

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/rarwork/out/logo and thing"
DST = sys.argv[2] if len(sys.argv) > 2 else "/tmp/icons_out"
os.makedirs(DST, exist_ok=True)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
import arabic_reshaper
from bidi.algorithm import get_display


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def alpha_bbox(im, thr=6):
    a = np.array(im.convert("RGBA"))[:, :, 3]
    ys, xs = np.where(a > thr)
    if len(ys) == 0:
        return (0, 0, im.width, im.height)
    return (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)


def square(im, box):
    """يقتطع مربّعاً حول المحتوى (بلا تمديد)."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    s = max(w, h)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    return im.crop((cx - s // 2, cy - s // 2, cx - s // 2 + s, cy - s // 2 + s))


def save_square(im, name, size, circular=False, pad=0.0):
    im = im.convert("RGBA")
    box = alpha_bbox(im)
    im = square(im, box)
    if pad:
        n = int(im.width * pad)
        canvas = Image.new("RGBA", (im.width + 2 * n, im.height + 2 * n), (0, 0, 0, 0))
        canvas.paste(im, (n, n))
        im = canvas
    im = im.resize((size, size), Image.LANCZOS)
    if circular:
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        mask = mask.resize((size, size), Image.LANCZOS)
        a = im.getchannel("A")
        im.putalpha(Image.composite(a, Image.new("L", (size, size), 0), mask))
    path = os.path.join(DST, name)
    im.save(path, "PNG", optimize=True)
    print(f"  ✓ {name:<26} {size}×{size}")
    return path


def disc_box(im, thr=222, verbose=False):
    """مربّع القطر حول القرص: أعرض صف = صف مركز القرص، وعرضه = القطر.

    هذا يناسب الأقراص الدائرية (بلا نص أسفلها) ولا يمسّ المحتوى مطلقاً.
    """
    g = np.array(im.convert("L")).astype(int)
    m = g < thr
    rw = m.sum(axis=1)
    if rw.max() == 0:
        return (0, 0, im.width, im.height)
    cy = int(np.argmax(rw))                 # أعرض صف = مركز القرص
    d = int(rw[cy])                          # القطر
    xs = np.where(m[cy])[0]
    cx = (int(xs.min()) + int(xs.max())) // 2
    half = d // 2
    box = (max(0, cx - half), max(0, cy - half),
           min(im.width, cx - half + d), min(im.height, cy - half + d))
    if verbose:
        print(f"      القطر {d} · المركز ({cx},{cy}) · المربّع {box} · الصورة {im.size}")
    return box


def main():
    print("=== 1) شعار شاشة الترحيب (1.png) ===")
    logo = Image.open(os.path.join(SRC, "1.png")).convert("RGBA")
    x0, y0, x1, y1 = alpha_bbox(logo, 10)
    print(f"  حدود الشعار داخل الصورة: ({x0},{y0})-({x1},{y1}) · "
          f"عرض {x1-x0} ارتفاع {y1-y0} · نسبة {(x1-x0)/(y1-y0):.2f}")
    cut = logo.crop((x0, y0, x1, y1))
    n = int(cut.height * 0.10)                      # هامش شفاف 10% من الارتفاع
    canvas = Image.new("RGBA", (cut.width + 2 * n, cut.height + 2 * n), (0, 0, 0, 0))
    canvas.paste(cut, (n, n))
    w = 1200                                       # العرض النهائي (الارتفاع يتغيّر تلقائياً ⇒ بلا تشويه)
    h = max(1, round(canvas.height * w / canvas.width))
    canvas.resize((w, h), Image.LANCZOS).save(os.path.join(DST, "splash_logo.png"), "PNG", optimize=True)
    print(f"  ✓ splash_logo.png {w}×{h} (نسبة محفوظة {w/h:.2f})")

    print("=== 2) عملة AC (2.png) ===")
    save_square(Image.open(os.path.join(SRC, "2.png")), "coin_ac.png", 384)

    print("=== 3) ورقة أيقونات الإعدادات (3.png) ===")
    sheet = Image.open(os.path.join(SRC, "3.png")).convert("RGBA")
    core = np.array(sheet)[:, :, 3] > 200

    def band(y0, y1, n):
        prof = core[y0:y1 + 1].sum(axis=0)
        bounds = [0]
        for k in range(1, n):
            lo = sheet.width * k // n
            hi = sheet.width * (k + 1) // n
            bounds.append(lo + int(np.argmin(prof[lo:hi + 1])))
        bounds.append(sheet.width)
        out = []
        for i in range(n):
            seg = core[y0:y1 + 1, bounds[i]:bounds[i + 1]]
            ys, xs = np.where(seg)
            if len(ys) == 0:
                continue
            out.append((bounds[i] + int(xs.min()), y0 + int(ys.min()),
                        bounds[i] + int(xs.max()), y0 + int(ys.max())))
        return out

    cells = band(130, 385, 4) + band(566, 786, 6)
    # الخلية الأخيرة فيها أيقونتان (كوكب + مظروف) ⇒ فصلها
    last = cells[-1]
    prof = core[last[1]:last[3] + 1, last[0]:last[2] + 1].sum(axis=0)
    cut_x = last[0] + int(np.argmin(prof))
    sub_a = core[last[1]:last[3] + 1, last[0]:cut_x]
    sub_b = core[last[1]:last[3] + 1, cut_x:last[2] + 1]
    def bbox_of(m, ox, oy):
        ys, xs = np.where(m)
        return (ox + int(xs.min()), oy + int(ys.min()), ox + int(xs.max()), oy + int(ys.max()))
    cells.pop()
    cells.append(bbox_of(sub_a, last[0], last[1]))
    cells.append(bbox_of(sub_b, cut_x, last[1]))
    settings_map = ["ic_set_notifications", "ic_set_appearance", "ic_set_help", "ic_set_report",
                    "ic_set_contact", "ic_set_version", "ic_set_terms", "ic_set_privacy",
                    "ic_set_language", "ic_set_email"]
    assert len(cells) == len(settings_map), (len(cells), len(settings_map))
    for name, b in zip(settings_map, cells):
        save_square(sheet.crop(b), name + ".png", 160)

    print("=== 4) الصورة الافتراضية للمستخدم (4.png) ===")
    save_square(Image.open(os.path.join(SRC, "4.png")), "ic_user.png", 256)

    print("=== 5) ورقة غرف الشات (6.jpeg) ===")
    rooms = Image.open(os.path.join(SRC, "6.jpeg")).convert("RGB")
    COLS = [(278, 573), (680, 971), (1075, 1366), (1473, 1764)]
    ROWS = [(169, 476), (612, 919), (1050, 1353)]
    cells6 = [(x0, y0, x1, y1) for (y0, y1) in ROWS for (x0, x1) in COLS]
    room_map = {1: "ic_room_general", 2: "ic_room_minecraft", 3: "ic_room_creator", 4: "ic_room_support",
                6: "ic_room_lang_ru", 7: "ic_room_lang_fr", 8: "ic_room_lang_pt",
                9: "ic_room_lang_zh", 11: "ic_room_lang_es"}
    for idx, name in room_map.items():
        x0, y0, x1, y1 = cells6[idx - 1]
        cell = rooms.crop((x0, y0, x1 + 1, y1 + 1))
        b = disc_box(cell, 224)
        save_square(cell.crop(b), name + ".png", 160, circular=True)

    print("=== 6) غرفة السوشيال (5.jpeg) وغرف English (7.png) ===")
    social = Image.open(os.path.join(SRC, "5.jpeg")).convert("RGB")
    b = disc_box(social, 226)
    save_square(social.crop(b), "ic_room_social.png", 160, circular=True)
    en = Image.open(os.path.join(SRC, "7.png")).convert("RGB")
    b = disc_box(en, 232)
    save_square(en.crop(b), "ic_room_lang_en.png", 160, circular=True)

    # لوحة معاينة
    names = sorted(f for f in os.listdir(DST) if f.endswith(".png"))
    cols = 5
    rows = (len(names) + cols - 1) // cols
    cell_px, pad = 150, 24
    W = cols * (cell_px + pad) + pad
    H = rows * (cell_px + pad + 26) + pad + 34
    sheet_out = Image.new("RGB", (W, H), "#0E1620")
    d = ImageDraw.Draw(sheet_out)
    f = ImageFont.truetype(FONT, 20)
    d.text((W - 14, 14), rtl("الأيقونات المستوردة من ملف المالك — قصّ فقط بلا تشويه"),
           font=f, fill="#6FB8FF", anchor="ra")
    for i, n in enumerate(names):
        p = os.path.join(DST, n)
        im = Image.open(p).convert("RGBA")
        k = (cell_px - 24) / max(im.size)
        im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
        col, row = i % cols, i // cols
        x = pad + col * (cell_px + pad)
        y = pad + 34 + row * (cell_px + pad + 26)
        d.rounded_rectangle((x, y, x + cell_px, y + cell_px), radius=14, fill="#141C27", outline="#263240")
        sheet_out.paste(im, (x + (cell_px - im.width) // 2, y + (cell_px - im.height) // 2), im)
        d.text((x + cell_px // 2, y + cell_px + 12), n.replace(".png", ""), font=f, fill="#8EA0B3", anchor="ma")
    sheet_out.save("/tmp/imported_icons_preview.png")
    print(f"✓ لوحة المعاينة: /tmp/imported_icons_preview.png ({W}×{H})")


if __name__ == "__main__":
    main()
