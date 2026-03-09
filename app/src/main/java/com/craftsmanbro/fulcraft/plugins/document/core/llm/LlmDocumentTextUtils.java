package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared text normalization and token-matching rules for document adapters. */
public final class LlmDocumentTextUtils {

  private LlmDocumentTextUtils() {}

  public static boolean isNoneMarker(final String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    final String normalized = value.strip().toLowerCase(Locale.ROOT);
    return "なし".equals(normalized)
        || "none".equals(normalized)
        || "なし（宣言のみ）".equals(normalized)
        || "none (declaration only)".equals(normalized);
  }

  public static String normalizeLine(final String line) {
    if (line == null || line.isBlank()) {
      return "";
    }
    return line.toLowerCase(Locale.ROOT).replace("`", "").strip();
  }

  public static boolean containsMethodToken(final String text, final String methodName) {
    if (text == null || text.isBlank() || methodName == null || methodName.isBlank()) {
      return false;
    }
    final String normalizedText = text.toLowerCase(Locale.ROOT);
    final String normalizedMethod = methodName.toLowerCase(Locale.ROOT);
    final Pattern tokenPattern =
        Pattern.compile(
            "(^|[^a-z0-9_])" + Pattern.quote(normalizedMethod) + "([^a-z0-9_]|$)",
            Pattern.CASE_INSENSITIVE);
    return tokenPattern.matcher(normalizedText).find();
  }

  public static String extractMethodName(final String signatureOrReference) {
    if (signatureOrReference == null || signatureOrReference.isBlank()) {
      return "";
    }
    String text = signatureOrReference.strip().replace('$', '.');
    final int hashIndex = text.lastIndexOf('#');
    if (hashIndex >= 0 && hashIndex + 1 < text.length()) {
      text = text.substring(hashIndex + 1).trim();
    }
    final int openParen = text.indexOf('(');
    if (openParen > 0) {
      text = text.substring(0, openParen).trim();
    }
    final int spaceIndex = text.lastIndexOf(' ');
    if (spaceIndex >= 0 && spaceIndex + 1 < text.length()) {
      text = text.substring(spaceIndex + 1).trim();
    }
    final int dotIndex = text.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < text.length()) {
      text = text.substring(dotIndex + 1).trim();
    }
    return text;
  }

  public static String normalizeMethodName(final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return "";
    }
    String extracted = extractMethodName(methodName);
    if (extracted.isEmpty()) {
      extracted = methodName;
    }
    return extracted.replace("`", "").strip().toLowerCase(Locale.ROOT);
  }

  public static List<String> splitTopLevelCsv(final String value) {
    final List<String> tokens = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return tokens;
    }
    final StringBuilder current = new StringBuilder();
    int genericDepth = 0;
    int parenthesisDepth = 0;
    for (int i = 0; i < value.length(); i++) {
      final char ch = value.charAt(i);
      if (ch == '<') {
        genericDepth++;
      } else if (ch == '>' && genericDepth > 0) {
        genericDepth--;
      } else if (ch == '(') {
        parenthesisDepth++;
      } else if (ch == ')' && parenthesisDepth > 0) {
        parenthesisDepth--;
      } else if (ch == ',' && genericDepth == 0 && parenthesisDepth == 0) {
        tokens.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    if (!current.isEmpty()) {
      tokens.add(current.toString().trim());
    }
    return tokens;
  }

  public static String emptyIfNull(final String value) {
    return value == null ? "" : value;
  }
}
