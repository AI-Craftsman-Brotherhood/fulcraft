package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class MavenClasspathResolver {

  private static final int TIMEOUT_SECONDS = 60;

  private static final String TOOL_MAVEN = "maven";

  private static final String MVNW = "mvnw";

  private static final String MVNW_CMD = "mvnw.cmd";

  private static final String MVNW_BAT = "mvnw.bat";

  private static final String OUTPUT_DIR = "target/ful";

  private static final String OUTPUT_FILE = "mvn-classpath.txt";

  private MavenClasspathResolver() {}

  public static List<Path> resolveCompileClasspath(final Path projectRoot) {
    return resolveCompileClasspathAttempt(projectRoot).safeEntries();
  }

  public static ClasspathAttemptResult resolveCompileClasspathAttempt(final Path projectRoot) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) {
      return failure("Invalid project root: " + projectRoot);
    }
    final Path mvnw = findMavenWrapper(projectRoot);
    if (mvnw == null && !isMavenAvailable()) {
      return failure("Maven wrapper or mvn not found");
    }
    final Path outputFile = projectRoot.resolve(OUTPUT_DIR).resolve(OUTPUT_FILE);
    try {
      final Path parent = outputFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      return failure("Failed to create output path: " + e.getMessage());
    }
    int exitCode = -1;
    try {
      exitCode = executeMavenDependencyBuild(projectRoot, mvnw, outputFile);
      if (exitCode != 0) {
        return failure(exitCode, "Maven classpath resolution failed with exit code " + exitCode);
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return failure(exitCode, "Maven execution failed: " + e.getMessage());
    }
    try {
      final List<Path> classpathEntries = parseClasspathFile(outputFile);
      if (classpathEntries.isEmpty()) {
        return failure(exitCode, "Maven classpath resolution returned empty output");
      }
      return success(classpathEntries, exitCode);
    } catch (IOException e) {
      return failure(exitCode, "Failed to read classpath file: " + e.getMessage());
    }
  }

  private static int executeMavenDependencyBuild(
      final Path projectRoot, final Path mvnw, final Path outputFile)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    if (mvnw != null) {
      addWrapperCommand(command, mvnw);
    } else {
      command.add("mvn");
    }
    command.add("-q");
    command.add("-DincludeScope=compile");
    command.add("-Dmdep.outputAbsoluteArtifactFilename=true");
    command.add("-Dmdep.outputFile=" + outputFile.toAbsolutePath());
    command.add("dependency:build-classpath");
    final ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(projectRoot.toFile());
    pb.redirectErrorStream(true);
    final Process process = pb.start();
    try (var input = process.getInputStream()) {
      // Consume output to prevent blocking.
      input.transferTo(OutputStream.nullOutputStream());
    }
    final boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!completed) {
      process.destroyForcibly();
      throw new IOException(
          MessageSource.getMessage(
              "infra.common.error.message", "Maven classpath resolution timed out"));
    }
    return process.exitValue();
  }

  private static List<Path> parseClasspathFile(final Path outputFile) throws IOException {
    final List<Path> classpathEntries = new ArrayList<>();
    if (Files.isRegularFile(outputFile)) {
      final String raw = Files.readString(outputFile);
      for (final String part : raw.split(java.io.File.pathSeparator)) {
        final String trimmed = part.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        final Path entry = Path.of(trimmed);
        if ((Files.isRegularFile(entry) && entry.toString().endsWith(".jar"))
            || Files.isDirectory(entry)) {
          classpathEntries.add(entry);
        }
      }
    }
    return classpathEntries;
  }

  private static Path findMavenWrapper(final Path projectRoot) {
    final Path mvnw = projectRoot.resolve(MVNW);
    if (Files.isExecutable(mvnw)) {
      return mvnw;
    }
    final Path mvnwCmd = projectRoot.resolve(MVNW_CMD);
    if (Files.isRegularFile(mvnwCmd)) {
      return mvnwCmd;
    }
    final Path mvnwBat = projectRoot.resolve(MVNW_BAT);
    if (Files.isRegularFile(mvnwBat)) {
      return mvnwBat;
    }
    return null;
  }

  private static void addWrapperCommand(final List<String> command, final Path mvnw) {
    final String wrapper = mvnw.toAbsolutePath().toString();
    final String lower = wrapper.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".cmd") || lower.endsWith(".bat")) {
      command.add("cmd");
      command.add("/c");
      command.add(wrapper);
    } else {
      command.add(wrapper);
    }
  }

  private static boolean isMavenAvailable() {
    final ProcessBuilder pb = new ProcessBuilder("mvn", "-v");
    pb.redirectErrorStream(true);
    try {
      final Process process = pb.start();
      final boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!completed) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return false;
    }
  }

  private static ClasspathAttemptResult success(final List<Path> entries, final Integer exitCode) {
    return new ClasspathAttemptResult(TOOL_MAVEN, entries, true, exitCode, null);
  }

  private static ClasspathAttemptResult failure(final String message) {
    return failure(null, message);
  }

  private static ClasspathAttemptResult failure(final Integer exitCode, final String message) {
    return new ClasspathAttemptResult(TOOL_MAVEN, List.of(), false, exitCode, message);
  }
}
