#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص ساكن لخصوصية أصول الأدمن: قاعدة + Storage + تطبيق + أداة ترحيل."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.exists():
        problems.append(f"ملف مفقود: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


problems: list[str] = []
sql = read("supabase/40_admin_asset_privacy.sql")
rls = read("supabase/04_rls.sql")
all_sql = read("supabase/00_ALL_IN_ONE.sql")
privacy = read("android/app/src/main/java/com/addonstation/mobile/data/AdminAssetPrivacy.kt")
state = read("android/app/src/main/java/com/addonstation/mobile/data/AppState.kt")
container = read("android/app/src/main/java/com/addonstation/mobile/data/AppContainer.kt")
assets = read("android/app/src/main/java/com/addonstation/mobile/data/repo/AssetsRepository.kt")
profile = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ProfileRepository.kt")
creator = read("android/app/src/main/java/com/addonstation/mobile/data/repo/CreatorRepository.kt")
admin = read("android/app/src/main/java/com/addonstation/mobile/data/repo/AdminRepository.kt")
common = read("android/app/src/main/java/com/addonstation/mobile/ui/components/Common.kt")
migrate = read("tools/migrate_admin_assets_private.py")

checks = [
    ("RLS يخفي صف admin عن غير الأدمن",
     "required_plan <> 'admin'" in rls and "public.is_admin()" in rls),
    ("الترحيل يعيد تثبيت RLS نفسه",
     "required_plan <> 'admin'" in sql and "create policy assets_read" in sql),
    ("المسارات الخاصة تُعاد إلى admin-private عند كل تجهيز",
     "update public.site_assets" in sql
     and "'admin-private/' || ltrim(file_url" in sql
     and "update public.profiles" in sql),
    ("visible_profiles ينقّح الأفاتار والغلاف والأداة",
     "create or replace view public.visible_profiles" in sql
     and sql.count("is_admin_only_profile_asset(") >= 4
     and "else 'none'" in sql),
    ("لا تجاوز مباشر لجدول profiles أو equipped_item_id",
     "revoke select on public.profiles from public, anon, authenticated" in sql
     and "revoke update (equipped_item_id)" in sql
     and "grant select on public.visible_profiles to anon, authenticated" in sql),
    ("admin-assets خاص وله أربع سياسات admin",
     "'admin-assets'" in sql and "false," in sql
     and all(f'"admin_assets_{name}"' in sql for name in ("read", "insert", "update", "delete"))
     and sql.count("public.is_admin()") >= 8),
    ("المسار الخاص معروف مركزياً",
     'ADMIN_ASSETS_BUCKET = "admin-assets"' in privacy
     and 'ADMIN_PRIVATE_PREFIX = "admin-private/"' in privacy),
    ("AppState لا ينشر required_plan=admin لغير الأدمن",
     "publishVisibleAssets" in state
     and 'filterNot { it.requiredPlan.equals("admin"' in state
     and "redactAdminAssets" in state),
    ("حل الرابط الخاص لا يرجع URL عاماً",
     "isPrivateAdminAssetPath(clean)" in container
     and "state.privateAssetUrl(clean)" in container),
    ("رفع admin يذهب للمخزن الخاص ويوقّع الرابط",
     "if (privateAdmin) ADMIN_ASSETS_BUCKET" in assets
     and 'supabase.signedUrl(ADMIN_ASSETS_BUCKET' in assets
     and 'requiredPlan.equals("admin"' in assets),
    ("كل قراءات الملفات الشخصية تفضّل visible_profiles",
     all('supabase.select("visible_profiles"' in text for text in (profile, creator, admin))),
    ("النسخة الاحتياطية تنقّح القيم القديمة",
     ".map(state::redactAdminAssets)" in profile
     and ".map(state::redactAdminAssets)" in creator),
    ("الرابط الموقّع لا يُحوّل خطأً إلى thumb آخر",
     'url.contains("/object/sign/admin-assets/"' in common),
    ("أداة النقل آمنة افتراضياً والتنفيذ صريح",
     'parser.add_argument("--apply", action="store_true"' in migrate
     and "if not args.apply" in migrate
     and "delete_objects" in migrate),
    ("40 داخل ملف التجهيز الجامع", "القسم: 40_admin_asset_privacy.sql" in all_sql),
]

for label, ok in checks:
    if not ok:
        problems.append(label)
    print(f"{'✅' if ok else '❌'} {label}")

if problems:
    print("\n✗ مشاكل خصوصية أصول الأدمن:")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)
print("\n🟢 أصول الأدمن مخفية عن غير الأدمن في الكتالوج والملفات الشخصية والمخزن والتطبيق.")
