package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class CodeValidator {

  private static final String JUNIT_TEST_FQN = "org.junit.jupiter.api.Test";

  private static final String JUNIT_ASSERTIONS_FQN = "org.junit.jupiter.api.Assertions";

  private static final String ASSERT_PREFIX = "assert";

  public List<String> extractPrivateFields(final String sourceCode) {
    final java.util.Set<String> privateFieldNames = new java.util.HashSet<>();
    final java.util.Set<String> nonPrivateFieldNames = new java.util.HashSet<>();
    try {
      final ParseResult<CompilationUnit> parseResult = parse(sourceCode);
      final Optional<CompilationUnit> maybeCu = parseResult.getResult();
      if (parseResult.isSuccessful() && maybeCu.isPresent()) {
        maybeCu
            .get()
            .findAll(FieldDeclaration.class)
            .forEach(
                fd ->
                    fd.getVariables()
                        .forEach(
                            v -> {
                              if (fd.isPrivate()) {
                                privateFieldNames.add(v.getNameAsString());
                              } else {
                                nonPrivateFieldNames.add(v.getNameAsString());
                              }
                            }));
      }
    } catch (Exception e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to extract private fields: " + e.getMessage()),
          e);
    }
    // If a field name exists as non-private in any class, do NOT treat it as
    // strictly private.
    // This avoids false positives when different inner classes have fields with the
    // same name but different visibility.
    privateFieldNames.removeAll(nonPrivateFieldNames);
    return new ArrayList<>(privateFieldNames);
  }

  /**
   * Extracts names of ALL fields declared as private, even if they are public in other classes.
   * Used to prevent unsafe auto-replacements of getters with field access.
   */
  public java.util.Set<String> extractAllPrivateFieldNames(final String sourceCode) {
    final java.util.Set<String> privateFieldNames = new java.util.HashSet<>();
    try {
      final ParseResult<CompilationUnit> parseResult = parse(sourceCode);
      final Optional<CompilationUnit> maybeCu = parseResult.getResult();
      if (parseResult.isSuccessful() && maybeCu.isPresent()) {
        maybeCu
            .get()
            .findAll(FieldDeclaration.class)
            .forEach(
                fd -> {
                  if (fd.isPrivate()) {
                    fd.getVariables().forEach(v -> privateFieldNames.add(v.getNameAsString()));
                  }
                });
      }
    } catch (Exception e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to extract all private fields: " + e.getMessage()),
          e);
    }
    return privateFieldNames;
  }

  public String validateCode(
      final String code, final List<String> privateFields, final String targetMethodName) {
    return validateCode(code, privateFields, targetMethodName, null, false);
  }

  /**
   * Validates generated test code with static misuse detection.
   *
   * @param code the generated test code
   * @param privateFields list of private field names to check
   * @param targetMethodName the target method name
   * @param className the simple name of the class under test (for static misuse check)
   * @param isStatic whether the target method is static
   * @return error message if validation fails, null if valid
   */
  public String validateCode(
      final String code,
      final List<String> privateFields,
      final String targetMethodName,
      final String className,
      final boolean isStatic) {
    final String cleaned = stripCodeFences(code);
    try {
      final ParseResult<CompilationUnit> parseResult = parse(cleaned);
      if (!parseResult.isSuccessful()) {
        return "Syntax error: " + parseResult.getProblems();
      }
      final var cu =
          parseResult
              .getResult()
              .orElseThrow(
                  () -> new IllegalStateException("JavaParser succeeded but returned no AST"));
      return firstError(
          () -> validateHasTestAnnotation(cu),
          () -> validateHasClassDeclaration(cleaned),
          () -> validateNoPublicDeclarations(cu),
          () -> validateNoJUnit4Assert(cu),
          () -> validateNoPrivateFieldAccess(cu, privateFields),
          () -> validateRequiredImports(cu, cleaned),
          () -> validateCallsTargetMethod(cu, targetMethodName),
          () -> validateHasAssertion(cu),
          () -> validateNoEmptyCatch(cu),
          () -> validateStaticMisuse(cu, className, targetMethodName, isStatic));
    } catch (Exception e) {
      return "Validation exception: " + e.getMessage();
    }
  }

  /**
   * Detects static method misuse patterns where instance is created to call a static method.
   * Patterns detected: 1. new ClassName().methodName(...) - inline instantiation 2. ClassName var =
   * new ClassName(); var.methodName(...) - variable-based
   *
   * @param cu the compilation unit
   * @param className the simple name of the class under test
   * @param targetMethodName the target method name (may include signature)
   * @param isStatic whether the target method is static
   * @return error message if misuse detected, null otherwise
   */
  private String validateStaticMisuse(
      final CompilationUnit cu,
      final String className,
      final String targetMethodName,
      final boolean isStatic) {
    if (!isStatic || className == null || className.isBlank()) {
      return null;
    }
    final String simpleMethodName = extractSimpleMethodName(targetMethodName);
    if (simpleMethodName == null) {
      return null;
    }
    final Set<String> instanceVars = collectInstanceVariables(cu, className);
    return findStaticMisuseError(cu, className, simpleMethodName, instanceVars);
  }

  private String extractSimpleMethodName(final String targetMethodName) {
    if (targetMethodName == null) {
      return null;
    }
    final String simpleName = targetMethodName.split("\\(")[0];
    return simpleName.isBlank() ? null : simpleName;
  }

  private Set<String> collectInstanceVariables(final CompilationUnit cu, final String className) {
    final Set<String> instanceVars = new HashSet<>();
    for (final VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
      variable
          .getInitializer()
          .filter(ObjectCreationExpr.class::isInstance)
          .map(ObjectCreationExpr.class::cast)
          .filter(oce -> oce.getType().getNameAsString().equals(className))
          .ifPresent(oce -> instanceVars.add(variable.getNameAsString()));
    }
    cu.findAll(AssignExpr.class).stream()
        .filter(assign -> assign.getValue() instanceof ObjectCreationExpr)
        .map(assign -> java.util.Map.entry(assign, (ObjectCreationExpr) assign.getValue()))
        .filter(entry -> entry.getValue().getType().getNameAsString().equals(className))
        .map(entry -> assignedVariableName(entry.getKey().getTarget()))
        .filter(java.util.Objects::nonNull)
        .forEach(instanceVars::add);
    return instanceVars;
  }

  private String findStaticMisuseError(
      final CompilationUnit cu,
      final String className,
      final String simpleMethodName,
      final Set<String> instanceVars) {
    return cu.findAll(MethodCallExpr.class).stream()
        .filter(mce -> mce.getNameAsString().equals(simpleMethodName))
        .filter(mce -> mce.getScope().isPresent())
        .map(
            mce ->
                checkMisusePattern(mce.getScope().get(), className, simpleMethodName, instanceVars))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private String checkMisusePattern(
      final com.github.javaparser.ast.expr.Expression scope,
      final String className,
      final String simpleMethodName,
      final Set<String> instanceVars) {
    // Pattern 1: new ClassName().methodName(...)
    if (scope instanceof ObjectCreationExpr oce
        && oce.getType().getNameAsString().equals(className)) {
      return buildStaticMisuseError(className, simpleMethodName, "new " + className + "()");
    }
    // Pattern 2: var.methodName(...) where var = new ClassName()
    if (scope instanceof NameExpr ne && instanceVars.contains(ne.getNameAsString())) {
      return buildStaticMisuseError(className, simpleMethodName, ne.getNameAsString());
    }
    if (scope instanceof FieldAccessExpr fae
        && fae.getScope().isThisExpr()
        && instanceVars.contains(fae.getNameAsString())) {
      return buildStaticMisuseError(
          className, simpleMethodName, fae.getScope() + "." + fae.getNameAsString());
    }
    return null;
  }

  private String assignedVariableName(final com.github.javaparser.ast.expr.Expression target) {
    if (target instanceof NameExpr ne) {
      return ne.getNameAsString();
    }
    if (target instanceof FieldAccessExpr fae && fae.getScope().isThisExpr()) {
      return fae.getNameAsString();
    }
    return null;
  }

  private String buildStaticMisuseError(
      final String className, final String methodName, final String foundPattern) {
    return "STATIC_MISUSE: Do NOT instantiate "
        + className
        + " to call static method "
        + methodName
        + ". Use "
        + className
        + "."
        + methodName
        + "(...) instead. Found: "
        + foundPattern
        + "."
        + methodName
        + "(...)";
  }

  // Renamed from validateWithJavac to validateSyntax to reflect actual behavior
  // (parsing only)
  public String validateSyntax(final String code) {
    try {
      final ParseResult<CompilationUnit> parseResult = parse(stripCodeFences(code));
      if (parseResult.isSuccessful()) {
        return null;
      }
      return "Syntax error: " + parseResult.getProblems().toString();
    } catch (RuntimeException e) {
      return "Validation exception: " + e.getMessage();
    }
  }

  public boolean isMissingRequiredTestElements(
      final String code,
      final String testClassName,
      final String sutSimpleName,
      final String methodName) {
    try {
      final ParseResult<CompilationUnit> parseResult = parse(stripCodeFences(code));
      final Optional<CompilationUnit> maybeCu = parseResult.getResult();
      if (parseResult.isSuccessful() && maybeCu.isPresent()) {
        final var cu = maybeCu.get();
        final boolean missingTestAnnotation = validateHasTestAnnotation(cu) != null;
        final boolean missingClassName = cu.getClassByName(testClassName).isEmpty();
        final boolean missingSutReference = !code.contains(sutSimpleName);
        final boolean missingMethodReference = validateCallsTargetMethod(cu, methodName) != null;
        final boolean missingAssertion = validateHasAssertion(cu) != null;
        return missingTestAnnotation
            || missingClassName
            || missingSutReference
            || missingMethodReference
            || missingAssertion;
      }
      // Parse failed, so assume missing elements
      return true;
    } catch (Exception e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Error checking required elements: " + e.getMessage()),
          e);
      return true;
    }
  }

  private ParseResult<CompilationUnit> parse(final String code) {
    return new JavaParser().parse(code);
  }

  /**
   * Strips Markdown code fences from text.
   *
   * <p>Removes leading {@code ```language} and trailing {@code ```} markers commonly added by LLMs
   * when generating code.
   *
   * @param text the text to clean; may contain code fences
   * @return the text with code fences removed and trimmed
   */
  public static String stripCodeFences(final String text) {
    var cleaned = text.trim();
    if (cleaned.startsWith("```")) {
      final int firstNewline = cleaned.indexOf('\n');
      if (firstNewline >= 0) {
        cleaned = cleaned.substring(firstNewline + 1);
      }
    }
    if (cleaned.endsWith("```")) {
      cleaned = cleaned.substring(0, cleaned.length() - 3);
    }
    return cleaned.trim();
  }

  @SafeVarargs
  private static String firstError(final Supplier<String>... checks) {
    for (final Supplier<String> check : checks) {
      final var error = check.get();
      if (error != null) {
        return error;
      }
    }
    return null;
  }

  private static String validateHasTestAnnotation(final CompilationUnit cu) {
    final boolean hasTestAnnotation =
        cu.findAll(MethodDeclaration.class).stream()
            .anyMatch(
                m ->
                    m.getAnnotationByName("Test").isPresent()
                        || m.getAnnotationByName(JUNIT_TEST_FQN).isPresent());
    return hasTestAnnotation ? null : "Missing @Test annotation";
  }

  private static String validateHasClassDeclaration(final String cleaned) {
    return cleaned.contains("class ") ? null : "Missing class declaration";
  }

  private static String validateNoPublicDeclarations(final CompilationUnit cu) {
    for (final TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
      if (type.isPublic()) {
        return "Public class declarations are not allowed. Use package-private instead.";
      }
    }
    for (final MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
      if (method.isPublic()) {
        return "Public test methods are not allowed. Use package-private instead.";
      }
    }
    return null;
  }

  private static String validateNoJUnit4Assert(final CompilationUnit cu) {
    for (final var mce : cu.findAll(MethodCallExpr.class)) {
      if (mce.getScope().map(Object::toString).filter("Assert"::equals).isPresent()) {
        return "Use 'Assertions' instead of 'Assert' for JUnit 5.";
      }
    }
    return null;
  }

  private static String validateNoPrivateFieldAccess(
      final CompilationUnit cu, final List<String> privateFields) {
    if (privateFields == null || privateFields.isEmpty()) {
      return null;
    }
    // Collect all local variable and parameter names to exclude them
    final var declaredNames = new java.util.HashSet<String>();
    // Add all local variable declarations
    for (final var vd : cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
      declaredNames.add(vd.getNameAsString());
    }
    // Add all method parameter names
    for (final var param : cu.findAll(com.github.javaparser.ast.body.Parameter.class)) {
      declaredNames.add(param.getNameAsString());
    }
    // Check FieldAccessExpr (e.g., obj.firstName) - this is always a problem
    for (final var fae : cu.findAll(FieldAccessExpr.class)) {
      final String fieldName = fae.getNameAsString();
      if (privateFields.contains(fieldName)) {
        return "Direct access to private field '"
            + fieldName
            + "' is not allowed. Use getter or reflection if necessary.";
      }
    }
    // Check NameExpr - but only if NOT a local variable or parameter
    for (final var ne : cu.findAll(NameExpr.class)) {
      final String name = ne.getNameAsString();
      if (privateFields.contains(name) && !declaredNames.contains(name)) {
        return "Direct reference to private field '"
            + name
            + "' is not allowed. Use getter or reflection if necessary.";
      }
    }
    return null;
  }

  private static String validateRequiredImports(final CompilationUnit cu, final String cleaned) {
    final boolean hasTestImport =
        cu.getImports().stream().anyMatch(i -> JUNIT_TEST_FQN.equals(i.getNameAsString()));
    if (cleaned.contains("@Test") && !hasTestImport) {
      return "Missing import for @Test. Add 'import org.junit.jupiter.api.Test;'";
    }
    final boolean usesAssertionsClass =
        cu.findAll(MethodCallExpr.class).stream()
            .anyMatch(
                mce ->
                    mce.getScope().map(Object::toString).filter("Assertions"::equals).isPresent());
    final boolean hasAssertionsImport =
        cu.getImports().stream()
            .anyMatch(i -> !i.isStatic() && JUNIT_ASSERTIONS_FQN.equals(i.getNameAsString()));
    if (usesAssertionsClass && !hasAssertionsImport) {
      return "Missing import for Assertions. Add 'import org.junit.jupiter.api.Assertions;'";
    }
    return null;
  }

  private static String validateCallsTargetMethod(
      final CompilationUnit cu, final String targetMethodName) {
    if (targetMethodName == null || targetMethodName.isBlank()) {
      return null;
    }
    final var simpleMethodName = targetMethodName.split("\\(")[0];
    // Special handling for constructors: <init> should check for 'new
    // ClassName(...)'
    if ("<init>".equals(simpleMethodName) || simpleMethodName.contains("<init>")) {
      // For constructors, check if any ObjectCreationExpr exists
      final boolean callsConstructor =
          !cu.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).isEmpty();
      if (callsConstructor) {
        return null;
      }
      return "Test does not invoke the constructor. Ensure the test uses 'new ClassName(...)'.";
    }
    boolean callsTargetMethod =
        cu.findAll(MethodCallExpr.class).stream()
            .anyMatch(mce -> mce.getNameAsString().equals(simpleMethodName));
    // Also check for ObjectCreationExpr (for constructors named like class name)
    if (!callsTargetMethod) {
      callsTargetMethod =
          cu.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream()
              .anyMatch(oce -> oce.getType().getNameAsString().equals(simpleMethodName));
    }
    if (callsTargetMethod) {
      return null;
    }
    return "Test does not invoke the target method '"
        + simpleMethodName
        + "'. Ensure the test calls the method being tested.";
  }

  private static String validateHasAssertion(final CompilationUnit cu) {
    final boolean hasAssertion =
        cu.findAll(MethodCallExpr.class).stream()
            .anyMatch(
                mce -> {
                  final var name = mce.getNameAsString();
                  return name.startsWith(ASSERT_PREFIX) || "fail".equals(name);
                });
    return hasAssertion ? null : "Test does not contain any assertions";
  }

  private static String validateNoEmptyCatch(final CompilationUnit cu) {
    return cu.findAll(com.github.javaparser.ast.stmt.CatchClause.class).stream()
        .filter(c -> c.getBody().getStatements().isEmpty())
        .findFirst()
        .map(c -> "Empty catch block found. Please handle exceptions.")
        .orElse(null);
  }

  public int countTests(final String code) {
    try {
      final ParseResult<CompilationUnit> parseResult = parse(stripCodeFences(code));
      return parseResult
          .getResult()
          .map(
              cu ->
                  (int)
                      cu.findAll(MethodDeclaration.class).stream()
                          .filter(
                              m ->
                                  m.getAnnotationByName("Test").isPresent()
                                      || m.getAnnotationByName("ParameterizedTest").isPresent()
                                      || m.getAnnotationByName(JUNIT_TEST_FQN).isPresent())
                          .count())
          .orElse(0);
    } catch (Exception e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to count tests: " + e.getMessage()),
          e);
    }
    return 0;
  }
}
