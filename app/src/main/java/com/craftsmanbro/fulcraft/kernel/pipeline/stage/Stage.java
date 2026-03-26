package com.craftsmanbro.fulcraft.kernel.pipeline.stage;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.util.Objects;

/**
 * Interface for pipeline stages.
 *
 * <p>Each stage represents a distinct phase in the test generation pipeline. Stages are executed in
 * order by the Pipeline, with each stage receiving the RunContext containing results from previous
 * stages.
 */
public interface Stage {

  String ERROR_NODE_ID_NULL = MessageSource.getMessage("kernel.stage.error.node_id_null");

  String ERROR_CONTEXT_NULL = MessageSource.getMessage("kernel.stage.error.context_null");

  /**
   * Get the workflow node id that this stage implements.
   *
   * @return the node id for this stage (must not be null)
   */
  String getNodeId();

  /**
   * Execute this stage.
   *
   * <p>Implementations should:
   *
   * <ul>
   *   <li>Read any required input from the context
   *   <li>Perform the stage's work
   *   <li>Store results back in the context
   *   <li>Add any errors or warnings to the context
   * </ul>
   *
   * @param context the run context containing configuration and intermediate results
   * @throws StageException if the stage fails in a way that should stop the pipeline
   */
  void execute(RunContext context) throws StageException;

  /**
   * Get a human-readable name for this stage.
   *
   * <p>Default implementation uses the workflow node id as the stage name.
   *
   * @return the stage name
   */
  default String getName() {
    return Objects.requireNonNull(getNodeId(), ERROR_NODE_ID_NULL);
  }

  /**
   * Check if this stage should be skipped for the given context.
   *
   * <p>Default implementation never skips the stage.
   *
   * @param context the run context
   * @return true if this stage should be skipped
   */
  default boolean shouldSkip(final RunContext context) {
    Objects.requireNonNull(context, ERROR_CONTEXT_NULL);
    return false;
  }
}
