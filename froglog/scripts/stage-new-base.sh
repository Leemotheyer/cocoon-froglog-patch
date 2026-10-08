#!/usr/bin/env bash
# Download or copy a newer Cocoon APK, disassemble it beside the current cache,
# and write an upgrade report against froglog/base/hooks.json.
#
#   ./froglog/scripts/stage-new-base.sh \
#     https://github.com/inssekt/CocoonFE/releases/download/beta-3.07/cocoon-307.apk \
#     3.07
#
#   ./froglog/scripts/stage-new-base.sh /path/to/cocoon.apk 3.07
#
# Does not replace work/cocoon-306.apk or work/smali-out. The current Froglog
# build keeps using those until you point build.sh at the new decode on purpose.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${1:-}"
LABEL="${2:-}"
if [[ -z "$SRC" || -z "$LABEL" ]]; then
  echo "usage: $0 <apk-or-url> <label>" >&2
  echo "  label becomes work/bases/<label>/ (example: 3.07)" >&2
  exit 1
fi
if [[ ! "$LABEL" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "label must be a simple directory name" >&2
  exit 1
fi

DEST="$ROOT/work/bases/$LABEL"
mkdir -p "$DEST"
APK="$DEST/cocoon.apk"

if [[ "$SRC" == http://* || "$SRC" == https://* ]]; then
  echo "downloading $SRC"
  curl -fsSL -o "$APK.part" "$SRC"
  mv "$APK.part" "$APK"
else
  if [[ ! -f "$SRC" ]]; then
    echo "not a file: $SRC" >&2
    exit 1
  fi
  cp -f "$SRC" "$APK"
fi

if command -v sha256sum >/dev/null 2>&1; then
  SHA="$(sha256sum "$APK" | awk '{print $1}')"
else
  SHA="$(shasum -a 256 "$APK" | awk '{print $1}')"
fi
echo "sha256 $SHA"
printf '%s\n' "$SHA" > "$DEST/sha256"

export SRC_APK="$APK"
export DECODE="$DEST/decoded"
export SMALI_OUT="$DEST/smali"
export WORK="$DEST"
export COCOON_APK_SHA256="$SHA"
export COCOON_APK_URL="${COCOON_APK_URL:-$SRC}"

"$ROOT/froglog/scripts/prepare-cocoon-reference.sh"

python3 "$ROOT/froglog/scripts/scan-base-upgrade.py" \
  --smali "$SMALI_OUT" \
  --decode "$DECODE" \
  --out "$DEST/UPGRADE_REPORT.md"

echo "staged $DEST"
echo "report $DEST/UPGRADE_REPORT.md"
