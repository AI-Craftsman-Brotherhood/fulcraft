package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link IssueHandlingOption}. */
class IssueHandlingOptionTest {

  @Nested
  @DisplayName("getKeyNumber")
  class GetKeyNumberTests {

    @Test
    @DisplayName("SAFE_FIX is key 1")
    void safeFix_keyNumber() {
      assertEquals(1, IssueHandlingOption.SAFE_FIX.getKeyNumber());
    }

    @Test
    @DisplayName("PROPOSE_ONLY is key 2")
    void proposeOnly_keyNumber() {
      assertEquals(2, IssueHandlingOption.PROPOSE_ONLY.getKeyNumber());
    }

    @Test
    @DisplayName("SKIP is key 3")
    void skip_keyNumber() {
      assertEquals(3, IssueHandlingOption.SKIP.getKeyNumber());
    }
  }

  @Nested
  @DisplayName("getDisplayName")
  class GetDisplayNameTests {

    @Test
    @DisplayName("SAFE_FIX has correct display name")
    void safeFix_displayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.safe_fix.name"),
          IssueHandlingOption.SAFE_FIX.getDisplayName());
    }

    @Test
    @DisplayName("PROPOSE_ONLY has correct display name")
    void proposeOnly_displayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.propose_only.name"),
          IssueHandlingOption.PROPOSE_ONLY.getDisplayName());
    }

    @Test
    @DisplayName("SKIP has correct display name")
    void skip_displayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.skip.name"),
          IssueHandlingOption.SKIP.getDisplayName());
    }
  }

  @Nested
  @DisplayName("getDescription")
  class GetDescriptionTests {

    @Test
    @DisplayName("SAFE_FIX has correct description")
    void safeFix_description() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.safe_fix.desc"),
          IssueHandlingOption.SAFE_FIX.getDescription());
    }

    @Test
    @DisplayName("PROPOSE_ONLY has correct description")
    void proposeOnly_description() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.propose_only.desc"),
          IssueHandlingOption.PROPOSE_ONLY.getDescription());
    }

    @Test
    @DisplayName("SKIP has correct description")
    void skip_description() {
      assertEquals(
          MessageSource.getMessage("tui.issue_option.skip.desc"),
          IssueHandlingOption.SKIP.getDescription());
    }
  }

  @Nested
  @DisplayName("fromKeyNumber")
  class FromKeyNumberTests {

    @Test
    @DisplayName("returns SAFE_FIX for key 1")
    void key1_returnsSafeFix() {
      assertEquals(IssueHandlingOption.SAFE_FIX, IssueHandlingOption.fromKeyNumber(1));
    }

    @Test
    @DisplayName("returns PROPOSE_ONLY for key 2")
    void key2_returnsProposeOnly() {
      assertEquals(IssueHandlingOption.PROPOSE_ONLY, IssueHandlingOption.fromKeyNumber(2));
    }

    @Test
    @DisplayName("returns SKIP for key 3")
    void key3_returnsSkip() {
      assertEquals(IssueHandlingOption.SKIP, IssueHandlingOption.fromKeyNumber(3));
    }

    @Test
    @DisplayName("returns null for invalid key")
    void invalidKey_returnsNull() {
      assertNull(IssueHandlingOption.fromKeyNumber(0));
      assertNull(IssueHandlingOption.fromKeyNumber(4));
      assertNull(IssueHandlingOption.fromKeyNumber(-1));
    }
  }

  @Test
  @DisplayName("all options are defined")
  void allOptionsDefined() {
    assertEquals(3, IssueHandlingOption.values().length);
  }
}
