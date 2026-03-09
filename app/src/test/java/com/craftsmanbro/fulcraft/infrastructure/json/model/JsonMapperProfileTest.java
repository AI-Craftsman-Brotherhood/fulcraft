package com.craftsmanbro.fulcraft.infrastructure.json.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonMapperProfileTest {

  @Test
  void deterministicCompact_disablesPrettyPrintAndKeepsDeterminism() {
    JsonMapperProfile profile = JsonMapperProfile.deterministicCompact();

    assertTrue(profile.sortMapEntriesByKeys());
    assertTrue(profile.sortPropertiesAlphabetically());
    assertFalse(profile.prettyPrint());
  }

  @Test
  void deterministicPretty_enablesPrettyPrintAndKeepsDeterminism() {
    JsonMapperProfile profile = JsonMapperProfile.deterministicPretty();

    assertTrue(profile.sortMapEntriesByKeys());
    assertTrue(profile.sortPropertiesAlphabetically());
    assertTrue(profile.prettyPrint());
  }
}
