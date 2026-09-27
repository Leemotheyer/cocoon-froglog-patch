# Cocoon Shell with Froglog

A rebuilt [Cocoon Shell](https://github.com/inssekt/CocoonFE) 3.06 (`cocoon-306.apk`) that adds Froglog beside the existing **Recently played** widget. Recently played still uses Cocoon's own library.

Froglog data comes from the [Froglog API](https://wiki.froglog.co.uk/Api/Documentation). The recent-games widget reads public `GET /api/users/:username/games` and `GET /api/users/:username/live-service`. Writes use the signed-in library only to match a title before posting.

## What it adds

- **Froglog pod.** It sits with Picnic, Log, and the other pods. Sign in there. The password is sent to Froglog and is not stored. The signed-in library, including private games, is on that screen, with the same Recent, Playing, Finished, and Live filters. Tap a game for the cover, the status line, and the review. **Add a Cocoon game** is on the same screen. **Following** lists people from your Froglog activity feed. **New games** lists Cocoon sessions that are not mapped to a Froglog entry yet.
- **Friends list.** Cocoon's social panel keeps Discord and Steam. People you follow on Froglog are added at the top, with what they last played. Tap one for their public games. Steam chat still opens for Steam friends.
- **Froglog** catalog tile. Four cells show cover, title, platform, status (In Progress, Completed, DNF, Live, Dormant), rating, session count, and hours. The chip cycles Recent, Playing, Finished, and Live. Tap a cell for the cover, that line, and the review. Tap the title to open the Froglog pod.
- **Froglog stats** catalog tile from `GET /api/stats`: hours and games finished this month and this year, plus completion rate. A rate of 1 or below is shown as a percent.
- **Session logging.** When Cocoon inserts a play session that just ended, Froglog keeps it if that Cocoon game is not mapped yet. The pod's **New games** list is where you map it to an existing library entry, create a Froglog game, or dismiss it. A suggestion ignores edition suffixes such as "Definitive Edition" and prefers an in-progress entry when two titles tie. Creating a game sends a stable `client_ref`. If Froglog answers that you already have that game, you can add the time to that entry or log it as a separate one. Before the hours are posted, a regular game has session tracking turned on, and a Completed or DNF entry is resumed. A failed post stays in New games so it can be retried, with the same `sync_ref`. Later sessions for a mapped game post on their own. "Don't ask for this game" is remembered. Nothing is posted until the game is confirmed.
- **Add a Cocoon game.** From the Froglog pod, pick a recent Cocoon library game, search Froglog, and create it with `POST /api/games` as public or private if it is missing. The same screen opens from the session prompt. The game context menu in Cocoon is unchanged.

Sign-in stores the JWT and username only. The password is not saved. The widget grid stays on the public user endpoints, so private games do not appear there. A private game can still receive a session after it is linked.

This build shares the package name `rip.moth.cocoonshell` and is signed with the debug key in `froglog/debug.keystore` (store password `froglog`, alias `froglog`). Uninstall the official Cocoon Shell app before the first install of this mod. A later Froglog build signed with the same key can upgrade in place. `versionCode` is 7 and `versionName` is `3.06-1-froglog6`.

The Froglog recent tile uses the same 3×2 grid size as Recently played. Covers stay small inside that cell. Remove the old Froglog tile and add it again so the grid span updates.

## Install

The packaged file is `dist/cocoon-306-froglog.apk` after `./froglog/build.sh`. It is not committed. This build is sha256 `7efd98f2b037340b5509ad808cc4bda0d94de1aa2ef10185273846e1de2dd657` and is attached to the GitHub release [v3.06-1-froglog6](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.06-1-froglog6) as `cocoon-306-froglog.apk`. There is no device on this machine, so install and on-screen behavior were not checked. Treat the APK as a prototype until you install it and open New games in the Froglog pod.

1. Uninstall official Cocoon Shell if it is still the store build. A previous Froglog build with this same debug key can update over itself.
2. Install `dist/cocoon-306-froglog.apk`.
3. Open the pods overlay and choose **Froglog**. Sign in and allow notifications if asked. The library on that screen includes private games. Following shows people from your Froglog activity.
4. Open Cocoon's friends list. Followed Froglog users appear with their latest game. Tap one for their public games.
5. In Cocoon's widget picker, add **Froglog** and, if you want the totals, **Froglog stats**. Leave **Recently played** as it is. The grid stays on your public Froglog library. Tap the widget title to return to the pod.
6. Finish a play session that Froglog does not know yet. It waits under **New games** in the pod. Map it to a library game, create one, or dismiss it. The next session for that Cocoon game posts on its own.
7. To add a library game that is not on Froglog yet, open the Froglog pod, then **Add a Cocoon game**.

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

The catalog edit rewrites `Lmf/y1; <clinit>` and `Lmf/y1;->h`. The session edit prepends one call at the start of `GameSessionDao_Impl.insert`. The pod edit appends one entry in `Lxd/m0; <clinit>` and opens it from `Lrip/moth/cocoonshell/utils/u6;->a` before the other pods. The friends edit appends Froglog follows in `Lef/d0;->k0` and opens them from `Lef/q3;->invoke` before Steam chat. Those are the only rewritten methods in `classes4.dex`. The new classes ship in `classes7.dex`. Baseline profiles are removed because the code no longer matches them.
