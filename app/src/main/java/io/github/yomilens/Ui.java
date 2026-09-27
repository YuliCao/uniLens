package io.github.yomilens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Small shared palette; native controls keep their normal focus and accessibility behavior. */
final class Ui {
  static final int BACKGROUND = 0xfff5f7f5;
  static final int INK = 0xff203632;
  static final int MUTED = 0xff667873;
  static final int ACCENT = 0xff216f61;
  static final int SOFT = 0xffedf3ef;
  static final int LINE = 0xffdfe7e2;

  static int dp(Context c, int value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable rounded(Context c, int color, int radius, int border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    if (border != 0) d.setStroke(dp(c, 1), border);
    return d;
  }

  static RippleDrawable button(Context c, int color, int radius, int border) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x24216f61),
        rounded(c, color, radius, border),
        rounded(c, 0xffffffff, radius, 0));
  }
}
