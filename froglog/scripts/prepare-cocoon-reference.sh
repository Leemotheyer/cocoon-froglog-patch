#!/usr/bin/env bash
# Idempotently cache base Cocoon 3.06 for local reference (not committed to git).
# Populates work/cocoon-306.apk, work/cocoon-decoded/, and work/smali-out/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$ROOT/work}"
TOOLS="${TOOLS:-$ROOT/tools}"
APKTOOL="${APKTOOL:-$TOOLS/apktool.jar}"
if [[ -z "${BAKSMALI:-}" ]]; then
  if [[ -f "$TOOLS/smali/dexlib2.jar" ]]; then
    BAKSMALI="$TOOLS/smali/dexlib2.jar:$TOOLS/smali/util.jar:$TOOLS/smali/guava.jar:$TOOLS/smali/failureaccess.jar:$TOOLS/smali/jcommander.jar:$TOOLS/smali/baksmali.jar"
  else
    BAKSMALI="$TOOLS/baksmali.jar"
  fi
fi

COCOON_APK_URL="${COCOON_APK_URL:-https://github.com/inssekt/CocoonFE/releases/download/beta-3.06/cocoon-306.apk}"
COCOON_APK_SHA256="${COCOON_APK_SHA256:-e9df90ad83200f984f085dac51448fffb558b509f7ece50d6889bb5b393cc4d5}"

SRC_APK="${SRC_APK:-$WORK/cocoon-306.apk}"
DECODE="${DECODE:-$WORK/cocoon-decoded}"
SMALI_OUT="${SMALI_OUT:-$WORK/smali-out}"
MARKER="$WORK/cocoon-reference.sha256"
SKIP_SMALI="${SKIP_SMALI:-0}"

need() {
  if [[ ! -e "$1" ]]; then
    echo "prepare-cocoon-reference: missing $1 (build tools live under tools/, also gitignored)" >&2
    exit 1
  fi
}

need "$APKTOOL"
if [[ "$BAKSMALI" == *:* ]]; then
  need "${BAKSMALI%%:*}"
else
  need "$BAKSMALI"
fi

mkdir -p "$WORK"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

verify_apk() {
  local got
  got="$(sha256_file "$SRC_APK")"
  if [[ "$got" != "$COCOON_APK_SHA256" ]]; then
    echo "prepare-cocoon-reference: $SRC_APK sha256 mismatch (got $got)" >&2
    exit 1
  fi
}

if [[ ! -f "$SRC_APK" ]]; then
  echo "downloading $COCOON_APK_URL"
  curl -fsSL -o "$SRC_APK.part" "$COCOON_APK_URL"
  mv "$SRC_APK.part" "$SRC_APK"
fi
verify_apk

if [[ ! -d "$DECODE/res" ]]; then
  echo "apktool decode (resources + dex) -> $DECODE"
  java -jar "$APKTOOL" d -s -f -o "$DECODE" "$SRC_APK"
fi

if [[ "$SKIP_SMALI" == "1" ]]; then
  echo "$COCOON_APK_SHA256" > "$MARKER"
  echo "reference ready (smali skipped): $DECODE"
  exit 0
fi

smali_dir_for_dex() {
  local base="$1"
  if [[ "$base" == "classes" ]]; then
    echo "$SMALI_OUT/smali"
  else
    echo "$SMALI_OUT/smali_${base}"
  fi
}

disassemble_dex() {
  local dex="$1"
  local out="$2"
  if [[ -d "$out" ]] && [[ -n "$(ls -A "$out" 2>/dev/null || true)" ]]; then
    return 0
  fi
  echo "baksmali $dex -> $out"
  rm -rf "$out"
  mkdir -p "$out"
  if [[ "$BAKSMALI" == *:* ]]; then
    java -cp "$BAKSMALI" org.jf.baksmali.Main disassemble -o "$out" "$dex"
  else
    java -jar "$BAKSMALI" d -o "$out" "$dex"
  fi
}

marker_ok=0
if [[ -f "$MARKER" ]] && [[ "$(cat "$MARKER")" == "$COCOON_APK_SHA256" ]]; then
  marker_ok=1
fi

if [[ "$marker_ok" -eq 1 ]] && [[ -d "$SMALI_OUT/smali" ]] && [[ -d "$SMALI_OUT/smali_classes4" ]]; then
  echo "reference cache up to date ($SMALI_OUT)"
  exit 0
fi

mkdir -p "$SMALI_OUT"
shopt -s nullglob
for dex in "$DECODE"/classes*.dex; do
  base="$(basename "$dex" .dex)"
  out="$(smali_dir_for_dex "$base")"
  disassemble_dex "$dex" "$out"
done
shopt -u nullglob

echo "$COCOON_APK_SHA256" > "$MARKER"
echo "reference ready: $DECODE and $SMALI_OUT"
