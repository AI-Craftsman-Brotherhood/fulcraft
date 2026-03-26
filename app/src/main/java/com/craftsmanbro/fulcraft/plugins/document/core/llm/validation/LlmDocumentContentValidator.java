package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapInspector;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates non-path/non-method-section document constraints. */
public final class LlmDocumentContentValidator {

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private static final Pattern METHOD_HEADING_PATTERN =
      Pattern.compile("(?m)^###\\s*3\\.\\d+\\s+(.+?)\\s*$");

  private static final Pattern COMPLEXITY_NUMBER_PATTERN =
      Pattern.compile("(?i)(複雑度|complexity)[^\\n\\r]{0,32}?(\\d+)");

  private static final Pattern CLAIMED_METHOD_PATTERN_EN_BACKTICK =
      Pattern.compile("(?i)method\\s+`([A-Za-z_][A-Za-z0-9_]*)`");

  private static final Pattern CLAIMED_METHOD_PATTERN_EN_PLAIN =
      Pattern.compile(
          "(?i)method\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(?:must|is|exists|should|can|cannot|with)");

  private static final Pattern CLAIMED_METHOD_PATTERN_JA =
      Pattern.compile("`?([A-Za-z_][A-Za-z0-9_]*)`?\\s*メソッド");

  private static final Pattern CLAIMED_SIGNATURE_PATTERN =
      Pattern.compile("`?([A-Za-z_][A-Za-z0-9_$.]*)\\s*\\(([^\\r\\n)]*)\\)`?");

  private static final Pattern NESTED_CLASS_TRUE_PATTERN =
      Pattern.compile("(?i)(nested[_ ]class\\s*[:：]\\s*true|ネストクラス\\s*[:：]\\s*あり)");

  private static final Pattern NESTED_CLASS_FALSE_PATTERN =
      Pattern.compile("(?i)(nested[_ ]class\\s*[:：]\\s*false|ネストクラス\\s*[:：]\\s*なし)");

  private static final Pattern ENCLOSING_TYPE_NONE_PATTERN =
      Pattern.compile("(?i)(enclosing[_ ]type|parent\\s*class|親クラス)\\s*[:：]\\s*(none|なし|未設定|n/?a)");

  private static final Pattern NESTING_UNCERTAINTY_PATTERN =
      Pattern.compile(
          "(?i)(ネストクラス[^\\n\\r]{0,48}(未確定|不明|未設定)|nested\\s*class[^\\n\\r]{0,48}(uncertain|unknown|not\\s+clear)|enclosing[_ ]type[^\\n\\r]{0,48}(unknown|uncertain)|親クラス[^\\n\\r]{0,48}(未確定|不明|未設定))");

  private static final Pattern GENERIC_DYNAMIC_UNCERTAINTY_PATTERN =
      Pattern.compile(
          "(?i)(dynamic_resolutions[^\\n\\r]{0,96}(verified\\s*=\\s*false|confidence\\s*<\\s*1\\.0)|"
              + "`?dynamic_resolutions`?[^\\n\\r]{0,96}(未確定|不明)|"
              + "verified\\s*=\\s*false[^\\n\\r]{0,96}(未確定|不明)|"
              + "confidence\\s*<\\s*1\\.0[^\\n\\r]{0,96}(未確定|不明))");

  private static final List<String> REQUIRED_EXTERNAL_SPEC_LABELS_JA =
      List.of("クラス名", "パッケージ", "ファイルパス", "クラス種別", "継承", "実装インターフェース");

  private static final List<String> REQUIRED_EXTERNAL_SPEC_LABELS_EN =
      List.of("Class Name", "Package", "File Path", "Class Type", "Extends", "Implements");

  private final LlmAnalysisGapInspector analysisGapInspector = new LlmAnalysisGapInspector();

  public void validate(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    if (document == null || document.isBlank()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.output_empty.ja")
              : msg("document.llm.content_validation.output_empty.en"));
      return;
    }
    if (context == null) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.context_missing.ja")
              : msg("document.llm.content_validation.context_missing.en"));
      return;
    }
    validateMethodHeadings(document, context, reasons, japanese);
    validateExternalSpecificationSection(document, reasons, japanese);
    validateNestedMetadata(document, context, reasons, japanese);
    validateCautionSection(document, context, reasons, japanese);
    validateRecommendationsSection(document, reasons, japanese);
    validateInferenceMarkers(document, reasons, japanese);
    validateUncertainDynamicAssertions(document, context, reasons, japanese);
    validateKnownConstructorUncertaintyOutsideOpenQuestions(document, context, reasons, japanese);
    validateOpenQuestionConsistency(document, context, reasons, japanese);
    validateAnalysisGapOpenQuestionAlignment(document, reasons, japanese);
    validateUncertainDynamicMethodPlacement(document, context, reasons, japanese);
  }

  private void validateMethodHeadings(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    final List<String> actual = new ArrayList<>();
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(document);
    while (matcher.find()) {
      actual.add(LlmDocumentTextUtils.normalizeMethodName(matcher.group(1)));
    }
    final List<String> expectedMethodNames = context.methodNames();
    if (actual.size() != expectedMethodNames.size()) {
      reasons.add(
          japanese
              ? msg(
                  "document.llm.content_validation.method_heading_count_mismatch.ja",
                  expectedMethodNames.size(),
                  actual.size())
              : msg(
                  "document.llm.content_validation.method_heading_count_mismatch.en",
                  expectedMethodNames.size(),
                  actual.size()));
    }
    final Map<String, Integer> expectedCounts = countByName(expectedMethodNames);
    final Map<String, Integer> actualCounts = countByName(actual);
    for (final Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
      final String methodName = entry.getKey();
      final int expectedCount = entry.getValue();
      final int actualCount = actualCounts.getOrDefault(methodName, 0);
      if (actualCount < expectedCount) {
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.missing_method_heading.ja",
                    methodName,
                    expectedCount)
                : msg(
                    "document.llm.content_validation.missing_method_heading.en",
                    methodName,
                    expectedCount));
      }
    }
  }

  private void validateExternalSpecificationSection(
      final String document, final List<String> reasons, final boolean japanese) {
    final String section = extractSection(document, "## 2.", "## 3.");
    if (section.isBlank()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.external_section_empty.ja")
              : msg("document.llm.content_validation.external_section_empty.en"));
      return;
    }
    final List<String> missingJa =
        collectMissingMetadataLabels(section, REQUIRED_EXTERNAL_SPEC_LABELS_JA);
    final List<String> missingEn =
        collectMissingMetadataLabels(section, REQUIRED_EXTERNAL_SPEC_LABELS_EN);
    if (missingJa.isEmpty() || missingEn.isEmpty()) {
      return;
    }
    reasons.add(
        japanese
            ? msg(
                "document.llm.content_validation.external_missing_required.ja",
                String.join(", ", missingJa))
            : msg(
                "document.llm.content_validation.external_missing_required.en",
                String.join(", ", missingEn)));
  }

  private List<String> collectMissingMetadataLabels(
      final String section, final List<String> labels) {
    final List<String> missing = new ArrayList<>();
    if (labels == null || labels.isEmpty()) {
      return missing;
    }
    for (final String label : labels) {
      if (!containsMetadataLabel(section, label)) {
        missing.add(label);
      }
    }
    return missing;
  }

  private boolean containsMetadataLabel(final String section, final String label) {
    if (section == null || section.isBlank() || label == null || label.isBlank()) {
      return false;
    }
    final Pattern pattern =
        Pattern.compile("(?m)^\\s*-\\s*" + Pattern.quote(label) + "\\s*[:：]\\s*.+$");
    return pattern.matcher(section).find();
  }

  private void validateNestedMetadata(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    final String section = extractSection(document, "## 2.", "## 3.");
    if (section.isBlank()) {
      return;
    }
    final boolean nestedClaimedTrue = NESTED_CLASS_TRUE_PATTERN.matcher(section).find();
    final boolean nestedClaimedFalse = NESTED_CLASS_FALSE_PATTERN.matcher(section).find();
    final boolean enclosingNone = ENCLOSING_TYPE_NONE_PATTERN.matcher(section).find();
    if (context.nestedClass()) {
      if (nestedClaimedFalse) {
        reasons.add(
            japanese
                ? msg("document.llm.content_validation.nested_metadata_false_conflict.ja")
                : msg("document.llm.content_validation.nested_metadata_false_conflict.en"));
        return;
      }
      if (enclosingNone) {
        reasons.add(
            japanese
                ? msg("document.llm.content_validation.nested_metadata_enclosing_none_conflict.ja")
                : msg(
                    "document.llm.content_validation.nested_metadata_enclosing_none_conflict.en"));
      }
      return;
    }
    if (nestedClaimedTrue) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.nested_metadata_true_conflict.ja")
              : msg("document.llm.content_validation.nested_metadata_true_conflict.en"));
    }
  }

  private Map<String, Integer> countByName(final List<String> names) {
    final Map<String, Integer> counts = new HashMap<>();
    if (names == null) {
      return counts;
    }
    for (final String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      counts.merge(name, 1, (a, b) -> a + b);
    }
    return counts;
  }

  private void validateCautionSection(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    final String section = extractSection(document, "## 4.", "## 5.");
    if (section.isBlank()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.cautions_section_empty.ja")
              : msg("document.llm.content_validation.cautions_section_empty.en"));
      return;
    }
    final Matcher complexityMatcher = COMPLEXITY_NUMBER_PATTERN.matcher(section);
    while (complexityMatcher.find()) {
      final int complexity = Integer.parseInt(complexityMatcher.group(2));
      if (complexity < HIGH_COMPLEXITY_THRESHOLD) {
        reasons.add(
            japanese
                ? msg("document.llm.content_validation.cautions_low_complexity.ja", complexity)
                : msg("document.llm.content_validation.cautions_low_complexity.en", complexity));
        break;
      }
    }
    for (final String line : section.split("\\R")) {
      final String normalizedLine = LlmDocumentTextUtils.normalizeLine(line);
      if (normalizedLine.isEmpty()) {
        continue;
      }
      validateDeadCodeClaims(normalizedLine, context.deadCodeMethods(), reasons, japanese);
      validateDuplicateClaims(normalizedLine, context.duplicateMethods(), reasons, japanese);
    }
    if (!context.hasAnyCautions() && hasPositiveCautionClaim(section)) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.cautions_out_of_input.ja")
              : msg("document.llm.content_validation.cautions_out_of_input.en"));
    }
  }

  private void validateRecommendationsSection(
      final String document, final List<String> reasons, final boolean japanese) {
    final String section = extractSection(document, "## 5.", "## 6.");
    if (section.isBlank()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.recommendations_must_be_none.ja")
              : msg("document.llm.content_validation.recommendations_must_be_none.en"));
      return;
    }
    if (!isNoneOnlySection(section)) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.recommendations_disallowed_content.ja")
              : msg("document.llm.content_validation.recommendations_disallowed_content.en"));
    }
  }

  private void validateInferenceMarkers(
      final String document, final List<String> reasons, final boolean japanese) {
    final String lower = document.toLowerCase(Locale.ROOT);
    if (lower.contains("[推測]") || lower.contains("[inference]")) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.inference_markers_prohibited.ja")
              : msg("document.llm.content_validation.inference_markers_prohibited.en"));
    }
  }

  private void validateDeadCodeClaims(
      final String line,
      final Set<String> deadCodeMethods,
      final List<String> reasons,
      final boolean japanese) {
    if (!containsAffirmativeKeyword(line, "dead code", "デッドコード")) {
      return;
    }
    for (final String mentionedMethod : extractMentionedMethodNames(line)) {
      if (!deadCodeMethods.contains(mentionedMethod)) {
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.dead_code_claim_mismatch.ja", mentionedMethod)
                : msg(
                    "document.llm.content_validation.dead_code_claim_mismatch.en",
                    mentionedMethod));
        return;
      }
    }
  }

  private void validateDuplicateClaims(
      final String line,
      final Set<String> duplicateMethods,
      final List<String> reasons,
      final boolean japanese) {
    if (!containsAffirmativeKeyword(line, "duplicate", "重複")) {
      return;
    }
    for (final String mentionedMethod : extractMentionedMethodNames(line)) {
      if (!duplicateMethods.contains(mentionedMethod)) {
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.duplicate_claim_mismatch.ja", mentionedMethod)
                : msg(
                    "document.llm.content_validation.duplicate_claim_mismatch.en",
                    mentionedMethod));
        return;
      }
    }
  }

  private void validateUncertainDynamicAssertions(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    final Set<String> knownMethods = new LinkedHashSet<>(context.knownMethodNames());
    if (knownMethods.isEmpty()) {
      knownMethods.addAll(context.methodNames());
    }
    final String[] lines = document.split("\\R");
    for (final String rawLine : lines) {
      final String line = LlmDocumentTextUtils.normalizeLine(rawLine);
      if (line.isEmpty() || !isStrongAssertionLine(line) || containsUncertaintyMarker(line)) {
        continue;
      }
      for (final String uncertainMethod : context.uncertainDynamicMethodNames()) {
        if (!uncertainMethod.isBlank() && line.contains(uncertainMethod)) {
          reasons.add(
              japanese
                  ? msg(
                      "document.llm.content_validation.uncertain_dynamic_asserted.ja",
                      uncertainMethod)
                  : msg(
                      "document.llm.content_validation.uncertain_dynamic_asserted.en",
                      uncertainMethod));
          return;
        }
      }
      for (final String claimedMethod : extractClaimedMethodNamesFromAssertion(line)) {
        if (knownMethods.contains(claimedMethod)) {
          continue;
        }
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.external_method_asserted_without_uncertainty.ja",
                    claimedMethod)
                : msg(
                    "document.llm.content_validation.external_method_asserted_without_uncertainty.en",
                    claimedMethod));
        return;
      }
    }
  }

  private void validateKnownConstructorUncertaintyOutsideOpenQuestions(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    if (document == null
        || document.isBlank()
        || context.knownConstructorSignatures() == null
        || context.knownConstructorSignatures().isEmpty()) {
      return;
    }
    final int openQuestionsStart = document.indexOf("## 6.");
    final String mainSections =
        openQuestionsStart >= 0 ? document.substring(0, openQuestionsStart) : document;
    for (final String rawLine : mainSections.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank()
          || !containsConstructorKeyword(normalized)
          || !containsDefinitionUncertaintyMarker(normalized)) {
        continue;
      }
      for (final ConstructorSignatureClaim claim : extractClaimedConstructorSignatures(rawLine)) {
        if (!context.knownConstructorSignatures().contains(claim.normalized())) {
          continue;
        }
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.known_constructor_unresolved.ja",
                    claim.display())
                : msg(
                    "document.llm.content_validation.known_constructor_unresolved.en",
                    claim.display()));
        return;
      }
    }
  }

  private void validateOpenQuestionConsistency(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    final String section = extractSection(document, "## 6.", "## 7.");
    if (section.isBlank()) {
      return;
    }
    if (context.methodNames().isEmpty() && containsNonNoneOpenQuestionEntry(section)) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.methodless_open_questions_none_only.ja")
              : msg("document.llm.content_validation.methodless_open_questions_none_only.en"));
      return;
    }
    if (GENERIC_DYNAMIC_UNCERTAINTY_PATTERN.matcher(section).find()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.open_questions_generic_template.ja")
              : msg("document.llm.content_validation.open_questions_generic_template.en"));
      return;
    }
    if (!context.uncertainDynamicMethodDisplayNames().isEmpty()
        && !containsAnyMethodName(section, context.uncertainDynamicMethodDisplayNames())) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.open_questions_missing_candidates.ja")
              : msg("document.llm.content_validation.open_questions_missing_candidates.en"));
      return;
    }
    final String knownMissingMethodInOpenQuestions =
        findFirstContainedMethodName(section, context.knownMissingDynamicMethodDisplayNames());
    if (!knownMissingMethodInOpenQuestions.isBlank()) {
      reasons.add(
          japanese
              ? msg(
                  "document.llm.content_validation.open_questions_known_missing_method.ja",
                  knownMissingMethodInOpenQuestions)
              : msg(
                  "document.llm.content_validation.open_questions_known_missing_method.en",
                  knownMissingMethodInOpenQuestions));
      return;
    }
    validateKnownMethodOpenQuestionConsistency(section, context, reasons, japanese);
    if (!reasons.isEmpty()) {
      return;
    }
    if (!NESTING_UNCERTAINTY_PATTERN.matcher(section).find()) {
      return;
    }
    if (context.nestedClass()) {
      reasons.add(
          japanese
              ? msg("document.llm.content_validation.open_questions_nested_conflict_nested.ja")
              : msg("document.llm.content_validation.open_questions_nested_conflict_nested.en"));
      return;
    }
    reasons.add(
        japanese
            ? msg("document.llm.content_validation.open_questions_nested_conflict_non_nested.ja")
            : msg("document.llm.content_validation.open_questions_nested_conflict_non_nested.en"));
  }

  private void validateAnalysisGapOpenQuestionAlignment(
      final String document, final List<String> reasons, final boolean japanese) {
    if (document == null || document.isBlank()) {
      return;
    }
    final String openQuestions = extractSection(document, "## 6.", "## 7.");
    if (openQuestions.isBlank() || containsNonNoneOpenQuestionEntry(openQuestions)) {
      return;
    }
    if (!analysisGapInspector.hasGapInMainSections(document)) {
      return;
    }
    reasons.add(
        japanese
            ? msg("document.llm.content_validation.analysis_gap_outside_open_questions.ja")
            : msg("document.llm.content_validation.analysis_gap_outside_open_questions.en"));
  }

  private boolean containsNonNoneOpenQuestionEntry(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || isNoneEquivalentOpenQuestionLine(normalized)) {
        continue;
      }
      return true;
    }
    return false;
  }

  private boolean isNoneEquivalentOpenQuestionLine(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return true;
    }
    if (LlmDocumentTextUtils.isNoneMarker(normalizedLine)) {
      return true;
    }
    return normalizedLine.contains("未確定事項はなし")
        || normalizedLine.contains("未確定事項はない")
        || normalizedLine.contains("未確定事項はありません")
        || normalizedLine.startsWith("no open questions")
        || (normalizedLine.contains("open questions")
            && (normalizedLine.contains("none") || normalizedLine.contains("no open question")));
  }

  private void validateUncertainDynamicMethodPlacement(
      final String document,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    if (document == null || document.isBlank() || context.uncertainDynamicMethodNames().isEmpty()) {
      return;
    }
    final int openQuestionsStart = document.indexOf("## 6.");
    final String mainSections =
        openQuestionsStart >= 0 ? document.substring(0, openQuestionsStart) : document;
    for (final String uncertainMethod : context.uncertainDynamicMethodNames()) {
      if (uncertainMethod == null || uncertainMethod.isBlank()) {
        continue;
      }
      if (!LlmDocumentTextUtils.containsMethodToken(mainSections, uncertainMethod)) {
        continue;
      }
      reasons.add(
          japanese
              ? msg(
                  "document.llm.content_validation.uncertain_dynamic_outside_open_questions.ja",
                  uncertainMethod)
              : msg(
                  "document.llm.content_validation.uncertain_dynamic_outside_open_questions.en",
                  uncertainMethod));
      return;
    }
    final String openQuestionsSection = extractSection(document, "## 6.", "## 7.");
    if (openQuestionsSection.isBlank()) {
      return;
    }
    for (final String rawLine : openQuestionsSection.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      for (final String uncertainMethod : context.uncertainDynamicMethodNames()) {
        if (uncertainMethod == null
            || uncertainMethod.isBlank()
            || !LlmDocumentTextUtils.containsMethodToken(normalized, uncertainMethod)) {
          continue;
        }
        if (containsUncertaintyMarker(normalized) || isMethodExistenceQuestionLine(normalized)) {
          continue;
        }
        reasons.add(
            japanese
                ? msg(
                    "document.llm.content_validation.open_questions_uncertain_asserted.ja",
                    uncertainMethod)
                : msg(
                    "document.llm.content_validation.open_questions_uncertain_asserted.en",
                    uncertainMethod));
        return;
      }
    }
  }

  private void validateKnownMethodOpenQuestionConsistency(
      final String section,
      final ValidationContext context,
      final List<String> reasons,
      final boolean japanese) {
    if (section == null || section.isBlank()) {
      return;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if (isMethodExistenceQuestionLine(normalized) && !context.knownMethodNames().isEmpty()) {
        for (final String mentionedMethod : extractClaimedMethodNamesFromAssertion(rawLine)) {
          if (!context.knownMethodNames().contains(mentionedMethod)) {
            continue;
          }
          reasons.add(
              japanese
                  ? msg(
                      "document.llm.content_validation.open_questions_known_method_unresolved.ja",
                      mentionedMethod)
                  : msg(
                      "document.llm.content_validation.open_questions_known_method_unresolved.en",
                      mentionedMethod));
          return;
        }
      }
      if (isConstructorExistenceQuestionLine(normalized)
          && context.knownConstructorSignatures() != null
          && !context.knownConstructorSignatures().isEmpty()) {
        for (final ConstructorSignatureClaim claim : extractClaimedConstructorSignatures(rawLine)) {
          if (!context.knownConstructorSignatures().contains(claim.normalized())) {
            continue;
          }
          reasons.add(
              japanese
                  ? msg(
                      "document.llm.content_validation.known_constructor_unresolved.ja",
                      claim.display())
                  : msg(
                      "document.llm.content_validation.known_constructor_unresolved.en",
                      claim.display()));
          return;
        }
      }
    }
  }

  private boolean isMethodExistenceQuestionLine(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    return normalizedLine.contains("実在確認")
        || normalizedLine.contains("存在確認")
        || normalizedLine.contains("存在と詳細")
        || normalizedLine.contains("有無")
        || normalizedLine.contains("存在しない")
        || normalizedLine.contains("提示されていない")
        || normalizedLine.contains("明示なし")
        || normalizedLine.contains("記載なし")
        || normalizedLine.contains("記載がない")
        || normalizedLine.contains("未提示")
        || normalizedLine.contains("未確認")
        || normalizedLine.contains("確認が必要")
        || normalizedLine.contains("定義有無")
        || normalizedLine.contains("定義が不明")
        || normalizedLine.contains("含まれていない")
        || normalizedLine.contains("verify existence")
        || normalizedLine.contains("existence of")
        || normalizedLine.contains("whether")
        || normalizedLine.contains("must exist")
        || normalizedLine.contains("needs verification")
        || normalizedLine.contains("not provided")
        || normalizedLine.contains("not shown")
        || normalizedLine.contains("not available")
        || normalizedLine.contains("not included")
        || normalizedLine.contains("missing")
        || normalizedLine.contains("not documented")
        || normalizedLine.contains("undefined")
        || normalizedLine.contains("unknown")
        || normalizedLine.contains("unclear");
  }

  private boolean containsAnyMethodName(final String section, final Set<String> methodNames) {
    if (section == null || section.isBlank() || methodNames == null || methodNames.isEmpty()) {
      return false;
    }
    for (final String methodName : methodNames) {
      if (methodName == null || methodName.isBlank()) {
        continue;
      }
      if (LlmDocumentTextUtils.containsMethodToken(section, methodName)) {
        return true;
      }
    }
    return false;
  }

  private String findFirstContainedMethodName(final String section, final Set<String> methodNames) {
    if (section == null || section.isBlank() || methodNames == null || methodNames.isEmpty()) {
      return "";
    }
    for (final String methodName : methodNames) {
      if (methodName == null || methodName.isBlank()) {
        continue;
      }
      if (LlmDocumentTextUtils.containsMethodToken(section, methodName)) {
        return methodName;
      }
    }
    return "";
  }

  private Set<String> extractClaimedMethodNamesFromAssertion(final String line) {
    final Set<String> names = new LinkedHashSet<>();
    final Matcher enBacktick = CLAIMED_METHOD_PATTERN_EN_BACKTICK.matcher(line);
    while (enBacktick.find()) {
      names.add(enBacktick.group(1).toLowerCase(Locale.ROOT));
    }
    final Matcher enPlain = CLAIMED_METHOD_PATTERN_EN_PLAIN.matcher(line);
    while (enPlain.find()) {
      names.add(enPlain.group(1).toLowerCase(Locale.ROOT));
    }
    final Matcher ja = CLAIMED_METHOD_PATTERN_JA.matcher(line);
    while (ja.find()) {
      names.add(ja.group(1).toLowerCase(Locale.ROOT));
    }
    return names;
  }

  private Set<ConstructorSignatureClaim> extractClaimedConstructorSignatures(final String line) {
    final LinkedHashMap<String, String> normalizedToDisplay = new LinkedHashMap<>();
    final Matcher matcher = CLAIMED_SIGNATURE_PATTERN.matcher(line);
    while (matcher.find()) {
      final String rawName = matcher.group(1);
      final String rawParameters = matcher.group(2);
      final String normalized = normalizeConstructorSignature(rawName, rawParameters);
      if (normalized.isBlank()) {
        continue;
      }
      String displayName =
          rawName == null ? "" : rawName.strip().replace('$', '.').replace("`", "");
      final int dotIndex = displayName.lastIndexOf('.');
      if (dotIndex >= 0 && dotIndex + 1 < displayName.length()) {
        displayName = displayName.substring(dotIndex + 1);
      }
      final String parameterDisplay = rawParameters == null ? "" : rawParameters.strip();
      normalizedToDisplay.putIfAbsent(normalized, displayName + "(" + parameterDisplay + ")");
    }
    final Set<ConstructorSignatureClaim> claims = new LinkedHashSet<>();
    for (final Map.Entry<String, String> entry : normalizedToDisplay.entrySet()) {
      claims.add(new ConstructorSignatureClaim(entry.getKey(), entry.getValue()));
    }
    return claims;
  }

  private String normalizeConstructorSignature(final String rawName, final String rawParameters) {
    if (rawName == null || rawName.isBlank()) {
      return "";
    }
    String simpleName = rawName.strip().replace('$', '.').replace("`", "");
    final int dotIndex = simpleName.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < simpleName.length()) {
      simpleName = simpleName.substring(dotIndex + 1);
    }
    final String normalizedName = LlmDocumentTextUtils.normalizeMethodName(simpleName);
    if (normalizedName.isBlank()) {
      return "";
    }
    final List<String> parameterTypes = normalizeConstructorParameterTypes(rawParameters);
    return normalizedName + "(" + String.join(",", parameterTypes) + ")";
  }

  private List<String> normalizeConstructorParameterTypes(final String parameterSection) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return List.of();
    }
    final List<String> normalized = new ArrayList<>();
    for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(parameterSection)) {
      final String parameter = normalizeConstructorParameterType(token);
      if (!parameter.isBlank()) {
        normalized.add(parameter);
      }
    }
    return normalized;
  }

  private String normalizeConstructorParameterType(final String rawParameter) {
    if (rawParameter == null || rawParameter.isBlank()) {
      return "";
    }
    String normalized = rawParameter.strip().replace('$', '.').replace("`", "");
    normalized = normalized.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
    normalized = normalized.replaceAll("\\b(final|volatile|transient)\\b\\s*", "");
    final int lastSpace = normalized.lastIndexOf(' ');
    if (lastSpace > 0 && lastSpace + 1 < normalized.length()) {
      final String tail = normalized.substring(lastSpace + 1);
      if (isLikelyParameterName(tail)) {
        normalized = normalized.substring(0, lastSpace).trim();
      }
    }
    normalized = normalized.replace("...", "[]");
    normalized = eraseGenericArguments(normalized);
    normalized = simplifyQualifiedTypes(normalized);
    return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private String eraseGenericArguments(final String signature) {
    if (signature == null || signature.isBlank() || !signature.contains("<")) {
      return signature == null ? "" : signature;
    }
    final StringBuilder sb = new StringBuilder(signature.length());
    int depth = 0;
    for (int i = 0; i < signature.length(); i++) {
      final char ch = signature.charAt(i);
      if (ch == '<') {
        depth++;
        continue;
      }
      if (ch == '>' && depth > 0) {
        depth--;
        continue;
      }
      if (depth == 0) {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private String simplifyQualifiedTypes(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    final int length = value.length();
    int lastStart = 0;
    for (int i = 0; i < length; i++) {
      final char ch = value.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != '.') {
        if (i > lastStart) {
          sb.append(simplifyToken(value.substring(lastStart, i)));
        }
        sb.append(ch);
        lastStart = i + 1;
      }
    }
    if (lastStart < length) {
      sb.append(simplifyToken(value.substring(lastStart)));
    }
    return sb.toString();
  }

  private String simplifyToken(final String token) {
    if (token == null || token.isBlank() || !token.contains(".")) {
      return token == null ? "" : token;
    }
    final int lastDot = token.lastIndexOf('.');
    if (lastDot > 0 && lastDot < token.length() - 1) {
      return token.substring(lastDot + 1);
    }
    return token;
  }

  private boolean isLikelyParameterName(final String token) {
    if (token == null || token.isBlank() || !Character.isLowerCase(token.charAt(0))) {
      return false;
    }
    for (int i = 0; i < token.length(); i++) {
      final char ch = token.charAt(i);
      if (!Character.isLetterOrDigit(ch) && ch != '_') {
        return false;
      }
    }
    return true;
  }

  private boolean isConstructorExistenceQuestionLine(final String normalizedLine) {
    if (!containsConstructorKeyword(normalizedLine)) {
      return false;
    }
    return isMethodExistenceQuestionLine(normalizedLine)
        || containsDefinitionUncertaintyMarker(normalizedLine)
        || containsConstructorExistenceIntent(normalizedLine);
  }

  private boolean containsConstructorExistenceIntent(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    return normalizedLine.contains("定義")
        || normalizedLine.contains("存在")
        || normalizedLine.contains("有無")
        || normalizedLine.contains("確認")
        || normalizedLine.contains("exist")
        || normalizedLine.contains("defined")
        || normalizedLine.contains("whether");
  }

  private boolean containsConstructorKeyword(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    return normalizedLine.contains("コンストラクタ")
        || normalizedLine.contains("constructor")
        || normalizedLine.contains("ctor");
  }

  private boolean containsDefinitionUncertaintyMarker(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    return normalizedLine.contains("未確定")
        || normalizedLine.contains("不明")
        || normalizedLine.contains("明示なし")
        || normalizedLine.contains("記載なし")
        || normalizedLine.contains("記載がない")
        || normalizedLine.contains("未提示")
        || normalizedLine.contains("未確認")
        || normalizedLine.contains("確認が必要")
        || normalizedLine.contains("含まれていない")
        || normalizedLine.contains("提示されていない")
        || normalizedLine.contains("確定できない")
        || normalizedLine.contains("unknown")
        || normalizedLine.contains("uncertain")
        || normalizedLine.contains("unclear")
        || normalizedLine.contains("not confirmed")
        || normalizedLine.contains("cannot determine")
        || normalizedLine.contains("not provided")
        || normalizedLine.contains("not shown")
        || normalizedLine.contains("not available")
        || normalizedLine.contains("not included")
        || normalizedLine.contains("missing")
        || normalizedLine.contains("not documented")
        || normalizedLine.contains("undefined");
  }

  private Set<String> extractMentionedMethodNames(final String line) {
    final Set<String> names = new LinkedHashSet<>();
    final Matcher matcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)").matcher(line);
    while (matcher.find()) {
      final String token = matcher.group(1);
      if (token.length() <= 1 || Character.isUpperCase(token.charAt(0))) {
        continue;
      }
      names.add(token.toLowerCase(Locale.ROOT));
    }
    return names;
  }

  private String extractSection(
      final String document, final String startHeading, final String endHeadingPrefix) {
    final int start = document.indexOf(startHeading);
    if (start < 0) {
      return "";
    }
    final int from = document.indexOf('\n', start);
    if (from < 0) {
      return "";
    }
    int next = document.indexOf(endHeadingPrefix, from);
    if (next < 0) {
      next = document.length();
    }
    return document.substring(from + 1, next).strip();
  }

  private boolean hasPositiveCautionClaim(final String section) {
    final String normalized = LlmDocumentTextUtils.normalizeLine(section);
    if (normalized.isEmpty()) {
      return false;
    }
    if (containsNegation(normalized)) {
      return false;
    }
    return normalized.contains("デッドコード")
        || normalized.contains("重複")
        || normalized.contains("複雑度")
        || normalized.contains("dead code")
        || normalized.contains("duplicate")
        || normalized.contains("complexity");
  }

  private boolean containsAffirmativeKeyword(
      final String line, final String englishKeyword, final String japaneseKeyword) {
    if (!line.contains(englishKeyword) && !line.contains(japaneseKeyword)) {
      return false;
    }
    return !containsNegation(line);
  }

  private boolean containsNegation(final String line) {
    return line.contains("なし")
        || line.contains("ない")
        || line.contains("未検出")
        || line.contains("確認されていない")
        || line.contains("none")
        || line.contains("not ")
        || line.contains("no ");
  }

  private boolean isNoneOnlySection(final String section) {
    final List<String> normalizedLines = new ArrayList<>();
    for (final String line : section.split("\\R")) {
      final String normalized = LlmDocumentTextUtils.normalizeLine(line).replaceFirst("^-\\s*", "");
      if (!normalized.isBlank()) {
        normalizedLines.add(normalized);
      }
    }
    if (normalizedLines.size() != 1) {
      return false;
    }
    final String value = normalizedLines.get(0);
    return "なし".equals(value) || "none".equals(value);
  }

  private boolean isStrongAssertionLine(final String line) {
    return line.contains("定義されている")
        || line.contains("存在する")
        || line.contains("must exist")
        || line.contains("is defined")
        || line.contains("exists")
        || line.contains("declared");
  }

  private boolean containsUncertaintyMarker(final String line) {
    return line.contains("[推測]")
        || line.contains("[inference]")
        || line.contains("候補")
        || line.contains("未確定")
        || line.contains("candidate")
        || line.contains("uncertain");
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  public record ValidationContext(
      List<String> methodNames,
      Set<String> deadCodeMethods,
      Set<String> duplicateMethods,
      Set<String> uncertainDynamicMethodNames,
      Set<String> uncertainDynamicMethodDisplayNames,
      Set<String> knownMissingDynamicMethodNames,
      Set<String> knownMissingDynamicMethodDisplayNames,
      Set<String> knownMethodNames,
      Set<String> knownConstructorSignatures,
      boolean hasAnyCautions,
      boolean nestedClass) {}

  private record ConstructorSignatureClaim(String normalized, String display) {}
}
