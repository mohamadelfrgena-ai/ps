#!/usr/bin/env python3
"""Pin every Android app-icon surface to the supplied phone icon package."""

from __future__ import annotations

import hashlib
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"
KOTLIN = ROOT / "android/app/src/main/java"

# SHA-256 values are from vae/addonstation-icons.zip (phone/ only), archive
# SHA-256: 16baaf4c2cf9ef2a53619df88323ad70a3cdf6201cdd08596a7c6eb5c3817962.
EXPECTED: dict[str, tuple[str, tuple[int, int]]] = {
    "drawable-nodpi/app_icon.png": (
        "442d4feafab31195a751afa0f77595982ecaafa113bd94da1394a07e7ecf7b38", (512, 512)
    ),
    "drawable-nodpi/ic_launcher_background.png": (
        "5931af07da1afc6dfe227723a7d72edb05093209904e00b9a0060de0785669fa", (432, 432)
    ),
    "drawable-nodpi/ic_launcher_foreground.png": (
        "c90ebbfa39832a7a31f3d3948c9c38da50a3edb16478b7b804dfa1363f231f03", (432, 432)
    ),
    "drawable-nodpi/ic_launcher_monochrome.png": (
        "d80ffc759210f7170d97c9a3b2d2adac59b2f65e69c0166831c69adda9a1e3ce", (432, 432)
    ),
    "drawable-nodpi/ic_push_notification.png": (
        "d80ffc759210f7170d97c9a3b2d2adac59b2f65e69c0166831c69adda9a1e3ce", (432, 432)
    ),
    "mipmap-mdpi/ic_launcher.png": (
        "ffb4151524bac3522c985677a99ef4c05807a53a690812dc89fb14025884c781", (48, 48)
    ),
    "mipmap-mdpi/ic_launcher_round.png": (
        "808f3a102f801373be96047608c9a89966ebb62fc6aac481307e0310d886f235", (48, 48)
    ),
    "mipmap-hdpi/ic_launcher.png": (
        "5abecf43554a04e11710398d678aa7c376c4ea08e7dba738084d7d30efb2ef62", (72, 72)
    ),
    "mipmap-hdpi/ic_launcher_round.png": (
        "d4abbb7cb5da65cd11310f41a51bc7350ab1553159765ba97cc88e0b86f6d662", (72, 72)
    ),
    "mipmap-xhdpi/ic_launcher.png": (
        "fcf4eb26d9f2a5f6a8d89218cf8556ac4a63c64a1fe833804b5f24c1061b11df", (96, 96)
    ),
    "mipmap-xhdpi/ic_launcher_round.png": (
        "a42b79efaa3c08b8cbee8fc10710e4835f3a9fc271c195d5a622c609ee33edb9", (96, 96)
    ),
    "mipmap-xxhdpi/ic_launcher.png": (
        "c8c45a027dd1b17e91072856e1e3e46b9bdfb839338d3f4811a5184ee821823b", (144, 144)
    ),
    "mipmap-xxhdpi/ic_launcher_round.png": (
        "f7f6c6362a944d2c0a98510108262620449e9a98528883c99a7d6c70fe7b3a0d", (144, 144)
    ),
    "mipmap-xxxhdpi/ic_launcher.png": (
        "d8560b5bfffe654c784bda1627713665e2642c7a6c81aac21a06bb99e134f85e", (192, 192)
    ),
    "mipmap-xxxhdpi/ic_launcher_round.png": (
        "31ba0017cc8d5a79c970b86a4ef354d109ea80e2616d7b665e69b32801625641", (192, 192)
    ),
}

OLD_ICON_HASHES = {
    "a3ff0b039bba5f9e765e98ca8324d6db573b1bfba3a8682729361929e408ac93",
    "ca14bb7b0ba5f212bb0acad62a077c30060b2d82310ba1185bb787e7e3730574",
    "1e8c16fa39a0b730c24c1d005d145f18a3c9ca3994a1c3dba1149608cf497546",
    "d0d4e506724cc525cd042cc117b79e10a74dd1b95970ae3f74fd6a34e3601ccd",
    "472e9c07ba621be4657ab7bde5c1b9d66704074b48e111e5703aedcf7a2c6197",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_size(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) < 24:
        return None
    return struct.unpack(">II", data[16:24])


def main() -> int:
    failures: list[str] = []
    for relative, (expected_hash, expected_size) in EXPECTED.items():
        path = RES / relative
        if not path.is_file():
            failures.append(f"missing {relative}")
            continue
        actual_hash = digest(path)
        actual_size = png_size(path)
        if actual_hash != expected_hash:
            failures.append(f"wrong bytes {relative}: {actual_hash}")
        if actual_size != expected_size:
            failures.append(f"wrong dimensions {relative}: {actual_size}")
        if actual_hash in OLD_ICON_HASHES:
            failures.append(f"old icon survived at {relative}")

    manifest = MANIFEST.read_text(encoding="utf-8")
    if 'android:icon="@mipmap/ic_launcher"' not in manifest:
        failures.append("manifest does not use ic_launcher")
    if 'android:roundIcon="@mipmap/ic_launcher_round"' not in manifest:
        failures.append("manifest does not use the supplied round icon")
    if 'android:resource="@drawable/ic_push_notification"' not in manifest:
        failures.append("manifest does not use the supplied monochrome notification icon")

    for api in (26, 33):
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            path = RES / f"mipmap-anydpi-v{api}" / name
            try:
                root = ET.parse(path).getroot()
            except Exception as error:
                failures.append(f"invalid adaptive icon {path.relative_to(ROOT)}: {error}")
                continue
            xml = path.read_text(encoding="utf-8")
            if "@drawable/ic_launcher_background" not in xml or "@drawable/ic_launcher_foreground" not in xml:
                failures.append(f"adaptive layers missing in {path.relative_to(ROOT)}")
            has_monochrome = "@drawable/ic_launcher_monochrome" in xml
            if (api == 33) != has_monochrome:
                failures.append(f"wrong monochrome declaration in {path.relative_to(ROOT)}")
            if not root.tag.endswith("adaptive-icon"):
                failures.append(f"wrong root in {path.relative_to(ROOT)}")

    kotlin = "\n".join(path.read_text(encoding="utf-8") for path in KOTLIN.rglob("*.kt"))
    if "painterResource(R.mipmap.ic_launcher)" in kotlin:
        failures.append("Compose still tries to decode the adaptive launcher XML directly")

    if failures:
        print("App icon guard failed:")
        for failure in failures:
            print(" -", failure)
        return 1
    print(f"App icon guard passed: {len(EXPECTED)} phone assets, adaptive + round + monochrome; no old icon bytes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
