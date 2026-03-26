package com.craftsmanbro.fulcraft.ui.cli.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
@ExtendWith(KernelPortTestExtension.class)
class PipelineRunnerFactoryTest {

  @Test
  void createDefault_rejectsNullConfig() {
    ServiceFactory serviceFactory = mock(ServiceFactory.class);

    assertThatThrownBy(() -> PipelineRunnerFactory.createDefault(null, serviceFactory, "spoon"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("config must not be null");
  }

  @Test
  void createDefault_rejectsNullServices() {
    Config config = configWithStages(List.of("analyze"));

    assertThatThrownBy(() -> PipelineRunnerFactory.createDefault(config, null, "spoon"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("services must not be null");
  }

  @Test
  void createDefault_passesEngineTypeToServiceFactory() {
    Config config = configWithStages(List.of("analyze"));
    ServiceFactory serviceFactory = mock(ServiceFactory.class);
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    when(serviceFactory.createAnalysisPort("spoon")).thenReturn(analysisPort);

    PipelineRunner runner = PipelineRunnerFactory.createDefault(config, serviceFactory, "spoon");

    assertThat(runner).isNotNull();
    assertThat(runner.getPipeline().getStages()).containsOnlyKeys(PipelineNodeIds.ANALYZE);
    verify(serviceFactory).createAnalysisPort("spoon");
  }

  @Test
  void createDefault_supportsNullEngineType() {
    Config config = configWithStages(List.of("analyze"));
    ServiceFactory serviceFactory = mock(ServiceFactory.class);
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    when(serviceFactory.createAnalysisPort(null)).thenReturn(analysisPort);

    PipelineRunner runner = PipelineRunnerFactory.createDefault(config, serviceFactory, null);

    assertThat(runner).isNotNull();
    assertThat(runner.getPipeline().getStages()).containsOnlyKeys(PipelineNodeIds.ANALYZE);
    verify(serviceFactory).createAnalysisPort(null);
  }

  private static Config configWithStages(List<String> stages) {
    Config config = new Config();
    Config.PipelineConfig pipelineConfig = new Config.PipelineConfig();
    pipelineConfig.setStages(stages);
    config.setPipeline(pipelineConfig);
    return config;
  }
}
