package com.craftsmanbro.fulcraft.kernel.pipeline;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.List;
import java.util.Locale;

/** Canonical workflow node ids and shared node-id helpers. */
public final class PipelineNodeIds {

  public static final String ANALYZE = "analyze";

  public static final String GENERATE = "generate";

  public static final String REPORT = "report";

  public static final String DOCUMENT = "document";

  public static final String EXPLORE = "explore";

  public static final List<String> OFFICIAL_TOP_LEVEL =
      List.of(ANALYZE, GENERATE, REPORT, DOCUMENT, EXPLORE);

  private PipelineNodeIds() {}

  public static String normalizeRequired(final String nodeId, final String parameterName) {
    if (nodeId == null || nodeId.isBlank()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("kernel.pipeline.node_ids.error.blank", parameterName));
    }
    return nodeId.trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizeNullable(final String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      return null;
    }
    return nodeId.trim().toLowerCase(Locale.ROOT);
  }

  /** Maps arbitrary node ids to interceptor/config phases. */
  public static String classifyPhase(final String nodeId) {
    final String normalized = normalizeNullable(nodeId);
    if (normalized == null) {
      return null;
    }
    if (ANALYZE.equals(normalized)) {
      return ANALYZE;
    }
    if (REPORT.equals(normalized)) {
      return REPORT;
    }
    if (DOCUMENT.equals(normalized)) {
      return DOCUMENT;
    }
    if (EXPLORE.equals(normalized)) {
      return EXPLORE;
    }
    return GENERATE;
  }

  public static String llmStageKeySuffix(final String nodeId) {
    return classifyPhase(normalizeRequired(nodeId, "nodeId"));
  }
}
