package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmCalledMethodFilter;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.PromptInputCanonicalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Formats method-level prompt facts for LLM input. */
public final class LlmMethodsInfoFormatter {

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private static final int CALLED_METHODS_PREVIEW_LIMIT = 12;

  private static final int BRANCH_PREVIEW_LIMIT = 10;

  private static final int REPRESENTATIVE_PATH_PREVIEW_LIMIT = 10;

  private static final int SOURCE_CODE_FULL_PREVIEW_MAX_LINES = 80;

  private static final int SOURCE_CODE_HEAD_PREVIEW_LINES = 28;

  private static final int SOURCE_CODE_TAIL_PREVIEW_LINES = 20;

  private static final int DYNAMIC_RESOLUTION_PREVIEW_LIMIT = 8;

  private final MessageResolver messageResolver;

  private final LlmCalledMethodFilter calledMethodFilter;

  private final Predicate<DynamicResolution> resolutionOpenQuestionChecker;

  private final Predicate<DynamicResolution> resolutionKnownMissingChecker;

  private final Function<DynamicResolution, String> verifiedFlagReader;

  public LlmMethodsInfoFormatter(
      final MessageResolver messageResolver,
      final LlmCalledMethodFilter calledMethodFilter,
      final Predicate<DynamicResolution> resolutionUncertainChecker,
      final Function<DynamicResolution, String> verifiedFlagReader) {
    this(
        messageResolver,
        calledMethodFilter,
        resolutionUncertainChecker,
        resolution -> false,
        verifiedFlagReader);
  }

  public LlmMethodsInfoFormatter(
      final MessageResolver messageResolver,
      final LlmCalledMethodFilter calledMethodFilter,
      final Predicate<DynamicResolution> resolutionOpenQuestionChecker,
      final Predicate<DynamicResolution> resolutionKnownMissingChecker,
      final Function<DynamicResolution, String> verifiedFlagReader) {
    this.messageResolver = messageResolver;
    this.calledMethodFilter = calledMethodFilter;
    this.resolutionOpenQuestionChecker = resolutionOpenQuestionChecker;
    this.resolutionKnownMissingChecker = resolutionKnownMissingChecker;
    this.verifiedFlagReader = verifiedFlagReader;
  }

  public String buildMethodsInfo(
      final List<MethodInfo> methods, final LlmValidationFacts validationFacts) {
    if (methods.isEmpty()) {
      return msg("document.value.no_methods");
    }
    final StringBuilder sb = new StringBuilder();
    final var sortedMethods = PromptInputCanonicalizer.sortMethods(methods);
    for (final MethodInfo method : sortedMethods) {
      sb.append("\n#### ").append(method.getName()).append("\n");
      sb.append("- ")
          .append(msg("document.label.signature"))
          .append(": `")
          .append(nullSafe(method.getSignature()))
          .append("`\n");
      sb.append("- ")
          .append(msg("document.label.visibility"))
          .append(": ")
          .append(DocumentUtils.translateVisibility(method.getVisibility()))
          .append("\n");
      sb.append("- ")
          .append(msg("document.summary.static"))
          .append(": ")
          .append(method.isStatic())
          .append("\n");
      sb.append("- ")
          .append(msg("document.label.lines"))
          .append(": ")
          .append(method.getLoc())
          .append("\n");
      sb.append("- ")
          .append(msg("document.label.complexity"))
          .append(": ")
          .append(method.getCyclomaticComplexity())
          .append(" (")
          .append(DocumentUtils.getComplexityLabel(method.getCyclomaticComplexity()))
          .append(")\n");
      sb.append("- ")
          .append(msg("document.label.nesting_depth"))
          .append(": ")
          .append(method.getMaxNestingDepth())
          .append("\n");
      sb.append("- ")
          .append(msg("document.label.parameters"))
          .append(": ")
          .append(method.getParameterCount())
          .append("\n");
      sb.append("- ")
          .append(msg("document.label.usage_count"))
          .append(": ")
          .append(method.getUsageCount())
          .append("\n");
      appendMethodIndicators(sb, method);
      appendBranchSummaryInfo(sb, method);
      appendRepresentativePathsInfo(sb, method);
      appendCalledMethodsInfo(sb, method, validationFacts);
      appendExceptionsInfo(sb, method);
      appendDynamicResolutionsInfo(sb, method);
      appendSourceCodePreview(sb, method);
    }
    return sb.toString();
  }

  private void appendMethodIndicators(final StringBuilder sb, final MethodInfo method) {
    if (method.hasLoops()) {
      sb.append("- ")
          .append(msg("document.label.loop"))
          .append(": ")
          .append(msg("document.value.yes"))
          .append("\n");
    }
    if (method.hasConditionals()) {
      sb.append("- ")
          .append(msg("document.label.conditional"))
          .append(": ")
          .append(msg("document.value.yes"))
          .append("\n");
    }
    if (method.isDeadCode()) {
      sb.append("- ⚠️ ").append(msg("document.llm.indicator.dead_code")).append("\n");
    }
    if (method.isDuplicate()) {
      sb.append("- ⚠️ ").append(msg("document.llm.indicator.duplicate")).append("\n");
    }
    if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
      sb.append("- ⚠️ ").append(msg("document.warning.high_complexity")).append("\n");
    }
    if (method.isUsesRemovedApis()) {
      sb.append("- ⚠️ ").append(msg("document.signal.removed_api")).append("\n");
    }
    if (method.isPartOfCycle()) {
      sb.append("- ⚠️ ").append(msg("document.signal.cycle")).append("\n");
    }
  }

  private void appendCalledMethodsInfo(
      final StringBuilder sb, final MethodInfo method, final LlmValidationFacts validationFacts) {
    final List<String> calledMethods =
        calledMethodFilter.filterCalledMethodsForSpecificationWithArgumentLiterals(
            method, validationFacts);
    if (!calledMethods.isEmpty()) {
      sb.append("- ").append(msg("document.label.called_methods")).append(": ");
      final int count = Math.min(CALLED_METHODS_PREVIEW_LIMIT, calledMethods.size());
      for (int i = 0; i < count; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("`").append(calledMethods.get(i)).append("`");
      }
      if (calledMethods.size() > CALLED_METHODS_PREVIEW_LIMIT) {
        sb.append(" ")
            .append(
                msg(
                    "document.list.more.inline",
                    calledMethods.size() - CALLED_METHODS_PREVIEW_LIMIT));
      }
      sb.append("\n");
    }
  }

  private void appendExceptionsInfo(final StringBuilder sb, final MethodInfo method) {
    if (!method.getThrownExceptions().isEmpty()) {
      sb.append("- ")
          .append(msg("document.label.exceptions"))
          .append(": `")
          .append(PromptInputCanonicalizer.sortAndJoin(method.getThrownExceptions(), "`, `"))
          .append("`\n");
    }
  }

  private void appendDynamicResolutionsInfo(final StringBuilder sb, final MethodInfo method) {
    final List<DynamicResolution> resolutions = method.getDynamicResolutions();
    if (resolutions.isEmpty()) {
      return;
    }
    sb.append("- dynamic_resolutions:\n");
    sb.append("  - trust_high: ").append(method.getDynamicFeatureHigh()).append("\n");
    sb.append("  - trust_medium: ").append(method.getDynamicFeatureMedium()).append("\n");
    sb.append("  - trust_low: ").append(method.getDynamicFeatureLow()).append("\n");
    sb.append("  - has_service_loader: ")
        .append(method.hasDynamicFeatureServiceLoader())
        .append("\n");
    final List<DynamicResolution> sorted = new ArrayList<>(resolutions);
    sorted.sort(
        Comparator.comparing(
                (DynamicResolution resolution) ->
                    resolution.subtype() == null ? "" : resolution.subtype())
            .thenComparingInt(DynamicResolution::lineStart));
    final int previewCount = Math.min(DYNAMIC_RESOLUTION_PREVIEW_LIMIT, sorted.size());
    for (int i = 0; i < previewCount; i++) {
      final DynamicResolution resolution = sorted.get(i);
      sb.append("  - ");
      sb.append("subtype=").append(nullSafe(resolution.subtype()));
      sb.append(", line=").append(resolution.lineStart());
      sb.append(", confidence=").append(formatConfidence(resolution.confidence()));
      final TrustLevel trustLevel =
          resolution.trustLevel() != null
              ? resolution.trustLevel()
              : TrustLevel.fromConfidence(resolution.confidence());
      sb.append(", trust=").append(trustLevel.name());
      sb.append(", verified=").append(verifiedFlagReader.apply(resolution));
      sb.append(", status=").append(resolveResolutionStatus(resolution));
      final String resolvedMethod =
          LlmDocumentTextUtils.normalizeMethodName(
              LlmDocumentTextUtils.extractMethodName(resolution.resolvedMethodSig()));
      if (!resolvedMethod.isEmpty()) {
        sb.append(", resolved_method=").append(resolvedMethod);
      }
      if (!resolution.candidates().isEmpty()) {
        sb.append(", candidates=");
        sb.append("`").append(String.join("`, `", resolution.candidates())).append("`");
      }
      sb.append("\n");
    }
    if (sorted.size() > DYNAMIC_RESOLUTION_PREVIEW_LIMIT) {
      sb.append("  - ... ")
          .append(
              msg("document.list.more.inline", sorted.size() - DYNAMIC_RESOLUTION_PREVIEW_LIMIT))
          .append("\n");
    }
  }

  private void appendBranchSummaryInfo(final StringBuilder sb, final MethodInfo method) {
    final BranchSummary summary = method.getBranchSummary();
    if (summary == null) {
      return;
    }
    final List<String> entries = new ArrayList<>();
    for (final GuardSummary guard : summary.getGuards()) {
      if (guard == null) {
        continue;
      }
      final String condition = nullSafe(guard.getCondition());
      final String type = guard.getType() != null ? guard.getType().name() : "LEGACY";
      entries.add("guard(" + type + "): " + condition);
    }
    for (final String switchExpr : summary.getSwitches()) {
      entries.add(msg("document.label.switch") + ": " + nullSafe(switchExpr));
    }
    for (final String predicate : summary.getPredicates()) {
      entries.add(msg("document.label.predicate") + ": " + nullSafe(predicate));
    }
    if (entries.isEmpty()) {
      return;
    }
    sb.append("- ").append(msg("document.md.section.branches")).append(": ");
    final int count = Math.min(BRANCH_PREVIEW_LIMIT, entries.size());
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(" / ");
      }
      sb.append(entries.get(i));
    }
    if (entries.size() > BRANCH_PREVIEW_LIMIT) {
      sb.append(" ")
          .append(msg("document.list.more.inline", entries.size() - BRANCH_PREVIEW_LIMIT));
    }
    sb.append("\n");
  }

  private void appendRepresentativePathsInfo(final StringBuilder sb, final MethodInfo method) {
    final List<RepresentativePath> paths = method.getRepresentativePaths();
    if (paths.isEmpty()) {
      return;
    }
    sb.append("- ").append(msg("document.md.section.representative_paths")).append(": ");
    final int count = Math.min(REPRESENTATIVE_PATH_PREVIEW_LIMIT, paths.size());
    for (int i = 0; i < count; i++) {
      final RepresentativePath path = paths.get(i);
      if (i > 0) {
        sb.append(" / ");
      }
      if (path == null) {
        sb.append("[")
            .append(msg("document.value.na"))
            .append("] ")
            .append(msg("document.value.na"))
            .append(" -> ")
            .append(msg("document.value.na"));
        continue;
      }
      sb.append("[")
          .append(nullSafe(path.getId()))
          .append("] ")
          .append(nullSafe(path.getDescription()))
          .append(" -> ")
          .append(nullSafe(path.getExpectedOutcomeHint()));
    }
    if (paths.size() > REPRESENTATIVE_PATH_PREVIEW_LIMIT) {
      sb.append(" ")
          .append(
              msg("document.list.more.inline", paths.size() - REPRESENTATIVE_PATH_PREVIEW_LIMIT));
    }
    sb.append("\n");
  }

  private void appendSourceCodePreview(final StringBuilder sb, final MethodInfo method) {
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    if (sourceCode == null || sourceCode.isBlank()) {
      return;
    }
    final String[] lines = sourceCode.strip().split("\\R");
    if (lines.length == 0) {
      return;
    }
    sb.append("- ").append(msg("document.md.section.source_code")).append(":\n");
    if (lines.length <= SOURCE_CODE_FULL_PREVIEW_MAX_LINES) {
      for (final String line : lines) {
        sb.append("  ").append(line).append("\n");
      }
      return;
    }
    final int headCount = Math.min(SOURCE_CODE_HEAD_PREVIEW_LINES, lines.length);
    for (int i = 0; i < headCount; i++) {
      sb.append("  ").append(lines[i]).append("\n");
    }
    final int tailStart = Math.max(headCount, lines.length - SOURCE_CODE_TAIL_PREVIEW_LINES);
    final int omittedCount = tailStart - headCount;
    if (omittedCount > 0) {
      sb.append("  ... ").append(msg("document.list.more.inline", omittedCount)).append("\n");
    }
    for (int i = tailStart; i < lines.length; i++) {
      sb.append("  ").append(lines[i]).append("\n");
    }
  }

  private String formatConfidence(final double confidence) {
    return String.format(java.util.Locale.ROOT, "%.2f", confidence);
  }

  private String resolveResolutionStatus(final DynamicResolution resolution) {
    if (resolution == null) {
      return "CONFIRMED";
    }
    if (resolutionKnownMissingChecker.test(resolution)) {
      return "KNOWN_MISSING";
    }
    if (resolutionOpenQuestionChecker.test(resolution)) {
      return "UNCONFIRMED";
    }
    return "CONFIRMED";
  }

  private String nullSafe(final String value) {
    return value == null ? "" : value;
  }

  private String msg(final String key, final Object... args) {
    return messageResolver.resolve(key, args);
  }

  @FunctionalInterface
  public interface MessageResolver {

    String resolve(String key, Object... args);
  }
}
