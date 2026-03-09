package com.craftsmanbro.fulcraft.plugins.analysis.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityFilterInterceptorTest {

  private SecurityFilterInterceptor interceptor;
  private RunContext runContext;
  private Config config;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    interceptor = new SecurityFilterInterceptor();
    config = mock(Config.class);
    runContext = new RunContext(tempDir, config, "test-run-id");
  }

  @Test
  @DisplayName("Should return correct metadata values")
  void testMetadata() {
    assertThat(interceptor.id()).isEqualTo("security-filter");
    assertThat(interceptor.phase()).isEqualTo(PipelineNodeIds.GENERATE);
    assertThat(interceptor.hook()).isEqualTo(Hook.PRE);
    assertThat(interceptor.order()).isEqualTo(50);
    assertThat(interceptor.supports(config)).isTrue();
  }

  @Test
  @DisplayName("Should do nothing if AnalysisResult is missing from context")
  void testApply_NoAnalysisResult() {
    // AnalysisResultContext.get(runContext) will return empty optional by default
    interceptor.apply(runContext);

    // Verify no side effects on metadata or diagnostics
    assertThat(
            runContext
                .getMetadata()
                .containsKey(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS))
        .isFalse();
    assertThat(runContext.getWarnings()).isEmpty();
  }

  @Test
  @DisplayName("Should detect methods with sensitive names (case insensitive)")
  void testApply_SensitiveMethodNames() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.AuthService");

    // Sensitive methods
    MethodInfo loginMethod = createMethod("login", "com.example.AuthService#login");
    MethodInfo updatePassword =
        createMethod("updatePassword", "com.example.AuthService#updatePassword");
    MethodInfo getSecret = createMethod("getSecretKey", "com.example.AuthService#getSecretKey");

    // Non-sensitive method
    MethodInfo getData = createMethod("getData", "com.example.AuthService#getData");

    classInfo.setMethods(List.of(loginMethod, updatePassword, getSecret, getData));
    analysisResult.setClasses(List.of(classInfo));

    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    verifySensitiveMethodsDetected(
        "com.example.AuthService#login",
        "com.example.AuthService#updatePassword",
        "com.example.AuthService#getSecretKey");
  }

  @Test
  @DisplayName("Should detect methods with sensitive annotations")
  void testApply_SensitiveAnnotations() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.SecureController");

    MethodInfo securedMethod =
        createMethod("doAdminWork", "com.example.SecureController#doAdminWork");
    securedMethod.setAnnotations(List.of("@Secured(\"ROLE_ADMIN\")"));

    MethodInfo preAuthMethod =
        createMethod("deleteUser", "com.example.SecureController#deleteUser");
    preAuthMethod.setAnnotations(
        List.of("org.springframework.security.access.prepost.PreAuthorize"));

    MethodInfo normalMethod = createMethod("publicInfo", "com.example.SecureController#publicInfo");
    normalMethod.setAnnotations(List.of("@Override")); // Harmless annotation

    classInfo.setMethods(List.of(securedMethod, preAuthMethod, normalMethod));
    analysisResult.setClasses(List.of(classInfo));

    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    verifySensitiveMethodsDetected(
        "com.example.SecureController#doAdminWork", "com.example.SecureController#deleteUser");
  }

  @Test
  @DisplayName("Should find no sensitive methods if none match")
  void testApply_NoSensitiveMethods() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Utility");

    MethodInfo method1 = createMethod("processData", "com.example.Utility#processData");
    MethodInfo method2 = createMethod("calculate", "com.example.Utility#calculate");

    classInfo.setMethods(List.of(method1, method2));
    analysisResult.setClasses(List.of(classInfo));

    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    assertThat(
            runContext
                .getMetadata()
                .containsKey(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS))
        .isFalse();
    assertThat(runContext.getWarnings()).isEmpty();
  }

  @Test
  @DisplayName("Should ignore null classes or methods gracefully")
  void testApply_NullSafe() {
    AnalysisResult analysisResult = new AnalysisResult();

    // ClassInfo can be null in the list? Technically verify logic handles it
    List<ClassInfo> classes = new java.util.ArrayList<>();
    classes.add(null);
    classes.add(new ClassInfo()); // Empty class info
    analysisResult.setClasses(classes);

    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    // Just ensuring no exception is thrown
    assertThat(runContext.getWarnings()).isEmpty();
  }

  @Test
  @DisplayName("Should parse fully-qualified annotation with @ and arguments")
  void testApply_FullyQualifiedAnnotationWithAtAndArguments() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.SecurityController");

    MethodInfo method =
        createMethod("adminEndpoint", "com.example.SecurityController#adminEndpoint");
    method.setAnnotations(List.of("@org.example.security.PreAuthorize(\"hasRole('ADMIN')\")"));

    classInfo.setMethods(List.of(method));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    verifySensitiveMethodsDetected("com.example.SecurityController#adminEndpoint");
  }

  @Test
  @DisplayName("Should add both full methodId and class#method fallback when they differ")
  void testApply_AddsMethodIdAndSimpleId_WhenDifferent() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.AuthService");

    MethodInfo method =
        createMethod("authenticateUser", "com.example.AuthService#authenticateUser(String)");

    classInfo.setMethods(List.of(method));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    Set<String> detectedIds =
        (Set<String>)
            runContext.getMetadata().get(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);

    assertThat(detectedIds)
        .containsExactlyInAnyOrder(
            "com.example.AuthService#authenticateUser(String)",
            "com.example.AuthService#authenticateUser");
    assertThat(runContext.getWarnings())
        .anyMatch(
            warning ->
                warning.contains("Security-sensitive methods detected")
                    && warning.contains("2 methods"));
  }

  @Test
  @DisplayName("Should fall back to class#method when methodId is blank")
  void testApply_UsesSimpleIdWhenMethodIdBlank() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CryptoService");

    MethodInfo method = createMethod("encryptPayload", " ");
    classInfo.setMethods(List.of(method));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    Set<String> detectedIds =
        (Set<String>)
            runContext.getMetadata().get(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);
    assertThat(detectedIds).containsExactly("com.example.CryptoService#encryptPayload");
  }

  @Test
  @DisplayName("Should deduplicate simple fallback IDs for overloaded sensitive methods")
  void testApply_DeduplicatesFallbackIdsForOverloads() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.AuthService");

    MethodInfo method1 = createMethod("login", "com.example.AuthService#login()");
    MethodInfo method2 = createMethod("login", "com.example.AuthService#login(String)");

    classInfo.setMethods(List.of(method1, method2));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    Set<String> detectedIds =
        (Set<String>)
            runContext.getMetadata().get(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);

    assertThat(detectedIds)
        .containsExactlyInAnyOrder(
            "com.example.AuthService#login()",
            "com.example.AuthService#login(String)",
            "com.example.AuthService#login");
    assertThat(runContext.getWarnings())
        .anyMatch(
            warning ->
                warning.contains("Security-sensitive methods detected")
                    && warning.contains("3 methods"));
  }

  @Test
  @DisplayName("Should ignore blank and unrelated annotations")
  void testApply_IgnoresBlankAndUnrelatedAnnotations() {
    AnalysisResult analysisResult = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Utility");

    MethodInfo method = createMethod("processData", "com.example.Utility#processData");
    List<String> annotations = new java.util.ArrayList<>();
    annotations.add(null);
    annotations.add("");
    annotations.add("  ");
    annotations.add("@Override");
    method.setAnnotations(annotations);

    classInfo.setMethods(List.of(method));
    analysisResult.setClasses(List.of(classInfo));
    AnalysisResultContext.set(runContext, analysisResult);

    interceptor.apply(runContext);

    assertThat(runContext.getMetadata())
        .doesNotContainKey(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);
    assertThat(runContext.getWarnings()).isEmpty();
  }

  private MethodInfo createMethod(String name, String id) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setMethodId(id);
    return method;
  }

  private void verifySensitiveMethodsDetected(String... expectedIds) {
    boolean anyExpected = expectedIds.length > 0;

    if (anyExpected) {
      assertThat(runContext.getMetadata())
          .containsKey(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);

      Set<String> detectedIds =
          (Set<String>)
              runContext.getMetadata().get(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);
      assertThat(detectedIds).contains(expectedIds);

      assertThat(runContext.getWarnings())
          .anyMatch(
              w ->
                  w.contains("Security-sensitive methods detected")
                      && w.contains(String.valueOf(expectedIds.length)));
    } else {
      assertThat(runContext.getMetadata())
          .doesNotContainKey(SecurityFilterInterceptor.METADATA_SENSITIVE_METHOD_IDS);
    }
  }
}
