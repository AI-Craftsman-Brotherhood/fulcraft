package com.craftsmanbro.fulcraft.plugins.analysis.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
public class SelectionRulesConfig {

  private static final int DEFAULT_CLASS_MIN_LOC = 10;

  private static final int DEFAULT_CLASS_MIN_METHOD_COUNT = 1;

  private static final int DEFAULT_METHOD_MIN_LOC = 3;

  private static final int DEFAULT_METHOD_MAX_LOC = 1000;

  private static final boolean DEFAULT_EXCLUDE_GETTERS_SETTERS = true;

  private static final boolean DEFAULT_EXCLUDE_DEAD_CODE = false;

  @JsonProperty("class_min_loc")
  private Integer classMinLoc;

  @JsonProperty("class_min_method_count")
  private Integer classMinMethodCount;

  @JsonProperty("max_targets")
  @Getter
  private Integer maxTargets = 50;

  @JsonProperty("strategy")
  @Getter
  private String strategy = "SMART";

  @JsonProperty("selection_engine")
  private String selectionEngine = "rule_based";

  @JsonProperty("method_min_loc")
  private Integer methodMinLoc;

  @JsonProperty("method_max_loc")
  private Integer methodMaxLoc;

  @JsonProperty("max_methods_per_class")
  @Getter
  private Integer maxMethodsPerClass;

  @JsonProperty("exclude_getters_setters")
  private Boolean excludeGettersSetters = true;

  // D: Exclude potentially unused methods
  @JsonProperty("exclude_dead_code")
  private Boolean excludeDeadCode;

  // S6: Limit per package/module to avoid over-concentration
  @JsonProperty("max_methods_per_package")
  @Getter
  private Integer maxMethodsPerPackage;

  // S3: Annotation-based weighting/exclusion
  @JsonProperty("exclude_annotations")
  private List<String> excludeAnnotations = new ArrayList<>();

  @JsonProperty("deprioritize_annotations")
  private List<String> deprioritizeAnnotations = new ArrayList<>();

  @JsonProperty("priority_annotations")
  private List<String> priorityAnnotations = new ArrayList<>();

  @JsonProperty("scoring_weights")
  @Getter
  private ScoringWeights scoringWeights;

  // Complexity handling configuration
  @JsonProperty("complexity")
  private ComplexityConfig complexity;

  // Score boost for methods using removed/deprecated APIs
  @JsonProperty("removal_boost")
  private Double removalBoost = 50.0;

  // Score multiplier for deprioritized classes/methods
  @JsonProperty("deprioritize_factor")
  private Double deprioritizeFactor = 0.5;

  // Feasibility scoring penalties
  @JsonProperty("feasibility_penalties")
  private FeasibilityPenalties feasibilityPenalties;

  // Version history-based selection (V Extension)
  @JsonProperty("version_history")
  private VersionHistoryConfig versionHistory;

  // Dynamic Selection Configuration (Phase 4)
  @JsonProperty("enable_dynamic_selection")
  private Boolean enableDynamicSelection = false;

  @JsonProperty("dynamic_selection_dry_run")
  private Boolean dynamicSelectionDryRun = true;

  @JsonProperty("min_dynamic_confidence")
  private Double minDynamicConfidence = 0.5;

  @JsonProperty("unresolved_penalty")
  private Double unresolvedPenalty = 0.2;

  public ComplexityConfig getComplexity() {
    if (complexity == null) {
      complexity = new ComplexityConfig();
    }
    return complexity;
  }

  public Double getRemovalBoost() {
    return removalBoost != null ? removalBoost : 50.0;
  }

  public Double getDeprioritizeFactor() {
    return deprioritizeFactor != null ? deprioritizeFactor : 0.5;
  }

  public FeasibilityPenalties getFeasibilityPenalties() {
    return feasibilityPenalties != null ? feasibilityPenalties : new FeasibilityPenalties();
  }

  public VersionHistoryConfig getVersionHistory() {
    if (versionHistory == null) {
      versionHistory = VersionHistoryConfig.createDefault();
    }
    return versionHistory;
  }

  public Boolean getEnableDynamicSelection() {
    return Boolean.TRUE.equals(enableDynamicSelection);
  }

  public Boolean getDynamicSelectionDryRun() {
    return dynamicSelectionDryRun == null || dynamicSelectionDryRun;
  }

  public Double getMinDynamicConfidence() {
    return minDynamicConfidence != null ? minDynamicConfidence : 0.5;
  }

  public Double getUnresolvedPenalty() {
    return unresolvedPenalty != null ? unresolvedPenalty : 0.2;
  }

  // Phase 4 New Configs
  @JsonProperty("skip_threshold")
  private Double skipThreshold = 0.5;

  @JsonProperty("penalty_low_confidence")
  private Double penaltyLowConfidence = 0.4;

  @JsonProperty("penalty_unresolved_each")
  private Double penaltyUnresolvedEach = 0.1;

  @JsonProperty("penalty_external_each")
  private Double penaltyExternalEach = 0.2;

  @JsonProperty("penalty_service_loader_low")
  private Double penaltyServiceLoaderLow = 0.2;

  // Selection Strength Evaluation Thresholds
  @JsonProperty("evaluation_max_excluded_rate")
  private Double evaluationMaxExcludedRate = 0.20;

  @JsonProperty("evaluation_wasted_potential_ratio")
  private Double evaluationWastedPotentialRatio = 0.30;

  @JsonProperty("evaluation_failure_concentration")
  private Double evaluationFailureConcentration = 0.50;

  @JsonProperty("evaluation_min_failure_rate")
  private Double evaluationMinFailureRate = 0.15;

  public Double getSkipThreshold() {
    return skipThreshold != null ? skipThreshold : 0.5;
  }

  public Double getPenaltyLowConfidence() {
    return penaltyLowConfidence != null ? penaltyLowConfidence : 0.4;
  }

  public Double getPenaltyUnresolvedEach() {
    return penaltyUnresolvedEach != null ? penaltyUnresolvedEach : 0.1;
  }

  public Double getPenaltyExternalEach() {
    return penaltyExternalEach != null ? penaltyExternalEach : 0.2;
  }

  public Double getPenaltyServiceLoaderLow() {
    return penaltyServiceLoaderLow != null ? penaltyServiceLoaderLow : 0.2;
  }

  // Selection Strength Evaluation Threshold Getters
  public Double getEvaluationMaxExcludedRate() {
    return evaluationMaxExcludedRate != null ? evaluationMaxExcludedRate : 0.20;
  }

  public Double getEvaluationWastedPotentialRatio() {
    return evaluationWastedPotentialRatio != null ? evaluationWastedPotentialRatio : 0.30;
  }

  public Double getEvaluationFailureConcentration() {
    return evaluationFailureConcentration != null ? evaluationFailureConcentration : 0.50;
  }

  public Double getEvaluationMinFailureRate() {
    return evaluationMinFailureRate != null ? evaluationMinFailureRate : 0.15;
  }

  /** Configuration for handling high-complexity methods. */
  @Getter
  @Setter
  public static class ComplexityConfig {

    /** Maximum cyclomatic complexity before applying special strategy. Default: 20 */
    @JsonProperty("max_cyclomatic")
    private Integer maxCyclomatic = 20;

    /** Strategy to apply for high-complexity methods: skip, warn, split, specialized_prompt */
    @JsonProperty("strategy")
    private String strategy = "warn";

    /** Expected tests per unit of complexity. Default: 1.5 */
    @JsonProperty("expected_tests_per_complexity")
    private Double expectedTestsPerComplexity = 1.5;

    /** Maximum expected test count regardless of complexity. Default: 20 */
    @JsonProperty("max_expected_tests")
    private Integer maxExpectedTests = 20;

    /** Check if this is a high-complexity method based on threshold. */
    public boolean isHighComplexity(final int complexity) {
      return maxCyclomatic != null && complexity > maxCyclomatic;
    }

    /** Get the strategy enum value safely. */
    public ComplexityStrategy getStrategyEnum() {
      if (strategy == null) {
        return ComplexityStrategy.WARN;
      }
      try {
        return ComplexityStrategy.valueOf(strategy.toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
        return ComplexityStrategy.WARN;
      }
    }

    /** Complexity handling strategies. */
    public enum ComplexityStrategy {
      SKIP,
      WARN,
      SPLIT,
      SPECIALIZED_PROMPT
    }
  }

  public Integer getClassMinLoc() {
    return classMinLoc != null ? classMinLoc : DEFAULT_CLASS_MIN_LOC;
  }

  public Integer getClassMinMethodCount() {
    return classMinMethodCount != null ? classMinMethodCount : DEFAULT_CLASS_MIN_METHOD_COUNT;
  }

  public String getSelectionEngine() {
    return selectionEngine != null && !selectionEngine.isBlank() ? selectionEngine : "core";
  }

  public Integer getMethodMinLoc() {
    return methodMinLoc != null ? methodMinLoc : DEFAULT_METHOD_MIN_LOC;
  }

  public Integer getMethodMaxLoc() {
    return methodMaxLoc != null ? methodMaxLoc : DEFAULT_METHOD_MAX_LOC;
  }

  public Boolean getExcludeGettersSetters() {
    return excludeGettersSetters != null ? excludeGettersSetters : DEFAULT_EXCLUDE_GETTERS_SETTERS;
  }

  public Boolean getExcludeDeadCode() {
    return excludeDeadCode != null ? excludeDeadCode : DEFAULT_EXCLUDE_DEAD_CODE;
  }

  public List<String> getExcludeAnnotations() {
    if (excludeAnnotations == null) {
      return List.of();
    }
    return Collections.unmodifiableList(excludeAnnotations);
  }

  public void setExcludeAnnotations(final List<String> excludeAnnotations) {
    this.excludeAnnotations =
        (excludeAnnotations == null) ? new ArrayList<>() : new ArrayList<>(excludeAnnotations);
  }

  public List<String> getDeprioritizeAnnotations() {
    if (deprioritizeAnnotations == null) {
      return List.of();
    }
    return Collections.unmodifiableList(deprioritizeAnnotations);
  }

  public void setDeprioritizeAnnotations(final List<String> deprioritizeAnnotations) {
    this.deprioritizeAnnotations =
        (deprioritizeAnnotations == null)
            ? new ArrayList<>()
            : new ArrayList<>(deprioritizeAnnotations);
  }

  public List<String> getPriorityAnnotations() {
    if (priorityAnnotations == null) {
      return List.of();
    }
    return Collections.unmodifiableList(priorityAnnotations);
  }

  public void setPriorityAnnotations(final List<String> priorityAnnotations) {
    this.priorityAnnotations =
        (priorityAnnotations == null) ? new ArrayList<>() : new ArrayList<>(priorityAnnotations);
  }

  @Getter
  @Setter
  public static class ScoringWeights {

    @JsonProperty("complexity")
    private Double complexity = 2.0;

    @JsonProperty("loc")
    private Double loc = 0.1;

    @JsonProperty("commit_count")
    private Double commitCount = 1.0;

    @JsonProperty("priority_annotation")
    private Double priorityAnnotation = 100.0;
  }

  /** Configuration for feasibility scoring penalties. */
  @Setter
  public static class FeasibilityPenalties {

    @JsonProperty("external_io")
    private Double externalIo = -0.40;

    @JsonProperty("di_dependency")
    private Double diDependency = -0.35;

    @JsonProperty("unsupported_param")
    private Double unsupportedParam = -0.40;

    @JsonProperty("high_param_count")
    private Double highParamCount = -0.10;

    @JsonProperty("high_complexity")
    private Double highComplexity = -0.10;

    @JsonProperty("unresolved_calls")
    private Double unresolvedCalls = -0.30;

    @JsonProperty("static_utility_bonus")
    private Double staticUtilityBonus = 0.10;

    public Double getExternalIo() {
      return externalIo != null ? externalIo : -0.40;
    }

    public Double getDiDependency() {
      return diDependency != null ? diDependency : -0.35;
    }

    public Double getUnsupportedParam() {
      return unsupportedParam != null ? unsupportedParam : -0.40;
    }

    public Double getHighParamCount() {
      return highParamCount != null ? highParamCount : -0.10;
    }

    public Double getHighComplexity() {
      return highComplexity != null ? highComplexity : -0.10;
    }

    public Double getUnresolvedCalls() {
      return unresolvedCalls != null ? unresolvedCalls : -0.30;
    }

    public Double getStaticUtilityBonus() {
      return staticUtilityBonus != null ? staticUtilityBonus : 0.10;
    }
  }

  /**
   * Configuration for version history-based selection scoring (V Extension).
   *
   * <p>Enables prioritization of frequently changed or recently modified code, based on Git commit
   * history analysis.
   */
  @Setter
  public static class VersionHistoryConfig {

    @JsonProperty("enabled")
    private Boolean enabled = false;

    @JsonProperty("change_weight")
    private Double changeWeight = 1.0;

    @JsonProperty("recency_weight")
    private Double recencyWeight = 1.0;

    @JsonProperty("bug_fix_weight")
    private Double bugFixWeight = 0.5;

    @JsonProperty("default_score")
    private Double defaultScore = 0.0;

    @JsonProperty("fallback_to_static")
    private Boolean fallbackToStatic = true;

    @JsonProperty("cache_enabled")
    private Boolean cacheEnabled = true;

    @JsonProperty("parse_release_notes")
    private Boolean parseReleaseNotes = false;

    /** Creates a default configuration. */
    public static VersionHistoryConfig createDefault() {
      return new VersionHistoryConfig();
    }

    public Boolean isEnabled() {
      return Boolean.TRUE.equals(enabled);
    }

    public Double getChangeWeight() {
      return changeWeight != null ? changeWeight : 1.0;
    }

    public Double getRecencyWeight() {
      return recencyWeight != null ? recencyWeight : 1.0;
    }

    public Double getBugFixWeight() {
      return bugFixWeight != null ? bugFixWeight : 0.5;
    }

    public Double getDefaultScore() {
      return defaultScore != null ? defaultScore : 0.0;
    }

    public Boolean isFallbackToStatic() {
      return fallbackToStatic == null || fallbackToStatic;
    }

    public Boolean isCacheEnabled() {
      return cacheEnabled == null || cacheEnabled;
    }

    public Boolean isParseReleaseNotes() {
      return Boolean.TRUE.equals(parseReleaseNotes);
    }
  }
}
