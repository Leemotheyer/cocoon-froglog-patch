#!/usr/bin/env bash
# Download pinned APK rebuild tools into gitignored tools/ (idempotent).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="${TOOLS:-$ROOT/tools}"
MARKER="$TOOLS/.build-tools-version"
WANT_VERSION="apktool-2.11.1-smali-2.5.2-bt-35.0.0"

APKTOOL_URL="https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar"
BUILD_TOOLS_URL="https://dl.google.com/android/repository/build-tools_r35_linux.zip"
PLATFORM_URL="https://dl.google.com/android/repository/platform-35_r01.zip"

MAVEN="https://repo1.maven.org/maven2"
SMALI_VER="2.5.2"
SMALI_JARS=(
  "org/smali/baksmali/${SMALI_VER}/baksmali-${SMALI_VER}.jar"
  "org/smali/dexlib2/${SMALI_VER}/dexlib2-${SMALI_VER}.jar"
  "org/smali/util/${SMALI_VER}/util-${SMALI_VER}.jar"
  "com/google/guava/guava/31.1-jre/guava-31.1-jre.jar"
  "com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar"
  "com/beust/jcommander/1.82/jcommander-1.82.jar"
)

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "setup-build-tools: missing command $1 (install OpenJDK 11+)" >&2
    exit 1
  fi
}

download() {
  local url="$1"
  local dest="$2"
  if [[ -f "$dest" ]]; then
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  echo "curl $url"
  curl -fsSL -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
}

need_cmd curl
need_cmd java
need_cmd unzip

if [[ -f "$MARKER" ]] && [[ "$(cat "$MARKER")" == "$WANT_VERSION" ]] \
  && [[ -f "$TOOLS/apktool.jar" ]] \
  && [[ -f "$TOOLS/android/build-tools/35.0.0/d8" ]] \
  && [[ -f "$TOOLS/android/platforms/android-35/android.jar" ]] \
  && [[ -f "$TOOLS/smali/baksmali.jar" ]]; then
  echo "build tools up to date ($TOOLS)"
  exit 0
fi

mkdir -p "$TOOLS/smali" "$TOOLS/android/build-tools" "$TOOLS/android/platforms"
download "$APKTOOL_URL" "$TOOLS/apktool.jar"

for path in "${SMALI_JARS[@]}"; do
  name="$(basename "$path")"
  case "$name" in
    baksmali-*) dest="$TOOLS/smali/baksmali.jar" ;;
    dexlib2-*) dest="$TOOLS/smali/dexlib2.jar" ;;
    util-*) dest="$TOOLS/smali/util.jar" ;;
    guava-*) dest="$TOOLS/smali/guava.jar" ;;
    failureaccess-*) dest="$TOOLS/smali/failureaccess.jar" ;;
    jcommander-*) dest="$TOOLS/smali/jcommander.jar" ;;
    *) dest="$TOOLS/smali/$name" ;;
  esac
  download "$MAVEN/$path" "$dest"
done

BT_ZIP="$TOOLS/.cache/build-tools_r35_linux.zip"
PL_ZIP="$TOOLS/.cache/platform-35_r01.zip"
mkdir -p "$TOOLS/.cache"
download "$BUILD_TOOLS_URL" "$BT_ZIP"
download "$PLATFORM_URL" "$PL_ZIP"

rm -rf "$TOOLS/.cache/bt-unpack" "$TOOLS/.cache/pl-unpack"
mkdir -p "$TOOLS/.cache/bt-unpack" "$TOOLS/.cache/pl-unpack"
unzip -q "$BT_ZIP" -d "$TOOLS/.cache/bt-unpack"
unzip -q "$PL_ZIP" -d "$TOOLS/.cache/pl-unpack"
rm -rf "$TOOLS/android/build-tools/35.0.0"
mv "$TOOLS/.cache/bt-unpack/android-15" "$TOOLS/android/build-tools/35.0.0"
rm -rf "$TOOLS/android/platforms/android-35"
mv "$TOOLS/.cache/pl-unpack/android-35" "$TOOLS/android/platforms/android-35"
chmod +x "$TOOLS/android/build-tools/35.0.0/d8" \
  "$TOOLS/android/build-tools/35.0.0/aapt" \
  "$TOOLS/android/build-tools/35.0.0/zipalign" \
  "$TOOLS/android/build-tools/35.0.0/apksigner" 2>/dev/null || true

echo "$WANT_VERSION" > "$MARKER"
echo "build tools ready under $TOOLS"
