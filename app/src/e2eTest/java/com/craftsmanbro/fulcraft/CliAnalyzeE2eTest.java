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
 * End-to-end smoke test for {@code ful analyze}: drives the real CLI ({@link Main#run}) against a
 * temporary project and asserts the produced analysis shards. Opt-in (RUN_E2E / -Pe2e).
 */
@DisplayName("ful analyze (end-to-end CLI)")
class CliAnalyzeE2eTest extends E2eTestBase {

  @Test
  @DisplayName("analyze writes shards for classes, records, and enums")
  void analyzeWritesShardsForAllTypes() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().writeTo(workspace);

    final int exitCode = runCli("-c", config.toString(), "analyze", workspace.toString());

    assertThat(exitCode).isZero();
    final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());
    assertThat(artifacts.classFqns())
        .contains("com.demo.Greeter", "com.demo.Point", "com.demo.Color", "com.demo.App");
  }
}
