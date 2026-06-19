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
 * End-to-end regression guards for analysis engine selection (#1) and {@code language_level} alias
 * resolution (#6) at the CLI level. The engine actually used is inferred from {@code
 * type_resolution_summary.json}'s {@code per_source} counts (a JavaParser-only run resolves zero
 * Spoon entries, and vice versa).
 */
@DisplayName("ful analyze engine/language_level selection (end-to-end CLI)")
class CliEngineSelectionE2eTest extends E2eTestBase {

  @Test
  @DisplayName("config analysis.engine=javaparser is honored (no Spoon resolution)")
  void configEngineIsHonored() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().engine("javaparser").writeTo(workspace);

    final int exitCode = runCli("-c", config.toString(), "analyze", workspace.toString());

    assertThat(exitCode).isZero();
    assertThat(spoonResolved()).isZero();
    assertThat(javaParserResolved()).isPositive();
  }

  @Test
  @DisplayName("--engine flag overrides config analysis.engine")
  void cliEngineFlagOverridesConfig() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().engine("spoon").writeTo(workspace);

    final int exitCode =
        runCli("-c", config.toString(), "analyze", "--engine", "javaparser", workspace.toString());

    assertThat(exitCode).isZero();
    assertThat(spoonResolved()).isZero();
    assertThat(javaParserResolved()).isPositive();
  }

  @Test
  @DisplayName("language_level POPULAR alias flows through the CLI")
  void popularAliasIsAccepted() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    final Path config = ConfigBuilder.create().languageLevel("POPULAR").writeTo(workspace);

    final int exitCode = runCli("-c", config.toString(), "analyze", workspace.toString());

    assertThat(exitCode).isZero();
    final RunArtifacts artifacts = RunArtifacts.locateLatest(runsRoot());
    assertThat(artifacts.classFqns()).contains("com.demo.Point", "com.demo.Color");
  }

  private int spoonResolved() throws IOException {
    return RunArtifacts.locateLatest(runsRoot())
        .typeResolutionSummary()
        .path("per_source")
        .path("spoon")
        .path("resolved")
        .asInt(0);
  }

  private int javaParserResolved() throws IOException {
    return RunArtifacts.locateLatest(runsRoot())
        .typeResolutionSummary()
        .path("per_source")
        .path("javaparser")
        .path("resolved")
        .asInt(0);
  }
}
