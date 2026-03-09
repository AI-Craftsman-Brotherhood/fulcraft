package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class MethodDocClassifierTest {

  private final MethodDocClassifier classifier = new MethodDocClassifier();

  @Test
  void isTemplateTarget_shouldReturnTrueForSimpleGetter() {
    MethodInfo method = method("getId", "public String getId()", 0, 2);

    boolean result = classifier.isTemplateTarget(method, "Customer");

    assertThat(result).isTrue();
  }

  @Test
  void isTemplateTarget_shouldReturnFalseWhenConstructorDetectedFromSignature() {
    MethodInfo method = method("create", "public com.example.Customer(java.lang.String)", 1, 2);

    boolean result = classifier.isTemplateTarget(method, "Customer");

    assertThat(result).isFalse();
  }

  @Test
  void isTemplateTarget_shouldReturnFalseWhenBehavioralSignalsExist() {
    MethodInfo method = method("getStatus", "public String getStatus()", 0, 2);
    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(new GuardSummary("value == null")));
    method.setBranchSummary(summary);

    boolean result = classifier.isTemplateTarget(method, "Customer");

    assertThat(result).isFalse();
  }

  @Test
  void isTemplateTarget_shouldReturnFalseForInvalidAccessorCasing() {
    MethodInfo method = method("getvalue", "public String getvalue()", 0, 2);

    boolean result = classifier.isTemplateTarget(method, "Customer");

    assertThat(result).isFalse();
  }

  @Test
  void isTemplateTarget_shouldReturnFalseForInvalidInputAndHighLoc() {
    MethodInfo highLoc = method("getId", "public String getId()", 0, 5);
    MethodInfo unnamed = method(null, "public String value()", 0, 2);

    assertThat(classifier.isTemplateTarget(null, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(highLoc, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(unnamed, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(method("getId", "public String getId()", 0, 2), " "))
        .isFalse();
  }

  @Test
  void isTemplateTarget_shouldSupportCanonicalAndSetterMethods() {
    MethodInfo setter = method("setName", "public void setName(String n)", 1, 2);
    MethodInfo equalsMethod = method("equals", "public boolean equals(Object o)", 1, 2);
    MethodInfo hashCodeMethod = method("hashCode", "public int hashCode()", 0, 2);
    MethodInfo getterWithBlankSignature = method("getName", "   ", 0, 2);

    assertThat(classifier.isTemplateTarget(setter, "Customer")).isTrue();
    assertThat(classifier.isTemplateTarget(equalsMethod, "Customer")).isTrue();
    assertThat(classifier.isTemplateTarget(hashCodeMethod, "Customer")).isTrue();
    assertThat(classifier.isTemplateTarget(getterWithBlankSignature, "Customer")).isTrue();
  }

  @Test
  void isTemplateTarget_shouldRejectNonTrivialSetterOrShortAccessorPrefixes() {
    MethodInfo overloadedSetter =
        method("setName", "public void setName(String first, String second)", 2, 2);
    MethodInfo shortIs = method("is", "public boolean is()", 0, 2);
    MethodInfo shortHas = method("has", "public boolean has()", 0, 2);

    assertThat(classifier.isTemplateTarget(overloadedSetter, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(shortIs, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(shortHas, "Customer")).isFalse();
  }

  @Test
  void isTemplateTarget_shouldRejectBehavioralSignalsFromConditionalsLoopsSwitchesAndThrows() {
    MethodInfo conditionalMethod = method("getConditional", "public int getConditional()", 0, 2);
    conditionalMethod.setHasConditionals(true);

    MethodInfo loopMethod = method("getLoop", "public int getLoop()", 0, 2);
    loopMethod.setHasLoops(true);

    MethodInfo throwingMethod = method("getRisky", "public String getRisky()", 0, 2);
    throwingMethod.setThrownExceptions(List.of("java.io.IOException"));

    MethodInfo switchMethod = method("getMode", "public String getMode()", 0, 2);
    BranchSummary switchSummary = new BranchSummary();
    switchSummary.setSwitches(List.of("mode"));
    switchMethod.setBranchSummary(switchSummary);

    MethodInfo predicateMethod = method("getFlag", "public boolean getFlag()", 0, 2);
    BranchSummary predicateSummary = new BranchSummary();
    predicateSummary.setPredicates(List.of("value > 0"));
    predicateMethod.setBranchSummary(predicateSummary);

    assertThat(classifier.isTemplateTarget(conditionalMethod, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(loopMethod, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(throwingMethod, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(switchMethod, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(predicateMethod, "Customer")).isFalse();
  }

  @Test
  void isTemplateTarget_shouldDetectConstructorByMethodNameAndRejectMalformedSignature() {
    MethodInfo constructorByName = method("Customer", "public Customer()", 0, 1);
    MethodInfo malformedSignature = method("create", "public com.example.Customer", 0, 2);

    assertThat(classifier.isTemplateTarget(constructorByName, "Customer")).isFalse();
    assertThat(classifier.isTemplateTarget(malformedSignature, "Customer")).isFalse();
  }

  @Test
  void identifyType_shouldClassifyKnownTemplateKinds() {
    assertThat(classifier.identifyType(method("toString", "public String toString()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.TO_STRING);
    assertThat(classifier.identifyType(method("hashCode", "public int hashCode()", 0, 1)))
        .isEqualTo(MethodDocClassifier.TemplateType.HASH_CODE);
    assertThat(classifier.identifyType(method("equals", "public boolean equals(Object o)", 1, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.EQUALS);
    assertThat(classifier.identifyType(method("getName", "public String getName()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.GETTER);
    assertThat(classifier.identifyType(method("setName", "public void setName(String n)", 1, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.SETTER);
    assertThat(classifier.identifyType(method("compute", "public int compute()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.UNKNOWN);
  }

  @Test
  void identifyType_shouldClassifyIsHasGetterPrefixesAndHandleNullOrBlank() {
    assertThat(classifier.identifyType(method("isReady", "public boolean isReady()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.GETTER);
    assertThat(classifier.identifyType(method("hasItems", "public boolean hasItems()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.GETTER);
    assertThat(classifier.identifyType(method("   ", "public String value()", 0, 2)))
        .isEqualTo(MethodDocClassifier.TemplateType.UNKNOWN);
    assertThat(classifier.identifyType(null)).isEqualTo(MethodDocClassifier.TemplateType.UNKNOWN);
  }

  private MethodInfo method(String name, String signature, int parameterCount, int loc) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    method.setParameterCount(parameterCount);
    method.setLoc(loc);
    method.setCyclomaticComplexity(1);
    method.setThrownExceptions(List.of());
    return method;
  }
}
