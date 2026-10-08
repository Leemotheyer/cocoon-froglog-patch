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


def rel_to_type(rel: str) -> str:
    rest = rel[len("smali/"):] if rel.startswith("smali/") else rel.split("/", 1)[1]
    if rest.endswith(".smali"):
        rest = rest[:-6]
    return "L" + rest + ";"


def in_dex(rel: str, dex: str) -> bool:
    return rel.startswith(dex_dir(dex) + "/")


def combine(needles: list[str], hits: dict[str, list[str]], mode: str) -> set[str]:
    sets = [set(hits.get(needle, [])) for needle in needles]
    if not sets:
        return set()
    if mode == "any":
        return set.union(*sets)
    return set.intersection(*sets)


def parse_method(line: str) -> tuple[str, str, str, str] | None:
    stripped = line.strip()
    if not stripped.startswith(".method "):
        return None
    head, sep, tail = stripped.partition("(")
    if not sep or ")" not in tail:
        return None
    args, ret = tail.split(")", 1)
    name = head.split()[-1]
    return name, args, ret, stripped


def check_methods(text: str, specs: list) -> tuple[bool, list[str]]:
    parsed = []
    for line in text.splitlines():
        item = parse_method(line)
        if item:
            parsed.append(item)
    notes: list[str] = []
    ok = True
    for spec in specs:
        if isinstance(spec, str):
            if spec not in text:
                ok = False
                notes.append(f"Missing `{spec}`.")
            continue
        name = spec.get("name")
        descriptor = spec.get("descriptor")
        returns = spec.get("returns")
        matches = [item for item in parsed if item[0] == name]
        if descriptor:
            found = [item for item in matches if f"({item[1]}){item[2]}" == descriptor]
            if found:
                continue
            ok = False
            if matches:
                shown = ", ".join(f"`({item[1]}){item[2]}`" for item in matches[:3])
                notes.append(f"`{name}` descriptor changed. Catalog has `{descriptor}`. This file has {shown}.")
            else:
                notes.append(f"`{name}{descriptor}` is not in this file.")
        elif returns:
            found = [item for item in matches if item[2] == returns]
            if found:
                continue
            ok = False
            if matches:
                shown = ", ".join(f"`{item[2]}`" for item in matches[:3])
                notes.append(f"`{name}` no longer returns `{returns}` (now {shown}).")
            else:
                notes.append(f"No method `{name}` returning `{returns}`.")
    return ok, notes


def classify(hook: dict, hits: dict[str, list[str]], smali: Path, old_types: dict[str, str]) -> dict:
    expected = type_to_rel(hook["type"], hook["dex"])
    anchors = hook.get("anchors") or []
    fallbacks = hook.get("fallbackAnchors") or []
    excludes = hook.get("excludeAnchors") or []
    chosen = combine(anchors, hits, hook.get("match", "all"))
    if not chosen and fallbacks:
        chosen = combine(fallbacks, hits, hook.get("fallbackMatch", "all"))
    if hook.get("dexOnly"):
        chosen = {path for path in chosen if in_dex(path, hook["dex"])}
    if excludes:
        banned: set[str] = set()
        for anchor in excludes:
            banned |= set(hits.get(anchor, []))
        chosen -= banned
    exists = (smali / expected).is_file()
    resolved = None
    if expected in chosen:
        status = "same"
        resolved = expected
    elif len(chosen) == 1:
        status = "moved"
        resolved = next(iter(chosen))
    elif len(chosen) > 1:
        status = "ambiguous"
    elif exists:
        status = "string_drift"
        resolved = expected
    else:
        status = "missing"
    method_notes: list[str] = []
    specs = hook.get("methods") or []
    if resolved and specs:
        text = (smali / resolved).read_text(encoding="utf-8", errors="replace")
        methods_ok, method_notes = check_methods(text, specs)
        if not methods_ok and status == "same":
            status = "method_drift"
        elif not methods_ok and status == "string_drift":
            status = "method_drift"
    collisions = []
    if resolved and resolved != expected:
        new_type = rel_to_type(resolved)
        owner = old_types.get(new_type)
        if owner and owner != hook["id"]:
            collisions.append(f"`{new_type}` belonged to `{owner}` on the catalog base. That older type moved too.")
    return {
        "id": hook["id"],
        "status": status,
        "expected": expected,
        "resolved": resolved,
        "exists": exists,
        "candidates": sorted(chosen),
        "feature": hook.get("feature") or hook["id"],
        "edit": hook.get("edit") or [],
        "caveat": hook.get("caveat"),
        "methodNotes": method_notes,
        "collisions": collisions,
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
    if any(not row["ok"] for row in report["resources"]):
        lines.append("Move colliding Froglog ids in `apply_resources.py`. Drawable ids also have to match `FROGLOG_POD_ICON` and `FROGLOG_ICON` in `PatchCatalog.java`.")
        lines.append("")
    for row in report["resources"]:
        lines.append(f"- {row['text']}")
    lines.append("")
    if report["shared"]:
        lines.append("## Shared types")
        lines.append("")
        lines.append("These obfuscated types appear inside method descriptors. When one moves, every signature that names the old type has to move with it.")
        lines.append("")
        for row in report["shared"]:
            where = row["resolved"] or ", ".join(row["candidates"]) or "not found"
            lines.append(f"- `{row['id']}` `{row['status']}`: catalog `{row['expected']}`, now `{where}`")
            for note in row["methodNotes"]:
                lines.append(f"  - {note}")
            for note in row["collisions"]:
                lines.append(f"  - {note}")
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
        if row["resolved"] and row["status"] != "ambiguous":
            lines.append(f"Resolved class: `{row['resolved']}`")
            lines.append("")
        elif row["candidates"]:
            lines.append("Candidates:")
            lines.append("")
            for candidate in row["candidates"]:
                lines.append(f"- `{candidate}`")
            lines.append("")
        elif row["exists"]:
            lines.append("The old class file is still on disk, but it does not contain the fingerprint. The name was reused for something else, or the method line changed.")
            lines.append("")
        else:
            lines.append("No file contained the fingerprint.")
            lines.append("")
        for note in row["methodNotes"]:
            lines.append(f"- {note}")
        if row["methodNotes"]:
            lines.append("")
        for note in row["collisions"]:
            lines.append(f"- {note}")
        if row["collisions"]:
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
            "- [ ] `PatchCatalog` constants, `verify_patch.py`, stubs, and `Class.forName` strings match the resolved classes",
            "- [ ] Method descriptors in the notes above are updated, including the shared composer type",
            "- [ ] A resolved name that used to belong to a different hook is not copied onto the old stub",
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

    def add_needle(value: str) -> None:
        if value not in needles:
            needles.append(value)

    entries = list(catalog["hooks"]) + list(catalog.get("sharedTypes") or [])
    for hook in entries:
        for key in ("anchors", "fallbackAnchors", "excludeAnchors"):
            for anchor in hook.get(key) or []:
                add_needle(anchor)
    if not args.smali.is_dir():
        raise SystemExit(f"missing smali tree {args.smali} (run stage-new-base.sh or agent-bootstrap.sh)")
    hits = index_smali(args.smali, needles)
    old_types: dict[str, str] = {}
    for hook in catalog["hooks"]:
        old_types[hook["type"]] = hook["id"]
        for related in hook.get("relatedTypes") or []:
            old_types[related["type"]] = f"{hook['id']} ({related['role']})"
    rows = [classify(hook, hits, args.smali, old_types) for hook in catalog["hooks"]]
    shared = [classify(hook, hits, args.smali, old_types) for hook in catalog.get("sharedTypes") or []]
    code, name = ("?", "?")
    if args.decode.is_dir():
        code, name = apk_version(args.decode)
    maxima = public_max(args.decode) if args.decode.is_dir() else {}
    grouped: dict[str, list[int]] = {}
    for typ, _res_name, rid in froglog_ids():
        grouped.setdefault(typ, []).append(rid)
    resources = []
    conflicts = False
    for typ, ids in grouped.items():
        top = maxima.get(typ, 0)
        floor = min(ids)
        if top >= floor:
            conflicts = True
            resources.append({
                "ok": False,
                "text": (
                    f"**{typ}** collides. Froglog's {len(ids)} ids start at `0x{floor:08x}` "
                    f"and this base already uses `0x{top:08x}`. Start this type at `0x{top + 1:08x}`."
                ),
            })
        else:
            resources.append({
                "ok": True,
                "text": (
                    f"**{typ}** is free. Froglog starts at `0x{floor:08x}`; "
                    f"this base tops out at `0x{top:08x}`."
                ),
            })
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
        "resources": resources,
        "hooks": rows,
        "shared": shared,
    }
    text = render(report)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text, encoding="utf-8")
        print(f"wrote {args.out}")
    print(text)
    bad = [row for row in rows + shared if row["status"] != "same" or row["methodNotes"]]
    if args.check and (bad or conflicts or not version_ok):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
