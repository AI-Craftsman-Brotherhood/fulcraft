package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrittlenessDetectorTest {

  @Test
  void apply_delegatesToHeuristics_andUnwrapReturnsSameInstance() {
    StubHeuristics heuristics = new StubHeuristics(true);
    BrittlenessDetector detector = new BrittlenessDetector(heuristics);

    boolean result = detector.apply(new AnalysisResult("test"));

    assertTrue(result);
    assertTrue(heuristics.wasCalled());
    assertSame(heuristics, detector.unwrap());
  }

  @Test
  void getAllSignalTypes_returnsAllEnumValues() {
    List<BrittlenessSignal> expected = List.of(BrittlenessSignal.values());

    assertEquals(expected, BrittlenessDetector.getAllSignalTypes());
  }

  private static final class StubHeuristics extends BrittlenessHeuristics {
    private final boolean returnValue;
    private boolean called;

    private StubHeuristics(boolean returnValue) {
      this.returnValue = returnValue;
    }

    @Override
    public boolean apply(AnalysisResult result) {
      called = true;
      return returnValue;
    }

    private boolean wasCalled() {
      return called;
    }
  }
}
