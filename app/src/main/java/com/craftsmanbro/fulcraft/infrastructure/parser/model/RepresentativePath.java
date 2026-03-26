package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    if (requiredConditions == null) {
      requiredConditions = new ArrayList<>();
    }
    return Collections.unmodifiableList(requiredConditions);
  }

  public void setRequiredConditions(final List<String> requiredConditions) {
    this.requiredConditions = Objects.requireNonNullElseGet(requiredConditions, ArrayList::new);
  }

  public String getExpectedOutcomeHint() {
    return expectedOutcomeHint;
  }

  public void setExpectedOutcomeHint(final String expectedOutcomeHint) {
    this.expectedOutcomeHint = expectedOutcomeHint;
  }
}
