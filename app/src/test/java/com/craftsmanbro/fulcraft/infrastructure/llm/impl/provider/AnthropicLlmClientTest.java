package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.system.impl.Env;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AnthropicLlmClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void generateTest_sendsMessagesRequestAndParsesText() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    String responseJson = "{\"content\":[{\"type\":\"text\",\"text\":\"class Foo {}\"}]}";
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));

    AnthropicLlmClient client = new AnthropicLlmClient(config);
    setHttpClient(client, httpClient);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    JsonNode bodyJson = MAPPER.readTree(bodyAsString(httpClient.requests.get(0)));
    assertEquals("claude-3-5-sonnet-latest", bodyJson.get("model").asText());
    assertEquals("prompt-text", bodyJson.get("messages").get(0).get("content").asText());
    assertEquals(
        "anthropic-key",
        httpClient.requests.get(0).headers().firstValue("x-api-key").orElseThrow());
  }

  @Test
  void isHealthy_returnsTrueWithApiKey() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");

    AnthropicLlmClient client = new AnthropicLlmClient(config);

    assertTrue(client.isHealthy());
  }

  @Test
  void constructor_usesApiKeyFromEnvironmentWhenConfigValueIsBlank() {
    Env.setForTest(name -> "ANTHROPIC_API_KEY".equals(name) ? "env-anthropic-key" : null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey(" ");
    config.setModelName("claude-3-5-sonnet-latest");

    AnthropicLlmClient client = new AnthropicLlmClient(config);

    assertTrue(client.isHealthy());
  }

  @Test
  void constructor_throwsWhenModelNameMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName(" ");

    assertThrows(IllegalStateException.class, () -> new AnthropicLlmClient(config));
  }

  @Test
  void generateTest_storesUsageWhenUsagePayloadExists() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "content":[{"type":"text","text":"class Foo {}"}],
          "usage":{"input_tokens":5,"output_tokens":7,"total_tokens":12}
        }
        """;
    AnthropicLlmClient client =
        new AnthropicLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, responseJson)), MAPPER);

    String result = client.generateTest("prompt", config);

    assertEquals("class Foo {}", result);
    assertEquals(12L, client.getLastUsage().orElseThrow().getTotalTokens());
  }

  @Test
  void generateTest_throwsRetryableExceptionForHttp500() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    AnthropicLlmClient client =
        new AnthropicLlmClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(500, "{\"error\":\"boom\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(500, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenContentMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    AnthropicLlmClient client =
        new AnthropicLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenTextMissingInFirstContentItem() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    AnthropicLlmClient client =
        new AnthropicLlmClient(
            config,
            new CapturingHttpClient(
                new SimpleHttpResponse(200, "{\"content\":[{\"type\":\"text\"}]}")),
            MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_usesStoredConfigWithSystemMessageAndCustomHeaders() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setSystemMessage("follow constraints");
    config.setCustomHeaders(java.util.Map.of("X-Trace", "trace-1"));
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "content":[{"type":"text","text":"class Foo {}"}],
          "usage":{"input_tokens":0,"output_tokens":0,"total_tokens":0}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    AnthropicLlmClient client = new AnthropicLlmClient(config, httpClient, MAPPER);

    String result = client.generateTest("prompt-text", (Config.LlmConfig) null);

    assertEquals("class Foo {}", result);
    assertTrue(client.getLastUsage().isEmpty());

    HttpRequest request = httpClient.requests.get(0);
    assertEquals("trace-1", request.headers().firstValue("X-Trace").orElseThrow());
    JsonNode body = MAPPER.readTree(bodyAsString(request));
    assertEquals("follow constraints", body.path("system").asText());
  }

  @Test
  void generateTest_marksHttp400AsNonRetryable() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");
    config.setMaxRetries(1);

    AnthropicLlmClient client =
        new AnthropicLlmClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(400, "{\"error\":\"bad\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(400, ex.getStatusCode());
    assertFalse(ex.isRetryable());
  }

  @Test
  void generateTest_rethrowsRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");

    RuntimeException source = new RuntimeException(new IllegalArgumentException("runtime-cause"));
    AnthropicLlmClient client =
        new ThrowingResilienceAnthropicClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.generateTest("prompt", config));
    assertEquals("runtime-cause", ex.getMessage());
  }

  @Test
  void generateTest_wrapsNonRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey("anthropic-key");
    config.setModelName("claude-3-5-sonnet-latest");

    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    AnthropicLlmClient client =
        new ThrowingResilienceAnthropicClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
    assertTrue(ex.getMessage().contains("Failed to generate test with Anthropic"));
  }

  @Test
  void constructor_throwsWhenApiKeyMissingInConfigAndEnvironment() {
    Env.setForTest(name -> null);
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("anthropic");
    config.setUrl("http://localhost/anthropic");
    config.setApiKey(" ");
    config.setModelName("claude-3-5-sonnet-latest");

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new AnthropicLlmClient(config));
    assertTrue(ex.getMessage().contains("ANTHROPIC_API_KEY"));
  }

  private static void setHttpClient(AnthropicLlmClient client, HttpClient httpClient)
      throws Exception {
    // httpClient field is now in BaseLlmClient parent class
    Field f = BaseLlmClient.class.getDeclaredField("httpClient");
    f.setAccessible(true);
    f.set(client, httpClient);
  }

  private static String bodyAsString(HttpRequest request) throws Exception {
    HttpRequest.BodyPublisher publisher =
        request
            .bodyPublisher()
            .orElseThrow(() -> new IllegalStateException("Request missing body"));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CountDownLatch latch = new CountDownLatch(1);
    publisher.subscribe(
        new java.util.concurrent.Flow.Subscriber<>() {
          @Override
          public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            baos.write(bytes, 0, bytes.length);
          }

          @Override
          public void onError(Throwable throwable) {
            latch.countDown();
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });
    latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static class SimpleHttpResponse implements HttpResponse<String> {
    private final int status;
    private final String body;

    SimpleHttpResponse(int status, String body) {
      this.status = status;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public String body() {
      return body;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
    }

    @Override
    public URI uri() {
      return URI.create("http://localhost/anthropic");
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }
  }

  private static class CapturingHttpClient extends HttpClient {
    final List<HttpRequest> requests = new java.util.ArrayList<>();
    private final HttpResponse<String> response;

    CapturingHttpClient(HttpResponse<String> response) {
      this.response = response;
    }

    @Override
    public <T> HttpResponse<T> send(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      requests.add(request);
      HttpResponse<T> casted = (HttpResponse<T>) response;
      return casted;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
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
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }
  }

  private static final class ThrowingResilienceAnthropicClient extends AnthropicLlmClient {
    private final RuntimeException toThrow;

    private ThrowingResilienceAnthropicClient(
        Config.LlmConfig config,
        HttpClient httpClient,
        ObjectMapper mapper,
        RuntimeException toThrow) {
      super(config, httpClient, mapper);
      this.toThrow = toThrow;
    }

    @Override
    protected <T> T executeWithResilience(java.util.concurrent.Callable<T> task) {
      throw toThrow;
    }
  }
}
