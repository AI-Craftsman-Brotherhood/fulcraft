package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.auth.aws.impl.AwsSigV4Signer;
import com.craftsmanbro.fulcraft.infrastructure.auth.aws.model.AwsCredentials;
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
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AWS Bedrock Runtime client (Anthropic-style messages payload).
 *
 * <p>This client extends {@link BaseLlmClient} to leverage common functionality such as token usage
 * tracking, response truncation, and Java code extraction. Provider-specific logic for AWS
 * Bedrock's API format is implemented here.
 */
public class BedrockLlmClient extends BaseLlmClient {

  private static final String SERVICE_NAME = "bedrock";

  private static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";

  private static final int DEFAULT_MAX_TOKENS = 2048;

  private final Clock clock;

  private final Map<String, String> customHeaders;

  public BedrockLlmClient(final Config.LlmConfig config) {
    this(config, Clock.systemUTC());
  }

  public BedrockLlmClient(final Config.LlmConfig config, final Clock clock) {
    this(config, clock, createDefaultHttpClient(config), new ObjectMapper());
  }

  /** Protected constructor for testing and DI. */
  protected BedrockLlmClient(
      final Config.LlmConfig config,
      final Clock clock,
      final HttpClient httpClient,
      final ObjectMapper mapper) {
    super(config, httpClient, mapper);
    this.clock =
        Objects.requireNonNull(
            clock,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "Clock must not be null"));
    this.customHeaders =
        config.getCustomHeaders() != null ? config.getCustomHeaders() : Collections.emptyMap();
  }

  @Override
  public boolean isHealthy() {
    return resolveAccessKeyId(llmConfig) != null && resolveSecretAccessKey(llmConfig) != null;
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    final Config.LlmConfig cfg = llmConfig != null ? llmConfig : getLlmConfig();
    final String modelId = cfg.getModelName();
    if (modelId == null || modelId.isBlank()) {
      throw new IllegalStateException(
          "'llm.model_name' (model id) is required for bedrock provider");
    }
    final String region = resolveRegion(cfg);
    final String accessKeyId = resolveAccessKeyId(cfg);
    final String secretAccessKey = resolveSecretAccessKey(cfg);
    final String sessionToken = resolveSessionToken(cfg);
    if (region == null || accessKeyId == null || secretAccessKey == null) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message",
              "AWS credentials/region are required for bedrock provider"));
    }
    final URI url = URI.create(buildUrl(cfg, region, modelId));
    // Warn if request params are unsupported by this provider
    final var params = LlmRequestFactory.resolveParams(cfg);
    RequestParamWarner.warnIfUnsupported(profile(), params.seed());
    try {
      return executeWithResilience(
          () -> {
            final String body = createRequestBody(prompt, cfg);
            final Instant now = clock.instant();
            final Map<String, String> sigHeaders =
                AwsSigV4Signer.signHeaders(
                    "POST",
                    url,
                    region,
                    SERVICE_NAME,
                    new AwsCredentials(accessKeyId, secretAccessKey, sessionToken),
                    body,
                    now);
            final HttpRequest request = buildBedrockRequest(url, body, sigHeaders);
            final HttpResponse<String> response = sendRequest(request);
            return handleResponse(response);
          });
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
            "infra.common.error.message", "Failed to generate test with Bedrock"),
        exception);
  }

  private String createRequestBody(final String prompt, final Config.LlmConfig cfg)
      throws IOException {
    final ObjectNode requestBody = mapper.createObjectNode();
    requestBody.put("anthropic_version", DEFAULT_ANTHROPIC_VERSION);
    // Use centralized generation parameters from LlmRequestFactory
    final var params = LlmRequestFactory.resolveParams(cfg);
    requestBody.put(
        "max_tokens", params.maxTokens() != null ? params.maxTokens() : DEFAULT_MAX_TOKENS);
    requestBody.put("temperature", params.temperature());
    // Note: Bedrock (Anthropic) does not support seed parameter
    final String systemMessage = cfg != null ? cfg.getSystemMessage() : null;
    if (systemMessage != null && !systemMessage.isBlank()) {
      requestBody.put("system", systemMessage);
    }
    final ArrayNode messages = requestBody.putArray("messages");
    final ObjectNode user = messages.addObject();
    user.put("role", "user");
    final ArrayNode content = user.putArray("content");
    final ObjectNode textBlock = content.addObject();
    textBlock.put("type", "text");
    textBlock.put("text", prompt);
    return mapper.writeValueAsString(requestBody);
  }

  private HttpRequest buildBedrockRequest(
      final URI url, final String body, final Map<String, String> sigHeaders) {
    final HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(url)
            .header(HDR_CONTENT_TYPE, VAL_APP_JSON)
            .timeout(Duration.ofSeconds(requestTimeout))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    for (final Map.Entry<String, String> header : sigHeaders.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }
    for (final Map.Entry<String, String> header : customHeaders.entrySet()) {
      requestBuilder.header(header.getKey(), header.getValue());
    }
    return requestBuilder.build();
  }

  private String handleResponse(final HttpResponse<String> response) throws IOException {
    final int status = response.statusCode();
    if (status != 200) {
      final boolean retryable = status == 429 || status >= 500;
      throw new LlmProviderHttpException(
          LlmProviderHttpException.buildMessage("Bedrock error", status, response.body()),
          status,
          response.body(),
          retryable);
    }
    final JsonNode responseNode = mapper.readTree(response.body());
    final JsonNode contentArray = responseNode.path("content");
    if (contentArray.isEmpty()) {
      throw new LlmResponseParseException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Bedrock response has no content items"));
    }
    final StringBuilder generatedText = new StringBuilder();
    for (final JsonNode item : contentArray) {
      if ("text".equals(item.path("type").asString())) {
        generatedText.append(item.path("text").asString());
      }
    }
    storeLastUsage(parseUsage(responseNode));
    String result = generatedText.toString();
    result = truncateIfNeeded(result);
    return extractJavaCode(result);
  }

  private TokenUsage parseUsage(final JsonNode responseNode) {
    final JsonNode usageNode = responseNode.path("usage");
    if (usageNode.isMissingNode() || usageNode.isNull()) {
      return null;
    }
    final long inputTokens = usageNode.path("input_tokens").asLong(-1);
    final long outputTokens = usageNode.path("output_tokens").asLong(-1);
    final long totalTokens = usageNode.path("total_tokens").asLong(-1);
    if (inputTokens <= 0 && outputTokens <= 0 && totalTokens <= 0) {
      return null;
    }
    return new TokenUsage(
        Math.max(0, inputTokens), Math.max(0, outputTokens), Math.max(0, totalTokens));
  }

  private String buildUrl(final Config.LlmConfig cfg, final String region, final String modelId) {
    if (cfg.getUrl() != null && !cfg.getUrl().isBlank()) {
      return cfg.getUrl();
    }
    return "https://bedrock-runtime." + region + ".amazonaws.com/model/" + modelId + "/invoke";
  }

  private String resolveRegion(final Config.LlmConfig cfg) {
    if (cfg.getAwsRegion() != null && !cfg.getAwsRegion().isBlank()) {
      return cfg.getAwsRegion();
    }
    final String env = com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AWS_REGION");
    if (env != null && !env.isBlank()) {
      return env;
    }
    final String defaultRegion =
        com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AWS_DEFAULT_REGION");
    return (defaultRegion != null && !defaultRegion.isBlank()) ? defaultRegion : null;
  }

  private String resolveAccessKeyId(final Config.LlmConfig cfg) {
    if (cfg.getAwsAccessKeyId() != null && !cfg.getAwsAccessKeyId().isBlank()) {
      return cfg.getAwsAccessKeyId();
    }
    final String env =
        com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AWS_ACCESS_KEY_ID");
    return (env != null && !env.isBlank()) ? env : null;
  }

  @SuppressWarnings("SameReturnValue")
  private String resolveSecretAccessKey(final Config.LlmConfig cfg) {
    if (cfg.getAwsSecretAccessKey() != null && !cfg.getAwsSecretAccessKey().isBlank()) {
      return cfg.getAwsSecretAccessKey();
    }
    final String env =
        com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AWS_SECRET_ACCESS_KEY");
    return (env != null && !env.isBlank()) ? env : null;
  }

  private String resolveSessionToken(final Config.LlmConfig cfg) {
    if (cfg.getAwsSessionToken() != null && !cfg.getAwsSessionToken().isBlank()) {
      return cfg.getAwsSessionToken();
    }
    final String env =
        com.craftsmanbro.fulcraft.infrastructure.system.impl.Env.get("AWS_SESSION_TOKEN");
    return (env != null && !env.isBlank()) ? env : null;
  }

  @Override
  public ProviderProfile profile() {
    return new ProviderProfile(SERVICE_NAME, Collections.emptySet(), java.util.Optional.empty());
  }
}
