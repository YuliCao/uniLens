package io.github.yomilens;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import java.util.*;

/** Whole-line Viterbi analysis; bounded cache. Access on the analysis worker only. */
public final class ReadingEngine {
  public static final class Reading {
    public final String original, kana, romaji;
    public final boolean uncertain;

    Reading(String o, String k, String r, boolean u) {
      original = o;
      kana = k;
      romaji = r;
      uncertain = u;
    }
  }

  private final Tokenizer tokenizer = new Tokenizer();
  private final Map<String, String> overrides = new HashMap<>();
  private final List<String> overrideKeys = new ArrayList<>();
  private final Map<String, Reading> cache =
      new LinkedHashMap<String, Reading>(256, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Reading> e) {
          return size() > 512;
        }
      };

  public void setOverrides(String lines) {
    overrides.clear();
    overrideKeys.clear();
    cache.clear();
    for (String line : lines.split("\n")) {
      String[] p = line.split("=", 2);
      if (p.length == 2 && !p[0].trim().isEmpty() && !p[1].trim().isEmpty())
        overrides.put(p[0].trim(), p[1].trim());
      if (overrides.size() >= 500) break;
    }
    overrideKeys.addAll(overrides.keySet());
    overrideKeys.sort(
        Comparator.comparingInt(String::length)
            .reversed()
            .thenComparing(Comparator.naturalOrder()));
  }

  public Reading read(String input) {
    Reading cached = cache.get(input);
    if (cached != null) return cached;
    StringBuilder kana = new StringBuilder(), romaji = new StringBuilder();
    boolean uncertain = false;
    int start = 0, index = 0;
    while (index < input.length()) {
      String match = null;
      for (String key : overrideKeys)
        if (input.startsWith(key, index)) {
          match = key;
          break;
        }
      if (match == null) {
        index++;
        continue;
      }
      if (index > start) {
        Reading part = readPlain(input.substring(start, index));
        kana.append(part.kana);
        append(romaji, part.romaji, true);
        uncertain |= part.uncertain;
      }
      String k = Romaji.hiragana(overrides.get(match));
      kana.append(k);
      append(romaji, Romaji.convert(k), true);
      index += match.length();
      start = index;
    }
    if (start < input.length()) {
      Reading part = readPlain(input.substring(start));
      kana.append(part.kana);
      append(romaji, part.romaji, true);
      uncertain |= part.uncertain;
    }
    Reading result = new Reading(input, kana.toString(), romaji.toString(), uncertain);
    cache.put(input, result);
    return result;
  }

  private static void append(StringBuilder out, String text, boolean space) {
    if (space
        && out.length() > 0
        && !text.isEmpty()
        && Character.isLetterOrDigit(out.charAt(out.length() - 1))
        && Character.isLetterOrDigit(text.charAt(0))) out.append(' ');
    out.append(text);
  }

  private Reading readPlain(String input) {
    StringBuilder kana = new StringBuilder(), romaji = new StringBuilder();
    StringBuilder group = new StringBuilder();
    boolean uncertain = false;
    int position = 0;
    for (Token t : tokenizer.tokenize(input)) {
      if (t.getPosition() > position) {
        flushGroup(romaji, group);
        String gap = input.substring(position, t.getPosition());
        kana.append(gap);
        romaji.append(gap);
      }
      String surface = t.getSurface(), k = overrides.getOrDefault(surface, t.getReading());
      if (k == null || k.equals("*")) {
        k = surface;
        uncertain |= JapaneseText.containsHan(surface);
      }
      kana.append(Romaji.hiragana(k));
      String phonetic = k;
      if (t.getPartOfSpeechLevel1().equals("助詞")) {
        if (surface.equals("は")) phonetic = "ワ";
        if (surface.equals("へ")) phonetic = "エ";
        if (surface.equals("を")) phonetic = "オ";
      }
      boolean join =
          t.getPartOfSpeechLevel1().equals("助動詞")
              || t.getPartOfSpeechLevel2().equals("接尾")
              || t.getPartOfSpeechLevel2().equals("接続助詞")
              || (group.length() > 0 && "ッっ".indexOf(group.charAt(group.length() - 1)) >= 0);
      if (!join) flushGroup(romaji, group);
      group.append(phonetic);
      position = t.getPosition() + surface.length();
    }
    flushGroup(romaji, group);
    if (position < input.length()) {
      kana.append(input.substring(position));
      romaji.append(input.substring(position));
    }
    return new Reading(input, kana.toString(), romaji.toString(), uncertain);
  }

  private static void flushGroup(StringBuilder out, StringBuilder group) {
    if (group.length() == 0) return;
    append(out, Romaji.convert(group.toString()), true);
    group.setLength(0);
  }
}
