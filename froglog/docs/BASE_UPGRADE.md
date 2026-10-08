# Updating the Cocoon base under Froglog

Froglog is not a fork of Cocoon's Kotlin sources. It is stock Cocoon plus:

- `classes7.dex` — Froglog's own Java (`froglog/src`)
- rewrites of a few methods in `classes.dex` and `classes4.dex` (`froglog/tools/PatchCatalog.java`)
- extra resources and manifest entries (`froglog/tools/apply_resources.py`)

A newer Cocoon beta keeps the same product behavior and **renames obfuscated classes**. Room types such as `GameSessionDao_Impl` and `ThemeSettings` usually keep their names. Everything else in `PatchCatalog` (`Lmf/y1;`, `Lcf/pi;`, `Lxd/m0;`, …) has to be found again or the build throws `catalog=false` and the app will not get that feature.

The map of those hooks is [`froglog/base/hooks.json`](../base/hooks.json). Each entry has a fingerprint that survives renaming (a log string, an enum name, or a method line) and the files to edit when it moves.

## Stage a new APK without touching the 3.06 cache

`work/cocoon-decoded` and `work/smali-out` stay the tree `./froglog/build.sh` patches. A candidate base goes beside them:

```bash
./froglog/scripts/stage-new-base.sh \
  https://github.com/inssekt/CocoonFE/releases/download/beta-3.07/cocoon-307.apk \
  3.07
```

That writes:

| Path | What it is |
|------|------------|
| `work/bases/3.07/cocoon.apk` | The candidate, with `sha256` next to it |
| `work/bases/3.07/decoded/` | apktool decode |
| `work/bases/3.07/smali/` | Full baksmali |
| `work/bases/3.07/UPGRADE_REPORT.md` | What still matches, what moved, resource-id collisions, version rewrite |

A local file works the same way: `./froglog/scripts/stage-new-base.sh /path/to/cocoon.apk 3.07`.

Re-scan the current tree any time:

```bash
python3 froglog/scripts/scan-base-upgrade.py --check
```

`--check` exits 0 only when every hook is still on the class recorded in `hooks.json` and Froglog's resource ids are still free. Run that before you change the catalog, so you know the fingerprints themselves still describe 3.06.

## Rebind, in this order

1. Read `UPGRADE_REPORT.md`. A row marked `same` can stay. `moved` lists the new smali file. `missing` means the fingerprint is gone and that feature needs a fresh read of the feature (the report names the user-visible behavior).
2. For each moved hook, update every path in the report: `PatchCatalog.java` type constants, `verify_patch.py` paths, `froglog/stubs/<old package>/`, and any `Class.forName` (`CatalogHook`, `FroglogPods`, `FroglogTheme`, `FroglogSocial`, `FroglogMenu`). The stub's package must be the runtime name or the new dex will not link.
3. Point resource ids in `apply_resources.py` above the new `public.xml` maximums when the report lists collisions. Update `FROGLOG_POD_ICON` and `FROGLOG_ICON` in `PatchCatalog.java` to the same drawable ids.
4. Change the `versionName` / `versionCode` strings in `apply_resources.py` so they match `decoded/apktool.yml` and the new Froglog suffix. The report calls this out when the replace would no-op.
5. Replace the strings in `hooks.json` (`base` plus each `type` / `dex`) so the next scan describes the new base. Keep the anchors unless the report showed `string_drift`.
6. Build against the new decode **without** deleting the old cache until the build is good:

```bash
SRC_APK=work/bases/3.07/cocoon.apk \
DECODE=work/bases/3.07/decoded \
  ./froglog/build.sh
```

`build.sh` copies `DECODE` and patches that copy. It does not write back into `decoded/`.

7. When the APK installs and the checklist at the bottom of the report passes, make that tree the default: set `COCOON_APK_URL` and `COCOON_APK_SHA256` in `froglog/scripts/prepare-cocoon-reference.sh`, refresh `AGENTS.md`, and delete `work/cocoon-decoded` plus `work/smali-out` so the next bootstrap rebuilds them.

## What usually breaks, and what does not

| Usually survives | Usually needs a rebind |
|------------------|------------------------|
| Froglog Java in `froglog/src` that only talks to Froglog's API | `PatchCatalog` type strings |
| Session row shape on `GameSession` if Room keeps the class | Widget catalog `mf.y1` and field `f` |
| `classes7.dex` as an extra dex | Pod list `xd.m0`, action enum `xd.k0`, router `utils/u6` |
| Side-by-side package rename in `apply_resources.py` | Friends `ef.d0`, tab enum `ef.w0`, icons `ef.b` |
| | Picnic info dialog (`View session info in Log`) |
| | Menu list in `classes.dex` and dispatch `lf.k` |
| | Theme flow `eg.j0` field `j` |
| | Drawable ids `0x7f060218` and `0x7f06021a` if the new app used those slots |

## Patch rules that stay true on every base

- Do not add registers to a large Compose method. Parameter registers shift and the screen crashes. The Picnic info dialog is the known case.
- The Picnic row's platform string must stay a real platform (`SELECT`). An unknown platform crashes inside `fg/e`.
- A copied Compose row needs its own restart-group key.
- Write dex with version 037. dexlib2's API 35 opcodes rewrite the header to 041 and baksmali rejects it.
- Leave `insertAll` and the Recently played catalog path untouched. `verify_patch.py` checks that.

## Adding a feature later

When a patch starts depending on a new Cocoon class, add a hook to `hooks.json` in the same change: the current type, a string that is unlikely to be shared, and the files a future upgrade must touch. Then `python3 froglog/scripts/scan-base-upgrade.py --check` on the current `work/smali-out`.
