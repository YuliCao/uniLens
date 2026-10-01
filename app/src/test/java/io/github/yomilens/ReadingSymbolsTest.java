package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReadingSymbolsTest {
  @Test
  public void separatesAdjacentTextWithoutDamagingPunctuation() {
    assertEquals("「ni·hon·go ｜ tou·kyou」！", ReadingSymbols.format("「ni·hon·go🙂tou·kyou」！"));
    assertEquals("「」（）【】『』、。！？… / HP 100", ReadingSymbols.format("「」（）【】『』、。！？… / HP 100"));
  }

  @Test
  public void complexEmojiSequencesHaveOnePlaceholder() {
    assertEquals("a ｜ b", ReadingSymbols.format("a👨‍👩‍👧‍👦👍🏽🇯🇵❤️b"));
    assertEquals("a ｜ b", ReadingSymbols.format("a1️⃣#️⃣*️⃣b"));
    assertEquals("a ｜ b", ReadingSymbols.format("a‼️b"));
    assertEquals("a ｜ b", ReadingSymbols.format("a🫠b"));
    assertEquals("a ｜ b", ReadingSymbols.format("a🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC7Fb"));
  }

  @Test
  public void mathCurrencyAndDecorationsDoNotJoinWords() {
    assertEquals("HP 100 ｜ MP 50", ReadingSymbols.format("HP 100★→￥MP 50"));
    assertEquals("｜ word ｜", ReadingSymbols.format("♪word♬"));
  }

  @Test
  public void preservesWhitespaceAndSeparatesSymbolsOnDifferentLines() {
    assertEquals("a ｜ b", ReadingSymbols.format("a 🙂 b"));
    assertEquals("｜\n｜", ReadingSymbols.format("🙂\n😀"));
    assertEquals("a\nb", ReadingSymbols.format("a\nb"));
    assertEquals("", ReadingSymbols.format(""));
  }
}
