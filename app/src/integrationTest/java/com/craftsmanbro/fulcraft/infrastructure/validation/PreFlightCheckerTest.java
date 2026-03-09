package com.craftsmanbro.fulcraft.infrastructure.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.validation.impl.PreFlightChecker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreFlightCheckerTest {

  @TempDir Path tempDir;
  private PreFlightChecker checker;

  @BeforeEach
  void setUp() {
    checker = new PreFlightChecker();
  }

  @Test
  void check_ValidMavenProject_ShouldPass() throws IOException {
    // Arrange
    Files.createFile(tempDir.resolve("pom.xml"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    // Act & Assert
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_ValidGradleProject_ShouldPass() throws IOException {
    // Arrange
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    // Act & Assert
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_ValidGradleKtsProject_ShouldPass() throws IOException {
    // Arrange
    Files.createFile(tempDir.resolve("build.gradle.kts"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    // Act & Assert
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_NullProjectRoot_ShouldThrowException() {
    assertThrows(NullPointerException.class, () -> checker.check((Path) null));
  }

  @Test
  void check_NonExistentDirectory_ShouldThrowException() {
    Path nonExistentPath = tempDir.resolve("non-existent");
    Exception exception =
        assertThrows(IllegalStateException.class, () -> checker.check(nonExistentPath));
    assertTrue(exception.getMessage().contains("does not exist"));
  }

  @Test
  void check_MissingBuildFile_ShouldThrowException() throws IOException {
    // Arrange: No pom.xml or build.gradle
    Files.createDirectories(tempDir.resolve("src/main/java"));

    // Act & Assert
    Exception exception = assertThrows(IllegalStateException.class, () -> checker.check(tempDir));
    assertTrue(exception.getMessage().contains("does not contain build definition"));
  }

  @Test
  void check_MissingSrcDir_ShouldThrowException() throws IOException {
    // Arrange: Has build file but no recognizable source layout
    Files.createFile(tempDir.resolve("pom.xml"));

    // Act & Assert
    Exception exception = assertThrows(IllegalStateException.class, () -> checker.check(tempDir));
    assertTrue(exception.getMessage().contains("recognizable main source directory"));
  }

  @Test
  void check_AppSrcMainJavaLayout_ShouldPass() throws IOException {
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("app/src/main/java"));
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_SettingsGradle_ShouldPass() throws IOException {
    Files.createFile(tempDir.resolve("settings.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_SettingsGradleKts_ShouldPass() throws IOException {
    Files.createFile(tempDir.resolve("settings.gradle.kts"));
    Files.createDirectories(tempDir.resolve("src/main/java"));
    assertDoesNotThrow(() -> checker.check(tempDir));
  }

  @Test
  void check_PathIsFile_ShouldThrowException() throws IOException {
    Path file = Files.createFile(tempDir.resolve("pom.xml"));

    Exception exception = assertThrows(IllegalStateException.class, () -> checker.check(file));
    assertTrue(exception.getMessage().contains("not a directory"));
  }

  @Test
  void check_CustomSourceRootFromConfig_ShouldPass() throws IOException {
    Files.createFile(tempDir.resolve("pom.xml"));
    Files.createDirectories(tempDir.resolve("custom-src"));

    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootMode("STRICT");
    analysisConfig.setSourceRootPaths(List.of("custom-src"));
    config.setAnalysis(analysisConfig);

    assertDoesNotThrow(() -> checker.check(tempDir, config));
  }

  @Test
  void check_WithProjectRootAndLlmClientOverload_ShouldRunHealthCheck() throws IOException {
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    final boolean[] healthCheckCalled = {false};
    LlmClientPort llmClient =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile("fake", Set.of(), Optional.empty());
          }

          @Override
          public boolean isHealthy() {
            healthCheckCalled[0] = true;
            return true;
          }
        };

    assertDoesNotThrow(() -> checker.check(tempDir, llmClient));
    assertTrue(healthCheckCalled[0]);
  }

  @Test
  void check_HealthyLlmClient_ShouldPass() throws IOException {
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    LlmClientPort llmClient =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile("fake", Set.of(), Optional.empty());
          }

          @Override
          public boolean isHealthy() {
            return true;
          }
        };

    assertDoesNotThrow(() -> checker.check(tempDir, null, llmClient));
  }

  @Test
  void check_UnhealthyLlmClient_ShouldThrowException() throws IOException {
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    LlmClientPort llmClient =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile("fake", Set.of(), Optional.empty());
          }

          @Override
          public boolean isHealthy() {
            return false;
          }
        };

    Exception exception =
        assertThrows(IllegalStateException.class, () -> checker.check(tempDir, null, llmClient));
    assertTrue(exception.getMessage().contains("LLM Client health check failed"));
  }

  @Test
  void check_LlmClientThrows_ShouldWrapException() throws IOException {
    Files.createFile(tempDir.resolve("build.gradle"));
    Files.createDirectories(tempDir.resolve("src/main/java"));

    RuntimeException cause = new RuntimeException("boom");
    LlmClientPort llmClient =
        new LlmClientPort() {
          @Override
          public String generateTest(String prompt, Config.LlmConfig llmConfig) {
            return "";
          }

          @Override
          public ProviderProfile profile() {
            return new ProviderProfile("fake", Set.of(), Optional.empty());
          }

          @Override
          public boolean isHealthy() {
            throw cause;
          }
        };

    Exception exception =
        assertThrows(IllegalStateException.class, () -> checker.check(tempDir, null, llmClient));
    assertTrue(exception.getMessage().contains("LLM Client health check failed"));
    assertSame(cause, exception.getCause());
  }

  @Test
  void constructor_NullSourcePathResolver_ShouldThrowException() {
    assertThrows(NullPointerException.class, () -> new PreFlightChecker(null));
  }
}
