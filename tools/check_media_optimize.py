#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
حارس وزن الصور — يمنع عودة الأصول الثقيلة وعودة تقطيع الشبكات.

المشكلة التي عولجت: مكتبة `site-assets` كانت ~176MB وفيها صور حتى 4.8MB،
و214 ملف GIF، وكلها تُنزَّل كما هي على الهاتف ⇒ شبكة الأصول وبانرات الملف
الشخصي بطيئة الظهور والتمرير يتهنّج.

القواعد المحروسة (لا يجوز أن تعود):

  1) **الشبكات لا تُشغّل GIF**: `CoverImage`/`AssetThumb`/`BannerThumb` تستعمل
     اللقطة الثابتة `…thumb.jpg` افتراضياً، والحركة تُطلب صراحةً (`animate = true`).
  2) **المعاينات المفردة تُبقي الحركة**: صفحة العنصر (`StoreScreens`) وبانر
     الملف الشخصي يمرّران `animate = true` — فلا يتضرّر ما يراه المستخدم منفرداً.
  3) **غياب اللقطة مسجَّل**: ذاكرة جلسة واحدة (`missingThumbUrls`) تمنع إعادة
     طلب رابط 404 مع كل تمرير أو إعادة تركيب خلية.
  4) **مُحسِّن واحد للجميع**: `tools/optimize_site_assets.py` موجود، يشتغل على
     مخزن `site-assets`، يولّد اللقطات، يصغّر الثابت الكبير، ولا يرفع نسخة أكبر
     من الأصل (بوّابة KEEP_RATIO)، وفيه فحص ذاتي `--selftest`.
  5) **سير التشغيل لا يبني شيئاً**: `.github/workflows/optimize-media.yml` يدوي
     فقط (`workflow_dispatch`)، ولا يقرأ `.build-request` ولا يحتوي خطوة بناء APK.

الاستخدام:
    python3 tools/check_media_optimize.py      # 0 = سليم · 1 = عاد خلل
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
TOOLS = os.path.join(ROOT, "tools")
WORKFLOWS = os.path.join(ROOT, ".github", "workflows")

problems: list[str] = []


def read(path):
    if not os.path.exists(path):
        problems.append(f"ملف مفقود: {os.path.relpath(path, ROOT)}")
        return ""
    return io.open(path, encoding="utf-8").read()


def strip_comments(text):
    return re.sub(r"(?m)^\s*(//|\*|/\*).*$", "", text)


common = read(os.path.join(SRC, "ui", "components", "Common.kt"))
code = strip_comments(common)

# 1) ذاكرة اللقطات المفقودة موجودة ومستعملة في المواضع الثلاثة
if "missingThumbUrls" not in common:
    problems.append("Common.kt: ذاكرة اللقطات المفقودة (missingThumbUrls) مفقودة")
else:
    uses = len(re.findall(r"missingThumbUrls", common))
    if uses < 5:
        problems.append(f"Common.kt: ذاكرة اللقطات المفقودة مستعملة مرة واحدة فقط ({uses} إشارة)")

# 2) الحركة صارت اختيارية في الدوال الثلاث
for fn in ("fun CoverImage(", "fun AssetThumb(", "fun BannerThumb("):
    idx = code.find(fn)
    if idx == -1:
        problems.append(f"Common.kt: الدالة {fn} مفقودة")
        continue
    head = code[idx:idx + 1400]
    if not re.search(r"animate:\s*Boolean\s*(=|)", head.split(")", 1)[0] + ")"):
        problems.append(f"Common.kt: {fn.strip('fun (')} لا تستقبل الوسيط animate")

# 3) السلوك الافتراضي: لقطة ثابتة للـGIF (thumb.jpg) داخل المكوّنات الشبكية
if '".thumb.jpg"' not in common and ".thumb.jpg" not in common:
    problems.append("Common.kt: منطق اللقطة الثابتة (.thumb.jpg) مفقود")
if not re.search(r"animate:\s*Boolean\s*=\s*false", common):
    problems.append("Common.kt: الافتراضي يجب أن يكون animate = false (الشبكات ثابتة)")

# 4) المعاينات المفردة تُبقي الحركة
store = read(os.path.join(SRC, "ui", "store", "StoreScreens.kt"))
if not re.search(r"animate\s*=\s*true", strip_comments(store)):
    problems.append("ui/store/StoreScreens.kt: صفحة العنصر لم تعد تعرض الصورة بحركتها (animate = true)")

profile = read(os.path.join(SRC, "ui", "profile", "ProfileScreens.kt"))
# بانر الملف الشخصي في وثيقة AddonStation_profiles صار في مكوّن مشترك
# (ui/components/ProfileSpecUi.kt) — والقاعدة نفسها: يبقى متحرّكاً (animate = true)
spec_profile = read(os.path.join(SRC, "ui", "components", "ProfileSpecUi.kt"))
if not re.search(r"CoverImage\(\s*\n?\s*url = bannerUrl,\s*\n?\s*radius = 0\.dp,\s*\n?\s*animate = true",
                 strip_comments(spec_profile)):
    problems.append("ui/components/ProfileSpecUi.kt: بانر الملف الشخصي لم يعد متحرّكاً (animate = true)")

# موضع شبكة محتوى الملف تغيّر بين الإصدارات. في الإصدار الحالي تستخدم
# ProfileScreens ContentGrid → ContentCard → CoverImage الافتراضي (animate=false).
# الإصدارات السابقة تستعمل ProfileItemCard مباشرةً في ProfileWalletAdminScreens.
profile_code = strip_comments(profile)
profile_lists_path = os.path.join(SRC, "ui", "profile", "ProfileWalletAdminScreens.kt")
profile_lists_code = strip_comments(io.open(profile_lists_path, encoding="utf-8").read()) if os.path.exists(profile_lists_path) else ""
local_grid = re.search(
    r"CoverImage\(url = assetUrl\(container, item\.coverUrl\), modifier = Modifier\.fillMaxSize\(\), radius = 12\.dp(?:,\s*animate = (true|false))?\)",
    profile_lists_code,
)
remote_grid = (
    "private fun ContentGrid(" in profile_code
    and "ContentCard(" in profile_code
    and "coverUrl = assetUrl(container, item.coverUrl)" in profile_code
)
if local_grid:
    if local_grid.group(1) == "true":
        problems.append("ui/profile/ProfileWalletAdminScreens.kt: شبكة العناصر تشغّل GIF لكل خلية (يجب animate = false)")
elif remote_grid:
    idx = code.find("fun ContentCard(")
    end = code.find("\n}\n", idx) if idx >= 0 else -1
    card_body = code[idx:end] if idx >= 0 and end > idx else ""
    cover_call = re.search(r"CoverImage\s*\(", card_body)
    if not cover_call or re.search(r"animate\s*=\s*true", card_body):
        problems.append("ui/components/Common.kt: ContentCard يجب أن يعرض لقطة ثابتة افتراضياً")
else:
    problems.append("شبكة محتوى الملف: تعذر التحقق من استخدام CoverImage الثابت")

# 5) المُحسِّن الخادمي
opt_path = os.path.join(TOOLS, "optimize_site_assets.py")
opt = read(opt_path)
if opt:
    code_opt = strip_comments(opt)
    for needle, why in (
        ('BUCKET = "site-assets"', "يعمل على مخزن site-assets"),
        ("THUMB_SIDE", "مقاس اللقطة الثابتة"),
        ("KEEP_RATIO", "بوّابة عدم التضخيم"),
        ("--selftest", "فحص ذاتي"),
        ("x-upsert", "الرفع فوق نفس المسار"),
        (".thumb.jpg", "توليد اللقطات"),
        ("ImageSequence", "التقاط أول إطار من GIF"),
    ):
        if needle not in opt:
            problems.append(f"tools/optimize_site_assets.py: {why} مفقود ({needle})")
    if "svg" not in code_opt.lower():
        problems.append("tools/optimize_site_assets.py: لا يتخطّى ملفات SVG")

# 6) سير التشغيل: يدوي فقط ولا يبني APK
wf_path = os.path.join(WORKFLOWS, "optimize-media.yml")
wf = read(wf_path)
wf_code = re.sub(r"(?m)^\s*#.*$", "", wf)          # بلا تعليقات YAML
if wf:
    if "workflow_dispatch" not in wf_code:
        problems.append("optimize-media.yml: السير ليس يدوياً (workflow_dispatch مفقود)")
    for bad, why in ((".build-request", "يقرأ ملف طلب البناء"),
                     ("Build debug APK", "يحتوي خطوة بناء APK"),
                     ("gradlew", "يشغّل Gradle")):
        if bad in wf_code:
            problems.append(f"optimize-media.yml: {why} ({bad})")
    if "ADDONSTATION_SUPABASE_SECRET_KEY" not in wf:
        problems.append("optimize-media.yml: لا يستعمل سرّ الخادم المخزّن")

# 7) حارس مرتبط بسير البناء (كي لا يُتجاوز في أي بناء قادم)
build_path = os.path.join(WORKFLOWS, "build-apk.yml")
build = read(build_path)
if build and "check_media_optimize.py" not in build:
    problems.append("build-apk.yml: حارس وزن الصور غير مرتبط بخطوات ما قبل البناء")

print("حارس وزن الصور")
print("-" * 52)
print("فُحص: Common.kt (٣ مكوّنات) · صفحة العنصر · الملف الشخصي · مُحسِّن الأصول · سير Optimization")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عاد {len(problems)} خللاً في وزن الصور — الشبكات ستثقل من جديد.")
    sys.exit(1)
print("🟢 الشبكات تعرض لقطات ثابتة خفيفة · المعاينات المفردة تُبقي الحركة ·")
print("   اللقطة الغائبة تُسجَّل مرة واحدة · المُحسِّن لا يُضخّم صورة · السير يدوي ولا يبني APK.")
