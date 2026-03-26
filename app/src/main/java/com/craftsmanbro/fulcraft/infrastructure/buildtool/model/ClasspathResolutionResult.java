package com.craftsmanbro.fulcraft.infrastructure.buildtool.model;

import java.nio.file.Path;
import java.util.List;

public final class ClasspathResolutionResult {

  private final List<Path> entries;

  private final String selectedTool;

  private final List<Attempt> attempts;

  public ClasspathResolutionResult(
      final List<Path> classpathEntries,
      final String selectedTool,
      final List<Attempt> resolutionAttempts) {
    this.entries = immutableCopy(classpathEntries);
    this.selectedTool = selectedTool;
    this.attempts = immutableCopy(resolutionAttempts);
  }

  public List<Path> getEntries() {
    return entries;
  }

  public String getSelectedTool() {
    return selectedTool;
  }

  public List<Attempt> getAttempts() {
    return attempts;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  private static <T> List<T> immutableCopy(final List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record Attempt(String tool, boolean success, Integer exitCode, String message) {}
}
