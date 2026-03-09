package com.craftsmanbro.fulcraft.plugins.analysis.core.service.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldAccessorAnalyzerTest {

  @Test
  void buildFieldAccessorSummary_shouldReturnEmptyForNullClassInfo() {
    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    assertEquals("", analyzer.buildFieldAccessorSummary(null));
  }

  @Test
  void buildFieldAccessorSummary_shouldReturnEmptyWhenNoFields() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setMethods(List.of(method("getValue", "public", 0)));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    assertEquals("", analyzer.buildFieldAccessorSummary(classInfo));
  }

  @Test
  void buildFieldAccessorSummary_shouldEmphasizeDirectAccessForPublicFields() {
    ClassInfo classInfo =
        classInfo(List.of(field("totalAmount", "BigDecimal", "public")), List.of());

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(
        summary.contains("ACCESS DIRECTLY"), "Public field should be marked for direct access");
  }

  @Test
  void buildFieldAccessorSummary_shouldResolveBooleanIsPrefixAccessors() {
    FieldInfo privateField = new FieldInfo();
    privateField.setName("isActive");
    privateField.setType("boolean");
    privateField.setVisibility("private");

    MethodInfo getter = new MethodInfo();
    getter.setName("isActive");
    getter.setVisibility("public");
    getter.setParameterCount(0);

    MethodInfo setter = new MethodInfo();
    setter.setName("setActive");
    setter.setVisibility("public");
    setter.setParameterCount(1);

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFields(List.of(privateField));
    classInfo.setMethods(List.of(getter, setter));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(summary.contains("isActive()"), "Boolean getter should be detected");
    assertTrue(summary.contains("setActive(...)"), "Boolean setter should be detected");
  }

  @Test
  void buildFieldAccessorSummary_shouldResolveBooleanWrapperUsingSuffixFallback() {
    FieldInfo privateField = field("isEnabled", "java.lang.Boolean", "private");

    ClassInfo classInfo =
        classInfo(
            List.of(privateField),
            List.of(method("getEnabled", "public", 0), method("setEnabled", "public", 1)));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(summary.contains("getEnabled()"), "Wrapper type getter should be detected");
    assertTrue(summary.contains("setEnabled(...)"), "Wrapper type setter should be detected");
  }

  @Test
  void buildFieldAccessorSummary_shouldListPrivateFieldsBeforeOthersInSortedOrder() {
    ClassInfo classInfo =
        classInfo(
            List.of(
                field("zeta", "String", "public"),
                field("beta", "String", "protected"),
                field("alpha", "String", "private")),
            List.of(method("getAlpha", "public", 0)));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    int alphaIndex = summary.indexOf("- alpha");
    int betaIndex = summary.indexOf("- beta");
    int zetaIndex = summary.indexOf("- zeta");

    assertTrue(alphaIndex >= 0, "Private field entry should exist");
    assertTrue(betaIndex >= 0, "Protected field entry should exist");
    assertTrue(zetaIndex >= 0, "Public field entry should exist");
    assertTrue(alphaIndex < betaIndex, "Private fields should be listed before non-private fields");
    assertTrue(betaIndex < zetaIndex, "Non-private fields should be sorted by field name");
    assertTrue(summary.contains("getAlpha()"), "Mapped private getter should be included");
  }

  @Test
  void buildFieldAccessorSummary_shouldTreatNonPublicGetterAsMissingAccessor() {
    ClassInfo classInfo =
        classInfo(
            List.of(field("secret", "String", "private")),
            List.of(method("getSecret", "private", 0)));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(summary.contains("NO PUBLIC ACCESSOR"));
  }

  @Test
  void buildFieldAccessorSummary_shouldGuideDirectAccessForProtectedFields() {
    ClassInfo classInfo = classInfo(List.of(field("count", "int", "protected")), List.of());

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(summary.contains("ACCESS DIRECTLY if in same package"));
  }

  @Test
  void buildFieldAccessorSummary_shouldIgnoreNonAccessorOverloads() {
    ClassInfo classInfo =
        classInfo(
            List.of(field("value", "String", "private")), List.of(method("getValue", "public", 1)));

    FieldAccessorAnalyzer analyzer = new FieldAccessorAnalyzer();

    String summary = analyzer.buildFieldAccessorSummary(classInfo);

    assertTrue(
        summary.contains("NO PUBLIC ACCESSOR"),
        "Overloaded methods with params should not be treated as getters");
  }

  private static ClassInfo classInfo(List<FieldInfo> fields, List<MethodInfo> methods) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFields(fields);
    classInfo.setMethods(methods);
    return classInfo;
  }

  private static FieldInfo field(String name, String type, String visibility) {
    FieldInfo field = new FieldInfo();
    field.setName(name);
    field.setType(type);
    field.setVisibility(visibility);
    return field;
  }

  private static MethodInfo method(String name, String visibility, int parameterCount) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setVisibility(visibility);
    method.setParameterCount(parameterCount);
    return method;
  }
}
