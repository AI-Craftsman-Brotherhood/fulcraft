package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects Reflection usage in test code using AST analysis.
 *
 * <p>Detected patterns:
 *
 * <ul>
 *   <li>import java.lang.reflect.* (warning only - usage is what matters)
 *   <li>setAccessible(true) calls
 *   <li>getDeclaredField(...) calls
 *   <li>getDeclaredMethod(...) calls
 *   <li>getDeclaredConstructor(...) calls
 *   <li>Field/Method/Constructor.setAccessible(...) calls
 * </ul>
 *
 * <p>Reflection in tests typically indicates accessing private/internal implementation details,
 * which makes tests brittle and tightly coupled to implementation.
 */
public class ReflectionRule extends AbstractJavaParserBrittleRule {

  /** Method names that strongly indicate reflection usage. */
  private static final Set<String> STRONG_REFLECTION_METHODS =
      Set.of(
          "setAccessible",
          "getDeclaredField",
          "getDeclaredFields",
          "getDeclaredMethod",
          "getDeclaredMethods",
          "getDeclaredConstructor",
          "getDeclaredConstructors");

  /** Method names that need context to avoid false positives. */
  private static final Set<String> CONTEXTUAL_REFLECTION_METHODS = Set.of("getField", "getMethod");

  /** Class-level reflection methods. */
  private static final Set<String> CLASS_REFLECTION_METHODS = Set.of("forName");

  private static final String JAVA_LANG_CLASS = "java.lang.Class";

  private final boolean warnOnImportOnly;

  public ReflectionRule() {
    this(Severity.ERROR, false);
  }

  public ReflectionRule(final Severity severity) {
    this(severity, false);
  }

  /**
   * Create a ReflectionRule with custom configuration.
   *
   * @param severity the severity level for findings
   * @param warnOnImportOnly if true, also warn on reflection imports (not just usage)
   */
  public ReflectionRule(final Severity severity, final boolean warnOnImportOnly) {
    super(severity);
    this.warnOnImportOnly = warnOnImportOnly;
  }

  @Override
  public RuleId getRuleId() {
    return RuleId.REFLECTION;
  }

  @Override
  public List<BrittleFinding> checkAst(final CompilationUnit cu, final String filePath) {
    final List<BrittleFinding> findings = new ArrayList<>();
    final boolean hasReflectionImport = hasReflectionImport(cu);
    final boolean hasClassStaticImport = hasClassStaticImport(cu);
    final boolean usesClassType = usesJavaLangClassType(cu);
    // Optionally check for reflection imports
    if (warnOnImportOnly) {
      for (final ImportDeclaration imp : cu.getImports()) {
        final String importName = imp.getNameAsString();
        if (importName.startsWith("java.lang.reflect")) {
          final int lineNumber = imp.getBegin().map(pos -> pos.line).orElse(-1);
          findings.add(
              new BrittleFinding(
                  getRuleId(), // Imports are warnings, actual usage is error
                  Severity.WARNING,
                  filePath,
                  lineNumber,
                  "Reflection import detected - review for potential brittle test patterns",
                  imp.toString().trim()));
        }
      }
    }
    // Check for actual reflection method usage
    cu.accept(
        new ReflectionMethodVisitor(
            filePath,
            getDefaultSeverity(),
            getRuleId(),
            hasReflectionImport,
            hasClassStaticImport,
            usesClassType),
        findings);
    return findings;
  }

  private boolean hasReflectionImport(final CompilationUnit cu) {
    return cu.getImports().stream()
        .map(ImportDeclaration::getNameAsString)
        .anyMatch(
            name -> name.startsWith("java.lang.reflect") || name.startsWith("java.lang.invoke"));
  }

  private boolean hasClassStaticImport(final CompilationUnit cu) {
    return cu.getImports().stream()
        .filter(ImportDeclaration::isStatic)
        .map(ImportDeclaration::getNameAsString)
        .anyMatch(name -> JAVA_LANG_CLASS.equals(name) || name.startsWith(JAVA_LANG_CLASS + "."));
  }

  private boolean usesJavaLangClassType(final CompilationUnit cu) {
    final boolean hasNonJavaLangClassImport =
        cu.getImports().stream()
            .filter(imp -> !imp.isStatic())
            .map(ImportDeclaration::getNameAsString)
            .anyMatch(name -> name.endsWith(".Class") && !JAVA_LANG_CLASS.equals(name));
    if (hasNonJavaLangClassImport) {
      return false;
    }
    return cu.findAll(ClassOrInterfaceType.class).stream()
        .map(ClassOrInterfaceType::getNameWithScope)
        .anyMatch(name -> "Class".equals(name) || JAVA_LANG_CLASS.equals(name));
  }

  /** Visitor that detects reflection-related method calls. */
  private static class ReflectionMethodVisitor extends MethodCallCollector {

    private final boolean hasReflectionImport;

    private final boolean hasClassStaticImport;

    private final boolean usesClassType;

    ReflectionMethodVisitor(
        final String filePath,
        final Severity severity,
        final RuleId ruleId,
        final boolean hasReflectionImport,
        final boolean hasClassStaticImport,
        final boolean usesClassType) {
      super(filePath, severity, ruleId);
      this.hasReflectionImport = hasReflectionImport;
      this.hasClassStaticImport = hasClassStaticImport;
      this.usesClassType = usesClassType;
    }

    @Override
    public void visit(final MethodCallExpr expr, final List<BrittleFinding> findings) {
      super.visit(expr, findings);
      final String methodName = expr.getNameAsString();
      if (isShadowedByEnclosingTypeMethod(expr)) {
        return;
      }
      if (STRONG_REFLECTION_METHODS.contains(methodName)) {
        final String message = getMessageForMethod(methodName);
        findings.add(createFinding(expr, message));
        return;
      }
      if (CLASS_REFLECTION_METHODS.contains(methodName)) {
        if (isClassScope(expr) || hasClassStaticImport) {
          final String message = getMessageForMethod(methodName);
          findings.add(createFinding(expr, message));
        }
        return;
      }
      if (CONTEXTUAL_REFLECTION_METHODS.contains(methodName) && isLikelyReflectionContext(expr)) {
        final String message = getMessageForMethod(methodName);
        findings.add(createFinding(expr, message));
      }
    }

    private String getMessageForMethod(final String methodName) {
      return switch (methodName) {
        case "setAccessible" ->
            "setAccessible() bypasses access control - tests should not rely on private internals";
        case "getDeclaredField", "getDeclaredFields", "getField" ->
            "Field access via reflection - tests should use public API";
        case "getDeclaredMethod", "getDeclaredMethods", "getMethod" ->
            "Method access via reflection - tests should use public API";
        case "getDeclaredConstructor", "getDeclaredConstructors" ->
            "getDeclaredConstructor() accesses private constructors - tests should use public API";
        case "forName" -> "Class.forName() loads classes reflectively - tests should avoid this";
        default -> "Reflection usage detected - tests should not rely on private internals";
      };
    }

    private boolean isLikelyReflectionContext(final MethodCallExpr expr) {
      return hasReflectionImport
          || usesClassType
          || isClassScope(expr)
          || isClassLiteralScope(expr)
          || isGetClassScope(expr);
    }

    private boolean isClassScope(final MethodCallExpr expr) {
      return expr.getScope()
          .map(
              scope -> "Class".equals(scope.toString()) || JAVA_LANG_CLASS.equals(scope.toString()))
          .orElse(false);
    }

    private boolean isClassLiteralScope(final MethodCallExpr expr) {
      return expr.getScope().map(scope -> scope.toString().endsWith(".class")).orElse(false);
    }

    private boolean isGetClassScope(final MethodCallExpr expr) {
      return expr.getScope().map(scope -> scope.toString().contains("getClass()")).orElse(false);
    }
  }
}
