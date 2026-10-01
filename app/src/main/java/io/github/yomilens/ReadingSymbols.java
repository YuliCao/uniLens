package io.github.yomilens;

/** Keep readable punctuation, but give non-reading symbols a visible boundary. */
final class ReadingSymbols {
  static String format(String text) {
    StringBuilder out = new StringBuilder(text.length());
    boolean symbolRun = false;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      int end = i + Character.charCount(cp);
      // Keycaps begin with an ordinary digit, # or *, followed by optional VS16.
      if (cp >= '0' && cp <= '9' || cp == '#' || cp == '*') {
        int keycap = end;
        if (keycap < text.length() && text.codePointAt(keycap) == 0xfe0f) keycap++;
        if (keycap < text.length() && text.codePointAt(keycap) == 0x20e3) {
          cp = 0x20e3;
          end = keycap + 1;
        }
      }
      boolean special = isSpecial(cp);
      // Emoji-style punctuation (e.g. ‼️) is one symbol rather than a stray selector.
      if (end < text.length() && text.codePointAt(end) == 0xfe0f) special = true;
      if (special) {
        if (!symbolRun) {
          space(out);
          out.append('｜');
        }
        symbolRun = true;
      } else {
        if (symbolRun && !Character.isWhitespace(cp)) {
          space(out);
          symbolRun = false;
        }
        out.appendCodePoint(cp);
        if (cp == '\n' || cp == '\r') symbolRun = false;
      }
      i = end;
    }
    return out.toString();
  }

  private static void space(StringBuilder out) {
    if (out.length() > 0 && !Character.isWhitespace(out.codePointBefore(out.length())))
      out.append(' ');
  }

  private static boolean isSpecial(int cp) {
    int type = Character.getType(cp);
    return type == Character.MATH_SYMBOL
        || type == Character.CURRENCY_SYMBOL
        || type == Character.MODIFIER_SYMBOL
        || type == Character.OTHER_SYMBOL
        // Older Android Unicode tables may not know recently added emoji yet.
        || cp >= 0x1f000 && cp <= 0x1faff
        || cp == 0x200d
        || cp == 0x20e3
        || cp >= 0xfe00 && cp <= 0xfe0f
        || cp >= 0xe0020 && cp <= 0xe007f;
  }

  private ReadingSymbols() {}
}
