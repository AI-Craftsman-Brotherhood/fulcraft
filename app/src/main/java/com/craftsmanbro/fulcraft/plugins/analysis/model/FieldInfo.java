package com.craftsmanbro.fulcraft.plugins.analysis.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldInfo {

  @JsonProperty("name")
  private String name;

  @JsonProperty("type")
  private String type;

  @JsonProperty("visibility")
  private String visibility;

  @JsonProperty("is_static")
  @JsonAlias("static")
  private boolean isStatic;

  @JsonProperty("is_final")
  @JsonAlias("final")
  private boolean isFinal;

  /** Indicates if this field appears to be injected via constructor (DI pattern). */
  @JsonProperty("is_injectable")
  @JsonAlias("injectable")
  private boolean isInjectable;

  /**
   * Mock hint for test generation: "required" (type is interface/abstract), "recommended"
   * (heuristic match), or null (no mock needed).
   */
  @JsonProperty("mock_hint")
  private String mockHint;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(final String visibility) {
    this.visibility = visibility;
  }

  @JsonProperty("is_static")
  public boolean isStatic() {
    return isStatic;
  }

  @JsonProperty("is_static")
  public void setStatic(final boolean isStatic) {
    this.isStatic = isStatic;
  }

  @JsonProperty("is_final")
  public boolean isFinal() {
    return isFinal;
  }

  @JsonProperty("is_final")
  public void setFinal(final boolean isFinal) {
    this.isFinal = isFinal;
  }

  @JsonProperty("is_injectable")
  public boolean isInjectable() {
    return isInjectable;
  }

  @JsonProperty("is_injectable")
  public void setInjectable(final boolean isInjectable) {
    this.isInjectable = isInjectable;
  }

  public String getMockHint() {
    return mockHint;
  }

  public void setMockHint(final String mockHint) {
    this.mockHint = mockHint;
  }
}
