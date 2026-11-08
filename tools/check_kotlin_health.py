#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص «سلامة الكود» — شبكة أمان قبل البناء (لا نستطيع التصريف هنا):

    • كل ملف Kotlin: أقواسه متوازنة (لا قوس ناقص يكسر التصريف)؛
    • لا تعريف نوع علوي أو استيراد مكرّر (يُسقط التصريف)؛
    • كل ملف يبدأ بـ package com.addonstation.mobile؛
    • لا «قُمامة» مولّدات: سطر تعليق عربي فيه محارف صينية/يابانية/كورية
      (تحدث حين يُخلط نصّان في سطر واحد) — أمّا ملفات اللغة الصينية
      الشرعية فلا تُلام؛
    • لا TODO/FIXME منسيّ.

الفحص سريع ويعمل على كل الملفات دفعة واحدة.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"


def strip_noise(src: str) -> str:
    """احذف السلاسل والتعليقات حتى لا تُربك حساب الأقواس."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n and not (src[i] == '"' and src[i - 1] != "\\"):
                i += 1
            i += 1
        elif c == "'":
            # Kotlin char literals can contain escaped characters (for example
            # '\\\\'); consume escapes as pairs so the escaped quote is not
            # mistaken for the end of the literal.
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                elif src[i] == "'":
                    i += 1
                    break
                else:
                    i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def balanced(src: str) -> bool:
    pairs = {")": "(", "]": "[", "}": "{"}
    stack: list[str] = []
    for c in strip_noise(src):
        if c in "([{":
            stack.append(c)
        elif c in ")]}":
            if not stack or stack.pop() != pairs[c]:
                return False
    return not stack


CJK = re.compile(r"[　-〿㐀-鿿぀-ヿ]")
ARABIC = re.compile(r"[؀-ۿ]")


def garbled(line: str) -> bool:
    """
    نصّ «مخلوط» = محارف آسيوية **ملتصقة بكلام عربي** في نفس السطر.

    أمّا قوائم أسماء الغرف بلغاتها (العربية · Русский · 中文) فشرعية:
    المحارف الآسيوية فيها مفصولة بـ «·» ولا تلتصق بعربي.
    """
    for m in CJK.finditer(line):
        window = line[max(0, m.start() - 3): m.start()]
        if ARABIC.search(window):
            return True
    return False


def main() -> int:
    problems: list[str] = []
    files = sorted(SRC.rglob("*.kt"))
    if not files:
        print("✗ لم أجد ملفات Kotlin")
        return 1

    top_level_declarations: dict[tuple[str, str], Path] = {}

    for f in files:
        text = f.read_text(encoding="utf-8")
        rel = f.relative_to(ROOT)

        if not balanced(text):
            problems.append(f"أقواس غير متوازنة · {rel}")

        # الملف قد يبدأ بـ @file:OptIn(...) قبل تصريح الحزمة — هذا شرعي
        head = "\n".join(
            line for line in text.split("\n")
            if line.strip() and not line.strip().startswith("@file:")
        )
        if not head.lstrip().startswith("package com.addonstation.mobile"):
            problems.append(f"تصريح package ناقص · {rel}")

        package_match = re.search(r"^package\s+([\w.]+)", text, re.M)
        package_name = package_match.group(1) if package_match else ""
        # Kotlin does not permit two top-level type declarations with the same
        # name in one package. Catch this before spending minutes on Gradle.
        declaration_source = strip_noise(text)
        declaration_pattern = re.compile(
            r"^(?:(?:data|sealed|enum|annotation|value)\s+)?"
            r"(?:class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)",
            re.M,
        )
        for declaration in declaration_pattern.finditer(declaration_source):
            name = declaration.group(1)
            key = (package_name, name)
            previous = top_level_declarations.get(key)
            if previous is not None:
                problems.append(
                    f"تعريف نوع مكرّر · {rel} → {package_name}.{name} "
                    f"(موجود أيضاً في {previous.relative_to(ROOT)})"
                )
            else:
                top_level_declarations[key] = f

        imports = [
            line.strip()
            for line in text.split("\n")
            if line.strip().startswith("import ")
        ]
        dupes = {i for i in imports if imports.count(i) > 1}
        for d in sorted(dupes):
            problems.append(f"استيراد مكرّر · {rel} → {d}")

        # أعضاء النطاقات (RowScope/BoxScope/ColumnScope) لا تُستورد كدوال عليا:
        # `weight` و`align` و`matchParentSize` تُنادى داخل النطاق مباشرةً.
        # استيرادها يمنع البناء بخطأ «unresolved reference» — حدث فعلاً مرةً.
        for imp in imports:
            if imp in (
                "import androidx.compose.foundation.layout.weight",
                "import androidx.compose.foundation.layout.align",
                "import androidx.compose.foundation.layout.matchParentSize",
            ):
                problems.append(f"استيراد غير صالح (عضو نطاق) · {rel} → {imp}")

        for n, line in enumerate(text.split("\n"), 1):
            if "TODO" in line or "FIXME" in line:
                problems.append(f"TODO/FIXME منسيّ · {rel}:{n}")
            # عربي ملتصق بمحارف آسيوية ⇒ خلط توليدي في تعليق
            if garbled(line):
                problems.append(f"نص مخلوط (عربي + محارف آسيوية) · {rel}:{n}")

    print("فحص سلامة الكود")
    print("----------------------------------------------")
    print(f"ملفات Kotlin المفحوصة: {len(files)}")
    print()
    if problems:
        for p in problems:
            print("❌", p)
        print(f"\n✗ عدد المشاكل: {len(problems)}")
        return 1

    print("🟢 الأقواس متوازنة في كل الملفات · لا تعريفات أنواع أو استيرادات مكرّرة · "
          "لا TODO/FIXME · لا نصوص مخلوطة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
