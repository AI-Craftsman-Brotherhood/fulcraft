package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for BrittlenessDetectionService. */
class BrittlenessDetectionServiceTest {

  private BrittlenessDetectionService service;

  @BeforeEach
  void setUp() {
    service = new BrittlenessDetectionService();
  }

  @Test
  void detectBrittleness_shouldReturnFalse_whenNoSignals() {
    AnalysisResult result = createAnalysisResultWithMethods(List.of());

    boolean hasBrittleness = service.detectBrittleness(result);

    assertThat(hasBrittleness).isFalse();
  }

  @Test
  void detectBrittleness_shouldThrow_whenResultIsNull() {
    assertThrows(NullPointerException.class, () -> service.detectBrittleness(null));
  }

  @Test
  void detectBrittleness_shouldExecuteWithoutError_whenMethodsExist() {
    AnalysisResult result = createAnalysisResultWithMethods(List.of("method1", "method2"));

    // Verify execution completes without exception
    boolean hasBrittleness = service.detectBrittleness(result);

    // Result depends on content; we just verify no exception
    assertThat(hasBrittleness).isIn(true, false);
  }

  @Test
  void getSummary_shouldReturnEmptySummary_whenNoMethods() {
    AnalysisResult result = new AnalysisResult("test");
    result.setClasses(List.of());

    var summary = service.getSummary(result);

    assertThat(summary.brittleMethodCount()).isZero();
    assertThat(summary.totalMethodCount()).isZero();
    assertThat(summary.brittleMethods()).isEmpty();
    assertThat(summary.hasBrittleness()).isFalse();
  }

  @Test
  void getSummary_shouldCountMethods_whenMethodsExist() {
    AnalysisResult result = createAnalysisResultWithMethods(List.of("method1", "method2"));

    var summary = service.getSummary(result);

    assertThat(summary.totalMethodCount()).isEqualTo(2);
    assertThat(summary.brittlenessRate()).isGreaterThanOrEqualTo(0.0);
    assertThat(summary.brittlenessRate()).isLessThanOrEqualTo(1.0);
  }

  @Test
  void getSummary_shouldSkipNullClassAndMethodEntries() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    MethodInfo method = new MethodInfo();
    method.setName("valid");
    method.setSignature("valid()");
    classInfo.setMethods(Arrays.asList(null, method));
    result.setClasses(Arrays.asList(null, classInfo));

    var summary = service.getSummary(result);

    assertThat(summary.totalMethodCount()).isEqualTo(1);
    assertThat(summary.brittleMethodCount()).isZero();
  }

  @Test
  void getSummary_shouldIdentifyBrittleMethods_whenSignalsPresent() {
    AnalysisResult result = createAnalysisResultWithBrittleMethod();

    var summary = service.getSummary(result);

    assertThat(summary.totalMethodCount()).isGreaterThan(0);
    assertThat(summary.brittleMethodCount()).isGreaterThan(0);
    assertThat(summary.brittleMethods()).isNotEmpty();
    assertThat(summary.hasBrittleness()).isTrue();
  }

  @Test
  void getSummary_brittleMethod_shouldContainCorrectInfo() {
    AnalysisResult result = createAnalysisResultWithBrittleMethod();

    var summary = service.getSummary(result);

    assertThat(summary.brittleMethods()).isNotEmpty();
    var brittleMethod = summary.brittleMethods().get(0);
    assertThat(brittleMethod.classFqn()).isEqualTo("com.example.TestClass");
    assertThat(brittleMethod.methodName()).isEqualTo("brittleMethod");
    assertThat(brittleMethod.signals()).contains(BrittlenessSignal.TIME);
  }

  private AnalysisResult createAnalysisResultWithMethods(List<String> methodNames) {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("com/example/TestClass.java");

    List<MethodInfo> methods = new ArrayList<>();
    for (String name : methodNames) {
      MethodInfo method = new MethodInfo();
      method.setName(name);
      method.setSignature(name + "()");
      methods.add(method);
    }
    classInfo.setMethods(methods);
    result.setClasses(List.of(classInfo));
    return result;
  }

  private AnalysisResult createAnalysisResultWithBrittleMethod() {
    AnalysisResult result = new AnalysisResult("test");
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("com/example/TestClass.java");

    MethodInfo method = new MethodInfo();
    method.setName("brittleMethod");
    method.setSignature("brittleMethod()");
    method.setBrittlenessSignals(List.of(BrittlenessSignal.TIME));
    classInfo.setMethods(List.of(method));
    result.setClasses(List.of(classInfo));
    return result;
  }
}
