#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تحقق من إطار العنصر المختار في محرر الملف الشخصي وفق مواصفة التحرير الحالية.

يجب أن تُظهر خلايا الأفاتار والغلاف والأداة التحديد بإطار أزرق 3dp (مقابل
1dp لغير المختار) وشارة تحقق في الزاوية العليا الخارجية؛ وتبرز الأداة المختارة
بلون أزرق أيضاً. هذا الفحص يتابع المكوّنات الفعلية في EditProfileEditor.kt.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/profile/EditProfileEditor.kt"


def composable_block(src: str, name: str) -> str:
    start = src.find(f"private fun {name}(")
    if start < 0:
        return ""
    end = src.find("\n@Composable\nprivate fun ", start + 1)
    return src[start:end if end >= 0 else len(src)]


def main() -> int:
    if not KOTLIN.exists():
        print(f"✗ الملف مفقود: {KOTLIN}")
        return 1
    src = KOTLIN.read_text(encoding="utf-8")
    tiles = {
        name: composable_block(src, name)
        for name in ("AvatarAssetTile", "BannerAssetTile", "ItemAssetTile")
    }
    badge = composable_block(src, "SelectionBadge")
    checks = []
    for name, block in tiles.items():
        checks.extend([
            (
                f"{name}: حدّ أزرق 3dp للمختار و1dp لغيره",
                bool(re.search(
                    r"\.border\(\s*if\s*\(selected\)\s*3\.dp\s*else\s*1\.dp\s*,\s*"
                    r"if\s*\(selected\)\s*EditBlue\s*else\s*palette\.border",
                    block,
                )),
            ),
            (
                f"{name}: شارة التحقق عند أعلى نهاية الخلية",
                bool(re.search(
                    r"if\s*\(selected\)\s*SelectionBadge\(Modifier\.align\(Alignment\.TopEnd\)",
                    block,
                )),
            ),
        ])
    checks.extend([
        (
            "الأداة المختارة لها خلفية زرقاء خفيفة",
            "background(if (selected) palette.blue.copy(alpha = 0.12f) else palette.surface)" in tiles["ItemAssetTile"],
        ),
        (
            "اسم الأداة المختارة يبرز بالأزرق الفاتح",
            "color = if (selected) EditBlueLight else palette.textMuted" in tiles["ItemAssetTile"],
        ),
        (
            "شارة الاختيار دائرة زرقاء 24dp بعلامة تحقق بيضاء",
            "size(24.dp)" in badge and ".background(EditBlue)" in badge
            and "Icons.Filled.Check" in badge and "tint = Color.White" in badge,
        ),
    ])

    print("فحص إطار الاختيار | النتيجة")
    print("----------------------------------------------")
    failed = []
    for label, ok in checks:
        print(f"{label:<64} | {'✅' if ok else '❌'}")
        if not ok:
            failed.append(label)
    if failed:
        print(f"\n✗ عدد المشاكل: {len(failed)}")
        return 1
    print("\n🟢 خلايا الأفاتار والغلاف والأداة تبيّن الاختيار بإطار وشارة واضحين.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
