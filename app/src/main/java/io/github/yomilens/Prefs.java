package io.github.yomilens;

import android.content.*;

final class Prefs {
  static final int INTERVAL = 750;

  static SharedPreferences get(Context c) {
    return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  static int font(Context c) {
    return get(c).getInt("font", 14);
  }

  /** A saved manual region; otherwise the whole screen is read. */
  static boolean hasRegion(Context c) {
    return get(c).contains("left");
  }

  static void clearRegion(Context c) {
    get(c).edit().remove("left").remove("top").remove("right").remove("bottom").apply();
  }
}
