package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonicalizes and validates ordered-list structure used in generated method sections. */
public final class LlmListStructureNormalizer {

  private static final Pattern ORDERED_LIST_LINE_PATTERN =
      Pattern.compile("^(\\s*)(?:-\\s*)?(\\d+)\\.\\s+(.+?)\\s*$");

  public String normalizeOrderedListLines(final String text) {
    if (text == null || text.isBlank()) {
      return text == null ? "" : text;
    }
    final List<String> normalized = new ArrayList<>();
    OrderedBlockState blockState = OrderedBlockState.empty();
    for (final String rawLine : text.split("\\R", -1)) {
      final String line = rawLine == null ? "" : rawLine;
      if (line.isBlank()) {
        normalized.add(line);
        blockState = OrderedBlockState.empty();
        continue;
      }
      final Matcher orderedMatcher = ORDERED_LIST_LINE_PATTERN.matcher(line);
      if (orderedMatcher.matches()) {
        final String indent = orderedMatcher.group(1) == null ? "" : orderedMatcher.group(1);
        final String body = orderedMatcher.group(3).strip();
        if (blockState.isInactive() || !blockState.isSameIndent(indent)) {
          blockState = OrderedBlockState.start(indent);
        }
        normalized.add(indent + blockState.nextOrder() + ". " + body);
        blockState = blockState.advance();
        continue;
      }
      if (blockState.isContinuationLine(line)) {
        normalized.add(line.stripTrailing());
        continue;
      }
      normalized.add(line.stripTrailing());
      blockState = OrderedBlockState.empty();
    }
    return String.join("\n", normalized);
  }

  public boolean hasNonCanonicalOrderedList(final String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return !normalizeOrderedListLines(text).equals(text);
  }

  private record OrderedBlockState(String indent, int nextOrder) {

    private static OrderedBlockState empty() {
      return new OrderedBlockState(null, 1);
    }

    private static OrderedBlockState start(final String indent) {
      return new OrderedBlockState(indent == null ? "" : indent, 1);
    }

    private boolean isInactive() {
      return indent == null;
    }

    private boolean isSameIndent(final String otherIndent) {
      if (indent == null) {
        return false;
      }
      final String current = indent;
      final String other = otherIndent == null ? "" : otherIndent;
      return current.equals(other);
    }

    private OrderedBlockState advance() {
      if (indent == null) {
        return this;
      }
      return new OrderedBlockState(indent, nextOrder + 1);
    }

    private boolean isContinuationLine(final String line) {
      if (indent == null || line == null || line.isBlank()) {
        return false;
      }
      final int currentIndent = countLeadingWhitespace(line);
      final int baseIndent = countLeadingWhitespace(indent);
      return currentIndent > baseIndent;
    }

    private int countLeadingWhitespace(final String value) {
      if (value == null || value.isEmpty()) {
        return 0;
      }
      int index = 0;
      while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
        index++;
      }
      return index;
    }
  }
}
