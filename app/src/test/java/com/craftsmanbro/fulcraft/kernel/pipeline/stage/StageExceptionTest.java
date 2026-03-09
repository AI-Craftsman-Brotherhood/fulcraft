package com.craftsmanbro.fulcraft.kernel.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import org.junit.jupiter.api.Test;

class StageExceptionTest {

  @Test
  void constructor_shouldCaptureStepAndMessage() {
    StageException exception = new StageException(PipelineNodeIds.ANALYZE, "boom");

    assertThat(exception.getNodeId()).isEqualTo(PipelineNodeIds.ANALYZE);
    assertThat(exception.getMessage()).isEqualTo("boom");
    assertThat(exception.getCause()).isNull();
    assertThat(exception.isRecoverable()).isFalse();
  }

  @Test
  void constructor_withCause_shouldCaptureCauseAndNonRecoverable() {
    RuntimeException cause = new RuntimeException("cause");

    StageException exception = new StageException(PipelineNodeIds.GENERATE, "failed", cause);

    assertThat(exception.getNodeId()).isEqualTo(PipelineNodeIds.GENERATE);
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.isRecoverable()).isFalse();
  }

  @Test
  void constructor_withCauseAndRecoverable_shouldExposeRecoverable() {
    IllegalStateException cause = new IllegalStateException("cause");

    StageException exception = new StageException(PipelineNodeIds.REPORT, "failed", cause, true);

    assertThat(exception.getNodeId()).isEqualTo(PipelineNodeIds.REPORT);
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.isRecoverable()).isTrue();
  }

  @Test
  void constructor_withRecoverable_shouldExposeRecoverable() {
    StageException exception = new StageException(PipelineNodeIds.REPORT, "failed", true);

    assertThat(exception.getNodeId()).isEqualTo(PipelineNodeIds.REPORT);
    assertThat(exception.getCause()).isNull();
    assertThat(exception.isRecoverable()).isTrue();
  }

  @Test
  void constructor_withCauseAndRecoverableFalse_shouldExposeNonRecoverable() {
    IllegalStateException cause = new IllegalStateException("cause");

    StageException exception = new StageException(PipelineNodeIds.DOCUMENT, "failed", cause, false);

    assertThat(exception.getNodeId()).isEqualTo(PipelineNodeIds.DOCUMENT);
    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.isRecoverable()).isFalse();
  }

  @Test
  void constructor_shouldRejectNullMessage() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, null);
            });

    assertThat(thrown).hasMessage("message");
  }

  @Test
  void constructor_shouldRejectNullStep() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(null, "boom");
            });

    assertThat(thrown).hasMessage("nodeId");
  }

  @Test
  void constructor_withCause_shouldRejectNullCause() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, "boom", (Throwable) null);
            });

    assertThat(thrown).hasMessage("cause");
  }

  @Test
  void constructor_withCause_shouldRejectNullMessage() {
    RuntimeException cause = new RuntimeException("cause");

    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, null, cause);
            });

    assertThat(thrown).hasMessage("message");
  }

  @Test
  void constructor_withCause_shouldRejectNullStep() {
    RuntimeException cause = new RuntimeException("cause");

    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(null, "boom", cause);
            });

    assertThat(thrown).hasMessage("nodeId");
  }

  @Test
  void constructor_withRecoverable_shouldRejectNullMessage() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, null, true);
            });

    assertThat(thrown).hasMessage("message");
  }

  @Test
  void constructor_withRecoverable_shouldRejectNullStep() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(null, "boom", true);
            });

    assertThat(thrown).hasMessage("nodeId");
  }

  @Test
  void constructor_withCauseAndRecoverable_shouldRejectNullMessage() {
    RuntimeException cause = new RuntimeException("cause");

    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, null, cause, true);
            });

    assertThat(thrown).hasMessage("message");
  }

  @Test
  void constructor_withCauseAndRecoverable_shouldRejectNullStep() {
    RuntimeException cause = new RuntimeException("cause");

    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(null, "boom", cause, true);
            });

    assertThat(thrown).hasMessage("nodeId");
  }

  @Test
  void constructor_withCauseAndRecoverable_shouldRejectNullCause() {
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new StageException(PipelineNodeIds.ANALYZE, "boom", null, true);
            });

    assertThat(thrown).hasMessage("cause");
  }
}
