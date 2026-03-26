package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DetectionContextTest {

  @Test
  void thresholds_shouldClampAndMaintainInvariant() {
    DetectionContext ctx = new DetectionContext();

    ctx.setMaskThreshold(0.8);
    ctx.setBlockThreshold(0.5);

    assertThat(ctx.getMaskThreshold()).isEqualTo(0.8);
    assertThat(ctx.getBlockThreshold()).isEqualTo(0.8);

    ctx.setMaskThreshold(-1.0);
    ctx.setBlockThreshold(2.0);

    assertThat(ctx.getMaskThreshold()).isEqualTo(0.0);
    assertThat(ctx.getBlockThreshold()).isEqualTo(1.0);
  }

  @Test
  void thresholds_shouldFallbackForNaN() {
    DetectionContext ctx = new DetectionContext();

    ctx.setMaskThreshold(Double.NaN);
    ctx.setBlockThreshold(Double.NaN);

    assertThat(ctx.getMaskThreshold()).isEqualTo(0.60);
    assertThat(ctx.getBlockThreshold()).isEqualTo(0.90);
  }

  @Test
  void allowlist_shouldMatchCaseAndFullWidth() {
    DetectionContext ctx = new DetectionContext();
    ctx.setAllowlistTerms(Set.of("secret42"));

    assertThat(ctx.isAllowlisted("SECRET42")).isTrue();
    assertThat(ctx.isAllowlisted("ＳＥＣＲＥＴ４２")).isTrue();
  }

  @Test
  void detectionFlags_reflectMode() {
    DetectionContext ctx = new DetectionContext();
    ctx.setMode(DetectionContext.Mode.OFF);

    assertThat(ctx.isDetectionEnabled()).isFalse();
    assertThat(ctx.isBlockingEnabled()).isFalse();
  }

  @Test
  void isDetectorEnabled_isCaseInsensitive() {
    DetectionContext ctx = new DetectionContext();
    ctx.setEnabledDetectors(List.of("Regex", "Dictionary"));

    assertThat(ctx.isDetectorEnabled("regex")).isTrue();
    assertThat(ctx.isDetectorEnabled("ML")).isFalse();
  }

  @Test
  void nullInputs_shouldFallbackToSafeDefaults() {
    DetectionContext ctx = new DetectionContext();

    ctx.setMode(null);
    ctx.setEnabledDetectors(null);
    ctx.setAllowlistTerms(null);

    assertThat(ctx.getMode()).isEqualTo(DetectionContext.Mode.ENFORCE);
    assertThat(ctx.getEnabledDetectors()).isEmpty();
    assertThat(ctx.getAllowlistTerms()).isEmpty();
  }

  @Test
  void thresholds_shouldFallbackForInfinity() {
    DetectionContext ctx = new DetectionContext();

    ctx.setMaskThreshold(Double.POSITIVE_INFINITY);
    ctx.setBlockThreshold(Double.NEGATIVE_INFINITY);

    assertThat(ctx.getMaskThreshold()).isEqualTo(0.60);
    assertThat(ctx.getBlockThreshold()).isEqualTo(0.90);
  }

  @Test
  void maskAndBlockDecision_shouldRespectModeAndThresholdBoundaries() {
    DetectionContext ctx =
        DetectionContext.of(DetectionContext.Mode.ENFORCE, List.of("regex"), 0.60, 0.90);

    assertThat(ctx.shouldMask(0.60)).isTrue();
    assertThat(ctx.shouldMask(0.89)).isTrue();
    assertThat(ctx.shouldMask(0.90)).isFalse();
    assertThat(ctx.shouldBlock(0.89)).isFalse();
    assertThat(ctx.shouldBlock(0.90)).isTrue();

    ctx.setMode(DetectionContext.Mode.REPORT);
    assertThat(ctx.shouldMask(0.95)).isFalse();
    assertThat(ctx.shouldBlock(0.95)).isFalse();
  }

  @Test
  void attributes_shouldReturnOnlyMatchingTypes() {
    DetectionContext ctx = new DetectionContext();
    ctx.setAttribute("count", 3);

    assertThat(ctx.getAttribute("count", Integer.class)).contains(3);
    assertThat(ctx.getAttribute("count", String.class)).isEmpty();
    assertThat(ctx.getAttribute("missing", Integer.class)).isEmpty();
  }
}
