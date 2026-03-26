package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmResponseParseException;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.Capability;
import com.craftsmanbro.fulcraft.infrastructure.system.impl.Env;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeminiLlmClientTest {

  private HttpClient httpClient;
  private ObjectMapper mapper;
  private Config.LlmConfig config;

  @BeforeEach
  void setUp() {
    Env.setForTest(name -> null);
    httpClient = mock(HttpClient.class);
    mapper = new ObjectMapper();
    config = new Config.LlmConfig();
    config.setProvider("gemini");
    config.setApiKey("test-api-key");
    config.setModelName("gemini-test");
    config.setMaxRetries(0);
    config.setRetryInitialDelayMs(1L);
  }

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void constructor_requiresApiKeyWhenUnset() {
    Config.LlmConfig noKeyConfig = new Config.LlmConfig();
    noKeyConfig.setProvider("gemini");

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> new GeminiLlmClient(noKeyConfig));
    assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
  }

  @Test
  void constructor_resolvesApiKeyFromEnvironmentWhenConfigIsBlank() {
    Env.setForTest(name -> "GEMINI_API_KEY".equals(name) ? "env-gemini-key" : null);
    Config.LlmConfig fromEnv = new Config.LlmConfig();
    fromEnv.setProvider("gemini");
    fromEnv.setApiKey(" ");

    GeminiLlmClient client = new GeminiLlmClient(fromEnv, httpClient, mapper);

    assertEquals("env-gemini-key", apiKeyOf(client));
  }

  @Test
  void defaultConstructor_usesEnvironmentApiKey() {
    Env.setForTest(name -> "GEMINI_API_KEY".equals(name) ? "env-gemini-key" : null);

    assertNotNull(new GeminiLlmClient());
  }

  @Test
  void isHealthy_returnsTrueWhenHealthEndpointReturns200() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"models\":[]}");
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(httpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertTrue(client.isHealthy());
    assertEquals("GET", requestCaptor.getValue().method());
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models?key=test-api-key",
        requestCaptor.getValue().uri().toString());
  }

  @Test
  void isHealthy_returnsFalseWhenRequestFails() throws Exception {
    when(httpClient.<String>send(any(), any())).thenThrow(new IOException("connection failed"));

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertFalse(client.isHealthy());
  }

  @Test
  void isHealthy_returnsFalseWhenStatusIsNot200() throws Exception {
    HttpResponse<String> response = mockResponse(503, "{\"error\":\"down\"}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertFalse(client.isHealthy());
  }

  @Test
  void isHealthy_returnsFalseWhenApiKeyFieldIsBlank() {
    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    setApiKey(client, " ");

    assertFalse(client.isHealthy());
  }

  @Test
  void generateTest_buildsRequestParsesResponseAndStoresUsage() throws Exception {
    String responseJson =
        """
        {
          "candidates":[{"content":{"parts":[{"text":"```java\\nclass Foo {}\\n```"}]}}],
          "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":34,"totalTokenCount":46}
        }
        """;
    HttpResponse<String> response = mockResponse(200, responseJson);
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(httpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    HttpRequest request = requestCaptor.getValue();
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent?key=test-api-key",
        request.uri().toString());
    assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());

    JsonNode body = mapper.readTree(bodyAsString(request));
    assertEquals(
        "prompt-text", body.path("contents").path(0).path("parts").path(0).path("text").asText());
    assertEquals(0.0, body.path("generationConfig").path("temperature").asDouble());
    assertEquals(42, body.path("generationConfig").path("seed").asInt());
    assertEquals(65536, body.path("generationConfig").path("maxOutputTokens").asInt());

    TokenUsage usage = client.getLastUsage().orElseThrow();
    assertEquals(12, usage.getPromptTokens());
    assertEquals(34, usage.getCompletionTokens());
    assertEquals(46, usage.getTotalTokens());
  }

  @Test
  void generateTest_prefersOverrideConfigForModelAndGenerationParams() throws Exception {
    Config.LlmConfig override = new Config.LlmConfig();
    override.setProvider("gemini");
    override.setModelName("override-model");
    override.setDeterministic(false);
    override.setTemperature(0.75);
    override.setMaxTokens(128);

    HttpResponse<String> response =
        mockResponse(
            200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"class Bar {}\"}]}}]}");
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(httpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    String result = client.generateTest("override-prompt", override);

    assertEquals("class Bar {}", result);
    HttpRequest request = requestCaptor.getValue();
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models/override-model:generateContent?key=test-api-key",
        request.uri().toString());

    JsonNode body = mapper.readTree(bodyAsString(request));
    JsonNode generationConfig = body.path("generationConfig");
    assertEquals(0.75, generationConfig.path("temperature").asDouble());
    assertEquals(128, generationConfig.path("maxOutputTokens").asInt());
    assertTrue(generationConfig.path("seed").isMissingNode());
  }

  @Test
  void generateTest_usesStoredConfigWhenOverrideIsNull() throws Exception {
    Config.LlmConfig stored = new Config.LlmConfig();
    stored.setProvider("gemini");
    stored.setApiKey("test-api-key");
    stored.setModelName("stored-model");
    stored.setDeterministic(false);
    stored.setTemperature(0.5);
    stored.setSeed(99);
    stored.setMaxTokens(256);
    stored.setMaxRetries(0);
    stored.setRetryInitialDelayMs(1L);

    HttpResponse<String> response =
        mockResponse(
            200,
            """
            {
              "candidates":[{"content":{"parts":[{"text":"class Stored {}"}]}}],
              "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}
            }
            """);
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(httpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(stored, httpClient, mapper);
    String result = client.generateTest("stored-prompt", (Config.LlmConfig) null);

    assertEquals("class Stored {}", result);
    assertEquals(3L, client.getLastUsage().orElseThrow().getTotalTokens());
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models/stored-model:generateContent?key=test-api-key",
        requestCaptor.getValue().uri().toString());
  }

  @Test
  void generateTest_usesDefaultModelWhenStoredModelIsBlank() throws Exception {
    Config.LlmConfig stored = new Config.LlmConfig();
    stored.setProvider("gemini");
    stored.setApiKey("test-api-key");
    stored.setModelName(" ");
    stored.setDeterministic(false);
    stored.setTemperature(0.4);
    stored.setMaxRetries(0);
    stored.setRetryInitialDelayMs(1L);

    HttpResponse<String> response =
        mockResponse(
            200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"class D {}\"}]}}]}");
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    when(httpClient.<String>send(requestCaptor.capture(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(stored, httpClient, mapper);
    client.generateTest("p", (Config.LlmConfig) null);

    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=test-api-key",
        requestCaptor.getValue().uri().toString());
  }

  @Test
  void generateTest_throwsRetryableHttpExceptionWhenStatusIs429() throws Exception {
    HttpResponse<String> response = mockResponse(429, "rate limit");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(429, ex.getStatusCode());
    assertEquals("rate limit", ex.getResponseBody());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsNonRetryableHttpExceptionWhenStatusIs400() throws Exception {
    HttpResponse<String> response = mockResponse(400, "bad request");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(400, ex.getStatusCode());
    assertFalse(ex.isRetryable());
  }

  @Test
  void generateTest_throwsRetryableHttpExceptionWhenStatusIs500() throws Exception {
    HttpResponse<String> response = mockResponse(500, "server error");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));
    assertEquals(500, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void generateTest_throwsParseExceptionWhenCandidatesAreMissing() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"candidates\":[]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenCandidatesNodeIsMissing() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"foo\":1}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenCandidatesNodeIsNotArray() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"candidates\":{}}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenCandidateContentMissing() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"candidates\":[{}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenPartsMissing() throws Exception {
    HttpResponse<String> response = mockResponse(200, "{\"candidates\":[{\"content\":{}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenPartsIsNotArray() throws Exception {
    HttpResponse<String> response =
        mockResponse(200, "{\"candidates\":[{\"content\":{\"parts\":{}}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenPartsIsEmptyArray() throws Exception {
    HttpResponse<String> response =
        mockResponse(200, "{\"candidates\":[{\"content\":{\"parts\":[]}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenTextNodeIsNull() throws Exception {
    HttpResponse<String> response =
        mockResponse(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":null}]}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_throwsParseExceptionWhenTextMissing() throws Exception {
    HttpResponse<String> response =
        mockResponse(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"type\":\"text\"}]}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void generateTest_clearsUsageWhenUsageMetadataMissing() throws Exception {
    HttpResponse<String> response =
        mockResponse(
            200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"class Foo {}\"}]}}]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    client.generateTest("prompt", config);

    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void generateTest_clearsUsageWhenUsageMetadataIsNull() throws Exception {
    HttpResponse<String> response =
        mockResponse(
            200,
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"class Foo {}\"}]}}],\"usageMetadata\":null}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    client.generateTest("prompt", config);

    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void generateTest_clearsUsageWhenUsageValuesAreNonPositive() throws Exception {
    HttpResponse<String> response =
        mockResponse(
            200,
            """
            {
              "candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}],
              "usageMetadata":{"promptTokenCount":0,"candidatesTokenCount":0,"totalTokenCount":0}
            }
            """);
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    client.generateTest("prompt", config);

    assertTrue(client.getLastUsage().isEmpty());
  }

  @Test
  void generateTest_storesUsageWhenOnlyCompletionTokensPositive() throws Exception {
    HttpResponse<String> response =
        mockResponse(
            200,
            """
            {
              "candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}],
              "usageMetadata":{"promptTokenCount":0,"candidatesTokenCount":4,"totalTokenCount":0}
            }
            """);
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    client.generateTest("prompt", config);

    assertEquals(4L, client.getLastUsage().orElseThrow().getCompletionTokens());
  }

  @Test
  void generateTest_storesUsageWhenOnlyTotalTokensPositive() throws Exception {
    HttpResponse<String> response =
        mockResponse(
            200,
            """
            {
              "candidates":[{"content":{"parts":[{"text":"class Foo {}"}]}}],
              "usageMetadata":{"promptTokenCount":0,"candidatesTokenCount":0,"totalTokenCount":9}
            }
            """);
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);
    client.generateTest("prompt", config);

    assertEquals(9L, client.getLastUsage().orElseThrow().getTotalTokens());
  }

  @Test
  void generateTest_wrapsSerializationFailure() throws Exception {
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    ObjectMapper helper = new ObjectMapper();
    when(failingMapper.createObjectNode()).thenReturn(helper.createObjectNode());
    when(failingMapper.writeValueAsString(any()))
        .thenThrow(org.mockito.Mockito.mock(tools.jackson.core.JacksonException.class));

    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, failingMapper);

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));
    assertTrue(ex.getMessage().contains("Failed to build request body"));
  }

  @Test
  void profile_reportsGeminiProviderWithSeedCapability() {
    GeminiLlmClient client = new GeminiLlmClient(config, httpClient, mapper);

    assertEquals("gemini", client.profile().providerName());
    assertTrue(client.profile().supports(Capability.SEED));
  }

  private static HttpResponse<String> mockResponse(int statusCode, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(statusCode);
    when(response.body()).thenReturn(body);
    return response;
  }

  private static String apiKeyOf(GeminiLlmClient client) {
    try {
      Field f = GeminiLlmClient.class.getDeclaredField("apiKey");
      f.setAccessible(true);
      return (String) f.get(client);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setApiKey(GeminiLlmClient client, String value) {
    try {
      Field f = GeminiLlmClient.class.getDeclaredField("apiKey");
      f.setAccessible(true);
      f.set(client, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
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
    latch.await(1, TimeUnit.SECONDS);
    return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
