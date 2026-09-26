# 验证记录 · 0.1.0

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
