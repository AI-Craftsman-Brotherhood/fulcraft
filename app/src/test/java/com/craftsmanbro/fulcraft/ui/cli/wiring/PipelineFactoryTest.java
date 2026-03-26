package com.craftsmanbro.fulcraft.ui.cli.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.kernel.plugin.runtime.PluginRegistry;
import com.craftsmanbro.fulcraft.plugins.analysis.AnalyzePlugin;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.stage.AnalyzeStage;
import com.craftsmanbro.fulcraft.plugins.document.DocumentPlugin;
import com.craftsmanbro.fulcraft.plugins.exploration.ExplorePlugin;
import com.craftsmanbro.fulcraft.plugins.reporting.ReportPlugin;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
@ExtendWith(KernelPortTestExtension.class)
class PipelineFactoryTest {

  @TempDir Path tempDir;

  private Config config;
  private AnalysisPort analysisPort;
  private ServiceFactory serviceFactory;
  private PluginRegistry pluginRegistry;

  @BeforeEach
  void setUp() {
    config = configWithStages(List.of("analyze"));
    analysisPort = mock(AnalysisPort.class);
    serviceFactory = mock(ServiceFactory.class);
    pluginRegistry =
        new PluginRegistry(
            List.of(
                new StubPlugin(AnalyzePlugin.PLUGIN_ID),
                new StubPlugin(ReportPlugin.PLUGIN_ID),
                new StubPlugin(DocumentPlugin.PLUGIN_ID),
                new StubPlugin(ExplorePlugin.PLUGIN_ID),
                new StubPlugin("junit-select"),
                new StubPlugin("junit-generate"),
                new StubPlugin("junit-brittle-check")));
  }

  @Test
  void builder_rejectsMissingAnalysisPort() {
    assertThatThrownBy(() -> PipelineFactory.builder(config).serviceFactory(serviceFactory).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("AnalysisPort must be set");
  }

  @Test
  void builder_rejectsMissingServiceFactory() {
    assertThatThrownBy(() -> PipelineFactory.builder(config).analysisPort(analysisPort).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ServiceFactory must be set");
  }

  @Test
  void constructor_rejectsNullArguments() {
    assertThatThrownBy(() -> new PipelineFactory(null, analysisPort, serviceFactory))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Config cannot be null");
    assertThatThrownBy(() -> new PipelineFactory(config, null, serviceFactory))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("AnalysisPort cannot be null");
    assertThatThrownBy(() -> new PipelineFactory(config, analysisPort, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ServiceFactory cannot be null");
  }

  @Test
  void create_registersOnlyAnalyzeNode_whenOnlyAnalyzeEnabled() {
    config = configWithStages(List.of("analyze"));
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .pluginRegistry(pluginRegistry)
            .build();

    final Pipeline pipeline = factory.create();

    assertThat(pipeline.getStageNodes()).containsOnlyKeys("analyze");
    assertThat(pipeline.getStages()).containsOnlyKeys(PipelineNodeIds.ANALYZE);
  }

  @Test
  void create_includesAnalyzeDependency_whenReportEnabled() {
    config = configWithStages(List.of("report"));
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .pluginRegistry(pluginRegistry)
            .build();

    final Pipeline pipeline = factory.create();

    assertThat(new ArrayList<>(pipeline.getStageNodes().keySet()))
        .containsExactly("analyze", "report");
    assertThat(pipeline.getStages()).containsOnlyKeys("analyze", "report");
  }

  @Test
  void create_registersWorkflowNodesInResolvedDagOrder_whenAllEnabled() {
    config = configWithStages(List.of("analyze", "report", "document", "explore"));
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .pluginRegistry(pluginRegistry)
            .build();

    final Pipeline pipeline = factory.create();

    assertThat(new ArrayList<>(pipeline.getStageNodes().keySet()))
        .containsExactly("analyze", "document", "explore", "report");
    assertThat(new ArrayList<>(pipeline.getStages().keySet()))
        .containsExactly("analyze", "document", "explore", "report");
  }

  @Test
  void create_mapsUnknownWorkflowPluginNodeToGenerateStep() throws Exception {
    final Path workflowPath = tempDir.resolve("workflow.json");
    Files.writeString(
        workflowPath,
        """
            {
              "version": 1,
              "nodes": [
                {"id":"custom-generate","plugin":"custom-workflow"}
              ]
            }
            """);
    config = configWithStages(List.of("generate"));
    config.getProject().setRoot(tempDir.toString());
    config.getPipeline().setWorkflowFile("workflow.json");
    pluginRegistry = new PluginRegistry(List.of(new StubPlugin("custom-workflow")));
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .pluginRegistry(pluginRegistry)
            .build();

    final Pipeline pipeline = factory.create();

    assertThat(pipeline.getStageNodes()).containsOnlyKeys("custom-generate");
    assertThat(pipeline.getStages()).containsOnlyKeys("custom-generate");
  }

  @Test
  void createAnalysisOnlyPipeline_registersOnlyAnalyzeStage() {
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .build();

    final Pipeline pipeline = factory.createAnalysisOnlyPipeline();
    final var stages = pipeline.getStages();

    assertThat(pipeline).isNotNull();
    assertThat(stages).containsOnlyKeys(PipelineNodeIds.ANALYZE);
    assertThat(stages.get(PipelineNodeIds.ANALYZE)).isInstanceOf(AnalyzeStage.class);
  }

  @Test
  void withPluginRegistry_returnsSameFactoryInstance() {
    final PipelineFactory factory =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(serviceFactory)
            .build();

    final PipelineFactory chained = factory.withPluginRegistry(pluginRegistry);

    assertThat(chained).isSameAs(factory);
  }

  private static Config configWithStages(final List<String> stages) {
    final Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    final Config.PipelineConfig pipelineConfig = new Config.PipelineConfig();
    pipelineConfig.setStages(stages);
    config.setPipeline(pipelineConfig);
    return config;
  }

  private static final class StubPlugin implements ActionPlugin {
    private final String id;

    private StubPlugin(final String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String kind() {
      return "workflow";
    }

    @Override
    public PluginResult execute(final PluginContext context) {
      return PluginResult.success(id, "ok");
    }
  }
}
