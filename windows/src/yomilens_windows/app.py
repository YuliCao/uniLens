import ctypes
from ctypes import wintypes
import sys
import time
from PySide6.QtCore import Qt, QTimer, QAbstractNativeEventFilter, QLockFile, QRect
from PySide6.QtGui import QColor, QCursor, QFont, QIcon, QPainter, QPixmap
from PySide6.QtWidgets import (QApplication, QWidget, QLabel, QPushButton, QVBoxLayout,
    QHBoxLayout, QComboBox, QSpinBox, QTextEdit, QGroupBox, QFormLayout, QSystemTrayIcon,
    QMenu, QMessageBox, QFileDialog, QScrollArea, QCheckBox)
from . import __version__
from . import settings
from .native import u, exclude_capture, physical_monitor_origin
from .geometry import logical_to_physical
from .overlay import Overlay, Selector
from .pipeline import AnalysisWorker, Request

STYLE = """
QWidget { font-family: 'Microsoft YaHei UI'; font-size: 13px; color: #203c34; }
QWidget#home { background: #f4f7f5; }
QLabel#title { font-size: 28px; font-weight: 600; }
QLabel#muted { color: #677a73; }
QPushButton { border: 0; border-radius: 7px; background: #e4eeea; padding: 10px 16px; }
QPushButton:hover { background: #d3e5dd; }
QPushButton:disabled { color: #8b9992; background: #edf0ee; }
QPushButton#primary { background: #216f61; color: white; font-weight: 600; }
QPushButton#primary:hover { background: #185749; }
QGroupBox { background: white; border: 1px solid #e1e8e3; border-radius: 10px; margin-top: 12px; padding: 18px 12px 12px; }
QGroupBox::title { subcontrol-origin: margin; left: 14px; padding: 0 5px; }
QComboBox, QSpinBox, QTextEdit { border: 1px solid #d7e1db; border-radius: 5px; padding: 6px; background: white; }
"""


def make_icon():
    pix = QPixmap(64, 64)
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    p.setBrush(QColor("#216f61"))
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(2, 2, 60, 60, 15, 15)
    p.setPen(QColor("white"))
    p.setFont(QFont("Microsoft YaHei UI", 28, QFont.Bold))
    p.drawText(pix.rect(), Qt.AlignCenter, "あ")
    p.end()
    return QIcon(pix)


class Hotkeys(QAbstractNativeEventFilter):
    def __init__(self, owner):
        super().__init__()
        self.owner = owner
        self.ids = []

    def register(self, start):
        self.close()
        failures = []
        for i, name in enumerate(("暂停/继续", "重新框选", "退出")):
            key = start+i
            if u.RegisterHotKey(int(self.owner.winId()), i+1, 0x4000 | 0x2 | 0x1, 0x70+key-1):
                self.ids.append(i+1)
            else:
                failures.append(f"Ctrl+Alt+F{key}（{name}）")
        return failures

    def close(self):
        for hotkey_id in self.ids:
            u.UnregisterHotKey(int(self.owner.winId()), hotkey_id)
        self.ids.clear()

    def nativeEventFilter(self, event_type, message):
        if bytes(event_type) in (b"windows_generic_MSG", b"windows_dispatcher_MSG"):
            msg = wintypes.MSG.from_address(int(message))
            if msg.message == 0x0312 and msg.wParam in self.ids:
                callback = {1: self.owner.toggle, 2: self.owner.select_region, 3: self.owner.shutdown}[msg.wParam]
                QTimer.singleShot(0, callback)
                return True, 0
        return False, 0


class MainWindow(QWidget):
    def __init__(self, test_mode=False):
        super().__init__()
        self.test_mode = test_mode
        self.setObjectName("home")
        self.setWindowTitle(f"YomiLens · 日语透镜 {__version__}")
        self.setWindowIcon(make_icon())
        self.resize(520, 660)
        self.setMinimumSize(460, 460)
        self.cfg = settings.load()
        self.generation = 0
        self.running = False
        self.initialized = False
        self.closing = False
        self.region = None
        self.selected_screen = None
        self.selector = None
        self.last_result = 0
        self.result_count = 0
        self.ocr_count = 0
        self.last_lines = []
        self.overlay = Overlay()
        self.build_ui()
        self.build_bar()
        self.build_tray()
        self.hotkeys = Hotkeys(self)
        QApplication.instance().installNativeEventFilter(self.hotkeys)
        if not test_mode:
            self.register_hotkeys()
        self.worker = AnalysisWorker()
        self.worker.ready.connect(self.on_ready)
        self.worker.result.connect(self.on_result)
        self.worker.changed.connect(self.on_changed)
        self.worker.error.connect(self.on_error)
        self.worker.finished.connect(self.on_finished)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.tick)
        self.timer.start(750)
        self.worker.start()
        app = QApplication.instance()
        app.screenRemoved.connect(lambda _: self.invalidate_display())
        app.screenAdded.connect(lambda _: self.invalidate_display())
        for screen in app.screens():
            screen.geometryChanged.connect(lambda _: self.invalidate_display())
            screen.logicalDotsPerInchChanged.connect(lambda _: self.invalidate_display())

    def build_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QScrollArea.NoFrame)
        page = QWidget()
        page.setObjectName("home")
        layout = QVBoxLayout(page)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        badge = QLabel("YOMILENS  /  WINDOWS")
        badge.setStyleSheet("color: #216f61; font-size: 12px; letter-spacing: 2px;")
        layout.addWidget(badge)
        title = QLabel("让日语，读得出来。")
        title.setObjectName("title")
        layout.addWidget(title)
        subtitle = QLabel("框选屏幕文字，在原文附近显示读音。全程离线。")
        subtitle.setObjectName("muted")
        subtitle.setWordWrap(True)
        layout.addWidget(subtitle)
        self.status = QLabel("正在加载离线模型…")
        self.status.setWordWrap(True)
        self.status.setMinimumHeight(38)
        layout.addWidget(self.status)
        self.start_button = QPushButton("开始阅读 · 框选区域")
        self.start_button.setObjectName("primary")
        self.start_button.setEnabled(False)
        self.start_button.clicked.connect(self.select_region)
        layout.addWidget(self.start_button)
        row = QHBoxLayout()
        self.pause_button = QPushButton("暂停 / 继续")
        self.pause_button.clicked.connect(self.toggle)
        self.pause_button.setEnabled(False)
        row.addWidget(self.pause_button)
        sample = QPushButton("打开测试文字")
        sample.clicked.connect(self.show_sample)
        row.addWidget(sample)
        layout.addLayout(row)
        box = QGroupBox("显示与响应")
        form = QFormLayout(box)
        self.mode = QComboBox()
        self.mode.addItems(["罗马音", "平假名", "原文 + 罗马音"])
        mode = self.cfg.get("mode", 0)
        self.mode.setCurrentIndex(mode if type(mode) is int and 0 <= mode <= 2 else 0)
        self.mode.currentIndexChanged.connect(self.change_display)
        form.addRow("显示模式", self.mode)
        self.font_size = QSpinBox()
        self.font_size.setRange(10, 28)
        font = self.cfg.get("font_size", 15)
        self.font_size.setValue(font if type(font) is int else 15)
        self.font_size.valueChanged.connect(self.change_display)
        form.addRow("标注字号", self.font_size)
        self.interval = QComboBox()
        self.interval.addItems(["快速 · 0.35 秒", "均衡 · 0.75 秒", "省电 · 1.5 秒"])
        self.interval.setCurrentIndex(1)
        self.interval.currentIndexChanged.connect(self.change_interval)
        form.addRow("扫描间隔", self.interval)
        self.kana_only = QCheckBox("仅识别含假名的行（减少中文误识别）")
        self.kana_only.setChecked(self.cfg.get("kana_only", True) is not False)
        self.kana_only.toggled.connect(self.change_display)
        form.addRow(self.kana_only)
        self.hotkey_choice = QComboBox()
        self.hotkey_choice.addItems(["Ctrl+Alt+F8 / F9 / F10", "Ctrl+Alt+F5 / F6 / F7"])
        self.hotkey_choice.setCurrentIndex(1 if self.cfg.get("hotkey_profile") == 1 else 0)
        self.hotkey_choice.currentIndexChanged.connect(self.register_hotkeys)
        form.addRow("暂停 / 框选 / 退出", self.hotkey_choice)
        self.hotkey_warning = QLabel()
        self.hotkey_warning.setWordWrap(True)
        self.hotkey_warning.setStyleSheet("color: #976822")
        self.hotkey_warning.hide()
        form.addRow(self.hotkey_warning)
        layout.addWidget(box)
        self.dict_toggle = QPushButton("专名纠音与导入导出  ＋")
        layout.addWidget(self.dict_toggle)
        self.dictionary_box = QWidget()
        dlayout = QVBoxLayout(self.dictionary_box)
        dlayout.setContentsMargins(0, 0, 0, 0)
        dlayout.addWidget(QLabel("每行：原文=假名（最多 500 条）"))
        self.dictionary = QTextEdit()
        self.dictionary.setMaximumHeight(125)
        self.dictionary.setPlaceholderText("八重神子=やえみこ")
        overrides = self.cfg.get("overrides", "日本語=にほんご")
        self.dictionary.setPlainText(overrides if isinstance(overrides, str) else "")
        self.applied_overrides = self.dictionary.toPlainText()
        dlayout.addWidget(self.dictionary)
        buttons = QHBoxLayout()
        for text, callback in [("保存词典", self.save_dictionary), ("导入", self.import_dictionary), ("导出", self.export_dictionary)]:
            button = QPushButton(text)
            button.clicked.connect(callback)
            buttons.addWidget(button)
        dlayout.addLayout(buttons)
        self.dictionary_box.hide()
        self.dict_toggle.clicked.connect(lambda: self.dictionary_box.setVisible(not self.dictionary_box.isVisible()))
        layout.addWidget(self.dictionary_box)
        note = QLabel("先打开游戏、漫画或网页，再框选文字。支持普通窗口与无边框窗口；首版按行标注，密集或竖排文字可能漏读。问号表示未知读音。\n\n不保存屏幕内容。关闭此窗口会暂停并收至托盘；彻底退出请使用托盘菜单或悬浮条的 ×。")
        note.setWordWrap(True)
        note.setObjectName("muted")
        layout.addWidget(note)
        layout.addStretch()
        scroll.setWidget(page)
        outer.addWidget(scroll)

    def build_bar(self):
        self.bar = QWidget(None, Qt.Tool | Qt.WindowStaysOnTopHint | Qt.WindowDoesNotAcceptFocus)
        self.bar.setAttribute(Qt.WA_ShowWithoutActivating)
        self.bar.setWindowTitle("YomiLens 控制条 · 拖动标题栏移动")
        row = QHBoxLayout(self.bar)
        row.setContentsMargins(6, 6, 6, 6)
        self.bar_pause = QPushButton("暂停")
        self.bar_pause.clicked.connect(self.toggle)
        row.addWidget(self.bar_pause)
        for text, callback in [("框选", self.select_region), ("模式", lambda: self.mode.setCurrentIndex((self.mode.currentIndex()+1)%3)), ("设置", self.show_settings), ("×", self.shutdown)]:
            button = QPushButton(text)
            button.clicked.connect(callback)
            row.addWidget(button)
        self.bar.closeEvent = self.bar_close

    def bar_close(self, event):
        if self.closing:
            event.accept()
        else:
            event.ignore()
            self.pause("已暂停，可从托盘继续")
            self.bar.hide()

    def build_tray(self):
        self.tray = QSystemTrayIcon(make_icon(), self)
        self.tray.setToolTip("YomiLens · 日语透镜")
        menu = QMenu(self)
        for label, callback in [("打开设置", self.show_settings), ("框选区域", self.select_region), ("暂停 / 继续", self.toggle), ("退出", self.shutdown)]:
            menu.addAction(label, callback)
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(lambda reason: self.show_settings() if reason == QSystemTrayIcon.DoubleClick else None)
        if not self.test_mode:
            self.tray.show()

    def on_ready(self):
        self.initialized = True
        self.start_button.setEnabled(True)
        self.status.setText("准备就绪 · 默认框选 · 离线运行")

    def on_finished(self):
        if self.closing:
            QApplication.instance().quit()
        elif not self.initialized:
            self.start_button.setEnabled(False)

    def register_hotkeys(self, *_):
        if not hasattr(self, "hotkeys") or self.test_mode:
            return
        failures = self.hotkeys.register(5 if self.hotkey_choice.currentIndex() else 8)
        self.hotkey_warning.setText("快捷键被占用："+"、".join(failures)+"；请切换另一组" if failures else "")
        self.hotkey_warning.setVisible(bool(failures))
        self.persist()

    def persist(self):
        if self.test_mode:
            return
        try:
            settings.save(dict(mode=self.mode.currentIndex(), font_size=self.font_size.value(),
                hotkey_profile=self.hotkey_choice.currentIndex(), overrides=self.applied_overrides,
                kana_only=self.kana_only.isChecked()))
        except OSError as exc:
            self.status.setText(f"设置保存失败：{exc}")

    def select_region(self):
        if not self.initialized or self.closing:
            return
        self.pause("正在框选；Esc 取消")
        if self.selector is not None:
            self.selector.close()
        screen = QApplication.screenAt(QCursor.pos()) or QApplication.primaryScreen()
        self.hide()
        self.bar.hide()
        self.selector = Selector(screen)
        self.selector.selected.connect(self.accept_region)
        self.selector.cancelled.connect(self.cancel_selection)
        self.selector.show()
        self.selector.activateWindow()

    def cancel_selection(self):
        if not self.closing:
            self.status.setText("已取消框选 · 已暂停")
            self.show_settings()

    def accept_region(self, screen, rect):
        if self.closing:
            return
        self.selected_screen = screen
        self.logical_region = QRect(rect)
        if not self.overlay.configure(screen, rect):
            self.pause("系统无法排除自身标注，本次未启动识别")
            self.show_settings()
            return
        origin = physical_monitor_origin(self.overlay)
        logical = screen.geometry().topLeft()
        self.region = logical_to_physical((rect.x(), rect.y(), rect.width(), rect.height()),
            (logical.x(), logical.y()), origin, screen.devicePixelRatio())
        self.generation += 1
        self.running = True
        self.pause_button.setEnabled(True)
        self.bar_pause.setText("暂停")
        self.overlay.mode = self.mode.currentIndex()
        self.overlay.font_size = self.font_size.value()
        self.bar.adjustSize()
        available = screen.availableGeometry()
        self.bar.move(available.x()+16, available.y()+16)
        self.bar.show()
        if not exclude_capture(self.bar):
            self.pause("控制条无法从截图排除，已暂停")
            self.show_settings()
            return
        self.last_result = time.monotonic()
        self.status.setText("正在读取框选区域…")
        self.tick()

    def pause(self, message="已暂停"):
        self.running = False
        self.generation += 1
        self.worker.cancel_pending()
        self.overlay.lines = []
        self.overlay.hide()
        self.last_lines = []
        self.bar_pause.setText("继续")
        self.status.setText(message)

    def toggle(self):
        if self.closing or not self.initialized:
            return
        if self.running:
            self.pause()
        elif self.region is not None and self.selected_screen in QApplication.screens():
            self.hide()
            self.accept_region(self.selected_screen, self.logical_region)
        else:
            self.select_region()

    def tick(self):
        if self.running and not self.closing:
            if time.monotonic()-self.last_result > 3:
                self.overlay.lines = []
                self.overlay.update()
            self.worker.submit(Request(self.generation, self.region, self.applied_overrides, self.kana_only.isChecked()))

    def on_changed(self, generation):
        if self.running and generation == self.generation:
            self.overlay.lines = []
            self.overlay.update()

    def on_result(self, generation, lines, elapsed, reused):
        if not self.running or generation != self.generation or self.closing:
            return
        if elapsed > 3:
            self.overlay.lines = []
            self.overlay.update()
            self.status.setText("识别超过 3 秒，已丢弃过期画面；请缩小选区")
            return
        self.last_result = time.monotonic()
        self.result_count += 1
        self.ocr_count += not reused
        self.last_lines = lines
        self.overlay.lines = lines
        self.overlay.update()
        self.status.setText(f"{len(lines)} 行日语 · {'静止复用' if reused else f'处理 {elapsed*1000:.0f} ms'} · 离线")

    def on_error(self, generation, message):
        if self.closing:
            return
        if generation == -1 or generation == self.generation:
            self.pause(message)
            self.show_settings()

    def change_display(self, *_):
        if not hasattr(self, "worker"):
            return
        self.generation += 1
        self.worker.cancel_pending()
        self.overlay.mode = self.mode.currentIndex()
        self.overlay.font_size = self.font_size.value()
        self.overlay.lines = []
        self.overlay.update()
        self.persist()
        self.tick()

    def change_interval(self, index):
        if hasattr(self, "timer"):
            self.timer.setInterval([350, 750, 1500][index])

    def invalidate_display(self):
        if self.region is not None:
            self.pause("显示器或缩放已变化，请重新框选")
            self.region = None
            self.show_settings()

    def show_settings(self):
        self.show()
        self.raise_()
        self.activateWindow()
        if self.running and not exclude_capture(self):
            self.pause("设置窗口无法排除截图，已暂停")

    def save_dictionary(self):
        try:
            from .reading import ReadingEngine
            validator = ReadingEngine()
            validator.set_overrides(self.dictionary.toPlainText())
        except (ValueError, RuntimeError) as exc:
            self.status.setText(str(exc))
            return
        self.applied_overrides = self.dictionary.toPlainText()
        self.change_display()
        self.status.setText("专名词典已保存")

    def import_dictionary(self):
        path, _ = QFileDialog.getOpenFileName(self, "导入 UTF-8 专名词典", "", "文本 (*.txt);;所有文件 (*)")
        if path:
            try:
                from pathlib import Path
                if Path(path).stat().st_size > 1024*1024:
                    raise ValueError("词典文件超过 1 MB")
                self.dictionary.setPlainText(Path(path).read_text(encoding="utf-8-sig"))
                self.save_dictionary()
            except (OSError, ValueError) as exc:
                self.status.setText(f"导入失败：{exc}")

    def export_dictionary(self):
        path, _ = QFileDialog.getSaveFileName(self, "导出词典", "yomilens-dictionary.txt", "文本 (*.txt)")
        if path:
            try:
                from pathlib import Path
                Path(path).write_text(self.applied_overrides, encoding="utf-8")
            except OSError as exc:
                self.status.setText(f"导出失败：{exc}")

    def show_sample(self):
        self.sample = QWidget()
        self.sample.setWindowTitle("日语阅读测试 · 在此窗口框选")
        self.sample.setStyleSheet("background: white; color: #183a32;")
        layout = QVBoxLayout(self.sample)
        label = QLabel("私は東京へ行きます。\n\n新しい仲間が加わった！\n\n「日本語」を読んでください。")
        label.setFont(QFont("Yu Gothic", 24))
        label.setContentsMargins(35, 35, 35, 35)
        layout.addWidget(label)
        button = QPushButton("点击测试：0")
        button.clicked.connect(lambda: button.setText(f"点击测试：{int(button.text().split('：')[-1])+1}"))
        layout.addWidget(button)
        self.sample.resize(800, 500)
        self.sample.show()

    def closeEvent(self, event):
        if self.closing:
            event.accept()
        elif QSystemTrayIcon.isSystemTrayAvailable() and not self.test_mode:
            self.pause("已暂停并收至托盘")
            self.hide()
            event.ignore()
        else:
            event.ignore()
            self.shutdown()

    def shutdown(self):
        if self.closing:
            return
        self.closing = True
        self.timer.stop()
        self.pause("正在退出…")
        self.persist()
        self.hotkeys.close()
        QApplication.instance().removeNativeEventFilter(self.hotkeys)
        self.overlay.hide()
        self.bar.hide()
        self.tray.hide()
        if self.selector is not None:
            self.selector.close()
        if hasattr(self, "sample"):
            self.sample.close()
        self.hide()
        self.worker.stop()
        if not self.worker.isRunning():
            QApplication.instance().quit()


def main():
    # Qt owns DPI initialization before MSS is imported in the worker.
    app = QApplication(sys.argv)
    app.setApplicationName("YomiLens")
    app.setStyleSheet(STYLE)
    app.setQuitOnLastWindowClosed(False)
    if "--self-test" in sys.argv:
        from .selftest import run
        return run(app)
    lock = QLockFile(str(settings.config_dir()/"instance.lock"))
    lock.setStaleLockTime(0)
    if not lock.tryLock(100):
        QMessageBox.information(None, "YomiLens", "日语透镜已运行，请从系统托盘打开。")
        return 0
    window = MainWindow()
    window.show()
    def report_exception(kind, value, tb):
        window.pause(f"发生错误：{kind.__name__}；请退出后重启")
        window.show_settings()
    sys.excepthook = report_exception
    result = app.exec()
    window.worker.stop()
    window.worker.wait()
    lock.unlock()
    return result


if __name__ == "__main__":
    sys.exit(main())
