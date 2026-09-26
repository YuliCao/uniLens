package io.github.yomilens;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  static volatile boolean visible = false;
  private LinearLayout content;
  private TextView state, preview;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private ReadingEngine demoEngine;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          if (state != null) state.setText(CaptureService.status);
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    build();
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(6), 0, dp(6));
    return t;
  }

  private void title(String s) {
    TextView t = text(s, 19, 0xff16383c);
    t.setTypeface(null, Typeface.BOLD);
    content.addView(t);
  }

  private Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setOnClickListener(v -> r.run());
    content.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
    return b;
  }

  private void build() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(0xfff3f6f7);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(24), dp(24), dp(24), dp(32));
    scroll.addView(content);
    setContentView(scroll);
    content.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              dp(24),
              dp(16) + insets.getSystemWindowInsetTop(),
              dp(24),
              dp(24) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    TextView badge = text("YOMILENS  /  日语透镜", 13, 0xff167d8d);
    badge.setLetterSpacing(.14f);
    content.addView(badge);
    TextView hero = text("让日语，读得出来。", 30, 0xff153b40);
    hero.setTypeface(null, Typeface.BOLD);
    content.addView(hero);
    content.addView(text("游戏 · 漫画 · 网页\n离线识别屏幕日语，在原文附近显示读音。", 15, 0xff566a70));
    state = text(CaptureService.status, 14, 0xff167d8d);
    content.addView(state);
    button(
        "① 允许悬浮显示",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()))));
    button("② 开始屏幕辅助", this::startCapture);
    button(
        "停止辅助",
        () -> {
          stopService(new Intent(this, CaptureService.class));
          CaptureService.status = "已停止";
          state.setText(CaptureService.status);
        });
    button("打开识别测试页", () -> startActivity(new Intent(this, SampleActivity.class)));
    title("显示与响应");
    addChoice("标注模式", new String[] {"罗马音", "振假名（平假名）", "原文 + 罗马音"}, "mode", 0);
    addChoice("扫描区域", new String[] {"全屏", "下半屏（游戏对话）", "中部（漫画 / 网页）", "自定义：启动后点「框选」"}, "region", 0);
    TextView speedLabel = text("识别频率：静止画面自动降频", 14, 0xff566a70);
    content.addView(speedLabel);
    Spinner speed = new Spinner(this);
    speed.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"快速 · 约 0.35 秒间隔", "均衡 · 约 0.75 秒间隔", "省电 · 约 1.5 秒间隔"}));
    int val = Prefs.interval(this);
    speed.setSelection(val == 350 ? 0 : val == 1500 ? 2 : 1);
    speed.setOnItemSelectedListener(
        new SimpleSelection() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            Prefs.get(MainActivity.this)
                .edit()
                .putInt("interval", new int[] {350, 750, 1500}[pos])
                .apply();
          }
        });
    content.addView(speed);
    content.addView(text("标注字号", 14, 0xff566a70));
    SeekBar size = new SeekBar(this);
    size.setMax(18);
    size.setProgress(Prefs.get(this).getInt("font", 14) - 10);
    size.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) Prefs.get(MainActivity.this).edit().putInt("font", p + 10).apply();
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    content.addView(size);
    title("试读一句日语");
    EditText sample = new EditText(this);
    sample.setText("今日は日本語を勉強します。東京へ行きます。");
    sample.setMinLines(2);
    content.addView(sample);
    preview = text("在这里检查离线读音。", 16, 0xff153b40);
    preview.setTextIsSelectable(true);
    button(
        "生成读音",
        () -> {
          preview.setText("正在载入离线词典…");
          String input = sample.getText().toString(),
              dict = Prefs.get(this).getString("dictionary", "");
          worker.execute(
              () -> {
                try {
                  if (demoEngine == null) demoEngine = new ReadingEngine();
                  demoEngine.setOverrides(dict);
                  ReadingEngine.Reading r = demoEngine.read(input);
                  runOnUiThread(
                      () ->
                          preview.setText(
                              r.kana
                                  + "\n\n"
                                  + r.romaji
                                  + (r.uncertain ? "\n含未收录汉字，请添加专名纠音。" : "")));
                } catch (Exception e) {
                  runOnUiThread(() -> preview.setText("词典加载失败：" + e.getMessage()));
                }
              });
        });
    content.addView(preview);
    title("专名纠音");
    content.addView(text("每行一项：原文=假名。句中按最长专名优先匹配，最多 500 项。", 13, 0xff566a70));
    EditText dict = new EditText(this);
    dict.setHint("八重神子=やえみこ\n原神=げんしん");
    dict.setMinLines(3);
    dict.setGravity(Gravity.TOP);
    dict.setText(Prefs.get(this).getString("dictionary", ""));
    content.addView(dict);
    button(
        "保存纠音词典",
        () -> {
          Prefs.get(this).edit().putString("dictionary", dict.getText().toString()).apply();
          Toast.makeText(this, "已保存，下一轮识别生效", Toast.LENGTH_SHORT).show();
        });
    title("vivo 后台使用");
    content.addView(
        text(
            "在系统设置中允许悬浮窗、通知和后台高耗电运行，并允许自启动；必要时在最近任务中锁定本应用。不同 OriginOS / Funtouch OS 的入口可能不同。\n\n"
                + "开始时请选择「整个屏幕」。系统每次开始都需要确认屏幕共享。标注层不接收触摸；小控制条可拖动、暂停和框选。\n\n"
                + "本版按文字行标注，竖排、花体、极小文字及专有名词可能误识别。受保护视频、银行或禁止截屏的页面无法识别。",
            14,
            0xff566a70));
    button(
        "打开应用系统设置",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    title("隐私");
    content.addView(text("屏幕图像只在内存中处理，不保存、不上传。OCR 模型与读音词典随 APK 内置。停止辅助即释放屏幕采集。", 14, 0xff566a70));
  }

  private void addChoice(String label, String[] options, String key, int def) {
    content.addView(text(label, 14, 0xff566a70));
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
    s.setSelection(Prefs.get(this).getInt(key, def));
    s.setOnItemSelectedListener(
        new SimpleSelection() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            Prefs.get(MainActivity.this).edit().putInt(key, pos).apply();
          }
        });
    content.addView(s);
  }

  private abstract static class SimpleSelection implements AdapterView.OnItemSelectedListener {
    public void onNothingSelected(AdapterView<?> p) {}
  }

  private void startCapture() {
    if (!Settings.canDrawOverlays(this)) {
      Toast.makeText(this, "请先允许悬浮显示", Toast.LENGTH_LONG).show();
      return;
    }
    if (CaptureService.running) {
      Toast.makeText(this, "辅助已经运行，请使用悬浮控制条", Toast.LENGTH_SHORT).show();
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        && !Prefs.get(this).getBoolean("notificationAsked", false)) {
      Prefs.get(this).edit().putBoolean("notificationAsked", true).apply();
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 10);
      return;
    }
    launchCapture();
  }

  private void launchCapture() {
    MediaProjectionManager manager =
        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    Intent capture =
        Build.VERSION.SDK_INT >= 34
            ? manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay())
            : manager.createScreenCaptureIntent();
    startActivityForResult(capture, 20);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 10) launchCapture();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 20 && result == RESULT_OK && data != null) {
      Intent i =
          new Intent(this, CaptureService.class).putExtra("result", result).putExtra("data", data);
      startForegroundService(i);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    visible = true;
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    visible = false;
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }
}
