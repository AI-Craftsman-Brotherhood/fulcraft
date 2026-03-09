package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

class VertexAiLlmClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void generateTest_buildsVertexUrlAndParsesText() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setMaxRetries(1);

    String responseJson =
        """
        {"candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}]}
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));

    VertexAiLlmClient client = new VertexAiLlmClient(config);
    setHttpClient(client, httpClient);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    assertEquals(
        "https://us-central1-aiplatform.googleapis.com/v1/projects/p1/locations/us-central1/publishers/google/models/gemini-1.5-pro:generateContent",
        httpClient.requests.get(0).uri().toString());

    JsonNode bodyJson = MAPPER.readTree(bodyAsString(httpClient.requests.get(0)));
    assertEquals(
        "prompt-text", bodyJson.get("contents").get(0).get("parts").get(0).get("text").asText());
    assertEquals(
        "Bearer access-token",
        httpClient.requests.get(0).headers().firstValue("Authorization").orElseThrow());
  }

  @Test
  void isHealthy_returnsTrueWhenApiKeyConfigured() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");

    VertexAiLlmClient client = new VertexAiLlmClient(config);

    assertTrue(client.isHealthy());
  }

  @Test
  void isHealthy_readsAccessTokenFromEnvironment() {
    Env.setForTest(name -> "VERTEX_AI_ACCESS_TOKEN".equals(name) ? "env-token" : null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey(" ");

    VertexAiLlmClient client = new VertexAiLlmClient(config);

    assertTrue(client.isHealthy());
  }

  @Test
  void isHealthy_returnsFalseWhenNoTokenIsAvailable() {
    Env.setForTest(name -> null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey(" ");

    VertexAiLlmClient client = new VertexAiLlmClient(config);

    assertFalse(client.isHealthy());
  }

  @Test
  void generateTest_throwsWhenAccessTokenMissing() {
    Env.setForTest(name -> null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey(" ");

    VertexAiLlmClient client = new VertexAiLlmClient(config);

    assertThrows(IllegalStateException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_storesUsageFromUsageMetadata() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}],
          "usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":4,"totalTokenCount":7}
        }
        """;
    VertexAiLlmClient client =
        new VertexAiLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, responseJson)), MAPPER);

    String result = client.generateTest("prompt", config);

    assertEquals("class Foo {}", result);
    assertEquals(7L, client.getLastUsage().orElseThrow().getTotalTokens());
  }

  @Test
  void generateTest_throwsRetryableExceptionForHttp429() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setMaxRetries(1);

    VertexAiLlmClient client =
        new VertexAiLlmClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(429, "{\"error\":\"limited\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(429, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenResponseTextMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setMaxRetries(1);

    VertexAiLlmClient client =
        new VertexAiLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_usesStoredConfigAndBuildsSystemInstructionWhenOverrideIsNull()
      throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setVertexPublisher("partner");
    config.setApiKey("access-token");
    config.setSystemMessage("follow rules");
    config.setDeterministic(false);
    config.setTemperature(0.7);
    config.setSeed(11);
    config.setMaxTokens(90);
    config.setCustomHeaders(java.util.Map.of("X-Trace", "trace-vertex"));
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}],
          "usageMetadata":{"promptTokenCount":0,"candidatesTokenCount":0,"totalTokenCount":0}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    VertexAiLlmClient client = new VertexAiLlmClient(config, httpClient, MAPPER);

    String generated = client.generateTest("prompt-text", (Config.LlmConfig) null);

    assertEquals("class Foo {}", generated);
    assertTrue(client.getLastUsage().isEmpty());

    HttpRequest request = httpClient.requests.get(0);
    assertEquals("trace-vertex", request.headers().firstValue("X-Trace").orElseThrow());
    assertTrue(
        request
            .uri()
            .toString()
            .contains("/publishers/partner/models/gemini-1.5-pro:generateContent"));
    JsonNode body = MAPPER.readTree(bodyAsString(request));
    assertEquals(
        "follow rules", body.path("systemInstruction").path("parts").path(0).path("text").asText());
    assertEquals(11, body.path("generationConfig").path("seed").asInt());
    assertEquals(90, body.path("generationConfig").path("maxOutputTokens").asInt());
  }

  @Test
  void generateTest_usesConfiguredCustomUrlWhenProvided() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setUrl("https://custom.craftsmann-bro.com/generate");
    config.setMaxRetries(1);

    CapturingHttpClient httpClient =
        new CapturingHttpClient(
            new SimpleHttpResponse(
                200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"class Foo {}\"}]}}]}"));
    VertexAiLlmClient client = new VertexAiLlmClient(config, httpClient, MAPPER);

    client.generateTest("prompt", config);

    assertEquals(
        "https://custom.craftsmann-bro.com/generate", httpClient.requests.get(0).uri().toString());
  }

  @Test
  void generateTest_marksHttp400AsNonRetryable() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");
    config.setMaxRetries(1);

    VertexAiLlmClient client =
        new VertexAiLlmClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(400, "{\"error\":\"bad\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(400, ex.getStatusCode());
    assertFalse(ex.isRetryable());
  }

  @Test
  void generateTest_wrapsRequestBodyBuildFailure() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");

    ObjectMapper failingMapper = mock(ObjectMapper.class);
    ObjectMapper helper = new ObjectMapper();
    when(failingMapper.createObjectNode()).thenReturn(helper.createObjectNode());
    when(failingMapper.writeValueAsString(any()))
        .thenThrow(org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class));

    VertexAiLlmClient client =
        new VertexAiLlmClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), failingMapper);

    assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_wrapsGenericCatchException() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("vertex");
    config.setVertexProject("p1");
    config.setVertexLocation("us-central1");
    config.setVertexModel("gemini-1.5-pro");
    config.setApiKey("access-token");

    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    VertexAiLlmClient client =
        new ThrowingResilienceVertexClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
    assertTrue(ex.getMessage().contains("Failed to generate test with Vertex AI"));
  }

  private static void setHttpClient(VertexAiLlmClient client, HttpClient httpClient)
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
      return URI.create("http://localhost/vertex");
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

  private static final class ThrowingResilienceVertexClient extends VertexAiLlmClient {
    private final RuntimeException toThrow;

    private ThrowingResilienceVertexClient(
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
