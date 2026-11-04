#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة الإشعارات الحقيقية — لصاحب التطبيق.

ثلاث لوحات: (١) لماذا كانت «تجريبية» (تشخيص نسخة التطبيق المبنية فعلاً)
· (٢) ما أُنجز الآن (الطابور يُفرَّغ كل دقيقة + إذن يُطلب مرة واحدة + الطبيب)
· (٣) الـ٣ خطوات الباقية عليك.

يُنتج `push_live_preview.png` في الجذر.
"""
import io
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "push_live_preview.png")
APK = os.path.join(ROOT, "apk-debug.apk")

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


def apk_status():
    """نفحص نسخة التطبيق المسجَّلة فعلاً: هل تحمل إعدادات Firebase؟"""
    if not os.path.exists(APK):
        return False, "الملف غير موجود"
    try:
        with zipfile.ZipFile(APK) as z:
            names = z.namelist()
            arsc = z.read("resources.arsc") if "resources.arsc" in names else b""
        has = b"google_app_id" in arsc or any(
            n.startswith("assets/") and "firebase" in n.lower() for n in names)
        return has, f"{os.path.getsize(APK)/1024/1024:.1f} ميغابايت"
    except Exception as exc:
        return False, str(exc)[:40]


def main() -> int:
    W, H = 1520, 1030
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    has_fcm, apk_size = apk_status()

    d.text((W / 2, 32), rtl("الإشعارات الحقيقية — أين كانت مقطوعة، وما بقي عليك"),
           font=font(30), fill=TXT, anchor="ma")
    d.text((W / 2, 76), rtl("المنظومة مكتوبة كاملة: قاعدة الأجهزة والتفضيلات · دالة إرسال FCM · "
                            "خمس قنوات · ساعات هدوء · تجميع. الناقص كان اثنان فقط"),
           font=font(17), fill=MUTED, anchor="ma")

    # ---------- لوحة ١: التشخيص ----------
    x1, y1, w1, h1 = 70, 126, 440, 400
    d.rounded_rectangle((x1, y1, x1 + w1, y1 + h1), radius=18, fill=CARD, outline=RED)
    d.text((x1 + w1 / 2, y1 + 22), rtl("١) لماذا كانت تجريبية؟"), font=font(20), fill=RED, anchor="ma")
    lines = [
        f"نسخة التطبيق المسجَّلة: {apk_size}",
        "هل تحمل إعدادات Firebase؟",
        ("نعم ✓" if has_fcm else "لا ✗ — بُنيت بلا google-services.json"),
        "",
        "النتيجة: لا توكن ولا جهاز مسجّل",
        "⇒ لا يوجد ما يُرسل إليه أصلاً",
        "",
        "وسبب ثانٍ كان ينتظر:",
        "لا شيء كان يُفرّغ طابور الإشعارات",
        "إلا نداء يدوي/يومي ⇒ وصول متأخر",
    ]
    for i, t in enumerate(lines):
        color = TXT if t else MUTED
        if t.startswith("لا ✗") or t.startswith("⇒") or t.startswith("النتيجة"):
            color = RED
        if t.startswith("نعم"):
            color = GREEN
        d.text((x1 + w1 - 24, y1 + 62 + i * 30), rtl(t), font=font(15), fill=color, anchor="ra")

    # ---------- لوحة ٢: ما أُنجز ----------
    x2, y2, w2, h2 = 540, 126, 460, 400
    d.rounded_rectangle((x2, y2, x2 + w2, y2 + h2), radius=18, fill=CARD, outline=GREEN)
    d.text((x2 + w2 / 2, y2 + 22), rtl("٢) ما أُنجز الآن"), font=font(20), fill=GREEN, anchor="ma")
    done = [
        ("كل دقيقة", "تفريغ الطابور تلقائياً (pg_cron + pg_net)"),
        ("فوري", "منشن/ردّ يُرسل لحظة الإدراج — ثوانٍ"),
        ("يومياً", "تذكير الغياب · عرض الاشتراك · تنظيف"),
        ("مرة واحدة", "بطاقة «فعّل الإشعارات» بعد الدخول"),
        ("أمر واحد", "select public.push_doctor() — أين الانقطاع؟"),
        ("من الجوال", "Actions ← Push doctor ← Run workflow"),
        ("حارس", "check_push_live.py قبل كل بناء"),
    ]
    for i, (tag, text) in enumerate(done):
        yy = y2 + 66 + i * 46
        d.rounded_rectangle((x2 + w2 - 130, yy, x2 + w2 - 16, yy + 30), radius=9,
                            fill="#16261C", outline=GREEN)
        d.text((x2 + w2 - 73, yy + 15), rtl(tag), font=font(13), fill=GREEN, anchor="mm")
        d.text((x2 + w2 - 146, yy + 15), rtl(text), font=font(14), fill=TXT, anchor="rm")

    # ---------- لوحة ٣: الباقي عليك ----------
    x3, y3, w3, h3 = 1030, 126, 420, 400
    d.rounded_rectangle((x3, y3, x3 + w3, y3 + h3), radius=18, fill=CARD, outline=GOLD)
    d.text((x3 + w3 / 2, y3 + 22), rtl("٣) الباقي عليك (٢٠ دقيقة)"), font=font(20), fill=GOLD, anchor="ma")
    steps = [
        ("١", "مشروع Firebase ثم نزّل google-services.json"),
        ("", "وضعه في android/app/ داخل المودع"),
        ("٢", "مفتاح خدمة ⇒ FCM_SERVICE_ACCOUNT و PUSH_CRON_SECRET"),
        ("", "ثم انشر: supabase functions deploy send-push"),
        ("٣", "نفّذ 35_push_live.sql ثم push_live_enable(...)"),
        ("", "ثم select * from public.push_doctor();"),
        ("٤", "اطلب بناء APK جديداً (يُدمج فيه ملف Firebase)"),
    ]
    for i, (num, text) in enumerate(steps):
        yy = y3 + 66 + i * 44
        if num:
            d.ellipse((x3 + w3 - 46, yy, x3 + w3 - 16, yy + 30), fill="#1B2733", outline=GOLD)
            d.text((x3 + w3 - 31, yy + 15), rtl(num), font=font(14), fill=GOLD, anchor="mm")
        d.text((x3 + w3 - 56, yy + 15), rtl(text), font=font(14), fill=TXT, anchor="rm")

    # ---------- الشريط السفلي ----------
    by = 552
    d.rounded_rectangle((70, by, W - 70, by + 200), radius=18, fill=CARD, outline=EDGE)
    d.text((W / 2, by + 20), rtl("كيف تفرّق بين إشعار حقيقي وتجريبي؟"), font=font(20), fill=BLUE, anchor="ma")
    table = [
        ("المصدر", "يُصنَّع داخل التطبيق", "يأتي من الخادم بلغة جهازك"),
        ("والتطبيق مغلق", "لا يصل", "يصل — وهذا الفرق الجوهري"),
        ("التوقيت", "فوري دائماً", "ثوانٍ للمنشن · دقيقة للعادي · يحترم ساعات الهدوء"),
        ("التحكّم", "لا شيء", "٥ أقسام + مفتاح رئيسي + تجميع ذكي"),
    ]
    # الأعمدة (من اليمين): العلامة · تجريبي · حقيقي — والعناوين فوق أعمدتها نفسها
    d.text((W - 120, by + 58), rtl("العلامة"), font=font(15), fill=MUTED, anchor="ra")
    d.text((760, by + 58), rtl("تجريبي"), font=font(15), fill=RED, anchor="ra")
    d.text((430, by + 58), rtl("حقيقي"), font=font(15), fill=GREEN, anchor="ra")
    for i, (a, b, c) in enumerate(table):
        yy = by + 92 + i * 26
        d.text((W - 120, yy), rtl(a), font=font(14), fill=MUTED, anchor="ra")
        d.text((760, yy), rtl(b), font=font(13), fill=RED, anchor="ra")
        d.text((430, yy), rtl(c), font=font(13), fill=GREEN, anchor="ra")

    d.text((W - 26, H - 84),
           rtl("الأمان: مفتاح FCM السرّي يبقى في أسرار دالة الإرسال — والتطبيق لا يحمل أي مفتاح خدمة (حارس آلي يمنع ذلك)."),
           font=font(15), fill=BLUE, anchor="ra")
    d.text((W - 26, H - 54),
           rtl("القيد الساري: لا يُبنى APK حتى تقول «ابْنِ» — ولذلك لا شيء تغيّر في ملف طلب البناء (task: none)."),
           font=font(15), fill=GOLD, anchor="ra")
    d.text((W - 26, H - 26),
           rtl("التفصيل الكامل: docs/15-PUSH-LIVE-AR.md · والفاحص: python3 tools/push_doctor.py"),
           font=font(14), fill=MUTED, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H}) · Firebase في نسخة التطبيق: {'موجود' if has_fcm else 'غير موجود'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
