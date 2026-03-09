package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
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
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Vertex AI (Gemini) generateContent client.
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for Vertex AI's
 * API format is implemented here.
 */
public class VertexAiLlmClient extends BaseLlmClient {

  private static final String DEFAULT_PUBLISHER = "google";

  private static final String VERTEX_AI_URL_TEMPLATE =
      "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/%s/models/%s:generateContent";

  private static final String ENV_ACCESS_TOKEN = "VERTEX_AI_ACCESS_TOKEN";

  private static final String JSON_CONTENTS = "contents";

  private static final String JSON_ROLE = "role";

  private static final String JSON_USER = "user";

  private static final String JSON_PARTS = "parts";

  private static final String JSON_TEXT = "text";

  private static final String JSON_GENERATION_CONFIG = "generationConfig";

  private static final String JSON_SYSTEM_INSTRUCTION = "systemInstruction";

  private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2048;

  private final Map<String, String> customHeaders;

  public VertexAiLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  // Visible for testing
  VertexAiLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.customHeaders = Objects.requireNonNullElse(config.getCustomHeaders(), Collections.emptyMap());
  }

  @Override
  public boolean isHealthy() {
    final var token =
        StringUtils.defaultIfBlank(
            llmConfig.getApiKey(),
            com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get(ENV_ACCESS_TOKEN));
    return StringUtils.isNotBlank(token);
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final var cfg = llmConfig != null ? llmConfig : getLlmConfig();
    final var llmRequest = buildLlmRequest(prompt, cfg);
    // Warn if request params are unsupported by this provider
    RequestParamWarner.warnIfUnsupported(profile(), llmRequest);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Executing Vertex AI request. Hash: " + llmRequest.getRequestHash()));
    try {
      return executeWithResilience(() -> executeRequest(llmRequest));
    } catch (Exception exception) {
      return handleGenerateTestException(exception);
    }
  }

  private String handleGenerateTestException(final Exception exception) {
    if (exception instanceof LlmProviderHttpException llmProviderHttpException) {
      throw llmProviderHttpException;
    }
    if (exception instanceof LlmResponseParseException llmResponseParseException) {
      throw llmResponseParseException;
    }
    if (exception instanceof LlmProviderException llmProviderException) {
      throw new LlmProviderException(
          "Failed to generate test with Vertex AI", llmProviderException);
    }
    if (exception.getCause() instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new LlmProviderException(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.message", "Failed to generate test with Vertex AI"),
        exception);
  }

  private LlmRequest buildLlmRequest(final String prompt, final Config.LlmConfig cfg) {
    final var token = resolveAccessToken(cfg);
    final var url = URI.create(buildUrl(cfg));
    final String requestBody;
    try {
      requestBody = buildRequestBody(prompt, cfg);
    } catch (Exception exception) {
      throw new LlmProviderException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to build request body"),
          exception);
    }
    final Map<String, String> headers = new HashMap<>();
    headers.put(HDR_CONTENT_TYPE, VAL_APP_JSON);
    headers.put(HDR_AUTHORIZATION, "Bearer " + token);
    headers.putAll(customHeaders);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(cfg);
    return LlmRequest.newBuilder()
        .prompt(prompt)
        .model(cfg.getVertexModel())
        .temperature(params.temperature())
        .topP(params.topP())
        .seed(params.seed())
        .maxTokens(params.maxTokens())
        .uri(url)
        .headers(headers)
        .requestBody(requestBody)
        .build();
  }

  private String executeRequest(final LlmRequest llmRequest) throws IOException {
    final var request = buildHttpRequest(llmRequest);
    final var response = sendRequest(request);
    validateStatus(response);
    final var responseData = parseResponse(response.body());
    storeLastUsage(responseData.usage());
    var generatedText = responseData.content();
    generatedText = truncateIfNeeded(generatedText);
    return extractJavaCode(generatedText);
  }

  private String resolveAccessToken(final Config.LlmConfig cfg) {
    final var token =
        StringUtils.defaultIfBlank(
            cfg.getApiKey(),
            com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get(ENV_ACCESS_TOKEN));
    if (StringUtils.isBlank(token)) {
      throw new IllegalStateException(
          "'llm.api_key' (OAuth access token) or "
              + ENV_ACCESS_TOKEN
              + " env var is required for vertex provider");
    }
    return token;
  }

  private String buildRequestBody(final String prompt, final Config.LlmConfig cfg)
      throws IOException {
    final var requestBody = mapper.createObjectNode();
    final var contents = requestBody.putArray(JSON_CONTENTS);
    final var content = contents.addObject();
    content.put(JSON_ROLE, JSON_USER);
    final var parts = content.putArray(JSON_PARTS);
    final var part = parts.addObject();
    part.put(JSON_TEXT, prompt);
    final String systemMessage = cfg != null ? cfg.getSystemMessage() : null;
    if (systemMessage != null && !systemMessage.isBlank()) {
      final var systemInstruction = requestBody.putObject(JSON_SYSTEM_INSTRUCTION);
      final var systemParts = systemInstruction.putArray(JSON_PARTS);
      final var systemPart = systemParts.addObject();
      systemPart.put(JSON_TEXT, systemMessage);
    }
    final var generationConfig = requestBody.putObject(JSON_GENERATION_CONFIG);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(cfg);
    generationConfig.put("temperature", params.temperature());
    if (params.topP() != null) {
      generationConfig.put("topP", params.topP());
    }
    if (params.seed() != null) {
      generationConfig.put("seed", params.seed());
    }
    generationConfig.put(
        "maxOutputTokens",
        params.maxTokens() != null ? params.maxTokens() : DEFAULT_MAX_OUTPUT_TOKENS);
    return mapper.writeValueAsString(requestBody);
  }

  private void validateStatus(final HttpResponse<String> response) {
    final int status = response.statusCode();
    if (status == 200) {
      return;
    }
    final boolean retryable = status == 429 || status >= 500;
    throw new LlmProviderHttpException(
        LlmProviderHttpException.buildMessage("Vertex AI error", status, response.body()),
        status,
        response.body(),
        retryable);
  }

  private ResponseData parseResponse(final String responseBody) throws IOException {
    final var responseNode = mapper.readTree(responseBody);
    final var textNode =
        responseNode
            .path("candidates")
            .path(0)
            .path("content")
            .path(JSON_PARTS)
            .path(0)
            .path(JSON_TEXT);
    if (textNode.isMissingNode() || textNode.isNull()) {
      throw new LlmResponseParseException(
          "Vertex AI response missing candidates[0].content.parts[0].text");
    }
    return new ResponseData(textNode.asString(), parseUsage(responseNode));
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final var usageNode = responseNode.path("usageMetadata");
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

  private String buildUrl(final Config.LlmConfig cfg) {
    if (StringUtils.isNotBlank(cfg.getUrl())) {
      return cfg.getUrl();
    }
    final var publisher =
        StringUtils.isNotBlank(cfg.getVertexPublisher())
            ? cfg.getVertexPublisher()
            : DEFAULT_PUBLISHER;
    // projects/{project}/locations/{location}/publishers/{publisher}/models/{model}:generateContent
    return String.format(
        VERTEX_AI_URL_TEMPLATE,
        cfg.getVertexLocation(),
        cfg.getVertexProject(),
        cfg.getVertexLocation(),
        publisher,
        cfg.getVertexModel());
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile(
        "vertexai", Set.of(Capability.SEED, Capability.SYSTEM_MESSAGE), Optional.empty());
  }

  private record ResponseData(String content, TokenUsage usage) {}
}
