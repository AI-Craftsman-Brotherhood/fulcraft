package com.craftsmanbro.fulcraft.infrastructure.json.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for canonicalizing data structures before JSON serialization.
 *
 * <p>This ensures deterministic JSON output by:
 *
 * <ul>
 *   <li>Sorting Map keys in natural order
 *   <li>Converting Sets to sorted Lists
 *   <li>Recursively processing nested structures
 * </ul>
 */
public final class JsonCanonicalizer {

  private JsonCanonicalizer() {
    // Utility class
  }

  /**
   * Canonicalizes an object for deterministic JSON serialization. Maps have their keys sorted, Sets
   * are converted to sorted Lists, and nested structures are processed recursively.
   *
   * @param obj The object to canonicalize
   * @return The canonicalized object
   */
  public static Object canonicalize(final Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof Map<?, ?> map) {
      return canonicalizeMap(map);
    }
    if (obj instanceof Set<?> set) {
      return canonicalizeSet(set);
    }
    if (obj instanceof List<?> list) {
      return canonicalizeList(list);
    }
    if (obj instanceof Collection<?> collection) {
      // Other collections: convert to List and canonicalize
      return canonicalizeList(new ArrayList<>(collection));
    }
    // Primitives, Strings, Numbers, etc. - return as-is
    return obj;
  }

  /**
   * Canonicalizes a Map by sorting its keys deterministically. Keys with the same runtime type use
   * natural ordering when available; otherwise, ordering falls back to key type name and string
   * representation.
   *
   * @param map The map to canonicalize
   * @param <K> Key type
   * @return A new LinkedHashMap with sorted keys and canonicalized values
   */
  public static <K> Map<K, Object> canonicalizeMap(final Map<K, ?> map) {
    if (map == null) {
      return Map.of();
    }
    final List<Map.Entry<K, ?>> entries = new ArrayList<>(map.entrySet());
    entries.sort((e1, e2) -> compareKeys(e1.getKey(), e2.getKey()));
    final Map<K, Object> sortedMap = new LinkedHashMap<>();
    for (final Map.Entry<K, ?> entry : entries) {
      sortedMap.put(entry.getKey(), canonicalize(entry.getValue()));
    }
    return sortedMap;
  }

  private static int compareKeys(final Object k1, final Object k2) {
    if (Objects.equals(k1, k2)) {
      return 0;
    }
    if (k1 == null) {
      return -1;
    }
    if (k2 == null) {
      return 1;
    }
    if (k1.getClass() == k2.getClass() && k1 instanceof Comparable<?>) {
      final int result = compareComparableKeys(k1, k2);
      if (result != 0) {
        return result;
      }
    }
    final int classCompare = k1.getClass().getName().compareTo(k2.getClass().getName());
    if (classCompare != 0) {
      return classCompare;
    }
    return String.valueOf(k1).compareTo(String.valueOf(k2));
  }

  @SuppressWarnings("unchecked")
  private static int compareComparableKeys(final Object k1, final Object k2) {
    return ((Comparable<Object>) k1).compareTo(k2);
  }

  /**
   * Canonicalizes a Set by converting it to a sorted List. Elements are sorted using their string
   * representation for consistent ordering.
   *
   * @param set The set to canonicalize
   * @param <T> Element type
   * @return A sorted List containing the canonicalized elements
   */
  public static <T> List<Object> canonicalizeSet(final Set<T> set) {
    if (set == null) {
      return List.of();
    }
    final List<Object> list = new ArrayList<>();
    for (final T element : set) {
      list.add(canonicalize(element));
    }
    // Sort by string representation
    list.sort(
        (o1, o2) -> {
          final String s1 = String.valueOf(o1);
          final String s2 = String.valueOf(o2);
          return s1.compareTo(s2);
        });
    return list;
  }

  /**
   * Canonicalizes a List by recursively canonicalizing its elements. The list order is preserved
   * (not sorted) since list order may be semantically significant.
   *
   * @param list The list to canonicalize
   * @param <T> Element type
   * @return A new List with canonicalized elements
   */
  public static <T> List<Object> canonicalizeList(final List<T> list) {
    if (list == null) {
      return List.of();
    }
    final List<Object> result = new ArrayList<>(list.size());
    for (final T element : list) {
      result.add(canonicalize(element));
    }
    return result;
  }

  /**
   * Canonicalizes a List and optionally sorts it.
   *
   * <p>Use this when the list represents a set (unordered collection) that was stored as a List.
   *
   * @param list The list to canonicalize
   * @param sort Whether to sort the list
   * @param <T> Element type
   * @return A new List with canonicalized elements, sorted if requested
   */
  public static <T> List<Object> canonicalizeList(final List<T> list, final boolean sort) {
    final List<Object> result = canonicalizeList(list);
    if (result != null && sort) {
      result.sort(
          (o1, o2) -> {
            final String s1 = String.valueOf(o1);
            final String s2 = String.valueOf(o2);
            return s1.compareTo(s2);
          });
    }
    return result;
  }
}
