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
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.resilience.ResilienceExecutionException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiLlmClientErrorTest {

  private Config.LlmConfig config;
  private HttpClient httpClient;
  private OpenAiLlmClient client;

  @BeforeEach
  void setUp() {
    config = new Config.LlmConfig();
    config.setApiKey("test-key");
    config.setModelName("test-model");
    config.setUrl("http://localhost/openai");
    config.setMaxRetries(0);
    config.setRetryInitialDelayMs(1L);

    httpClient = mock(HttpClient.class);
    client = new OpenAiLlmClient(config, httpClient, new ObjectMapper());
  }

  @Test
  void shouldThrowProviderHttpException_whenStatusIsClientError() throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("Bad Request");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));

    assertEquals(400, ex.getStatusCode());
    assertEquals("Bad Request", ex.getResponseBody());
    assertFalse(ex.isRetryable());
  }

  @Test
  void shouldMarkRetryable_whenStatusIsServerError() throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(500);
    when(response.body()).thenReturn("Server Error");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    LlmProviderHttpException ex =
        assertThrows(LlmProviderHttpException.class, () -> client.generateTest("prompt", config));

    assertEquals(500, ex.getStatusCode());
    assertTrue(ex.isRetryable());
  }

  @Test
  void shouldThrowResponseParseException_whenContentMissing() throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"choices\":[]}");
    when(httpClient.<String>send(any(), any())).thenReturn(response);

    assertThrows(LlmResponseParseException.class, () -> client.generateTest("prompt", config));
  }

  @Test
  void shouldWrapTimeoutAsProviderException_whenRequestTimesOut() throws Exception {
    when(httpClient.<String>send(any(), any())).thenThrow(new HttpTimeoutException("timeout"));

    LlmProviderException ex =
        assertThrows(LlmProviderException.class, () -> client.generateTest("prompt", config));

    assertTrue(ex.getMessage().contains("Failed to generate test with OpenAI"));
    assertTrue(ex.getCause() instanceof ResilienceExecutionException);
    assertTrue(ex.getCause().getCause() instanceof HttpTimeoutException);
  }
}
