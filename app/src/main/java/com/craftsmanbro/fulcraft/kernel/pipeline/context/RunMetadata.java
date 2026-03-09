package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Arbitrary metadata captured during a pipeline run. */
public final class RunMetadata {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "kernel.common.error.argument_null";

  private final Map<String, Object> metadataByKey = new LinkedHashMap<>();

  public Map<String, Object> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(metadataByKey));
  }

  public void put(final String key, final Object value) {
    metadataByKey.put(
        Objects.requireNonNull(key, argumentNullMessage("key")),
        Objects.requireNonNull(value, argumentNullMessage("value")));
  }

  public void remove(final String key) {
    Objects.requireNonNull(key, argumentNullMessage("key"));
    metadataByKey.remove(key);
  }

  public <T> Optional<T> get(final String key, final Class<T> type) {
    final Object value = rawValue(key);
    Objects.requireNonNull(type, argumentNullMessage("type"));
    if (value == null) {
      return Optional.empty();
    }
    if (type.isInstance(value)) {
      return Optional.of(type.cast(value));
    }
    return Optional.empty();
  }

  Object rawValue(final String key) {
    Objects.requireNonNull(key, argumentNullMessage("key"));
    return metadataByKey.get(key);
  }

  private static String argumentNullMessage(final String argumentName) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentName);
  }
}
