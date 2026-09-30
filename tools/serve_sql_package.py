#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يخدم حزمة SQL الخاصة بـ AddonStation كرابط تحميل مباشر من داخل المودع،
بدون إنشاء أي ملفات مؤقتة: الحزمة المضغوطة تُبنى في الذاكرة عند كل طلب.

الاستخدام:
    python3 tools/serve_sql_package.py --email you@example.com --port 8000

ثم افتح:  http://localhost:8000
ورابط التحميل المباشر:  http://localhost:8000/AddonStation-Supabase-SQL.zip

ملاحظات:
  • لا يحتوي هذا السكربت على أي مفتاح سري، والبريد يُمرَّر كوسيط (اختياري).
  • ملف الأدمن الجاهز (08_admin_bootstrap_READY.sql) يُبنى ببريدك وقت التشغيل.
  • كل الملفات تُقرأ من مجلد supabase/ داخل المودع.
"""
import argparse
import hashlib
import io
import os
import sys
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUPABASE = os.path.join(ROOT, "supabase")

# ترتيب الملفات في الحزمة:  (اسم الملف داخل مجلد supabase، الاسم داخل الحزمة، الوصف)
ORDER = [
    ("00_ALL_IN_ONE.sql", "00_ALL_IN_ONE.sql",
     "الملف الأساسي — 35 جدولاً و68 دالة و78 سياسة و6 مخازن والبيانات الأولية"),
    ("01_schema.sql", "01_schema.sql", "بديل: الجداول والمشغّلات"),
    ("02_functions_core.sql", "02_functions_core.sql", "بديل: 27 دالة أساسية"),
    ("03_functions_chat.sql", "03_functions_chat.sql", "بديل: 33 دالة الشات والمبدعين"),
    ("04_rls.sql", "04_rls.sql", "بديل: 58 سياسة أمان"),
    ("05_storage.sql", "05_storage.sql", "بديل: 6 مخازن وسياساتها"),
    ("06_seed.sql", "06_seed.sql", "بديل: الخطط وطرق الدفع والمهام والغرف"),
    ("07_seed_assets.sql", "07_seed_assets.sql", "بديل: 286 صفاً في site_assets (بدون الأفاتارات)"),
    ("12_avatars.sql", "12_avatars.sql", "الأفاتارات الـ25 (1 مجاني · 12 Epic · 12 Legendary)"),
    ("10_realtime.sql", "10_realtime.sql", "بديل: تشغيل المزامنة اللحظية"),
    ("11_grants.sql", "11_grants.sql", "⚠️ إلزامي للمشاريع الجديدة: منح صلاحيات anon/authenticated"),
    ("13_channels_icons.sql", "13_channels_icons.sql", "أسماء غرف الشات بلا إيموجي (الأيقونات داخل التطبيق)"),
    ("14_redeem_codes.sql", "14_redeem_codes.sql", "أكواد هدايا AC: جدول + دالتا توليد واستبدال"),
    ("16_language_room_names.sql", "16_language_room_names.sql",
     "أسماء غرف اللغة بلغتها (العربية · Русский · 中文 …) والغرف الأساسية بلا إيموجي"),
    ("15_assets_v2.sql", "15_assets_v2.sql",
     "الأصول الجديدة: يستبدل كل الأفاتارات والملصقات والأدوات بـ492 أصلاً في 21 قسماً"),
    ("08_admin_bootstrap.sql", "08_admin_bootstrap_example.sql", "النسخة العامة (you@example.com)"),
    ("09_gateway_functions.sql", "09_gateway_functions.sql", "اختياري: بوابات الدفع الإلكتروني"),
]

ADMIN_TEMPLATE = """-- ============================================================================
--  AddonStation Mobile — تعيين الأدمن (جاهز للنسخ واللصق)
--  البريد: {email}
--  ⚠️ نفّذ هذا الملف فقط بعد:
--     1) تنفيذ 00_ALL_IN_ONE.sql
--     2) إنشاء الحساب من لوحة Supabase بنفس هذا البريد (Authentication → Users → Add user)
--  إن نفّذته قبل إنشاء الحساب ستظهر: "لا يوجد مستخدم بهذا البريد" — والملف آمن للتكرار.
-- ============================================================================

do $$
declare
  v_email text := '{email}';   -- ✅ بريدك
  v_uid   uuid;
begin
  select id into v_uid from auth.users where email = v_email;
  if v_uid is null then
    raise notice 'لا يوجد مستخدم بهذا البريد: % — أنشئ الحساب أولاً', v_email;
    return;
  end if;

  insert into public.profiles (id, username, display_name)
  values (v_uid,
          coalesce(nullif(split_part(v_email,'@',1),''), 'player_' || substr(v_uid::text,1,8)),
          split_part(v_email,'@',1))
  on conflict (id) do nothing;

  insert into public.ac_wallets (user_id) values (v_uid) on conflict (user_id) do nothing;
  update public.profiles set role = 'admin' where id = v_uid;
  insert into public.admin_audit_log(actor_id, action, target_type, target_id, details)
  values (v_uid, 'admin_bootstrapped', 'profile', v_uid::text, jsonb_build_object('email', v_email));
  raise notice 'تم تعيين % مديراً (Admin) ✅', v_email;
end $$;

-- (اختياري) خطة أسطورية لك سنة كاملة لتجربة المحتوى المدفوع فوراً
update public.profiles
   set plan = 'legendary', plan_expires_at = now() + interval '365 days'
 where role = 'admin';

-- (اختياري) تفعيل تحويل بنكي للتجربة — كل الطرق معطّلة افتراضياً
update public.payment_method_settings
   set enabled = true,
       account_name = 'AddonStation',
       account_identifier = '091-0000000',
       instructions = 'حوّل المبلغ ثم ارفع صورة الإيصال ورقم العملية.'
 where code = 'bank_transfer';

-- ✅ تحقّق: الصق هذا وحده واضغط RUN
select p.id, p.username, p.display_name, p.role, p.plan, p.plan_expires_at, w.balance_ac
  from public.profiles p
  left join public.ac_wallets w on w.user_id = p.id
 where p.role = 'admin';
-- المتوقّع: صف واحد: role = admin · plan = legendary · balance_ac = 50
"""

README_TEMPLATE = """AddonStation Mobile — حزمة SQL الجاهزة لمشروع Supabase
تاريخ التجهيز: {date}
=================================================================

الخطوة 1) نفّذ الملف الجامع (5 دقائق)
-----------------------------------------------------------------
1. في Supabase: SQL Editor → New query
2. افتح 00_ALL_IN_ONE.sql (زر يمين → Open with → Notepad)
3. Ctrl+A ثم Ctrl+C  ثم الصقه في المحرر كاملاً → RUN
4. المتوقّع:  Success. No rows returned

❌ إن ظهر خطأ أحمر: توقّف وأرسل نص الخطأ حرفياً مع رقم السطر.
   البديل: نفّذ الملفات المنفصلة بهذا الترتيب:
   01_schema → 02_functions_core → 03_functions_chat → 04_rls
   → 05_storage → 06_seed → 07_seed_assets → 12_avatars
   → 10_realtime → 11_grants → 13_channels_icons → 14_redeem_codes

الخطوة 2) تحقّق (الصق هذا الاستعلام وحده)
-----------------------------------------------------------------
select
  (select count(*) from information_schema.tables where table_schema='public') as tables,    -- 35
  (select count(*) from storage.buckets)                                       as buckets,   -- 6
  (select count(*) from pg_policies where schemaname in ('public','storage'))  as policies,  -- 78
  (select count(*) from public.subscription_plans)                             as plans,     -- 3
  (select count(*) from public.chat_channels)                                  as rooms,     -- 13
  (select count(*) from public.ac_tasks)                                       as tasks,     -- 6
  (select count(*) from public.sticker_packs)                                  as packs,     -- 5
  (select count(*) from public.site_assets)                                    as assets;    -- 311

الخطوة 3) أنشئ حساب الأدمن
-----------------------------------------------------------------
من لوحة Supabase: Authentication → Users → Add user → Create new user
     Email:    {email}
     Password: (كلمة قوية)
     ✅ Auto Confirm User

الخطوة 4) اجعله مديراً
-----------------------------------------------------------------
افتح 08_admin_bootstrap_READY.sql → Ctrl+A → Ctrl+C → الصقه في SQL Editor → RUN
المتوقّع: تم تعيين {email} مديراً (Admin) ✅

الخطوة 5) (اختياري) أكواد هدايا AC — مضمَّنة في 14_redeem_codes.sql :
   ولّد الأكواد من: لوحة الإدارة → AC Management → «أكواد هدايا AC» → توليد الأكواد
   أو من الحاسوب: python3 tools/make_redeem_codes.py -n 5

الخطوة 6) (اختياري) بوابات الدفع — نفّذ 09_gateway_functions.sql لاحقاً.

أخطاء شائعة
-----------------------------------------------------------------
| already exists        | أعدت التنفيذ            | طبيعي وآمن — تجاوزه                 |
| syntax error near $$  | اللصق ناقص              | انسخ 00_ALL_IN_ONE.sql كاملاً        |
| لا يوجد مستخدم بهذا البريد | الحساب لم يُنشأ    | نفّذ الخطوة 3 ثم أعد المحاولة        |
"""

PAGE = """<!DOCTYPE html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>تحميل حزمة SQL — AddonStation</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;padding:26px 16px 60px;font-family:"Segoe UI",Tahoma,Arial,sans-serif;background:#0B0F14;color:#E8EEF5;line-height:1.9}
.wrap{max-width:760px;margin:0 auto}.logo{font-size:13px;letter-spacing:2px;color:#3DDC84;font-weight:700}
h1{font-size:25px;margin:8px 0 4px}.sub{color:#8EA0B3;font-size:15px;margin-bottom:22px}
.card{background:#131A24;border:1px solid #263240;border-radius:22px;padding:20px;margin-bottom:16px}
a.btn{display:block;text-align:center;text-decoration:none;font-weight:800;font-size:19px;padding:20px;border-radius:16px;color:#06210F;background:linear-gradient(180deg,#43E68C,#27A05E)}
.meta{display:flex;flex-wrap:wrap;gap:8px 20px;margin-top:14px;font-size:14px;color:#8EA0B3}.meta b{color:#E8EEF5}
table{width:100%;border-collapse:collapse;font-size:14px}td,th{padding:8px 6px;border-bottom:1px solid #212D3D;text-align:right;vertical-align:top}th{color:#8EA0B3;font-weight:600}
code{background:#1A2330;border:1px solid #263240;border-radius:7px;padding:2px 6px;font-family:Consolas,monospace;font-size:13px;direction:ltr;display:inline-block}
.ok{color:#3DDC84}.warn{background:#241A1A;border-color:#4A2A2A;color:#FFB4B4;font-size:14px}
.sha{direction:ltr;text-align:left;font-size:11px;color:#8EA0B3;word-break:break-all;margin-top:10px}
ol{padding-inline-start:20px;margin:0}li{margin-bottom:10px}
</style></head><body><div class="wrap">
<div class="logo">ADDONSTATION</div>
<h1>حزمة SQL الجاهزة لمشروع Supabase</h1>
<div class="sub">اضغط الزر الأخضر لتنزيل الحزمة المضغوطة، ثم اتبع الخطوات بالترتيب.</div>

<div class="card">
  <a class="btn" href="AddonStation-Supabase-SQL.zip">⬇️ &nbsp;تحميل الحزمة المضغوطة</a>
  <div class="meta"><span>الحجم: <b>__SIZE__ KB</b></span><span>عدد الملفات: <b>__COUNT__</b></span>
  <span>الأهم: <b>00_ALL_IN_ONE.sql</b></span></div>
  <div class="sha">SHA256: __SHA__</div>
</div>

<div class="card"><b>4 خطوات بعد فك الضغط</b><ol style="margin-top:10px">
<li>اقرأ <code>README-AR.txt</code>.</li>
<li><code>00_ALL_IN_ONE.sql</code> → Notepad → Ctrl+A/Ctrl+C → الصقه في <b>Supabase → SQL Editor</b> → <b>RUN</b>.<br><span class="ok">المتوقّع: Success. No rows returned</span></li>
<li>أنشئ الحساب: <b>Authentication → Users → Add user</b> بالبريد <code>__EMAIL__</code> + ✅ Auto Confirm User.</li>
<li><code>08_admin_bootstrap_READY.sql</code> → نسخ ولصق → <b>RUN</b>.<br><span class="ok">المتوقّع: تم تعيين المدير ✅</span></li>
</ol></div>

<div class="card"><b>محتويات الحزمة</b><table style="margin-top:8px"><tr><th>الملف</th><th>الوظيفة</th></tr>
__ROWS__
</table></div>

<div class="card warn">🔐 لا ترفع الحزمة إلى GitHub ولا تشاركها — تحتوي بريدك الإلكتروني. ولا تحتوي أي مفتاح سري.</div>
</div></body></html>
"""


def read_sql(name: str) -> bytes:
    with open(os.path.join(SUPABASE, name), "rb") as fh:
        return fh.read()


def build_zip(email: str) -> bytes:
    import datetime

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for src, zname, _ in ORDER:
            z.writestr("AddonStation-Supabase-SQL/" + zname, read_sql(src))
        if email:
            z.writestr("AddonStation-Supabase-SQL/08_admin_bootstrap_READY.sql",
                       ADMIN_TEMPLATE.format(email=email).encode("utf-8"))
        z.writestr("AddonStation-Supabase-SQL/README-AR.txt",
                   README_TEMPLATE.format(email=email or "you@example.com",
                                          date=datetime.date.today().isoformat()).encode("utf-8-sig"))
    return buf.getvalue()


def build_page(zip_bytes: bytes, email: str) -> bytes:
    rows = []
    for _src, zname, desc in ORDER:
        rows.append("<tr><td><b>%s</b></td><td>%s</td></tr>" % (zname, desc))
    if email:
        rows.append("<tr><td><b>08_admin_bootstrap_READY.sql</b></td>"
                    "<td>تعيينك مديراً — بريدك مكتوب داخله</td></tr>")
    rows.append("<tr><td><b>README-AR.txt</b></td><td>دليل التنفيذ + استعلامات التحقق + حل الأخطاء</td></tr>")
    count = len(rows)
    page = (PAGE
            .replace("__ROWS__", "\n".join(rows))
            .replace("__SIZE__", "%.1f" % (len(zip_bytes) / 1024.0))
            .replace("__COUNT__", str(count))
            .replace("__SHA__", hashlib.sha256(zip_bytes).hexdigest())
            .replace("__EMAIL__", email or "you@example.com"))
    return page.encode("utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--email", default="")
    ap.add_argument("--port", type=int, default=8000)
    args = ap.parse_args()

    zip_bytes = build_zip(args.email)
    page = build_page(zip_bytes, args.email)
    print("✓ الحزمة جاهزة في الذاكرة: %d ملفاً · %.1f KB · sha256=%s"
          % (len(ORDER) + (2 if args.email else 1),
             len(zip_bytes) / 1024.0,
             hashlib.sha256(zip_bytes).hexdigest()[:16]))

    class Handler(BaseHTTPRequestHandler):
        server_version = "AddonStationPack/1.0"

        def _send(self, code, ctype, body, extra=None, head_only=False):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            for k, v in (extra or {}).items():
                self.send_header(k, v)
            self.end_headers()
            if not head_only:
                self.wfile.write(body)

        def _route(self, body_only):
            path = self.path.split("?")[0]
            if path in ("/", "/index.html"):
                self._send(200, "text/html; charset=utf-8", page, head_only=body_only)
            elif path in ("/AddonStation-Supabase-SQL.zip", "/download"):
                self._send(200, "application/zip", zip_bytes,
                           {"Content-Disposition": 'attachment; filename="AddonStation-Supabase-SQL.zip"'},
                           head_only=body_only)
            else:
                self._send(404, "text/plain; charset=utf-8", "غير موجود".encode("utf-8"),
                           head_only=body_only)

        def do_GET(self):
            self._route(False)

        def do_HEAD(self):
            self._route(True)

        def log_message(self, fmt, *a):
            sys.stderr.write("[pack] %s\n" % (fmt % a))

    srv = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("→ الصفحة:   http://0.0.0.0:%d/" % args.port)
    print("→ التحميل:  http://0.0.0.0:%d/AddonStation-Supabase-SQL.zip" % args.port)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
