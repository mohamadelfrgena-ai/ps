#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""خدمة تنزيل حزمة PS4 PKG: تفتح صفحة ويب تعرض زر تحميل مباشر لملف pkg-addon-test.pkg."""
import argparse
import hashlib
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = os.path.join(ROOT, "ps4", "downloads", "pkg-addon-test.pkg")

PAGE = """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>تنزيل pkg addon test (PS4)</title>
<style>
 body{{margin:0;background:#080C12;color:#E8EEF5;font-family:system-ui,'Segoe UI',Tahoma,sans-serif;
      display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}}
 .card{{background:#141C27;border:1px solid #263240;border-radius:18px;padding:28px;max-width:560px;width:100%}}
 h1{{margin:0 0 6px;font-size:22px}} .sub{{color:#8EA0B3;font-size:14px;margin:0 0 20px;line-height:1.7}}
 a.btn{{display:block;text-align:center;background:linear-gradient(180deg,#2E7D32,#1B5E20);color:#fff;
        text-decoration:none;font-weight:700;font-size:17px;padding:16px 20px;border-radius:14px;margin-bottom:12px}}
 a.btn2{{display:block;text-align:center;background:#1E2A3A;border:1px solid #354860;color:#90CAF9;
        text-decoration:none;font-weight:600;font-size:14px;padding:12px 16px;border-radius:12px;margin-bottom:8px}}
 a.btn:hover,a.btn2:hover{{filter:brightness(1.1)}}
 .meta{{margin-top:18px;color:#5A6B7D;font-size:12.5px;line-height:2;word-break:break-all}}
 code{{color:#6FB8FF;background:#0E1620;padding:2px 6px;border-radius:6px}}
</style></head><body>
<div class="card">
  <h1>🎮 تنزيل حزمة pkg addon test (PS4 Mini-App)</h1>
  <p class="sub">نسخة <b>CATEGORY = gde (Mini-App)</b> بمعرّف <code>ATST00002</code> تحتوي على إضافة وغلاف <b>PrizmaVisuals 1.3.10</b> (8,806 ملفات) وتعمل مع بقاء Minecraft معلّقة في الخلفية.</p>
  <a class="btn" href="/pkg-addon-test.pkg?v={tag}" download="pkg-addon-test.pkg">⬇️ تحميل مباشر pkg-addon-test.pkg ({size})</a>
  <a class="btn2" href="https://github.com/mohamadelfrgena-ai/ps/raw/arena/5622bbf6-ps/ps4/downloads/pkg-addon-test.pkg" target="_blank" rel="noopener">🔗 رابط التحميل المباشر من GitHub Raw</a>
  <a class="btn2" href="https://github.com/mohamadelfrgena-ai/ps/actions/runs/38732514925" target="_blank" rel="noopener">📦 صفحة البناء في GitHub Actions (Artifacts)</a>
  <div class="meta">
    الحجم: {size} ({mb} MB)<br>
    وقت البناء: <b>{built}</b><br>
    TITLE_ID: <code>ATST00002</code> · CATEGORY: <code>gde</code><br>
    SHA-256: <code>{sha}</code>
  </div>
</div></body></html>
"""


class Handler(BaseHTTPRequestHandler):
    def _html(self, code=200):
        data = open(PKG, "rb").read() if os.path.exists(PKG) else b""
        sha = hashlib.sha256(data).hexdigest() if data else "—"
        mtime = os.path.getmtime(PKG) if os.path.exists(PKG) else 0
        built = time.strftime("%Y-%m-%d %H:%M", time.localtime(mtime)) if mtime else "—"
        body = PAGE.format(
            size="%0.1f ميجابايت" % (len(data) / 1048576.0) if data else "غير متاح",
            mb=round(len(data) / 1048576.0, 2),
            sha=sha[:32] + "…",
            built=built,
            tag=sha[:12] + "-" + str(int(mtime)),
        ).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_HEAD(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
        elif path == "/pkg-addon-test.pkg":
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(os.path.getsize(PKG) if os.path.exists(PKG) else 0))
            self.end_headers()
        else:
            self.send_error(404)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self._html()
        elif path == "/pkg-addon-test.pkg":
            if not os.path.exists(PKG):
                self.send_error(404, "PKG not found")
                return
            size = os.path.getsize(PKG)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            self.send_header("Content-Disposition", 'attachment; filename="pkg-addon-test.pkg"')
            self.send_header("Content-Length", str(size))
            self.end_headers()
            with open(PKG, "rb") as f:
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
    print(f"✓ خدمة تنزيل PKG تعمل على 0.0.0.0:{args.port} · الملف: {PKG}")
    srv.serve_forever()


if __name__ == "__main__":
    main()
