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
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LocalLlmClientCoverageTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void generateTest_usesStoredConfigWhenOverrideIsNull() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody(3, 4, 7)));
      server.start();

      Config.LlmConfig config = baseConfig(server.url("/v1").toString(), "local-model");
      config.setSystemMessage("system-local");
      config.setMaxTokens(55);
      config.setDeterministic(false);
      config.setTemperature(0.6);
      config.setSeed(17);

      LocalLlmClient client = new LocalLlmClient(config);
      String result = client.generateTest("hello-local", (Config.LlmConfig) null);

      assertEquals("class LocalOut {}", result);
      assertEquals(7L, client.getLastUsage().orElseThrow().getTotalTokens());

      var recorded = server.takeRequest();
      assertEquals("/v1/chat/completions", recorded.getPath());
      JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
      assertEquals("local-model", body.path("model").asText());
      assertEquals("system", body.path("messages").path(0).path("role").asText());
      assertEquals("system-local", body.path("messages").path(0).path("content").asText());
      assertEquals("user", body.path("messages").path(1).path("role").asText());
      assertEquals(55, body.path("max_tokens").asInt());
      assertEquals(17, body.path("seed").asInt());
    }
  }

  @Test
  void generateTest_normalizesUrlWhenAlreadyChatCompletions() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody(1, 1, 2)));
      server.start();

      Config.LlmConfig config =
          baseConfig(server.url("/v1/chat/completions").toString(), "local-model");
      LocalLlmClient client = new LocalLlmClient(config);

      client.generateTest("hello", config);

      assertEquals("/v1/chat/completions", server.takeRequest().getPath());
    }
  }

  @Test
  void generateTest_normalizesUrlForCustomBasePath() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody(1, 1, 2)));
      server.start();

      Config.LlmConfig config = baseConfig(server.url("/api").toString(), "local-model");
      LocalLlmClient client = new LocalLlmClient(config);

      client.generateTest("hello", config);

      assertEquals("/api/v1/chat/completions", server.takeRequest().getPath());
    }
  }

  @Test
  void generateTest_throwsParseExceptionWhenContentMissing() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1").toString(), "local-model"));
      assertThrows(
          LlmResponseParseException.class, () -> client.generateTest("x", (Config.LlmConfig) null));
    }
  }

  @Test
  void generateTest_throwsParseExceptionWhenJsonIsInvalid() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody("{not-json"));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1").toString(), "local-model"));
      assertThrows(
          LlmResponseParseException.class, () -> client.generateTest("x", (Config.LlmConfig) null));
    }
  }

  @Test
  void generateTest_clearsUsageWhenUsageMissing() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setBody("{\"choices\":[{\"message\":{\"content\":\"class LocalOut {}\"}}]}"));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1").toString(), "local-model"));
      client.generateTest("x", (Config.LlmConfig) null);

      assertTrue(client.getLastUsage().isEmpty());
    }
  }

  @Test
  void generateTest_clearsUsageWhenUsageNonPositive() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody(0, 0, 0)));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1").toString(), "local-model"));
      client.generateTest("x", (Config.LlmConfig) null);

      assertTrue(client.getLastUsage().isEmpty());
    }
  }

  @Test
  void generateTest_marks429AsRetryable() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(429).setBody("limit"));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1").toString(), "local-model"));
      LlmProviderHttpException ex =
          assertThrows(
              LlmProviderHttpException.class,
              () -> client.generateTest("x", (Config.LlmConfig) null));

      assertEquals(429, ex.getStatusCode());
      assertTrue(ex.isRetryable());
    }
  }

  @Test
  void generateTest_wrapsSerializationFailureWhileBuildingRequest() {
    Config.LlmConfig config = baseConfig("http://localhost:8000/v1", "local-model");
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    ObjectMapper helper = new ObjectMapper();
    try {
      when(failingMapper.createObjectNode()).thenReturn(helper.createObjectNode());
      when(failingMapper.writeValueAsString(any()))
          .thenThrow(org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class));
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException(e);
    }
    LocalLlmClient client = new LocalLlmClient(config, HttpClient.newHttpClient(), failingMapper);

    assertThrows(LlmProviderException.class, () -> client.generateTest("x", config));
  }

  @Test
  void generateTest_rethrowsRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = baseConfig("http://localhost:8000/v1", "local-model");
    RuntimeException source = new RuntimeException(new IllegalArgumentException("runtime"));
    LocalLlmClient client =
        new ThrowingResilienceLocalClient(config, HttpClient.newHttpClient(), mapper, source);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.generateTest("x", config));
    assertEquals("runtime", ex.getMessage());
  }

  @Test
  void generateTest_wrapsNonRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = baseConfig("http://localhost:8000/v1", "local-model");
    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    LocalLlmClient client =
        new ThrowingResilienceLocalClient(config, HttpClient.newHttpClient(), mapper, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("x", config));
    assertTrue(ex.getMessage().contains("Failed to generate test with local LLM"));
  }

  @Test
  void isHealthy_returnsFalseOnIoException() {
    Config.LlmConfig config = baseConfig("http://localhost:8000/v1", "local-model");
    LocalLlmClient client =
        new LocalLlmClient(
            config, new ThrowingHealthHttpClient(new IOException("io"), null), mapper);

    assertFalse(client.isHealthy());
  }

  @Test
  void isHealthy_returnsFalseAndPreservesInterruptStatus() {
    Config.LlmConfig config = baseConfig("http://localhost:8000/v1", "local-model");
    LocalLlmClient client =
        new LocalLlmClient(
            config,
            new ThrowingHealthHttpClient(null, new InterruptedException("interrupt")),
            mapper);

    boolean wasInterrupted = Thread.currentThread().isInterrupted();
    Thread.interrupted();
    try {
      assertFalse(client.isHealthy());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      } else {
        Thread.interrupted();
      }
    }
  }

  @Test
  void isHealthy_callsModelsEndpointWhenConfigAlreadyPointsToModels() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200));
      server.start();

      LocalLlmClient client =
          new LocalLlmClient(baseConfig(server.url("/v1/models").toString(), "local-model"));

      assertTrue(client.isHealthy());
      assertEquals("/v1/models", server.takeRequest().getPath());
    }
  }

  @Test
  void constructor_usesDefaultsWhenUrlAndModelAreBlank() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(successBody(1, 1, 2)));
      server.start();

      Config.LlmConfig defaults = new Config.LlmConfig();
      defaults.setProvider("local");
      defaults.setUrl(" ");
      defaults.setModelName(" ");
      defaults.setMaxRetries(0);
      defaults.setRetryInitialDelayMs(1L);

      Config.LlmConfig override = baseConfig(server.url("/v1").toString(), "override-model");
      LocalLlmClient client = new LocalLlmClient(defaults, HttpClient.newHttpClient(), mapper);
      client.generateTest("x", override);

      JsonNode body = mapper.readTree(server.takeRequest().getBody().readUtf8());
      assertEquals("override-model", body.path("model").asText());
    }
  }

  private static Config.LlmConfig baseConfig(String url, String model) {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("local");
    config.setUrl(url);
    config.setModelName(model);
    config.setMaxRetries(0);
    config.setRetryInitialDelayMs(1L);
    return config;
  }

  private static String successBody(long prompt, long completion, long total) {
    return """
        {
          "choices":[{"message":{"content":"class LocalOut {}"}}],
          "usage":{"prompt_tokens":%d,"completion_tokens":%d,"total_tokens":%d}
        }
        """
        .formatted(prompt, completion, total);
  }

  private static final class ThrowingResilienceLocalClient extends LocalLlmClient {
    private final RuntimeException toThrow;

    private ThrowingResilienceLocalClient(
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

  private static final class ThrowingHealthHttpClient extends HttpClient {
    private final IOException ioException;
    private final InterruptedException interruptedException;

    private ThrowingHealthHttpClient(
        IOException ioException, InterruptedException interruptedException) {
      this.ioException = ioException;
      this.interruptedException = interruptedException;
    }

    @Override
    public <T> HttpResponse<T> send(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      if (ioException != null) {
        throw ioException;
      }
      if (interruptedException != null) {
        throw interruptedException;
      }
      throw new IOException("no configured response");
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
}
