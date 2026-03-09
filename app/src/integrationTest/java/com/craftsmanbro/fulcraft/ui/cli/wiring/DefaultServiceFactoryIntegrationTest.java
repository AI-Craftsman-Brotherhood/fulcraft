package com.craftsmanbro.fulcraft.ui.cli.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultVerifier;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.BuildToolPort;
import io.opentelemetry.api.trace.Tracer;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class DefaultServiceFactoryIntegrationTest {

  private DefaultServiceFactory factory;
  private Tracer tracer;

  @BeforeEach
  void setUp() {
    tracer = mock(Tracer.class);
    factory = new DefaultServiceFactory(tracer);
  }

  @Test
  void createAnalysisPort_returnsCompositeAnalysisPort_whenEngineTypeIsNull() {
    AnalysisPort port = factory.createAnalysisPort(null);
    assertThat(port).isNotNull();
    assertThat(port.getClass().getSimpleName()).contains("CompositeAnalysisPort");
  }

  @Test
  void createAnalysisPort_returnsCompositeAnalysisPort_whenEngineTypeIsUnknown() {
    AnalysisPort port = factory.createAnalysisPort("unknown");
    assertThat(port).isNotNull();
    assertThat(port.getClass().getSimpleName()).contains("CompositeAnalysisPort");
  }

  @Test
  void createAnalysisPort_returnsSpoonAnalysisAdapter_whenEngineTypeIsSpoon() {
    AnalysisPort port = factory.createAnalysisPort("spoon");
    assertThat(port).isNotNull();
    assertThat(port.getClass().getSimpleName()).contains("SpoonAnalysisAdapter");
  }

  @Test
  void createAnalysisPort_returnsJavaParserAnalysisAdapter_whenEngineTypeIsJavaparser() {
    AnalysisPort port = factory.createAnalysisPort("javaparser");
    assertThat(port).isNotNull();
    assertThat(port.getClass().getSimpleName()).contains("JavaParserAnalysisAdapter");
  }

  @Test
  void createAnalysisPort_treatsEngineTypeCaseInsensitively() {
    AnalysisPort spoonPort = factory.createAnalysisPort("SPOON");
    AnalysisPort parserPort = factory.createAnalysisPort("JAVAPARSER");

    assertThat(spoonPort.getClass().getSimpleName()).contains("SpoonAnalysisAdapter");
    assertThat(parserPort.getClass().getSimpleName()).contains("JavaParserAnalysisAdapter");
  }

  @Test
  void createResultMerger_returnsInstance() {
    ResultMerger merger = factory.createResultMerger();
    assertThat(merger).isNotNull();
  }

  @Test
  void createResultVerifier_returnsInstance() {
    ResultVerifier verifier = factory.createResultVerifier();
    assertThat(verifier).isNotNull();
  }

  @Test
  void createBuildTool_returnsDefaultBuildTool() {
    BuildToolPort tool = factory.createBuildTool();
    assertThat(tool).isNotNull();
    assertThat(tool.getClass().getSimpleName()).contains("DefaultBuildTool");
  }

  @Test
  void constructor_rejectsNullTracer() {
    assertThatThrownBy(() -> new DefaultServiceFactory(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createLlmClient_returnsFeatureView_whenConfigIsValid() {
    Config config = mockLlmConfig();

    var client = factory.createLlmClient(config);

    assertThat(client).isNotNull();
    assertThat(client.getClass().getSimpleName()).contains("FeatureView");
  }

  @Test
  void createLlmClient_throwsWhenLlmConfigIsMissing() {
    Config config = new Config();

    assertThatThrownBy(() -> factory.createLlmClient(config))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("baseConfig must not be null");
  }

  @Test
  void createDecoratedLlmClient_rejectsNullConfig(@TempDir Path projectRoot) {
    assertThatThrownBy(() -> factory.createDecoratedLlmClient(null, projectRoot))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("config must not be null");
  }

  @Test
  void createDecoratedLlmClient_rejectsNullProjectRoot() {
    Config config = mockLlmConfig();

    assertThatThrownBy(() -> factory.createDecoratedLlmClient(config, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("projectRoot must not be null");
  }

  @Test
  void createDecoratedLlmClient_returnsFeatureView(@TempDir Path projectRoot) {
    Config config = mockLlmConfig();

    var client = factory.createDecoratedLlmClient(config, projectRoot);

    assertThat(client).isNotNull();
    assertThat(client.getClass().getSimpleName()).contains("FeatureView");
  }

  private static Config mockLlmConfig() {
    Config config = new Config();
    Config.LlmConfig llm = new Config.LlmConfig();
    llm.setProvider("mock");
    llm.setModelName("mock-model");
    config.setLlm(llm);
    return config;
  }
}
