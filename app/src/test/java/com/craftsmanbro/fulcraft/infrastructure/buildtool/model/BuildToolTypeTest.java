package com.craftsmanbro.fulcraft.infrastructure.buildtool.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BuildToolType} enum.
 *
 * <p>Verifies that all expected enum values exist.
 */
class BuildToolTypeTest {

  @Test
  void enum_hasGradleValue() {
    assertNotNull(BuildToolType.GRADLE);
    assertEquals("GRADLE", BuildToolType.GRADLE.name());
  }

  @Test
  void enum_hasMavenValue() {
    assertNotNull(BuildToolType.MAVEN);
    assertEquals("MAVEN", BuildToolType.MAVEN.name());
  }

  @Test
  void enum_hasUnknownValue() {
    assertNotNull(BuildToolType.UNKNOWN);
    assertEquals("UNKNOWN", BuildToolType.UNKNOWN.name());
  }

  @Test
  void enum_hasExactlyThreeValues() {
    assertEquals(3, BuildToolType.values().length);
  }

  @Test
  void valueOf_returnsCorrectValue() {
    assertEquals(BuildToolType.GRADLE, BuildToolType.valueOf("GRADLE"));
    assertEquals(BuildToolType.MAVEN, BuildToolType.valueOf("MAVEN"));
    assertEquals(BuildToolType.UNKNOWN, BuildToolType.valueOf("UNKNOWN"));
  }

  @Test
  void valueOf_withUnknownName_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> BuildToolType.valueOf("ANT"));
  }
}
