## 0.3.2 罗马音音节分隔

日期：2026-09-30。验证环境为 Android 15 / API 35 x86_64 模拟器。

- 罗马音、原文对照及离线试读使用 `·` 分隔；保留词间空格、括号与标点。`日本語 → ni·hon·go`、`東京 → tou·kyou`、`学校 → gak·kou`、`抹茶 → mat·cha`。拗音不拆开，拨音与促音归入前节；长音保留显式元音。相邻相同元音及 `ou / ei` 默认合并，是阅读分隔规则，不能完全判断词素内部的元音边界。
- 英文数字保持完整；专名纠音、助词和跨分词边界的促音使用相同规则。标注换行优先使用词间空格，其次使用音节边界。
- 30/30 JVM 测试通过，包含新增音节、长音、促音、拨音、外来语、标点与缓存回归。构建、Lint 通过（0 errors / 15 warnings）。
- 关闭 Wi-Fi 与移动数据后，bundled OCR + 词典设备测试 2/2 通过（14.217 秒），直接检查识别后的 `ni·hon·go` 与 `tou·kyou`。见 `offline-032.log`。
- 默认框选、区域重选、横竖屏、暂停恢复、三种模式及底层按钮切页通过；人工查看分隔符与换行截图。见 `smoke-032.json`、`syllables-romaji.png`、`syllables-original.png`。
- 独立外部测试应用两页各四行的识别位置、点击穿透及停止释放 MediaProjection 通过；崩溃缓冲为空。见 `cross-app-032.json`。
- 分隔罗马音模式连续 25 秒、129 次采样、完成 8 次 OCR；刷新期间清空标注次数增量为 0，标注与边框始终可见。见 `refresh-stability-romaji.json`。命令：`python scripts/refresh_stability.py --adb .tools/android-sdk/platform-tools/adb.exe --mode romaji`，前置是 `emulator_smoke.py --keep-running`。
- ARM64 与通用 APK 的 v2 签名验证通过，ARM64 包通过 16 KB ZIP 对齐检查；模拟器安装包与通用交付 APK 的 SHA256 一致。新版未在物理 vivo 手机上实测。

## 0.3.1 简洁 UI 更新

- 统一浅色背景、青绿强调色、圆角按钮与卡片；试读、专名纠音、后台及隐私说明可展开/收起。
- 原生 Spinner、输入框及按钮保留；常用操作集中展示，次要操作并排。修复 Android 15 边到边显示下滚动内容进入状态栏的问题。
- 悬浮条仅调整背景、圆角和按压反馈，保留按钮文字及操作。
- 构建与 Lint 通过（0 errors / 15 warnings）；Android 8.0 使用兼容的导航栏样式，较新系统使用浅色导航栏。
- 人工查看首页、滚动设置和展开试读截图；展开/收起及生成离线读音可正常使用。截图为 `ui-home.png`、`ui-settings.png`、`ui-expanded.png`。
- 更新后的按钮入口通过完整模拟器操作回归：默认框选、横竖屏、暂停恢复、三种模式、重选区域、切页和停止释放通过，见 `smoke-031.json`。通用交付 APK 与实际安装测试 APK 的 SHA256 一致；ARM64 包签名验证通过。

## 0.3.0 真机反馈改进

日期：2026-09-27。当前验证为 API 35 模拟器，未对用户手机远程实测。

- 默认框选：新安装及从 0.2.1 升级首次打开设置时迁移为自定义区域；离开设置进入阅读 App 时自动框选。取消首次框选会暂停，不会默默切成全屏识别。
- 框选后常显青色区域边框，标注更新不会清除它；边框在无触摸权限的标注窗口绘制，不拦截底层操作。旋转后按归一化区域更新。
- 标点：词典处理时直接保留符号 token；宽度归一化仅处理假名及兼容的全角字母数字，不改写括号、引号和全角标点。新增 `「」（）【】『』、。！？…` 回归测试。保留的是 OCR 已识别出的符号，不能补回 OCR 漏掉的标点。
- 刷新：删除每次截图前隐藏标注及识别前清空标注的流程，完成新 OCR 后替换结果。仅在识别副本中填补自身标注区域；填补色取周围背景，避免黑色遮罩被检测成文字。
- 24 项 JVM 测试通过；构建、Lint 通过（0 errors / 15 warnings）。默认框选、横竖屏、暂停恢复、三种模式、重选区域、切页通过。
- 最终断网设备测试 2/2 通过（12.297 秒）；跨应用两页各四行识别框检查、点击穿透及停止释放通过。证据为 `offline-030.log`、`cross-app-030.json`。
- `refresh_stability.py` 在假名模式连续观察 25 秒，共采样 116 次，完成 8 次 OCR；清空标注次数增量为 0，悬浮标注和边框始终可见。证据为 `refresh-stability.json`、`steady-kana.png` 和 `smoke-030.json`。
- 新策略不需要隐藏标注，但不能恢复标注遮挡住的原始像素；目前仍适合较稳定的文字画面，快速滚动、密集文本存在漏识别风险。新结果返回前旧标注会短暂保留。

# 验证记录

## 0.2.1 旋转修复与初版回归

日期：2026-09-26。验证设备为 Android 15 / API 35 x86_64 模拟器，未连接 vivo 真机。

- 修复旋转时虚拟显示与接收 Surface 的更新顺序：先提供新 Surface，再调用 `resize`。旧顺序存在竞态，系统可能按旧 Surface 尺寸计算缩放，产生留边、识别框错位及控制条误识别。仅增加等待时间仍能复现，未采用它作为根本修复。
- 配置变化立即增加识别代次、清除旧标注；旋转过渡帧通过时间戳丢弃。每次 OCR 固定使用采样时的源尺寸和图像尺寸映射坐标。
- 严格回归完成 8 轮横屏/竖屏往返，16 次方向变化。逐轮把四行日语识别框与 UI 中原文控件位置比较，出现非空但错位的结果立即失败；本轮全部通过。
- 暂停清空标注、恢复、三种模式、下部区域框选、重新扩大区域、底层按钮切页、停止后释放 MediaProjection 全部通过。测试期间应用崩溃缓冲为空。
- 23/23 JVM 测试通过；构建与 Lint 通过（0 errors / 15 warnings）。ARM64 和通用包签名通过，ARM64 ZIP 16 KB 对齐检查通过。
- 最终断网设备测试 2/2 通过（11.076 秒）。内置 OCR 的 960 / 1280 / 1600 长边样本各运行 5 次，p50 为 535 / 504 / 573 ms；仍只是模拟器纯 OCR 数据。
- 独立测试 APK（`io.github.yomilens.test`）前台显示日语，主应用服务在后台采集：两页各四行的位置检查通过，悬浮标注存在时点击外部应用按钮可正常切页。测试 APK 不随用户安装包分发。
- 人工复核最后一轮横屏及假名模式截图，日语下划线与原文字行对应。纯汉字中文仍可能被日语模型识别，可开启“只标注含假名的行”过滤。
- 本轮屏幕 OCR+读音耗时随主机负载约 1–3 秒，不能将历史较快数据当作固定实时指标；旋转还有约 0.9 秒稳定等待。未测试 vivo 功耗、温升或 OriginOS 保活。

证据：`artifacts/validation/rotation-regression.json`、`rotation-regression.log`、`rotation-8-landscape.png`、`rotation-8-portrait.png`、`mode-*.png`、`region-*.png`。APK 位于 `artifacts/apk`，SHA256SUMS 可核对文件。

跨应用独立回归记录为 `cross-app.json`、`cross-app.log`、`external-app*.png`，停止后共享会话为 null；断网设备测试输出为 `offline-021.log`。UI 脚本清理测试应用旧任务栈，避免重跑时继承上次页面。所有自动化仅允许 emulator 序列号。

更新顺序分析依据为 Android 15 AOSP 的 [VirtualDisplayAdapter](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/display/VirtualDisplayAdapter.java) 与 [ContentRecorder](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/wm/ContentRecorder.java)：镜像变换会查询消费 Surface 的默认尺寸。

增强回归命令（先安装通用 APK 和 androidTest APK）：

```powershell
python scripts/emulator_smoke.py --adb .tools/android-sdk/platform-tools/adb.exe --rotation-cycles 8
python scripts/emulator_smoke.py --adb .tools/android-sdk/platform-tools/adb.exe --rotation-cycles 1 --external-fixture
```

## 0.2.0 增量验证（历史，已被 0.2.1 替代）

- 最新截图复核发现横竖屏切换后仍会间歇出现标注错位及控制条被识别。0.2.0 属于实验构建，尚未通过旋转稳定性验收；下方自动化通过只证明流程和服务生命周期，不能证明标注位置正确。

- 23/23 JVM 单元测试通过；`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过（0 errors / 15 warnings）。
- 断网设备测试仍为 2/2 通过。通用 APK 在 API 35 x86_64 模拟器运行；ARM64 APK 进行了架构、签名和打包检查，未在物理 ARM64 手机运行。
- 新增纯假名行过滤、补充平面汉字检测、标注避让及连接线；渲染布局缓存减少重复绘制分配。
- ImageReader 持续释放过期图像，只保留最新帧；采样时检查单调时钟时间戳，避免 UI 忙时读到隐藏悬浮层之前的帧。
- 控制条保持显示，其像素在识别副本中被覆盖。受控制条遮挡的识别框跳过，默认控制条靠右以减少覆盖正文。
- 区域框选后确认得到四行日语标注，无崩溃；样本诊断帧龄为 10–40 ms。帧龄不是 OCR 或端到端延迟。
- 通用包约 61 MB，ARM64 包约 31 MB。ARM64 OCR 原生库的所有 PT_LOAD 段为 16384 字节对齐；这只是文件检查，不等于 Android 16 真机测试。
- APK 校验和位于 `artifacts/apk/SHA256SUMS.txt`。最新 UI 自动化截图保存在 `artifacts/validation`，历史 0.1.0 证据可在 Git 首次提交查看。

## 0.1.0 基线

日期：2026-09-26（Asia/Shanghai）。开发签名 APK，非应用商店正式发行。

## 构建与自动化

- JDK 21、Gradle 8.13、AGP 8.13.0、compile SDK 36、target SDK 35、min SDK 26。
- `assembleDebug`、`testDebugUnitTest`、`lintDebug` 成功。
- 16 个 JVM 单元测试全部通过：Hepburn、促音/拗音/拨音、跨分词边界的促音、助词、专名最长匹配、未知汉字与细小图像变化。
- Lint：0 errors / 16 warnings。警告包含国际化资源、绘制时分配、可访问性提示和新版本建议，未隐藏错误检查。
- APK v2 签名校验通过，签名者为 Android 开发调试签名。

## 设备测试

环境：Windows WHPX 上的 Android 15 / API 35 / x86_64 模拟器，1080×1920、420 dpi、SwiftShader。没有连接物理 vivo 手机。

1. 关闭模拟器 Wi-Fi 和移动数据，验证安装包无 `INTERNET` 权限。
2. `OfflinePipelineTest`：2/2 通过。生成日语图片，经 bundled OCR 识别，再用 IPADIC 生成 `nihongo` 和 `toukyou`，并验证识别框存在。
3. `scripts/emulator_smoke.py`：通过授权对话框启动前台服务；覆盖层存在时点击底层按钮，页面从第一组日语切换至第二组；横屏后转回竖屏；返回设置停止后，`dumpsys media_projection` 不再存在本应用会话。
4. 人工检查保存的截图：文字行与下划线位置匹配；旋转后位置恢复。控制条附近及文字密集处仍可能遮挡。
5. 手动验证暂停清除标注、恢复、区域框选、三种模式。区域框选使用屏幕绝对坐标归一化，避免导航栏高度造成偏移。
6. 测试期间本应用 crash buffer 为空。

证据：`artifacts/validation/smoke.json`、`portrait.png`、`landscape.png`、`portrait-after-rotation.png`、`page-two.png`。区域与暂停截图是同一开发过程中的手工回归记录。

## 性能数据的适用范围

设备内生成四行清晰日语，纯 OCR 每种尺寸运行五次。最新一轮长边 960 / 1280 / 1600 的 p50 分别为 222 / 188 / 252 ms，最大值为 490 / 279 / 299 ms。较早一轮在开发机其他构建任务运行期间，p50 为 678 / 677 / 755 ms。样本很小，存在预热、主机负载与运行顺序影响；不是 vivo 成绩，也不是端到端延迟或能耗测量。

最新屏幕示例中显示的 OCR+读音约 359–393 ms。扫描间隔默认 750 ms，另有采集前 80 ms 隐藏悬浮层等待，因此不能据此宣称每秒 30 帧或无感实时。

## 尚未验证 / 已知限制

- vivo / OriginOS 后台保活、系统悬浮权限差异、真实耗电与温度、长时间游戏负载。
- API 26–34 和 Android 16 真机；静态 API 检查不能替代运行验证。
- 漫画竖排、花体、极小文字、动画中运动补偿与逐字振假名。
- 只含汉字的中文可能被当作日语；首版没有独立语种过滤。
- 人名、日期及语义相关多音词仍可能读错。用户专名词典可以纠正指定字符串。
- 标注层为避免自识别会短暂隐藏，存在闪动。行框定位不等于逐字对应。
- 屏幕受 DRM / FLAG_SECURE 保护时不可读取；系统可撤销或终止共享会话。

## 重现

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w io.github.yomilens.test/androidx.test.runner.AndroidJUnitRunner
python scripts/emulator_smoke.py --adb path/to/adb.exe --serial emulator-5554
```

smoke 脚本只允许模拟器设备，并会清空该模拟器中 YomiLens 的测试数据。它不会操作真机。
