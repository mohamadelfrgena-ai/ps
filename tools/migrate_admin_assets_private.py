#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ينقل أصول ``required_plan=admin`` من ``site-assets`` العام إلى مخزن خاص.

الأمان:
  * الوضع الافتراضي تقرير فقط؛ لا كتابة ولا حذف إلا مع ``--apply``.
  * ينسخ الملف أولاً، ثم يحدّث مراجع القاعدة، ثم يحذف النسخة العامة أخيراً.
  * لا يطبع المفتاح السري ولا يحفظه في ملف.

قبل التشغيل نفّذ ``supabase/40_admin_asset_privacy.sql``، ثم من بيئة آمنة:

    SUPABASE_URL=https://xxxx.supabase.co \\
    SUPABASE_SECRET_KEY=sb_secret_xxx \\
    python3 tools/migrate_admin_assets_private.py             # تقرير

    python3 tools/migrate_admin_assets_private.py --apply     # تنفيذ صريح
"""
from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

PUBLIC_BUCKET = "site-assets"
PRIVATE_BUCKET = "admin-assets"
PRIVATE_PREFIX = "admin-private/"


@dataclass(frozen=True)
class Asset:
    id: int
    asset_type: str
    name: str
    file_url: str

    @property
    def destination(self) -> str:
        clean = self.file_url.strip().lstrip("/")
        return clean if clean.startswith(PRIVATE_PREFIX) else PRIVATE_PREFIX + clean

    @property
    def public_source(self) -> str:
        clean = self.file_url.strip().lstrip("/")
        return clean[len(PRIVATE_PREFIX):] if clean.startswith(PRIVATE_PREFIX) else clean


def auth_headers(key: str, content_type: str | None = None) -> dict[str, str]:
    headers = {"apikey": key, "Accept": "application/json"}
    # مفاتيح service_role القديمة JWT؛ مفاتيح sb_secret الجديدة تمر في apikey فقط.
    if key.startswith("eyJ"):
        headers["Authorization"] = "Bearer " + key
    if content_type:
        headers["Content-Type"] = content_type
    return headers


def http(
    method: str,
    url: str,
    key: str,
    payload: bytes | None = None,
    content_type: str | None = None,
    extra: dict[str, str] | None = None,
    allow_404: bool = False,
) -> tuple[int, bytes, object]:
    headers = auth_headers(key, content_type)
    headers.update(extra or {})
    req = urllib.request.Request(url, data=payload, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as exc:
        body = exc.read()
        detail = body.decode("utf-8", "replace")
        # Storage currently reports a missing private object either as a real
        # HTTP 404 or as HTTP 400 with a nested 404/Object-not-found payload.
        # Only normalize that exact absence signal; every other 400 remains a
        # deployment failure rather than being silently treated as missing.
        missing_object = exc.code == 404 or (
            exc.code == 400
            and ("Object not found" in detail or '"statusCode":"404"' in detail)
        )
        if allow_404 and missing_object:
            return 404, body, exc.headers
        raise RuntimeError(f"HTTP {exc.code}: {detail[:400]}") from exc


def encoded(path: str) -> str:
    return urllib.parse.quote(path.strip().lstrip("/"), safe="/")


def rest_url(base: str, resource: str) -> str:
    return f"{base.rstrip('/')}/rest/v1/{resource}"


def list_admin_assets(base: str, key: str) -> list[Asset]:
    resource = "site_assets?select=id,asset_type,name,file_url&required_plan=eq.admin&order=id.asc"
    _, body, _ = http("GET", rest_url(base, resource), key)
    rows = json.loads(body.decode("utf-8") or "[]")
    return [
        Asset(int(row["id"]), str(row["asset_type"]), str(row["name"]), str(row["file_url"]))
        for row in rows
    ]


def download(base: str, key: str, bucket: str, path: str, allow_404: bool = False):
    url = f"{base.rstrip('/')}/storage/v1/object/{bucket}/{encoded(path)}"
    status, body, headers = http("GET", url, key, allow_404=allow_404)
    if status == 404:
        return None
    content_type = getattr(headers, "get_content_type", lambda: None)()
    if not content_type or content_type == "application/octet-stream":
        content_type = mimetypes.guess_type(path)[0] or "application/octet-stream"
    return body, content_type


def upload(base: str, key: str, bucket: str, path: str, blob: bytes, content_type: str) -> None:
    url = f"{base.rstrip('/')}/storage/v1/object/{bucket}/{encoded(path)}"
    http(
        "POST",
        url,
        key,
        payload=blob,
        content_type=content_type,
        extra={"x-upsert": "true", "cache-control": "3600"},
    )


def patch(base: str, key: str, resource: str, values: dict) -> None:
    payload = json.dumps(values, ensure_ascii=False).encode("utf-8")
    http(
        "PATCH",
        rest_url(base, resource),
        key,
        payload=payload,
        content_type="application/json",
        extra={"Prefer": "return=minimal"},
    )


def delete_objects(base: str, key: str, bucket: str, paths: list[str]) -> None:
    if not paths:
        return
    payload = json.dumps({"prefixes": paths}).encode("utf-8")
    http(
        "DELETE",
        f"{base.rstrip('/')}/storage/v1/object/{bucket}",
        key,
        payload=payload,
        content_type="application/json",
    )


def eq_filter(column: str, value: str) -> str:
    return f"{column}=eq.{urllib.parse.quote(value, safe='')}"


def migrate_one(base: str, key: str, asset: Asset) -> str:
    catalog_path = asset.file_url.strip().lstrip("/")
    source = asset.public_source
    destination = asset.destination

    # قد يكون SQL 40 سبق وغيّر مسار الصف قبل نقل البايتات؛ لذلك نفحص المخزن
    # الخاص فعلياً ولا نعتبر البادئة وحدها دليلاً على اكتمال النقل.
    private_original = download(base, key, PRIVATE_BUCKET, destination, allow_404=True)
    public_original = download(base, key, PUBLIC_BUCKET, source, allow_404=True)
    copied = False
    if private_original is None:
        if public_original is None:
            raise RuntimeError(f"الملف مفقود من المخزنين: {source}")
        blob, mime = public_original
        upload(base, key, PRIVATE_BUCKET, destination, blob, mime)
        copied = True

    # لقطة GIF اختيارية. عدم وجودها طبيعي؛ إن وُجدت تنتقل مع الأصل.
    thumb_source = source + ".thumb.jpg"
    thumb_destination = destination + ".thumb.jpg"
    private_thumb = download(base, key, PRIVATE_BUCKET, thumb_destination, allow_404=True)
    public_thumb = download(base, key, PUBLIC_BUCKET, thumb_source, allow_404=True)
    if private_thumb is None and public_thumb is not None:
        thumb_blob, thumb_mime = public_thumb
        upload(base, key, PRIVATE_BUCKET, thumb_destination, thumb_blob, thumb_mime)

    # If the catalog still has a public path, this tool is being run without
    # migration 40 and must update profile references itself. When migration 40
    # already changed the catalog path (the production case), it also changed
    # those profile references transactionally; do not issue redundant REST
    # writes against the protected profiles table.
    if catalog_path != destination:
        if asset.asset_type == "avatar":
            patch(base, key, "profiles?" + eq_filter("avatar_url", source), {"avatar_url": destination})
        elif asset.asset_type == "banner":
            patch(base, key, "profiles?" + eq_filter("banner_url", source), {"banner_url": destination})
        elif asset.asset_type == "item":
            patch(base, key, "profiles?" + eq_filter("equipped_item_id", source), {"equipped_item_id": asset.name})
        patch(base, key, f"site_assets?id=eq.{asset.id}", {"file_url": destination})

    # آخر خطوة فقط: إزالة أي نسخة عامة بعد التأكد أن النسخة الخاصة موجودة.
    public_paths = []
    if public_original is not None:
        public_paths.append(source)
    if public_thumb is not None:
        public_paths.append(thumb_source)
    delete_objects(base, key, PUBLIC_BUCKET, public_paths)
    if public_paths:
        return "copied_and_public_removed" if copied else "public_removed"
    return "copied" if copied else "already_private"


def selftest() -> int:
    cases = [
        (Asset(1, "avatar", "PFP 4", "profiles/pfp/download-1.jpg"),
         "admin-private/profiles/pfp/download-1.jpg"),
        (Asset(2, "banner", "Admin", "/banners/admin/a.gif"),
         "admin-private/banners/admin/a.gif"),
        (Asset(3, "item", "X", "admin-private/items/x.gif"),
         "admin-private/items/x.gif"),
    ]
    for asset, expected in cases:
        assert asset.destination == expected, (asset.destination, expected)
        assert asset.public_source == expected[len(PRIVATE_PREFIX):]
    assert eq_filter("avatar_url", "profiles/a b.png") == "avatar_url=eq.profiles%2Fa%20b.png"
    print("✓ selftest: مسارات المخزن الخاص وترميز مرشحات REST سليمة")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="نقل أصول الأدمن إلى مخزن Supabase خاص")
    parser.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    parser.add_argument(
        "--key",
        default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")),
    )
    parser.add_argument("--apply", action="store_true", help="تنفيذ النقل والحذف (بدونه: تقرير فقط)")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        return selftest()
    if not args.url.startswith("http"):
        print("✗ مرّر SUPABASE_URL أو --url https://xxxx.supabase.co")
        return 2
    if not args.key:
        print("✗ مرّر SUPABASE_SECRET_KEY أو --key (لا تستخدم anon/publishable)")
        return 2
    if args.key.startswith("sb_publishable_"):
        print("✗ المفتاح عام؛ النقل يحتاج sb_secret أو service_role")
        return 2

    assets = list_admin_assets(args.url, args.key)
    print(f"→ أصول admin المطلوب التحقق منها: {len(assets)}")
    for asset in assets:
        print(f"   {asset.id}: {asset.asset_type} · {asset.public_source} → {asset.destination}")

    if not args.apply:
        print("✓ تقرير فقط — لم يتغيّر شيء. --apply يفحص المخزنين ثم ينقل/ينظّف ما يلزم.")
        return 0

    ok = 0
    failures: list[str] = []
    for asset in assets:
        try:
            outcome = migrate_one(args.url, args.key, asset)
            ok += 1
            labels = {
                "copied_and_public_removed": "نُقل وحُذفت النسخة العامة",
                "public_removed": "الخاص موجود وحُذفت النسخة العامة",
                "copied": "نُقل إلى الخاص (لم توجد نسخة عامة بعد النسخ)",
                "already_private": "خاص مسبقاً ولا نسخة عامة",
            }
            print(f"   ✓ {labels[outcome]}: {asset.destination}")
        except Exception as exc:  # لا نحذف ملفات بقية الأصول عند فشل أصل واحد
            failures.append(f"{asset.file_url}: {exc}")
            print(f"   ✗ {asset.file_url}: {exc}")

    print(f"\n✅ نجح: {ok} · ❌ فشل: {len(failures)}")
    if failures:
        print("لم تكتمل الخصوصية الفيزيائية لهذه الملفات؛ أصلح الخطأ ثم أعد التشغيل:")
        for failure in failures:
            print("  -", failure)
        return 1
    print("✓ كل أصول admin أصبحت في admin-assets الخاص ولا توجد لها نسخة عامة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
