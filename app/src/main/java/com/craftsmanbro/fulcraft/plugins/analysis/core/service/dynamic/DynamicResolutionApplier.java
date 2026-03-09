package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies resolved dynamic information to {@link MethodInfo}.
 *
 * <p>Dynamic resolutions are produced separately from core method analysis and then merged back
 * into in-memory model for downstream consumers such as document generation.
 */
public final class DynamicResolutionApplier {

  private static final String VERIFIED_EVIDENCE_KEY = "verified";

  private DynamicResolutionApplier() {}

  /**
   * Applies dynamic resolutions to matching methods in the analysis result.
   *
   * @param result analysis result to update
   * @param resolutions dynamic resolutions
   */
  public static void apply(final AnalysisResult result, final List<DynamicResolution> resolutions) {
    if (result == null || result.getClasses().isEmpty()) {
      return;
    }
    clearDynamicSignals(result);
    if (resolutions == null || resolutions.isEmpty()) {
      return;
    }
    for (final DynamicResolution resolution : resolutions) {
      if (resolution == null || isBlank(resolution.classFqn()) || isBlank(resolution.methodSig())) {
        continue;
      }
      final ClassInfo classInfo = findClass(result, resolution.classFqn());
      if (classInfo == null || classInfo.getMethods().isEmpty()) {
        continue;
      }
      final MethodInfo target = findMethod(classInfo, resolution.methodSig());
      if (target == null) {
        continue;
      }
      final List<DynamicResolution> updated = new ArrayList<>(target.getDynamicResolutions());
      updated.add(resolution);
      target.setDynamicResolutions(updated);
    }
    recomputeDynamicSignalCounters(result);
  }

  private static void clearDynamicSignals(final AnalysisResult result) {
    for (final ClassInfo classInfo : result.getClasses()) {
      for (final MethodInfo method : classInfo.getMethods()) {
        method.setDynamicResolutions(List.of());
        method.setDynamicFeatureHigh(0);
        method.setDynamicFeatureMedium(0);
        method.setDynamicFeatureLow(0);
        method.setDynamicFeatureHasServiceLoader(false);
      }
    }
  }

  private static void recomputeDynamicSignalCounters(final AnalysisResult result) {
    for (final ClassInfo classInfo : result.getClasses()) {
      for (final MethodInfo method : classInfo.getMethods()) {
        int high = 0;
        int medium = 0;
        int low = 0;
        boolean hasServiceLoader = false;
        for (final DynamicResolution resolution : method.getDynamicResolutions()) {
          final TrustLevel trustLevel = effectiveTrustLevel(resolution);
          switch (trustLevel) {
            case HIGH -> high++;
            case MEDIUM -> medium++;
            case LOW -> low++;
          }
          if (DynamicResolution.SERVICELOADER_PROVIDERS.equals(resolution.subtype())) {
            hasServiceLoader = true;
          }
        }
        method.setDynamicFeatureHigh(high);
        method.setDynamicFeatureMedium(medium);
        method.setDynamicFeatureLow(low);
        method.setDynamicFeatureHasServiceLoader(hasServiceLoader);
      }
    }
  }

  private static ClassInfo findClass(final AnalysisResult result, final String classFqn) {
    for (final ClassInfo classInfo : result.getClasses()) {
      if (classFqn.equals(classInfo.getFqn())) {
        return classInfo;
      }
    }
    return null;
  }

  private static MethodInfo findMethod(
      final ClassInfo classInfo, final String methodSigFromResolution) {
    for (final MethodInfo method : classInfo.getMethods()) {
      if (methodSigFromResolution.equals(method.getSignature())) {
        return method;
      }
    }
    final String normalizedTarget = normalizeSignature(methodSigFromResolution);
    for (final MethodInfo method : classInfo.getMethods()) {
      if (normalizedTarget.equals(normalizeSignature(method.getSignature()))) {
        return method;
      }
    }
    final String targetMethodName = extractMethodName(methodSigFromResolution);
    final int targetParamCount = extractParameterCount(methodSigFromResolution);
    if (isBlank(targetMethodName)) {
      return null;
    }
    MethodInfo fallback = null;
    for (final MethodInfo method : classInfo.getMethods()) {
      final String methodName = resolveMethodName(method);
      if (!targetMethodName.equals(methodName)) {
        continue;
      }
      if (targetParamCount >= 0 && method.getParameterCount() == targetParamCount) {
        return method;
      }
      if (fallback == null) {
        fallback = method;
      }
    }
    return fallback;
  }

  private static String resolveMethodName(final MethodInfo method) {
    if (method == null) {
      return "";
    }
    if (!isBlank(method.getName())) {
      return method.getName().strip();
    }
    return extractMethodName(method.getSignature());
  }

  private static String extractMethodName(final String signature) {
    if (isBlank(signature)) {
      return "";
    }
    final String normalized = signature.strip().replace('$', '.');
    final int openParen = normalized.indexOf('(');
    String left = openParen > 0 ? normalized.substring(0, openParen).trim() : normalized;
    final int space = left.lastIndexOf(' ');
    if (space >= 0 && space + 1 < left.length()) {
      left = left.substring(space + 1);
    }
    final int dot = left.lastIndexOf('.');
    if (dot >= 0 && dot + 1 < left.length()) {
      left = left.substring(dot + 1);
    }
    return left;
  }

  private static int extractParameterCount(final String signature) {
    if (isBlank(signature)) {
      return -1;
    }
    final String normalized = signature.strip();
    final int openParen = normalized.indexOf('(');
    final int closeParen = normalized.lastIndexOf(')');
    if (openParen < 0 || closeParen <= openParen) {
      return -1;
    }
    final String params = normalized.substring(openParen + 1, closeParen).trim();
    if (params.isEmpty()) {
      return 0;
    }
    return splitTopLevelCsv(params).size();
  }

  private static List<String> splitTopLevelCsv(final String params) {
    final List<String> tokens = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int genericDepth = 0;
    for (int i = 0; i < params.length(); i++) {
      final char ch = params.charAt(i);
      if (ch == '<') {
        genericDepth++;
      } else if (ch == '>' && genericDepth > 0) {
        genericDepth--;
      } else if (ch == ',' && genericDepth == 0) {
        tokens.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    if (!current.isEmpty()) {
      tokens.add(current.toString().trim());
    }
    return tokens;
  }

  private static String normalizeSignature(final String signature) {
    if (isBlank(signature)) {
      return "";
    }
    String normalized = signature.strip().replace('$', '.');
    normalized = normalized.replace("java.lang.", "");
    normalized = normalized.replaceAll("\\s+", "");
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static TrustLevel effectiveTrustLevel(final DynamicResolution resolution) {
    final TrustLevel base =
        resolution.trustLevel() != null
            ? resolution.trustLevel()
            : TrustLevel.fromConfidence(resolution.confidence());
    if (isVerifiedFalse(resolution)) {
      return TrustLevel.LOW;
    }
    return base;
  }

  private static boolean isVerifiedFalse(final DynamicResolution resolution) {
    if (resolution == null || resolution.evidence() == null) {
      return false;
    }
    final String verified = resolution.evidence().get(VERIFIED_EVIDENCE_KEY);
    return verified != null && "false".equalsIgnoreCase(verified.strip());
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
