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

  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG),
      back = new Paint(Paint.ANTI_ALIAS_FLAG);
  private List<Label> labels = Collections.emptyList();

  OverlayView(Context c) {
    super(c);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void setLabels(List<Label> next) {
    labels = next;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float density = getResources().getDisplayMetrics().density;
    float requested = Prefs.get(getContext()).getInt("font", 14) * density;
    int mode = Prefs.mode(getContext());
    List<RectF> occupied = new ArrayList<>();
    for (Label label : labels) {
      RectF b = label.box;
      String value = mode == 1 ? label.reading.kana : label.reading.romaji;
      if (mode == 2) value = label.reading.original + "  /  " + value;
      if (value.isEmpty()) continue;
      ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      ink.setTextSize(requested);
      float pad = 4 * density,
          available =
              Math.max(
                  50 * density, Math.min(getWidth() - 2 * pad, Math.max(b.width(), 180 * density)));
      float measured = ink.measureText(value);
      if (measured > available)
        ink.setTextSize(Math.max(9 * density, requested * available / measured));
      // Wrap long lines instead of truncating readings.
      List<String> rows = wrap(value, Math.max(1, available - pad * 2));
      float lineHeight = ink.getFontSpacing(), width = 0;
      for (String row : rows) width = Math.max(width, ink.measureText(row));
      width += 2 * pad;
      float height = lineHeight * rows.size() + pad;
      float x = Math.max(pad, Math.min(b.left, getWidth() - width - pad));
      float y = b.top - height - 2 * density;
      if (y < 0) y = Math.min(getHeight() - height, b.bottom + 2 * density);
      RectF rect = new RectF(x, y, x + width, y + height);
      for (RectF used : occupied)
        if (RectF.intersects(rect, used)) {
          float next = used.bottom + 2 * density;
          if (next + height < getHeight()) rect.offsetTo(x, next);
        }
      occupied.add(rect);
      back.setColor(0xdd10292f);
      canvas.drawRoundRect(rect, 4 * density, 4 * density, back);
      ink.setColor(label.reading.uncertain ? 0xffffcf7a : 0xffd3fff4);
      float baseline = rect.top + pad / 2 - ink.ascent();
      for (String row : rows) {
        canvas.drawText(row, rect.left + pad, baseline, ink);
        baseline += lineHeight;
      }
      back.setColor(0xaa70d6c7);
      canvas.drawRect(b.left, b.bottom, b.right, b.bottom + density, back);
    }
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
      }
      result.add(text.substring(offset, end));
      offset = end;
      while (offset < text.length() && text.charAt(offset) == ' ') offset++;
    }
    return result;
  }
}
