#!/usr/bin/env bash
# Disassemble one or more classes from a cached dex for quick reading/diffs.
# Example: ./froglog/scripts/disassemble-classes.sh classes4 cf/pi cf/pd
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$ROOT/work}"
TOOLS="${TOOLS:-$ROOT/tools}"
OUT="${OUT:-$WORK/disasm-scratch}"

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <dex-basename> <class> [class...]" >&2
  echo "  dex-basename: classes | classes2 | ... | classes6" >&2
  echo "  class: smali path without .smali, e.g. cf/pi" >&2
  exit 1
fi

DEX_BASE="$1"
shift
DEX="$WORK/cocoon-decoded/${DEX_BASE}.dex"
if [[ ! -f "$DEX" ]]; then
  echo "missing $DEX (run agent-bootstrap.sh first)" >&2
  exit 1
fi

if [[ -f "$TOOLS/smali/baksmali.jar" ]]; then
  BAKSMALI="$TOOLS/smali/dexlib2.jar:$TOOLS/smali/util.jar:$TOOLS/smali/guava.jar:$TOOLS/smali/failureaccess.jar:$TOOLS/smali/jcommander.jar:$TOOLS/smali/baksmali.jar"
else
  echo "missing smali jars under tools/ (run setup-build-tools.sh)" >&2
  exit 1
fi

SMALI_CLASSES=()
for path in "$@"; do
  path="${path%.smali}"
  path="${path//.//}"
  if [[ "$path" != L* ]]; then
    path="L${path};"
  fi
  SMALI_CLASSES+=("$path")
done
CLASSES="$(IFS=,; echo "${SMALI_CLASSES[*]}")"

rm -rf "$OUT"
mkdir -p "$OUT"
java -cp "$BAKSMALI" org.jf.baksmali.Main disassemble \
  --classes "$CLASSES" \
  -o "$OUT" \
  "$DEX"

echo "wrote $OUT"
find "$OUT" -name '*.smali' | sort
