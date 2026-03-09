package com.craftsmanbro.fulcraft.ui.cli.command.run;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.Pipeline;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.util.List;
import java.util.Objects;

/** Attaches RunCommand-specific pipeline lifecycle logging listeners. */
public final class RunPipelineLogging {

  private RunPipelineLogging() {}

  public static void attach(final PipelineRunner runner) {
    Objects.requireNonNull(runner, "runner is required");
    runner.addListener(listener());
  }

  private static Pipeline.PipelineListener listener() {
    return new Pipeline.PipelineListener() {

      @Override
      public void onPipelineStarted(final RunContext ctx, final List<String> nodeIds) {
        UiLogger.stdout(MessageSource.getMessage("pipeline.starting", nodeIds.size()));
      }

      @Override
      public void onPipelineCompleted(final RunContext ctx, final int exitCode) {
        if (exitCode == 0) {
          UiLogger.stdout(MessageSource.getMessage("pipeline.completed_success"));
        } else {
          UiLogger.stdout(MessageSource.getMessage("pipeline.completed_errors"));
        }
      }
    };
  }
}
