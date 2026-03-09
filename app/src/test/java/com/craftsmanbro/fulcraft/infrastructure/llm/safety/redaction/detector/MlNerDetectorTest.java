package com.craftsmanbro.fulcraft.infrastructure.llm.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.MlNerDetector;
import java.util.List;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class MlNerDetectorTest {

  @Test
  void detect_parsesEntitiesAndMapsTypes() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setBody(
                  "{\"entities\":[{\"text\":\"John Doe\",\"label\":\"PERSON\",\"start\":0,\"end\":8,\"score\":0.95}]}"));
      server.start();

      MlNerDetector detector = new MlNerDetector(server.url("/ner").toString());
      DetectionContext ctx = new DetectionContext();
      ctx.setAllowlistTerms(Set.of());

      DetectionResult result = detector.detect("John Doe works here", ctx);

      assertThat(result.findings()).hasSize(1);
      Finding finding = result.findings().get(0);
      assertThat(finding.type()).isEqualTo("PERSON_NAME");
      assertThat(finding.ruleId()).isEqualTo("ml:default:PERSON");
    }
  }

  @Test
  void detect_returnsEmptyOnNon200() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(500));
      server.start();

      MlNerDetector detector = new MlNerDetector(server.url("/ner").toString());
      DetectionResult result = detector.detect("John Doe", new DetectionContext());

      assertThat(result.hasFindings()).isFalse();
    }
  }

  @Test
  void detect_filtersInvalidEntitiesAndAllowlist() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setBody(
                  "{\"entities\":["
                      + "{\"text\":\"Jane\",\"label\":\"PERSON\",\"start\":0,\"end\":4,\"score\":0.90},"
                      + "{\"text\":\"Bad\",\"label\":\"PERSON\",\"start\":0,\"end\":50,\"score\":0.90}"
                      + "]}"));
      server.start();

      MlNerDetector detector = new MlNerDetector(server.url("/ner").toString());
      DetectionContext ctx = new DetectionContext();
      ctx.setAllowlistTerms(Set.of("Jane"));

      DetectionResult result = detector.detect("Jane works", ctx);

      assertThat(result.hasFindings()).isFalse();
    }
  }

  @Test
  void isEnabled_requiresEndpointAndContextFlag() {
    DetectionContext ctx = new DetectionContext();
    MlNerDetector blankEndpoint = new MlNerDetector("  ");
    MlNerDetector configured = new MlNerDetector("http://localhost:8080/ner");

    assertThat(blankEndpoint.isEnabled(ctx)).isFalse();

    ctx.setEnabledDetectors(List.of("regex"));
    assertThat(configured.isEnabled(ctx)).isFalse();

    ctx.setEnabledDetectors(List.of("ml"));
    assertThat(configured.isEnabled(ctx)).isTrue();
  }

  @Test
  void detect_mapsKnownLabelsAndFallsBackForUnknownLabel() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setBody(
                  "{\"entities\":["
                      + "{\"text\":\"support@craftsmann-bro.com\",\"label\":\"EMAIL\",\"start\":0,\"end\":16,\"score\":0.91},"
                      + "{\"text\":\"4111111111111111\",\"label\":\"CARD\",\"start\":17,\"end\":33,\"score\":0.92},"
                      + "{\"text\":\"Tokyo\",\"label\":\"GPE\",\"start\":34,\"end\":39,\"score\":0.80},"
                      + "{\"text\":\"X\",\"start\":40,\"end\":41,\"score\":0.70}"
                      + "]}"));
      server.start();

      MlNerDetector detector = new MlNerDetector(server.url("/ner").toString());
      DetectionResult result =
          detector.detect(
              "support@craftsmann-bro.com 4111111111111111 Tokyo X", new DetectionContext());

      assertThat(result.findings())
          .extracting(Finding::type)
          .containsExactly("EMAIL", "CREDIT_CARD", "LOCATION", "ML_ENTITY");
      assertThat(result.findings().get(3).ruleId()).isEqualTo("ml:default:null");
    }
  }

  @Test
  void detect_returnsEmptyOnMalformedJson() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"entities\":["));
      server.start();

      MlNerDetector detector = new MlNerDetector(server.url("/ner").toString());
      DetectionResult result = detector.detect("John Doe", new DetectionContext());

      assertThat(result.hasFindings()).isFalse();
    }
  }
}
