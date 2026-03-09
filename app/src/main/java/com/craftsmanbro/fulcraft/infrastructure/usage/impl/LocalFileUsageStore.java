package com.craftsmanbro.fulcraft.infrastructure.usage.impl;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.contract.UsageTrackerPort;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageRecord;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageScope;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.UsageSnapshot;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public class LocalFileUsageStore implements UsageTrackerPort {

  private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final String DEFAULT_DIR = ".ful";

  private static final String DEFAULT_FILE = "usage.json";

  private static final String LOCK_SUFFIX = ".lock";

  private static final String TEMP_SUFFIX = ".tmp";

  private static final String CORRUPTED_SUFFIX = ".corrupted";

  private final Path usageFile;

  private final ObjectMapper objectMapper;

  private final Clock clock;

  public LocalFileUsageStore(final Path projectRoot) {
    this(projectRoot, Clock.systemDefaultZone());
  }

  public LocalFileUsageStore(final Path projectRoot, final Clock clock) {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectRoot must not be null"));
    this.usageFile = projectRoot.resolve(DEFAULT_DIR).resolve(DEFAULT_FILE);
    this.clock =
        Objects.requireNonNull(
            clock,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "clock must not be null"));
    this.objectMapper =
        tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();
  }

  @Override
  public synchronized void recordUsage(
      final UsageScope scope, final long requestCount, final long tokenCount) {
    if (scope == null) {
      return;
    }
    if (requestCount <= 0 && tokenCount <= 0) {
      return;
    }
    final UsageSnapshot snapshot = loadSnapshot();
    applyUsage(snapshot, scope, requestCount, tokenCount);
    persistSnapshot(snapshot);
  }

  public synchronized UsageSnapshot getSnapshot() {
    return loadSnapshot();
  }

  private void applyUsage(
      final UsageSnapshot snapshot,
      final UsageScope scope,
      final long requestCount,
      final long tokenCount) {
    final LocalDate currentDate = LocalDate.now(clock);
    final String dayKey = DAY_FORMAT.format(currentDate);
    final String monthKey = MONTH_FORMAT.format(currentDate);
    final UsageSnapshot.ScopeUsage scopeUsage = snapshot.getOrCreateScope(scope.key());
    final UsageRecord dayRecord = scopeUsage.getOrCreateDay(dayKey);
    final UsageRecord monthRecord = scopeUsage.getOrCreateMonth(monthKey);
    dayRecord.add(requestCount, tokenCount);
    monthRecord.add(requestCount, tokenCount);
  }

  private UsageSnapshot loadSnapshot() {
    try {
      return withFileLock(this::readSnapshot);
    } catch (JacksonException e) {
      logWarn("Usage file is corrupted. Resetting usage data: " + usageFile);
      tryBackupCorruptedFile();
      return new UsageSnapshot();
    } catch (IOException e) {
      logWarn("Failed to read usage file. Resetting usage data: " + usageFile);
      return new UsageSnapshot();
    }
  }

  private UsageSnapshot readSnapshot() throws IOException {
    if (!Files.exists(usageFile)) {
      return new UsageSnapshot();
    }
    final String snapshotJson = Files.readString(usageFile, StandardCharsets.UTF_8);
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return new UsageSnapshot();
    }
    return objectMapper.readValue(snapshotJson, UsageSnapshot.class);
  }

  private void persistSnapshot(final UsageSnapshot snapshot) {
    try {
      withFileLock(
          () -> {
            writeSnapshotAtomically(snapshot);
            return null;
          });
    } catch (IOException e) {
      logWarn("Failed to persist usage file: " + usageFile);
    }
  }

  private void writeSnapshotAtomically(final UsageSnapshot snapshot) throws IOException {
    final String snapshotJson = objectMapper.writeValueAsString(snapshot);
    final Path temporaryUsagePath = resolveUsageFileSibling(TEMP_SUFFIX);
    Files.writeString(temporaryUsagePath, snapshotJson, StandardCharsets.UTF_8);
    try {
      Files.move(
          temporaryUsagePath,
          usageFile,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      // Fallback for filesystems that do not support atomic move.
      Files.move(temporaryUsagePath, usageFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void tryBackupCorruptedFile() {
    try {
      withFileLock(
          () -> {
            if (!Files.exists(usageFile)) {
              return null;
            }
            final Path corruptedBackupPath = resolveUsageFileSibling(CORRUPTED_SUFFIX);
            Files.move(usageFile, corruptedBackupPath, StandardCopyOption.REPLACE_EXISTING);
            return null;
          });
    } catch (IOException e) {
      logWarn("Failed to backup corrupted usage file: " + usageFile);
    }
  }

  private <T> T withFileLock(final LockedOperation<T> operation) throws IOException {
    final Path parentDirectory = usageFile.getParent();
    if (parentDirectory != null) {
      Files.createDirectories(parentDirectory);
    }
    final Path lockFilePath = resolveUsageFileSibling(LOCK_SUFFIX);
    try (FileChannel lockChannel =
        FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      lockChannel.lock();
      return operation.execute();
    }
  }

  private Path resolveUsageFileSibling(final String suffix) {
    return usageFile.resolveSibling(usageFile.getFileName() + suffix);
  }

  private void logWarn(final String message) {
    Logger.warn(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", message));
  }

  private interface LockedOperation<T> {

    T execute() throws IOException;
  }
}
