# Cloud agents and bytecode development

This guide is for **agents and contributors patching Cocoon Shell**. User-facing install notes stay in [README.md](README.md).

## Branch policy

`main` is the only long-lived branch. It holds the product, the build tools, the base-upgrade map, and the release notes. Use a short-lived branch for a feature or a port, then merge it back to `main` and delete it.

The repo default is `main`. `.cursor/environment.json` runs the bootstrap from whichever ref the environment uses, so point Cloud Agent environments at `main`.

## One-time bootstrap (local or fresh VM)

```bash
./froglog/scripts/agent-bootstrap.sh
```

This installs (into gitignored dirs):

- **`tools/`** — apktool 2.11.1, smali 2.5.2, Android build-tools 35.0.0, `android-35` stub jar
- **`work/cocoon-311.apk`** — [CocoonFE beta-3.11](https://github.com/KansoMirai/CocoonFE/releases/tag/beta-3.11) (sha256 `e460300a902172f7cda8b9f9af8e7b33933ba0baef775e44b7c9ddfd05387378`)
- **`work/cocoon-311-decoded/`** — `apktool d -s` template copied by `build.sh`
- **`work/smali-311-out/`** — full baksmali of stock Cocoon for reading upstream code

Lighter cache (no smali, ~400 MB):

```bash
SKIP_SMALI=1 ./froglog/scripts/prepare-cocoon-reference.sh
```

Cloud Agent **`install`** (`.cursor/environment.json`) runs the same bootstrap so environment **build snapshots** retain `tools/` and `work/` between runs.

## Where to read stock Cocoon

| Feature area | Dex | Smali path (under `work/smali-311-out/`) |
|--------------|-----|--------------------------------------|
| Picnic UI (`cf/pi`, screenshot info) | `classes4` | `smali_classes4/ff/fi.smali` |
| Friends tabs, pods router | `classes4` | `smali_classes4/ef/`, `xd/`, `rip/moth/cocoonshell/utils/` |
| Widget catalog | `classes4` | `smali_classes4/pf/x1.smali` |
| Game context menu list | `classes` | `smali/a8/z.smali` |
| Session DAO | `classes4` | `smali_classes4/rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali` |

Quick slice of one or two classes without loading all of `smali-out`:

```bash
./froglog/scripts/disassemble-classes.sh classes4 cf/pi cf/pd
# output: work/disasm-scratch/
```

## Updating to a newer Cocoon beta

Stock class names such as `Lpf/x1;` and `Lff/fi;` change when Cocoon ships a new beta. The Froglog features stay; the bindings move.

```bash
./froglog/scripts/stage-new-base.sh <apk-or-url> <label>
```

That leaves the current `work/smali-out` cache in place and writes `work/bases/<label>/UPGRADE_REPORT.md`. The hook map is `froglog/base/hooks.json`. The full order (rebind, resource ids, version rewrite, then point `build.sh` at the new decode) is [froglog/docs/BASE_UPGRADE.md](froglog/docs/BASE_UPGRADE.md). That workflow was checked against Cocoon beta 3.11; the report is a map for a port, and the default base is 3.11 until the rebind is done.

Check the fingerprints still describe the tree you are patching:

```bash
python3 froglog/scripts/scan-base-upgrade.py --check
```

## How Froglog changes Cocoon

1. **Java sources** — `froglog/src/rip/moth/cocoonshell/froglog/` (compiled to `classes7.dex`).
2. **Bytecode patches** — `froglog/tools/PatchCatalog.java` rewrites methods in `classes4.dex` (and `classes.dex` for menus). Constants at the top of `PatchCatalog` name the smali types (`Lff/fi;`, `Lhf/j8;`, …).
3. **Resources / manifest** — `froglog/tools/apply_resources.py` on the decoded tree.
4. **Verification** — `froglog/tools/verify_patch.py` disassembles patched methods and asserts structure.

**Build everything:**

```bash
./froglog/build.sh
# APK: dist/cocoon-311-froglog-<versionName>.apk
```

**Unit tests only** (no APK): the `echo "unit test"` block at the start of `build.sh` — or run those `javac`/`java` lines from the script.

## Dalvik / Compose pitfalls (read before editing patches)

- **Do not increase `.registers` in large Compose methods** (e.g. `cf/pi.Y`) without re-checking every `p0`/`p1` parameter read. Extra locals shift parameter registers and cause subtle runtime crashes.
- **Picnic action rows** use platform strings with `fg/e.a`. Unknown platform IDs throw at runtime. Froglog upload uses `FroglogPicnic.isUploadRow()` and real platform `"SELECT"`, not a fake platform id in the string slot.
- **Compose restart groups** need distinct keys when duplicating a row pattern (`cf/pi.W`).
- Keep dex **version 037** when patching (`PatchCatalog` comment); do not bump to dex 041 for API 35 tooling.

## Gitignored paths (never commit)

```
work/     # APK, decode, smali-out, patch scratch
dist/     # signed output APKs
tools/    # apktool, smali, Android SDK pieces
```

## Merging to `main`

When cutting a release for `main`, merge **product** changes (Java, PatchCatalog, resources, README release notes). You do **not** need to merge `AGENTS.md` or `.cursor/environment.json` to keep `main` tidy; keep agent bootstrap on `dev` only.
