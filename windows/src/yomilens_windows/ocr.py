from dataclasses import dataclass
import hashlib
import json
from .settings import resource_dir


@dataclass
class OcrLine:
    text: str
    box: list
    score: float


class OcrEngine:
    def __init__(self):
        from rapidocr import RapidOCR, LangRec, ModelType, OCRVersion
        root = resource_dir()/"models"
        if not (root/"manifest.json").exists():
            raise RuntimeError("缺少离线模型，请重新解压完整发行包；源码开发请运行 scripts/prepare_models.py")
        manifest = json.loads((root/"manifest.json").read_text(encoding="utf-8"))
        params = {"Rec.lang_type": LangRec.JAPAN, "Global.log_level": "error",
                  "EngineConfig.onnxruntime.intra_op_num_threads": 2,
                  "EngineConfig.onnxruntime.inter_op_num_threads": 1,
                  "Det.limit_type": "max", "Det.limit_side_len": 1280}
        for item in manifest:
            path = root/item["filename"]
            if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
                raise RuntimeError(f"离线模型缺失或损坏：{path.name}，请重新解压完整发行包")
            prefix = item["task"].capitalize()
            params[f"{prefix}.model_path"] = str(path)
            params[f"{prefix}.model_type"] = ModelType.MOBILE
            params[f"{prefix}.ocr_version"] = OCRVersion.PPOCRV4
        # Prevent dependency fallback downloads even when metadata is malformed.
        from rapidocr.utils.download_file import DownloadFile
        def forbidden_download(*args, **kwargs):
            raise RuntimeError("运行时禁止下载模型；请重新准备完整离线模型")
        DownloadFile.run = staticmethod(forbidden_download)
        self.engine = RapidOCR(params=params)

    def recognize(self, image):
        result = self.engine(image)
        if result.boxes is None:
            return []
        return [OcrLine(text, box.tolist(), float(score)) for text, box, score in zip(result.txts, result.boxes, result.scores)]
