package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.RequestParamWarner;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequestFactory;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceExecutionException;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI-compatible chat completions client.
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for OpenAI's API
 * format is implemented here.
 */
public class OpenAiLlmClient extends BaseLlmClient {

  private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

  private static final String DEFAULT_MODEL = "gpt-4o-mini";

  // JSON keys and role values
  private static final String KEY_MODEL = "model";

  private static final String KEY_MESSAGES = "messages";

  private static final String KEY_ROLE = "role";

  private static final String KEY_CONTENT = "content";

  private static final String ROLE_SYSTEM = "system";

  private static final String ROLE_USER = "user";

  private static final String KEY_TEMPERATURE = "temperature";

  private static final String KEY_TOP_P = "top_p";

  private static final String KEY_CHOICES = "choices";

  private static final String KEY_MESSAGE = "message";

  private static final String KEY_SEED = "seed";

  private static final String KEY_MAX_TOKENS = "max_tokens";

  private static final String KEY_USAGE = "usage";

  private static final String KEY_PROMPT_TOKENS = "prompt_tokens";

  private static final String KEY_COMPLETION_TOKENS = "completion_tokens";

  private static final String KEY_TOTAL_TOKENS = "total_tokens";

  private final String defaultUrl;

  private final String defaultModel;

  private final String defaultApiKey;

  private final Map<String, String> customHeaders;

  public OpenAiLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  // Visible for testing
  protected OpenAiLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.defaultUrl = resolveUrl(config);
    this.defaultModel = resolveModel(config);
    this.defaultApiKey = resolveApiKeyOrThrow(config);
    this.customHeaders =
        Objects.requireNonNullElse(config.getCustomHeaders(), Collections.emptyMap());
  }

  @Override
  public boolean isHealthy() {
    return StringUtils.isNotBlank(defaultApiKey);
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final var cfg = resolveEffectiveConfig(llmConfig);
    final var effectiveConfig = llmConfig != null ? llmConfig : getLlmConfig();
    final var llmRequest = buildLlmRequest(prompt, cfg, effectiveConfig);
    // Warn if request params are unsupported by this provider
    RequestParamWarner.warnIfUnsupported(profile(), llmRequest);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Executing OpenAI request. Hash: " + llmRequest.getRequestHash()));
    try {
      return executeWithResilience(() -> executeRequest(llmRequest));
    } catch (Exception e) {
      return handleGenerateTestException(e);
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
      throw wrapProviderException("Failed to generate test with OpenAI", llmProviderException);
    }
    if (exception.getCause() instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new LlmProviderException(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.message", "Failed to generate test with OpenAI"),
        exception);
  }

  private static LlmProviderException wrapProviderException(
      final String message, final LlmProviderException e) {
    final Throwable cause = e.getCause() != null ? e.getCause() : e;
    final ResilienceExecutionException resilienceCause =
        cause instanceof ResilienceExecutionException rec
            ? rec
            : new ResilienceExecutionException(cause);
    return new LlmProviderException(message, resilienceCause);
  }

  private LlmRequest buildLlmRequest(
      final String prompt, final EffectiveConfig cfg, final Config.LlmConfig effectiveConfig) {
    final String requestBody;
    try {
      requestBody = buildRequestBody(prompt, cfg.model(), effectiveConfig);
    } catch (Exception e) {
      throw new LlmProviderException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to build request body"),
          e);
    }
    // Prepare headers
    final Map<String, String> headers = new java.util.HashMap<>();
    headers.put(HDR_CONTENT_TYPE, VAL_APP_JSON);
    headers.put(HDR_AUTHORIZATION, "Bearer " + cfg.apiKey());
    headers.putAll(customHeaders);
    // Extract generation parameters from factory (single source of truth)
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    return LlmRequest.newBuilder()
        .prompt(prompt)
        .model(cfg.model())
        .temperature(params.temperature())
        .topP(params.topP())
        .seed(params.seed())
        .maxTokens(params.maxTokens())
        .uri(URI.create(cfg.url()))
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

  private static String resolveUrl(final Config.LlmConfig cfg) {
    return StringUtils.isNotBlank(cfg.getUrl()) ? cfg.getUrl() : DEFAULT_URL;
  }

  private static String resolveModel(final Config.LlmConfig cfg) {
    return StringUtils.isNotBlank(cfg.getModelName()) ? cfg.getModelName() : DEFAULT_MODEL;
  }

  private static String resolveApiKeyOrThrow(final Config.LlmConfig cfg) {
    final var apiKey =
        StringUtils.isNotBlank(cfg.getApiKey())
            ? cfg.getApiKey()
            : com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("OPENAI_API_KEY");
    if (StringUtils.isBlank(apiKey)) {
      throw new IllegalStateException(
          "'llm.api_key' or OPENAI_API_KEY env var is required for openai provider");
    }
    return apiKey;
  }

  private EffectiveConfig resolveEffectiveConfig(final Config.LlmConfig cfg) {
    if (cfg == null) {
      return new EffectiveConfig(defaultUrl, defaultModel, defaultApiKey);
    }
    final var url = StringUtils.defaultIfBlank(cfg.getUrl(), defaultUrl);
    final var model = StringUtils.defaultIfBlank(cfg.getModelName(), defaultModel);
    final var apiKey = StringUtils.defaultIfBlank(cfg.getApiKey(), defaultApiKey);
    return new EffectiveConfig(url, model, apiKey);
  }

  private String buildRequestBody(
      final String prompt, final String model, final Config.LlmConfig effectiveConfig)
      throws IOException {
    final ObjectNode requestBody = mapper.createObjectNode();
    requestBody.put(KEY_MODEL, model);
    final ArrayNode messages = requestBody.putArray(KEY_MESSAGES);
    final String systemMessage =
        effectiveConfig != null ? effectiveConfig.getSystemMessage() : null;
    if (systemMessage != null && !systemMessage.isBlank()) {
      final ObjectNode system = messages.addObject();
      system.put(KEY_ROLE, ROLE_SYSTEM);
      system.put(KEY_CONTENT, systemMessage);
    }
    final ObjectNode user = messages.addObject();
    user.put(KEY_ROLE, ROLE_USER);
    user.put(KEY_CONTENT, prompt);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    requestBody.put(KEY_TEMPERATURE, params.temperature());
    if (params.topP() != null) {
      requestBody.put(KEY_TOP_P, params.topP());
    }
    if (params.seed() != null) {
      requestBody.put(KEY_SEED, params.seed());
    }
    if (params.maxTokens() != null) {
      requestBody.put(KEY_MAX_TOKENS, params.maxTokens());
    }
    return mapper.writeValueAsString(requestBody);
  }

  private void validateStatus(final HttpResponse<String> response) {
    final int status = response.statusCode();
    if (status == 200) {
      return;
    }
    final boolean retryable = status == 429 || status >= 500;
    throw new LlmProviderHttpException(
        LlmProviderHttpException.buildMessage("OpenAI error", status, response.body()),
        status,
        response.body(),
        retryable);
  }

  private ResponseData parseResponse(final String responseBody) throws IOException {
    final JsonNode responseNode = mapper.readTree(responseBody);
    final JsonNode contentNode =
        responseNode.path(KEY_CHOICES).path(0).path(KEY_MESSAGE).path(KEY_CONTENT);
    if (contentNode.isMissingNode() || contentNode.isNull()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "OpenAI response missing choices[0].message.content"));
    }
    return new ResponseData(contentNode.asString(), parseUsage(responseNode));
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final JsonNode usageNode = responseNode.path(KEY_USAGE);
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long promptTokens = usageNode.path(KEY_PROMPT_TOKENS).asLong(-1);
    final long completionTokens = usageNode.path(KEY_COMPLETION_TOKENS).asLong(-1);
    final long totalTokens = usageNode.path(KEY_TOTAL_TOKENS).asLong(-1);
    if (promptTokens <= 0 && completionTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, promptTokens), Math.max(0, completionTokens), Math.max(0, totalTokens));
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile(
        "openai", Set.of(Capability.SEED, Capability.SYSTEM_MESSAGE), java.util.Optional.empty());
  }

  record EffectiveConfig(String url, String model, String apiKey) {}

  private record ResponseData(String content, TokenUsage usage) {}
}
