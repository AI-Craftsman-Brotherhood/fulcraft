package com.craftsmanbro.fulcraft.infrastructure.llm.provider;

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
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.AzureOpenAiLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider.BaseLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResiliencePolicies;
import com.craftsmanbro.fulcraft.infrastructure.system.impl.Env;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
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

class AzureOpenAiLlmClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void generateTest_buildsDeploymentUrlAndParsesContent() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    String responseJson = "{\"choices\":[{\"message\":{\"content\":\"class Foo {}\"}}]}";
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));

    AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(config);
    setHttpClient(client, httpClient);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    assertEquals(
        "https://example.openai.azure.com/openai/deployments/dep1/chat/completions?api-version=2024-02-15-preview",
        httpClient.requests.get(0).uri().toString());

    JsonNode bodyJson = MAPPER.readTree(bodyAsString(httpClient.requests.get(0)));
    assertEquals("prompt-text", bodyJson.get("messages").get(0).get("content").asText());
    assertEquals(
        "azure-key", httpClient.requests.get(0).headers().firstValue("api-key").orElseThrow());
  }

  @Test
  void isHealthy_returnsTrueWhenApiKeyExists() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");

    AzureOpenAiLlmClient client = new AzureOpenAiLlmClient(config);

    assertTrue(client.isHealthy());
  }

  @Test
  void constructor_throwsWhenRequiredAzureFieldsAreMissing() {
    Config.LlmConfig missingUrl = new Config.LlmConfig();
    missingUrl.setProvider("azure-openai");
    missingUrl.setAzureDeployment("dep1");
    missingUrl.setAzureApiVersion("2024-02-15-preview");
    missingUrl.setApiKey("azure-key");
    assertThrows(IllegalStateException.class, () -> new AzureOpenAiLlmClient(missingUrl));

    Config.LlmConfig missingDeployment = new Config.LlmConfig();
    missingDeployment.setProvider("azure-openai");
    missingDeployment.setUrl("https://example.openai.azure.com");
    missingDeployment.setAzureApiVersion("2024-02-15-preview");
    missingDeployment.setApiKey("azure-key");
    assertThrows(IllegalStateException.class, () -> new AzureOpenAiLlmClient(missingDeployment));

    Config.LlmConfig missingVersion = new Config.LlmConfig();
    missingVersion.setProvider("azure-openai");
    missingVersion.setUrl("https://example.openai.azure.com");
    missingVersion.setAzureDeployment("dep1");
    missingVersion.setApiKey("azure-key");
    assertThrows(IllegalStateException.class, () -> new AzureOpenAiLlmClient(missingVersion));
  }

  @Test
  void generateTest_usesEnvironmentApiKeyWhenConfigIsBlank() throws Exception {
    Env.setForTest(name -> "AZURE_OPENAI_API_KEY".equals(name) ? "env-azure-key" : null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey(" ");
    config.setMaxRetries(1);

    String responseJson = "{\"choices\":[{\"message\":{\"content\":\"class Foo {}\"}}]}";
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    AzureOpenAiLlmClient client = newClient(config, httpClient, MAPPER);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    assertEquals(
        "env-azure-key", httpClient.requests.get(0).headers().firstValue("api-key").orElseThrow());
  }

  @Test
  void generateTest_throwsRetryableExceptionForHttp429() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    AzureOpenAiLlmClient client =
        newClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(429, "{\"error\":\"limit\"}")),
            MAPPER);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(429, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenChoicesMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    AzureOpenAiLlmClient client =
        newClient(config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenMessageContentMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    AzureOpenAiLlmClient client =
        newClient(
            config,
            new CapturingHttpClient(
                new SimpleHttpResponse(200, "{\"choices\":[{\"message\":{}}]}")),
            MAPPER);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void constructor_throwsWhenApiKeyMissingInConfigAndEnvironment() {
    Env.setForTest(name -> null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey(" ");

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new AzureOpenAiLlmClient(config));
    assertTrue(ex.getMessage().contains("AZURE_OPENAI_API_KEY"));
  }

  @Test
  void generateTest_usesStoredConfigWhenOverrideIsNullAndNormalizesTrailingSlash()
      throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com/");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setSystemMessage("keep strict");
    config.setDeterministic(false);
    config.setTemperature(0.6);
    config.setSeed(77);
    config.setMaxTokens(123);
    config.setCustomHeaders(java.util.Map.of("X-Azure-Trace", "trace-1"));
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "choices":[{"message":{"content":"class Foo {}"}}],
          "usage":{"prompt_tokens":3,"completion_tokens":5,"total_tokens":8}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    AzureOpenAiLlmClient client = newClient(config, httpClient, MAPPER);

    String generated = client.generateTest("prompt-text", (Config.LlmConfig) null);

    assertEquals("class Foo {}", generated);
    assertEquals(8L, client.getLastUsage().orElseThrow().getTotalTokens());

    HttpRequest request = httpClient.requests.get(0);
    assertEquals(
        "https://example.openai.azure.com/openai/deployments/dep1/chat/completions?api-version=2024-02-15-preview",
        request.uri().toString());
    assertEquals("trace-1", request.headers().firstValue("X-Azure-Trace").orElseThrow());

    JsonNode body = MAPPER.readTree(bodyAsString(request));
    assertEquals("system", body.path("messages").path(0).path("role").asText());
    assertEquals("keep strict", body.path("messages").path(0).path("content").asText());
    assertEquals(77, body.path("seed").asInt());
    assertEquals(123, body.path("max_tokens").asInt());
  }

  @Test
  void generateTest_clearsUsageWhenUsageIsNonPositive() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "choices":[{"message":{"content":"class Foo {}"}}],
          "usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}
        }
        """;
    AzureOpenAiLlmClient client =
        newClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, responseJson)), MAPPER);

    String generated = client.generateTest("prompt", config);

    assertEquals("class Foo {}", generated);
    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void generateTest_marksHttp400AsNonRetryable() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");
    config.setMaxRetries(1);

    AzureOpenAiLlmClient client =
        newClient(
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
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");

    RuntimeException source = new RuntimeException(new IllegalArgumentException("runtime-cause"));
    AzureOpenAiLlmClient client =
        newClient(config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER);
    setResiliencePoliciesToThrow(client, source);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.generateTest("prompt", config));
    assertEquals("runtime-cause", ex.getMessage());
  }

  @Test
  void generateTest_wrapsNonRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");

    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    AzureOpenAiLlmClient client =
        newClient(config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), MAPPER);
    setResiliencePoliciesToThrow(client, source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
    assertTrue(ex.getMessage().contains("Failed to generate test with Azure OpenAI"));
  }

  @Test
  void generateTest_wrapsRequestBodySerializationFailure() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("https://example.openai.azure.com");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");

    ObjectMapper failingMapper = mock(ObjectMapper.class);
    ObjectMapper helper = new ObjectMapper();
    when(failingMapper.createObjectNode()).thenReturn(helper.createObjectNode());
    when(failingMapper.writeValueAsString(any()))
        .thenThrow(org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class));

    AzureOpenAiLlmClient client =
        newClient(
            config, new CapturingHttpClient(new SimpleHttpResponse(200, "{}")), failingMapper);

    assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsWhenConfiguredUrlIsInvalid() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("azure-openai");
    config.setUrl("://bad url");
    config.setAzureDeployment("dep1");
    config.setAzureApiVersion("2024-02-15-preview");
    config.setApiKey("azure-key");

    AzureOpenAiLlmClient client =
        newClient(
            config,
            new CapturingHttpClient(new SimpleHttpResponse(200, "{\"choices\":[]}")),
            MAPPER);

    assertThrows(IllegalStateException.class, () -> client.generateTest("prompt", config));
  }

  private static void setHttpClient(AzureOpenAiLlmClient client, HttpClient httpClient)
      throws Exception {
    Field f = BaseLlmClient.class.getDeclaredField("httpClient");
    f.setAccessible(true);
    f.set(client, httpClient);
  }

  private static AzureOpenAiLlmClient newClient(
      Config.LlmConfig config, HttpClient httpClient, ObjectMapper mapper) {
    try {
      Constructor<AzureOpenAiLlmClient> ctor =
          AzureOpenAiLlmClient.class.getDeclaredConstructor(
              Config.LlmConfig.class, HttpClient.class, ObjectMapper.class);
      ctor.setAccessible(true);
      return ctor.newInstance(config, httpClient, mapper);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create AzureOpenAiLlmClient for tests", e);
    }
  }

  private static void setResiliencePoliciesToThrow(
      AzureOpenAiLlmClient client, RuntimeException toThrow) {
    ResiliencePolicies policies = mock(ResiliencePolicies.class);
    when(policies.executeLlmCall(any())).thenThrow(toThrow);
    try {
      Field f = BaseLlmClient.class.getDeclaredField("resiliencePolicies");
      f.setAccessible(true);
      f.set(client, policies);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to inject ResiliencePolicies", e);
    }
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
      return URI.create("https://example.openai.azure.com");
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
}
