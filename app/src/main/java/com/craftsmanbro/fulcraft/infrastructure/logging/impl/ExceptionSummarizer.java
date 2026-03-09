package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * E1: Utility for summarizing exceptions to improve CLI UX and prepare for LLM repair prompts.
 *
 * <p>Extracts the root cause and key stack frames from exception chains, filtering out framework
 * noise (JUnit, Gradle, reflection internals).
 */
public final class ExceptionSummarizer {

  private ExceptionSummarizer() {}

  /** Output mode for summary formatting. */
  public enum Mode {

    /** Human-readable format with headers for CLI output. */
    LOG,
    /** Compact format optimized for LLM input (minimal noise). */
    LLM
  }

  /** Configuration options for summarization. */
  public record SummaryOptions(
      Mode mode, int maxFrames, int maxChars, boolean stripFramework, boolean stripLineNumbers) {

    /** Default options for LOG mode output. */
    public static SummaryOptions defaults() {
      return new SummaryOptions(Mode.LOG, 8, 2000, true, false);
    }

    /** Options optimized for LLM input. */
    public static SummaryOptions forLlm() {
      return new SummaryOptions(Mode.LLM, 5, 1000, true, true);
    }

    /** Builder-style method to customize max frames. */
    public SummaryOptions withMaxFrames(final int maxFrames) {
      return new SummaryOptions(mode, maxFrames, maxChars, stripFramework, stripLineNumbers);
    }

    /** Builder-style method to customize max chars. */
    public SummaryOptions withMaxChars(final int maxChars) {
      return new SummaryOptions(mode, maxFrames, maxChars, stripFramework, stripLineNumbers);
    }
  }

  /** Structured summary result for programmatic use. */
  public record ExceptionSummary(
      String rootCauseType, String rootCauseMessage, List<String> keyFrames, boolean truncated) {

    /**
     * Formats the summary as a string.
     *
     * @param mode the output mode
     * @return formatted string representation
     */
    public String format(final Mode mode) {
      final var sb = new StringBuilder();
      appendRootCause(sb, mode);
      appendKeyFrames(sb, mode);
      appendTruncationMarker(sb);
      return sb.toString().stripTrailing();
    }

    private void appendRootCause(final StringBuilder sb, final Mode mode) {
      if (mode == Mode.LOG) {
        sb.append("Root cause: ");
      }
      sb.append(rootCauseType);
      if (rootCauseMessage != null && !rootCauseMessage.isBlank()) {
        sb.append(": ").append(rootCauseMessage);
      }
      sb.append("\n");
    }

    private void appendKeyFrames(final StringBuilder sb, final Mode mode) {
      if (keyFrames.isEmpty()) {
        return;
      }
      if (mode == Mode.LOG) {
        sb.append("Key stack frames (near failing test):\n");
        for (final String frame : keyFrames) {
          sb.append("  ").append(frame).append("\n");
        }
      } else {
        for (final String frame : keyFrames) {
          sb.append("at ").append(frame).append("\n");
        }
      }
    }

    private void appendTruncationMarker(final StringBuilder sb) {
      if (truncated) {
        sb.append("...(truncated)\n");
      }
    }
  }

  // Framework packages to filter out
  private static final Set<String> FRAMEWORK_PACKAGES =
      Set.of(
          "org.junit.",
          "org.opentest4j.",
          "org.gradle.",
          "gradle.",
          "java.lang.reflect.",
          "sun.reflect.",
          "jdk.internal.",
          "org.mockito.",
          "org.assertj.");

  /**
   * Summarizes an exception into a structured format.
   *
   * @param t the exception to summarize (may be null)
   * @param options summarization options
   * @return structured summary, never null
   */
  public static ExceptionSummary summarize(final Throwable t, SummaryOptions options) {
    if (t == null) {
      return new ExceptionSummary("Unknown", null, List.of(), false);
    }
    final SummaryOptions normalizedOptions = normalizeOptions(options);
    // Find root cause
    final Throwable rootCause = findRootCause(t);
    final String rootType = rootCause.getClass().getSimpleName();
    final String rootMessage = rootCause.getMessage();
    // Extract and filter stack frames
    final List<String> frames = extractKeyFrames(t, normalizedOptions);
    // Check if truncated
    final boolean truncated = wasFramesTruncated(t, normalizedOptions, frames.size());
    return new ExceptionSummary(rootType, rootMessage, frames, truncated);
  }

  /**
   * Summarizes an exception as a formatted string.
   *
   * @param t the exception to summarize (may be null)
   * @param options summarization options
   * @return formatted string summary
   */
  public static String summarizeAsString(final Throwable t, SummaryOptions options) {
    try {
      final SummaryOptions normalizedOptions = normalizeOptions(options);
      final var summary = summarize(t, normalizedOptions);
      String result = summary.format(normalizedOptions.mode());
      // Apply max chars limit
      if (result.length() > normalizedOptions.maxChars()) {
        result = result.substring(0, normalizedOptions.maxChars() - 15) + "\n...(truncated)";
      }
      return result;
    } catch (RuntimeException e) {
      // Fallback: never throw from this utility
      return t != null ? t.toString() : "Unknown exception";
    }
  }

  /**
   * Finds the root cause by traversing the cause chain.
   *
   * @param t the exception
   * @return the deepest cause in the chain
   */
  static Throwable findRootCause(final Throwable t) {
    if (t == null) {
      return null;
    }
    Throwable current = t;
    Throwable lastNonNull = t;
    final var visited =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
    // Limit iterations to prevent infinite loops
    final int maxDepth = 50;
    int depth = 0;
    // Loop until current is null, visited, self-referencing, or depth exceeded
    while (current != null && depth < maxDepth && visited.add(current)) {
      lastNonNull = current;
      final Throwable cause = current.getCause();
      // Move to next cause only if it's different (avoid self-reference cycles)
      current = Objects.equals(cause, current) ? null : cause;
      depth++;
    }
    return lastNonNull;
  }

  /**
   * Extracts key stack frames, filtering out framework noise.
   *
   * @param t the exception
   * @param options summarization options
   * @return list of formatted frame strings
   */
  private static List<String> extractKeyFrames(final Throwable t, final SummaryOptions options) {
    // Get the stack trace from the root cause for most relevant frames
    final Throwable rootCause = findRootCause(t);
    if (rootCause == null) {
      return new ArrayList<>();
    }
    final StackTraceElement[] elements = rootCause.getStackTrace();
    if (elements.length == 0) {
      return new ArrayList<>();
    }
    // Filter and limit frames using stream to avoid multiple break/continue
    return java.util.Arrays.stream(elements)
        .filter(element -> !options.stripFramework() || !isFrameworkClass(element.getClassName()))
        .limit(options.maxFrames())
        .map(element -> formatFrame(element, options))
        .toList();
  }

  /**
   * Checks if a class belongs to a framework package that should be filtered.
   *
   * @param className the fully qualified class name
   * @return true if it's a framework class
   */
  private static boolean isFrameworkClass(final String className) {
    if (className == null) {
      return false;
    }
    for (final String pkg : FRAMEWORK_PACKAGES) {
      if (className.startsWith(pkg)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Formats a stack trace element.
   *
   * @param element the stack trace element
   * @param options summarization options
   * @return formatted string
   */
  private static String formatFrame(final StackTraceElement element, final SummaryOptions options) {
    final var sb = new StringBuilder();
    if (options.mode() == Mode.LLM) {
      // Compact format: ClassName.method:line
      final String simpleClassName = getSimpleClassName(element.getClassName());
      sb.append(simpleClassName).append(".").append(element.getMethodName());
      if (!options.stripLineNumbers() && element.getLineNumber() > 0) {
        sb.append(":").append(element.getLineNumber());
      }
    } else {
      // LOG format: full qualified.ClassName.method(File.java:line)
      sb.append(element.getClassName()).append(".").append(element.getMethodName()).append("(");
      final String fileName = element.getFileName();
      if (fileName != null) {
        sb.append(fileName);
        if (!options.stripLineNumbers() && element.getLineNumber() > 0) {
          sb.append(":").append(element.getLineNumber());
        }
      } else {
        sb.append("Unknown Source");
      }
      sb.append(")");
    }
    return sb.toString();
  }

  /**
   * Extracts the simple class name from a fully qualified name.
   *
   * @param fqcn the fully qualified class name
   * @return the simple class name
   */
  private static String getSimpleClassName(final String fqcn) {
    if (fqcn == null) {
      return "Unknown";
    }
    final int lastDot = fqcn.lastIndexOf('.');
    return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
  }

  /**
   * Checks if frames were truncated due to the max limit.
   *
   * @param t the exception
   * @param options summarization options
   * @param extractedCount number of frames actually extracted
   * @return true if truncation occurred
   */
  private static boolean wasFramesTruncated(
      final Throwable t, final SummaryOptions options, final int extractedCount) {
    final Throwable rootCause = findRootCause(t);
    if (rootCause == null) {
      return false;
    }
    final StackTraceElement[] elements = rootCause.getStackTrace();
    // Count non-framework frames using stream
    final long availableFrames =
        java.util.Arrays.stream(elements)
            .filter(
                element -> !options.stripFramework() || !isFrameworkClass(element.getClassName()))
            .count();
    return availableFrames > extractedCount;
  }

  private static SummaryOptions normalizeOptions(final SummaryOptions options) {
    if (options == null) {
      return SummaryOptions.defaults();
    }
    int maxFrames = options.maxFrames();
    int maxChars = options.maxChars();
    boolean changed = false;
    if (maxFrames <= 0) {
      maxFrames = 1;
      changed = true;
    }
    if (maxChars < 15) {
      maxChars = 15;
      changed = true;
    }
    if (!changed) {
      return options;
    }
    return new SummaryOptions(
        options.mode(), maxFrames, maxChars, options.stripFramework(), options.stripLineNumbers());
  }
}
