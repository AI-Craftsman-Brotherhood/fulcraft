package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.config.Config;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;

/**
 * Single source of truth for constructing {@link JavaParser} instances with a consistent {@link
 * ParserConfiguration.LanguageLevel}.
 *
 * <p>Two construction modes are exposed:
 *
 * <ul>
 *   <li>{@link #newParser(Config)} / {@link #newConfiguration(Config)} honor the user's {@code
 *       analysis.language_level} setting. Use these on the main analysis path that processes user
 *       project sources.
 *   <li>{@link #newDefaultParser()} pins {@link LanguageLevels#DEFAULT} (JAVA_21 LTS) regardless of
 *       configuration. Use this for parsers that handle inputs orthogonal to the user's project
 *       language level — e.g. LLM-generated snippets, cached responses, internal expression
 *       analysis, and project-wide symbol indexing — where the broadest syntactic support is
 *       required and silently degrading to a narrower level would drop valid constructs.
 * </ul>
 *
 * <p>Formatter and brittleness paths that intentionally pin {@code BLEEDING_EDGE} (e.g. {@code
 * TestCodeFormatter}, {@code JavaParserBrittleTestChecker}, {@code BranchSummaryExtractor}) are the
 * only places allowed to instantiate {@code JavaParser} directly without going through this
 * factory.
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
