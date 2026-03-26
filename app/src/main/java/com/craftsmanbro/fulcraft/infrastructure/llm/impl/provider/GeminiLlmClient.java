package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmRequestException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.RequestParamWarner;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequestFactory;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google Gemini API client.
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for Google's
 * Gemini API format is implemented here.
 */
public class GeminiLlmClient extends BaseLlmClient {

  private static final String DEFAULT_MODEL = "gemini-2.0-flash-exp";

  private static final String BASE_URL_TEMPLATE =
      "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

  private static final String HEALTH_CHECK_URL_TEMPLATE =
      "https://generativelanguage.googleapis.com/v1beta/models?key=%s";

  private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";

  private static final int DEFAULT_MAX_TOKENS = 65_536;

  private static final String JSON_ROLE = "role";

  private static final String JSON_USER = "user";

  private final String apiKey;

  /** Default constructor that resolves configuration from environment or default file paths. */
  public GeminiLlmClient() {
    this(new Config.LlmConfig());
  }

  /**
   * Constructor with configuration object.
   *
   * @param config the LLM configuration
   */
  public GeminiLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  /**
   * Constructor for dependency injection and testing.
   *
   * @param config the LLM configuration
   * @param httpClient the HTTP client
   * @param mapper the Object Mapper
   */
  GeminiLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.apiKey = resolveApiKey(config);
  }

  private static String resolveApiKey(final Config.LlmConfig config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Config must not be null"));
    final String key =
        StringUtils.defaultIfBlank(
            config.getApiKey(),
            com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get(GEMINI_API_KEY_ENV));
    if (StringUtils.isBlank(key)) {
      throw new IllegalStateException(
          "GEMINI_API_KEY environment variable or config api_key not set. "
              + "Get your API key from https://aistudio.google.com/app/apikey");
    }
    return key;
  }

  @Override
  public boolean isHealthy() {
    try {
      if (StringUtils.isBlank(apiKey)) {
        return false;
      }
      final String url = String.format(HEALTH_CHECK_URL_TEMPLATE, apiKey);
      final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      final HttpResponse<String> response = sendRequest(request);
      return response.statusCode() == 200;
    } catch (LlmRequestException ignored) {
      return false;
    }
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final var cfg = (llmConfig != null) ? llmConfig : getLlmConfig();
    final var llmRequest = buildLlmRequest(prompt, cfg);
    // Warn if request params are unsupported by this provider
    RequestParamWarner.warnIfUnsupported(profile(), llmRequest);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Executing Gemini request. Hash: " + llmRequest.getRequestHash()));
    return executeWithResilience(() -> executeRequest(llmRequest));
  }

  private LlmRequest buildLlmRequest(final String prompt, final Config.LlmConfig cfg) {
    final String modelName =
        (cfg != null && StringUtils.isNotBlank(cfg.getModelName()))
            ? cfg.getModelName()
            : DEFAULT_MODEL;
    final String requestBody;
    try {
      requestBody = buildRequestBody(prompt, cfg);
    } catch (Exception e) {
      throw new LlmProviderException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to build request body"),
          e);
    }
    final String url = String.format(BASE_URL_TEMPLATE, modelName, apiKey);
    final Map<String, String> headers = new HashMap<>();
    headers.put(HDR_CONTENT_TYPE, VAL_APP_JSON);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(cfg);
    return LlmRequest.newBuilder()
        .prompt(prompt)
        .model(modelName)
        .temperature(params.temperature())
        .topP(params.topP())
        .seed(params.seed())
        .maxTokens(params.maxTokens())
        .uri(URI.create(url))
        .headers(headers)
        .requestBody(requestBody)
        .build();
  }

  private String executeRequest(final LlmRequest llmReq) throws IOException {
    final var requestBuilder =
        HttpRequest.newBuilder()
            .uri(llmReq.getUri())
            .timeout(Duration.ofSeconds(requestTimeout))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    llmReq.getRequestBody(), StandardCharsets.UTF_8));
    llmReq.getHeaders().forEach(requestBuilder::header);
    final var response = sendRequest(requestBuilder.build());
    if (response.statusCode() != 200) {
      final boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
      throw new LlmProviderHttpException(
          LlmProviderHttpException.buildMessage(
              "Gemini API error", response.statusCode(), response.body()),
          response.statusCode(),
          response.body(),
          retryable);
    }
    final var responseData = parseResponse(response.body());
    storeLastUsage(responseData.usage());
    final var generatedText = truncateIfNeeded(responseData.content());
    return extractJavaCode(generatedText);
  }

  private String buildRequestBody(final String prompt, final Config.LlmConfig cfg)
      throws IOException {
    final var requestBody = mapper.createObjectNode();
    final var contents = requestBody.putArray("contents");
    final var content = contents.addObject();
    content.put(JSON_ROLE, JSON_USER);
    final var parts = content.putArray("parts");
    final var part = parts.addObject();
    part.put("text", prompt);
    final var generationConfig = requestBody.putObject("generationConfig");
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(cfg);
    generationConfig.put("temperature", params.temperature());
    if (params.seed() != null) {
      generationConfig.put("seed", params.seed());
    }
    generationConfig.put(
        "maxOutputTokens", params.maxTokens() != null ? params.maxTokens() : DEFAULT_MAX_TOKENS);
    return mapper.writeValueAsString(requestBody);
  }

  private ResponseData parseResponse(final String responseBody) throws JacksonException {
    final var responseJson = mapper.readTree(responseBody);
    final JsonNode candidates = responseJson.get("candidates");
    if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "No candidates found in response: " + responseBody));
    }
    final JsonNode firstCandidate = candidates.get(0);
    final JsonNode content = firstCandidate.get("content");
    if (content == null) {
      throw new LlmResponseParseException(
          "No content in candidate (possibly blocked): " + responseBody);
    }
    final JsonNode parts = content.get("parts");
    if (parts == null || !parts.isArray() || parts.isEmpty()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "No parts in content: " + responseBody));
    }
    final JsonNode textNode = parts.path(0).path("text");
    if (textNode.isMissingNode() || textNode.isNull()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "No text in content parts: " + responseBody));
    }
    return new ResponseData(textNode.asString(), parseUsage(responseJson));
  }

  private TokenUsage parseUsage(final JsonNode responseJson) {
    final var usageNode = responseJson.path("usageMetadata");
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long promptTokens = usageNode.path("promptTokenCount").asLong(-1);
    final long completionTokens = usageNode.path("candidatesTokenCount").asLong(-1);
    final long totalTokens = usageNode.path("totalTokenCount").asLong(-1);
    if (promptTokens <= 0 && completionTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, promptTokens), Math.max(0, completionTokens), Math.max(0, totalTokens));
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile("gemini", Set.of(Capability.SEED), Optional.empty());
  }

  private record ResponseData(String content, TokenUsage usage) {}
}
