#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يفحص مجلد الموارد res/ قبل البناء — لأن أي خطأ هنا يُفشل مهمّة
`mergeDebugResources` ورسالته في GitHub غامضة.

يفحص:
  1) أسماء الملفات والمجلدات: حروف صغيرة وأرقام وشرطة سفلية فقط
     (ملف مثل res/font/FONT_LICENSE_OFL.txt يُفشل البناء)
  2) سلامة XML لكل ملفات values*/ (خطأ & أو < غير مهرّب)
  3) تكرار اسم المورد داخل الملف أو بين ملفات مجلد القيم نفسه
  4) صيغ % غير الصحيحة (مثل %5$s أو %s% في نص غير مُهيّأ)
  5) فواصل عليا ' أو علامات اقتباس " غير مهرّبة داخل النصوص
  6) نصوص تبدأ بـ @ أو ? (تُفسَّر كمرجع مورد)

الاستخدام:  python3 tools/check_resources.py
"""

import collections
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

NAME_RE = re.compile(r"^[a-z0-9_.]+$")
FMT_RE = re.compile(r"%(?!(\d+\$)?[sdf]|%)")
STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)


def main() -> int:
    if not os.path.isdir(RES):
        print("✗ مجلد res غير موجود:", RES)
        return 1

    problems = []

    # 1) أسماء الملفات (والملفات فقط): حروف صغيرة/أرقام/شرطة سفلية/نقطة
    #    ملاحظة: المجلدات يجوز أن تحتوي شرطة لأنها مُحدِّدات مثل values-ar و mipmap-xxhdpi
    for root, dirs, files in os.walk(RES):
        for name in files:
            if not NAME_RE.match(name):
                rel = os.path.relpath(os.path.join(root, name), RES)
                problems.append(f"اسم مورد غير صالح (يجب حروف صغيرة/أرقام/نقطة/شرطة سفلية): res/{rel}")

    # 2..6 لكل ملفات القيم
    value_files = []
    resources_by_folder = collections.defaultdict(list)
    for root, _dirs, files in os.walk(RES):
        if os.path.basename(root).startswith("values"):
            for f in files:
                if f.endswith(".xml"):
                    value_files.append(os.path.join(root, f))
    value_files.sort()

    for path in value_files:
        rel = os.path.relpath(path, ROOT)
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            problems.append(f"XML تالف: {rel} → {exc}")
            continue

        names = []
        for el in tree.getroot():
            name = el.get("name")
            resource_type = el.get("type") if el.tag == "item" else el.tag
            if name and el.tag not in {"public", "eat-comment"}:
                resources_by_folder[(os.path.dirname(path), resource_type, name)].append(rel)
            if el.tag != "string":
                continue
            name = name or "?"
            names.append(name)
            text = el.text or ""

            # Android String.format escapes a literal percent as "%%". Remove
            # those complete escapes before checking placeholders so the last
            # percent of "%1$d%%" is not mistaken for a dangling conversion.
            bad = FMT_RE.search(text.replace("%%", ""))
            if bad:
                problems.append(f"صيغة % غير صحيحة: {rel} · {name} → {text[:50]!r}")

            if text.strip().startswith(("@", "?")):
                problems.append(f"نص يبدأ بـ @ أو ? (يُفسَّر كمرجع مورد): {rel} · {name}")

        dups = [n for n, c in collections.Counter(names).items() if c > 1]
        for n in dups:
            problems.append(f"اسم مورد مكرر داخل نفس الملف: {rel} · {n}")

        raw = open(path, encoding="utf-8").read()
        for m in STRING_RE.finditer(raw):
            name, body = m.group(1), m.group(2)
            if '"' in body:
                problems.append(f'علامة اقتباس غير مهرّبة: {rel} · {name} → {body[:50]!r}')
            if re.search(r"(?<!\\)'", body):
                problems.append(f"فاصلة عليا غير مهرّبة (استخدم \\'): {rel} · {name} → {body[:50]!r}")

    for (folder, resource_type, name), paths in resources_by_folder.items():
        unique_paths = sorted(set(paths))
        if len(unique_paths) > 1:
            folder_rel = os.path.relpath(folder, ROOT)
            problems.append(
                f"اسم مورد مكرر بين ملفات مجلد القيم نفسه: {folder_rel} · {resource_type}/{name} → "
                + ", ".join(unique_paths)
            )

    if problems:
        print("🔴 مشاكل في الموارد (ستُفشل mergeDebugResources):\n")
        for p in problems[:60]:
            print("  ✗", p)
        if len(problems) > 60:
            print(f"  … و{len(problems) - 60} مشكلة أخرى")
        return 1

    print(f"🟢 الموارد سليمة: {len(value_files)} ملف values · الأسماء وXML والصيغ كلها صحيحة")
    return 0


if __name__ == "__main__":
    sys.exit(main())
