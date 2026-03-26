package com.craftsmanbro.fulcraft.ui.tui.execution;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.UiLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class SafeFixApplier {

  private static final String DISABLED_IMPORT = "import org.junit.jupiter.api.Disabled;";

  private static final String DISABLED_ANNOTATION = "@Disabled(\"FUL safe fix: failing test\")";

  private final List<Path> testRoots;

  SafeFixApplier(final Path projectRoot, final Config config) {
    this.testRoots = resolveTestRoots(projectRoot, config);
  }

  boolean disableTest(final String testClass, final String testMethod) {
    if (testClass == null || testClass.isBlank() || testMethod == null || testMethod.isBlank()) {
      return false;
    }
    final Path testFile = resolveTestFile(testClass);
    if (testFile == null) {
      UiLogger.warn(MessageSource.getMessage("tui.safefix.skipped_not_found", testClass));
      return false;
    }
    try {
      final List<String> lines = new ArrayList<>(Files.readAllLines(testFile));
      int methodLine = findMethodLine(lines, testMethod);
      if (methodLine < 0) {
        UiLogger.warn(
            MessageSource.getMessage("tui.safefix.skipped_method_not_found", testFile, testMethod));
        return false;
      }
      final int importInsertAt = ensureDisabledImport(lines);
      if (importInsertAt >= 0 && importInsertAt <= methodLine) {
        methodLine++;
      }
      final int insertAt = findAnnotationInsertLine(lines, methodLine);
      if (hasDisabledAnnotation(lines, insertAt, methodLine)) {
        return false;
      }
      final String indent = leadingWhitespace(lines.get(methodLine));
      lines.add(insertAt, indent + DISABLED_ANNOTATION);
      Files.write(testFile, lines);
      return true;
    } catch (IOException e) {
      UiLogger.warn(
          MessageSource.getMessage("tui.safefix.failed", testClass, testMethod, e.getMessage()));
      return false;
    }
  }

  private Path resolveTestFile(final String testClass) {
    final String relativePath = testClass.replace('.', '/') + ".java";
    for (final Path root : testRoots) {
      final Path candidate = root.resolve(relativePath);
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private List<Path> resolveTestRoots(final Path projectRoot, final Config config) {
    final List<Path> roots = new ArrayList<>();
    if (config != null && config.getContextAwareness() != null) {
      for (final String dir : config.getContextAwareness().getTestDirs()) {
        if (dir != null && !dir.isBlank()) {
          roots.add(projectRoot.resolve(dir));
        }
      }
    }
    if (roots.isEmpty()) {
      roots.add(projectRoot.resolve("src/test/java"));
      roots.add(projectRoot.resolve("app/src/test/java"));
    }
    return roots;
  }

  private int ensureDisabledImport(final List<String> lines) {
    for (final String line : lines) {
      if (DISABLED_IMPORT.equals(line.trim())) {
        return -1;
      }
    }
    int insertAt = -1;
    for (int i = 0; i < lines.size(); i++) {
      final String trimmed = lines.get(i).trim();
      if (trimmed.startsWith("import ")) {
        insertAt = i + 1;
      }
    }
    if (insertAt < 0) {
      for (int i = 0; i < lines.size(); i++) {
        final String trimmed = lines.get(i).trim();
        if (trimmed.startsWith("package ")) {
          insertAt = i + 1;
          break;
        }
      }
    }
    if (insertAt < 0) {
      insertAt = 0;
    }
    lines.add(insertAt, DISABLED_IMPORT);
    return insertAt;
  }

  private int findMethodLine(final List<String> lines, final String methodName) {
    final String needle = methodName + "(";
    final String signaturePattern = "\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\(";
    for (int i = 0; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (isCommentLine(line)) {
        continue;
      }
      if (line.contains(needle) && line.matches(".*" + signaturePattern + ".*")) {
        return i;
      }
    }
    for (int i = 0; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (isCommentLine(line)) {
        continue;
      }
      if (line.contains(needle)) {
        return i;
      }
    }
    return -1;
  }

  private int findAnnotationInsertLine(final List<String> lines, final int methodLine) {
    int insertAt = methodLine;
    for (int i = methodLine - 1; i >= 0; i--) {
      final String trimmed = lines.get(i).trim();
      if (trimmed.startsWith("@")) {
        insertAt = i;
      } else if (trimmed.isEmpty()) {
        return i + 1;
      } else {
        return insertAt;
      }
    }
    return insertAt;
  }

  private boolean hasDisabledAnnotation(final List<String> lines, final int start, final int end) {
    final int from = Math.max(0, start);
    final int to = Math.min(lines.size() - 1, end);
    for (int i = from; i <= to; i++) {
      if (lines.get(i).trim().startsWith("@Disabled")) {
        return true;
      }
    }
    return false;
  }

  private String leadingWhitespace(final String line) {
    int idx = 0;
    while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
      idx++;
    }
    return line.substring(0, idx);
  }

  private boolean isCommentLine(final String line) {
    final String trimmed = line.trim();
    return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
  }
}
