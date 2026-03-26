package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.Optional;
import java.util.regex.Pattern;

/** Derives loop and conditional flags from raw source code to keep analyzers consistent. */
public class MethodDerivedMetricsComputer {

  private static final String UNKNOWN_FILE_PATH = "unknown";
  private static final String TWO_SPACES = "  ";
  private static final String THREE_SPACES = "   ";

  private static final Pattern LOOP_FOR = Pattern.compile("\\bfor\\s*\\(");

  private static final Pattern LOOP_WHILE = Pattern.compile("\\bwhile\\s*\\(");

  private static final Pattern LOOP_DO = Pattern.compile("\\bdo\\b");

  private static final Pattern COND_IF = Pattern.compile("\\bif\\s*\\(");

  private static final Pattern COND_SWITCH = Pattern.compile("\\bswitch\\s*\\(");

  private static final Pattern COND_TERNARY = Pattern.compile("\\?[^:]+:", Pattern.DOTALL);

  private static final Pattern COND_CATCH = Pattern.compile("\\bcatch\\s*\\(");

  private static final Pattern[] LOOP_PATTERNS = {LOOP_FOR, LOOP_WHILE, LOOP_DO};

  private static final Pattern[] CONDITIONAL_PATTERNS = {
    COND_IF, COND_SWITCH, COND_TERNARY, COND_CATCH
  };

  public Optional<AnalysisError> compute(final MethodInfo method) {
    return compute(method, UNKNOWN_FILE_PATH);
  }

  public Optional<AnalysisError> compute(final MethodInfo method, final String filePath) {
    if (method == null) {
      return Optional.empty();
    }
    final String path = resolveFilePath(filePath);
    final String source = method.getSourceCode();
    if (source == null || source.isBlank()) {
      return Optional.of(
          new AnalysisError(
              path, "Missing source_code for derived metrics; leaving existing flags", null));
    }
    final String cleaned = stripLiteralsAndComments(source);
    applyDerivedFlags(method, cleaned);
    return Optional.empty();
  }

  private String resolveFilePath(final String filePath) {
    return (filePath == null || filePath.isBlank()) ? UNKNOWN_FILE_PATH : filePath;
  }

  private void applyDerivedFlags(final MethodInfo method, final String source) {
    method.setHasLoops(containsLoop(source));
    method.setHasConditionals(containsConditionals(source));
  }

  private boolean containsLoop(final String source) {
    return containsAny(source, LOOP_PATTERNS);
  }

  private boolean containsConditionals(final String source) {
    return containsAny(source, CONDITIONAL_PATTERNS);
  }

  private boolean containsAny(final String source, final Pattern[] patterns) {
    for (final Pattern pattern : patterns) {
      if (pattern.matcher(source).find()) {
        return true;
      }
    }
    return false;
  }

  private String stripLiteralsAndComments(final String source) {
    // Single-pass scan to avoid treating comment markers inside literals as comments.
    final StringBuilder cleaned = new StringBuilder(source.length());
    final ScanCursor cursor = new ScanCursor(source);
    ScanMode scanMode = ScanMode.NORMAL;
    while (cursor.hasNext()) {
      final Transition transition =
          switch (scanMode) {
            case NORMAL -> handleNormal(cursor, cleaned);
            case LINE_COMMENT -> handleLineComment(cursor, cleaned);
            case BLOCK_COMMENT -> handleBlockComment(cursor, cleaned);
            case TEXT_BLOCK -> handleTextBlock(cursor, cleaned);
            case STRING -> handleString(cursor, cleaned);
            case CHAR -> handleChar(cursor, cleaned);
          };
      scanMode = transition.mode();
      cursor.advance(transition.advance());
    }
    return cleaned.toString();
  }

  private Transition handleNormal(final ScanCursor cursor, final StringBuilder cleaned) {
    final char current = cursor.current();
    final char next = cursor.peek(1);
    final char next2 = cursor.peek(2);
    if (current == '/' && next == '/') {
      cleaned.append(TWO_SPACES);
      return new Transition(ScanMode.LINE_COMMENT, 2);
    }
    if (current == '/' && next == '*') {
      cleaned.append(TWO_SPACES);
      return new Transition(ScanMode.BLOCK_COMMENT, 2);
    }
    if (current == '"' && next == '"' && next2 == '"') {
      cleaned.append(THREE_SPACES);
      return new Transition(ScanMode.TEXT_BLOCK, 3);
    }
    if (current == '"') {
      cleaned.append(' ');
      return new Transition(ScanMode.STRING, 1);
    }
    if (current == '\'') {
      cleaned.append(' ');
      return new Transition(ScanMode.CHAR, 1);
    }
    cleaned.append(current);
    return new Transition(ScanMode.NORMAL, 1);
  }

  private Transition handleLineComment(final ScanCursor cursor, final StringBuilder cleaned) {
    final char current = cursor.current();
    if (current == '\n') {
      cleaned.append('\n');
      return new Transition(ScanMode.NORMAL, 1);
    }
    cleaned.append(' ');
    return new Transition(ScanMode.LINE_COMMENT, 1);
  }

  private Transition handleBlockComment(final ScanCursor cursor, final StringBuilder cleaned) {
    final char current = cursor.current();
    final char next = cursor.peek(1);
    if (current == '*' && next == '/') {
      cleaned.append(TWO_SPACES);
      return new Transition(ScanMode.NORMAL, 2);
    }
    cleaned.append(' ');
    return new Transition(ScanMode.BLOCK_COMMENT, 1);
  }

  private Transition handleTextBlock(final ScanCursor cursor, final StringBuilder cleaned) {
    final char current = cursor.current();
    final char next = cursor.peek(1);
    final char next2 = cursor.peek(2);
    if (current == '"' && next == '"' && next2 == '"') {
      cleaned.append(THREE_SPACES);
      return new Transition(ScanMode.NORMAL, 3);
    }
    cleaned.append(current == '\n' ? '\n' : ' ');
    return new Transition(ScanMode.TEXT_BLOCK, 1);
  }

  private Transition handleString(final ScanCursor cursor, final StringBuilder cleaned) {
    return handleQuotedLiteral(cursor, cleaned, '"', ScanMode.STRING);
  }

  private Transition handleChar(final ScanCursor cursor, final StringBuilder cleaned) {
    return handleQuotedLiteral(cursor, cleaned, '\'', ScanMode.CHAR);
  }

  private Transition handleQuotedLiteral(
      final ScanCursor cursor,
      final StringBuilder cleaned,
      final char terminator,
      final ScanMode scanMode) {
    final char current = cursor.current();
    final char next = cursor.peek(1);
    if (current == '\\' && next != '\0') {
      cleaned.append(TWO_SPACES);
      return new Transition(scanMode, 2);
    }
    if (current == terminator) {
      cleaned.append(' ');
      return new Transition(ScanMode.NORMAL, 1);
    }
    cleaned.append(' ');
    return new Transition(scanMode, 1);
  }

  private enum ScanMode {
    NORMAL,
    LINE_COMMENT,
    BLOCK_COMMENT,
    TEXT_BLOCK,
    STRING,
    CHAR
  }

  private record Transition(ScanMode mode, int advance) {}

  private static final class ScanCursor {

    private final String source;

    private final int length;

    private int index;

    private ScanCursor(final String source) {
      this.source = source;
      this.length = source.length();
    }

    private boolean hasNext() {
      return index < length;
    }

    private char current() {
      return source.charAt(index);
    }

    private char peek(final int offset) {
      final int position = index + offset;
      return position < length ? source.charAt(position) : '\0';
    }

    private void advance(final int count) {
      index += count;
    }
  }
}
