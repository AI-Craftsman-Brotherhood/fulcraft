package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmRequestException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BaseLlmClientTest {

  @Test
  void clearContext_removesLastUsageBetweenCalls() {
    TestLlmClient client = new TestLlmClient(new Config.LlmConfig());

    client.setUsage(new TokenUsage(1, 2, 3));
    assertTrue(client.getLastUsage().isPresent());

    client.clearContext();
    assertTrue(client.getLastUsage().isEmpty());

    client.setUsage(new TokenUsage(4, 5, 9));
    assertEquals(9L, client.getLastUsage().orElseThrow().getTotalTokens());
  }

  @Test
  void extractJavaCode_returnsTrimmedCodeBlock() {
    TestLlmClient client = new TestLlmClient(new Config.LlmConfig());

    String response =
        """
        prefix
        ```java
        class Foo {}
        ```
        suffix
        """;

    assertEquals("class Foo {}", client.callExtractJavaCode(response));
  }

  @Test
  void extractJavaCode_returnsTrimmedRawTextWhenNoBlock() {
    TestLlmClient client = new TestLlmClient(new Config.LlmConfig());

    assertEquals("class Foo {}", client.callExtractJavaCode("  class Foo {}  "));
  }

  @Test
  void extractJavaCode_returnsEmptyStringWhenNull() {
    TestLlmClient client = new TestLlmClient(new Config.LlmConfig());

    assertEquals("", client.callExtractJavaCode(null));
  }

  @Test
  void truncateIfNeeded_respectsMaxLength() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setMaxResponseLength(5);

    TestLlmClient client = new TestLlmClient(config);

    assertEquals("abcde", client.callTruncateIfNeeded("abcdef"));
    assertEquals("abc", client.callTruncateIfNeeded("abc"));
  }

  @Test
  void resolveHelpers_applyFallbacks() {
    assertEquals(7, TestLlmClient.callResolvePositiveInt(null, 7));
    assertEquals(7, TestLlmClient.callResolvePositiveInt(0, 7));
    assertEquals(8, TestLlmClient.callResolvePositiveInt(8, 7));

    assertEquals("default", TestLlmClient.callResolveString(" ", "default"));
    assertEquals("value", TestLlmClient.callResolveString("value", "default"));
  }

  @Test
  void buildHttpRequest_setsTimeoutAndHeaders() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setRequestTimeout(12);

    TestLlmClient client = new TestLlmClient(config);
    LlmRequest llmRequest =
        LlmRequest.newBuilder()
            .uri(URI.create("http://localhost/llm"))
            .headers(Map.of("X-Test", "value"))
            .requestBody("{\"k\":\"v\"}")
            .build();

    HttpRequest request = client.callBuildHttpRequest(llmRequest);

    assertEquals(Duration.ofSeconds(12), request.timeout().orElseThrow());
    assertEquals("value", request.headers().firstValue("X-Test").orElseThrow());
  }

  @Test
  void sendRequest_wrapsIOExceptionAsRetryable() {
    TestLlmClient client =
        new TestLlmClient(
            new Config.LlmConfig(),
            new ThrowingHttpClient(new IOException("boom"), null),
            new ObjectMapper());

    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost")).build();
    LlmRequestException ex =
        assertThrows(LlmRequestException.class, () -> client.callSendRequest(request));

    assertTrue(ex.isRetryable());
  }

  @Test
  void sendRequest_wrapsInterruptedExceptionAndPreservesInterrupt() {
    InterruptedException interrupted = new InterruptedException("interrupted");
    TestLlmClient client =
        new TestLlmClient(
            new Config.LlmConfig(), new ThrowingHttpClient(null, interrupted), new ObjectMapper());

    boolean wasInterrupted = Thread.currentThread().isInterrupted();
    Thread.interrupted(); // clear before test

    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost")).build();
      LlmRequestException ex =
          assertThrows(LlmRequestException.class, () -> client.callSendRequest(request));
      assertFalse(ex.isRetryable());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      } else {
        Thread.interrupted();
      }
    }
  }

  private static final class TestLlmClient extends BaseLlmClient {
    private TestLlmClient(Config.LlmConfig config) {
      super(config, HttpClient.newHttpClient(), new ObjectMapper());
    }

    private TestLlmClient(Config.LlmConfig config, HttpClient httpClient, ObjectMapper mapper) {
      super(config, httpClient, mapper);
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "";
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("test", Collections.emptySet(), Optional.empty());
    }

    private void setUsage(TokenUsage usage) {
      storeLastUsage(usage);
    }

    private String callExtractJavaCode(String response) {
      return extractJavaCode(response);
    }

    private String callTruncateIfNeeded(String response) {
      return truncateIfNeeded(response);
    }

    private HttpRequest callBuildHttpRequest(LlmRequest request) {
      return buildHttpRequest(request);
    }

    private HttpResponse<String> callSendRequest(HttpRequest request) {
      return sendRequest(request);
    }

    private static int callResolvePositiveInt(Integer value, int fallback) {
      return resolvePositiveInt(value, fallback);
    }

    private static String callResolveString(String value, String defaultValue) {
      return resolveString(value, defaultValue);
    }
  }

  private static final class ThrowingHttpClient extends HttpClient {
    private final IOException ioException;
    private final InterruptedException interruptedException;

    private ThrowingHttpClient(IOException ioException, InterruptedException interruptedException) {
      this.ioException = ioException;
      this.interruptedException = interruptedException;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      if (ioException != null) {
        throw ioException;
      }
      if (interruptedException != null) {
        throw interruptedException;
      }
      throw new IOException("no exception configured");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        BodyHandler<T> responseBodyHandler,
        PushPromiseHandler<T> pushPromiseHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<java.net.CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<java.net.ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public javax.net.ssl.SSLContext sslContext() {
      return null;
    }

    @Override
    public javax.net.ssl.SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<java.net.Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<java.util.concurrent.Executor> executor() {
      return Optional.empty();
    }
  }
}
