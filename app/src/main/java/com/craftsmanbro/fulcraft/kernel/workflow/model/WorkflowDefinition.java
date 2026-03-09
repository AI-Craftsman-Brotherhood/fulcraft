package com.craftsmanbro.fulcraft.kernel.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** Root definition for workflow-driven execution. */
public class WorkflowDefinition {

  @JsonProperty("version")
  private Integer version = 1;

  @JsonProperty("nodes")
  private List<WorkflowNodeDefinition> nodes = new ArrayList<>();

  public Integer getVersion() {
    return version != null ? version : 1;
  }

  public void setVersion(final Integer version) {
    this.version = version;
  }

  public List<WorkflowNodeDefinition> getNodes() {
    return nodes == null ? List.of() : List.copyOf(nodes);
  }

  public void setNodes(final List<WorkflowNodeDefinition> nodes) {
    if (nodes == null) {
      this.nodes = new ArrayList<>();
      return;
    }
    this.nodes = new ArrayList<>(nodes);
  }
}
