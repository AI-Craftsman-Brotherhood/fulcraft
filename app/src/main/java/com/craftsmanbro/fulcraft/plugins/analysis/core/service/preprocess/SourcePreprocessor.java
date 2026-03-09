package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig.PreprocessConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Orchestrates source preprocessing (delombok, APT). */
public class SourcePreprocessor {

  /** Preprocessing status. */
  public enum Status {
    SUCCESS,
    SKIPPED,
    FAILED_FALLBACK,
    FAILED
  }

  /** Result of preprocessing. */
  public static class Result {

    private final Status status;

    private final List<Path> sourceRootsBefore;

    private final List<Path> sourceRootsAfter;

    private final String toolUsed;

    private final String failureReason;

    private final long durationMs;

    public Result(
        final Status status,
        final List<Path> before,
        final List<Path> after,
        final String toolUsed,
        final String failureReason,
        final long durationMs) {
      this.status = status;
      this.sourceRootsBefore = before;
      this.sourceRootsAfter = after;
      this.toolUsed = toolUsed;
      this.failureReason = failureReason;
      this.durationMs = durationMs;
    }

    public Status getStatus() {
      return status;
    }

    public List<Path> getSourceRootsBefore() {
      return Collections.unmodifiableList(sourceRootsBefore);
    }

    public List<Path> getSourceRootsAfter() {
      return Collections.unmodifiableList(sourceRootsAfter);
    }

    public String getToolUsed() {
      return toolUsed;
    }

    public String getFailureReason() {
      return failureReason;
    }

    public long getDurationMs() {
      return durationMs;
    }

    public boolean isSuccess() {
      return status == Status.SUCCESS;
    }

    public boolean shouldUsePreprocessed() {
      return status == Status.SUCCESS;
    }

    /** Converts result to map for JSON output. */
    public Map<String, Object> toMap(final String mode, final Path workDir) {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("mode", mode);
      map.put("tool_used", toolUsed);
      map.put("status", status.name());
      map.put("work_dir", workDir.toString());
      map.put("source_roots_before", sourceRootsBefore.stream().map(Path::toString).toList());
      map.put("source_roots_after", sourceRootsAfter.stream().map(Path::toString).toList());
      map.put("duration_ms", durationMs);
      if (failureReason != null) {
        map.put("failure_reason", failureReason);
      }
      return map;
    }
  }

  private final LombokDetector lombokDetector = new LombokDetector();

  private final DelombokExecutor delombokExecutor = new DelombokExecutor();

  /**
   * Preprocesses sources if needed based on configuration.
   *
   * @param projectRoot Project root directory
   * @param sourceRoots Original source roots
   * @param config Configuration
   * @return Preprocessing result
   */
  public Result preprocess(
      final Path projectRoot,
      final List<Path> sourceRoots,
      final Config config,
      final Path outputDir) {
    final PreprocessConfig preprocessConfig =
        config.getAnalysis() != null ? config.getAnalysis().getPreprocess() : null;
    // Check if preprocessing is disabled
    if (preprocessConfig == null || preprocessConfig.isOffMode()) {
      Logger.debug(MessageSource.getMessage("analysis.preprocess.debug.off"));
      return new Result(Status.SKIPPED, sourceRoots, sourceRoots, null, null, 0);
    }
    final long startTime = System.currentTimeMillis();
    final String mode = preprocessConfig.getMode();
    final Path workDir = projectRoot.resolve(preprocessConfig.getWorkDir());
    final Path logFile =
        outputDir != null
            ? outputDir.resolve("preprocess_delombok.log")
            : projectRoot.resolve("reports/preprocess_delombok.log");
    // Ensure reports dir exists
    try {
      final Path logParent = logFile.getParent();
      if (logParent != null) {
        Files.createDirectories(logParent);
      }
    } catch (IOException e) {
      Logger.warn(MessageSource.getMessage("analysis.preprocess.log_dir_create_failed"));
    }
    if (preprocessConfig.isDelombokTool() && !isDelombokEnabled(preprocessConfig)) {
      Logger.debug(MessageSource.getMessage("analysis.preprocess.debug.delombok_disabled"));
      return new Result(
          Status.SKIPPED,
          sourceRoots,
          sourceRoots,
          null,
          null,
          System.currentTimeMillis() - startTime);
    }
    // AUTO mode: check if Lombok is used
    if (preprocessConfig.isAutoMode() && !lombokDetector.detectLombok(projectRoot, sourceRoots)) {
      Logger.debug(MessageSource.getMessage("analysis.preprocess.debug.no_lombok_detected"));
      return new Result(
          Status.SKIPPED,
          sourceRoots,
          sourceRoots,
          null,
          null,
          System.currentTimeMillis() - startTime);
    }
    // Execute delombok
    if (preprocessConfig.isDelombokTool()) {
      return executeDelombok(
          projectRoot, sourceRoots, workDir, preprocessConfig, logFile, mode, startTime);
    }
    return new Result(
        Status.SKIPPED,
        sourceRoots,
        sourceRoots,
        null,
        null,
        System.currentTimeMillis() - startTime);
  }

  private Result executeDelombok(
      final Path projectRoot,
      final List<Path> sourceRoots,
      final Path workDir,
      final PreprocessConfig config,
      final Path logFile,
      final String mode,
      final long startTime) {
    Logger.info(MessageSource.getMessage("analysis.preprocess.delombok.executing"));
    final DelombokExecutor.Result delombokResult =
        delombokExecutor.execute(projectRoot, sourceRoots, workDir, config, logFile);
    final long duration = System.currentTimeMillis() - startTime;
    if (delombokResult.success()) {
      // Build preprocessed source roots
      final List<Path> preprocessedRoots = buildPreprocessedRoots(workDir, sourceRoots, config);
      Logger.info(MessageSource.getMessage("analysis.preprocess.delombok.success"));
      return new Result(Status.SUCCESS, sourceRoots, preprocessedRoots, "DELOMBOK", null, duration);
    }
    // Handle failure
    final String errorMessage = delombokResult.errorMessage();
    Logger.warn(MessageSource.getMessage("analysis.preprocess.delombok.failed", errorMessage));
    if ("STRICT".equalsIgnoreCase(mode)) {
      Logger.error(MessageSource.getMessage("analysis.preprocess.strict_failure"));
      return new Result(Status.FAILED, sourceRoots, sourceRoots, "DELOMBOK", errorMessage, duration);
    }
    // AUTO mode - fallback to original sources
    Logger.warn(MessageSource.getMessage("analysis.preprocess.auto_fallback"));
    return new Result(
        Status.FAILED_FALLBACK, sourceRoots, sourceRoots, "DELOMBOK", errorMessage, duration);
  }

  private List<Path> buildPreprocessedRoots(
      final Path workDir, final List<Path> originalRoots, final PreprocessConfig config) {
    final List<Path> roots = new ArrayList<>();
    // If delombok output is in workDir directly
    if (Files.exists(workDir) && hasJavaFiles(workDir)) {
      roots.add(workDir);
    }
    // Check for subdirectories matching original structure
    for (final Path original : originalRoots) {
      final Path dirName = original.getFileName();
      if (dirName == null) {
        continue;
      }
      final Path preprocessed = workDir.resolve(dirName);
      if (Files.exists(preprocessed)) {
        roots.add(preprocessed);
      }
    }
    // Exclude generated/ if configured
    if (!config.getIncludeGenerated()) {
      roots.removeIf(p -> p.toString().contains("generated"));
    }
    return roots.isEmpty() ? List.of(workDir) : roots;
  }

  private boolean isDelombokEnabled(final PreprocessConfig config) {
    return config.getDelombok() == null || config.getDelombok().getEnabled();
  }

  private boolean hasJavaFiles(final Path dir) {
    try (var walk = Files.walk(dir, 3)) {
      return walk
          .sorted(Comparator.comparing(Path::toString))
          .anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      return false;
    }
  }
}
