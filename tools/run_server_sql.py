#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تنفيذ ملفات الخادم على Supabase من سير GitHub (بلا SQL Editor يدوياً).

المتغيّرات المطلوبة:
    PROJECT_REF            مرجع المشروع (ref)
    SUPABASE_ACCESS_TOKEN  مفتاح واجهة الإدارة (Management API)
واختيارية (لكتابة الحصيلة في حالة الالتزام حتى تُقرأ من أي مكان):
    GITHUB_REPOSITORY · GITHUB_SHA · GITHUB_TOKEN

ماذا ينفّذ؟ الملفات بالترتيب — وكلٌّ آمن للتكرار:
    35_push_live.sql        الإشعارات الحقيقية + جدولة الطابور
    36_profile_stats.sql    إحصاءات الملف الشخصي (أصدقاء · تنزيلات · مفضّلة · مستوى)
    37_profile_reports.sql  الإبلاغ عن عضو من ملفّه
    38_user_mute.sql        كتم العضو (لا رسائل ولا إشعارات · ميزة Legendary)

لماذا سكربت بدل كتابة SQL داخل YAML؟ لأن heredoc داخل ملف سير العمل سبق أن
أخفى سبب الفشل تماماً (لا مخرجات ولا ملاحظات). هنا: كل ملف يُطبع نتاجه، وإعادة
محاولة عند سقوط الشبكة، والحصيلة تُكتب في **حالة الالتزام** فتُقرأ بـ:

    gh api repos/<owner>/<repo>/commits/<sha>/statuses
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

FILES = [
    "supabase/35_push_live.sql",
    "supabase/36_profile_stats.sql",
    "supabase/37_profile_reports.sql",
    "supabase/38_user_mute.sql",
]

# فحص صريح بعد التنفيذ: هل وصلت الدوال/الأعمدة فعلاً؟
CHECK = (
    "select 'profile_stats=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'profile_stats')::text"
    " || ' chat_report_user=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'chat_report_user')::text"
    " || ' user_mute=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'user_mute')::text"
    " || ' can_mute_members=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'can_mute_members')::text"
    " || ' user_mutes=' || (select count(*) from information_schema.tables "
    "where table_schema = 'public' and table_name = 'user_mutes')::text"
    " || ' muted_users=' || (select count(*) from information_schema.columns "
    "where table_schema = 'public' and table_name = 'push_prefs' "
    "and column_name = 'muted_users')::text as report"
)


def api(query: str) -> str:
    """ينفّذ استعلاماً عبر واجهة إدارة Supabase. يرفع الاستثناء عند الفشل."""
    ref = os.environ["PROJECT_REF"]
    token = os.environ["SUPABASE_ACCESS_TOKEN"]
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{ref}/database/query",
        data=json.dumps({"query": query}).encode(), method="POST")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=180) as res:
        return res.read().decode()


def run_file(path: str, tries: int = 3) -> bool:
    """ينفّذ ملف SQL مع إعادة محاولة، ويطبع سبب الفشل الحقيقي."""
    try:
        sql = open(path, encoding="utf-8").read()
    except Exception as exc:  # الملف مفقود/غير مقروء
        print(f"::error title=ملف مفقود {path}::{type(exc).__name__} {exc}")
        return False
    last = ""
    for attempt in range(1, tries + 1):
        try:
            api(sql)
            print(f"::notice title=نُفِّذ {path}::كل عباراته نجحت (محاولة {attempt})")
            return True
        except urllib.error.HTTPError as exc:
            body = ""
            try:
                body = exc.read().decode()[:300]
            except Exception:
                pass
            last = f"HTTP {exc.code} {body}"
        except Exception as exc:
            last = f"{type(exc).__name__} {exc}"[:260]
        if attempt < tries:
            time.sleep(5)
    print("::error title=فشل تنفيذ " + path + "::" + last.replace("\n", " ")[:400])
    return False


def write_status(notes, ok: bool) -> None:
    """حصيلة التنفيذ في حالة الالتزام — تُقرأ من أي مكان بلا سجلّات."""
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("GITHUB_SHA")
    token = os.environ.get("GITHUB_TOKEN")
    if not (repo and sha and token):
        print("::warning::بلا GITHUB_REPOSITORY/GITHUB_SHA/GITHUB_TOKEN — لن تُكتب الحصيلة")
        return
    body = json.dumps({
        "state": "success" if ok else "failure",
        "context": "خادم SQL (35 · 36 · 37 · 38)",
        "description": " · ".join(notes)[:130],
    }).encode()
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/statuses/{sha}", data=body, method="POST")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/vnd.github+json")
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            print("✓ الحصيلة في حالة الالتزام:", res.status)
    except Exception as exc:
        print("::warning::تعذّر كتابة الحصيلة: " + str(exc)[:140])


def main() -> int:
    print("=== تنفيذ ملفات الخادم ===")
    results = [(path, run_file(path)) for path in FILES]

    check_text = "check=?"
    try:
        check_text = api(CHECK).strip()[:220]
        print("::notice title=تحقّق الخادم::" + check_text)
    except Exception as exc:
        print("::warning::تعذّر التحقّق: " + str(exc)[:140])
        check_text = "check=FAIL " + str(exc)[:120]

    notes = [f"{p.split('/')[-1]}={'ok' if ok else 'FAIL'}" for p, ok in results] + [check_text]
    ok = all(o for _, o in results) and "=FAIL" not in check_text
    write_status(notes, ok)
    print("=== انتهى ===")
    return 0        # لا نُسقط السير: الحصيلة تكفي، والفشل يُقرأ من حالة الالتزام


if __name__ == "__main__":
    sys.exit(main())
