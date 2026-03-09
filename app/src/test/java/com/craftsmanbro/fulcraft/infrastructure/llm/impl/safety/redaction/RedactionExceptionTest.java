package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedactionExceptionTest {

  @Test
  void blocked_shouldCaptureDetails() {
    List<Finding> findings =
        List.of(
            new Finding("EMAIL", 0, 5, 0.9, "a@b", "regex:email"),
            new Finding("JWT", 10, 20, 0.7, "token", "regex:jwt"));

    RedactionException ex = RedactionException.blocked(findings, 0.8);

    assertThat(ex.getDetectorName()).isEqualTo("regex");
    assertThat(ex.getMaxConfidence()).isEqualTo(0.9);
    assertThat(ex.getBlockingFindings()).hasSize(2);
    assertThat(ex.getMessage()).contains("Detector: regex");
  }

  @Test
  void getDetailedMessage_whenNoFindings_returnsMessage() {
    RedactionException ex = new RedactionException("boom");

    assertThat(ex.getDetailedMessage()).isEqualTo("boom");
  }

  @Test
  void isRedactionFailure_detectsNestedException() {
    RuntimeException nested = new RuntimeException(new RedactionException("blocked"));

    assertThat(RedactionException.isRedactionFailure(nested)).isTrue();
  }

  @Test
  void blocked_withNoFindings_returnsFallbackMessage() {
    RedactionException ex = RedactionException.blocked(List.of(), 0.9);

    assertThat(ex.getMessage()).isEqualTo("Blocked due to policy but no findings recorded");
    assertThat(ex.getBlockingFindings()).isEmpty();
  }

  @Test
  void getBlockingFindings_returnsDefensiveCopy() {
    List<Finding> findings = new java.util.ArrayList<>();
    findings.add(new Finding("EMAIL", 0, 5, 0.9, "a@b", "regex:email"));
    RedactionException ex = RedactionException.blocked(findings, 0.8);

    List<Finding> returned = ex.getBlockingFindings();

    assertThat(returned).hasSize(1);
    assertThat(returned).isNotSameAs(findings);
  }

  @Test
  void isRedactionFailure_returnsFalseWhenAbsent() {
    assertThat(RedactionException.isRedactionFailure(new RuntimeException("boom"))).isFalse();
    assertThat(RedactionException.isRedactionFailure(null)).isFalse();
  }

  @Test
  void deserialization_restoresTransientBlockingFindingsAsEmpty() throws Exception {
    List<Finding> findings = List.of(new Finding("EMAIL", 0, 5, 0.9, "a@b", "regex:email"));
    RedactionException original = RedactionException.blocked(findings, 0.8);

    byte[] payload;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
      payload = bytes.toByteArray();
    }

    RedactionException restored;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(payload))) {
      restored = (RedactionException) in.readObject();
    }

    assertThat(restored.getBlockingFindings()).isEmpty();
    assertThat(restored.getDetectorName()).isEqualTo("regex");
    assertThat(restored.getMaxConfidence()).isEqualTo(0.9);
    assertThat(restored.getDetailedMessage()).doesNotContain("--- Blocking Findings ---");
  }
}
