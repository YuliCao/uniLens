package io.github.yomilens;

import static org.junit.Assert.*;

import org.junit.Test;

public class FrameDifferenceTest {
  @Test
  public void firstFrameIsAlwaysNew() {
    assertFalse(FrameDifference.similar(new int[100], null));
  }

  @Test
  public void identicalIsSkipped() {
    assertTrue(FrameDifference.similar(new int[100], new int[100]));
  }

  @Test
  public void oneSmallGlyphChangeIsNotLostInGlobalAverage() {
    int[] image = new int[24576];
    image[173] = 0xff333333;
    assertFalse(FrameDifference.similar(image, new int[24576]));
  }

  @Test
  public void colorOnlyChangeIsDetected() {
    assertFalse(FrameDifference.similar(new int[] {0xffff0000}, new int[] {0xff00ff00}));
  }
}
