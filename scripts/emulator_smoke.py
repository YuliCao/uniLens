"""UI smoke checks against an explicitly selected emulator (never a physical phone).

Usage: python scripts/emulator_smoke.py --adb path/to/adb.exe --serial emulator-5554
The test resets this development app's emulator data and grants its test permissions.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--keep-running", action="store_true")
parser.add_argument("--rotation-cycles", type=int, default=3)
parser.add_argument("--external-fixture", action="store_true")
parser.add_argument("--external-only", action="store_true",
                    help="Test the external fixture against an already running emulator session")
args = parser.parse_args()
if args.external_only:
    args.external_fixture = True
if not args.serial.startswith("emulator-"):
    raise SystemExit("This smoke test is restricted to emulator serials.")
root = Path(__file__).resolve().parents[1]
output = root / "artifacts" / "validation"
output.mkdir(parents=True, exist_ok=True)
events = []


def adb(*parts):
    return subprocess.check_output(
        [args.adb, "-s", args.serial, *parts], stderr=subprocess.STDOUT,
        timeout=40,
    ).decode("utf-8", errors="replace")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/yomilens-test.xml")
    xml = adb("shell", "cat", "/sdcard/yomilens-test.xml")
    return list(ET.fromstring(xml).iter("node"))


def find(label, tap=False):
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        for node in nodes():
            if node.attrib.get("text") == label:
                if tap:
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
                    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                events.append({"label": label, "action": "tap" if tap else "found"})
                return
        time.sleep(.5)
    raise AssertionError(f"UI label not found: {label}")


def control(index):
    state = adb("shell", "dumpsys", "activity", "service", "io.github.yomilens/.CaptureService")
    match = re.search(rf"control={index},(\d+),(\d+)", state)
    assert match, state
    adb("shell", "input", "tap", *match.groups())
    time.sleep(.3)
    events.append({"controlTapped": index})


def screenshot(name, minimum=4, region=None, strict=False):
    expected = []
    for node in nodes():
        if re.search(r"[ぁ-ヿ]", node.attrib.get("text", "")):
            box = tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
            if region is None or region[1] <= (box[1]+box[3])/2 <= region[3]:
                expected.append(box)
    assert len(expected) >= minimum, expected
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        state=adb("shell","dumpsys","activity","service","io.github.yomilens/.CaptureService")
        boxes = [tuple(map(float, match)) for match in re.findall(
            r"box=([\d.]+),([\d.]+),([\d.]+),([\d.]+)", state)]
        aligned = all(any(l <= (x1+x2)/2 <= r and t <= (y1+y2)/2 <= b
                          for x1,y1,x2,y2 in boxes) for l,t,r,b in expected)
        if strict and boxes and not aligned:
            raise AssertionError("Visible stale/misaligned rotation result: " + state)
        if aligned and "overlayVisible=true" in state:
            events.append({"geometry": name, "matchedJapaneseLines": len(expected)})
            break
        time.sleep(.25)
    else:
        raise AssertionError("OCR boxes do not match Japanese text views: "+state)
    adb("shell", "screencap", "-p", f"/sdcard/{name}.png")
    adb("pull", f"/sdcard/{name}.png", str(output / f"{name}.png"))
    print(f"PASS geometry: {name}, {len(expected)} Japanese lines", flush=True)


if not args.external_only:
    adb("shell", "am", "force-stop", "io.github.yomilens")
    adb("shell", "pm", "clear", "io.github.yomilens")
    adb("shell", "appops", "set", "io.github.yomilens", "SYSTEM_ALERT_WINDOW", "allow")
    adb("shell", "pm", "grant", "io.github.yomilens", "android.permission.POST_NOTIFICATIONS")
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    adb("shell", "am", "start", "-W", "-n", "io.github.yomilens/.MainActivity")
    find("② 开始屏幕辅助", tap=True)
    find("Start", tap=True)
    find("打开识别测试页")
    time.sleep(1)
    find("打开识别测试页", tap=True)
    time.sleep(2)
    state = adb("shell", "dumpsys", "activity", "service", "io.github.yomilens/.CaptureService")
    assert "selecting=true" in state, "Default region selector did not open: " + state
    adb("shell", "input", "swipe", "10", "80", "1070", "1850", "700")
    find("日本語を勉強します。")
    time.sleep(7)
    screenshot("portrait")
    adb("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(5)
    screenshot("landscape", strict=True)
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(5)
    screenshot("portrait-after-rotation", strict=True)
    for cycle in range(args.rotation_cycles - 1):
        for rotation, name in [(1, "landscape"), (0, "portrait")]:
            adb("shell", "settings", "put", "system", "user_rotation", str(rotation))
            time.sleep(2)
            screenshot(f"rotation-{cycle+2}-{name}", strict=True)
    control(0)
    state=adb("shell","dumpsys","activity","service","io.github.yomilens/.CaptureService")
    assert "paused=true" in state and "labels=0" in state, state
    control(0)
    screenshot("resumed")
    for name in ["kana", "original", "romaji"]:
        control(1)
        screenshot("mode-"+name)
    control(2)
    adb("shell", "input", "swipe", "20", "1050", "1060", "1800", "700")
    time.sleep(2)
    screenshot("region-bottom", minimum=1, region=(20,1050,1060,1800))
    control(2)
    adb("shell", "input", "swipe", "10", "80", "1070", "1850", "700")
    time.sleep(2)
    screenshot("region-full")
    find("下一页 · 验证点击穿透", tap=True)
    find("冒険を始めましょう。")
    time.sleep(3)
    screenshot("page-two")
    services = adb("shell", "dumpsys", "activity", "services", "io.github.yomilens")
    assert "isForeground=true" in services, services
    events.append({"foregroundService": True, "touchPassThrough": True})
if args.external_fixture:
    adb("shell", "am", "force-stop", "io.github.yomilens.test")
    adb("shell", "am", "start", "-W", "-n",
        "io.github.yomilens.test/io.github.yomilens.ExternalFixtureActivity")
    find("日本語を勉強します。")
    screenshot("external-app")
    find("下一页 · 外部应用", tap=True)
    find("冒険を始めましょう。")
    screenshot("external-app-next")
    events.append({"crossPackageCapture": True, "crossPackageTouchPassThrough": True})
if not args.keep_running:
    if args.external_fixture:
        adb("shell", "am", "start", "-W", "-f", "0x04000000", "-n", "io.github.yomilens/.MainActivity")
    else:
        find("返回设置", tap=True)
    find("停止辅助", tap=True)
    time.sleep(1)
    projection = adb("shell", "dumpsys", "media_projection")
    assert "io.github.yomilens" not in projection, projection
    events.append({"projectionReleased": True})
crashes = adb("logcat", "-b", "crash", "-d")
(output / "crashes.txt").write_text(crashes, encoding="utf-8")
assert "Process: io.github.yomilens" not in crashes, crashes
(output / "smoke.json").write_text(json.dumps(events, ensure_ascii=False, indent=2), encoding="utf-8")
print("PASS: cross-app geometry, touch-through, and lifecycle checks." if args.external_only
      else "PASS: foreground session, page changes through overlay, rotation, and lifecycle checks.")
print("Screenshots require visual review for text alignment.")
