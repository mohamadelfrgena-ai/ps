#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **أشرطة أسفل الشاشة** يجب ألا تتداخل مع شريط التنقّل (Navigation Bar).

المشكلة: التطبيق يرسم من الحافة إلى الحافة (edge-to-edge) وtargetSdk=35 يفرض
ذلك؛ وأي شريط سفلي لا يحجز منطقة `WindowInsets.navigationBars` ينزل **تحت**
أزرار النظام فيتداخل معها (كما حدث في شريط لوحة الإدارة: 5 عناصر).

القاعدة:
  • كل `Row` يُرسم كشريط سفلي (fillMaxWidth + Arrangement.SpaceEvenly +
    خلفية بلون التطبيق) يجب أن تُحيط به حاوية تحجز
    `WindowInsets.navigationBars` (كما في BottomNavBar)؛
  • والحاوية تلوّن تلك المنطقة بلون التطبيق حتى لا تظهر فجوة شفّافة.

يفشل الفاحص إن وُجد شريط سفلي بلا حجز للمنطقة.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"

# شريط سفلي: Row( modifier = Modifier … fillMaxWidth … background(AppColors.Bg …
#                horizontalArrangement = Arrangement.SpaceEvenly
BAR_RE = re.compile(
    r"Row\(\s*\n\s*modifier\s*=\s*Modifier\s*\n"
    r"(?:\s*\.[^\n]*\n){0,8}?"
    r"\s*horizontalArrangement\s*=\s*Arrangement\.SpaceEvenly\s*,",
)
BAR_BG_RE = re.compile(r"background\(\s*AppColors\.Bg")
WINDOW = 500  # عدد المحارف التي نبحث فيها عن حجز المنطقة (قبل الشريط وبعده)


def main() -> int:
    problems: list[str] = []

    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for m in BAR_RE.finditer(text):
            block = text[max(0, m.start() - WINDOW) : m.end() + 200]
            if not BAR_BG_RE.search(block):
                continue  # ليس شريطاً سفلياً ملوّناً بلون التطبيق
            line = text[: m.start()].count("\n") + 1
            rel = path.relative_to(ROOT)
            if "bottomBarInsets()" in block:
                continue  # المساعد يحجز المنطقة في كل أوضاع التنقّل ✓
            if "navigationBars" in block and "systemGestures" in block:
                continue  # يحجز الأزرار الثلاثة **والإيماءات** ✓
            if "navigationBars" in block:
                problems.append(
                    f"{rel}:{line} — الشريط يحجز navigationBars فقط: "
                    f"على أجهزة الإيماءات تكون المنطقة 0 فيبقى التداخل "
                    f"(استخدم bottomBarInsets())"
                )
                continue
            problems.append(f"{rel}:{line} — شريط سفلي بلا حجز منطقة Navigation Bar")

    # المساعد نفسه يجب أن يغطّي النوعين: أزرار ثلاثة + إيماءات
    helper = SRC / "com/addonstation/mobile/ui/components/BottomNav.kt"
    htext = helper.read_text(encoding="utf-8") if helper.exists() else ""
    if "fun bottomBarInsets()" not in htext:
        problems.append("BottomNav.kt — المساعد bottomBarInsets() مفقود")
    else:
        body = htext[htext.index("fun bottomBarInsets()") :][:400]
        for needle in ("navigationBars", "systemGestures"):
            if needle not in body:
                problems.append(f"bottomBarInsets() لا يغطّي {needle}")

    print("الفحص | النتيجة")
    print("----------------------------------------------")
    if problems:
        for p in problems:
            print(f"❌ {p}")
        print(f"\n✗ عدد المشاكل: {len(problems)}")
        print("  الحل: غلّف الشريط بـ Box فيه "
              "`.background(AppColors.Bg).windowInsetsPadding(WindowInsets.navigationBars)`")
        return 1

    print("✅ كل الأشرطة السفلية تحجز منطقة شريط التنقّل (أزرار + إيماءات)")
    print("\n🟢 لا شريط سفلي يتداخل مع Navigation Bar: كل شريط فوق أزرار النظام "
          "والمنطقة تحته ملوّنة بلون التطبيق.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
