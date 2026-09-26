package io.github.yomilens;

final class JapaneseText {
  static boolean containsKana(String text) {
    return text.codePoints()
        .anyMatch(
            c -> {
              Character.UnicodeScript script = Character.UnicodeScript.of(c);
              return script == Character.UnicodeScript.HIRAGANA
                  || script == Character.UnicodeScript.KATAKANA;
            });
  }

  static boolean containsHan(String text) {
    return text.codePoints()
        .anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN || c == '々');
  }

  static boolean candidate(String text, boolean kanaOnly) {
    return containsKana(text) || (!kanaOnly && containsHan(text));
  }
}
