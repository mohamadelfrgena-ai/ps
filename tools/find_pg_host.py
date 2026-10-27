#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يكتشف مضيف Postgres الصحيح لمشروع Supabase ويختبر كلمة السر.

الاتصال المباشر db.<ref>.supabase.co يكون IPv6-only أحياناً (منفذ GitHub بلا
IPv6)، وعنقود الـ Shared Pooler (aws-N) لا يمكن استنتاجه من المنطقة.

الاستعمال:
    python3 find_pg_host.py <project-ref> <password> [region-hint ...]
المخرجات: التشخيصات إلى stderr (::notice::) وسطر النتيجة إلى stdout:
    OK <host> <port> <user>
"""
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

CLUSTERS = ["aws-0", "aws-1", "aws-2", "aws-3"]
REGIONS_ALL = [
    "eu-central-1", "us-east-1", "us-east-2", "us-west-1", "us-west-2",
    "eu-west-1", "eu-west-2", "eu-west-3", "eu-north-1",
    "ap-southeast-1", "ap-southeast-2", "ap-northeast-1", "ap-northeast-2",
    "ap-south-1", "sa-east-1", "ca-central-1",
]


def eprint(*a):
    print(*a, file=sys.stderr)


def try_one(args):
    ref, pw, host, port, user = args
    try:
        out = subprocess.run(
            ["docker", "run", "--rm", "-e", f"PGPASSWORD={pw}", "postgres:17",
             "psql", "-h", host, "-p", str(port), "-U", user, "-d", "postgres",
             "-c", "select 1"],
            capture_output=True, text=True, timeout=45,
        )
        combined = (out.stdout or "") + (out.stderr or "")
        if out.returncode == 0 and "1 row" in combined:
            return (host, port, user, None)
        err = [ln for ln in combined.splitlines() if ln.strip()]
        return (host, port, user, err[-1] if err else f"exit {out.returncode}")
    except Exception as e:  # noqa: BLE001
        return (host, port, user, str(e)[:140])


def main():
    if len(sys.argv) < 3:
        eprint("usage: find_pg_host.py <ref> <password> [regions...]")
        sys.exit(2)
    ref, pw = sys.argv[1], sys.argv[2]
    regions = sys.argv[3:] or REGIONS_ALL

    targets = []
    # 1) الاتصال المباشر (إن وُجد IPv4/IPv6 على العدّاد)
    targets.append((ref, pw, f"db.{ref}.supabase.co", 5432, "postgres"))
    # 2) صيغ الـ Pooler المشتركة: aws-N-<region>
    for cl in CLUSTERS:
        for rg in regions:
            for port in (5432, 6543):
                targets.append((ref, pw, f"{cl}-{rg}.pooler.supabase.com", port, f"postgres.{ref}"))
    # 3) صيغ بديلة (مشاريع حديثة)
    for host in (f"{ref}.pooler.supabase.com", f"db.{ref}.pooler.supabase.com"):
        for port in (5432, 6543):
            targets.append((ref, pw, host, port, f"postgres.{ref}"))

    eprint(f"::notice title=اكتشاف الاتصال::جرب {len(targets)} مضيفاً — مناطق: {len(regions)}")
    errors = {}
    with ThreadPoolExecutor(max_workers=16) as pool:
        for host, port, user, err in pool.map(try_one, targets):
            if err is None:
                eprint(f"::notice title=اتصال ناجح::{user}@{host}:{port}")
                print(f"OK {host} {port} {user}")
                return
            kind = err.split("failed:")[-1].strip()[:90] if "failed:" in err else err[:90]
            errors.setdefault(kind, []).append(f"{host}:{port}")
    eprint("::error title=لا يوجد مضيف صالح::كل المحاولات فشلت")
    for kind, hosts in sorted(errors.items(), key=lambda kv: -len(kv[1])):
        eprint(f"::notice title=سبب ({len(hosts)} مضيفاً)::{kind}")
    print("FAIL")
    sys.exit(1)


if __name__ == "__main__":
    main()
