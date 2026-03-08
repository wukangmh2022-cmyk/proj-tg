#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 /path/to/Nekogram" >&2
  exit 1
fi

target_repo="$1"
script_dir="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
expected_head="40caf3b2"

if ! git -C "$target_repo" rev-parse --git-dir >/dev/null 2>&1; then
  echo "target is not a git repository: $target_repo" >&2
  exit 1
fi

patch_dir="$project_root/patches/nekogram"
if [ ! -d "$patch_dir" ]; then
  echo "patch directory not found: $patch_dir" >&2
  exit 1
fi

current_head="$(git -C "$target_repo" rev-parse HEAD)"
if [[ "$current_head" != "$expected_head"* ]]; then
  echo "warning: patch was validated on Nekogram $expected_head, current HEAD is ${current_head:0:10}" >&2
fi

applied=0
for patch_file in "$patch_dir"/*.patch; do
  [ -e "$patch_file" ] || continue
  git -C "$target_repo" apply --check "$patch_file"
  git -C "$target_repo" apply "$patch_file"
  echo "applied: $patch_file"
  applied=1
done

if [ "$applied" -eq 0 ]; then
  echo "no patch files found in $patch_dir" >&2
  exit 1
fi
