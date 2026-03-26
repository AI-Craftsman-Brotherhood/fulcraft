package com.craftsmanbro.fulcraft.infrastructure.json.model;

/** Immutable configuration profile for ObjectMapper creation in JSON infrastructure. */
public record JsonMapperProfile(
    boolean sortMapEntriesByKeys, boolean sortPropertiesAlphabetically, boolean prettyPrint) {

  public static JsonMapperProfile deterministicCompact() {
    return new JsonMapperProfile(true, true, false);
  }

  public static JsonMapperProfile deterministicPretty() {
    return new JsonMapperProfile(true, true, true);
  }
}
