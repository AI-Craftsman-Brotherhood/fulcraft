package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared helpers for hashing method bodies.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.CodeHashing} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class CodeHashing {

  private static final String HASH_ALGORITHM = "SHA-256";

  private CodeHashing() {}

  public static String hashNormalized(final String body) {
    final String input = body == null ? "" : body;
    final String normalized = normalizeWhitespace(input);
    return sha256Hex(normalized);
  }

  public static String sha256Hex(final String input) {
    try {
      final var digest = MessageDigest.getInstance(HASH_ALGORITHM);
      final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(HASH_ALGORITHM + " not available", e);
    }
  }

  private static String normalizeWhitespace(final String input) {
    final int length = input.length();
    if (length == 0) {
      return input;
    }
    final StringBuilder normalized = new StringBuilder(length);
    final ParseState state = new ParseState();
    int index = 0;
    while (index < length) {
      final char current = input.charAt(index);
      final char next = index + 1 < length ? input.charAt(index + 1) : '\0';
      final char nextNext = index + 2 < length ? input.charAt(index + 2) : '\0';
      final int advance;
      if (state.inLineComment) {
        advance = handleLineComment(normalized, state, current);
      } else if (state.inBlockComment) {
        advance = handleBlockComment(normalized, state, current, next);
      } else if (state.inTextBlock) {
        advance = handleTextBlock(normalized, state, current, next, nextNext);
      } else if (state.inString) {
        advance = handleStringLiteral(normalized, state, current);
      } else if (state.inChar) {
        advance = handleCharLiteral(normalized, state, current);
      } else {
        advance = handleDefault(normalized, state, current, next, nextNext);
      }
      index += advance;
    }
    return normalized.toString();
  }

  private static int handleLineComment(
      final StringBuilder normalized, final ParseState state, final char current) {
    if (current == '\n' || current == '\r') {
      state.inLineComment = false;
      return 1;
    }
    if (!Character.isWhitespace(current)) {
      normalized.append(current);
    }
    return 1;
  }

  private static int handleBlockComment(
      final StringBuilder normalized, final ParseState state, final char current, final char next) {
    if (current == '*' && next == '/') {
      normalized.append(current).append(next);
      state.inBlockComment = false;
      return 2;
    }
    if (!Character.isWhitespace(current)) {
      normalized.append(current);
    }
    return 1;
  }

  private static int handleTextBlock(
      final StringBuilder normalized,
      final ParseState state,
      final char current,
      final char next,
      final char nextNext) {
    if (isTextBlockDelimiter(current, next, nextNext)) {
      normalized.append(current).append(next).append(nextNext);
      state.inTextBlock = false;
      return 3;
    }
    normalized.append(current);
    return 1;
  }

  private static int handleStringLiteral(
      final StringBuilder normalized, final ParseState state, final char current) {
    normalized.append(current);
    if (state.escaped) {
      state.escaped = false;
    } else if (current == '\\') {
      state.escaped = true;
    } else if (current == '"') {
      state.inString = false;
    }
    return 1;
  }

  private static int handleCharLiteral(
      final StringBuilder normalized, final ParseState state, final char current) {
    normalized.append(current);
    if (state.escaped) {
      state.escaped = false;
    } else if (current == '\\') {
      state.escaped = true;
    } else if (current == '\'') {
      state.inChar = false;
    }
    return 1;
  }

  private static int handleDefault(
      final StringBuilder normalized,
      final ParseState state,
      final char current,
      final char next,
      final char nextNext) {
    if (current == '/' && next == '/') {
      normalized.append(current).append(next);
      state.inLineComment = true;
      return 2;
    }
    if (current == '/' && next == '*') {
      normalized.append(current).append(next);
      state.inBlockComment = true;
      return 2;
    }
    if (isTextBlockDelimiter(current, next, nextNext)) {
      normalized.append(current).append(next).append(nextNext);
      state.inTextBlock = true;
      return 3;
    }
    if (current == '"') {
      normalized.append(current);
      state.inString = true;
      state.escaped = false;
      return 1;
    }
    if (current == '\'') {
      normalized.append(current);
      state.inChar = true;
      state.escaped = false;
      return 1;
    }
    if (Character.isWhitespace(current)) {
      return 1;
    }
    normalized.append(current);
    return 1;
  }

  private static boolean isTextBlockDelimiter(
      final char current, final char next, final char nextNext) {
    return current == '"' && next == '"' && nextNext == '"';
  }

  private static final class ParseState {

    private boolean inString;

    private boolean inChar;

    private boolean inTextBlock;

    private boolean inLineComment;

    private boolean inBlockComment;

    private boolean escaped;
  }
}
