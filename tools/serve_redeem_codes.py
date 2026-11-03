#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""خدمة تنزيل محمية لأكواد هدايا AC.

- تُخدّم أربعة ملفات فقط، وكلها تحت مسار سري عشوائي (/ac-<token>/).
- لا تُعرض قائمة مجلدات ولا أي ملف آخر من المشروع.
- الملفات لا تُرفع إلى Git (مجلد redeem-codes/ مستثنى).
"""
import http.server
import os
import re
import socketserver
import sys
import urllib.parse

FILES = {
    "AddonStation-AC-Codes.zip": ("application/zip", "كل الأكواد في ملف مضغوط"),
    "redeem_codes.txt": ("text/plain; charset=utf-8", "الأكواد مرتّبة حسب الفئة (للطباعة)"),
    "redeem_codes.sql": ("text/plain; charset=utf-8", "الصقه في Supabase → SQL Editor → Run"),
    "redeem_codes.csv": ("text/csv; charset=utf-8", "جدول أعمدة لحملة أو Excel"),
}

SIZE_ORDER = list(FILES.keys())


def page(token, base):
    rows = []
    for name in SIZE_ORDER:
        mime, desc = FILES[name]
        size = os.path.getsize(os.path.join(base, name)) if os.path.exists(os.path.join(base, name)) else 0
        rows.append((name, desc, size))
    items = "\n".join(
        f'<a class="row" href="{token}/{urllib.parse.quote(n)}" download>'
        f'<span class="name">{n}</span><span class="desc">{d}</span>'
        f'<span class="size">{s:,} B</span><span class="dl">تنزيل ⬇</span></a>'
        for n, d, s in rows
    )
    return f"""<!DOCTYPE html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>أكواد هدايا AC</title><style>
:root{{color-scheme:dark}}*{{box-sizing:border-box}}
body{{margin:0;padding:28px 14px 60px;background:#0B0F14;color:#E8EEF5;font-family:"Segoe UI",Tahoma,Arial,sans-serif}}
.wrap{{max-width:680px;margin:0 auto}}
.logo{{font-size:12px;letter-spacing:3px;color:#3DDC84;font-weight:800}}
h1{{font-size:23px;margin:8px 0 4px}}
.sub{{color:#8EA0B3;font-size:15px;margin-bottom:20px}}
.warn{{background:#2A1A1A;border:1px solid #4A2A2A;color:#FFC9C9;border-radius:14px;padding:12px 14px;font-size:14px;margin-bottom:18px}}
a.row{{display:flex;align-items:center;gap:12px;text-decoration:none;color:inherit;background:#131A24;
border:1px solid #263240;border-radius:16px;padding:14px;margin-bottom:10px}}
a.row:hover{{border-color:#3DDC84;background:#16202C}}
.name{{font-family:Consolas,monospace;font-size:15px;color:#3DDC84;direction:ltr;flex:0 0 auto}}
.desc{{color:#8EA0B3;font-size:13px;flex:1 1 auto}}
.size{{color:#5A6B7D;font-size:12px;direction:ltr}}
.dl{{background:#27A05E;color:#06210F;font-weight:800;border-radius:10px;padding:7px 12px;font-size:13px}}
.foot{{color:#5A6B7D;font-size:13px;margin-top:18px;line-height:1.9}}
code{{background:#1A2330;border:1px solid #263240;border-radius:6px;padding:2px 6px;font-size:12px;direction:ltr;display:inline-block}}
</style></head><body><div class="wrap">
<div class="logo">ADDONSTATION</div>
<h1>أكواد هدايا AC</h1>
<div class="sub">190 كوداً · 30,000×10 · 15,000×30 · 10,000×50 · 5,000×100</div>
<div class="warn">🔒 هذه الصفحة محمية بمسار سري داخل رابط التنزيل — لا تشارك الرابط علناً، وأكمل تنفيذ ملف SQL في Supabase من لوحتك فقط.</div>
{items}
<div class="foot">
<b>الخطوة الأخيرة:</b> افتح <code>redeem_codes.sql</code> ثم انسخه والصقه في
<b>Supabase → SQL Editor → Run</b> (يشمل الـ190 كوداً في عبارة واحدة).<br>
بديل: من لوحة الإدارة → AC Management → «أحدث الأكواد» تظهر وتُدار من هناك.
</div>
</div></body></html>"""


class Handler(http.server.BaseHTTPRequestHandler):
    token = ""
    base = ""

    def log_message(self, *args):  # silence
        pass

    def do_GET(self):
        path = urllib.parse.urlparse(self.path).path.strip("/")
        parts = path.split("/")

        if not path:
            self.redirect(f"/{self.token}/")
            return

        if parts[0] != self.token:
            self.send_error(404, "Not found")
            return

        if len(parts) == 1 or parts[1] == "":
            body = page(self.token, self.base).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("X-Robots-Tag", "noindex, nofollow")
            self.end_headers()
            self.wfile.write(body)
            return

        name = urllib.parse.unquote(parts[1])
        if name not in FILES:
            self.send_error(404, "Not found")
            return
        full = os.path.join(self.base, name)
        if not os.path.exists(full):
            self.send_error(404, "Not found")
            return
        mime, _ = FILES[name]
        data = open(full, "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.end_headers()
        self.wfile.write(data)

    def redirect(self, to):
        self.send_response(302)
        self.send_header("Location", to)
        self.end_headers()


def main():
    token = sys.argv[1] if len(sys.argv) > 1 else "ac-secret"
    base = sys.argv[2] if len(sys.argv) > 2 else "redeem-codes"
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 8000
    Handler.token = re.sub(r"[^A-Za-z0-9-]", "", token)
    Handler.base = os.path.abspath(base)
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(("0.0.0.0", port), Handler) as httpd:
        print(f"🔒 http://0.0.0.0:{port}/{Handler.token}/  →  {Handler.base}")
        httpd.serve_forever()


if __name__ == "__main__":
    main()
