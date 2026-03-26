package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.FixErrorHistory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Saves fix error history to JSON files for post-hoc analysis.
 *
 * <p>When a fix loop fails to converge, the error history is saved to enable debugging and pattern
 * analysis.
 */
public class FixErrorHistoryWriter {

  private static final String FIX_ERRORS_DIR = "fix_errors";

  private final JsonServicePort jsonService;

  private final Path logsRoot;

  /**
   * Creates a FixErrorHistoryWriter with the specified logs directory.
   *
   * @param logsRoot the root directory for logs
   */
  public FixErrorHistoryWriter(final Path logsRoot) {
    this(logsRoot, new DefaultJsonService());
  }

  /**
   * Creates a FixErrorHistoryWriter with the specified logs directory and JsonServicePort.
   *
   * @param logsRoot the root directory for logs
   * @param jsonService the JSON service
   */
  public FixErrorHistoryWriter(final Path logsRoot, final JsonServicePort jsonService) {
    this.logsRoot =
        Objects.requireNonNull(
            logsRoot,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "logsRoot must not be null"));
    this.jsonService =
        Objects.requireNonNull(
            jsonService,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "jsonService must not be null"));
  }

  /**
   * Saves the fix error history to a JSON file.
   *
   * @param history the error history to save
   */
  public void save(final FixErrorHistory history) {
    Objects.requireNonNull(
        history,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "history must not be null"));
    if (history.getTaskId() == null || history.getTaskId().isBlank()) {
      Logger.warn(MessageSource.getMessage("report.fix_error_history.missing_task_id"));
      return;
    }
    final Path outputDir = logsRoot.resolve(FIX_ERRORS_DIR);
    final String sanitizedTaskId = sanitizeFilename(history.getTaskId());
    final Path outputFile = outputDir.resolve(sanitizedTaskId + ".json");
    try {
      Files.createDirectories(outputDir);
      jsonService.writeToFile(outputFile, history);
      Logger.debug(MessageSource.getMessage("report.fix_error_history.saved", outputFile));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "report.fix_error_history.save_failed",
              outputFile,
              e.getClass().getSimpleName(),
              e.getMessage()));
    }
  }

  /**
   * Sanitizes a task ID to be safe for use as a filename.
   *
   * @param taskId the task ID
   * @return sanitized filename
   */
  private String sanitizeFilename(final String taskId) {
    // Replace problematic characters with underscores
    return taskId.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  /**
   * Gets the output directory for fix error histories.
   *
   * @return the fix errors directory path
   */
  public Path getOutputDirectory() {
    return logsRoot.resolve(FIX_ERRORS_DIR);
  }
}
