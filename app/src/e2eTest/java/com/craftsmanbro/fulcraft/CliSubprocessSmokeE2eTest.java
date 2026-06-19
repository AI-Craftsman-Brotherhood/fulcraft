package com.craftsmanbro.fulcraft;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.craftsmanbro.fulcraft.support.ConfigBuilder;
import com.craftsmanbro.fulcraft.support.ProjectFixtures;
import com.craftsmanbro.fulcraft.support.RunArtifacts;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Black-box smoke test that runs the packaged fat JAR as a real subprocess. Unlike the in-process
 * tests (which call {@link Main#run}), this exercises {@code System.exit}, the JAR manifest, and
 * everything that only works once packaged — SPI service files, resource bundles, and HTML
 * templates. The Gradle {@code e2eTest} task builds the shadow JAR and passes its path via the
 * {@code ful.jar} system property.
 */
@DisplayName("ful packaged JAR (black-box subprocess)")
class CliSubprocessSmokeE2eTest extends E2eTestBase {

  @Test
  @DisplayName("the packaged JAR analyzes a project (exit 0, shards written)")
  void packagedJarAnalyzesProject() throws Exception {
    // ful.jar is set by the e2eTest Gradle task (after building the shadow JAR). When the class is
    // run directly from an IDE without that task, skip rather than fail.
    final String jarPath = System.getProperty("ful.jar");
    assumeTrue(jarPath != null && !jarPath.isBlank(), "ful.jar system property not set");
    final Path jar = Path.of(jarPath);
    assertThat(jar).exists();

    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().writeTo(workspace);

    final String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final Process process =
        new ProcessBuilder(
                javaBin,
                "-Dful.runsRoot=" + runsRoot().toAbsolutePath(),
                "-jar",
                jar.toString(),
                "-c",
                config.toString(),
                "analyze",
                workspace.toString())
            // redirectErrorStream merges stderr into stdout so the single readAllBytes() below
            // drains both; removing it would deadlock the read-before-waitFor sequence.
            .redirectErrorStream(true)
            .start();
    try {
      final String output = new String(process.getInputStream().readAllBytes(), UTF_8);
      // 180s is intentionally generous; analyze is static (no LLM) and finishes in seconds.
      final boolean finished = process.waitFor(180, TimeUnit.SECONDS);
      assertThat(finished).as("subprocess should finish; output:%n%s", output).isTrue();
      assertThat(process.exitValue()).as("exit code; output:%n%s", output).isZero();

      final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());
      assertThat(artifacts.classFqns()).contains("com.demo.Point", "com.demo.Color");
    } finally {
      // Ensure no subprocess is orphaned if an assertion/IO error exits the block early.
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }
}
