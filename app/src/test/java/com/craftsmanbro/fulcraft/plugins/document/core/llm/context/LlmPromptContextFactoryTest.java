package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmPromptContextFactoryTest {

  @Test
  void buildPromptContext_shouldCollectKnownConstructorSignatures() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.PaymentResult");

    MethodInfo implicitDefault = new MethodInfo();
    implicitDefault.setName("PaymentResult");
    implicitDefault.setSignature("PaymentResult()");
    implicitDefault.setParameterCount(0);
    implicitDefault.setLoc(0);

    MethodInfo explicitConstructor = new MethodInfo();
    explicitConstructor.setName("PaymentResult");
    explicitConstructor.setVisibility("private");
    explicitConstructor.setSignature(
        "private PaymentResult(boolean success, String transactionId, java.math.BigDecimal amount, String errorMessage)");
    explicitConstructor.setParameterCount(4);
    explicitConstructor.setLoc(6);

    MethodInfo factoryMethod = new MethodInfo();
    factoryMethod.setName("failure");
    factoryMethod.setVisibility("public");
    factoryMethod.setSignature("public static PaymentResult failure(String errorMessage)");
    factoryMethod.setParameterCount(1);
    factoryMethod.setLoc(3);

    classInfo.setMethods(List.of(implicitDefault, explicitConstructor, factoryMethod));

    var context =
        factory.buildPromptContext(
            classInfo, Set.of(), "{{METHODS_INFO}}", (methods, validationFacts) -> "methods");

    assertThat(context.validationFacts().knownConstructorSignatures())
        .containsExactly("paymentresult(boolean,string,bigdecimal,string)");
  }

  @Test
  void buildPromptContext_shouldBuildMethodScopedUncertainDynamicMethodNames() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> true);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");

    MethodInfo resolveMethod = new MethodInfo();
    resolveMethod.setName("resolve");
    resolveMethod.setSignature("public Object resolve()");
    resolveMethod.setVisibility("public");
    resolveMethod.setLoc(12);
    resolveMethod.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicSample")
                .methodSig("public Object resolve()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));

    MethodInfo stableMethod = new MethodInfo();
    stableMethod.setName("stable");
    stableMethod.setSignature("public String stable()");
    stableMethod.setVisibility("public");
    stableMethod.setLoc(6);

    classInfo.setMethods(List.of(resolveMethod, stableMethod));

    var context =
        factory.buildPromptContext(
            classInfo, Set.of(), "{{METHODS_INFO}}", (methods, validationFacts) -> "methods");

    assertThat(context.validationFacts().uncertainDynamicMethodNames()).contains("processcustomer");
    assertThat(context.validationFacts().uncertainDynamicMethodNamesFor("resolve"))
        .contains("processcustomer");
    assertThat(context.validationFacts().uncertainDynamicMethodNamesFor("stable")).isEmpty();
  }

  @Test
  void buildPromptContext_shouldKeepAccessorMethodsInSpecButExcludeFromLlmPromptMethods() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerDto");

    MethodInfo getter = new MethodInfo();
    getter.setName("getId");
    getter.setSignature("public String getId()");
    getter.setVisibility("public");
    getter.setParameterCount(0);
    getter.setLoc(2);
    getter.setCyclomaticComplexity(1);
    getter.setSourceCode("public String getId() { return id; }");

    MethodInfo setter = new MethodInfo();
    setter.setName("setId");
    setter.setSignature("public void setId(String id)");
    setter.setVisibility("public");
    setter.setParameterCount(1);
    setter.setLoc(2);
    setter.setCyclomaticComplexity(1);
    setter.setSourceCode("public void setId(String id) { this.id = id; }");

    MethodInfo behavior = new MethodInfo();
    behavior.setName("validate");
    behavior.setSignature("public boolean validate()");
    behavior.setVisibility("public");
    behavior.setParameterCount(0);
    behavior.setLoc(10);
    behavior.setCyclomaticComplexity(2);
    behavior.setSourceCode(
        """
        public boolean validate() {
          if (id == null) {
            return false;
          }
          return !id.isBlank();
        }
        """);

    classInfo.setMethods(List.of(getter, setter, behavior));

    var context =
        factory.buildPromptContext(
            classInfo, Set.of(), "{{METHODS_INFO}}", (methods, validationFacts) -> "methods");

    assertThat(context.specMethods())
        .extracting(MethodInfo::getName)
        .containsExactly("getId", "setId", "validate");
    assertThat(context.llmMethods()).extracting(MethodInfo::getName).containsExactly("validate");
  }
}
