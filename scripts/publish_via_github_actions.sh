#!/usr/bin/env bash
# 仅用 git/gh：推源码到公开库，由 GitHub Actions 编译并打 Release（不在本机/WSL 编译）
# 用法:
#   ./scripts/publish_via_github_actions.sh [源码目录] [owner/repo] [tag]
set -euo pipefail

SRC="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
REPO="${2:-Zheng-QianChen/gn-reader}"
TAG="${3:-v0.1.30}"
WORKDIR="${TMPDIR:-/tmp}/gn-reader-publish-$$"

echo "==> 源码: $SRC"
echo "==> 目标仓库: $REPO"
echo "==> Release tag: $TAG"

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
cp -a "$SRC"/. "$WORKDIR"/
rm -rf "$WORKDIR/build" "$WORKDIR/app/build" "$WORKDIR/.gradle" \
  "$WORKDIR/local.properties" "$WORKDIR/.git"

cd "$WORKDIR"
git init -b main
git add -A
git commit -m "GN Reader ${TAG}: publish for GitHub Actions build"

if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "==> 仓库已存在，推送 main"
  git remote add origin "https://github.com/${REPO}.git"
  git push -u origin main --force
else
  echo "==> 创建公开仓库并推送"
  gh repo create "$REPO" --public --source=. --remote=origin --push \
    --description "GN Reader — Android ebook reader (.gnbook, hardware keys, TTS)"
fi

echo "==> 打 tag 触发 Actions 编译 + Release"
git tag -f "$TAG"
git push -f origin "refs/tags/${TAG}"

echo "==> 等待 workflow 跑完…"
sleep 5
# 等最近一次 workflow
gh run watch --repo "$REPO" --exit-status || true
gh run list --repo "$REPO" --limit 3

echo
echo "Actions:  https://github.com/${REPO}/actions"
echo "Release:  https://github.com/${REPO}/releases/tag/${TAG}"
echo "（若 Release 尚未出现，打开 Actions 看 build 是否仍在跑）"
