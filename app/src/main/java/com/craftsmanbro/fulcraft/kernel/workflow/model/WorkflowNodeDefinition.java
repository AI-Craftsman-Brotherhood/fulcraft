package com.craftsmanbro.fulcraft.kernel.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definition of a single workflow node as loaded from JSON.
 *
 * <p>Collection getters return immutable empty values when the source omits them. Runtime-facing
 * accessors such as {@link #getOnFailurePolicy()} and {@link #getRetry()} normalize optional
 * values, while raw configured values remain available through their corresponding string/nullable
 * getters.
 */
public class WorkflowNodeDefinition {

  @JsonProperty("id")
  private String id;

  @JsonProperty("plugin")
  private String pluginId;

  @JsonProperty("depends_on")
  private List<String> dependsOn;

  @JsonProperty("with")
  private Map<String, Object> with;

  @JsonProperty("on_failure")
  private String onFailure;

  @JsonProperty("retry")
  private WorkflowRetryPolicy retry;

  @JsonProperty("timeout_sec")
  private Integer timeoutSec;

  @JsonProperty("enabled")
  private Boolean enabled = true;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(final String pluginId) {
    this.pluginId = pluginId;
  }

  public List<String> getDependsOn() {
    return dependsOn == null ? List.of() : List.copyOf(dependsOn);
  }

  public void setDependsOn(final List<String> dependsOn) {
    this.dependsOn = dependsOn;
  }

  public Map<String, Object> getWith() {
    if (with == null || with.isEmpty()) {
      return Map.of();
    }
    return Map.copyOf(with);
  }

  public void setWith(final Map<String, Object> with) {
    if (with == null || with.isEmpty()) {
      this.with = null;
      return;
    }
    this.with = new LinkedHashMap<>(with);
  }

  /** Returns the normalized failure policy, defaulting to {@link WorkflowFailurePolicy#STOP}. */
  public WorkflowFailurePolicy getOnFailurePolicy() {
    return WorkflowFailurePolicy.fromString(onFailure);
  }

  /** Returns the raw configured failure policy text as loaded from JSON. */
  public String getOnFailure() {
    return onFailure;
  }

  public void setOnFailure(final String onFailure) {
    this.onFailure = onFailure;
  }

  /** Returns the configured retry policy, defaulting to {@link WorkflowRetryPolicy#none()}. */
  public WorkflowRetryPolicy getRetry() {
    return retry != null ? retry : WorkflowRetryPolicy.none();
  }

  public void setRetry(final WorkflowRetryPolicy retry) {
    this.retry = retry;
  }

  /** Returns the raw timeout value from JSON, or {@code null} when it is not configured. */
  public Integer getTimeoutSec() {
    return timeoutSec;
  }

  public void setTimeoutSec(final Integer timeoutSec) {
    this.timeoutSec = timeoutSec;
  }

  public boolean isEnabled() {
    return enabled == null || enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }
}
