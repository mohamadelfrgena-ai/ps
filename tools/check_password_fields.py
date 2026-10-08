#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""حارس حقول كلمة المرور بعد توحيدها في AuthSettingsField.

يتأكد من وجود عين الإظهار/الإخفاء داخل المكوّن، ومن توصيله بكل الحقول
(الدخول 1 · التسجيل 2 · إعادة التعيين 2)، ومن منع التسجيل إن لم تتطابق
كلمتا المرور، ومن وجود نصوص العين بجميع اللغات.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"
AUTH = SRC / "com/addonstation/mobile/ui/auth/AuthScreens.kt"
FIELDS = SRC / "com/addonstation/mobile/ui/components/AuthSettingsUi.kt"
RES = ROOT / "android/app/src/main/res"


def main() -> int:
    problems: list[str] = []
    if not AUTH.exists() or not FIELDS.exists():
        print("✗ ملف شاشة المصادقة أو مكوّنات الحقول مفقود")
        return 1
    auth = AUTH.read_text(encoding="utf-8")
    fields = FIELDS.read_text(encoding="utf-8")

    # الحقل المشترك يحتوي على عين 44dp، وتحويل إظهار/إخفاء.
    if "fun AuthSettingsField(" not in fields:
        problems.append("لا يوجد AuthSettingsField موحّد")
    for needle, label in (
        ("Icons.Filled.Visibility", "عين الإظهار"),
        ("Icons.Filled.VisibilityOff", "عين الإخفاء"),
        ("VisualTransformation.None", "إظهار النص"),
        ("PasswordVisualTransformation()", "إخفاء افتراضي"),
        ("IconButton(", "زرّ عين تفاعلي"),
        ("Modifier.size(44.dp)", "هدف لمس 44dp"),
    ):
        if needle not in fields:
            problems.append(f"AuthSettingsField بلا {label}")

    # لا توجد تحولات كلمة مرور منفصلة/عمياء في الشاشات الأخرى.
    occurrences = []
    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        if "PasswordVisualTransformation()" in text:
            occurrences.append(path)
    admin_gate = SRC / "com/addonstation/mobile/ui/admin/AdminGate.kt"
    allowed = {FIELDS, admin_gate}
    if set(occurrences) != allowed:
        problems.append("تحويل كلمة مرور في ملف غير معتمد: " + " · ".join(str(p.relative_to(ROOT)) for p in occurrences))
    if admin_gate.exists():
        admin = admin_gate.read_text(encoding="utf-8")
        if not all(mark in admin for mark in ("Icons.Filled.Visibility", "Icons.Filled.VisibilityOff", "VisualTransformation.None")):
            problems.append("حقل كلمة مرور لوحة الإدارة بلا عين إظهار/إخفاء")

    signup = auth[auth.index("fun SignUpScreen("):]
    if "R.string.auth_confirm_password" not in signup:
        problems.append("شاشة إنشاء الحساب بلا حقل تأكيد كلمة المرور")
    if not re.search(r"password\s*!=\s*confirm", signup):
        problems.append("لا يوجد تحقق من تطابق كلمتي المرور")

    uses = len(re.findall(r"password\s*=\s*true", auth))
    if uses != 5:
        problems.append(f"عدد حقول كلمة المرور الموصولة بالعين {uses} (المتوقع 5)")

    missing = []
    for path in sorted(RES.glob("values*/strings.xml")):
        text = path.read_text(encoding="utf-8")
        for name in ("auth_show_password", "auth_hide_password"):
            if f'name="{name}"' not in text:
                missing.append(f"{path.parent.name}/{name}")
    if missing:
        problems.append("نصوص ناقصة: " + " · ".join(missing))

    print("فحص حقول كلمة المرور")
    print("----------------------------------------------")
    if problems:
        for problem in problems:
            print(f"❌ {problem}")
        print(f"\n✗ عدد المشاكل: {len(problems)}")
        return 1
    print("✅ العين الموحّدة داخل الحقول الخمسة · التأكيد والتحقق محفوظان")
    print("🟢 كل الحقول لها عين إظهار/إخفاء والنصوص كاملة باللغات الثماني.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
