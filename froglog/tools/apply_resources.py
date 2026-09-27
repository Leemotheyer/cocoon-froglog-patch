"""Copy Froglog resources into a clean apktool decode and register them.

Recently played strings and components are not rewritten.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "res"

IDS = [
    "froglog_root",
    "froglog_title",
    "froglog_subtitle",
    "froglog_message",
    "froglog_row",
    "froglog_slot0",
    "froglog_art0",
    "froglog_name0",
    "froglog_meta0",
    "froglog_slot1",
    "froglog_art1",
    "froglog_name1",
    "froglog_meta1",
    "froglog_slot2",
    "froglog_art2",
    "froglog_name2",
    "froglog_meta2",
    "froglog_slot3",
    "froglog_art3",
    "froglog_name3",
    "froglog_meta3",
    "froglog_filter",
    "froglog_stats_root",
    "froglog_stats_title",
    "froglog_stats_month",
    "froglog_stats_year",
    "froglog_stats_rate",
    "froglog_stats_message",
]

PUBLIC = [
    ("drawable", "froglog_widget_bg", 0x7F060216),
    ("drawable", "froglog_cover_placeholder", 0x7F060217),
    ("drawable", "froglog", 0x7F060218),
    ("layout", "froglog_widget", 0x7F0A001E),
    ("layout", "froglog_stats", 0x7F0A001F),
    ("string", "widget_type_froglog", 0x7F0E072D),
    ("string", "widget_type_froglog_desc", 0x7F0E072E),
    ("string", "widget_type_froglog_stats", 0x7F0E072F),
    ("string", "widget_type_froglog_stats_desc", 0x7F0E0730),
    ("string", "pods_overlay_froglog", 0x7F0E0731),
    ("xml", "froglog_widget_info", 0x7F110006),
    ("xml", "froglog_stats_info", 0x7F110007),
] + [("id", name, 0x7F0800CA + index) for index, name in enumerate(IDS)]

STRINGS = """
    <string name="widget_type_froglog">Froglog</string>
    <string name="widget_type_froglog_desc">Your recent Froglog games and play time</string>
    <string name="widget_type_froglog_stats">Froglog stats</string>
    <string name="widget_type_froglog_stats_desc">Hours and games finished this month and this year</string>
    <string name="pods_overlay_froglog">Froglog</string>
"""

STRINGS_FR = """
    <string name="widget_type_froglog">Froglog</string>
    <string name="widget_type_froglog_desc">Vos parties Froglog récentes et le temps de jeu</string>
    <string name="widget_type_froglog_stats">Stats Froglog</string>
    <string name="widget_type_froglog_stats_desc">Heures et jeux terminés ce mois-ci et cette année</string>
    <string name="pods_overlay_froglog">Froglog</string>
"""

MANIFEST = """
        <receiver android:exported="true" android:label="@string/widget_type_froglog" android:name="rip.moth.cocoonshell.froglog.FroglogRecentWidget">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/froglog_widget_info"/>
        </receiver>
        <receiver android:exported="true" android:label="@string/widget_type_froglog_stats" android:name="rip.moth.cocoonshell.froglog.FroglogStatsWidget">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/froglog_stats_info"/>
        </receiver>
        <activity android:exported="true" android:name="rip.moth.cocoonshell.froglog.FroglogWidgetConfig" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="true" android:name="rip.moth.cocoonshell.froglog.FroglogPodActivity" android:theme="@style/Theme.Cocoon"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogFriendActivity" android:theme="@style/Theme.Cocoon"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogGameDetail" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogSessionPrompt" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogMapActivity" android:theme="@style/Theme.Cocoon"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogAddGame" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <activity android:exported="false" android:name="rip.moth.cocoonshell.froglog.FroglogLibraryPicker" android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>
        <provider android:authorities="rip.moth.cocoonshell.froglog.init" android:exported="false" android:initOrder="100" android:name="rip.moth.cocoonshell.froglog.FroglogInitProvider"/>
"""


def insert_before(path: Path, marker: str, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if label in text:
        return
    if marker not in text:
        raise SystemExit(f"missing {marker!r} in {path}")
    path.write_text(text.replace(marker, snippet + marker, 1), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: apply_resources.py <decoded-apk-dir>")
    decoded = Path(sys.argv[1])
    for relative in (
        "layout/froglog_widget.xml",
        "layout/froglog_stats.xml",
        "xml/froglog_widget_info.xml",
        "xml/froglog_stats_info.xml",
        "drawable/froglog_widget_bg.xml",
        "drawable/froglog_cover_placeholder.xml",
        "drawable/froglog.xml",
    ):
        source = RES / relative
        target = decoded / "res" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    id_lines = "\n".join(f'    <item type="id" name="{name}" />' for name in IDS) + "\n"
    insert_before(decoded / "res/values/ids.xml", "</resources>", id_lines, "froglog_root")
    insert_before(decoded / "res/values/strings.xml", "</resources>", STRINGS, "widget_type_froglog")
    insert_before(decoded / "res/values-fr/strings.xml", "</resources>", STRINGS_FR, "widget_type_froglog")

    public_lines = "\n".join(
        f'    <public type="{typ}" name="{name}" id="{rid:#010x}" />' for typ, name, rid in PUBLIC
    ) + "\n"
    insert_before(decoded / "res/values/public.xml", "</resources>", public_lines, "froglog_widget_info")
    insert_before(decoded / "AndroidManifest.xml", "    </application>", MANIFEST, "FroglogRecentWidget")

    yml = decoded / "apktool.yml"
    text = yml.read_text(encoding="utf-8")
    text = text.replace("versionCode: 1\n", "versionCode: 8\n", 1)
    text = text.replace("versionName: 3.06-1\n", "versionName: 3.06-1-froglog7\n", 1)
    text = text.replace("- assets/dexopt/baseline.prof\n", "")
    text = text.replace("- assets/dexopt/baseline.profm\n", "")
    if "versionCode: 8\n" not in text or "versionName: 3.06-1-froglog7\n" not in text:
        raise SystemExit("version was not bumped")
    yml.write_text(text, encoding="utf-8")
    for profile in ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"):
        path = decoded / profile
        if path.exists():
            path.unlink()

    now_playing = (decoded / "res/values/strings.xml").read_text(encoding="utf-8")
    if "<string name=\"widget_type_now_playing\">Recently played</string>" not in now_playing:
        raise SystemExit("Recently played string was not left intact")
    print("resources applied")


if __name__ == "__main__":
    main()
