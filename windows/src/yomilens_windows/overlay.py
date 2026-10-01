from PySide6.QtCore import Qt, QRect, QRectF, QPoint, Signal
from PySide6.QtGui import QPainter, QColor, QPen, QFont, QFontMetrics
from PySide6.QtWidgets import QWidget
from .native import exclude_capture, click_through
from .geometry import physical_box_to_local


class Overlay(QWidget):
    def __init__(self):
        super().__init__(None, Qt.Tool | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.WindowTransparentForInput | Qt.WindowDoesNotAcceptFocus)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setAttribute(Qt.WA_ShowWithoutActivating)
        self.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.lines = []
        self.region = QRect()
        self.scale = 1
        self.mode = 0
        self.font_size = 15
        self.excluded = False

    def configure(self, screen, region):
        self.setGeometry(screen.geometry())
        self.region = region.translated(-screen.geometry().topLeft())
        self.scale = screen.devicePixelRatio()
        self.lines = []
        self.show()
        click_through(self)
        self.excluded = exclude_capture(self)
        self.update()
        return self.excluded

    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        p.setPen(QPen(QColor("#3baf97"), 2))
        p.drawRoundedRect(QRectF(self.region).adjusted(1, 1, -1, -1), 4, 4)
        p.setFont(QFont("Microsoft YaHei UI", self.font_size))
        fm = QFontMetrics(p.font())
        occupied = []
        sources = []
        for line, reading in self.lines:
            x, y, w, h = physical_box_to_local(line.box, self.scale)
            sources.append(QRectF(self.region.x()+x, self.region.y()+y, w, h))
        for (line, reading), source in zip(self.lines, sources):
            text = reading.romaji if self.mode == 0 else reading.kana if self.mode == 1 else f"{reading.original}  /  {reading.romaji}"
            if reading.uncertain:
                text = "? " + text
            max_width = max(60, min(self.width()-20, 1000))
            text = fm.elidedText(text, Qt.ElideRight, max_width-16)
            width, height = fm.horizontalAdvance(text)+16, fm.height()+8
            left = min(max(6, source.x()), max(6, self.width()-width-6))
            candidates = [source.top()-height-3, source.bottom()+3]
            candidates.extend(source.top()-height-3-step*(height+2) for step in range(1, 4))
            rect = None
            for top in candidates:
                candidate = QRectF(left, top, width, height)
                if top < 3 or candidate.bottom() > self.height()-3:
                    continue
                if any(candidate.intersects(r) for r in occupied+sources):
                    continue
                rect = candidate
                break
            if rect is None:
                # Dense text: omit instead of obscuring source or another annotation.
                continue
            occupied.append(rect)
            p.setPen(Qt.NoPen)
            p.setBrush(QColor(22, 43, 38, 232))
            p.drawRoundedRect(rect, 5, 5)
            p.setPen(QColor("#ffd88c" if reading.uncertain else "#f2fffb"))
            p.drawText(rect.adjusted(8, 0, -8, 0), Qt.AlignVCenter | Qt.AlignLeft, text)
        p.end()


class Selector(QWidget):
    selected = Signal(object, object)
    cancelled = Signal()

    def __init__(self, screen):
        super().__init__(None, Qt.Tool | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint)
        self.target_screen = screen
        self.setGeometry(screen.geometry())
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setCursor(Qt.CrossCursor)
        self.anchor = None
        self.end = QPoint()
        self.completed = False

    def paintEvent(self, event):
        p = QPainter(self)
        p.fillRect(self.rect(), QColor(12, 30, 27, 105))
        p.setPen(QColor("white"))
        p.setFont(QFont("Microsoft YaHei UI", 14))
        p.drawText(QRect(24, 20, self.width()-48, 50), Qt.AlignCenter, "拖动框选日语区域 · Esc 取消 · 本次选区限当前显示器")
        if self.anchor is not None:
            rect = QRect(self.anchor, self.end).normalized()
            p.setCompositionMode(QPainter.CompositionMode_Clear)
            p.fillRect(rect, Qt.transparent)
            p.setCompositionMode(QPainter.CompositionMode_SourceOver)
            p.setPen(QPen(QColor("#5de4be"), 2))
            p.drawRect(rect)
        p.end()

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self.anchor = event.position().toPoint()
            self.end = self.anchor
            self.update()

    def mouseMoveEvent(self, event):
        if self.anchor is not None:
            self.end = event.position().toPoint()
            self.update()

    def mouseReleaseEvent(self, event):
        if event.button() != Qt.LeftButton or self.anchor is None:
            return
        rect = QRect(self.anchor, event.position().toPoint()).normalized().intersected(self.rect())
        if rect.width() < 40 or rect.height() < 30:
            self.anchor = None
            self.update()
            return
        self.completed = True
        self.hide()
        self.selected.emit(self.target_screen, rect.translated(self.geometry().topLeft()))
        self.close()

    def keyPressEvent(self, event):
        if event.key() == Qt.Key_Escape:
            self.close()

    def closeEvent(self, event):
        if not self.completed:
            self.cancelled.emit()
        event.accept()
