package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.support.ConfigBuilder;
import com.craftsmanbro.fulcraft.support.ProjectFixtures;
import com.craftsmanbro.fulcraft.support.RunArtifacts;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for the {@code ful run} pipeline, which has its own analyze→report wiring
 * distinct from the standalone {@code report} command.
 */
@DisplayName("ful run (end-to-end CLI)")
class CliRunPipelineE2eTest extends E2eTestBase {

  @Test
  @DisplayName("run --steps analyze,report produces analysis shards and report artifacts")
  void runAnalyzeReportProducesArtifacts() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().writeTo(workspace);

    final int exitCode =
        runCli("-c", config.toString(), "run", "--steps", "analyze,report", workspace.toString());

    assertThat(exitCode).isZero();
    final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());
    assertThat(artifacts.classFqns()).contains("com.demo.Greeter", "com.demo.Point");
    assertThat(artifacts.reportDir().resolve("analysis_visual.html")).exists();
  }

  @Test
  @DisplayName("run --steps analyze produces analysis shards without a report")
  void runAnalyzeOnlyProducesShards() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().writeTo(workspace);

    final int exitCode =
        runCli("-c", config.toString(), "run", "--steps", "analyze", workspace.toString());

    assertThat(exitCode).isZero();
    final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());
    assertThat(artifacts.classFqns()).contains("com.demo.Greeter");
  }
}
