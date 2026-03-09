package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessingService;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePreprocessingFlowTest {

  @TempDir Path tempDir;

  @Test
  void preprocess_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    Config config = new Config();
    Path outputDir = tempDir.resolve("analysis");
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SKIPPED, List.of(), List.of(), null, null, 0);

    when(service.preprocess(tempDir, config, outputDir)).thenReturn(result);

    SourcePreprocessor.Result actual = flow.preprocess(tempDir, config, outputDir);

    assertThat(actual).isSameAs(result);
    verify(service).preprocess(tempDir, config, outputDir);
  }

  @Test
  void isStrictModeFailure_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED, List.of(), List.of(), null, "fail", 1);

    when(service.isStrictModeFailure(result)).thenReturn(true);

    assertThat(flow.isStrictModeFailure(result)).isTrue();
    verify(service).isStrictModeFailure(result);
  }

  @Test
  void getFailureReason_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED, List.of(), List.of(), null, "fail", 1);

    when(service.getFailureReason(result)).thenReturn("fail");

    assertThat(flow.getFailureReason(result)).isEqualTo("fail");
    verify(service).getFailureReason(result);
  }

  @Test
  void buildProjectSymbolIndex_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    Config config = new Config();
    SourcePreprocessor.Result result =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SKIPPED, List.of(), List.of(), null, null, 0);
    ProjectSymbolIndex index = mock(ProjectSymbolIndex.class);

    when(service.buildProjectSymbolIndex(tempDir, config, result)).thenReturn(index);

    ProjectSymbolIndex actual = flow.buildProjectSymbolIndex(tempDir, config, result);

    assertThat(actual).isSameAs(index);
    verify(service).buildProjectSymbolIndex(tempDir, config, result);
  }

  @Test
  void loadExternalConfigValues_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    Config config = new Config();
    Map<String, String> values = Map.of("key", "value");

    when(service.loadExternalConfigValues(tempDir, config)).thenReturn(values);

    Map<String, String> actual = flow.loadExternalConfigValues(tempDir, config);

    assertThat(actual).isSameAs(values);
    verify(service).loadExternalConfigValues(tempDir, config);
  }

  @Test
  void resolveSourceDirectories_delegatesToService() {
    SourcePreprocessingService service = mock(SourcePreprocessingService.class);
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow(service);
    Config config = new Config();
    SourcePathResolver.SourceDirectories directories =
        new SourcePathResolver.SourceDirectories(
            Optional.of(tempDir.resolve("src/main/java")), Optional.empty());

    when(service.resolveSourceDirectories(tempDir, config)).thenReturn(directories);

    SourcePathResolver.SourceDirectories actual = flow.resolveSourceDirectories(tempDir, config);

    assertThat(actual).isSameAs(directories);
    verify(service).resolveSourceDirectories(tempDir, config);
  }

  @Test
  void constructor_rejectsNullService() {
    assertThatThrownBy(() -> new SourcePreprocessingFlow(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void preprocess_rejectsNullProjectRoot_whenUsingDefaultConstructor() {
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow();
    Config config = new Config();

    assertThatNullPointerException()
        .isThrownBy(() -> flow.preprocess(null, config, tempDir.resolve("analysis")))
        .withMessageContaining("projectRoot");
  }

  @Test
  void loadExternalConfigValues_returnsEmptyMap_whenConfigIsNull() {
    SourcePreprocessingFlow flow = new SourcePreprocessingFlow();

    Map<String, String> values = flow.loadExternalConfigValues(tempDir, null);

    assertThat(values).isEmpty();
  }
}
