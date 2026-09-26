package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class JapaneseTextTest {
  @Test
  public void mixedKana() {
    assertTrue(JapaneseText.candidate("HPが100になった", true));
    assertTrue(JapaneseText.candidate("ｹﾞｰﾑ", true));
  }

  @Test
  public void optionalHanMenus() {
    assertTrue(JapaneseText.candidate("設定", false));
    assertFalse(JapaneseText.candidate("設定", true));
    assertFalse(JapaneseText.candidate("HP 100", false));
  }

  @Test
  public void supplementaryKanji() {
    assertTrue(JapaneseText.containsHan("𠮷"));
    assertTrue(JapaneseText.containsHan("々"));
  }
}
