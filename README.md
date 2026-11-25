# Cocoon Froglog

A rebuilt [Cocoon Shell](https://github.com/KansoMirai/CocoonFE) 3.11 with a Froglog pod, two home-screen widgets, a Froglog friends tab, and automatic session logging.

Froglog data comes from the [Froglog API](https://wiki.froglog.co.uk/Api/Documentation).

## Install

1. Download the latest APK from [Releases](https://github.com/Leemotheyer/cocoon-froglog-patch/releases).
2. Install it. It uses the package name `rip.moth.cocoonshell.froglog`, so it installs beside stock Cocoon.
3. Open the pods overlay and choose **Froglog**. Sign in there.
4. Add the **Froglog** and **Froglog stats** widgets from Cocoon's widget picker.

The current release is [v3.11-1-froglog19-dev](https://github.com/Leemotheyer/cocoon-froglog-patch/releases/tag/v3.11-1-froglog19-dev) (`cocoon-311-froglog-3.11-1-froglog19-dev.apk`, sha256 `8bdc9091a89c5917bcee592e3a9d0bbaf695d13ca6c686048f0368b67225a5fd`).

## Features

- **Froglog pod** — library, game details and editing, following, new games, mappings, and Picnic screenshot uploads.
- **Froglog widget** — recent, playing, finished, and live games, with a waiting-to-upload count.
- **Froglog stats widget** — month hours, daily bars, streak, year, finished count, and top game.
- **Friends tab** — online Froglog follows beside Steam and Android, with a count in the friends pill.
- **Game context menu** — **Log to Froglog…** opens that game's mapping.
- **Picnic** — **Upload to Froglog** on a screenshot's info screen.
- **Session logging** — finished Cocoon sessions post to the matched Froglog game, online or offline.
- **Presence** — Cocoon's running game appears on Froglog's now-playing card.
- **Theme and controller** — follows Cocoon's light, dark, OLED, accent, and custom themes; B goes back, A activates.

Sign-in stores the JWT and username only. The password is not saved.

## Build

Needs JDK 17, apktool 2.11.x, smali/dexlib2 2.5.2, and Android build-tools 35.

```bash
./froglog/build.sh
# dist/cocoon-311-froglog-<versionName>.apk
```

`./froglog/scripts/agent-bootstrap.sh` downloads the tools and the Cocoon 3.11 base. See [AGENTS.md](AGENTS.md) for the patch layout and the upgrade workflow.

## Notes

- `versionCode` 20, `versionName` `3.11-1-froglog19-dev`.
- Signed with `froglog/debug.keystore` (store password `froglog`, alias `froglog`).
- Cocoon only records play sessions with Usage Access.
- Glass uses `RuntimeShader` on Android 13+; older devices fall back to solid surfaces.
