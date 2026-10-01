# YomiLens · 日语透镜

适用于 Android 8.0+ 的本地日语屏幕读音辅助工具，优先支持 vivo / OriginOS 的侧载使用。

当前开发版：`artifacts/apk/YomiLens-0.3.3-arm64.apk`（适合多数较新的 vivo 手机），以及 `artifacts/apk/YomiLens-0.3.3-universal.apk`（含 32/64 位 ARM 与模拟器架构）。与旧版使用相同开发签名，可直接覆盖安装。进入阅读时不自动弹出框选，沿用已保存区域；未选过区域时先全屏识别。头像图标采用 `uni-image.png` 的近景版本，罗马音 `·` 分隔、简洁 UI 和用户专名词典继续保留。

已完成 Android 15 模拟器的断网 OCR、横竖屏切换、区域选择、模式切换、跨应用点击穿透和停止释放验证；尚无新版 vivo 真机结果。

## 使用

1. 安装 APK，在首页点击「悬浮显示权限」并授予权限。
2. 点击「开始阅读」，在系统对话框选择整个屏幕并确认。
3. 切换到游戏、漫画或网页后直接阅读，不会自动弹出框选。点悬浮条「框选」后拖动选定区域，青色边框持续显示；下次启动沿用区域，未选过区域时先全屏识别。轻点取消框选会继续使用此前区域。屏幕上方的小控制条可以拖动；「暂停」隐藏读音，「模式」切换罗马音 / 平假名 / 原文对照，「×」完全结束采集。
4. 在 vivo 的应用权限、电池后台管理中允许悬浮窗和后台运行。实际入口随系统版本变化。系统仍可能终止进程；终止后需要重新授权屏幕共享。
5. 用首页「识别测试」验证标注、模式切换和点击穿透，不需要其他 App 或联网。

不要把「暂停」误认为结束共享：暂停只停止 OCR；「停止辅助」、控制条「×」或通知「停止」才会释放共享会话。

## 已实现的首版范围

- Android MediaProjection 屏幕授权与前台服务。
- 内置 ML Kit 日语 OCR 模型，整行 Kuromoji IPADIC 形态分析与 Hepburn 罗马音。
- 罗马音按音节分隔：`日本語 → ni·hon·go`、`東京 → tou·kyou`、`学校 → gak·kou`。悬浮标注、原文对照及离线试读统一显示；英文、数字和标点不拆分。
- 全屏、下半屏、中部与手动框选区域。
- 不接收触摸的透明标注层、可拖动控制条、三种显示模式与字号设置。
- 变化检测、静止降频、512 条 LRU 读音缓存。
- 离线试读与专名纠音词典（`原文=假名`）。
- 可选仅识别含假名的行；标注避让原文及控制条。
- 刷新时保持标注可见，只在新结果完成时替换；识别副本中用周边背景色排除自身标注、边框与控制条。
- 旋转时先更新接收 Surface 再调整虚拟显示，立即废弃旧结果；过渡期间短暂等待画面稳定。

## 界面风格

保持简洁：浅色背景、低饱和青绿、清晰主次和统一圆角。首页保留单一主操作「开始阅读」，停止与测试并排；试读、专名纠音、后台说明与隐私说明按需展开。后续功能优先归入相应设置区，避免增加装饰、动效或挤满首页。

## 准确性边界

首版是行级邻近标注，不是逐字振假名排版。未知汉字保留原文并使用提示色。IPADIC 基于句内形态分析选择读音，不能保证人名、地名、游戏专名和所有多音汉字正确。专名纠音优先于内置词典。音节分隔用于辅助阅读：拗音不拆开，拨音与促音归入前一节；长音保留显式元音（例如 `tou·kyou`、`koo·hii`），相邻相同元音及 `ou / ei` 默认合并，不能保证识别所有跨词素的元音边界。助词 は / へ / を 按 `wa / e / o` 处理。

竖排、艺术字体、低分辨率、剧烈动画、透明叠字和受保护画面存在限制。刷新不再主动隐藏悬浮层。遮罩只能排除自身绘制，无法还原被标注覆盖的真实像素；密集排版或快速移动的原文仍可能漏识别。频率设置表示最小扫描等待时间，不是已承诺的端到端延迟。真实延迟包含采集、OCR、词典与显示，并随设备变化。

## 构建

需要 JDK 17+、Android SDK Platform 36、Build Tools 和 Gradle 8.13。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

本工作区可直接执行 `./scripts/build.ps1`，它使用隔离的工具缓存。ARM64 单架构包使用 `./scripts/build.ps1 -Tasks ':app:assembleDebug','-PtargetAbi=arm64-v8a'`。生成文件名仍为 `app-debug.apk`，分发目录中的版本化文件不会混淆架构。

在 `local.properties` 中设置本机 `sdk.dir`。默认生成 `app/build/outputs/apk/debug/app-debug.apk`，为开发签名版本，可侧载。发布到应用商店前需要由所有者管理正式签名。

## 实现结构

- `CaptureService`：前台共享会话、屏幕帧、区域裁剪、变化检测与结果调度。
- `ReadingEngine`：整行分词、读音缓存和人工纠音。
- `Romaji`：假名归一化、拗音、促音、拨音与外来语组合。
- `OverlayView`：将采集坐标映射回屏幕并显示行级标注。
- `MainActivity` / `SampleActivity`：设置、授权、离线试读与可重复测试画面。

应用不保存屏幕帧。清单显式移除网络权限；OCR 与词典在本机执行。运行诊断可用 `adb shell dumpsys activity service io.github.yomilens/.CaptureService`，只包含尺寸、计数、耗时、帧龄、状态和几何坐标，不包含识别文字。

## 依赖与依据

- [ML Kit 日语文本识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)：使用 bundled `text-recognition-japanese:16.0.1`，模型随 APK 分发。
- [Android MediaProjection](https://developer.android.com/media/grow/media-projection)：每次用户授权一个会话，注册停止回调并使用前台服务。
- [Kuromoji](https://github.com/atilika/kuromoji)：Apache 2.0 开源日语形态分析器；IPADIC 及第三方许可随依赖保留。

当前没有连接 vivo 真机；模拟器结果不能替代 vivo 的后台保活、触摸和功耗验证。完整测试结果见 `docs/TESTING.md`。
