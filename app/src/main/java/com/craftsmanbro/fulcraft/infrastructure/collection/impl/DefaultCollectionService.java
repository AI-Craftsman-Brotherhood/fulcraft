package com.craftsmanbro.fulcraft.infrastructure.collection.impl;

import com.craftsmanbro.fulcraft.infrastructure.collection.contract.CollectionServicePort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default implementation of {@link CollectionServicePort}. */
public class DefaultCollectionService implements CollectionServicePort {

  @Override
  public Map<String, Object> toMap(final Object object) {
    if (!(object instanceof Map<?, ?> rawMap)) {
      return new LinkedHashMap<>();
    }
    final Map<String, Object> stringKeyedMap = new LinkedHashMap<>(rawMap.size());
    for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
      // Preserve only string-keyed entries for the typed result.
      if (entry.getKey() instanceof String key) {
        stringKeyedMap.put(key, entry.getValue());
      }
    }
    return stringKeyedMap;
  }

  @Override
  public Map<String, Object> toUnmodifiableMap(final Object object) {
    final Map<String, Object> convertedMap = toMap(object);
    if (convertedMap.isEmpty()) {
      return Map.of();
    }
    return Collections.unmodifiableMap(convertedMap);
  }

  @Override
  public List<Object> toList(final Object object) {
    if (!(object instanceof List<?> rawList)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(rawList);
  }

  @Override
  public List<Map<String, Object>> toMapList(final Object object) {
    if (!(object instanceof List<?> rawList)) {
      return new ArrayList<>();
    }
    final List<Map<String, Object>> mapElements = new ArrayList<>(rawList.size());
    for (final Object element : rawList) {
      if (element instanceof Map<?, ?> rawMap) {
        mapElements.add(toMap(rawMap));
      }
    }
    return mapElements;
  }
}
