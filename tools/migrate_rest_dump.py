#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""استخراج بيانات كل جداول public من مشروع Supabase عبر PostgREST (service key)
وتحويلها إلى SQL جاهز للحقن في المشروع الجديد.

لا يحتاج كلمة سر قاعدة البيانات إطلاقاً — service_role يتجاوز RLS ويقرأ كل شيء.

الاستعمال:
    python3 migrate_rest_dump.py <base_url> <service_key> <outdir>
المخرجات داخل <outdir>:
    rest-data.sql   إدراج كل الصفوف (json_populate_recordset)
    truncate.sql    تفريغ الجداول قبل الإدراج (يحلّ محل بيانات الـ seed)
    counts.txt      عدد صفوف كل جدول
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

TABLES = [
    "profiles", "subscription_plans", "payment_method_settings", "app_settings",
    "ac_tasks", "ac_wallets", "ac_transactions", "ac_task_claims",
    "ac_redeem_codes", "ac_redeem_uses", "ac_events",
    "content_items", "content_downloads", "content_favorites", "content_import_batches",
    "store_banners", "site_assets", "sticker_packs",
    "chat_channels", "chat_messages", "chat_reactions", "chat_read_state",
    "chat_notifications", "chat_rules", "chat_rule_acceptances", "chat_typing",
    "chat_bans", "chat_mutes", "chat_moderator_rooms", "chat_reports",
    "console_link_codes", "console_link_audit", "linked_consoles",
    "payment_orders", "admin_audit_log",
    "push_config", "push_devices", "push_prefs", "push_queue",
    "user_blocks", "user_friends", "user_mutes",
    "creator_applications", "creator_follows", "creator_submissions",
    "creator_tips", "featured_creators",
]

PAGE = 1000


def fetch_all(base, key, table):
    rows, offset = [], 0
    while True:
        url = f"{base}/rest/v1/{urllib.parse.quote(table)}?select=*"
        req = urllib.request.Request(url, headers={
            "apikey": key, "Authorization": f"Bearer {key}",
            "Range": f"{offset}-{offset + PAGE - 1}", "Range-Unit": "items",
            "Accept": "application/json",
        })
        with urllib.request.urlopen(req, timeout=120) as resp:
            batch = json.loads(resp.read() or b"[]")
        rows.extend(batch)
        if len(batch) < PAGE:
            return rows
        offset += PAGE


def main():
    if len(sys.argv) != 4:
        print("usage: migrate_rest_dump.py <base_url> <service_key> <outdir>", file=sys.stderr)
        sys.exit(2)
    base, key, outdir = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]

    # تحقق سريع من المفتاح
    try:
        probe = urllib.request.Request(f"{base}/rest/v1/", headers={"apikey": key, "Authorization": f"Bearer {key}"})
        urllib.request.urlopen(probe, timeout=30)
    except urllib.error.HTTPError as e:
        print(f"::error title=REST::المفتاح مرفوض ({e.code}) — تحقق من ADDONSTATION_SUPABASE_SECRET_KEY", file=sys.stderr)
        sys.exit(1)

    data_sql = [
        "-- بيانات المشروع القديم (استخراج REST بلا كلمة سر) — أُنشئت تلقائياً",
        "-- كل جدول في معاملة مستقلة: فشل جدول واحد لا يُسقط الباقي",
    ]
    counts = []
    empty_tables = []
    for t in TABLES:
        rows = None
        for attempt in (1, 2):
            try:
                rows = fetch_all(base, key, t)
                break
            except urllib.error.HTTPError as e:
                if attempt == 2:
                    print(f"::warning title=REST::الجدول {t} غير متاح ({e.code}) — تخطّي", file=sys.stderr)
                else:
                    import time; time.sleep(2)
        if rows is None:
            continue
        counts.append(f"{t}={len(rows)}")
        if not rows:
            empty_tables.append(t)
            continue
        blob = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
        blob = blob.replace("'", "''")
        data_sql.append(f"-- TABLE {t} rows={len(rows)}")
        data_sql.append("begin;")
        data_sql.append("set local session_replication_role = replica;")
        data_sql.append(
            f"insert into public.{t} "
            f"select * from json_populate_recordset(null::public.{t}, '{blob}'::json);"
        )
        data_sql.append("commit;")

    with open(f"{outdir}/rest-data.sql", "w", encoding="utf-8") as f:
        f.write("\n".join(data_sql) + "\n")
    with open(f"{outdir}/truncate.sql", "w", encoding="utf-8") as f:
        live = [c.split("=")[0] for c in counts if not c.endswith("=0")]
        f.write("begin;\nset local session_replication_role = replica;\n")
        f.write("truncate table " + ", ".join(f"public.{t}" for t in live) + " cascade;\ncommit;\n")
    with open(f"{outdir}/counts.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(counts) + "\n")

    total = sum(int(c.split("=")[1]) for c in counts)
    print(f"::notice title=استخراج REST::{len(counts)} جدولاً · {total} صفّاً · جداول فارغة: {len(empty_tables)}")
    print(f"::notice title=الجدول::{' '.join(counts)}")


if __name__ == "__main__":
    main()
