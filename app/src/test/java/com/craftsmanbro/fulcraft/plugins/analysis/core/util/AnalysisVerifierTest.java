package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultVerifierTest {

  private final ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
  private final io.opentelemetry.api.trace.Tracer tracer = OpenTelemetry.noop().getTracer("test");
  private boolean previousJsonMode;

  @BeforeEach
  void setUp() {
    previousJsonMode = Logger.isJsonMode();
    Logger.setJsonMode(false);
    Logger.setOutput(
        new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8),
        new PrintStream(stderrBytes, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    Logger.setOutput(System.out, System.err);
    Logger.setJsonMode(previousJsonMode);
  }

  @Test
  void verify_printsSummaryAndDiffs() {
    AnalysisResult primary =
        analysisResult(classInfo("com.example.A", 2), classInfo("com.example.B", 1));
    AnalysisResult secondary =
        analysisResult(classInfo("com.example.A", 1), classInfo("com.example.C", 3));

    new ResultVerifier(tracer).verify(primary, secondary);

    String out = stdoutBytes.toString(StandardCharsets.UTF_8);
    System.out.println("DEBUG OUT: [" + out + "]");
    assertTrue(out.contains("--- Analysis Verification Report ---"));
    assertTrue(out.contains("Classes\t\t2\t\t2"));
    assertTrue(out.contains("Methods\t\t3\t\t4"));

    assertTrue(out.contains("[!] Classes only in JavaParser (1):"));
    assertTrue(out.contains("  - com.example.B"));

    assertTrue(out.contains("[!] Classes only in Spoon (1):"));
    assertTrue(out.contains("  - com.example.C"));

    assertTrue(out.contains("[!] Method count mismatches (1):"));
    assertTrue(out.contains("com.example.A (JP: 2, Spoon: 1)"));
  }

  @Test
  void verify_handlesNullInputs() {
    assertDoesNotThrow(() -> new ResultVerifier(tracer).verify(null, null));

    String out = stdoutBytes.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Classes\t\t0\t\t0"));
    assertTrue(out.contains("Methods\t\t0\t\t0"));
  }

  @Test
  void verify_doesNotThrowOnDuplicateFqns() {
    AnalysisResult primary =
        analysisResult(classInfo("com.example.A", 1), classInfo("com.example.A", 99));
    AnalysisResult secondary = analysisResult(classInfo("com.example.A", 1));

    assertDoesNotThrow(() -> new ResultVerifier(tracer).verify(primary, secondary));
  }

  @Test
  void verify_printsMethodFieldAndMetadataDiffs() {
    ClassInfo primary = classInfo("com.example.A", 0);
    primary.setMethods(List.of(method("foo", "foo()"), method("bar", "bar()")));
    primary.setFields(List.of(field("id", "String")));
    primary.setExtendsTypes(List.of("Base"));
    primary.setImplementsTypes(List.of("Runnable"));
    primary.setAnnotations(List.of("Service"));

    ClassInfo secondary = classInfo("com.example.A", 0);
    secondary.setMethods(List.of(method("foo", "foo()"), method("baz", "baz()")));
    secondary.setFields(List.of(field("id", "String"), field("count", "int")));
    secondary.setExtendsTypes(List.of("Base2"));
    secondary.setImplementsTypes(List.of("AutoCloseable"));
    secondary.setAnnotations(List.of("Service", "Deprecated"));

    new ResultVerifier(tracer).verify(analysisResult(primary), analysisResult(secondary));

    String out = stdoutBytes.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("[!] Methods only in JavaParser (1):"));
    assertTrue(out.contains("com.example.A missing in Spoon (1): bar()"));
    assertTrue(out.contains("[!] Methods only in Spoon (1):"));
    assertTrue(out.contains("com.example.A missing in JavaParser (1): baz()"));
    assertTrue(!out.contains("[!] Fields only in JavaParser (0):"));
    assertTrue(out.contains("[!] Fields only in Spoon (1):"));
    assertTrue(out.contains("com.example.A missing in JavaParser (1): count"));
    assertTrue(out.contains("[!] Class metadata mismatches (3):"));
    assertTrue(out.contains("com.example.A extends differ"));
    assertTrue(out.contains("com.example.A implements differ"));
    assertTrue(out.contains("com.example.A annotations differ"));
  }

  @Test
  void verify_usesFallbackKeysAndTruncatesLargeDiffLists() {
    ClassInfo sharedPrimary = classInfo("com.example.Shared", 0);
    List<MethodInfo> primaryMethods = new ArrayList<>();
    primaryMethods.add(method("byName", " "));
    MethodInfo byId = method(" ", " ");
    byId.setMethodId("shared#byId");
    primaryMethods.add(byId);
    primaryMethods.add(new MethodInfo());
    for (int i = 0; i < 5; i++) {
      primaryMethods.add(method("extra" + i, " "));
    }
    sharedPrimary.setMethods(primaryMethods);
    sharedPrimary.setFields(List.of(field(" ", "int")));

    ClassInfo sharedSecondary = classInfo("com.example.Shared", 0);
    sharedSecondary.setMethods(List.of());
    sharedSecondary.setFields(List.of(field("count", "int")));

    List<ClassInfo> primaryClasses = new ArrayList<>();
    primaryClasses.add(sharedPrimary);
    for (int i = 0; i < 12; i++) {
      primaryClasses.add(classInfo("com.example.OnlyPrimary" + i, 0));
    }
    List<ClassInfo> secondaryClasses = new ArrayList<>();
    secondaryClasses.add(sharedSecondary);

    new ResultVerifier(tracer)
        .verify(analysisResult(primaryClasses), analysisResult(secondaryClasses));

    String out = stdoutBytes.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("[!] Classes only in JavaParser (12):"));
    assertTrue(out.contains("  ... and 2 more"));
    assertTrue(out.contains("com.example.Shared missing in Spoon (8):"));
    assertTrue(out.contains("<unknown>"));
    assertTrue(out.contains("... and 3 more"));
    assertTrue(out.contains("[!] Fields only in JavaParser (1):"));
    assertTrue(out.contains("[!] Fields only in Spoon (1):"));
  }

  @Test
  void verify_usesMethodIdFallbackWhenNameAndSignatureAreBlank() {
    ClassInfo primary = classInfo("com.example.Fallback", 0);
    MethodInfo method = method(" ", " ");
    method.setMethodId("com.example.Fallback#viaMethodId()");
    primary.setMethods(List.of(method));

    ClassInfo secondary = classInfo("com.example.Fallback", 0);
    secondary.setMethods(List.of());

    new ResultVerifier(tracer).verify(analysisResult(primary), analysisResult(secondary));

    String out = stdoutBytes.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("viaMethodId()"));
  }

  @Test
  void verify_recordsExceptionAndRethrowsWhenScopeCreationFails() {
    Tracer throwingTracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mock(Span.class);
    when(throwingTracer.spanBuilder(any())).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(span.makeCurrent()).thenThrow(new RuntimeException());

    assertThrows(
        RuntimeException.class, () -> new ResultVerifier(throwingTracer).verify(null, null));

    verify(span).recordException(any(RuntimeException.class));
    verify(span).setStatus(eq(StatusCode.ERROR), eq("error"));
    verify(span).end();
  }

  private static AnalysisResult analysisResult(ClassInfo... classes) {
    AnalysisResult result = new AnalysisResult();
    result.setProjectId("proj");
    result.setCommitHash("deadbeef");
    result.setClasses(List.of(classes));
    return result;
  }

  private static AnalysisResult analysisResult(List<ClassInfo> classes) {
    AnalysisResult result = new AnalysisResult();
    result.setProjectId("proj");
    result.setCommitHash("deadbeef");
    result.setClasses(classes);
    return result;
  }

  private static ClassInfo classInfo(String fqn, int methodCount) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setMethodCount(methodCount);
    return classInfo;
  }

  private static MethodInfo method(String name, String signature) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName(name);
    methodInfo.setSignature(signature);
    return methodInfo;
  }

  private static FieldInfo field(String name, String type) {
    FieldInfo fieldInfo = new FieldInfo();
    fieldInfo.setName(name);
    fieldInfo.setType(type);
    return fieldInfo;
  }
}
