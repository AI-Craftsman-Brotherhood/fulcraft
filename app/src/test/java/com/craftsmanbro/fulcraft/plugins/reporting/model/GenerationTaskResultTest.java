package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GenerationTaskResultTest {

  @Test
  void setStatusParsesCaseInsensitiveAndBlankAsNull() {
    GenerationTaskResult result = new GenerationTaskResult();

    result.setStatus(" success ");
    assertEquals(GenerationTaskResult.Status.SUCCESS, result.getStatusEnum());
    assertEquals("SUCCESS", result.getStatus());

    result.setStatus(" ");
    assertNull(result.getStatusEnum());
    assertNull(result.getStatus());
  }

  @Test
  void setStatusRejectsUnknownValue() {
    GenerationTaskResult result = new GenerationTaskResult();

    assertThrows(IllegalArgumentException.class, () -> result.setStatus("unknown"));
  }

  @Test
  void generatedTestFileStringParsesPath() {
    GenerationTaskResult result = new GenerationTaskResult();

    result.setGeneratedTestFile(" ");
    assertNull(result.getGeneratedTestFile());
    assertNull(result.getGeneratedTestFilePath());

    result.setGeneratedTestFile("build/tests/FooTest.java");
    Path expected = Path.of("build/tests/FooTest.java");

    assertEquals(expected, result.getGeneratedTestFilePath());
    assertEquals(expected.toString(), result.getGeneratedTestFile());
  }

  @Test
  void generatedTestFileRejectsInvalidPath() {
    GenerationTaskResult result = new GenerationTaskResult();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> result.setGeneratedTestFile("bad\0path"));

    assertTrue(exception.getMessage().contains("bad"));
    assertInstanceOf(InvalidPathException.class, exception.getCause());
  }

  @Test
  void regularFieldsRoundTrip() {
    GenerationTaskResult result = new GenerationTaskResult();
    GenerationResult generationResult = GenerationResult.success().build();

    result.setTaskId("task-1");
    result.setClassFqn("com.example.Foo");
    result.setMethodName("run");
    result.setStatus(GenerationTaskResult.Status.FAILURE);
    result.setErrorMessage("boom");
    result.setExpectedTestCount(5);
    result.setActualTestCount(3);
    result.setFixAttemptCount(2);
    result.setErrorCategory("COMPILE");
    result.setStaticFixCount(1);
    result.setRuntimeFixCount(1);
    result.setComplexityStrategy("strict");
    result.setHighComplexity(Boolean.TRUE);
    result.setGenerationResult(generationResult);
    result.setGeneratedTestFile(Path.of("build/tests/FooTest.java"));

    assertEquals("task-1", result.getTaskId());
    assertEquals("com.example.Foo", result.getClassFqn());
    assertEquals("run", result.getMethodName());
    assertEquals("FAILURE", result.getStatus());
    assertEquals("boom", result.getErrorMessage());
    assertEquals(Integer.valueOf(5), result.getExpectedTestCount());
    assertEquals(Integer.valueOf(3), result.getActualTestCount());
    assertEquals(Integer.valueOf(2), result.getFixAttemptCount());
    assertEquals("COMPILE", result.getErrorCategory());
    assertEquals(Integer.valueOf(1), result.getStaticFixCount());
    assertEquals(Integer.valueOf(1), result.getRuntimeFixCount());
    assertEquals("strict", result.getComplexityStrategy());
    assertEquals(Boolean.TRUE, result.getHighComplexity());
    assertEquals(generationResult, result.getGenerationResult());
    assertEquals(Path.of("build/tests/FooTest.java"), result.getGeneratedTestFilePath());
  }
}
