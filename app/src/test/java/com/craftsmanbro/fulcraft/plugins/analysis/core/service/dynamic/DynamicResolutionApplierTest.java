package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicResolutionApplierTest {

  @Test
  void apply_matchesByNormalizedSignatureAndRecomputesCounters() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    MethodInfo method = new MethodInfo();
    method.setName("load");
    method.setSignature("public void load(String id)");
    method.setParameterCount(1);
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Service")
            .methodSig("public void load(java.lang.String id)")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .build();

    DynamicResolutionApplier.apply(result, List.of(resolution));

    assertThat(method.getDynamicResolutions()).hasSize(1);
    assertThat(method.getDynamicFeatureHigh()).isEqualTo(1);
    assertThat(method.getDynamicFeatureMedium()).isZero();
    assertThat(method.getDynamicFeatureLow()).isZero();
  }

  @Test
  void apply_fallsBackToMethodNameAndParameterCountWhenSignatureDiffers() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("legacySignature");
    method.setParameterCount(2);
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Service")
            .methodSig("com.example.Service.process(java.lang.String, int)")
            .subtype(DynamicResolution.INTERPROCEDURAL_SINGLE)
            .confidence(0.6)
            .trustLevel(TrustLevel.LOW)
            .build();

    DynamicResolutionApplier.apply(result, List.of(resolution));

    assertThat(method.getDynamicResolutions()).hasSize(1);
    assertThat(method.getDynamicFeatureLow()).isEqualTo(1);
    assertThat(method.getDynamicFeatureHigh()).isZero();
    assertThat(method.getDynamicFeatureMedium()).isZero();
  }

  @Test
  void apply_clearsStaleDynamicSignalsWhenNoResolutionsProvided() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("void process()");
    method.setParameterCount(0);
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.Service")
                .methodSig("void process()")
                .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
                .confidence(1.0)
                .trustLevel(TrustLevel.HIGH)
                .build()));
    method.setDynamicFeatureHigh(1);
    method.setDynamicFeatureMedium(1);
    method.setDynamicFeatureLow(1);
    method.setDynamicFeatureHasServiceLoader(true);
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));

    DynamicResolutionApplier.apply(result, List.of());

    assertThat(method.getDynamicResolutions()).isEmpty();
    assertThat(method.getDynamicFeatureHigh()).isZero();
    assertThat(method.getDynamicFeatureMedium()).isZero();
    assertThat(method.getDynamicFeatureLow()).isZero();
    assertThat(method.hasDynamicFeatureServiceLoader()).isFalse();
  }

  @Test
  void apply_downgradesTrustCounterWhenVerifiedFlagIsFalse() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public void resolve()");
    method.setParameterCount(0);
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Service")
            .methodSig("public void resolve()")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .evidence(Map.of("verified", "false"))
            .build();

    DynamicResolutionApplier.apply(result, List.of(resolution));

    assertThat(method.getDynamicFeatureHigh()).isZero();
    assertThat(method.getDynamicFeatureMedium()).isZero();
    assertThat(method.getDynamicFeatureLow()).isEqualTo(1);
  }

  @Test
  void apply_returnsWithoutFailureForNullOrEmptyResult() {
    assertThatCode(() -> DynamicResolutionApplier.apply(null, List.of()))
        .doesNotThrowAnyException();

    AnalysisResult emptyResult = new AnalysisResult("empty");
    assertThatCode(() -> DynamicResolutionApplier.apply(emptyResult, List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void apply_skipsInvalidResolutionsAndUnknownTargets() {
    AnalysisResult result = new AnalysisResult("test");

    ClassInfo serviceClass = new ClassInfo();
    serviceClass.setFqn("com.example.Service");
    MethodInfo serviceMethod = new MethodInfo();
    serviceMethod.setName("work");
    serviceMethod.setSignature("void work()");
    serviceMethod.setParameterCount(0);
    serviceMethod.setDynamicFeatureHigh(4);
    serviceMethod.setDynamicFeatureMedium(2);
    serviceMethod.setDynamicFeatureLow(1);
    serviceMethod.setDynamicFeatureHasServiceLoader(true);
    serviceMethod.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.Service")
                .methodSig("void work()")
                .confidence(1.0)
                .build()));
    serviceClass.setMethods(List.of(serviceMethod));

    ClassInfo emptyClass = new ClassInfo();
    emptyClass.setFqn("com.example.Empty");
    emptyClass.setMethods(List.of());

    result.setClasses(List.of(serviceClass, emptyClass));

    List<DynamicResolution> resolutions =
        new ArrayList<>(
            Arrays.asList(
                null,
                DynamicResolution.builder().classFqn(" ").methodSig("void work()").build(),
                DynamicResolution.builder().classFqn("com.example.Service").methodSig(" ").build(),
                DynamicResolution.builder()
                    .classFqn("com.example.Unknown")
                    .methodSig("void work()")
                    .build(),
                DynamicResolution.builder()
                    .classFqn("com.example.Empty")
                    .methodSig("void nothing()")
                    .build(),
                DynamicResolution.builder()
                    .classFqn("com.example.Service")
                    .methodSig("void missing()")
                    .build()));

    DynamicResolutionApplier.apply(result, resolutions);

    assertThat(serviceMethod.getDynamicResolutions()).isEmpty();
    assertThat(serviceMethod.getDynamicFeatureHigh()).isZero();
    assertThat(serviceMethod.getDynamicFeatureMedium()).isZero();
    assertThat(serviceMethod.getDynamicFeatureLow()).isZero();
    assertThat(serviceMethod.hasDynamicFeatureServiceLoader()).isFalse();
  }

  @Test
  void apply_usesMethodNameFallbackWhenParameterCountIsUnavailable() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");

    MethodInfo first = new MethodInfo();
    first.setName("execute");
    first.setSignature("legacyA");
    first.setParameterCount(3);

    MethodInfo second = new MethodInfo();
    second.setName("execute");
    second.setSignature("legacyB");
    second.setParameterCount(1);

    classInfo.setMethods(List.of(first, second));
    result.setClasses(List.of(classInfo));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Service")
            .methodSig("execute")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.95)
            .build();

    DynamicResolutionApplier.apply(result, List.of(resolution));

    assertThat(first.getDynamicResolutions()).hasSize(1);
    assertThat(second.getDynamicResolutions()).isEmpty();
  }

  @Test
  void apply_recomputesMediumCounterAndServiceLoaderFlag() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    MethodInfo method = new MethodInfo();
    method.setName("load");
    method.setSignature("void load()");
    method.setParameterCount(0);
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Service")
            .methodSig("void load()")
            .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
            .confidence(0.7)
            .trustLevel(TrustLevel.MEDIUM)
            .evidence(Map.of("verified", "true"))
            .build();

    DynamicResolutionApplier.apply(result, List.of(resolution));

    assertThat(method.getDynamicFeatureHigh()).isZero();
    assertThat(method.getDynamicFeatureMedium()).isEqualTo(1);
    assertThat(method.getDynamicFeatureLow()).isZero();
    assertThat(method.hasDynamicFeatureServiceLoader()).isTrue();
  }

  @Test
  void privateHelpers_handleEdgeCases() throws Exception {
    assertThat(invokeStatic("extractMethodName", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo("");
    assertThat(invokeStatic("extractMethodName", new Class<?>[] {String.class}, "nameOnly"))
        .isEqualTo("nameOnly");
    assertThat(
            invokeStatic(
                "extractMethodName",
                new Class<?>[] {String.class},
                " public void com.example.Outer$Inner.run(java.lang.String id) "))
        .isEqualTo("run");

    assertThat(invokeStatic("extractParameterCount", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo(-1);
    assertThat(invokeStatic("extractParameterCount", new Class<?>[] {String.class}, "broken("))
        .isEqualTo(-1);
    assertThat(invokeStatic("extractParameterCount", new Class<?>[] {String.class}, "m()"))
        .isEqualTo(0);
    assertThat(
            invokeStatic(
                "extractParameterCount",
                new Class<?>[] {String.class},
                "m(java.util.Map<java.lang.String, java.lang.Integer>, int)"))
        .isEqualTo(2);

    List<String> tokens =
        (List<String>)
            invokeStatic(
                "splitTopLevelCsv",
                new Class<?>[] {String.class},
                "java.util.Map<java.lang.String, java.lang.Integer>, int");
    assertThat(tokens).containsExactly("java.util.Map<java.lang.String, java.lang.Integer>", "int");

    assertThat(invokeStatic("normalizeSignature", new Class<?>[] {String.class}, " "))
        .isEqualTo("");
    assertThat(
            invokeStatic(
                "normalizeSignature",
                new Class<?>[] {String.class},
                " Public void com.example.Outer$Inner.run( java.lang.String ) "))
        .isEqualTo("publicvoidcom.example.outer.inner.run(string)");

    assertThat(
            invokeStatic(
                "isVerifiedFalse",
                new Class<?>[] {DynamicResolution.class},
                (DynamicResolution) null))
        .isEqualTo(false);
    assertThat(
            invokeStatic(
                "isVerifiedFalse",
                new Class<?>[] {DynamicResolution.class},
                DynamicResolution.builder().evidence(Map.of("verified", "true")).build()))
        .isEqualTo(false);

    assertThat(invokeStatic("isBlank", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo(true);
    assertThat(invokeStatic("isBlank", new Class<?>[] {String.class}, "value")).isEqualTo(false);
  }

  private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = DynamicResolutionApplier.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
  }
}
