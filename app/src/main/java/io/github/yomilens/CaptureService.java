package io.github.yomilens;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CaptureService extends Service {
  public static volatile boolean running = false;
  public static volatile String status = "尚未启动 · 模型内置 / 离线运行";
  private final Handler main = new Handler(Looper.getMainLooper());
  private HandlerThread thread;
  private Handler analysis;
  private MediaProjection projection;
  private VirtualDisplay display;
  private ImageReader reader;
  // Worker-owned latest frame. Drain the producer continuously to avoid queued stale frames.
  private Image latestImage;
  private TextRecognizer recognizer;
  private ReadingEngine readings;
  private WindowManager wm;
  private OverlayView overlay;
  private LinearLayout controls;
  private TextView info;
  private Button pauseButton;
  private WindowManager.LayoutParams overlayParams, controlParams;
  private volatile boolean stopped = false, paused = false, selecting = false;
  private int screenW, screenH, captureW, captureH, dpi;
  private volatile int[] lastSignature;
  private int stableFrames = 0;
  private volatile int generation = 0;
  private long lastOcrAt = 0;
  private final AtomicBoolean inFlight = new AtomicBoolean();
  private String dictionary = "\u0000";
  private int lastRegion = -1;
  private long scans = 0, skips = 0;
  private volatile long framesReceived = 0, lastOcrMs = 0;
  private volatile List<RectF> annotationMasks = Collections.emptyList();
  private volatile long sampledFrameAgeMs = 0;
  // Discard animation/letterboxed frames while the display and capture surface settle.
  private volatile long geometryReadyNanos;
  private volatile String frameDetails = "";
  private View selector;
  private volatile Rect controlBounds = new Rect();
  private final Runnable tick = this::beginSample;

  @Override
  public void onCreate() {
    super.onCreate();
    thread = new HandlerThread("YomiLens-analysis", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();
    analysis = new Handler(thread.getLooper());
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "stop".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (running) return START_NOT_STICKY;
    if (intent == null || !intent.hasExtra("data")) {
      stopSelf();
      return START_NOT_STICKY;
    }
    try {
      NotificationManager nm = getSystemService(NotificationManager.class);
      nm.createNotificationChannel(
          new NotificationChannel("capture", "屏幕日语辅助", NotificationManager.IMPORTANCE_LOW));
      PendingIntent open =
          PendingIntent.getActivity(
              this,
              0,
              new Intent(this, MainActivity.class),
              PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      PendingIntent stop =
          PendingIntent.getService(
              this,
              1,
              new Intent(this, CaptureService.class).setAction("stop"),
              PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      Notification n =
          new Notification.Builder(this, "capture")
              .setSmallIcon(io.github.yomilens.R.drawable.ic_lens)
              .setContentTitle("YomiLens 正在辅助阅读")
              .setContentText("屏幕只在本机处理 · 点停止结束采集")
              .setContentIntent(open)
              .setOngoing(true)
              .addAction(new Notification.Action.Builder(null, "停止", stop).build())
              .build();
      if (Build.VERSION.SDK_INT >= 29)
        startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      else startForeground(7, n);
      Intent data = intent.getParcelableExtra("data");
      projection =
          ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE))
              .getMediaProjection(intent.getIntExtra("result", Activity.RESULT_CANCELED), data);
      if (projection == null) throw new IllegalStateException("屏幕授权已失效，请重新开始");
      projection.registerCallback(
          new MediaProjection.Callback() {
            @Override
            public void onStop() {
              if (!stopped) {
                status = "系统已结束屏幕共享";
                stopSelf();
              }
            }

            @Override
            public void onCapturedContentResize(int width, int height) {
              resizeCapture(width, height);
            }
          },
          main);
      dimensions();
      createReader();
      display =
          projection.createVirtualDisplay(
              "YomiLens",
              captureW,
              captureH,
              dpi,
              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
              reader.getSurface(),
              null,
              analysis);
      createOverlay();
      running = true;
      status = "正在加载离线 OCR 与读音词典…";
      analysis.post(
          () -> {
            try {
              recognizer =
                  TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
              readings = new ReadingEngine();
              main.post(
                  () -> {
                    if (!stopped) {
                      status = "运行中 · 等待日语画面";
                      main.post(tick);
                    }
                  });
            } catch (Exception e) {
              fail(e);
            }
          });
    } catch (Exception e) {
      fail(e);
    }
    return START_NOT_STICKY;
  }

  private void dimensions() {
    DisplayMetrics m = new DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(m);
    dpi = m.densityDpi;
    setDimensions(m.widthPixels, m.heightPixels);
  }

  private void setDimensions(int width, int height) {
    screenW = width;
    screenH = height;
    float scale = Math.min(1f, 1600f / Math.max(screenW, screenH));
    captureW = Math.max(1, Math.round(screenW * scale));
    captureH = Math.max(1, Math.round(screenH * scale));
  }

  private void createReader() {
    reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 3);
    reader.setOnImageAvailableListener(
        source -> {
          if (stopped || source != reader) return;
          Image next = source.acquireLatestImage();
          if (next == null) return;
          framesReceived++;
          if (latestImage != null) latestImage.close();
          latestImage = next;
        },
        analysis);
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private WindowManager.LayoutParams params(int w, int h, int flags) {
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT);
    p.gravity = Gravity.TOP | Gravity.LEFT;
    if (Build.VERSION.SDK_INT >= 28)
      p.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    return p;
  }

  private void createOverlay() {
    overlay = new OverlayView(this);
    overlayParams =
        params(
            -1,
            -1,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    // Android 12+ allows pass-through only below maximum obscuring opacity.
    overlayParams.alpha = .75f;
    wm.addView(overlay, overlayParams);
    controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    controls.setPadding(dp(4), 0, dp(4), 0);
    controls.setBackground(Ui.rounded(this, 0xf223403a, 18, 0));
    controls.setElevation(dp(4));
    info = new TextView(this);
    info.setText("読 · 拖动");
    info.setTextColor(Color.WHITE);
    info.setTextSize(11);
    info.setPadding(dp(8), dp(10), dp(8), dp(10));
    controls.addView(info, new LinearLayout.LayoutParams(dp(92), -1));
    pauseButton =
        control(
            "暂停",
            () -> {
              paused = !paused;
              generation++;
              pauseButton.setText(paused ? "继续" : "暂停");
              overlay.setLabels(Collections.emptyList());
              status = paused ? "已暂停（屏幕共享仍开启）" : "运行中";
              if (!paused) {
                lastSignature = null;
                main.removeCallbacks(tick);
                main.post(tick);
              }
            });
    control(
        "模式",
        () -> {
          Prefs.get(this).edit().putInt("mode", (Prefs.mode(this) + 1) % 3).apply();
          overlay.invalidate();
        });
    control("框选", this::selectRegion);
    control("×", this::stopSelf);
    controlParams =
        params(
            -2,
            dp(44),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
    controls.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    controlParams.x = Math.max(dp(8), screenW - controls.getMeasuredWidth() - dp(8));
    controlParams.y = dp(80);
    wm.addView(controls, controlParams);
    info.setOnTouchListener(
        new View.OnTouchListener() {
          float x, y;
          int startX, startY;

          public boolean onTouch(View v, android.view.MotionEvent e) {
            if (e.getAction() == 0) {
              x = e.getRawX();
              y = e.getRawY();
              startX = controlParams.x;
              startY = controlParams.y;
              return true;
            }
            if (e.getAction() == 2) {
              controlParams.x =
                  Math.max(
                      0, Math.min(screenW - controls.getWidth(), startX + (int) (e.getRawX() - x)));
              controlParams.y =
                  Math.max(
                      0,
                      Math.min(screenH - controls.getHeight(), startY + (int) (e.getRawY() - y)));
              wm.updateViewLayout(controls, controlParams);
              return true;
            }
            return true;
          }
        });
  }

  private Button control(String name, Runnable action) {
    Button b = new Button(this);
    b.setText(name);
    b.setTextSize(11);
    b.setTextColor(Color.WHITE);
    b.setBackground(Ui.button(this, Color.TRANSPARENT, 12, 0));
    b.setPadding(dp(4), 0, dp(4), 0);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setOnClickListener(v -> action.run());
    controls.addView(b, new LinearLayout.LayoutParams(dp(name.equals("×") ? 32 : 46), -1));
    return b;
  }

  private void beginSample() {
    if (stopped || paused || selecting) return;
    long remaining = (geometryReadyNanos - System.nanoTime()) / 1_000_000L;
    if (remaining > 0) {
      schedule(remaining + 50);
      return;
    }
    if (!((PowerManager) getSystemService(POWER_SERVICE)).isInteractive() || MainActivity.visible) {
      overlay.setLabels(Collections.emptyList());
      schedule(1000);
      return;
    }
    if (!inFlight.compareAndSet(false, true)) {
      schedule(100);
      return;
    }
    updateRegionBorder();
    // Keep the display stable; exclude only our drawing in the OCR copy.
    int[] controlOrigin = new int[2];
    controls.getLocationOnScreen(controlOrigin);
    controlBounds =
        new Rect(
            controlOrigin[0],
            controlOrigin[1],
            controlOrigin[0] + controls.getWidth(),
            controlOrigin[1] + controls.getHeight());
    overlay.setControlBounds(controlBounds);
    annotationMasks = overlay.captureMasks();
    int current = generation;
    analysis.postDelayed(() -> capture(current, 0), 80);
  }

  private void capture(int current, int attempt) {
    if (stopped || paused || selecting || current != generation) {
      restore();
      finishFrame(200);
      return;
    }
    // Reject frames produced during the display rotation transition.
    long earliest = geometryReadyNanos;
    if (latestImage == null || latestImage.getTimestamp() < earliest) {
      if (attempt < 8) analysis.postDelayed(() -> capture(current, attempt + 1), 40);
      else {
        restore();
        finishFrame(250);
      }
      return;
    }
    Bitmap bitmap = null;
    Image sampled = latestImage;
    latestImage = null;
    try (Image image = sampled) {
      if (image == null) {
        restore();
        finishFrame(200);
        return;
      }
      sampledFrameAgeMs = (System.nanoTime() - image.getTimestamp()) / 1_000_000L;
      final int sourceWidth = screenW, sourceHeight = screenH;
      final int frameWidth = image.getWidth(), frameHeight = image.getHeight();
      if (frameWidth != captureW || frameHeight != captureH) {
        restore();
        finishFrame(200);
        return;
      }
      Image.Plane plane = image.getPlanes()[0];
      ByteBuffer buffer = plane.getBuffer();
      int paddedW = plane.getRowStride() / plane.getPixelStride();
      frameDetails =
          "image="
              + image.getWidth()
              + "x"
              + image.getHeight()
              + " stride="
              + paddedW
              + " bytes="
              + buffer.remaining()
              + " crop="
              + image.getCropRect();
      Bitmap padded = Bitmap.createBitmap(paddedW, captureH, Bitmap.Config.ARGB_8888);
      padded.copyPixelsFromBuffer(buffer);
      Rect crop = region();
      bitmap = Bitmap.createBitmap(padded, crop.left, crop.top, crop.width(), crop.height());
      if (bitmap != padded) padded.recycle();
      if (!bitmap.isMutable()) {
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap.recycle();
        bitmap = mutable;
      }
      // Keep controls visible and responsive; erase only their pixels from the OCR copy.
      Rect panel = controlBounds;
      RectF masked =
          new RectF(
              panel.left * (float) captureW / screenW - crop.left,
              panel.top * (float) captureH / screenH - crop.top,
              panel.right * (float) captureW / screenW - crop.left,
              panel.bottom * (float) captureH / screenH - crop.top);
      Paint mask = new Paint();
      mask.setColor(Color.BLACK);
      Canvas clean = new Canvas(bitmap);
      maskDrawing(bitmap, clean, masked, mask);
      for (RectF r : annotationMasks) {
        maskDrawing(
            bitmap,
            clean,
            new RectF(
                r.left * captureW / screenW - crop.left,
                r.top * captureH / screenH - crop.top,
                r.right * captureW / screenW - crop.left,
                r.bottom * captureH / screenH - crop.top),
            mask);
      }
      restore();
      int[] signature = signature(bitmap);
      String nextDictionary = Prefs.get(this).getString("dictionary", "");
      int nextRegion = Prefs.get(this).getInt("region", 3);
      boolean dirty = !nextDictionary.equals(dictionary) || lastRegion != nextRegion;
      if (!nextDictionary.equals(dictionary)) {
        dictionary = nextDictionary;
        readings.setOverrides(dictionary);
      }
      lastRegion = nextRegion;
      int interval = Prefs.interval(this);
      boolean unchanged = FrameDifference.similar(signature, lastSignature);
      long maxIdle = interval == 350 ? 1500 : interval == 1500 ? 4000 : 2500;
      if (!dirty && unchanged && SystemClock.elapsedRealtime() - lastOcrAt < maxIdle) {
        stableFrames++;
        skips++;
        bitmap.recycle();
        finishFrame(Math.min(1500, interval + stableFrames * 100));
        return;
      }
      lastSignature = signature;
      stableFrames = 0;
      lastOcrAt = SystemClock.elapsedRealtime();
      Bitmap input = bitmap;
      long start = SystemClock.elapsedRealtime();
      recognizer
          .process(InputImage.fromBitmap(input, 0))
          .addOnSuccessListener(
              r ->
                  analysis.post(
                      () -> {
                        try {
                          if (stopped || current != generation || paused || selecting) return;
                          List<OverlayView.Label> labels = new ArrayList<>();
                          blocks:
                          for (Text.TextBlock block : r.getTextBlocks())
                            for (Text.Line line : block.getLines()) {
                              String value = line.getText();
                              Rect b = line.getBoundingBox();
                              if (b == null
                                  || !JapaneseText.candidate(
                                      value, Prefs.get(this).getBoolean("kanaOnly", false)))
                                continue;
                              RectF box =
                                  new RectF(
                                      (b.left + crop.left) * (float) sourceWidth / frameWidth,
                                      (b.top + crop.top) * (float) sourceHeight / frameHeight,
                                      (b.right + crop.left) * (float) sourceWidth / frameWidth,
                                      (b.bottom + crop.top) * (float) sourceHeight / frameHeight);
                              RectF occlusion = new RectF(panel);
                              occlusion.inset(-dp(4), -dp(4));
                              if (RectF.intersects(box, occlusion)) continue;
                              labels.add(new OverlayView.Label(box, readings.read(value)));
                              if (labels.size() >= 100) break blocks;
                            }
                          long elapsed = SystemClock.elapsedRealtime() - start;
                          lastOcrMs = elapsed;
                          scans++;
                          main.post(
                              () -> {
                                if (stopped || current != generation || paused || selecting) return;
                                overlay.setLabels(labels);
                                info.setText("読 " + elapsed + "ms");
                                status =
                                    "运行中 · "
                                        + labels.size()
                                        + " 行 · OCR+读音 "
                                        + elapsed
                                        + " ms · 识别 "
                                        + scans
                                        + " / 跳过 "
                                        + skips;
                              });
                        } catch (Exception e) {
                          fail(e);
                        } finally {
                          input.recycle();
                          finishFrame(Prefs.interval(this));
                        }
                      }))
          .addOnFailureListener(
              e ->
                  analysis.post(
                      () -> {
                        input.recycle();
                        lastSignature = null;
                        main.post(
                            () -> {
                              if (stopped || current != generation) return;
                              status = "OCR 暂不可用：" + e.getMessage();
                            });
                        finishFrame(2000);
                      }));
    } catch (Exception e) {
      if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
      inFlight.set(false);
      restore();
      fail(e);
    }
  }

  private static void maskDrawing(Bitmap bitmap, Canvas canvas, RectF rect, Paint paint) {
    RectF clipped = new RectF(rect);
    if (!clipped.intersect(0, 0, bitmap.getWidth(), bitmap.getHeight())) return;
    // Match the surrounding background instead of introducing a black glyph-like bar.
    int[] red = new int[16], green = new int[16], blue = new int[16];
    for (int i = 0; i < 16; i++) {
      float f = ((i % 4) + .5f) / 4;
      float x =
          i < 8
              ? clipped.left + f * clipped.width()
              : i < 12 ? clipped.left - 3 : clipped.right + 3;
      float y =
          i < 4 ? clipped.top - 3 : i < 8 ? clipped.bottom + 3 : clipped.top + f * clipped.height();
      int color =
          bitmap.getPixel(
              Math.max(0, Math.min(bitmap.getWidth() - 1, (int) x)),
              Math.max(0, Math.min(bitmap.getHeight() - 1, (int) y)));
      red[i] = Color.red(color);
      green[i] = Color.green(color);
      blue[i] = Color.blue(color);
    }
    Arrays.sort(red);
    Arrays.sort(green);
    Arrays.sort(blue);
    paint.setColor(Color.rgb(red[8], green[8], blue[8]));
    canvas.drawRect(clipped, paint);
  }

  private void updateRegionBorder() {
    Rect r = region();
    overlay.setRegion(
        Prefs.get(this).getInt("region", 3) == 3 && Prefs.get(this).contains("left")
            ? new RectF(
                r.left * (float) screenW / captureW,
                r.top * (float) screenH / captureH,
                r.right * (float) screenW / captureW,
                r.bottom * (float) screenH / captureH)
            : null);
  }

  private Rect region() {
    int mode = Prefs.get(this).getInt("region", 3);
    if (mode == 1) return new Rect(0, captureH / 2, captureW, captureH);
    if (mode == 2) return new Rect(0, captureH / 6, captureW, captureH * 5 / 6);
    if (mode == 3) {
      android.content.SharedPreferences p = Prefs.get(this);
      int l = Math.max(0, Math.min(captureW - 1, (int) (p.getFloat("left", 0) * captureW))),
          t = Math.max(0, Math.min(captureH - 1, (int) (p.getFloat("top", 0) * captureH)));
      int r = Math.max(l + 1, Math.min(captureW, (int) (p.getFloat("right", 1) * captureW))),
          b = Math.max(t + 1, Math.min(captureH, (int) (p.getFloat("bottom", 1) * captureH)));
      return new Rect(l, t, r, b);
    }
    return new Rect(0, 0, captureW, captureH);
  }

  private int[] signature(Bitmap b) {
    Bitmap small = Bitmap.createScaledBitmap(b, 128, 192, true);
    int[] values = new int[128 * 192];
    small.getPixels(values, 0, 128, 0, 0, 128, 192);
    if (small != b) small.recycle();
    return values;
  }

  private void restore() {
    main.post(
        () -> {
          if (!stopped && !selecting && overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            controls.setAlpha(1f);
          }
        });
  }

  private void finishFrame(long delay) {
    inFlight.set(false);
    schedule(delay);
  }

  private void schedule(long delay) {
    main.post(
        () -> {
          if (!stopped && !paused && !selecting) {
            main.removeCallbacks(tick);
            main.postDelayed(tick, delay);
          }
        });
  }

  private void selectRegion() {
    if (selecting) return;
    selecting = true;
    generation++;
    main.removeCallbacks(tick);
    overlay.setLabels(Collections.emptyList());
    controls.setVisibility(View.GONE);
    selector =
        new View(this) {
          final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
          float sx, sy, ex, ey;
          boolean down;

          protected void onDraw(Canvas c) {
            c.drawColor(0x55304750);
            p.setColor(Color.WHITE);
            p.setTextSize(dp(17));
            c.drawText("拖动框选识别区域 · 轻点取消", dp(20), dp(64), p);
            if (down) {
              p.setColor(0xaa41dcc0);
              p.setStyle(Paint.Style.STROKE);
              p.setStrokeWidth(dp(2));
              c.drawRect(Math.min(sx, ex), Math.min(sy, ey), Math.max(sx, ex), Math.max(sy, ey), p);
              p.setStyle(Paint.Style.FILL);
            }
          }

          public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
              sx = ex = e.getX();
              sy = ey = e.getY();
              down = true;
            } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
              ex = e.getX();
              ey = e.getY();
            } else if (e.getAction() == MotionEvent.ACTION_UP) {
              ex = e.getX();
              ey = e.getY();
              if (Math.abs(ex - sx) > dp(32) && Math.abs(ey - sy) > dp(32)) {
                int[] origin = new int[2];
                getLocationOnScreen(origin);
                Prefs.get(CaptureService.this)
                    .edit()
                    .putInt("region", 3)
                    .putFloat("left", (origin[0] + Math.min(sx, ex)) / screenW)
                    .putFloat("top", (origin[1] + Math.min(sy, ey)) / screenH)
                    .putFloat("right", (origin[0] + Math.max(sx, ex)) / screenW)
                    .putFloat("bottom", (origin[1] + Math.max(sy, ey)) / screenH)
                    .apply();
              }
              finishSelection();
            } else if (e.getAction() == MotionEvent.ACTION_CANCEL) finishSelection();
            invalidate();
            return true;
          }
        };
    WindowManager.LayoutParams p =
        params(
            -1,
            -1,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
    wm.addView(selector, p);
  }

  private void finishSelection() {
    if (selector != null) {
      wm.removeView(selector);
      selector = null;
    }
    selecting = false;
    updateRegionBorder();
    lastSignature = null;
    controls.setVisibility(View.VISIBLE);
    controls.setAlpha(1);
    overlay.setVisibility(View.VISIBLE);
    if (!paused) main.post(tick);
  }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    if (stopped || display == null) return;
    generation++;
    geometryReadyNanos = System.nanoTime() + 900_000_000L;
    lastSignature = null;
    overlay.setLabels(Collections.emptyList());
    schedule(950);
    if (Build.VERSION.SDK_INT < 34)
      main.postDelayed(
          () -> {
            DisplayMetrics m = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(m);
            resizeCapture(m.widthPixels, m.heightPixels);
          },
          250);
  }

  private void resizeCapture(int width, int height) {
    if (stopped
        || display == null
        || width <= 0
        || height <= 0
        || (width == screenW && height == screenH)) return;
    generation++;
    geometryReadyNanos = System.nanoTime() + 900_000_000L;
    main.removeCallbacks(tick);
    overlay.setLabels(Collections.emptyList());
    analysis.post(
        () -> {
          try {
            if (stopped) return;
            ImageReader old = reader;
            if (latestImage != null) {
              latestImage.close();
              latestImage = null;
            }
            setDimensions(width, height);
            createReader();
            // Publish the new consumer surface BEFORE resize notifies WindowManager.
            // Otherwise it can compute the mirror transform using the old surface size;
            // replacing one non-null surface with another does not itself send that event.
            display.setSurface(reader.getSurface());
            display.resize(captureW, captureH, dpi);
            old.close();
            lastSignature = null;
            main.post(
                () -> {
                  if (stopped) return;
                  controlParams.x = Math.max(dp(8), screenW - controls.getWidth() - dp(8));
                  controlParams.y = dp(80);
                  wm.updateViewLayout(controls, controlParams);
                  updateRegionBorder();
                  restore();
                  schedule(250);
                });
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  private void fail(Exception e) {
    if (stopped) return;
    android.util.Log.e("YomiLens", "Capture failure", e);
    main.post(
        () -> {
          status = "辅助停止：" + e.getClass().getSimpleName() + " · " + e.getMessage();
          Toast.makeText(this, status, Toast.LENGTH_LONG).show();
          stopSelf();
        });
  }

  @Override
  public void onDestroy() {
    stopped = true;
    running = false;
    generation++;
    main.removeCallbacksAndMessages(null);
    if (selector != null && selector.isAttachedToWindow()) wm.removeView(selector);
    if (controls != null && controls.isAttachedToWindow()) wm.removeView(controls);
    if (overlay != null && overlay.isAttachedToWindow()) wm.removeView(overlay);
    if (display != null) display.release();
    if (projection != null) projection.stop();
    analysis.post(
        () -> {
          if (latestImage != null) {
            latestImage.close();
            latestImage = null;
          }
          if (reader != null) reader.close();
          if (recognizer != null) recognizer.close();
          thread.quitSafely();
        });
    if (!status.startsWith("辅助停止") && !status.startsWith("系统")) status = "已停止 · 屏幕采集已释放";
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
    out.println(frameDetails);
    out.println("YomiLens diagnostics (no screen text)");
    out.println("running=" + running + " paused=" + paused + " selecting=" + selecting);
    out.println("source=" + screenW + "x" + screenH + " capture=" + captureW + "x" + captureH);
    out.println(
        "framesReceived="
            + framesReceived
            + " ocrRuns="
            + scans
            + " skipped="
            + skips
            + " lastOcrMs="
            + lastOcrMs);
    out.println(
        "labels="
            + (overlay == null ? 0 : overlay.labelCount())
            + " overlayVisible="
            + (overlay != null && overlay.getVisibility() == View.VISIBLE));
    out.println("sampledFrameAgeMs=" + sampledFrameAgeMs);
    if (overlay != null) overlay.dumpGeometry(out);
    if (controls != null) {
      for (int i = 1; i < controls.getChildCount(); i++) {
        View button = controls.getChildAt(i);
        int[] origin = new int[2];
        button.getLocationOnScreen(origin);
        out.println(
            "control="
                + (i - 1)
                + ","
                + (origin[0] + button.getWidth() / 2)
                + ","
                + (origin[1] + button.getHeight() / 2));
      }
    }
  }
}
