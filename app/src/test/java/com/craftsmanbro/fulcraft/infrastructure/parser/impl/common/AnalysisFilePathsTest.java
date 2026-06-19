package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisFilePathsTest {

  private static final Path PROJECT_ROOT = Path.of("/project");
  private static final Path SOURCE_ROOT = Path.of("/project/src/main/java");

  @Test
  @DisplayName("returns a project-root-relative path keeping the source-root prefix")
  void projectRelativeKeepsSourceRootPrefix() {
    final Path file = Path.of("/project/src/main/java/com/demo/model/Circle.java");

    assertThat(AnalysisFilePaths.toProjectRelative(file, PROJECT_ROOT, SOURCE_ROOT))
        .isEqualTo("src/main/java/com/demo/model/Circle.java");
  }

  @Test
  @DisplayName("records and regular classes resolve to the same convention")
  void recordAndClassUseSameConvention() {
    final Path record = Path.of("/project/src/main/java/com/demo/model/Circle.java");
    final Path clazz = Path.of("/project/src/main/java/com/demo/model/Shape.java");

    final String recordPath =
        AnalysisFilePaths.toProjectRelative(record, PROJECT_ROOT, SOURCE_ROOT);
    final String classPath = AnalysisFilePaths.toProjectRelative(clazz, PROJECT_ROOT, SOURCE_ROOT);

    assertThat(recordPath).startsWith("src/main/java/");
    assertThat(classPath).startsWith("src/main/java/");
  }

  @Test
  @DisplayName("prefers the project root over the source root when both match")
  void prefersProjectRootOverSourceRoot() {
    final Path file = Path.of("/project/src/main/java/com/demo/Foo.java");

    // Source root is a child of project root; project-root-relative must win.
    assertThat(AnalysisFilePaths.toProjectRelative(file, PROJECT_ROOT, SOURCE_ROOT))
        .isEqualTo("src/main/java/com/demo/Foo.java");
  }

  @Test
  @DisplayName("falls back to source root when project root is null")
  void fallsBackToSourceRootWhenProjectRootNull() {
    final Path file = Path.of("/project/src/main/java/com/demo/Foo.java");

    assertThat(AnalysisFilePaths.toProjectRelative(file, null, SOURCE_ROOT))
        .isEqualTo("com/demo/Foo.java");
  }

  @Test
  @DisplayName("falls back to the absolute path when the file is outside both roots")
  void fallsBackToAbsoluteWhenOutsideRoots() {
    final Path file = Path.of("/elsewhere/Generated.java");

    assertThat(AnalysisFilePaths.toProjectRelative(file, PROJECT_ROOT, SOURCE_ROOT))
        .isEqualTo("/elsewhere/Generated.java");
  }
}
