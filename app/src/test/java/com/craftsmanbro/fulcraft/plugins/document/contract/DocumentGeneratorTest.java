package com.craftsmanbro.fulcraft.plugins.document.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DocumentGeneratorTest {

  @Test
  void defaultMethodsReturnExpectedDefaults() {
    DocumentGenerator generator = new RecordingDocumentGenerator(0, null);

    assertThat(generator.getFormat()).isEqualTo("unknown");
    assertThat(generator.getFileExtension()).isEqualTo(".txt");
  }

  @Test
  void overriddenDefaultMethodsReturnCustomValues() {
    DocumentGenerator generator = new CustomFormatDocumentGenerator();

    assertThat(generator.getFormat()).isEqualTo("markdown");
    assertThat(generator.getFileExtension()).isEqualTo(".md");
  }

  @Test
  void generateReturnsConfiguredValueAndCapturesArguments() throws IOException {
    AnalysisResult result = new AnalysisResult();
    Path outputDir = Path.of("docs/output");
    Config config = new Config();
    RecordingDocumentGenerator generator = new RecordingDocumentGenerator(3, null);

    int generatedCount = generator.generate(result, outputDir, config);

    assertThat(generatedCount).isEqualTo(3);
    assertThat(generator.capturedResult).isSameAs(result);
    assertThat(generator.capturedOutputDir).isEqualTo(outputDir);
    assertThat(generator.capturedConfig).isSameAs(config);
    assertThat(generator.generateCalls).isEqualTo(1);
  }

  @Test
  void generatePropagatesIOException() {
    IOException expected = new IOException("write failed");
    RecordingDocumentGenerator generator = new RecordingDocumentGenerator(0, expected);

    Throwable thrown =
        catchThrowable(
            () -> generator.generate(new AnalysisResult(), Path.of("docs"), new Config()));

    assertThat(thrown).isSameAs(expected);
    assertThat(generator.generateCalls).isEqualTo(1);
  }

  private static class RecordingDocumentGenerator implements DocumentGenerator {
    private final int returnValue;
    private final IOException exceptionToThrow;
    private AnalysisResult capturedResult;
    private Path capturedOutputDir;
    private Config capturedConfig;
    private int generateCalls;

    private RecordingDocumentGenerator(int returnValue, IOException exceptionToThrow) {
      this.returnValue = returnValue;
      this.exceptionToThrow = exceptionToThrow;
    }

    @Override
    public int generate(AnalysisResult result, Path outputDir, Config config) throws IOException {
      this.capturedResult = result;
      this.capturedOutputDir = outputDir;
      this.capturedConfig = config;
      this.generateCalls++;
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      return returnValue;
    }
  }

  private static final class CustomFormatDocumentGenerator extends RecordingDocumentGenerator {
    private CustomFormatDocumentGenerator() {
      super(1, null);
    }

    @Override
    public String getFormat() {
      return "markdown";
    }

    @Override
    public String getFileExtension() {
      return ".md";
    }
  }
}
