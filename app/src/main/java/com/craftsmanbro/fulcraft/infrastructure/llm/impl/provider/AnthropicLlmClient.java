package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.RequestParamWarner;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequestFactory;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anthropic Messages API client.
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for Anthropic's
 * Messages API format is implemented here.
 */
public class AnthropicLlmClient extends BaseLlmClient {

  private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";

  private static final int DEFAULT_MAX_TOKENS = 2048;

  private static final int HTTP_OK = 200;

  private static final String HEADER_API_KEY = "x-api-key";

  private static final String HEADER_VERSION = "anthropic-version";

  private static final String ANTHROPIC_VERSION = "2023-06-01";

  private static final String JSON_FIELD_MODEL = "model";

  private static final String JSON_FIELD_MAX_TOKENS = "max_tokens";

  private static final String JSON_FIELD_MESSAGES = "messages";

  private static final String JSON_FIELD_ROLE = "role";

  private static final String JSON_FIELD_CONTENT = "content";

  private static final String JSON_FIELD_TEXT = "text";

  private static final String JSON_FIELD_SYSTEM = "system";

  private static final String JSON_FIELD_TEMPERATURE = "temperature";

  private static final String JSON_FIELD_USAGE = "usage";

  private static final String JSON_FIELD_INPUT_TOKENS = "input_tokens";

  private static final String JSON_FIELD_OUTPUT_TOKENS = "output_tokens";

  private static final String JSON_FIELD_TOTAL_TOKENS = "total_tokens";

  private static final String ROLE_USER = "user";

  private final String url;

  private final String model;

  private final String apiKey;

  private final Map<String, String> customHeaders;

  public AnthropicLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  // Visible for testing
  AnthropicLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.url = resolveString(config.getUrl(), DEFAULT_URL);
    this.model =
        requireNonBlank(
            config.getModelName(), "'llm.model_name' is required for anthropic provider");
    this.apiKey = resolveApiKey(config);
    this.customHeaders =
        config.getCustomHeaders() != null
            ? Map.copyOf(config.getCustomHeaders())
            : Collections.emptyMap();
  }

  @Override
  public boolean isHealthy() {
    return StringUtils.isNotBlank(apiKey);
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    // Only override if explicitly provided and not blank
    final var effectiveModel =
        resolveString(llmConfig != null ? llmConfig.getModelName() : null, model);
    final var effectiveUrl = resolveString(llmConfig != null ? llmConfig.getUrl() : null, url);
    final var effectiveConfig = llmConfig != null ? llmConfig : getLlmConfig();
    // Warn if request params are unsupported by this provider
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    RequestParamWarner.warnIfUnsupported(profile(), params.seed());
    try {
      return executeWithResilience(
          () -> generateOnce(prompt, effectiveModel, effectiveUrl, effectiveConfig));
    } catch (Exception exception) {
      return handleGenerateTestException(exception);
    }
  }

  private String handleGenerateTestException(final Exception exception) {
    if (exception instanceof LlmProviderException llmProviderException) {
      throw llmProviderException;
    }
    if (exception.getCause() instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new LlmProviderException(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.message", "Failed to generate test with Anthropic"),
        exception);
  }

  private String generateOnce(
      final String prompt,
      final String effectiveModel,
      final String effectiveUrl,
      final Config.LlmConfig effectiveConfig)
      throws IOException {
    final var requestBody = buildRequestBody(prompt, effectiveModel, effectiveConfig);
    final var request = buildAnthropicHttpRequest(effectiveUrl, requestBody);
    final var response = sendRequest(request);
    final int status = response.statusCode();
    if (status != HTTP_OK) {
      final boolean retryable = status == 429 || status >= 500;
      throw new LlmProviderHttpException(
          LlmProviderHttpException.buildMessage("Anthropic error", status, response.body()),
          status,
          response.body(),
          retryable);
    }
    final var responseData = parseResponse(response.body());
    storeLastUsage(responseData.usage());
    final var generatedText = truncateIfNeeded(responseData.content());
    return extractJavaCode(generatedText);
  }

  private ObjectNode buildRequestBody(
      final String prompt,
      final String effectiveModel,
      final Config.LlmConfig effectiveConfig) {
    final var requestBody = mapper.createObjectNode();
    requestBody.put(JSON_FIELD_MODEL, effectiveModel);
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    requestBody.put(
        JSON_FIELD_MAX_TOKENS,
        params.maxTokens() != null ? params.maxTokens() : DEFAULT_MAX_TOKENS);
    // Use centralized generation parameters from LlmRequestFactory
    requestBody.put(JSON_FIELD_TEMPERATURE, params.temperature());
    // Note: Anthropic does not support seed parameter
    final String systemMessage =
        effectiveConfig != null ? effectiveConfig.getSystemMessage() : null;
    if (systemMessage != null && !systemMessage.isBlank()) {
      requestBody.put(JSON_FIELD_SYSTEM, systemMessage);
    }
    final var messages = requestBody.putArray(JSON_FIELD_MESSAGES);
    final var user = messages.addObject();
    user.put(JSON_FIELD_ROLE, ROLE_USER);
    user.put(JSON_FIELD_CONTENT, prompt);
    return requestBody;
  }

  private HttpRequest buildAnthropicHttpRequest(
      final String effectiveUrl, final ObjectNode requestBody) throws IOException {
    final var requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(effectiveUrl))
            .header(HDR_CONTENT_TYPE, VAL_APP_JSON)
            .header(HEADER_API_KEY, apiKey)
            .header(HEADER_VERSION, ANTHROPIC_VERSION)
            .timeout(Duration.ofSeconds(requestTimeout))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)));
    for (final var customHeader : customHeaders.entrySet()) {
      requestBuilder.header(customHeader.getKey(), customHeader.getValue());
    }
    return requestBuilder.build();
  }

  private ResponseData parseResponse(final String responseBody) throws IOException {
    final var responseNode = mapper.readTree(responseBody);
    final var contentNode = responseNode.path(JSON_FIELD_CONTENT);
    if (!contentNode.isArray() || contentNode.isEmpty()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Anthropic response missing content"));
    }
    final var textNode = contentNode.get(0).path(JSON_FIELD_TEXT);
    if (textNode.isMissingNode() || textNode.isNull()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Anthropic response missing content[0].text"));
    }
    return new ResponseData(textNode.asString(), parseUsage(responseNode));
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final var usageNode = responseNode.path(JSON_FIELD_USAGE);
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long inputTokens = usageNode.path(JSON_FIELD_INPUT_TOKENS).asLong(-1);
    final long outputTokens = usageNode.path(JSON_FIELD_OUTPUT_TOKENS).asLong(-1);
    final long totalTokens = usageNode.path(JSON_FIELD_TOTAL_TOKENS).asLong(-1);
    if (inputTokens <= 0 && outputTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, inputTokens), Math.max(0, outputTokens), Math.max(0, totalTokens));
  }

  private static String requireNonBlank(final String value, final String message) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException(message);
    }
    return value;
  }

  private static String resolveApiKey(final Config.LlmConfig config) {
    final var key =
        resolveString(
            config.getApiKey(),
            com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("ANTHROPIC_API_KEY"));
    return requireNonBlank(
        key, "'llm.api_key' or ANTHROPIC_API_KEY env var is required for anthropic provider");
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile("anthropic", Collections.emptySet(), java.util.Optional.empty());
  }

  private record ResponseData(String content, TokenUsage usage) {}
}
