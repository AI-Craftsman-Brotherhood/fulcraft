package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.ExceptionSummarizer;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExceptionSummarizerTest {

  @Nested
  class RootCauseExtractionTests {

    @Test
    void shouldReturnExceptionItselfWhenNoCause() {
      var exception = new IllegalArgumentException("test message");

      var rootCause = invokeFindRootCause(exception);

      assertSame(exception, rootCause);
    }

    @Test
    void shouldFindDeepestCauseInChain() {
      var rootCause = new NullPointerException("root cause");
      var middle = new RuntimeException("middle", rootCause);
      var top = new Exception("top level", middle);

      var found = invokeFindRootCause(top);

      assertSame(rootCause, found);
    }

    @Test
    void shouldHandleCircularCauseReference() {
      var parent = new RuntimeException("parent");
      var child = new RuntimeException("child", parent);
      parent.initCause(child);

      var found = invokeFindRootCause(child);

      assertNotNull(found);
      assertTrue(found == parent || found == child);
    }

    @Test
    void shouldReturnNullForNullInput() {
      var result = invokeFindRootCause(null);

      assertNull(result);
    }
  }

  @Nested
  class SummarizeTests {

    @Test
    void shouldExtractRootCauseTypeAndMessage() {
      var cause = new NullPointerException("x was null");
      var wrapper = new RuntimeException("wrapper", cause);

      var summary =
          ExceptionSummarizer.summarize(wrapper, ExceptionSummarizer.SummaryOptions.defaults());

      assertEquals("NullPointerException", summary.rootCauseType());
      assertEquals("x was null", summary.rootCauseMessage());
    }

    @Test
    void shouldHandleNullException() {
      var summary =
          ExceptionSummarizer.summarize(null, ExceptionSummarizer.SummaryOptions.defaults());

      assertEquals("Unknown", summary.rootCauseType());
      assertNull(summary.rootCauseMessage());
      assertTrue(summary.keyFrames().isEmpty());
    }

    @Test
    void shouldHandleNullOptions() {
      var exception = new IllegalStateException("test");

      var summary = ExceptionSummarizer.summarize(exception, null);

      assertNotNull(summary);
      assertEquals("IllegalStateException", summary.rootCauseType());
    }

    @Test
    void shouldRespectMaxFramesLimit() {
      var exception = createExceptionWithDeepStack(20);
      var options = ExceptionSummarizer.SummaryOptions.defaults().withMaxFrames(3);

      var summary = ExceptionSummarizer.summarize(exception, options);

      assertTrue(summary.keyFrames().size() <= 3);
      assertTrue(summary.truncated());
    }

    @Test
    void shouldIndicateTruncationWhenLimitExceeded() {
      var exception = createExceptionWithDeepStack(15);
      var options = ExceptionSummarizer.SummaryOptions.defaults().withMaxFrames(5);

      var summary = ExceptionSummarizer.summarize(exception, options);

      assertTrue(summary.truncated());
    }

    @Test
    void shouldClampMaxFramesWhenInvalid() {
      var exception = createExceptionWithDeepStack(5);
      var options =
          new ExceptionSummarizer.SummaryOptions(
              ExceptionSummarizer.Mode.LOG, 0, 2000, true, false);

      var summary = ExceptionSummarizer.summarize(exception, options);

      assertTrue(summary.keyFrames().size() <= 1);
    }

    @Test
    void shouldHandleRootCauseWithEmptyStackTrace() {
      var exception = new RuntimeException("empty stack");
      exception.setStackTrace(new StackTraceElement[0]);

      var summary =
          ExceptionSummarizer.summarize(exception, ExceptionSummarizer.SummaryOptions.defaults());

      assertTrue(summary.keyFrames().isEmpty());
    }
  }

  @Nested
  class FrameFilteringTests {

    @Test
    void shouldFilterJUnitFrames() {
      // Create a mock exception that simulates JUnit frames
      var exception =
          createExceptionWithFrames(
              "com.example.FooTest",
              "shouldWork",
              "FooTest.java",
              42,
              "org.junit.jupiter.api.Test",
              "execute",
              "Test.java",
              100);
      var options =
          new ExceptionSummarizer.SummaryOptions(
              ExceptionSummarizer.Mode.LOG, 10, 2000, true, false);

      var summary = ExceptionSummarizer.summarize(exception, options);

      // Should only have the app frame, not the JUnit frame
      var frames = summary.keyFrames();
      boolean hasJunitFrame = frames.stream().anyMatch(f -> f.contains("org.junit"));
      assertFalse(hasJunitFrame, "JUnit frames should be filtered out");
    }

    @Test
    void shouldPreserveApplicationFrames() {
      var exception = new RuntimeException("test");
      var options =
          new ExceptionSummarizer.SummaryOptions(
              ExceptionSummarizer.Mode.LOG, 10, 2000, true, false);

      var summary = ExceptionSummarizer.summarize(exception, options);

      // Should have application frames
      assertFalse(summary.keyFrames().isEmpty());
    }

    @Test
    void shouldNotFilterWhenStripFrameworkDisabled() {
      var exception = new RuntimeException("test");
      var options =
          new ExceptionSummarizer.SummaryOptions(
              ExceptionSummarizer.Mode.LOG, 10, 2000, false, false);

      var summary = ExceptionSummarizer.summarize(exception, options);

      // Should have more frames when not filtering
      assertFalse(summary.keyFrames().isEmpty());
    }
  }

  @Nested
  class FormattingTests {

    @Test
    void shouldFormatLogModeWithHeaders() {
      var summary =
          new ExceptionSummarizer.ExceptionSummary(
              "NullPointerException",
              "x was null",
              List.of("com.example.Foo.doSomething(Foo.java:42)"),
              false);

      var formatted = summary.format(ExceptionSummarizer.Mode.LOG);

      assertTrue(formatted.startsWith("Root cause:"));
      assertTrue(formatted.contains("NullPointerException"));
      assertTrue(formatted.contains("x was null"));
      assertTrue(formatted.contains("Key stack frames"));
    }

    @Test
    void shouldFormatLlmModeCompact() {
      var summary =
          new ExceptionSummarizer.ExceptionSummary(
              "NullPointerException", "x was null", List.of("Foo.doSomething:42"), false);

      var formatted = summary.format(ExceptionSummarizer.Mode.LLM);

      assertFalse(formatted.contains("Root cause:"));
      assertFalse(formatted.contains("Key stack frames"));
      assertTrue(formatted.contains("NullPointerException: x was null"));
      assertTrue(formatted.contains("at Foo.doSomething:42"));
    }

    @Test
    void shouldIncludeTruncationMarker() {
      var summary =
          new ExceptionSummarizer.ExceptionSummary(
              "RuntimeException", "test", List.of("com.example.Test.run"), true);

      var formatted = summary.format(ExceptionSummarizer.Mode.LOG);

      assertTrue(formatted.contains("...(truncated)"));
    }

    @Test
    void shouldHandleNullMessage() {
      var summary =
          new ExceptionSummarizer.ExceptionSummary("RuntimeException", null, List.of(), false);

      var formatted = summary.format(ExceptionSummarizer.Mode.LOG);

      assertTrue(formatted.contains("RuntimeException"));
      assertFalse(formatted.contains("null"));
    }

    @Test
    void shouldHandleEmptyFrames() {
      var summary =
          new ExceptionSummarizer.ExceptionSummary("RuntimeException", "message", List.of(), false);

      var formatted = summary.format(ExceptionSummarizer.Mode.LOG);

      assertFalse(formatted.contains("Key stack frames"));
    }
  }

  @Nested
  class SummarizeAsStringTests {

    @Test
    void shouldReturnFormattedString() {
      var exception = new NullPointerException("test");

      var result =
          ExceptionSummarizer.summarizeAsString(
              exception, ExceptionSummarizer.SummaryOptions.defaults());

      assertNotNull(result);
      assertTrue(result.contains("NullPointerException"));
    }

    @Test
    void shouldRespectMaxCharsLimit() {
      var exception = createExceptionWithDeepStack(50);
      var options = ExceptionSummarizer.SummaryOptions.defaults().withMaxChars(100);

      var result = ExceptionSummarizer.summarizeAsString(exception, options);

      assertTrue(result.length() <= 100);
      assertTrue(result.contains("...(truncated)"));
    }

    @Test
    void shouldClampMaxCharsWhenInvalid() {
      var exception = createExceptionWithDeepStack(20);
      var options = ExceptionSummarizer.SummaryOptions.defaults().withMaxChars(10);

      var result = ExceptionSummarizer.summarizeAsString(exception, options);

      assertTrue(result.length() <= 15);
    }

    @Test
    void shouldFallbackOnError() {
      // Test with null - should not throw
      var result = ExceptionSummarizer.summarizeAsString(null, null);

      assertNotNull(result);
    }

    @Test
    void shouldUseLlmModeFormat() {
      var exception = new IllegalStateException("invalid state");

      var result =
          ExceptionSummarizer.summarizeAsString(
              exception, ExceptionSummarizer.SummaryOptions.forLlm());

      assertFalse(result.contains("Root cause:"));
      assertTrue(result.contains("IllegalStateException"));
    }

    @Test
    void shouldFallbackToThrowableToStringWhenSummarizationThrows() {
      var exception = new BrokenStackTraceException("broken");

      var result =
          ExceptionSummarizer.summarizeAsString(
              exception, ExceptionSummarizer.SummaryOptions.defaults());

      assertEquals(exception.toString(), result);
    }
  }

  @Nested
  class SummaryOptionsTests {

    @Test
    void shouldCreateDefaultOptions() {
      var options = ExceptionSummarizer.SummaryOptions.defaults();

      assertEquals(ExceptionSummarizer.Mode.LOG, options.mode());
      assertEquals(8, options.maxFrames());
      assertEquals(2000, options.maxChars());
      assertTrue(options.stripFramework());
      assertFalse(options.stripLineNumbers());
    }

    @Test
    void shouldCreateLlmOptions() {
      var options = ExceptionSummarizer.SummaryOptions.forLlm();

      assertEquals(ExceptionSummarizer.Mode.LLM, options.mode());
      assertEquals(5, options.maxFrames());
      assertEquals(1000, options.maxChars());
      assertTrue(options.stripFramework());
      assertTrue(options.stripLineNumbers());
    }

    @Test
    void shouldAllowCustomization() {
      var options =
          ExceptionSummarizer.SummaryOptions.defaults().withMaxFrames(3).withMaxChars(500);

      assertEquals(3, options.maxFrames());
      assertEquals(500, options.maxChars());
    }
  }

  @Nested
  class PrivateBranchCoverageTests {

    @Test
    void shouldIncludeLineNumberInLlmModeWhenNotStrippingLineNumbers() {
      var exception = new RuntimeException("line number");
      exception.setStackTrace(
          new StackTraceElement[] {
            new StackTraceElement("com.example.SampleClass", "run", "SampleClass.java", 42)
          });

      var options =
          new ExceptionSummarizer.SummaryOptions(
              ExceptionSummarizer.Mode.LLM, 5, 1000, false, false);
      var summary = ExceptionSummarizer.summarize(exception, options);

      assertTrue(summary.keyFrames().getFirst().contains(":42"));
    }

    @Test
    void extractKeyFrames_returnsEmptyWhenThrowableIsNull() throws Exception {
      var frames =
          invokePrivate(
              "extractKeyFrames",
              new Class<?>[] {Throwable.class, ExceptionSummarizer.SummaryOptions.class},
              null,
              ExceptionSummarizer.SummaryOptions.defaults());

      assertTrue(((List<?>) frames).isEmpty());
    }

    @Test
    void isFrameworkClass_returnsFalseForNullClassName() throws Exception {
      boolean result =
          (boolean)
              invokePrivate("isFrameworkClass", new Class<?>[] {String.class}, new Object[] {null});

      assertFalse(result);
    }

    @Test
    void getSimpleClassName_returnsUnknownForNull() throws Exception {
      String result =
          (String)
              invokePrivate(
                  "getSimpleClassName", new Class<?>[] {String.class}, new Object[] {null});

      assertEquals("Unknown", result);
    }

    @Test
    void wasFramesTruncated_returnsFalseForNullThrowable() throws Exception {
      boolean truncated =
          (boolean)
              invokePrivate(
                  "wasFramesTruncated",
                  new Class<?>[] {
                    Throwable.class, ExceptionSummarizer.SummaryOptions.class, int.class
                  },
                  null,
                  ExceptionSummarizer.SummaryOptions.defaults(),
                  0);

      assertFalse(truncated);
    }
  }

  // Helper methods

  private RuntimeException createExceptionWithDeepStack(int depth) {
    try {
      throwDeep(depth);
    } catch (RuntimeException e) {
      return e;
    }
    return new RuntimeException("fallback");
  }

  private void throwDeep(int depth) {
    if (depth <= 0) {
      throw new RuntimeException("deep exception");
    }
    throwDeep(depth - 1);
  }

  private RuntimeException createExceptionWithFrames(
      String className1,
      String methodName1,
      String fileName1,
      int line1,
      String className2,
      String methodName2,
      String fileName2,
      int line2) {
    var exception = new RuntimeException("test");
    var frames =
        new StackTraceElement[] {
          new StackTraceElement(className1, methodName1, fileName1, line1),
          new StackTraceElement(className2, methodName2, fileName2, line2)
        };
    exception.setStackTrace(frames);
    return exception;
  }

  private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = ExceptionSummarizer.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  private Throwable invokeFindRootCause(Throwable t) {
    try {
      return (Throwable) invokePrivate("findRootCause", new Class<?>[] {Throwable.class}, t);
    } catch (Exception e) {
      fail("Failed to invoke findRootCause via reflection: " + e.getMessage());
      return null;
    }
  }

  private static final class BrokenStackTraceException extends RuntimeException {
    private BrokenStackTraceException(String message) {
      super(message);
    }

    @Override
    public StackTraceElement[] getStackTrace() {
      throw new IllegalStateException("boom");
    }
  }
}
