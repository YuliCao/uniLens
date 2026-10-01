import json
import pytest
from yomilens_windows import ocr


def test_missing_models_fail_locally(tmp_path, monkeypatch):
    monkeypatch.setattr(ocr, "resource_dir", lambda: tmp_path)
    with pytest.raises(RuntimeError, match="缺少离线模型"):
        ocr.OcrEngine()


def test_corrupt_model_rejected_before_inference(tmp_path, monkeypatch):
    root = tmp_path/'models'
    root.mkdir()
    (root/'broken.onnx').write_bytes(b'not an ONNX model')
    (root/'manifest.json').write_text(json.dumps([dict(task='rec', filename='broken.onnx', sha256='0'*64)]))
    monkeypatch.setattr(ocr, "resource_dir", lambda: tmp_path)
    with pytest.raises(RuntimeError, match="缺失或损坏"):
        ocr.OcrEngine()
