package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BranchSummary {

  @JsonProperty("guards")
  private List<GuardSummary> guards = new ArrayList<>();

  @JsonProperty("switches")
  private List<String> switches = new ArrayList<>();

  @JsonProperty("predicates")
  private List<String> predicates = new ArrayList<>();

  public List<GuardSummary> getGuards() {
    if (guards == null) {
      guards = new ArrayList<>();
    }
    return Collections.unmodifiableList(guards);
  }

  public void setGuards(final List<GuardSummary> guards) {
    this.guards = guards != null ? new ArrayList<>(guards) : new ArrayList<>();
  }

  public List<String> getSwitches() {
    if (switches == null) {
      switches = new ArrayList<>();
    }
    return Collections.unmodifiableList(switches);
  }

  public void setSwitches(final List<String> switches) {
    this.switches = switches != null ? new ArrayList<>(switches) : new ArrayList<>();
  }

  public List<String> getPredicates() {
    if (predicates == null) {
      predicates = new ArrayList<>();
    }
    return Collections.unmodifiableList(predicates);
  }

  public void setPredicates(final List<String> predicates) {
    this.predicates = predicates != null ? new ArrayList<>(predicates) : new ArrayList<>();
  }
}
