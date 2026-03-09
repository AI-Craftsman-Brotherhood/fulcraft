package com.craftsmanbro.fulcraft.plugins.exploration;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.plugins.exploration.stage.ExploreStage;
import java.util.Objects;

/** Built-in workflow plugin that executes {@link ExploreStage}. */
public final class ExplorePlugin implements ActionPlugin {

  public static final String PLUGIN_ID = "explore-builtin";

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
        context, MessageSource.getMessage("explore.common.error.argument_null", "context"));
    final RunContext runContext = context.getRunContext();
    try {
      new ExploreStage().execute(runContext);
      return PluginResult.success(PLUGIN_ID, MessageSource.getMessage("explore.plugin.completed"));
    } catch (StageException e) {
      throw new ActionPluginException(MessageSource.getMessage("explore.plugin.error.failed"), e);
    }
  }
}
