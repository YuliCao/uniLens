package io.github.yomilens;

import java.util.List;

/** Screen-space label placement, preferring clear space near the original line. */
final class LabelPlacer {
  static final class Box {
    final float left, top, right, bottom;

    Box(float l, float t, float r, float b) {
      left = l;
      top = t;
      right = r;
      bottom = b;
    }

    float width() {
      return right - left;
    }

    float height() {
      return bottom - top;
    }
  }

  static Box place(
      Box source,
      float width,
      float height,
      float screenWidth,
      float screenHeight,
      float gap,
      List<Box> obstacles,
      List<Box> placed) {
    width = Math.min(width, Math.max(1, screenWidth - gap * 2));
    height = Math.min(height, Math.max(1, screenHeight - gap * 2));
    float[][] candidates = {
      {source.left, source.top - height - gap},
      {source.left, source.bottom + gap},
      {source.right + gap, source.top},
      {source.left - width - gap, source.top},
      {source.right - width, source.top - height - gap},
      {source.right - width, source.bottom + gap},
      {source.left, source.top - height * 2 - gap * 2},
      {screenWidth - width - gap, source.top - height - gap}
    };
    Box best = null;
    double bestScore = Double.MAX_VALUE;
    for (int i = 0; i < candidates.length; i++) {
      float x = Math.max(gap, Math.min(candidates[i][0], screenWidth - width - gap));
      float y = Math.max(gap, Math.min(candidates[i][1], screenHeight - height - gap));
      Box candidate = new Box(x, y, x + width, y + height);
      double score = i * .1 + Math.abs(x - source.left) + Math.abs(y - (source.top - height - gap));
      for (Box other : obstacles) score += overlap(candidate, other) * 1000;
      for (Box other : placed) score += overlap(candidate, other) * 2000;
      if (score < bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best;
  }

  static float overlap(Box a, Box b) {
    return Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left))
        * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
  }
}
