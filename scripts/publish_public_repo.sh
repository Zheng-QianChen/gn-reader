#!/usr/bin/env bash
# 在本机执行：把 gn-reader 拆成独立公开仓库并推送
# 用法: ./scripts/publish_public_repo.sh [目标目录] [owner/repo]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$HOME/src/gn-reader}"
REPO="${2:-Zheng-QianChen/gn-reader}"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a "$ROOT"/. "$DEST"/
rm -rf "$DEST/build" "$DEST/app/build" "$DEST/.gradle" "$DEST/local.properties" "$DEST/.git"
cd "$DEST"
git init -b main
git add -A
git commit -m "Initial public release of GN Reader"
gh repo create "$REPO" --public --source=. --remote=origin --push \
  --description "GN Reader — Android ebook reader (.gnbook, hardware keys, TTS)"
echo "Done: https://github.com/$REPO"
