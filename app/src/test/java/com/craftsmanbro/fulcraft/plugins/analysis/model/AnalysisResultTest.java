package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisResultTest {

  @Test
  void constructor_setsProjectId() {
    AnalysisResult result = new AnalysisResult("demo-project");

    assertThat(result.getProjectId()).isEqualTo("demo-project");
  }

  @Test
  void constructor_requiresProjectId() {
    assertThatThrownBy(() -> new AnalysisResult(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("projectId");
  }

  @Test
  void getters_initializeListsAndAllowMutation() {
    AnalysisResult result = new AnalysisResult();

    assertThat(result.getClasses()).isEmpty();
    result.getClasses().add(new ClassInfo());
    assertThat(result.getClasses()).hasSize(1);

    assertThat(result.getAnalysisErrors()).isEmpty();
    result.getAnalysisErrors().add(new AnalysisError("file", "msg", 1));
    assertThat(result.getAnalysisErrors()).hasSize(1);
  }

  @Test
  void setters_acceptNullAndResetToEmpty() {
    AnalysisResult result = new AnalysisResult();

    result.setClasses(null);
    result.setAnalysisErrors(null);

    assertThat(result.getClasses()).isEmpty();
    assertThat(result.getAnalysisErrors()).isEmpty();
  }

  @Test
  void scalarSetters_roundTripValues() {
    AnalysisResult result = new AnalysisResult();

    result.setProjectId("project-a");
    result.setCommitHash("abc123");

    assertThat(result.getProjectId()).isEqualTo("project-a");
    assertThat(result.getCommitHash()).isEqualTo("abc123");
  }
}
