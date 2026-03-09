package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmRequestException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResiliencePolicies;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Base implementation for LLM clients providing common functionality.
 *
 * <p>This abstract class consolidates shared logic across all LLM provider implementations:
 *
 * <ul>
 *   <li>Token usage tracking via ThreadLocal
 *   <li>Response truncation
 *   <li>Java code extraction from markdown code blocks
 *   <li>HTTP client management
 *   <li>Configuration resolution utilities
 * </ul>
 *
 * <p>Subclasses only need to implement provider-specific request building and response parsing.
 */
public abstract class BaseLlmClient implements LlmClientPort, TokenUsageAware {

  /** Pattern to extract code from markdown code blocks. */
  protected static final Pattern CODE_BLOCK_PATTERN =
      Pattern.compile("```[a-zA-Z0-9_+-]*\\s*(.*?)```", Pattern.DOTALL);

  // Common HTTP headers
  protected static final String HDR_CONTENT_TYPE = "Content-Type";

  protected static final String HDR_AUTHORIZATION = "Authorization";

  protected static final String VAL_APP_JSON = "application/json";

  // Default timeouts
  protected static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

  protected static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 300;

  protected static final int DEFAULT_MAX_RESPONSE_LENGTH = 50_000;

  /** Thread-local storage for last token usage. */
  private final ThreadLocal<TokenUsage> lastUsage = new ThreadLocal<>();

  protected final HttpClient httpClient;

  protected final ObjectMapper mapper;

  protected final Config.LlmConfig llmConfig;

  protected final ResiliencePolicies resiliencePolicies;

  protected final int requestTimeout;

  protected final int maxResponseLength;

  /**
   * Constructs a BaseLlmClient with the given configuration.
   *
   * @param config The LLM configuration
   * @param httpClient The HTTP client to use
   * @param mapper The ObjectMapper for JSON processing
   */
  protected BaseLlmClient(
      final Config.LlmConfig config, final HttpClient httpClient, final ObjectMapper mapper) {
    this.llmConfig =
        Objects.requireNonNull(
            config,
            MessageSource.getMessage(
                "infra.common.error.argument_null", "config must not be null"));
    this.httpClient =
        Objects.requireNonNull(
            httpClient,
            MessageSource.getMessage(
                "infra.common.error.argument_null", "httpClient must not be null"));
    this.mapper =
        Objects.requireNonNull(
            mapper,
            MessageSource.getMessage(
                "infra.common.error.argument_null", "mapper must not be null"));
    this.resiliencePolicies = new ResiliencePolicies(this.llmConfig);
    this.requestTimeout =
        resolvePositiveInt(this.llmConfig.getRequestTimeout(), DEFAULT_REQUEST_TIMEOUT_SECONDS);
    this.maxResponseLength =
        resolvePositiveInt(this.llmConfig.getMaxResponseLength(), DEFAULT_MAX_RESPONSE_LENGTH);
  }

  /**
   * Creates a default HTTP client with appropriate timeouts.
   *
   * @param config The LLM configuration
   * @return A configured HttpClient
   */
  protected static HttpClient createDefaultHttpClient(final Config.LlmConfig config) {
    final int timeout =
        resolvePositiveInt(
            config != null ? config.getConnectTimeout() : null, DEFAULT_CONNECT_TIMEOUT_SECONDS);
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
  }

  /**
   * Stores the last token usage for retrieval.
   *
   * @param usage The token usage to store (may be null to clear)
   */
  protected void storeLastUsage(final TokenUsage usage) {
    if (usage == null) {
      lastUsage.remove();
      return;
    }
    lastUsage.set(usage);
  }

  @Override
  public Optional<TokenUsage> getLastUsage() {
    return Optional.ofNullable(lastUsage.get());
  }

  @Override
  public void clearContext() {
    lastUsage.remove();
  }

  /**
   * Truncates the response if it exceeds the maximum allowed length.
   *
   * @param text The response text
   * @return The possibly truncated text
   */
  protected String truncateIfNeeded(final String text) {
    if (text == null || text.length() <= maxResponseLength) {
      return text;
    }
    Logger.warn(
        "LLM response exceeded max length ("
            + text.length()
            + " > "
            + maxResponseLength
            + "), truncating...");
    return text.substring(0, maxResponseLength);
  }

  /**
   * Extracts Java code from a markdown code block or raw text.
   *
   * <p>This method handles various formats:
   *
   * <ul>
   *   <li>```java ... ```
   *   <li>``` ... ```
   *   <li>Raw code without markers
   * </ul>
   *
   * @param response The LLM response potentially containing code blocks
   * @return The extracted Java code
   */
  protected String extractJavaCode(final String response) {
    if (response == null) {
      return "";
    }
    final Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    // Fallback: robust clean if regex fails (e.g. no code block found)
    String cleaned = response.trim();
    cleaned = cleaned.replaceFirst("^```[a-zA-Z0-9_+-]*\\s*", "");
    cleaned = cleaned.replaceFirst("\\s*```$", "");
    return cleaned.trim();
  }

  /**
   * Resolves a positive integer value with fallback.
   *
   * @param value The value to check (may be null)
   * @param fallback The fallback value if value is null or non-positive
   * @return The resolved value
   */
  protected static int resolvePositiveInt(final Integer value, final int fallback) {
    if (value != null && value > 0) {
      return value;
    }
    return fallback;
  }

  /**
   * Resolves a string with fallback to default.
   *
   * @param value The value to check
   * @param defaultValue The default value if value is blank
   * @return The resolved value
   */
  protected static String resolveString(final String value, final String defaultValue) {
    return StringUtils.isNotBlank(value) ? value : defaultValue;
  }

  /**
   * Creates an HTTP request builder with common settings.
   *
   * @param llmRequest The LLM request containing URI, headers, and body
   * @return A configured HttpRequest
   */
  protected HttpRequest buildHttpRequest(final LlmRequest llmRequest) {
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(llmRequest.getUri())
            .timeout(Duration.ofSeconds(requestTimeout))
            .POST(HttpRequest.BodyPublishers.ofString(llmRequest.getRequestBody()));
    llmRequest.getHeaders().forEach(builder::header);
    return builder.build();
  }

  /**
   * Sends an HTTP request and returns the response.
   *
   * @param request The HTTP request to send
   * @return The HTTP response
   */
  protected HttpResponse<String> sendRequest(final HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new LlmRequestException(
          MessageSource.getMessage(
              "infra.common.error.message", "Failed to send LLM request"),
          e,
          true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmRequestException(
          MessageSource.getMessage(
              "infra.common.error.message", "LLM request interrupted"),
          e,
          false);
    }
  }

  /**
   * Gets the LLM configuration.
   *
   * @return The LLM configuration
   */
  protected Config.LlmConfig getLlmConfig() {
    return llmConfig;
  }

  /**
   * Gets the ObjectMapper for JSON processing.
   *
   * @return The ObjectMapper
   */
  protected ObjectMapper getMapper() {
    return mapper;
  }

  /**
   * Executes a task with LLM resilience policies applied.
   *
   * @param task The task to execute
   * @param <T> The result type
   * @return The result
   */
  protected <T> T executeWithResilience(final Callable<T> task) {
    return resiliencePolicies.executeLlmCall(task);
  }
}
