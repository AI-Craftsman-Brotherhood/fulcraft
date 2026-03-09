package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GenerationConfig {
  @JsonProperty("marker")
  private MarkerConfig marker;

  @JsonProperty("prompt_template_path")
  private String promptTemplatePath;

  @JsonProperty("few_shot")
  private FewShotConfig fewShot;

  @JsonProperty("temperature")
  private Double temperature;

  @JsonProperty("max_tokens")
  private Integer maxTokens;

  @JsonProperty("default_model")
  private String defaultModel;

  public MarkerConfig getMarker() {
    return marker != null ? marker : new MarkerConfig();
  }

  public void setMarker(final MarkerConfig marker) {
    this.marker = marker;
  }

  public String getPromptTemplatePath() {
    return promptTemplatePath;
  }

  public void setPromptTemplatePath(final String promptTemplatePath) {
    this.promptTemplatePath = promptTemplatePath;
  }

  public FewShotConfig getFewShot() {
    return fewShot != null ? fewShot : new FewShotConfig();
  }

  public void setFewShot(final FewShotConfig fewShot) {
    this.fewShot = fewShot;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(final Double temperature) {
    this.temperature = temperature;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(final Integer maxTokens) {
    this.maxTokens = maxTokens;
  }

  public String getDefaultModel() {
    return defaultModel;
  }

  public void setDefaultModel(final String defaultModel) {
    this.defaultModel = defaultModel;
  }

  /** Configuration for generated test markers. */
  public static class MarkerConfig {

    @JsonProperty("enabled")
    private Boolean enabled = true;

    @JsonProperty("tag")
    private String tag = "FUL:GENERATED_TEST";

    @JsonProperty("scan_first_lines")
    private Integer scanFirstLines = 20;

    public boolean isEnabled() {
      return enabled == null || enabled;
    }

    public void setEnabled(final Boolean enabled) {
      this.enabled = enabled;
    }

    public String getTag() {
      return tag != null ? tag : "FUL:GENERATED_TEST";
    }

    public void setTag(final String tag) {
      this.tag = tag;
    }

    public Integer getScanFirstLines() {
      return scanFirstLines != null ? scanFirstLines : 20;
    }

    public void setScanFirstLines(final Integer scanFirstLines) {
      this.scanFirstLines = scanFirstLines;
    }
  }

  /** Configuration for Few-Shot examples. */
  public static class FewShotConfig {

    @JsonProperty("enabled")
    private Boolean enabled = true;

    @JsonProperty("examples_dir")
    private String examplesDir;

    @JsonProperty("max_examples")
    private Integer maxExamples = 3;

    @JsonProperty("use_class_type_detection")
    private Boolean useClassTypeDetection = true;

    public boolean isEnabled() {
      return enabled == null || enabled;
    }

    public void setEnabled(final Boolean enabled) {
      this.enabled = enabled;
    }

    public String getExamplesDir() {
      return examplesDir;
    }

    public void setExamplesDir(final String examplesDir) {
      this.examplesDir = examplesDir;
    }

    public Integer getMaxExamples() {
      return maxExamples != null ? maxExamples : 3;
    }

    public void setMaxExamples(final Integer maxExamples) {
      this.maxExamples = maxExamples;
    }

    public boolean isUseClassTypeDetection() {
      return useClassTypeDetection == null || useClassTypeDetection;
    }

    public void setUseClassTypeDetection(final Boolean useClassTypeDetection) {
      this.useClassTypeDetection = useClassTypeDetection;
    }
  }
}
