#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة مسار الدخول/التسجيل بعد الإصلاح — لصاحب التطبيق.

ثلاث شاشات: (١) كان: حساب يُنشأ وتبقى خارج التطبيق · (٢) بطاقة تأكيد البريد
الجديدة بزرّيها · (٣) شاشة الضيف في «ملف شخصي» بدل الطرد إلى شاشة الدخول.

يُنتج `auth_flow_preview.png` في الجذر. الاستخدام: python3 tools/preview_auth_flow.py
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "auth_flow_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BG = "#080C12"
CARD = "#141C27"
EDGE = "#263240"
TXT = "#E8EEF5"
MUTED = "#8EA0B3"
GREEN = "#2E9E5B"
RED = "#E5484D"
GOLD = "#D6A93A"
BLUE = "#2F7FD8"


def rtl(t: str) -> str:
    return get_display(arabic_reshaper.reshape(t))


def font(size: int):
    return ImageFont.truetype(FONT_BOLD, size)


def phone(d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int):
    d.rounded_rectangle((x, y, x + w, y + h), radius=26, fill="#0B1015", outline=EDGE, width=2)


def btn(d, x, y, w, h, text, fill, text_color="#FFFFFF", outline=None):
    d.rounded_rectangle((x, y, x + w, y + h), radius=14, fill=fill, outline=outline, width=2 if outline else 1)
    d.text((x + w / 2, y + h / 2 - 1), rtl(text), font=font(17), fill=text_color, anchor="mm")


def main() -> int:
    W, H = 1520, 1000
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    d.rectangle((0, 0, W, 80), fill="#111823")
    d.text((W - 26, 24), rtl("حالة الدخول بعد الإصلاح — التطبيق يعرف أنك سجّلت، ولا صفحة تسجيل تفاجئك"),
           font=font(26), fill=TXT, anchor="ra")
    d.text((26, 28), "v1.3 · auth state", font=font(15), fill="#5A6B7D", anchor="la")

    pw, ph = 420, 720
    pad = 40
    y0 = 150
    xs = [pad + i * (pw + pad) for i in range(3)]

    # ------------------------- (١) قبل: الحساب يُنشأ والباب مغلق -------------------------
    x = xs[0]
    phone(d, x, y0, pw, ph)
    d.text((x + pw / 2, y0 + 24), rtl("إنشاء حساب"), font=font(20), fill=TXT, anchor="ma")
    d.rounded_rectangle((x + 30, y0 + 66, x + pw - 30, y0 + 120), radius=12, fill="#0E1620", outline=EDGE)
    d.text((x + pw - 46, y0 + 82), rtl("البريد الإلكتروني"), font=font(14), fill=MUTED, anchor="ra")
    d.rounded_rectangle((x + 30, y0 + 132, x + pw - 30, y0 + 186), radius=12, fill="#0E1620", outline=EDGE)
    d.text((x + pw - 46, y0 + 148), rtl("كلمة المرور"), font=font(14), fill=MUTED, anchor="ra")
    btn(d, x + 30, y0 + 200, pw - 60, 52, "إنشاء حساب", "#274156")
    d.rounded_rectangle((x + 30, y0 + 276, x + pw - 30, y0 + 372), radius=14, fill="#1A1410", outline="#4a3a20")
    d.text((x + pw / 2, y0 + 300), rtl("✓ تم إنشاء الحساب! أكّد بريدك"), font=font(15), fill=GOLD, anchor="ma")
    d.text((x + pw / 2, y0 + 330), rtl("…ثم تبقى خارج التطبيق بلا مخرج"), font=font(14), fill="#C98A8E", anchor="ma")
    d.text((x + pw / 2, y0 + 420), rtl("تضغط «ملف شخصي»"), font=font(16), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + 452), rtl("↓"), font=font(22), fill=RED, anchor="ma")
    d.rounded_rectangle((x + 30, y0 + 486, x + pw - 30, y0 + 562), radius=14, fill="#2A1418", outline="#7A3F44", width=2)
    d.text((x + pw / 2, y0 + 508), rtl("صفحة التسجيل مرة أخرى!"), font=font(17), fill="#F2A0A4", anchor="ma")
    d.text((x + pw / 2, y0 + 536), rtl("(طرد قسري إلى شاشة الدخول)"), font=font(13), fill="#C98A8E", anchor="ma")
    d.text((x + pw / 2, y0 + ph - 34), rtl("قبل"), font=font(18), fill=RED, anchor="ma")

    # ------------------------- (٢) بعد: بطاقة تأكيد البريد -------------------------
    x = xs[1]
    phone(d, x, y0, pw, ph)
    d.text((x + pw / 2, y0 + 24), rtl("تم إنشاء حسابك ✓"), font=font(20), fill=GREEN, anchor="ma")
    d.rounded_rectangle((x + 30, y0 + 70, x + pw - 30, y0 + 400), radius=16, fill="#12211A", outline=GREEN, width=2)
    d.text((x + pw / 2, y0 + 96), rtl("أرسلنا رابط تأكيد إلى بريدك"), font=font(16), fill=TXT, anchor="ma")
    d.text((x + pw / 2, y0 + 126), rtl("افتحه ثم اضغط الزرّ بالأسفل"), font=font(14), fill=MUTED, anchor="ma")
    d.rounded_rectangle((x + 60, y0 + 152, x + pw - 60, y0 + 194), radius=10, fill="#0E1620", outline=EDGE)
    d.text((x + pw / 2, y0 + 164), "mohamad@example.com", font=font(14), fill=BLUE, anchor="ma")
    btn(d, x + 46, y0 + 212, pw - 92, 52, "أكّدت بريدي — سجّلني الآن", GREEN)
    btn(d, x + 46, y0 + 276, pw - 92, 48, "أعِد إرسال رابط التأكيد", "#1B2733", outline=EDGE)
    d.text((x + pw / 2, y0 + 348), rtl("لم تجد الرسالة؟ تحقّق من مجلد"), font=font(12), fill="#5A6B7D", anchor="ma")
    d.text((x + pw / 2, y0 + 368), rtl("«الرسائل غير المرغوبة» أيضاً."), font=font(12), fill="#5A6B7D", anchor="ma")
    d.text((x + pw / 2, y0 + 440), rtl("المخرَج كامل من داخل التطبيق:"), font=font(15), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + 470), rtl("تأكيد ← دخول فوري"), font=font(16), fill=GREEN, anchor="ma")
    d.text((x + pw / 2, y0 + 500), rtl("أو إعادة إرسال الرابط بضغطة"), font=font(16), fill=GREEN, anchor="ma")
    d.rounded_rectangle((x + 30, y0 + 546, x + pw - 30, y0 + 640), radius=14, fill="#101C2A", outline="#2A4A6A")
    d.text((x + pw / 2, y0 + 570), rtl("دخولك يُبلّغ التطبيق بكامله:"), font=font(14), fill=TXT, anchor="ma")
    d.text((x + pw / 2, y0 + 598), rtl("الرئيسية · الشات · المتجر · الإعدادات"), font=font(13), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + ph - 34), rtl("بعد"), font=font(18), fill=GREEN, anchor="ma")

    # ------------------------- (٣) ملف شخصي للضيف -------------------------
    x = xs[2]
    phone(d, x, y0, pw, ph)
    d.text((x + pw / 2, y0 + 24), rtl("ملف شخصي"), font=font(20), fill=TXT, anchor="ma")
    d.ellipse((x + pw / 2 - 52, y0 + 66, x + pw / 2 + 52, y0 + 170), fill="#1B2733", outline=GREEN, width=3)
    d.text((x + pw / 2, y0 + 105), rtl("ضيف"), font=font(16), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + 196), rtl("أنت تتصفّح كضيف"), font=font(20), fill=TXT, anchor="ma")
    d.text((x + pw / 2, y0 + 232), rtl("سجّل الدخول أو أنشئ حساباً ليحفظ"), font=font(13), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + 252), rtl("التطبيق مفضّلتك ومحتواك ورصيد AC"), font=font(13), fill=MUTED, anchor="ma")
    btn(d, x + 46, y0 + 296, pw - 92, 52, "تسجيل الدخول", GREEN)
    btn(d, x + 46, y0 + 360, pw - 92, 48, "إنشاء حساب", "#1B2733", outline=EDGE)
    d.rounded_rectangle((x + 30, y0 + 436, x + pw - 30, y0 + 520), radius=14, fill="#101C2A", outline="#2A4A6A")
    d.text((x + pw / 2, y0 + 458), rtl("لا نقل تلقائي — التنقّل بيدك"), font=font(15), fill=GREEN, anchor="ma")
    d.text((x + pw / 2, y0 + 486), rtl("(كان يُقتلع إلى شاشة الدخول"), font=font(13), fill=MUTED, anchor="ma")
    d.text((x + pw / 2, y0 + ph - 34), rtl("الآن"), font=font(18), fill=BLUE, anchor="ma")

    # تذييل
    d.text((W - 26, y0 + ph + 20),
           rtl("حارس آلي (tools/check_auth_state.py) يمنع عودة أي سبب من هذه الأسباب، والنصوص الجديدة مترجمة إلى اللغات الثماني."),
           font=font(15), fill=BLUE, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
