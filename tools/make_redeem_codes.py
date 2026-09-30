#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يولّد أكواد هدايا AC خارج التطبيق (نفس تنسيق دالة ac_random_code داخل قاعدة البيانات).

التنسيق: 8 خانات = 6 حروف كبيرة/صغيرة + رقمان — مثال: Ac7Kp2Qm
استُبعدت الحروف المتشابهة I/O/l/o والأرقام 0/1 لمنع الخطأ عند الكتابة.

المخرجات (داخل مجلد --out):
    redeem_codes.sql   ← الصقه في Supabase → SQL Editor → Run
    redeem_codes.csv   ← لجدول أو حملة تسويقية (الأكواد بلا أعمدة)

أمثلة:
    python3 tools/make_redeem_codes.py                 # 5 أكواد من كل فئة
    python3 tools/make_redeem_codes.py -n 20           # 20 كوداً من كل فئة
    python3 tools/make_redeem_codes.py --amount 50000 -n 3
"""
import argparse
import csv
import os
import secrets
import sys

UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"    # 24 حرفاً (بلا I و O)
LOWER = "abcdefghijkmnpqrstuvwxyz"    # 24 حرفاً (بلا l و o)
DIGIT = "23456789"                    # 8 أرقام (بلا 0 و 1)

TIERS = [5000, 15000, 30000, 100000]


def make_code() -> str:
    """كود مضمون: حرف كبير + حرف صغير + 4 حروف + رقمان، بترتيب مخلوط."""
    chars = [secrets.choice(UPPER), secrets.choice(LOWER)]
    for _ in range(4):
        chars.append(secrets.choice(UPPER + LOWER))
    chars += [secrets.choice(DIGIT), secrets.choice(DIGIT)]
    secrets.SystemRandom().shuffle(chars)
    return "".join(chars)


def build(amounts, per_tier, max_uses, note):
    rows = []
    seen = set()
    for amount in amounts:
        for _ in range(per_tier):
            while True:
                code = make_code()
                if code.upper() not in seen:
                    seen.add(code.upper())
                    break
            rows.append((code, amount, max_uses, note))
    return rows


def write_sql(path, rows):
    lines = [
        "-- أكواد هدايا AC — مولَّدة بـ tools/make_redeem_codes.py",
        "-- نفّذها في Supabase → SQL Editor → Run  (بعد تنفيذ supabase/14_redeem_codes.sql)",
        "-- كل كود يُستخدم مرة واحدة لكل مستخدم (max_uses = عدد المستخدمين المسموح لهم).",
        "",
        "insert into public.ac_redeem_codes (code, amount, max_uses, note)",
        "values",
    ]
    body = []
    for code, amount, max_uses, note in rows:
        note_sql = "null" if not note else "'" + note.replace("'", "''") + "'"
        body.append(f"  ('{code}', {amount}, {max_uses}, {note_sql})")
    lines.append(",\n".join(body))
    lines.append("on conflict (code) do nothing;")
    lines.append("")
    lines.append("-- تحقق:")
    lines.append("select amount, count(*) as codes from public.ac_redeem_codes group by amount order by amount;")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser(description="توليد أكواد هدايا AC (حروف كبيرة/صغيرة + رقمان)")
    ap.add_argument("-n", "--per-tier", type=int, default=5, help="عدد الأكواد لكل فئة قيمة (افتراضي 5)")
    ap.add_argument("--amount", type=int, action="append", help="فئة قيمة مخصّصة (يمكن تكرارها)")
    ap.add_argument("--max-uses", type=int, default=1, help="أقصى عدد مستخدمين لكل كود (افتراضي 1)")
    ap.add_argument("--note", default=None, help="وصف داخلي يُحفظ مع الأكواد (اختياري)")
    ap.add_argument("--out", default="redeem-codes", help="مجلد الإخراج (افتراضي redeem-codes/)")
    args = ap.parse_args()

    amounts = args.amount or TIERS
    rows = build(amounts, max(1, args.per_tier), max(1, args.max_uses), args.note)

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)
    sql_path = os.path.join(out_dir, "redeem_codes.sql")
    csv_path = os.path.join(out_dir, "redeem_codes.csv")

    write_sql(sql_path, rows)
    with open(csv_path, "w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["code", "amount_ac", "max_uses", "note"])
        writer.writerows(rows)

    total = sum(amount for _code, amount, _uses, _note in rows)
    print(f"✓ {len(rows)} كوداً ({len(amounts)} فئة × {args.per_tier}) — قيمة إجمالية {total:,} AC")
    for code, amount, _uses, _note in rows[:5]:
        print(f"   {code}   {amount:,} AC")
    if len(rows) > 5:
        print(f"   … و{len(rows) - 5} كوداً آخر")
    print(f"\n📄 SQL (الصقه في SQL Editor): {sql_path}")
    print(f"📄 CSV: {csv_path}")
    if args.out == "redeem-codes":
        print("ℹ️ المجلد redeem-codes/ مُستثنى من Git — الأكواد تبقى عندك ولا تُرفع للمستودع.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
