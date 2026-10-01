"""Development-time download only; runtime uses explicit local model paths."""
from pathlib import Path
import hashlib
import json
import urllib.request
import rapidocr
import yaml

root = Path(__file__).resolve().parents[1] / "models"
root.mkdir(exist_ok=True)
registry = yaml.safe_load((Path(rapidocr.__file__).parent / "default_models.yaml").read_text(encoding="utf-8"))["onnxruntime"]["PP-OCRv4"]
manifest_path = root / "manifest.json"
if manifest_path.exists():
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
else:
    manifest = []
    for task, key in [("det", "ch_PP-OCRv4_det_mobile"), ("cls", "ch_ppocr_mobile_v2.0_cls_mobile"), ("rec", "japan_PP-OCRv4_rec_mobile")]:
        # Classifier registry naming is discovered explicitly, with one candidate required.
        if task == "cls" and key not in registry[task]:
            candidates = list(registry[task])
            if len(candidates) != 1:
                raise ValueError(f"Choose a classifier explicitly: {candidates}")
            key = candidates[0]
        info = registry[task][key]
        manifest.append(dict(task=task, filename=Path(info["model_dir"]).name,
                             url=info["model_dir"], sha256=info["SHA256"], license="Apache-2.0 (PaddleOCR / RapidOCR)"))
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
for info in manifest:
    path = root / info["filename"]
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != info["sha256"]:
        print(f"Downloading {path.name}", flush=True)
        tmp = path.with_suffix(".partial")
        with urllib.request.urlopen(info["url"], timeout=60) as response, tmp.open("wb") as out:
            while data := response.read(1024 * 1024):
                out.write(data)
        if hashlib.sha256(tmp.read_bytes()).hexdigest() != info["sha256"]:
            raise ValueError(f"Checksum mismatch: {path.name}")
        tmp.replace(path)
    info["size_bytes"] = path.stat().st_size
    print(f"Verified {path.name}: {info['size_bytes']} bytes", flush=True)
manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
