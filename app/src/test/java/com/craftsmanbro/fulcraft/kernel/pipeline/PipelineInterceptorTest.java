package com.craftsmanbro.fulcraft.kernel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptorLoader;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Unit tests for {@link Pipeline} interceptor integration.
 *
 * <p>These tests verify that interceptors are executed correctly around stage execution.
 */
class PipelineInterceptorTest {

  private PhaseInterceptorLoader mockLoader;
  private Config config;
  private RunContext context;

  @BeforeEach
  void setUp() {
    mockLoader = mock(PhaseInterceptorLoader.class);
    config = new Config();

    // Setup default empty interceptor map
    Map<String, Map<Hook, List<PhaseInterceptor>>> emptyMap = createEmptyInterceptorMap();
    when(mockLoader.loadAll(any())).thenReturn(emptyMap);

    context = new RunContext(Path.of("/tmp/test-project"), config, "test-run-1");
  }

  private Map<String, Map<Hook, List<PhaseInterceptor>>> createEmptyInterceptorMap() {
    Map<String, Map<Hook, List<PhaseInterceptor>>> map = new HashMap<>();
    for (String nodeId : PipelineNodeIds.OFFICIAL_TOP_LEVEL) {
      Map<Hook, List<PhaseInterceptor>> hookMap = new HashMap<>();
      hookMap.put(Hook.PRE, new ArrayList<>());
      hookMap.put(Hook.POST, new ArrayList<>());
      map.put(nodeId, hookMap);
    }
    return map;
  }

  @Nested
  @DisplayName("Interceptor execution order")
  class InterceptorExecutionOrderTests {

    @Test
    @DisplayName("PRE interceptors should execute before stage, POST after stage")
    void preAndPostInterceptorsAroundStage() throws StageException {
      // Given
      List<String> executionLog = new ArrayList<>();

      PhaseInterceptor preInterceptor =
          createLoggingInterceptor("pre", PipelineNodeIds.ANALYZE, Hook.PRE, executionLog);
      PhaseInterceptor postInterceptor =
          createLoggingInterceptor("post", PipelineNodeIds.ANALYZE, Hook.POST, executionLog);

      Stage mockStage = mock(Stage.class);
      when(mockStage.getNodeId()).thenReturn(PipelineNodeIds.ANALYZE);
      when(mockStage.getName()).thenReturn("AnalyzeStage");
      when(mockStage.shouldSkip(any())).thenReturn(false);

      Map<String, Map<Hook, List<PhaseInterceptor>>> interceptorMap = createEmptyInterceptorMap();
      interceptorMap.get(PipelineNodeIds.ANALYZE).get(Hook.PRE).add(preInterceptor);
      interceptorMap.get(PipelineNodeIds.ANALYZE).get(Hook.POST).add(postInterceptor);
      when(mockLoader.loadAll(any())).thenReturn(interceptorMap);

      Pipeline pipelineWithInterceptors = new Pipeline(mockLoader, config);
      pipelineWithInterceptors.registerStage(mockStage);

      // When
      pipelineWithInterceptors.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

      // Then: PRE before stage, POST after stage
      InOrder inOrder = inOrder(preInterceptor, mockStage, postInterceptor);
      inOrder.verify(preInterceptor).apply(context);
      inOrder.verify(mockStage).execute(context);
      inOrder.verify(postInterceptor).apply(context);
    }

    @Test
    @DisplayName("multiple PRE interceptors should execute in order")
    void multiplePreInterceptorsInOrder() {
      // Given
      List<String> executionLog = new ArrayList<>();

      PhaseInterceptor first =
          createLoggingInterceptor("first", PipelineNodeIds.REPORT, Hook.PRE, executionLog);
      when(first.order()).thenReturn(10);

      PhaseInterceptor second =
          createLoggingInterceptor("second", PipelineNodeIds.REPORT, Hook.PRE, executionLog);
      when(second.order()).thenReturn(20);

      Stage mockStage = mock(Stage.class);
      when(mockStage.getNodeId()).thenReturn(PipelineNodeIds.REPORT);
      when(mockStage.getName()).thenReturn("ReportStage");
      when(mockStage.shouldSkip(any())).thenReturn(false);

      // Interceptors should be pre-sorted by loader
      Map<String, Map<Hook, List<PhaseInterceptor>>> interceptorMap = createEmptyInterceptorMap();
      interceptorMap.get(PipelineNodeIds.REPORT).get(Hook.PRE).add(first);
      interceptorMap.get(PipelineNodeIds.REPORT).get(Hook.PRE).add(second);
      when(mockLoader.loadAll(any())).thenReturn(interceptorMap);

      Pipeline pipelineWithInterceptors = new Pipeline(mockLoader, config);
      pipelineWithInterceptors.registerStage(mockStage);

      // When
      pipelineWithInterceptors.run(context, List.of(PipelineNodeIds.REPORT), null, null);

      // Then: Interceptors executed in order
      InOrder inOrder = inOrder(first, second);
      inOrder.verify(first).apply(context);
      inOrder.verify(second).apply(context);
    }
  }

  @Nested
  @DisplayName("Interceptor error handling")
  class InterceptorErrorHandlingTests {

    @Test
    @DisplayName("interceptor exception should not stop pipeline")
    void interceptorExceptionShouldNotStopPipeline() throws StageException {
      // Given
      PhaseInterceptor failingInterceptor = mock(PhaseInterceptor.class);
      when(failingInterceptor.id()).thenReturn("failing");
      doThrow(new RuntimeException("Interceptor failed")).when(failingInterceptor).apply(any());

      Stage mockStage = mock(Stage.class);
      when(mockStage.getNodeId()).thenReturn(PipelineNodeIds.ANALYZE);
      when(mockStage.getName()).thenReturn("AnalyzeStage");
      when(mockStage.shouldSkip(any())).thenReturn(false);

      Map<String, Map<Hook, List<PhaseInterceptor>>> interceptorMap = createEmptyInterceptorMap();
      interceptorMap.get(PipelineNodeIds.ANALYZE).get(Hook.PRE).add(failingInterceptor);
      when(mockLoader.loadAll(any())).thenReturn(interceptorMap);

      Pipeline pipelineWithInterceptors = new Pipeline(mockLoader, config);
      pipelineWithInterceptors.registerStage(mockStage);

      // When
      int exitCode =
          pipelineWithInterceptors.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

      // Then: Pipeline should continue and stage should still execute
      verify(mockStage).execute(context);
      assertThat(exitCode).isZero();
      assertThat(context.getWarnings()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Pipeline without interceptors")
  class PipelineWithoutInterceptorsTests {

    @Test
    @DisplayName("pipeline created with default constructor should work without interceptors")
    void defaultConstructorShouldWork() throws StageException {
      // Given
      Pipeline defaultPipeline = new Pipeline();

      Stage mockStage = mock(Stage.class);
      when(mockStage.getNodeId()).thenReturn(PipelineNodeIds.ANALYZE);
      when(mockStage.getName()).thenReturn("AnalyzeStage");
      when(mockStage.shouldSkip(any())).thenReturn(false);

      defaultPipeline.registerStage(mockStage);

      // When
      int exitCode = defaultPipeline.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

      // Then
      verify(mockStage).execute(context);
      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("skipped stage should not trigger interceptors")
    void skippedStageShouldNotTriggerInterceptors() throws StageException {
      // Given
      PhaseInterceptor preInterceptor = mock(PhaseInterceptor.class);
      when(preInterceptor.id()).thenReturn("pre");

      Stage mockStage = mock(Stage.class);
      when(mockStage.getNodeId()).thenReturn(PipelineNodeIds.ANALYZE);
      when(mockStage.getName()).thenReturn("AnalyzeStage");
      when(mockStage.shouldSkip(any())).thenReturn(true); // Skip the stage

      Map<String, Map<Hook, List<PhaseInterceptor>>> interceptorMap = createEmptyInterceptorMap();
      interceptorMap.get(PipelineNodeIds.ANALYZE).get(Hook.PRE).add(preInterceptor);
      when(mockLoader.loadAll(any())).thenReturn(interceptorMap);

      Pipeline pipelineWithInterceptors = new Pipeline(mockLoader, config);
      pipelineWithInterceptors.registerStage(mockStage);

      // When
      pipelineWithInterceptors.run(context, List.of(PipelineNodeIds.ANALYZE), null, null);

      // Then: Interceptor should not be called for skipped stage
      verify(preInterceptor, never()).apply(any());
      verify(mockStage, never()).execute(any());
    }
  }

  private PhaseInterceptor createLoggingInterceptor(
      String id, String phase, Hook hook, List<String> log) {
    PhaseInterceptor interceptor = mock(PhaseInterceptor.class);
    when(interceptor.id()).thenReturn(id);
    when(interceptor.phase()).thenReturn(phase);
    when(interceptor.hook()).thenReturn(hook);
    when(interceptor.order()).thenReturn(100);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              log.add(id);
              return null;
            })
        .when(interceptor)
        .apply(any());
    return interceptor;
  }
}
