package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.util.Locale;

/** Applies logger-related CLI overrides in one place. */
public final class CliLoggerSettingsSupport {

  private CliLoggerSettingsSupport() {}

  public static void apply(final CommonOverrides overrides) {
    if (overrides == null) {
      return;
    }
    final String effectiveLogFormat = overrides.getEffectiveLogFormat();
    if (effectiveLogFormat != null) {
      UiLogger.setJsonMode("json".equalsIgnoreCase(effectiveLogFormat));
    }
    final String overrideColorMode = overrides.getColorMode();
    if (overrideColorMode != null) {
      final boolean colorEnabled =
          switch (overrideColorMode.toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> System.console() != null;
          };
      UiLogger.setColorEnabled(colorEnabled);
    }
  }
}
