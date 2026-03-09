package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class TestRunFailedException extends IOException {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private static final long serialVersionUID = 1L;

  private final String runId;

  private final transient Path logsDir;

  private final transient Path reportDir;

  public TestRunFailedException(
      final String runId, final Path logsDir, final Path reportDir, final String message) {
    super(Objects.requireNonNull(message, argumentNullMessage("message")));
    this.runId = Objects.requireNonNull(runId, argumentNullMessage("runId"));
    this.logsDir = Objects.requireNonNull(logsDir, argumentNullMessage("logsDir"));
    this.reportDir = Objects.requireNonNull(reportDir, argumentNullMessage("reportDir"));
  }

  public String getRunId() {
    return runId;
  }

  public Path getLogsDir() {
    return logsDir;
  }

  public Path getReportDir() {
    return reportDir;
  }

  private static String argumentNullMessage(final String argumentName) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentName);
  }
}
