package io.github.yomilens;

import android.content.*;

final class Prefs {
  static SharedPreferences get(Context c) {
    return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  static int interval(Context c) {
    return get(c).getInt("interval", 750);
  }

  static int mode(Context c) {
    return get(c).getInt("mode", 0);
  }
}
