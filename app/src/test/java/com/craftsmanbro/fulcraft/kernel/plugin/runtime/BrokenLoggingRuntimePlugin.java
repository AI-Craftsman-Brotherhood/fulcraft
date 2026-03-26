package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;

/** Test-only plugin that throws when metadata is accessed. */
public class BrokenLoggingRuntimePlugin implements ActionPlugin {

  static final String PLUGIN_ID = "broken-logging-runtime-plugin";

  @Override
  public String id() {
    throw new IllegalStateException("id lookup failed");
  }

  @Override
  public String kind() {
    return "report";
  }

  @Override
  public PluginResult execute(PluginContext context) {
    return PluginResult.success(PLUGIN_ID, "ok");
  }
}
