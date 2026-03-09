package com.craftsmanbro.fulcraft.infrastructure.llm.model;

public enum Capability {
  /** Supports deterministic output via a fixed seed. */
  SEED,

  /** Supports defining a separate system message/instruction. */
  SYSTEM_MESSAGE,

  /** Supports constrained JSON output mode (e.g., response_format={"type": "json_object"}). */
  JSON_OUTPUT,

  /** Supports function calling or tool use APIs. */
  TOOL_CALLING
}
