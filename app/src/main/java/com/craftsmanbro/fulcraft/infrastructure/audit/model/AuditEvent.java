package com.craftsmanbro.fulcraft.infrastructure.audit.model;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;

public record AuditEvent(
    String provider,
    String model,
    String prompt,
    String promptRaw,
    String response,
    String promptHash,
    String responseHash,
    long requestChars,
    long responseChars,
    TokenUsage tokenUsage,
    RedactionReport redactionReport,
    String outcome,
    String errorType) {}
