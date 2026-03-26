package com.craftsmanbro.fulcraft.plugins.analysis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepresentativePath {

  @JsonProperty("id")
  private String id;

  @JsonProperty("description")
  private String description;

  @JsonProperty("required_conditions")
  private List<String> requiredConditions = new ArrayList<>();

  @JsonProperty("expected_outcome_hint")
  private String expectedOutcomeHint;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public List<String> getRequiredConditions() {
    this.requiredConditions = normalizeRequiredConditions(this.requiredConditions);
    return Collections.unmodifiableList(this.requiredConditions);
  }

  public void setRequiredConditions(final List<String> requiredConditions) {
    this.requiredConditions = normalizeRequiredConditions(requiredConditions);
  }

  public String getExpectedOutcomeHint() {
    return expectedOutcomeHint;
  }

  public void setExpectedOutcomeHint(final String expectedOutcomeHint) {
    this.expectedOutcomeHint = expectedOutcomeHint;
  }

  private static List<String> normalizeRequiredConditions(final List<String> conditions) {
    if (conditions == null) {
      return new ArrayList<>();
    }
    return conditions;
  }
}
