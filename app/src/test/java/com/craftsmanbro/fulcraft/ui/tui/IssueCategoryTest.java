package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link IssueCategory}. */
class IssueCategoryTest {

  @Nested
  @DisplayName("getDisplayName")
  class GetDisplayNameTests {

    @Test
    @DisplayName("EXCEPTION has correct display name")
    void exceptionDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.exception.name"),
          IssueCategory.EXCEPTION.getDisplayName());
    }

    @Test
    @DisplayName("TEST_FAILURE has correct display name")
    void testFailureDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.test_failure.name"),
          IssueCategory.TEST_FAILURE.getDisplayName());
    }

    @Test
    @DisplayName("COMPILE_ERROR has correct display name")
    void compileErrorDisplayName() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.compile_error.name"),
          IssueCategory.COMPILE_ERROR.getDisplayName());
    }
  }

  @Nested
  @DisplayName("getDescription")
  class GetDescriptionTests {

    @Test
    @DisplayName("EXCEPTION has correct description")
    void exceptionDescription() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.exception.desc"),
          IssueCategory.EXCEPTION.getDescription());
    }

    @Test
    @DisplayName("TEST_FAILURE has correct description")
    void testFailureDescription() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.test_failure.desc"),
          IssueCategory.TEST_FAILURE.getDescription());
    }

    @Test
    @DisplayName("COMPILE_ERROR has correct description")
    void compileErrorDescription() {
      assertEquals(
          MessageSource.getMessage("tui.issue_category.compile_error.desc"),
          IssueCategory.COMPILE_ERROR.getDescription());
    }
  }

  @Test
  @DisplayName("all categories are defined")
  void allCategoriesDefined() {
    assertEquals(3, IssueCategory.values().length);
  }
}
