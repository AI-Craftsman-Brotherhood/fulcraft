package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the compile classpath for a Gradle project by executing gradlew.
 *
 * <p>This class runs the Gradle wrapper to extract dependency JAR paths for use in type resolution
 * during static analysis.
 */
public final class GradleClasspathResolver {

  private static final int TIMEOUT_SECONDS = 60;

  private static final String TOOL_GRADLE = "gradle";

  private GradleClasspathResolver() {}

  /**
   * Resolves the compile classpath for the given Gradle project.
   *
   * @param projectRoot the root directory of the Gradle project
   * @return a list of JAR file paths, or empty list if resolution fails
   */
  public static List<Path> resolveCompileClasspath(final Path projectRoot) {
    return resolveCompileClasspathAttempt(projectRoot).safeEntries();
  }

  public static ClasspathAttemptResult resolveCompileClasspathAttempt(final Path projectRoot) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) {
      return failure("Invalid project root: " + projectRoot);
    }
    final Path gradleWrapper = findGradleWrapper(projectRoot);
    if (gradleWrapper == null) {
      return failure("Gradle wrapper not found");
    }
    try {
      return executeClasspathResolution(projectRoot, gradleWrapper);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failure("Gradle classpath resolution interrupted");
    } catch (IOException e) {
      return failure("Gradle classpath resolution failed: " + e.getMessage());
    }
  }

  private static Path findGradleWrapper(final Path projectRoot) {
    final Path gradlew = projectRoot.resolve("gradlew");
    if (Files.isExecutable(gradlew)) {
      return gradlew;
    }
    // Windows
    final Path gradlewBat = projectRoot.resolve("gradlew.bat");
    if (Files.isRegularFile(gradlewBat)) {
      return gradlewBat;
    }
    return null;
  }

  private static ClasspathAttemptResult executeClasspathResolution(
      final Path projectRoot, final Path gradleWrapper) throws IOException, InterruptedException {
    // Use dependencies task with configuration filter and parse output
    final ProcessBuilder processBuilder =
        new ProcessBuilder(
            gradleWrapper.toAbsolutePath().toString(),
            "dependencies",
            "--configuration",
            "compileClasspath",
            "-q");
    processBuilder.directory(projectRoot.toFile());
    processBuilder.redirectErrorStream(true);
    final Process process = processBuilder.start();
    // Consume the process output stream (required to prevent blocking)
    discardProcessOutput(process);
    if (!waitForProcess(process)) {
      return failure("Gradle classpath resolution timed out");
    }
    if (process.exitValue() != 0) {
      return failure(
          process.exitValue(),
          "Gradle classpath resolution failed with exit code " + process.exitValue());
    }
    // Try to get actual JAR paths from Gradle cache
    return resolveFromGradleCache(projectRoot, gradleWrapper);
  }

  /**
   * Resolves JAR paths from the Gradle cache based on the project's dependencies. This method
   * parses the Gradle configuration to find actual JAR files.
   */
  private static ClasspathAttemptResult resolveFromGradleCache(
      final Path projectRoot, final Path gradleWrapper) {
    final List<Path> classpathEntries = new ArrayList<>();
    try {
      final Path initScript = createInitScript(projectRoot);
      // Execute a one-liner Gradle task to print classpath
      final ProcessBuilder processBuilder =
          new ProcessBuilder(
              gradleWrapper.toAbsolutePath().toString(),
              "-q",
              "--console=plain",
              "-I",
              initScript.toAbsolutePath().toString(),
              "printClasspath");
      processBuilder.directory(projectRoot.toFile());
      processBuilder.redirectErrorStream(true);
      final Process process = processBuilder.start();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.endsWith(".jar") && !line.isEmpty()) {
            final Path classpathEntry = Path.of(line);
            if (Files.isRegularFile(classpathEntry)) {
              classpathEntries.add(classpathEntry);
            }
          }
        }
      }
      if (!waitForProcess(process)) {
        return failure("Gradle classpath resolution timed out");
      }
      final int exitCode = process.exitValue();
      if (exitCode != 0) {
        return failure(exitCode, "Gradle classpath resolution failed with exit code " + exitCode);
      }
      if (!classpathEntries.isEmpty()) {
        return success(classpathEntries, exitCode);
      }
      return failure(exitCode, "Gradle classpath resolution returned empty output");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failure("Gradle classpath resolution interrupted");
    } catch (IOException e) {
      return failure("Gradle classpath resolution failed: " + e.getMessage());
    }
  }

  /** Creates a Gradle init script that prints the compile classpath. */
  private static Path createInitScript(final Path projectRoot) throws IOException {
    final String script =
        """
        allprojects {
            task printClasspath {
                doLast {
                    configurations.findByName('compileClasspath')?.files?.each {
                        println it.absolutePath
                    }
                }
            }
        }
        """;
    final Path initScript =
        projectRoot.resolve("build").resolve("tmp").resolve("classpath-init.gradle");
    final Path parent = initScript.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(initScript, script);
    return initScript;
  }

  private static void discardProcessOutput(final Process process) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      reader.transferTo(Writer.nullWriter());
    }
  }

  private static boolean waitForProcess(final Process process) throws InterruptedException {
    final boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!completed) {
      process.destroyForcibly();
    }
    return completed;
  }

  private static ClasspathAttemptResult success(final List<Path> entries, final Integer exitCode) {
    return new ClasspathAttemptResult(TOOL_GRADLE, entries, true, exitCode, null);
  }

  private static ClasspathAttemptResult failure(final String message) {
    return failure(null, message);
  }

  private static ClasspathAttemptResult failure(final Integer exitCode, final String message) {
    return new ClasspathAttemptResult(TOOL_GRADLE, List.of(), false, exitCode, message);
  }
}
