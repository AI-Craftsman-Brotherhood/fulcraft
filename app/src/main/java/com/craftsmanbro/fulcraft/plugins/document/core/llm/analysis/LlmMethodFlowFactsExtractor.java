package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts source-backed flow facts used by fallback generation and validation. */
public final class LlmMethodFlowFactsExtractor {

  private static final Pattern SWITCH_HEADER_PATTERN =
      Pattern.compile("\\bswitch\\s*\\(([^)]+)\\)\\s*\\{");

  private static final Pattern CASE_LABEL_PATTERN =
      Pattern.compile("(?m)^\\s*case\\s+(.+?)\\s*(?::|->)");

  private static final Pattern DEFAULT_LABEL_PATTERN =
      Pattern.compile("(?m)^\\s*default\\s*(?::|->)");

  private static final Pattern RETURN_TOKEN_PATTERN = Pattern.compile("\\breturn\\b");

  private static final Pattern THROW_TOKEN_PATTERN = Pattern.compile("\\bthrow\\b");

  /**
   * Returns true when an early-return outcome is incompatible with the source.
   *
   * <p>An early-return label is considered incompatible if the method throws but has no explicit
   * return statement in the source.
   */
  public boolean isEarlyReturnIncompatible(final MethodInfo method) {
    final String sourceCode = stripSource(method);
    if (sourceCode.isBlank()) {
      return false;
    }
    if (!THROW_TOKEN_PATTERN.matcher(sourceCode).find()) {
      return false;
    }
    return !RETURN_TOKEN_PATTERN.matcher(sourceCode).find();
  }

  /** Collects switch-case facts from source code. */
  public List<SwitchCaseFact> collectSwitchCaseFacts(final MethodInfo method) {
    final String sourceCode = stripSource(method);
    if (sourceCode.isBlank()) {
      return List.of();
    }
    final Map<String, SwitchCaseFact> deduplicated = new LinkedHashMap<>();
    final Matcher switchMatcher = SWITCH_HEADER_PATTERN.matcher(sourceCode);
    while (switchMatcher.find()) {
      final String switchExpression = normalizeSwitchExpression(switchMatcher.group(1));
      final int openBraceIndex = switchMatcher.end() - 1;
      final int closeBraceIndex = findMatchingClosingBrace(sourceCode, openBraceIndex);
      if (closeBraceIndex < 0) {
        continue;
      }
      final String switchBody = sourceCode.substring(openBraceIndex + 1, closeBraceIndex);
      addCaseFacts(deduplicated, switchExpression, switchBody);
    }
    return new ArrayList<>(deduplicated.values());
  }

  private void addCaseFacts(
      final Map<String, SwitchCaseFact> deduplicated,
      final String switchExpression,
      final String switchBody) {
    if (switchBody == null || switchBody.isBlank()) {
      return;
    }
    final Matcher caseMatcher = CASE_LABEL_PATTERN.matcher(switchBody);
    while (caseMatcher.find()) {
      for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(caseMatcher.group(1))) {
        final String caseLabel = normalizeCaseLabel(token);
        if (caseLabel.isBlank()) {
          continue;
        }
        final String description = "Switch case " + switchExpression + "=\"" + caseLabel + "\"";
        final String expectedOutcome = "case-\"" + caseLabel + "\"";
        final String factId =
            "switch-" + sanitizeToken(switchExpression) + "-" + sanitizeToken(caseLabel);
        deduplicated.putIfAbsent(
            switchExpression.toLowerCase(Locale.ROOT) + "::" + caseLabel.toLowerCase(Locale.ROOT),
            new SwitchCaseFact(factId, description, expectedOutcome));
      }
    }
    if (!DEFAULT_LABEL_PATTERN.matcher(switchBody).find()) {
      return;
    }
    final String description = "Switch default " + switchExpression;
    final String expectedOutcome = "default";
    final String factId = "switch-" + sanitizeToken(switchExpression) + "-default";
    deduplicated.putIfAbsent(
        switchExpression.toLowerCase(Locale.ROOT) + "::default",
        new SwitchCaseFact(factId, description, expectedOutcome));
  }

  private String stripSource(final MethodInfo method) {
    if (method == null) {
      return "";
    }
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    return sourceCode == null ? "" : sourceCode;
  }

  private String normalizeSwitchExpression(final String expression) {
    if (expression == null || expression.isBlank()) {
      return "value";
    }
    String normalized = expression.strip().replaceAll("\\s+", " ");
    final int lastDot = normalized.lastIndexOf('.');
    if (lastDot >= 0 && lastDot + 1 < normalized.length()) {
      normalized = normalized.substring(lastDot + 1).strip();
    }
    return normalized.isBlank() ? "value" : normalized;
  }

  private String normalizeCaseLabel(final String rawLabel) {
    if (rawLabel == null || rawLabel.isBlank()) {
      return "";
    }
    String normalized = rawLabel.strip();
    if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
      normalized = normalized.substring(1, normalized.length() - 1);
    } else if (normalized.startsWith("'") && normalized.endsWith("'") && normalized.length() >= 2) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return normalized.replace("`", "").strip();
  }

  private int findMatchingClosingBrace(final String sourceCode, final int openBraceIndex) {
    if (sourceCode == null
        || sourceCode.isBlank()
        || openBraceIndex < 0
        || openBraceIndex >= sourceCode.length()
        || sourceCode.charAt(openBraceIndex) != '{') {
      return -1;
    }
    int depth = 1;
    for (int i = openBraceIndex + 1; i < sourceCode.length(); i++) {
      final char current = sourceCode.charAt(i);
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private String sanitizeToken(final String token) {
    if (token == null || token.isBlank()) {
      return "value";
    }
    final String sanitized =
        token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return sanitized.isBlank() ? "value" : sanitized;
  }

  public record SwitchCaseFact(String id, String description, String expectedOutcome) {}
}
