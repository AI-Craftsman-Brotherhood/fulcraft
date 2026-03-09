package com.craftsmanbro.fulcraft.kernel.workflow;

/** Thrown when workflow definition/configuration is invalid. */
public class WorkflowConfigurationException extends IllegalArgumentException {

  public WorkflowConfigurationException(final String message) {
    super(message);
  }

  public WorkflowConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
