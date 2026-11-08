#!/usr/bin/env python3
"""Guard the owner's attached image as the one and only free account avatar."""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPABASE = ROOT / "supabase"
EXPECTED_SHA256 = "6e2e27187e757d8f213d6dd29c6afe9667e960447e752d3ae2c82b66bb33b7a5"
CANONICAL_PATH = "profiles/profiles/onefree.png"

M31 = SUPABASE / "31_single_free_avatar.sql"
M45 = SUPABASE / "45_single_attached_free_avatar.sql"
M46 = SUPABASE / "46_profile_avatar_packs.sql"
SCHEMA = SUPABASE / "01_schema.sql"
FUNCTIONS = SUPABASE / "02_functions_core.sql"
SEED = SUPABASE / "15_assets_v2.sql"
ALL_IN_ONE = SUPABASE / "00_ALL_IN_ONE.sql"
SERVER_IMAGE = SUPABASE / "site-assets/profiles/onefree.png"
LOCAL_IMAGE = ROOT / "android/app/src/main/res/drawable-nodpi/ic_free_profile.png"
COMMON = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/components/Common.kt"
PROFILE_SPEC = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/components/ProfileSpecUi.kt"
HOME = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/home/HomeScreen.kt"
EDITOR = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/profile/EditProfileEditor.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def sha256(path: Path) -> str:
    if not path.is_file():
        return ""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def deletes_other_free_rows(sql: str) -> bool:
    return bool(
        re.search(
            r"delete\s+from\s+public\.site_assets[\s\S]{0,300}?"
            r"asset_type\s*=\s*'avatar'[\s\S]{0,200}?"
            r"required_plan\s*=\s*'free'[\s\S]{0,200}?"
            r"file_url\s*<>\s*'profiles/profiles/onefree\.png'",
            sql,
            re.IGNORECASE,
        )
    )


def main() -> int:
    m31 = read(M31)
    m45 = read(M45)
    m46 = read(M46)
    schema = read(SCHEMA)
    functions = read(FUNCTIONS)
    seed = read(SEED)
    all_in_one = read(ALL_IN_ONE)
    common = read(COMMON)
    registered_ui = "\n".join(read(path) for path in (PROFILE_SPEC, HOME, EDITOR))

    asset_line = next(
        (line.strip() for line in common.splitlines() if line.strip().startswith("private val ASSET_FREE_DEFAULT")),
        "",
    )

    rows = [
        ("ملف المالك محفوظ في مستودع الخادم",
         sha256(SERVER_IMAGE) == EXPECTED_SHA256),
        ("نسخة التطبيق المحلية مطابقة للصورة المرفقة بايتاً ببايت",
         sha256(LOCAL_IMAGE) == EXPECTED_SHA256),
        ("صورة الخادم والنسخة المحلية متطابقتان",
         sha256(SERVER_IMAGE) == sha256(LOCAL_IMAGE) != ""),
        ("ملف 31 يحذف كل سجل مجاني قديم",
         deletes_other_free_rows(m31)),
        ("الترحيل 45 يحذف كل سجل مجاني قديم",
         deletes_other_free_rows(m45)),
        ("إعادة زرع الأصول لا تعيد السجلات المجانية القديمة",
         deletes_other_free_rows(seed)),
        ("الترحيل يثبّت الصورة كسجل free الوحيد المفعّل",
         all(token in m45 for token in (
             "'Default Free Avatar'",
             f"'{CANONICAL_PATH}'",
             "required_plan",
             "enabled          = true",
         ))),
        ("الحزمتان المدفوعتان لا تبتلعان الصورة المجانية",
         "'Default', 1, 1, true" in m31
         and "'Default', 1, 1, true" in m45
         and "collection = 'Default'" in m46
         and "onefree.png must remain the sole enabled free avatar" in m46),
        ("الحسابات ذات المسار القديم تُنقل قبل الحذف",
         "p.avatar_url = 'profiles/onefree.png'" in m45
         and m45.index("update public.profiles p") < m45.index("delete from public.site_assets")),
        ("افتراضي جدول profiles هو المسار المعتمد",
         f"default '{CANONICAL_PATH}'" in schema),
        ("دالة إنشاء الحساب تعيّن الصورة صراحةً",
         bool(re.search(
             r"insert\s+into\s+public\.profiles\s*\([^)]*avatar_url[^)]*\)"
             r"[\s\S]{0,180}?profiles/profiles/onefree\.png",
             m45,
             re.IGNORECASE,
         ))),
        ("إعادة تعيين الصورة عبر RPC تعود إلى المجاني الوحيد",
         CANONICAL_PATH in functions
         and "perform set_config('app.allow_profile_write', '1', true);" in functions),
        ("التطبيق لا يجرب مسار onefree قديم أو مفقود",
         asset_line == f'private val ASSET_FREE_DEFAULT = listOf("{CANONICAL_PATH}")'),
        ("Fallback الحساب المسجّل يستخدم صورة المالك المحلية",
         common.count("R.drawable.ic_free_profile") >= 2
         and registered_ui.count("R.drawable.ic_free_profile") >= 5),
        ("صورة المجهول المنفصلة بقيت محصورة في AnonymousAvatarImage",
         common.count("R.drawable.ic_default_profile") == 1
         and "R.drawable.ic_default_profile" not in registered_ui),
        ("الملف الجامع يطبّق 45 ثم حزم 46 في النهاية",
         "-- ##  القسم: 45_single_attached_free_avatar.sql" in all_in_one
         and "-- ##  القسم: 46_profile_avatar_packs.sql" in all_in_one
         and all_in_one.rfind("46_profile_avatar_packs.sql")
             > all_in_one.rfind("45_single_attached_free_avatar.sql")),
    ]

    print("فحص الصورة المجانية الوحيدة")
    print("-" * 66)
    for name, ok in rows:
        print(f"{'✅' if ok else '❌'} {name}")

    failed = [name for name, ok in rows if not ok]
    if failed:
        print(f"\n✗ عدد المشاكل: {len(failed)}")
        return 1

    print("\n🟢 صورة المالك هي سجل free الوحيد، وافتراضي الحساب الجديد،")
    print("   والنسخة المحلية المطابقة تمنع ظهور صورة مختلفة عند بطء الشبكة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
