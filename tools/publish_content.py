#!/usr/bin/env python3
"""AddonStation: Supabase <-> GitHub Releases publisher.

One command that connects the whole store pipeline:

  1. Packs an addon/world archive (fixing scattered files + pack0/pack1
     collisions -> unique <slug>_<uuid8> folders from manifest.json).
  2. Stages a content-staging/<slug>.publish.json manifest in the repo.
  3. Triggers the 'Publish content release' workflow, which attaches the
     generated .raw/.zip assets to a GitHub Release (supabase content_items
     points at those release URLs via ps4_raw_url).
  4. Emits the SQL INSERT for content_items (or the path of an existing
     .supabase.json from tools/pack_addon.py).

Usage (normal — release assets are attached by GitHub Actions):
  python3 tools/publish_content.py MyAddon.zip \\
      --name "MyAddon" --version 2.0 --content-type addon

  python3 tools/publish_content.py MyAddon.zip --name "MyAddon" --trigger

Usage (direct upload with YOUR gh auth — e.g. publishing to the PUBLIC
repo mohamadelfrgena-ai/vae so consoles download without any token):
  python3 tools/publish_content.py MyAddon.zip --name "MyAddon" \\
      --repo mohamadelfrgena-ai/vae --direct
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile

DEFAULT_REPO = "mohamadelfrgena-ai/ps"
PUBLIC_CDN_REPO = "mohamadelfrgena-ai/vae"


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess:
    print("+", " ".join(cmd))
    return subprocess.run(cmd, check=check)


def slugify(text: str) -> str:
    import re
    slug = re.sub(r"[^A-Za-z0-9]+", "-", (text or "").strip()).strip("-").lower()
    return slug or "content"


def main() -> None:
    ap = argparse.ArgumentParser(description="Publish addon content to GitHub Releases + Supabase.")
    ap.add_argument("input", help="addon .zip/.mcpack/.mcaddon or folder")
    ap.add_argument("--name", required=True, help="display name")
    ap.add_argument("--version", default="1.0.0")
    ap.add_argument("--content-type", default="addon", choices=["addon", "world", "resource_pack", "map", "skin"])
    ap.add_argument("--cover", default="", help="cover image path (jpg/png)")
    ap.add_argument("--notes", default="", help="release notes")
    ap.add_argument("--repo", default=DEFAULT_REPO,
                    help=f"GitHub repo for the release (default: {DEFAULT_REPO}; "
                         f"public CDN option: {PUBLIC_CDN_REPO})")
    ap.add_argument("--tag", default="", help="release tag (default: content-<slug>-<version>)")
    ap.add_argument("--direct", action="store_true",
                    help="pack AND upload the assets right now with your gh auth")
    ap.add_argument("--trigger", action="store_true",
                    help="commit nothing, but trigger the publish workflow (needs pushed staging)")
    ap.add_argument("--staging-dir", default="content-staging")
    ap.add_argument("--out", default="", help="pack output dir (default: temp)")
    args = ap.parse_args()

    slug = slugify(args.name)
    tag = args.tag or f"content-{slug}-{args.version}"

    with tempfile.TemporaryDirectory() as tmp:
        out_dir = args.out or os.path.join(tmp, "dist")

        # 1) Pack (scattered files + pack0/pack1 -> <slug>_<uuid8>)
        run([
            "python3", "tools/pack_addon.py", args.input,
            "--name", args.name, "--version", args.version,
            "--repo", args.repo, "--tag", tag, "--out", out_dir,
        ])

        supabase_path = os.path.join(out_dir, f"{slug}.supabase.json")

        if args.direct:
            # 2a) Direct upload with the caller's gh auth (works for PUBLIC repos)
            assets = [os.path.join(out_dir, f) for f in sorted(os.listdir(out_dir))
                      if f.endswith((".raw", ".zip", ".json", ".jpg", ".png"))]
            if args.cover and os.path.isfile(args.cover):
                assets.append(args.cover)
            run(["gh", "release", "create", tag, "--repo", args.repo,
                 "--title", f"AddonStation content: {args.name} {args.version}",
                 "--notes", args.notes or f"AddonStation content package: {args.name} {args.version}"],
                check=False)
            run(["gh", "release", "upload", tag, "--repo", args.repo, "--clobber"] + assets)
        else:
            # 2b) Stage the manifest so CI attaches the assets (private-repo safe)
            os.makedirs(args.staging_dir, exist_ok=True)
            manifest = {
                "tag": tag,
                "title": f"AddonStation content: {args.name} {args.version}",
                "source": args.input if os.path.isfile(args.input) else args.input,
                "name": args.name,
                "version": args.version,
                "content_type": args.content_type,
                "cover": args.cover,
                "notes": args.notes or
                         f"{args.name} {args.version} — .raw installs to <target>/<slug>_<uuid8> "
                         f"(manifest header.uuid), handles scattered files and pack0/pack1 collisions.",
            }
            manifest_path = os.path.join(args.staging_dir, f"{slug}.publish.json")
            with open(manifest_path, "w", encoding="utf-8") as f:
                json.dump(manifest, f, ensure_ascii=False, indent=2)
            print(f"staged: {manifest_path}")
            print("push this file to the branch and the 'Publish content release' workflow")
            print("will attach .raw/.zip to the GitHub Release automatically.")

        if args.trigger:
            run(["gh", "workflow", "run", "publish-content-release.yml",
                 "-f", f"tag={tag}"])

        if os.path.isfile(supabase_path):
            print(f"\nSupabase row (from {supabase_path}):")
            with open(supabase_path, "r", encoding="utf-8") as f:
                print(f.read())


if __name__ == "__main__":
    main()
