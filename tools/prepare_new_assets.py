#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يجهّز أصول الواجهة الجديدة من الأرشيف المقسّم (news.*) إلى شجرة منظَّمة بأقسام.

المصدر: مستودع عام `mohamadelfrgena-ai/vae` (news.z01..z07 + news.zip).
المخرج:
    new-assets/profiles/<قسم>/<ملف>    ← الأفاتارات (11 قسماً)
    new-assets/stickers/<قسم>/<ملف>    ← الملصقات (6 أقسام)
    new-assets/items/<قسم>/<ملف>       ← الأدوات (4 أقسام)
    assets-manifest.json               ← بيان صغير يصف كل أصل (يُرفع إلى Git)

هذا الملف هو «مصدر الحقيقة» للأسماء والأقسام: تبني منه أداةُ
tools/build_assets_sql.py ملفَ SQL، ويستعمله سير الرفع إلى Supabase.

أمثلة:
    # 1) تنزيل الأرشيف من GitHub ثم التحضير الكامل
    python3 tools/prepare_new_assets.py --download

    # 2) الأرشيف موجود مسبقاً في مجلد
    python3 tools/prepare_new_assets.py --archive-dir /tmp/news

    # 3) تجربة سريعة بلا تنزيل (يتحقق من الجدول فقط)
    python3 tools/prepare_new_assets.py --list-sections
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import unicodedata
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from spanned_zip import SpannedZip  # noqa: E402

REPO = "mohamadelfrgena-ai/vae"
BRANCH = "main"
PARTS = [f"news.z{i:02d}" for i in range(1, 8)] + ["news.zip"]
RAW = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/"

DEFAULT_OUT = os.path.join(ROOT, "new-assets")
DEFAULT_MANIFEST = os.path.join(ROOT, "assets-manifest.json")

IMAGE_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp"}

# ---------------------------------------------------------------------------
# الأقسام التاريخية في news.*: مجلد ← (المسار، المعرّف، الاسم، الخطة).
# ملاحظة مهمة: هذه بذرة المرحلة 15 فقط. الترحيل 45 يحذف أفاتارات free القديمة،
# ثم 46_profile_avatar_packs يستبدل Profiles من supabase/profile-packs.json
# (24 Epic) ويضيف Profile 2 (11 Legendary)، مع إبقاء onefree وحدها مجانية.
# لا تستخدم تعريف Profiles التاريخي أدناه بوصفه الحالة النهائية للحزم.
# ---------------------------------------------------------------------------
SECTIONS = {
    # ------------------------------------------------ الأفاتارات (profiles)
    "avatars": [
        ("افتارات/pfp",                        "pfp",             "PFP",            "free"),
        ("افتارات/profiles",                   "profiles",        "Profiles",       "free"),
        ("افتارات/pack one",                   "pack-one",        "Pack One",       "free"),
        ("افتارات/Best Cartoon-pfpsgg-pack",   "best-cartoon",    "Best Cartoon",   "epic"),
        ("افتارات/Cartoon Meme-pfpsgg-pack",   "cartoon-meme",    "Cartoon Meme",   "epic"),
        ("افتارات/Cool Cartoon-pfpsgg-pack",   "cool-cartoon",    "Cool Cartoon",   "epic"),
        ("افتارات/Call Of Duty-pfpsgg-pack",   "call-of-duty",    "Call Of Duty",   "epic"),
        ("افتارات/_aw fasdf sad-pfpsgg-pack",  "sad",             "Sad",            "epic"),
        ("افتارات/ahh-pfpsgg-pack",            "ahh",             "Ahh",            "epic"),
        ("افتارات/Moumen-pfpsgg-pack",         "moumen",          "Moumen",         "legendary"),
        ("افتارات/Animated-pfpsgg-pack",       "animated",        "Animated",       "legendary"),
    ],
    # -------------------------------------------------- الملصقات (stickers)
    "stickers": [
        ("Stickers/Memes",            "memes",             "Memes",             "free"),
        ("Stickers/Minecraft Memes1", "minecraft-memes-1", "Minecraft Memes 1", "free"),
        ("Stickers/Minecraft Memes2", "minecraft-memes-2", "Minecraft Memes 2", "free"),
        ("Stickers/Minecraft Memes3", "minecraft-memes-3", "Minecraft Memes 3", "free"),
        ("Stickers/Minecraft Movie",  "minecraft-movie",   "Minecraft Movie",   "free"),
        ("Stickers/Top",              "top",               "Top",               "free"),
    ],
    # ---------------------------------------------------- الأدوات (items)
    "items": [
        ("items/minecraft item", "minecraft-item", "Minecraft Items", "legendary"),
        ("items/games",          "games",          "Games",           "legendary"),
        ("items/playstation",    "playstation",    "PlayStation",     "legendary"),
        ("items/Random things",  "random-things",  "Random Things",   "legendary"),
    ],
}

FOLDER_FOR_TYPE = {"avatars": "profiles", "stickers": "stickers", "items": "items"}


# --------------------------------------------------------------------- مساعدات
def slugify(text: str) -> str:
    """يحوّل اسم ملف إلى مقطع آمن للروابط: حروف صغيرة وأرقام وشرطات فقط."""
    text = unicodedata.normalize("NFKD", text)
    text = text.encode("ascii", "ignore").decode("ascii").lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text or "img"


GENERIC_WORDS = {
    "file", "files", "image", "images", "img", "photo", "photos", "pic", "pics",
    "download", "downloads", "untitled", "asset", "sticker", "avatar", "pfp", "gif", "png",
}


def prettify(stem: str, fallback: str) -> str:
    """اسم معروض لطيف: يحذف أرقام الملفات البادئة والأسماء العامة، ويرتّب الحروف.

    مثال: «1491-minecraft» → «Minecraft» · «file_2346084» → الاسم البديل (مثل «Memes 12»)
    """
    text = stem.replace("_", " ").replace("-", " ").strip()
    text = re.sub(r"\s+", " ", text)

    # أرقام بادئة من مواقع التحميل مثل «1491 minecraft» أو «174095 cake»
    text = re.sub(r"^\d{3,}\s+", "", text).strip()

    # أسماء عامة أو أرقام بحتة ⇒ اسم القسم مع رقم متسلسل
    words = text.lower().split()
    meaningful = [w for w in words if w not in GENERIC_WORDS and not w.isdigit()]
    if not meaningful:
        return fallback

    text = " ".join(meaningful)
    # حروف كبيرة أولى لكل كلمة لاتينية (نترك غير اللاتينية كما هي)
    text = " ".join(w[:1].upper() + w[1:] if w[:1].isascii() and w[:1].isalpha() else w for w in text.split())
    text = text[:52].strip()

    # أسماء مشوّهة (رموز كثيرة أو حروف متكررة بلا معنى) ⇒ الاسم البديل
    if len(re.sub(r"[^A-Za-z\u0600-\u06FF]", "", text)) < 3:
        return fallback
    return text or fallback


def download_archive(target_dir: str) -> str:
    """ينزّل أقراص الأرشيف من المستودع العام ويرجع مسار القرص الأخير."""
    os.makedirs(target_dir, exist_ok=True)
    for index, part in enumerate(PARTS, 1):
        dest = os.path.join(target_dir, part)
        expected = os.path.getsize(dest) if os.path.exists(dest) else -1
        if expected > 0:
            print(f"   [{index}/{len(PARTS)}] موجود مسبقاً: {part}")
            continue
        url = RAW + part
        print(f"   [{index}/{len(PARTS)}] تنزيل {part} …")
        with urllib.request.urlopen(url, timeout=300) as resp, open(dest, "wb") as fh:
            shutil.copyfileobj(resp, fh)
        print(f"      {os.path.getsize(dest):,} بايت")
    return os.path.join(target_dir, "news.zip")


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:16]


def build_sections_listing():
    """يطبع جدول الأقسام (للتأكد من التطابق)."""
    for kind, entries in SECTIONS.items():
        print(f"===== {kind} =====")
        for folder, slug, label, plan in entries:
            print(f"  {folder:38} → {slug:18} {label:18} {plan}")


def prepare(archive_dir: str, out_dir: str, manifest_path: str, clean: bool) -> int:
    last_disk = os.path.join(archive_dir, "news.zip")
    if not os.path.exists(last_disk):
        print(f"✗ لم أجد الأرشيف في {archive_dir} (المطلوب: news.z01..news.z07 + news.zip)")
        return 2

    work = tempfile.mkdtemp(prefix="news-extract-")
    print(f"→ استخراج الأرشيف إلى {work}")
    z = SpannedZip(archive_dir, "news.zip")
    print(f"   الأقراص: {len(z.disk_files)} · الملفات في الفهرس: {len(z.entries)}")
    ok, failed = z.extract(work, on_error=lambda n, e: print(f"   ✗ {n} → {e}"))
    z.close()
    if failed:
        print(f"✗ فشل استخراج {failed} ملفاً — أتوقف")
        return 1
    print(f"   ✓ استُخرج {ok} ملفاً")

    if clean and os.path.isdir(out_dir):
        shutil.rmtree(out_dir)

    manifest = {"source": f"{REPO}/{BRANCH}", "sections": [], "assets": []}
    used_paths = set()
    used_names = {}

    for kind, entries in SECTIONS.items():
        base_out = os.path.join(out_dir, FOLDER_FOR_TYPE[kind])
        for folder, slug, label, plan in entries:
            src_dir = os.path.join(work, folder)
            if not os.path.isdir(src_dir):
                print(f"✗ قسم مفقود في الأرشيف: {folder}")
                return 1
            files = sorted(
                f for f in os.listdir(src_dir)
                if os.path.isfile(os.path.join(src_dir, f)) and os.path.splitext(f)[1].lower() in IMAGE_EXT
            )
            dest_dir = os.path.join(base_out, slug)
            os.makedirs(dest_dir, exist_ok=True)
            count = 0
            for index, file_name in enumerate(files, 1):
                ext = os.path.splitext(file_name)[1].lower()
                stem = os.path.splitext(file_name)[0]
                # اسم ملف جديد آمن وفريد داخل القسم
                new_name = f"{slugify(stem)[:48]}{ext}"
                remote = f"{FOLDER_FOR_TYPE[kind]}/{slug}/{new_name}"
                bump = 2
                while remote in used_paths:
                    new_name = f"{slugify(stem)[:44]}-{bump}{ext}"
                    remote = f"{FOLDER_FOR_TYPE[kind]}/{slug}/{new_name}"
                    bump += 1
                used_paths.add(remote)

                # اسم معروض فريد لكل نوع (الأدوات تُحفظ باسمها في الملف الشخصي)
                display = prettify(stem, fallback=f"{label} {index}")
                key = (kind, display.lower())
                if key in used_names:
                    used_names[key] += 1
                    display = f"{display} {used_names[key]}"
                else:
                    used_names[key] = 1

                src = os.path.join(src_dir, file_name)
                shutil.copy2(src, os.path.join(dest_dir, new_name))
                manifest["assets"].append({
                    "type": kind,
                    "name": display,
                    "path": remote,
                    "section": slug,
                    "section_label": label,
                    "plan": plan,
                    "animated": ext == ".gif",
                    "bytes": os.path.getsize(src),
                    "sha": sha256_of(src),
                    "order": count + 1,
                })
                count += 1
            manifest["sections"].append({
                "type": kind, "id": slug, "label": label, "plan": plan, "folder": folder,
                "count": count, "order": len(manifest["sections"]) + 1,
            })
            print(f"   ✓ {label:18} ({slug:18}) {count:>4} ملفاً")

    os.makedirs(os.path.dirname(manifest_path) or ".", exist_ok=True)
    with open(manifest_path, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=1)

    total = len(manifest["assets"])
    size = sum(a["bytes"] for a in manifest["assets"]) / 1e6
    animated = sum(1 for a in manifest["assets"] if a["animated"])
    print(f"\n✓ جهّزت {total} أصلاً ({size:.1f} MB · {animated} متحرّك GIF)")
    print(f"   الشجرة: {out_dir}")
    print(f"   البيان: {manifest_path}")
    shutil.rmtree(work, ignore_errors=True)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="تحضير أصول الواجهة الجديدة من الأرشيف المقسّم")
    ap.add_argument("--download", action="store_true", help="نزّل الأرشيف من GitHub أولاً")
    ap.add_argument("--archive-dir", default=None, help="مجلد أقراص الأرشيف (news.z01..news.zip)")
    ap.add_argument("--out", default=DEFAULT_OUT, help="مجلد الخروج (افتراضي new-assets/)")
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST, help="مسار بيان JSON")
    ap.add_argument("--keep", action="store_true", help="لا تحذف مجلد الخروج القديم")
    ap.add_argument("--list-sections", action="store_true", help="اطبع الأقسام فقط")
    args = ap.parse_args()

    if args.list_sections:
        build_sections_listing()
        return 0

    archive_dir = args.archive_dir
    if args.download:
        archive_dir = archive_dir or os.path.join(tempfile.gettempdir(), "news")
        print(f"→ تنزيل الأرشيف من {REPO}/{BRANCH}")
        download_archive(archive_dir)
    if not archive_dir:
        archive_dir = os.path.join(tempfile.gettempdir(), "news")
        print(f"ℹ️ لا مجلد أرشيف محدَّد — أستخدم {archive_dir}")

    return prepare(archive_dir, args.out, args.manifest, clean=not args.keep)


if __name__ == "__main__":
    sys.exit(main())
