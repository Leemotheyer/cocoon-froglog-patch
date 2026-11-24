#!/usr/bin/env bash
# Fast iteration: patch only the dex files into an already-built APK.
# Use ./froglog/build.sh for the first build and whenever resources change.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS="${TOOLS:-$ROOT/tools}"
WORK="${WORK:-$ROOT/work}"
DIST="${DIST:-$ROOT/dist}"
BASE_APK="${BASE_APK:-$WORK/cocoon-froglog-signed.apk}"
SRC_APK="${SRC_APK:-$WORK/cocoon-311.apk}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android/platforms/android-35/android.jar}"
SDK="${SDK:-$TOOLS/android/build-tools/35.0.0}"
KS="$ROOT/froglog/debug.keystore"
KS_PASS="${KS_PASS:-froglog}"
KS_ALIAS="${KS_ALIAS:-froglog}"

if [[ -z "${BAKSMALI:-}" ]]; then
  BAKSMALI="$TOOLS/smali/dexlib2.jar:$TOOLS/smali/util.jar:$TOOLS/smali/guava.jar:$TOOLS/smali/failureaccess.jar:$TOOLS/smali/jcommander.jar:$TOOLS/smali/baksmali.jar"
fi

need() { [[ -e "$1" ]] || { echo "missing $1" >&2; exit 1; }; }
need "$BASE_APK"
need "$SRC_APK"
need "$ANDROID_JAR"
need "$SDK/d8"
need "$SDK/zipalign"
need "$SDK/apksigner"

echo "compile widget"
rm -rf "$WORK/froglog-stubs" "$WORK/froglog-classes" "$WORK/froglog-dex"
mkdir -p "$WORK/froglog-stubs" "$WORK/froglog-classes" "$WORK/froglog-dex"
mapfile -t STUBS < <(find "$ROOT/froglog/stubs" -name '*.java' | sort)
mapfile -t SOURCES < <(find "$ROOT/froglog/src" -name '*.java' | sort)
javac --release 11 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/froglog-stubs" "${STUBS[@]}"
javac --release 11 -encoding UTF-8 -cp "$ANDROID_JAR:$WORK/froglog-stubs" -d "$WORK/froglog-classes" "${SOURCES[@]}"
jar cf "$WORK/froglog.jar" -C "$WORK/froglog-classes" .
"$SDK/d8" --min-api 24 --lib "$ANDROID_JAR" --output "$WORK/froglog-dex" "$WORK/froglog.jar"

echo "patch classes4"
rm -rf "$WORK/fast-classes" "$WORK/fast"
mkdir -p "$WORK/fast-classes" "$WORK/fast"
python3 - "$SRC_APK" "$WORK/fast/classes4.dex" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    open(sys.argv[2], 'wb').write(apk.read('classes4.dex'))
PY
javac --release 11 -encoding UTF-8 -cp "$BAKSMALI" -d "$WORK/fast-classes" "$ROOT/froglog/tools/PatchCatalog.java"
java -cp "$WORK/fast-classes:$BAKSMALI" PatchCatalog "$WORK/fast/classes4.dex" "$WORK/fast/classes4.patched.dex"

echo "inject dex"
python3 - "$BASE_APK" "$WORK/fast.apk" "$WORK/fast/classes4.patched.dex" "$WORK/froglog-dex/classes.dex" <<'PY'
import sys, zipfile
base, out, classes4, classes7 = sys.argv[1:5]
replace = {'classes4.dex': classes4, 'classes7.dex': classes7}
with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, 'w') as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename in replace:
            data = open(replace[item.filename], 'rb').read()
        zout.writestr(item, data)
PY
"$SDK/zipalign" -f -p 4 "$WORK/fast.apk" "$WORK/fast-aligned.apk"
"$SDK/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" --out "$WORK/fast-signed.apk" "$WORK/fast-aligned.apk"
mkdir -p "$DIST"
cp "$WORK/fast-signed.apk" "$DIST/cocoon-311-froglog-fast.apk"
echo "built $DIST/cocoon-311-froglog-fast.apk"
