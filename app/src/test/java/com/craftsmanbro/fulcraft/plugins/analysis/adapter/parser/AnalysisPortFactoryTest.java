package com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.AnalysisPortFactory.EngineType;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalysisPortFactoryTest {

  @Test
  void create_requiresConfig() {
    assertThatThrownBy(() -> AnalysisPortFactory.create((Config) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("config must not be null");
  }

  @Test
  void create_defaultsToSpoonWhenNoAnalysisConfig() {
    Config config = new Config();

    AnalysisPort port = AnalysisPortFactory.create(config);

    assertThat(port).isInstanceOf(SpoonAnalysisAdapter.class);
  }

  @Test
  void create_defaultsToSpoonWhenEngineBlank() {
    Config config = configWithEngine("  ");

    AnalysisPort port = AnalysisPortFactory.create(config);

    assertThat(port).isInstanceOf(SpoonAnalysisAdapter.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"javaparser", "jp"})
  void create_returnsJavaParserAdapterForAliases(String engine) {
    Config config = configWithEngine(engine);

    AnalysisPort port = AnalysisPortFactory.create(config);

    assertThat(port).isInstanceOf(JavaParserAnalysisAdapter.class);
  }

  @Test
  void create_returnsSpoonAdapterForSpoon() {
    Config config = configWithEngine("spoon");

    AnalysisPort port = AnalysisPortFactory.create(config);

    assertThat(port).isInstanceOf(SpoonAnalysisAdapter.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"composite", "all", "both"})
  void create_returnsCompositeAdapterForAliases(String engine) {
    Config config = configWithEngine(engine);

    AnalysisPort port = AnalysisPortFactory.create(config);

    assertThat(port).isInstanceOf(CompositeAnalysisAdapter.class);
  }

  @Test
  void create_createsAdaptersForEngineType() {
    assertThat(AnalysisPortFactory.create(EngineType.JAVAPARSER))
        .isInstanceOf(JavaParserAnalysisAdapter.class);
    assertThat(AnalysisPortFactory.create(EngineType.SPOON))
        .isInstanceOf(SpoonAnalysisAdapter.class);
    assertThat(AnalysisPortFactory.create(EngineType.COMPOSITE))
        .isInstanceOf(CompositeAnalysisAdapter.class);
  }

  @Test
  void getDefaultEngineType_returnsSpoon() {
    assertThat(AnalysisPortFactory.getDefaultEngineType()).isEqualTo(EngineType.SPOON);
  }

  @Test
  void getAvailableEngines_returnsEnumValues() {
    assertThat(AnalysisPortFactory.getAvailableEngines()).containsExactly(EngineType.values());
  }

  private static Config configWithEngine(String engine) {
    Config config = new Config();
    Config.AnalysisConfig analysis = new Config.AnalysisConfig();
    analysis.setEngine(engine);
    config.setAnalysis(analysis);
    return config;
  }
}
