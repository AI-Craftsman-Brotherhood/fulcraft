package com.craftsmanbro.fulcraft.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.cache.impl.CacheRevalidator;
import com.craftsmanbro.fulcraft.infrastructure.cache.model.CacheRevalidationResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.CodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheRevalidatorTest {

  private CacheRevalidator revalidator;

  @BeforeEach
  void setUp() {
    revalidator = new CacheRevalidator(new CodeValidator());
  }

  @Test
  void testValidCode() {
    String validCode =
        """
                package com.example;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                class MyTest {
                    @Test
                    void testSomething() {
                        assertEquals(1, 1);
                    }
                }
                """;

    CacheRevalidationResult result = revalidator.revalidate(validCode, "task_valid");

    assertTrue(result.isValid());
    assertNull(result.getReason());
    assertTrue(result.getDurationMs() >= 0);
  }

  @Test
  void testValidCodeInsideMarkdownFence() {
    String fencedCode =
        """
                ```java
                package com.example;

                import org.junit.jupiter.api.Test;

                class FencedTest {
                    @Test
                    void testInsideFence() {}
                }
                ```
                """;

    CacheRevalidationResult result = revalidator.revalidate(fencedCode, "task_fenced");

    assertTrue(result.isValid());
    assertNull(result.getReason());
  }

  @Test
  void testValidCodeWithFullyQualifiedAnnotation() {
    String code =
        """
                package com.example;

                class QualifiedAnnotationTest {
                    @org.junit.jupiter.api.Test
                    void testSomething() {}
                }
                """;

    CacheRevalidationResult result = revalidator.revalidate(code, "task_fully_qualified");

    assertTrue(result.isValid());
  }

  @Test
  void testInvalidSyntax() {
    String invalidCode =
        """
                package com.example;

                class MyTest {
                    @Test
                    void testSomething() {
                        // Missing closing brace

                }
                """;

    CacheRevalidationResult result = revalidator.revalidate(invalidCode, "task_invalid_syntax");

    assertFalse(result.isValid());
    assertNotNull(result.getReason());
    assertTrue(result.getReason().contains("Syntax error"));
  }

  @Test
  void testMissingClassDeclaration() {
    // This is valid Java syntax but contains no class declaration
    String noClassCode =
        """
                package com.example;

                import org.junit.jupiter.api.Test;

                interface TestInterface {
                    void method();
                }
                """;

    CacheRevalidationResult result = revalidator.revalidate(noClassCode, "task_no_class");

    // The code is syntactically valid and contains "class " in "TestInterface" -
    // wait, no...
    // Actually interface doesn't contain "class " keyword, so it should fail
    // But the current implementation checks for "class " substring which won't
    // match interface
    assertFalse(result.isValid());
    assertNotNull(result.getReason());
    assertTrue(result.getReason().contains("Structure error"));
  }

  @Test
  void testMissingTestAnnotation() {
    String noTestAnnotationCode =
        """
                package com.example;

                class MyTest {
                    void testSomething() {
                        // no @Test annotation
                    }
                }
                """;

    CacheRevalidationResult result = revalidator.revalidate(noTestAnnotationCode, "task_no_test");

    assertFalse(result.isValid());
    assertNotNull(result.getReason());
    assertTrue(result.getReason().contains("Structure error"));
  }

  @Test
  void testNullCode() {
    CacheRevalidationResult result = revalidator.revalidate(null, "task_null");

    assertFalse(result.isValid());
    assertTrue(result.getReason().contains("empty or null"));
  }

  @Test
  void testEmptyCode() {
    CacheRevalidationResult result = revalidator.revalidate("", "task_empty");

    assertFalse(result.isValid());
    assertTrue(result.getReason().contains("empty or null"));
  }

  @Test
  void testBlankCode() {
    CacheRevalidationResult result = revalidator.revalidate("   \n\t  ", "task_blank");

    assertFalse(result.isValid());
    assertTrue(result.getReason().contains("empty or null"));
  }

  @Test
  void testStatisticsTracking() {
    // Reset stats
    revalidator.resetStats();
    assertEquals(0, revalidator.getRevalidationCount());
    assertEquals(0, revalidator.getFailureCount());
    assertEquals(100.0, revalidator.getSuccessRate());

    // Valid code
    String validCode = "class Test { @Test void test() {} }";
    revalidator.revalidate(validCode, "task1");
    assertEquals(1, revalidator.getRevalidationCount());
    assertEquals(0, revalidator.getFailureCount());
    assertEquals(100.0, revalidator.getSuccessRate());

    // Invalid code
    String invalidCode = "class Test { void test() { // missing brace";
    revalidator.revalidate(invalidCode, "task2");
    assertEquals(2, revalidator.getRevalidationCount());
    assertEquals(1, revalidator.getFailureCount());
    assertEquals(50.0, revalidator.getSuccessRate());
  }

  @Test
  void testRevalidationResultToString() {
    CacheRevalidationResult valid = CacheRevalidationResult.valid(1.5);
    String validStr = valid.toString();
    assertTrue(validStr.contains("valid=true"));
    assertTrue(validStr.contains("durationMs"));

    CacheRevalidationResult invalid = CacheRevalidationResult.invalid("Test reason");
    String invalidStr = invalid.toString();
    assertTrue(invalidStr.contains("valid=false"));
    assertTrue(invalidStr.contains("Test reason"));
  }

  @Test
  void testRevalidationOverhead() {
    String validCode =
        """
                package com.example;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                class PerformanceTest {
                    @Test
                    void testPerformance() {
                        String s = "test";
                        assertEquals("test", s);
                    }
                }
                """;

    // Warm up
    revalidator.revalidate(validCode, "warmup");

    // Measure multiple revalidations
    int iterations = 100;
    long startTime = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      CacheRevalidationResult result = revalidator.revalidate(validCode, "perf_" + i);
      assertTrue(result.isValid());
    }
    long totalTimeNanos = System.nanoTime() - startTime;
    double avgTimeMs = (totalTimeNanos / 1_000_000.0) / iterations;

    // Verify overhead is acceptable (should be < 50ms per revalidation)
    assertTrue(avgTimeMs < 50, "Revalidation overhead too high: " + avgTimeMs + "ms");
    System.out.println("Average revalidation time: " + String.format("%.2f", avgTimeMs) + "ms");
  }

  @Test
  void testDefaultConstructor() {
    CacheRevalidator defaultRevalidator = new CacheRevalidator();

    String validCode = "class Test { @Test void test() {} }";
    CacheRevalidationResult result = defaultRevalidator.revalidate(validCode, "default_test");

    assertTrue(result.isValid());
  }

  @Test
  void testConstructorRejectsNullCodeValidator() {
    assertThrows(NullPointerException.class, () -> new CacheRevalidator(null));
  }
}
