package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunContextTest {

  @TempDir Path tempDir;

  @Test
  void shouldResolveRunDirectoryFromConfigWhenNotProvided() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("runs-root");
    config.setExecution(execution);

    RunContext context = new RunContext(tempDir, config, "run-1");

    Path expected = tempDir.resolve("runs-root").resolve("run-1").normalize();
    assertThat(context.getRunDirectory()).isEqualTo(expected);
  }

  @Test
  void shouldUseProvidedRunDirectory() {
    Config config = new Config();
    Path runDirectory = tempDir.resolve("custom-run");

    RunContext context = new RunContext(tempDir, config, "run-2", runDirectory);

    assertThat(context.getRunDirectory()).isEqualTo(runDirectory);
  }

  @Test
  void shouldResolveRunDirectoryWhenNullIsExplicitlyProvided() {
    Config config = new Config();
    Config.ExecutionConfig execution = new Config.ExecutionConfig();
    execution.setLogsRoot("runs-root");
    config.setExecution(execution);

    RunContext context = new RunContext(tempDir, config, "run-2b", null);

    Path expected = tempDir.resolve("runs-root").resolve("run-2b").normalize();
    assertThat(context.getRunDirectory()).isEqualTo(expected);
  }

  @Test
  void shouldRejectNullConstructorArguments() {
    Config config = new Config();

    assertThatThrownBy(() -> new RunContext(null, config, "run-null-1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("projectRoot");
    assertThatThrownBy(() -> new RunContext(tempDir, null, "run-null-2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("config");
    assertThatThrownBy(() -> new RunContext(tempDir, config, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("runId");
  }

  @Test
  void shouldSupportFluentOptions() {
    RunContext context = new RunContext(tempDir, new Config(), "run-3");

    RunContext returned = context.withDryRun(true).withFailFast(true).withShowSummary(true);

    assertThat(returned).isSameAs(context);
    assertThat(context.isDryRun()).isTrue();
    assertThat(context.isFailFast()).isTrue();
    assertThat(context.isShowSummary()).isTrue();
  }

  @Test
  void shouldExposeArtifactsAndDiagnostics() {
    RunContext context = new RunContext(tempDir, new Config(), "run-4");
    ReportTaskResult result = new ReportTaskResult();
    List<ReportTaskResult> results = new ArrayList<>();
    results.add(result);

    context.setBrittlenessDetected(true);
    context.setReportTaskResults(results);
    results.clear();
    context.addError("boom");
    context.addWarning("heads up");

    assertThat(context.isBrittlenessDetected()).isTrue();
    assertThat(context.getReportTaskResults()).containsExactly(result);
    assertThatThrownBy(() -> context.getReportTaskResults().add(new ReportTaskResult()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(context.getErrors()).containsExactly("boom");
    assertThat(context.getWarnings()).containsExactly("heads up");
    assertThat(context.hasErrors()).isTrue();
    assertThat(context.hasWarnings()).isTrue();
  }

  @Test
  void shouldWarnOnMetadataTypeMismatch() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5");

    context.putMetadata("answer", "42");

    Optional<Integer> value = context.getMetadata("answer", Integer.class);

    assertThat(value).isEmpty();
    assertThat(context.getWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("Metadata value for key 'answer' has unexpected type")
                    .contains(Integer.class.getName())
                    .contains(String.class.getName()));
  }

  @Test
  void shouldNotWarnWhenMetadataIsMissing() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5b");

    Optional<String> value = context.getMetadata("missing", String.class);

    assertThat(value).isEmpty();
    assertThat(context.getWarnings()).isEmpty();
  }

  @Test
  void shouldReturnTypedMetadataCollections() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5c");
    context.putMetadata("names", List.of("alice", "bob"));
    context.putMetadata("ids", Set.of("id-1", "id-2"));

    Optional<List<String>> names = context.getMetadataList("names", String.class);
    Optional<Set<String>> ids = context.getMetadataSet("ids", String.class);

    assertThat(names).contains(List.of("alice", "bob"));
    assertThat(ids).isPresent();
    assertThat(ids.orElseThrow()).containsExactlyInAnyOrder("id-1", "id-2");
  }

  @Test
  void shouldWarnOnMetadataCollectionTypeMismatch() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5d");
    context.putMetadata("names", Set.of("alice"));

    Optional<List<String>> names = context.getMetadataList("names", String.class);

    assertThat(names).isEmpty();
    assertThat(context.getWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("Metadata value for key 'names' has unexpected type")
                    .contains(List.class.getName()));
  }

  @Test
  void shouldWarnOnMetadataCollectionElementTypeMismatch() {
    RunContext context = new RunContext(tempDir, new Config(), "run-5e");
    context.putMetadata("numbers", List.of(1, 2));

    Optional<List<String>> numbers = context.getMetadataList("numbers", String.class);

    assertThat(numbers).isEmpty();
    assertThat(context.getWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("Metadata list value for key 'numbers'")
                    .contains("expected " + String.class.getName())
                    .contains(Integer.class.getName()));
  }

  @Test
  void shouldReturnImmutableMetadataSnapshot() {
    RunContext context = new RunContext(tempDir, new Config(), "run-6");
    context.putMetadata("key", "value");

    Map<String, Object> snapshot = context.getMetadata();

    assertThat(snapshot).containsEntry("key", "value");
    assertThatThrownBy(() -> snapshot.put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getMetadataSet_shouldPreserveInsertionOrderAndReturnImmutableSnapshot() {
    RunContext context =
        new RunContext(Path.of("/tmp/project"), new Config(), "run-1", Path.of("build/tmp/run-1"));
    LinkedHashSet<String> roots = new LinkedHashSet<>();
    roots.add("alpha");
    roots.add("beta");
    context.putMetadata("roots", roots);

    Set<String> result = context.getMetadataSet("roots", String.class).orElseThrow();

    assertThat(result).containsExactly("alpha", "beta");
    assertThatThrownBy(() -> result.add("gamma")).isInstanceOf(UnsupportedOperationException.class);
  }
}
