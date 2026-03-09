package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmPromptContextFactoryBranchCoverageTest {

  private Locale previousLocale;

  @BeforeEach
  void captureLocale() {
    previousLocale = MessageSource.getLocale();
  }

  @AfterEach
  void restoreLocale() {
    if (previousLocale != null) {
      MessageSource.setLocale(previousLocale);
      return;
    }
    MessageSource.initialize();
  }

  @Test
  void buildClassAttributes_shouldHandleJapaneseEnglishAndNullClass() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);

    MessageSource.setLocale(Locale.JAPANESE);
    ClassInfo nested = new ClassInfo();
    nested.setFqn("com.example.Outer.Inner");
    nested.setNestedClass(true);
    nested.setAnonymous(true);
    nested.setHasNestedClasses(true);
    String japanese = factory.buildClassAttributes(nested);
    assertThat(japanese).contains("- nested_class: true");
    assertThat(japanese).contains("- anonymous_class: true");
    assertThat(japanese).contains("- has_nested_classes: true");
    assertThat(japanese).contains("- enclosing_type: `Outer`");

    MessageSource.setLocale(Locale.ENGLISH);
    String english = factory.buildClassAttributes(null);
    assertThat(english).contains("- nested_class: false");
    assertThat(english).contains("- anonymous_class: false");
    assertThat(english).contains("- has_nested_classes: false");
    assertThat(english).contains("- enclosing_type: ");
  }

  @Test
  void collectKnownMethodNames_shouldNormalizeAndSkipInvalidEntries() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);

    MethodInfo named = new MethodInfo();
    named.setName("  Process  ");
    named.setSignature("public void Process()");

    MethodInfo signatureOnly = new MethodInfo();
    signatureOnly.setName(" ");
    signatureOnly.setSignature("private int compute()");

    MethodInfo invalid = new MethodInfo();
    invalid.setName(" ");
    invalid.setSignature(" ");

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setMethods(List.of(named, signatureOnly, invalid));

    assertThat(factory.collectKnownMethodNames(null)).isEmpty();
    assertThat(factory.collectKnownMethodNames(List.of())).isEmpty();
    assertThat(factory.collectKnownMethodNames(Arrays.asList(null, classInfo)))
        .contains("process", "compute", "n/a");
  }

  @Test
  void buildCautionsInfo_shouldReturnNoneAndRenderAllWarningTypes() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);

    MethodInfo plain = new MethodInfo();
    plain.setName("plain");
    plain.setSignature("void plain()");

    String none = factory.buildCautionsInfo(List.of(plain));
    assertThat(none).doesNotContain("warned");

    MethodInfo warned = new MethodInfo();
    warned.setName("warned");
    warned.setSignature("void warned()");
    warned.setCyclomaticComplexity(17);
    warned.setDeadCode(true);
    warned.setDuplicate(true);
    warned.setUsesRemovedApis(true);
    warned.setPartOfCycle(true);

    String cautions = factory.buildCautionsInfo(List.of(warned));
    assertThat(cautions).contains("- ");
    assertThat(cautions).contains("warned");
  }

  @Test
  void resolveMethodDisplayName_shouldPreferNameThenSignatureThenNa() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);

    MethodInfo fromName = new MethodInfo();
    fromName.setName("  execute  ");
    fromName.setSignature("void ignored()");
    assertThat(factory.resolveMethodDisplayName(fromName)).isEqualTo("execute");

    MethodInfo fromSignature = new MethodInfo();
    fromSignature.setName(" ");
    fromSignature.setSignature("public boolean verify(String id)");
    assertThat(factory.resolveMethodDisplayName(fromSignature)).isEqualTo("verify");

    MethodInfo noData = new MethodInfo();
    noData.setName(" ");
    noData.setSignature(" ");
    assertThat(factory.resolveMethodDisplayName(noData))
        .isEqualTo(factory.resolveMethodDisplayName(null));
  }

  @Test
  void buildPromptContext_shouldExercisePredicateConstructorPathWithDynamicResolution() {
    LlmPromptContextFactory factory =
        new LlmPromptContextFactory(resolution -> true, new MethodDocClassifier());

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public Object resolve()");
    method.setVisibility("public");
    method.setLoc(10);
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .resolvedMethodSig("com.example.ExternalService#resolve(String)")
                .candidates(List.of("com.example.ExternalService#fallback()"))
                .confidence(0.7)
                .build()));

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");
    classInfo.setMethods(List.of(method));

    var context =
        factory.buildPromptContext(
            classInfo,
            Set.of(),
            "{{CLASS_NAME}} {{METHODS_INFO}}",
            (methods, facts) -> "methods=" + methods.size());

    assertThat(context.prompt()).contains("DynamicSample methods=1");
    assertThat(context.validationFacts().uncertainDynamicMethodNames()).contains("fallback");
  }

  @Test
  void privateHelpers_shouldCoverConstructorAndNormalizationBranches() {
    LlmPromptContextFactory factory = new LlmPromptContextFactory(resolution -> false);

    assertThat(invokeEraseGenericArguments(factory, null)).isEmpty();
    assertThat(invokeEraseGenericArguments(factory, "List<String>")).isEqualTo("List");
    assertThat(invokeEraseGenericArguments(factory, "Map<String, List<com.example.Type>>"))
        .isEqualTo("Map");
    assertThat(invokeEraseGenericArguments(factory, "PlainType")).isEqualTo("PlainType");

    assertThat(invokeSimplifyQualifiedTypes(factory, null)).isEmpty();
    assertThat(
            invokeSimplifyQualifiedTypes(
                factory, "java.util.Map<java.lang.String,com.example.Type>"))
        .isEqualTo("Map<String,Type>");
    assertThat(invokeSimplifyQualifiedTypes(factory, "List<String>[]")).isEqualTo("List<String>[]");

    assertThat(invokeIsLikelyParameterName(factory, null)).isFalse();
    assertThat(invokeIsLikelyParameterName(factory, "Value")).isFalse();
    assertThat(invokeIsLikelyParameterName(factory, "a-b")).isFalse();
    assertThat(invokeIsLikelyParameterName(factory, "arg1")).isTrue();

    MethodInfo ctorByName = new MethodInfo();
    ctorByName.setName("Sample");
    assertThat(invokeIsConstructor(factory, ctorByName, "Sample")).isTrue();

    MethodInfo ctorBySignature = new MethodInfo();
    ctorBySignature.setName(" ");
    ctorBySignature.setSignature("public com.example.Sample(java.lang.String value)");
    assertThat(invokeIsConstructor(factory, ctorBySignature, "Sample")).isTrue();

    MethodInfo nonCtor = new MethodInfo();
    nonCtor.setName("build");
    nonCtor.setSignature("public Object build()");
    assertThat(invokeIsConstructor(factory, nonCtor, "Sample")).isFalse();

    MethodInfo branchless = new MethodInfo();
    assertThat(invokeComputeBranchCount(factory, null)).isZero();
    assertThat(invokeComputeBranchCount(factory, branchless)).isZero();

    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(new GuardSummary(), new GuardSummary()));
    summary.setSwitches(List.of("s1"));
    summary.setPredicates(List.of("p1", "p2"));
    MethodInfo branched = new MethodInfo();
    branched.setBranchSummary(summary);
    assertThat(invokeComputeBranchCount(factory, branched)).isEqualTo(5);

    MethodInfo noSig = new MethodInfo();
    noSig.setSignature(" ");
    assertThat(invokeNormalizeConstructorSignature(factory, noSig, "Sample")).isEmpty();

    MethodInfo validCtor = new MethodInfo();
    validCtor.setSignature(
        "public com.example.Sample(final java.util.List<java.lang.String> values, int count)");
    assertThat(invokeNormalizeConstructorSignature(factory, validCtor, "Sample"))
        .isEqualTo("sample(list,int)");
  }

  private String invokeEraseGenericArguments(LlmPromptContextFactory factory, String signature) {
    return (String)
        invoke(factory, "eraseGenericArguments", new Class<?>[] {String.class}, signature);
  }

  private String invokeSimplifyQualifiedTypes(LlmPromptContextFactory factory, String value) {
    return (String) invoke(factory, "simplifyQualifiedTypes", new Class<?>[] {String.class}, value);
  }

  private boolean invokeIsLikelyParameterName(LlmPromptContextFactory factory, String token) {
    return (Boolean) invoke(factory, "isLikelyParameterName", new Class<?>[] {String.class}, token);
  }

  private boolean invokeIsConstructor(
      LlmPromptContextFactory factory, MethodInfo method, String classSimpleName) {
    return (Boolean)
        invoke(
            factory,
            "isConstructor",
            new Class<?>[] {MethodInfo.class, String.class},
            method,
            classSimpleName);
  }

  private int invokeComputeBranchCount(LlmPromptContextFactory factory, MethodInfo method) {
    return (Integer)
        invoke(factory, "computeBranchCount", new Class<?>[] {MethodInfo.class}, method);
  }

  private String invokeNormalizeConstructorSignature(
      LlmPromptContextFactory factory, MethodInfo method, String classSimpleName) {
    return (String)
        invoke(
            factory,
            "normalizeConstructorSignature",
            new Class<?>[] {MethodInfo.class, String.class},
            method,
            classSimpleName);
  }

  private Object invoke(
      LlmPromptContextFactory factory,
      String methodName,
      Class<?>[] parameterTypes,
      Object... args) {
    try {
      Method method = LlmPromptContextFactory.class.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method.invoke(factory, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke " + methodName, e);
    }
  }
}
