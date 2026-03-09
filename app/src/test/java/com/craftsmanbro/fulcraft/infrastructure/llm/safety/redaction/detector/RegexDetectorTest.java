package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.RegexDetector;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegexDetectorTest {

  @Test
  void detect_findsCommonPatternsAndRespectsAllowlist() {
    RegexDetector detector = new RegexDetector();
    DetectionContext ctx = new DetectionContext();
    ctx.setAllowlistTerms(Set.of("support@craftsmann-bro.com", "token123"));

    String text =
        "Email support@craftsmann-bro.com Authorization: Bearer token123 "
            + "jwt aaaaaaaaaa.bbbbbbbbbb.cccccccc card 4242 4242 4242 4242";

    DetectionResult result = detector.detect(text, ctx);

    assertThat(result.findings())
        .extracting(Finding::type)
        .contains(RegexDetector.TYPE_JWT, RegexDetector.TYPE_CREDIT_CARD)
        .doesNotContain(RegexDetector.TYPE_EMAIL, RegexDetector.TYPE_AUTH_TOKEN);
  }

  @Test
  void detect_returnsEmptyOnBlankText() {
    DetectionResult result = new RegexDetector().detect("", new DetectionContext());

    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void detect_extractsQuotedAndBareKeyValueSecrets() {
    RegexDetector detector = new RegexDetector();
    String text = "apiKey=\"quotedSecret\" token=bareSecret";

    DetectionResult result = detector.detect(text, new DetectionContext());

    assertThat(result.findings())
        .filteredOn(f -> RegexDetector.TYPE_AUTH_TOKEN.equals(f.type()))
        .extracting(Finding::snippet)
        .containsExactlyInAnyOrder("quotedSecret", "bareSecret");
  }

  @Test
  void detect_doesNotFlagInvalidCreditCardCandidate() {
    RegexDetector detector = new RegexDetector();
    DetectionResult result = detector.detect("card 1234 5678 9012 3456", new DetectionContext());

    assertThat(result.findings())
        .extracting(Finding::type)
        .doesNotContain(RegexDetector.TYPE_CREDIT_CARD);
  }

  @Test
  void isEnabled_followsContextDetectorList() {
    RegexDetector detector = new RegexDetector();
    DetectionContext ctx = new DetectionContext();
    ctx.setEnabledDetectors(List.of("ml"));

    assertThat(detector.isEnabled(ctx)).isFalse();
  }
}
