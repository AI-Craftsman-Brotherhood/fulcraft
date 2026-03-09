package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathExcluderTest {

  @Test
  void buildExcludeRoots_includesAppTestRoots_whenExcludeTestsTrue() {
    Path root = Paths.get("project").toAbsolutePath().normalize();
    List<Path> excludes = PathExcluder.buildExcludeRoots(root, List.of(), true);

    assertTrue(excludes.contains(root.resolve("app/src/test").normalize()));
    assertTrue(excludes.contains(root.resolve("app/src/test/java").normalize()));
  }

  @Test
  void buildExcludeRoots_omitsTestRoots_whenExcludeTestsFalse() {
    Path root = Paths.get("project").toAbsolutePath().normalize();
    List<Path> excludes = PathExcluder.buildExcludeRoots(root, List.of(), false);

    assertFalse(excludes.contains(root.resolve("src/test").normalize()));
    assertFalse(excludes.contains(root.resolve("app/src/test").normalize()));
  }

  @Test
  void buildExcludeRoots_addsConfiguredRelativeAndAbsolutePaths() {
    Path root = Paths.get("project").toAbsolutePath().normalize();
    Path absoluteConfigured = root.resolve("custom/absolute").normalize();

    List<Path> excludes =
        PathExcluder.buildExcludeRoots(
            root, List.of("custom/relative", absoluteConfigured.toString(), "target"), false);

    assertTrue(excludes.contains(root.resolve("custom/relative").normalize()));
    assertTrue(excludes.contains(absoluteConfigured));
    assertTrue(excludes.contains(root.resolve("target").normalize()));
  }

  @Test
  void buildExcludeRoots_acceptsNullConfiguredEntries() {
    Path root = Paths.get("project").toAbsolutePath().normalize();

    List<Path> excludes = PathExcluder.buildExcludeRoots(root, null, false);

    assertTrue(excludes.contains(root.resolve("build").normalize()));
    assertFalse(excludes.contains(root.resolve("src/test").normalize()));
  }

  @Test
  void buildExcludeRoots_ignoresNullAndBlankConfiguredPaths() {
    Path root = Paths.get("project").toAbsolutePath().normalize();
    List<String> configured = new ArrayList<>();
    configured.add(null);
    configured.add("");
    configured.add("   ");
    configured.add("custom/path");

    List<Path> excludes = PathExcluder.buildExcludeRoots(root, configured);

    assertTrue(excludes.contains(root.resolve("custom/path").normalize()));
    assertTrue(excludes.contains(root.resolve("src/test").normalize()));
  }

  @Test
  void isExcluded_matchesPathUnderAnyExcludeRoot() {
    Path root = Paths.get("project").toAbsolutePath().normalize();
    List<Path> excludes = PathExcluder.buildExcludeRoots(root, List.of("custom"), false);

    assertTrue(PathExcluder.isExcluded(root.resolve("custom/App.java"), excludes));
    assertFalse(PathExcluder.isExcluded(root.resolve("src/main/java/App.java"), excludes));
  }
}
