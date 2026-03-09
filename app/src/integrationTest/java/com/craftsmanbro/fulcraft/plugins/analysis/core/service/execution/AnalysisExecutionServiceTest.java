package com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution.AnalysisExecutionService.AnalysisStats;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution.AnalysisExecutionService.ValidationResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link AnalysisExecutionService}. */
class AnalysisExecutionServiceTest {

  @TempDir Path tempDir;

  private AnalysisPort analysisPort;
  private AnalysisExecutionService service;
  private Config config;

  @BeforeEach
  void setUp() {
    analysisPort = mock(AnalysisPort.class);
    service = new AnalysisExecutionService(analysisPort);
    config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setId("test-project");
  }

  @Test
  @DisplayName("constructor: Should throw NPE when analysisPort is null")
  void constructor_shouldThrowNPE_whenAnalysisPortIsNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AnalysisExecutionService(null))
        .withMessageContaining("analysisPort");
  }

  @Test
  @DisplayName("analyze: Should delegate execution to AnalysisPort")
  void analyze_shouldDelegateToAnalysisPort() throws IOException {
    AnalysisResult expected = createAnalysisResultWithClass("com.example.MyClass");
    when(analysisPort.analyze(any(Path.class), any(Config.class))).thenReturn(expected);

    AnalysisResult result = service.analyze(tempDir, config);

    assertThat(result).isSameAs(expected);
    verify(analysisPort).analyze(tempDir, config);
  }

  @Test
  @DisplayName("analyze: Should throw NPE when projectRoot is null")
  void analyze_shouldThrowNPE_whenProjectRootIsNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> service.analyze(null, config))
        .withMessageContaining("projectRoot");
  }

  @Test
  @DisplayName("analyze: Should throw NPE when config is null")
  void analyze_shouldThrowNPE_whenConfigIsNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> service.analyze(tempDir, null))
        .withMessageContaining("config");
  }

  @Test
  @DisplayName("analyze: Should propagate IOException from port")
  void analyze_shouldThrowIOException_whenPortFails() throws IOException {
    when(analysisPort.analyze(any(Path.class), any(Config.class)))
        .thenThrow(new IOException("Analysis failed"));

    assertThatThrownBy(() -> service.analyze(tempDir, config))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Analysis failed");
  }

  @Test
  @DisplayName("validate: Should return valid when result has classes and no errors")
  void validate_shouldReturnValid_whenResultHasClasses() {
    AnalysisResult result = createAnalysisResultWithClass("com.example.MyClass");

    ValidationResult validation = service.validate(result);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.hasWarning()).isFalse();
    assertThat(validation.warning()).isNull();
  }

  @Test
  @DisplayName("validate: Should return valid with warning when result has no classes")
  void validate_shouldReturnValidWithWarning_whenResultHasNoClasses() {
    AnalysisResult result = new AnalysisResult("test");
    result.setClasses(List.of());

    ValidationResult validation = service.validate(result);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.hasWarning()).isTrue();
    // Avoid strict string matching, but check it's not empty
    assertThat(validation.warning()).isNotBlank();
  }

  @Test
  @DisplayName("validate: Should return valid with warning when result has errors")
  void validate_shouldReturnValidWithWarning_whenResultHasErrors() {
    AnalysisResult result = createAnalysisResultWithClass("com.example.MyClass");
    result.setAnalysisErrors(List.of(new AnalysisError("File.java", "Error message", 10)));

    ValidationResult validation = service.validate(result);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.hasWarning()).isTrue();
    assertThat(validation.warning()).isNotBlank();
  }

  @Test
  @DisplayName("validate: Should combine warnings when both errors and no classes exist")
  void validate_shouldCombineWarnings() {
    AnalysisResult result = new AnalysisResult("test");
    result.setClasses(List.of());
    result.setAnalysisErrors(List.of(new AnalysisError("File.java", "Error message", 10)));

    ValidationResult validation = service.validate(result);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.hasWarning()).isTrue();
    assertThat(validation.warning()).isNotBlank();
    assertThat(validation.warning()).contains(";");
  }

  @Test
  @DisplayName("validate: Should return invalid when result is null")
  void validate_shouldReturnInvalid_whenResultIsNull() {
    ValidationResult validation = service.validate(null);

    assertThat(validation.valid()).isFalse();
    assertThat(validation.warning()).isNotBlank();
  }

  @Test
  @DisplayName("validate: hasWarning should be false when warning is blank")
  void validationResult_hasWarning_shouldBeFalse_whenWarningIsBlank() {
    ValidationResult validationResult = new ValidationResult(true, "   ");

    assertThat(validationResult.hasWarning()).isFalse();
  }

  @Test
  @DisplayName("validate: hasWarning should be true when warning has text")
  void validationResult_hasWarning_shouldBeTrue_whenWarningHasText() {
    ValidationResult validationResult = new ValidationResult(true, "warning");

    assertThat(validationResult.hasWarning()).isTrue();
  }

  @Test
  @DisplayName("getStats: Should skip null classes and aggregate method counts")
  void getStats_shouldSkipNullClasses_andAggregateMethodCounts() {
    AnalysisResult result = new AnalysisResult("test-project");
    ClassInfo classFromMethodCount = createClassInfoWithMethodCount("com.example.ClassA", 2);
    ClassInfo classFromMethods = createClassInfoWithMethods("com.example.ClassB", 3);

    List<ClassInfo> classes = new ArrayList<>();
    classes.add(null);
    classes.add(classFromMethodCount);
    classes.add(classFromMethods);
    result.setClasses(classes);
    result.setAnalysisErrors(
        List.of(
            new AnalysisError("FileA.java", "msg-a", 1),
            new AnalysisError("FileB.java", "msg-b", 2)));

    AnalysisStats stats = service.getStats(result);

    assertThat(stats.classCount()).isEqualTo(2);
    assertThat(stats.methodCount()).isEqualTo(5);
    assertThat(stats.errorCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("getStats: Should return zero counts when result is null")
  void getStats_shouldReturnZeroCounts_whenResultIsNull() {
    AnalysisStats stats = service.getStats(null);

    assertThat(stats.classCount()).isEqualTo(0);
    assertThat(stats.methodCount()).isEqualTo(0);
    assertThat(stats.errorCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("logStats: Should not throw when result is valid")
  void logStats_shouldNotThrow_whenResultIsValid() {
    AnalysisResult result = new AnalysisResult("test-project");
    List<ClassInfo> classes = new ArrayList<>();
    classes.add(null);
    classes.add(createClassInfoWithMethodCount("com.example.ClassA", 2));
    classes.add(createClassInfoWithMethods("com.example.ClassB", 1));
    result.setClasses(classes);

    assertThatCode(() -> service.logStats(result)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("logStats: Should not throw exception when result is null")
  void logStats_shouldNotThrow_whenResultIsNull() {
    assertThatCode(() -> service.logStats(null)).doesNotThrowAnyException();
  }

  private AnalysisResult createAnalysisResultWithClass(String fqn) {
    AnalysisResult result = new AnalysisResult("test-project");
    result.setClasses(List.of(createClassInfo(fqn)));
    return result;
  }

  private ClassInfo createClassInfoWithMethodCount(String fqn, int methodCount) {
    ClassInfo classInfo = createClassInfo(fqn);
    classInfo.setMethodCount(methodCount);
    return classInfo;
  }

  private ClassInfo createClassInfoWithMethods(String fqn, int methodSize) {
    ClassInfo classInfo = createClassInfo(fqn);
    List<MethodInfo> methods = new ArrayList<>();
    for (int i = 0; i < methodSize; i++) {
      methods.add(new MethodInfo());
    }
    classInfo.setMethods(methods);
    return classInfo;
  }

  private ClassInfo createClassInfo(String fqn) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setFilePath(fqn.replace('.', '/') + ".java");
    return classInfo;
  }
}
