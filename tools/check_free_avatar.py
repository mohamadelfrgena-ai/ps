#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **الأفاتار المجاني واحد فقط — onefree لكل الحسابات** (طلب المالك).

طلب المالك (حرفياً):
    «أنا أنشئ حساباً جديداً — أريد هذه الصورة المجانية الافتراضية لكل الحساب
     [onefree.png]، والصور التي معيّنة مجانية والافتراضية القديمة أزلها.»

القواعد التي يحرسها الفاحص:
  ١) `supabase/31_single_free_avatar.sql` موجود ويعطّل الأفاتارات المجانية
     القديمة (`enabled = false`) بدل حذفها — فتبقى قابلة للإرجاع؛
  ٢) يفعّل صف onefree واحداً فقط (المسار المعتمد profiles/profiles/onefree.png
     — المسار الموجود فعلاً في المخزن)، و`32` يوحّد كل شيء عليه؛
  ٣) يحوّل الحسابات التي تستعمل صورة مجانية قديمة إلى onefree؛
  ٤) القيمة الافتراضية لعمود `profiles.avatar_url` = onefree (ملف 24)؛
  ٥) `handle_new_user()` يولّد الحساب وصورته onefree (ملف 24)؛
  ٦) البذرة `15_assets_v2.sql` تعطّل الأفاتارات المجانية القديمة كذلك؛
  ٧) الملف الجامع `00_ALL_IN_ONE.sql` يحوي الملف 31؛
  ٨) صورة onefree محفوظة في المستودع (للرجوع إليها عند الرفع)؛
  ٩) التطبيق يقرأ الأصول بـ `enabled=eq.true` (لولاها لظهرت المعطّلات).

الاستخدام:  python3 tools/check_free_avatar.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPABASE = ROOT / "supabase"
M31 = SUPABASE / "31_single_free_avatar.sql"
M24 = SUPABASE / "24_free_default_avatar.sql"
M32 = SUPABASE / "32_onefree_canonical_path.sql"
SEED = SUPABASE / "15_assets_v2.sql"
ALL_IN_ONE = SUPABASE / "00_ALL_IN_ONE.sql"
ASSETS_REPO = ROOT / "android/app/src/main/java/com/addonstation/mobile/data/repo/AssetsRepository.kt"
IMAGE = SUPABASE / "site-assets" / "profiles" / "onefree.png"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def main() -> int:
    m31, m24, m32, seed = read(M31), read(M24), read(M32), read(SEED)
    allinone, repo = read(ALL_IN_ONE), read(ASSETS_REPO)

    rows = [
        ("ملف 31 موجود", M31.exists()),
        ("تعطيل الأفاتارات المجانية القديمة (لا حذفها)",
         bool(re.search(r"set\s+enabled\s*=\s*false[\s\S]{0,200}?"
                        r"required_plan\s*=\s*'free'[\s\S]{0,200}?"
                        r"not\s+like\s+'%onefree%'", m31, re.I))),
        ("إبقاء onefree مفعّلاً (أفاتار مجاني واحد على المسار المعتمد)",
         bool(re.search(r"insert\s+into\s+public\.site_assets[\s\S]{0,400}?"
                        r"'profiles/profiles/onefree\.png'[\s\S]{0,500}?"
                        r"on\s+conflict\s*\(\s*file_url\s*\)\s*do\s+update[\s\S]{0,400}?"
                        r"enabled\s*=\s*true", m31, re.I))),
        ("الحسابات على صورة قديمة تُحوَّل إلى onefree",
         bool(re.search(r"update\s+public\.profiles[\s\S]{0,900}?"
                        r"set\s+avatar_url\s*=\s*'profiles/profiles/onefree\.png'", m31, re.I))
         or bool(re.search(r"update\s+public\.profiles[\s\S]{0,900}?"
                           r"set\s+avatar_url\s*=\s*'profiles/profiles/onefree\.png'", m32, re.I))),
        ("تحقّق داخل 31: مفعّل واحد فقط",
         "v_enabled <> 1" in m31),
        ("افتراضي العمود = المسار المعتمد (ملف 24)",
         "alter column avatar_url set default 'profiles/profiles/onefree.png'" in m24),
        ("ملف 32 يوحّد المسار على ما هو موجود في المخزن",
         M32.exists() and "profiles/profiles/onefree.png" in m32),
        ("حساب جديد ⇒ صورته onefree (handle_new_user)",
         bool(re.search(r"insert\s+into\s+public\.profiles[\s\S]{0,300}?"
                        r"'profiles/profiles/onefree\.png'", m24, re.I))),
        ("البذرة 15 تعطّل المجاني القديم أيضاً",
         bool(re.search(r"update\s+public\.site_assets\s*\n\s*set\s+enabled\s*=\s*false"
                        r"[\s\S]{0,200}?not\s+like\s+'%onefree%'", seed, re.I))),
        ("الملف الجامع يحوي 31 و32",
         "31) أفاتار مجاني واحد" in allinone and "32) توحيد مسار onefree" in allinone),
        ("التطبيق يقرأ المفعّل فقط (enabled=eq.true)",
         "enabled=eq.true" in repo),
        ("صورة onefree محفوظة في المستودع", IMAGE.exists()),
    ]

    print("الفحص                                             | النتيجة")
    print("-" * 62)
    for name, ok in rows:
        print(f"{name:<50} | {'✅' if ok else '❌'}")

    failed = [n for n, ok in rows if not ok]
    print()
    if failed:
        for n in failed:
            print("✗", n)
        print(f"\n✗ عدد المشاكل: {len(failed)}")
        return 1
    print("🟢 أفاتار مجاني واحد مفعّل (onefree) لكل حساب جديد ولكل حساب قديم، "
          "والصور المجانية القديمة معطّلة محفوظة تُرجَع بسطر واحد.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
