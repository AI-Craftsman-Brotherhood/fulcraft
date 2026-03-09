package com.craftsmanbro.fulcraft.plugins.document.core.service.document;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves and generates links between source classes/methods and their corresponding tests.
 *
 * <p>Uses naming conventions and TaskRecord/GenerationTaskResult information to establish
 * relationships between production code and test code when available.
 */
public class TestLinkResolver {

  /** Represents a link to a test class or method. */
  public record TestLink(String testClassName, String testMethodName, String relativePath) {

    public String toMarkdownLink() {
      if (testMethodName != null && !testMethodName.isEmpty()) {
        return String.format("[%s#%s](%s)", testClassName, testMethodName, relativePath);
      }
      return String.format("[%s](%s)", testClassName, relativePath);
    }

    public String toHtmlLink() {
      final String displayText =
          testMethodName != null ? testClassName + "#" + testMethodName : testClassName;
      return String.format("<a href=\"%s\">%s</a>", relativePath, displayText);
    }
  }

  private final Path testOutputRoot;

  private final Path linkBase;

  private final Map<String, List<TestLink>> classToTests;

  private final Map<String, List<TestLink>> methodToTests;

  public TestLinkResolver() {
    this(null, null);
  }

  public TestLinkResolver(final Path testOutputRoot) {
    this(testOutputRoot, null);
  }

  public TestLinkResolver(final Path testOutputRoot, final Path linkBase) {
    this.testOutputRoot = testOutputRoot;
    this.linkBase = linkBase;
    this.classToTests = new HashMap<>();
    this.methodToTests = new HashMap<>();
  }

  /**
   * Registers a test link for a class.
   *
   * @param classFqn the fully qualified name of the source class
   * @param testLink the test link
   */
  public void registerClassTest(final String classFqn, final TestLink testLink) {
    classToTests.computeIfAbsent(classFqn, k -> new ArrayList<>()).add(testLink);
  }

  /**
   * Registers a test link for a method.
   *
   * @param classFqn the fully qualified name of the source class
   * @param methodName the method name
   * @param testLink the test link
   */
  public void registerMethodTest(
      final String classFqn, final String methodName, final TestLink testLink) {
    final String key = classFqn + "#" + methodName;
    methodToTests.computeIfAbsent(key, k -> new ArrayList<>()).add(testLink);
  }

  /**
   * Gets test links for a class.
   *
   * @param classFqn the fully qualified name of the source class
   * @return list of test links, empty if none found
   */
  public List<TestLink> getTestsForClass(final String classFqn) {
    return classToTests.getOrDefault(classFqn, List.of());
  }

  /**
   * Gets test links for a method.
   *
   * @param classFqn the fully qualified name of the source class
   * @param methodName the method name
   * @return list of test links, empty if none found
   */
  public List<TestLink> getTestsForMethod(final String classFqn, final String methodName) {
    final String key = classFqn + "#" + methodName;
    return methodToTests.getOrDefault(key, List.of());
  }

  /**
   * Registers test links using task/result metadata when available.
   *
   * @param task the task definition
   * @param result the generation result, if available
   */
  public void registerTaskLink(final TaskRecord task, final GenerationTaskResult result) {
    Objects.requireNonNull(
        task,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "task"));
    final String classFqn = task.getClassFqn();
    final String methodName = task.getMethodName();
    final String testClassName = task.getTestClassName();
    final String path = resolveTestPath(task, result, testClassName);
    if (classFqn == null || testClassName == null || path == null) {
      return;
    }
    final TestLink link = new TestLink(testClassName, null, path);
    registerClassTest(classFqn, link);
    if (methodName != null && !methodName.isBlank()) {
      registerMethodTest(classFqn, methodName, link);
    }
  }

  /**
   * Infers test links based on naming conventions.
   *
   * <p>Common conventions:
   *
   * <ul>
   *   <li>Foo -> FooTest, FooTests
   *   <li>Foo#bar -> FooTest#testBar, FooBarTest#test*
   * </ul>
   *
   * @param classInfo the class information
   * @return inferred test links
   */
  public List<TestLink> inferTestLinks(final ClassInfo classInfo) {
    final List<TestLink> links = new ArrayList<>();
    final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String packageName = DocumentUtils.getPackageName(classInfo.getFqn());
    // Infer class-level tests
    addIfAbsent(
        links,
        new TestLink(simpleName + "Test", null, inferTestPath(packageName, simpleName + "Test")));
    addIfAbsent(
        links,
        new TestLink(simpleName + "Tests", null, inferTestPath(packageName, simpleName + "Tests")));
    return links;
  }

  /**
   * Infers test links for a method based on naming conventions.
   *
   * @param classInfo the class information
   * @param methodInfo the method information
   * @return inferred test links
   */
  public List<TestLink> inferTestLinks(final ClassInfo classInfo, final MethodInfo methodInfo) {
    final List<TestLink> links = new ArrayList<>();
    final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String packageName = DocumentUtils.getPackageName(classInfo.getFqn());
    final String methodName = methodInfo.getName();
    // Pattern 1: FooTest#testMethodName
    final String testClassName = simpleName + "Test";
    final String testMethodName = "test" + capitalize(methodName);
    final String testPath = inferTestPath(packageName, testClassName);
    addIfAbsent(links, new TestLink(testClassName, testMethodName, testPath));
    // Pattern 2: FooMethodNameTest (method-level test class)
    final String methodTestClassName = simpleName + capitalize(methodName) + "Test";
    final String methodTestPath = inferTestPath(packageName, methodTestClassName);
    addIfAbsent(links, new TestLink(methodTestClassName, null, methodTestPath));
    return links;
  }

  /**
   * Generates a Markdown section with test links for a class.
   *
   * @param classInfo the class information
   * @param includeInferred whether to include inferred test links
   * @return the Markdown content
   */
  public String generateTestLinksSection(final ClassInfo classInfo, final boolean includeInferred) {
    final StringBuilder sb = new StringBuilder();
    List<TestLink> classTests = getTestsForClass(classInfo.getFqn());
    if (includeInferred && classTests.isEmpty()) {
      classTests = inferTestLinks(classInfo);
    }
    if (!classTests.isEmpty()) {
      sb.append("\n## ").append(msg("document.md.section.related_tests")).append("\n\n");
      sb.append("| ")
          .append(msg("document.table.test_class"))
          .append(" | ")
          .append(msg("document.table.link"))
          .append(" |\n");
      sb.append("|------------|--------|\n");
      for (final TestLink link : classTests) {
        sb.append("| ").append(link.testClassName());
        if (link.testMethodName() != null) {
          sb.append("#").append(link.testMethodName());
        }
        sb.append(" | ").append(link.toMarkdownLink()).append(" |\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Generates test links for a method.
   *
   * @param classInfo the class information
   * @param methodInfo the method information
   * @param includeInferred whether to include inferred test links
   * @return the Markdown content
   */
  public String generateMethodTestLinks(
      final ClassInfo classInfo, final MethodInfo methodInfo, final boolean includeInferred) {
    final StringBuilder sb = new StringBuilder();
    List<TestLink> methodTests = getTestsForMethod(classInfo.getFqn(), methodInfo.getName());
    if (includeInferred && methodTests.isEmpty()) {
      methodTests = inferTestLinks(classInfo, methodInfo);
    }
    if (!methodTests.isEmpty()) {
      sb.append("**").append(msg("document.label.related_tests")).append("**: ");
      boolean first = true;
      for (final TestLink link : methodTests) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(link.toMarkdownLink());
        first = false;
      }
      sb.append("\n\n");
    }
    return sb.toString();
  }

  private String inferTestPath(final String packageName, final String testClassName) {
    // Convert package to path
    final String safePackage = normalizePackageName(packageName);
    Path relativePath = Path.of(testClassName + ".java");
    if (!safePackage.isEmpty()) {
      final String[] segments = safePackage.split("\\.");
      final Path packagePath =
          Path.of(segments[0], Arrays.copyOfRange(segments, 1, segments.length));
      relativePath = packagePath.resolve(relativePath);
    }
    if (testOutputRoot != null) {
      return testOutputRoot.resolve(relativePath).toString();
    }
    // Default to standard test directory
    return Path.of("src", "test", "java").resolve(relativePath).toString();
  }

  private String resolveTestPath(
      final TaskRecord task, final GenerationTaskResult result, final String testClassName) {
    if (result != null && result.getGeneratedTestFilePath() != null) {
      final Path path = result.getGeneratedTestFilePath().normalize();
      if (linkBase != null && path.isAbsolute()) {
        try {
          final Path base = linkBase.toAbsolutePath().normalize();
          final Path baseRoot = base.getRoot();
          final Path pathRoot = path.getRoot();
          if (baseRoot != null && baseRoot.equals(pathRoot)) {
            return base.relativize(path).toString();
          }
        } catch (IllegalArgumentException ignored) {
          // Fallback to absolute path string.
        }
      }
      return path.toString();
    }
    final String packageName = task != null ? DocumentUtils.getPackageName(task.getClassFqn()) : "";
    return testClassName != null ? inferTestPath(packageName, testClassName) : null;
  }

  private String normalizePackageName(final String packageName) {
    if (packageName == null || packageName.isBlank() || "(default)".equals(packageName)) {
      return "";
    }
    return packageName;
  }

  private void addIfAbsent(final List<TestLink> links, final TestLink link) {
    for (final TestLink existing : links) {
      if (existing.testClassName().equals(link.testClassName())
          && Objects.equals(existing.testMethodName(), link.testMethodName())
          && existing.relativePath().equals(link.relativePath())) {
        return;
      }
    }
    links.add(link);
  }

  private String capitalize(final String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
