package com.craftsmanbro.fulcraft.ui.cli.wiring;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import java.util.Objects;

/** UI wiring helper for creating a default PipelineRunner. */
public final class PipelineRunnerFactory {

  private PipelineRunnerFactory() {}

  public static PipelineRunner createDefault(
      final Config config, final ServiceFactory services, final String engineType) {
    return createDefault(config, services, engineType, null);
  }

  public static PipelineRunner createDefault(
      final Config config,
      final ServiceFactory services,
      final String engineType,
      final String pluginClasspath) {
    Objects.requireNonNull(config, "config must not be null");
    Objects.requireNonNull(services, "services must not be null");
    final AnalysisPort analysisPort = services.createAnalysisPort(engineType);
    final Pipeline pipeline =
        PipelineFactory.builder(config)
            .analysisPort(analysisPort)
            .serviceFactory(services)
            .pluginClasspath(pluginClasspath)
            .build()
            .create();
    return new PipelineRunner(pipeline);
  }
}
