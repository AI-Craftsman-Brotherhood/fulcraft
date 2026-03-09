package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects generated documents and extracts analysis-gap evidence from sections before open
 * questions (section 6).
 */
public final class LlmAnalysisGapInspector {

  private static final Pattern METHOD_BLOCK_PATTERN =
      Pattern.compile("(?s)(###\\s*3\\.\\d+\\s+.*?)(?=\\R###\\s*3\\.\\d+\\s+|\\R##\\s*4\\.|\\z)");

  private static final Pattern METHOD_HEADING_PATTERN =
      Pattern.compile("(?m)^###\\s*3\\.\\d+\\s+(.+?)\\s*$");

  private static final Pattern TOP_LEVEL_SECTION_HEADING_PATTERN =
      Pattern.compile("(?m)^##\\s*(\\d+)\\.");

  /** Returns whether the main sections (before section 6) contain analysis-gap statements. */
  public boolean hasGapInMainSections(final String document) {
    return inspectMainSections(document).hasGap();
  }

  /** Inspects main sections and returns method-scoped analysis-gap evidence. */
  public AnalysisGapInspection inspectMainSections(final String document) {
    if (document == null || document.isBlank()) {
      return AnalysisGapInspection.none();
    }
    final int openQuestionsStart = findSectionHeadingStart(document, 6);
    final String mainSections =
        openQuestionsStart >= 0 ? document.substring(0, openQuestionsStart) : document;
    if (mainSections.isBlank()
        || !LlmAnalysisGapLexicon.containsAnalysisGapStatement(mainSections)) {
      return AnalysisGapInspection.none();
    }
    final LinkedHashSet<String> methods = new LinkedHashSet<>();
    final Matcher methodMatcher = METHOD_BLOCK_PATTERN.matcher(mainSections);
    while (methodMatcher.find()) {
      final String block = methodMatcher.group(1);
      if (!LlmAnalysisGapLexicon.containsAnalysisGapStatement(block)) {
        continue;
      }
      final String methodName = extractMethodHeadingName(block);
      if (!methodName.isBlank()) {
        methods.add(methodName);
      }
    }
    return new AnalysisGapInspection(true, List.copyOf(methods));
  }

  private int findSectionHeadingStart(final String document, final int sectionNo) {
    if (document == null || document.isBlank() || sectionNo <= 0) {
      return -1;
    }
    final Matcher matcher = TOP_LEVEL_SECTION_HEADING_PATTERN.matcher(document);
    while (matcher.find()) {
      final String candidate = matcher.group(1);
      if (candidate == null) {
        continue;
      }
      try {
        if (Integer.parseInt(candidate) == sectionNo) {
          return matcher.start();
        }
      } catch (NumberFormatException ignored) {
        // Ignore malformed heading token.
      }
    }
    return -1;
  }

  private String extractMethodHeadingName(final String methodBlock) {
    if (methodBlock == null || methodBlock.isBlank()) {
      return "";
    }
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(methodBlock);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1).strip();
  }

  /** Immutable analysis-gap inspection result. */
  public record AnalysisGapInspection(boolean hasGap, List<String> methodNames) {

    public AnalysisGapInspection {
      methodNames = methodNames == null ? List.of() : List.copyOf(methodNames);
    }

    public static AnalysisGapInspection none() {
      return new AnalysisGapInspection(false, List.of());
    }
  }
}
