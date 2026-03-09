package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects random number generation in tests using AST analysis.
 *
 * <p>Detected patterns:
 *
 * <ul>
 *   <li>new Random()
 *   <li>new SecureRandom()
 *   <li>ThreadLocalRandom.current()
 *   <li>UUID.randomUUID()
 *   <li>Math.random()
 * </ul>
 *
 * <p>Random values in tests cause non-deterministic behavior. Tests should use fixed seeds or
 * predetermined values for reproducibility.
 */
public class RandomRule extends AbstractJavaParserBrittleRule {

  /** Classes that produce random values when instantiated. */
  private static final Set<String> RANDOM_CLASSES =
      Set.of("Random", "SecureRandom", "SplittableRandom");

  private static final String TYPE_UUID = "UUID";

  private static final String TYPE_THREAD_LOCAL_RANDOM = "ThreadLocalRandom";

  private static final String TYPE_MATH = "Math";

  private static final String METHOD_RANDOM_UUID = "randomUUID";

  private static final String METHOD_CURRENT = "current";

  private static final String METHOD_RANDOM = "random";

  public RandomRule() {
    this(Severity.WARNING);
  }

  public RandomRule(final Severity severity) {
    super(severity);
  }

  @Override
  public RuleId getRuleId() {
    return RuleId.RANDOM;
  }

  @Override
  public List<BrittleFinding> checkAst(final CompilationUnit cu, final String filePath) {
    final List<BrittleFinding> findings = new ArrayList<>();
    final StaticImportConfig staticImports = resolveStaticImports(cu);
    cu.accept(new RandomVisitor(filePath, getDefaultSeverity(), getRuleId(), staticImports), findings);
    return findings;
  }

  /** Visitor that detects random-related code. */
  private static class RandomVisitor extends VoidVisitorAdapter<List<BrittleFinding>> {

    private final String filePath;

    private final Severity severity;

    private final RuleId ruleId;

    private final StaticImportConfig staticImports;

    RandomVisitor(
        final String filePath,
        final Severity severity,
        final RuleId ruleId,
        final StaticImportConfig staticImports) {
      this.filePath = filePath;
      this.severity = severity;
      this.ruleId = ruleId;
      this.staticImports = staticImports;
    }

    @Override
    public void visit(final ObjectCreationExpr expr, final List<BrittleFinding> findings) {
      super.visit(expr, findings);
      final String typeName = expr.getType().getNameAsString();
      // Check new Random(), new SecureRandom(), etc.
      if (RANDOM_CLASSES.contains(typeName)) {
        findings.add(
            createFinding(
                expr, String.format("new %s() causes non-deterministic tests", typeName)));
      }
    }

    @Override
    public void visit(final MethodCallExpr expr, final List<BrittleFinding> findings) {
      super.visit(expr, findings);
      final String methodName = expr.getNameAsString();
      final String scope = expr.getScope().map(Object::toString).orElse("");
      final String scopeSimple = simpleName(scope);
      // Check UUID.randomUUID()
      if (TYPE_UUID.equals(scopeSimple) && METHOD_RANDOM_UUID.equals(methodName)) {
        findings.add(createFinding(expr, "UUID.randomUUID() causes non-deterministic tests"));
        return;
      }
      // Check ThreadLocalRandom.current()
      if (TYPE_THREAD_LOCAL_RANDOM.equals(scopeSimple) && METHOD_CURRENT.equals(methodName)) {
        findings.add(
            createFinding(expr, "ThreadLocalRandom.current() causes non-deterministic tests"));
        return;
      }
      // Check Math.random()
      if (TYPE_MATH.equals(scopeSimple) && METHOD_RANDOM.equals(methodName)) {
        findings.add(createFinding(expr, "Math.random() causes non-deterministic tests"));
        return;
      }
      if (isShadowedByEnclosingTypeMethod(expr)) {
        return;
      }
      if (scope.isEmpty()
          && METHOD_RANDOM_UUID.equals(methodName)
          && staticImports.uuidRandomUuid) {
        findings.add(
            createFinding(expr, "randomUUID() causes non-deterministic tests (static import)"));
        return;
      }
      if (scope.isEmpty()
          && METHOD_CURRENT.equals(methodName)
          && staticImports.threadLocalRandomCurrent) {
        findings.add(
            createFinding(expr, "current() causes non-deterministic tests (static import)"));
        return;
      }
      if (scope.isEmpty() && METHOD_RANDOM.equals(methodName) && staticImports.mathRandom) {
        findings.add(
            createFinding(expr, "random() causes non-deterministic tests (static import)"));
      }
    }

    private BrittleFinding createFinding(final MethodCallExpr expr, final String message) {
      final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
      return new BrittleFinding(ruleId, severity, filePath, lineNumber, message, expr.toString());
    }

    private BrittleFinding createFinding(final ObjectCreationExpr expr, final String message) {
      final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
      return new BrittleFinding(ruleId, severity, filePath, lineNumber, message, expr.toString());
    }
  }

  private static StaticImportConfig resolveStaticImports(final CompilationUnit cu) {
    final StaticImportConfigBuilder builder = new StaticImportConfigBuilder();
    for (final ImportDeclaration imp : cu.getImports()) {
      if (imp.isStatic()) {
        builder.apply(imp);
      }
    }
    return builder.build();
  }

  private static String simpleName(final String scope) {
    if (scope == null || scope.isBlank()) {
      return "";
    }
    final int lastDot = scope.lastIndexOf('.');
    return lastDot >= 0 ? scope.substring(lastDot + 1) : scope;
  }

  private record StaticImportConfig(
      boolean uuidRandomUuid, boolean threadLocalRandomCurrent, boolean mathRandom) {}

  private static final class StaticImportConfigBuilder {

    private boolean uuidRandomUuid;

    private boolean threadLocalRandomCurrent;

    private boolean mathRandom;

    private void apply(final ImportDeclaration imp) {
      final String importName = imp.getNameAsString();
      if (imp.isAsterisk()) {
        applyWildcard(importName);
      } else {
        applySpecific(importName);
      }
    }

    private void applyWildcard(final String importName) {
      final String simpleType = simpleName(importName);
      if (TYPE_UUID.equals(simpleType)) {
        uuidRandomUuid = true;
      }
      if (TYPE_THREAD_LOCAL_RANDOM.equals(simpleType)) {
        threadLocalRandomCurrent = true;
      }
      if (TYPE_MATH.equals(simpleType)) {
        mathRandom = true;
      }
    }

    private void applySpecific(final String importName) {
      final String[] parts = importName.split("\\.");
      if (parts.length < 2) {
        return;
      }
      final String methodName = parts[parts.length - 1];
      final String typeName = parts[parts.length - 2];
      if (TYPE_UUID.equals(typeName) && METHOD_RANDOM_UUID.equals(methodName)) {
        uuidRandomUuid = true;
      }
      if (TYPE_THREAD_LOCAL_RANDOM.equals(typeName) && METHOD_CURRENT.equals(methodName)) {
        threadLocalRandomCurrent = true;
      }
      if (TYPE_MATH.equals(typeName) && METHOD_RANDOM.equals(methodName)) {
        mathRandom = true;
      }
    }

    private StaticImportConfig build() {
      return new StaticImportConfig(uuidRandomUuid, threadLocalRandomCurrent, mathRandom);
    }
  }
}
