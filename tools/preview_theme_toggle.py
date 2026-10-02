#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة نقل أيقونة المستخدم: من رأس الحساب ← إلى حقل اسم المستخدم + زر تبديل الوضع.

المحاكاة تطابق شاشة الإعدادات في لقطة المالك (نفس النصوص الإنجليزية ونفس الترتيب):
  ① رأس الحساب: الأيقونة أُزيلت (يظهر مكانها ظلّها عليه خط أحمر).
  ② حقل Username: أيقونة المستخدم صارت ملاصقة لاسم المستخدم المكتوب داخل الحقل.
  ③ صف App theme: مفتاح تبديل فوري بين Dark و Light (والاختيار يُحفظ).
ويمينها نفس الشاشة بالألوان الفاتحة لإثبات أن تبديل الوضع يغيّر كل الألوان.
المصدر: SettingsScreen.kt · Color.kt · Theme.kt · AppNav.kt
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "theme_toggle_preview.png")
DRAW = os.path.join(ROOT, "android/app/src/main/res/drawable-nodpi")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# لوحتا الألوان كما في Color.kt بعد الإصلاح
DARK = {
    "bg": "#0B0F14", "surface": "#131A24", "elev": "#1A2330", "elev2": "#212D3D",
    "border": "#263240", "border2": "#334152", "text": "#E8EEF5", "muted": "#8EA0B3",
    "dim": "#5C6F82", "green": "#3DDC84", "green2": "#2FAE67", "ongreen": "#04210F",
    "blue": "#6FB8FF",
}
LIGHT = {
    "bg": "#F2F5F9", "surface": "#FFFFFF", "elev": "#FFFFFF", "elev2": "#E7EDF4",
    "border": "#DCE4EC", "border2": "#C4D1DF", "text": "#0F1B26", "muted": "#56697C",
    "dim": "#7B8EA1", "green": "#17A75A", "green2": "#128A48", "ongreen": "#FFFFFF",
    "blue": "#1E6FD9",
}

W, H, TOP = 392, 800, 176
RED, BLUE, GRN = "#D93838", "#1E6FD9", "#17A75A"
img = None
d = None


def rtl(text):
    return get_display(arabic_reshaper.reshape(text))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def arrow(x1, y1, x2, y2, color, w=4):
    d.line([(x1, y1), (x2, y2)], fill=color, width=w)
    a = math.atan2(y2 - y1, x2 - x1)
    for s in (0.55, -0.55):
        d.line([(x2, y2), (x2 - 15 * math.cos(a - s), y2 - 15 * math.sin(a - s))], fill=color, width=w)


def note(x, y, color, text, anchor="mm", size=19):
    d.text((x, y), rtl(text), font=font(size, True), fill=color, anchor=anchor)


def icon(name, size):
    im = Image.open(os.path.join(DRAW, name + ".png")).convert("RGBA")
    return im.resize((size, size), Image.LANCZOS)


def screen(x0, pal, dark=True, show_ghost=True):
    """هاتف بنفس أقسام شاشة الإعدادات في لقطة المالك."""
    x1 = x0 + W
    rounded((x0, TOP, x1, TOP + H), 26, fill=pal["bg"], outline=pal["border2"], width=2)
    d.rectangle((x0 + 3, TOP + 3, x1 - 3, TOP + 44), fill=pal["bg"])
    stat = pal["text"] if dark else "#10171F"
    d.text((x0 + 18, TOP + 22), "2:37", font=font(12), fill=stat, anchor="lm")
    d.text((x1 - 18, TOP + 22), "▮▮", font=font(11), fill=stat, anchor="rm")

    y = TOP + 44
    # شريط التطبيق
    gb = icon("ic_set_appearance", 34)
    img.paste(gb, (int(x0 + 16), int(y + 6)), gb)
    d.text((x0 + 62, y + 23), "Settings", font=font(20, True), fill=pal["text"], anchor="lm")
    rounded((x1 - 104, y + 6, x1 - 16, y + 40), 18, fill=pal["elev2"], outline=pal["border"])
    coin = icon("coin_ac", 20)
    img.paste(coin, (int(x1 - 98), int(y + 16)), coin)
    d.text((x1 - 26, y + 23), "80 AC", font=font(13, True), fill=pal["text"], anchor="rm")
    y += 62

    # ① رأس الحساب — بلا أيقونة المستخدم (مكانها ظاهر بخط أحمر)
    head_y = y
    rounded((x0 + 14, y, x1 - 14, y + 78), 14, fill=pal["surface"], outline=pal["border"])
    d.ellipse((x1 - 66, y + 16, x1 - 18, y + 64), fill=pal["elev2"], outline=pal["green"], width=2)
    d.text((x1 - 42, y + 40), "M", font=font(17, True), fill=pal["muted"], anchor="mm")
    # ظلّ الأيقونة القديمة + خط أحمر = مكانها الذي حُذفت منه
    if show_ghost:
        gh = icon("ic_user", 26).copy()
        gh.putalpha(gh.getchannel("A").point(lambda v: int(v * 0.25)))
        img.paste(gh, (int(x1 - 128), int(y + 20)), gh)
        d.line([(x1 - 132, y + 16), (x1 - 98, y + 50)], fill=RED, width=3)
        d.line([(x1 - 98, y + 16), (x1 - 132, y + 50)], fill=RED, width=3)
    d.text((x1 - 74, y + 28), "Mrforest", font=font(17, True), fill=pal["text"], anchor="rm")
    d.text((x1 - 74, y + 50), "mohamadelfrgena@gmail.com", font=font(11), fill=pal["muted"], anchor="rm")
    rounded((x1 - 108, y + 58, x1 - 62, y + 74), 7, fill=pal["elev2"], outline=pal["border"])
    d.text((x1 - 85, y + 66), "Free", font=font(10), fill=pal["text"], anchor="mm")
    y += 100

    # ② بطاقة الحساب — حقل اسم المستخدم وأيقونته ملاصقة للاسم
    d.text((x0 + 18, y), "Account", font=font(17, True), fill=pal["text"], anchor="lt")
    y += 28
    rounded((x0 + 14, y, x1 - 14, y + 172), 14, fill=pal["surface"], outline=pal["border"])
    fy = y + 50
    d.rounded_rectangle((x0 + 28, fy - 26, x1 - 28, fy + 26), radius=9, outline=pal["border2"], width=2)
    d.text((x0 + 38, fy - 16), "Username", font=font(11), fill=pal["muted"], anchor="lm")
    # الأيقونة ملاصقة لاسم المستخدم المكتوب داخل الحقل
    ic = icon("ic_user", 24)
    img.paste(ic, (int(x0 + 40), int(fy - 11)), ic)
    d.text((x0 + 72, fy + 2), "Mrforest", font=font(15), fill=pal["text"], anchor="lm")
    y += 90
    rounded((x0 + 28, y, x1 - 28, y + 50), 12, fill=pal["green"])
    d.text(((x0 + x1) // 2, y + 25), "Save username", font=font(15, True), fill=pal["ongreen"], anchor="mm")
    y += 62
    em = icon("ic_set_email", 34)
    img.paste(em, (int(x0 + 30), int(y)), em)
    d.text((x0 + 76, y + 10), "Email", font=font(15, True), fill=pal["text"], anchor="lm")
    d.text((x0 + 76, y + 30), "mohamadelfrgena@gmail.com", font=font(11), fill=pal["muted"], anchor="lm")
    d.text((x1 - 32, y + 20), "›", font=font(22), fill=pal["dim"], anchor="mm")
    y += 58

    # ③ التفضيلات: مفتاح التبديل في صف App theme
    d.text((x0 + 18, y), "Preferences", font=font(17, True), fill=pal["text"], anchor="lt")
    y += 28
    switch_y = None
    for name, sub, ic_name, on in (("Notifications", "", "ic_set_notifications", False),
                                   ("App language", "English", "ic_set_language", False),
                                   ("App theme", "Dark" if dark else "Light", "ic_set_appearance", True)):
        rounded((x0 + 14, y, x1 - 14, y + 58), 14, fill=pal["surface"], outline=pal["border"])
        im = icon(ic_name, 30)
        img.paste(im, (int(x0 + 26), int(y + 14)), im)
        d.text((x0 + 68, y + 20), name, font=font(15, True), fill=pal["text"], anchor="lm")
        if sub:
            d.text((x0 + 68, y + 41), sub, font=font(11), fill=pal["muted"], anchor="lm")
        else:
            d.text((x1 - 30, y + 29), "›", font=font(22), fill=pal["dim"], anchor="mm")
        if name == "App theme":
            switch_y = y + 29
            tw, th, tx, ty = 48, 27, x1 - 74, y + 16
            rounded((tx, ty, tx + tw, ty + th), th // 2, fill=pal["green"] if on else pal["elev2"],
                    outline=None if on else pal["border2"], width=2)
            d.ellipse((tx + (tw - 21 if on else 3), ty + 3, tx + (tw - 3 if on else 21), ty + 24), fill="#FFFFFF")
            d.text((x1 - 86, y + 29), "›", font=font(22), fill=pal["dim"], anchor="rm")
        y += 66

    # الدعم
    d.text((x0 + 18, y), "Support", font=font(17, True), fill=pal["text"], anchor="lt")
    y += 28
    rounded((x0 + 14, y, x1 - 14, y + 54), 14, fill=pal["surface"], outline=pal["border"])
    hc = icon("ic_set_help", 30)
    img.paste(hc, (int(x0 + 26), int(y + 12)), hc)
    d.text((x0 + 68, y + 27), "Help center", font=font(15, True), fill=pal["text"], anchor="lm")
    y += 62
    d.text((x0 + 18, y), rtl("الوضع %s" % ("الداكن" if dark else "الفاتح")),
           font=font(15, True), fill=pal["muted"], anchor="lt")

    return {"ghost": (x1 - 114, head_y + 33), "field": (x0 + 52, fy), "switch": (x1 - 50, switch_y)}


def main():
    global img, d
    img = Image.new("RGB", (1180, 1120), "#F7F9FC")
    d = ImageDraw.Draw(img)
    d.text((590, 34), rtl("نقل أيقونة المستخدم: من رأس الحساب ← إلى حقل اسم المستخدم"),
           font=font(25, True), fill="#0F1B26", anchor="mm")
    d.text((590, 70), rtl("نفس شاشة الإعدادات في صورتك — الأيقونة صارت ملاصقة للاسم داخل الحقل، وزر تبديل الوضع في صف App theme"),
           font=font(15), fill="#56697C", anchor="mm")

    left = screen(70, DARK, dark=True)
    right = screen(660, LIGHT, dark=False, show_ghost=False)

    note(215, 126, RED, "1. الأيقونة حُذفت من هنا")
    arrow(215, 140, left["ghost"][0], left["ghost"][1] - 26, RED)
    arrow(left["ghost"][0] - 6, left["ghost"][1] - 26, left["ghost"][0], left["ghost"][1] - 2, RED, w=3)
    note(640, 126, BLUE, "2. الأيقونة صارت هنا", anchor="rm")
    arrow(626, 140, right["field"][0], right["field"][1] - 24, BLUE)
    arrow(right["field"][0] + 2, right["field"][1] - 24, right["field"][0] + 2, right["field"][1] - 2, BLUE, w=3)
    note(560, left["switch"][1], GRN, "3. زر التبديل", anchor="lm", size=18)
    arrow(546, left["switch"][1], left["switch"][0] + 2, left["switch"][1], GRN, w=3)
    note(1030, 126, GRN, "ونفس الشاشة\nبالألوان الفاتحة")
    arrow(1050, 158, right["field"][0] + 250, right["field"][1] + 30, GRN, w=3)

    d.text((590, TOP + H + 48), rtl("الاختيار يُحفظ على الجهاز، وأيقونات شريط الحالة تتبع الوضع المختار"),
           font=font(18, True), fill="#0F1B26", anchor="mm")
    d.text((590, TOP + H + 78), rtl("الوضع الداكن بقي كما هو تماماً (بقيمه الأصلية)، والفاتح لوحة جديدة بنفس أسماء الألوان"),
           font=font(15), fill="#56697C", anchor="mm")
    img.save(OUT)
    print("✔", OUT, img.size)


if __name__ == "__main__":
    main()
