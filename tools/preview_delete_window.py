#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة نافذة حذف الرسالة (٣ دقائق) — الواجهة + القاعدة.

يرسم حالتين من ورقة خيارات الرسالة في الشات:
  1) قبل انتهاء المدة  → خيار «حذف رسالتي» ظاهر ومعه ما تبقّى من الوقت.
  2) بعد ٣ دقائق       → الخيار مختفٍ تماماً، ويظهر سبب واضح.
كما يرسم الخط الزمني للنافذة، وناتج الدالة في القاعدة لكل حالة.
المصدر: ChatSheets.kt · ChatScreen.kt · ChatTimeRules.kt · 17_message_delete_window.sql
"""
import io
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "delete_window_preview.png")

BG, CARD, CARD2, TEXT, MUTED, GREEN, RED, DIM, BORDER, BLUE = (
    "#080C12", "#151E2A", "#1A2534", "#E8EEF5", "#8EA0B3", "#3DDC84", "#FF6B6B",
    "#5A6B7D", "#263240", "#7CB7FF")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# نصوص الواجهة الحقيقية من موارد اللغة (values-ar/strings.xml)
STR_DELETE_MINE = "حذف رسالتي"
STR_LEFT = "متبقٍ ٣ دقيقة للحذف"
STR_EXPIRED = "انتهت مدة الحذف: يمكن حذف الرسالة خلال ٣ دقائق فقط من إرسالها."
STR_REPLY, STR_COPY, STR_REPORT = "رد", "نسخ", "إبلاغ"
STR_DELETED_VIEW = '🚫 تم إخفاء هذه الرسالة'  # نص التطبيق الفعلي (chat_hidden_message)


def rtl(text: str) -> str:
    return get_display(arabic_reshaper.reshape(text))


def icon_clock(size):
    """يرسم أيقونة ساعة صغيرة."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, size - 2, size - 2), outline="#8EA0B3", width=2)
    d.line((size // 2, size // 2, size // 2, int(size * 0.28)), fill="#8EA0B3", width=2)
    d.line((size // 2, size // 2, int(size * 0.72), size // 2), fill="#8EA0B3", width=2)
    return im


def icon_trash(size, color="#FF6B6B"):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((int(size * 0.24), int(size * 0.3), int(size * 0.76), size - 2), outline=color, width=2)
    d.line((int(size * 0.14), int(size * 0.22), int(size * 0.86), int(size * 0.22)), fill=color, width=2)
    d.line((int(size * 0.38), int(size * 0.12), int(size * 0.62), int(size * 0.12)), fill=color, width=2)
    return im


def sheet(img, d, fonts, x, y, w, title, age_label, in_window):
    """يرسم ورقة الخيارات كما تظهر فعلاً."""
    f_title, f_row, f_small = fonts
    rows = [(STR_REPLY, TEXT), (STR_COPY, TEXT)]
    if in_window:
        rows.append((STR_DELETE_MINE, RED))
    rows.append((STR_REPORT, TEXT))
    h = 68 + len(rows) * 40 + (24 if in_window else 34) + 16

    d.rounded_rectangle((x, y, x + w, y + h), radius=20, fill=CARD2, outline=BORDER)
    d.text((x + w - 16, y + 10), rtl(title), font=f_title, fill=TEXT, anchor="ra")
    d.text((x + w - 16, y + 40), rtl(age_label), font=f_small, fill=DIM, anchor="ra")

    yy = y + 64
    for label, color in rows:
        d.rounded_rectangle((x + 10, yy, x + w - 10, yy + 34), radius=10, fill=CARD)
        d.text((x + w - 24, yy + 7), rtl(label), font=f_row, fill=color, anchor="ra")
        ic = icon_trash(16) if label == STR_DELETE_MINE else icon_clock(16)
        img.paste(ic, (x + 20, yy + 9), ic)
        yy += 40
        if label == STR_DELETE_MINE:
            d.text((x + w - 24, yy - 4), rtl(STR_LEFT), font=f_small, fill=GREEN, anchor="ra")
            yy += 24
    if not in_window:
        d.text((x + w - 24, yy + 2), rtl(STR_EXPIRED), font=f_small, fill=DIM, anchor="ra")
    return h


def main() -> int:
    W, H = 1240, 900
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_big = ImageFont.truetype(FONT_BOLD, 27)
    f_h = ImageFont.truetype(FONT_BOLD, 20)
    f_row = ImageFont.truetype(FONT_REG, 22)
    f_body = ImageFont.truetype(FONT_REG, 19)
    f_small = ImageFont.truetype(FONT_REG, 16)
    f_mono = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 13)

    d.rectangle((0, 0, W, 70), fill="#111823")
    d.text((W - 22, 20), rtl("الشات — حذف رسالتي خلال ٣ دقائق"), font=f_big, fill=TEXT, anchor="ra")

    # ---------- اللوحتان ----------
    d.text((W - 30, 92), rtl("① داخل المدة (مثال: بعد ٤٠ ثانية)"), font=f_h, fill=GREEN, anchor="ra")
    d.text((640, 92), rtl("② بعد انتهاء المدة (٣:٢٠)"), font=f_h, fill=RED, anchor="ra")

    sheet(img, d, (f_row, f_body, f_small), 630, 130, 580,
          "خيارات الرسالة", "أُرسلت قبل ٤٠ ثانية", True)
    sheet(img, d, (f_row, f_body, f_small), 40, 130, 580,
          "خيارات الرسالة", "أُرسلت قبل ٣ دقائق و٢٠ ثانية", False)

    # ---------- الخط الزمني ----------
    y = 430
    d.text((W - 30, y), rtl("خط النافذة الزمنية (chat_delete_window_seconds = 180)"), font=f_h, fill=TEXT, anchor="ra")
    y += 40
    x0, x1 = 90, W - 90
    mid = x0 + int((x1 - x0) * 0.42)
    d.rounded_rectangle((x0, y, mid, y + 34), radius=10, fill="#173D28", outline=GREEN)
    d.rounded_rectangle((mid + 6, y, x1, y + 34), radius=10, fill="#3A1D22", outline=RED)
    d.text(((x0 + mid) // 2, y + 8), rtl("يمكن الحذف · من 0:00 إلى 3:00"), font=f_body, fill=GREEN, anchor="ma")
    d.text(((mid + x1) // 2, y + 8), rtl("لا يمكن الحذف · بعد 3:00"), font=f_body, fill=RED, anchor="ma")
    d.text((mid + 3, y + 44), "3:00", font=f_small, fill=DIM, anchor="ma")

    # ---------- جدول ناتج القاعدة ----------
    y += 90
    d.text((W - 30, y), rtl("ما تعيده قاعدة البيانات (17_message_delete_window.sql)"), font=f_h, fill=BLUE, anchor="ra")
    y += 34
    table = [
        ("عمر الرسالة 0:45", '{"ok": true, "window_seconds": 180}', "status → deleted", GREEN),
        ("عمر الرسالة 3:20", '{"ok": false, "message": "delete_window_expired", "age_seconds": 200}', "بلا تغيير", RED),
        ("رسالة شخص آخر", '{"ok": false, "message": "forbidden"}', "بلا تغيير", RED),
        ("مشرف — chat_moderate_message", '{"ok": true}', "إخفاء وحذف بلا حدّ", BLUE),
    ]
    for label, res, note, color in table:
        d.rounded_rectangle((30, y, W - 30, y + 40), radius=10, fill=CARD, outline=BORDER)
        d.text((W - 46, y + 9), rtl(label), font=f_body, fill=TEXT, anchor="ra")
        d.text((W // 2 + 40, y + 12), res, font=f_mono, fill=color, anchor="ma")
        d.text((250, y + 10), rtl(note) if any("\u0600" <= c <= "\u06FF" for c in note) else note,
               font=f_small, fill=MUTED, anchor="ra")
        y += 46

    # ---------- صف الرسالة المحذوفة ----------
    y += 6
    d.text((W - 30, y), rtl("بعد الحذف: تظهر الرسالة في الشات هكذا للجميع"), font=f_h, fill=TEXT, anchor="ra")
    y += 36
    d.rounded_rectangle((30, y, W - 30, y + 52), radius=12, fill=CARD, outline=BORDER)
    d.ellipse((W - 78, y + 8, W - 46, y + 40), fill="#2B3A4D")
    d.text((W - 92, y + 14), rtl(STR_DELETED_VIEW), font=f_body, fill=MUTED, anchor="ra")
    d.text((60, y + 16), "status = deleted", font=f_mono, fill=DIM, anchor="la")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
