# Cocoon Shell with Froglog

A rebuilt [Cocoon Shell](https://github.com/inssekt/CocoonFE) 3.06 (`cocoon-306.apk`) that adds Froglog beside the existing **Recently played** widget. Recently played still uses Cocoon's own library.

Froglog data comes from the [Froglog API](https://wiki.froglog.co.uk/Api/Documentation). The recent-games widget reads public `GET /api/users/:username/games` and `GET /api/users/:username/live-service`. Writes use the signed-in library only to match a title before posting.

## What it adds

- **Froglog pod.** It sits with Picnic, Log, and the other pods. Sign in there. The password is sent to Froglog and is not stored. The signed-in library, including private games, is on that screen, with the same Recent, Playing, Finished, and Live filters. Tap a game for its details: a small cover beside the title, every field Froglog has for it (status, platform, hours, rating, dates, genre, and the rest), the review, and your logged sessions. **Add a Cocoon game** is on the same screen. **Following** lists people from your Froglog activity feed. **New games** lists Cocoon sessions that are not mapped to a Froglog entry yet. **Game mappings** lists every Cocoon title that logs to a Froglog game, whether it was matched by title or chosen by hand, plus titles set to not log. Tap one to move it to another library game, remove the mapping so the next session is matched again, or stop logging it. **Map a Cocoon game** maps a library game before it has a session. A change applies to sessions that have not been sent yet.
- **Friends list.** Cocoon's friends panel gets its own **Froglog** tab beside Steam and Android. It is not mixed into the Steam list. Like Froglog's Online Now card, it only lists people you follow (`GET /users/me/following`) whose `online` flag is set on `GET /activity/online`. Last-played activity does not count as online. Avatars load from Froglog's API host; animated GIFs are cached as a still PNG for Cocoon's friend row. The home friends pill counts them next to Steam friends, and the panel opens for a signed-in Froglog account even without Steam. Tap someone for their public games.
- **Froglog** catalog tile. Same 3×2 size, mint title, and empty-state body as **Recently played**. Four cells show cover, title, and play time. The header chip cycles Recent, Playing, Finished, and Live. Tap a cell for the same game details screen. Tap the title to open the Froglog pod.
- **Froglog stats** 1×1 tile from `GET /api/stats`: hours played this month.
- **Online presence.** While Cocoon has a game running, Froglog gets the same now-playing update LilyPad sends (`PUT /users/me/now-playing`). Friends see you on the Online Now card with that title. It clears as soon as the game is paused, closed, or finalized. A clear that fails, or a status left over from a crash, is retried on the next 15-second poll until Froglog accepts it. The game has to already be in your Froglog library, or mapped from a previous session.
- **Session logging.** Every finished session in Cocoon's `game_sessions` table, the one the Log pod lists, is copied into Froglog's queue and posted with `POST /games/:id/sessions`, the same LilyPad flow: session tracking and a start date on the library row, public sessions, and a stable `sync_ref`. The sync rechecks recent rows so a session that finalizes late is not skipped. Emulator ROM names are normalized before matching. The first run picks up the last 48 hours. If Cocoon later extends that session, the Froglog row's hours are updated with a full session PUT instead of a duplicate POST. A row is not sent while Cocoon still tracks that game, running or paused, because Cocoon folds a relaunch into the same row. Games added as Android shortcuts (BannerHub, for example) often end a few-second first session when the shortcut hands off, and that row would otherwise post as one minute while the game is still open. A row that ended in the last 30 seconds also waits one poll. Nothing is lost offline: queued sessions show in the pod under **Waiting to upload** and are sent when the network returns. Sessions for a game Froglog cannot match wait under **New games**. A one-time repair turns on tracking, sets the start date, and makes earlier Cocoon sessions public on games that were logged before this fix. A successful auto-submit shows a notification like LilyPad.
- **Add a Cocoon game.** From the Froglog pod, pick a recent Cocoon library game, search Froglog, and create it with `POST /api/games` as public or private if it is missing. The same screen opens from the session prompt. The game context menu in Cocoon is unchanged.

Sign-in stores the JWT and username only. The password is not saved. The widget grid stays on the public user endpoints, so private games do not appear there. A private game can still receive a session after it is linked.

This build uses the package name `rip.moth.cocoonshell.froglog` and the launcher label **Cocoon Froglog**, so it installs beside official Cocoon (`rip.moth.cocoonshell`). It is signed with the debug key in `froglog/debug.keystore` (store password `froglog`, alias `froglog`). A later Froglog build signed with the same key can upgrade in place. `versionCode` is 12 and `versionName` is `3.06-1-froglog11-dev`.

Cocoon only records play sessions with Usage Access, which its onboarding asks for. The `SKIP_SETUP=1` test build skips onboarding, so grant it by hand: `adb shell appops set rip.moth.cocoonshell.froglog GET_USAGE_STATS allow`.

Glass / `RuntimeShader` stays on Android 13+ (API 33). On older devices, including the Android 9 BlueStacks box used for testing, those compose paths return before they touch `RuntimeShader` and the theme falls back to solid surfaces. Android 13 is unchanged.

The Froglog recent tile uses the same 3×2 grid size as Recently played. Stats is 1×1. Adding a widget opens the pod only if you still need to sign in. Remove an old tile and add it again so the grid span updates.

## Install

`./froglog/build.sh` writes `dist/cocoon-306-froglog-<versionName>.apk` (not committed), for example `dist/cocoon-306-froglog-3.06-1-froglog11-dev.apk`. GitHub release assets use the same naming. The current **dev** release from the `dev` branch is [v3.06-1-froglog11-dev](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.06-1-froglog11-dev) (`cocoon-306-froglog-3.06-1-froglog11-dev.apk`, sha256 `b5438e20ff6c816f40d0923fcd9a14db493abc53d2fd23efd64e49cdf13eb7d7`). The last stable tag on `main` is [v3.06-1-froglog10](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.06-1-froglog10). `SKIP_SETUP=1 ./froglog/build.sh` still marks setup complete for BlueStacks test builds. The default build leaves setup in place for Android 13.

1. Official Cocoon can stay installed. A previous Froglog build with this same debug key can update over itself.
2. Install the versioned APK from Releases or from `dist/` after a local build.
3. Open the pods overlay and choose **Froglog**. Sign in and allow notifications if asked. The library on that screen includes private games. Following shows people from your Froglog activity.
4. Tap the friends pill at the top left of the home screen and pick the **Froglog** tab. People you follow who are in a game appear there. Tap one for their public games.
5. In Cocoon's widget picker, add **Froglog** and, if you want the monthly hours, **Froglog stats**. Leave **Recently played** as it is. Already signed in, the tile is placed without opening the pod. The grid stays on your public Froglog library. Tap the widget title to return to the pod.
6. Play a game from Cocoon. When it ends, the session posts to the matching Froglog game. Offline, it waits under **Waiting to upload**. If Froglog does not know the game yet, it waits under **New games**. Map it to a library game, create one, or dismiss it. Later sessions for that Cocoon game post on their own.
7. To add a library game that is not on Froglog yet, open the Froglog pod, then **Add a Cocoon game**.
8. To check or fix where a game logs, open the Froglog pod, then **Game mappings**.
9. In the Froglog pod account section, tap **Default session visibility** to choose **Public** (default) or **Private** for new Cocoon sessions posted to Froglog.

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

The catalog edit rewrites `Lmf/y1; <clinit>` and `Lmf/y1;->h`. The session edit prepends one call at the start of `GameSessionDao_Impl.insert`, which only wakes the sync. The sync itself reads `game_sessions` directly. The pod edit appends one entry in `Lxd/m0; <clinit>` and opens it from `Lrip/moth/cocoonshell/utils/u6;->a` before the other pods. The friends edit adds `FROGLOG` to the `Lef/w0;` tab enum and its `Lef/s5;` switch map. In `Lef/d0;->c0` it prepends that tab, lets a Froglog sign-in pass the "Steam available" gate, and adds the live Froglog count to the pill. `Lef/d0;->b0` swaps in Froglog rows for that tab, `Ldg/h4;->k` adds the same count to the Now Playing overlay pill, and `Lef/q3;->invoke` opens Froglog rows before Steam chat. The counts come from a StateFlow read with Cocoon's own `collectAsState`, so the pill updates when Froglog presence changes. Theme getters and the glass compose methods (`kf.n2.b`, `dg.m3.h`, `dg.m3.A0`) are gated so API 33+ still runs glass and older devices skip `RuntimeShader`. Android widgets keep a host-view squircle and an in-bounds drop shadow so the card does not blink while the home screen pans. Those are the rewritten methods in `classes4.dex`. The new classes ship in `classes7.dex`. Baseline profiles are removed because the code no longer matches them.
