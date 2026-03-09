package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePathsTest {

  @TempDir Path tempDir;

  @Test
  void resolve_delegatesToResolver() throws IOException {
    Path main = tempDir.resolve("src/main/java");
    Path test = tempDir.resolve("src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("App.java"));

    SourcePathResolver.SourceDirectories dirs = SourcePaths.resolve(tempDir, null);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
  }

  @Test
  void resolve_rejectsNullRoot() {
    assertThatThrownBy(() -> SourcePaths.resolve(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("projectRoot");
  }
}
