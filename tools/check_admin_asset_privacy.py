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
hotfix_sql = read("supabase/42_visible_profiles_access.sql")
view_sql = sql.split("create or replace view public.visible_profiles", 1)[-1].split("comment on view", 1)[0]
hotfix_view_sql = hotfix_sql.split("create or replace view public.visible_profiles", 1)[-1].split("comment on view", 1)[0]
rls = read("supabase/04_rls.sql")
all_sql = read("supabase/00_ALL_IN_ONE.sql")
privacy = read("android/app/src/main/java/com/addonstation/mobile/data/AdminAssetPrivacy.kt")
state = read("android/app/src/main/java/com/addonstation/mobile/data/AppState.kt")
container = read("android/app/src/main/java/com/addonstation/mobile/data/AppContainer.kt")
assets = read("android/app/src/main/java/com/addonstation/mobile/data/repo/AssetsRepository.kt")
profile = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ProfileRepository.kt")
profile_ui = read("android/app/src/main/java/com/addonstation/mobile/ui/profile/ProfileScreens.kt")
profile_editor = read("android/app/src/main/java/com/addonstation/mobile/ui/profile/EditProfileEditor.kt")
settings_ui = read("android/app/src/main/java/com/addonstation/mobile/ui/settings/SettingsScreen.kt")
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
    ("visible_profiles ينقّح الأفاتار والغلاف والأداة بلا صلاحية EXECUTE عامة",
     "create or replace view public.visible_profiles" in sql
     and all(f"a.asset_type = '{kind}'" in view_sql for kind in ("avatar", "banner", "item"))
     and view_sql.count("a.required_plan = 'admin'") == 3
     and "is_admin_only_profile_asset(" not in view_sql
     and "else 'none'" in view_sql
     and "revoke all on function public.is_admin_only_profile_asset" in sql),
    ("ترحيل إصلاح القراءة يحافظ على التنقيح نفسه",
     "create or replace view public.visible_profiles" in hotfix_sql
     and all(f"a.asset_type = '{kind}'" in hotfix_view_sql for kind in ("avatar", "banner", "item"))
     and hotfix_view_sql.count("a.required_plan = 'admin'") == 3
     and "is_admin_only_profile_asset(" not in hotfix_view_sql
     and "grant select on public.visible_profiles to anon, authenticated" in hotfix_sql),
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
    ("حل الرابط الخاص لا يرجع URL عاماً ويظل محصوراً بدور admin",
     "isPrivateAdminAssetPath(clean)" in container
     and "if (state.isAdmin) privateAssetUrls[clean] else null" in container),
    ("شاشات صورة/غلاف الحساب تراقب وصول الروابط الموقّعة",
     all("state.privateAssetUrls.collectAsState()" in text for text in (profile_ui, profile_editor, settings_ui))
     and "privateAssetUrls: Map<String, String>" in container),
    ("رفع admin يذهب للمخزن الخاص ويوقّع الرابط",
     "if (privateAdmin) ADMIN_ASSETS_BUCKET" in assets
     and 'supabase.signedUrl(ADMIN_ASSETS_BUCKET' in assets
     and 'requiredPlan.equals("admin"' in assets),
    ("قراءات ملفات الآخرين تفضّل visible_profiles",
     '"visible_profiles" to null' in profile
     and all('supabase.select("visible_profiles"' in text for text in (creator, admin))
     and "private suspend fun selectProfiles" in profile),
    ("قراءة التطبيق تبدأ بـ visible_profiles الموثّق لإبقاء أصول الأدمن",
     profile.find('"visible_profiles" to sessionToken') >= 0
     and profile.find('"visible_profiles" to sessionToken') < profile.find('"visible_profiles" to null')),
    ("الملف العام يفتح بلا اعتماد على صلاحية JWT",
     '"visible_profiles" to null' in profile
     and "supabase.select(table, query, token = token)" in profile
     and "supabase.currentUserId()" in profile),
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
