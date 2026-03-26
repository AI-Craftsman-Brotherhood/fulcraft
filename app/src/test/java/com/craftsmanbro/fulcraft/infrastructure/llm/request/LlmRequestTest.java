package com.craftsmanbro.fulcraft.infrastructure.llm.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.llm.impl.request.LlmRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmRequestTest {

  @Test
  void build_throwsWhenUriMissing() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class, () -> LlmRequest.newBuilder().requestBody("body").build());

    assertTrue(exception.getMessage().endsWith("URI must not be null"), exception.getMessage());
  }

  @Test
  void build_throwsWhenRequestBodyMissing() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> LlmRequest.newBuilder().uri(URI.create("http://localhost")).build());

    assertTrue(
        exception.getMessage().endsWith("Request body must not be null"), exception.getMessage());
  }

  @Test
  void build_copiesHeadersAndPreventsMutation() {
    Map<String, String> headers = new HashMap<>();
    headers.put("X-Test", "1");

    LlmRequest request =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("body")
            .headers(headers)
            .build();

    headers.put("X-New", "2");

    assertEquals(1, request.getHeaders().size());
    assertEquals("1", request.getHeaders().get("X-Test"));
    assertNull(request.getHeaders().get("X-New"));
    assertThrows(UnsupportedOperationException.class, () -> request.getHeaders().put("X", "3"));
  }

  @Test
  void requestHash_isStableAcrossHeaderOrder() {
    Map<String, String> headersA = new LinkedHashMap<>();
    headersA.put("A", "1");
    headersA.put("B", "2");

    Map<String, String> headersB = new LinkedHashMap<>();
    headersB.put("B", "2");
    headersB.put("A", "1");

    LlmRequest requestA =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(headersA)
            .build();

    LlmRequest requestB =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(headersB)
            .build();

    assertEquals(requestA.getRequestHash(), requestB.getRequestHash());
  }

  @Test
  void requestHash_changesWhenInputsChange() {
    Map<String, String> headers = Map.of("A", "1");

    LlmRequest requestA =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(headers)
            .build();

    LlmRequest requestB =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload-updated")
            .headers(headers)
            .build();

    LlmRequest requestC =
        LlmRequest.newBuilder()
            .uri(URI.create("http://example.org"))
            .requestBody("payload")
            .headers(headers)
            .build();

    LlmRequest requestD =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(Map.of("A", "2"))
            .build();

    assertNotEquals(requestA.getRequestHash(), requestB.getRequestHash());
    assertNotEquals(requestA.getRequestHash(), requestC.getRequestHash());
    assertNotEquals(requestA.getRequestHash(), requestD.getRequestHash());
  }

  @Test
  void build_setsOptionalFieldsAndDefaultsHeadersToEmptyMap() {
    LlmRequest request =
        LlmRequest.newBuilder()
            .prompt("test prompt")
            .model("gpt-test")
            .temperature(0.3)
            .topP(0.9)
            .seed(123)
            .maxTokens(456)
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .build();

    assertEquals("test prompt", request.getPrompt());
    assertEquals("gpt-test", request.getModel());
    assertEquals(0.3, request.getTemperature());
    assertEquals(0.9, request.getTopP());
    assertEquals(123, request.getSeed());
    assertEquals(456, request.getMaxTokens());
    assertEquals(0, request.getHeaders().size());
    assertThrows(UnsupportedOperationException.class, () -> request.getHeaders().put("X", "1"));
  }

  @Test
  void requestHash_isSameForNullAndEmptyHeaders() {
    LlmRequest withNullHeaders =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .build();

    LlmRequest withEmptyHeaders =
        LlmRequest.newBuilder()
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(Map.of())
            .build();

    assertEquals(withNullHeaders.getRequestHash(), withEmptyHeaders.getRequestHash());
  }

  @Test
  void requestHash_ignoresPromptAndSamplingMetadata() {
    Map<String, String> headers = Map.of("Authorization", "Bearer token");

    LlmRequest requestA =
        LlmRequest.newBuilder()
            .prompt("prompt A")
            .model("model-a")
            .temperature(0.1)
            .topP(0.5)
            .seed(1)
            .maxTokens(100)
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(headers)
            .build();

    LlmRequest requestB =
        LlmRequest.newBuilder()
            .prompt("prompt B")
            .model("model-b")
            .temperature(0.9)
            .topP(1.0)
            .seed(999)
            .maxTokens(2048)
            .uri(URI.create("http://craftsmann-bro.com"))
            .requestBody("payload")
            .headers(headers)
            .build();

    assertEquals(requestA.getRequestHash(), requestB.getRequestHash());
  }
}
