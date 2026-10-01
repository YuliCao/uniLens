package io.github.yomilens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Pink and white from the uni avatar, with deeper rose text for contrast. */
final class Ui {
  static final int BACKGROUND = 0xfffefdfd;
  static final int CARD = 0xffffffff;
  static final int INK = 0xff35252d;
  static final int MUTED = 0xff796871;
  static final int ACCENT = 0xff9b3e68;
  static final int PINK = 0xfffde2e8;
  static final int PRIMARY_INK = 0xff75354f;
  static final int SOFT = 0xfffff1f5;
  static final int LINE = 0xffecdfe5;
  static final int LIME = 0xff9be15d;
  static final int PANEL = 0xf22a2433;

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
        ColorStateList.valueOf(0x299b3e68),
        rounded(c, color, radius, border),
        rounded(c, 0xffffffff, radius, 0));
  }
}
