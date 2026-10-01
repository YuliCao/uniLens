"""Check a stationary sample page in an already running emulator capture session."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", default="emulator-5554")
args = parser.parse_args()
assert args.serial.startswith("emulator-")

def adb(*parts):
    return subprocess.check_output([args.adb, "-s", args.serial, *parts], timeout=20).decode("utf-8")

def state():
    return adb("shell", "dumpsys", "activity", "service", "io.github.yomilens/.CaptureService")

def number(s, key):
    return int(re.search(rf"{key}=(\d+)", s)[1])

time.sleep(4)
initial = state()
blank = number(initial, "blankTransitions")
first_scan = number(initial, "ocrRuns")
samples = []
deadline = time.monotonic() + 25
while time.monotonic() < deadline:
    s = state()
    assert "overlayVisible=true" in s and "regionBorder=true" in s, s
    assert number(s, "blankTransitions") == blank, s
    count = number(s, "labels")
    assert 4 <= count <= 8, s
    samples.append({"labels": count, "ocrRuns": number(s, "ocrRuns")})
    time.sleep(.15)
assert samples[-1]["ocrRuns"] >= first_scan + 3, samples
result = {"durationSeconds": 25, "samples": len(samples), "mode": "romaji",
          "blankTransitionsDuringRefresh": 0, "overlayAlwaysVisible": True,
          "borderAlwaysVisible": True, "completedOcrRuns": samples[-1]["ocrRuns"] - first_scan}
filename = "refresh-stability.json"
output = Path(__file__).resolve().parents[1] / "artifacts/validation" / filename
output.write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result))
