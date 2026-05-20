package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes user-facing Java language level strings into JavaParser's {@link LanguageLevel} enum.
 *
 * <p>Accepts loose variants such as {@code "JAVA_17"}, {@code "17"}, {@code "java17"}, {@code "Java
 * 17"}, and {@code "java-17"}. Unknown or null inputs fall back to the project default ({@link
 * #DEFAULT}) and emit a single WARN log.
 *
 * <p>JavaParser 3.25.7 only ships levels up to {@code JAVA_18}. Versions {@code 19}–{@code 21} are
 * mapped to {@link LanguageLevel#BLEEDING_EDGE} as a best-effort approximation so that Java 17+
 * features (records, sealed, pattern matching) continue to parse correctly.
 */
public final class LanguageLevels {

  /**
   * Default language level when not configured. {@link LanguageLevel#BLEEDING_EDGE} (= {@code
   * JAVA_17_PREVIEW}) is the maximum that JavaParser 3.25.7 can recognize and matches the behavior
   * of {@code TestCodeFormatter}/{@code JavaParserBrittleTestChecker}.
   */
  public static final LanguageLevel DEFAULT = LanguageLevel.BLEEDING_EDGE;

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
          // 19+ does not exist in JavaParser 3.25.7; approximate with BLEEDING_EDGE.
          Map.entry("JAVA19", LanguageLevel.BLEEDING_EDGE),
          Map.entry("JAVA20", LanguageLevel.BLEEDING_EDGE),
          Map.entry("JAVA21", LanguageLevel.BLEEDING_EDGE),
          Map.entry("BLEEDINGEDGE", LanguageLevel.BLEEDING_EDGE),
          Map.entry("POPULAR", LanguageLevel.POPULAR),
          Map.entry("CURRENT", LanguageLevel.CURRENT));

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
    Logger.warn(
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
