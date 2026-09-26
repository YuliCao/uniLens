package io.github.yomilens;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class LabelPlacerTest {
  @Test
  public void prefersAboveOriginal() {
    LabelPlacer.Box source = new LabelPlacer.Box(10, 100, 150, 130);
    LabelPlacer.Box result =
        LabelPlacer.place(source, 100, 20, 400, 600, 4, List.of(source), List.of());
    assertEquals(76, result.top, .01);
  }

  @Test
  public void avoidsOriginalWhenNoTopSpace() {
    LabelPlacer.Box source = new LabelPlacer.Box(10, 5, 150, 35);
    LabelPlacer.Box result =
        LabelPlacer.place(source, 100, 20, 400, 600, 4, List.of(source), List.of());
    assertEquals(0, LabelPlacer.overlap(source, result), .01);
  }

  @Test
  public void avoidsAdjacentOriginalAndPreviousLabel() {
    LabelPlacer.Box source = new LabelPlacer.Box(10, 50, 150, 80),
        next = new LabelPlacer.Box(10, 83, 150, 120),
        label = new LabelPlacer.Box(0, 0, 150, 50);
    LabelPlacer.Box result =
        LabelPlacer.place(source, 100, 30, 400, 600, 4, List.of(source, next), List.of(label));
    assertEquals(
        0,
        LabelPlacer.overlap(source, result)
            + LabelPlacer.overlap(next, result)
            + LabelPlacer.overlap(label, result),
        .01);
  }

  @Test
  public void clampsToScreen() {
    LabelPlacer.Box source = new LabelPlacer.Box(350, 580, 399, 599);
    LabelPlacer.Box result =
        LabelPlacer.place(source, 190, 40, 400, 600, 4, List.of(source), List.of());
    assertTrue(result.left >= 0 && result.top >= 0 && result.right <= 400 && result.bottom <= 600);
  }
}
