package com.craftsmanbro.fulcraft.infrastructure.parser.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePathResolverAdditionalTest {

  @TempDir Path tempDir;

  private final SourcePathResolver resolver = new SourcePathResolver();

  @Test
  void resolve_usesConfiguredSourceRootPathsInAutoMode() throws IOException {
    Path configuredMain = tempDir.resolve("module/src/main/java");
    Files.createDirectories(configuredMain);
    Files.createFile(configuredMain.resolve("Configured.java"));

    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootPaths(List.of("missing", "module/src/main/java"));
    config.setAnalysis(analysisConfig);

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir, config);

    assertThat(dirs.mainSource()).contains(configuredMain);
    assertThat(config.getAnalysis().getSourceRootPaths()).containsExactly("module/src/main/java");
  }

  @Test
  void resolve_inStrictModeFallsBackToStandardTestCandidatesWhenDerivationFails()
      throws IOException {
    Path strictMain = tempDir.resolve("module/custom/java");
    Path testCandidate = tempDir.resolve("src/test/java");
    Files.createDirectories(strictMain);
    Files.createDirectories(testCandidate);

    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootMode("STRICT");
    analysisConfig.setSourceRootPaths(List.of("module/custom/java"));
    config.setAnalysis(analysisConfig);

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir, config);

    assertThat(dirs.mainSource()).contains(strictMain);
    assertThat(dirs.testSource()).contains(testCandidate);
    assertThat(config.getAnalysis().getSourceRootPaths()).containsExactly("module/custom/java");
  }

  @Test
  void resolve_honorsBuildGradleKtsSourceSetsAndUpdatesConfig() throws IOException {
    Path main = tempDir.resolve("kotlin/src/main/java");
    Path test = tempDir.resolve("kotlin/src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("App.java"));
    Files.createFile(test.resolve("AppTest.java"));
    Files.writeString(
        tempDir.resolve("build.gradle.kts"),
        """
            sourceSets {
              named("main") {
                java.srcDir("kotlin/src/main/java")
              }
              named("test") {
                java.srcDir("kotlin/src/test/java")
              }
            }
            """);

    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootPaths(List.of("ignored/by/gradle"));
    config.setAnalysis(analysisConfig);

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir, config);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
    assertThat(config.getAnalysis().getSourceRootPaths()).containsExactly("kotlin/src/main/java");
  }

  @Test
  void resolve_fallsBackToDefaultMainSourceWhenGradleDefinesOnlyTestSource() throws IOException {
    Path main = tempDir.resolve("src/main/java");
    Path test = tempDir.resolve("custom/src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("MainApp.java"));
    Files.createFile(test.resolve("MainAppTest.java"));
    Files.writeString(
        tempDir.resolve("build.gradle"),
        """
            sourceSets {
              test {
                java {
                  srcDirs = ['custom/src/test/java']
                }
              }
            }
            """);

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
  }

  @Test
  void resolve_fallsBackToDefaultTestSourceWhenPomDefinesOnlyMainSource() throws IOException {
    Path main = tempDir.resolve("source/main/java");
    Path test = tempDir.resolve("src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("PomApp.java"));
    Files.createFile(test.resolve("PomAppTest.java"));
    Files.writeString(
        tempDir.resolve("pom.xml"),
        """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <build>
                <sourceDirectory>source/main/java</sourceDirectory>
              </build>
            </project>
            """);

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
  }

  @Test
  void resolve_fallsBackToDefaultCandidatesWhenPomIsMalformed() throws IOException {
    Path main = tempDir.resolve("src/main/java");
    Path test = tempDir.resolve("src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("Fallback.java"));
    Files.createFile(test.resolve("FallbackTest.java"));
    Files.writeString(tempDir.resolve("pom.xml"), "<project><build><sourceDirectory>");

    SourcePathResolver.SourceDirectories dirs = resolver.resolve(tempDir);

    assertThat(dirs.mainSource()).contains(main);
    assertThat(dirs.testSource()).contains(test);
  }
}
