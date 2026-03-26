package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects time-dependent code in tests using AST analysis.
 *
 * <p>Detected patterns:
 *
 * <ul>
 *   <li>System.currentTimeMillis()
 *   <li>System.nanoTime()
 *   <li>Instant.now()
 *   <li>LocalDateTime.now()
 *   <li>LocalDate.now()
 *   <li>LocalTime.now()
 *   <li>ZonedDateTime.now()
 *   <li>OffsetDateTime.now()
 *   <li>Clock.systemDefaultZone() / Clock.systemUTC() / Clock.system()
 * </ul>
 *
 * <p>Time-dependent tests can fail unpredictably based on execution time. Tests should use
 * injectable Clock or fixed time values.
 */
public class TimeRule extends AbstractJavaParserBrittleRule {

  /** Method calls on System class that are time-dependent. */
  private static final Set<String> SYSTEM_TIME_METHODS = Set.of("currentTimeMillis", "nanoTime");

  private static final String SYSTEM_CLASS = "System";

  private static final String CLOCK_CLASS = "Clock";

  private static final String NOW_METHOD = "now";

  private static final String TIME_DEPENDENT_WARNING_TEMPLATE =
      "%s.%s() causes time-dependent tests";

  /** Classes with static now() methods that are time-dependent. */
  private static final Set<String> NOW_CLASSES =
      Set.of(
          "Instant",
          "LocalDateTime",
          "LocalDate",
          "LocalTime",
          "ZonedDateTime",
          "OffsetDateTime",
          "OffsetTime",
          "Year",
          "YearMonth",
          "MonthDay");

  /** Clock factory methods that create system clocks. */
  private static final Set<String> CLOCK_SYSTEM_METHODS =
      Set.of("systemDefaultZone", "systemUTC", "system");

  public TimeRule() {
    this(Severity.WARNING);
  }

  public TimeRule(final Severity severity) {
    super(severity);
  }

  @Override
  public RuleId getRuleId() {
    return RuleId.TIME;
  }

  @Override
  public List<BrittleFinding> checkAst(final CompilationUnit cu, final String filePath) {
    final List<BrittleFinding> findings = new ArrayList<>();
    final Set<String> staticNowTypes = new HashSet<>();
    final Set<String> staticSystemMethods = new HashSet<>();
    final Set<String> staticClockMethods = new HashSet<>();
    collectStaticImports(cu, staticNowTypes, staticSystemMethods, staticClockMethods);
    cu.accept(
        new TimeMethodVisitor(
            filePath,
            getDefaultSeverity(),
            getRuleId(),
            staticNowTypes,
            staticSystemMethods,
            staticClockMethods),
        findings);
    return findings;
  }

  private static void collectStaticImports(
      final CompilationUnit cu,
      final Set<String> staticNowTypes,
      final Set<String> staticSystemMethods,
      final Set<String> staticClockMethods) {
    for (final ImportDeclaration imp : cu.getImports()) {
      if (imp.isStatic()) {
        final String importName = imp.getNameAsString();
        final String simpleType = simpleName(importName);
        if (imp.isAsterisk()) {
          addAsteriskStaticImport(
              simpleType, staticNowTypes, staticSystemMethods, staticClockMethods);
        } else {
          addExplicitStaticImport(
              importName, staticNowTypes, staticSystemMethods, staticClockMethods);
        }
      }
    }
  }

  private static void addAsteriskStaticImport(
      final String simpleType,
      final Set<String> staticNowTypes,
      final Set<String> staticSystemMethods,
      final Set<String> staticClockMethods) {
    if (NOW_CLASSES.contains(simpleType)) {
      staticNowTypes.add(simpleType);
    }
    if (SYSTEM_CLASS.equals(simpleType)) {
      staticSystemMethods.addAll(SYSTEM_TIME_METHODS);
    }
    if (CLOCK_CLASS.equals(simpleType)) {
      staticClockMethods.addAll(CLOCK_SYSTEM_METHODS);
    }
  }

  private static void addExplicitStaticImport(
      final String importName,
      final Set<String> staticNowTypes,
      final Set<String> staticSystemMethods,
      final Set<String> staticClockMethods) {
    final String[] parts = importName.split("\\.");
    if (parts.length < 2) {
      return;
    }
    final String methodName = parts[parts.length - 1];
    final String typeName = parts[parts.length - 2];
    if (NOW_METHOD.equals(methodName) && NOW_CLASSES.contains(typeName)) {
      staticNowTypes.add(typeName);
    }
    if (SYSTEM_CLASS.equals(typeName) && SYSTEM_TIME_METHODS.contains(methodName)) {
      staticSystemMethods.add(methodName);
    }
    if (CLOCK_CLASS.equals(typeName) && CLOCK_SYSTEM_METHODS.contains(methodName)) {
      staticClockMethods.add(methodName);
    }
  }

  /** Visitor that detects time-dependent method calls. */
  private static class TimeMethodVisitor extends MethodCallCollector {

    private final Set<String> staticNowTypes;

    private final Set<String> staticSystemMethods;

    private final Set<String> staticClockMethods;

    TimeMethodVisitor(
        final String filePath,
        final Severity severity,
        final RuleId ruleId,
        final Set<String> staticNowTypes,
        final Set<String> staticSystemMethods,
        final Set<String> staticClockMethods) {
      super(filePath, severity, ruleId);
      this.staticNowTypes = staticNowTypes;
      this.staticSystemMethods = staticSystemMethods;
      this.staticClockMethods = staticClockMethods;
    }

    @Override
    public void visit(final MethodCallExpr expr, final List<BrittleFinding> findings) {
      super.visit(expr, findings);
      final String methodName = expr.getNameAsString();
      final String scope = expr.getScope().map(Object::toString).orElse("");
      final String scopeSimple = simpleName(scope);
      if (isSystemTimeCall(scopeSimple, methodName, expr, findings)) {
        return;
      }
      if (isStaticSystemTimeCall(scope, methodName, expr, findings)) {
        return;
      }
      if (isNowCall(scopeSimple, methodName, expr, findings)) {
        return;
      }
      if (isStaticNowCall(scope, methodName, expr, findings)) {
        return;
      }
      if (isClockSystemCall(scopeSimple, methodName, expr, findings)) {
        return;
      }
      addStaticClockCallFinding(scope, methodName, expr, findings);
    }

    private boolean isSystemTimeCall(
        final String scopeSimple,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!SYSTEM_CLASS.equals(scopeSimple) || !SYSTEM_TIME_METHODS.contains(methodName)) {
        return false;
      }
      findings.add(
          createFinding(
              expr, String.format(TIME_DEPENDENT_WARNING_TEMPLATE, SYSTEM_CLASS, methodName)));
      return true;
    }

    private boolean isStaticSystemTimeCall(
        final String scope,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!scope.isEmpty() || !staticSystemMethods.contains(methodName)) {
        return false;
      }
      if (isShadowedByEnclosingTypeMethod(expr)) {
        return false;
      }
      findings.add(
          createFinding(
              expr, String.format("%s() causes time-dependent tests (static import)", methodName)));
      return true;
    }

    private boolean isNowCall(
        final String scopeSimple,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!NOW_METHOD.equals(methodName) || !NOW_CLASSES.contains(scopeSimple)) {
        return false;
      }
      if (hasClockArgument(expr)) {
        return false;
      }
      findings.add(
          createFinding(
              expr, String.format(TIME_DEPENDENT_WARNING_TEMPLATE, scopeSimple, methodName)));
      return true;
    }

    private boolean isStaticNowCall(
        final String scope,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!scope.isEmpty() || !NOW_METHOD.equals(methodName) || staticNowTypes.isEmpty()) {
        return false;
      }
      if (isShadowedByEnclosingTypeMethod(expr) || hasClockArgument(expr)) {
        return false;
      }
      findings.add(createFinding(expr, "now() causes time-dependent tests (static import)"));
      return true;
    }

    private boolean isClockSystemCall(
        final String scopeSimple,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!CLOCK_CLASS.equals(scopeSimple) || !CLOCK_SYSTEM_METHODS.contains(methodName)) {
        return false;
      }
      findings.add(
          createFinding(
              expr, String.format(TIME_DEPENDENT_WARNING_TEMPLATE, CLOCK_CLASS, methodName)));
      return true;
    }

    private void addStaticClockCallFinding(
        final String scope,
        final String methodName,
        final MethodCallExpr expr,
        final List<BrittleFinding> findings) {
      if (!scope.isEmpty() || !staticClockMethods.contains(methodName)) {
        return;
      }
      if (isShadowedByEnclosingTypeMethod(expr)) {
        return;
      }
      findings.add(
          createFinding(
              expr, String.format("%s() causes time-dependent tests (static import)", methodName)));
    }

    private boolean hasClockArgument(final MethodCallExpr expr) {
      if (expr.getArguments().isEmpty()) {
        return false;
      }
      for (final Expression argument : expr.getArguments()) {
        if (isClockLike(argument)) {
          return true;
        }
      }
      return false;
    }

    private boolean isClockLike(final Expression argument) {
      final String text = argument.toString().toLowerCase(Locale.ROOT);
      if (text.contains("clock")) {
        return true;
      }
      if (argument.isMethodCallExpr()) {
        final MethodCallExpr call = argument.asMethodCallExpr();
        final String scope = call.getScope().map(Object::toString).orElse("");
        return CLOCK_CLASS.equals(simpleName(scope));
      }
      return false;
    }
  }

  private static String simpleName(final String scope) {
    if (scope == null || scope.isBlank()) {
      return "";
    }
    final int lastDot = scope.lastIndexOf('.');
    return lastDot >= 0 ? scope.substring(lastDot + 1) : scope;
  }
}
