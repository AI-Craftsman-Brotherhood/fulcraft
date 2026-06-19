package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.support.ConfigBuilder;
import com.craftsmanbro.fulcraft.support.ProjectFixtures;
import com.craftsmanbro.fulcraft.support.RunArtifacts;
import com.craftsmanbro.fulcraft.support.VisualReport;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for {@code ful report}. Verifies the visual report HTML and per-class HTML are
 * produced, and regression-guards records/enums extraction (#2), project-root-relative file paths
 * (#3), and no duplicate methods from the composite merge (#4) — all observed via the CLI output.
 */
@DisplayName("ful report (end-to-end CLI)")
class CliReportE2eTest extends E2eTestBase {

  @Test
  @DisplayName("report generates visual + per-class HTML and consistent analysis shards")
  void reportGeneratesHtmlAndConsistentShards() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().writeTo(workspace);

    final int exitCode = runCli("-c", config.toString(), "report", workspace.toString());

    assertThat(exitCode).isZero();
    final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());

    // Report artifacts.
    assertThat(artifacts.reportDir().resolve("report.md")).exists();
    final Path visual = artifacts.reportDir().resolve("analysis_visual.html");
    assertThat(visual).exists();
    assertThat(VisualReport.readData(visual).toString()).contains("com.demo");

    // Per-class HTML lives in a single source-aligned tree (record included) — guards #3.
    assertThat(artifacts.reportDir().resolve("src/main/java/com/demo/Point.html")).exists();
    assertThat(artifacts.reportDir().resolve("src/main/java/com/demo/Color.html")).exists();

    // #2: records and enums are present in the analysis.
    assertThat(artifacts.classFqns()).contains("com.demo.Point", "com.demo.Color");

    // #3: file paths are project-root-relative across engines.
    artifacts
        .filePathByClass()
        .forEach(
            (fqn, filePath) ->
                assertThat(filePath).as("file_path of %s", fqn).startsWith("src/main/java/"));

    // #4: the composite merge does not duplicate methods.
    artifacts
        .methodNamesByClass()
        .forEach(
            (fqn, methods) -> assertThat(methods).as("methods of %s", fqn).doesNotHaveDuplicates());
  }
}
