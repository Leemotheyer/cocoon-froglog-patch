# Cloud agents and bytecode development

This guide is for **agents and contributors patching Cocoon Shell**. User-facing install notes stay in [README.md](README.md).

## Branch policy

| Branch | Purpose |
|--------|---------|
| **`dev`** | Active Froglog work, `.cursor/environment.json`, this file, and bootstrap scripts |
| **`main`** | Release APKs only; intentionally **no** agent bootstrap or decompiled cache |

Point Cloud Agent environments at the **`dev`** branch for builds and installs. The repo default is `main`, so a build that only tracks default `main` will **not** run the bootstrap in `.cursor/environment.json`.

After merging bootstrap changes on `dev`, trigger or rebuild your Cloud environment from the **`dev`** ref and **activate** that build so `install` snapshots `tools/` and `work/` (~1 GB). Draft builds from non-default refs are fine for this repo; promotable default-branch builds are optional because agent files stay off `main`.

## One-time bootstrap (local or fresh VM)

```bash
./froglog/scripts/agent-bootstrap.sh
```

This installs (into gitignored dirs):

- **`tools/`** — apktool 2.11.1, smali 2.5.2, Android build-tools 35.0.0, `android-35` stub jar
- **`work/cocoon-306.apk`** — [CocoonFE beta-3.06](https://github.com/inssekt/CocoonFE/releases/tag/beta-3.06) (sha256 `e9df90ad83200f984f085dac51448fffb558b509f7ece50d6889bb5b393cc4d5`)
- **`work/cocoon-decoded/`** — `apktool d -s` template copied by `build.sh`
- **`work/smali-out/`** — full baksmali of stock Cocoon for reading upstream code

Lighter cache (no smali, ~400 MB):

```bash
SKIP_SMALI=1 ./froglog/scripts/prepare-cocoon-reference.sh
```

Cloud Agent **`install`** (`.cursor/environment.json`) runs the same bootstrap so environment **build snapshots** retain `tools/` and `work/` between runs.

## Where to read stock Cocoon

| Feature area | Dex | Smali path (under `work/smali-out/`) |
|--------------|-----|--------------------------------------|
| Picnic UI (`cf/pi`, screenshot info) | `classes4` | `smali_classes4/cf/pi.smali` |
| Friends tabs, pods router | `classes4` | `smali_classes4/ef/`, `xd/`, `rip/moth/cocoonshell/utils/` |
| Widget catalog | `classes4` | `smali_classes4/mf/y1.smali` |
| Game context menu list | `classes` | `smali/a8/z.smali` |
| Session DAO | `classes4` | `smali_classes4/rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali` |

Quick slice of one or two classes without loading all of `smali-out`:

```bash
./froglog/scripts/disassemble-classes.sh classes4 cf/pi cf/pd
# output: work/disasm-scratch/
```

## How Froglog changes Cocoon

1. **Java sources** — `froglog/src/rip/moth/cocoonshell/froglog/` (compiled to `classes7.dex`).
2. **Bytecode patches** — `froglog/tools/PatchCatalog.java` rewrites methods in `classes4.dex` (and `classes.dex` for menus). Constants at the top of `PatchCatalog` name the smali types (`Lcf/pi;`, `Lef/d0;`, …).
3. **Resources / manifest** — `froglog/tools/apply_resources.py` on the decoded tree.
4. **Verification** — `froglog/tools/verify_patch.py` disassembles patched methods and asserts structure.

**Build everything:**

```bash
./froglog/build.sh
# APK: dist/cocoon-306-froglog-<versionName>.apk
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
