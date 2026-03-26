package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A PrintStream that intercepts output and redirects it to an ExecutionSession.
 *
 * <p>This class is used to capture Logger output during TUI execution and display it in the log
 * pane instead of writing directly to stdout/stderr.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ExecutionSession session = new ExecutionSession();
 * TuiLogRedirector redirector = new TuiLogRedirector(session);
 * Logger.setOutput(redirector.getStdout(), redirector.getStderr());
 * // ... run pipeline ...
 * Logger.setOutput(redirector.getOriginalStdout(), redirector.getOriginalStderr());
 * }</pre>
 */
public class TuiLogRedirector {

  private final PrintStream originalStdout;

  private final PrintStream originalStderr;

  private final InterceptingPrintStream interceptedStdout;

  private final InterceptingPrintStream interceptedStderr;

  /**
   * Creates a TuiLogRedirector that captures output to the given session.
   *
   * @param session the execution session to capture logs to
   */
  public TuiLogRedirector(final ExecutionSession session) {
    Objects.requireNonNull(session, "session must not be null");
    // Store originals by capturing current System.out/err since Logger.setOutput
    // may change them
    this.originalStdout = System.out;
    this.originalStderr = System.err;
    final Consumer<String> sessionLogAppender =
        line -> session.appendLog(maskMessageForLogPane(line));
    this.interceptedStdout = new InterceptingPrintStream(originalStdout, sessionLogAppender);
    this.interceptedStderr = new InterceptingPrintStream(originalStderr, sessionLogAppender);
  }

  /**
   * Returns the intercepted stdout PrintStream.
   *
   * @return the stdout stream
   */
  public PrintStream getStdout() {
    return interceptedStdout;
  }

  /**
   * Returns the intercepted stderr PrintStream.
   *
   * @return the stderr stream
   */
  public PrintStream getStderr() {
    return interceptedStderr;
  }

  /**
   * Returns the original stdout for restoration.
   *
   * @return the original stdout
   */
  public PrintStream getOriginalStdout() {
    return originalStdout;
  }

  /**
   * Returns the original stderr for restoration.
   *
   * @return the original stderr
   */
  public PrintStream getOriginalStderr() {
    return originalStderr;
  }

  /** A PrintStream that intercepts output and sends it to a consumer. */
  private static class InterceptingPrintStream extends PrintStream {

    private final Consumer<String> lineConsumer;

    private char[] lineBuffer = new char[256];

    private int lineLength;

    private boolean pendingCarriageReturn;

    InterceptingPrintStream(final OutputStream underlying, final Consumer<String> lineConsumer) {
      super(underlying, true, StandardCharsets.UTF_8);
      this.lineConsumer = Objects.requireNonNull(lineConsumer, "lineConsumer must not be null");
    }

    @Override
    public void print(final String s) {
      final String message = s == null ? "null" : s;
      processText(message);
    }

    @Override
    public void println(final String s) {
      final String message = s == null ? "null" : s;
      processText(message + System.lineSeparator());
    }

    @Override
    public void println() {
      processText(System.lineSeparator());
    }

    @Override
    public void write(final byte[] buf, final int off, final int len) {
      final String decodedText = new String(buf, off, len, StandardCharsets.UTF_8);
      processText(decodedText);
    }

    @Override
    public void write(final int b) {
      processText(String.valueOf((char) b));
    }

    private synchronized void processText(final String text) {
      for (int index = 0; index < text.length(); index++) {
        final char currentChar = text.charAt(index);
        if (pendingCarriageReturn) {
          if (currentChar == '\n') {
            emitBufferedLine();
            pendingCarriageReturn = false;
            continue;
          }
          // A standalone carriage return means subsequent text overwrites the current
          // line.
          lineLength = 0;
          pendingCarriageReturn = false;
        }

        if (currentChar == '\r') {
          pendingCarriageReturn = true;
          continue;
        }
        if (currentChar == '\n') {
          emitBufferedLine();
          continue;
        }
        appendBufferedChar(currentChar);
      }
    }

    @Override
    public synchronized void flush() {
      if (pendingCarriageReturn) {
        // A pending carriage return discards the buffered line because it was being
        // rewritten.
        lineLength = 0;
        pendingCarriageReturn = false;
      }
      // Flush any remaining content
      if (lineLength > 0) {
        emitBufferedLine();
      }
      super.flush();
    }

    @Override
    public void close() {
      flush();
    }

    private void emitBufferedLine() {
      lineConsumer.accept(new String(lineBuffer, 0, lineLength));
      lineLength = 0;
    }

    private void appendBufferedChar(final char c) {
      ensureLineBufferCapacity(lineLength + 1);
      lineBuffer[lineLength++] = c;
    }

    private void ensureLineBufferCapacity(final int requiredCapacity) {
      if (requiredCapacity > lineBuffer.length) {
        final int expandedCapacity = Math.max(lineBuffer.length * 2, requiredCapacity);
        lineBuffer = Arrays.copyOf(lineBuffer, expandedCapacity);
      }
    }
  }

  private static String maskMessageForLogPane(final String message) {
    final String maskedMessage = SecretMasker.mask(message);
    return maskedMessage == null ? "" : maskedMessage;
  }
}
