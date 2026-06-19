package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisEngineResolverTest {

  @Test
  @DisplayName("explicit CLI engine takes precedence over config")
  void cliEngineWinsOverConfig() {
    final Config config = configWithEngine("spoon");

    assertThat(AnalysisEngineResolver.resolve("javaparser", config)).isEqualTo("javaparser");
  }

  @Test
  @DisplayName("CLI engine is trimmed")
  void cliEngineTrimmed() {
    assertThat(AnalysisEngineResolver.resolve("  spoon  ", null)).isEqualTo("spoon");
  }

  @Test
  @DisplayName("falls back to config.analysis.engine when CLI engine is null")
  void configEngineUsedWhenCliNull() {
    final Config config = configWithEngine("javaparser");

    assertThat(AnalysisEngineResolver.resolve(null, config)).isEqualTo("javaparser");
  }

  @Test
  @DisplayName("falls back to config.analysis.engine when CLI engine is blank")
  void configEngineUsedWhenCliBlank() {
    final Config config = configWithEngine("spoon");

    assertThat(AnalysisEngineResolver.resolve("   ", config)).isEqualTo("spoon");
  }

  @Test
  @DisplayName("falls back to config.analysis.engine when CLI engine is empty string")
  void configEngineUsedWhenCliEmpty() {
    final Config config = configWithEngine("spoon");

    assertThat(AnalysisEngineResolver.resolve("", config)).isEqualTo("spoon");
  }

  @Test
  @DisplayName("config engine is trimmed")
  void configEngineTrimmed() {
    final Config config = configWithEngine("  composite  ");

    assertThat(AnalysisEngineResolver.resolve(null, config)).isEqualTo("composite");
  }

  @Test
  @DisplayName("defaults to composite when neither CLI nor config specify an engine")
  void defaultsToComposite() {
    assertThat(AnalysisEngineResolver.resolve(null, null))
        .isEqualTo(AnalysisEngineResolver.DEFAULT_ENGINE_TYPE);
    assertThat(AnalysisEngineResolver.DEFAULT_ENGINE_TYPE).isEqualTo("composite");
  }

  @Test
  @DisplayName("defaults to composite when config has no analysis section")
  void defaultsToCompositeWhenNoAnalysis() {
    assertThat(AnalysisEngineResolver.resolve(null, new Config())).isEqualTo("composite");
  }

  @Test
  @DisplayName("defaults to composite when config engine is blank")
  void defaultsToCompositeWhenConfigEngineBlank() {
    assertThat(AnalysisEngineResolver.resolve(null, configWithEngine("  "))).isEqualTo("composite");
  }

  private static Config configWithEngine(final String engine) {
    final Config config = new Config();
    final Config.AnalysisConfig analysis = new Config.AnalysisConfig();
    analysis.setEngine(engine);
    config.setAnalysis(analysis);
    return config;
  }
}
