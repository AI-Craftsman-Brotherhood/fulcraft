package com.craftsmanbro.fulcraft.infrastructure.formatter.model;

/**
 * Immutable profile that controls deterministic formatting behavior for generated Java test code.
 */
public record TestCodeFormattingProfile(
    boolean sortImports,
    boolean sortMembers,
    boolean normalizeEmptyBraces,
    boolean keepEmptyRecordCompactBraces,
    boolean ensureTrailingNewline) {

  public static TestCodeFormattingProfile deterministicDefaults() {
    return new TestCodeFormattingProfile(true, true, true, true, true);
  }
}
