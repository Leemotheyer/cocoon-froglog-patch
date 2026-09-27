# Cocoon Shell with Froglog

A rebuilt [Cocoon Shell](https://github.com/inssekt/CocoonFE) 3.06 (`cocoon-306.apk`) that adds Froglog beside the existing **Recently played** widget. Recently played still uses Cocoon's own library.

Froglog data comes from the [Froglog API](https://wiki.froglog.co.uk/Api/Documentation). The recent-games widget reads public `GET /api/users/:username/games` and `GET /api/users/:username/live-service`. Writes use the signed-in library only to match a title before posting.

## What it adds

- **Froglog pod.** It sits with Picnic, Log, and the other pods. Sign in there. The password is sent to Froglog and is not stored. The signed-in library, including private games, is on that screen, with the same Recent, Playing, Finished, and Live filters. Tap a game for the cover, the status line, and the review. **Add a Cocoon game** is on the same screen. **Following** lists people from your Froglog activity feed. **New games** lists Cocoon sessions that are not mapped to a Froglog entry yet.
- **Friends list.** Cocoon's social panel keeps Discord and Steam. People you follow on Froglog are added at the top, with what they last played. Tap one for their public games. Steam chat still opens for Steam friends.
- **Froglog** catalog tile. Same 3×2 size, mint title, and empty-state body as **Recently played**. Four cells show cover, title, and play time. The header chip cycles Recent, Playing, Finished, and Live. Tap a cell for the cover, that line, and the review. Tap the title to open the Froglog pod.
- **Froglog stats** 1×1 tile from `GET /api/stats`: hours played this month.
- **Online presence.** While a Cocoon game is open, Froglog gets the same now-playing update LilyPad sends (`PUT /users/me/now-playing`). Friends see you on the Online Now card with that title. The status clears when the session ends. The game has to already be in your Froglog library, or mapped from a previous session.
- **Session logging.** When Cocoon inserts a play session that just ended, Froglog keeps it if that Cocoon game is not mapped yet. The pod's **New games** list is where you map it to an existing library entry, create a Froglog game, or dismiss it. A suggestion ignores edition suffixes such as "Definitive Edition" and prefers an in-progress entry when two titles tie. Creating a game sends a stable `client_ref`. If Froglog answers that you already have that game, you can add the time to that entry or log it as a separate one. Before the hours are posted, a regular game has session tracking turned on, and a Completed or DNF entry is resumed. A failed post stays in New games so it can be retried, with the same `sync_ref`. Later sessions for a mapped game post on their own. "Don't ask for this game" is remembered. Nothing is posted until the game is confirmed.
- **Add a Cocoon game.** From the Froglog pod, pick a recent Cocoon library game, search Froglog, and create it with `POST /api/games` as public or private if it is missing. The same screen opens from the session prompt. The game context menu in Cocoon is unchanged.

Sign-in stores the JWT and username only. The password is not saved. The widget grid stays on the public user endpoints, so private games do not appear there. A private game can still receive a session after it is linked.

This build uses the package name `rip.moth.cocoonshell.froglog` and the launcher label **Cocoon Froglog**, so it installs beside official Cocoon (`rip.moth.cocoonshell`). It is signed with the debug key in `froglog/debug.keystore` (store password `froglog`, alias `froglog`). A later Froglog build signed with the same key can upgrade in place. `versionCode` is 9 and `versionName` is `3.06-1-froglog8`.

Glass / `RuntimeShader` stays on Android 13+ (API 33). On older devices, including the Android 9 BlueStacks box used for testing, those compose paths return before they touch `RuntimeShader` and the theme falls back to solid surfaces. Android 13 is unchanged.

The Froglog recent tile uses the same 3×2 grid size as Recently played. Stats is 1×1. Adding a widget opens the pod only if you still need to sign in. Remove an old tile and add it again so the grid span updates.

## Install

Download [`cocoon-306-froglog.apk`](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/download/v3.06-1-froglog8/cocoon-306-froglog.apk) from [GitHub Releases](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.06-1-froglog8) (`3.06-1-froglog8`, sha256 `679e7ad4ee1c925d5484d0cf9af990ea96c7ce90a806e0133b1edaa6bf86bb4d`). Or build `dist/cocoon-306-froglog.apk` with `./froglog/build.sh` (not committed). `SKIP_SETUP=1 ./froglog/build.sh` writes a test APK that marks setup complete so BlueStacks can reach the home screen without the onboarding wizard. The default build leaves setup in place for Android 13.

On Android 9 BlueStacks, `3.06-1-froglog8` reaches the home screen, the all-apps drawer, and the Froglog pod. Official Cocoon can stay installed at the same time. Android 13 glass has not been re-checked on a physical device yet.

1. Official Cocoon can stay installed. A previous Froglog build with this same debug key can update over itself.
2. Install `dist/cocoon-306-froglog.apk`.
3. Open the pods overlay and choose **Froglog**. Sign in and allow notifications if asked. The library on that screen includes private games. Following shows people from your Froglog activity.
4. Open Cocoon's friends list. Followed Froglog users appear with their latest game. Tap one for their public games.
5. In Cocoon's widget picker, add **Froglog** and, if you want the monthly hours, **Froglog stats**. Leave **Recently played** as it is. Already signed in, the tile is placed without opening the pod. The grid stays on your public Froglog library. Tap the widget title to return to the pod.
6. Finish a play session that Froglog does not know yet. It waits under **New games** in the pod. Map it to a library game, create one, or dismiss it. The next session for that Cocoon game posts on its own.
7. To add a library game that is not on Froglog yet, open the Froglog pod, then **Add a Cocoon game**.

## Rebuild

Needs a JDK, `apktool` 2.11.x, smali/dexlib2 2.5.2 (`tools/smali/*.jar`), and Android build-tools 35 (`d8`, `aapt`, `zipalign`, `apksigner`) plus `platforms/android-35/android.jar`.

```bash
# expected layout
# tools/apktool.jar
# tools/smali/{dexlib2,util,baksmali,guava,failureaccess}.jar
# tools/android/build-tools/35.0.0/
# tools/android/platforms/android-35/android.jar
# work/cocoon-306.apk          # GitHub release beta-3.06
./froglog/build.sh
SKIP_SETUP=1 ./froglog/build.sh   # BlueStacks test APK that skips onboarding
```

If `work/cocoon-decoded` is missing, the script decodes the APK with `apktool d -s` first. A clean decode is the control: the script copies it, then patches that copy.

The catalog edit rewrites `Lmf/y1; <clinit>` and `Lmf/y1;->h`. The session edit prepends one call at the start of `GameSessionDao_Impl.insert`. The pod edit appends one entry in `Lxd/m0; <clinit>` and opens it from `Lrip/moth/cocoonshell/utils/u6;->a` before the other pods. The friends edit appends Froglog follows in `Lef/d0;->k0` and opens them from `Lef/q3;->invoke` before Steam chat. Theme getters and the glass compose methods (`kf.n2.b`, `dg.m3.h`, `dg.m3.A0`) are gated so API 33+ still runs glass and older devices skip `RuntimeShader`. Android widgets keep a host-view squircle and an in-bounds drop shadow so the card does not blink while the home screen pans. Those are the rewritten methods in `classes4.dex`. The new classes ship in `classes7.dex`. Baseline profiles are removed because the code no longer matches them.
