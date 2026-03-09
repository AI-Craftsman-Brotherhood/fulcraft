package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;

/** Shared metadata keys produced by the run phase. */
public final class RunMetadataKeys {

  public static final String FLAKY_TEST_SUMMARY_KEY = "flakyTestSummary";

  public static final String LLM_ENABLED = "run.llm.enabled";

  public static final String LLM_STAGE_FLAGS = "run.llm.stageFlags";

  private RunMetadataKeys() {}

  public static String llmStageKey(final String stageName) {
    final String normalizedStageName = PipelineNodeIds.normalizeRequired(stageName, "stageName");
    final String keySuffix = PipelineNodeIds.classifyPhase(normalizedStageName);
    return "run.llm.stage." + keySuffix;
  }
}
