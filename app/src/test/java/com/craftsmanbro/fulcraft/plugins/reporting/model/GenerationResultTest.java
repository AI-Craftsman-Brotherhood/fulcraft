package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GenerationResultTest {

  @Test
  void successBuilderPopulatesFields() {
    GenerationResult result =
        GenerationResult.success()
            .generatedTestCode("class FooTest {}")
            .testClassName("FooTest")
            .elapsedTime(Duration.ofMillis(1500))
            .tokenUsage(123)
            .promptTokens(100)
            .completionTokens(23)
            .retryCount(1)
            .llmModelUsed("gpt-4")
            .rawLlmResponse("raw-response")
            .build();

    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
    assertEquals("class FooTest {}", result.getGeneratedTestCode().orElseThrow());
    assertEquals("class FooTest {}", result.getGeneratedTestCodeValue());
    assertEquals("FooTest", result.getTestClassName().orElseThrow());
    assertEquals("FooTest", result.getTestClassNameValue());
    assertEquals(Duration.ofMillis(1500), result.getElapsedTime().orElseThrow());
    assertEquals(1500L, result.getElapsedTimeMs());
    assertEquals(123, result.getTokenUsage());
    assertEquals(100, result.getPromptTokens());
    assertEquals(23, result.getCompletionTokens());
    assertEquals(1, result.getRetryCount());
    assertEquals("gpt-4", result.getLlmModelUsed().orElseThrow());
    assertEquals("raw-response", result.getRawLlmResponse().orElseThrow());
  }

  @Test
  void failureBuilderPopulatesFields() {
    GenerationResult result =
        GenerationResult.failure()
            .errorMessage("LLM request timed out")
            .errorCode("TIMEOUT")
            .elapsedTime(Duration.ofSeconds(30))
            .retryCount(2)
            .build();

    assertFalse(result.isSuccess());
    assertTrue(result.isFailure());
    assertEquals("LLM request timed out", result.getErrorMessage().orElseThrow());
    assertEquals("LLM request timed out", result.getErrorMessageValue());
    assertEquals("TIMEOUT", result.getErrorCode().orElseThrow());
    assertEquals("TIMEOUT", result.getErrorCodeValue());
    assertEquals(Duration.ofSeconds(30), result.getElapsedTime().orElseThrow());
    assertEquals(2, result.getRetryCount());
  }

  @Test
  void elapsedTimeMsSetterRoundTripsAndHandlesNull() {
    GenerationResult result = new GenerationResult();

    result.setElapsedTimeMs(250L);
    assertEquals(Duration.ofMillis(250), result.getElapsedTime().orElseThrow());
    assertEquals(250L, result.getElapsedTimeMs());

    result.setElapsedTimeMs(null);
    assertTrue(result.getElapsedTime().isEmpty());
    assertNull(result.getElapsedTimeMs());
  }

  @Test
  void optionalGettersAreEmptyWhenUnset() {
    GenerationResult result = new GenerationResult();

    assertTrue(result.getGeneratedTestCode().isEmpty());
    assertTrue(result.getTestClassName().isEmpty());
    assertTrue(result.getErrorMessage().isEmpty());
    assertTrue(result.getErrorCode().isEmpty());
    assertTrue(result.getLlmModelUsed().isEmpty());
    assertTrue(result.getRawLlmResponse().isEmpty());
    assertNull(result.getGeneratedTestCodeValue());
    assertNull(result.getTestClassNameValue());
    assertNull(result.getErrorMessageValue());
    assertNull(result.getErrorCodeValue());
    assertNull(result.getLlmModelUsedValue());
    assertNull(result.getRawLlmResponseValue());
  }
}
