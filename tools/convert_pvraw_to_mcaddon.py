#!/usr/bin/env python3
"""Convert trusted PVRAW001 pack snapshots into one Minecraft .mcaddon file.

PVRAW001 is a simple lossless container used by the content release archive:

    magic[8] | directory_count<u32>
    repeated(directory_name_length<u16> | directory_name[bytes])
    file_count<u32>
    repeated(path_length<u16> | file_size<u64> | path[bytes] | payload[bytes])

Each input must contain exactly one Bedrock pack with a root manifest.json. The
resulting .mcaddon contains one .mcpack per input pack and can be imported by
Minecraft Bedrock. This tool deliberately rejects malformed paths, duplicate
entries, trailing bytes, and unreasonable counts before writing output.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import pathlib
import struct
import sys
import zipfile
from dataclasses import dataclass
from typing import BinaryIO, Iterable

MAGIC = b"PVRAW001"
MAX_ENTRIES = 100_000
MAX_NAME_BYTES = 16_384
MAX_FILE_BYTES = 512 * 1024 * 1024
FIXED_ZIP_TIME = (2020, 1, 1, 0, 0, 0)


class FormatError(ValueError):
    """Raised when a PVRAW input is malformed or unsafe."""


@dataclass(frozen=True)
class RawPack:
    source: pathlib.Path
    directories: tuple[str, ...]
    files: tuple[tuple[str, bytes], ...]
    manifest: dict
    pack_kind: str

    @property
    def uuid(self) -> str:
        header = self.manifest.get("header")
        return str(header.get("uuid", "")) if isinstance(header, dict) else ""

    @property
    def name(self) -> str:
        header = self.manifest.get("header")
        return str(header.get("name", self.source.stem)) if isinstance(header, dict) else self.source.stem


def _read_exact(stream: BinaryIO, size: int, label: str) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise FormatError(f"truncated {label}: expected {size} bytes, found {len(data)}")
    return data


def _read_uint(stream: BinaryIO, fmt: str, label: str) -> int:
    size = struct.calcsize(fmt)
    return int(struct.unpack(fmt, _read_exact(stream, size, label))[0])


def _read_name(stream: BinaryIO, label: str) -> str:
    size = _read_uint(stream, "<H", f"{label} length")
    if size <= 0 or size > MAX_NAME_BYTES:
        raise FormatError(f"invalid {label} length: {size}")
    try:
        name = _read_exact(stream, size, label).decode("utf-8")
    except UnicodeDecodeError as error:
        raise FormatError(f"{label} is not UTF-8") from error
    _validate_relative_path(name, label)
    return name


def _validate_relative_path(value: str, label: str) -> None:
    if "\\" in value or "\x00" in value:
        raise FormatError(f"unsafe {label}: {value!r}")
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or not value or any(part in ("", ".", "..") for part in path.parts):
        raise FormatError(f"unsafe {label}: {value!r}")


def _entry_count(stream: BinaryIO, label: str) -> int:
    count = _read_uint(stream, "<I", label)
    if count > MAX_ENTRIES:
        raise FormatError(f"unreasonable {label}: {count}")
    return count


def _pack_kind(manifest: dict) -> str:
    modules = manifest.get("modules")
    if not isinstance(modules, list):
        raise FormatError("manifest.json has no modules array")
    module_types = {
        str(module.get("type", "")).lower()
        for module in modules
        if isinstance(module, dict)
    }
    if "resources" in module_types:
        return "resources"
    if module_types.intersection({"data", "script"}):
        return "behavior"
    raise FormatError(f"unsupported Bedrock module types: {sorted(module_types)}")


def parse_pvraw(path: pathlib.Path) -> RawPack:
    """Parse one PVRAW pack with the on-disk file-size-before-path ordering."""
    with path.open("rb") as stream:
        if _read_exact(stream, len(MAGIC), "magic") != MAGIC:
            raise FormatError(f"{path.name}: invalid PVRAW001 magic")

        directory_count = _entry_count(stream, "directory count")
        directories = tuple(_read_name(stream, f"directory {index}") for index in range(directory_count))
        if len(set(directories)) != len(directories):
            raise FormatError(f"{path.name}: duplicate directory entry")

        file_count = _entry_count(stream, "file count")
        files: list[tuple[str, bytes]] = []
        seen: set[str] = set()
        for index in range(file_count):
            path_size = _read_uint(stream, "<H", f"file {index} path length")
            if path_size <= 0 or path_size > MAX_NAME_BYTES:
                raise FormatError(f"invalid file {index} path length: {path_size}")
            payload_size = _read_uint(stream, "<Q", f"file {index} payload size")
            if payload_size > MAX_FILE_BYTES:
                raise FormatError(f"file {index} is too large: {payload_size}")
            try:
                name = _read_exact(stream, path_size, f"file {index} path").decode("utf-8")
            except UnicodeDecodeError as error:
                raise FormatError(f"file {index} path is not UTF-8") from error
            _validate_relative_path(name, f"file {index} path")
            if name in seen:
                raise FormatError(f"{path.name}: duplicate file entry {name!r}")
            seen.add(name)
            payload = _read_exact(stream, payload_size, f"payload for {name}")
            files.append((name, payload))

        trailing = stream.read(1)
        if trailing:
            raise FormatError(f"{path.name}: unexpected trailing bytes")

    manifest_payload = dict(files).get("manifest.json")
    if manifest_payload is None:
        raise FormatError(f"{path.name}: root manifest.json is missing")
    try:
        manifest = json.loads(manifest_payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise FormatError(f"{path.name}: manifest.json is invalid") from error
    if not isinstance(manifest, dict):
        raise FormatError(f"{path.name}: manifest.json must be an object")

    return RawPack(
        source=path,
        directories=directories,
        files=tuple(files),
        manifest=manifest,
        pack_kind=_pack_kind(manifest),
    )


def _zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(filename=name, date_time=FIXED_ZIP_TIME)
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def build_mcpack(pack: RawPack) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, payload in sorted(pack.files, key=lambda pair: pair[0]):
            info = _zip_info(name)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    payload = output.getvalue()
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        bad = archive.testzip()
        if bad is not None:
            raise FormatError(f"generated {pack.pack_kind} pack failed CRC at {bad}")
    return payload


def build_mcaddon(packs: Iterable[RawPack], output_path: pathlib.Path) -> list[dict]:
    pack_list = list(packs)
    kinds = [pack.pack_kind for pack in pack_list]
    if len(pack_list) < 1:
        raise FormatError("at least one PVRAW input is required")
    if len(set(pack.uuid for pack in pack_list)) != len(pack_list):
        raise FormatError("input packs contain duplicate header UUIDs")
    if len(pack_list) > 1 and not {"behavior", "resources"}.issubset(set(kinds)):
        raise FormatError("a multi-pack addon must include behavior and resources packs")

    suffixes = {"behavior": "BP", "resources": "RP"}
    used_names: set[str] = set()
    report: list[dict] = []
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = output_path.with_name(f".{output_path.name}.part")
    try:
        with zipfile.ZipFile(temp_path, "w", compression=zipfile.ZIP_STORED) as addon:
            for index, pack in enumerate(sorted(pack_list, key=lambda item: item.pack_kind)):
                base = f"Sulfur_Cars_{suffixes.get(pack.pack_kind, index + 1)}.mcpack"
                if base in used_names:
                    raise FormatError(f"duplicate generated pack name: {base}")
                used_names.add(base)
                payload = build_mcpack(pack)
                info = _zip_info(base)
                info.compress_type = zipfile.ZIP_STORED
                addon.writestr(info, payload, compress_type=zipfile.ZIP_STORED)
                report.append(
                    {
                        "source": pack.source.name,
                        "kind": pack.pack_kind,
                        "uuid": pack.uuid,
                        "name": pack.name,
                        "files": len(pack.files),
                        "mcpack": base,
                        "mcpack_size": len(payload),
                        "mcpack_sha256": hashlib.sha256(payload).hexdigest(),
                    }
                )
        with zipfile.ZipFile(temp_path) as addon:
            if addon.testzip() is not None:
                raise FormatError("generated mcaddon failed CRC validation")
            for name in addon.namelist():
                with addon.open(name) as nested:
                    nested_payload = nested.read()
                with zipfile.ZipFile(io.BytesIO(nested_payload)) as pack_archive:
                    if "manifest.json" not in pack_archive.namelist() or pack_archive.testzip() is not None:
                        raise FormatError(f"generated nested pack is invalid: {name}")
        temp_path.replace(output_path)
    finally:
        temp_path.unlink(missing_ok=True)
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Convert PVRAW001 Bedrock packs to one .mcaddon")
    parser.add_argument("inputs", nargs="+", type=pathlib.Path, help="PVRAW001 input files")
    parser.add_argument("--output", required=True, type=pathlib.Path, help="destination .mcaddon path")
    parser.add_argument("--report", type=pathlib.Path, help="optional JSON report path")
    args = parser.parse_args(argv)

    if args.output.suffix.lower() != ".mcaddon":
        parser.error("--output must end in .mcaddon")

    try:
        packs = [parse_pvraw(path) for path in args.inputs]
        report = build_mcaddon(packs, args.output)
    except (OSError, FormatError, zipfile.BadZipFile) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    output_bytes = args.output.read_bytes()
    result = {
        "output": str(args.output),
        "size": len(output_bytes),
        "sha256": hashlib.sha256(output_bytes).hexdigest(),
        "packs": report,
    }
    rendered = json.dumps(result, indent=2, ensure_ascii=True, sort_keys=True)
    print(rendered)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
