"""Tests only a generated sample window: no desktop content is persisted."""
import ctypes
from ctypes import wintypes as w
import json
from pathlib import Path
import sys
import numpy as np
import mss
from PySide6.QtCore import Qt, QTimer, QPoint
from PySide6.QtWidgets import QApplication, QWidget, QLabel, QVBoxLayout
from PySide6.QtGui import QPainter, QColor
from yomilens_windows.native import exclude_capture, click_through, u

class Overlay(QWidget):
    def __init__(self):
        super().__init__(None, Qt.Tool | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.WindowTransparentForInput | Qt.WindowDoesNotAcceptFocus)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setAttribute(Qt.WA_ShowWithoutActivating)
        self.paint_on = False
    def paintEvent(self, event):
        p = QPainter(self)
        if self.paint_on:
            p.fillRect(self.rect(), QColor("#ff00ff"))
        p.end()

app = QApplication([])
base = QWidget(None, Qt.WindowStaysOnTopHint)
base.setWindowTitle("YomiLens · 自动验证（稍后关闭）")
base.setStyleSheet("background: white; color: black;")
layout = QVBoxLayout(base)
label = QLabel("私は東京へ行きます。\n透明层下的日语应完整保留。")
label.setStyleSheet("font-size: 28px; padding: 24px")
layout.addWidget(label)
base.resize(620, 210)
base.show()
overlay = Overlay()
state = {}
u.GetWindowRect.argtypes = [w.HWND, ctypes.POINTER(w.RECT)]
u.GetForegroundWindow.restype = w.HWND
u.WindowFromPoint.argtypes = [w.POINT]
u.WindowFromPoint.restype = w.HWND

def capture():
    rect = w.RECT()
    u.GetWindowRect(int(overlay.winId()), ctypes.byref(rect))
    with mss.MSS() as grab:
        return np.array(grab.grab(dict(left=rect.left, top=rect.top, width=rect.right-rect.left, height=rect.bottom-rect.top)))

def place():
    position = label.mapToGlobal(QPoint(25, 25))
    overlay.setGeometry(position.x(), position.y(), 300, 85)
    overlay.winId()
    state["foreground_before"] = int(u.GetForegroundWindow() or 0)
    overlay.show()
    click_through(overlay)
    QTimer.singleShot(400, baseline)

def baseline():
    state["baseline"] = capture()
    overlay.paint_on = True
    overlay.update()
    QTimer.singleShot(400, visible)

def visible():
    state["visible"] = capture()
    state["exclude_return"] = exclude_capture(overlay)
    QTimer.singleShot(400, excluded)

def excluded():
    image = capture()
    rect = w.RECT()
    u.GetWindowRect(int(overlay.winId()), ctypes.byref(rect))
    hit = int(u.WindowFromPoint(w.POINT(rect.left+30, rect.top+30)) or 0)
    baseline_img = state["baseline"].astype(float)
    result = dict(exclude_return=state["exclude_return"],
                  visible_difference=float(np.abs(state["visible"].astype(float)-baseline_img).mean()),
                  excluded_difference=float(np.abs(image.astype(float)-baseline_img).mean()),
                  click_through=hit != int(overlay.winId()),
                  did_not_activate=int(u.GetForegroundWindow() or 0) == state["foreground_before"],
                  dpr=base.devicePixelRatioF(), sample_width_px=image.shape[1])
    result["passed"] = bool(result["exclude_return"] and result["visible_difference"] > 10 and result["excluded_difference"] < 1 and result["click_through"] and result["did_not_activate"])
    out = Path(__file__).resolve().parents[2]/"artifacts/windows/validation"
    out.mkdir(parents=True, exist_ok=True)
    (out/f"overlay-probe-{round(base.devicePixelRatioF()*100)}.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result), flush=True)
    overlay.close()
    base.close()
    app.exit(0 if result["passed"] else 1)

QTimer.singleShot(500, place)
QTimer.singleShot(15000, lambda: app.exit(2))
sys.exit(app.exec())
