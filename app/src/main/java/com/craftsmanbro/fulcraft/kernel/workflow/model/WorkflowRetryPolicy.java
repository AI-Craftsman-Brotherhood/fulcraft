package com.craftsmanbro.fulcraft.kernel.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Retry policy for a workflow node. */
public class WorkflowRetryPolicy {

  @JsonProperty("max")
  private Integer max;

  @JsonProperty("backoff_ms")
  private Integer backoffMs;

  public Integer getMax() {
    return max != null && max >= 0 ? max : 0;
  }

  public void setMax(final Integer max) {
    this.max = max;
  }

  public Integer getBackoffMs() {
    return backoffMs != null && backoffMs >= 0 ? backoffMs : 0;
  }

  public void setBackoffMs(final Integer backoffMs) {
    this.backoffMs = backoffMs;
  }

  public static WorkflowRetryPolicy none() {
    return new WorkflowRetryPolicy();
  }
}
