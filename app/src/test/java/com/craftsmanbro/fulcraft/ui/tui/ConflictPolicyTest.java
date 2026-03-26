package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConflictPolicy}. */
class ConflictPolicyTest {

  @Nested
  @DisplayName("Enum values")
  class EnumValuesTests {

    @Test
    @DisplayName("Should have exactly three values")
    void shouldHaveThreeValues() {
      assertEquals(3, ConflictPolicy.values().length);
    }

    @Test
    @DisplayName("Should contain SAFE, SKIP, and OVERWRITE")
    void shouldContainExpectedValues() {
      ConflictPolicy[] values = ConflictPolicy.values();
      assertEquals(ConflictPolicy.SAFE, values[0]);
      assertEquals(ConflictPolicy.SKIP, values[1]);
      assertEquals(ConflictPolicy.OVERWRITE, values[2]);
    }
  }

  @Nested
  @DisplayName("Display names")
  class DisplayNameTests {

    @Test
    @DisplayName("SAFE should have correct display name")
    void safeShouldHaveDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.safe.name"),
          ConflictPolicy.SAFE.getDisplayName());
    }

    @Test
    @DisplayName("SKIP should have correct display name")
    void skipShouldHaveDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.skip.name"),
          ConflictPolicy.SKIP.getDisplayName());
    }

    @Test
    @DisplayName("OVERWRITE should have correct display name")
    void overwriteShouldHaveDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.overwrite.name"),
          ConflictPolicy.OVERWRITE.getDisplayName());
    }
  }

  @Nested
  @DisplayName("Descriptions")
  class DescriptionTests {

    @Test
    @DisplayName("Each policy should have a non-null description")
    void eachPolicyShouldHaveDescription() {
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.safe.desc"),
          ConflictPolicy.SAFE.getDescription());
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.skip.desc"),
          ConflictPolicy.SKIP.getDescription());
      assertEquals(
          MessageSource.getMessage("tui.conflict_policy.overwrite.desc"),
          ConflictPolicy.OVERWRITE.getDescription());
    }
  }
}
