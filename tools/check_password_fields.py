#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **حقول كلمة المرور** في شاشات الحساب (طلب المالك):

    • عين 👁 **داخل** كل حقل كلمة مرور تُظهرها وتُخفيها؛
    • شاشة «إنشاء الحساب» فيها **حقل تأكيد** ثانٍ؛
    • إنشاء الحساب **يُرفض** إن اختلف الحقلان (مع رسالة واضحة)؛
    • لا يبقى أي حقل كلمة مرور «أعمى» (بلا عين) في شاشات الحساب؛
    • نصّا «إظهار/إخفاء كلمة المرور» موجودان بكل اللغات.

يفشل الفاحص إن عاد أيٌّ من ذلك.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"
AUTH = SRC / "com/addonstation/mobile/ui/auth/AuthScreens.kt"
RES = ROOT / "android/app/src/main/res"


def main() -> int:
    problems: list[str] = []
    if not AUTH.exists():
        print(f"✗ الملف مفقود: {AUTH}")
        return 1
    s = AUTH.read_text(encoding="utf-8")

    # ١) الحقل الموحّد موجود وفيه عين التبديل
    if "private fun PasswordField(" not in s:
        problems.append("لا يوجد حقل PasswordField موحّد")
    else:
        body = s[s.index("private fun PasswordField("):]
        body = body[: body.index("\n}\n") + 3]
        for needle, label in (
            ("Icons.Filled.Visibility", "أيقونة العين (إظهار)"),
            ("Icons.Filled.VisibilityOff", "أيقونة العين (إخفاء)"),
            ("VisualTransformation.None", "إظهار النص عند الفتح"),
            ("PasswordVisualTransformation()", "إخفاء النص افتراضاً"),
            ("trailingIcon", "العين داخل الحقل (trailingIcon)"),
        ):
            if needle not in body:
                problems.append(f"PasswordField بلا {label}")

    # ٢) لا حقل كلمة مرور «أعمى» (بلا عين) في **أي** شاشة في التطبيق
    blind = s.count("PasswordVisualTransformation()")
    if blind > 1:  # واحد فقط داخل PasswordField نفسه
        problems.append(f"يوجد {blind - 1} حقل/حقول كلمة مرور بلا عين في شاشات الحساب")
    for f in sorted(SRC.rglob("*.kt")):
        if f == AUTH:
            continue
        t = f.read_text(encoding="utf-8")
        if "PasswordVisualTransformation()" not in t:
            continue
        # الحقل مقبول فقط إن كان بجانبه تبديل إظهار/إخفاء (عين)
        if "Icons.Filled.Visibility" not in t or "VisualTransformation.None" not in t:
            problems.append(
                f"حقل كلمة مرور بلا عين · {f.relative_to(ROOT)}"
            )

    # ٣) حقل التأكيد في إنشاء الحساب + التحقق من التطابق
    signup = s[s.index("fun SignUpScreen("):]
    if "R.string.auth_confirm_password" not in signup:
        problems.append("شاشة إنشاء الحساب بلا حقل «تأكيد كلمة المرور»")
    if not re.search(r"password\s*!=\s*confirm", signup):
        problems.append("لا تحقّق من تطابق الحقلين قبل إنشاء الحساب")

    # ٤) عدد الحقول المستخدمة (دخول ١ · إنشاء حساب ٢ · كلمة المرور الجديدة ٢)
    used = s.count("PasswordField(\n") + len(re.findall(r"^\s+PasswordField\(", s, re.M))
    if used < 5:
        problems.append(f"عدد حقول كلمة المرور قليل: {used} (المتوقّع ٥)")

    # ٥) النصوص موجودة بكل اللغات
    missing = []
    for path in sorted(RES.glob("values*/strings.xml")):
        text = path.read_text(encoding="utf-8")
        for name in ("auth_show_password", "auth_hide_password"):
            if f'name="{name}"' not in text:
                missing.append(f"{path.parent.name}/{name}")
    if missing:
        problems.append("نصوص ناقصة: " + " · ".join(missing))

    print("الفحص | النتيجة")
    print("----------------------------------------------")
    if problems:
        for p in problems:
            print(f"❌ {p}")
        print(f"\n✗ عدد المشاكل: {len(problems)}")
        return 1

    print("✅ عين الإظهار في **كل** حقول كلمة المرور في التطبيق + حقل تأكيد في إنشاء الحساب")
    print("\n🟢 كل حقل كلمة مرور في التطبيق يملك عين 👁 داخله (الحساب ولوحة "
          "الإدارة)، وإنشاء الحساب يرفض الحقلين غير المتطابقين، والنصوص بكل اللغات.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
