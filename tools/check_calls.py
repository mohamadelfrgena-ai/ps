#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فحص نداءات الدوال في واجهة Compose بدون مترجم:
  - كل وسيط مُسمّى في النداء موجود في تعريف الدالة
  - كل وسيط إلزامي (بلا قيمة افتراضية) مُمرَّر فعلاً
  - عدد الوسائط الموضعية لا يتجاوز عدد المعاملات

الاستخدام: python3 tools/check_calls.py [مسار_الجذر]
"""
import os
import re
import sys
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
KOTLIN = os.path.join(ROOT, "android/app/src/main/java")


def mask(src: str) -> str:
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                j = n if j < 0 else j + 3
                for k in range(i, j):
                    if out[k] != "\n":
                        out[k] = " "
                i = j
                continue
            i += 1
            while i < n:
                if src[i] == "\\":
                    out[i] = " "
                    if i + 1 < n and out[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def strip_generics(text: str) -> str:
    """يحذف <...> المتوازنة (مع تجاهل -> في الأنواع الدالية) حتى لا تكسر تقسيم المعاملات."""
    out, depth = [], 0
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "-" and i + 1 < len(text) and text[i + 1] == ">":
            out.append("->")
            i += 2
            continue
        if ch == "<":
            depth += 1
            i += 1
            continue
        if ch == ">" and depth > 0:
            depth -= 1
            i += 1
            continue
        if depth == 0:
            out.append(ch)
        i += 1
    return "".join(out)


def split_top(body: str):
    parts, depth, cur = [], 0, []
    for ch in body:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
            continue
        cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def parse_params(body: str):
    """يرجع (params, required_names, positional_count)."""
    params, required = [], []
    for part in split_top(strip_generics(body)):
        text = part.strip()
        if not text or text.startswith("//"):
            continue
        # تجاهل علامات التعليق/الوصف
        m = re.match(r"(?:@\w+(?:\([^)]*\))?\s*)*(?:vararg\s+|crossinline\s+|noinline\s+)?"
                     r"([a-zA-Z_][\w]*)\s*((?::\s*[^=]+)?)(=\s*[\s\S]+)?$", text)
        if not m:
            continue
        name = m.group(1)
        has_default = m.group(3) is not None
        params.append(name)
        if not has_default:
            required.append(name)
    return params, required


def main() -> int:
    decls = defaultdict(list)
    files = {}
    for root, _, fs in os.walk(KOTLIN):
        for f in fs:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(root, f)
            raw = open(path, encoding="utf-8").read()
            files[path] = raw
            src = mask(raw)
            for m in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_][\w]*)\s*\(", src):
                start = m.end()
                depth, i = 1, start
                while i < len(src) and depth > 0:
                    if src[i] == "(":
                        depth += 1
                    elif src[i] == ")":
                        depth -= 1
                    i += 1
                params, required = parse_params(src[start:i - 1])
                decls[m.group(1)].append((path, params, required))

    problems = []
    for path, raw in files.items():
        short = path.split("mobile/")[-1]
        src = mask(raw)
        for m in re.finditer(r"(?<![\w.])(?<!fun )([A-Z][A-Za-z0-9_]*)\s*\(", src):
            name = m.group(1)
            if name not in decls or name in ("Icons",):
                continue
            # تجاهل التعريفات
            before = src[max(0, m.start() - 24):m.start()]
            if re.search(r"fun\s+$", before) or "class " in before or "interface " in before:
                continue
            start = m.end()
            depth, i = 1, start
            while i < len(src) and depth > 0:
                if src[i] == "(":
                    depth += 1
                elif src[i] == ")":
                    depth -= 1
                i += 1
            body = src[start:i - 1]
            trailing = False
            j = i
            while j < len(src) and src[j] in " \n\t":
                j += 1
            if j < len(src) and src[j] == "{":
                trailing = True
            args = [a.strip() for a in split_top(body) if a.strip()]
            if trailing:
                args.append("__trailing_lambda__")
            named = {}
            positional = 0
            for a in args:
                if a == "__trailing_lambda__":
                    continue
                nm = re.match(r"([a-zA-Z_][\w]*)\s*=(?!=)", a)
                if nm:
                    named[nm.group(1)] = True
                else:
                    positional += 1
            line = raw[:m.start()].count("\n") + 1
            ok_any = False
            for (_p, params, required) in decls[name]:
                if any(k not in params for k in named):
                    continue
                trailing_param = params[-1] if trailing and params else None
                available = [x for x in params if x != trailing_param]
                if positional > len(available):
                    continue
                provided = set(named) | set(available[:positional])
                if trailing_param is not None:
                    provided.add(trailing_param)
                if any(r not in provided for r in required):
                    continue
                ok_any = True
                break
            if not ok_any:
                sigs = [f"({', '.join(p)})" for (_p, p, _r) in decls[name][:2]]
                problems.append(
                    f"{short}:{line}  نداء {name}(...) لا يطابق التعريف: "
                    f"وسائط مُسمّاة={sorted(named)} موضعية={positional} — التعريف: {sigs}"
                )

    if problems:
        print(f"✗ عدد مشاكل النداءات: {len(problems)}")
        for p in sorted(set(problems)):
            print("   ", p)
        return 1
    print("✓ كل نداءات الدوال تطابق تعريفاتها")
    return 0


if __name__ == "__main__":
    sys.exit(main())
