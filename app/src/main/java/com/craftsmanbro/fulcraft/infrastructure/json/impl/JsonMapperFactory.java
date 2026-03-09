package com.craftsmanbro.fulcraft.infrastructure.json.impl;

import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonMapperPort;
import com.craftsmanbro.fulcraft.infrastructure.json.model.JsonMapperProfile;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * Factory for creating pre-configured ObjectMapper instances with deterministic serialization
 * settings.
 *
 * <p>This factory ensures consistent JSON output across all components by enabling key ordering and
 * other deterministic features.
 */
public final class JsonMapperFactory implements JsonMapperPort {

  private static final JsonMapperFactory INSTANCE = new JsonMapperFactory();

  private JsonMapperFactory() {
    // Utility class
  }

  public static JsonMapperPort port() {
    return INSTANCE;
  }

  /**
   * Creates an ObjectMapper with deterministic serialization settings.
   *
   * <p>The mapper is configured to:
   *
   * <ul>
   *   <li>Sort map entries by keys for consistent ordering
   *   <li>Sort POJO properties alphabetically for deterministic output
   * </ul>
   *
   * @return A new ObjectMapper instance
   */
  public static ObjectMapper create() {
    return INSTANCE.createCompact();
  }

  /**
   * Creates an ObjectMapper with deterministic serialization settings and pretty printing enabled.
   *
   * <p>The mapper is configured to:
   *
   * <ul>
   *   <li>Sort map entries by keys for consistent ordering
   *   <li>Indent output for readability
   * </ul>
   *
   * @return A new ObjectMapper instance with pretty printing
   */
  public static ObjectMapper createPrettyPrinter() {
    return INSTANCE.createPretty();
  }

  @Override
  public ObjectMapper create(final JsonMapperProfile profile) {
    final JsonMapperProfile effectiveProfile =
        profile == null ? JsonMapperProfile.deterministicCompact() : profile;
    return tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
        .configure(
            SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, effectiveProfile.sortMapEntriesByKeys())
        .configure(
            MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,
            effectiveProfile.sortPropertiesAlphabetically())
        .configure(SerializationFeature.INDENT_OUTPUT, effectiveProfile.prettyPrint())
        .build();
  }
}
