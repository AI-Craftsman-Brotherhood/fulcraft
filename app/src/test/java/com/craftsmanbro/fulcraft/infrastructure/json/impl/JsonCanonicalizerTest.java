package com.craftsmanbro.fulcraft.infrastructure.json.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonCanonicalizerTest {

  @Test
  void canonicalize_returnsNullForNullInput() {
    assertNull(JsonCanonicalizer.canonicalize(null));
  }

  @Test
  void canonicalize_returnsSameObjectForNonCollectionValue() {
    Object marker = new Object();

    assertSame(marker, JsonCanonicalizer.canonicalize(marker));
  }

  @Test
  void canonicalizeMap_returnsEmptyMapForNullInput() {
    assertTrue(JsonCanonicalizer.canonicalizeMap(null).isEmpty());
  }

  @Test
  void canonicalizeMap_sortsKeysAndCanonicalizesNestedStructures() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("z", 2);
    nestedMap.put("a", 1);

    Set<String> nestedSet = new LinkedHashSet<>(List.of("b", "a"));

    Map<String, Object> valueMap = new HashMap<>();
    valueMap.put("set", nestedSet);
    valueMap.put("map", nestedMap);

    Map<String, Object> input = new HashMap<>();
    input.put("b", valueMap);
    input.put("a", List.of(Set.of("y", "x")));

    Map<String, Object> result = JsonCanonicalizer.canonicalizeMap(input);

    assertEquals(List.of("a", "b"), new ArrayList<>(result.keySet()));

    List<?> topLevelList = (List<?>) result.get("a");
    assertEquals(List.of(List.of("x", "y")), topLevelList);

    Map<?, ?> canonicalizedValueMap = (Map<?, ?>) result.get("b");
    assertEquals(List.of("map", "set"), new ArrayList<>(canonicalizedValueMap.keySet()));

    Map<?, ?> canonicalizedNestedMap = (Map<?, ?>) canonicalizedValueMap.get("map");
    assertEquals(List.of("a", "z"), new ArrayList<>(canonicalizedNestedMap.keySet()));

    assertEquals(List.of("a", "b"), canonicalizedValueMap.get("set"));
  }

  @Test
  void canonicalizeMap_ordersMixedKeyTypesDeterministically() {
    Map<Object, String> input = new LinkedHashMap<>();
    input.put("10", "string");
    input.put(2, "integer");
    input.put(null, "null");

    Map<Object, Object> result = JsonCanonicalizer.canonicalizeMap(input);

    assertEquals(Arrays.asList(null, 2, "10"), new ArrayList<>(result.keySet()));
  }

  @Test
  void canonicalizeMap_usesNaturalOrderingForComparableKeysOfSameType() {
    Map<Integer, String> input = new LinkedHashMap<>();
    input.put(10, "ten");
    input.put(2, "two");

    Map<Integer, Object> result = JsonCanonicalizer.canonicalizeMap(input);

    assertEquals(List.of(2, 10), new ArrayList<>(result.keySet()));
  }

  @Test
  void canonicalizeMap_usesToStringForNonComparableKeys() {
    Map<NonComparableKey, String> input = new LinkedHashMap<>();
    input.put(new NonComparableKey("b"), "second");
    input.put(new NonComparableKey("a"), "first");

    Map<NonComparableKey, Object> result = JsonCanonicalizer.canonicalizeMap(input);
    List<String> sortedKeyNames = result.keySet().stream().map(String::valueOf).toList();

    assertEquals(List.of("a", "b"), sortedKeyNames);
  }

  @Test
  void canonicalizeMap_preservesNullValues() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("a", null);
    input.put("b", "value");

    Map<String, Object> result = JsonCanonicalizer.canonicalizeMap(input);

    assertTrue(result.containsKey("a"));
    assertNull(result.get("a"));
    assertEquals("value", result.get("b"));
  }

  @Test
  void canonicalizeMap_returnsEmptyMapForEmptyInput() {
    Map<String, Object> result = JsonCanonicalizer.canonicalizeMap(new HashMap<>());

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void canonicalizeSet_convertsElementsAndSortsByStringValue() {
    Set<Integer> input = new LinkedHashSet<>(List.of(2, 10, 1));

    List<Object> result = JsonCanonicalizer.canonicalizeSet(input);

    assertEquals(List.of(1, 10, 2), result);
  }

  @Test
  void canonicalizeSet_returnsEmptyListForEmptySet() {
    List<Object> result = JsonCanonicalizer.canonicalizeSet(new LinkedHashSet<>());

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void canonicalizeList_preservesOrderWhileCanonicalizingElements() {
    List<Object> input = new ArrayList<>();
    input.add(Set.of("b", "a"));
    input.add(Map.of("b", 2, "a", 1));

    List<Object> result = JsonCanonicalizer.canonicalizeList(input);

    assertEquals(List.of("a", "b"), result.get(0));

    Map<?, ?> canonicalizedMap = (Map<?, ?>) result.get(1);
    assertEquals(List.of("a", "b"), new ArrayList<>(canonicalizedMap.keySet()));
  }

  @Test
  void canonicalizeList_withSortFlagSortsByStringRepresentation() {
    List<Integer> input = List.of(2, 10, 1);

    List<Object> result = JsonCanonicalizer.canonicalizeList(input, true);

    assertEquals(List.of(1, 10, 2), result);
  }

  @Test
  void canonicalizeList_returnsEmptyListForEmptyInput() {
    List<Object> result = JsonCanonicalizer.canonicalizeList(new ArrayList<>());

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void canonicalize_convertsOtherCollectionsToCanonicalizedList() {
    Collection<Object> input = new ArrayDeque<>();
    input.add(Set.of("b", "a"));
    input.add(Map.of("b", 2, "a", 1));

    Object result = JsonCanonicalizer.canonicalize(input);
    List<?> canonicalized = assertInstanceOf(List.class, result);

    assertEquals(List.of("a", "b"), canonicalized.get(0));

    Map<?, ?> canonicalizedMap = (Map<?, ?>) canonicalized.get(1);
    assertEquals(List.of("a", "b"), new ArrayList<>(canonicalizedMap.keySet()));
  }

  private static final class NonComparableKey {
    private final String value;

    NonComparableKey(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
