package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes user-facing Java language level strings into JavaParser's {@link LanguageLevel} enum.
 *
 * <p>Accepts loose variants such as {@code "JAVA_21"}, {@code "21"}, {@code "java21"}, {@code "Java
 * 21"}, and {@code "java-21"}. Unknown or null inputs fall back to the project default ({@link
 * #DEFAULT}) and emit a WARN log de-duplicated per raw input value via {@code Logger.warnOnce}.
 *
 * <p>JavaParser 3.27.0 ships first-class enum constants for {@code JAVA_19}–{@code JAVA_21}. The
 * project default is pinned to {@link LanguageLevel#JAVA_21} (LTS) rather than {@link
 * LanguageLevel#BLEEDING_EDGE} so behavior does not silently drift when the library bumps its
 * preview target.
 */
public final class LanguageLevels {

  /**
   * Default language level when not configured. Pinned to {@link LanguageLevel#JAVA_21} (LTS) so
   * behavior stays stable across JavaParser upgrades that move {@code BLEEDING_EDGE} forward.
   */
  public static final LanguageLevel DEFAULT = LanguageLevel.JAVA_21;

  private static final Map<String, LanguageLevel> CANONICAL =
      Map.ofEntries(
          Map.entry("JAVA8", LanguageLevel.JAVA_8),
          Map.entry("JAVA9", LanguageLevel.JAVA_9),
          Map.entry("JAVA10", LanguageLevel.JAVA_10),
          Map.entry("JAVA11", LanguageLevel.JAVA_11),
          Map.entry("JAVA12", LanguageLevel.JAVA_12),
          Map.entry("JAVA13", LanguageLevel.JAVA_13),
          Map.entry("JAVA14", LanguageLevel.JAVA_14),
          Map.entry("JAVA15", LanguageLevel.JAVA_15),
          Map.entry("JAVA16", LanguageLevel.JAVA_16),
          Map.entry("JAVA17", LanguageLevel.JAVA_17),
          Map.entry("JAVA18", LanguageLevel.JAVA_18),
          Map.entry("JAVA19", LanguageLevel.JAVA_19),
          Map.entry("JAVA20", LanguageLevel.JAVA_20),
          Map.entry("JAVA21", LanguageLevel.JAVA_21),
          Map.entry("BLEEDINGEDGE", LanguageLevel.BLEEDING_EDGE),
          // POPULAR/CURRENT are pinned to concrete LTS levels (not JavaParser's version-dependent
          // POPULAR/CURRENT constants) so they mean the same thing in the Spoon engine — see
          // SpoonComplianceLevels.
          Map.entry("POPULAR", LanguageLevel.JAVA_17),
          Map.entry("CURRENT", LanguageLevel.JAVA_21));

  private LanguageLevels() {
    // utility
  }

  /**
   * Resolve a free-form language level string to a JavaParser {@link LanguageLevel}.
   *
   * @param raw user input from configuration (may be null or blank)
   * @return resolved level, never null; falls back to {@link #DEFAULT} on unknown input
   */
  public static LanguageLevel resolve(final String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT;
    }
    final String key = normalize(raw);
    final LanguageLevel resolved = CANONICAL.get(key);
    if (resolved != null) {
      return resolved;
    }
    final LanguageLevel numeric = CANONICAL.get("JAVA" + key);
    if (numeric != null) {
      return numeric;
    }
    Logger.warnOnce(
        "analysis.language_level.unknown:" + raw,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.language_level.warn.unknown", raw, DEFAULT.name()));
    return DEFAULT;
  }

  /**
   * Build a {@link ParserConfiguration} pre-populated with the resolved language level.
   *
   * @param raw user input from configuration (may be null or blank)
   * @return a fresh {@link ParserConfiguration} with language level set
   */
  public static ParserConfiguration configurationFor(final String raw) {
    final ParserConfiguration pc = new ParserConfiguration();
    pc.setLanguageLevel(resolve(raw));
    return pc;
  }

  private static String normalize(final String raw) {
    return raw.toUpperCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
  }
}
