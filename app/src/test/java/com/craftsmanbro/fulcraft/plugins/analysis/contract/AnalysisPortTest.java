package com.craftsmanbro.fulcraft.plugins.analysis.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AnalysisPortTest {

  @Test
  void analyze_configFirstDelegatesToPathFirstOverload() throws IOException {
    Config config = new Config();
    Path projectRoot = Path.of("dummy/path");
    AnalysisResult expectedResult = new AnalysisResult();
    RecordingAnalysisPort port = RecordingAnalysisPort.successful(expectedResult);

    AnalysisResult actual = port.analyze(config, projectRoot);

    assertThat(actual).isSameAs(expectedResult);
    assertThat(port.capturedConfig).isSameAs(config);
    assertThat(port.capturedProjectRoot).isSameAs(projectRoot);
    assertThat(port.analyzeCalls).isEqualTo(1);
  }

  @Test
  void analyze_configFirstPropagatesIOExceptionFromPathFirstOverload() {
    Config config = new Config();
    Path projectRoot = Path.of("dummy/path");
    IOException expected = new IOException("analysis failed");
    RecordingAnalysisPort port = RecordingAnalysisPort.failing(expected);

    Throwable thrown = catchThrowable(() -> port.analyze(config, projectRoot));

    assertThat(thrown).isSameAs(expected);
    assertThat(port.capturedConfig).isSameAs(config);
    assertThat(port.capturedProjectRoot).isSameAs(projectRoot);
    assertThat(port.analyzeCalls).isEqualTo(1);
  }

  private static final class RecordingAnalysisPort implements AnalysisPort {

    private final AnalysisResult analysisResult;
    private final IOException exceptionToThrow;
    private Config capturedConfig;
    private Path capturedProjectRoot;
    private int analyzeCalls;

    private RecordingAnalysisPort(AnalysisResult analysisResult, IOException exceptionToThrow) {
      this.analysisResult = analysisResult;
      this.exceptionToThrow = exceptionToThrow;
    }

    private static RecordingAnalysisPort successful(AnalysisResult analysisResult) {
      return new RecordingAnalysisPort(analysisResult, null);
    }

    private static RecordingAnalysisPort failing(IOException exceptionToThrow) {
      return new RecordingAnalysisPort(null, exceptionToThrow);
    }

    @Override
    public AnalysisResult analyze(Path projectRoot, Config config) throws IOException {
      this.capturedConfig = config;
      this.capturedProjectRoot = projectRoot;
      this.analyzeCalls++;
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      return analysisResult;
    }

    @Override
    public String getEngineName() {
      return "test";
    }

    @Override
    public boolean supports(Path projectRoot) {
      return true;
    }
  }
}
