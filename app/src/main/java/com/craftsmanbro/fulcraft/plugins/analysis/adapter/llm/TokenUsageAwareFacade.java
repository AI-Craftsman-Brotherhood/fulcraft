package com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm;

/**
 * Analysis-layer facade for LLM clients that can report token usage.
 *
 * <p>Prefer {@link com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware} directly.
 */
public interface TokenUsageAwareFacade
    extends com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware {}
