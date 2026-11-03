#!/usr/bin/env bash
# Restore the Java mod source used as build input.
#
#   tools/fetch_source.sh [target-dir]
#
# Pulls Bedrock-Skins-master.zip straight out of the user's own repository
# through the GitHub blob API (works for files > 1 MB, unlike the contents API)
# and unpacks it.  Needs an authenticated `gh`; no other network access.
set -euo pipefail

REPO="${BS_REPO:-mohamadelfrgena-ai/vae}"
PATH_IN_REPO="${BS_PATH:-Bedrock-Skins-master.zip}"
TARGET="${1:-source}"

mkdir -p "$TARGET"
SHA=$(gh api "repos/$REPO/contents/$PATH_IN_REPO" --jq '.sha')
echo "[fetch] $REPO/$PATH_IN_REPO ($SHA)"
gh api "repos/$REPO/git/blobs/$SHA" --jq '.content' | tr -d '\n' | base64 -d > "$TARGET/Bedrock-Skins-master.zip"
unzip -q -o "$TARGET/Bedrock-Skins-master.zip" -d "$TARGET"
rm -f "$TARGET/Bedrock-Skins-master.zip"
echo "[fetch] ready: $TARGET/Bedrock-Skins-master"
