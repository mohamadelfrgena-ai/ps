#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يكتشف مضيف Pooler الصحيح لمشروع Supabase ويختبر كلمة السر.

السبب: الاتصال المباشر db.<ref>.supabase.co يصبح IPv6-only أحياناً (ومنفذ
GitHub بلا IPv6)، وعنقود الـ Shared Pooler (aws-N) لا يمكن استنتاجه من المنطقة
(توثيق Supabase: "aws-0 ليس افتراضاً آمناً").

الاستعمال:
    python3 find_pg_host.py <project-ref> <password> [region-hint ...]
المخرجات (سطر أخير):  OK <host> <port> <user>
عند الفشل:            FAIL مع تفاصيل كـ ::notice:: annotations
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


def try_one(args):
    ref, pw, host, port = args
    user = f"postgres.{ref}"
    try:
        out = subprocess.run(
            ["docker", "run", "--rm", "-e", f"PGPASSWORD={pw}", "postgres:17",
             "psql", "-h", host, "-p", str(port), "-U", user, "-d", "postgres",
             "-c", "select 1"],
            capture_output=True, text=True, timeout=40,
        )
        combined = (out.stdout or "") + (out.stderr or "")
        if out.returncode == 0 and "1 row" in combined:
            return (host, port, user, None)
        err = [ln for ln in combined.splitlines() if ln.strip()]
        return (host, port, user, err[-1] if err else f"exit {out.returncode}")
    except Exception as e:  # noqa: BLE001
        return (host, port, user, str(e)[:120])


def main():
    if len(sys.argv) < 3:
        print("usage: find_pg_host.py <ref> <password> [regions...]", file=sys.stderr)
        sys.exit(2)
    ref, pw = sys.argv[1], sys.argv[2]
    regions = sys.argv[3:] or REGIONS_ALL

    targets = []
    for cl in CLUSTERS:
        for rg in regions:
            for port in (5432, 6543):
                targets.append((ref, pw, f"{cl}-{rg}.pooler.supabase.com", port))

    print(f"::notice title=اكتشاف الاتصال::جرب {len(targets)} مضيفاً ({len(regions)} منطقة × {len(CLUSTERS)} عنقود × منفذان)")
    done = 0
    with ThreadPoolExecutor(max_workers=16) as pool:
        for host, port, user, err in pool.map(try_one, targets):
            done += 1
            if err is None:
                print(f"::notice title=اتصال ناجح::{user}@{host}:{port}")
                print(f"OK {host} {port} {user}")
                return
    print("::error title=لا يوجد مضيف صالح::جرّبت كل التركيبات بلا نجاح — كلمة السر خاطئة؟ (أو المشروع متوقف)")
    print("FAIL")
    sys.exit(1)


if __name__ == "__main__":
    main()
