package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuardSummary {

  @JsonProperty("type")
  private GuardType type;

  @JsonProperty("condition")
  private String condition;

  @JsonProperty("effects")
  private List<String> effects = new ArrayList<>();

  @JsonProperty("message_literal")
  private String messageLiteral;

  @JsonProperty("location")
  private String location;

  public GuardSummary() {}

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public GuardSummary(final String legacyCondition) {
    this.type = GuardType.LEGACY;
    this.condition = legacyCondition;
    this.effects = new ArrayList<>();
  }

  public GuardSummary(
      final GuardType type,
      final String condition,
      final List<String> effects,
      final String messageLiteral,
      final String location) {
    this.type = type;
    this.condition = condition;
    this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
    this.messageLiteral = messageLiteral;
    this.location = location;
  }

  public GuardType getType() {
    return type;
  }

  public void setType(final GuardType type) {
    this.type = type;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(final String condition) {
    this.condition = condition;
  }

  public List<String> getEffects() {
    if (effects == null) {
      effects = new ArrayList<>();
    }
    return Collections.unmodifiableList(effects);
  }

  public void setEffects(final List<String> effects) {
    this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
  }

  public String getMessageLiteral() {
    return messageLiteral;
  }

  public void setMessageLiteral(final String messageLiteral) {
    this.messageLiteral = messageLiteral;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(final String location) {
    this.location = location;
  }
}
