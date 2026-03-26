package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import java.util.ArrayList;
import java.util.List;

public final class PathParser {

  private PathParser() {}

  public record ParsedPath(List<ConfigEditor.PathSegment> segments, boolean append) {

    public ParsedPath {
      segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public boolean hasIndex() {
      for (final ConfigEditor.PathSegment segment : segments) {
        if (segment.isIndex()) {
          return true;
        }
      }
      return false;
    }
  }

  public static ParsedPath parse(final String rawPath) {
    final String trimmed = normalizeAndValidate(rawPath);
    final ParseState state = new ParseState();
    parseSegments(trimmed, state);
    state.flushKeyIfPresent();
    validateParsedSegments(state);
    return new ParsedPath(state.segments, state.append);
  }

  public static String toMetadataPath(final List<ConfigEditor.PathSegment> segments) {
    if (segments == null || segments.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (final ConfigEditor.PathSegment segment : segments) {
      if (segment.isKey()) {
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(segment.key());
      }
    }
    return sb.toString();
  }

  public static String toPathString(final List<ConfigEditor.PathSegment> segments) {
    if (segments == null || segments.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (final ConfigEditor.PathSegment segment : segments) {
      if (segment.isKey()) {
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(segment.key());
      } else if (segment.isIndex()) {
        sb.append('[').append(segment.index()).append(']');
      }
    }
    return sb.toString();
  }

  private static String normalizeAndValidate(final String rawPath) {
    final String trimmed = rawPath == null ? "" : rawPath.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.path_required"));
    }
    if (trimmed.endsWith(".")) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.empty_segment"));
    }
    return trimmed;
  }

  private static void parseSegments(final String trimmed, final ParseState state) {
    int index = 0;
    while (index < trimmed.length()) {
      final char c = trimmed.charAt(index);
      if (c == '.') {
        handleDot(state);
        index++;
        continue;
      }
      if (c == '[') {
        final int close = handleIndexSegment(trimmed, index, state);
        index = close + 1;
        continue;
      }
      if (c == ']') {
        throw new IllegalArgumentException(msg("tui.path_parser.error.unexpected_closing_bracket"));
      }
      state.appendKeyChar(c);
      index++;
    }
  }

  private static void handleDot(final ParseState state) {
    state.flushKeyOrThrowEmptySegment();
  }

  private static int handleIndexSegment(
      final String trimmed, final int start, final ParseState state) {
    if (start > 0 && trimmed.charAt(start - 1) == '.') {
      throw new IllegalArgumentException(msg("tui.path_parser.error.empty_segment"));
    }
    state.prepareIndexSegment();
    final int close = findClosingBracket(trimmed, start);
    final String inside = trimmed.substring(start + 1, close).trim();
    if (inside.isEmpty()) {
      ensureAppendIsFinal(trimmed, close);
      state.append = true;
      return close;
    }
    final int index = parseIndexValue(inside);
    validateIndexTerminator(trimmed, close);
    state.segments.add(ConfigEditor.PathSegment.index(index));
    return close;
  }

  private static int findClosingBracket(final String trimmed, final int start) {
    final int close = trimmed.indexOf(']', start);
    if (close < 0) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.missing_closing_bracket"));
    }
    return close;
  }

  private static void ensureAppendIsFinal(final String trimmed, final int close) {
    if (close != trimmed.length() - 1) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.append_must_be_final"));
    }
  }

  private static int parseIndexValue(final String inside) {
    final int index;
    try {
      index = Integer.parseInt(inside);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          msg("tui.path_parser.error.invalid_list_index", inside), e);
    }
    if (index < 0) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.list_index_non_negative"));
    }
    return index;
  }

  private static void validateIndexTerminator(final String trimmed, final int close) {
    if (close < trimmed.length() - 1 && trimmed.charAt(close + 1) != '.') {
      throw new IllegalArgumentException(msg("tui.path_parser.error.index_terminator"));
    }
  }

  private static void validateParsedSegments(final ParseState state) {
    if (state.segments.isEmpty()) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.path_required"));
    }
    if (state.append && state.segments.get(state.segments.size() - 1).isIndex()) {
      throw new IllegalArgumentException(msg("tui.path_parser.error.append_requires_key"));
    }
  }

  private static final class ParseState {

    private final List<ConfigEditor.PathSegment> segments = new ArrayList<>();

    private String key = "";

    private boolean append;

    private void appendKeyChar(final char c) {
      key += c;
    }

    private void flushKeyIfPresent() {
      if (!key.isEmpty()) {
        segments.add(ConfigEditor.PathSegment.key(key));
        key = "";
      }
    }

    private void flushKeyOrThrowEmptySegment() {
      if (!key.isEmpty()) {
        flushKeyIfPresent();
        return;
      }
      if (segments.isEmpty() || segments.get(segments.size() - 1).isKey()) {
        throw new IllegalArgumentException(msg("tui.path_parser.error.empty_segment"));
      }
    }

    private void prepareIndexSegment() {
      if (!key.isEmpty()) {
        flushKeyIfPresent();
        return;
      }
      if (segments.isEmpty() || segments.get(segments.size() - 1).isIndex()) {
        throw new IllegalArgumentException(msg("tui.path_parser.error.index_requires_key"));
      }
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
