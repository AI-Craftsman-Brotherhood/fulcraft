package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Feature-layer neutral task representation for task file I/O and reporting. */
public class TaskRecord {

  @JsonProperty("task_id")
  private String taskId;

  @JsonProperty("project_id")
  private String projectId;

  @JsonProperty("class_fqn")
  private String classFqn;

  @JsonProperty("file_path")
  private String filePath;

  @JsonProperty("method_name")
  private String methodName;

  @JsonProperty("method_signature")
  private String methodSignature;

  @JsonProperty("jdk_target")
  private String jdkTarget;

  @JsonProperty("test_class_name")
  private String testClassName;

  @JsonProperty("selected")
  private Boolean selected;

  @JsonProperty("exclusion_reason")
  private String exclusionReason;

  @JsonProperty("visibility")
  private String visibility;

  @JsonProperty("loc")
  private Integer loc;

  @JsonProperty("complexity")
  private Integer complexity;

  @JsonProperty("representative_path_count")
  private Integer representativePathCount;

  @JsonProperty("dynamic_pattern_rank")
  private Integer dynamicPatternRank;

  @JsonProperty("selection_reason")
  private String selectionReason;

  @JsonProperty("selection_metadata")
  private Map<String, Object> selectionMetadata;

  @JsonProperty("thrown_exceptions")
  private List<String> thrownExceptions = new ArrayList<>();

  @JsonProperty("feasibility_score")
  private Double feasibilityScore;

  @JsonProperty("feasibility_breakdown")
  private Map<String, Object> feasibilityBreakdown;

  @JsonProperty("analysis_context_json")
  private String analysisContextJson;

  @JsonProperty("complexity_strategy")
  private String complexityStrategy;

  @JsonProperty("high_complexity")
  private Boolean highComplexity;

  @JsonProperty("has_loops")
  private Boolean hasLoops;

  @JsonProperty("has_conditionals")
  private Boolean hasConditionals;

  @JsonProperty("is_static")
  private Boolean isStatic;

  @JsonProperty("brittle")
  private Boolean brittle;

  @JsonProperty("brittleness_signals")
  private List<String> brittlenessSignals = new ArrayList<>();

  @JsonProperty("test_stability_policy")
  private String testStabilityPolicy;

  @JsonProperty("expected_test_case_count")
  private Integer expectedTestCaseCount;

  @JsonProperty("ful_custom_assertions")
  private List<String> utGenCustomAssertions = new ArrayList<>();

  @JsonProperty("ful_timeout_seconds")
  private Integer utGenTimeoutSeconds;

  @JsonProperty("ful_provider")
  private String utGenProvider;

  @JsonProperty("ful_model")
  private String utGenModel;

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(final String taskId) {
    this.taskId = taskId;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(final String projectId) {
    this.projectId = projectId;
  }

  public String getClassFqn() {
    return classFqn;
  }

  public void setClassFqn(final String classFqn) {
    this.classFqn = classFqn;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(final String filePath) {
    this.filePath = filePath;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(final String methodName) {
    this.methodName = methodName;
  }

  public String getMethodSignature() {
    return methodSignature;
  }

  public void setMethodSignature(final String methodSignature) {
    this.methodSignature = methodSignature;
  }

  public String getJdkTarget() {
    return jdkTarget;
  }

  public void setJdkTarget(final String jdkTarget) {
    this.jdkTarget = jdkTarget;
  }

  public String getTestClassName() {
    return testClassName;
  }

  public void setTestClassName(final String testClassName) {
    this.testClassName = testClassName;
  }

  public Boolean getSelected() {
    return selected;
  }

  public void setSelected(final Boolean selected) {
    this.selected = selected;
  }

  public String getExclusionReason() {
    return exclusionReason;
  }

  public void setExclusionReason(final String exclusionReason) {
    this.exclusionReason = exclusionReason;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(final String visibility) {
    this.visibility = visibility;
  }

  public Integer getLoc() {
    return loc;
  }

  public void setLoc(final Integer loc) {
    this.loc = loc;
  }

  public Integer getComplexity() {
    return complexity;
  }

  public void setComplexity(final Integer complexity) {
    this.complexity = complexity;
  }

  public Integer getRepresentativePathCount() {
    return representativePathCount;
  }

  public void setRepresentativePathCount(final Integer representativePathCount) {
    this.representativePathCount = representativePathCount;
  }

  public Integer getDynamicPatternRank() {
    return dynamicPatternRank;
  }

  public void setDynamicPatternRank(final Integer dynamicPatternRank) {
    this.dynamicPatternRank = dynamicPatternRank;
  }

  public String getSelectionReason() {
    return selectionReason;
  }

  public void setSelectionReason(final String selectionReason) {
    this.selectionReason = selectionReason;
  }

  public Map<String, Object> getSelectionMetadata() {
    if (selectionMetadata == null) {
      return Collections.emptyMap();
    }
    return new TreeMap<>(selectionMetadata);
  }

  public void setSelectionMetadata(final Map<String, Object> selectionMetadata) {
    if (selectionMetadata == null) {
      this.selectionMetadata = null;
      return;
    }
    this.selectionMetadata = new TreeMap<>(selectionMetadata);
  }

  public List<String> getThrownExceptions() {
    return Collections.unmodifiableList(thrownExceptions);
  }

  public void setThrownExceptions(final List<String> thrownExceptions) {
    this.thrownExceptions = Objects.requireNonNullElseGet(thrownExceptions, ArrayList::new);
  }

  public Double getFeasibilityScore() {
    return feasibilityScore;
  }

  public void setFeasibilityScore(final Double feasibilityScore) {
    this.feasibilityScore = feasibilityScore;
  }

  public Map<String, Object> getFeasibilityBreakdown() {
    if (feasibilityBreakdown == null) {
      return Collections.emptyMap();
    }
    return new TreeMap<>(feasibilityBreakdown);
  }

  public void setFeasibilityBreakdown(final Map<String, Object> feasibilityBreakdown) {
    if (feasibilityBreakdown == null) {
      this.feasibilityBreakdown = null;
      return;
    }
    this.feasibilityBreakdown = new TreeMap<>(feasibilityBreakdown);
  }

  public String getAnalysisContextJson() {
    return analysisContextJson;
  }

  public void setAnalysisContextJson(final String analysisContextJson) {
    this.analysisContextJson = analysisContextJson;
  }

  public String getComplexityStrategy() {
    return complexityStrategy;
  }

  public void setComplexityStrategy(final String complexityStrategy) {
    this.complexityStrategy = complexityStrategy;
  }

  public Boolean getHighComplexity() {
    return highComplexity;
  }

  public void setHighComplexity(final Boolean highComplexity) {
    this.highComplexity = highComplexity;
  }

  public Boolean getHasLoops() {
    return hasLoops;
  }

  public void setHasLoops(final Boolean hasLoops) {
    this.hasLoops = hasLoops;
  }

  public Boolean getHasConditionals() {
    return hasConditionals;
  }

  public void setHasConditionals(final Boolean hasConditionals) {
    this.hasConditionals = hasConditionals;
  }

  public Boolean getIsStatic() {
    return isStatic;
  }

  public void setIsStatic(final Boolean isStatic) {
    this.isStatic = isStatic;
  }

  public Boolean getBrittle() {
    return brittle;
  }

  public void setBrittle(final Boolean brittle) {
    this.brittle = brittle;
  }

  public List<String> getBrittlenessSignals() {
    return Collections.unmodifiableList(brittlenessSignals);
  }

  public void setBrittlenessSignals(final List<String> brittlenessSignals) {
    this.brittlenessSignals = Objects.requireNonNullElseGet(brittlenessSignals, ArrayList::new);
  }

  public String getTestStabilityPolicy() {
    return testStabilityPolicy;
  }

  public void setTestStabilityPolicy(final String testStabilityPolicy) {
    this.testStabilityPolicy = testStabilityPolicy;
  }

  public Integer getExpectedTestCaseCount() {
    return expectedTestCaseCount;
  }

  public void setExpectedTestCaseCount(final Integer expectedTestCaseCount) {
    this.expectedTestCaseCount = expectedTestCaseCount;
  }

  public List<String> getUtGenCustomAssertions() {
    return Collections.unmodifiableList(utGenCustomAssertions);
  }

  public void setUtGenCustomAssertions(final List<String> utGenCustomAssertions) {
    this.utGenCustomAssertions =
        Objects.requireNonNullElseGet(utGenCustomAssertions, ArrayList::new);
  }

  public Integer getUtGenTimeoutSeconds() {
    return utGenTimeoutSeconds;
  }

  public void setUtGenTimeoutSeconds(final Integer utGenTimeoutSeconds) {
    this.utGenTimeoutSeconds = utGenTimeoutSeconds;
  }

  public String getUtGenProvider() {
    return utGenProvider;
  }

  public void setUtGenProvider(final String utGenProvider) {
    this.utGenProvider = utGenProvider;
  }

  public String getUtGenModel() {
    return utGenModel;
  }

  public void setUtGenModel(final String utGenModel) {
    this.utGenModel = utGenModel;
  }
}
