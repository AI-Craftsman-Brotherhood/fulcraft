package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MethodKeyUtil;
import com.craftsmanbro.fulcraft.plugins.analysis.core.model.MethodId;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric.BranchSummaryExtractor;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric.BranchSummaryResult;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric.MethodDerivedMetricsComputer;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError.Severity;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ResultMerger {

  private final MethodSignatureNormalizer signatureNormalizer;

  private final MethodDerivedMetricsComputer derivedMetricsComputer;

  private final BranchSummaryExtractor branchSummaryExtractor;

  public ResultMerger() {
    this(
        new MethodSignatureNormalizer(),
        new MethodDerivedMetricsComputer(),
        new BranchSummaryExtractor());
  }

  public ResultMerger(
      final MethodSignatureNormalizer signatureNormalizer,
      final MethodDerivedMetricsComputer derivedMetricsComputer,
      final BranchSummaryExtractor branchSummaryExtractor) {
    this.signatureNormalizer = Objects.requireNonNull(signatureNormalizer);
    this.derivedMetricsComputer = Objects.requireNonNull(derivedMetricsComputer);
    this.branchSummaryExtractor = Objects.requireNonNull(branchSummaryExtractor);
  }

  public AnalysisResult merge(final AnalysisResult primary, final AnalysisResult secondary) {
    if (primary == null && secondary == null) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("analysis.result_merger.error.both_null"));
    }
    if (primary == null) {
      computeDerivedMetrics(secondary);
      return secondary;
    }
    if (secondary == null) {
      computeDerivedMetrics(primary);
      return primary;
    }
    final AnalysisResult merged = initializeMergedResult(primary);
    mergeErrors(merged, primary, secondary);
    mergeClasses(merged, primary, secondary);
    computeDerivedMetrics(merged);
    return merged;
  }

  private AnalysisResult initializeMergedResult(final AnalysisResult primary) {
    final AnalysisResult merged = new AnalysisResult();
    merged.setProjectId(primary.getProjectId());
    merged.setCommitHash(primary.getCommitHash());
    merged.setClasses(new ArrayList<>());
    merged.setAnalysisErrors(new ArrayList<>());
    return merged;
  }

  private void computeDerivedMetrics(final AnalysisResult result) {
    for (final ClassInfo cls : result.getClasses()) {
      for (final MethodInfo method : cls.getMethods()) {
        ensureMethodId(cls, method);
        computeDerivedMetricsForMethod(result, cls, method);
      }
    }
    dedupeAnalysisErrors(result);
  }

  private void ensureMethodId(final ClassInfo cls, final MethodInfo method) {
    if (method.getMethodId() == null || method.getMethodId().isBlank()) {
      final MethodId id = signatureNormalizer.toMethodId(cls.getFqn(), method);
      method.setMethodId(id.toString());
    }
  }

  private void computeDerivedMetricsForMethod(
      final AnalysisResult result, final ClassInfo cls, final MethodInfo method) {
    derivedMetricsComputer
        .compute(method, cls.getFilePath())
        .ifPresent(error -> addAnalysisError(result, error));
    final BranchSummaryResult branchResult = branchSummaryExtractor.compute(method);
    applyBranchSummary(method, branchResult);
    handleBranchParseErrors(result, cls, method, branchResult);
  }

  private void applyBranchSummary(final MethodInfo method, final BranchSummaryResult branchResult) {
    branchResult.branchSummary().ifPresent(method::setBranchSummary);
    method.setRepresentativePaths(branchResult.representativePaths());
  }

  private void handleBranchParseErrors(
      final AnalysisResult result,
      final ClassInfo cls,
      final MethodInfo method,
      final BranchSummaryResult branchResult) {
    if (branchResult.branchSummary().isPresent()) {
      dropParseErrorsForMethod(result, method.getMethodId());
    }
    branchResult
        .parseError()
        .ifPresent(msg -> addBranchSummaryError(result, cls, method, branchResult, msg));
  }

  private void addBranchSummaryError(
      final AnalysisResult result,
      final ClassInfo cls,
      final MethodInfo method,
      final BranchSummaryResult branchResult,
      final String message) {
    final Severity severity =
        branchResult.branchSummary().isPresent() ? Severity.WARN : Severity.ERROR;
    final String suffix = branchResult.usedFallback() ? " (fallback_used=true)" : "";
    final String filePath = cls.getFilePath() == null ? "" : cls.getFilePath();
    addAnalysisError(
        result,
        new AnalysisError(filePath, message + suffix, null, method.getMethodId(), severity));
  }

  private void dropParseErrorsForMethod(final AnalysisResult result, final String methodId) {
    if (methodId == null || methodId.isBlank()) {
      return;
    }
    final List<AnalysisError> filtered =
        result.getAnalysisErrors().stream()
            .filter(
                e ->
                    !(methodId.equals(e.methodId())
                        && e.message() != null
                        && e.message().contains("Unable to parse method for branch summary")))
            .toList();
    result.setAnalysisErrors(new ArrayList<>(filtered));
  }

  private void addAnalysisError(final AnalysisResult result, final AnalysisError error) {
    final List<AnalysisError> errors = new ArrayList<>(result.getAnalysisErrors());
    if (containsError(errors, error)) {
      return;
    }
    errors.add(error);
    result.setAnalysisErrors(errors);
  }

  private boolean containsError(final List<AnalysisError> errors, final AnalysisError candidate) {
    return errors.stream()
        .anyMatch(
            e ->
                Objects.equals(e.filePath(), candidate.filePath())
                    && Objects.equals(e.methodId(), candidate.methodId())
                    && Objects.equals(e.message(), candidate.message()));
  }

  private void mergeErrors(
      final AnalysisResult merged, final AnalysisResult primary, final AnalysisResult secondary) {
    merged.getAnalysisErrors().addAll(primary.getAnalysisErrors());
    merged.getAnalysisErrors().addAll(secondary.getAnalysisErrors());
    dedupeAnalysisErrors(merged);
  }

  private void mergeClasses(
      final AnalysisResult merged, final AnalysisResult primary, final AnalysisResult secondary) {
    final Map<String, ClassInfo> primaryClassMap = new HashMap<>();
    for (final ClassInfo cls : primary.getClasses()) {
      normalizeClassMethods(cls);
      final String classKey = safeFqn(cls);
      primaryClassMap.put(classKey, cls);
      merged.getClasses().add(cls);
    }
    for (final ClassInfo secondaryClass : secondary.getClasses()) {
      normalizeClassMethods(secondaryClass);
      final String classKey = safeFqn(secondaryClass);
      if (primaryClassMap.containsKey(classKey)) {
        mergeMethods(primaryClassMap.get(classKey), secondaryClass);
      } else {
        merged.getClasses().add(secondaryClass);
      }
    }
  }

  private void normalizeClassMethods(final ClassInfo cls) {
    if (cls == null) {
      return;
    }
    for (final MethodInfo method : cls.getMethods()) {
      final MethodId methodId = signatureNormalizer.toMethodId(cls.getFqn(), method);
      method.setMethodId(methodId.toString());
      method.setRawSignatures(mergeRawSignatures(method.getRawSignatures(), method.getSignature()));
    }
  }

  private void mergeMethods(final ClassInfo target, final ClassInfo source) {
    final List<MethodInfo> targetMethods = new ArrayList<>(target.getMethods());
    final Map<MethodId, MethodInfo> targetMethodsById = new LinkedHashMap<>();
    for (final MethodInfo method : targetMethods) {
      final MethodId methodId = signatureNormalizer.toMethodId(target.getFqn(), method);
      method.setMethodId(methodId.toString());
      method.setRawSignatures(mergeRawSignatures(method.getRawSignatures(), method.getSignature()));
      targetMethodsById.put(methodId, method);
    }
    for (final MethodInfo incoming : source.getMethods()) {
      final MethodId methodId = signatureNormalizer.toMethodId(source.getFqn(), incoming);
      incoming.setMethodId(methodId.toString());
      incoming.setRawSignatures(
          mergeRawSignatures(incoming.getRawSignatures(), incoming.getSignature()));
      final MethodInfo existing = targetMethodsById.get(methodId);
      if (existing != null) {
        mergeMethodDetails(existing, incoming);
      } else {
        targetMethods.add(incoming);
        targetMethodsById.put(methodId, incoming);
      }
    }
    target.setMethods(targetMethods);
    target.setMethodCount(targetMethods.size());
  }

  private void mergeMethodDetails(final MethodInfo primary, final MethodInfo secondary) {
    applyMethodIdFallback(primary, secondary);
    mergeMethodCollections(primary, secondary);
    mergeBranchSummaryAndPaths(primary, secondary);
    mergeComplexityMetrics(primary, secondary);
    mergeSourceDetails(primary, secondary);
    mergeFlags(primary, secondary);
    mergeOptionalFields(primary, secondary);
    mergeBooleanCharacteristics(primary, secondary);
  }

  private void applyMethodIdFallback(final MethodInfo primary, final MethodInfo secondary) {
    if (primary.getMethodId() == null) {
      primary.setMethodId(secondary.getMethodId());
    }
  }

  private void mergeMethodCollections(final MethodInfo primary, final MethodInfo secondary) {
    primary.setAnnotations(mergeLists(primary.getAnnotations(), secondary.getAnnotations()));
    primary.setThrownExceptions(
        mergeThrownExceptions(primary.getThrownExceptions(), secondary.getThrownExceptions()));
    primary.setRawSignatures(
        mergeRawSignatures(primary.getRawSignatures(), secondary.getRawSignatures()));
    primary.setCalledMethodRefs(
        mergeCalledMethodRefs(primary.getCalledMethodRefs(), secondary.getCalledMethodRefs()));
  }

  private void mergeBranchSummaryAndPaths(final MethodInfo primary, final MethodInfo secondary) {
    if (primary.getBranchSummary() == null && secondary.getBranchSummary() != null) {
      primary.setBranchSummary(secondary.getBranchSummary());
    }
    if (primary.getRepresentativePaths().isEmpty()
        && !secondary.getRepresentativePaths().isEmpty()) {
      primary.setRepresentativePaths(secondary.getRepresentativePaths());
    }
  }

  private void mergeComplexityMetrics(final MethodInfo primary, final MethodInfo secondary) {
    if (secondary.getCyclomaticComplexity() > 0) {
      primary.setCyclomaticComplexity(secondary.getCyclomaticComplexity());
    }
    if (secondary.getMaxNestingDepth() > 0) {
      primary.setMaxNestingDepth(secondary.getMaxNestingDepth());
    }
  }

  private void mergeSourceDetails(final MethodInfo primary, final MethodInfo secondary) {
    if (primary.getLoc() <= 0 && secondary.getLoc() > 0) {
      primary.setLoc(secondary.getLoc());
    }
    if ((primary.getSourceCode() == null || primary.getSourceCode().isBlank())
        && secondary.getSourceCode() != null
        && !secondary.getSourceCode().isBlank()) {
      primary.setSourceCode(secondary.getSourceCode());
    }
  }

  private void mergeFlags(final MethodInfo primary, final MethodInfo secondary) {
    primary.setUsesRemovedApis(primary.isUsesRemovedApis() || secondary.isUsesRemovedApis());
    primary.setDuplicate(primary.isDuplicate() || secondary.isDuplicate());
    primary.setPartOfCycle(primary.isPartOfCycle() || secondary.isPartOfCycle());
    primary.setDeadCode(primary.isDeadCode() || secondary.isDeadCode());
  }

  private void mergeOptionalFields(final MethodInfo primary, final MethodInfo secondary) {
    if (primary.getDuplicateGroup() == null) {
      primary.setDuplicateGroup(secondary.getDuplicateGroup());
    }
    if (primary.getCodeHash() == null || primary.getCodeHash().isBlank()) {
      primary.setCodeHash(secondary.getCodeHash());
    }
    if (primary.getParameterCount() == 0 && secondary.getParameterCount() > 0) {
      primary.setParameterCount(secondary.getParameterCount());
    }
    if (primary.getVisibility() == null || primary.getVisibility().isBlank()) {
      primary.setVisibility(secondary.getVisibility());
    }
  }

  private void mergeBooleanCharacteristics(final MethodInfo primary, final MethodInfo secondary) {
    // Merge boolean flags from either analyzer (OR semantics for static, loops,
    // conditionals)
    primary.setStatic(primary.isStatic() || secondary.isStatic());
    primary.setHasLoops(primary.hasLoops() || secondary.hasLoops());
    primary.setHasConditionals(primary.hasConditionals() || secondary.hasConditionals());
  }

  private List<String> mergeLists(final List<String> current, final List<String> incoming) {
    final LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (current != null) {
      merged.addAll(current);
    }
    if (incoming != null) {
      merged.addAll(incoming);
    }
    return new ArrayList<>(merged);
  }

  private List<String> mergeThrownExceptions(
      final List<String> current, final List<String> incoming) {
    final LinkedHashSet<String> ordered = new LinkedHashSet<>();
    addNormalizedStrings(ordered, current);
    addNormalizedStrings(ordered, incoming);
    final LinkedHashSet<String> javaLangSimpleNames = new LinkedHashSet<>();
    for (final String exceptionName : ordered) {
      if (exceptionName.startsWith("java.lang.")) {
        javaLangSimpleNames.add(simpleName(exceptionName).toLowerCase(Locale.ROOT));
      }
    }
    final List<String> merged = new ArrayList<>();
    for (final String exceptionName : ordered) {
      if (!exceptionName.contains(".")
          && javaLangSimpleNames.contains(exceptionName.toLowerCase(Locale.ROOT))) {
        continue;
      }
      merged.add(exceptionName);
    }
    return merged;
  }

  private void addNormalizedStrings(final LinkedHashSet<String> target, final List<String> values) {
    if (target == null || values == null) {
      return;
    }
    for (final String value : values) {
      if (value == null) {
        continue;
      }
      final String normalized = value.trim();
      if (!normalized.isEmpty()) {
        target.add(normalized);
      }
    }
  }

  private String simpleName(final String fqcn) {
    if (fqcn == null || fqcn.isBlank()) {
      return "";
    }
    final int lastDot = fqcn.lastIndexOf('.');
    return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
  }

  private List<String> mergeRawSignatures(final List<String> current, final String signature) {
    if (signature == null || signature.isBlank()) {
      return mergeRawSignatures(current, List.of());
    }
    return mergeRawSignatures(current, List.of(signature));
  }

  private List<String> mergeRawSignatures(final List<String> current, final List<String> incoming) {
    final LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (current != null) {
      current.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(merged::add);
    }
    if (incoming != null) {
      incoming.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(merged::add);
    }
    return new ArrayList<>(merged);
  }

  private String safeFqn(final ClassInfo cls) {
    if (cls == null) {
      return "";
    }
    if (cls.getFqn() != null && !cls.getFqn().isBlank()) {
      return cls.getFqn();
    }
    if (cls.getFilePath() != null && !cls.getFilePath().isBlank()) {
      return "file:" + cls.getFilePath();
    }
    return "unknown:" + System.identityHashCode(cls);
  }

  private List<CalledMethodRef> mergeCalledMethodRefs(
      final List<CalledMethodRef> primary, final List<CalledMethodRef> secondary) {
    final Map<String, CalledMethodRef> bySignature = new LinkedHashMap<>();
    mergeCalledMethodRefsInto(bySignature, primary);
    mergeCalledMethodRefsInto(bySignature, secondary);
    return new ArrayList<>(bySignature.values());
  }

  private void mergeCalledMethodRefsInto(
      final Map<String, CalledMethodRef> accumulator, final List<CalledMethodRef> incoming) {
    if (incoming == null) {
      return;
    }
    for (final CalledMethodRef ref : incoming) {
      if (ref == null) {
        continue;
      }
      final String key = callSignature(ref);
      final CalledMethodRef existing = accumulator.get(key);
      if (existing == null) {
        accumulator.put(key, cloneRef(ref));
      } else {
        accumulator.put(key, mergeRef(existing, ref));
      }
    }
  }

  private CalledMethodRef mergeRef(final CalledMethodRef base, final CalledMethodRef incoming) {
    // Prefer resolved target if available
    final boolean baseResolved = base.getResolved() != null && !base.getResolved().isBlank();
    final boolean incomingResolved =
        incoming.getResolved() != null && !incoming.getResolved().isBlank();
    if (baseResolved && incomingResolved) {
      // Use normalized comparison to handle Inner Class delimiter differences ($ vs
      // .)
      final String baseNorm = normalizeSignature(base.getResolved());
      final String incomingNorm = normalizeSignature(incoming.getResolved());
      if (Objects.equals(baseNorm, incomingNorm)) {
        final CalledMethodRef merged = cloneRef(base);
        merged.setArgumentLiterals(
            mergeStringLists(base.getArgumentLiterals(), incoming.getArgumentLiterals()));
        return merged;
      }
      final CalledMethodRef merged = cloneRef(base);
      merged.setStatus(ResolutionStatus.AMBIGUOUS);
      merged.setConfidence(0.5);
      final LinkedHashSet<String> candidates = new LinkedHashSet<>(base.getCandidates());
      if (base.getResolved() != null) {
        candidates.add(base.getResolved());
      }
      if (incoming.getResolved() != null) {
        candidates.add(incoming.getResolved());
      }
      merged.setCandidates(new ArrayList<>(candidates));
      merged.setResolved(null);
      merged.setSource("merge");
      merged.setArgumentLiterals(
          mergeStringLists(base.getArgumentLiterals(), incoming.getArgumentLiterals()));
      return merged;
    }
    if (incomingResolved && !baseResolved) {
      final CalledMethodRef merged = cloneRef(incoming);
      merged.setArgumentLiterals(
          mergeStringLists(base.getArgumentLiterals(), incoming.getArgumentLiterals()));
      return merged;
    }
    if (baseResolved) {
      final CalledMethodRef merged = cloneRef(base);
      merged.setArgumentLiterals(
          mergeStringLists(base.getArgumentLiterals(), incoming.getArgumentLiterals()));
      return merged;
    }
    // Neither resolved: keep base but merge metadata
    final CalledMethodRef merged = cloneRef(base);
    merged.setConfidence(Math.max(base.getConfidence(), incoming.getConfidence()));
    if (merged.getSource() == null) {
      merged.setSource(incoming.getSource());
    }
    merged.setArgumentLiterals(
        mergeStringLists(base.getArgumentLiterals(), incoming.getArgumentLiterals()));
    return merged;
  }

  private CalledMethodRef cloneRef(final CalledMethodRef ref) {
    final CalledMethodRef clone = new CalledMethodRef();
    clone.setRaw(ref.getRaw());
    clone.setResolved(ref.getResolved());
    clone.setStatus(ref.getStatus());
    clone.setConfidence(ref.getConfidence());
    clone.setSource(ref.getSource());
    clone.setCandidates(new ArrayList<>(ref.getCandidates()));
    clone.setArgumentLiterals(new ArrayList<>(ref.getArgumentLiterals()));
    return clone;
  }

  private List<String> mergeStringLists(final List<String> primary, final List<String> secondary) {
    final LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (primary != null) {
      for (final String value : primary) {
        if (value != null && !value.isBlank()) {
          merged.add(value.strip());
        }
      }
    }
    if (secondary != null) {
      for (final String value : secondary) {
        if (value != null && !value.isBlank()) {
          merged.add(value.strip());
        }
      }
    }
    return new ArrayList<>(merged);
  }

  private String callSignature(final CalledMethodRef ref) {
    final String target =
        ref.getResolved() != null && !ref.getResolved().isBlank()
            ? ref.getResolved()
            : ref.getRaw();
    if (target == null) {
      return "";
    }
    final String normalized = normalizeSignature(target);
    final int separatorIndex = normalized.indexOf(MethodKeyUtil.SEPARATOR);
    if (separatorIndex < 0) {
      return normalized;
    }
    final String methodPart = normalized.substring(separatorIndex + 1).trim();
    return methodPart.isEmpty() ? normalized : methodPart;
  }

  /**
   * Normalizes method signatures to handle differences like '$' vs '.' in inner classes. Only
   * replaces '$' when followed by a letter to protect anonymous classes (Outer$1) or synthetic
   * methods.
   */
  private String normalizeSignature(final String signature) {
    if (signature == null) {
      return null;
    }
    // Replace $ with . only if followed by a letter (likely a named inner class)
    final String normalized = signature.trim().replaceAll("\\$([A-Za-z])", ".$1");
    // Remove formatting-only whitespace differences in signatures.
    return normalized.replaceAll("\\s+", "");
  }

  private void dedupeAnalysisErrors(final AnalysisResult result) {
    final List<AnalysisError> existing = result.getAnalysisErrors();
    if (existing.isEmpty()) {
      return;
    }
    final Map<String, AnalysisError> deduped = new LinkedHashMap<>();
    for (final AnalysisError error : existing) {
      final String key = error.filePath() + "|" + error.methodId() + "|" + error.message();
      final AnalysisError current = deduped.get(key);
      if (current == null || severityRank(error.severity()) > severityRank(current.severity())) {
        deduped.put(key, error);
      }
    }
    result.setAnalysisErrors(new ArrayList<>(deduped.values()));
  }

  private int severityRank(final Severity severity) {
    if (severity == null) {
      return 0;
    }
    return switch (severity) {
      case INFO -> 1;
      case WARN -> 2;
      case ERROR -> 3;
    };
  }
}
