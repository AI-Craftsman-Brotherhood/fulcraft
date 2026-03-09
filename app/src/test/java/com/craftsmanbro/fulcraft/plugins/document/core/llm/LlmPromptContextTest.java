package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmPromptContextTest {

  @Test
  void methods_shouldReturnSpecMethodsForBackwardCompatibility() {
    MethodInfo specMethod = new MethodInfo();
    specMethod.setName("getId");
    MethodInfo llmMethod = new MethodInfo();
    llmMethod.setName("validate");

    List<MethodInfo> specMethods = List.of(specMethod, llmMethod);
    List<MethodInfo> llmMethods = List.of(llmMethod);
    LlmPromptContext context =
        new LlmPromptContext("prompt", specMethods, llmMethods, emptyFacts());

    assertThat(context.methods()).isSameAs(specMethods);
    assertThat(context.specMethods())
        .extracting(MethodInfo::getName)
        .containsExactly("getId", "validate");
    assertThat(context.llmMethods()).extracting(MethodInfo::getName).containsExactly("validate");
  }

  private LlmValidationFacts emptyFacts() {
    return new LlmValidationFacts(
        List.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Map.of(),
        Set.of(),
        Set.of(),
        Map.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        false,
        false,
        "",
        "com.example.Sample",
        "Sample",
        Map.of());
  }
}
