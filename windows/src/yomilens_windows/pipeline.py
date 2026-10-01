from dataclasses import dataclass
import threading
import time
import numpy as np
from PySide6.QtCore import QThread, Signal
from .geometry import Region


@dataclass(frozen=True)
class Request:
    generation: int
    region: Region
    overrides: str
    kana_only: bool = True


class AnalysisWorker(QThread):
    ready = Signal()
    result = Signal(int, object, float, bool)
    changed = Signal(int)
    error = Signal(int, str)

    def __init__(self):
        super().__init__()
        self.condition = threading.Condition()
        self.pending = None
        self.stopping = False

    def submit(self, request):
        with self.condition:
            self.pending = request  # One pending latest request, never an unbounded queue.
            self.condition.notify()

    def cancel_pending(self):
        with self.condition:
            self.pending = None

    def stop(self):
        with self.condition:
            self.stopping = True
            self.pending = None
            self.condition.notify()

    def run(self):
        generation = -1
        try:
            import mss
            from .ocr import OcrEngine
            from .reading import ReadingEngine, JAPANESE
            engine, reader = OcrEngine(), ReadingEngine()
            previous, old_key, old_overrides, cached = None, None, None, []
            last_ocr = 0.0
            self.ready.emit()
            with mss.MSS() as capture:
                while True:
                    with self.condition:
                        self.condition.wait_for(lambda: self.stopping or self.pending is not None)
                        if self.stopping:
                            return
                        request, self.pending = self.pending, None
                    generation = request.generation
                    started = time.perf_counter()
                    try:
                        if request.overrides != old_overrides:
                            reader.set_overrides(request.overrides)
                            old_overrides = request.overrides
                            previous = None
                        frame = np.array(capture.grab(request.region.capture_dict()))[:, :, :3].copy()
                        # Compare a fixed downsample; exact region/session changes always invalidate.
                        sample = frame[::4, ::4].astype(np.int16)
                        key = (request.region, generation, request.kana_only)
                        delta = 255.0 if previous is None or old_key != key or previous.shape != sample.shape else float(np.abs(sample-previous).mean())
                        unchanged = delta < 0.35 and started-last_ocr < 3
                        if unchanged:
                            self.result.emit(generation, cached, time.perf_counter()-started, True)
                            continue
                        if delta >= 0.35:
                            self.changed.emit(generation)
                        lines = engine.recognize(frame)
                        results = [(line, reader.read(line.text)) for line in lines if JAPANESE.search(line.text)
                                   and (not request.kana_only or any('ぁ' <= c <= 'ヿ' or '\uff66' <= c <= '\uff9f' for c in line.text))]
                        previous, old_key, cached, last_ocr = sample, key, results, started
                        # Drop results after stop; UI additionally checks generation.
                        with self.condition:
                            if self.stopping:
                                return
                        self.result.emit(generation, results, time.perf_counter()-started, False)
                    except Exception as exc:
                        previous = None
                        self.error.emit(generation, f"{type(exc).__name__}: {exc}")
        except Exception as exc:
            self.error.emit(generation, f"初始化失败：{type(exc).__name__}: {exc}")
