package com.craftsmanbro.fulcraft.ui.banner;

import java.nio.file.Path;
import java.util.List;

/** Builds CLI startup banner lines (without slash-command hints). */
public final class CliStartupBanner {

  private static final int BOX_WIDTH = 53;

  private static final String UNKNOWN_MODEL = "unknown";

  private CliStartupBanner() {}

  public static List<String> buildLines(
      final String applicationName,
      final String applicationVersion,
      final String modelName,
      final Path projectRoot,
      final Path configPath) {
    final int innerWidth = BOX_WIDTH - 2;
    final String horizontal = "─".repeat(innerWidth);
    final String startupTitle =
        ">_ "
            + normalize(applicationName, StartupBannerSupport.resolveApplicationName())
            + " (v"
            + normalize(applicationVersion, StartupBannerSupport.resolveApplicationVersion())
            + ")";
    final String startupModelLabel = "model:     " + normalize(modelName, UNKNOWN_MODEL);
    final Path effectiveConfigPath = configPath != null ? configPath : Path.of("config.json");
    final String configLabel = "config:    " + StartupBannerSupport.formatPath(effectiveConfigPath);
    return List.of(
        "╭" + horizontal + "╮",
        formatBoxLine(" " + startupTitle, innerWidth),
        formatBoxLine("", innerWidth),
        formatBoxLine(" " + startupModelLabel, innerWidth),
        formatBoxLine(" " + configLabel, innerWidth),
        formatBoxLine(
            " directory: " + StartupBannerSupport.formatDirectory(projectRoot), innerWidth),
        "╰" + horizontal + "╯");
  }

  private static String formatBoxLine(final String content, final int innerWidth) {
    final String normalized = content == null ? "" : content;
    final String clipped =
        normalized.length() > innerWidth ? normalized.substring(0, innerWidth) : normalized;
    final StringBuilder builder = new StringBuilder(innerWidth + 2);
    builder.append('│').append(clipped);
    while (builder.length() < innerWidth + 1) {
      builder.append(' ');
    }
    return builder.append('│').toString();
  }

  private static String normalize(final String value, final String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }
}
