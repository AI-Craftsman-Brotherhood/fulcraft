package com.craftsmanbro.fulcraft.infrastructure.llm.impl.request;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates all information needed to send an LLM request. Designed to be immutable and reusable
 * across retries.
 */
public final class LlmRequest {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private static final String ERROR_MESSAGE_KEY = "infra.common.error.message";

  private static final String HASH_ALGORITHM = "SHA-256";

  private final String prompt;

  private final String model;

  private final Double temperature;

  private final Double topP;

  private final Integer seed;

  private final Integer maxTokens;

  private final URI uri;

  private final Map<String, String> headers;

  private final String requestBody;

  private final String requestHash;

  private LlmRequest(final Builder builder) {
    this.prompt = builder.prompt;
    this.model = builder.model;
    this.temperature = builder.temperature;
    this.topP = builder.topP;
    this.seed = builder.seed;
    this.maxTokens = builder.maxTokens;
    this.uri = builder.uri;
    this.headers = builder.headers != null ? Map.copyOf(builder.headers) : Collections.emptyMap();
    this.requestBody = builder.requestBody;
    this.requestHash = calculateHash(this.requestBody, this.uri, this.headers);
  }

  public String getPrompt() {
    return prompt;
  }

  public String getModel() {
    return model;
  }

  public Double getTemperature() {
    return temperature;
  }

  public Double getTopP() {
    return topP;
  }

  public Integer getSeed() {
    return seed;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public URI getUri() {
    return uri;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public String getRequestHash() {
    return requestHash;
  }

  private static String calculateHash(
      final String requestBody, final URI uri, final Map<String, String> headers) {
    if (requestBody == null) {
      return "";
    }
    final StringBuilder hashMaterial = new StringBuilder();
    if (uri != null) {
      hashMaterial.append(uri).append('\n');
    }
    if (headers != null && !headers.isEmpty()) {
      headers.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry ->
                  hashMaterial
                      .append(entry.getKey())
                      .append('=')
                      .append(entry.getValue())
                      .append('\n'));
    }
    hashMaterial.append(requestBody);
    try {
      final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      final byte[] hash = digest.digest(hashMaterial.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          MessageSource.getMessage(ERROR_MESSAGE_KEY, HASH_ALGORITHM + " algorithm not available"),
          e);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }

  public static class Builder {

    private String prompt;

    private String model;

    private Double temperature;

    private Double topP;

    private Integer seed;

    private Integer maxTokens;

    private URI uri;

    private Map<String, String> headers;

    private String requestBody;

    public Builder prompt(final String prompt) {
      this.prompt = prompt;
      return this;
    }

    public Builder model(final String model) {
      this.model = model;
      return this;
    }

    public Builder temperature(final Double temperature) {
      this.temperature = temperature;
      return this;
    }

    public Builder topP(final Double topP) {
      this.topP = topP;
      return this;
    }

    public Builder seed(final Integer seed) {
      this.seed = seed;
      return this;
    }

    public Builder maxTokens(final Integer maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder uri(final URI uri) {
      this.uri = uri;
      return this;
    }

    public Builder headers(final Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public Builder requestBody(final String requestBody) {
      this.requestBody = requestBody;
      return this;
    }

    public LlmRequest build() {
      Objects.requireNonNull(uri, argumentNullMessage("URI must not be null"));
      Objects.requireNonNull(requestBody, argumentNullMessage("Request body must not be null"));
      return new LlmRequest(this);
    }
  }
}
