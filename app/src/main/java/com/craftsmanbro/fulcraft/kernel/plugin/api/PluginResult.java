package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Objects;

/** Result of plugin execution. */
public final class PluginResult {

  private final String pluginId;

  private final boolean success;

  private final String message;

  private final Throwable error;

  private PluginResult(
      final String pluginId, final boolean success, final String message, final Throwable error) {
    this.pluginId =
        Objects.requireNonNull(
            pluginId, MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    this.success = success;
    this.message = message;
    this.error = error;
  }

  public static PluginResult success(final String pluginId, final String message) {
    return new PluginResult(pluginId, true, message, null);
  }

  public static PluginResult failure(
      final String pluginId, final String message, final Throwable error) {
    return new PluginResult(pluginId, false, message, error);
  }

  public String getPluginId() {
    return pluginId;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getMessage() {
    return message;
  }

  public Throwable getError() {
    return error;
  }
}
