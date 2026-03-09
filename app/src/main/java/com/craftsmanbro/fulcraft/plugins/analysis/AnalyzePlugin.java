package com.craftsmanbro.fulcraft.plugins.analysis;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.stage.AnalyzeStage;
import java.util.Objects;

/** Built-in workflow plugin that executes {@link AnalyzeStage}. */
public final class AnalyzePlugin implements ActionPlugin {

  public static final String PLUGIN_ID = "analyze-builtin";

  @Override
  public String id() {
    return PLUGIN_ID;
  }

  @Override
  public String kind() {
    return "workflow";
  }

  @Override
  public PluginResult execute(final PluginContext context) throws ActionPluginException {
    Objects.requireNonNull(
        context, MessageSource.getMessage("analysis.common.error.argument_null", "context"));
    final RunContext runContext = context.getRunContext();
    final AnalysisPort analysisPort;
    try {
      analysisPort = context.getServiceRegistry().require(AnalysisPort.class);
    } catch (IllegalStateException e) {
      throw new ActionPluginException(
          MessageSource.getMessage("analysis.plugin.error.analysis_port_required"), e);
    }
    try {
      new AnalyzeStage(analysisPort).execute(runContext);
      return PluginResult.success(PLUGIN_ID, MessageSource.getMessage("analysis.plugin.completed"));
    } catch (StageException e) {
      throw new ActionPluginException(MessageSource.getMessage("analysis.plugin.error.failed"), e);
    }
  }
}
