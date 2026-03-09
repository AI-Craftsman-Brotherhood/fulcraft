package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector.BrittlenessDetectionService;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrittlenessDetectionFlowTest {

  @Test
  void detectBrittleness_delegatesToService() {
    BrittlenessDetectionService service = mock(BrittlenessDetectionService.class);
    BrittlenessDetectionFlow flow = new BrittlenessDetectionFlow(service);
    AnalysisResult result = new AnalysisResult("test");

    when(service.detectBrittleness(result)).thenReturn(true);

    assertThat(flow.detectBrittleness(result)).isTrue();
    verify(service).detectBrittleness(result);
  }

  @Test
  void getSummary_mapsServiceSummary() {
    BrittlenessDetectionService service = mock(BrittlenessDetectionService.class);
    BrittlenessDetectionFlow flow = new BrittlenessDetectionFlow(service);
    AnalysisResult result = new AnalysisResult("test");

    List<BrittlenessSignal> signals = new ArrayList<>();
    signals.add(BrittlenessSignal.TIME);
    BrittlenessDetectionService.BrittleMethod serviceMethod =
        new BrittlenessDetectionService.BrittleMethod(
            "com.example.Test", "method", "method()", signals);
    List<BrittlenessDetectionService.BrittleMethod> serviceMethods = new ArrayList<>();
    serviceMethods.add(serviceMethod);
    BrittlenessDetectionService.BrittlenessSummary serviceSummary =
        new BrittlenessDetectionService.BrittlenessSummary(1, 2, serviceMethods);

    when(service.getSummary(result)).thenReturn(serviceSummary);

    BrittlenessDetectionFlow.BrittlenessSummary summary = flow.getSummary(result);

    assertThat(summary.brittleMethodCount()).isEqualTo(1);
    assertThat(summary.totalMethodCount()).isEqualTo(2);
    assertThat(summary.brittlenessRate()).isEqualTo(0.5);
    assertThat(summary.hasBrittleness()).isTrue();
    assertThat(summary.brittleMethods()).hasSize(1);

    BrittlenessDetectionFlow.BrittleMethod method = summary.brittleMethods().get(0);
    assertThat(method.classFqn()).isEqualTo("com.example.Test");
    assertThat(method.methodName()).isEqualTo("method");
    assertThat(method.signature()).isEqualTo("method()");
    assertThat(method.signals()).containsExactly(BrittlenessSignal.TIME);

    signals.add(BrittlenessSignal.RANDOM);
    assertThat(method.signals()).containsExactly(BrittlenessSignal.TIME);
  }

  @Test
  void brittleMethod_handlesNullSignals() {
    BrittlenessDetectionFlow.BrittleMethod method =
        new BrittlenessDetectionFlow.BrittleMethod("com.example.Test", "method", "method()", null);

    assertThat(method.signals()).isEmpty();
  }

  @Test
  void brittlenessSummary_handlesNullList() {
    BrittlenessDetectionFlow.BrittlenessSummary summary =
        new BrittlenessDetectionFlow.BrittlenessSummary(0, 0, null);

    assertThat(summary.brittleMethods()).isEmpty();
    assertThat(summary.brittlenessRate()).isZero();
    assertThat(summary.hasBrittleness()).isFalse();
  }

  @Test
  void summaryLists_areDefensiveCopies() {
    List<BrittlenessDetectionFlow.BrittleMethod> methods = new ArrayList<>();
    methods.add(
        new BrittlenessDetectionFlow.BrittleMethod(
            "com.example.Test", "method", "method()", List.of(BrittlenessSignal.TIME)));

    BrittlenessDetectionFlow.BrittlenessSummary summary =
        new BrittlenessDetectionFlow.BrittlenessSummary(1, 1, methods);

    assertThatThrownBy(() -> summary.brittleMethods().add(summary.brittleMethods().get(0)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> summary.brittleMethods().get(0).signals().add(BrittlenessSignal.IO))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void constructor_rejectsNullService() {
    assertThatThrownBy(() -> new BrittlenessDetectionFlow(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void detectBrittleness_rejectsNullResult_whenUsingDefaultConstructor() {
    BrittlenessDetectionFlow flow = new BrittlenessDetectionFlow();

    assertThatNullPointerException()
        .isThrownBy(() -> flow.detectBrittleness(null))
        .withMessageContaining("result");
  }

  @Test
  void getSummary_rejectsNullResult_whenUsingDefaultConstructor() {
    BrittlenessDetectionFlow flow = new BrittlenessDetectionFlow();

    assertThatNullPointerException()
        .isThrownBy(() -> flow.getSummary(null))
        .withMessageContaining("result");
  }
}
