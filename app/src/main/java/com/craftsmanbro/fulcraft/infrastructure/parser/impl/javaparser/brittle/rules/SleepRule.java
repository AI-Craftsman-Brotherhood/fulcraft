package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detects Thread.sleep or TimeUnit.sleep usage in test code using AST analysis.
 *
 * <p>Detected patterns:
 *
 * <ul>
 *   <li>Thread.sleep(...)
 *   <li>TimeUnit.SECONDS.sleep(...) (and other TimeUnit constants)
 *   <li>Thread.currentThread().sleep(...) (deprecated but sometimes used)
 * </ul>
 *
 * <p>Sleep in tests causes flakiness due to timing dependencies and slows down test execution.
 * Tests should use deterministic waiting mechanisms (e.g., CountDownLatch, Awaitility, or mocked
 * Clock).
 */
public class SleepRule extends AbstractJavaParserBrittleRule {

  private static final String THREAD_SIMPLE_NAME = "Thread";

  private static final String METHOD_SLEEP = "sleep";

  private static final String METHOD_CURRENT_THREAD = "currentThread";

  /** TimeUnit constants that have sleep methods. */
  private static final Set<String> TIMEUNIT_CONSTANTS =
      Set.of("NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS");

  private record ThreadImportFlags(
      boolean hasStaticThreadSleep, boolean hasStaticThreadCurrentThread) {}

  public SleepRule() {
    this(Severity.ERROR);
  }

  public SleepRule(final Severity severity) {
    super(severity);
  }

  @Override
  public RuleId getRuleId() {
    return RuleId.SLEEP;
  }

  @Override
  public List<BrittleFinding> checkAst(final CompilationUnit cu, final String filePath) {
    final List<BrittleFinding> findings = new ArrayList<>();
    final ThreadImportFlags threadImports = resolveThreadImports(cu.getImports());
    cu.accept(
        new SleepMethodVisitor(
            filePath,
            getDefaultSeverity(),
            getRuleId(),
            threadImports.hasStaticThreadSleep(),
            threadImports.hasStaticThreadCurrentThread()),
        findings);
    return findings;
  }

  private static ThreadImportFlags resolveThreadImports(final List<ImportDeclaration> imports) {
    boolean hasStaticThreadSleep = false;
    boolean hasStaticThreadCurrentThread = false;
    for (final ImportDeclaration imp : imports) {
      if (!imp.isStatic()) {
        continue;
      }
      final String importName = imp.getNameAsString();
      if (imp.isAsterisk()) {
        if (THREAD_SIMPLE_NAME.equals(simpleName(importName))) {
          hasStaticThreadSleep = true;
          hasStaticThreadCurrentThread = true;
        }
      } else {
        final String[] parts = importName.split("\\.");
        if (parts.length >= 2) {
          final String memberName = parts[parts.length - 1];
          final String typeName = parts[parts.length - 2];
          if (THREAD_SIMPLE_NAME.equals(typeName)) {
            if (METHOD_SLEEP.equals(memberName)) {
              hasStaticThreadSleep = true;
            } else if (METHOD_CURRENT_THREAD.equals(memberName)) {
              hasStaticThreadCurrentThread = true;
            }
          }
        }
      }
    }
    return new ThreadImportFlags(hasStaticThreadSleep, hasStaticThreadCurrentThread);
  }

  /** Visitor that detects sleep-related method calls. */
  private static class SleepMethodVisitor extends MethodCallCollector {

    private final boolean hasStaticThreadSleep;

    private final boolean hasStaticThreadCurrentThread;

    SleepMethodVisitor(
        final String filePath,
        final Severity severity,
        final RuleId ruleId,
        final boolean hasStaticThreadSleep,
        final boolean hasStaticThreadCurrentThread) {
      super(filePath, severity, ruleId);
      this.hasStaticThreadSleep = hasStaticThreadSleep;
      this.hasStaticThreadCurrentThread = hasStaticThreadCurrentThread;
    }

    @Override
    public void visit(final MethodCallExpr expr, final List<BrittleFinding> findings) {
      super.visit(expr, findings);
      final String methodName = expr.getNameAsString();
      if (!METHOD_SLEEP.equals(methodName)) {
        return;
      }
      final Optional<Expression> scope = expr.getScope();
      final String scopeValue = scope.map(Object::toString).orElse("");
      final String scopeSimple = simpleName(scopeValue);
      // Check Thread.sleep()
      if (THREAD_SIMPLE_NAME.equals(scopeSimple)) {
        findings.add(createFinding(expr, "Thread.sleep() causes flaky tests"));
        return;
      }
      if (scope.isEmpty()) {
        if (hasStaticThreadSleep && !isShadowedByEnclosingTypeMethod(expr)) {
          findings.add(createFinding(expr, "Thread.sleep() causes flaky tests (static import)"));
        }
        return;
      }
      if (scope.map(this::isThreadCurrentThread).orElse(false)) {
        findings.add(createFinding(expr, "Thread.currentThread().sleep() causes flaky tests"));
        return;
      }
      // Check TimeUnit.*.sleep()
      if (scopeValue.contains("TimeUnit.") || TIMEUNIT_CONSTANTS.contains(scopeSimple)) {
        findings.add(createFinding(expr, "TimeUnit.sleep() causes flaky tests"));
      }
    }

    private boolean isThreadCurrentThread(final Expression scopeExpr) {
      if (!scopeExpr.isMethodCallExpr()) {
        return false;
      }
      final MethodCallExpr callExpr = scopeExpr.asMethodCallExpr();
      if (!METHOD_CURRENT_THREAD.equals(callExpr.getNameAsString())) {
        return false;
      }
      final String callScopeSimple = callExpr.getScope().map(Object::toString).orElse("");
      if (!callScopeSimple.isBlank()) {
        return THREAD_SIMPLE_NAME.equals(simpleName(callScopeSimple));
      }
      return hasStaticThreadCurrentThread && !isShadowedByEnclosingTypeMethod(callExpr);
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
