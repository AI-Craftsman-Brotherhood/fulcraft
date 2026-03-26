package com.craftsmanbro.fulcraft.infrastructure.collection.contract;

import java.util.List;
import java.util.Map;

/**
 * Contract for runtime-validated collection conversion and shallow defensive copying.
 *
 * <p>When working with parsed JSON/YAML data held as {@code Object}, callers often need typed
 * collections such as {@code Map<String, Object>} or {@code List<Object>}. This contract provides
 * runtime-validated conversions without unchecked casts.
 *
 * <p>These conversion methods return shallow defensive copies. Mutating the returned collections
 * does not mutate the original structure, but nested values are still shared with the source
 * object. Callers that need in-place updates should operate on the original reference directly.
 */
public interface CollectionServicePort {

  /**
   * Converts the given object to a mutable {@code Map<String, Object>} if it is a {@link Map};
   * otherwise returns an empty map.
   *
   * <p>Only entries whose keys are {@link String}s are copied, which keeps the returned map
   * string-keyed for callers. Other entries are ignored.
   *
   * @param object the object to convert
   * @return a mutable map containing string-keyed entries, or an empty map
   */
  Map<String, Object> toMap(Object object);

  /**
   * Returns an unmodifiable {@code Map<String, Object>} created from {@link #toMap(Object)}.
   *
   * @param object the object to convert
   * @return an unmodifiable map containing string-keyed entries, or an empty map
   */
  Map<String, Object> toUnmodifiableMap(Object object);

  /**
   * Converts the given object to a mutable {@code List<Object>} if it is a {@link List}; otherwise
   * returns an empty list.
   *
   * @param object the object to convert
   * @return a mutable list with the same elements, or an empty list
   */
  List<Object> toList(Object object);

  /**
   * Converts the given object to a mutable {@code List<Map<String, Object>>} if it is a {@link
   * List}; otherwise returns an empty list.
   *
   * <p>Each map element is converted via {@link #toMap(Object)}, so only string-keyed entries are
   * retained. Non-map elements are ignored.
   *
   * @param object the object to convert
   * @return a mutable list of string-keyed maps, or an empty list
   */
  List<Map<String, Object>> toMapList(Object object);
}
