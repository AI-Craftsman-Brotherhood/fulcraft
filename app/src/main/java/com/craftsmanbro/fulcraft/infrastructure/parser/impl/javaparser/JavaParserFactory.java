package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.config.Config;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;

/**
 * Single source of truth for constructing {@link JavaParser} instances with a consistent {@link
 * ParserConfiguration.LanguageLevel}.
 *
 * <p>All analysis-path JavaParser usages must go through this factory; only the formatter/
 * brittleness paths are allowed to instantiate {@code JavaParser} directly because they
 * intentionally pin {@code BLEEDING_EDGE}.
 */
public final class JavaParserFactory {

  private JavaParserFactory() {
    // utility
  }

  /** Returns a parser configured from {@code config.analysis.language_level}. */
  public static JavaParser newParser(final Config config) {
    return new JavaParser(newConfiguration(config));
  }

  /** Returns a parser configured with the project default ({@link LanguageLevels#DEFAULT}). */
  public static JavaParser newDefaultParser() {
    return new JavaParser(LanguageLevels.configurationFor(null));
  }

  /** Returns a fresh configuration derived from the given {@link Config}. */
  public static ParserConfiguration newConfiguration(final Config config) {
    return LanguageLevels.configurationFor(extractRawLanguageLevel(config));
  }

  private static String extractRawLanguageLevel(final Config config) {
    if (config == null || config.getAnalysis() == null) {
      return null;
    }
    return config.getAnalysis().getLanguageLevel();
  }
}
