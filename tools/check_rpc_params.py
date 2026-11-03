#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: أسماء مُعاملات نداءات `rpc(...)` في التطبيق يجب أن تطابق أسماء
مُعاملات الدوال في ملفات SQL حرفياً.

PostgREST (واجهة REST في Supabase) يطابق الدالة بـ **أسماء المُعاملات** لا
بترتيبها، فأي اختلاف بسيط يظهر للمستخدم هكذا:

    Could not find the function public.update_my_profile(bio, custom_status,
    show_online, username) in the schema cache

أي أن `p_username` ≠ `username`. هذا الفاحص يمنع ذلك قبل أن يصل للمستخدم.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"
SQL = ROOT / "supabase"

# ------------------------------------------------------------------ SQL
FUNC_RE = re.compile(
    r"create\s+or\s+replace\s+function\s+public\.([a-z_][a-z_0-9]*)\s*\((.*?)\)\s*returns",
    re.S | re.I,
)
PARAM_LINE = re.compile(r"^\s*([a-z_][a-z_0-9]*)\s+[a-z_0-9]+", re.I)


def sql_functions() -> dict[str, set[str]]:
    out: dict[str, set[str]] = {}
    for f in sorted(SQL.glob("*.sql")):
        if f.name == "00_ALL_IN_ONE.sql":
            continue
        text = f.read_text(encoding="utf-8")
        for m in FUNC_RE.finditer(text):
            name = m.group(1)
            params = set()
            # التوقيع قد يكون سطراً واحداً أو متعدّد الأسطر ⇒ نقسّم بالفواصل
            # بعد حذف محتوى الأقواس (numeric(10,2) لا تُقسَم)
            block = re.sub(r"\([^)]*\)", "", m.group(2))
            for part in block.split(","):
                part = part.strip()
                if not part or part.startswith("--"):
                    continue
                pm = PARAM_LINE.match(part)
                if pm:
                    params.add(pm.group(1))
            if params:
                out.setdefault(name, set()).update(params)
    return out


# --------------------------------------------------------------- Kotlin
RPC_RE = re.compile(r'\.rpc\(\s*\n?\s*"([a-z_][a-z_0-9]*)"\s*,\s*', re.S)
KEY_RE = re.compile(r'"([a-z_][a-z_0-9]*)"\s+to\s')


def arg_block(text: str, i: int) -> str:
    """يقطع كتلة المُعاملات (params(...) / mapOf(...)) بأقواس متوازنة."""
    while i < len(text) and text[i] in " \n\t\r":
        i += 1
    if not (text.startswith("params(", i) or text.startswith("mapOf(", i)):
        return ""
    start = text.index("(", i)
    depth = 0
    for j in range(start, len(text)):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[start : j + 1]
    return text[start : start + 2000]


def app_calls() -> dict[str, dict[str, list[str]]]:
    """اسم الدالة ⇒ {المُعامل: [أماكن النداء]}"""
    out: dict[str, dict[str, list[str]]] = {}
    for kt in sorted(SRC.rglob("*.kt")):
        text = kt.read_text(encoding="utf-8")
        for m in RPC_RE.finditer(text):
            name = m.group(1)
            block = arg_block(text, m.end())
            line_no = text[: m.start()].count("\n") + 1
            for key in KEY_RE.findall(block):
                out.setdefault(name, {}).setdefault(key, []).append(
                    f"{kt.relative_to(ROOT)}:{line_no}"
                )
    return out


def main() -> int:
    defs = sql_functions()
    calls = app_calls()
    bad: list[str] = []

    for name, keys in sorted(calls.items()):
        if name not in defs:
            # دالة مشروطة/مُعرَّفة في ملف تفريغات (functions/*) — نتجاوزها
            continue
        allowed = defs[name]
        for key, places in sorted(keys.items()):
            if key not in allowed:
                bad.append(
                    f"public.{name}: التطبيق يرسل «{key}» وهو غير موجود في SQL "
                    f"({', '.join(sorted(allowed))})\n      ⇒ {places[0]}"
                )

    if bad:
        print("✗ أسماء مُعاملات RPC لا تطابق SQL:")
        for b in bad:
            print("   ", b)
        print("\nسيظهر للمستخدم: Could not find the function public.… in the schema cache")
        return 1

    total = sum(len(v) for v in calls.values())
    print(f"✓ كل مُعاملات RPC مطابقة لـ SQL ({len(calls)} دالة · {total} مُعاملاً)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
