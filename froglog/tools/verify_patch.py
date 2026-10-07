"""Confirm the catalog patch adds Froglog and leaves the other picker path in place."""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

LABEL = re.compile(r":(cond|goto|pswitch_data|sswitch_data|pswitch|sswitch|array)_([0-9a-f]+)")


def disassemble(baksmali: str, dex: Path, out: Path, *classes: str) -> None:
    cmd = ["java", "-cp", baksmali, "org.jf.baksmali.Main"]
    if Path(baksmali).is_file() and baksmali.endswith("baksmali.jar"):
        try:
            subprocess.check_call(
                ["java", "-jar", baksmali, "d", "--classes", ",".join(classes), "-o", str(out), str(dex)],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return
        except subprocess.CalledProcessError:
            pass
    subprocess.check_call(
        cmd + ["d", "--classes", ",".join(classes), "-o", str(out), str(dex)],
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
    baksmali, original, patched = sys.argv[1:4]
    menu_dex = sys.argv[4:6]
    classes = (
        "Lmf/y1;",
        "Lrip/moth/cocoonshell/data/local/GameSessionDao_Impl;",
        "Lxd/m0;",
        "Lrip/moth/cocoonshell/utils/u6;",
        "Lef/d0;",
        "Lef/q3;",
        "Ltf/i1;",
        "Lef/b;",
        "Lkf/n2;",
        "Llf/k;",
        "Lcf/pi;",
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
        widget_after = (root / "after" / "tf" / "i1.smali").read_text(encoding="utf-8")
        icons_before = (root / "before" / "ef" / "b.smali").read_text(encoding="utf-8")
        icons_after = (root / "after" / "ef" / "b.smali").read_text(encoding="utf-8")
        dispatch = {
            "Z0": (root / "before" / "kf" / "n2.smali", root / "after" / "kf" / "n2.smali"),
            "g": (root / "before" / "lf" / "k.smali", root / "after" / "lf" / "k.smali"),
        }
        dispatch = {name: (b.read_text(encoding="utf-8"), a.read_text(encoding="utf-8")) for name, (b, a) in dispatch.items()}
        picnic_before = (root / "before" / "cf" / "pi.smali").read_text(encoding="utf-8")
        picnic_after = (root / "after" / "cf" / "pi.smali").read_text(encoding="utf-8")
        menu_text = None
        if len(menu_dex) == 2:
            disassemble(baksmali, Path(menu_dex[1]), root / "menu", "La8/z;")
            menu_text = (root / "menu" / "a8" / "z.smali").read_text(encoding="utf-8")

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
    if friends_new != friends_old:
        raise SystemExit("Steam friend list should stay Steam-only")
    if "withFollows" in friends_after:
        raise SystemExit("Froglog follows were merged into the Steam list")
    tabs = "Lrip/moth/cocoonshell/froglog/FroglogSocial;->withFriendsTabs(Ljava/util/List;)Ljava/util/List;"
    if tabs not in friends_after:
        raise SystemExit("friend tabs are missing the Froglog tab")
    freeze = "Lr3/a;->k(Ljava/util/List;)Lva/b;"
    if freeze not in friends_after or friends_after.find(tabs) < friends_after.find(freeze):
        raise SystemExit("friend tabs hook is not after the tab list freeze")
    panel = "Lrip/moth/cocoonshell/froglog/FroglogSocial;->listForTab(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;"
    if panel not in friends_after:
        raise SystemExit("friend panel is missing the Froglog tab list")

    chip_icon = "Lrip/moth/cocoonshell/froglog/FroglogSocial;->tabIcon(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
    chips = instructions(method(friends_after, ".method public static final s(Ljava/util/List;Lef/w0;Leg/l0;Ljb/c;Lz0/e0;I)V"))
    hits = [i for i, line in enumerate(chips) if chip_icon in line]
    if len(hits) != 1:
        raise SystemExit("friend chips are missing the Froglog icon")
    at = hits[0]
    if not chips[at - 1].startswith("sget-object v12, Lef/b;->STEAM") or chips[at + 1] != "move-result-object v12" \
            or chips[at + 2] != "check-cast v12, Lef/b;" or not chips[at + 3].startswith("const/16 v14, 0x3e8"):
        raise SystemExit("friend chip icon hook is not where both icon branches meet")

    icon_field = ".field public static final enum FROGLOG:Lef/b;"
    if icon_field not in icons_after or icon_field in icons_before:
        raise SystemExit("icon enum is missing FROGLOG")
    icon_init = instructions(method(icons_after, ".method static constructor <clinit>()V"))
    expected_icon = [
        "new-instance v0, Lef/b;",
        'const-string v1, "FROGLOG"',
        "const/16 v2, 0xd3",
        "const v3, 0x7f06021a",
        "const/4 v4, 0x0",
        "const/4 v5, 0x0",
        "const/16 v6, 0x1c",
        "invoke-direct/range {v0 .. v6}, Lef/b;-><init>(Ljava/lang/String;IILw1/v;Lw1/v;I)V",
        "sput-object v0, Lef/b;->FROGLOG:Lef/b;",
        "invoke-static {}, Lef/b;->a()[Lef/b;",
    ]
    start = icon_init.index(expected_icon[1]) - 1
    if icon_init[start:start + len(expected_icon)] != expected_icon:
        raise SystemExit("icon enum does not build FROGLOG before values")
    icon_values = instructions(method(icons_after, ".method public static final synthetic a()[Lef/b;"))
    if icon_values[0] != "const/16 v0, 0xd4" or icon_values[-4:] != [
        "sget-object v1, Lef/b;->FROGLOG:Lef/b;",
        "const/16 v2, 0xd3",
        "aput-object v1, v0, v2",
        "return-object v0",
    ]:
        raise SystemExit("icon values do not include FROGLOG")

    on_action = "invoke-static/range {p0 .. p1}, Lrip/moth/cocoonshell/froglog/FroglogMenu;->onAction(Landroid/content/Context;Ljava/lang/String;)Z"
    for name, (text_before, text_after) in dispatch.items():
        header = f".method public static final {name}(Landroid/content/Context;Ljava/lang/String;Ljb/c;Ljb/e;Ljb/c;Ljb/a;)V"
        body_before = instructions(method(text_before, header))
        body_after = instructions(method(text_after, header))
        if body_after[:2] != [on_action, "move-result v0"] or not body_after[2].startswith("if-eqz v0, ") \
                or body_after[3:5] != ["return-void", "nop"]:
            raise SystemExit(f"menu dispatch {name} is missing the Froglog action prefix")
        if [shift_labels(line, 8) for line in body_before] != body_after[5:]:
            raise SystemExit(f"menu dispatch {name} changed more than the Froglog prefix")
    share_before = instructions(method(picnic_before, ".method public static final Z0(Lc/j;Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;)V"))
    share_after = instructions(method(picnic_after, ".method public static final Z0(Lc/j;Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;)V"))
    if share_before != share_after:
        raise SystemExit("picnic share chooser should stay unmodified")
    info = instructions(method(picnic_after, ".method public static final Y(Lcf/pd;Ljb/a;Ljava/lang/String;Lp1/o;Lz0/e0;I)V"))
    y_body = method(picnic_after, ".method public static final Y(Lcf/pd;Ljb/a;Ljava/lang/String;Lp1/o;Lz0/e0;I)V")
    info_text = "\n".join(info)
    if ".registers 49" not in y_body or "move-object/from16 v3, p0" not in y_body:
        raise SystemExit("picnic info dialog register frame changed")
    if "invoke-static {v13, v14}, Lrip/moth/cocoonshell/froglog/FroglogPicnic;->uploadAction" not in info_text:
        raise SystemExit("picnic info dialog is missing the Froglog upload row")
    if "invoke-static {v4, v14, v15, v12, v1}, Lcf/pi;->W(" not in info_text:
        raise SystemExit("picnic info dialog is missing the Froglog upload W row")
    row = instructions(method(picnic_after, ".method public static final W(Ljava/lang/String;Ljb/a;Lp1/o;Lz0/e0;I)V"))
    row_text = "\n".join(row)
    if "invoke-virtual {v0, v4}, Ljava/lang/String;->equals" in row_text:
        raise SystemExit("picnic info row compares the flags register as a string")
    if "move-object/from16 v4, p0" not in row_text or 'const-string v13, "Upload to Froglog"' not in row_text:
        raise SystemExit("picnic info row is missing the Froglog upload label")
    if menu_text is not None:
        wrapper = instructions(method(menu_text, ".method public static final E(Landroid/content/Context;Lnf/d0;Lde/o;ZZZZ)Ljava/util/List;"))
        if wrapper != [
            "invoke-static/range {p0 .. p6}, La8/z;->E$froglog(Landroid/content/Context;Lnf/d0;Lde/o;ZZZZ)Ljava/util/List;",
            "move-result-object v0",
            "invoke-static {v0, p1, p2}, Lrip/moth/cocoonshell/froglog/FroglogMenu;->withFroglog(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "move-result-object v0",
            "return-object v0",
        ]:
            raise SystemExit("context menu builder does not pass through FroglogMenu")
        if ".method public static final E$froglog(" not in menu_text:
            raise SystemExit("original context menu builder is missing")

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

    android_widget = method(
        widget_after,
        ".method public static final a(Lrip/moth/cocoonshell/data/model/Widget;FFZLp1/o;Lz0/e0;I)V",
    )
    if "Lwf/d2;->f(Lp1/o;Le0/e;Lz0/e0;)Lp1/o;" in android_widget:
        raise SystemExit("android widgets still use Compose drop shadows")
    host_view = method(widget_after, ".method public static final f(Landroid/view/View;)V")
    if "Lrip/moth/cocoonshell/froglog/GlassCompat;->styleAndroidWidget(Landroid/view/View;)V" not in host_view:
        raise SystemExit("android widget host does not keep a stable tile face")
    print("patch check ok")


if __name__ == "__main__":
    main()
