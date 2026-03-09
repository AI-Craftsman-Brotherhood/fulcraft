package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MockingConfig {

  @JsonProperty("enable_static")
  private Boolean enableStatic = false;

  @JsonProperty("enable_external")
  private Boolean enableExternal = false;

  @JsonProperty("http_stub")
  private String httpStub = "none";

  @JsonProperty("db_stub")
  private String dbStub = "none";

  @JsonProperty("framework")
  private String framework = "mockito";

  @JsonProperty("count_static_mocks")
  private Boolean countStaticMocks = false;

  @JsonProperty("count_stubs")
  private Boolean countStubs = false;

  public boolean isEnableStatic() {
    return enableStatic != null && enableStatic;
  }

  public void setEnableStatic(final Boolean enableStatic) {
    this.enableStatic = enableStatic;
  }

  public boolean isEnableExternal() {
    return enableExternal != null && enableExternal;
  }

  public void setEnableExternal(final Boolean enableExternal) {
    this.enableExternal = enableExternal;
  }

  public String getHttpStub() {
    return httpStub != null ? httpStub : "none";
  }

  public void setHttpStub(final String httpStub) {
    this.httpStub = httpStub;
  }

  public String getDbStub() {
    return dbStub != null ? dbStub : "none";
  }

  public void setDbStub(final String dbStub) {
    this.dbStub = dbStub;
  }

  public String getFramework() {
    return framework != null ? framework : "mockito";
  }

  public void setFramework(final String framework) {
    this.framework = framework;
  }

  public boolean isCountStaticMocks() {
    return countStaticMocks != null && countStaticMocks;
  }

  public void setCountStaticMocks(final Boolean countStaticMocks) {
    this.countStaticMocks = countStaticMocks;
  }

  public boolean isCountStubs() {
    return countStubs != null && countStubs;
  }

  public void setCountStubs(final Boolean countStubs) {
    this.countStubs = countStubs;
  }
}
