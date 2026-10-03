#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""خدمة تنزيل APK: تفتح صفحة ويب تعرض زرّ تحميل ملف apk-debug.apk.

الاستخدام:
    python3 tools/serve_apk.py --port 8000
ثم افتح المنفذ الظاهر في المعاينة المباشرة.
"""
import argparse
import hashlib
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "apk-debug.apk")

PAGE = """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>تنزيل AddonStation</title>
<style>
 body{{margin:0;background:#080C12;color:#E8EEF5;font-family:system-ui,'Segoe UI',Tahoma,sans-serif;
      display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}}
 .card{{background:#141C27;border:1px solid #263240;border-radius:18px;padding:28px;max-width:560px;width:100%}}
 h1{{margin:0 0 6px;font-size:22px}} .sub{{color:#8EA0B3;font-size:14px;margin:0 0 20px;line-height:1.7}}
 a.btn{{display:block;text-align:center;background:linear-gradient(180deg,#2E7D32,#1B5E20);color:#fff;
        text-decoration:none;font-weight:700;font-size:17px;padding:16px 20px;border-radius:14px}}
 a.btn:hover{{filter:brightness(1.1)}}
 .meta{{margin-top:18px;color:#5A6B7D;font-size:12.5px;line-height:2;word-break:break-all}}
 code{{color:#6FB8FF;background:#0E1620;padding:2px 6px;border-radius:6px}}
 .warn{{margin-top:16px;background:#1A1410;border:1px solid #4a3a20;color:#E8C88A;padding:12px 14px;
        border-radius:12px;font-size:13px;line-height:1.8}}
</style></head><body>
<div class="card">
  <h1>📦 تنزيل تطبيق AddonStation</h1>
  <p class="sub">نسخة debug مبنية من آخر كود (شاشة ترحيب بالقرية عند الغروب · بلا بحث في الشات · لوحة إدارة للأدمن فقط).</p>
  <a class="btn" href="/apk-debug.apk?v={tag}" download>تحميل APK ({size})</a>
  <div class="meta">
    الحجم: {size} · {mb} ميغابايت<br>
    وقت البناء: <b>{built}</b><br>
    SHA-256: <code>{sha}</code>
  </div>
  <div class="warn">⚠️ إذا لم تتغيّر symptoms بعد التنزيل: افتح هذه الصفحة من جديد (ولا تستخدم تحميلاً سابقاً محفوظاً في المتصفح)،<br>
  وتأكّد أن وقت البناء أعلاه هو الأحدث. الرابط يحمل رقماً جديداً لكل نسخة فلا يعيد المتصفح الملف القديم.</div>
  <div class="warn">📱 على الهاتف: بعد التحميل فعّل «تثبيت من مصادر غير معروفة» لهاتفك ثم افتح الملف.<br>
  🖥️ على الكمبيوتر: فعّل «السماح بملفات من أي مصدر» في المتصفح إذا رفض التحميل.</div>
</div></body></html>
"""


class Handler(BaseHTTPRequestHandler):
    def _html(self, code=200):
        data = open(APK, "rb").read() if os.path.exists(APK) else b""
        sha = hashlib.sha256(data).hexdigest() if data else "—"
        mtime = os.path.getmtime(APK) if os.path.exists(APK) else 0
        built = time.strftime("%Y-%m-%d %H:%M", time.localtime(mtime)) if mtime else "—"
        body = PAGE.format(size="%0.1f ميجابايت" % (len(data) / 1048576.0) if data else "غير متاح",
                           mb=round(len(data) / 1048576.0, 2), sha=sha[:32] + "…",
                           built=built, tag=sha[:12] + "-" + str(int(mtime))).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_HEAD(self):
        # نفس الترويسات بلا جسم: أدوات الفحص والمتصفحات تستعمله أيضاً
        if self.path.split("?")[0] in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            self.send_header("Pragma", "no-cache")
            self.send_header("Expires", "0")
            self.end_headers()
        elif self.path.split("?")[0] == "/apk-debug.apk":
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            self.send_header("Pragma", "no-cache")
            self.send_header("Expires", "0")
            self.send_header("Content-Length", str(os.path.getsize(APK) if os.path.exists(APK) else 0))
            self.end_headers()
        else:
            self.send_error(404)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self._html()
        elif path == "/apk-debug.apk":
            if not os.path.exists(APK):
                self.send_error(404, "APK غير موجود")
                return
            size = os.path.getsize(APK)
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            # **بدون هذا يأخذ المتصفح/الهاتف النسخة القديمة من ذاكرته المؤقتة**
            # ويبقى المستخدم يجرّب نسخة قديمة بعد التحديث.
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            self.send_header("Pragma", "no-cache")
            self.send_header("Expires", "0")
            self.send_header("Content-Disposition", 'attachment; filename="AddonStation-debug.apk"')
            self.send_header("Content-Length", str(size))
            self.end_headers()
            with open(APK, "rb") as f:
                while True:
                    chunk = f.read(1 << 20)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        print("·", fmt % args, flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8000)
    args = ap.parse_args()
    srv = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"✓ خدمة تنزيل APK تعمل على 0.0.0.0:{args.port} · الملف: {APK} · "
          f"{os.path.getsize(APK) if os.path.exists(APK) else 0} B")
    srv.serve_forever()


if __name__ == "__main__":
    main()
