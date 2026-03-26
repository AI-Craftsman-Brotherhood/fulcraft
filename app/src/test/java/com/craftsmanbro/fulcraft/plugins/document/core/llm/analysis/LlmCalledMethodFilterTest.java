package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionStatus;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmCalledMethodFilterTest {

  private final LlmCalledMethodFilter filter = new LlmCalledMethodFilter("-");

  @Test
  void filterCalledMethodsForSpecification_shouldExcludePrivateSameClassAndImplicitConstructors() {
    MethodInfo method = new MethodInfo();
    method.setCalledMethods(
        List.of(
            "normalize()",
            "com.example.OrderService#normalize(java.lang.String)",
            "com.example.ExternalService#normalize(java.lang.String)",
            "com.example.OrderService#OrderService()",
            "com.example.OrderService.Inner#Inner()",
            "com.example.OrderService#OrderService(java.lang.String)"));

    List<String> output =
        filter.filterCalledMethodsForSpecification(
            method,
            validationFacts(Set.of("normalize"), "com.example.orderservice", "orderservice"));

    assertThat(output)
        .containsExactly(
            "com.example.ExternalService#normalize(java.lang.String)",
            "com.example.OrderService#OrderService(java.lang.String)");
  }

  @Test
  void filterCalledMethodsForSpecificationWithArgumentLiterals_shouldAppendLiteralHints() {
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public boolean processOrder(java.lang.String)");

    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw(
        "com.example.legacy.service.NotificationService#sendNotification(java.lang.String, java.lang.String)");
    ref.setResolved(
        "com.example.legacy.service.NotificationService#sendNotification(java.lang.String, java.lang.String)");
    ref.setStatus(ResolutionStatus.RESOLVED);
    ref.setArgumentLiterals(List.of("\"Order is being processed\""));
    method.setCalledMethodRefs(List.of(ref));

    List<String> output =
        filter.filterCalledMethodsForSpecificationWithArgumentLiterals(method, validationFacts());

    assertThat(output)
        .containsExactly(
            "com.example.legacy.service.NotificationService#sendNotification(java.lang.String, java.lang.String) [arg_literals: \"Order is being processed\"]");
  }

  @Test
  void filterCalledMethodsForSpecificationWithArgumentLiterals_shouldMergeDeduplicatedLiterals() {
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public boolean processOrder(java.lang.String)");

    CalledMethodRef first = new CalledMethodRef();
    first.setRaw("com.example.Service#send(java.lang.String)");
    first.setResolved("com.example.Service#send(java.lang.String)");
    first.setStatus(ResolutionStatus.RESOLVED);
    first.setArgumentLiterals(List.of("\"A\""));

    CalledMethodRef second = new CalledMethodRef();
    second.setRaw("com.example.Service#send(java.lang.String)");
    second.setResolved("com.example.Service#send(java.lang.String)");
    second.setStatus(ResolutionStatus.RESOLVED);
    second.setArgumentLiterals(List.of("\"B\""));

    method.setCalledMethodRefs(List.of(first, second));

    List<String> output =
        filter.filterCalledMethodsForSpecificationWithArgumentLiterals(method, validationFacts());

    assertThat(output)
        .containsExactly("com.example.Service#send(java.lang.String) [arg_literals: \"A\", \"B\"]");
  }

  @Test
  void filterCalledMethodsForSpecification_shouldMergeSemanticallyEquivalentParameterTypes() {
    MethodInfo method = new MethodInfo();

    CalledMethodRef first = new CalledMethodRef();
    first.setResolved("com.example.Service#process(java.util.List<java.lang.String>, T...)");
    first.setRaw("com.example.Service#process(java.util.List<java.lang.String>, T...)");
    first.setStatus(ResolutionStatus.RESOLVED);

    CalledMethodRef second = new CalledMethodRef();
    second.setResolved("com.example.Service#process(List<Integer>, Object[])");
    second.setRaw("com.example.Service#process(List<Integer>, Object[])");
    second.setStatus(ResolutionStatus.RESOLVED);

    method.setCalledMethodRefs(List.of(first, second));

    List<String> output = filter.filterCalledMethodsForSpecification(method, validationFacts());

    assertThat(output)
        .containsExactly("com.example.Service#process(java.util.List<java.lang.String>, T...)");
  }

  @Test
  void filterCalledMethodsForSpecificationWithArgumentLiterals_shouldNormalizeLiteralText() {
    MethodInfo method = new MethodInfo();

    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw("com.example.Service#audit(java.lang.String)");
    ref.setResolved("com.example.Service#audit(java.lang.String)");
    ref.setStatus(ResolutionStatus.RESOLVED);
    String longLiteral = "x".repeat(130);
    ref.setArgumentLiterals(
        java.util.Arrays.asList("  `Alpha`  ", "", null, "`Beta`", longLiteral));
    method.setCalledMethodRefs(List.of(ref));

    List<String> output =
        filter.filterCalledMethodsForSpecificationWithArgumentLiterals(method, validationFacts());

    assertThat(output).hasSize(1);
    assertThat(output.get(0))
        .isEqualTo(
            "com.example.Service#audit(java.lang.String) [arg_literals: Alpha, Beta, "
                + "x".repeat(117)
                + "...]");
  }

  @Test
  void filterCalledMethodsForSpecification_shouldHandleNullEntriesAndCompactFallbackReferences() {
    MethodInfo method = new MethodInfo();
    method.setCalledMethodRefs(
        java.util.Arrays.asList(
            null,
            calledMethodRef(null, "   utilityCall   ", ResolutionStatus.UNRESOLVED, List.of()),
            calledMethodRef(null, "com.example.Service#", ResolutionStatus.UNRESOLVED, List.of())));

    List<String> output = filter.filterCalledMethodsForSpecification(method, validationFacts());

    assertThat(output).containsExactly("utilityCall", "com.example.Service#");
  }

  @Test
  void filterCalledMethodsForSpecification_shouldKeepLegacyFormatWithoutHints() {
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public boolean processOrder(java.lang.String)");
    method.setCalledMethods(
        List.of(
            "com.example.legacy.service.NotificationService#sendNotification(java.lang.String, java.lang.String)"));

    List<String> output = filter.filterCalledMethodsForSpecification(method, validationFacts());

    assertThat(output)
        .containsExactly(
            "com.example.legacy.service.NotificationService#sendNotification(java.lang.String, java.lang.String)");
  }

  @Test
  void filterCalledMethodsForSpecification_shouldReturnEmptyForNullMethodAndNoCandidates() {
    MethodInfo noCalls = new MethodInfo();

    assertThat(filter.filterCalledMethodsForSpecification(null, validationFacts())).isEmpty();
    assertThat(filter.filterCalledMethodsForSpecification(noCalls, validationFacts())).isEmpty();
  }

  @Test
  void
      filterCalledMethodsForSpecificationWithArgumentLiterals_shouldKeepReferenceWithoutHintsWhenNone()
          throws Exception {
    MethodInfo method = new MethodInfo();

    CalledMethodRef ref = new CalledMethodRef();
    ref.setResolved("com.example.Service#run(java.lang.String)");
    ref.setRaw("com.example.Service#run(java.lang.String)");
    ref.setStatus(ResolutionStatus.RESOLVED);
    ref.setArgumentLiterals(List.of());
    method.setCalledMethodRefs(List.of(ref));

    List<String> output =
        filter.filterCalledMethodsForSpecificationWithArgumentLiterals(method, validationFacts());

    assertThat(output).containsExactly("com.example.Service#run(java.lang.String)");
    assertThat(invokeCanonicalize(null)).isNull();
    assertThat(invokeCanonicalize("   ")).isNull();
  }

  @Test
  void privateHelpers_shouldCoverCanonicalizationAndOwnerExtractionBranches() throws Exception {
    Object compact = invokeCanonicalize("   utilityCall   ");
    assertThat(readRecordString(compact, "ownerType")).isEmpty();
    assertThat(readRecordString(compact, "normalizedReference")).isEqualTo("utilityCall");
    assertThat(readRecordBoolean(compact, "constructor")).isFalse();

    Object missingOwner = invokeCanonicalize("#run()");
    assertThat(readRecordString(missingOwner, "ownerType")).isEmpty();
    assertThat(readRecordString(missingOwner, "normalizedReference")).isEqualTo("#run()");

    Object missingSignature = invokeCanonicalize("com.example.Service#");
    assertThat(readRecordString(missingSignature, "ownerType")).isEqualTo("com.example.Service");
    assertThat(readRecordString(missingSignature, "normalizedReference"))
        .isEqualTo("com.example.Service#");

    Object unnamedMethod = invokeCanonicalize("com.example.Service#()");
    assertThat(readRecordString(unnamedMethod, "methodName")).isEqualTo("-");
    assertThat(readRecordString(unnamedMethod, "normalizedReference"))
        .isEqualTo("com.example.Service#-()");

    assertThat(invokeString("extractOwnerType", null)).isEmpty();
    assertThat(invokeString("extractOwnerType", "run()")).isEmpty();
    assertThat(invokeString("extractOwnerType", "#run()")).isEmpty();
    assertThat(invokeString("extractOwnerType", "com.example.Service#run()"))
        .isEqualTo("com.example.Service");
  }

  @Test
  void privateHelpers_shouldCoverConstructorAndPrivateSameClassBranches() throws Exception {
    assertThat(invokeBoolean("isConstructorReference", ".", "Type", "Type")).isFalse();
    assertThat(invokeBoolean("isConstructorReference", "com.example.Type", "Type", "Type"))
        .isTrue();
    assertThat(invokeBoolean("isConstructorReference", "com.example.Type", "ignored", "Type"))
        .isTrue();
    assertThat(invokeBoolean("isConstructorReference", "com.example.Type", "other", "Else"))
        .isFalse();
    assertThat(invokeBoolean("isConstructorReference", "com.example.Type", "other", "")).isFalse();

    Object selfNoArg = invokeCanonicalize("com.example.OrderService#OrderService()");
    Object selfArg = invokeCanonicalize("com.example.OrderService#OrderService(java.lang.String)");
    Object innerNoArg = invokeCanonicalize("com.example.OrderService.Inner#Inner()");
    Object externalNoArg = invokeCanonicalize("com.example.External#External()");

    assertThat(invokeImplicitNoArgConstructorCall(null, validationFacts())).isFalse();
    assertThat(invokeImplicitNoArgConstructorCall(selfNoArg, validationFacts())).isTrue();
    assertThat(invokeImplicitNoArgConstructorCall(innerNoArg, validationFacts())).isTrue();
    assertThat(invokeImplicitNoArgConstructorCall(selfArg, validationFacts())).isFalse();
    assertThat(invokeImplicitNoArgConstructorCall(externalNoArg, validationFacts())).isFalse();

    LlmValidationFacts privateFacts =
        validationFacts(Set.of("normalize"), "com.example.orderservice", "orderservice");
    assertThat(invokeIsPrivateSameClassCall(null, privateFacts)).isFalse();
    assertThat(invokeIsPrivateSameClassCall("normalize()", privateFacts)).isTrue();
    assertThat(invokeIsPrivateSameClassCall("com.example.OrderService#normalize()", privateFacts))
        .isTrue();
    assertThat(invokeIsPrivateSameClassCall("com.example.External#normalize()", privateFacts))
        .isFalse();
  }

  @Test
  void privateHelpers_shouldCoverLiteralAndTypeNormalizationBranches() throws Exception {
    assertThat(invokeList("normalizeArgumentLiterals", new Class<?>[] {List.class}, (Object) null))
        .isEmpty();
    assertThat(
            invokeList(
                "normalizeArgumentLiterals",
                new Class<?>[] {List.class},
                List.of("", "   ", "`Beta`", "Alpha")))
        .containsExactly("Alpha", "Beta");

    assertThat(
            invokeList(
                "mergeArgumentLiterals", new Class<?>[] {List.class, List.class}, null, null))
        .isEmpty();
    assertThat(
            invokeList(
                "mergeArgumentLiterals",
                new Class<?>[] {List.class, List.class},
                List.of("Beta"),
                List.of("Alpha", "Beta")))
        .containsExactly("Alpha", "Beta");

    assertThat(
            invokeList(
                "normalizeCalledMethodParameterTypes",
                new Class<?>[] {String.class},
                (String) null))
        .isEmpty();
    assertThat(
            invokeList(
                "normalizeCalledMethodParameterTypes",
                new Class<?>[] {String.class},
                " , java.lang.String , java.util.List<java.lang.Integer>  "))
        .containsExactly("java.lang.String", "java.util.List<java.lang.Integer>");

    assertThat(invokeString("normalizeTypeVariableErasure", (String) null)).isEmpty();
    assertThat(invokeString("normalizeTypeVariableErasure", "T")).isEqualTo("Object");
    assertThat(invokeString("normalizeTypeVariableErasure", "T[]")).isEqualTo("Object[]");
    assertThat(invokeString("normalizeTypeVariableErasure", "String")).isEqualTo("String");

    assertThat(invokeString("eraseGenericArguments", (String) null)).isEmpty();
    assertThat(invokeString("eraseGenericArguments", "java.lang.String"))
        .isEqualTo("java.lang.String");
    assertThat(invokeString("eraseGenericArguments", "java.util.List<java.lang.String>"))
        .isEqualTo("java.util.List");

    assertThat(invokeString("simplifyQualifiedTypes", (String) null)).isEmpty();
    assertThat(invokeString("simplifyQualifiedTypes", "String")).isEqualTo("String");
    assertThat(invokeString("simplifyQualifiedTypes", "java.util.Map.Entry")).isEqualTo("Entry");

    assertThat(invokeString("normalizeTypeName", (String) null)).isEmpty();
    assertThat(invokeString("normalizeTypeName", " Com.Example$Type "))
        .isEqualTo("com.example.type");

    assertThat(
            invokeString(
                "buildSemanticParameterDedupKey",
                new Class<?>[] {List.class},
                List.of("T", "java.util.List<java.lang.String>", "X[]")))
        .isEqualTo("object,list,object[]");
  }

  private CalledMethodRef calledMethodRef(
      String resolved, String raw, ResolutionStatus status, List<String> argumentLiterals) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setResolved(resolved);
    ref.setRaw(raw);
    ref.setStatus(status);
    ref.setArgumentLiterals(argumentLiterals);
    return ref;
  }

  private LlmValidationFacts validationFacts() {
    return validationFacts(Set.of(), "com.example.orderservice", "orderservice");
  }

  private LlmValidationFacts validationFacts(
      Set<String> privateMethodNames, String classFqn, String classSimpleName) {
    return new LlmValidationFacts(
        List.of("processorder"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        java.util.Map.of(),
        Set.of(),
        Set.of(),
        java.util.Map.of(),
        Set.of("processorder"),
        Set.of(),
        privateMethodNames,
        false,
        false,
        "",
        classFqn,
        classSimpleName,
        java.util.Map.of());
  }

  private Object invokeCanonicalize(String calledMethod) throws Exception {
    return invokePrivate(
        "canonicalizeCalledMethodReference", new Class<?>[] {String.class}, calledMethod);
  }

  private boolean invokeBoolean(
      String methodName, String ownerType, String rawMethodToken, String normalizedMethodName)
      throws Exception {
    return (boolean)
        invokePrivate(
            methodName,
            new Class<?>[] {String.class, String.class, String.class},
            ownerType,
            rawMethodToken,
            normalizedMethodName);
  }

  private boolean invokeImplicitNoArgConstructorCall(Object reference, LlmValidationFacts facts)
      throws Exception {
    Class<?> referenceClass =
        Class.forName(
            "com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis"
                + ".LlmCalledMethodFilter$CalledMethodReference");
    Method method =
        LlmCalledMethodFilter.class.getDeclaredMethod(
            "isImplicitNoArgConstructorCall", referenceClass, LlmValidationFacts.class);
    method.setAccessible(true);
    return (boolean) method.invoke(filter, reference, facts);
  }

  private boolean invokeIsPrivateSameClassCall(String calledMethod, LlmValidationFacts facts)
      throws Exception {
    return (boolean)
        invokePrivate(
            "isPrivateSameClassCall",
            new Class<?>[] {String.class, LlmValidationFacts.class},
            calledMethod,
            facts);
  }

  private String invokeString(String methodName, String value) throws Exception {
    return (String) invokePrivate(methodName, new Class<?>[] {String.class}, value);
  }

  private String invokeString(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    return (String) invokePrivate(methodName, parameterTypes, args);
  }

  private List<String> invokeList(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    return (List<String>) invokePrivate(methodName, parameterTypes, args);
  }

  private String readRecordString(Object record, String accessor) throws Exception {
    Method method = record.getClass().getDeclaredMethod(accessor);
    method.setAccessible(true);
    return (String) method.invoke(record);
  }

  private boolean readRecordBoolean(Object record, String accessor) throws Exception {
    Method method = record.getClass().getDeclaredMethod(accessor);
    method.setAccessible(true);
    return (boolean) method.invoke(record);
  }

  private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = LlmCalledMethodFilter.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(filter, args);
  }
}
