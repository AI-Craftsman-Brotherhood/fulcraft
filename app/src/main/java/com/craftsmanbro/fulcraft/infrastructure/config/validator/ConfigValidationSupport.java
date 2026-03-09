package com.craftsmanbro.fulcraft.infrastructure.config.validator;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigValidationSupport {

  private ConfigValidationSupport() {}

  public static void addUnknownKeys(
      final Map<?, ?> section,
      final String sectionName,
      final Set<String> allowed,
      final List<String> errors) {
    final List<String> unknownKeys = new ArrayList<>();
    boolean nonStringKeyFound = false;
    for (final Object keyObj : section.keySet()) {
      if (keyObj != null) {
        if (keyObj instanceof String key) {
          if (!allowed.contains(key)) {
            unknownKeys.add(key);
          }
        } else {
          nonStringKeyFound = true;
        }
      }
    }
    if (nonStringKeyFound) {
      errors.add("Keys in '" + sectionName + "' must be strings.");
    }
    if (!unknownKeys.isEmpty()) {
      Collections.sort(unknownKeys);
      errors.add(
          "Unknown keys in '"
              + sectionName
              + "': "
              + String.join(", ", unknownKeys)
              + ". See config.example.json for supported fields.");
    }
  }

  public static void warnIfExceedsMax(
      final Map<?, ?> section, final String key, final String sectionName, final int max) {
    final Object value = section.get(key);
    if (value == null) {
      return;
    }
    final Integer intValue = coerceInteger(value);
    if (intValue != null && intValue > max) {
      Logger.warn(
          String.format(
              "'%s.%s' is set to %d which exceeds recommended maximum of %d. "
                  + "High values may cause excessive LLM API calls and slow execution.",
              sectionName, key, intValue, max));
    }
  }

  public static Integer coerceInteger(final Object value) {
    if (value instanceof Integer intValue) {
      return intValue;
    }
    if (value instanceof Long longValue) {
      final long l = longValue;
      if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
        return null;
      }
      return (int) l;
    }
    if (value instanceof Short shortValue) {
      return (int) shortValue.shortValue();
    }
    if (value instanceof Byte byteValue) {
      return (int) byteValue.byteValue();
    }
    if (value instanceof Number numberValue) {
      final double doubleValue = numberValue.doubleValue();
      if (doubleValue < Integer.MIN_VALUE || doubleValue > Integer.MAX_VALUE) {
        return null;
      }
      if (Math.floor(doubleValue) == doubleValue) {
        return (int) doubleValue;
      }
    }
    return null;
  }
}
