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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

class BedrockLlmClientTest {

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void generateTest_signsRequestAndParsesText() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKIDEXAMPLE");
    config.setAwsSecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setMaxRetries(1);

    String responseJson = "{\"content\":[{\"type\":\"text\",\"text\":\"class Foo {}\"}]}";
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));

    Clock fixedClock = Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC);
    ObjectMapper mapper = new ObjectMapper();
    BedrockLlmClient client = new BedrockLlmClient(config, fixedClock, httpClient, mapper);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    HttpRequest req = httpClient.requests.get(0);
    assertEquals(
        "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-3-5-sonnet-20240620-v1:0/invoke",
        req.uri().toString());
    String auth = req.headers().firstValue("Authorization").orElseThrow();
    String amzDate = req.headers().firstValue("x-amz-date").orElseThrow();
    assertEquals("20240102T030405Z", amzDate);
    // Signature is variable, but header should include SigV4 format.
    org.junit.jupiter.api.Assertions.assertTrue(auth.contains("AWS4-HMAC-SHA256"));
  }

  @Test
  void constructors_createHealthyClientsWithConfiguredCredentials() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");

    BedrockLlmClient defaultCtorClient = new BedrockLlmClient(config);
    BedrockLlmClient clockCtorClient = new BedrockLlmClient(config, Clock.systemUTC());

    assertTrue(defaultCtorClient.isHealthy());
    assertTrue(clockCtorClient.isHealthy());
  }

  @Test
  void isHealthy_checksConfigAndEnvironmentCredentials() {
    Config.LlmConfig fromConfig = new Config.LlmConfig();
    fromConfig.setProvider("bedrock");
    fromConfig.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    fromConfig.setAwsAccessKeyId("AKID");
    fromConfig.setAwsSecretAccessKey("SECRET");

    BedrockLlmClient configClient =
        new BedrockLlmClient(
            fromConfig,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{\"content\":[]}")),
            new ObjectMapper());
    assertTrue(configClient.isHealthy());

    Config.LlmConfig fromEnv = new Config.LlmConfig();
    fromEnv.setProvider("bedrock");
    fromEnv.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");

    Env.setForTest(
        name ->
            switch (name) {
              case "AWS_ACCESS_KEY_ID" -> "ENV_ACCESS";
              case "AWS_SECRET_ACCESS_KEY" -> "ENV_SECRET";
              default -> null;
            });
    BedrockLlmClient envClient =
        new BedrockLlmClient(
            fromEnv,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{\"content\":[]}")),
            new ObjectMapper());
    assertTrue(envClient.isHealthy());

    Env.setForTest(name -> null);
    BedrockLlmClient unhealthyClient =
        new BedrockLlmClient(
            fromEnv,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{\"content\":[]}")),
            new ObjectMapper());
    assertFalse(unhealthyClient.isHealthy());
  }

  @Test
  void generateTest_resolvesEnvironmentSettingsAndStoresUsage() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setMaxRetries(1);
    config.setCustomHeaders(java.util.Map.of("X-Custom", "custom-value"));

    Env.setForTest(
        name ->
            switch (name) {
              case "AWS_REGION" -> "us-west-2";
              case "AWS_ACCESS_KEY_ID" -> "ENV_ACCESS";
              case "AWS_SECRET_ACCESS_KEY" -> "ENV_SECRET";
              case "AWS_SESSION_TOKEN" -> "ENV_SESSION";
              default -> null;
            });

    String responseJson =
        """
        {
          "content":[{"type":"text","text":"```java\\nclass Foo {}\\n```"}],
          "usage":{"input_tokens":11,"output_tokens":7,"total_tokens":18}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC),
            httpClient,
            new ObjectMapper());

    String generated = client.generateTest("prompt", config);

    assertEquals("class Foo {}", generated);
    assertEquals(18L, client.getLastUsage().orElseThrow().getTotalTokens());
    HttpRequest request = httpClient.requests.get(0);
    assertEquals(
        "https://bedrock-runtime.us-west-2.amazonaws.com/model/anthropic.claude-3-5-sonnet-20240620-v1:0/invoke",
        request.uri().toString());
    assertEquals("ENV_SESSION", request.headers().firstValue("x-amz-security-token").orElseThrow());
    assertEquals("custom-value", request.headers().firstValue("X-Custom").orElseThrow());
  }

  @Test
  void generateTest_throwsRetryableHttpExceptionForRateLimit() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setMaxRetries(1);

    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(429, "{\"error\":\"limited\"}")),
            new ObjectMapper());

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(429, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenContentIsMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setMaxRetries(1);

    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{\"content\":[]}")),
            new ObjectMapper());

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsWhenModelNameMissing() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName(" ");

    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{}")),
            new ObjectMapper());

    assertThrows(IllegalStateException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsWhenCredentialsOrRegionMissing() {
    Env.setForTest(name -> null);
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setAwsAccessKeyId(" ");
    config.setAwsSecretAccessKey(" ");

    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{}")),
            new ObjectMapper());

    assertThrows(IllegalStateException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_usesCustomUrlAwsDefaultRegionConfigSessionTokenAndSystemMessage()
      throws Exception {
    Env.setForTest(name -> "AWS_DEFAULT_REGION".equals(name) ? "ap-northeast-1" : null);

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setUrl("http://localhost/custom-bedrock");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setAwsSessionToken("CFG_TOKEN");
    config.setSystemMessage("bedrock-system");
    config.setMaxRetries(1);

    String responseJson =
        """
        {
          "content":[
            {"type":"tool_result","text":"ignored"},
            {"type":"text","text":"class Foo {}"}
          ],
          "usage":{"input_tokens":0,"output_tokens":0,"total_tokens":0}
        }
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));
    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC),
            httpClient,
            new ObjectMapper());

    String generated = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", generated);
    assertTrue(client.getLastUsage().isEmpty());
    HttpRequest req = httpClient.requests.get(0);
    assertEquals("http://localhost/custom-bedrock", req.uri().toString());
    assertEquals("CFG_TOKEN", req.headers().firstValue("x-amz-security-token").orElseThrow());
    JsonNode body = new ObjectMapper().readTree(bodyAsString(req));
    assertEquals("bedrock-system", body.path("system").asText());
  }

  @Test
  void generateTest_marksHttp400AsNonRetryable() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
    config.setMaxRetries(1);

    BedrockLlmClient client =
        new BedrockLlmClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(400, "{\"error\":\"bad\"}")),
            new ObjectMapper());

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(400, ex.getStatusCode());
    assertFalse(ex.isRetryable());
  }

  @Test
  void generateTest_rethrowsRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");

    RuntimeException source = new RuntimeException(new IllegalArgumentException("runtime-cause"));
    BedrockLlmClient client =
        new ThrowingResilienceBedrockClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{}")),
            new ObjectMapper(),
            source);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.generateTest("prompt", config));
    assertEquals("runtime-cause", ex.getMessage());
  }

  @Test
  void generateTest_wrapsNonRuntimeCauseFromGenericCatch() {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("bedrock");
    config.setAwsRegion("us-east-1");
    config.setAwsAccessKeyId("AKID");
    config.setAwsSecretAccessKey("SECRET");
    config.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");

    RuntimeException source = new RuntimeException(new IOException("io-cause"));
    BedrockLlmClient client =
        new ThrowingResilienceBedrockClient(
            config,
            Clock.systemUTC(),
            new CapturingHttpClient(new SimpleHttpResponse(200, "{}")),
            new ObjectMapper(),
            source);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
    assertTrue(ex.getMessage().contains("Failed to generate test with Bedrock"));
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
      return URI.create("https://bedrock-runtime.us-east-1.amazonaws.com");
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

  private static final class ThrowingResilienceBedrockClient extends BedrockLlmClient {
    private final RuntimeException toThrow;

    private ThrowingResilienceBedrockClient(
        Config.LlmConfig config,
        Clock clock,
        HttpClient httpClient,
        ObjectMapper mapper,
        RuntimeException toThrow) {
      super(config, clock, httpClient, mapper);
      this.toThrow = toThrow;
    }

    @Override
    protected <T> T executeWithResilience(java.util.concurrent.Callable<T> task) {
      throw toThrow;
    }
  }
}
