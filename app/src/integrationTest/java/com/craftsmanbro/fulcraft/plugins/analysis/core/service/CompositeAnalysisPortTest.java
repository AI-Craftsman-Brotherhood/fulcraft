package com.craftsmanbro.fulcraft.plugins.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositeAnalysisPortTest {

  @TempDir Path tempDir;

  @Test
  void constructor_rejectsNullAnalyzers() {
    assertThatThrownBy(() -> new CompositeAnalysisPort(null, new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            MessageSource.getMessage("analysis.composite_port.error.at_least_one_analyzer"));
  }

  @Test
  void constructor_rejectsEmptyAnalyzers() {
    assertThatThrownBy(() -> new CompositeAnalysisPort(List.of(), new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            MessageSource.getMessage("analysis.composite_port.error.at_least_one_analyzer"));
  }

  @Test
  void constructor_rejectsNullAnalyzerEntry() {
    List<AnalysisPort> analyzers = new ArrayList<>();
    analyzers.add(null);

    assertThatThrownBy(() -> new CompositeAnalysisPort(analyzers, new ResultMerger()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(MessageSource.getMessage("analysis.composite_port.error.null_elements"));
  }

  @Test
  void constructor_rejectsNullMerger() {
    List<AnalysisPort> analyzers = List.of(new StubAnalyzer(true, new AnalysisResult()));

    assertThatThrownBy(() -> new CompositeAnalysisPort(analyzers, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("merger must not be null");
  }

  @Test
  void getEngineName_returnsComposite() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult())), new ResultMerger());

    assertThat(port.getEngineName()).isEqualTo("composite");
  }

  @Test
  void supports_returnsFalseWhenProjectRootIsNull() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult())), new ResultMerger());

    assertThat(port.supports(null)).isFalse();
  }

  @Test
  void supports_returnsFalseWhenProjectRootIsNotDirectory() throws IOException {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult())), new ResultMerger());
    Path file = Files.createFile(tempDir.resolve("file.txt"));

    assertThat(port.supports(file)).isFalse();
  }

  @Test
  void supports_returnsTrueWhenAnyAnalyzerSupports() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(
                new StubAnalyzer(false, new AnalysisResult()),
                new StubAnalyzer(true, new AnalysisResult())),
            new ResultMerger());

    assertThat(port.supports(tempDir)).isTrue();
  }

  @Test
  void supports_returnsFalseWhenNoAnalyzerSupports() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(
                new StubAnalyzer(false, new AnalysisResult()),
                new StubAnalyzer(false, new AnalysisResult())),
            new ResultMerger());

    assertThat(port.supports(tempDir)).isFalse();
  }

  @Test
  void analyze_throwsWhenProjectRootIsNull() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult())), new ResultMerger());

    assertThatNullPointerException()
        .isThrownBy(() -> port.analyze(null, new Config()))
        .withMessage("projectRoot must not be null");
  }

  @Test
  void analyze_throwsWhenConfigIsNull() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult())), new ResultMerger());

    assertThatNullPointerException()
        .isThrownBy(() -> port.analyze(tempDir, null))
        .withMessage("config must not be null");
  }

  @Test
  void analyze_throwsWhenAnalyzerReturnsNull() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(List.of(new NullResultAnalyzer()), new ResultMerger());

    assertThatThrownBy(() -> port.analyze(tempDir, new Config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            MessageSource.getMessage(
                "analysis.composite_port.error.analyzer_returned_null", "NullResultAnalyzer"));
  }

  @Test
  void analyze_throwsWhenLaterAnalyzerReturnsNull() {
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, new AnalysisResult()), new NullResultAnalyzer()),
            new ResultMerger());

    assertThatThrownBy(() -> port.analyze(tempDir, new Config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            MessageSource.getMessage(
                "analysis.composite_port.error.analyzer_returned_null", "NullResultAnalyzer"));
  }

  @Test
  void analyze_returnsFirstResultWhenSingleAnalyzer() throws IOException {
    AnalysisResult expected = new AnalysisResult();
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(new StubAnalyzer(true, expected)), new FailOnMergeMerger());

    AnalysisResult actual = port.analyze(tempDir, new Config());

    assertThat(actual).isSameAs(expected);
  }

  @Test
  void analyze_propagatesIOExceptionFromFirstAnalyzer() {
    IOException expected = new IOException("first analyzer failed");
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(
            List.of(
                new ThrowingAnalyzer(true, expected), new StubAnalyzer(true, new AnalysisResult())),
            new ResultMerger());

    assertThatThrownBy(() -> port.analyze(tempDir, new Config()))
        .isInstanceOf(IOException.class)
        .hasMessage("first analyzer failed");
  }

  @Test
  void analyze_propagatesIOExceptionFromLaterAnalyzer() {
    IOException expected = new IOException("later analyzer failed");
    StubAnalyzer first = new StubAnalyzer(true, new AnalysisResult());
    ThrowingAnalyzer second = new ThrowingAnalyzer(true, expected);
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(List.of(first, second), new ResultMerger());

    assertThatThrownBy(() -> port.analyze(tempDir, new Config()))
        .isInstanceOf(IOException.class)
        .hasMessage("later analyzer failed");
    assertThat(first.analyzeCalls).isEqualTo(1);
    assertThat(second.analyzeCalls).isEqualTo(1);
  }

  @Test
  void constructor_makesDefensiveCopyOfAnalyzers() throws IOException {
    AnalysisResult expected = new AnalysisResult();
    StubAnalyzer analyzer = new StubAnalyzer(true, expected);
    List<AnalysisPort> analyzers = new ArrayList<>();
    analyzers.add(analyzer);
    CompositeAnalysisPort port = new CompositeAnalysisPort(analyzers, new ResultMerger());

    analyzers.clear();

    assertThat(port.analyze(tempDir, new Config())).isSameAs(expected);
  }

  @Test
  void analyze_mergesResultsInOrder() throws IOException {
    AnalysisResult resultA = new AnalysisResult();
    AnalysisResult resultB = new AnalysisResult();
    AnalysisResult resultC = new AnalysisResult();
    AnalysisResult mergedFirst = new AnalysisResult();
    AnalysisResult mergedFinal = new AnalysisResult();
    StubAnalyzer analyzerA = new StubAnalyzer(true, resultA);
    StubAnalyzer analyzerB = new StubAnalyzer(true, resultB);
    StubAnalyzer analyzerC = new StubAnalyzer(true, resultC);
    RecordingMerger merger = new RecordingMerger(List.of(mergedFirst, mergedFinal));
    CompositeAnalysisPort port =
        new CompositeAnalysisPort(List.of(analyzerA, analyzerB, analyzerC), merger);

    AnalysisResult returned = port.analyze(tempDir, new Config());

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

    private final boolean supported;
    private final AnalysisResult result;
    private int analyzeCalls;

    private StubAnalyzer(boolean supported, AnalysisResult result) {
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
      return "stub";
    }

    @Override
    public boolean supports(Path projectRoot) {
      return supported;
    }
  }

  private static final class NullResultAnalyzer implements AnalysisPort {

    @Override
    public AnalysisResult analyze(Path projectRoot, Config config) {
      return null;
    }

    @Override
    public String getEngineName() {
      return "null";
    }

    @Override
    public boolean supports(Path projectRoot) {
      return true;
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

  private static final class FailOnMergeMerger extends ResultMerger {

    @Override
    public AnalysisResult merge(AnalysisResult primary, AnalysisResult secondary) {
      throw new AssertionError("merge should not be called");
    }
  }

  private static final class ThrowingAnalyzer implements AnalysisPort {

    private final boolean supported;
    private final IOException exceptionToThrow;
    private int analyzeCalls;

    private ThrowingAnalyzer(boolean supported, IOException exceptionToThrow) {
      this.supported = supported;
      this.exceptionToThrow = exceptionToThrow;
    }

    @Override
    public AnalysisResult analyze(Path projectRoot, Config config) throws IOException {
      analyzeCalls++;
      throw exceptionToThrow;
    }

    @Override
    public String getEngineName() {
      return "throwing";
    }

    @Override
    public boolean supports(Path projectRoot) {
      return supported;
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
