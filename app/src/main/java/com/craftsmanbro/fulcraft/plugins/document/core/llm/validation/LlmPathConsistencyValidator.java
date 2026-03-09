package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates section-level consistency for representative path IDs and outcomes. */
public final class LlmPathConsistencyValidator {

  private static final Pattern METHOD_BLOCK_PATTERN =
      Pattern.compile("(?s)(###\\s*3\\.\\d+\\s+.*?)(?=\\n###\\s*3\\.\\d+\\s+|\\n##\\s*4\\.|\\z)");

  private static final Pattern METHOD_HEADING_PATTERN =
      Pattern.compile("(?m)^###\\s*3\\.\\d+\\s+(.+?)\\s*$");

  private static final Pattern NORMAL_FLOW_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.4\\s+(?:正常フロー|Normal Flow)\\R(.*?)(?=####\\s+3\\.\\d+\\.5\\s+(?:異常・境界|Error/Boundary Handling)|\\z)");

  private static final Pattern ERROR_BOUNDARY_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.5\\s+(?:異常・境界|Error/Boundary Handling)\\R(.*?)(?=####\\s+3\\.\\d+\\.6\\s+(?:依存呼び出し|Dependencies)|\\z)");

  private static final Pattern POSTCONDITION_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.3\\s+(?:事後条件|Postconditions)\\R(.*?)(?=####\\s+3\\.\\d+\\.4\\s+(?:正常フロー|Normal Flow)|\\z)");

  private static final Pattern TEST_VIEWPOINT_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.7\\s+(?:テスト観点|Test Viewpoints)\\R(.*?)(?=###\\s*3\\.\\d+\\s+|##\\s*4\\.|\\z)");

  private static final Pattern REPRESENTATIVE_PATH_ID_PATTERN =
      Pattern.compile("\\[(path-[^\\]\\s]+)\\]", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_ID_TOKEN_PATTERN =
      Pattern.compile("\\b(path-[a-z0-9_-]+)\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_OUTCOME_ARROW_PATTERN = Pattern.compile("->\\s*(.+?)\\s*$");

  private static final Pattern PATH_OUTCOME_RESULT_PATTERN =
      Pattern.compile("結果\\s*[:：]\\s*(.+?)\\s*$");

  private static final Pattern PATH_OUTCOME_EN_PATTERN =
      Pattern.compile(
          "outcome\\s+for\\s+branch\\s+.+?[:：]\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

  private final String unavailableValue;

  public LlmPathConsistencyValidator() {
    this("n/a");
  }

  public LlmPathConsistencyValidator(final String unavailableValue) {
    this.unavailableValue = unavailableValue;
  }

  public void validate(final String document, final List<String> reasons, final boolean japanese) {
    validatePathSectionSeparation(document, reasons, japanese);
    validatePathOutcomeConsistency(document, reasons, japanese);
    validateTestViewpointPathCoverage(document, reasons, japanese);
  }

  private void validatePathSectionSeparation(
      final String document, final List<String> reasons, final boolean japanese) {
    final Matcher methodMatcher = METHOD_BLOCK_PATTERN.matcher(document);
    while (methodMatcher.find()) {
      final String methodBlock = methodMatcher.group(1);
      final Set<String> normalPathIds =
          extractRepresentativePathIds(methodBlock, NORMAL_FLOW_SECTION_PATTERN);
      if (normalPathIds.isEmpty()) {
        continue;
      }
      final Set<String> errorPathIds =
          extractRepresentativePathIds(methodBlock, ERROR_BOUNDARY_SECTION_PATTERN);
      if (errorPathIds.isEmpty()) {
        continue;
      }
      final Set<String> duplicates = new LinkedHashSet<>(normalPathIds);
      duplicates.retainAll(errorPathIds);
      if (duplicates.isEmpty()) {
        continue;
      }
      final String methodName = extractMethodHeadingName(methodBlock);
      final String joined = String.join(", ", duplicates);
      reasons.add(
          japanese
              ? msg("document.llm.path_consistency.duplicate_path.ja", joined, methodName)
              : msg("document.llm.path_consistency.duplicate_path.en", joined, methodName));
      return;
    }
  }

  private void validatePathOutcomeConsistency(
      final String document, final List<String> reasons, final boolean japanese) {
    final Matcher methodMatcher = METHOD_BLOCK_PATTERN.matcher(document);
    while (methodMatcher.find()) {
      final String methodBlock = methodMatcher.group(1);
      final String methodName = extractMethodHeadingName(methodBlock);
      final Map<String, String> postconditionOutcomes =
          extractPathOutcomes(methodBlock, POSTCONDITION_SECTION_PATTERN);
      if (postconditionOutcomes.isEmpty()) {
        continue;
      }
      final Map<String, String> normalFlowOutcomes =
          extractPathOutcomes(methodBlock, NORMAL_FLOW_SECTION_PATTERN);
      if (normalFlowOutcomes.isEmpty()) {
        continue;
      }
      for (final Map.Entry<String, String> entry : postconditionOutcomes.entrySet()) {
        final String pathId = entry.getKey();
        final String postconditionOutcome = entry.getValue();
        final String normalFlowOutcome = normalFlowOutcomes.get(pathId);
        if (postconditionOutcome == null
            || postconditionOutcome.isBlank()
            || normalFlowOutcome == null
            || normalFlowOutcome.isBlank()) {
          continue;
        }
        if (postconditionOutcome.equals(normalFlowOutcome)) {
          continue;
        }
        reasons.add(
            japanese
                ? msg(
                    "document.llm.path_consistency.outcome_mismatch.ja",
                    pathId,
                    postconditionOutcome,
                    normalFlowOutcome,
                    methodName)
                : msg(
                    "document.llm.path_consistency.outcome_mismatch.en",
                    pathId,
                    postconditionOutcome,
                    normalFlowOutcome,
                    methodName));
        return;
      }
    }
  }

  private void validateTestViewpointPathCoverage(
      final String document, final List<String> reasons, final boolean japanese) {
    final Matcher methodMatcher = METHOD_BLOCK_PATTERN.matcher(document);
    while (methodMatcher.find()) {
      final String methodBlock = methodMatcher.group(1);
      final String methodName = extractMethodHeadingName(methodBlock);
      final Set<String> expectedPathIds = new LinkedHashSet<>();
      expectedPathIds.addAll(extractPathIds(methodBlock, POSTCONDITION_SECTION_PATTERN));
      expectedPathIds.addAll(extractPathIds(methodBlock, NORMAL_FLOW_SECTION_PATTERN));
      expectedPathIds.addAll(extractPathIds(methodBlock, ERROR_BOUNDARY_SECTION_PATTERN));
      if (expectedPathIds.isEmpty()) {
        continue;
      }
      final Set<String> testViewpointPathIds =
          extractPathIds(methodBlock, TEST_VIEWPOINT_SECTION_PATTERN);
      if (testViewpointPathIds.containsAll(expectedPathIds)) {
        continue;
      }
      final Set<String> missing = new LinkedHashSet<>(expectedPathIds);
      missing.removeAll(testViewpointPathIds);
      if (missing.isEmpty()) {
        continue;
      }
      final String joinedMissing = String.join(", ", missing);
      reasons.add(
          japanese
              ? msg(
                  "document.llm.path_consistency.test_viewpoint_missing.ja",
                  joinedMissing,
                  methodName)
              : msg(
                  "document.llm.path_consistency.test_viewpoint_missing.en",
                  joinedMissing,
                  methodName));
      return;
    }
  }

  private Set<String> extractPathIds(final String methodBlock, final Pattern sectionPattern) {
    if (methodBlock == null || methodBlock.isBlank() || sectionPattern == null) {
      return Set.of();
    }
    final Matcher sectionMatcher = sectionPattern.matcher(methodBlock);
    if (!sectionMatcher.find()) {
      return Set.of();
    }
    return extractPathIdsFromText(sectionMatcher.group(1));
  }

  private Set<String> extractPathIdsFromText(final String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }
    final Set<String> ids = new LinkedHashSet<>();
    final Matcher matcher = PATH_ID_TOKEN_PATTERN.matcher(text);
    while (matcher.find()) {
      ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return ids;
  }

  private Map<String, String> extractPathOutcomes(
      final String methodBlock, final Pattern sectionPattern) {
    if (methodBlock == null || methodBlock.isBlank() || sectionPattern == null) {
      return Map.of();
    }
    final String section = extractMethodSubsection(methodBlock, sectionPattern);
    if (section.isBlank()) {
      return Map.of();
    }
    final Map<String, String> outcomes = new LinkedHashMap<>();
    for (final String line : section.split("\\R")) {
      final Set<String> ids = extractPathIdsFromText(line);
      if (ids.isEmpty()) {
        continue;
      }
      final String outcome = extractPathOutcomeFromLine(line);
      if (outcome.isBlank()) {
        continue;
      }
      for (final String id : ids) {
        outcomes.putIfAbsent(id, outcome);
      }
    }
    return outcomes;
  }

  private String extractPathOutcomeFromLine(final String line) {
    if (line == null || line.isBlank()) {
      return "";
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(line);
    final Matcher arrowMatcher = PATH_OUTCOME_ARROW_PATTERN.matcher(normalized);
    if (arrowMatcher.find()) {
      return normalizePathOutcomeLabel(arrowMatcher.group(1));
    }
    final Matcher resultMatcher = PATH_OUTCOME_RESULT_PATTERN.matcher(normalized);
    if (resultMatcher.find()) {
      return normalizePathOutcomeLabel(resultMatcher.group(1));
    }
    final Matcher englishMatcher = PATH_OUTCOME_EN_PATTERN.matcher(normalized);
    if (englishMatcher.find()) {
      return normalizePathOutcomeLabel(englishMatcher.group(1));
    }
    return "";
  }

  private String normalizePathOutcomeLabel(final String outcome) {
    if (outcome == null || outcome.isBlank()) {
      return "";
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(outcome).replaceFirst("[。.]$", "");
    if (normalized.isBlank()) {
      return "";
    }
    if (normalized.contains("failure結果オブジェクト")
        || normalized.contains("returns a failure result object")) {
      return "failure-result";
    }
    if (normalized.contains("early-return")
        || normalized.contains("early return")
        || normalized.contains("short-circuit")
        || normalized.contains("short circuit")) {
      return "early-return";
    }
    if (normalized.contains("boundary") || normalized.contains("境界")) {
      return "boundary";
    }
    if (normalized.contains("failure")
        || normalized.contains("fail")
        || normalized.contains("失敗")) {
      return "failure";
    }
    if (normalized.contains("success") || normalized.contains("成功")) {
      return "success";
    }
    return normalized;
  }

  private Set<String> extractRepresentativePathIds(
      final String methodBlock, final Pattern sectionPattern) {
    if (methodBlock == null || methodBlock.isBlank() || sectionPattern == null) {
      return Set.of();
    }
    final Matcher sectionMatcher = sectionPattern.matcher(methodBlock);
    if (!sectionMatcher.find()) {
      return Set.of();
    }
    final String sectionBody = sectionMatcher.group(1);
    final Set<String> ids = new LinkedHashSet<>();
    final Matcher idMatcher = REPRESENTATIVE_PATH_ID_PATTERN.matcher(sectionBody);
    while (idMatcher.find()) {
      ids.add(idMatcher.group(1).toLowerCase(Locale.ROOT));
    }
    return ids;
  }

  private String extractMethodHeadingName(final String methodBlock) {
    if (methodBlock == null || methodBlock.isBlank()) {
      return unavailableValue;
    }
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(methodBlock);
    if (!matcher.find()) {
      return unavailableValue;
    }
    return matcher.group(1).strip();
  }

  private String extractMethodSubsection(final String methodBlock, final Pattern sectionPattern) {
    if (methodBlock == null || methodBlock.isBlank() || sectionPattern == null) {
      return "";
    }
    final Matcher matcher = sectionPattern.matcher(methodBlock);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1).strip();
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
