package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathOrderTest {

  @TempDir Path tempDir;

  @Test
  void stableComparatorSortsByNormalizedAbsolutePath() {
    Path later = tempDir.resolve("b").resolve("Sample.java");
    Path earlierWithExtraSegments =
        tempDir.resolve("a").resolve(".").resolve("..").resolve("a").resolve("Sample.java");

    List<Path> sorted =
        List.of(later, earlierWithExtraSegments).stream().sorted(PathOrder.STABLE).toList();

    assertEquals(earlierWithExtraSegments, sorted.get(0));
    assertEquals(later, sorted.get(1));
  }

  @Test
  void stableComparatorTreatsEquivalentNormalizedPathsAsEqual() {
    Path first = tempDir.resolve("pkg").resolve("..").resolve("pkg").resolve("Foo.java");
    Path second = tempDir.resolve("pkg").resolve("Foo.java");

    assertEquals(0, PathOrder.STABLE.compare(first, second));
  }
}
