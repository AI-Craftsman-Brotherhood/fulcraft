package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig.PreprocessConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Executes delombok to expand Lombok annotations. */
public class DelombokExecutor {

  // 5 minutes
  private static final int TIMEOUT_SECONDS = 300;

  /** Result of delombok execution. */
  public record Result(boolean success, Path outputDir, String errorMessage, long durationMs) {}

  /**
   * Executes delombok on the given source roots.
   *
   * @param projectRoot Project root directory
   * @param sourceRoots Source directories to process
   * @param workDir Output directory for processed sources
   * @param config Preprocessing configuration
   * @param logFile Path to write delombok output log
   * @return Execution result
   */
  public Result execute(
      final Path projectRoot,
      final List<Path> sourceRoots,
      final Path workDir,
      final PreprocessConfig config,
      final Path logFile) {
    final long startTime = System.currentTimeMillis();
    try {
      // Find lombok jar
      final Path lombokJar = findLombokJar(projectRoot, config);
      if (lombokJar == null) {
        return new Result(
            false,
            null,
            MessageSource.getMessage("analysis.delombok.error.lombok_jar_not_found"),
            System.currentTimeMillis() - startTime);
      }
      Logger.info(MessageSource.getMessage("analysis.delombok.using_lombok", lombokJar));
      // Prepare output directory
      if (Files.exists(workDir) && config.getCleanWorkDir()) {
        deleteDirectory(workDir);
      }
      Files.createDirectories(workDir);
      // Build command
      final List<String> command = buildCommand(lombokJar, sourceRoots, workDir);
      Logger.debug(
          MessageSource.getMessage("analysis.delombok.debug.command", String.join(" ", command)));
      // Execute
      final ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(projectRoot.toFile());
      pb.redirectErrorStream(true);
      pb.redirectOutput(logFile.toFile());
      final Process process = pb.start();
      final boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final int exitCode = completed ? process.exitValue() : -1;
      Logger.debug(MessageSource.getMessage("analysis.delombok.debug.log_written", logFile));
      final long duration = System.currentTimeMillis() - startTime;
      if (!completed) {
        process.destroyForcibly();
        return new Result(
            false,
            workDir,
            MessageSource.getMessage("analysis.delombok.error.timeout", TIMEOUT_SECONDS),
            duration);
      }
      if (exitCode != 0) {
        return new Result(
            false,
            workDir,
            MessageSource.getMessage("analysis.delombok.error.exit_code", exitCode),
            duration);
      }
      // Verify output exists
      if (!hasOutputFiles(workDir)) {
        return new Result(
            false,
            workDir,
            MessageSource.getMessage("analysis.delombok.error.no_output"),
            duration);
      }
      Logger.info(MessageSource.getMessage("analysis.delombok.completed", duration));
      return new Result(true, workDir, null, duration);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      final long duration = System.currentTimeMillis() - startTime;
      Logger.error(MessageSource.getMessage("analysis.delombok.error", e.getMessage()));
      return new Result(false, workDir, e.getMessage(), duration);
    }
  }

  private List<String> buildCommand(
      final Path lombokJar, final List<Path> sourceRoots, final Path workDir) {
    final List<String> command = new ArrayList<>();
    command.add("java");
    command.add("-jar");
    command.add(lombokJar.toString());
    command.add("delombok");
    command.add("-d");
    command.add(workDir.toString());
    // Add source roots
    for (final Path sourceRoot : sourceRoots) {
      command.add(sourceRoot.toString());
    }
    return command;
  }

  /** Finds lombok.jar using multiple strategies. */
  private Path findLombokJar(final Path projectRoot, final PreprocessConfig config) {
    // 1. Check config-specified path
    if (config.getDelombok() != null && config.getDelombok().getLombokJarPath() != null) {
      final Path configPath = Path.of(config.getDelombok().getLombokJarPath());
      if (Files.exists(configPath)) {
        return configPath;
      }
      // Try relative to project root
      final Path relativePath = projectRoot.resolve(configPath);
      if (Files.exists(relativePath)) {
        return relativePath;
      }
    }
    // 2. Try project local paths
    final List<Path> localPaths =
        List.of(
            projectRoot.resolve("lombok.jar"),
            projectRoot.resolve("lib/lombok.jar"),
            projectRoot.resolve("libs/lombok.jar"));
    for (final Path path : localPaths) {
      if (Files.exists(path)) {
        return path;
      }
    }
    // 3. Search Gradle cache
    final Path gradleCache = findInGradleCache();
    if (gradleCache != null) {
      return gradleCache;
    }
    // 4. Search Maven cache
    final Path mavenCache = findInMavenCache();
    if (mavenCache != null) {
      return mavenCache;
    }
    return null;
  }

  private Path findInGradleCache() {
    final Path gradleCacheDir =
        Path.of(
            System.getProperty("user.home"),
            ".gradle",
            "caches",
            "modules-2",
            "files-2.1",
            "org.projectlombok",
            "lombok");
    if (!Files.exists(gradleCacheDir)) {
      return null;
    }
    try (Stream<Path> walk = Files.walk(gradleCacheDir, 4)) {
      return walk.filter(this::isLombokJar)
          .sorted(Comparator.comparing(Path::toString))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private Path findInMavenCache() {
    final Path mavenRepo =
        Path.of(
            System.getProperty("user.home"), ".m2", "repository", "org", "projectlombok", "lombok");
    if (!Files.exists(mavenRepo)) {
      return null;
    }
    try (Stream<Path> walk = Files.walk(mavenRepo, 3)) {
      return walk.filter(this::isLombokJar)
          .sorted(Comparator.comparing(Path::toString))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private boolean isLombokJar(final Path path) {
    final Path fileName = path.getFileName();
    return fileName != null
        && fileName.toString().startsWith("lombok-")
        && fileName.toString().endsWith(".jar");
  }

  private boolean hasOutputFiles(final Path workDir) {
    try (Stream<Path> walk = Files.walk(workDir)) {
      return walk.anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      return false;
    }
  }

  private void deleteDirectory(final Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      // Reverse order for deletion
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  Logger.debug(
                      MessageSource.getMessage("analysis.delombok.debug.delete_failed", p));
                }
              });
    }
  }
}
