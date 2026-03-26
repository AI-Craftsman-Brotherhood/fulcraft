package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisError(
    @JsonProperty("file_path") String filePath,
    @JsonProperty("message") String message,
    @JsonProperty("line") Integer line,
    @JsonProperty("method_id") String methodId,
    @JsonProperty("severity") Severity severity) {

  public AnalysisError {
    Objects.requireNonNull(
        filePath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "filePath must not be null"));
    Objects.requireNonNull(
        message,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "message must not be null"));
    if (severity == null) {
      severity = Severity.ERROR;
    }
  }

  public AnalysisError(final String filePath, final String message, final Integer line) {
    this(filePath, message, line, null, Severity.ERROR);
  }

  public AnalysisError(
      final String filePath, final String message, final Integer line, final String methodId) {
    this(filePath, message, line, methodId, Severity.ERROR);
  }

  public enum Severity {
    INFO,
    WARN,
    ERROR
  }
}
