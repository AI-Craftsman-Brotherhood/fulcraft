package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.SensitiveDetector;
import org.junit.jupiter.api.Test;

class SensitiveDetectorTest {

  @Test
  void defaultIsEnabled_returnsTrue() {
    SensitiveDetector detector =
        new SensitiveDetector() {
          @Override
          public DetectionResult detect(String text, DetectionContext ctx) {
            return DetectionResult.EMPTY;
          }

          @Override
          public String getName() {
            return "stub";
          }
        };

    assertTrue(detector.isEnabled(new DetectionContext()));
  }
}
