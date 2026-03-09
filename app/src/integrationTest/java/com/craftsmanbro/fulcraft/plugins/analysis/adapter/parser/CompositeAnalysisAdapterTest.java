package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositeAnalysisAdapterTest {

  @TempDir Path tempDir;

  @Test
  void constructor_rejectsNullAnalyzers() {
    assertThatThrownBy(() -> new CompositeAnalysisAdapter(null, new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            MessageSource.getMessage("analysis.composite_adapter.error.at_least_one_analyzer"));
  }

  @Test
  void constructor_rejectsEmptyAnalyzers() {
    assertThatThrownBy(() -> new CompositeAnalysisAdapter(List.of(), new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            MessageSource.getMessage("analysis.composite_adapter.error.at_least_one_analyzer"));
  }

  @Test
  void constructor_rejectsNullAnalyzerEntry() {
    List<AnalysisPort> analyzers = new ArrayList<>();
    analyzers.add(null);

    assertThatThrownBy(() -> new CompositeAnalysisAdapter(analyzers, new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(MessageSource.getMessage("analysis.composite_adapter.error.null_entries"));
  }

  @Test
  void constructor_rejectsNullMerger() {
    List<AnalysisPort> analyzers = List.of(new StubAnalyzer("a", true, new AnalysisResult()));

    assertThatThrownBy(() -> new CompositeAnalysisAdapter(analyzers, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("merger must not be null");
  }

  @Test
  void supports_returnsTrueWhenAnyAnalyzerSupports() {
    CompositeAnalysisAdapter adapter =
        new CompositeAnalysisAdapter(
            List.of(
                new StubAnalyzer("a", false, new AnalysisResult()),
                new StubAnalyzer("b", true, new AnalysisResult())),
            new ResultMerger());

    assertThat(adapter.supports(tempDir)).isTrue();
  }

  @Test
  void supports_returnsFalseWhenNoAnalyzerSupports() {
    CompositeAnalysisAdapter adapter =
        new CompositeAnalysisAdapter(
            List.of(
                new StubAnalyzer("a", false, new AnalysisResult()),
                new StubAnalyzer("b", false, new AnalysisResult())),
            new ResultMerger());

    assertThat(adapter.supports(tempDir)).isFalse();
  }

  @Test
  void analyze_throwsWhenNoSupportedAnalyzers() {
    CompositeAnalysisAdapter adapter =
        new CompositeAnalysisAdapter(
            List.of(
                new StubAnalyzer("a", false, new AnalysisResult()),
                new StubAnalyzer("b", false, new AnalysisResult())),
            new ResultMerger());

    assertThatThrownBy(() -> adapter.analyze(tempDir, new Config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            MessageSource.getMessage(
                "analysis.composite_adapter.error.no_compatible_analyzers", tempDir));
  }

  @Test
  void analyze_mergesSingleSupportedAnalyzerThroughMerger() throws IOException {
    AnalysisResult result = new AnalysisResult();
    AnalysisResult merged = new AnalysisResult();
    StubAnalyzer supported = new StubAnalyzer("supported", true, result);
    StubAnalyzer unsupported = new StubAnalyzer("unsupported", false, new AnalysisResult());
    RecordingMerger merger = new RecordingMerger(List.of(merged));
    CompositeAnalysisAdapter adapter =
        new CompositeAnalysisAdapter(List.of(supported, unsupported), merger);

    AnalysisResult returned = adapter.analyze(tempDir, new Config());

    assertThat(returned).isSameAs(merged);
    assertThat(supported.analyzeCalls).isEqualTo(1);
    assertThat(unsupported.analyzeCalls).isZero();
    assertThat(merger.calls).hasSize(1);
    MergeCall call = merger.calls.get(0);
    assertThat(call.primary).isSameAs(result);
    assertThat(call.secondary).isNull();
  }

  @Test
  void analyze_mergesMultipleAnalyzersInOrder() throws IOException {
    AnalysisResult resultA = new AnalysisResult();
    AnalysisResult resultB = new AnalysisResult();
    AnalysisResult resultC = new AnalysisResult();
    AnalysisResult mergedFirst = new AnalysisResult();
    AnalysisResult mergedFinal = new AnalysisResult();
    StubAnalyzer analyzerA = new StubAnalyzer("a", true, resultA);
    StubAnalyzer analyzerB = new StubAnalyzer("b", true, resultB);
    StubAnalyzer analyzerC = new StubAnalyzer("c", true, resultC);
    RecordingMerger merger = new RecordingMerger(List.of(mergedFirst, mergedFinal));
    CompositeAnalysisAdapter adapter =
        new CompositeAnalysisAdapter(List.of(analyzerA, analyzerB, analyzerC), merger);

    AnalysisResult returned = adapter.analyze(tempDir, new Config());

    assertThat(returned).isSameAs(mergedFinal);
    assertThat(analyzerA.analyzeCalls).isEqualTo(1);
    assertThat(analyzerB.analyzeCalls).isEqualTo(1);
    assertThat(analyzerC.analyzeCalls).isEqualTo(1);
    assertThat(merger.calls).hasSize(2);
    assertThat(merger.calls.get(0).primary).isSameAs(resultA);
    assertThat(merger.calls.get(0).secondary).isSameAs(resultB);
    assertThat(merger.calls.get(1).primary).isSameAs(mergedFirst);
    assertThat(merger.calls.get(1).secondary).isSameAs(resultC);
  }

  private static final class StubAnalyzer implements AnalysisPort {

    private final String name;
    private final boolean supported;
    private final AnalysisResult result;
    private int analyzeCalls;

    private StubAnalyzer(String name, boolean supported, AnalysisResult result) {
      this.name = name;
      this.supported = supported;
      this.result = result;
    }

    @Override
    public AnalysisResult analyze(Path projectRoot, Config config) {
      analyzeCalls++;
      return result;
    }

    @Override
    public String getEngineName() {
      return name;
    }

    @Override
    public boolean supports(Path projectRoot) {
      return supported;
    }
  }

  private static final class RecordingMerger extends ResultMerger {

    private final List<MergeCall> calls = new ArrayList<>();
    private final List<AnalysisResult> resultsToReturn;
    private int index;

    private RecordingMerger(List<AnalysisResult> resultsToReturn) {
      this.resultsToReturn = resultsToReturn;
    }

    @Override
    public AnalysisResult merge(AnalysisResult primary, AnalysisResult secondary) {
      calls.add(new MergeCall(primary, secondary));
      if (index < resultsToReturn.size()) {
        return resultsToReturn.get(index++);
      }
      return primary;
    }
  }

  private static final class MergeCall {

    private final AnalysisResult primary;
    private final AnalysisResult secondary;

    private MergeCall(AnalysisResult primary, AnalysisResult secondary) {
      this.primary = primary;
      this.secondary = secondary;
    }
  }
}
