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

  @Test
  public void separatedSyllablesKeepNasalsAndDigraphsTogether() {
    assertEquals("ni·hon·go", Romaji.convertSeparated("ニホンゴ"));
    assertEquals("sha·shin", Romaji.convertSeparated("しゃしん"));
    assertEquals("kon·ni·chi", Romaji.convertSeparated("こんにち"));
    assertEquals("shin·you", Romaji.convertSeparated("しんよう"));
    assertEquals("kan·i", Romaji.convertSeparated("かんい"));
    assertEquals("n·na", Romaji.convertSeparated("んな"));
  }

  @Test
  public void separatedLongVowelsAndHiatus() {
    assertEquals("tou·kyou", Romaji.convertSeparated("とうきょう"));
    assertEquals("koo·hii", Romaji.convertSeparated("コーヒー"));
    assertEquals("sen·sei", Romaji.convertSeparated("せんせい"));
    assertEquals("a·i·u·e·o", Romaji.convertSeparated("あいうえお"));
    assertEquals("a·o·i", Romaji.convertSeparated("あおい"));
    assertEquals("ー", Romaji.convertSeparated("ー"));
  }

  @Test
  public void separatedGeminationKeepsConsonantWithPreviousSyllable() {
    assertEquals("gak·kou", Romaji.convertSeparated("がっこう"));
    assertEquals("mat·cha", Romaji.convertSeparated("まっちゃ"));
    assertEquals("is·sho", Romaji.convertSeparated("いっしょ"));
    assertEquals("a’", Romaji.convertSeparated("あっ"));
    assertEquals("a’·i", Romaji.convertSeparated("あっい"));
  }

  @Test
  public void separatedLoanwords() {
    assertEquals("fii·ru·do·waa·ku", Romaji.convertSeparated("フィールドワーク"));
    assertEquals("she·je·che", Romaji.convertSeparated("シェジェチェ"));
    assertEquals("va", Romaji.convertSeparated("ヴァ"));
    assertEquals("wi·fi", Romaji.convertSeparated("ウィフィ"));
  }

  @Test
  public void separatedMixedTextAndPunctuationStayIntact() {
    assertEquals("ABC123, gee·mu!", Romaji.convertSeparated("ＡＢＣ１２３, ｹﾞｰﾑ!"));
    assertEquals("「ni·hon·go」（tou·kyou）！", Romaji.convertSeparated("「ニホンゴ」（とうきょう）！"));
    assertEquals("HP100ko·re", Romaji.convertSeparated("HP100これ"));
    assertEquals("helloー", Romaji.convertSeparated("helloー"));
    assertEquals("a·i", Romaji.convertSeparated("あ·い"));
    assertEquals("日本🙂", Romaji.convertSeparated("日本🙂"));
    assertEquals("", Romaji.convertSeparated(""));
  }
}
