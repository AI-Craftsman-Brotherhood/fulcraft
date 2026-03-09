package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ClasspathResolver}. */
class ClasspathResolverTest {

  @TempDir Path tempDir;

  @Test
  void portResolveCompileClasspath_whenGradleSucceeds_returnsGradleEntries() throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("gradle-success-via-port"));
    Path expectedJar = createJar(projectRoot, "lib/gradle-dep-port.jar");
    createGradleWrapperSuccess(projectRoot, expectedJar);

    ClasspathResolutionResult result =
        ClasspathResolver.port()
            .resolveCompileClasspath(projectRoot, configWithBuildTool("gradle"));

    assertEquals("gradle", result.getSelectedTool());
    assertEquals(List.of(expectedJar), result.getEntries());
    assertFalse(result.isEmpty());
  }

  @Test
  void resolveCompileClasspath_whenMavenSucceeds_returnsMavenEntries() throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("maven-success"));
    Path expectedJar = createJar(projectRoot, "lib/maven-dep.jar");
    createMavenWrapperSuccess(projectRoot, expectedJar);

    ClasspathResolutionResult result =
        ClasspathResolver.resolveCompileClasspath(projectRoot, configWithBuildTool("maven"));

    assertEquals("maven", result.getSelectedTool());
    assertEquals(List.of(expectedJar), result.getEntries());
    assertFalse(result.isEmpty());
    assertEquals(1, result.getAttempts().size());
    ClasspathResolutionResult.Attempt attempt = result.getAttempts().get(0);
    assertEquals("maven", attempt.tool());
    assertTrue(attempt.success());
    assertEquals(0, attempt.exitCode());
    assertNull(attempt.message());
  }

  @Test
  void resolveCompileClasspath_whenMavenFailsAndGradleSucceeds_fallsBackToGradle()
      throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("maven-fail-gradle-success"));
    Path expectedJar = createJar(projectRoot, "lib/gradle-dep.jar");
    createMavenWrapperFailure(projectRoot, 23);
    createGradleWrapperSuccess(projectRoot, expectedJar);

    ClasspathResolutionResult result =
        ClasspathResolver.resolveCompileClasspath(projectRoot, configWithBuildTool("maven"));

    assertEquals("gradle", result.getSelectedTool());
    assertEquals(List.of(expectedJar), result.getEntries());
    assertFalse(result.isEmpty());
    assertEquals(2, result.getAttempts().size());

    ClasspathResolutionResult.Attempt mavenAttempt = result.getAttempts().get(0);
    assertEquals("maven", mavenAttempt.tool());
    assertFalse(mavenAttempt.success());
    assertEquals(23, mavenAttempt.exitCode());

    ClasspathResolutionResult.Attempt gradleAttempt = result.getAttempts().get(1);
    assertEquals("gradle", gradleAttempt.tool());
    assertTrue(gradleAttempt.success());
    assertEquals(0, gradleAttempt.exitCode());
  }

  @Test
  void resolveCompileClasspath_whenGradleSucceeds_returnsGradleEntries() throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("gradle-success"));
    Path expectedJar = createJar(projectRoot, "lib/gradle-dep.jar");
    createGradleWrapperSuccess(projectRoot, expectedJar);

    ClasspathResolutionResult result =
        ClasspathResolver.resolveCompileClasspath(projectRoot, configWithBuildTool("gradle"));

    assertEquals("gradle", result.getSelectedTool());
    assertEquals(List.of(expectedJar), result.getEntries());
    assertFalse(result.isEmpty());
    assertEquals(1, result.getAttempts().size());
    ClasspathResolutionResult.Attempt attempt = result.getAttempts().get(0);
    assertEquals("gradle", attempt.tool());
    assertTrue(attempt.success());
    assertEquals(0, attempt.exitCode());
    assertNull(attempt.message());
  }

  @Test
  void resolveCompileClasspath_whenGradleFailsAndMavenSucceeds_fallsBackToMaven()
      throws IOException {
    Path projectRoot = Files.createDirectories(tempDir.resolve("gradle-fail-maven-success"));
    Path expectedJar = createJar(projectRoot, "lib/maven-dep.jar");
    createGradleWrapperFailure(projectRoot, 12);
    createMavenWrapperSuccess(projectRoot, expectedJar);

    ClasspathResolutionResult result =
        ClasspathResolver.resolveCompileClasspath(projectRoot, configWithBuildTool("gradle"));

    assertEquals("maven", result.getSelectedTool());
    assertEquals(List.of(expectedJar), result.getEntries());
    assertFalse(result.isEmpty());
    assertEquals(2, result.getAttempts().size());

    ClasspathResolutionResult.Attempt gradleAttempt = result.getAttempts().get(0);
    assertEquals("gradle", gradleAttempt.tool());
    assertFalse(gradleAttempt.success());
    assertEquals(12, gradleAttempt.exitCode());

    ClasspathResolutionResult.Attempt mavenAttempt = result.getAttempts().get(1);
    assertEquals("maven", mavenAttempt.tool());
    assertTrue(mavenAttempt.success());
    assertEquals(0, mavenAttempt.exitCode());
  }

  @Test
  void resolveCompileClasspath_whenToolUnknownAndBothAttemptsFail_returnsUnknownTool() {
    ClasspathResolutionResult result = ClasspathResolver.resolveCompileClasspath(null, null);

    assertTrue(result.isEmpty());
    assertEquals("unknown", result.getSelectedTool());
    assertEquals(2, result.getAttempts().size());
    assertEquals("gradle", result.getAttempts().get(0).tool());
    assertFalse(result.getAttempts().get(0).success());
    assertEquals("maven", result.getAttempts().get(1).tool());
    assertFalse(result.getAttempts().get(1).success());
  }

  private static Config configWithBuildTool(String buildTool) {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setBuildTool(buildTool);
    config.setProject(project);
    return config;
  }

  private static Path createJar(Path projectRoot, String relativePath) throws IOException {
    Path jar = projectRoot.resolve(relativePath).toAbsolutePath();
    Path parent = jar.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(jar, "fake-jar");
    return jar;
  }

  private static void createMavenWrapperSuccess(Path projectRoot, Path classpathEntry)
      throws IOException {
    String script =
        "#!/usr/bin/env bash\n"
            + "set -eu\n"
            + "mkdir -p target/ful\n"
            + "printf '%s' '"
            + classpathEntry
            + "' > target/ful/mvn-classpath.txt\n"
            + "exit 0\n";
    writeExecutable(projectRoot.resolve("mvnw"), script);
  }

  private static void createMavenWrapperFailure(Path projectRoot, int exitCode) throws IOException {
    String script = "#!/usr/bin/env bash\n" + "exit " + exitCode + "\n";
    writeExecutable(projectRoot.resolve("mvnw"), script);
  }

  private static void createGradleWrapperSuccess(Path projectRoot, Path classpathEntry)
      throws IOException {
    String script =
        "#!/usr/bin/env bash\n"
            + "set -eu\n"
            + "if [ \"${1:-}\" = \"dependencies\" ]; then\n"
            + "  exit 0\n"
            + "fi\n"
            + "printf '%s\\n' '"
            + classpathEntry
            + "'\n"
            + "exit 0\n";
    writeExecutable(projectRoot.resolve("gradlew"), script);
  }

  private static void createGradleWrapperFailure(Path projectRoot, int exitCode)
      throws IOException {
    String script = "#!/usr/bin/env bash\n" + "exit " + exitCode + "\n";
    writeExecutable(projectRoot.resolve("gradlew"), script);
  }

  private static void writeExecutable(Path scriptPath, String content) throws IOException {
    Files.writeString(scriptPath, content);
    if (!scriptPath.toFile().setExecutable(true)) {
      throw new IOException("Failed to mark script as executable: " + scriptPath);
    }
  }
}
