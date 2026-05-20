package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps the user-facing {@code analysis.language_level} to Spoon's {@code complianceLevel} integer.
 * Spoon 10.4.x bundles JDT 3.33 which supports compliance levels 8-17; higher values are clamped to
 * {@value #MAX_SUPPORTED} to avoid JDT's {@code Unrecognized option : -NN} error.
 */
public final class SpoonComplianceLevels {

  /**
   * Default Spoon compliance level when not configured. Spoon 10.4.x bundles JDT, which only
   * supports compliance levels up to {@value #MAX_SUPPORTED}; higher values trigger {@code
   * Unrecognized option : -NN} from JDT.
   */
  public static final int DEFAULT = 17;

  private static final int MIN_SUPPORTED = 8;

  private static final int MAX_SUPPORTED = 17;

  private static final Pattern JAVA_NUMERIC = Pattern.compile("^JAVA_?(\\d{1,2})(?:_PREVIEW)?$");

  private static final Map<String, Integer> ALIASES =
      Map.of(
          "POPULAR", 11,
          "CURRENT", 16,
          "BLEEDINGEDGE", MAX_SUPPORTED);

  private SpoonComplianceLevels() {
    // utility
  }

  /** Resolve the {@link Config}'s {@code analysis.language_level} into a Spoon compliance int. */
  public static int fromConfig(final Config config) {
    if (config == null || config.getAnalysis() == null) {
      return DEFAULT;
    }
    return resolve(config.getAnalysis().getLanguageLevel());
  }

  /** Resolve a raw user-facing string into a Spoon compliance int. */
  public static int resolve(final String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT;
    }
    final String key = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    final Integer alias = ALIASES.get(key);
    if (alias != null) {
      return clamp(alias);
    }
    final Matcher m = JAVA_NUMERIC.matcher(key);
    if (m.matches()) {
      return clamp(Integer.parseInt(m.group(1)));
    }
    final Matcher numeric = JAVA_NUMERIC.matcher("JAVA" + key);
    if (numeric.matches()) {
      return clamp(Integer.parseInt(numeric.group(1)));
    }
    // Unknown form -> safest default. We avoid going through LanguageLevels so that
    // Spoon's compliance level isn't capped by JavaParser's library limit.
    Logger.warn(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.language_level.warn.unknown", raw, String.valueOf(DEFAULT)));
    return DEFAULT;
  }

  private static int clamp(final int level) {
    return Math.min(Math.max(level, MIN_SUPPORTED), MAX_SUPPORTED);
  }
}
