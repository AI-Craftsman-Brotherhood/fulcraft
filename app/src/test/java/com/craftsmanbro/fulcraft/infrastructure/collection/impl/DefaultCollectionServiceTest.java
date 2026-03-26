package com.craftsmanbro.fulcraft.infrastructure.collection.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultCollectionServiceTest {

  private final DefaultCollectionService service = new DefaultCollectionService();

  @Test
  void toMap_copiesOnlyStringKeyedEntries() {
    Map<Object, Object> rawMap = new LinkedHashMap<>();
    rawMap.put("first", 1);
    rawMap.put(2, "ignored");
    rawMap.put("second", null);

    Map<String, Object> convertedMap = service.toMap(rawMap);

    assertEquals(2, convertedMap.size());
    assertEquals(1, convertedMap.get("first"));
    assertTrue(convertedMap.containsKey("second"));
    convertedMap.put("third", 3);
    assertFalse(rawMap.containsKey("third"));
  }

  @Test
  void toUnmodifiableMap_returnsReadOnlySnapshot() {
    Map<String, Object> rawMap = new LinkedHashMap<>();
    rawMap.put("name", "fulcraft");

    Map<String, Object> convertedMap = service.toUnmodifiableMap(rawMap);

    assertEquals("fulcraft", convertedMap.get("name"));
    assertThrows(UnsupportedOperationException.class, () -> convertedMap.put("other", "value"));
  }

  @Test
  void toList_returnsMutableCopy() {
    List<Object> rawList = new ArrayList<>(List.of("alpha", "beta"));

    List<Object> copiedList = service.toList(rawList);

    copiedList.add("gamma");
    assertEquals(List.of("alpha", "beta", "gamma"), copiedList);
    assertEquals(List.of("alpha", "beta"), rawList);
  }

  @Test
  void toMapList_skipsNonMapElements() {
    Map<Object, Object> firstMap = new LinkedHashMap<>();
    firstMap.put("first", 1);
    firstMap.put(99, "ignored");
    Map<Object, Object> secondMap = new LinkedHashMap<>();
    secondMap.put("second", 2);
    List<Object> rawList = List.of(firstMap, "skip", secondMap);

    List<Map<String, Object>> convertedList = service.toMapList(rawList);

    assertEquals(2, convertedList.size());
    assertEquals(Map.of("first", 1), convertedList.get(0));
    assertEquals(Map.of("second", 2), convertedList.get(1));
  }
}
