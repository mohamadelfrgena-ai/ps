#!/usr/bin/env python3
"""Regression guard for protected addon download and publication integrity."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str, problems: list[str]) -> None:
    if needle not in text:
        problems.append(f"{label}: missing {needle!r}")


def forbid(text: str, needle: str, label: str, problems: list[str]) -> None:
    if needle in text:
        problems.append(f"{label}: forbidden regression {needle!r}")


def main() -> int:
    problems: list[str] = []
    core = read("supabase/02_functions_core.sql")
    bundle = read("supabase/00_ALL_IN_ONE.sql")
    migration = read("supabase/43_content_download_integrity.sql")
    client = read("android/app/src/main/java/com/addonstation/mobile/data/SupabaseClient.kt")
    repository = read("android/app/src/main/java/com/addonstation/mobile/data/repo/ContentRepository.kt")
    creator = read("android/app/src/main/java/com/addonstation/mobile/data/repo/CreatorRepository.kt")
    screen = read("android/app/src/main/java/com/addonstation/mobile/ui/store/StorePhoneDesign.kt")
    open_file_block = screen.split("private fun openDownloadedFile", 1)[-1].split(
        "private fun shareDownloadedFile", 1
    )[0]
    manifest = read("android/app/src/main/AndroidManifest.xml")
    provider_paths = read("android/app/src/main/res/xml/download_file_paths.xml")

    for label, sql in (("core SQL", core), ("all-in-one SQL", bundle), ("focused migration", migration)):
        require(sql, "from storage.objects o", label, problems)
        require(sql, "o.bucket_id = 'content-files'", label, problems)
        require(sql, "message = 'file_not_uploaded'", label, problems)
        require(sql, "content_items_require_uploaded_file", label, problems)
        require(sql, "before insert or update of is_published, file_path", label, problems)

    require(client, "suspend fun downloadToFile", "Supabase client", problems)
    require(client, "java.io.File.createTempFile", "Supabase client", problems)
    require(client, "body.byteStream().buffered()", "Supabase client", problems)
    require(client, "coroutine.ensureActive()", "Supabase client", problems)
    require(client, "download_size_mismatch", "Supabase client", problems)

    require(repository, 'outcome.data["size"].asLong()', "content repository", problems)
    require(repository, "supabase.downloadToFile(signed, file, expectedSize = expectedSize)", "content repository", problems)
    forbid(repository, "val bytes = supabase.downloadBytes(signed)", "content repository", problems)
    forbid(repository, "file.writeBytes(bytes)", "content repository", problems)

    require(screen, "DetailStage.NO_FILE", "content detail", problems)
    require(screen, "ApiErrorKind.FILE_NOT_UPLOADED -> DetailStage.NO_FILE", "content detail", problems)
    require(screen, "clipData = ClipData.newRawUri", "FileProvider intents", problems)
    require(open_file_block, 'setPackage(MINECRAFT_ANDROID_PACKAGE)', "Minecraft-only open", problems)
    require(open_file_block, 'MinecraftOpenResult.NOT_INSTALLED', "Minecraft missing state", problems)
    forbid(open_file_block, "Intent.createChooser", "Minecraft-only open", problems)
    require(screen, "R.string.phone_item_minecraft_required", "Minecraft install message", problems)
    require(manifest, '<package android:name="com.mojang.minecraftpe" />', "Minecraft package visibility", problems)
    require(manifest, "androidx.core.content.FileProvider", "Android manifest", problems)
    require(provider_paths, 'path="AddonStation/"', "FileProvider paths", problems)

    require(creator, 'val bucket = if (isCover) "site-assets" else "content-files"', "creator upload", problems)
    forbid(creator, 'val bucket = if (isCover) "site-assets" else "creator-media"', "creator upload", problems)

    if problems:
        print("Protected content download guard failed:")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print("Protected content download guard passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
