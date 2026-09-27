#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sha=a97cce86a8addeb9f40cba7a261c94b1f0c576cb
if [ ! -d "$root/vendor/llama.cpp/.git" ]; then
  mkdir -p "$root/vendor"
  git clone --filter=blob:none https://github.com/ggml-org/llama.cpp.git "$root/vendor/llama.cpp"
fi
git -C "$root/vendor/llama.cpp" fetch --depth 1 origin "$sha"
git -C "$root/vendor/llama.cpp" checkout --detach "$sha"
