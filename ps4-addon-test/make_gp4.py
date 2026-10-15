#!/usr/bin/env python3
"""Extracts PrizmaVisuals-1.3.10222.zip at build time into assets/folder.raw."""
import os
import shutil
import struct
import sys
import tempfile
import zipfile

MAGIC = b"PVRAW001"
ADDON_FOLDER_NAME = "PrizmaVisuals 1.3.10222"


def is_pure_ascii(text: str) -> bool:
    return all(32 <= ord(c) <= 126 for c in text)


def build_preextracted_folder(zip_path: str, out_raw_path: str) -> tuple[int, int]:
    if os.path.exists(out_raw_path):
        os.remove(out_raw_path)

    with tempfile.TemporaryDirectory() as tmpdir:
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(tmpdir)

        extracted_root = os.path.join(tmpdir, ADDON_FOLDER_NAME)
        if not os.path.isdir(extracted_root):
            raise RuntimeError(f"Expected folder '{ADDON_FOLDER_NAME}' inside {zip_path}")

        rel_dirs: list[str] = []
        rel_files: list[tuple[str, str]] = []

        for root, dirs, files in os.walk(extracted_root):
            dirs.sort()
            files.sort()
            rel_root = os.path.relpath(root, extracted_root).replace("\\", "/")
            if rel_root != ".":
                if not is_pure_ascii(rel_root):
                    continue
                rel_dirs.append(rel_root)
            for fname in files:
                full_path = os.path.join(root, fname)
                rel_path = fname if rel_root == "." else f"{rel_root}/{fname}"
                if not is_pure_ascii(rel_path):
                    continue
                rel_files.append((rel_path, full_path))

        with open(out_raw_path, "wb") as out_f:
            out_f.write(MAGIC)
            out_f.write(struct.pack("<I", len(rel_dirs)))
            for d in rel_dirs:
                b = d.encode("utf-8")
                out_f.write(struct.pack("<H", len(b)))
                out_f.write(b)

            out_f.write(struct.pack("<I", len(rel_files)))
            for rel_path, full_path in rel_files:
                b = rel_path.encode("utf-8")
                size = os.path.getsize(full_path)
                out_f.write(struct.pack("<HQ", len(b), size))
                out_f.write(b)
                with open(full_path, "rb") as in_f:
                    shutil.copyfileobj(in_f, out_f, length=256 * 1024)

        return len(rel_dirs), len(rel_files)


def main() -> None:
    zip_path = os.path.join("assets", "PrizmaVisuals-1.3.10222.zip")
    out_raw = os.path.join("assets", "folder.raw")
    if len(sys.argv) >= 2:
        out_raw = sys.argv[1]
    num_dirs, num_files = build_preextracted_folder(zip_path, out_raw)
    print(f"Pre-extracted '{ADDON_FOLDER_NAME}': {num_dirs} dirs, {num_files} files -> {out_raw}")


if __name__ == "__main__":
    main()
