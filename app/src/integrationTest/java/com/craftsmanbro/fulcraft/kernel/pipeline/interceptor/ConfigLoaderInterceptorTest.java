package com.craftsmanbro.fulcraft.kernel.pipeline.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for the {@link ConfigLoaderInterceptor}. */
class ConfigLoaderInterceptorTest {

  @TempDir Path tempDir;

  private ConfigLoaderInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new ConfigLoaderInterceptor();
  }

  @Test
  void shouldHaveCorrectId() {
    assertThat(interceptor.id()).isEqualTo("config-loader");
  }

  @Test
  void shouldTargetAnalyzePhase() {
    assertThat(interceptor.phase()).isEqualTo(PipelineNodeIds.ANALYZE);
  }

  @Test
  void shouldUsePreHook() {
    assertThat(interceptor.hook()).isEqualTo(Hook.PRE);
  }

  @Test
  void shouldHaveEarlyOrder() {
    assertThat(interceptor.order()).isEqualTo(10);
  }

  @Test
  void shouldAlwaysBeSupported() {
    Config config = new Config();
    assertThat(interceptor.supports(config)).isTrue();
  }

  @Test
  void shouldAddErrorWhenConfigurationIsInvalid() {
    Config config = new Config();
    RunContext context = new RunContext(tempDir, config, "run-001");

    interceptor.apply(context);

    assertThat(context.hasErrors()).isTrue();
    assertThat(context.getErrors()).anyMatch(e -> e.contains("Configuration validation failed"));
  }

  @Test
  void shouldAddErrorWhenProjectRootIsNull() {
    Config config = createValidConfig();
    // projectRoot is required but we pass tempDir to constructor
    RunContext context =
        new RunContext(tempDir, config, "run-001") {
          @Override
          public Path getProjectRoot() {
            return null; // Override to return null
          }
        };

    interceptor.apply(context);

    assertThat(context.hasErrors()).isTrue();
    assertThat(context.getErrors()).anyMatch(e -> e.contains("Project root is not set"));
  }

  @Test
  void shouldAddErrorWhenProjectRootDoesNotExist() {
    Config config = createValidConfig();
    Path nonExistentPath = tempDir.resolve("nonexistent");
    RunContext context = new RunContext(nonExistentPath, config, "run-001");

    interceptor.apply(context);

    assertThat(context.hasErrors()).isTrue();
    assertThat(context.getErrors()).anyMatch(e -> e.contains("does not exist"));
  }

  @Test
  void shouldAddErrorWhenProjectRootIsNotDirectory() throws IOException {
    Config config = createValidConfig();
    Path regularFile = tempDir.resolve("notADir.txt");
    Files.createFile(regularFile);
    RunContext context = new RunContext(regularFile, config, "run-001");

    interceptor.apply(context);

    assertThat(context.hasErrors()).isTrue();
    assertThat(context.getErrors()).anyMatch(e -> e.contains("is not a directory"));
  }

  @Test
  void shouldAddErrorWhenConfigurationIsNullInContext() {
    RunContext context =
        new RunContext(tempDir, createValidConfig(), "run-001") {
          @Override
          public Config getConfig() {
            return null;
          }
        };

    interceptor.apply(context);

    assertThat(context.hasErrors()).isTrue();
    assertThat(context.getErrors()).anyMatch(e -> e.contains("Configuration is not loaded"));
  }

  @Test
  void shouldPassProjectRootValidation() {
    Config config = createValidConfig();
    RunContext context = new RunContext(tempDir, config, "run-001");

    interceptor.apply(context);

    assertThat(context.hasErrors()).isFalse();
  }

  private Config createValidConfig() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);
    config.setSelectionRules(new Config.SelectionRules());
    Config.LlmConfig llmConfig = new Config.LlmConfig();
    llmConfig.setProvider("mock");
    config.setLlm(llmConfig);
    return config;
  }
}
