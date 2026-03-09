package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmFallbackPreconditionExtractor;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmMethodFlowFactsExtractor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmFallbackPathSectionBuilderTest {

  @Test
  void collectFallbackNormalFlows_shouldNormalizeMainSuccessPathLabel() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("void processOrder()");

    RepresentativePath representativePath = new RepresentativePath();
    representativePath.setId("path-1");
    representativePath.setDescription("Main success path");
    representativePath.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(representativePath));

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).hasSize(1);
    assertThat(normalFlows.get(0)).contains("主返却パス");
    assertThat(normalFlows.get(0)).doesNotContain("Main success path");
  }

  @Test
  void collectFallbackNormalFlows_shouldUseSourceBackedDefaultWhenPathsAreUnavailable() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("getId");
    method.setSignature("String getId()");
    method.setSourceCode("String getId() { return id; }");

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).hasSize(1);
    assertThat(normalFlows.get(0)).contains("直接返す");
    assertThat(normalFlows.get(0)).contains("id");
  }

  @Test
  void collectFallbackErrorBoundaries_shouldUseSourceBackedDefaultWhenNoErrorFactsExist() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("setId");
    method.setSignature("void setId(String id)");
    method.setSourceCode("void setId(String id) { this.id = id; }");

    List<String> errorBoundaries =
        builder.collectFallbackErrorBoundaries(method, true, emptyFacts());

    assertThat(errorBoundaries).hasSize(1);
    assertThat(errorBoundaries.get(0)).contains("明示的な例外送出");
  }

  @Test
  void collectFallbackPostconditions_shouldUseSourceAssignmentsBeforeSignatureFallback() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("PaymentResult");
    method.setSignature("public PaymentResult(boolean success, String message)");
    method.setSourceCode(
        """
            public PaymentResult(boolean success, String message) {
              this.success = success;
              this.message = message;
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("this.success = success"));
    assertThat(postconditions).anyMatch(line -> line.contains("this.message = message"));
    assertThat(postconditions).noneMatch(line -> line.contains("シグネチャ"));
  }

  @Test
  void collectFallbackNormalAndTestViewpoints_shouldUseSourceAssignmentsForSetterLikeMethod() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("setId");
    method.setSignature("void setId(String id)");
    method.setParameterCount(1);
    method.setSourceCode(
        """
            void setId(String id) {
              this.id = id;
            }
            """);

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());
    List<String> testViewpoints = builder.collectFallbackTestViewpoints(method, true, emptyFacts());

    assertThat(normalFlows).anyMatch(line -> line.contains("this.id = id"));
    assertThat(testViewpoints).anyMatch(line -> line.contains("this.id = id"));
  }

  @Test
  void collectFallbackPostconditions_shouldUseSourceReturnExpressionForGetterLikeMethod() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("getId");
    method.setSignature("String getId()");
    method.setSourceCode(
        """
            String getId() {
              return this.id;
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("this.id"));
    assertThat(postconditions).noneMatch(line -> line.contains("シグネチャ"));
  }

  @Test
  void collectFallbackSections_shouldDescribeTrivialEmptyConstructorWithoutSignatureFallback() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("PaymentResult");
    method.setSignature("public PaymentResult()");
    method.setParameterCount(0);
    method.setSourceCode("""
            public PaymentResult() {}
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());
    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());
    List<String> testViewpoints = builder.collectFallbackTestViewpoints(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("既定値"));
    assertThat(postconditions).noneMatch(line -> line.contains("シグネチャ"));
    assertThat(normalFlows).anyMatch(line -> line.contains("空のコンストラクタ本体"));
    assertThat(testViewpoints).anyMatch(line -> line.contains("空コンストラクタ呼び出し"));
  }

  @Test
  void collectFallbackNormalFlows_shouldUseDependencyPreviewWhenSourceFactsAreUnavailable() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("execute");
    method.setSignature("void execute()");
    method.setSourceCode(
        """
            void execute() {
              helper();
            }
            """);

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(
            method,
            List.of("A#one", "B#two", "C#three", "D#four", "E#five", "F#six"),
            true,
            emptyFacts());

    assertThat(normalFlows).hasSize(1);
    assertThat(normalFlows.get(0)).contains("主な依存呼び出し");
    assertThat(normalFlows.get(0)).contains("A#one");
    assertThat(normalFlows.get(0)).doesNotContain("F#six");
  }

  @Test
  void collectFallbackNormalFlows_shouldFallbackToSourceOrderWhenNoFactsAreAvailable() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("execute");
    method.setSignature("void execute()");
    method.setSourceCode(
        """
            void execute() {
              helper();
            }
            """);

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).hasSize(1);
    assertThat(normalFlows.get(0)).contains("順に実行");
  }

  @Test
  void collectFallbackPostconditions_shouldFallbackToSignatureWhenNoPostconditionFactsExist() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("compute");
    method.setSignature("String compute()");
    method.setSourceCode(
        """
            String compute() {
              helper();
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).hasSize(1);
    assertThat(postconditions.get(0)).contains("シグネチャ");
    assertThat(postconditions.get(0)).contains("String compute()");
  }

  @Test
  void collectFallbackPostconditions_shouldEmitKnownMissingMessageWhenMethodIsMarked() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> true,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("invoke");
    method.setSignature("Object invoke()");

    LlmValidationFacts facts = factsWithKnownMissing("invoke", Set.of("missingFactory"));
    List<String> postconditions = builder.collectFallbackPostconditions(method, true, facts);

    assertThat(postconditions).anyMatch(line -> line.contains("NoSuchMethodException"));
    assertThat(postconditions).anyMatch(line -> line.contains("missingFactory"));
  }

  @Test
  void collectFallbackPostconditions_shouldNormalizeFailureFactorySuccessOutcome() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> "createFailure".equals(methodName),
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("createFailure");
    method.setSignature("Result createFailure()");

    RepresentativePath path = new RepresentativePath();
    path.setId("p1");
    path.setDescription("Main path");
    path.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(path));

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("主返却パス"));
    assertThat(postconditions).anyMatch(line -> line.contains("failure結果オブジェクト"));
  }

  @Test
  void collectFallbackPostconditions_shouldIncludeUncoveredSwitchCaseFacts() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method ->
                List.of(
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                        "switch-covered", "Switch case status=\"ok\"", "case-\"ok\""),
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                        "switch-default", "Switch default status", "default")),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("String resolve()");

    RepresentativePath covered = new RepresentativePath();
    covered.setId("path-covered");
    covered.setDescription("Switch case status=\"ok\"");
    covered.setExpectedOutcomeHint("case-\"ok\"");
    method.setRepresentativePaths(List.of(covered));

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("switch-default"));
  }

  @Test
  void collectFallbackNormalFlows_shouldUseMethodMissingFallbackWhenKnownMissingReferenceExists() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> true,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("invoke");
    method.setSignature("void invoke()");
    RepresentativePath path = new RepresentativePath();
    path.setId("n1");
    path.setDescription("calls missingFactory()");
    path.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(path));

    LlmValidationFacts facts = factsWithKnownMissing("invoke", Set.of("missingFactory"));
    List<String> normalFlows = builder.collectFallbackNormalFlows(method, List.of(), true, facts);

    assertThat(normalFlows).anyMatch(line -> line.contains("NoSuchMethodException"));
    assertThat(normalFlows).noneMatch(line -> line.contains("n1"));
  }

  @Test
  void collectFallbackErrorBoundaries_shouldIncludeExceptionsAndErrorPathSummary() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> value != null && value.toLowerCase().contains("error"),
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("void process()");
    method.setThrownExceptions(List.of("java.io.IOException"));

    RepresentativePath path = new RepresentativePath();
    path.setId("e1");
    path.setDescription("Error path");
    path.setExpectedOutcomeHint("failed");
    method.setRepresentativePaths(List.of(path));

    List<String> errors = builder.collectFallbackErrorBoundaries(method, true, emptyFacts());

    assertThat(errors).anyMatch(line -> line.contains("java.io.IOException"));
    assertThat(errors).anyMatch(line -> line.contains("[e1] Error path"));
  }

  @Test
  void collectFallbackTestViewpoints_shouldUseDynamicPendingWhenUnresolvedOnly() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> true,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("void run()");

    List<String> viewpoints = builder.collectFallbackTestViewpoints(method, true, emptyFacts());

    assertThat(viewpoints).anyMatch(line -> line.contains("動的解決候補"));
  }

  @Test
  void collectFallbackNormalFlows_shouldSkipPositiveNonNullBoundaryPaths() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("void run()");
    method.setSourceCode(
        """
            void run() {
              helper();
            }
            """);

    RepresentativePath boundary = new RepresentativePath();
    boundary.setId("b1");
    boundary.setDescription("Boundary check when input != null");
    boundary.setExpectedOutcomeHint("success");
    boundary.setRequiredConditions(List.of("input != null"));
    method.setRepresentativePaths(List.of(boundary));

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).anyMatch(line -> line.contains("順に実行"));
    assertThat(normalFlows).noneMatch(line -> line.contains("b1"));
  }

  @Test
  void collectFallbackTestViewpoints_shouldReturnEmptyWhenMethodIsNull() {
    List<String> viewpoints =
        defaultBuilder().collectFallbackTestViewpoints(null, true, emptyFacts());

    assertThat(viewpoints).isEmpty();
  }

  @Test
  void collectFallbackTestViewpoints_shouldLimitPreconditionsAndExceptionsAndUseKnownMissing() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> true,
            method -> true,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("invoke");
    method.setSignature("void invoke(String id, int count)");
    method.setThrownExceptions(
        List.of(
            "java.io.IOException",
            "java.sql.SQLException",
            "java.lang.IllegalStateException",
            "java.lang.RuntimeException"));
    RepresentativePath preconditionPath = new RepresentativePath();
    preconditionPath.setId("pre");
    preconditionPath.setDescription("validation path");
    preconditionPath.setExpectedOutcomeHint("error");
    preconditionPath.setRequiredConditions(
        List.of("id == null", "count <= 0", "count < 0", "id != null", "count > 100"));
    method.setRepresentativePaths(List.of(preconditionPath));

    LlmValidationFacts facts = factsWithKnownMissing("invoke", Set.of("missingFactory"));
    List<String> viewpoints = builder.collectFallbackTestViewpoints(method, true, facts);

    assertThat(viewpoints.stream().filter(line -> line.contains("ガード条件")).count())
        .isGreaterThanOrEqualTo(3)
        .isLessThanOrEqualTo(4);
    assertThat(viewpoints.stream().filter(line -> line.startsWith("例外 `")).count()).isEqualTo(3);
    assertThat(viewpoints).anyMatch(line -> line.contains("NoSuchMethodException"));
    assertThat(viewpoints).noneMatch(line -> line.contains("動的解決候補メソッド"));
  }

  @Test
  void collectFallbackTestViewpoints_shouldSkipBlankIdAndUseExpectedCheckWhenOutcomeMissing() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("void run()");

    RepresentativePath blankId = new RepresentativePath();
    blankId.setId(" ");
    blankId.setDescription("ignored");
    blankId.setExpectedOutcomeHint("success");

    RepresentativePath normal = new RepresentativePath();
    normal.setId("p1");
    normal.setDescription("normal path");
    normal.setExpectedOutcomeHint(" ");

    method.setRepresentativePaths(List.of(blankId, normal));

    List<String> viewpoints = builder.collectFallbackTestViewpoints(method, true, emptyFacts());

    assertThat(viewpoints).noneMatch(line -> line.contains("ignored"));
    assertThat(viewpoints).anyMatch(line -> line.contains("p1: normal path"));
    assertThat(viewpoints).anyMatch(line -> line.contains("期待結果の確認"));
  }

  @Test
  void collectFallbackTestViewpoints_shouldSkipUncertainAndPositiveBoundaryPaths() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("void run()");

    RepresentativePath uncertain = new RepresentativePath();
    uncertain.setId("u1");
    uncertain.setDescription("calls dynamicCall()");
    uncertain.setExpectedOutcomeHint("success");

    RepresentativePath positiveBoundary = new RepresentativePath();
    positiveBoundary.setId("b1");
    positiveBoundary.setDescription("Boundary check when input != null");
    positiveBoundary.setExpectedOutcomeHint("success");
    positiveBoundary.setRequiredConditions(List.of("input != null"));

    RepresentativePath safe = new RepresentativePath();
    safe.setId("s1");
    safe.setDescription("safe path");
    safe.setExpectedOutcomeHint("done");

    method.setRepresentativePaths(List.of(uncertain, positiveBoundary, safe));

    LlmValidationFacts facts = factsWithUncertain("run", Set.of("dynamicCall"));
    List<String> viewpoints = builder.collectFallbackTestViewpoints(method, true, facts);

    assertThat(viewpoints).noneMatch(line -> line.contains("u1"));
    assertThat(viewpoints).noneMatch(line -> line.contains("b1"));
    assertThat(viewpoints).anyMatch(line -> line.contains("s1: safe path"));
  }

  @Test
  void
      collectFallbackTestViewpoints_shouldSkipBlankSwitchLabelAndFallbackExpectedForBlankOutcome() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method ->
                List.of(
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact(" ", " ", "case-default"),
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                        "switch-1", "switch default status", " ")),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("dispatch");
    method.setSignature("void dispatch()");

    List<String> viewpoints = builder.collectFallbackTestViewpoints(method, true, emptyFacts());

    assertThat(viewpoints).noneMatch(line -> line.contains("switch-1:  "));
    assertThat(viewpoints).anyMatch(line -> line.contains("switch-1: switch default status"));
    assertThat(viewpoints).anyMatch(line -> line.contains("期待結果の確認"));
  }

  @Test
  void collectFallbackNormalFlows_shouldFilterUncertainDependencyReferences() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("void run()");
    method.setSourceCode(
        """
            void run() {
              helper();
            }
            """);

    LlmValidationFacts facts = factsWithUncertain("run", Set.of("dynamicCall"));
    List<String> normalFlows =
        builder.collectFallbackNormalFlows(
            method, List.of("safeCall()", "dynamicCall()"), true, facts);

    assertThat(normalFlows).anyMatch(line -> line.contains("safeCall()"));
    assertThat(normalFlows).noneMatch(line -> line.contains("dynamicCall()"));
  }

  @Test
  void collectFallbackPostconditions_shouldUseDynamicPendingForSuccessWhenUnresolved() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> true,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("run");
    method.setSignature("String run()");

    RepresentativePath path = new RepresentativePath();
    path.setId("p1");
    path.setDescription("Main path");
    path.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(path));

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("動的解決候補の実在確認後"));
  }

  @Test
  void collectFallbackNormalFlows_shouldNormalizeEarlyReturnOutcomeWhenIncompatible() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> true,
            method -> List.of(),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("String process()");

    RepresentativePath path = new RepresentativePath();
    path.setId("p1");
    path.setDescription("Main path");
    path.setExpectedOutcomeHint("early-return");
    method.setRepresentativePaths(List.of(path));

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).anyMatch(line -> line.contains("failure"));
  }

  @Test
  void collectFallbackNormalFlows_shouldUseReturnDefaultWhenSourceHasControlFlow() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("compute");
    method.setSignature("String compute()");
    method.setSourceCode(
        """
            String compute(String in) {
              if (in == null) {
                return "x";
              }
              return in;
            }
            """);

    List<String> normalFlows =
        builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

    assertThat(normalFlows).anyMatch(line -> line.contains("`return`文"));
  }

  @Test
  void collectFallbackErrorBoundaries_shouldUseThrowSpecificDefaultInEnglish() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("explode");
    method.setSignature("void explode()");
    method.setSourceCode(
        """
            void explode() {
              throw new IllegalStateException();
            }
            """);

    List<String> boundaries = builder.collectFallbackErrorBoundaries(method, false, emptyFacts());

    assertThat(boundaries)
        .anyMatch(line -> line.contains("exceptions are explicitly thrown in source"));
  }

  @Test
  void collectFallbackPostconditions_shouldAcceptNonThisAssignmentWhenParameterIsReferenced() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("setValue");
    method.setSignature("String setValue(String value)");
    method.setSourceCode(
        """
            String setValue(String value) {
              target = value;
              return target;
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("target = value"));
  }

  @Test
  void collectFallbackPostconditions_shouldRejectNonThisAssignmentWithoutParameterReference() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("setValue");
    method.setSignature("String setValue(String value)");
    method.setSourceCode(
        """
            String setValue(String value) {
              target = "fixed";
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("シグネチャ"));
    assertThat(postconditions).noneMatch(line -> line.contains("target = \"fixed\""));
  }

  @Test
  void collectFallbackPostconditions_shouldFallbackToSignatureWhenSourceHasTooManyStatements() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();

    MethodInfo method = new MethodInfo();
    method.setName("compute");
    method.setSignature("String compute(String value)");
    method.setSourceCode(
        """
            String compute(String value) {
              this.a1 = value;
              this.a2 = value;
              this.a3 = value;
              this.a4 = value;
              this.a5 = value;
              this.a6 = value;
              this.a7 = value;
              this.a8 = value;
              this.a9 = value;
              this.a10 = value;
              this.a11 = value;
              this.a12 = value;
              this.a13 = value;
              this.a14 = value;
              this.a15 = value;
              this.a16 = value;
              this.a17 = value;
            }
            """);

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("シグネチャ"));
  }

  @Test
  void collectFallbackNormalFlows_shouldRecognizeAllSupportedNonNullBoundaryPhrases() {
    LlmFallbackPathSectionBuilder builder = defaultBuilder();
    List<String> nonNullPhrases =
        List.of("input!=null", "nullでない", "null でない", "nullではない", "null ではない");

    for (int i = 0; i < nonNullPhrases.size(); i++) {
      MethodInfo method = new MethodInfo();
      method.setName("run" + i);
      method.setSignature("void run" + i + "()");
      method.setSourceCode("void run" + i + "() { step(); }");

      RepresentativePath path = new RepresentativePath();
      path.setId("b" + i);
      path.setDescription("Boundary check " + nonNullPhrases.get(i));
      path.setExpectedOutcomeHint("success");
      method.setRepresentativePaths(List.of(path));

      List<String> normalFlows =
          builder.collectFallbackNormalFlows(method, List.of(), true, emptyFacts());

      String pathMarker = "[b" + i + "]";
      assertThat(normalFlows).noneMatch(line -> line.contains(pathMarker));
    }
  }

  @Test
  void collectFallbackPostconditions_shouldTreatWildcardSwitchLabelAsCoveredWhenSuffixMatches() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method ->
                List.of(
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact("fact-wild", "", "case-\"ok\"")),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("dispatch");
    method.setSignature("String dispatch()");

    RepresentativePath covered = new RepresentativePath();
    covered.setId("path-1");
    covered.setDescription("Switch case status=\"ok\"");
    covered.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(covered));

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).noneMatch(line -> line.contains("fact-wild"));
  }

  @Test
  void collectFallbackPostconditions_shouldSkipSwitchFactWhenSingleQuotedCaseLabelIsBlank() {
    LlmFallbackPathSectionBuilder builder =
        new LlmFallbackPathSectionBuilder(
            new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
            method -> false,
            method -> false,
            MethodInfo::getName,
            methodName -> false,
            value -> false,
            method -> false,
            method ->
                List.of(
                    new LlmMethodFlowFactsExtractor.SwitchCaseFact("fact-blank", "", "case-''")),
            "N/A");

    MethodInfo method = new MethodInfo();
    method.setName("dispatch");
    method.setSignature("String dispatch()");
    RepresentativePath covered = new RepresentativePath();
    covered.setId("covered");
    covered.setDescription("Switch case status=\"ok\"");
    covered.setExpectedOutcomeHint("case-\"ok\"");
    method.setRepresentativePaths(List.of(covered));

    List<String> postconditions = builder.collectFallbackPostconditions(method, true, emptyFacts());

    assertThat(postconditions).anyMatch(line -> line.contains("fact-blank"));
  }

  private LlmFallbackPathSectionBuilder defaultBuilder() {
    return new LlmFallbackPathSectionBuilder(
        new LlmFallbackPreconditionExtractor(condition -> false, condition -> false),
        method -> false,
        method -> false,
        MethodInfo::getName,
        methodName -> false,
        value -> false,
        method -> false,
        method -> List.of(),
        "N/A");
  }

  private LlmValidationFacts factsWithKnownMissing(String ownerMethodName, Set<String> methods) {
    return new LlmValidationFacts(
        List.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Map.of(),
        methods,
        methods,
        Map.of(ownerMethodName, methods),
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

  private LlmValidationFacts factsWithUncertain(String ownerMethodName, Set<String> methods) {
    return new LlmValidationFacts(
        List.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        methods,
        methods,
        Map.of(ownerMethodName, methods),
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
