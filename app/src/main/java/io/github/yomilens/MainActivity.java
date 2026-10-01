package io.github.yomilens;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
  static volatile boolean visible = false;
  private LinearLayout page;
  private TextView state, regionText, fontPreview;
  private View dot, regionReset;
  private Button primary;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          update();
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    build();
  }

  private int dp(int n) {
    return Ui.dp(this, n);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private LinearLayout card(int topMargin) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(18), dp(16), dp(18), dp(18));
    card.setBackground(Ui.rounded(this, Ui.CARD, 22, Ui.LINE));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(topMargin);
    page.addView(card, lp);
    return card;
  }

  private Button button(String s, boolean main, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(main ? 16 : 14);
    b.setAllCaps(false);
    b.setTextColor(main ? Ui.PRIMARY_INK : Ui.ACCENT);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(Ui.button(this, main ? Ui.PINK : Ui.SOFT, 16, 0));
    b.setStateListAnimator(null);
    b.setOnClickListener(v -> r.run());
    return b;
  }

  private void build() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Ui.BACKGROUND);
    scroll.setVerticalScrollBarEnabled(false);
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(22), dp(20), dp(22), dp(28));
    scroll.addView(page);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
          return insets;
        });

    // Header: avatar + name.
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageView avatar = new ImageView(this);
    avatar.setImageResource(R.drawable.uni_avatar);
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    avatar.setBackground(Ui.rounded(this, Ui.SOFT, 32, 0));
    avatar.setOutlineProvider(
        new ViewOutlineProvider() {
          public void getOutline(View v, Outline o) {
            o.setOval(0, 0, v.getWidth(), v.getHeight());
          }
        });
    avatar.setClipToOutline(true);
    header.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));
    LinearLayout names = new LinearLayout(this);
    names.setOrientation(LinearLayout.VERTICAL);
    names.setPadding(dp(14), 0, 0, 0);
    TextView name = text("uniLens", 24, Ui.INK);
    name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    names.addView(name);
    names.addView(text("识别屏幕指定区域中的日语，返回罗马音", 13, Ui.MUTED));
    header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(header);

    // Main card: status + actions.
    LinearLayout main = card(22);
    LinearLayout status = new LinearLayout(this);
    status.setGravity(Gravity.CENTER_VERTICAL);
    dot = new View(this);
    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
    dotLp.rightMargin = dp(8);
    status.addView(dot, dotLp);
    state = text("", 13, Ui.MUTED);
    status.addView(state, new LinearLayout.LayoutParams(0, -2, 1));
    status.setMinimumHeight(dp(32));
    main.addView(status);
    TextView sample = text("日本語 → ni·hon·go", 20, Ui.INK);
    sample.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    sample.setPadding(0, dp(10), 0, dp(14));
    main.addView(sample);
    primary = button("开始扫描", true, this::onPrimary);
    main.addView(primary, new LinearLayout.LayoutParams(-1, dp(56)));
    LinearLayout row = new LinearLayout(this);
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(48));
    rowLp.topMargin = dp(10);
    main.addView(row, rowLp);
    row.addView(
        button(
            "停止",
            false,
            () -> {
              stopService(new Intent(this, CaptureService.class));
              CaptureService.status = "已停止";
              update();
            }),
        new LinearLayout.LayoutParams(0, -1, 1));
    LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -1, 1);
    second.leftMargin = dp(10);
    row.addView(
        button("效果预览", false, () -> startActivity(new Intent(this, SampleActivity.class))),
        second);

    // Display settings.
    LinearLayout settings = card(14);
    LinearLayout regionRow = new LinearLayout(this);
    regionRow.setGravity(Gravity.CENTER_VERTICAL);
    regionText = text("", 14, Ui.INK);
    regionRow.addView(regionText, new LinearLayout.LayoutParams(0, dp(48), 1));
    regionText.setGravity(Gravity.CENTER_VERTICAL);
    Button reset =
        button(
            "改为全屏",
            false,
            () -> {
              Prefs.clearRegion(this);
              update();
            });
    reset.setTextSize(13);
    reset.setPadding(dp(14), 0, dp(14), 0);
    regionReset = reset;
    regionRow.addView(reset, new LinearLayout.LayoutParams(-2, dp(40)));
    settings.addView(regionRow);
    settings.addView(text("扫描中点悬浮条「框选」可只读一块区域。", 12, Ui.MUTED));
    View divider = new View(this);
    divider.setBackgroundColor(Ui.LINE);
    LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, 1);
    divLp.topMargin = dp(14);
    divLp.bottomMargin = dp(10);
    settings.addView(divider, divLp);
    LinearLayout fontRow = new LinearLayout(this);
    fontRow.setGravity(Gravity.CENTER_VERTICAL);
    fontRow.addView(text("罗马音字号", 14, Ui.INK), new LinearLayout.LayoutParams(0, -2, 1));
    fontPreview = text("", 13, Ui.MUTED);
    fontRow.addView(fontPreview);
    settings.addView(fontRow);
    SeekBar size = new SeekBar(this);
    size.setContentDescription("罗马音字号");
    size.setMax(18);
    size.setProgress(Prefs.font(this) - 10);
    size.setMinimumHeight(dp(48));
    size.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) Prefs.get(MainActivity.this).edit().putInt("font", p + 10).apply();
            fontPreview.setText((p + 10) + " sp");
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    fontPreview.setText(Prefs.font(this) + " sp");
    settings.addView(size);

    // Footnote.
    TextView note =
        text(
            "全程离线识别，屏幕画面不保存、不上传。若扫描被系统中断，请在应用设置中允许悬浮窗与后台运行。",
            12,
            Ui.MUTED);
    note.setLineSpacing(dp(2), 1);
    LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
    noteLp.topMargin = dp(18);
    page.addView(note, noteLp);
    TextView appSettings = text("打开应用设置", 13, Ui.ACCENT);
    appSettings.setGravity(Gravity.CENTER_VERTICAL);
    appSettings.setMinHeight(dp(48));
    appSettings.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    page.addView(appSettings);
  }

  private void update() {
    if (state == null) return;
    boolean permitted = Settings.canDrawOverlays(this);
    state.setText(permitted ? CaptureService.status : "需要先允许悬浮窗，才能在其他应用上显示罗马音");
    dot.setBackground(
        Ui.rounded(this, CaptureService.running ? Ui.LIME : permitted ? Ui.LINE : 0xffe8a54b, 4, 0));
    primary.setText(!permitted ? "允许悬浮窗" : CaptureService.running ? "扫描中" : "开始扫描");
    primary.setEnabled(!CaptureService.running || !permitted);
    primary.setAlpha(primary.isEnabled() ? 1f : .6f);
    boolean region = Prefs.hasRegion(this);
    regionText.setText(region ? "识别区域：已框选" : "识别区域：全屏");
    regionReset.setVisibility(region ? View.VISIBLE : View.GONE);
  }

  private void onPrimary() {
    if (!Settings.canDrawOverlays(this)) {
      startActivity(
          new Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
      return;
    }
    if (CaptureService.running) return;
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
}
