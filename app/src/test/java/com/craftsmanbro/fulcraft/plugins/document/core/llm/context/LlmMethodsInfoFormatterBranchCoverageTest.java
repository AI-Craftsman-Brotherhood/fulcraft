package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmCalledMethodFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmMethodsInfoFormatterBranchCoverageTest {

  private final LlmMethodsInfoFormatter formatter =
      new LlmMethodsInfoFormatter(
          this::resolveMessage,
          new LlmCalledMethodFilter("N/A"),
          resolution -> resolution != null && "open".equals(resolution.subtype()),
          resolution -> resolution != null && "missing".equals(resolution.subtype()),
          resolution ->
              resolution == null
                  ? "false"
                  : resolution.evidence().getOrDefault("verified", "false"));

  @Test
  void buildMethodsInfo_shouldReturnNoMethodsMessageWhenInputIsEmpty() {
    assertThat(formatter.buildMethodsInfo(List.of(), minimalFacts()))
        .isEqualTo("document.value.no_methods");
  }

  @Test
  void buildMethodsInfo_shouldRenderRichSectionsAndOverflowPreviews() {
    MethodInfo rich = new MethodInfo();
    rich.setName("analyze");
    rich.setSignature("public Result analyze(String id)");
    rich.setVisibility("public");
    rich.setStatic(true);
    rich.setLoc(200);
    rich.setCyclomaticComplexity(20);
    rich.setMaxNestingDepth(4);
    rich.setParameterCount(1);
    rich.setUsageCount(9);
    rich.setHasLoops(true);
    rich.setHasConditionals(true);
    rich.setDeadCode(true);
    rich.setDuplicate(true);
    rich.setUsesRemovedApis(true);
    rich.setPartOfCycle(true);
    rich.setThrownExceptions(List.of("java.io.IOException", "java.lang.IllegalStateException"));
    rich.setSourceCode(buildSequentialSource(110));
    rich.setDynamicFeatureHigh(2);
    rich.setDynamicFeatureMedium(3);
    rich.setDynamicFeatureLow(4);
    rich.setDynamicFeatureHasServiceLoader(true);

    BranchSummary summary = new BranchSummary();
    GuardSummary guard1 = new GuardSummary();
    guard1.setType(GuardType.FAIL_GUARD);
    guard1.setCondition("id == null");
    GuardSummary guard2 = new GuardSummary();
    guard2.setType(null);
    guard2.setCondition("id.isBlank()");
    summary.setGuards(List.of(guard1, guard2));
    summary.setSwitches(List.of("s1", "s2", "s3", "s4", "s5"));
    summary.setPredicates(List.of("p1", "p2", "p3", "p4", "p5"));
    rich.setBranchSummary(summary);

    List<RepresentativePath> paths = new ArrayList<>();
    paths.add(null);
    for (int i = 1; i <= 12; i++) {
      RepresentativePath path = new RepresentativePath();
      path.setId("P-" + i);
      path.setDescription("desc-" + i);
      path.setExpectedOutcomeHint("hint-" + i);
      paths.add(path);
    }
    rich.setRepresentativePaths(paths);

    List<String> calledMethods = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      calledMethods.add("com.example.ExternalService#call" + i + "()");
    }
    rich.setCalledMethods(calledMethods);

    List<DynamicResolution> resolutions = new ArrayList<>();
    resolutions.add(
        DynamicResolution.builder()
            .subtype("missing")
            .lineStart(10)
            .confidence(0.20)
            .resolvedMethodSig("com.example.ExternalService#missingOne()")
            .candidates(List.of("com.example.ExternalService#candidateA()"))
            .evidence(Map.of("verified", "false"))
            .build());
    resolutions.add(
        DynamicResolution.builder()
            .subtype("open")
            .lineStart(11)
            .confidence(0.60)
            .resolvedMethodSig("com.example.ExternalService#openOne()")
            .candidates(List.of("com.example.ExternalService#candidateB()"))
            .evidence(Map.of("verified", "unknown"))
            .build());
    resolutions.add(
        DynamicResolution.builder()
            .subtype("confirm")
            .lineStart(12)
            .confidence(0.95)
            .trustLevel(TrustLevel.HIGH)
            .resolvedMethodSig("com.example.ExternalService#confirmedOne()")
            .evidence(Map.of("verified", "true"))
            .build());
    for (int i = 0; i < 8; i++) {
      resolutions.add(
          DynamicResolution.builder()
              .subtype("open")
              .lineStart(100 + i)
              .confidence(0.51 + (i * 0.01))
              .resolvedMethodSig("com.example.ExternalService#extra" + i + "()")
              .build());
    }
    rich.setDynamicResolutions(resolutions);

    String methodsInfo = formatter.buildMethodsInfo(List.of(rich), minimalFacts());

    assertThat(methodsInfo).contains("document.md.section.branches");
    assertThat(methodsInfo).contains("document.md.section.representative_paths");
    assertThat(methodsInfo).contains("document.label.called_methods");
    assertThat(methodsInfo).contains("document.label.exceptions");
    assertThat(methodsInfo).contains("dynamic_resolutions:");
    assertThat(methodsInfo).contains("status=KNOWN_MISSING");
    assertThat(methodsInfo).contains("status=UNCONFIRMED");
    assertThat(methodsInfo).contains("status=CONFIRMED");
    assertThat(methodsInfo).contains("resolved_method=missingone");
    assertThat(methodsInfo).contains("candidates=`com.example.ExternalService#candidateA()`");
    assertThat(methodsInfo).contains("document.md.section.source_code");
    assertThat(methodsInfo).contains("and ");
  }

  @Test
  void privateHelpers_shouldHandleStatusConfidenceAndNullSafetyBranches() {
    assertThat(invokeResolveResolutionStatus(null)).isEqualTo("CONFIRMED");
    assertThat(
            invokeResolveResolutionStatus(DynamicResolution.builder().subtype("missing").build()))
        .isEqualTo("KNOWN_MISSING");
    assertThat(invokeResolveResolutionStatus(DynamicResolution.builder().subtype("open").build()))
        .isEqualTo("UNCONFIRMED");
    assertThat(invokeResolveResolutionStatus(DynamicResolution.builder().subtype("other").build()))
        .isEqualTo("CONFIRMED");

    assertThat(invokeFormatConfidence(0.0)).isEqualTo("0.00");
    assertThat(invokeFormatConfidence(0.756)).isEqualTo("0.76");

    assertThat(invokeNullSafe(null)).isEmpty();
    assertThat(invokeNullSafe("value")).isEqualTo("value");
  }

  private LlmValidationFacts minimalFacts() {
    return new LlmValidationFacts(
        List.of("analyze"),
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
        "sample",
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

  private String invokeResolveResolutionStatus(DynamicResolution resolution) {
    return (String)
        invoke("resolveResolutionStatus", new Class<?>[] {DynamicResolution.class}, resolution);
  }

  private String invokeFormatConfidence(double confidence) {
    return (String) invoke("formatConfidence", new Class<?>[] {double.class}, confidence);
  }

  private String invokeNullSafe(String value) {
    return (String) invoke("nullSafe", new Class<?>[] {String.class}, value);
  }

  private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = LlmMethodsInfoFormatter.class.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method.invoke(formatter, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke " + methodName, e);
    }
  }
}
