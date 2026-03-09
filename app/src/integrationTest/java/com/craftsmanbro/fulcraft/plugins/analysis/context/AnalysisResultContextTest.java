package com.craftsmanbro.fulcraft.plugins.analysis.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisResultContextTest {

  @TempDir Path tempDir;

  @Test
  void shouldReturnEmptyWhenUnset() {
    RunContext context = new RunContext(tempDir, new Config(), "run-1");

    assertThat(AnalysisResultContext.get(context)).isEmpty();
  }

  @Test
  void shouldStoreAndRetrieveResult() {
    RunContext context = new RunContext(tempDir, new Config(), "run-2");
    AnalysisResult result = new AnalysisResult("project-1");

    AnalysisResultContext.set(context, result);

    assertThat(AnalysisResultContext.get(context)).containsSame(result);
  }

  @Test
  void shouldClearResult() {
    RunContext context = new RunContext(tempDir, new Config(), "run-3");
    AnalysisResult result = new AnalysisResult("project-2");

    AnalysisResultContext.set(context, result);
    AnalysisResultContext.clear(context);

    assertThat(AnalysisResultContext.get(context)).isEmpty();
  }

  @Test
  void shouldReplaceStoredResultWhenSetTwice() {
    RunContext context = new RunContext(tempDir, new Config(), "run-4");
    AnalysisResult first = new AnalysisResult("project-first");
    AnalysisResult second = new AnalysisResult("project-second");

    AnalysisResultContext.set(context, first);
    AnalysisResultContext.set(context, second);

    assertThat(AnalysisResultContext.get(context)).containsSame(second);
  }

  @Test
  void shouldClearOnlyAnalysisResultMetadata() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5");
    AnalysisResult result = new AnalysisResult("project-3");
    context.putMetadata("custom.key", "custom-value");

    AnalysisResultContext.set(context, result);
    AnalysisResultContext.clear(context);

    assertThat(context.getMetadata("custom.key", String.class)).contains("custom-value");
    assertThat(AnalysisResultContext.get(context)).isEmpty();
  }

  @Test
  void shouldReturnEmptyAndAddWarningWhenMetadataTypeIsUnexpected() {
    RunContext context = new RunContext(tempDir, new Config(), "run-6");
    context.putMetadata(AnalysisResultContext.METADATA_KEY, "unexpected-string");

    assertThat(AnalysisResultContext.get(context)).isEmpty();
    assertThat(context.getWarnings())
        .singleElement()
        .satisfies(
            warning ->
                assertThat(String.valueOf(warning))
                    .contains("analysis.result")
                    .contains(AnalysisResult.class.getName())
                    .contains(String.class.getName()));
  }

  @Test
  void shouldRejectNullContextOrResult() {
    RunContext context = new RunContext(tempDir, new Config(), "run-7");

    assertThatThrownBy(() -> AnalysisResultContext.get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context");
    assertThatThrownBy(() -> AnalysisResultContext.clear(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context");
    assertThatThrownBy(() -> AnalysisResultContext.set(null, new AnalysisResult("project-4")))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context");
    assertThatThrownBy(() -> AnalysisResultContext.set(context, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("result");
  }
}
