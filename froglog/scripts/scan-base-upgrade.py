#!/usr/bin/env python3
"""Compare a Cocoon smali tree to froglog/base/hooks.json.

Prints which Froglog hooks still sit on the recorded class, which moved,
and whether froglog resource ids would collide with the new public.xml.
Exit 0 with --check only when every hook is unchanged and resources are free.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOOKS_PATH = ROOT / "froglog" / "base" / "hooks.json"
RESOURCES = ROOT / "froglog" / "tools" / "apply_resources.py"

PUBLIC_TUPLE = re.compile(
    r'\("(?P<typ>[a-z]+)", "(?P<name>[^"]+)", (?P<id>0x[0-9A-Fa-f]+)\)'
)
ID_BASE = re.compile(r"0x([0-9A-Fa-f]+) \+ index")


def dex_dir(dex: str) -> str:
    if dex == "classes":
        return "smali"
    return "smali_" + dex


def type_to_rel(type_desc: str, dex: str) -> str:
    internal = type_desc[1:-1] if type_desc.startswith("L") and type_desc.endswith(";") else type_desc
    internal = internal.replace("$", "$")
    return f"{dex_dir(dex)}/{internal}.smali"


def load_hooks() -> dict:
    return json.loads(HOOKS_PATH.read_text(encoding="utf-8"))


def froglog_ids() -> list[tuple[str, str, int]]:
    text = RESOURCES.read_text(encoding="utf-8")
    found = [(m.group("typ"), m.group("name"), int(m.group("id"), 16)) for m in PUBLIC_TUPLE.finditer(text)]
    id_names = re.search(r"IDS = \[(.*?)\]", text, re.S)
    base = ID_BASE.search(text)
    if id_names and base:
        start = int(base.group(1), 16)
        names = re.findall(r'"([^"]+)"', id_names.group(1))
        for index, name in enumerate(names):
            found.append(("id", name, start + index))
    if not found:
        raise SystemExit(f"could not parse resource ids from {RESOURCES}")
    return found


def expected_version_rewrite() -> tuple[str, str] | None:
    text = RESOURCES.read_text(encoding="utf-8")
    src = re.search(r'replace\("versionName: ([^"]+)\\n", "versionName: ([^"]+)\\n"', text)
    if not src:
        return None
    return src.group(1), src.group(2)


def apk_version(decode: Path) -> tuple[str, str]:
    yml = (decode / "apktool.yml").read_text(encoding="utf-8")
    code = re.search(r"versionCode:\s*(\S+)", yml)
    name = re.search(r"versionName:\s*(\S+)", yml)
    return (code.group(1) if code else "?"), (name.group(1) if name else "?")


def index_smali(smali: Path, needles: list[str]) -> dict[str, list[str]]:
    hits = {needle: [] for needle in needles}
    if not needles:
        return hits
    for path in smali.rglob("*.smali"):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = path.relative_to(smali).as_posix()
        for needle in needles:
            if needle in text:
                hits[needle].append(rel)
    return hits


def public_max(decode: Path) -> dict[str, int]:
    public = decode / "res" / "values" / "public.xml"
    maxima: dict[str, int] = {}
    if not public.exists():
        return maxima
    for match in re.finditer(r'<public type="([^"]+)" name="([^"]+)" id="(0x[0-9A-Fa-f]+)"', public.read_text(encoding="utf-8")):
        typ, _name, raw = match.group(1), match.group(2), int(match.group(3), 16)
        maxima[typ] = max(maxima.get(typ, 0), raw)
    return maxima


def classify(hook: dict, hits: dict[str, list[str]], smali: Path) -> dict:
    expected = type_to_rel(hook["type"], hook["dex"])
    anchors = hook.get("anchors") or []
    match = hook.get("match", "all")
    sets = [set(hits.get(anchor, [])) for anchor in anchors]
    if not sets:
        chosen: set[str] = set()
    elif match == "any":
        chosen = set.union(*sets) if sets else set()
    else:
        chosen = set.intersection(*sets) if sets else set()
    exists = (smali / expected).is_file()
    if exists and (not anchors or expected in chosen):
        status = "same"
    elif chosen:
        status = "moved" if expected not in chosen else "same"
    elif exists:
        status = "string_drift"
    else:
        status = "missing"
    return {
        "id": hook["id"],
        "status": status,
        "expected": expected,
        "exists": exists,
        "candidates": sorted(chosen),
        "feature": hook["feature"],
        "edit": hook.get("edit") or [],
        "caveat": hook.get("caveat"),
        "stableName": bool(hook.get("stableName")),
    }


def render(report: dict) -> str:
    lines = [
        f"# Cocoon base upgrade report",
        "",
        f"- Scanned smali: `{report['smali']}`",
        f"- Decode: `{report['decode']}`",
        f"- apktool versionName: `{report['versionName']}` (versionCode field `{report['versionCode']}`)",
        f"- Hook catalog base: `{report['catalogVersion']}` `{report['catalogTag']}`",
        "",
        "## Version rewrite",
        "",
        report["versionNote"],
        "",
        "## Resource ids",
        "",
    ]
    if report["resourceConflicts"]:
        lines.append("Froglog ids overlap this base. Move them up in `froglog/tools/apply_resources.py` and the matching constants in `PatchCatalog.java` (`FROGLOG_POD_ICON`, `FROGLOG_ICON`).")
        lines.append("")
        for row in report["resourceConflicts"]:
            lines.append(
                f"- **{row['type']}** froglog `{row['name']}` is `{row['id']}`; "
                f"this base already uses up to `{row['max']}`"
            )
    else:
        lines.append("Froglog resource ids sit above every id in this base's `public.xml`.")
    lines.append("")
    lines.append("## Hooks")
    lines.append("")
    lines.append("| Status | Hook | Expected class |")
    lines.append("| --- | --- | --- |")
    for row in report["hooks"]:
        lines.append(f"| {row['status']} | `{row['id']}` | `{row['expected']}` |")
    lines.append("")
    for row in report["hooks"]:
        if row["status"] == "same":
            continue
        lines.append(f"### {row['id']} ({row['status']})")
        lines.append("")
        lines.append(row["feature"])
        lines.append("")
        if row["candidates"]:
            lines.append("Candidates:")
            lines.append("")
            for candidate in row["candidates"]:
                lines.append(f"- `{candidate}`")
            lines.append("")
        else:
            lines.append("No file contained the fingerprint.")
            lines.append("")
        if row.get("caveat"):
            lines.append(row["caveat"])
            lines.append("")
        lines.append("Update:")
        lines.append("")
        for path in row["edit"]:
            lines.append(f"- {path}")
        lines.append("")
    lines.extend(
        [
            "## After the names are rebound",
            "",
            "- [ ] `PatchCatalog` constants, `verify_patch.py`, stubs, and `Class.forName` strings match the candidates above",
            "- [ ] `apply_resources.py` versionName rewrite matches this apk",
            "- [ ] Resource ids still clear the table above",
            "- [ ] `./froglog/build.sh` passes `verify_patch.py`",
            "- [ ] Pod opens and signs in",
            "- [ ] Widget tiles show public recent games and stats",
            "- [ ] A finished session lands in Froglog (or Waiting to upload)",
            "- [ ] Friends tab lists online follows",
            "- [ ] Game menu **Log to Froglog** opens mapping",
            "- [ ] Picnic screenshot info shows **Upload to Froglog** and uploads the mapped game",
            "- [ ] Theme follows Cocoon light and dark",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--smali", type=Path, default=ROOT / "work" / "smali-out")
    parser.add_argument("--decode", type=Path, default=ROOT / "work" / "cocoon-decoded")
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--check", action="store_true", help="exit 1 unless every hook is unchanged and ids are free")
    args = parser.parse_args()
    catalog = load_hooks()
    needles: list[str] = []
    for hook in catalog["hooks"]:
        for anchor in hook.get("anchors") or []:
            if anchor not in needles:
                needles.append(anchor)
    if not args.smali.is_dir():
        raise SystemExit(f"missing smali tree {args.smali} (run stage-new-base.sh or agent-bootstrap.sh)")
    hits = index_smali(args.smali, needles)
    rows = [classify(hook, hits, args.smali) for hook in catalog["hooks"]]
    code, name = ("?", "?")
    if args.decode.is_dir():
        code, name = apk_version(args.decode)
    maxima = public_max(args.decode) if args.decode.is_dir() else {}
    conflicts = []
    for typ, res_name, rid in froglog_ids():
        top = maxima.get(typ, 0)
        if top >= rid:
            conflicts.append({"type": typ, "name": res_name, "id": f"0x{rid:08x}", "max": f"0x{top:08x}"})
    rewrite = expected_version_rewrite()
    if rewrite is None:
        version_note = "Could not read the versionName replace in apply_resources.py."
        version_ok = False
    elif name in ("?", ""):
        version_note = "No apktool.yml next to this decode, so the version rewrite was not checked."
        version_ok = True
    elif name != rewrite[0]:
        version_note = (
            f"`apply_resources.py` still rewrites versionName `{rewrite[0]}` into `{rewrite[1]}`, "
            f"but this apk is `{name}`. Change both strings or the build will not bump the version."
        )
        version_ok = False
    else:
        version_note = f"`apply_resources.py` rewrites `{rewrite[0]}` into `{rewrite[1]}`, which matches this apk."
        version_ok = True
    report = {
        "smali": str(args.smali),
        "decode": str(args.decode),
        "versionName": name,
        "versionCode": code,
        "catalogVersion": catalog["base"]["versionName"],
        "catalogTag": catalog["base"]["tag"],
        "versionNote": version_note,
        "resourceConflicts": conflicts,
        "hooks": rows,
    }
    text = render(report)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text, encoding="utf-8")
        print(f"wrote {args.out}")
    print(text)
    bad = [row for row in rows if row["status"] != "same"]
    if args.check and (bad or conflicts or not version_ok):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
