#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة خارطة التشغيل — إشعارات حقيقية من الجوال.

خمس خطوات مرتّبة: ما هو جاهز أصلاً (٥ بنود) وما بقي عليك (٤ خطوات)، مع
«ماذا أنسخ وأين ألصق» وجدول أعطال مختصر.

يُنتج `push_setup_preview.png` في الجذر.
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "push_setup_preview.png")

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


def main() -> int:
    W, H = 1520, 1060
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    d.text((W / 2, 30), rtl("إشعارات حقيقية من جوالك — خارطة التشغيل"), font=font(31), fill=TXT, anchor="ma")
    d.text((W / 2, 76), rtl("الخادم كلّه مكتوب وجاهز. الباقي: مشروع Firebase في حسابك "
                            "(٥ دقائق) ← ٣ أسرار ← سير واحد يضبط كل شيء ← «ابنِ»"),
           font=font(17), fill=MUTED, anchor="ma")

    # ---------------- يمين: عليك (٤ خطوات) ----------------
    xr, yr, wr, hr = 800, 130, 650, 470
    d.rounded_rectangle((xr, yr, xr + wr, yr + hr), radius=18, fill=CARD, outline=GOLD)
    d.text((xr + wr / 2, yr + 22), rtl("ما بقي عليك — ٤ خطوات (٢٠ دقيقة، مرة واحدة)"),
           font=font(20), fill=GOLD, anchor="ma")

    steps = [
        ("١", "Firebase: مشروع جديد + إضافة تطبيق أندرويد",
         "اسم الحزمة حرفياً: com.addonstation.mobile"),
        ("", "نزّل google-services.json", "ثم Project settings → Service accounts → Generate new private key"),
        ("٢", "ثلاثة أسرار في GitHub (Settings → Actions → New secret)",
         "GOOGLE_SERVICES_JSON · FCM_SERVICE_ACCOUNT · SUPABASE_ACCESS_TOKEN"),
        ("٣", "Actions ← Push doctor ← Run workflow (الخياران true)",
         "ينشر الدالة + ينفّذ 35_push_live.sql + يشغّل الطابور تلقائياً"),
        ("٤", "قل «ابنِ» — تبني نسخة تحمل Firebase",
         "ثم: ثبّت ← وافق على «فعّل الإشعارات» ← جرّب"),
    ]
    yy = yr + 62
    for num, title, sub in steps:
        if num:
            d.ellipse((xr + wr - 48, yy + 2, xr + wr - 18, yy + 32), fill="#1B2733", outline=GOLD)
            d.text((xr + wr - 33, yy + 17), rtl(num), font=font(15), fill=GOLD, anchor="mm")
        d.text((xr + wr - 58, yy + 16), rtl(title), font=font(16), fill=TXT, anchor="rm")
        d.text((xr + wr - 24, yy + 42), rtl(sub), font=font(13), fill=MUTED, anchor="ra")
        yy += 70

    # ---------------- يسار: جاهز ----------------
    xl, yl, wl, hl = 70, 130, 690, 470
    d.rounded_rectangle((xl, yl, xl + wl, yl + hl), radius=18, fill=CARD, outline=GREEN)
    d.text((xl + wl / 2, yl + 22), rtl("جاهز في المودع الآن — لا تلمسه"), font=font(20), fill=GREEN, anchor="ma")
    ready = [
        ("قاعدة الإشعارات", "أجهزة · تفضيلات (٥ أقسام + ساعات هدوء) · طابور + زنادات"),
        ("الطابور يُفرَّغ تلقائياً", "كل دقيقة (pg_cron + pg_net) · وفوراً للمنشن/الردّ (ثوانٍ)"),
        ("دالة الإرسال FCM", "تُترجم لكل جهاز بلغته · صور الأغلفة · تجميع ذكي · بلا مفتاح في التطبيق"),
        ("إذن الإشعارات داخل التطبيق", "بطاقة «فعّل الإشعارات» مرة واحدة بعد الدخول (٨ لغات)"),
        ("طبيب + حارس", "push_doctor() و Actions ← Push doctor · وحارس قبل كل بناء"),
    ]
    yy = yl + 66
    for title, sub in ready:
        d.text((xl + wl - 24, yy), rtl(title), font=font(16), fill=TXT, anchor="ra")
        d.text((xl + wl - 24, yy + 24), rtl(sub), font=font(13), fill=MUTED, anchor="ra")
        yy += 70

    # ---------------- شريط: ماذا تنسخ وأين ----------------
    by, bh = 630, 150
    d.rounded_rectangle((70, by, W - 70, by + bh), radius=18, fill="#101C2A", outline=BLUE)
    d.text((W / 2, by + 20), rtl("أهمّ ثلاث نسخ ولصق (لتفادي الأخطاء الشائعة)"),
           font=font(19), fill=BLUE, anchor="ma")
    copies = [
        ("اسم الحزمة", "com.addonstation.mobile", "في Firebase عند Register app — حرفياً"),
        ("ملف google-services.json", "أرسله لي هنا أو ضعه في سرّ GOOGLE_SERVICES_JSON", "ليس سرّاً: مُعرّف هوية فقط"),
        ("ملف حساب الخدمة", "في سرّ FCM_SERVICE_ACCOUNT فقط — لا ترسله في المحادثة", "سرّ حقيقي: يملك الإرسال"),
    ]
    for i, (a, b, c) in enumerate(copies):
        yy = by + 58 + i * 30
        d.text((W - 100, yy), rtl(a), font=font(14), fill=GOLD, anchor="ra")
        d.text((760, yy), rtl(b), font=font(13), fill=TXT, anchor="ra")
        d.text((300, yy), rtl(c), font=font(13), fill=MUTED, anchor="ra")

    # ---------------- شريط: أعطال سريعة ----------------
    ty, th = 802, 152
    d.rounded_rectangle((70, ty, W - 70, ty + th), radius=18, fill=CARD, outline=EDGE)
    d.text((W / 2, ty + 18), rtl("إن لم تصل — هذه الأسباب الأربعة وحدها"),
           font=font(19), fill=RED, anchor="ma")
    trouble = [
        ("«غير مُهيّأة» في التطبيق", "النسخة المثبّتة بلا Firebase ⇒ بناء جديد"),
        ("no_device", "لا توكن مسجّل ⇒ سجّل دخول ودع الإذن ممنوحاً ثم أعد الفتح"),
        ("401 من الطبيب", "سرّ القاعدة ≠ PUSH_CRON_SECRET ⇒ أعد push_live_enable"),
        ("missing_secrets", "FCM_SERVICE_ACCOUNT ناقص في أسرار الدالة ⇒ شغّل deploy_function"),
    ]
    for i, (a, b) in enumerate(trouble):
        xx = W - 110 - (i % 2) * 690
        yy = ty + 54 + (i // 2) * 46
        d.text((xx, yy), rtl(a), font=font(15), fill=GOLD, anchor="ra")
        d.text((xx, yy + 22), rtl(b), font=font(13), fill=MUTED, anchor="ra")

    d.text((W - 26, H - 62),
           rtl("الأمان: مفتاح FCM يبقى في أسرار دالة الإرسال · التطبيق يحمل المفتاح العام فقط (حارس آلي يمنع أي سرّ)."),
           font=font(14), fill=BLUE, anchor="ra")
    d.text((W - 26, H - 34),
           rtl("القيد الساري: لا يُبنى APK حتى تقول «ابنِ» — ومرجعك الكامل: docs/16-PUSH-SETUP-PHONE-AR.md"),
           font=font(14), fill=GOLD, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
