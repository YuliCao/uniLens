package io.github.yomilens;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

final class OverlayView extends View {
  static final class Label {
    final RectF box;
    final ReadingEngine.Reading reading;

    Label(RectF b, ReadingEngine.Reading r) {
      box = b;
      reading = r;
    }
  }

  private static final class Rendered {
    final Label label;
    final RectF bounds;
    final List<String> rows;
    final float size, spacing;

    Rendered(Label label, RectF bounds, List<String> rows, float size, float spacing) {
      this.label = label;
      this.bounds = bounds;
      this.rows = rows;
      this.size = size;
      this.spacing = spacing;
    }
  }

  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG),
      back = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final List<Rendered> rendered = new ArrayList<>();
  private List<Label> labels = Collections.emptyList();
  private final RectF controlBounds = new RectF();
  private final RectF region = new RectF();
  private boolean dirty = true;
  private int lastMode = -1, lastFont = -1;
  private int blankTransitions = 0;

  OverlayView(Context c) {
    super(c);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  }

  void setLabels(List<Label> next) {
    if (!labels.isEmpty() && next.isEmpty()) blankTransitions++;
    labels = next;
    dirty = true;
    invalidate();
  }

  int labelCount() {
    return labels.size();
  }

  void dumpGeometry(java.io.PrintWriter out) {
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    out.println("overlayOrigin=" + origin[0] + "," + origin[1]);
    out.println("regionBorder=" + !region.isEmpty());
    out.println("blankTransitions=" + blankTransitions);
    for (Label label : labels) {
      RectF b = label.box;
      out.println("box=" + b.left + "," + b.top + "," + b.right + "," + b.bottom);
    }
  }

  void setControlBounds(Rect bounds) {
    if (controlBounds.left != bounds.left
        || controlBounds.top != bounds.top
        || controlBounds.right != bounds.right
        || controlBounds.bottom != bounds.bottom) {
      controlBounds.set(bounds);
      dirty = true;
      invalidate();
    }
  }

  void setRegion(RectF value) {
    if (value == null) region.setEmpty();
    else region.set(value);
    invalidate();
  }

  List<RectF> captureMasks() {
    ensureLayout();
    List<RectF> masks = new ArrayList<>();
    float d = getResources().getDisplayMetrics().density;
    for (Rendered item : rendered) {
      RectF r = new RectF(item.bounds);
      r.inset(-2, -2);
      masks.add(r);
      RectF b = item.label.box;
      masks.add(new RectF(b.left, b.bottom, b.right, b.bottom + d + 1));
    }
    if (!region.isEmpty()) {
      float t = 2 * d + 1;
      masks.add(new RectF(region.left - t, region.top - t, region.right + t, region.top + t));
      masks.add(new RectF(region.left - t, region.bottom - t, region.right + t, region.bottom + t));
      masks.add(new RectF(region.left - t, region.top, region.left + t, region.bottom));
      masks.add(new RectF(region.right - t, region.top, region.right + t, region.bottom));
    }
    return masks;
  }

  private void ensureLayout() {
    int mode = Prefs.mode(getContext()), font = Prefs.get(getContext()).getInt("font", 14);
    if (dirty || mode != lastMode || font != lastFont) {
      rebuild(mode, font);
      lastMode = mode;
      lastFont = font;
      dirty = false;
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    dirty = true;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    ensureLayout();
    float density = getResources().getDisplayMetrics().density, pad = 4 * density;
    if (!region.isEmpty()) {
      back.setColor(0xff20d8c0);
      back.setStyle(Paint.Style.STROKE);
      back.setStrokeWidth(2 * density);
      canvas.drawRect(region, back);
      back.setStyle(Paint.Style.FILL);
    }
    for (Rendered item : rendered) {
      back.setColor(0xdd10292f);
      canvas.drawRoundRect(item.bounds, 4 * density, 4 * density, back);
      ink.setTextSize(item.size);
      ink.setColor(item.label.reading.uncertain ? 0xffffcf7a : 0xffd3fff4);
      float baseline = item.bounds.top + pad / 2 - ink.ascent();
      for (String row : item.rows) {
        canvas.drawText(row, item.bounds.left + pad, baseline, ink);
        baseline += item.spacing;
      }
      back.setColor(0xaa70d6c7);
      RectF b = item.label.box;
      canvas.drawRect(b.left, b.bottom, b.right, b.bottom + density, back);
    }
  }

  private void rebuild(int mode, int font) {
    rendered.clear();
    if (getWidth() == 0 || getHeight() == 0) return;
    float density = getResources().getDisplayMetrics().density,
        pad = 4 * density,
        requested = font * density;
    List<LabelPlacer.Box> obstacles = new ArrayList<>(), placed = new ArrayList<>();
    for (Label label : labels) obstacles.add(box(label.box));
    if (!controlBounds.isEmpty()) obstacles.add(box(controlBounds));
    for (Label label : labels) {
      RectF b = label.box;
      String value = mode == 1 ? label.reading.kana : label.reading.romaji;
      if (mode == 2) value = label.reading.original + "  /  " + value;
      if (value.isEmpty()) continue;
      ink.setTextSize(requested);
      float available =
          Math.max(
              50 * density, Math.min(getWidth() - 2 * pad, Math.max(b.width(), 180 * density)));
      float measured = ink.measureText(value);
      if (measured > available)
        ink.setTextSize(Math.max(9 * density, requested * available / measured));
      List<String> rows = wrap(value, Math.max(1, available - pad * 2));
      float lineHeight = ink.getFontSpacing(), width = 0;
      for (String row : rows) width = Math.max(width, ink.measureText(row));
      width += 2 * pad;
      float height = lineHeight * rows.size() + pad;
      LabelPlacer.Box position =
          LabelPlacer.place(
              box(b), width, height, getWidth(), getHeight(), 2 * density, obstacles, placed);
      placed.add(position);
      rendered.add(
          new Rendered(
              label,
              new RectF(position.left, position.top, position.right, position.bottom),
              rows,
              ink.getTextSize(),
              lineHeight));
    }
  }

  private static LabelPlacer.Box box(RectF r) {
    return new LabelPlacer.Box(r.left, r.top, r.right, r.bottom);
  }

  private List<String> wrap(String text, float width) {
    List<String> result = new ArrayList<>();
    int offset = 0;
    while (offset < text.length()) {
      int count = Math.max(1, ink.breakText(text, offset, text.length(), true, width, null));
      int end = Math.min(text.length(), offset + count);
      if (end < text.length()) {
        int space = text.lastIndexOf(' ', end);
        if (space > offset) end = space;
        else {
          int dot = text.lastIndexOf('·', end - 1);
          if (dot > offset) end = dot + 1;
        }
      }
      result.add(text.substring(offset, end));
      offset = end;
      while (offset < text.length() && text.charAt(offset) == ' ') offset++;
    }
    return result;
  }
}
