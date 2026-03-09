package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisErrorTest {

  @Test
  void canonicalConstructor_defaultsSeverityToError() {
    AnalysisError error = new AnalysisError("file", "msg", 10, "method", null);

    assertThat(error.filePath()).isEqualTo("file");
    assertThat(error.message()).isEqualTo("msg");
    assertThat(error.line()).isEqualTo(10);
    assertThat(error.methodId()).isEqualTo("method");
    assertThat(error.severity()).isEqualTo(AnalysisError.Severity.ERROR);
  }

  @Test
  void convenienceConstructors_setDefaultSeverity() {
    AnalysisError basic = new AnalysisError("file", "msg", 10);
    AnalysisError withMethod = new AnalysisError("file", "msg", 10, "method");

    assertThat(basic.severity()).isEqualTo(AnalysisError.Severity.ERROR);
    assertThat(withMethod.severity()).isEqualTo(AnalysisError.Severity.ERROR);
    assertThat(withMethod.methodId()).isEqualTo("method");
  }

  @Test
  void constructor_rejectsNullRequiredFields() {
    assertThatThrownBy(() -> new AnalysisError(null, "msg", 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AnalysisError("file", null, 1))
        .isInstanceOf(NullPointerException.class);
  }
}
