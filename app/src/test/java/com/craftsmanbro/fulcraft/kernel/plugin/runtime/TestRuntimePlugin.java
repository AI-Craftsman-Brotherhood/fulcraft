package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;

/** Test plugin registered via ServiceLoader for runtime loader verification. */
public class TestRuntimePlugin implements ActionPlugin {

  static final String PLUGIN_ID = "test-runtime-plugin";

  @Override
  public String id() {
    return PLUGIN_ID;
  }

  @Override
  public String kind() {
    return "report";
  }

  @Override
  public PluginResult execute(PluginContext context) {
    return PluginResult.success(id(), "ok");
  }
}
