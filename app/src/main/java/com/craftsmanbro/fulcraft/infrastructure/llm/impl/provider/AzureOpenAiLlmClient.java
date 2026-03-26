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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Azure OpenAI chat completions client.
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for Azure
 * OpenAI's API format is implemented here.
 */
public class AzureOpenAiLlmClient extends BaseLlmClient {

  private static final int HTTP_OK = 200;

  private static final String HEADER_API_KEY = "api-key";

  private static final String JSON_FIELD_MESSAGES = "messages";

  private static final String JSON_FIELD_ROLE = "role";

  private static final String JSON_FIELD_CONTENT = "content";

  private static final String JSON_FIELD_CHOICES = "choices";

  private static final String JSON_FIELD_MESSAGE = "message";

  private static final String JSON_FIELD_TEMPERATURE = "temperature";

  private static final String JSON_FIELD_TOP_P = "top_p";

  private static final String JSON_FIELD_SEED = "seed";

  private static final String JSON_FIELD_MAX_TOKENS = "max_tokens";

  private static final String JSON_FIELD_USAGE = "usage";

  private static final String JSON_FIELD_PROMPT_TOKENS = "prompt_tokens";

  private static final String JSON_FIELD_COMPLETION_TOKENS = "completion_tokens";

  private static final String JSON_FIELD_TOTAL_TOKENS = "total_tokens";

  private static final String ROLE_SYSTEM = "system";

  private static final String ROLE_USER = "user";

  private final String endpointBase;

  private final String deployment;

  private final String apiVersion;

  private final String apiKey;

  private final Map<String, String> customHeaders;

  public AzureOpenAiLlmClient(final Config.LlmConfig config) {
    this(config, createDefaultHttpClient(config), new ObjectMapper());
  }

  // Visible for testing
  AzureOpenAiLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    validateAzureConfig(config);
    this.endpointBase = config.getUrl();
    this.deployment = config.getAzureDeployment();
    this.apiVersion = config.getAzureApiVersion();
    this.apiKey = resolveApiKey(config);
    this.customHeaders =
        config.getCustomHeaders() != null
            ? Map.copyOf(config.getCustomHeaders())
            : Collections.emptyMap();
  }

  private void validateAzureConfig(final Config.LlmConfig config) {
    if (config.getUrl() == null || config.getUrl().isBlank()) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "'llm.url' is required for azure-openai provider"));
    }
    if (config.getAzureDeployment() == null || config.getAzureDeployment().isBlank()) {
      throw new IllegalStateException(
          "'llm.azure_deployment' is required for azure-openai provider");
    }
    if (config.getAzureApiVersion() == null || config.getAzureApiVersion().isBlank()) {
      throw new IllegalStateException(
          "'llm.azure_api_version' is required for azure-openai provider");
    }
  }

  private String resolveApiKey(final Config.LlmConfig config) {
    final var key =
        config.getApiKey() != null && !config.getApiKey().isBlank()
            ? config.getApiKey()
            : com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AZURE_OPENAI_API_KEY");
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(
          "'llm.api_key' or AZURE_OPENAI_API_KEY env var is required for azure-openai provider");
    }
    return key;
  }

  @Override
  public boolean isHealthy() {
    return apiKey != null && !apiKey.isBlank();
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final var llmRequest = buildLlmRequest(prompt, llmConfig);
    // Warn if request params are unsupported by this provider
    RequestParamWarner.warnIfUnsupported(profile(), llmRequest);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Executing Azure OpenAI request. Hash: " + llmRequest.getRequestHash()));
    try {
      return executeWithResilience(() -> executeRequest(llmRequest));
    } catch (Exception e) {
      return handleGenerateTestException(e);
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
            "infra.common.error.message", "Failed to generate test with Azure OpenAI"),
        exception);
  }

  private LlmRequest buildLlmRequest(final String prompt, final Config.LlmConfig config) {
    final var url = buildUrl(config);
    final String requestBody = createRequestBody(prompt, config);
    final Map<String, String> headers = new HashMap<>();
    headers.put(HDR_CONTENT_TYPE, VAL_APP_JSON);
    headers.put(HEADER_API_KEY, apiKey);
    headers.putAll(customHeaders);
    // Use centralized generation parameters from LlmRequestFactory
    final Config.LlmConfig effectiveConfig = (config != null) ? config : getLlmConfig();
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    final String model =
        (effectiveConfig != null) ? effectiveConfig.getAzureDeployment() : deployment;
    return LlmRequest.newBuilder()
        .prompt(prompt)
        .model(model)
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
    final var reqBuilder =
        HttpRequest.newBuilder()
            .uri(llmRequest.getUri())
            .timeout(Duration.ofSeconds(requestTimeout))
            .POST(HttpRequest.BodyPublishers.ofString(llmRequest.getRequestBody()));
    llmRequest.getHeaders().forEach(reqBuilder::header);
    final var response = sendRequest(reqBuilder.build());
    return handleResponse(response);
  }

  private String createRequestBody(final String prompt, final Config.LlmConfig config) {
    final var requestBody = mapper.createObjectNode();
    final var messages = requestBody.putArray(JSON_FIELD_MESSAGES);
    final Config.LlmConfig effectiveConfig = (config != null) ? config : getLlmConfig();
    final String systemMessage =
        effectiveConfig != null ? effectiveConfig.getSystemMessage() : null;
    if (systemMessage != null && !systemMessage.isBlank()) {
      final var system = messages.addObject();
      system.put(JSON_FIELD_ROLE, ROLE_SYSTEM);
      system.put(JSON_FIELD_CONTENT, systemMessage);
    }
    final var user = messages.addObject();
    user.put(JSON_FIELD_ROLE, ROLE_USER);
    user.put(JSON_FIELD_CONTENT, prompt);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(effectiveConfig);
    requestBody.put(JSON_FIELD_TEMPERATURE, params.temperature());
    if (params.topP() != null) {
      requestBody.put(JSON_FIELD_TOP_P, params.topP());
    }
    if (params.seed() != null) {
      requestBody.put(JSON_FIELD_SEED, params.seed());
    }
    if (params.maxTokens() != null) {
      requestBody.put(JSON_FIELD_MAX_TOKENS, params.maxTokens());
    }
    try {
      return mapper.writeValueAsString(requestBody);
    } catch (tools.jackson.core.JacksonException e) {
      throw new LlmProviderException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to serialize request body"),
          e);
    }
  }

  private String handleResponse(final HttpResponse<String> response) throws IOException {
    final int status = response.statusCode();
    if (status != HTTP_OK) {
      final boolean retryable = status == 429 || status >= 500;
      throw new LlmProviderHttpException(
          LlmProviderHttpException.buildMessage("Azure OpenAI error", status, response.body()),
          status,
          response.body(),
          retryable);
    }
    final var responseNode = mapper.readTree(response.body());
    final var choices = responseNode.path(JSON_FIELD_CHOICES);
    if (choices.isEmpty()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Azure OpenAI response has no choices"));
    }
    final var message = choices.get(0).path(JSON_FIELD_MESSAGE);
    final var content = message.path(JSON_FIELD_CONTENT);
    if (content.isMissingNode() || content.isNull()) {
      throw new LlmResponseParseException(
          "Azure OpenAI response missing choices[0].message.content");
    }
    var generatedText = content.asString();
    storeLastUsage(parseUsage(responseNode));
    generatedText = truncateIfNeeded(generatedText);
    return extractJavaCode(generatedText);
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final var usageNode = responseNode.path(JSON_FIELD_USAGE);
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long promptTokens = usageNode.path(JSON_FIELD_PROMPT_TOKENS).asLong(-1);
    final long completionTokens = usageNode.path(JSON_FIELD_COMPLETION_TOKENS).asLong(-1);
    final long totalTokens = usageNode.path(JSON_FIELD_TOTAL_TOKENS).asLong(-1);
    if (promptTokens <= 0 && completionTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, promptTokens), Math.max(0, completionTokens), Math.max(0, totalTokens));
  }

  private URI buildUrl(final Config.LlmConfig llmConfig) {
    final var base =
        llmConfig != null && llmConfig.getUrl() != null && !llmConfig.getUrl().isBlank()
            ? llmConfig.getUrl()
            : endpointBase;
    final var dep =
        llmConfig != null
                && llmConfig.getAzureDeployment() != null
                && !llmConfig.getAzureDeployment().isBlank()
            ? llmConfig.getAzureDeployment()
            : deployment;
    final var ver =
        llmConfig != null
                && llmConfig.getAzureApiVersion() != null
                && !llmConfig.getAzureApiVersion().isBlank()
            ? llmConfig.getAzureApiVersion()
            : apiVersion;
    try {
      final var baseUri = new URI(base);
      var normalizedBase = baseUri.toString();
      if (normalizedBase.endsWith("/")) {
        normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
      }
      return new URI(
          normalizedBase + "/openai/deployments/" + dep + "/chat/completions?api-version=" + ver);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Invalid Azure endpoint URL: " + base),
          e);
    }
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile(
        "azure-openai", Set.of(Capability.SEED, Capability.SYSTEM_MESSAGE), Optional.empty());
  }
}
