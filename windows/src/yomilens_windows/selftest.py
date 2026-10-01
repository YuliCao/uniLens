"""Self-contained UI regression, uses only generated sample text and own widgets."""
import json
from pathlib import Path
import socket
import sys
import time
from PySide6.QtCore import QTimer, Qt, QPoint
from PySide6.QtGui import QCursor
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QLabel


def run(app):
    def deny_network(*args, **kwargs):
        raise RuntimeError("Network blocked by offline self-test")
    socket.socket.connect = deny_network
    socket.create_connection = deny_network
    from .app import MainWindow
    out = Path(sys.argv[sys.argv.index("--output")+1]) if "--output" in sys.argv else Path.cwd()/"artifacts/windows/validation"
    out.mkdir(parents=True, exist_ok=True)
    window = MainWindow(test_mode=True)
    window.show()
    evidence = {"offline_socket_blocked": True, "phases": [], "inference_s": [], "passed": False}
    state = {"phase": "ready", "since": time.monotonic(), "started": time.monotonic(), "count": 0}
    window.worker.result.connect(lambda generation, lines, elapsed, reused: evidence["inference_s"].append(elapsed) if not reused else None)

    def finish(error=None):
        timer.stop()
        evidence["passed"] = error is None
        evidence["error"] = error
        evidence["result_count"] = window.result_count
        evidence["ocr_count"] = window.ocr_count
        evidence["elapsed_s"] = time.monotonic()-state["started"]
        (out/"ui-selftest.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        window.shutdown()

    def advance(phase):
        evidence["phases"].append(state["phase"])
        state.update(phase=phase, since=time.monotonic(), count=window.result_count)

    def step():
        try:
            if time.monotonic()-state["since"] > 25:
                raise AssertionError(f"Timed out in {state['phase']}: {window.status.text()}")
            phase = state["phase"]
            if phase == "ready" and window.initialized:
                failures = window.hotkeys.register(8)
                evidence["hotkey_conflicts"] = failures
                evidence["registered_hotkeys"] = len(window.hotkeys.ids)
                window.hotkeys.close()
                window.grab().save(str(out/"home.png"))
                window.show_sample()
                screen = QApplication.screenAt(QCursor.pos()) or QApplication.primaryScreen()
                window.sample.move(screen.availableGeometry().topLeft()+QPoint(100, 120))
                advance("sample")
            elif phase == "sample" and time.monotonic()-state["since"] > .5:
                window.select_region()
                advance("select")
            elif phase == "select" and time.monotonic()-state["since"] > .3:
                selector = window.selector
                rect = window.sample.geometry().adjusted(10, 10, -10, -50)
                origin = selector.geometry().topLeft()
                QTest.mousePress(selector, Qt.LeftButton, Qt.NoModifier, rect.topLeft()-origin)
                QTest.mouseMove(selector, rect.bottomRight()-origin)
                QTest.mouseRelease(selector, Qt.LeftButton, Qt.NoModifier, rect.bottomRight()-origin)
                assert window.running, window.status.text()
                advance("recognition")
            elif phase == "recognition" and window.last_lines:
                originals = [r.original for _, r in window.last_lines]
                assert any("東京" in s for s in originals), originals
                assert any("仲間" in s for s in originals), originals
                window.overlay.grab().save(str(out/"annotations.png"))
                window.sample.grab().save(str(out/"sample-window.png"))
                evidence["sample_readings"] = [r.__dict__ for _, r in window.last_lines]
                old, old_lines = window.generation, window.last_lines
                window.pause()
                assert not window.overlay.isVisible()
                window.on_result(old, old_lines, 0, False)
                assert not window.overlay.lines, "Stale generation was displayed after pause"
                window.toggle()
                advance("resume")
            elif phase == "resume" and window.result_count > state["count"]:
                window.mode.setCurrentIndex(1)
                advance("kana")
            elif phase == "kana" and window.result_count > state["count"]:
                assert window.overlay.mode == 1
                window.mode.setCurrentIndex(2)
                advance("parallel")
            elif phase == "parallel" and window.result_count > state["count"]:
                assert window.overlay.mode == 2
                window.dictionary.setPlainText("日本語=にほんご\n東京=とうけい")
                window.save_dictionary()
                advance("dictionary")
            elif phase == "dictionary" and window.result_count > state["count"]:
                assert any("toukei" in r.romaji for _, r in window.last_lines), [r.romaji for _, r in window.last_lines]
                advance("steady")
            elif phase == "steady" and window.result_count >= state["count"]+5:
                assert window.overlay.isVisible() and window.overlay.lines
                window.sample.findChild(QLabel).setText("明日は学校へ行きます。\n\nコーヒーを飲みます。")
                advance("dynamic")
            elif phase == "dynamic" and window.result_count > state["count"]:
                if not any("学校" in r.original for _, r in window.last_lines):
                    return
                assert not any("東京" in r.original for _, r in window.last_lines)
                window.sample.findChild(QLabel).setText("")
                advance("blank")
            elif phase == "blank" and window.result_count > state["count"]:
                if window.overlay.lines:
                    return
                window.select_region()
                QTest.keyClick(window.selector, Qt.Key_Escape)
                assert not window.running
                assert not window.overlay.isVisible()
                advance("cancel")
                finish()
        except Exception as exc:
            finish(f"{type(exc).__name__}: {exc}")

    timer = QTimer()
    timer.timeout.connect(step)
    timer.start(100)
    app.exec()
    window.worker.stop()
    window.worker.wait()
    evidence["worker_stopped"] = not window.worker.isRunning()
    (out/"ui-selftest.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False), flush=True)
    return 0 if evidence["passed"] and evidence["worker_stopped"] else 1
