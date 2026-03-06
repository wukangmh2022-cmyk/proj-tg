#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 /path/to/Nekogram" >&2
  exit 1
fi

target_repo="$1"
script_dir="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
patch_file="$project_root/patches/nekogram/0001-glocalvision-ai-entry.patch"
expected_head="40caf3b2"

if ! git -C "$target_repo" rev-parse --git-dir >/dev/null 2>&1; then
  echo "target is not a git repository: $target_repo" >&2
  exit 1
fi

if [ ! -f "$patch_file" ]; then
  echo "patch file not found: $patch_file" >&2
  exit 1
fi

current_head="$(git -C "$target_repo" rev-parse --short HEAD)"
if [ "$current_head" != "$expected_head" ]; then
  echo "warning: patch was validated on Nekogram $expected_head, current HEAD is $current_head" >&2
fi

git -C "$target_repo" apply --check "$patch_file"
git -C "$target_repo" apply "$patch_file"

echo "applied: $patch_file"
