package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;

/** Evaluates uncertainty and metadata facts for dynamic-resolution entries. */
public final class LlmDynamicResolutionEvaluator {

  private static final String EVIDENCE_VERIFIED_KEY = "verified";

  public boolean hasUncertainDynamicResolution(final MethodInfo method) {
    return hasOpenQuestionDynamicResolution(method) || hasKnownMissingDynamicResolution(method);
  }

  public boolean hasOpenQuestionDynamicResolution(final MethodInfo method) {
    if (method == null || method.getDynamicResolutions() == null) {
      return false;
    }
    for (final DynamicResolution resolution : method.getDynamicResolutions()) {
      if (isResolutionOpenQuestion(resolution)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasKnownMissingDynamicResolution(final MethodInfo method) {
    if (method == null || method.getDynamicResolutions() == null) {
      return false;
    }
    for (final DynamicResolution resolution : method.getDynamicResolutions()) {
      if (isResolutionKnownMissing(resolution)) {
        return true;
      }
    }
    return false;
  }

  public boolean isResolutionUncertain(final DynamicResolution resolution) {
    return isResolutionKnownMissing(resolution) || isResolutionOpenQuestion(resolution);
  }

  public boolean isResolutionKnownMissing(final DynamicResolution resolution) {
    if (resolution == null) {
      return false;
    }
    return resolution.reasonCode() == DynamicReasonCode.TARGET_METHOD_MISSING;
  }

  public boolean isResolutionOpenQuestion(final DynamicResolution resolution) {
    if (resolution == null) {
      return false;
    }
    if (isResolutionKnownMissing(resolution)) {
      return false;
    }
    if (isReasonCodeOpenQuestion(resolution.reasonCode())) {
      return true;
    }
    // Legacy fallback for historical analysis outputs without reason_code.
    final String verified = readVerifiedFlag(resolution);
    if ("false".equalsIgnoreCase(verified)) {
      return true;
    }
    final TrustLevel trustLevel = resolution.trustLevel();
    if ("true".equalsIgnoreCase(verified)
        && trustLevel == TrustLevel.HIGH
        && resolution.candidates().size() <= 1) {
      return false;
    }
    if (resolution.confidence() < 1.0) {
      return true;
    }
    if (trustLevel != null && trustLevel != TrustLevel.HIGH) {
      return true;
    }
    return resolution.candidates().size() > 1;
  }

  private boolean isReasonCodeOpenQuestion(final DynamicReasonCode reasonCode) {
    if (reasonCode == null) {
      return false;
    }
    return switch (reasonCode) {
      case UNSUPPORTED_EXPRESSION,
              AMBIGUOUS_CANDIDATES,
              DEPTH_LIMIT_EXCEEDED,
              CANDIDATE_LIMIT_EXCEEDED,
              UNRESOLVED_DEPENDENCY,
              TARGET_CLASS_UNRESOLVED ->
          true;
      case TARGET_METHOD_MISSING -> false;
    };
  }

  public String readVerifiedFlag(final DynamicResolution resolution) {
    final String verified = resolution.evidence().get(EVIDENCE_VERIFIED_KEY);
    if (verified == null || verified.isBlank()) {
      return "n/a";
    }
    return verified;
  }
}
