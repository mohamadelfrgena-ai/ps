#!/usr/bin/env python3
"""Static contract guard for the admin streaming ZIP batch importer."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android/app/src/main"
CORE = ANDROID / "java/com/addonstation/mobile/data/repo/BulkContentImport.kt"
CLIENT = ANDROID / "java/com/addonstation/mobile/data/SupabaseClient.kt"
REPO = ANDROID / "java/com/addonstation/mobile/data/repo/AdminRepository.kt"
SCREEN = ANDROID / "java/com/addonstation/mobile/ui/admin/AdminBulkContentImportScreen.kt"
NAV = ANDROID / "java/com/addonstation/mobile/ui/nav/AppNav.kt"
PANEL = ANDROID / "java/com/addonstation/mobile/ui/admin/AdminScreens.kt"
MIGRATION = ROOT / "supabase/47_bulk_content_import.sql"
ALL_IN_ONE = ROOT / "supabase/00_ALL_IN_ONE.sql"
BUILDER = ROOT / "tools/build_all_in_one_sql.py"
VERIFY = ROOT / "supabase/99_VERIFY_ALL.sql"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def main() -> int:
    checks: list[tuple[str, bool, str]] = []

    core = text(CORE)
    client = text(CLIENT)
    repo = text(REPO)
    screen = text(SCREEN)
    nav = text(NAV)
    panel = text(PANEL)
    sql = text(MIGRATION)
    all_sql = text(ALL_IN_ONE)

    checks += [
        (
            "Streaming limits are pinned",
            all(token in core for token in (
                "BULK_IMPORT_MAX_ARCHIVE_BYTES: Long = 1_073_741_824L",
                "BULK_IMPORT_MAX_CONTENT_BYTES: Long = 536_870_912L",
                "BULK_IMPORT_MAX_ENTRIES = 5_000",
                "BULK_IMPORT_MAX_ITEMS = 200",
                "BULK_IMPORT_MAX_TOTAL_UNCOMPRESSED: Long = 8L * 1024L * 1024L * 1024L",
            )),
            "1 GiB / 512 MiB / 5,000 / 200 / 8 GiB",
        ),
        (
            "Large archives never use whole-file ByteArray reads",
            ".readBytes(" not in core and ".readBytes(" not in client
            and "uploadFile(" in client and "file.inputStream()" in client,
            "streamed SAF copy, extraction, and Storage upload",
        ),
        (
            "Long upload is cancellable and reports real bytes",
            "suspendCancellableCoroutine" in client
            and "invokeOnCancellation { call.cancel() }" in client
            and "onProgress(sent, expectedLength)" in client,
            "OkHttp socket cancellation + byte callback",
        ),
        (
            "ZIP path/bomb/nested archive checks exist",
            all(token in core for token in (
                "ARCHIVE_UNSAFE_PATH", "ARCHIVE_SUSPICIOUS_COMPRESSION",
                "validateArchivePath(entry.name)", "validateNestedContentArchive",
                "seenPaths.add(pathKey)",
            )),
            "Zip Slip, duplicate paths, expansion and nested ZIP checks",
        ),
        (
            "Ambiguous registration retries without re-upload/delete",
            all(token in core for token in (
                "pendingRegistration", "pendingPayload == null",
                "preserveUploads = true", "REGISTRATION_UNCERTAIN",
            )),
            "same batch and manifest retained in memory",
        ),
        (
            "Repository exposes inspect/import operations",
            "inspectBulkContentArchive" in repo and "importBulkContentArchive" in repo,
            "AdminRepository bridge",
        ),
        (
            "Admin screen, item statuses, and route are wired",
            "AdminBulkContentImportScreen" in screen
            and "BulkItemUiStatus" in screen
            and "ADMIN_BULK_IMPORT" in nav
            and "AdminBulkContentImportScreen" in nav
            and "onOpenBulkImport" in panel,
            "AdminGate navigation + per-item UI states",
        ),
    ]

    validation_loops = [m.start() for m in re.finditer(r"jsonb_array_elements\(p_items\)", sql)]
    first_insert = sql.find("insert into public.content_items")
    checks += [
        (
            "Migration has admin-only batch history",
            all(token in sql for token in (
                "create table if not exists public.content_import_batches",
                "enable row level security",
                "content_import_batches_admin_read",
                "using (public.is_admin())",
                "revoke all on table public.content_import_batches from public, anon, authenticated",
            )),
            "RLS and least-privilege grants",
        ),
        (
            "RPC validates the complete batch before inserting",
            len(validation_loops) == 2
            and first_insert > (validation_loops[1] if len(validation_loops) == 2 else 10**9)
            and "pg_advisory_xact_lock" in sql
            and "storage.objects" in sql,
            f"validation loops={len(validation_loops)}, first insert={first_insert}",
        ),
        (
            "RPC is idempotent, audited, and access-controlled",
            all(token in sql for token in (
                "return v_existing.result", "insert into public.content_import_batches",
                "insert into public.admin_audit_log", "security definer",
                ") from public, anon;", ") to authenticated;",
            )),
            "batch result replay + audit log",
        ),
        (
            "Migration is generated into all-in-one and verifier",
            "\"47_bulk_content_import.sql\"" in text(BUILDER)
            and sql.strip() in all_sql
            and "47_bulk_content_import" in text(VERIFY),
            "source migration remains canonical",
        ),
    ]

    locale_dirs = ["values", "values-ar", "values-es", "values-fr", "values-pt", "values-ru", "values-tr", "values-zh"]
    expected_keys: set[str] | None = None
    locale_problem = ""
    for folder in locale_dirs:
        path = ANDROID / "res" / folder / "strings_bulk_import.xml"
        try:
            keys = {node.attrib["name"] for node in ET.parse(path).getroot().findall("string")}
        except Exception as exc:  # pragma: no cover - diagnostic path
            locale_problem = f"{folder}: {exc}"
            break
        if expected_keys is None:
            expected_keys = keys
        elif keys != expected_keys:
            locale_problem = f"{folder}: missing={sorted(expected_keys - keys)}, extra={sorted(keys - expected_keys)}"
            break
    checks.append((
        "All eight locales have matching bulk-import strings",
        not locale_problem and expected_keys is not None and len(expected_keys) >= 50,
        locale_problem or f"{len(expected_keys or ())} keys × 8 locales",
    ))

    checks.append((
        "Kotlin-only CI runs this guard",
        "python3 tools/check_bulk_content_import.py" in text(WORKFLOW),
        "no APK required",
    ))

    failed = False
    for label, ok, detail in checks:
        print(("✓" if ok else "✗"), label, f"— {detail}")
        failed |= not ok
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
