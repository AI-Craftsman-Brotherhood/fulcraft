package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution.AnalysisExecutionService;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisFlowTest {

  @TempDir Path tempDir;

  @Test
  void analyze_delegatesToService() throws IOException {
    AnalysisExecutionService service = mock(AnalysisExecutionService.class);
    AnalysisFlow flow = new AnalysisFlow(service);
    Config config = new Config();
    AnalysisResult result = new AnalysisResult("test");

    when(service.analyze(tempDir, config)).thenReturn(result);

    AnalysisResult actual = flow.analyze(tempDir, config);

    assertThat(actual).isSameAs(result);
    verify(service).analyze(tempDir, config);
  }

  @Test
  void validate_wrapsServiceResult() {
    AnalysisExecutionService service = mock(AnalysisExecutionService.class);
    AnalysisFlow flow = new AnalysisFlow(service);
    AnalysisResult result = new AnalysisResult("test");

    when(service.validate(result))
        .thenReturn(new AnalysisExecutionService.ValidationResult(true, "warn"));

    AnalysisFlow.ValidationResult validation = flow.validate(result);

    assertThat(validation.valid()).isTrue();
    assertThat(validation.warning()).isEqualTo("warn");
    assertThat(validation.hasWarning()).isTrue();
  }

  @Test
  void getStats_wrapsServiceStats() {
    AnalysisExecutionService service = mock(AnalysisExecutionService.class);
    AnalysisFlow flow = new AnalysisFlow(service);
    AnalysisResult result = new AnalysisResult("test");

    when(service.getStats(result)).thenReturn(new AnalysisExecutionService.AnalysisStats(2, 5, 1));

    AnalysisFlow.AnalysisStats stats = flow.getStats(result);

    assertThat(stats.classCount()).isEqualTo(2);
    assertThat(stats.methodCount()).isEqualTo(5);
    assertThat(stats.errorCount()).isEqualTo(1);
  }

  @Test
  void logStats_delegatesToService() {
    AnalysisExecutionService service = mock(AnalysisExecutionService.class);
    AnalysisFlow flow = new AnalysisFlow(service);
    AnalysisResult result = new AnalysisResult("test");

    flow.logStats(result);

    verify(service).logStats(result);
  }

  @Test
  void validationResult_hasWarning_returnsFalseForNullOrBlank() {
    assertThat(new AnalysisFlow.ValidationResult(true, null).hasWarning()).isFalse();
    assertThat(new AnalysisFlow.ValidationResult(true, " ").hasWarning()).isFalse();
  }

  @Test
  void validationResult_hasWarning_returnsTrueForNonBlank() {
    assertThat(new AnalysisFlow.ValidationResult(true, "warning").hasWarning()).isTrue();
  }

  @Test
  void constructor_rejectsNullService() {
    assertThatThrownBy(() -> new AnalysisFlow((AnalysisExecutionService) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_rejectsNullAnalysisPort() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AnalysisFlow((AnalysisPort) null))
        .withMessageContaining("analysisPort");
  }

  @Test
  void analyze_delegatesToAnalysisPort_whenConstructedWithPort() throws IOException {
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    AnalysisFlow flow = new AnalysisFlow(analysisPort);
    Config config = new Config();
    AnalysisResult result = new AnalysisResult("test");

    when(analysisPort.analyze(tempDir, config)).thenReturn(result);

    AnalysisResult actual = flow.analyze(tempDir, config);

    assertThat(actual).isSameAs(result);
    verify(analysisPort).analyze(tempDir, config);
  }
}
