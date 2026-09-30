package io.github.yomilens;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Hepburn, with explicit long vowels and disambiguating n'. */
public final class Romaji {
  private static final Map<String, String> MAP = new HashMap<>();

  static {
    String[] rows = {
      "あ a い i う u え e お o",
      "か ka き ki く ku け ke こ ko",
      "が ga ぎ gi ぐ gu げ ge ご go",
      "さ sa し shi す su せ se そ so",
      "ざ za じ ji ず zu ぜ ze ぞ zo",
      "た ta ち chi つ tsu て te と to",
      "だ da ぢ ji づ zu で de ど do",
      "な na に ni ぬ nu ね ne の no",
      "は ha ひ hi ふ fu へ he ほ ho",
      "ば ba び bi ぶ bu べ be ぼ bo",
      "ぱ pa ぴ pi ぷ pu ぺ pe ぽ po",
      "ま ma み mi む mu め me も mo",
      "や ya ゆ yu よ yo ら ra り ri る ru れ re ろ ro わ wa を o ゐ i ゑ e ん n ゔ vu",
      "ぁ a ぃ i ぅ u ぇ e ぉ o ゃ ya ゅ yu ょ yo ゎ wa ゕ ka ゖ ke",
      "しぇ she じぇ je ちぇ che てぃ ti でぃ di とぅ tu どぅ du つぁ tsa つぃ tsi つぇ tse つぉ tso",
      "ふぁ fa ふぃ fi ふぇ fe ふぉ fo ふゅ fyu うぃ wi うぇ we うぉ wo ゔぁ va ゔぃ vi ゔぇ ve ゔぉ vo ゔゅ vyu",
      "くぁ kwa くぃ kwi くぇ kwe くぉ kwo ぐぁ gwa いぇ ye てゅ tyu でゅ dyu"
    };
    for (String row : rows) {
      String[] p = row.split(" ");
      for (int i = 0; i < p.length; i += 2) MAP.put(p[i], p[i + 1]);
    }
    String[] kana = {"き", "ぎ", "し", "じ", "ち", "ぢ", "に", "ひ", "び", "ぴ", "み", "り"};
    String[] base = {"ky", "gy", "sh", "j", "ch", "j", "ny", "hy", "by", "py", "my", "ry"};
    for (int i = 0; i < kana.length; i++)
      for (int j = 0; j < 3; j++) MAP.put(kana[i] + "ゃゅょ".charAt(j), base[i] + "auo".charAt(j));
  }

  public static String hiragana(String s) {
    StringBuilder normalized = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int end = i;
      while (end < s.length() && s.charAt(end) >= '\uff66' && s.charAt(end) <= '\uff9f') end++;
      if (end > i) {
        normalized.append(Normalizer.normalize(s.substring(i, end), Normalizer.Form.NFKC));
        i = end;
      } else {
        char c = s.charAt(i++);
        normalized.append(
            c >= '\uff10' && c <= '\uff5a' && Character.isLetterOrDigit(c)
                ? (char) (c - 0xfee0)
                : c);
      }
    }
    s = normalized.toString();
    StringBuilder out = new StringBuilder();
    for (char c : s.toCharArray()) out.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c);
    return out.toString();
  }

  public static String convert(String source) {
    return convert(source, false);
  }

  /** Reading aid: kana syllables separated by middle dots; foreign text stays intact. */
  public static String convertSeparated(String source) {
    return convert(source, true);
  }

  private static String convert(String source, boolean separated) {
    String s = hiragana(source);
    StringBuilder out = new StringBuilder();
    boolean geminate = false;
    boolean kanaBefore = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == 'っ') {
        if (geminate) {
          out.append("' ");
          kanaBefore = false;
        }
        geminate = true;
        continue;
      }
      if (c == 'ー') {
        char vowel = 0;
        // In separated mode a long mark belongs only to an immediately preceding vowel.
        for (int j = out.length() - 1; j >= 0 && (!separated || kanaBefore); j--) {
          char v = out.charAt(j);
          if ("aeiou".indexOf(v) >= 0) {
            vowel = v;
            break;
          }
          if (separated || !Character.isLetter(v)) break;
        }
        out.append(vowel == 0 ? 'ー' : vowel);
        if (vowel == 0) kanaBefore = false;
        continue;
      }
      String key = s.substring(i, i + 1), value = null;
      if (i + 1 < s.length()) {
        value = MAP.get(s.substring(i, i + 2));
        if (value != null) i++;
      }
      if (value == null) value = MAP.get(key);
      boolean kana = value != null;
      if (value == null) value = key;
      char previous = out.length() == 0 ? 0 : out.charAt(out.length() - 1);
      boolean join =
          separated
              && kanaBefore
              && !geminate
              && (c == 'ん' && "aeiou".indexOf(previous) >= 0
                  || value.length() == 1
                      && "aeiou".indexOf(previous) >= 0
                      && (previous == value.charAt(0)
                          || previous == 'o' && value.equals("u")
                          || previous == 'e' && value.equals("i")));
      if (geminate) {
        if (value.startsWith("ch")) out.append('t');
        else if ("bcdfghjklmpqrstvwxyz".indexOf(value.charAt(0)) >= 0 && !value.startsWith("n"))
          out.append(value.charAt(0));
        else out.append('’');
        geminate = false;
      }
      if (separated && kana && kanaBefore && !join) out.append('·');
      out.append(value);
      kanaBefore = kana;
      if (!separated && c == 'ん' && i + 1 < s.length()) {
        String next = MAP.get(s.substring(i + 1, i + 2));
        if (next != null && "aeiouy".indexOf(next.charAt(0)) >= 0) out.append('\'');
      }
    }
    if (geminate) out.append('’');
    return out.toString();
  }
}
