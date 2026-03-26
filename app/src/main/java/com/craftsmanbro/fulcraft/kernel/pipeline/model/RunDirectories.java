package com.craftsmanbro.fulcraft.kernel.pipeline.model;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves run-scoped root directories.
 *
 * <p>All artifacts for a run should be written under {@code runsRoot/runId}.
 */
public final class RunDirectories {

  private final Path runsRoot;

  private final Path runRoot;

  private RunDirectories(final Path runsRoot, final Path runRoot) {
    this.runsRoot = runsRoot;
    this.runRoot = runRoot;
  }

  public static RunDirectories from(final RunContext context) {
    Objects.requireNonNull(
        context, MessageSource.getMessage("kernel.run_directories.error.context_null"));
    return from(context.getConfig(), context.getProjectRoot(), context.getRunId());
  }

  public static RunDirectories from(
      final Config config, final Path projectRoot, final String runId) {
    final Path resolvedRunsRoot = resolveRunsRoot(config, projectRoot);
    return new RunDirectories(resolvedRunsRoot, resolveRunRoot(resolvedRunsRoot, runId));
  }

  public static Path resolveRunsRoot(final Config config, final Path projectRoot) {
    Path base = Path.of(resolveConfiguredLogsRoot(config));
    if (base.isAbsolute()) {
      return base.normalize();
    }

    if (projectRoot != null) {
      return projectRoot.resolve(base).normalize();
    }

    return base.toAbsolutePath().normalize();
  }

  public static Path resolveRunRoot(
      final Config config, final Path projectRoot, final String runId) {
    return resolveRunRoot(resolveRunsRoot(config, projectRoot), runId);
  }

  private static String resolveConfiguredLogsRoot(final Config config) {
    if (config != null && config.getExecution() != null) {
      return config.getExecution().getEffectiveLogsRoot();
    }
    return Config.ExecutionConfig.DEFAULT_LOGS_ROOT;
  }

  private static Path resolveRunRoot(final Path runsRoot, final String runId) {
    return runsRoot.resolve(runId);
  }

  public Path runsRoot() {
    return runsRoot;
  }

  public Path runRoot() {
    return runRoot;
  }
}
