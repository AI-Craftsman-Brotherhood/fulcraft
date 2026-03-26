package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmListStructureNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sanitizes raw LLM output and normalizes document formatting for downstream validation. */
public final class LlmGeneratedDocumentPostProcessor {

  private static final Pattern METADATA_LINE_PATTERN =
      Pattern.compile("^(\\s*-\\s*(?:パッケージ|Package|ファイルパス|File Path)\\s*[:：]\\s*)(.+?)\\s*$");

  private static final Pattern CLASS_TYPE_METADATA_LINE_PATTERN =
      Pattern.compile("^(\\s*-\\s*)クラスの種別(\\s*[:：].*)$");

  private static final Pattern DEPENDENCY_SUBSECTION_HEADING_LINE_PATTERN =
      Pattern.compile("^####\\s+3\\.\\d+\\.6\\s+(?:依存呼び出し|Dependencies)\\s*$");

  private static final Pattern METHOD_SUBSECTION_HEADING_LINE_PATTERN =
      Pattern.compile("^####\\s+3\\.\\d+\\.\\d+\\s+.*$");

  private static final Pattern BULLET_LINE_PATTERN = Pattern.compile("^(\\s*-\\s*)(.+?)\\s*$");

  private static final Pattern REDUNDANT_BULLET_PREFIX_PATTERN =
      Pattern.compile("^(\\s*)-(?:\\s*-)+(?:\\s*(.*?))\\s*$");

  private static final Pattern MALFORMED_INLINE_INPUT_NONE_LINE_PATTERN =
      Pattern.compile(
          "^(\\s*)-\\s*((?:入力/出力|入出力|入力|Inputs/Outputs|Inputs?|Input))\\s*[:：]\\s*-\\s*(なし|None)\\s*$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern TOP_LEVEL_SECTION_HEADING_PATTERN =
      Pattern.compile("^##\\s+([1-9]\\d*)\\.");

  private final LlmListStructureNormalizer listStructureNormalizer =
      new LlmListStructureNormalizer();

  public String sanitize(final String response) {
    return normalizeGeneratedDocument(cleanupResponse(response));
  }

  private String cleanupResponse(final String response) {
    if (response == null) {
      return "";
    }
    String cleaned = response.trim();
    if (cleaned.startsWith("```")) {
      final int firstNewline = cleaned.indexOf('\n');
      if (firstNewline >= 0) {
        cleaned = cleaned.substring(firstNewline + 1);
      } else {
        cleaned = cleaned.substring(3);
      }
    }
    final int lastFence = cleaned.lastIndexOf("```");
    if (lastFence >= 0) {
      cleaned = cleaned.substring(0, lastFence);
    }
    return cleaned.trim();
  }

  private String normalizeGeneratedDocument(final String document) {
    if (document == null || document.isBlank()) {
      return "";
    }
    final String orderedListNormalized =
        listStructureNormalizer.normalizeOrderedListLines(document);
    final String[] lines = orderedListNormalized.split("\\R", -1);
    final List<String> normalizedLines = new ArrayList<>(lines.length);
    int currentTopLevelSection = 0;
    boolean inDependencySubsection = false;
    for (final String line : lines) {
      String normalized = normalizeMetadataLine(line);
      normalized = normalizeClassTypeLabel(normalized);
      normalized = normalizeRedundantBulletPrefix(normalized);
      normalized = normalizeInlineInputNoneLine(normalized);
      final Integer topLevelSection = resolveTopLevelSection(normalized);
      if (topLevelSection != null) {
        currentTopLevelSection = topLevelSection;
        inDependencySubsection = false;
      } else {
        inDependencySubsection =
            resolveDependencySubsectionState(normalized, inDependencySubsection);
        normalized = normalizeStandaloneNoneLine(normalized, currentTopLevelSection);
        if (inDependencySubsection) {
          normalized = normalizeDependencyReferenceLine(normalized);
        }
      }
      normalizedLines.add(normalized);
    }
    return String.join("\n", normalizedLines).strip();
  }

  private String normalizeMetadataLine(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final Matcher matcher = METADATA_LINE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return line;
    }
    final String prefix = matcher.group(1);
    final String value = matcher.group(2).strip();
    if (value.isBlank() || (value.startsWith("`") && value.endsWith("`"))) {
      return line;
    }
    return prefix + "`" + value.replace("`", "") + "`";
  }

  private String normalizeClassTypeLabel(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final Matcher matcher = CLASS_TYPE_METADATA_LINE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return line;
    }
    return matcher.group(1) + "クラス種別" + matcher.group(2);
  }

  private String normalizeRedundantBulletPrefix(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final Matcher matcher = REDUNDANT_BULLET_PREFIX_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return line;
    }
    final String indent = matcher.group(1);
    final String value = matcher.group(2) == null ? "" : matcher.group(2).strip();
    if (value.isBlank()) {
      return "";
    }
    return indent + "- " + value;
  }

  private String normalizeInlineInputNoneLine(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final Matcher matcher = MALFORMED_INLINE_INPUT_NONE_LINE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return line;
    }
    final String indent = matcher.group(1);
    final String label = matcher.group(2);
    final String noneToken = matcher.group(3) == null ? "" : matcher.group(3).strip();
    final String noneValue = "none".equalsIgnoreCase(noneToken) ? "None" : "なし";
    return indent + "- " + label + ": " + noneValue;
  }

  private Integer resolveTopLevelSection(final String line) {
    if (line == null || line.isBlank()) {
      return null;
    }
    final Matcher matcher = TOP_LEVEL_SECTION_HEADING_PATTERN.matcher(line.strip());
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String normalizeStandaloneNoneLine(final String line, final int currentTopLevelSection) {
    if (line == null || line.isBlank()) {
      return line;
    }
    if (currentTopLevelSection == 6) {
      final String normalizedOpenQuestionsNoneLine = normalizeOpenQuestionsNoneLine(line);
      if (!normalizedOpenQuestionsNoneLine.equals(line)) {
        return normalizedOpenQuestionsNoneLine;
      }
    }
    if (currentTopLevelSection < 4 || currentTopLevelSection > 6) {
      return line;
    }
    final String trimmed = line.strip();
    if ("なし".equals(trimmed)) {
      return leadingWhitespace(line) + "- なし";
    }
    if ("none".equalsIgnoreCase(trimmed)) {
      return leadingWhitespace(line) + "- None";
    }
    return line;
  }

  private String normalizeOpenQuestionsNoneLine(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final String trimmed = line.strip();
    final String withoutBullet = trimmed.replaceFirst("^-\\s*", "");
    final String normalized = LlmDocumentTextUtils.normalizeLine(withoutBullet);
    if (normalized.isBlank()) {
      return line;
    }
    if (normalized.contains("未確定事項はなし")
        || normalized.contains("未確定事項はない")
        || normalized.contains("未確定事項はありません")) {
      return leadingWhitespace(line) + "- なし";
    }
    if (normalized.startsWith("no open questions")
        || (normalized.contains("open questions")
            && (normalized.contains("none") || normalized.contains("no open question")))
        || (normalized.contains("open questions")
            && normalized.contains("none")
            && (normalized.contains("no method")
                || normalized.contains("without method")
                || normalized.contains("no methods")
                || normalized.contains("without methods")))) {
      return leadingWhitespace(line) + "- None";
    }
    return line;
  }

  private boolean resolveDependencySubsectionState(final String line, final boolean currentState) {
    if (line == null || line.isBlank()) {
      return currentState;
    }
    final String trimmed = line.strip();
    if (trimmed.startsWith("## ")) {
      return false;
    }
    if (DEPENDENCY_SUBSECTION_HEADING_LINE_PATTERN.matcher(trimmed).matches()) {
      return true;
    }
    if (METHOD_SUBSECTION_HEADING_LINE_PATTERN.matcher(trimmed).matches()) {
      return false;
    }
    return currentState;
  }

  private String normalizeDependencyReferenceLine(final String line) {
    if (line == null || line.isBlank()) {
      return line;
    }
    final Matcher matcher = BULLET_LINE_PATTERN.matcher(line);
    if (!matcher.matches()) {
      return line;
    }
    final String prefix = matcher.group(1);
    final String value = matcher.group(2).strip();
    if (value.isBlank() || LlmDocumentTextUtils.isNoneMarker(value)) {
      return line;
    }
    if (value.startsWith("`") && value.endsWith("`")) {
      return line;
    }
    if (!looksLikeDependencyReference(value)) {
      return line;
    }
    return prefix + "`" + value.replace("`", "") + "`";
  }

  private boolean looksLikeDependencyReference(final String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    final String token = value.strip();
    if (token.contains(":") || token.contains("：")) {
      return false;
    }
    if (token.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*")) {
      return false;
    }
    if (!token.matches("[A-Za-z0-9_.$#<>,()\\[\\]\\-\\s?]+")) {
      return false;
    }
    return token.contains("#") || token.contains("(") || token.contains(".");
  }

  private String leadingWhitespace(final String line) {
    int index = 0;
    while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
      index++;
    }
    return line.substring(0, index);
  }
}
