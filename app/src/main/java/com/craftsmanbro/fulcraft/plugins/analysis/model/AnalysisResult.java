package com.craftsmanbro.fulcraft.plugins.analysis.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Static analysis output model.
 *
 * <p>List fields are treated as non-null; when absent/undefined in JSON they default to empty
 * lists.
 */
public class AnalysisResult {

  @JsonProperty("project_id")
  private String projectId;

  @JsonProperty("commit_hash")
  private String commitHash;

  /** Analyzed classes. Empty when no class was detected or when omitted in JSON. */
  @JsonProperty("classes")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<ClassInfo> classes = new ArrayList<>();

  /** Errors occurred during analysis. Empty when no error occurred or when omitted in JSON. */
  @JsonProperty("analysis_errors")
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  private List<AnalysisError> analysisErrors = new ArrayList<>();

  public AnalysisResult() {}

  public AnalysisResult(final String projectId) {
    this.projectId =
        Objects.requireNonNull(
            projectId,
            MessageSource.getMessage("analysis.common.error.argument_null", "projectId"));
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(final String projectId) {
    this.projectId = projectId;
  }

  public String getCommitHash() {
    return commitHash;
  }

  public void setCommitHash(final String commitHash) {
    this.commitHash = commitHash;
  }

  public List<ClassInfo> getClasses() {
    classes = Objects.requireNonNullElseGet(classes, ArrayList::new);
    return classes;
  }

  public void setClasses(final List<ClassInfo> classes) {
    this.classes = Objects.requireNonNullElseGet(classes, ArrayList::new);
  }

  public List<AnalysisError> getAnalysisErrors() {
    analysisErrors = Objects.requireNonNullElseGet(analysisErrors, ArrayList::new);
    return analysisErrors;
  }

  public void setAnalysisErrors(final List<AnalysisError> analysisErrors) {
    this.analysisErrors = Objects.requireNonNullElseGet(analysisErrors, ArrayList::new);
  }
}
