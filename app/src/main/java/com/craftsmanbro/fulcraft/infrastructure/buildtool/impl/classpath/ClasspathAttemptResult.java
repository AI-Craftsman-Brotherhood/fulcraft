package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.nio.file.Path;
import java.util.List;

public record ClasspathAttemptResult(
    String tool, List<Path> entries, boolean success, Integer exitCode, String message) {

  /** Compact constructor that creates defensive copies of collections. */
  public ClasspathAttemptResult {
    entries = entries != null ? List.copyOf(entries) : List.of();
  }

  public ClasspathResolutionResult.Attempt toAttempt() {
    return new ClasspathResolutionResult.Attempt(tool, success, exitCode, message);
  }

  public List<Path> safeEntries() {
    return entries;
  }
}
