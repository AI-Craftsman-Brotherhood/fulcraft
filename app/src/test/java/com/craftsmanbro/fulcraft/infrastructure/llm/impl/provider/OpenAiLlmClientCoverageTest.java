package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceExecutionException;
import com.craftsmanbro.fulcraft.infrastructure.system.impl.Env;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
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

class OpenAiLlmClientCoverageTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void generateTest_usesDefaultsAndEnvApiKeyWhenOverrideIsNull() throws Exception {
    Env.setForTest(name -> "OPENAI_API_KEY".equals(name) ? "env-openai-key" : null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("openai");
    config.setApiKey(" ");
    config.setUrl(" ");
    config.setModelName(" ");
    config.setDeterministic(false);
    config.setTemperature(0.7);
    config.setSeed(99);
    config.setMaxTokens(123);
    config.setSystemMessage("sys-msg");
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "choices":[{"message":{"content":"class Foo {}"}}],
          "usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    OpenAiLlmClient client = new OpenAiLlmClient(config, httpClient, MAPPER);

    String generated = client.generateTest("prompt-text", (Config.LlmConfig) null);

    assertEquals("class Foo {}", generated);
    assertTrue(client.isHealthy());
    assertEquals(5L, client.getLastUsage().orElseThrow().getTotalTokens());

    HttpRequest request = httpClient.requests.get(0);
    assertEquals("https://api.openai.com/v1/chat/completions", request.uri().toString());

    JsonNode body = MAPPER.readTree(bodyAsString(request));
    assertEquals("gpt-4o-mini", body.path("model").asText());
    assertEquals("system", body.path("messages").path(0).path("role").asText());
    assertEquals("sys-msg", body.path("messages").path(0).path("content").asText());
    assertEquals(99, body.path("seed").asInt());
    assertEquals(123, body.path("max_tokens").asInt());
  }

  @Test
  void constructor_throwsWhenApiKeyIsMissingInConfigAndEnv() {
    Env.setForTest(name -> null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("openai");
    config.setApiKey(" ");

    assertThrows(IllegalStateException.class, () -> new OpenAiLlmClient(config));
  }

  @Test
  void generateTest_wrapsProviderExceptionWithoutCause() {
    Config.LlmConfig config = baseConfig();
    LlmProviderException source = new LlmProviderException("boom");
    OpenAiLlmClient client =
        new ThrowingResilienceOpenAiClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));

    assertTrue(ex.getCause() instanceof ResilienceExecutionException);
    assertSame(source, ex.getCause().getCause());
  }

  @Test
  void generateTest_preservesResilienceExecutionExceptionCause() {
    Config.LlmConfig config = baseConfig();
    ResilienceExecutionException resilienceCause =
        new ResilienceExecutionException(new IllegalStateException("inner"));
    LlmProviderException source = new LlmProviderException("boom", resilienceCause);
    OpenAiLlmClient client =
        new ThrowingResilienceOpenAiClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));

    assertSame(resilienceCause, ex.getCause());
  }

  @Test
  void generateTest_rethrowsRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = baseConfig();
    RuntimeException source = new RuntimeException(new IllegalArgumentException("runtime-cause"));
    OpenAiLlmClient client =
        new ThrowingResilienceOpenAiClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.generateTest("prompt", config));
    assertEquals("runtime-cause", ex.getMessage());
  }

  @Test
  void generateTest_wrapsNonRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = baseConfig();
    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    OpenAiLlmClient client =
        new ThrowingResilienceOpenAiClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsRetryableHttpExceptionFor429() {
    Config.LlmConfig config = baseConfig();
    OpenAiLlmClient client =
        new OpenAiLlmClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(429, "{\"error\":\"rate_limit\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(429, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenContentIsNull() {
    Config.LlmConfig config = baseConfig();
    OpenAiLlmClient client =
        new OpenAiLlmClient(
            config,
            new CapturingHttpClient(
                new SimpleHttpResponse(200, "{\"choices\":[{\"message\":{\"content\":null}}]}")),
            MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_clearsUsageWhenUsageValuesAreNotPositive() {
    Config.LlmConfig config = baseConfig();
    String responseJson =
        """
        {
          "choices":[{"message":{"content":"class Foo {}"}}],
          "usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}
        }
        """;
    OpenAiLlmClient client =
        new OpenAiLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, responseJson)), MAPPER);

    String generated = client.generateTest("prompt", config);

    assertEquals("class Foo {}", generated);
    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void generateTest_wrapsRequestBodySerializationFailure() {
    Config.LlmConfig config = baseConfig();
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    ObjectMapper helper = new ObjectMapper();
    try {
      when(failingMapper.createObjectNode()).thenReturn(helper.createObjectNode());
      when(failingMapper.writeValueAsString(any()))
          .thenThrow(org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class));
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException(e);
    }
    OpenAiLlmClient client =
        new OpenAiLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), failingMapper);

    assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
  }

  private static Config.LlmConfig baseConfig() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("openai");
    config.setApiKey("test-key");
    config.setModelName("gpt-test");
    config.setUrl("http://localhost/openai");
    config.setMaxRetries(1);
    return config;
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

  private static final class ThrowingResilienceOpenAiClient extends OpenAiLlmClient {
    private final RuntimeException toThrow;

    private ThrowingResilienceOpenAiClient(
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
      return URI.create("http://localhost/openai");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }
  }

  private static class CapturingHttpClient extends HttpClient {
    final List<HttpRequest> requests = new ArrayList<>();
    private final HttpResponse<String> response;

    CapturingHttpClient(HttpResponse<String> response) {
      this.response = response;
    }

    @Override
    public <T> HttpResponse<T> send(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
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
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }
  }
}
