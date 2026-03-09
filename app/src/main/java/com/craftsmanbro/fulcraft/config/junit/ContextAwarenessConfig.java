package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ContextAwarenessConfig {
  @JsonProperty("enabled")
  private Boolean enabled = true;

  @JsonProperty("test_dirs")
  private List<String> testDirs = List.of("src/test/java");

  @JsonProperty("include_globs")
  private List<String> includeGlobs = List.of("**/*Test.java");

  @JsonProperty("exclude_globs")
  private List<String> excludeGlobs =
      List.of(
          "**/*IT.java",
          "**/*IntegrationTest.java",
          "**/integration/**",
          "**/e2e/**",
          "**/benchmark/**");

  @JsonProperty("max_files")
  private Integer maxFiles = 200;

  @JsonProperty("max_injected_chars")
  private Integer maxInjectedChars = 1200;

  @JsonProperty("exclude_generated_tests")
  private Boolean excludeGeneratedTests = true;

  @JsonProperty("generated_output_dir")
  private String generatedOutputDir;

  public Boolean getEnabled() {
    return enabled != null && enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getTestDirs() {
    return testDirs != null ? testDirs : List.of("src/test/java");
  }

  public void setTestDirs(final List<String> testDirs) {
    this.testDirs = testDirs;
  }

  public List<String> getIncludeGlobs() {
    return includeGlobs != null ? includeGlobs : List.of("**/*Test.java");
  }

  public void setIncludeGlobs(final List<String> includeGlobs) {
    this.includeGlobs = includeGlobs;
  }

  public List<String> getExcludeGlobs() {
    if (excludeGlobs == null) {
      return List.of(
          "**/*IT.java",
          "**/*IntegrationTest.java",
          "**/integration/**",
          "**/e2e/**",
          "**/benchmark/**");
    }
    return excludeGlobs;
  }

  public void setExcludeGlobs(final List<String> excludeGlobs) {
    this.excludeGlobs = excludeGlobs;
  }

  public Integer getMaxFiles() {
    return maxFiles != null ? maxFiles : 200;
  }

  public void setMaxFiles(final Integer maxFiles) {
    this.maxFiles = maxFiles;
  }

  public Integer getMaxInjectedChars() {
    return maxInjectedChars != null ? maxInjectedChars : 1200;
  }

  public void setMaxInjectedChars(final Integer maxInjectedChars) {
    this.maxInjectedChars = maxInjectedChars;
  }

  public Boolean getExcludeGeneratedTests() {
    return excludeGeneratedTests != null && excludeGeneratedTests;
  }

  public void setExcludeGeneratedTests(final Boolean excludeGeneratedTests) {
    this.excludeGeneratedTests = excludeGeneratedTests;
  }

  public String getGeneratedOutputDir() {
    return generatedOutputDir;
  }

  public void setGeneratedOutputDir(final String generatedOutputDir) {
    this.generatedOutputDir = generatedOutputDir;
  }
}
