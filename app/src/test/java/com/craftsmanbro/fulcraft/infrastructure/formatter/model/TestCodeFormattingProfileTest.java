package com.craftsmanbro.fulcraft.infrastructure.formatter.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestCodeFormattingProfileTest {

  @Test
  void deterministicDefaultsEnablesAllDeterministicFlags() {
    TestCodeFormattingProfile profile = TestCodeFormattingProfile.deterministicDefaults();

    assertTrue(profile.sortImports());
    assertTrue(profile.sortMembers());
    assertTrue(profile.normalizeEmptyBraces());
    assertTrue(profile.keepEmptyRecordCompactBraces());
    assertTrue(profile.ensureTrailingNewline());
  }
}
