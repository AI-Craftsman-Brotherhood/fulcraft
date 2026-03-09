package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.craftsmanbro.fulcraft.infrastructure.cache.contract.CacheRevalidationPort;
import com.craftsmanbro.fulcraft.infrastructure.cache.model.CacheRevalidationResult;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.CodeValidator;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.Objects;
import java.util.Set;

/**
 * Validates cached code to ensure it's still valid before use.
 *
 * <p>This class performs lightweight revalidation of cached test code to detect if the code has
 * become invalid due to:
 *
 * <ul>
 *   <li>Source code changes that affect the test
 *   <li>Dependency updates that break compilation
 *   <li>Environment changes
 * </ul>
 *
 * <p>Revalidation is intentionally lightweight to minimize overhead:
 *
 * <ul>
 *   <li>Syntax validation using JavaParser
 *   <li>Basic structure checks (class declaration, JUnit-style test annotations)
 * </ul>
 *
 * <p>Full compilation is avoided as it would be too expensive for every cache hit.
 */
public class CacheRevalidator implements CacheRevalidationPort {

  private static final String LOG_PREFIX_REVALIDATION_FAILED = "Cache revalidation failed for ";

  private static final String ERROR_PREFIX_STRUCTURE = "Structure error: ";

  private final CodeValidator codeValidator;

  private int revalidationCount;

  private int failureCount;

  /**
   * Creates a new CacheRevalidator with the given code validator.
   *
   * @param codeValidator the code validator to use for revalidation
   */
  public CacheRevalidator(final CodeValidator codeValidator) {
    this.codeValidator =
        Objects.requireNonNull(
            codeValidator,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "codeValidator must not be null"));
  }

  /** Creates a new CacheRevalidator with a default code validator. */
  public CacheRevalidator() {
    this(new CodeValidator());
  }

  /**
   * Revalidates cached code to ensure it's still valid.
   *
   * <p>This performs lightweight validation including:
   *
   * <ul>
   *   <li>Syntax validation (Java parsing)
   *   <li>Basic structure checks
   * </ul>
   *
   * @param cachedCode the cached code to revalidate
   * @param taskId the task ID (for logging)
   * @return a CacheRevalidationResult indicating whether the code is valid
   */
  @Override
  public CacheRevalidationResult revalidate(final String cachedCode, final String taskId) {
    revalidationCount++;
    if (cachedCode == null || cachedCode.isBlank()) {
      failureCount++;
      return CacheRevalidationResult.invalid("Cached code is empty or null");
    }
    final long startTimeNanos = System.nanoTime();
    try {
      // Step 1: Syntax validation
      final String syntaxValidationError = codeValidator.validateSyntax(cachedCode);
      if (syntaxValidationError != null) {
        failureCount++;
        Logger.debug(LOG_PREFIX_REVALIDATION_FAILED + taskId + ": " + syntaxValidationError);
        return CacheRevalidationResult.invalid(syntaxValidationError);
      }
      // Step 2: Basic structure validation
      final CompilationUnit compilationUnit = parseCompilationUnit(cachedCode);
      if (compilationUnit == null) {
        failureCount++;
        final String structureErrorDetail = "Failed to parse code after syntax validation";
        Logger.debug(LOG_PREFIX_REVALIDATION_FAILED + taskId + ": " + structureErrorDetail);
        return CacheRevalidationResult.invalid(ERROR_PREFIX_STRUCTURE + structureErrorDetail);
      }
      final boolean hasClassDeclaration =
          compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
              .anyMatch(typeDeclaration -> !typeDeclaration.isInterface());
      if (!hasClassDeclaration) {
        failureCount++;
        final String structureErrorDetail = "Missing class declaration";
        Logger.debug(LOG_PREFIX_REVALIDATION_FAILED + taskId + ": " + structureErrorDetail);
        return CacheRevalidationResult.invalid(ERROR_PREFIX_STRUCTURE + structureErrorDetail);
      }
      if (!hasTestAnnotation(compilationUnit)) {
        failureCount++;
        final String structureErrorDetail = "Missing test annotation";
        Logger.debug(LOG_PREFIX_REVALIDATION_FAILED + taskId + ": " + structureErrorDetail);
        return CacheRevalidationResult.invalid(ERROR_PREFIX_STRUCTURE + structureErrorDetail);
      }
      final long durationNanos = System.nanoTime() - startTimeNanos;
      final double durationMillis = durationNanos / 1_000_000.0;
      Logger.debug(
          "Cache revalidation passed for "
              + taskId
              + " in "
              + String.format("%.2f", durationMillis)
              + "ms");
      return CacheRevalidationResult.valid(durationMillis);
    } catch (RuntimeException runtimeException) {
      failureCount++;
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Cache revalidation exception for " + taskId + ": " + runtimeException.getMessage()));
      return CacheRevalidationResult.invalid(
          "Validation exception: " + runtimeException.getMessage());
    }
  }

  /**
   * Returns the total number of revalidations performed.
   *
   * @return the revalidation count
   */
  @Override
  public int getRevalidationCount() {
    return revalidationCount;
  }

  /**
   * Returns the number of failed revalidations.
   *
   * @return the failure count
   */
  @Override
  public int getFailureCount() {
    return failureCount;
  }

  /**
   * Returns the revalidation success rate as a percentage.
   *
   * @return the success rate (0-100), or 100 if no revalidations performed
   */
  @Override
  public double getSuccessRate() {
    if (revalidationCount == 0) {
      return 100.0;
    }
    return ((double) (revalidationCount - failureCount) / revalidationCount) * 100.0;
  }

  /** Resets the statistics counters. */
  @Override
  public void resetStats() {
    revalidationCount = 0;
    failureCount = 0;
  }

  private static CompilationUnit parseCompilationUnit(final String code) {
    // Cached snippets may still arrive wrapped in Markdown fences.
    final String codeWithoutFences = stripCodeFences(code);
    final ParseResult<CompilationUnit> parseResult = new JavaParser().parse(codeWithoutFences);
    if (!parseResult.isSuccessful()) {
      return null;
    }
    return parseResult.getResult().orElse(null);
  }

  private static String stripCodeFences(final String text) {
    String trimmedText = text.trim();
    if (trimmedText.startsWith("```")) {
      final int openingFenceNewlineIndex = trimmedText.indexOf('\n');
      if (openingFenceNewlineIndex >= 0) {
        trimmedText = trimmedText.substring(openingFenceNewlineIndex + 1);
      }
    }
    if (trimmedText.endsWith("```")) {
      trimmedText = trimmedText.substring(0, trimmedText.length() - 3);
    }
    return trimmedText.trim();
  }

  private static boolean hasTestAnnotation(final CompilationUnit compilationUnit) {
    // Generated tests can use either imports or fully qualified JUnit annotations.
    final Set<String> supportedTestAnnotationNames =
        Set.of(
            "Test",
            "ParameterizedTest",
            "RepeatedTest",
            "TestFactory",
            "TestTemplate",
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.api.TestTemplate",
            "org.junit.Test",
            "org.junit.experimental.theories.Theory");
    return compilationUnit.findAll(MethodDeclaration.class).stream()
        .anyMatch(
            method ->
                method.getAnnotations().stream()
                    .map(annotation -> annotation.getName().asString())
                    .anyMatch(supportedTestAnnotationNames::contains));
  }
}
