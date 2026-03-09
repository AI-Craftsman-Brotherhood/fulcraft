package com.craftsmanbro.fulcraft.infrastructure.json.impl;

import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link JsonServicePort} backed by Jackson ObjectMapper.
 *
 * <p>This class is the only place in the application that directly uses Jackson API. All consumers
 * should interact through the {@link JsonServicePort} interface.
 */
public final class DefaultJsonService implements JsonServicePort {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF =
      new TypeReference<>() {};

  private final ObjectMapper prettyMapper;

  private final ObjectMapper compactMapper;

  private final ObjectMapper lenientMapper;

  /** Creates a new instance with standard deterministic settings. */
  public DefaultJsonService() {
    this.prettyMapper = JsonMapperFactory.createPrettyPrinter();
    this.compactMapper = JsonMapperFactory.create();
    this.lenientMapper =
        JsonMapperFactory.create()
            .rebuild()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
  }

  @Override
  public void writeToFile(final Path file, final Object value) throws IOException {
    try {
      prettyMapper.writeValue(file.toFile(), value);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to write JSON file: " + file),
          e);
    }
  }

  @Override
  public void writeToFileCompact(final Path file, final Object value) throws IOException {
    try {
      compactMapper.writeValue(file.toFile(), value);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to write compact JSON file: " + file),
          e);
    }
  }

  @Override
  public <T> T readFromFile(final Path file, final Class<T> type) throws IOException {
    try {
      return compactMapper.readValue(file.toFile(), type);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to read JSON file: " + file),
          e);
    }
  }

  @Override
  public LinkedHashMap<String, Object> readMapFromFile(final Path file) throws IOException {
    if (!Files.exists(file)) {
      return new LinkedHashMap<>();
    }
    try {
      return compactMapper.readValue(file.toFile(), MAP_TYPE_REF);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to read JSON map file: " + file),
          e);
    }
  }

  @Override
  public LinkedHashMap<String, Object> readMapFromString(final String json) throws IOException {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return compactMapper.readValue(json, MAP_TYPE_REF);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to read JSON map from string"),
          e);
    }
  }

  @Override
  public String toJson(final Object value) throws IOException {
    try {
      return compactMapper.writeValueAsString(value);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to serialize JSON"),
          e);
    }
  }

  @Override
  public String toJsonPretty(final Object value) throws IOException {
    try {
      return prettyMapper.writeValueAsString(value);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to serialize pretty JSON"),
          e);
    }
  }

  @Override
  public <T> T fromJson(final String json, final Class<T> type) throws IOException {
    try {
      return compactMapper.readValue(json, type);
    } catch (RuntimeException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to deserialize JSON"),
          e);
    }
  }

  @Override
  public <T> T convert(final Object source, final Class<T> type) {
    if (source == null) {
      return null;
    }
    if (type.isInstance(source)) {
      return type.cast(source);
    }
    try {
      return lenientMapper.convertValue(source, type);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
