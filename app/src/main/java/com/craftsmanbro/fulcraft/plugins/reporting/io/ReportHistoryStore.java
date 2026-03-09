package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportHistory;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Loads and saves report history. */
public class ReportHistoryStore {

  private static final String HISTORY_FILENAME = "report_history.json";

  private static final String LOGS_ROOT_REQUIRED = "logsRoot must not be null";

  private final JsonServicePort jsonService;

  public ReportHistoryStore() {
    this(new DefaultJsonService());
  }

  public ReportHistoryStore(final JsonServicePort jsonService) {
    this.jsonService =
        Objects.requireNonNull(
            jsonService,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "jsonService must not be null"));
  }

  public ReportHistory loadHistory(final Path logsRoot) {
    Objects.requireNonNull(logsRoot, LOGS_ROOT_REQUIRED);
    final Path historyFile = logsRoot.resolve(HISTORY_FILENAME);
    if (!Files.exists(historyFile)) {
      return new ReportHistory();
    }
    final Path lockFile = logsRoot.resolve(HISTORY_FILENAME + ".lock");
    try {
      try (FileChannel channel =
          FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        channel.lock();
        return jsonService.readFromFile(historyFile, ReportHistory.class);
      }
    } catch (IOException e) {
      Logger.warn(MessageSource.getMessage("report.history.load_failed", e.getMessage()));
      return new ReportHistory();
    }
  }

  public void saveHistory(final Path logsRoot, final ReportHistory history) {
    Objects.requireNonNull(logsRoot, LOGS_ROOT_REQUIRED);
    Objects.requireNonNull(
        history,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "history must not be null"));
    final Path historyFile = logsRoot.resolve(HISTORY_FILENAME);
    final Path lockFile = logsRoot.resolve(HISTORY_FILENAME + ".lock");
    final Path tempFile = logsRoot.resolve(HISTORY_FILENAME + ".tmp");
    try {
      Files.createDirectories(logsRoot);
      try (FileChannel channel =
          FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        channel.lock();
        jsonService.writeToFile(tempFile, history);
        moveHistoryFile(tempFile, historyFile);
        Logger.debug(MessageSource.getMessage("report.history.saved", historyFile));
      }
    } catch (IOException e) {
      Logger.warn(MessageSource.getMessage("report.history.save_failed", e.getMessage()));
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        Logger.debug(
            MessageSource.getMessage("report.history.cleanup_temp_failed", e.getMessage()));
      }
    }
  }

  public Path getHistoryFilePath(final Path logsRoot) {
    Objects.requireNonNull(logsRoot, LOGS_ROOT_REQUIRED);
    return logsRoot.resolve(HISTORY_FILENAME);
  }

  private void moveHistoryFile(final Path tempFile, final Path historyFile) throws IOException {
    try {
      Files.move(
          tempFile,
          historyFile,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.move(tempFile, historyFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
