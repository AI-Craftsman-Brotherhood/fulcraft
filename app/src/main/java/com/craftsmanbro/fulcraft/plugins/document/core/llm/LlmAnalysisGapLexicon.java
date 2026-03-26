package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import java.util.regex.Pattern;

/** Shared lexicon for analysis-gap statements used in generation and validation. */
public final class LlmAnalysisGapLexicon {

  private static final String EN_GAP_SUFFIX =
      "not\\s+provided"
          + "|not\\s+included"
          + "|insufficient"
          + "|missing"
          + "|unknown"
          + "|cannot\\s+determine"
          + "|not\\s+fully\\s+(?:available|visible|shown)"
          + "|beyond\\s+the\\s+provided\\s+excerpt"
          + "|in\\s+the\\s+excerpt"
          + "|provided\\s+source\\s+excerpt"
          + "|provided\\s+excerpt";

  private static final String EN_EXCERPT_CONTEXT =
      "(?:(?:not\\s+fully\\s+(?:available|visible|shown)|cannot\\s+determine|insufficient|missing|unknown)"
          + "[^\\n\\r]{0,96}(?:provided\\s+source\\s+excerpt|provided\\s+excerpt|source\\s+excerpt|excerpt)"
          + "|(?:provided\\s+source\\s+excerpt|provided\\s+excerpt|source\\s+excerpt|excerpt)"
          + "[^\\n\\r]{0,96}(?:not\\s+fully\\s+(?:available|visible|shown)|cannot\\s+determine|insufficient|missing|unknown))";

  private static final Pattern ANALYSIS_GAP_PATTERN =
      Pattern.compile(
          "(?i)("
              + "解析情報なし"
              + "|none\\s+specified\\s+in\\s+analysis\\s+data"
              + "|none\\s+indicated\\s+in\\s+analysis\\s+data"
              + "|no\\s+analysis\\s+data"
              + "|解析情報[^\\n\\r]{0,72}(含まれていない|提示されていない|不足|不明|特定不可|特定できない|判断できない)"
              + "|ソースコード[^\\n\\r]{0,72}(含まれていない|提示されていない|不足|不明)"
              + "|抜粋[^\\n\\r]{0,72}(不足|不明|見えない|見えず|確認できない|未提示|特定できない)"
              + "|analysis[^\\n\\r]{0,96}("
              + EN_GAP_SUFFIX
              + ")"
              + "|source[^\\n\\r]{0,96}("
              + EN_GAP_SUFFIX
              + ")"
              + "|excerpt[^\\n\\r]{0,72}("
              + EN_GAP_SUFFIX
              + ")"
              + "|"
              + EN_EXCERPT_CONTEXT
              + ")");

  private LlmAnalysisGapLexicon() {}

  public static boolean containsAnalysisGapStatement(final String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return ANALYSIS_GAP_PATTERN.matcher(text).find();
  }

  public static boolean isAnalysisGapLine(final String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(line).replaceFirst("^-\\s*", "");
    if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
      return false;
    }
    return containsAnalysisGapStatement(normalized);
  }

  public static Pattern analysisGapPattern() {
    return ANALYSIS_GAP_PATTERN;
  }
}
