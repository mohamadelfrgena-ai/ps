#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تنفيذ ملفات الخادم على Supabase من سير GitHub (بلا SQL Editor يدوياً).

المتغيّرات المطلوبة:
    PROJECT_REF            مرجع المشروع (ref)
    SUPABASE_ACCESS_TOKEN  مفتاح واجهة الإدارة (Management API)
واختيارية (لكتابة الحصيلة في حالة الالتزام حتى تُقرأ من أي مكان):
    GITHUB_REPOSITORY · GITHUB_SHA · GITHUB_TOKEN

ماذا ينفّذ؟ الملفات بالترتيب — وكلٌّ آمن للتكرار:
    33_user_block.sql       حظر المستخدمين (ينشئ user_blocks الذي يلمسه 38)
    34_push_notifications.sql  الإشعارات (زناد التحويل — إصلاح display_name)
    35_push_live.sql        الإشعارات الحقيقية + جدولة الطابور
    36_profile_stats.sql    إحصاءات الملف الشخصي (أصدقاء · تنزيلات · مفضّلة · مستوى)
    37_profile_reports.sql  الإبلاغ عن عضو من ملفّه
    38_user_mute.sql        كتم العضو (لا رسائل ولا إشعارات · ميزة Legendary)

لماذا سكربت بدل كتابة SQL داخل YAML؟ لأن heredoc داخل ملف سير العمل سبق أن
أخفى سبب الفشل تماماً (لا مخرجات ولا ملاحظات). هنا: كل ملف يُطبع نتاجه، وإعادة
محاولة عند سقوط الشبكة، وعند الفشل **تشخيص عبارة ببيان رقم سطرها**، والأسباب
تُكتب في **تعليق الالتزام** + الحصيلة في **حالة الالتزام** فتُقرأ بـ:

    gh api repos/<owner>/<repo>/commits/<sha>/comments --jq '.[0].body'
    gh api repos/<owner>/<repo>/commits/<sha>/statuses
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

FILES = [
    "supabase/33_user_block.sql",
    "supabase/34_push_notifications.sql",
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
    "and column_name = 'muted_users')::text"
    " || ' user_blocks=' || (select count(*) from information_schema.tables "
    "where table_schema = 'public' and table_name = 'user_blocks')::text"
    " || ' is_blocked_pair=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'is_blocked_pair')::text"
    " || ' push_on_notification=' || (select count(*) from pg_proc p "
    "join pg_namespace n on n.oid = p.pronamespace "
    "where n.nspname = 'public' and p.proname = 'push_on_notification')::text"
    " || ' display_name=' || (select count(*) from information_schema.columns "
    "where table_schema = 'public' and table_name = 'profiles' "
    "and column_name = 'display_name')::text as report"
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


def error_text(exc: Exception) -> str:
    if isinstance(exc, urllib.error.HTTPError):
        body = ""
        try:
            body = exc.read().decode()[:600]
        except Exception:
            pass
        text = f"HTTP {exc.code} {body}"
    else:
        text = f"{type(exc).__name__} {exc}"
    return " ".join(text.split())


def split_statements(sql: str):
    """يقسّم الملف إلى عبارات (لسطر التشخيص). يحترم $$…$$ و $اسم$…$اسم$."""
    # استبدال أسطر التعليق بمسافات يحفظ أرقام الأسطر كما هي
    lines = sql.splitlines()
    for i, line in enumerate(lines):
        if line.lstrip().startswith("--"):
            lines[i] = " " * len(line)
    text = "\n".join(lines)

    stmts, buf, i, line, start = [], [], 0, 1, 1
    tag = None
    while i < len(text):
        ch = text[i]
        if ch == "\n":
            line += 1
        if tag is None and ch == "$":
            found = None
            m = re.match(r"\$[A-Za-z_][A-Za-z_0-9]*\$|\$\$", text[i:])
            if m:
                found = m.group(0)
            if found:
                if not buf:
                    start = line
                buf.append(found)
                i += len(found)
                tag = found
                continue
        elif tag is not None and text.startswith(tag, i):
            buf.append(tag)
            i += len(tag)
            tag = None
            continue
        if tag is None and ch == ";":
            stmt = "".join(buf).strip()
            if stmt:
                stmts.append((start, stmt))
            buf = []
            i += 1
            start = line
            continue
        if not buf:
            start = line
        buf.append(ch)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        stmts.append((start, tail))
    return stmts


def diagnose(path: str, sql: str) -> str:
    """ينفّذ عبارات الملف واحدة واحدة ويُرجع نصّ أول عبارة فاشلة مع ردّ الخادم."""
    stmts = split_statements(sql)
    print(f"::warning title=تشخيص {path}::عدد العبارات: {len(stmts)}")
    for n, (line, stmt) in enumerate(stmts, 1):
        try:
            api(stmt)
        except Exception as exc:
            head = " ".join(stmt.split())[:170]
            out = (f"أول عبارة فاشلة في {path}: رقم {n} (سطر {line}) ⇒ {error_text(exc)[:320]}"
                   f" ‖ العبارة: {head}")
            print("::warning title=العبارة الفاشلة::" + out[:900])
            return out
    return f"{path}: كل العبارات نجحت منفردةً (المشكلة في التنفيذ ملفاً واحداً)"


def run_file(path: str, tries: int = 3):
    """ينفّذ ملف SQL مع إعادة محاولة. يُرجع (نجح؟، نصّ السبب)."""
    try:
        sql = open(path, encoding="utf-8").read()
    except Exception as exc:  # الملف مفقود/غير مقروء
        return False, f"{path}: ملف مفقود {type(exc).__name__}"
    last = ""
    for attempt in range(1, tries + 1):
        try:
            api(sql)
            print(f"::notice title=نُفِّذ {path}::كل عباراته نجحت (محاولة {attempt})")
            return True, ""
        except Exception as exc:
            last = error_text(exc)
        if attempt < tries:
            time.sleep(5)
    print(f"::warning title=فشل تنفيذ {path}::" + last[:500])
    reason = diagnose(path, sql) if os.environ.get("SQL_DIAGNOSE", "1") == "1" else last
    return False, f"{path}: {reason}"


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
        "context": "خادم SQL (33 · 34 · 35 · 36 · 37 · 38)",
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


def write_comment(body_text: str) -> None:
    """الأسباب الكاملة في تعليق الالتزام (الوصف في الحالة 140 حرفاً فقط)."""
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("GITHUB_SHA")
    token = os.environ.get("GITHUB_TOKEN")
    if not (repo and sha and token) or not body_text:
        return
    body = json.dumps({"body": body_text[:60000]}).encode()
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/commits/{sha}/comments",
        data=body, method="POST")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/vnd.github+json")
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            print("✓ الأسباب في تعليق الالتزام:", res.status)
    except Exception as exc:
        print("::warning::تعذّر كتابة التعليق: " + str(exc)[:140])


def main() -> int:
    print("=== تنفيذ ملفات الخادم ===")
    results, reasons = [], []
    for path in FILES:
        ok, reason = run_file(path)
        results.append((path, ok))
        if reason:
            reasons.append(reason)

    check_text = "check=?"
    try:
        check_text = api(CHECK).strip()[:220]
        print("::notice title=تحقّق الخادم::" + check_text)
    except Exception as exc:
        check_text = "check=FAIL " + error_text(exc)[:160]
        print("::warning::تعذّر التحقّق: " + check_text)
        reasons.append("فحص الخادم: " + check_text)

    notes = [f"{p.split('/')[-1]}={'ok' if ok else 'FAIL'}" for p, ok in results] + [check_text]
    ok = all(o for _, o in results) and "=FAIL" not in check_text
    write_status(notes, ok)
    if reasons:
        header = "### تنفيذ ملفات الخادم — أسباب الفشل\n\n"
        write_comment(header + "\n\n".join(f"- `{r}`" for r in reasons))
    print("=== انتهى:", "نجح" if ok else "فيه فشل", "===")
    return 0        # لا نُسقط السير: الحصيلة تكفي، والفشل يُقرأ من حالة الالتزام


if __name__ == "__main__":
    sys.exit(main())
