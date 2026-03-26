package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectorChain;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DictionaryDetector;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.RegexDetector;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.SensitiveDetector;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DetectorChainTest {

  @Test
  void detect_skipsDisabledDetectors() {
    AtomicBoolean called = new AtomicBoolean(false);
    SensitiveDetector disabled =
        new FixedDetector("disabled", DetectionResult.EMPTY, called, false);

    DetectorChain chain = DetectorChain.builder().add(disabled).build();

    DetectionResult result = chain.detect("text", new DetectionContext());

    assertThat(called.get()).isFalse();
    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void detect_mergesOverlappingFindingsPrefersHigherConfidence() {
    Finding low = new Finding("LOW", 0, 5, 0.6, "abcde", "low:rule");
    Finding high = new Finding("HIGH", 3, 7, 0.9, "cdef", "high:rule");

    DetectorChain chain =
        DetectorChain.builder()
            .add(new FixedDetector("low", DetectionResult.of(List.of(low)), null, true))
            .add(new FixedDetector("high", DetectionResult.of(List.of(high)), null, true))
            .build();

    DetectionResult result = chain.detect("abcdefghij", new DetectionContext());

    assertThat(result.findings()).containsExactly(high);
  }

  @Test
  void detect_continuesWhenDetectorThrows() {
    Finding finding = new Finding("EMAIL", 0, 5, 0.9, "a@b", "regex:email");

    DetectorChain chain =
        DetectorChain.builder()
            .add(new ThrowingDetector())
            .add(new FixedDetector("ok", DetectionResult.of(List.of(finding)), null, true))
            .build();

    DetectionResult result = chain.detect("email a@b", new DetectionContext());

    assertThat(result.findings()).containsExactly(finding);
  }

  @Test
  void detect_returnsEmptyWhenDisabledByContext() {
    DetectionContext ctx = new DetectionContext();
    ctx.setMode(DetectionContext.Mode.OFF);

    DetectorChain chain = DetectorChain.defaultChain();

    DetectionResult result = chain.detect("email a@b", ctx);

    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void detect_returnsEmptyForNullOrEmptyText() {
    AtomicBoolean called = new AtomicBoolean(false);
    DetectorChain chain =
        DetectorChain.builder()
            .add(new FixedDetector("stub", DetectionResult.EMPTY, called, true))
            .build();

    assertThat(chain.detect(null, new DetectionContext())).isSameAs(DetectionResult.EMPTY);
    assertThat(chain.detect("", new DetectionContext())).isSameAs(DetectionResult.EMPTY);
    assertThat(called.get()).isFalse();
  }

  @Test
  void detect_keepsFirstFindingWhenOverlapHasEqualConfidence() {
    Finding first = new Finding("FIRST", 0, 5, 0.8, "alpha", "rule:first");
    Finding second = new Finding("SECOND", 2, 6, 0.8, "lpha", "rule:second");

    DetectorChain chain =
        DetectorChain.builder()
            .add(new FixedDetector("first", DetectionResult.of(List.of(first)), null, true))
            .add(new FixedDetector("second", DetectionResult.of(List.of(second)), null, true))
            .build();

    DetectionResult result = chain.detect("alphabet", new DetectionContext());

    assertThat(result.findings()).containsExactly(first);
  }

  @Test
  void builder_addDefaults_setsExpectedDetectors() {
    DetectorChain chain = DetectorChain.builder().addDefaults().build();

    assertThat(chain.size()).isEqualTo(2);
    assertThat(chain.getDetectorNames())
        .containsExactly(RegexDetector.NAME, DictionaryDetector.NAME);
  }

  @Test
  void builder_addRejectsNullDetector() {
    assertThatThrownBy(() -> DetectorChain.builder().add(null))
        .isInstanceOf(NullPointerException.class);
  }

  private static final class FixedDetector implements SensitiveDetector {
    private final String name;
    private final DetectionResult result;
    private final AtomicBoolean called;
    private final boolean enabled;

    private FixedDetector(
        String name, DetectionResult result, AtomicBoolean called, boolean enabled) {
      this.name = name;
      this.result = result;
      this.called = called;
      this.enabled = enabled;
    }

    @Override
    public DetectionResult detect(String text, DetectionContext ctx) {
      if (called != null) {
        called.set(true);
      }
      return result;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isEnabled(DetectionContext ctx) {
      return enabled;
    }
  }

  private static final class ThrowingDetector implements SensitiveDetector {

    @Override
    public DetectionResult detect(String text, DetectionContext ctx) {
      throw new IllegalStateException("boom");
    }

    @Override
    public String getName() {
      return "throwing";
    }
  }
}
