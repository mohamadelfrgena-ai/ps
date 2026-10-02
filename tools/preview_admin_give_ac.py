#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة «منح AC لمستخدم» في لوحة الإدارة.

ترسم شاشة AC الجديدة: اختيار مستخدم · أي مبلغ · سبب اختياري · منح/خصم ·
نتيجة (الرصيد الجديد) · سجل آخر العمليات — ثم نافذة التأكيد.
المصدر: AdminScreens.kt (AdminAcScreen) · AdminRepository.acAdjust
        supabase/02_functions_core.sql (ac_admin_adjust)
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "admin_give_ac_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, RED, GOLD, BLUE = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#FF5A5A", "#FFD479", "#6FB8FF")

W, H, TOP = 420, 880, 150
img = None
d = None


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def arrow(x1, y1, x2, y2, color, w=4):
    d.line([(x1, y1), (x2, y2)], fill=color, width=w)
    a = math.atan2(y2 - y1, x2 - x1)
    for s in (0.55, -0.55):
        d.line([(x2, y2), (x2 - 14 * math.cos(a - s), y2 - 14 * math.sin(a - s))], fill=color, width=w)


def note(x, y, color, text, anchor="mm", size=18):
    d.text((x, y), rtl(text), font=font(size, True), fill=color, anchor=anchor)


def button(box, label, fill=None, text_color=TEXT, outline=None, bold=True, size=14):
    rounded(box, 12, fill=fill, outline=outline)
    d.text(((box[0] + box[2]) // 2, (box[1] + box[3]) // 2), rtl(label),
           font=font(size, bold), fill=text_color, anchor="mm")


def field(box, label, value, icon=None):
    rounded(box, 9, outline=BORDER2, width=2)
    d.text((box[0] + 10, box[1] + 11), rtl(label), font=font(11), fill=MUTED, anchor="lm")
    if icon:
        rounded((box[0] + 8, box[1] + 20, box[0] + 30, box[1] + 42), 6, fill=BLUE)
        d.ellipse((box[0] + 15, box[1] + 27, box[0] + 23, box[1] + 35), fill="#FFFFFF")
        d.text((box[0] + 38, box[1] + 32), value, font=font(14), fill=TEXT, anchor="lm")
    else:
        d.text((box[0] + 10, box[1] + 32), value, font=font(14), fill=TEXT, anchor="lm")


def main_screen(x0):
    x1 = x0 + W
    rounded((x0, TOP, x1, TOP + H), 26, fill=BG, outline=BORDER2, width=2)
    d.rectangle((x0 + 3, TOP + 3, x1 - 3, TOP + 46), fill=BG)
    d.text((x0 + 18, TOP + 22), "2:37", font=font(12), fill=TEXT, anchor="lm")
    d.text((x1 - 18, TOP + 22), "▮▮", font=font(11), fill=TEXT, anchor="rm")

    y = TOP + 48
    d.text((x0 + 18, y + 18), "‹", font=font(22, True), fill=TEXT, anchor="lm")
    d.text((x0 + 40, y + 18), rtl("محفظة AC"), font=font(18, True), fill=TEXT, anchor="lm")
    y += 46

    pts = {}
    # بحث
    field((x0 + 14, y, x1 - 14, y + 54), rtl("ابحث عن مستخدم"), "")
    pts["search"] = y
    y += 66

    # عنوان + بطاقة المنح
    d.text((x1 - 16, y), rtl("منح AC لمستخدم"), font=font(15, True), fill=TEXT, anchor="rt")
    y += 26
    card = (x0 + 14, y, x1 - 14, y + 330)
    rounded(card, 16, fill=SURFACE, outline=BORDER)
    y += 12
    field((x0 + 26, y, x1 - 26, y + 54), rtl("المبلغ (أي رقم)"), "1000")
    pts["amount"] = y + 30
    y += 60
    d.text((x1 - 28, y + 14), rtl("مبالغ سريعة"), font=font(11), fill=MUTED, anchor="rm")
    for i, v in enumerate(["100", "500", "1000", "5000"]):
        bx0 = x0 + 30 + i * ((W - 68) // 4)
        bx1 = bx0 + (W - 80) // 4
        button((bx0, y, bx1, y + 34), v, fill=ELEV2, outline=BORDER2, size=13)
    y += 44
    field((x0 + 26, y, x1 - 26, y + 54), rtl("السبب (يظهر للمستخدم)"), "مكافأة تالفة")
    pts["note"] = y + 27
    y += 62
    d.text((x1 - 28, y + 8), rtl("Mrforest · 1000 AC"), font=font(12, True), fill=GREEN, anchor="rm")
    pts["summary"] = y + 8
    y += 22
    button((x0 + 26, y, x0 + 26 + (W - 62) // 2, y + 44), "منح", fill=GREEN, text_color=ONGREEN)
    button((x0 + 36 + (W - 62) // 2, y, x1 - 26, y + 44), "خصم", outline=BORDER2)
    pts["buttons"] = y + 22
    y += 54
    button((x0 + 26, y, x1 - 26, y + 42), "منح لكل المستخدمين", outline=BORDER2, size=13)
    pts["all"] = y + 21
    y = card[3] + 18

    # النتيجة
    d.text((x1 - 16, y), rtl("+1000 AC · الرصيد 12400"), font=font(12, True), fill=GREEN, anchor="rt")
    pts["result"] = y
    y += 30

    # قائمة المستخدمين
    d.text((x1 - 16, y), rtl("المستخدمون"), font=font(15, True), fill=TEXT, anchor="rt")
    y += 24
    for name, sel in (("Mrforest", True), ("sara_2026", False), ("khaled_dev", False)):
        rounded((x0 + 14, y, x1 - 14, y + 40), 11,
                fill=ELEV2 if sel else ELEV, outline=GREEN if sel else BORDER, width=2 if sel else 1)
        d.text((x1 - 26, y + 20), name, font=font(13, sel), fill=TEXT, anchor="rm")
        button((x0 + 52, y + 5, x0 + 80, y + 35), "+", fill=ELEV2, outline=BORDER2, size=15)
        button((x0 + 22, y + 5, x0 + 48, y + 35), "−", fill=ELEV2, outline=BORDER2, size=15)
        y += 46
    y += 10

    # سجل العمليات
    d.text((x1 - 16, y), rtl("آخر عمليات AC"), font=font(15, True), fill=TEXT, anchor="rt")
    y += 24
    for who, val, bal, when in (("sara_2026", "+500", "8200", "12:40"),
                                ("Mrforest", "+1000", "12400", "12:38"),
                                ("khaled_dev", "−250", "3100", "11:05")):
        rounded((x0 + 14, y, x1 - 14, y + 40), 10, fill=ELEV)
        d.text((x1 - 24, y + 20), who, font=font(12), fill=TEXT, anchor="rm")
        d.text((x0 + 96, y + 20), rtl("الرصيد") + " " + bal, font=font(10), fill=MUTED, anchor="lm")
        d.text((x0 + 60, y + 20), val + " AC", font=font(11, True),
               fill=GREEN if val.startswith("+") else RED, anchor="rm")
        d.text((x0 + 22, y + 20), when, font=font(10), fill=DIM, anchor="lm")
        y += 46
    pts["history"] = y - 30
    return pts


def dialog(x0, y0):
    w, h = 400, 210
    d.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=20, fill="#00000066")
    rounded((x0 + 20, y0 + 18, x0 + w - 20, y0 + h - 18), 18, fill=SURFACE, outline=BORDER2, width=2)
    d.text(((x0 + x0 + w) // 2, y0 + 48), rtl("منح AC لمستخدم"), font=font(16, True), fill=TEXT, anchor="mm")
    d.text(((x0 + x0 + w) // 2, y0 + 86), rtl("منح 1000 AC للمستخدم Mrforest؟"),
           font=font(14), fill=MUTED, anchor="mm")
    d.text((x0 + w - 52, y0 + h - 46), rtl("تأكيد"), font=font(14, True), fill=GREEN, anchor="mm")
    d.text((x0 + 90, y0 + h - 46), rtl("إلغاء"), font=font(14), fill=MUTED, anchor="mm")


def main():
    global img, d
    img = Image.new("RGB", (1300, 1180), "#F7F9FC")
    d = ImageDraw.Draw(img)
    d.text((650, 34), rtl("لوحة التحكم: منح أي مبلغ AC لأي مستخدم — والنتيجة ترجع في اللوحة"),
           font=font(24, True), fill="#0F1B26", anchor="mm")
    d.text((650, 70), rtl("نفس دالة الخادم ac_admin_adjust — بدون أي تغيير في قاعدة البيانات"),
           font=font(15), fill="#56697C", anchor="mm")

    p = main_screen(70)
    NX = 560   # عمود الملاحظات

    def call(n, y_target, color, text, dx=-26):
        note(NX, y_target, color, "%d. %s" % (n, text), anchor="lm", size=17)
        arrow(NX - 12, y_target, x_target, y_target, color, w=2)

    x_target = 70 + W
    # ① حقل المبلغ
    call(1, p["amount"], GREEN, "اكتب أي مبلغ — أو اختره من المبالغ السريعة", dx=-26)
    # ② السبب
    call(2, p["note"], BLUE, "سبب اختياري يظهر للمستخدم في إشعاره")
    # ③ أزرار المنح والخصم
    call(3, p["buttons"], GOLD, "زر المنح / الخصم بعد اختيار المستخدم")
    # ④ المنح للجميع
    call(4, p["all"], "#B3268E", "منح نفس المبلغ لكل المستخدمين")
    # ⑤ النتيجة
    call(5, p["result"], "#128A48", "النتيجة ترجع في اللوحة: المبلغ + الرصيد الجديد")
    # ⑥ السجل
    call(6, p["history"], BLUE, "سجل آخر العمليات: الاسم · الرصيد · الوقت")

    dialog(820, 640)
    note(1020, 600, "#128A48", "تأكيد قبل التنفيذ", size=17)

    d.text((650, TOP + H + 46), rtl("العملية تُسجَّل في سجل التدقيق وإشعار للمستخدم · والنصوص مترجمة إلى اللغات الثماني"),
           font=font(17, True), fill="#0F1B26", anchor="mm")
    d.text((650, TOP + H + 76), rtl("ولا يمكن تنفيذها بالخطأ: زر التأكيد أولاً، وأي مبلغ أكبر من 9 أرقام مرفوض"),
           font=font(15), fill="#56697C", anchor="mm")
    img.save(OUT)
    print("✔", OUT, img.size)


if __name__ == "__main__":
    main()
