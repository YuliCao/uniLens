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
  private LinearLayout content, page, actions;
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
    if (!Prefs.get(this).getBoolean("regionDefaultV3", false)) {
      Prefs.get(this).edit().putInt("region", 3).putBoolean("regionDefaultV3", true).apply();
    }
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
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(16), dp(14), dp(16), dp(16));
    card.setBackground(Ui.rounded(this, 0xffffffff, 20, Ui.LINE));
    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
    layout.topMargin = dp(16);
    page.addView(card, layout);
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView heading = text(s, 16, Ui.INK);
    heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
    TextView arrow = text("＋", 20, Ui.MUTED);
    arrow.setGravity(Gravity.CENTER);
    arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    boolean collapsible = !s.equals("显示与响应") && !s.equals("屏幕辅助");
    if (collapsible) header.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(48)));
    card.addView(header);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    card.addView(content);
    if (collapsible) {
      LinearLayout body = content;
      body.setVisibility(View.GONE);
      header.setBackground(Ui.button(this, 0xffffffff, 12, 0));
      header.setContentDescription(s + "，展开");
      header.setOnClickListener(
          v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "−" : "＋");
            header.setContentDescription(s + (show ? "，收起" : "，展开"));
          });
    }
  }

  private Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setPadding(dp(12), 0, dp(12), 0);
    boolean primary = s.equals("开始阅读");
    b.setTextColor(primary ? 0xffffffff : Ui.ACCENT);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(Ui.button(this, primary ? Ui.ACCENT : Ui.SOFT, 14, 0));
    b.setElevation(0);
    b.setStateListAnimator(null);
    b.setOnClickListener(v -> r.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(primary ? 54 : 48));
    lp.topMargin = dp(8);
    if (s.equals("停止辅助") || s.equals("识别测试")) {
      if (actions == null) {
        actions = new LinearLayout(this);
        content.addView(actions);
      }
      lp.width = 0;
      lp.weight = 1;
      if (actions.getChildCount() > 0) lp.leftMargin = dp(8);
      actions.addView(b, lp);
    } else content.addView(b, lp);
    return b;
  }

  private void field(EditText input) {
    input.setTextSize(15);
    input.setTextColor(Ui.INK);
    input.setHintTextColor(Ui.MUTED);
    input.setPadding(dp(12), dp(12), dp(12), dp(12));
    input.setBackground(Ui.rounded(this, Ui.SOFT, 12, 0));
  }

  private ArrayAdapter<String> choices(String[] options) {
    return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options) {
      @Override
      public View getView(int position, View convert, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convert, parent);
        view.setTextSize(14);
        view.setTextColor(Ui.INK);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), 0, dp(24), 0);
        view.setMinHeight(dp(48));
        return view;
      }
    };
  }

  private void build() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Ui.BACKGROUND);
    scroll.setVerticalScrollBarEnabled(false);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(24), dp(24), dp(24), dp(32));
    page = content;
    scroll.addView(page);
    setContentView(scroll);
    content.setPadding(dp(24), dp(16), dp(24), dp(24));
    scroll.setClipToPadding(true);
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
          return insets;
        });
    TextView badge = text("YOMILENS  /  日语透镜", 12, Ui.ACCENT);
    badge.setLetterSpacing(.1f);
    content.addView(badge);
    TextView hero = text("让日语，读得出来。", 26, Ui.INK);
    hero.setTypeface(null, Typeface.BOLD);
    content.addView(hero);
    content.addView(text("圈选日语，即刻读音。全程离线处理。", 14, Ui.MUTED));
    title("屏幕辅助");
    state = text(CaptureService.status, 12, Ui.MUTED);
    state.setMinHeight(dp(36));
    content.addView(state);
    button(
        "悬浮显示权限",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()))));
    button("开始阅读", this::startCapture);
    button(
        "停止辅助",
        () -> {
          stopService(new Intent(this, CaptureService.class));
          CaptureService.status = "已停止";
          state.setText(CaptureService.status);
        });
    button("识别测试", () -> startActivity(new Intent(this, SampleActivity.class)));
    title("显示与响应");
    addChoice("标注模式", new String[] {"罗马音", "振假名（平假名）", "原文 + 罗马音"}, "mode", 0);
    addChoice("扫描区域", new String[] {"全屏", "下半屏（游戏对话）", "中部（漫画 / 网页）", "框选区域（默认）"}, "region", 3);
    CheckBox kanaOnly = new CheckBox(this);
    kanaOnly.setText("只标注含假名的行（略过纯汉字菜单）");
    kanaOnly.setTextSize(13);
    kanaOnly.setTextColor(Ui.MUTED);
    kanaOnly.setMinHeight(dp(48));
    kanaOnly.setChecked(Prefs.get(this).getBoolean("kanaOnly", false));
    kanaOnly.setOnCheckedChangeListener(
        (button, checked) -> Prefs.get(this).edit().putBoolean("kanaOnly", checked).apply());
    content.addView(kanaOnly);
    TextView speedLabel = text("识别频率：静止画面自动降频", 14, Ui.MUTED);
    content.addView(speedLabel);
    Spinner speed = new Spinner(this);
    speed.setAdapter(choices(new String[] {"快速 · 0.35 秒间隔", "均衡 · 0.75 秒间隔", "省电 · 1.5 秒间隔"}));
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
    content.addView(text("标注字号", 14, Ui.MUTED));
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
    field(sample);
    content.addView(sample);
    preview = text("在这里检查离线读音。", 16, Ui.INK);
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
    content.addView(text("每行一项：原文=假名。句中按最长专名优先匹配，最多 500 项。", 13, Ui.MUTED));
    EditText dict = new EditText(this);
    dict.setHint("八重神子=やえみこ\n原神=げんしん");
    dict.setMinLines(3);
    dict.setGravity(Gravity.TOP);
    dict.setText(Prefs.get(this).getString("dictionary", ""));
    field(dict);
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
            Ui.MUTED));
    button(
        "打开应用系统设置",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    title("隐私");
    content.addView(text("屏幕图像只在内存中处理，不保存、不上传。OCR 模型与读音词典随 APK 内置。停止辅助即释放屏幕采集。", 14, Ui.MUTED));
  }

  private void addChoice(String label, String[] options, String key, int def) {
    content.addView(text(label, 14, Ui.MUTED));
    Spinner s = new Spinner(this);
    s.setAdapter(choices(options));
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
