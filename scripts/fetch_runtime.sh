#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
OUT="$ROOT/app/src/main/assets/runtime"
mkdir -p "$OUT"

for cmd in curl sha256sum tar unzip zip; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "::error::Missing required tool: $cmd"; exit 1; }
done

fetch() { # <name> <url> <sha256>
  local name="$1" url="$2" sha="$3"
  [ -n "$url" ] && [ -n "$sha" ] || { echo "::error::Missing URL or SHA256 for $name"; exit 1; }

  local dl="/tmp/${name}.dl"
  local xdir="/tmp/${name}.x"
  local clean_url="${url%%\?*}"

  rm -f "$dl"
  curl -fsSL --retry 3 --retry-delay 2 -o "$dl" "$url"
  echo "$sha  $dl" | sha256sum -c -

  rm -rf "$xdir"
  mkdir -p "$xdir"

  case "$clean_url" in
    *.tar.xz|*.txz) tar -xJf "$dl" -C "$xdir" ;;
    *.tar.gz|*.tgz) tar -xzf "$dl" -C "$xdir" ;;
    *.zip)          unzip -q "$dl" -d "$xdir" ;;
    *) echo "::error::Unsupported archive format in URL: $url"; exit 1 ;;
  esac

  local count
  count=$(find "$xdir" -mindepth 1 -maxdepth 1 | wc -l)
  local base="$xdir"
  if [ "$count" -eq 1 ]; then
    local only
    only=$(find "$xdir" -mindepth 1 -maxdepth 1)
    if [ -d "$only" ]; then
      base="$only"
    fi
  fi

  rm -f "$OUT/${name}.zip"
  # بدون -y لتحويل الروابط الرمزية إلى ملفات فعلية
  (cd "$base" && zip -qr "$OUT/${name}.zip" .)
  rm -rf "$dl" "$xdir"
  echo "Done: $OUT/${name}.zip"
}

fetch jre8-arm64     "${JRE_URL:-}"     "${JRE_SHA256:-}"
fetch natives-arm64  "${NATIVES_URL:-}" "${NATIVES_SHA256:-}"
fetch shims          "${SHIMS_URL:-}"   "${SHIMS_SHA256:-}"
