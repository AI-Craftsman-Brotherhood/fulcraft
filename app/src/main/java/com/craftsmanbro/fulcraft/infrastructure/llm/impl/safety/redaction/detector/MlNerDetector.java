package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Machine learning-based Named Entity Recognition (NER) detector.
 *
 * <p>Detects PII (Personally Identifiable Information) and PHI (Protected Health Information) using
 * external ML services. This detector is designed to work with:
 *
 * <ul>
 *   <li>Internal NER APIs (closed network services)
 *   <li>spaCy NER endpoints
 *   <li>Custom ML model endpoints
 * </ul>
 *
 * <p>The detector expects the ML service to return entities in a standardized JSON format:
 *
 * <pre>{@code
 * {
 *   "entities": [
 *     {"text": "John Doe", "label": "PERSON", "start": 10, "end": 18, "score": 0.95},
 *     {"text": "support@craftsmann-bro.com", "label": "EMAIL", "start": 30, "end": 46, "score": 0.99}
 *   ]
 * }
 * }</pre>
 */
public final class MlNerDetector implements SensitiveDetector {

  private static final Logger LOG = LoggerFactory.getLogger(MlNerDetector.class);

  public static final String NAME = "ml";

  // Entity type mapping from common NER labels to our types
  private static final String TYPE_PERSON = "PERSON_NAME";

  private static final String TYPE_ORG = "ORGANIZATION";

  private static final String TYPE_LOCATION = "LOCATION";

  private static final String TYPE_DATE = "DATE";

  private static final String TYPE_SSN = "SSN";

  private static final String TYPE_PHONE = "PHONE";

  private static final String TYPE_MEDICAL = "MEDICAL";

  private static final String TYPE_UNKNOWN = "ML_ENTITY";

  private final String endpointUrl;

  private final HttpClient httpClient;

  private final ObjectMapper objectMapper;

  private final Duration timeout;

  private final String modelName;

  /**
   * Creates a detector with the specified endpoint.
   *
   * @param endpointUrl URL of the ML NER service
   */
  public MlNerDetector(final String endpointUrl) {
    this(endpointUrl, "default", Duration.ofSeconds(10));
  }

  /**
   * Creates a detector with full configuration.
   *
   * @param endpointUrl URL of the ML NER service
   * @param modelName Name of the model (for ruleId)
   * @param timeout Request timeout
   */
  public MlNerDetector(final String endpointUrl, final String modelName, final Duration timeout) {
    this.endpointUrl = endpointUrl;
    this.modelName = modelName;
    this.timeout = timeout;
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.objectMapper = new ObjectMapper();
  }

  // For testing with mock client
  MlNerDetector(
      final String endpointUrl,
      final String modelName,
      final Duration timeout,
      final HttpClient httpClient) {
    this.endpointUrl = endpointUrl;
    this.modelName = modelName;
    this.timeout = timeout;
    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isEnabled(final DetectionContext ctx) {
    return ctx.isDetectorEnabled(NAME) && endpointUrl != null && !endpointUrl.isBlank();
  }

  @Override
  public DetectionResult detect(final String text, final DetectionContext ctx) {
    if (text == null || text.isEmpty() || endpointUrl == null || endpointUrl.isBlank()) {
      return DetectionResult.EMPTY;
    }
    try {
      final NerResponse response = callNerService(text);
      final List<Finding> findings = convertToFindings(response, text, ctx);
      return DetectionResult.of(findings);
    } catch (IOException | InterruptedException | JacksonException e) {
      LOG.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.ml_ner.warn.service_call_failed", e.getMessage()));
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // Return empty result on failure (graceful degradation)
      return DetectionResult.EMPTY;
    }
  }

  private NerResponse callNerService(final String text) throws IOException, InterruptedException {
    final NerRequest request = new NerRequest(text);
    final String requestBody = objectMapper.writeValueAsString(request);
    final HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(endpointUrl))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    final HttpResponse<String> httpResponse =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message",
              "NER service returned status " + httpResponse.statusCode()));
    }
    return objectMapper.readValue(httpResponse.body(), NerResponse.class);
  }

  private List<Finding> convertToFindings(
      final NerResponse response, final String text, final DetectionContext ctx) {
    if (response == null || response.entities == null) {
      return List.of();
    }
    return response.entities.stream()
        .filter(entity -> isValidEntity(entity, text))
        .filter(entity -> !ctx.isAllowlisted(entity.text))
        .map(
            entity -> {
              final String type = mapEntityLabel(entity.label);
              final String ruleId = "ml:" + modelName + ":" + entity.label;
              return new Finding(type, entity.start, entity.end, entity.score, entity.text, ruleId);
            })
        .toList();
  }

  private boolean isValidEntity(final NerEntity entity, final String text) {
    if (entity == null || text == null) {
      return false;
    }
    if (!Double.isFinite(entity.score)) {
      return false;
    }
    final int length = text.length();
    return entity.start >= 0 && entity.end <= length && entity.start < entity.end;
  }

  private String mapEntityLabel(final String label) {
    if (label == null) {
      return TYPE_UNKNOWN;
    }
    return switch (label.toUpperCase(Locale.ROOT)) {
      case "PERSON", "PER" -> TYPE_PERSON;
      case "ORG", TYPE_ORG -> TYPE_ORG;
      case "LOC", TYPE_LOCATION, "GPE" -> TYPE_LOCATION;
      case "DATE", "TIME" -> TYPE_DATE;
      case "SSN", "SOCIAL_SECURITY" -> TYPE_SSN;
      case TYPE_PHONE, "PHONE_NUMBER" -> TYPE_PHONE;
      case TYPE_MEDICAL, "HEALTH", "PHI" -> TYPE_MEDICAL;
      case "EMAIL" -> RegexDetector.TYPE_EMAIL;
      case "CREDIT_CARD", "CARD" -> RegexDetector.TYPE_CREDIT_CARD;
      default -> TYPE_UNKNOWN;
    };
  }

  /** Request format for NER service. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record NerRequest(@JsonProperty("text") String text) {}

  /** Response format from NER service. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class NerResponse {

    @JsonProperty("entities")
    List<NerEntity> entities;
  }

  /** Entity format from NER service. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class NerEntity {

    @JsonProperty("text")
    String text;

    @JsonProperty("label")
    String label;

    @JsonProperty("start")
    int start;

    @JsonProperty("end")
    int end;

    @JsonProperty("score")
    double score;
  }
}
