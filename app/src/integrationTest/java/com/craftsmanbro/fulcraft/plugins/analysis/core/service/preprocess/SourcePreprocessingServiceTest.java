package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for SourcePreprocessingService. */
class SourcePreprocessingServiceTest {

  @TempDir Path tempDir;

  private SourcePreprocessingService service;
  private Config config;

  @BeforeEach
  void setUp() throws IOException {
    service = new SourcePreprocessingService();
    config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");

    // Create minimal project structure
    Files.createDirectories(tempDir.resolve("src/main/java"));
  }

  @Test
  void preprocess_shouldReturnResult_whenProjectExists() {
    SourcePreprocessor.Result result =
        service.preprocess(tempDir, config, tempDir.resolve("analysis"));

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isNotNull();
  }

  @Test
  void preprocess_shouldThrowNullPointerException_whenProjectRootIsNull() {
    assertThatThrownBy(() -> service.preprocess(null, config, tempDir.resolve("analysis")))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void preprocess_shouldThrowNullPointerException_whenConfigIsNull() {
    assertThatThrownBy(() -> service.preprocess(tempDir, null, tempDir.resolve("analysis")))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void preprocess_usesConfiguredSourceRoots_whenValid() throws IOException {
    Path customRoot = tempDir.resolve("custom/src");
    Files.createDirectories(customRoot);
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootPaths(List.of("custom/src", "missing", " "));
    config.setAnalysis(analysisConfig);

    RecordingPreprocessor preprocessor = new RecordingPreprocessor();
    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourcePathResolver.SourceDirectories resolve(Path rootPath, Config config) {
            throw new AssertionError("resolver should not be called");
          }
        };

    SourcePreprocessingService service = new SourcePreprocessingService(preprocessor, resolver);

    service.preprocess(tempDir, config, tempDir.resolve("analysis"));

    assertThat(preprocessor.getCapturedRoots()).containsExactly(customRoot);
  }

  @Test
  void preprocess_fallsBackToResolver_whenConfiguredRootsMissing() throws IOException {
    Path fallbackRoot = tempDir.resolve("fallback/src/main/java");
    Files.createDirectories(fallbackRoot);
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootPaths(List.of("missing1", "missing2"));
    config.setAnalysis(analysisConfig);

    RecordingPreprocessor preprocessor = new RecordingPreprocessor();
    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourcePathResolver.SourceDirectories resolve(Path rootPath, Config config) {
            return new SourcePathResolver.SourceDirectories(
                Optional.of(fallbackRoot), Optional.empty());
          }
        };

    SourcePreprocessingService service = new SourcePreprocessingService(preprocessor, resolver);

    service.preprocess(tempDir, config, tempDir.resolve("analysis"));

    assertThat(preprocessor.getCapturedRoots()).containsExactly(fallbackRoot);
  }

  @Test
  void preprocess_fallsBackToDefault_whenResolverThrows() {
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setSourceRootPaths(List.of("missing1"));
    config.setAnalysis(analysisConfig);

    RecordingPreprocessor preprocessor = new RecordingPreprocessor();
    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourcePathResolver.SourceDirectories resolve(Path rootPath, Config config) {
            throw new IllegalStateException("strict failure");
          }
        };

    SourcePreprocessingService service = new SourcePreprocessingService(preprocessor, resolver);

    service.preprocess(tempDir, config, tempDir.resolve("analysis"));

    assertThat(preprocessor.getCapturedRoots()).containsExactly(tempDir.resolve("src/main/java"));
  }

  @Test
  void isStrictModeFailure_shouldReturnTrue_whenStatusIsFailed() {
    // Create result with FAILED status
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED,
            List.of(tempDir),
            List.of(tempDir),
            "TEST",
            "Test failure",
            0);

    boolean isFailure = service.isStrictModeFailure(result);

    assertThat(isFailure).isTrue();
  }

  @Test
  void isStrictModeFailure_shouldReturnFalse_whenStatusIsSkipped() {
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SKIPPED, List.of(tempDir), List.of(tempDir), null, null, 0);

    boolean isFailure = service.isStrictModeFailure(result);

    assertThat(isFailure).isFalse();
  }

  @Test
  void getFailureReason_shouldReturnReason_whenFailed() {
    String expectedReason = "Test failure reason";
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED,
            List.of(tempDir),
            List.of(tempDir),
            "TEST",
            expectedReason,
            0);

    String reason = service.getFailureReason(result);

    assertThat(reason).isEqualTo(expectedReason);
  }

  @Test
  void buildProjectSymbolIndex_shouldReturnIndex_whenValidProject() {
    SourcePreprocessor.Result preprocessResult =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SKIPPED, List.of(tempDir), List.of(tempDir), null, null, 0);

    var index = service.buildProjectSymbolIndex(tempDir, config, preprocessResult);

    assertThat(index).isNotNull();
  }

  @Test
  void buildProjectSymbolIndex_prefersPreprocessedRoots_whenPreprocessSucceeded()
      throws IOException {
    Path mainSource = tempDir.resolve("src/main/java");
    Path testSource = tempDir.resolve("src/test/java");
    Path preprocessed = tempDir.resolve(".utg/preprocess");
    writeJavaSource(mainSource, "com.example", "MainOnly");
    writeJavaSource(testSource, "com.example", "TestOnly");
    writeJavaSource(preprocessed, "com.example", "PreprocessedOnly");

    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourceDirectories resolve(Path rootPath, Config config) {
            return new SourceDirectories(Optional.of(mainSource), Optional.of(testSource));
          }
        };
    SourcePreprocessingService service =
        new SourcePreprocessingService(new RecordingPreprocessor(), resolver);
    SourcePreprocessor.Result preprocessResult =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SUCCESS,
            List.of(mainSource),
            List.of(preprocessed),
            "DELOMBOK",
            null,
            0);

    var index = service.buildProjectSymbolIndex(tempDir, config, preprocessResult);

    assertThat(index.hasClass("com.example.PreprocessedOnly")).isTrue();
    assertThat(index.hasClass("com.example.TestOnly")).isTrue();
    assertThat(index.hasClass("com.example.MainOnly")).isFalse();
  }

  @Test
  void buildProjectSymbolIndex_usesResolvedMainSource_whenPreprocessNotSuccessful()
      throws IOException {
    Path mainSource = tempDir.resolve("src/main/java");
    Path testSource = tempDir.resolve("src/test/java");
    writeJavaSource(mainSource, "com.example", "MainOnly");
    writeJavaSource(testSource, "com.example", "TestOnly");

    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourceDirectories resolve(Path rootPath, Config config) {
            return new SourceDirectories(Optional.of(mainSource), Optional.of(testSource));
          }
        };
    SourcePreprocessingService service =
        new SourcePreprocessingService(new RecordingPreprocessor(), resolver);
    SourcePreprocessor.Result preprocessResult =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED,
            List.of(mainSource),
            List.of(tempDir.resolve(".utg/preprocess")),
            "DELOMBOK",
            "failed",
            0);

    var index = service.buildProjectSymbolIndex(tempDir, config, preprocessResult);

    assertThat(index.hasClass("com.example.MainOnly")).isTrue();
    assertThat(index.hasClass("com.example.TestOnly")).isTrue();
  }

  @Test
  void loadExternalConfigValues_shouldReturnEmptyMap_whenConfigIsNull() {
    Map<String, String> values = service.loadExternalConfigValues(tempDir, null);

    assertThat(values).isEmpty();
  }

  @Test
  void loadExternalConfigValues_shouldReturnEmptyMap_whenResolutionDisabled() {
    config.setAnalysis(new Config.AnalysisConfig());
    config.getAnalysis().setExternalConfigResolution(false);

    Map<String, String> values = service.loadExternalConfigValues(tempDir, config);

    assertThat(values).isEmpty();
  }

  @Test
  void loadExternalConfigValues_loadsValues_whenResolutionEnabled() throws IOException {
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setExternalConfigResolution(true);
    config.setAnalysis(analysisConfig);

    Path mainResources = tempDir.resolve("src/main/resources");
    Path resourceRoot = tempDir.resolve("resources");
    Files.createDirectories(mainResources);
    Files.createDirectories(resourceRoot);
    Files.writeString(
        mainResources.resolve("application.properties"),
        "shared=fromMain\nmain.only=mainValue\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        resourceRoot.resolve("application.yaml"),
        "shared: fromResources\nresource.only: resourceValue\n",
        StandardCharsets.UTF_8);

    Map<String, String> values = service.loadExternalConfigValues(tempDir, config);

    assertThat(values).containsEntry("main.only", "mainValue");
    assertThat(values).containsEntry("resource.only", "resourceValue");
    assertThat(values).containsEntry("shared", "fromResources");
  }

  @Test
  void resolveSourceDirectories_delegatesToResolver() {
    SourcePathResolver.SourceDirectories expected =
        new SourcePathResolver.SourceDirectories(
            Optional.of(tempDir.resolve("main")), Optional.of(tempDir.resolve("test")));
    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourceDirectories resolve(Path rootPath, Config config) {
            return expected;
          }
        };
    SourcePreprocessingService service =
        new SourcePreprocessingService(new RecordingPreprocessor(), resolver);

    SourcePathResolver.SourceDirectories actual = service.resolveSourceDirectories(tempDir, config);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void preprocess_usesDefaultMainSource_whenResolverReturnsEmpty() {
    Config config = new Config();
    config.setAnalysis(new Config.AnalysisConfig());
    RecordingPreprocessor preprocessor = new RecordingPreprocessor();
    SourcePathResolver resolver =
        new SourcePathResolver() {
          @Override
          public SourceDirectories resolve(Path rootPath, Config config) {
            return new SourceDirectories(Optional.empty(), Optional.empty());
          }
        };
    SourcePreprocessingService service = new SourcePreprocessingService(preprocessor, resolver);

    service.preprocess(tempDir, config, tempDir.resolve("analysis"));

    assertThat(preprocessor.getCapturedRoots()).containsExactly(tempDir.resolve("src/main/java"));
  }

  private static void writeJavaSource(Path root, String packageName, String className)
      throws IOException {
    Path packagePath = root.resolve(packageName.replace('.', '/'));
    Files.createDirectories(packagePath);
    Files.writeString(
        packagePath.resolve(className + ".java"),
        "package " + packageName + ";\n" + "\n" + "public class " + className + " {}\n",
        StandardCharsets.UTF_8);
  }

  private static final class RecordingPreprocessor extends SourcePreprocessor {
    private List<Path> capturedRoots;

    @Override
    public SourcePreprocessor.Result preprocess(
        Path projectRoot, List<Path> sourceRoots, Config config, Path outputDir) {
      capturedRoots = List.copyOf(sourceRoots);
      return new SourcePreprocessor.Result(
          SourcePreprocessor.Status.SKIPPED, sourceRoots, sourceRoots, null, null, 0);
    }

    private List<Path> getCapturedRoots() {
      return capturedRoots;
    }
  }
}
