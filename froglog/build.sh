#!/usr/bin/env bash
# Rebuild cocoon-306 with a separate Froglog widget. Recently played is not edited.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:-$ROOT/tools}"
WORK="${WORK:-$ROOT/work}"
DIST="${DIST:-$ROOT/dist}"
APKTOOL="${APKTOOL:-$TOOLS/apktool.jar}"
BAKSMALI="${BAKSMALI:-$TOOLS/baksmali.jar}"
SDK="${SDK:-$TOOLS/android/build-tools/35.0.0}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android/platforms/android-35/android.jar}"
SRC_APK="${SRC_APK:-$WORK/cocoon-306.apk}"
DECODE="${DECODE:-$WORK/cocoon-decoded}"
BUILD="${BUILD:-$WORK/froglog-decoded}"
JSON_JAR="${JSON_JAR:-$WORK/json.jar}"
KS="$ROOT/froglog/debug.keystore"
KS_PASS="${KS_PASS:-froglog}"
KS_ALIAS="${KS_ALIAS:-froglog}"

need() {
  if [[ ! -e "$1" ]]; then
    echo "missing $1" >&2
    exit 1
  fi
}

need "$APKTOOL"
need "$BAKSMALI"
need "$ANDROID_JAR"
need "$SDK/d8"
need "$SDK/zipalign"
need "$SDK/apksigner"
need "$SRC_APK"

if [[ ! -d "$DECODE/res" ]]; then
  echo "decoding $SRC_APK"
  java -jar "$APKTOOL" d -s -f -o "$DECODE" "$SRC_APK"
fi

mkdir -p "$WORK" "$DIST"
if [[ ! -f "$JSON_JAR" ]]; then
  curl -fsSL -o "$JSON_JAR" https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
fi

echo "unit test"
rm -rf "$WORK/froglog-test"
mkdir -p "$WORK/froglog-test"
javac --release 11 -encoding UTF-8 -cp "$JSON_JAR" -d "$WORK/froglog-test" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogGame.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogGames.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogMatch.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogQueue.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogCreate.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogTracking.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogFollow.java" \
  "$ROOT/froglog/src/rip/moth/cocoonshell/froglog/FroglogFollows.java" \
  "$ROOT/froglog/test/FroglogGamesTest.java" \
  "$ROOT/froglog/test/FroglogMatchTest.java" \
  "$ROOT/froglog/test/FroglogFollowsTest.java" \
  "$ROOT/froglog/test/FroglogQueueTest.java" \
  "$ROOT/froglog/test/FroglogTrackingTest.java"
java -cp "$WORK/froglog-test:$JSON_JAR" FroglogGamesTest
java -cp "$WORK/froglog-test:$JSON_JAR" FroglogMatchTest
java -cp "$WORK/froglog-test:$JSON_JAR" FroglogFollowsTest
java -cp "$WORK/froglog-test:$JSON_JAR" FroglogQueueTest
java -cp "$WORK/froglog-test:$JSON_JAR" FroglogTrackingTest

echo "compile widget"
rm -rf "$WORK/froglog-stubs" "$WORK/froglog-classes"
mkdir -p "$WORK/froglog-stubs" "$WORK/froglog-classes"
mapfile -t STUBS < <(find "$ROOT/froglog/stubs" -name '*.java' | sort)
mapfile -t SOURCES < <(find "$ROOT/froglog/src" -name '*.java' | sort)
javac --release 11 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/froglog-stubs" "${STUBS[@]}"
javac --release 11 -encoding UTF-8 -cp "$ANDROID_JAR:$WORK/froglog-stubs" -d "$WORK/froglog-classes" "${SOURCES[@]}"
# Stubs must not be dexed. org.json stays in the framework.
jar cf "$WORK/froglog.jar" -C "$WORK/froglog-classes" .
rm -rf "$WORK/froglog-dex"
mkdir -p "$WORK/froglog-dex"
"$SDK/d8" --min-api 24 --lib "$ANDROID_JAR" --output "$WORK/froglog-dex" "$WORK/froglog.jar"
test -f "$WORK/froglog-dex/classes.dex"

echo "patch catalog"
rm -rf "$WORK/patch-classes"
mkdir -p "$WORK/patch-classes"
javac --release 11 -encoding UTF-8 -cp "$BAKSMALI" -d "$WORK/patch-classes" "$ROOT/froglog/tools/PatchCatalog.java"
cp "$DECODE/classes4.dex" "$WORK/classes4.original.dex"
java -cp "$WORK/patch-classes:$BAKSMALI" PatchCatalog "$WORK/classes4.original.dex" "$WORK/classes4.patched.dex"

python3 "$ROOT/froglog/tools/verify_patch.py" \
  "$BAKSMALI" \
  "$WORK/classes4.original.dex" \
  "$WORK/classes4.patched.dex"

if [[ -f /tmp/apk-reverse/skills/apk-reverse/scripts/dex_classdiff.py ]]; then
  python3 /tmp/apk-reverse/skills/apk-reverse/scripts/dex_classdiff.py \
    "$WORK/classes4.original.dex" "$WORK/classes4.patched.dex"
fi

echo "assemble"
rm -rf "$BUILD"
cp -a "$DECODE" "$BUILD"
python3 "$ROOT/froglog/tools/apply_resources.py" "$BUILD"
cp "$WORK/classes4.patched.dex" "$BUILD/classes4.dex"
cp "$WORK/froglog-dex/classes.dex" "$BUILD/classes7.dex"

java -jar "$APKTOOL" b -o "$WORK/cocoon-froglog-unsigned.apk" "$BUILD"
"$SDK/zipalign" -f -p 4 "$WORK/cocoon-froglog-unsigned.apk" "$WORK/cocoon-froglog-aligned.apk"

if [[ ! -f "$KS" ]]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Froglog Widget,O=Cocoon Mod,C=UK"
fi

OUT="$DIST/cocoon-306-froglog.apk"
"$SDK/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" --out "$OUT" "$WORK/cocoon-froglog-aligned.apk"
"$SDK/apksigner" verify --verbose "$OUT"
"$SDK/aapt" dump badging "$OUT" > "$WORK/badging.txt"
head -n 5 "$WORK/badging.txt"

python3 - <<PY
import hashlib, zipfile
apk = "$OUT"
data = open(apk, "rb").read()
print("sha256", hashlib.sha256(data).hexdigest())
print("bytes", len(data))
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    manifest = z.read("AndroidManifest.xml")
for required in ("classes4.dex", "classes7.dex"):
    if required not in names:
        raise SystemExit(f"missing {required}")
if any(name.endswith("baseline.prof") or name.endswith("baseline.profm") for name in names):
    raise SystemExit("baseline profiles were not removed")
# Binary manifest strings are UTF-16.
for token in (
    "FroglogRecentWidget",
    "FroglogStatsWidget",
    "FroglogWidgetConfig",
    "FroglogInitProvider",
    "FroglogGameDetail",
    "FroglogSessionPrompt",
    "FroglogMapActivity",
    "FroglogAddGame",
    "FroglogLibraryPicker",
    "FroglogPodActivity",
    "FroglogFriendActivity",
):
    if token.encode("utf-16le") not in manifest:
        raise SystemExit(f"manifest missing {token}")
print("package contains classes7.dex and the Froglog components")
PY
echo "built $OUT"
