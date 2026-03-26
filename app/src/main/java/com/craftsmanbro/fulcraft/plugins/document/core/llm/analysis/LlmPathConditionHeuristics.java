package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import java.util.Locale;

/** Heuristic checks for classifying flow conditions and error/boundary indicators. */
public final class LlmPathConditionHeuristics {

  public boolean isLikelyErrorIndicator(final String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    final String normalized = value.toLowerCase(Locale.ROOT).strip();
    if ((normalized.contains("boundary") || normalized.contains("境界"))
        && (normalized.contains("!= null")
            || normalized.contains("!=null")
            || normalized.contains("nullでない")
            || normalized.contains("null でない")
            || normalized.contains("nullではない")
            || normalized.contains("null ではない"))) {
      return false;
    }
    return normalized.contains("error")
        || normalized.contains("fail")
        || normalized.contains("exception")
        || normalized.contains("invalid")
        || normalized.contains("boundary")
        || normalized.contains("early-return")
        || normalized.contains("early return")
        || normalized.contains("short-circuit")
        || normalized.contains("short circuit")
        || normalized.contains("== null")
        || normalized.contains("==null")
        || normalized.contains(" is null")
        || normalized.contains("nullの場合")
        || normalized.contains("null の場合")
        || normalized.contains("cannot be null")
        || normalized.contains("must not be null")
        || normalized.contains("失敗")
        || normalized.contains("例外")
        || normalized.contains("不正")
        || normalized.contains("境界");
  }

  public boolean isLikelyFlowCondition(final String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    final String normalized = value.toLowerCase(Locale.ROOT).strip();
    return normalized.contains("switch case")
        || normalized.contains("case-")
        || normalized.contains("loop guard")
        || normalized.contains("loop-continue")
        || normalized.contains("loop-break")
        || normalized.contains("main success")
        || "success".equals(normalized)
        || normalized.contains("main success path");
  }
}
