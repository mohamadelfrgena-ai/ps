#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يولّد أكواد هدايا AC خارج التطبيق (نفس تنسيق دالة ac_random_code داخل قاعدة البيانات).

التنسيق: 8 خانات = 6 حروف كبيرة/صغيرة + رقمان — مثال: Ac7Kp2Qm
استُبعدت الحروف المتشابهة I/O/l/o والأرقام 0/1 لمنع الخطأ عند الكتابة.

المخرجات (داخل مجلد --out):
    redeem_codes.sql   ← الصقه في Supabase → SQL Editor → Run
    redeem_codes.csv   ← لجدول أو حملة تسويقية (الأكواد بلا أعمدة)

أمثلة:
    python3 tools/make_redeem_codes.py --amount 30000 -n 10    # 10 أكواد فئة 30,000
    python3 tools/make_redeem_codes.py --tier 30000:10 --tier 15000:30 \
        --tier 10000:50 --tier 5000:100 --zip AC-codes.zip     # عدد مختلف لكل فئة
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


def build(tiers, max_uses, note):
    """tiers: قائمة أزواج (الفئة, العدد) — مثل [(30000, 10), (15000, 30)]."""
    rows = []
    seen = set()
    for amount, count in tiers:
        for _ in range(count):
            while True:
                code = make_code()
                if code.upper() not in seen:
                    seen.add(code.upper())
                    break
            rows.append((code, amount, max_uses, note))
    return rows


def parse_tier(text):
    """يقرأ «30000:10» أو «30000x10» → (30000, 10)."""
    for sep in (":", "x", "X", "*"):
        if sep in text:
            amount, count = text.split(sep, 1)
            return int(amount.strip()), int(count.strip())
    raise argparse.ArgumentTypeError(f"صيغة غير صحيحة: {text} — استخدم مثل 30000:10")


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
    lines.append("select amount, count(*) as codes, sum(used_count) as uses")
    lines.append("  from public.ac_redeem_codes group by amount order by amount desc;")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def write_list(path, rows):
    """ملف نصي مرتّب حسب الفئة (للطباعة أو التوزيع اليدوي)."""
    from collections import OrderedDict
    groups = OrderedDict()
    for code, amount, _uses, _note in rows:
        groups.setdefault(amount, []).append(code)
    out = [
        "AddonStation — أكواد هدايا AC",
        "=" * 58,
        "",
        "⚠️ محمي: المستودع الخاص فقط — لا تنشر هذه الأكواد ولا تجعل المستودع عاماً.",
        "المكان الرسمي: مجلد ac-codes/ داخل المستودع الخاص (وتظهر أيضاً في لوحة الإدارة).",
        "",
    ]
    for amount, codes in groups.items():
        out.append(f"فئة {amount:,} AC — {len(codes)} كوداً")
        out.append("-" * 58)
        for i in range(0, len(codes), 4):
            out.append("   ".join(f"{c:<10}" for c in codes[i:i + 4]).rstrip())
        out.append("")
    total = sum(amount for _c, amount, _u, _n in rows)
    out.append("=" * 58)
    out.append(f"الإجمالي: {len(rows)} كوداً — {total:,} AC")
    with open(path, "w", encoding="utf-8-sig") as fh:
        fh.write("\n".join(out) + "\n")


def main():
    ap = argparse.ArgumentParser(description="توليد أكواد هدايا AC (حروف كبيرة/صغيرة + رقمان)")
    ap.add_argument("-n", "--per-tier", type=int, default=10, help="عدد الأكواد لكل فئة قيمة (افتراضي 10)")
    ap.add_argument("--amount", type=int, action="append", help="فئة قيمة مخصّصة (يمكن تكرارها)")
    ap.add_argument("--tier", type=parse_tier, action="append",
                    help="فئة وعددها معاً: 30000:10 (يمكن تكرارها لكل فئة عدد مختلف)")
    ap.add_argument("--max-uses", type=int, default=1, help="أقصى عدد مستخدمين لكل كود (افتراضي 1)")
    ap.add_argument("--note", default=None, help="وصف داخلي يُحفظ مع الأكواد (اختياري)")
    ap.add_argument("--out", default="redeem-codes", help="مجلد الإخراج (افتراضي redeem-codes/)")
    ap.add_argument("--zip", dest="zip", default=None, help="اسم ملف مضغوط يجمع المخرجات (مثل AC-codes.zip)")
    args = ap.parse_args()

    if args.tier:
        tiers = [(int(a), max(1, int(c))) for a, c in args.tier]
    else:
        tiers = [(a, max(1, args.per_tier)) for a in (args.amount or TIERS)]
    rows = build(tiers, max(1, args.max_uses), args.note)

    out_dir = os.path.abspath(args.out)
    os.makedirs(out_dir, exist_ok=True)
    sql_path = os.path.join(out_dir, "redeem_codes.sql")
    csv_path = os.path.join(out_dir, "redeem_codes.csv")
    txt_path = os.path.join(out_dir, "redeem_codes.txt")

    write_sql(sql_path, rows)
    write_list(txt_path, rows)
    with open(csv_path, "w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["code", "amount_ac", "max_uses", "note"])
        writer.writerows(rows)

    total = sum(amount for _code, amount, _uses, _note in rows)
    print(f"✓ {len(rows)} كوداً — قيمة إجمالية {total:,} AC")
    for amount, count in tiers:
        print(f"   فئة {amount:>8,} AC  ×  {count}")
    for code, amount, _uses, _note in rows[:3]:
        print(f"   مثال: {code}   {amount:,} AC")
    print(f"\n📄 SQL (الصقه في SQL Editor → Run): {sql_path}")
    print(f"📄 TXT (مرتّب للطباعة): {txt_path}")
    print(f"📄 CSV (لجدول أو حملة): {csv_path}")
    if args.zip:
        import zipfile
        zip_path = os.path.join(out_dir, args.zip)
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
            for f in (sql_path, txt_path, csv_path):
                z.write(f, os.path.basename(f))
        print(f"🗜️ ZIP: {zip_path}")
    if os.path.abspath(out_dir) == os.path.abspath("redeem-codes"):
        print("🔒 المجلد redeem-codes/ مستثنى من Git — الأكواد لا تُرفع إلى المستودع.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
