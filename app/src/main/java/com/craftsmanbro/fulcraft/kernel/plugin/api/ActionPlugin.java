package com.craftsmanbro.fulcraft.kernel.plugin.api;

/**
 * Plugin interface for executing pipeline actions.
 *
 * <p>Implementations should be stateless and safe to invoke multiple times.
 */
public interface ActionPlugin {

  /**
   * Unique plugin identifier.
   *
   * @return plugin id
   */
  String id();

  /**
   * Plugin kind indicating where it is used.
   *
   * @return plugin kind
   */
  String kind();

  /**
   * Execute the plugin action.
   *
   * @param context plugin execution context
   * @return execution result
   * @throws ActionPluginException if execution fails
   */
  PluginResult execute(PluginContext context) throws ActionPluginException;
}
