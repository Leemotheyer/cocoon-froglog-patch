"""Confirm the Cocoon 3.11 patches add Froglog and leave the stock paths intact."""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

CLASSES = (
    "Lpf/x1;",
    "Lrip/moth/cocoonshell/data/local/GameSessionDao_Impl;",
    "Lyd/l0;",
    "Lrip/moth/cocoonshell/utils/p8;",
    "Lhf/j8;",
    "Lhf/b6;",
    "Lhg/x3;",
    "Lhf/b;",
    "Lof/n;",
    "Lqf/o0;",
    "Lff/fi;",
    "Lwf/q1;",
    "Lff/so;",
)


def disassemble(baksmali: str, dex: Path, out: Path) -> None:
    cmd = ["java", "-cp", baksmali, "org.jf.baksmali.Main"]
    args = ["d", "--classes", ",".join(CLASSES), "-o", str(out), str(dex)]
    if Path(baksmali).is_file() and baksmali.endswith("baksmali.jar"):
        try:
            subprocess.check_call(
                ["java", "-jar", baksmali] + args,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return
        except subprocess.CalledProcessError:
            pass
    subprocess.check_call(cmd + args, stdout=subprocess.DEVNULL)


def read(root: Path, relative: str) -> str:
    return (root / relative).read_text(encoding="utf-8")


def method(text: str, header: str) -> str:
    start = text.index(header)
    end = text.index(".end method", start)
    return text[start:end]


def need(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"{label} is missing {needle}")


def main() -> None:
    if len(sys.argv) < 4:
        raise SystemExit("usage: verify_patch.py <baksmali> <original.dex> <patched.dex>")
    baksmali, original, patched = sys.argv[1:4]
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        disassemble(baksmali, Path(original), root / "before")
        disassemble(baksmali, Path(patched), root / "after")

        catalog_before = read(root / "before", "pf/x1.smali")
        catalog_after = read(root / "after", "pf/x1.smali")
        clinit = method(catalog_after, ".method static constructor <clinit>()V")
        need(clinit, "Lrip/moth/cocoonshell/froglog/CatalogHook;->withFroglog", "catalog clinit")
        need(clinit, "sput-object v0, Lpf/x1;->f:Ljava/util/List;", "catalog field")
        picker = method(catalog_after, ".method public static final h(Ly0/b1;Ly0/b1;Ly0/v0;Ljb/g;I)V")
        need(picker, "CatalogHook;->maybeConfirm", "picker")
        need(picker, "Lpf/p;->PROVIDER:Lpf/p;", "picker provider")
        need(picker, "Lpf/x1;->f(Ljb/g;", "picker original path")
        if "CatalogHook;->maybeConfirm" in method(
            catalog_before, ".method public static final f(Ljb/g;"
        ):
            raise SystemExit("stock picker confirm path was rewritten")

        session_before = read(root / "before", "rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali")
        session_after = read(root / "after", "rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali")
        insert_header = ".method public insert(Lrip/moth/cocoonshell/data/model/GameSession;Lxa/c;)Ljava/lang/Object;"
        insert = method(session_after, insert_header)
        need(insert, "FroglogSessionBridge;->onInserted", "session insert")
        all_header = ".method public insertAll(Ljava/util/List;Lxa/c;)Ljava/lang/Object;"
        if method(session_before, all_header) != method(session_after, all_header):
            raise SystemExit("insertAll was rewritten")

        pods = read(root / "after", "yd/l0.smali")
        need(pods, "FroglogPods;->include", "pod list")
        need(pods, "sput-object v0, Lyd/l0;->a:Ljava/util/List;", "pod list field")

        router = read(root / "after", "rip/moth/cocoonshell/utils/p8.smali")
        route = method(router, ".method public final a(Lyd/k0;Landroid/content/Context;Ljava/lang/Boolean;Lrip/moth/cocoonshell/data/model/Game;Lza/c;)Ljava/lang/Object;")
        # The router gets the yd.k0 entry, not a yd.j0 action. Calling openIfAction here
        # passed a k0 where the j0 parameter is declared, and ART rejected p8 on the first
        # pod tap, so every pod crashed.
        need(route, "FroglogPods;->openIfFroglog", "pod router")
        if "FroglogPods;->openIfAction" in route:
            raise SystemExit("pod router passes a yd.k0 entry to openIfAction")
        # The caller casts the suspend result to oe.g; Unit would throw ClassCastException.
        need(route, "Loe/a;->a:Loe/a;", "pod router early return")
        if "Lsa/z;->a:Lsa/z;" in route:
            raise SystemExit("pod router returns Unit where oe.g is expected")

        friends = read(root / "after", "hf/j8.smali")
        need(friends, "FroglogSocial;->withFriendsTabs", "friend tabs")
        need(friends, "FroglogSocial;->listForTab", "friend panel")
        need(friends, "FroglogSocial;->tabIcon", "friend chips")
        if friends.find("Lqe/j;->m(Ljava/util/List;)Lua/b;") > friends.find("FroglogSocial;->withFriendsTabs"):
            raise SystemExit("friend tab hook is not after the frozen tab list")

        bar = read(root / "after", "hg/x3.smali")
        status = method(bar, ".method public static final k(ZLhf/q8;Lo1/p;Ly0/e0;I)V")
        need(status, "FroglogSocial;->withLiveCount", "status bar count")

        icons = read(root / "after", "hf/b.smali")
        need(icons, "FROGLOG:Lhf/b;", "icon enum")
        need(icons, "const/16 v2, 0x100", "icon ordinal")
        need(icons, "const/16 v0, 0x101", "icon values size")

        dispatch = read(root / "after", "of/n.smali")
        action = method(dispatch, ".method public static final h(Landroid/content/Context;Ljava/lang/String;Ljb/c;Ljb/e;Ljb/c;Ljb/a;)V")
        need(action, "FroglogMenu;->onAction", "menu dispatch")

        menu = read(root / "after", "qf/o0.smali")
        need(menu, ".method public static final c$froglog(", "context menu original")
        need(menu, "FroglogMenu;->withFroglog", "context menu wrapper")

        picnic = read(root / "after", "ff/fi.smali")
        info = method(picnic, ".method public static final Y(Lff/vd;Ljb/a;Ljava/lang/String;Lo1/p;Ly0/e0;I)V")
        need(info, "FroglogPicnic;->uploadAction", "picnic info dialog")
        need(info, 'const-string v13, "SELECT"', "picnic row key")
        row = method(picnic, ".method public static final W(Ljava/lang/String;Ljb/a;Lo1/p;Ly0/e0;I)V")
        need(row, "FroglogPicnic;->isUploadRow", "picnic row")
        need(row, 'const-string v13, "Upload to Froglog"', "picnic row label")
        # The icon branch replaces the stock const in v4. Writing v3 leaves v4 as the
        # label String and the following La/a.o0(II…) call fails verification.
        need(row, "const v4, 0x7f060273", "picnic upload icon register")
        need(row, "const v4, 0x7f060230", "picnic log icon register")
        if "const v3, 0x7f060273" in row or "const v3, 0x7f060230" in row:
            raise SystemExit("picnic upload icon writes the wrong register")

        click = read(root / "after", "hf/b6.smali")
        need(click, "FroglogSocial;->openIfFriend", "friend click")

        launch = read(root / "after", "ff/so.smali")
        launch_method = method(launch, ".method public final invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;")
        need(launch_method, "FroglogPods;->openIfAction", "pod launch fallback")
        need(launch_method, "La5/r0;", "pod launch original throw")
        # invokeSuspend has 13 registers and two parameter words (this plus the argument),
        # so p0 is v11 and the pod entry is read from this object. baksmali prints it as p0.
        need(launch_method, "iget-object v10, p0, Lff/so;->c", "pod launch this register")

        widget = read(root / "after", "wf/q1.smali")
        host = method(widget, ".method public static final f(Landroid/view/View;)V")
        need(host, "GlassCompat;->styleAndroidWidget", "widget host")

    print("patch check ok")


if __name__ == "__main__":
    main()
