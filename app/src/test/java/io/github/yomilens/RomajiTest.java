package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class RomajiTest {
  @Test
  public void basicAndDigraphs() {
    assertEquals("nihongo", Romaji.convert("ニホンゴ"));
    assertEquals("toukyou", Romaji.convert("とうきょう"));
    assertEquals("shashin", Romaji.convert("しゃしん"));
  }

  @Test
  public void gemination() {
    assertEquals("gakkou", Romaji.convert("がっこう"));
    assertEquals("matcha", Romaji.convert("まっちゃ"));
    assertEquals("issho", Romaji.convert("いっしょ"));
  }

  @Test
  public void apostrophes() {
    assertEquals("shin'you", Romaji.convert("しんよう"));
    assertEquals("kan'i", Romaji.convert("かんい"));
    assertEquals("konnichi", Romaji.convert("こんにち"));
  }

  @Test
  public void borrowedSounds() {
    assertEquals("fiirudowaaku", Romaji.convert("フィールドワーク"));
    assertEquals("shejeche", Romaji.convert("シェジェチェ"));
    assertEquals("va", Romaji.convert("ヴァ"));
  }

  @Test
  public void mixedAndWidth() {
    assertEquals("ABC123, geemu!", Romaji.convert("ＡＢＣ１２３, ｹﾞｰﾑ!"));
    assertEquals("日本", Romaji.convert("日本"));
    assertEquals("", Romaji.convert(""));
  }

  @Test
  public void standaloneSmallTsuDoesNotDisappear() {
    assertEquals("a’", Romaji.convert("あっ"));
  }
}
