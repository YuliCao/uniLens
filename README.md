# uniLens日语助手

适用于 Android 8.0+ 的离线日语屏幕扫描工具：识别屏幕上的日语，在每行旁显示按音节分隔的罗马音。优先支持 vivo / OriginOS 侧载。

当前开发版：`artifacts/apk/uniLens-0.4.1-arm64.apk`（多数较新的手机），`artifacts/apk/uniLens-0.4.1-universal.apk`（含 32 位与模拟器架构）。基于用户的 0.4.0 修复继续更新，包名与签名不变，可覆盖安装。

已在 Android 15 模拟器完成扫描、框选、横竖屏、点击穿透和停止释放验证；尚无 vivo 真机结果。

## 使用

1. 首页点「允许悬浮窗」并授权，再点「开始扫描」，在系统对话框选择整个屏幕。
2. 切到游戏、漫画或网页，罗马音显示在每行附近。悬浮条可拖动：「暂停」隐藏罗马音，「框选」拖出只识别的区域，「×」结束扫描。
3. 框选区域会保存，首页「改为全屏」可清除。首页还可调罗马音字号。
4. 「效果预览」提供固定日语页面，用来检查标注与点击穿透。
5. vivo 上请在应用权限与电池管理中允许悬浮窗和后台运行。

## 功能

- MediaProjection 屏幕授权与前台服务，内置 ML Kit 日语 OCR，整行 Kuromoji IPADIC 分析。
- 罗马音按音节分隔：`日本語 → ni·hon·go`、`東京 → tou·kyou`、`学校 → gak·kou`。
- 识别出的表情、装饰符号等用 `｜` 占位隔断，例如 `日本語🙂東京 → ni·hon·go ｜ tou·kyou`；连续符号合并，括号、引号、常用标点及普通英文数字继续保留。只能处理 OCR 已返回的符号，不能补回漏识别的表情。
- 全屏或手动框选区域；透明标注层不接收触摸，标注避让原文与控制条。
- 变化检测、静止降频、512 条 LRU 读音缓存；刷新时保持标注可见。
- 旋转时先更新接收 Surface 再调整虚拟显示，立即废弃旧结果。

0.4.0 精简了首页：移除平假名 / 原文对照模式、预设区域、识别频率、仅假名过滤、离线试读和专名纠音的界面入口，配色改为与头像一致的银白、淡紫与浅绿。`ReadingEngine` 仍保留纠音接口，便于以后恢复。

## 界面风格

0.4.1 首页采用从头像脸颊取样的浅粉 `#FDE2E8` 与头发取样的近白 `#FEFDFD`，配深玫瑰色文字；浅绿状态点及区域边框继续保留。标题下介绍为「识别屏幕指定区域中的日语，返回罗马音」。统一圆角，首页只保留「开始扫描」一个主操作。

## 准确性边界

首版是行级邻近标注，不是逐字振假名排版。未知汉字保留原文并使用提示色。IPADIC 基于句内形态分析选择读音，不能保证人名、地名、游戏专名和所有多音汉字正确。音节分隔用于辅助阅读：拗音不拆开，拨音与促音归入前一节；长音保留显式元音（例如 `tou·kyou`、`koo·hii`），相邻相同元音及 `ou / ei` 默认合并，不能保证识别所有跨词素的元音边界。助词 は / へ / を 按 `wa / e / o` 处理。

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
