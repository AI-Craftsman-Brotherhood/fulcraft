package com.craftsmanbro.fulcraft.infrastructure.parser.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePathResolverStrictModeTest {

  @TempDir Path tempDir;

  @Test
  void shouldThrow_whenStrictModeHasNoValidSourceRoot() {
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootMode("STRICT");
    analysisConfig.setSourceRootPaths(List.of("missing/src/main/java"));
    config.setAnalysis(analysisConfig);

    SourcePathResolver resolver = new SourcePathResolver();

    assertThatThrownBy(() -> resolver.resolve(tempDir, config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STRICT MODE ERROR")
        .hasMessageContaining("missing/src/main/java");
  }

  @Test
  void derivesTestSourceByMainSegmentInStrictMode() throws IOException {
    Path main = tempDir.resolve("domain-main/src/main/java");
    Path test = tempDir.resolve("domain-main/src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);

    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootMode("STRICT");
    analysisConfig.setSourceRootPaths(List.of("domain-main/src/main/java"));
    config.setAnalysis(analysisConfig);

    SourcePathResolver resolver = new SourcePathResolver();
    var dirs = resolver.resolve(tempDir, config);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
  }
}
