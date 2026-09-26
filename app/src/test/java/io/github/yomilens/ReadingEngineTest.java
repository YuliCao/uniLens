package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReadingEngineTest {
  @Test
  public void geminationAcrossConjugationBoundary() {
    ReadingEngine engine = new ReadingEngine();
    String joined = engine.read("新しい仲間が加わった！").romaji;
    assertTrue(joined, joined.contains("kuwawatta"));
    String wait = engine.read("待ってください。").romaji;
    assertTrue(wait, wait.contains("matte"));
  }

  @Test
  public void readsSentenceAndParticles() {
    ReadingEngine.Reading r = new ReadingEngine().read("私は東京へ行きます。");
    assertTrue(r.romaji, r.romaji.contains("wa"));
    assertTrue(r.romaji, r.romaji.contains("toukyou e"));
    assertFalse(r.uncertain);
  }

  @Test
  public void exactCustomName() {
    ReadingEngine e = new ReadingEngine();
    e.setOverrides("八重神子=やえみこ");
    assertEquals("yaemiko", e.read("八重神子").romaji);
    e.setOverrides("八重神子=やえしんし");
    assertEquals("yaeshinshi", e.read("八重神子").romaji);
  }

  @Test
  public void preservesLatinAndNumbers() {
    assertTrue(new ReadingEngine().read("HP 100").romaji.contains("100"));
  }

  @Test
  public void customNameAcrossTokenBoundaries() {
    ReadingEngine e = new ReadingEngine();
    e.setOverrides("八重=やえ\n八重神子=やえみこ");
    ReadingEngine.Reading r = e.read("八重神子に会いました。");
    assertTrue(r.romaji, r.romaji.startsWith("yaemiko ni"));
    assertTrue(r.romaji, r.romaji.contains("aimashita"));
  }

  @Test
  public void unknownKanjiRemainsVisible() {
    ReadingEngine.Reading r = new ReadingEngine().read("龯");
    assertTrue(r.uncertain);
    assertEquals("龯", r.romaji);
  }
}
