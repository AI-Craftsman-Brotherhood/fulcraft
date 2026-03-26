package com.craftsmanbro.fulcraft.infrastructure.json.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonMapperPort;
import com.craftsmanbro.fulcraft.infrastructure.json.model.JsonMapperProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

class JsonMapperFactoryTest {

  @Test
  void port_returnsSingleton() {
    JsonMapperPort first = JsonMapperFactory.port();
    JsonMapperPort second = JsonMapperFactory.port();

    assertSame(first, second);
  }

  @Test
  void create_enablesDeterministicSerializationFeatures() {
    ObjectMapper mapper = JsonMapperFactory.create();

    assertTrue(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    assertFalse(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
  }

  @Test
  void create_returnsNewInstanceEachTime() {
    ObjectMapper first = JsonMapperFactory.create();
    ObjectMapper second = JsonMapperFactory.create();

    assertNotSame(first, second);
  }

  @Test
  void create_serializesMapEntriesInSortedKeyOrder() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    Map<String, Integer> input = new LinkedHashMap<>();
    input.put("b", 2);
    input.put("a", 1);

    String json = mapper.writeValueAsString(input);

    assertTrue(json.indexOf("\"a\"") < json.indexOf("\"b\""));
  }

  @Test
  void create_serializesPojoPropertiesInAlphabeticalOrder() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();

    String json = mapper.writeValueAsString(new SamplePojo());

    assertTrue(json.indexOf("\"alpha\"") < json.indexOf("\"beta\""));
  }

  @Test
  void createPrettyPrinter_enablesIndentAndKeepsDeterministicFeatures() {
    ObjectMapper mapper = JsonMapperFactory.createPrettyPrinter();

    assertTrue(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
  }

  @Test
  void createPrettyPrinter_outputsPrettyPrintedAndSortedJson() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.createPrettyPrinter();
    Map<String, Integer> input = new LinkedHashMap<>();
    input.put("b", 2);
    input.put("a", 1);

    String json = mapper.writeValueAsString(input);

    assertTrue(json.contains("\n"));
    assertTrue(json.indexOf("\"a\"") < json.indexOf("\"b\""));
  }

  @Test
  void create_withExplicitProfile_respectsProfileFlags() {
    ObjectMapper mapper =
        JsonMapperFactory.port().create(new JsonMapperProfile(false, false, true));

    assertFalse(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    assertFalse(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
  }

  @Test
  void create_withNullProfile_fallsBackToDeterministicCompact() {
    ObjectMapper mapper = JsonMapperFactory.port().create(null);

    assertTrue(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    assertFalse(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
  }

  private static final class SamplePojo {
    private String alpha = "a";
    private String beta = "b";

    public String getBeta() {
      return beta;
    }

    public String getAlpha() {
      return alpha;
    }
  }
}
