package com.craftsmanbro.fulcraft.plugins.document.core.util;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Utility class for canonicalizing document inputs to ensure deterministic output. */
public final class PromptInputCanonicalizer {

  private PromptInputCanonicalizer() {
    // Utility class
  }

  /** Sorts a list of strings in natural (case-sensitive) order. */
  public static List<String> sortStrings(final List<String> items) {
    if (items == null || items.isEmpty()) {
      return new ArrayList<>();
    }
    final var sorted = new ArrayList<String>(items.size());
    for (final var item : items) {
      if (item != null) {
        sorted.add(item);
      }
    }
    Collections.sort(sorted);
    return sorted;
  }

  /** Sorts a collection of strings in natural (case-sensitive) order. */
  public static List<String> sortStrings(final Collection<String> items) {
    if (items == null || items.isEmpty()) {
      return new ArrayList<>();
    }
    final var sorted = new ArrayList<String>(items.size());
    for (final var item : items) {
      if (item != null) {
        sorted.add(item);
      }
    }
    Collections.sort(sorted);
    return sorted;
  }

  /** Sorts methods by signature for deterministic ordering. */
  public static List<MethodInfo> sortMethods(final List<MethodInfo> methods) {
    if (methods == null || methods.isEmpty()) {
      return new ArrayList<>();
    }
    final var sorted = new ArrayList<MethodInfo>(methods.size());
    for (final var method : methods) {
      if (method != null) {
        sorted.add(method);
      }
    }
    sorted.sort(
        Comparator.comparing((MethodInfo m) -> m.getSignature() != null ? m.getSignature() : "")
            .thenComparing(m -> m.getName() != null ? m.getName() : ""));
    return sorted;
  }

  /** Joins a list of strings with the specified delimiter after sorting. */
  public static String sortAndJoin(final List<String> items, final String delimiter) {
    return String.join(delimiter, sortStrings(items));
  }

  /** Joins a collection of strings with the specified delimiter after sorting. */
  public static String sortAndJoin(final Collection<String> items, final String delimiter) {
    return String.join(delimiter, sortStrings(items));
  }
}
