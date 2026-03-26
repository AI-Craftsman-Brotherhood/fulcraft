package com.craftsmanbro.fulcraft.plugins.analysis.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
public class AnalysisConfig {
  private static final String MODE_STRICT = "STRICT";
  private static final String MODE_AUTO = "AUTO";
  private static final String MODE_OFF = "OFF";
  private static final String TOOL_DELOMBOK = "DELOMBOK";
  private static final String CHARSET_UTF8 = "UTF-8";
  private static final int DEFAULT_INTERPROCEDURAL_CALLSITE_LIMIT = 20;
  private static final String DEFAULT_PREPROCESS_WORK_DIR = ".utg/preprocess";
  public static final String DEFAULT_VISUAL_REPORT_TEMPLATE =
      "templates/report/analysis_visual.html.tmpl";

  private static boolean isTrue(Boolean value) {
    return Boolean.TRUE.equals(value);
  }

  private static boolean isNullOrTrue(Boolean value) {
    return value == null || value;
  }

  private static <T> T valueOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static boolean isMode(String expectedMode, String actualMode) {
    return expectedMode.equalsIgnoreCase(actualMode);
  }

  @JsonProperty("engine")
  @Getter
  private String engine;

  @JsonProperty("spoon")
  @Getter
  private SpoonConfig spoon;

  @JsonProperty("dump_file_list")
  private Boolean dumpFileList = false;

  @JsonProperty("source_root_mode")
  private String sourceRootMode = MODE_AUTO;

  @JsonProperty("source_root_paths")
  private List<String> sourceRootPaths;

  @JsonProperty("source_charset")
  private String sourceCharset = CHARSET_UTF8;

  // 1-hop inter-procedural resolution for method parameter arguments
  @JsonProperty("enable_interprocedural_resolution")
  private Boolean enableInterproceduralResolution = false;

  @JsonProperty("interprocedural_callsite_limit")
  private Integer interproceduralCallsiteLimit = DEFAULT_INTERPROCEDURAL_CALLSITE_LIMIT;

  @JsonProperty("external_config_resolution")
  private Boolean externalConfigResolution = false;

  @JsonProperty("exclude_tests")
  private Boolean excludeTests = true;

  @JsonProperty("visual_report_template")
  @Getter
  private String visualReportTemplate = DEFAULT_VISUAL_REPORT_TEMPLATE;

  public Boolean getExcludeTests() {
    return isNullOrTrue(excludeTests);
  }

  public Boolean getDumpFileList() {
    return isTrue(dumpFileList);
  }

  public String getSourceRootMode() {
    return valueOrDefault(sourceRootMode, MODE_AUTO);
  }

  public List<String> getSourceRootPaths() {
    if (sourceRootPaths == null) {
      return List.of("src/main/java", "app/src/main/java");
    }
    return sourceRootPaths;
  }

  public String getSourceCharset() {
    return valueOrDefault(sourceCharset, CHARSET_UTF8);
  }

  public Boolean getEnableInterproceduralResolution() {
    return isTrue(enableInterproceduralResolution);
  }

  public Integer getInterproceduralCallsiteLimit() {
    return valueOrDefault(interproceduralCallsiteLimit, DEFAULT_INTERPROCEDURAL_CALLSITE_LIMIT);
  }

  public Boolean getExternalConfigResolution() {
    return isTrue(externalConfigResolution);
  }

  // Debug mode for dynamic resolution - enables verbose logging
  @JsonProperty("debug_dynamic_resolution")
  private Boolean debugDynamicResolution = false;

  public Boolean getDebugDynamicResolution() {
    return isTrue(debugDynamicResolution);
  }

  @JsonProperty("experimental_candidate_enum")
  private Boolean experimentalCandidateEnum = false;

  public Boolean getExperimentalCandidateEnum() {
    return isTrue(experimentalCandidateEnum);
  }

  public boolean isStrictMode() {
    return isMode(MODE_STRICT, getSourceRootMode());
  }

  @JsonProperty("classpath")
  @Getter
  private ClasspathConfig classpath;

  /** Gets the effective classpath mode (AUTO, STRICT, OFF). Returns AUTO if not configured. */
  public String getClasspathMode() {
    if (classpath == null) {
      return MODE_AUTO;
    }
    return classpath.getMode();
  }

  @Setter
  public static class SpoonConfig {

    @JsonProperty("no_classpath")
    private Boolean noClasspath = true;

    public Boolean getNoClasspath() {
      return isNullOrTrue(noClasspath);
    }
  }

  /** Configuration for classpath resolution behavior. */
  @Setter
  public static class ClasspathConfig {

    @JsonProperty("mode")
    private String mode = MODE_AUTO;

    public String getMode() {
      return valueOrDefault(mode, MODE_AUTO);
    }

    /** AUTO: use classpath if available, fallback to noClasspath if unavailable */
    public boolean isAutoMode() {
      return isMode(MODE_AUTO, getMode());
    }

    /** STRICT: fail if classpath cannot be resolved */
    public boolean isStrictMode() {
      return isMode(MODE_STRICT, getMode());
    }

    /** OFF: always use noClasspath mode */
    public boolean isOffMode() {
      return isMode(MODE_OFF, getMode());
    }
  }

  /** Configuration for source preprocessing (delombok, APT). */
  @Setter
  public static class PreprocessConfig {

    // OFF, AUTO, STRICT
    @JsonProperty("mode")
    private String mode = MODE_OFF;

    // AUTO, DELOMBOK
    @JsonProperty("tool")
    private String tool = MODE_AUTO;

    @JsonProperty("work_dir")
    private String workDir = DEFAULT_PREPROCESS_WORK_DIR;

    @JsonProperty("clean_work_dir")
    private Boolean cleanWorkDir = true;

    @JsonProperty("include_generated")
    private Boolean includeGenerated = false;

    @JsonProperty("delombok")
    @Getter
    private DelombokConfig delombok;

    public String getMode() {
      return valueOrDefault(mode, MODE_OFF);
    }

    public String getTool() {
      return valueOrDefault(tool, MODE_AUTO);
    }

    public String getWorkDir() {
      return valueOrDefault(workDir, DEFAULT_PREPROCESS_WORK_DIR);
    }

    public Boolean getCleanWorkDir() {
      return isNullOrTrue(cleanWorkDir);
    }

    public Boolean getIncludeGenerated() {
      return isTrue(includeGenerated);
    }

    public boolean isOffMode() {
      return isMode(MODE_OFF, getMode());
    }

    public boolean isAutoMode() {
      return isMode(MODE_AUTO, getMode());
    }

    public boolean isStrictMode() {
      return isMode(MODE_STRICT, getMode());
    }

    public boolean isDelombokTool() {
      String effectiveTool = getTool();
      return isMode(TOOL_DELOMBOK, effectiveTool) || isMode(MODE_AUTO, effectiveTool);
    }
  }

  /** Configuration for delombok execution. */
  @Setter
  public static class DelombokConfig {

    @JsonProperty("enabled")
    private Boolean enabled = true;

    @JsonProperty("lombok_jar_path")
    @Getter
    private String lombokJarPath;

    public Boolean getEnabled() {
      return isNullOrTrue(enabled);
    }
  }

  @JsonProperty("preprocess")
  @Getter
  private PreprocessConfig preprocess;

  /** Gets the effective preprocess mode (OFF, AUTO, STRICT). Returns OFF if not configured. */
  public String getPreprocessMode() {
    if (preprocess == null) {
      return MODE_OFF;
    }
    return preprocess.getMode();
  }
}
