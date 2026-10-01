package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReadingEngineTest {
  @Test
  public void preservesOriginalPunctuationAndBracketWidth() {
    ReadingEngine e = new ReadingEngine();
    String input = "「日本語」（東京）【学校】『友達』、。！？…";
    ReadingEngine.Reading r = e.read(input);
    String symbols = "「」（）【】『』、。！？…";
    assertEquals(symbols, r.romaji.replaceAll("[^「」（）【】『』、。！？…]", ""));
    assertEquals(symbols, r.kana.replaceAll("[^「」（）【】『』、。！？…]", ""));
    assertEquals("（koohii）！", Romaji.convert("（ｺｰﾋｰ）！"));
  }

  @Test
  public void geminationAcrossConjugationBoundary() {
    ReadingEngine engine = new ReadingEngine();
    String joined = engine.read("新しい仲間が加わった！").romaji;
    assertTrue(joined, joined.contains("ku·wa·wat·ta"));
    String wait = engine.read("待ってください。").romaji;
    assertTrue(wait, wait.contains("mat·te"));
  }

  @Test
  public void readsSentenceAndParticles() {
    ReadingEngine.Reading r = new ReadingEngine().read("私は東京へ行きます。");
    assertTrue(r.romaji, r.romaji.contains("wa"));
    assertTrue(r.romaji, r.romaji.contains("tou·kyou e"));
    assertFalse(r.uncertain);
  }

  @Test
  public void exactCustomName() {
    ReadingEngine e = new ReadingEngine();
    e.setOverrides("八重神子=やえみこ");
    assertEquals("ya·e·mi·ko", e.read("八重神子").romaji);
    e.setOverrides("八重神子=やえしんし");
    assertEquals("ya·e·shin·shi", e.read("八重神子").romaji);
  }

  @Test
  public void preservesLatinAndNumbers() {
    assertEquals("HP 100", new ReadingEngine().read("HP 100").romaji);
  }

  @Test
  public void customNameAcrossTokenBoundaries() {
    ReadingEngine e = new ReadingEngine();
    e.setOverrides("八重=やえ\n八重神子=やえみこ");
    ReadingEngine.Reading r = e.read("八重神子に会いました。");
    assertTrue(r.romaji, r.romaji.startsWith("ya·e·mi·ko ni"));
    assertTrue(r.romaji, r.romaji.contains("a·i·ma·shi·ta"));
  }

  @Test
  public void unknownKanjiRemainsVisible() {
    ReadingEngine.Reading r = new ReadingEngine().read("龯");
    assertTrue(r.uncertain);
    assertEquals("龯", r.romaji);
  }

  @Test
  public void separatedReadingKeepsWordSpacesAndCachedPunctuation() {
    ReadingEngine engine = new ReadingEngine();
    ReadingEngine.Reading reading = engine.read("「日本語」（東京）！");
    assertEquals("「ni·hon·go」（tou·kyou）！", reading.romaji);
    assertSame(reading, engine.read(reading.original));
    assertEquals("ni·hon·go o", engine.read("日本語を").romaji);
  }

  @Test
  public void emojiBoundaryPreservesBothReadingsAndOriginal() {
    String input = "「日本語👨‍👩‍👧‍👦👍🏽🇯🇵❤️東京」！";
    ReadingEngine engine = new ReadingEngine();
    ReadingEngine.Reading reading = engine.read(input);
    assertEquals("「ni·hon·go ｜ tou·kyou」！", reading.romaji);
    assertEquals(input, reading.original);
    assertSame(reading, engine.read(input));
    assertFalse(reading.uncertain);
  }

  @Test
  public void customNamesAndKeycapsUseTheSameBoundary() {
    ReadingEngine engine = new ReadingEngine();
    engine.setOverrides("八重神子=やえみこ");
    assertEquals("ya·e·mi·ko ｜ ni·hon·go", engine.read("八重神子1️⃣日本語").romaji);
    assertEquals("HP 100 ｜ MP 50", engine.read("HP 100★MP 50").romaji);
  }
}
