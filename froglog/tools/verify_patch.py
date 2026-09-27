"""Confirm the catalog patch adds Froglog and leaves the other picker path in place."""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

LABEL = re.compile(r":(cond|goto|pswitch_data|sswitch_data|array)_([0-9a-f]+)")


def disassemble(baksmali: str, dex: Path, out: Path, *classes: str) -> None:
    subprocess.check_call(
        ["java", "-jar", baksmali, "d", "--classes", ",".join(classes), "-o", str(out), str(dex)],
        stdout=subprocess.DEVNULL,
    )


def method(text: str, header: str) -> str:
    start = text.index(header)
    end = text.index(".end method", start)
    return text[start:end]


def shift_labels(line: str, delta: int) -> str:
    def repl(match: re.Match[str]) -> str:
        return f":{match.group(1)}_{int(match.group(2), 16) + delta:x}"

    return LABEL.sub(repl, line)


def instructions(body: str) -> list[str]:
    rows = []
    in_annotation = False
    for line in body.splitlines():
        stripped = line.strip()
        if stripped.startswith(".annotation"):
            in_annotation = True
            continue
        if stripped.startswith(".end annotation"):
            in_annotation = False
            continue
        if in_annotation or not stripped or stripped.startswith(".") or stripped.startswith(":") or stripped.startswith("#"):
            continue
        rows.append(stripped)
    return rows


def main() -> None:
    baksmali, original, patched = sys.argv[1:]
    classes = (
        "Lmf/y1;",
        "Lrip/moth/cocoonshell/data/local/GameSessionDao_Impl;",
        "Lxd/m0;",
        "Lrip/moth/cocoonshell/utils/u6;",
        "Lef/d0;",
        "Lef/q3;",
    )
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        disassemble(baksmali, Path(original), root / "before", *classes)
        disassemble(baksmali, Path(patched), root / "after", *classes)
        before = (root / "before" / "mf" / "y1.smali").read_text(encoding="utf-8")
        after = (root / "after" / "mf" / "y1.smali").read_text(encoding="utf-8")
        session_before = (root / "before" / "rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali").read_text(encoding="utf-8")
        session_after = (root / "after" / "rip/moth/cocoonshell/data/local/GameSessionDao_Impl.smali").read_text(encoding="utf-8")
        pods_before = (root / "before" / "xd" / "m0.smali").read_text(encoding="utf-8")
        pods_after = (root / "after" / "xd" / "m0.smali").read_text(encoding="utf-8")
        router_before = (root / "before" / "rip/moth/cocoonshell/utils/u6.smali").read_text(encoding="utf-8")
        router_after = (root / "after" / "rip/moth/cocoonshell/utils/u6.smali").read_text(encoding="utf-8")
        friends_before = (root / "before" / "ef" / "d0.smali").read_text(encoding="utf-8")
        friends_after = (root / "after" / "ef" / "d0.smali").read_text(encoding="utf-8")
        click_after = (root / "after" / "ef" / "q3.smali").read_text(encoding="utf-8")

    clinit_before = instructions(method(before, ".method static constructor <clinit>()V"))
    clinit_after = instructions(method(after, ".method static constructor <clinit>()V"))
    hook = "invoke-static {v0}, Lrip/moth/cocoonshell/froglog/CatalogHook;->withFroglog(Ljava/util/List;)Ljava/util/List;"
    move = "move-result-object v0"
    if hook not in clinit_after or clinit_after.count(hook) != 1:
        raise SystemExit("clinit is missing the Froglog catalog append")
    inserted = clinit_after.index(hook)
    if clinit_after[inserted + 1] != move:
        raise SystemExit("clinit did not keep the appended list")
    expected = clinit_before[:inserted] + [hook, move] + clinit_before[inserted:]
    if clinit_after != expected:
        raise SystemExit("clinit changed more than the Froglog append")
    if "sput-object v0, Lmf/y1;->f:Ljava/util/List;" not in clinit_after[inserted:]:
        raise SystemExit("catalog field is no longer stored")

    opened = method(after, ".method public static final h(Lz0/a1;Lz0/a1;Lz0/u0;Ljb/g;I)V")
    required = [
        "Lrip/moth/cocoonshell/froglog/CatalogHook;->maybeConfirm(ILjb/g;)Z",
        "Lmf/q;->PROVIDER:Lmf/q;",
        "invoke-static {p3, p0, p1, p2}, Lmf/y1;->f(Ljb/g;Lrip/moth/cocoonshell/data/model/Widget$WidgetType;Landroid/appwidget/AppWidgetProviderInfo;Ljava/util/List;)V",
        "Lrip/moth/cocoonshell/data/model/Widget$WidgetType;->ANDROID_WIDGET",
    ]
    for item in required:
        if item not in opened:
            raise SystemExit(f"picker method is missing {item}")
    if "Lrip/moth/cocoonshell/froglog/CatalogHook;->maybeConfirm" in method(
        after, ".method public static final f("
    ):
        raise SystemExit("widget confirm path was rewritten")

    insert_header = ".method public insert(Lrip/moth/cocoonshell/data/model/GameSession;Lxa/c;)Ljava/lang/Object;"
    insert_before = instructions(method(session_before, insert_header))
    insert_after = instructions(method(session_after, insert_header))
    hook_session = "Lrip/moth/cocoonshell/froglog/FroglogSessionBridge;->onInserted(Lrip/moth/cocoonshell/data/model/GameSession;)V"
    if not insert_after or hook_session not in insert_after[0]:
        raise SystemExit("session insert is missing the Froglog hook")
    if "{p1}" not in insert_after[0] and "{v4}" not in insert_after[0]:
        raise SystemExit("session hook does not receive the inserted session")
    if insert_after[1:] != insert_before:
        raise SystemExit("session insert changed more than the Froglog hook")
    all_header = ".method public insertAll(Ljava/util/List;Lxa/c;)Ljava/lang/Object;"
    if instructions(method(session_before, all_header)) != instructions(method(session_after, all_header)):
        raise SystemExit("insertAll was rewritten")
    if hook_session in method(session_after, all_header):
        raise SystemExit("insertAll calls the session hook")

    pod_before = instructions(method(pods_before, ".method static constructor <clinit>()V"))
    pod_after = instructions(method(pods_after, ".method static constructor <clinit>()V"))
    pod_hook = "invoke-static {v0}, Lrip/moth/cocoonshell/froglog/FroglogPods;->include(Ljava/util/List;)Ljava/util/List;"
    if pod_hook not in pod_after or pod_after.count(pod_hook) != 1:
        raise SystemExit("pod list is missing the Froglog append")
    pod_at = pod_after.index(pod_hook)
    if pod_after[pod_at + 1] != move:
        raise SystemExit("pod list did not keep the appended list")
    if pod_after != pod_before[:pod_at] + [pod_hook, move] + pod_before[pod_at:]:
        raise SystemExit("pod list changed more than the Froglog append")
    if "sput-object v0, Lxd/m0;->a:Ljava/util/List;" not in pod_after[pod_at:]:
        raise SystemExit("pod list field is no longer stored")

    router_header = ".method public final a(Lxd/l0;Landroid/content/Context;Ljava/lang/Boolean;Lrip/moth/cocoonshell/data/model/Game;Lza/c;)Ljava/lang/Object;"
    route_before = instructions(method(router_before, router_header))
    route_after = instructions(method(router_after, router_header))
    opened_pod = "Lrip/moth/cocoonshell/froglog/FroglogPods;->openIfFroglog(Lxd/l0;Landroid/content/Context;)Z"
    if not route_after or opened_pod not in route_after[0]:
        raise SystemExit("pod router is missing the Froglog open")
    if "{p1, p2}" not in route_after[0] and "{v13, v14}" not in route_after[0]:
        raise SystemExit("pod router does not receive the pod entry and context")
    expected_route = [
        route_after[0],
        "move-result v0",
        "if-eqz v0, :cond_9",
        "sget-object v0, Lta/z;->a:Lta/z;",
        "return-object v0",
    ]
    if route_after[:5] != expected_route:
        raise SystemExit("pod router prefix is not the Froglog early return")
    # Prepending 9 code units moves every original label. Relative offsets stay the same.
    shifted = [shift_labels(line, 9) for line in route_before]
    if route_after[5:] != shifted:
        raise SystemExit("pod router changed more than the Froglog open")

    friends_header = ".method public static final k0(Ljava/util/List;Ljava/util/Map;Z)Ljava/util/List;"
    friends_old = instructions(method(friends_before, friends_header))
    friends_new = instructions(method(friends_after, friends_header))
    follows = "invoke-static {v0}, Lrip/moth/cocoonshell/froglog/FroglogSocial;->withFollows(Ljava/util/List;)Ljava/util/List;"
    if friends_new != friends_old[:-1] + [follows, "move-result-object v0", "return-object v0"]:
        raise SystemExit("friend list changed more than the Froglog follows")

    click_header = ".method public final invoke(Ljava/lang/Object;)Ljava/lang/Object;"
    click = instructions(method(click_after, click_header))
    opened = "Lrip/moth/cocoonshell/froglog/FroglogSocial;->openIfFriend(Ljava/lang/Object;)Z"
    hits = [i for i, line in enumerate(click) if opened in line]
    if len(hits) != 1:
        raise SystemExit("friend click is missing the Froglog open")
    at = hits[0]
    if at == 0 or ("p1" not in click[at - 1] and "v25" not in click[at - 1]):
        raise SystemExit("friend click does not receive the friend")
    if not click[at + 1] == "move-result v0" or not click[at + 2].startswith("if-eqz v0, "):
        raise SystemExit("friend click does not branch around Steam")
    if click[at + 3] != "sget-object v0, Lta/z;->a:Lta/z;" or click[at + 4] != "return-object v0":
        raise SystemExit("friend click does not return after opening Froglog")
    if "check-cast v0, Lef/d6;" not in click or "invoke-virtual {v2}, Ljava/lang/Number;->longValue()J" not in click:
        raise SystemExit("steam friend click was removed")
    print("patch check ok")


if __name__ == "__main__":
    main()
