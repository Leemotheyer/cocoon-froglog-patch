# Cocoon Shell with a Froglog widget

A rebuilt [Cocoon Shell](https://github.com/inssekt/CocoonFE) 3.06 (`cocoon-306.apk`) that adds a **Froglog** home-screen widget. The existing **Recently played** widget is still there and still uses Cocoon's own library.

Froglog games come from the [Froglog API](https://wiki.froglog.co.uk/Api/Documentation). The widget lists the four most recently active public games from `GET /api/users/:username/games` and `GET /api/users/:username/live-service`, ordered by last session, then completion date, then start date. Each cell shows the cover, title, and date plus hours.

## What changed

- A new catalog tile named Froglog, separate from Recently played.
- Choosing it places `FroglogRecentWidget` at 4×2. Other tiles, including Recently played and the generic Android-widget picker, keep their original behavior.
- Sign-in is a Froglog username and password. The app stores the JWT and username only. The password is not saved.
- Games have to be public on Froglog or the API will not return them.

This build shares the package name `rip.moth.cocoonshell` and is signed with the debug key in `froglog/debug.keystore` (store password `froglog`, alias `froglog`). Uninstall the official Cocoon Shell app before installing this one. `versionCode` is 2 and `versionName` is `3.06-1-froglog`, so a later build with the same key can upgrade this install.

## Install

The packaged file is `dist/cocoon-306-froglog.apk` after a local build. It is not committed (the APK is about 180MB). The published build is sha256 `bbf052ca3eeea070737d93d9e5b10ddce6845d7cf334e39ce2efc14f1e829375` and is attached to the GitHub release [v3.06-1-froglog](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.06-1-froglog) as `cocoon-306-froglog.apk`.

There is no device on this machine, so install and on-screen behavior were not checked. Treat the APK as a prototype until you install it and open the widget picker.

1. Uninstall official Cocoon Shell.
2. Install `dist/cocoon-306-froglog.apk`.
3. In Cocoon's widget picker, add **Froglog**. Leave **Recently played** as it is.
4. Sign in. The grid fills from your public Froglog library. Tap the widget to sign in again or sign out.

## Rebuild

Needs a JDK, `apktool` 2.11.x, baksmali/dexlib2 3.0.9, and Android build-tools 35 (`d8`, `aapt`, `zipalign`, `apksigner`) plus `platforms/android-35/android.jar`.

```bash
# expected layout
# tools/apktool.jar
# tools/baksmali.jar
# tools/android/build-tools/35.0.0/
# tools/android/platforms/android-35/android.jar
# work/cocoon-306.apk          # GitHub release beta-3.06
./froglog/build.sh
```

If `work/cocoon-decoded` is missing, the script decodes the APK with `apktool d -s` first. A clean decode is the control: the script copies it, then patches that copy.

The catalog edit rewrites only `Lmf/y1; <clinit>` and `Lmf/y1;->h` inside `classes4.dex`. The new classes ship in `classes7.dex`. Baseline profiles are removed because the code no longer matches them.
