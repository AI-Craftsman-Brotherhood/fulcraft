package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Locale;

/** Kind normalization helpers for plugin metadata. */
public final class PluginKind {

  private PluginKind() {}

  public static String normalizeRequired(final String kind, final String parameterName) {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("kernel.plugin.kind.error.blank", parameterName));
    }
    return kind.trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizeNullable(final String kind) {
    if (kind == null || kind.isBlank()) {
      return null;
    }
    return kind.trim().toLowerCase(Locale.ROOT);
  }
}
