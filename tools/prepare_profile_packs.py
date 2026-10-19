#!/usr/bin/env python3
"""Prepare the two owner-approved profile image packs from pinned GitHub ZIPs.

The ZIPs are intentionally not trusted by filename alone: this tool checks their
full SHA-256, rejects traversal/symlinks/encrypted entries, accepts only images,
and writes a deterministic manifest consumed by the focused upload workflow.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import stat
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "supabase/profile-pack-files"
DEFAULT_MANIFEST = ROOT / "supabase/profile-packs.json"
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}


@dataclass(frozen=True)
class Pack:
    archive_name: str
    source_url: str
    git_blob_sha: str
    archive_sha256: str
    archive_root: str
    slug: str
    label: str
    plan: str
    collection_order: int
    expected_files: int


PACKS = (
    Pack(
        archive_name="profiles.zip",
        source_url="https://github.com/mohamadelfrgena-ai/vae/blob/main/profiles.zip",
        git_blob_sha="cc321820a81ee9fe79bdee12c8fe368c49383195",
        archive_sha256="35d1babb11b9e9cc5bff116a8c2efe5c3ba814c085e0682e133e50291a604be1",
        archive_root="profiles",
        slug="profiles",
        label="Profiles",
        plan="epic",
        collection_order=2,
        expected_files=24,
    ),
    Pack(
        archive_name="porfile.zip",
        source_url="https://github.com/mohamadelfrgena-ai/vae/blob/main/porfile.zip",
        git_blob_sha="ee130eaae3c992550d652d613ca6b4392ff6acb8",
        archive_sha256="58e6690bb2c7f08cc07b6544d8240568c1dbd1e5d658ded2f2d48eaa82b06608",
        archive_root="porfile",
        slug="profile-2",
        label="Profile 2",
        plan="legendary",
        collection_order=3,
        expected_files=11,
    ),
)


def digest(data: bytes, algorithm: str = "sha256") -> str:
    return hashlib.new(algorithm, data).hexdigest()


def file_digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def slugify(value: str) -> str:
    clean = value.encode("ascii", "ignore").decode("ascii").lower()
    clean = re.sub(r"[^a-z0-9]+", "-", clean).strip("-")
    return clean or "profile"


def display_name(pack: Pack, source_stem: str, index: int) -> str:
    if pack.slug == "profile-2":
        if "kermit" in source_stem.lower():
            return "Kermit The Frog"
        return f"Profile 2 {index}"
    text = source_stem.replace("_", " ").strip()
    text = re.sub(r"\s+", " ", text)
    return " ".join(word[:1].upper() + word[1:] for word in text.split())


def validate_image(data: bytes, suffix: str, name: str) -> None:
    signatures = {
        ".jpg": data.startswith(b"\xff\xd8\xff"),
        ".jpeg": data.startswith(b"\xff\xd8\xff"),
        ".png": data.startswith(b"\x89PNG\r\n\x1a\n"),
        ".gif": data.startswith((b"GIF87a", b"GIF89a")),
        ".webp": len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP",
    }
    if not signatures.get(suffix, False):
        raise ValueError(f"image signature does not match extension: {name}")


def archive_entries(pack: Pack, archive_path: Path) -> list[tuple[str, bytes]]:
    actual_sha = file_digest(archive_path)
    if actual_sha != pack.archive_sha256:
        raise ValueError(
            f"{pack.archive_name} SHA-256 mismatch: expected {pack.archive_sha256}, got {actual_sha}"
        )

    output: list[tuple[str, bytes]] = []
    seen: set[str] = set()
    with ZipFile(archive_path) as archive:
        bad_crc = archive.testzip()
        if bad_crc:
            raise ValueError(f"CRC failure in {pack.archive_name}: {bad_crc}")
        for info in archive.infolist():
            if info.is_dir():
                continue
            path = PurePosixPath(info.filename)
            mode = info.external_attr >> 16
            if (
                path.is_absolute()
                or ".." in path.parts
                or "\\" in info.filename
                or info.flag_bits & 1
                or stat.S_ISLNK(mode)
            ):
                raise ValueError(f"unsafe ZIP entry: {info.filename}")
            if len(path.parts) != 2 or path.parts[0] != pack.archive_root:
                raise ValueError(f"unexpected ZIP layout: {info.filename}")
            suffix = path.suffix.lower()
            if suffix not in ALLOWED_EXTENSIONS:
                raise ValueError(f"non-image entry in pack: {info.filename}")
            if info.file_size <= 0 or info.file_size > 5 * 1024 * 1024:
                raise ValueError(f"invalid image size in pack: {info.filename} ({info.file_size})")
            key = path.name.casefold()
            if key in seen:
                raise ValueError(f"duplicate case-insensitive filename: {path.name}")
            seen.add(key)
            data = archive.read(info)
            validate_image(data, suffix, info.filename)
            output.append((path.name, data))

    output.sort(key=lambda item: item[0].casefold())
    if len(output) != pack.expected_files:
        raise ValueError(
            f"{pack.archive_name} contains {len(output)} images; expected {pack.expected_files}"
        )
    return output


def prepare(archives_dir: Path, out_dir: Path, manifest_path: Path) -> dict:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest: dict = {
        "version": 1,
        "bucket": "site-assets",
        "packs": [],
        "assets": [],
    }
    used_paths: set[str] = set()

    for pack in PACKS:
        archive_path = archives_dir / pack.archive_name
        if not archive_path.is_file():
            raise FileNotFoundError(f"missing archive: {archive_path}")
        entries = archive_entries(pack, archive_path)
        pack_assets = []
        for index, (source_name, data) in enumerate(entries, 1):
            source_path = PurePosixPath(source_name)
            suffix = source_path.suffix.lower()
            stem = source_path.stem
            if pack.slug == "profile-2" and "kermit" not in stem.lower():
                output_stem = stem.lower()
            else:
                output_stem = slugify(stem)
            output_name = f"{output_stem}{suffix}"
            remote_path = f"profiles/{pack.slug}/{output_name}"
            if remote_path in used_paths:
                raise ValueError(f"duplicate output path: {remote_path}")
            used_paths.add(remote_path)

            local_path = out_dir / remote_path
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(data)
            asset = {
                "pack": pack.slug,
                "collection": pack.label,
                "collection_order": pack.collection_order,
                "plan": pack.plan,
                "order": index,
                "name": display_name(pack, stem, index),
                "path": remote_path,
                "source_name": source_name,
                "bytes": len(data),
                "sha256": digest(data),
                "md5": digest(data, "md5"),
                "animated": suffix == ".gif",
            }
            manifest["assets"].append(asset)
            pack_assets.append(remote_path)

        manifest["packs"].append(
            {
                "slug": pack.slug,
                "label": pack.label,
                "plan": pack.plan,
                "collection_order": pack.collection_order,
                "count": len(entries),
                "archive": pack.archive_name,
                "source_url": pack.source_url,
                "git_blob_sha": pack.git_blob_sha,
                "archive_sha256": pack.archive_sha256,
                "paths": pack_assets,
            }
        )

    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Prepare pinned profile image packs")
    parser.add_argument("--archives-dir", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args(argv)

    try:
        manifest = prepare(args.archives_dir, args.out, args.manifest)
    except (OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    total_bytes = sum(asset["bytes"] for asset in manifest["assets"])
    for pack in manifest["packs"]:
        print(f"{pack['label']}: {pack['count']} images · {pack['plan']}")
    print(f"Prepared {len(manifest['assets'])} images ({total_bytes} bytes)")
    print(f"Files: {args.out}")
    print(f"Manifest: {args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
