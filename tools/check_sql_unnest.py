#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: يمنع تكرار خطأ إرسال الرسالة
      ERROR: column "unnest" does not exist  (SQLSTATE 42703)

السبب: في PostgreSQL أسماء أعمدة المخرجات (`select unnest(x)` ⇒ عمود اسمه
`unnest`) **غير مرئية في WHERE** (تُرى في ORDER BY/GROUP BY فقط). فكتابة

        where unnest is not null

تُفشل الدالة عند أول تنفيذ. الحل: الدالة في عبارة FROM مع اسم مستعار:

        from unnest(coalesce(v, '{}'::uuid[])) as m where m is not null

هذا الفاحص يرفض أي سطر يحوي النمط المكسور — عدا العرض المتعمَّد داخل
`25_fix_send_chat_message_unnest.sql` (سطر `perform array(...)` داخل
كتلة exception لإثبات الخطأ).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "supabase"

# where <دالة مُعادة للصفوف> is not null  (بمسافات مرنة)
BAD = re.compile(
    r"\bwhere\s+(unnest|generate_series|generate_subscripts|regexp_matches|"
    r"jsonb_array_elements|json_array_elements|string_to_array|regexp_split_to_table)"
    r"\s+is\s+not\s+null",
    re.IGNORECASE,
)

# العرض المتعمَّد للخطأ داخل ملف الترحيل (داخل exception when undefined_column)
DEMO = re.compile(r"^\s*perform\s+array\(\s*select\s+distinct\s+unnest\(", re.IGNORECASE)


def strip_strings(line: str) -> str:
    """
    يحذف النصوص المُقتبسة من السطر قبل الفحص.

    لماذا؟ لأن ملف `99_VERIFY_ALL.sql` يبحث عن النمط المكسور **كنصّ** داخل
    `ilike '%where unnest is not null%'` — وهو استعمال سليم (فحص إصلاح)،
    فلا يجوز أن يُفشل الفاحص.
    """
    out, i, n = [], 0, len(line)
    while i < n:
        if line[i] == "'":
            i += 1
            while i < n:
                if line[i] == "'":
                    if i + 1 < n and line[i + 1] == "'":
                        i += 2
                        continue
                    i += 1
                    break
                i += 1
            out.append("''")
            continue
        out.append(line[i])
        i += 1
    return "".join(out)


def main() -> int:
    bad: list[str] = []
    files = sorted([*SQL_DIR.glob("*.sql"), *SQL_DIR.glob("functions/**/*.ts")])

    for f in files:
        try:
            text = f.read_text(encoding="utf-8")
        except Exception:
            continue
        for i, line in enumerate(text.split("\n"), 1):
            stripped = line.strip()
            # التعليقات لا تُنفَّذ (ملف الترحيل يشرح الخطأ القديم داخل تعليق)
            if stripped.startswith("--"):
                continue
            if DEMO.match(line):
                continue
            if BAD.search(strip_strings(line)):
                bad.append(f"{f.relative_to(ROOT)}:{i}: {line.strip()}")

    if bad:
        print("✗ نمط مكسور — أسماء الدوال المُعادة للصفوف غير مرئية في WHERE:")
        for b in bad:
            print("   ", b)
        print("\nاكتبها هكذا:  from unnest(arr) as m where m is not null")
        return 1

    print(f"✓ لا نمط `where <دالة صفوف> is not null` في {len(files)} ملف SQL/TS "
          "(لا خطأ column \"unnest\" does not exist)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
