package com.craftsmanbro.fulcraft.plugins.analysis.core.service.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes common method implementations (toString, equals, hashCode) to provide hints for test
 * generation.
 *
 * <p>This analyzer extracts implementation details that help the LLM generate more accurate tests
 * by understanding:
 *
 * <ul>
 *   <li>Expected toString() format patterns
 *   <li>Fields compared in equals() methods
 *   <li>Fields used in hashCode() calculations
 * </ul>
 */
public class CommonMethodAnalyzer {

  private static final String METHOD_TO_STRING = "toString";

  private static final String METHOD_EQUALS = "equals";

  private static final String METHOD_HASH_CODE = "hashCode";

  private static final String METHOD_HASH = "hash";

  private static final String FIELD_CLASS = "class";

  private static final String LOCAL_RESULT = "result";

  private static final String LOCAL_PRIME = "prime";

  private static final String LOCAL_HASH = "hash";

  /** Result containing analysis of common method implementations. */
  public record MethodHints(
      String toStringFormat,
      List<String> equalsFields,
      List<String> hashCodeFields,
      String constructorPattern) {

    /** Compact constructor that creates defensive copies of collections. */
    public MethodHints {
      equalsFields = equalsFields != null ? List.copyOf(equalsFields) : List.of();
      hashCodeFields = hashCodeFields != null ? List.copyOf(hashCodeFields) : List.of();
    }

    public boolean hasHints() {
      return toStringFormat != null
          || !equalsFields.isEmpty()
          || !hashCodeFields.isEmpty()
          || constructorPattern != null;
    }
  }

  /**
   * Analyzes source code to extract hints for common method implementations.
   *
   * @param sourceCode The source code to analyze
   * @param targetMethodName The target method being tested
   * @return MethodHints containing extracted information
   */
  public MethodHints analyzeMethodHints(final String sourceCode, final String targetMethodName) {
    if (sourceCode == null || sourceCode.isBlank()) {
      return emptyHints();
    }
    try {
      final var parseResult = new JavaParser().parse(sourceCode);
      if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        return emptyHints();
      }
      final CompilationUnit cu = parseResult.getResult().get();
      String toStringFormat = null;
      List<String> equalsFields = List.of();
      List<String> hashCodeFields = List.of();
      String constructorPattern = null;
      // Analyze based on target method
      if (METHOD_TO_STRING.equals(targetMethodName)) {
        toStringFormat = extractToStringFormat(cu);
      } else if (METHOD_EQUALS.equals(targetMethodName)) {
        equalsFields = extractEqualsFields(cu);
      } else if (METHOD_HASH_CODE.equals(targetMethodName)) {
        hashCodeFields = extractHashCodeFields(cu);
      }
      // For constructors, analyze what fields are set
      if (targetMethodName != null
          && !targetMethodName.isBlank()
          && (targetMethodName.contains("init")
              || Character.isUpperCase(targetMethodName.charAt(0)))) {
        constructorPattern = extractConstructorPattern(cu);
      }
      return new MethodHints(toStringFormat, equalsFields, hashCodeFields, constructorPattern);
    } catch (RuntimeException e) {
      return emptyHints();
    }
  }

  /** Returns an empty hints object for parse/validation fallback paths. */
  private MethodHints emptyHints() {
    return new MethodHints(null, List.of(), List.of(), null);
  }

  /** Extracts the expected format from a toString() implementation. */
  String extractToStringFormat(final CompilationUnit cu) {
    final Optional<MethodDeclaration> toStringMethod = findMethodByName(cu, METHOD_TO_STRING, 0);
    if (toStringMethod.isEmpty()) {
      return null;
    }
    final MethodDeclaration method = toStringMethod.get();
    final List<ReturnStmt> returns = method.findAll(ReturnStmt.class);
    if (returns.isEmpty()) {
      return null;
    }
    final ReturnStmt returnStmt = returns.get(0);
    // Try to extract format from String.format call
    final String formatFromCall = extractFormatFromMethodCall(returnStmt);
    if (formatFromCall != null) {
      return formatFromCall;
    }
    // Try to extract from string literals
    return extractFormatFromLiterals(returnStmt);
  }

  private Optional<MethodDeclaration> findMethodByName(
      final CompilationUnit cu, final String name, final int paramCount) {
    return cu.findAll(MethodDeclaration.class).stream()
        .filter(m -> name.equals(m.getNameAsString()))
        .filter(m -> m.getParameters().size() == paramCount)
        .findFirst();
  }

  private String extractFormatFromMethodCall(final ReturnStmt returnStmt) {
    for (final MethodCallExpr call : returnStmt.findAll(MethodCallExpr.class)) {
      if ("format".equals(call.getNameAsString())
          && !call.getArguments().isEmpty()
          && call.getArgument(0) instanceof StringLiteralExpr strLit) {
        return strLit.getValue();
      }
    }
    return null;
  }

  private String extractFormatFromLiterals(final ReturnStmt returnStmt) {
    final StringBuilder formatBuilder = new StringBuilder();
    for (final StringLiteralExpr lit : returnStmt.findAll(StringLiteralExpr.class)) {
      final String value = lit.getValue();
      if (value.contains("[") || value.contains("{") || value.contains("(")) {
        formatBuilder.append(value);
      }
    }
    if (formatBuilder.isEmpty()) {
      returnStmt
          .getExpression()
          .ifPresent(
              expr -> {
                if (expr.toString().contains("getClass().getSimpleName()")) {
                  formatBuilder.append("<ClassName>");
                }
              });
    }
    return formatBuilder.isEmpty() ? null : formatBuilder.toString();
  }

  /** Extracts fields compared in an equals() implementation. */
  List<String> extractEqualsFields(final CompilationUnit cu) {
    final Optional<MethodDeclaration> equalsMethod = findMethodByName(cu, METHOD_EQUALS, 1);
    if (equalsMethod.isEmpty()) {
      return List.of();
    }
    final Set<String> fields = new LinkedHashSet<>();
    final MethodDeclaration method = equalsMethod.get();
    // Look for Objects.equals(this.field, other.field) calls
    method.findAll(MethodCallExpr.class).stream()
        .filter(call -> METHOD_EQUALS.equals(call.getNameAsString()))
        .forEach(
            call -> {
              call.getScope()
                  .filter(scope -> !isObjectsScope(scope))
                  .ifPresent(scope -> addFieldFromArg(scope, fields));
              call.getArguments().forEach(arg -> addFieldFromArg(arg, fields));
            });
    // Look for field access in binary expressions (field == other.field)
    method.findAll(BinaryExpr.class).stream()
        .filter(
            be ->
                be.getOperator() == BinaryExpr.Operator.EQUALS
                    || be.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
        .forEach(
            be -> {
              extractFieldName(be.getLeft().toString()).ifPresent(fields::add);
              extractFieldName(be.getRight().toString()).ifPresent(fields::add);
            });
    // Look for direct field access expressions
    method
        .findAll(FieldAccessExpr.class)
        .forEach(
            fae -> {
              final String fieldName = fae.getNameAsString();
              if (!FIELD_CLASS.equals(fieldName)) {
                fields.add(fieldName);
              }
            });
    method.getParameters().forEach(param -> fields.remove(param.getNameAsString()));
    return new ArrayList<>(fields);
  }

  private boolean isObjectsScope(final com.github.javaparser.ast.expr.Expression scope) {
    if (scope instanceof NameExpr ne) {
      return "Objects".equals(ne.getNameAsString());
    }
    if (scope instanceof FieldAccessExpr fae) {
      return "Objects".equals(fae.getNameAsString());
    }
    return false;
  }

  /** Adds field name to the set from an expression argument. */
  private void addFieldFromArg(
      final com.github.javaparser.ast.expr.Expression arg, final Set<String> fields) {
    if (arg instanceof NameExpr ne) {
      fields.add(ne.getNameAsString());
    } else if (arg instanceof FieldAccessExpr fae) {
      fields.add(fae.getNameAsString());
    } else {
      extractFieldName(arg.toString()).ifPresent(fields::add);
    }
  }

  /** Extracts fields used in a hashCode() implementation. */
  List<String> extractHashCodeFields(final CompilationUnit cu) {
    final Optional<MethodDeclaration> hashCodeMethod = findMethodByName(cu, METHOD_HASH_CODE, 0);
    if (hashCodeMethod.isEmpty()) {
      return List.of();
    }
    final Set<String> fields = new LinkedHashSet<>();
    final MethodDeclaration method = hashCodeMethod.get();
    // Look for Objects.hash(field1, field2, ...) calls
    method.findAll(MethodCallExpr.class).stream()
        .filter(
            call ->
                METHOD_HASH.equals(call.getNameAsString())
                    || METHOD_HASH_CODE.equals(call.getNameAsString()))
        .forEach(call -> call.getArguments().forEach(arg -> addFieldFromArg(arg, fields)));
    // Look for field access in arithmetic expressions (31 * field.hashCode())
    method
        .findAll(FieldAccessExpr.class)
        .forEach(
            fae -> {
              final String fieldName = fae.getNameAsString();
              if (!FIELD_CLASS.equals(fieldName)) {
                fields.add(fieldName);
              }
            });
    // Look for simple name expressions used in calculations
    method
        .findAll(NameExpr.class)
        .forEach(
            ne -> {
              final String name = ne.getNameAsString();
              // Filter out common non-field names
              if (!LOCAL_RESULT.equals(name) && !LOCAL_PRIME.equals(name) && !LOCAL_HASH.equals(name)) {
                fields.add(name);
              }
            });
    return new ArrayList<>(fields);
  }

  /** Extracts constructor parameter pattern for better test generation. */
  String extractConstructorPattern(final CompilationUnit cu) {
    // Find constructors matching the name
    final var constructors =
        cu.findAll(com.github.javaparser.ast.body.ConstructorDeclaration.class);
    if (constructors.isEmpty()) {
      return null;
    }
    // Build a description of constructor parameters
    final StringBuilder pattern = new StringBuilder();
    for (final var constructor : constructors) {
      if (!constructor.getParameters().isEmpty()) {
        pattern.append("Constructor(");
        constructor
            .getParameters()
            .forEach(
                p -> {
                  if (pattern.length() > 12) {
                    pattern.append(", ");
                  }
                  pattern.append(p.getTypeAsString()).append(" ").append(p.getNameAsString());
                });
        pattern.append(")");
        // Take the first non-default constructor
        break;
      }
    }
    return pattern.isEmpty() ? null : pattern.toString();
  }

  /** Helper to extract field name from an expression string. */
  private Optional<String> extractFieldName(final String expr) {
    if (expr == null || expr.isBlank()) {
      return Optional.empty();
    }
    if (expr.contains(".")) {
      return extractFromPath(expr);
    }
    return extractSimpleField(expr);
  }

  private Optional<String> extractSimpleField(final String expr) {
    if (isValidField(expr)) {
      return Optional.of(expr);
    }
    return Optional.empty();
  }

  private Optional<String> extractFromPath(final String expr) {
    final String[] parts = expr.split("\\.");
    if (parts.length < 2) {
      return Optional.empty();
    }
    final String lastPart = parts[parts.length - 1];
    if (lastPart.contains("(")) {
      return extractFromMethodCallPath(parts, lastPart);
    }
    if (isValidField(lastPart)) {
      return Optional.of(lastPart);
    }
    return Optional.empty();
  }

  private Optional<String> extractFromMethodCallPath(final String[] parts, final String lastPart) {
    final String methodName = lastPart.substring(0, lastPart.indexOf('('));
    if (!isAllowedMethod(methodName)) {
      return Optional.empty();
    }
    final String fieldPart = parts[parts.length - 2];
    if (isValidField(fieldPart)) {
      return Optional.of(fieldPart);
    }
    return Optional.empty();
  }

  private boolean isValidField(final String name) {
    return !name.isEmpty() && Character.isLowerCase(name.charAt(0)) && !name.contains("(");
  }

  private boolean isAllowedMethod(final String methodName) {
    return METHOD_HASH_CODE.equals(methodName)
        || METHOD_EQUALS.equals(methodName)
        || METHOD_TO_STRING.equals(methodName);
  }

  /** Formats the method hints into a string suitable for inclusion in prompts. */
  public String formatHintsForPrompt(final MethodHints hints, final String methodName) {
    if (hints == null || !hints.hasHints()) {
      return "";
    }
    final StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append("\n=== METHOD IMPLEMENTATION HINTS ===\n");
    if (METHOD_TO_STRING.equals(methodName) && hints.toStringFormat() != null) {
      promptBuilder.append("Expected toString() format pattern: ").append(hints.toStringFormat()).append("\n");
      promptBuilder.append("IMPORTANT: Match this exact format in your test assertions.\n");
    }
    if (METHOD_EQUALS.equals(methodName)
        && hints.equalsFields() != null
        && !hints.equalsFields().isEmpty()) {
      promptBuilder.append("Fields compared in equals(): ")
          .append(String.join(", ", hints.equalsFields()))
          .append("\n");
      promptBuilder.append("IMPORTANT: Test cases should verify equality based on these specific fields.\n");
      promptBuilder.append("- Two objects with same values for these fields should be equal.\n");
      promptBuilder.append("- Objects differing in any of these fields should NOT be equal.\n");
    }
    if (METHOD_HASH_CODE.equals(methodName)
        && hints.hashCodeFields() != null
        && !hints.hashCodeFields().isEmpty()) {
      promptBuilder.append("Fields used in hashCode(): ")
          .append(String.join(", ", hints.hashCodeFields()))
          .append("\n");
      promptBuilder.append(
          "IMPORTANT: Objects with same values for these fields should have same hashCode.\n");
      promptBuilder.append("- Verify hashCode consistency: x.hashCode() == x.hashCode() for same object.\n");
      promptBuilder.append("- Verify contract: if x.equals(y), then x.hashCode() == y.hashCode().\n");
    }
    if (hints.constructorPattern() != null) {
      promptBuilder.append("Constructor pattern: ").append(hints.constructorPattern()).append("\n");
    }
    return promptBuilder.toString();
  }
}
