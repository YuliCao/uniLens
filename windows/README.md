# YomiLens Windows · 日语透镜 0.1.0

离线 Windows 桌面日语读音悬浮工具。框选屏幕日语，在原文附近显示罗马音、平假名或原文对照。首版为行级标注。

## 使用

1. 完整解压便携 ZIP，双击 `YomiLens.exe`。必须保留旁边的 `_internal` 目录，不需要 Python 或 Java。
2. 等待“准备就绪”。打开要阅读的窗口，点击“开始阅读”或按 Ctrl+Alt+F9，然后拖动框选。Esc 取消。
3. 选区边框会常显，标注层鼠标穿透；悬浮控制条可通过标题栏拖动。
4. Ctrl+Alt+F8 暂停/继续；Ctrl+Alt+F9 重新框选；Ctrl+Alt+F10 完全退出。快捷键冲突可在设置中切换另一组。
5. “模式”切换罗马音、平假名、原文对照。“专名纠音”接受每行 `原文=假名`，支持 UTF-8 文本导入导出。
6. 关闭设置窗口会暂停并收至托盘。托盘菜单或悬浮条 × 完全退出。

框选使用鼠标所在的显示器，首版选区限单屏。缩放、分辨率或显示器连接变化后，需要重新框选。默认仅识别含假名的行，以减少中文被误读；要识别纯汉字日语，可关闭此项。字号越大、原文越密集，可摆放的标注越少；首版宁可略过放不下的标注，也不覆盖原文。

默认附带 `日本語=にほんご` 纠音。词典仍可能把“私”读作“わたくし”等有效但不符合当前语境的读法，可通过专名词典纠正。未知汉字保留原文并显示问号。不能恢复 OCR 漏掉或识别错误的符号。

支持目标为 Windows 11 x64、Windows 10 2004+；实际验证范围见 `../docs/WINDOWS_HANDOFF.md` 或发行验证报告。普通桌面与无边框窗口优先；不保证独占全屏游戏、受保护画面、竖排或复杂艺术字体效果。

程序不保存屏幕帧和 OCR 历史，不上传内容，运行时禁止下载模型。设置保存在 `%LOCALAPPDATA%/YomiLens/settings.json`，包括用户主动保存的纠音词典。发行包未签名。

## 源码开发

推荐 Python 3.13 x64（本机验证 3.13.5）。所有依赖使用 `windows/.venv` 隔离。Qt 固定 6.8.3：本机 Qt 6.11.2 的 QtCore DLL 无法加载，未改动系统运行库。

从仓库根目录运行：

```powershell
./windows/scripts/setup.ps1
./windows/scripts/start.ps1
windows/.venv/Scripts/python.exe -m pytest windows/tests -q
windows/.venv/Scripts/python.exe windows/scripts/probe_ocr.py
windows/.venv/Scripts/python.exe windows/scripts/probe_overlay.py
windows/.venv/Scripts/python.exe -m yomilens_windows.app --self-test
./windows/scripts/build.ps1
```

依赖准备和模型下载阶段需要网络；模型地址和 SHA256 锁定在 `models/manifest.json`。正常运行不需要网络。GUI 回归只操作自制日语测试窗口，不保存用户桌面内容；启动参数 `--self-test --output <目录>` 也可用于 EXE 验证，会阻止该进程 Python socket 联网。

`dist/YomiLens/` 是打包产物。THIRD_PARTY 包含依赖许可，模型为 PaddleOCR 衍生的 RapidOCR ONNX 文件，使用显式日语识别模型和内嵌字符表。运行时先校验三个模型 SHA256，缺失或损坏会报错，不自动下载。

## 实现与限制

Qt 主线程绘图，独立工作线程截图与 OCR；一个执行任务加一个可覆盖的待处理请求。通过会话编号拒绝暂停、选区或模式变化后的旧结果。静止画面复用，至少每三秒重做一次 OCR；检测到变化先撤销旧标注。处理超过三秒的帧不显示，建议缩小选区。

截图为物理像素，使用显示器物理原点和 Qt DPR 转为界面坐标。自身窗口设置 WDA_EXCLUDEFROMCAPTURE；若设置失败则停止识别，避免自身标注循环识别。该功能必须结合实际采集后端验证，不能据此保证所有 Windows 版本和图形驱动。
