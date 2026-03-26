package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DiagramDocumentGeneratorBranchCoverageTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @Test
  void extractClassName_shouldHandleHashDotAndInvalidReferences() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    assertThat(invokeExtractClassName(generator, null)).isNull();
    assertThat(invokeExtractClassName(generator, "com.example.OrderService#process"))
        .isEqualTo("com.example.OrderService");
    assertThat(invokeExtractClassName(generator, "com.example.OrderService.process"))
        .isEqualTo("com.example.OrderService");
    assertThat(invokeExtractClassName(generator, "com.example.OrderService.Process")).isNull();
    assertThat(invokeExtractClassName(generator, "localCallOnly")).isNull();
  }

  @Test
  void extractMethodSignatureKey_shouldHandleNullBlankAndMixedFormats() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    assertThat(invokeExtractMethodSignatureKey(generator, null)).isEqualTo("unknown");
    assertThat(invokeExtractMethodSignatureKey(generator, " ")).isEqualTo("unknown");
    assertThat(
            invokeExtractMethodSignatureKey(
                generator, "com.example.OrderService#process(java.lang.String, int)"))
        .isEqualTo("process(java.lang.String,int)");
    assertThat(invokeExtractMethodSignatureKey(generator, "com.example.OrderService#process("))
        .isEqualTo("process()");
    assertThat(invokeExtractMethodSignatureKey(generator, "com.example.OrderService.process"))
        .isEqualTo("process");
    assertThat(invokeExtractMethodSignatureKey(generator, "process")).isEqualTo("process");
  }

  @Test
  void signatureHelpers_shouldRespectFallbackAndWhitespaceNormalization() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    assertThat(invokeExtractSignatureKey(generator, null, "fallbackMethod"))
        .isEqualTo("fallbackMethod");
    assertThat(invokeExtractSignatureKey(generator, "", null)).isEqualTo("unknown");
    assertThat(invokeExtractSignatureKey(generator, "noParenSignature", "fallbackMethod"))
        .isEqualTo("fallbackMethod");
    assertThat(
            invokeExtractSignatureKey(
                generator, "public String find(String id, int count)", "fallbackMethod"))
        .isEqualTo("find(Stringid,intcount)");
    assertThat(invokeExtractSignatureKey(generator, "plainSignature", null))
        .isEqualTo("plainSignature");
    assertThat(invokeExtractSignatureKey(generator, "public void broken(", null))
        .isEqualTo("broken()");

    assertThat(invokeExtractSignatureDisplayName(generator, null, "fallbackMethod"))
        .isEqualTo("fallbackMethod");
    assertThat(invokeExtractSignatureDisplayName(generator, null, null))
        .isEqualTo(MessageSource.getMessage("document.value.unknown"));
    assertThat(
            invokeExtractSignatureDisplayName(
                generator, "public String find(String id, int count)", "fallbackMethod"))
        .isEqualTo("find(String id, int count)");
    assertThat(invokeExtractSignatureDisplayName(generator, "plainSignature", null))
        .isEqualTo("plainSignature");
    assertThat(invokeExtractSignatureDisplayName(generator, "public void broken(", null))
        .isEqualTo("broken()");

    assertThat(invokeNormalizeParamList(generator, null)).isEmpty();
    assertThat(invokeNormalizeParamList(generator, "String id, int count"))
        .isEqualTo("Stringid,intcount");
  }

  @Test
  void resolveCalledMethodIds_shouldPreferExactSignatureThenFallbackName() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    Map<String, String> idsBySignature = new LinkedHashMap<>();
    idsBySignature.put("target(String)", "target_String_");
    Map<String, Set<String>> idsByName = new LinkedHashMap<>();
    idsByName.put("target", new LinkedHashSet<>(Set.of("target__", "target_String_")));
    idsByName.put("plain", Set.of("plain__"));

    assertThat(invokeResolveCalledMethodIds(generator, "target(String)", idsBySignature, idsByName))
        .containsExactly("target_String_");
    assertThat(
            invokeResolveCalledMethodIds(
                generator, "target(java.lang.String)", idsBySignature, idsByName))
        .containsExactlyInAnyOrder("target__", "target_String_");
    assertThat(invokeResolveCalledMethodIds(generator, "plain", idsBySignature, idsByName))
        .containsExactly("plain__");
    assertThat(invokeResolveCalledMethodIds(generator, null, idsBySignature, idsByName)).isEmpty();
    assertThat(invokeResolveCalledMethodIds(generator, " ", idsBySignature, idsByName)).isEmpty();
  }

  @Test
  void utilityMethods_shouldReturnExpectedValuesForEdgeCases() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    assertThat(invokeSanitizeDiagramId(generator, null)).isEqualTo("unknown");
    assertThat(invokeSanitizeDiagramId(generator, "Order-Service#process()"))
        .isEqualTo("Order_Service_process__");

    assertThat(invokeBuildPlantUmlDependencyEdge(generator, "OrderService", "localCall")).isNull();
    assertThat(
            invokeBuildPlantUmlDependencyEdge(
                generator, "OrderService", "com.example.OrderService#process()"))
        .isNull();
    assertThat(
            invokeBuildPlantUmlDependencyEdge(
                generator, "OrderService", "com.example.PaymentGateway#charge()"))
        .isEqualTo("OrderService --> PaymentGateway");

    assertThat(invokeExtractNameFromSignatureKey(generator, "find(String)")).isEqualTo("find");
    assertThat(invokeExtractNameFromSignatureKey(generator, "find")).isEqualTo("find");
  }

  @Test
  void generateInheritanceDiagram_plantUml_shouldRenderInterfaceAbstractAndConcreteRelations() {
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.PLANTUML);

    ClassInfo contract = new ClassInfo();
    contract.setFqn("com.example.Contract");
    contract.setInterface(true);

    ClassInfo baseService = new ClassInfo();
    baseService.setFqn("com.example.BaseService");
    baseService.setAbstract(true);

    ClassInfo orderService = new ClassInfo();
    orderService.setFqn("com.example.OrderService");
    orderService.setExtendsTypes(List.of("com.example.BaseService"));
    orderService.setImplementsTypes(List.of("com.example.Contract"));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(contract, baseService, orderService));

    String diagram = generator.generateInheritanceDiagram(result);

    assertThat(diagram).contains("interface Contract");
    assertThat(diagram).contains("abstract class BaseService");
    assertThat(diagram).contains("class OrderService");
    assertThat(diagram).contains("BaseService <|-- OrderService");
    assertThat(diagram).contains("Contract <|.. OrderService");
  }

  @Test
  void generateMethodCallGraph_plantUml_shouldResolveOverloadsByNameFallback() {
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.PLANTUML);

    MethodInfo caller = new MethodInfo();
    caller.setName("caller");
    caller.setSignature("public void caller()");
    caller.setCalledMethods(
        List.of("helper(java.lang.String)", "helper", "helper", "com.example.Sample.helper"));

    MethodInfo helperNoArg = new MethodInfo();
    helperNoArg.setName("helper");
    helperNoArg.setSignature("private void helper()");

    MethodInfo helperWithArg = new MethodInfo();
    helperWithArg.setName("helper");
    helperWithArg.setSignature("private void helper(String value)");

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setMethods(List.of(caller, helperNoArg, helperWithArg));

    String diagram = generator.generateMethodCallGraph(classInfo);

    assertThat(diagram).contains("caller__ --> helper__");
    assertThat(diagram).contains("caller__ --> helper_Stringvalue_");
    assertThat(countOccurrences(diagram, "caller__ --> helper__")).isEqualTo(1);
  }

  @Test
  void generateInheritanceDiagram_mermaid_shouldDeclareDuplicateSimpleNameOnlyOnce() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    ClassInfo first = new ClassInfo();
    first.setFqn("com.alpha.Duplicate");
    first.setInterface(true);

    ClassInfo second = new ClassInfo();
    second.setFqn("com.beta.Duplicate");
    second.setAbstract(true);
    second.setExtendsTypes(List.of("com.base.BaseType"));
    second.setImplementsTypes(List.of("com.base.Port"));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(first, second));

    String diagram = generator.generateInheritanceDiagram(result);

    assertThat(countOccurrences(diagram, "class Duplicate")).isEqualTo(1);
    assertThat(diagram).contains("BaseType <|-- Duplicate");
    assertThat(diagram).contains("Port <|.. Duplicate");
  }

  @Test
  void mermaidNodeAndEdgeHelpers_shouldHandleUnknownLabelsAndDuplicates() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();

    StringBuilder nodeOutput = new StringBuilder();
    Set<String> nodes = new LinkedHashSet<>();
    invokeMethod(
        generator,
        "appendMermaidCalledNodeIfAbsent",
        new Class<?>[] {StringBuilder.class, Set.class, String.class, String.class},
        nodeOutput,
        nodes,
        "called__",
        " ");
    invokeMethod(
        generator,
        "appendMermaidCalledNodeIfAbsent",
        new Class<?>[] {StringBuilder.class, Set.class, String.class, String.class},
        nodeOutput,
        nodes,
        "called__",
        "knownMethod()");

    assertThat(nodeOutput.toString()).contains(MessageSource.getMessage("document.value.unknown"));
    assertThat(countOccurrences(nodeOutput.toString(), "called__[")).isEqualTo(1);

    StringBuilder edgeOutput = new StringBuilder();
    Set<String> edges = new LinkedHashSet<>();
    invokeMethod(
        generator,
        "appendMermaidEdgeIfAbsent",
        new Class<?>[] {StringBuilder.class, Set.class, String.class, String.class},
        edgeOutput,
        edges,
        "source__",
        "target__");
    invokeMethod(
        generator,
        "appendMermaidEdgeIfAbsent",
        new Class<?>[] {StringBuilder.class, Set.class, String.class, String.class},
        edgeOutput,
        edges,
        "source__",
        "target__");

    assertThat(countOccurrences(edgeOutput.toString(), "source__-->target__")).isEqualTo(1);
  }

  private String invokeExtractClassName(DiagramDocumentGenerator generator, String methodRef) {
    return invokeStringMethod(
        generator, "extractClassName", new Class<?>[] {String.class}, methodRef);
  }

  private String invokeExtractMethodSignatureKey(
      DiagramDocumentGenerator generator, String methodRef) {
    return invokeStringMethod(
        generator, "extractMethodSignatureKey", new Class<?>[] {String.class}, methodRef);
  }

  private String invokeExtractSignatureKey(
      DiagramDocumentGenerator generator, String signature, String fallbackName) {
    return invokeStringMethod(
        generator,
        "extractSignatureKey",
        new Class<?>[] {String.class, String.class},
        signature,
        fallbackName);
  }

  private String invokeExtractSignatureDisplayName(
      DiagramDocumentGenerator generator, String signature, String fallbackName) {
    return invokeStringMethod(
        generator,
        "extractSignatureDisplayName",
        new Class<?>[] {String.class, String.class},
        signature,
        fallbackName);
  }

  private String invokeNormalizeParamList(DiagramDocumentGenerator generator, String params) {
    return invokeStringMethod(
        generator, "normalizeParamList", new Class<?>[] {String.class}, params);
  }

  private Set<String> invokeResolveCalledMethodIds(
      DiagramDocumentGenerator generator,
      String calledKey,
      Map<String, String> idsBySignature,
      Map<String, Set<String>> idsByName) {
    return (Set<String>)
        invokeMethod(
            generator,
            "resolveCalledMethodIds",
            new Class<?>[] {String.class, Map.class, Map.class},
            calledKey,
            idsBySignature,
            idsByName);
  }

  private String invokeSanitizeDiagramId(DiagramDocumentGenerator generator, String name) {
    return invokeStringMethod(generator, "sanitizeDiagramId", new Class<?>[] {String.class}, name);
  }

  private String invokeBuildPlantUmlDependencyEdge(
      DiagramDocumentGenerator generator, String sourceName, String calledMethod) {
    return invokeStringMethod(
        generator,
        "buildPlantUmlDependencyEdge",
        new Class<?>[] {String.class, String.class},
        sourceName,
        calledMethod);
  }

  private String invokeExtractNameFromSignatureKey(
      DiagramDocumentGenerator generator, String signatureKey) {
    return invokeStringMethod(
        generator, "extractNameFromSignatureKey", new Class<?>[] {String.class}, signatureKey);
  }

  private String invokeStringMethod(
      DiagramDocumentGenerator generator,
      String methodName,
      Class<?>[] parameterTypes,
      Object... args) {
    return (String) invokeMethod(generator, methodName, parameterTypes, args);
  }

  private Object invokeMethod(
      DiagramDocumentGenerator generator,
      String methodName,
      Class<?>[] parameterTypes,
      Object... args) {
    try {
      Method method = DiagramDocumentGenerator.class.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method.invoke(generator, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke " + methodName, e);
    }
  }

  private int countOccurrences(String text, String token) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }
}
