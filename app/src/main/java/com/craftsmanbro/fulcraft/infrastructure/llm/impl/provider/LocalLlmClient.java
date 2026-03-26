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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Local OpenAI-compatible chat completions client. */
public class LocalLlmClient extends BaseLlmClient {

  private static final String DEFAULT_URL = "http://localhost:8000/v1";

  private static final String DEFAULT_MODEL = "local-model";

  private static final String PATH_CHAT_COMPLETIONS = "/v1/chat/completions";

  private static final String KEY_MODEL = "model";

  private static final String KEY_MESSAGES = "messages";

  private static final String KEY_ROLE = "role";

  private static final String KEY_CONTENT = "content";

  private static final String KEY_SYSTEM = "system";

  private static final String KEY_USER = "user";

  private static final String KEY_TEMPERATURE = "temperature";

  private static final String KEY_TOP_P = "top_p";

  private static final String KEY_SEED = "seed";

  private static final String KEY_MAX_TOKENS = "max_tokens";

  private static final String KEY_CHOICES = "choices";

  private static final String KEY_MESSAGE = "message";

  private static final String KEY_USAGE = "usage";

  private final String defaultUrl;

  private final String defaultModel;

  private final Map<String, String> customHeaders;

  public LocalLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  // Visible for testing
  LocalLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.defaultUrl = resolveUrl(config);
    this.defaultModel = resolveModel(config);
    this.customHeaders =
        config.getCustomHeaders() != null
            ? Map.copyOf(config.getCustomHeaders())
            : Collections.emptyMap();
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig configOverride) {
    Objects.requireNonNull(
        prompt,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "prompt must not be null"));
    final var cfg = resolveEffectiveConfig(configOverride);
    Logger.warnOnce(
        "local-llm:" + cfg.url(),
        "Using local LLM at " + cfg.url() + " (self-hosted; use at your own risk).");
    final var effectiveConfig = configOverride != null ? configOverride : getLlmConfig();
    final var llmRequest = buildLlmRequest(prompt, cfg, effectiveConfig);
    RequestParamWarner.warnIfUnsupported(profile(), llmRequest);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Executing local LLM request. Hash: " + llmRequest.getRequestHash()));
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
      throw new LlmProviderException(
          "Failed to generate test with local LLM: " + llmProviderException.getMessage(),
          llmProviderException);
    }
    if (exception.getCause() instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    // Include cause message to aid debugging and satisfy tests expecting specific
    // error details.
    throw new LlmProviderException(
        "Failed to generate test with local LLM: " + exception.getMessage(), exception);
  }

  @Override
  public boolean isHealthy() {
    try {
      final URI base = URI.create(defaultUrl);
      final URI modelsUri = resolveModelsUri(base);
      final HttpRequest request =
          HttpRequest.newBuilder(modelsUri).timeout(Duration.ofSeconds(3)).GET().build();
      final HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      final int status = response.statusCode();
      return status > 0 && status < 500;
    } catch (RuntimeException | IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private URI resolveModelsUri(final URI base) {
    String baseUrl = StringUtils.removeEnd(base.toString(), "/");
    if (baseUrl.endsWith(PATH_CHAT_COMPLETIONS)) {
      baseUrl = StringUtils.removeEnd(baseUrl, "/chat/completions");
    }
    if (baseUrl.endsWith("/models")) {
      return URI.create(baseUrl);
    }
    return URI.create(baseUrl + "/models");
  }

  private LlmRequest buildLlmRequest(
      final String prompt, final EffectiveConfig cfg, final Config.LlmConfig effectiveConfig) {
    final String requestBody;
    try {
      requestBody = buildRequestBody(prompt, cfg.model(), effectiveConfig);
    } catch (Exception e) {
      throw new LlmProviderException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to build local LLM request body"),
          e);
    }
    final Map<String, String> headers = new HashMap<>();
    headers.put(HDR_CONTENT_TYPE, VAL_APP_JSON);
    headers.putAll(customHeaders);
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

  private String executeRequest(final LlmRequest llmRequest) {
    final var request = buildHttpRequest(llmRequest);
    final var response = sendRequest(request);
    validateStatus(response);
    final var responseData = parseResponse(response.body());
    storeLastUsage(responseData.usage());
    final var generatedText = truncateIfNeeded(responseData.content());
    return extractJavaCode(generatedText);
  }

  private void validateStatus(final HttpResponse<String> response) {
    final int status = response.statusCode();
    if (status == 200) {
      return;
    }
    final boolean retryable = status == 429 || status >= 500;
    final String message =
        LlmProviderHttpException.buildMessage(
            "Local LLM HTTP error", status, StringUtils.defaultString(response.body()));
    throw new LlmProviderHttpException(message, status, response.body(), retryable);
  }

  private ResponseData parseResponse(final String responseBody) {
    try {
      final JsonNode responseNode = mapper.readTree(responseBody);
      final JsonNode contentNode =
          responseNode.path(KEY_CHOICES).path(0).path(KEY_MESSAGE).path(KEY_CONTENT);
      if (contentNode.isMissingNode() || contentNode.isNull()) {
        throw new LlmResponseParseException(
            "Local LLM response missing choices[0].message.content: " + responseBody);
      }
      return new ResponseData(contentNode.asString(), parseUsage(responseNode));
    } catch (JacksonException e) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to parse local LLM response"),
          e);
    }
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final JsonNode usageNode = responseNode.path(KEY_USAGE);
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long promptTokens = usageNode.path("prompt_tokens").asLong(-1);
    final long completionTokens = usageNode.path("completion_tokens").asLong(-1);
    final long totalTokens = usageNode.path("total_tokens").asLong(-1);
    if (promptTokens <= 0 && completionTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, promptTokens), Math.max(0, completionTokens), Math.max(0, totalTokens));
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
      system.put(KEY_ROLE, KEY_SYSTEM);
      system.put(KEY_CONTENT, systemMessage);
    }
    final ObjectNode user = messages.addObject();
    user.put(KEY_ROLE, KEY_USER);
    user.put(KEY_CONTENT, prompt);
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

  private EffectiveConfig resolveEffectiveConfig(final Config.LlmConfig override) {
    if (override == null) {
      return new EffectiveConfig(normalizeUrl(defaultUrl), defaultModel);
    }
    final String url = StringUtils.defaultIfBlank(override.getUrl(), defaultUrl);
    final String model = StringUtils.defaultIfBlank(override.getModelName(), defaultModel);
    return new EffectiveConfig(normalizeUrl(url), model);
  }

  private static String resolveUrl(final Config.LlmConfig cfg) {
    return StringUtils.defaultIfBlank(cfg.getUrl(), DEFAULT_URL);
  }

  private static String resolveModel(final Config.LlmConfig cfg) {
    return StringUtils.defaultIfBlank(cfg.getModelName(), DEFAULT_MODEL);
  }

  private static String normalizeUrl(final String baseUrl) {
    final String trimmed = StringUtils.removeEnd(baseUrl, "/");
    if (trimmed.endsWith(PATH_CHAT_COMPLETIONS)) {
      return trimmed;
    }
    if (trimmed.endsWith("/v1")) {
      return trimmed + "/chat/completions";
    }
    return trimmed + PATH_CHAT_COMPLETIONS;
  }

  private record ResponseData(String content, TokenUsage usage) {}

  record EffectiveConfig(String url, String model) {}

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile(
        "local", Set.of(Capability.SEED, Capability.SYSTEM_MESSAGE), Optional.empty());
  }
}
