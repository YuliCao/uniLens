package io.github.yomilens;

/** Sensitive to local glyph changes, with an independent periodic OCR deadline. */
final class FrameDifference {
  static boolean similar(int[] current, int[] previous) {
    if (previous == null || current.length != previous.length) return false;
    long total = 0;
    int changed = 0;
    for (int i = 0; i < current.length; i++) {
      int a = current[i], b = previous[i];
      int blue = Math.abs((a & 255) - (b & 255));
      int green = Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
      int red = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255));
      int difference = Math.max(blue, Math.max(green, red));
      if (difference > 32) return false;
      if (difference > 10 && ++changed >= 3) return false;
      total += difference;
    }
    return total < current.length;
  }
}
