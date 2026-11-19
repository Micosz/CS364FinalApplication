"""Device smoke test: submit a word, force-stop, reopen and compare persisted state.

Use a development emulator. This changes only the installed Wordward test round.
The script never changes the device clock or clears app storage.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "th.ac.tu.cs364.wordward.starter"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=str(Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools/adb"))
    parser.add_argument("--output", type=Path, default=Path("build/force-stop-evidence"))
    parser.add_argument("--restart-won-round", action="store_true",
                        help="Confirm a restart through the UI if this development round is won")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command, binary=False):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], text=not binary)

    def screen():
        adb("shell", "uiautomator", "dump", "/sdcard/wordward-force-stop.xml")
        return ET.fromstring(adb("exec-out", "cat", "/sdcard/wordward-force-stop.xml"))

    def preferences():
        xml = adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/daily_game_v1.xml")
        root = ET.fromstring(xml)
        return {node.attrib["name"]: node.text for node in root if node.tag == "string"}

    def tap(node):
        left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/th.ac.tu.cs364.wordward.GameActivity")
    ui = screen()
    field = next(node for node in ui.iter("node") if node.get("resource-id") == PACKAGE + ":id/input")
    if field.get("enabled") != "true":
        if not args.restart_won_round:
            raise RuntimeError("Round already won; restart it or explicitly pass --restart-won-round")
        tap(next(n for n in ui.iter("node") if n.get("resource-id") == PACKAGE + ":id/menu_button"))
        ui = screen()
        tap(next(n for n in ui.iter("node")
                 if n.get("text") in {"Restart today's round", "เริ่มรอบวันนี้ใหม่"}))
        ui = screen()
        assert any(n.get("text") in {"Restart today's round?", "เริ่มรอบวันนี้ใหม่?"}
                   for n in ui.iter("node"))
        tap(next(n for n in ui.iter("node") if n.get("resource-id") == "android:id/button1"))
        ui = screen()
        field = next(n for n in ui.iter("node") if n.get("resource-id") == PACKAGE + ":id/input")
        assert field.get("enabled") == "true"
    tap(field)
    adb("shell", "input", "text", "pencil")
    adb("shell", "input", "keyevent", "66")

    before = None
    for _ in range(30):
        try:
            current = preferences()
            saved = json.loads(current["round"])
            if any(row[0] == "pencil" for row in saved["entries"]):
                before = current
                break
        except (KeyError, json.JSONDecodeError, subprocess.CalledProcessError):
            pass
        time.sleep(0.1)
    if before is None:
        raise AssertionError("The UI guess was not persisted before force-stop")

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/th.ac.tu.cs364.wordward.GameActivity")
    ui = screen()
    after = preferences()
    assert before["round"] == after["round"], "Round changed after force-stop/reopen"
    assert before.get("theme") == after.get("theme"), "Theme changed"
    assert before.get("language") == after.get("language"), "Language changed"
    assert any(node.get("text") == "pencil" for node in ui.iter("node")), "Saved word missing from UI"
    screenshot = args.output / "force-stop-restored.png"
    screenshot.write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    report = {
        "serial": args.serial,
        "android_api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "result": "PASS",
        "saved_entries": len(json.loads(after["round"])["entries"]),
        "round_unchanged": True,
        "language": after.get("language"),
        "theme": after.get("theme"),
        "screenshot": screenshot.name,
    }
    (args.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
