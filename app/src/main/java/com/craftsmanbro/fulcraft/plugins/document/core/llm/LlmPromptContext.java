package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;

/**
 * Prompt context containing the assembled prompt, target methods, and validation facts.
 *
 * <p>In hybrid mode, {@code specMethods} contains all methods that should appear in the final
 * document (including template targets), while {@code llmMethods} contains only the methods that
 * require LLM generation. The {@code prompt} is built using {@code llmMethods} only.
 */
public record LlmPromptContext(
    String prompt,
    List<MethodInfo> specMethods,
    List<MethodInfo> llmMethods,
    LlmValidationFacts validationFacts) {

  /**
   * Returns all methods targeted for the document specification. This is the canonical list for
   * validation of the final document.
   *
   * @deprecated Use {@link #specMethods()} or {@link #llmMethods()} for clarity. This returns
   *     specMethods for backward compatibility.
   */
  @Deprecated
  public List<MethodInfo> methods() {
    return specMethods;
  }
}
