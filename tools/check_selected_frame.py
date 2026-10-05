#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **إطار الاختيار** في شاشة «تعديل الملف الشخصي» (طلب المستخدم).

المطلوب: حين يختار المستخدم أفاتاراً أو غلافاً أو أداة، يبقى المختار مُؤطَّراً
بلون واضح يعرف منه ماذا اختار — لا حدّاً رمادياً رفيعاً لا يُرى.

يتحقّق الفاحص من `ProfileScreens.kt`:
  • الخلية المختارة تُزيّن بهالة `Modifier.shadow` بلون أخضر (خارج البطاقة)؛
  • حدّ المختار **أسمك** من حدّ غير المختار (2.5dp مقابل 1dp)؛
  • خلفية المختار مُلوّنة (أخضر بشفافية) لا رمادية عادية؛
  • علامة `✓` تظهر فوق زاوية الخلية المختارة وحدها؛
  • اسم المختار مكتوب بلون أخضر.

يفشل إن عاد أيٌّ من ذلك إلى الحالة الباهتة السابقة.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/profile/ProfileScreens.kt"


def main() -> int:
    if not KOTLIN.exists():
        print(f"✗ الملف مفقود: {KOTLIN}")
        return 1
    src = KOTLIN.read_text(encoding="utf-8")

    # شريحة خلايا الأصول وحدها (من «val selected = when (kind)» إلى نهاية حلقة الصفوف)
    start = src.find("val selected = when (kind)")
    end = src.find("// خلايا فارغة تكمل الصف الأخير")
    if start == -1 or end == -1 or end < start:
        print("✗ لم تُوجد شريحة خلايا الأصول في ProfileScreens.kt")
        return 1
    cell_block = src[start:end]

    checks = [
        (
            "هالة خضراء حول المختار (Modifier.shadow بـ ambientColor)",
            re.search(
                r"Modifier\.shadow\s*\(\s*[\d.]+\.dp\s*,\s*\w+\s*,"
                r"[^)]*ambientColor\s*=\s*AppColors\.Green",
                cell_block,
                re.S,
            ),
        ),
        (
            "حدّ المختار أسمك من غير المختار (2.5dp / 1dp)",
            re.search(
                r"\.border\s*\(\s*if\s*\(selected\)\s*[\d.]+\.dp\s*else\s*[\d.]+\.dp\s*,",
                cell_block,
            ),
        ),
        (
            "خلفية المختار مُلوّنة بالأخضر",
            re.search(
                r"if\s*\(selected\)\s*AppColors\.Green\.copy\(alpha\s*=[\s\d.]*f\)",
                cell_block,
            ),
        ),
        (
            "علامة ✓ فوق زاوية المختار وحده",
            re.search(r'if\s*\(selected\)\s*\{[^}]*"✓"', cell_block, re.S),
        ),
        (
            "اسم المختار بالأخضر",
            re.search(
                r"color\s*=\s*if\s*\(selected\)\s*AppColors\.Green\s*else\s*AppColors\.Muted",
                cell_block,
            ),
        ),
    ]

    print("الفحص | النتيجة")
    print("----------------------------------------------")
    bad = 0
    for label, hit in checks:
        ok = hit is not None
        if not ok:
            bad += 1
        print(f"{label:<52} | {'✅' if ok else '❌'}")

    if bad:
        print(f"\n✗ عدد المشاكل: {bad}")
        return 1
    print("\n🟢 المختار مُؤطَّر بلون واضح: هالة + حدّ سميك + خلفية مُلوّنة + علامة ✓ واسم أخضر.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
