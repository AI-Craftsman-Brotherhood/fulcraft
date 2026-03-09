package com.craftsmanbro.fulcraft.kernel.pipeline.stage;

import java.util.Objects;

/**
 * Exception thrown when a pipeline stage fails.
 *
 * <p>This exception indicates that a stage encountered an error that prevents it from completing
 * successfully. The pipeline may choose to continue with other stages or abort depending on
 * configuration.
 */
public class StageException extends Exception {

  private static final long serialVersionUID = 1L;

  private static final String MESSAGE_PARAM = "message";

  private static final String NODE_ID_PARAM = "nodeId";

  private static final String CAUSE_PARAM = "cause";

  private final String nodeId;

  private final boolean recoverable;

  /**
   * Create a new stage exception.
   *
   * @param nodeId The node id that failed
   * @param message The error message
   */
  public StageException(final String nodeId, final String message) {
    this(nodeId, message, false);
  }

  /**
   * Create a new stage exception with a cause.
   *
   * @param nodeId The node id that failed
   * @param message The error message
   * @param cause The underlying cause (must not be null)
   */
  public StageException(final String nodeId, final String message, final Throwable cause) {
    this(nodeId, message, cause, false);
  }

  /**
   * Create a new stage exception with recoverability flag.
   *
   * @param nodeId The node id that failed
   * @param message The error message
   * @param cause The underlying cause (must not be null)
   * @param recoverable Whether the pipeline can continue after this failure
   */
  public StageException(
      final String nodeId, final String message, final Throwable cause, final boolean recoverable) {
    super(
        Objects.requireNonNull(message, MESSAGE_PARAM), Objects.requireNonNull(cause, CAUSE_PARAM));
    this.nodeId = Objects.requireNonNull(nodeId, NODE_ID_PARAM);
    this.recoverable = recoverable;
  }

  /**
   * Create a new stage exception with recoverability flag.
   *
   * @param nodeId The node id that failed
   * @param message The error message
   * @param recoverable Whether the pipeline can continue after this failure
   */
  public StageException(final String nodeId, final String message, final boolean recoverable) {
    super(Objects.requireNonNull(message, MESSAGE_PARAM));
    this.nodeId = Objects.requireNonNull(nodeId, NODE_ID_PARAM);
    this.recoverable = recoverable;
  }

  /**
   * Get the node id that failed.
   *
   * @return the node id that threw this exception
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * Check if the pipeline can recover from this failure.
   *
   * @return true if the pipeline can continue, false if it must abort
   */
  public boolean isRecoverable() {
    return recoverable;
  }
}
