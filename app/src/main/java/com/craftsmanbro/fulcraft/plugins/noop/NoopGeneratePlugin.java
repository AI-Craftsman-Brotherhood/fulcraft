package com.craftsmanbro.fulcraft.plugins.noop;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import java.util.Objects;

/** No-op plugin for wiring validation. */
public class NoopGeneratePlugin implements ActionPlugin {

  public static final String PLUGIN_ID = "noop-generate";

  private static final String METADATA_KEY = "plugin.noop.executed";

  @Override
  public String id() {
    return PLUGIN_ID;
  }

  @Override
  public String kind() {
    return "generate";
  }

  @Override
  public PluginResult execute(final PluginContext context) {
    Objects.requireNonNull(
        context, MessageSource.getMessage("noop.common.error.argument_null", "context"));
    context.getRunContext().putMetadata(METADATA_KEY, true);
    Logger.debug(MessageSource.getMessage("noop.plugin.log.executed"));
    return PluginResult.success(PLUGIN_ID, MessageSource.getMessage("noop.plugin.completed"));
  }
}
