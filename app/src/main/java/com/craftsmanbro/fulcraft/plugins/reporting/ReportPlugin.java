package com.craftsmanbro.fulcraft.plugins.reporting;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.plugins.reporting.stage.ReportStage;
import java.util.Objects;

/** Built-in workflow plugin that executes {@link ReportStage}. */
public final class ReportPlugin implements ActionPlugin {

  public static final String PLUGIN_ID = "report-builtin";

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
        context, MessageSource.getMessage("report.common.error.argument_null", "context"));
    final RunContext runContext = context.getRunContext();
    final Config config =
        Objects.requireNonNull(
            runContext.getConfig(),
            MessageSource.getMessage("report.common.error.argument_null", "runContext.config"));
    try {
      new ReportStage(config).execute(runContext);
      return PluginResult.success(PLUGIN_ID, MessageSource.getMessage("report.plugin.completed"));
    } catch (StageException e) {
      throw new ActionPluginException(MessageSource.getMessage("report.plugin.error.failed"), e);
    }
  }
}
