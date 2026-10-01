import json
import os
import sys
from pathlib import Path


def resource_dir():
    return Path(sys._MEIPASS) if getattr(sys, "frozen", False) else Path(__file__).resolve().parents[2]


def config_dir():
    path = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "YomiLens"
    path.mkdir(parents=True, exist_ok=True)
    return path


def load():
    try:
        result = json.loads((config_dir()/"settings.json").read_text(encoding="utf-8"))
        return result if isinstance(result, dict) else {}
    except (ValueError, OSError):
        return {}


def save(data):
    path = config_dir()/"settings.json"
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)
