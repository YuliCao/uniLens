"""Reproducible synthetic Japanese OCR evidence; never captures user text."""
import json
import time
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from yomilens_windows.ocr import OcrEngine
from yomilens_windows.reading import ReadingEngine

out = Path(__file__).resolve().parents[2]/"artifacts/windows/validation"
out.mkdir(parents=True, exist_ok=True)
img = Image.new("RGB", (1000, 360), "white")
draw = ImageDraw.Draw(img)
font = ImageFont.truetype("C:/Windows/Fonts/YuGothM.ttc", 38)
sentences = ["私は東京へ行きます。", "新しい仲間が加わった！", "「日本語」を読んでください。"]
for i, sentence in enumerate(sentences):
    draw.text((36, 35+i*95), sentence, fill="black", font=font)
img.save(out/"ocr-sample.png")
t = time.perf_counter()
engine, reader = OcrEngine(), ReadingEngine()
startup = time.perf_counter()-t
times = []
for _ in range(3):
    t = time.perf_counter()
    lines = engine.recognize(img)
    times.append(time.perf_counter()-t)
result = dict(startup_s=startup, inference_s=times, expected=sentences,
              lines=[dict(text=l.text, box=l.box, score=l.score, **{k:v for k,v in reader.read(l.text).__dict__.items() if k != "original"}) for l in lines])
(out/"ocr-probe.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
assert all(any(s == line.text for line in lines) for s in sentences), "Synthetic OCR mismatch: see evidence"
