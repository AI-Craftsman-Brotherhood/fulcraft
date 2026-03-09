package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmCalledMethodFilter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmMethodsInfoFormatterTest {

  private final LlmMethodsInfoFormatter formatter =
      new LlmMethodsInfoFormatter(
          this::resolveMessage, new LlmCalledMethodFilter("N/A"), resolution -> false, r -> "true");

  @Test
  void buildMethodsInfo_shouldIncludeHeadAndTailForLongSourcePreview() {
    MethodInfo method = new MethodInfo();
    method.setName("longMethod");
    method.setSignature("void longMethod()");
    method.setVisibility("public");
    method.setLoc(120);
    method.setCyclomaticComplexity(8);
    method.setMaxNestingDepth(2);
    method.setParameterCount(0);
    method.setUsageCount(1);
    method.setSourceCode(buildSequentialSource(100));

    String methodsInfo = formatter.buildMethodsInfo(List.of(method), minimalFacts("longMethod"));

    assertThat(methodsInfo).contains("line_001();");
    assertThat(methodsInfo).contains("line_100();");
    assertThat(methodsInfo).contains("and 52 more");
  }

  @Test
  void buildMethodsInfo_shouldIncludeWholeSourceWhenLineCountIsSmall() {
    MethodInfo method = new MethodInfo();
    method.setName("shortMethod");
    method.setSignature("void shortMethod()");
    method.setVisibility("public");
    method.setLoc(12);
    method.setCyclomaticComplexity(2);
    method.setMaxNestingDepth(1);
    method.setParameterCount(0);
    method.setUsageCount(1);
    method.setSourceCode(buildSequentialSource(8));

    String methodsInfo = formatter.buildMethodsInfo(List.of(method), minimalFacts("shortMethod"));

    assertThat(methodsInfo).contains("line_001();");
    assertThat(methodsInfo).contains("line_008();");
    assertThat(methodsInfo).doesNotContain("and 0 more");
  }

  private LlmValidationFacts minimalFacts(String methodName) {
    return new LlmValidationFacts(
        List.of(methodName),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Map.of(),
        Set.of(),
        Set.of(),
        Map.of(),
        Set.of(methodName.toLowerCase()),
        Set.of(),
        Set.of(),
        false,
        false,
        "",
        "com.example.Sample",
        "Sample",
        Map.of());
  }

  private String buildSequentialSource(int lines) {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= lines; i++) {
      sb.append("line_").append(String.format("%03d", i)).append("();\n");
    }
    return sb.toString().stripTrailing();
  }

  private String resolveMessage(String key, Object... args) {
    if ("document.list.more.inline".equals(key) && args.length > 0) {
      return "and " + args[0] + " more";
    }
    return key;
  }
}
