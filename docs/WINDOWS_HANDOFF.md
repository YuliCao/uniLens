# Windows 开发续接记录

更新：2026-09-28（Windows 首版实现进行中）。

## 当前实现快照（优先阅读）

- 已创建 windows/ 独立 Python 3.13.5 虚拟环境与可运行源码；25 项读音/坐标测试通过。
- PySide6 6.11.2 在本机 QtCore DLL 加载失败，已改为 6.8.3 并验证成功；未修改系统运行库。
- 日语 PP-OCRv4 识别模型、检测模型及方向模型已下载到 windows/models，manifest.json 包含来源/版本/SHA256；运行时检查哈希并禁止依赖自动下载。
- 静态三句 OCR 全部精确匹配；热身后约 134–143 ms（1000×360 自制图片，仅本机小样本）。
- 原生透明层验证通过：点击穿透、不抢焦点、排除自身采集后底层与基准逐像素一致；100% 与 QT_SCALE_FACTOR=1.5 应用缩放验证通过。150% 首次测试发现探针在 show 前取窗口坐标不稳定，修正为 show 后稳定再取基准；这不是在系统中修改 DPI。
- 主程序：设置、框选、常显边框、三模式、字号、扫描间隔、默认含假名过滤、词典导入导出、托盘、两组可选热键、窗口排除、单任务/最新请求调度、会话代数丢弃旧结果、过期帧丢弃。
- 源码端到端自测已通过：框选、识别、暂停恢复、模式切换、词典修改、连续刷新、取消和退出；100% 与 150% 应用缩放均通过，测试进程 socket 被禁用。
- 正在构建 windows/dist/YomiLens/YomiLens.exe；日志 .tools/windows-build.log。**此快照时尚未验证最终 EXE，不能据此宣称发行已完成。**
- 后续第一步：检查构建完成情况，复制到中文/空格的独立路径，在仅系统 PATH 下运行 EXE --self-test；处理缺依赖，再交付 ZIP/哈希/说明，更新本文件最终状态。

运行命令（仓库根目录）：

```powershell
windows/.venv/Scripts/python.exe -m pytest windows/tests -q
windows/.venv/Scripts/python.exe windows/scripts/probe_ocr.py
windows/.venv/Scripts/python.exe windows/scripts/probe_overlay.py
windows/.venv/Scripts/python.exe -m yomilens_windows.app --self-test
./windows/scripts/build.ps1
```

实现文件与脚本均在 windows/。模型、虚拟环境、构建目录均忽略，模型清单及版本锁入库。验证证据在 artifacts/windows/validation。THIRD_PARTY 已收集实际依赖许可。注意当前自制屏幕场景存在引号 OCR 错读，非识别准确率基准；独占全屏、真实混合 DPI 多屏、显卡/Windows 10 兼容尚未实测。

## 用户意图

在现有日语读音 Android 项目基础上，开发 Windows 桌面悬浮工具，功能类似。用户剩余五小时窗口额度较少，要求先做好规划，以便随时中断后续接。本轮交付规划，不代表已交付 Windows 程序，也没有设置自动续跑任务。

## 规划阶段历史状态（已由上方实现快照更新）

- 已阅读 README、Android ReadingEngine、依赖配置与 NEXT 文档；Android 当前基线 0.3.1。
- 已确认本机命令路径可找到 Python、dotnet、Node，但尚未验证 Windows 开发依赖与模型。
- Windows 计划在 docs/WINDOWS_PLAN.md，任务优先级 P0 → P1 → P2 → P3。
- Windows 源码、环境、模型、安装包及测试均未创建或执行。
- 本轮开始时 git status --short 输出为空；本轮只新增规划及本交接文档。

## 下次直接从这里开始

1. 阅读本文件与 WINDOWS_PLAN.md，检查 git status，保留用户已有改动。
2. 查看 Android Romaji/ReadingEngine 的完整实现和测试；不要重新整理全量聊天记录。
3. 检查 Windows 与 Python 版本，建立 windows/ 独立环境。
4. 先实现 P0：日语静态图 OCR/读音，及真实采集与透明层的排除/穿透验证。
5. 通过后直接继续 P1 闭环，不需要再次询问常规技术选型许可。遇到必须由用户指定的产品范围变化时才澄清。

## 每个阶段的落盘要求

在此文件追加：已完成项、准确运行/测试命令、锁定版本和模型位置、真实测试结果、已知失败、下一条操作。未测试不得写成通过。依赖准备中断时保留下载文件和校验信息，避免重复下载。局部实现或未通过检查应明确记录，避免下一轮把存在源码等同于功能完成。

建议在形成可验证里程碑后创建本地提交，只包含本任务相关文件；不覆盖用户改动，不自动推送。不依赖长对话记忆，源代码、测试、计划和此交接记录共同作为恢复依据。

可用于恢复的消息：

> 继续开发 Windows 日语读音悬浮工具。先读 docs/WINDOWS_HANDOFF.md 和 docs/WINDOWS_PLAN.md，核对当前代码，从未完成的最高优先级阶段继续，每个阶段更新交接记录。
