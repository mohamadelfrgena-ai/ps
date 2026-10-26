#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
طبيب الإشعارات — يجيب على سؤال واحد: **لماذا لا تصل إشعاراتي؟**

يفحص السلسلة كاملة من أولها إلى آخرها، ويطبع تقريراً بالعربية يقول بالضبط
أين انقطعت:

    ١) هل المخزن/المشروع يستجيب أصلاً؟
    ٢) هل نُفِّذ `supabase/35_push_live.sql` (جدول الإعدادات + دوال التشغيل)؟
    ٣) هل ضُبط رابط دالة الإرسال وسرّها؟ (push_live_enable)
    ٤) هل المهام المجدولة موجودة؟ (كل دقيقة + يومية)
    ٥) هل هناك أجهزة مسجّلة (توكن FCM)؟
    ٦) هل الطابور يتحرّك؟ (مستحق · مُرسل · عالق · فشل)
    ٧) ماذا قال آخر نداء لدالة الإرسال؟ (200 · 401 · أسرار ناقصة · مفتاح FCM)
    ٨) **هل نسخة التطبيق المبنية تحمل google-services.json؟** — يُفحص داخل ملف
       APK نفسه: إن كان الحقل `google_app_id` غائباً من موارده فالتطبيق لا
       يستطيع استقبال أي إشعار، **حتى لو كان كل شيء على الخادم سليماً**.

الاستخدام:
    python3 tools/push_doctor.py --url https://xxx.supabase.co --key sb_secret_…
    python3 tools/push_doctor.py --url … --key … --apk apk-debug.apk

أو بلا شبكة (فحص نسخة الـAPK وحدها):
    python3 tools/push_doctor.py --apk apk-debug.apk --offline
"""
import argparse
import io
import json
import os
import sys
import urllib.error
import urllib.request
import zipfile

OK = "✓"
BAD = "✗"
WARN = "!"

results: list[tuple[str, bool, str]] = []


def add(check: str, ok: bool, detail: str = ""):
    results.append((check, ok, detail))
    mark = OK if ok else BAD
    print(f"  {mark} {check}" + (f" — {detail}" if detail else ""))


def http(url, key=None, data=None, method="POST", timeout=45, headers=None):
    req = urllib.request.Request(url, data=data, method=method)
    if key:
        req.add_header("apikey", key)
        req.add_header("Authorization", "Bearer " + key)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=timeout) as res:
        return res.status, res.read().decode("utf-8", "replace")


def rpc(base, key, name, params=None):
    status, body = http(f"{base}/rest/v1/rpc/{name}", key,
                        json.dumps(params or {}).encode("utf-8"))
    return status, body


# --------------------------------------------------------------------------- ١) APK

def check_apk(path: str):
    """هل هذا الـAPK يستطيع استقبال إشعارات؟ (وجود google_app_id في الموارد)."""
    if not path or not os.path.exists(path):
        add("نسخة التطبيق (APK) — هل تحمل إعدادات Firebase؟", False,
            f"الملف غير موجود: {path or '—'}")
        return
    try:
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            arsc = z.read("resources.arsc") if "resources.arsc" in names else b""
        has_id = b"google_app_id" in arsc
        has_ass = any(n.startswith("assets/") and "firebase" in n.lower() for n in names)
        size = os.path.getsize(path)
        add("نسخة التطبيق (APK) — هل تحمل إعدادات Firebase؟", has_id or has_ass,
            ("محمّلة (FCM يعمل في هذا البناء)" if (has_id or has_ass)
             else "لا — بُنيت بلا google-services.json ⇒ لا تستقبل أي إشعار")
            + f" · {size/1024/1024:.1f}MB")
    except Exception as exc:
        add("فحص ملف APK", False, str(exc)[:80])


# --------------------------------------------------------------------------- ٢) المشروع

def check_remote(base: str, key: str):
    # (أ) المشروع يستجيب؟
    try:
        status, _ = http(f"{base}/rest/v1/", key, b"", method="GET")
        add("المشروع يستجيب (Supabase)", True, f"HTTP {status}")
    except Exception as exc:
        add("المشروع يستجيب (Supabase)", False, f"تعذّر الاتصال: {str(exc)[:80]}")
        return

    # (ب) هل نُفِّذ 35_push_live.sql؟
    try:
        status, body = rpc(base, key, "push_doctor")
        rows = json.loads(body) if body.strip() else []
        ok35 = status == 200 and isinstance(rows, list) and len(rows) > 0
        add("مُشغِّل الإشعارات منشور (35_push_live.sql)", ok35,
            "دالة push_doctor موجودة" if ok35
            else "غير منشور — نفّذ supabase/35_push_live.sql في SQL Editor")
        if ok35:
            print("\n  تقرير القاعدة (push_doctor):")
            for row in rows:
                mark = OK if row.get("ok") else BAD
                print(f"    {mark} {row.get('check_ar')} — {row.get('detail')}")
            print()
    except Exception as exc:
        add("مُشغِّل الإشعارات منشور (35_push_live.sql)", False,
            f"push_doctor غير متاحة ({str(exc)[:60]}) — نفّذ supabase/35_push_live.sql")

    # (ج) دالة الإرسال منشورة؟
    for action in ("dispatch",):
        try:
            status, body = http(f"{base}/functions/v1/send-push?action={action}", None,
                                b"{}", headers={"x-push-secret": "probe"})
            unauthorized = "unauthorized" in body
            add("دالة الإرسال منشورة (send-push)", status in (200, 401, 500) or unauthorized,
                "منشورة وتستجيب" if unauthorized
                else f"استجابت {status}: {body[:90]}")
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", "replace")
            add("دالة الإرسال منشورة (send-push)", exc.code in (401, 400),
                "منشورة (ترفض بلا سرّ صحيح — وهذا المطلوب)" if exc.code == 401
                else f"HTTP {exc.code}: {body[:90]}")
        except Exception as exc:
            add("دالة الإرسال منشورة (send-push)", False,
                f"غير منشورة أو لا تُستجاب ({str(exc)[:60]}) — supabase functions deploy send-push --no-verify-jwt")

    # (د) الطابور والأجهزة
    for name, label in (("push_devices", "أجهزة مسجّلة"), ("push_queue", "طابور الإشعارات")):
        try:
            status, body = http(f"{base}/rest/v1/{name}?select=*&limit=1&order=created_at.desc",
                                key, None, method="GET")
            rows = json.loads(body)
            add(f"قراءة {label}", status == 200, f"{len(rows)} صف في العيّنة")
        except Exception as exc:
            add(f"قراءة {label}", False, str(exc)[:80])

    # (هـ) ملخّص الأرقام
    try:
        status, body = http(
            f"{base}/rest/v1/push_devices?select=token,enabled,locale,last_seen_at&limit=1000",
            key, None, method="GET")
        rows = json.loads(body)
        enabled = [r for r in rows if r.get("enabled")]
        add("أجهزة فعّالة (توكن FCM)", len(enabled) > 0,
            f"{len(enabled)} جهاز" + ("" if enabled else " — سجّل الدخول من التطبيق بعد تثبيت نسخة تحمل Firebase"))
    except Exception as exc:
        add("عدد الأجهزة الفعّالة", False, str(exc)[:80])

    try:
        status, body = http(
            f"{base}/rest/v1/push_queue?select=status,sent_at,created_at&order=created_at.desc&limit=200",
            key, None, method="GET")
        rows = json.loads(body)
        by_status: dict[str, int] = {}
        for r in rows:
            by_status[r.get("status", "?")] = by_status.get(r.get("status", "?"), 0) + 1
        sent = by_status.get("sent", 0)
        pending = by_status.get("pending", 0)
        failed = by_status.get("failed", 0)
        add("الطابور يتحرّك (أُرسل فعلاً)", sent > 0,
            f"مُرسل {sent} · مستحق {pending} · فشل {failed}" +
            ("" if sent else " — لا إشعار وصل لجهاز بعد"))
    except Exception as exc:
        add("حركة الطابور", False, str(exc)[:80])


def summarize():
    bad = [c for c in results if not c[1]]
    print("\n" + "=" * 64)
    if not bad:
        print("🟢 كل السلسلة سليمة — إن لم يصل إشعار بعد، جرّب «إشعار تجريبي» من الإعدادات.")
        return 0
    print(f"✗ عدد النقاط المتوقّفة: {len(bad)}")
    for name, _, detail in bad:
        print(f"   • {name}: {detail}")
    print("\nالترتيب الصحيح للإصلاح:")
    print("   ١) أنشئ مشروع Firebase وضع android/app/google-services.json ثم اطلب بناء APK جديد.")
    print("   ٢) نفّذ supabase/35_push_live.sql في SQL Editor.")
    print("   ٣) انشر الدالة: supabase functions deploy send-push --no-verify-jwt")
    print("   ٤) ضع مفتاح FCM في أسرار الدالة (FCM_SERVICE_ACCOUNT + PUSH_CRON_SECRET).")
    print("   ٥) select public.push_live_enable('رابط-الدالة','السرّ');")
    print("   ثم: select * from public.push_doctor();")
    return 1


def main():
    ap = argparse.ArgumentParser(description="طبيب الإشعارات — لماذا لا تصل؟")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""))
    ap.add_argument("--apk", default="apk-debug.apk")
    ap.add_argument("--offline", action="store_true", help="فحص الـAPK وحده بلا شبكة")
    args = ap.parse_args()

    print("طبيب الإشعارات")
    print("-" * 64)
    check_apk(args.apk)
    if not args.offline:
        if not args.url or not args.key:
            print("  ! بلا --url/--key: اقتصرنا على فحص نسخة التطبيق.")
        else:
            check_remote(args.url.rstrip("/"), args.key)
    return summarize()


if __name__ == "__main__":
    raise SystemExit(main())
