#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **ملف التحقق الشامل `supabase/99_VERIFY_ALL.sql`**.

الملف 99 هو ما يلصقه المالك في Supabase ليتأكّد أن **كل** ملفات SQL نُفِّذت.
هذا الفاحص يحرسه من الداخل:

  ١) موجود ويحوي بنداً لكل ملف ترحيل (01 → 31)؛
  ٢) **لا يكتب شيئاً**: لا insert/update/delete/drop/alter/truncate — قراءة فقط
     (الاستثناء الوحيد: إنشاء دالة التشخيص `public.diag_text`)؛
  ٣) كل جدول/دالة/سياسة/زناد/تسلسل/مخزن **يذكره** موجود فعلاً في ملفات
     الترحيل ⇒ لا أخطاء إملائية في الأسماء (وهو أكثر ما يُفسد فاحصاً كهذا)؛
  ٤) عدد البنود ومجاميع البنود مذكورة (المجموع في آخر صف)؛
  ٥) صيغة SQL سليمة (عبر `pglast` إن كان مثبّتاً — وإلا يُتخطّى بهدوء).

الاستخدام:  python3 tools/check_verify_sql.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPABASE = ROOT / "supabase"
VERIFY = SUPABASE / "99_VERIFY_ALL.sql"
ALL_IN_ONE = SUPABASE / "00_ALL_IN_ONE.sql"

# ملفات الترحيل التي يجب أن يغطّيها الفاحص (تُستخرج آلياً)
def migration_files():
    out = []
    for p in sorted(SUPABASE.glob("*.sql")):
        m = re.match(r"^(\d\d)_(.+)\.sql$", p.name)
        if m and m.group(1) != "00":
            out.append(p.name[:-4])       # بلا .sql
    return out


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


# ما تُنشئه ملفات الترحيل فعلاً (المصدر المرجعي للمقارنة)
def catalog():
    tables, funcs, policies, triggers, indexes, sequences, buckets = (set(), set(), set(),
                                                                     set(), set(), set(), set())
    # كائنات تُسقطها الترحيلات عمداً (مثل 39: إرجاع الإشعارات كما كانت) —
    # يجوز أن يذكرها الفاحص 99 للتأكد من غيابها.
    dropped_triggers = set()
    for p in sorted(SUPABASE.glob("*.sql")):
        if p.name in ("00_ALL_IN_ONE.sql", "99_VERIFY_ALL.sql"):
            continue
        s = read(p)
        tables |= set(re.findall(r"create table if not exists public\.([a-z_]+)", s))
        funcs |= set(re.findall(r"create (?:or replace )?function public\.([a-z_]+)\s*\(", s))
        policies |= set(re.findall(r"create policy\s+\"?([A-Za-z_]+)\"?\s+on\s+"
                                   r"(?:public\.|storage\.)?([a-z_]+)", s))
        triggers |= set(re.findall(r"create trigger\s+([a-z_]+)", s))
        dropped_triggers |= set(re.findall(r"drop trigger if exists\s+([a-z_]+)", s))
        indexes |= set(re.findall(r"create (?:unique )?index(?: if not exists)?\s+([a-z_]+)", s))
        sequences |= set(re.findall(r"sequence\s+public\.([a-z_]+)", s))
        buckets |= set(re.findall(r"\(\s*'([a-z-]+)',\s*'[a-z-]+',\s*(?:true|false),", s))
    return dict(tables=tables, funcs=funcs, policies=policies, triggers=triggers,
                dropped_triggers=dropped_triggers,
                indexes=indexes, sequences=sequences, buckets=buckets)


def statements(sql: str):
    """يقسّم SQL إلى عبارات (يحترم $tag$ والنصوص والتعليقات)."""
    out, buf, i, n = [], [], 0, len(sql)
    while i < n:
        c = sql[i]
        if sql.startswith("--", i):
            j = sql.find("\n", i)
            i = n if j < 0 else j
            continue
        if sql.startswith("/*", i):
            j = sql.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if c == "'":
            i += 1
            while i < n and sql[i] != "'":
                i += 2 if sql[i] == "\\" else 1
            i += 1
            buf.append("''")
            continue
        if c == "$":
            m = re.match(r"\$[a-z_]*\$", sql[i:])
            if m:
                tag = m.group(0)
                end = sql.find(tag, i + len(tag))
                i = n if end < 0 else end + len(tag)
                buf.append("$")
                continue
        if c == ";":
            out.append("".join(buf).strip())
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    if "".join(buf).strip():
        out.append("".join(buf).strip())
    return out


def comparisons(sql: str):
    """كل مقارنات «عمود = 'قيمة'» **خارج النصوص** (فتُهمَل نصوص الواجهة العربية)."""
    out = []
    i, n, pat = 0, len(sql), re.compile(r"\b([a-z_]+)\s*=\s*'([^']+)'")
    while i < n:
        if sql[i] == "'":                      # قفزة فوق نصّ واحد
            i += 1
            while i < n and sql[i] != "'":
                i += 2 if sql[i] == "\\" else 1
            i += 1
            continue
        m = pat.match(sql, i)
        if m:
            out.append((m.group(1), m.group(2)))
            i = m.end()
            continue
        i += 1
    return out


WRITE_RE = re.compile(r"\b(insert\s+into|update\s+\w|delete\s+from|drop\s+|alter\s+|truncate|"
                      r"grant\s+|revoke\s+)", re.I)


def main() -> int:
    rows = []
    if not VERIFY.exists():
        print("✗ الملف مفقود:", VERIFY)
        return 1
    sql = read(VERIFY)
    cat = catalog()

    # ١) بند لكل ملف ترحيل
    missing_files = [f for f in migration_files() if f not in sql]
    rows.append((f"يذكر كل ملفات الترحيل ({len(migration_files())} ملفاً)",
                 not missing_files, " · ".join(missing_files)))

    # ٢) قراءة فقط
    stmts = statements(sql)
    offenders = []
    for st in stmts:
        head = st[:60].lower()
        if head.startswith("create or replace function public.diag_text"):
            continue                                  # دالة التشخيص الوحيدة
        if head.startswith("comment on function") or head.startswith("do $revoke$"):
            continue
        if WRITE_RE.search(st):
            offenders.append(st[:60].replace("\n", " "))
    rows.append(("قراءة فقط (لا يكتب في القاعدة)", not offenders, " · ".join(offenders)))

    # ٣) الأسماء المذكورة موجودة فعلاً في الترحيلات
    checks = [
        ("الجداول", cat["tables"], r"to_regclass\('public\.'\s*\|\|\s*t\)", "tables"),
        ("الدوال", cat["funcs"], None, "funcs"),
        ("المخازن", cat["buckets"], None, "buckets"),
    ]
    # جداول لم تُذكر في فحص unnest (تُستعمل داخل استعلامات diag_text)
    used_tables = set(re.findall(r"from public\.([a-z_]+)", sql))
    unknown_tables = sorted(t for t in used_tables
                            if t not in cat["tables"]
                            and t not in ("profiles", "site_assets", "chat_messages",
                                          "chat_channels", "subscription_plans"))
    rows.append(("لا جدول مذكوراً بغير اسمه الصحيح", not unknown_tables,
                 " · ".join(unknown_tables)))

    # أسماء دوال داخل الاستعلامات
    used_funcs = set(re.findall(r"public\.([a-z_]+)\s*\(", sql))
    unknown_funcs = sorted(f for f in used_funcs
                           if f not in cat["funcs"] and f != "diag_text")
    rows.append(("لا دالة مذكورة بغير اسمها الصحيح", not unknown_funcs,
                 " · ".join(unknown_funcs)))

    # أعمدة مفحوصة عبر information_schema
    col_pairs = re.findall(r"table_name = '([a-z_]+)'[^)]*?column_name in \(([^)]*)\)", sql)
    bad_cols = []
    schema_cols = read(SUPABASE / "01_schema.sql") + "".join(
        read(p) for p in sorted(SUPABASE.glob("*.sql")))
    for table, cols in col_pairs:
        for col in re.findall(r"'([a-z_]+)'", cols):
            if not re.search(r"\b" + col + r"\b", schema_cols):
                bad_cols.append(f"{table}.{col}")
    rows.append(("لا عمود مذكوراً بغير اسمه الصحيح", not bad_cols, " · ".join(bad_cols)))

    # السياسات بنمط «سياسة:جدول»
    pairs = set(re.findall(r"'([a-z_]+):([a-z_]+)'", sql))
    bad_pol = [f"{p}:{t}" for p, t in pairs
               if (p, t) not in cat["policies"] and (p, t) not in
               {(a, b) for a, b in cat["policies"]}]
    rows.append(("لا سياسة مذكورة بغير اسمها الصحيح", not bad_pol, " · ".join(bad_pol)))

    # الأزرار (triggers) والتسلسلات والفهارس
    bad_trig = [t for t in re.findall(r"tgname = '([a-z_]+)'", sql)
                if t not in cat["triggers"] and t not in cat["dropped_triggers"]]
    rows.append(("لا زناد مذكوراً بغير اسمه الصحيح", not bad_trig, " · ".join(bad_trig)))
    bad_seq = [t for t in re.findall(r"sequencename = '([a-z_]+)'", sql)
               if t not in cat["sequences"] and t not in ("site_assets_id_seq",)]
    rows.append(("لا تسلسل مذكوراً بغير اسمه الصحيح", not bad_seq, " · ".join(bad_seq)))
    bad_idx = [t for t in re.findall(r"indexname in \(([^)]*)\)", sql)[0:1]
               for t in re.findall(r"'([a-z_]+)'", t) if t not in cat["indexes"]]
    rows.append(("لا فهرس مذكوراً بغير اسمه الصحيح", not bad_idx, " · ".join(bad_idx)))

    # ٣أ) الأعمدة: كل (جدول، عمود) يفحصه الفاحص يجب أن يكون عموداً حقيقياً في
    #     نفس الجدول — وهذا ما لم يمسكه الفحص السابق (فحص `cover_url` وقد كان
    #     الصحيح `banner_url` ⇒ بند يفشل دائماً).
    tables_cols = {}
    for p3 in sorted(SUPABASE.glob("*.sql")):
        if p3.name in ("00_ALL_IN_ONE.sql", "99_VERIFY_ALL.sql"):
            continue
        text = read(p3)
        for tname, body in re.findall(
                r"create table (?:if not exists )?public\.([a-z_]+)\s*\((.*?)\n\);", text, re.S):
            cols = set()
            for line in body.split("\n"):
                m = re.match(r"\s*([a-z_][a-z0-9_]*)\s+(?:uuid|text|int|integer|bigint|boolean|"
                             r"timestamptz|timestamp|citext|numeric|jsonb|json|date|smallint|real|"
                             r"serial|bigserial|inet|bytea|interval)\b", line, re.I)
                if m and m.group(1).lower() not in ("primary", "unique", "check", "constraint",
                                                    "foreign", "references", "not", "default"):
                    cols.add(m.group(1).lower())
            tables_cols.setdefault(tname, set()).update(cols)
        for tname, col in re.findall(
                r"alter table public\.([a-z_]+)\s+add column (?:if not exists )?([a-z_]+)", text, re.I):
            tables_cols.setdefault(tname, set()).add(col.lower())

    bad_pairs = []
    for tname, cols in re.findall(
            r"table_name = '([a-z_]+)'[\s\S]{0,120}?column_name in \(([^)]*)\)", sql):
        for col in re.findall(r"'([a-z_]+)'", cols):
            known = tables_cols.get(tname)
            if known is not None and col not in known:
                bad_pairs.append(f"{tname}.{col}")
    rows.append(("كل عمود مذكور موجود في جدوله فعلاً", not bad_pairs,
                 " · ".join(sorted(set(bad_pairs))[:6])))

    # ٣ب) القيم الحرفية: كل قيمة يقارنها الفاحص بعمود يجب أن تكون قيمة حقيقية
    #     في ملفات الترحيل — هكذا لا نطالب بقيمة لا وجود لها (مثال وقع فعلاً:
    #     post_permission = 'admin' والصحيح 'admin_only').
    migrations_text = "".join(read(p2) for p2 in sorted(SUPABASE.glob("*.sql"))
                              if p2.name not in ("00_ALL_IN_ONE.sql", "99_VERIFY_ALL.sql"))
    CATALOG_KEYS = {"schemaname", "table_schema", "table_name", "column_name", "policyname",
                    "tgname", "indexname", "sequencename", "pubname", "relname", "proname",
                    "column_default"}
    bad_values = []
    for key, val in comparisons(sql):
        if key in CATALOG_KEYS or val in ("public", "storage", "true", "false"):
            continue
        if "'" + val + "'" not in migrations_text:
            bad_values.append(f"{key} = '{val}'")
    bad_values = sorted(set(bad_values))
    rows.append(("كل قيمة حرفية مذكورة موجودة في الترحيلات", not bad_values,
                 " · ".join(bad_values[:6])))

    # ٣ث) أقسام الأصول: الأعداد المتوقّعة في الفاحص يجب أن تُطابق أعداد ملفات
    #     البذرة (15 · 21) بالحرف — لا رقم مخترع ولا قسم منسي.
    def seed_collections():
        counts = {}
        for fname in ("15_assets_v2.sql", "21_banners.sql"):
            for line in read(SUPABASE / fname).split("\n"):
                t = line.strip()
                if not t.startswith("('"):
                    continue
                # قسّم الحقول مع مراعاة النصوص
                body = t[t.find("(") + 1: t.rfind(")")]
                fields, buf, q, i = [], [], False, 0
                while i < len(body):
                    ch = body[i]
                    if ch == "'":
                        if q and i + 1 < len(body) and body[i + 1] == "'":
                            i += 2
                            continue
                        q = not q
                        i += 1
                        continue
                    if ch == "," and not q:
                        fields.append("".join(buf).strip())
                        buf = []
                        i += 1
                        continue
                    buf.append(ch)
                    i += 1
                fields.append("".join(buf).strip())
                if len(fields) < 9 or fields[0] not in ("avatar", "banner", "sticker", "item"):
                    continue
                name = fields[6].strip("'")
                counts[name] = counts.get(name, 0) + 1
        return counts

    seed = seed_collections()
    # **كل** ظهور للقسم يجب أن يطابق البذرة (القائمة تُكتب مرّتين: في الفحص وفي
    # التفاصيل — فأي خطأ في أحدهما يجب أن يُمسَك).
    occurrences = re.findall(r"\('([^']+)', (\d+)\)", sql)
    wrong_counts = [f"{c}: الفاحص {n} · البذرة {seed.get(c)}"
                    for c, n in occurrences if seed.get(c) != int(n)]
    missing_colls = [c for c in seed if c not in {c for c, _ in occurrences}]
    rows.append((f"أعداد الأقسام في الفاحص = ملفات البذرة ({len(seed)} قسماً)",
                 not wrong_counts and not missing_colls,
                 " · ".join(wrong_counts[:5] + [f"منسي: {c}" for c in missing_colls[:3]])))

    # ٣ج) كل مقارنة نتيجة في الفاحص يجب أن تكون 'true'/'false' (نتائج الفحص
    #     مصبوبة نصّاً) — خطأ من نوع «= '1'» يجعل البند يفشل دائماً بلا سبب.
    #     كل نداء diag_text ينتهي بـ "$q$)" ثم تأتي المقارنة بعده مباشرة
    expected = set(re.findall(r"\$q\$\)\s*=\s*'([^']*)'", sql))
    weird = sorted(v for v in expected if v not in ("true", "false"))
    rows.append(("كل مقارنات النتيجة 'true'/'false' فقط", not weird, " · ".join(weird[:6])))

    # ٣د) لا معرّف عربي/مسافة غير مُقتبَسة داخل استعلامات الفحص
    bad_ident = []
    for inner in re.findall(r"\$q\$([\s\S]*?)\$q\$", sql):
        i, n = 0, len(inner)
        while i < n:
            if inner[i] == "'":
                i += 1
                while i < n and inner[i] != "'":
                    i += 1
                i += 1
                continue
            if "\u0600" <= inner[i] <= "\u06ff":
                bad_ident.append(inner[max(0, i - 25):i + 12].replace("\n", " "))
                break
            i += 1
    rows.append(("لا نصّ عربي غير مُقتبَس داخل الاستعلامات", not bad_ident,
                 " · ".join(bad_ident[:3])))

    # ٣هـ) التغطية: لا شيء في الترحيلات بلا بند يفحصه (طلب المالك: «ولا يوجد
    #      شيء ناقص») — جداول · دوال · سياسات · مخازن · أزرار · تسلسلات.
    def mentioned(names, kind):
        """الأسماء التي يذكرها الفاحص (بأي صيغة: مصفوفة أو قائمة مقارنة)."""
        return sorted(n for n in names if f"'{n}'" in sql or f"{n}" in sql)

    gaps = []
    for kind, names in (("جدول", cat["tables"]), ("دالة", cat["funcs"])):
        for n in sorted(names):
            if n not in sql:
                gaps.append(f"{kind}: {n}")
    for pol, tbl in sorted(cat["policies"]):
        # السياسات تُذكر في الفاحص إمّا مزدوجة «سياسة:جدول» أو باسمها في قائمة
        if f"{pol}:{tbl}" not in sql and f"'{pol}'" not in sql:
            gaps.append(f"سياسة: {pol} ({tbl})")
    for n in sorted(cat["buckets"]):
        if f"'{n}'" not in sql:
            gaps.append(f"مخزن: {n}")
    for n in sorted(cat["triggers"]):
        if n not in sql:
            gaps.append(f"زناد: {n}")
    for n in sorted(cat["sequences"]):
        if n not in sql:
            gaps.append(f"تسلسل: {n}")
    rows.append((f"لا نقص في التغطية (كل ما في الترحيلات مفحوص)", not gaps,
                 " · ".join(gaps[:8])))

    # ٤) المجموع في آخر صف
    rows.append(("يحوي صف «المجموع» في النهاية",
                 "المجموع" in sql and "ok is not true" in sql, ""))

    # ٥) صيغة SQL
    try:
        import pglast
        try:
            pglast.parse_sql(sql)
            rows.append(("صيغة PostgreSQL سليمة (pglast)", True, ""))
        except Exception as e:                       # noqa: BLE001
            rows.append(("صيغة PostgreSQL سليمة (pglast)", False, str(e)[:90]))
    except ImportError:
        rows.append(("صيغة PostgreSQL (pglast غير مثبّت — تُتخطّى)", True,
                     "pip install pglast للتحقّق الكامل"))

    # ٦) ليس داخل الملف الجامع (فاحص لا شِعار)
    rows.append(("غير مُدرَج في 00_ALL_IN_ONE (فاحص لا بناء)",
                 "99_VERIFY_ALL" not in read(ALL_IN_ONE) and "diag_text" not in read(
                     ROOT / "tools/build_all_in_one_sql.py"), ""))

    print("الفحص                                             | النتيجة")
    print("-" * 62)
    for name, ok, extra in rows:
        line = f"{name:<50} | {'✅' if ok else '❌'}"
        print(line)
        if not ok and extra:
            print(f"   ↳ {extra}")

    failed = [n for n, ok, _ in rows if not ok]
    print()
    if failed:
        print(f"✗ عدد المشاكل: {len(failed)}")
        return 1
    n_checks = len(re.findall(r"^\s*select \d+, '", sql, re.M))
    print(f"🟢 99_VERIFY_ALL.sql سليم: {n_checks} بنداً · قراءة فقط · "
          f"كل الأسماء مطابقة لملفات الترحيل.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
