package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.List;

/**
 * Abstract base class for JavaParser-based brittle test detection rules.
 *
 * <p>This class uses JavaParser to analyze test code at the AST level, avoiding false positives
 * from comments, string literals, or import statements that regex-based detection would catch.
 *
 * <h2>Benefits over Regex</h2>
 *
 * <ul>
 *   <li>Ignores commented-out code
 *   <li>Ignores string literals containing method names
 *   <li>Provides accurate line numbers from AST nodes
 *   <li>Can distinguish between import statements and actual usage
 * </ul>
 *
 * @see BrittleRule
 */
public abstract class AbstractJavaParserBrittleRule implements BrittleRule {

  private final Severity severity;

  protected AbstractJavaParserBrittleRule(final Severity severity) {
    this.severity = severity;
  }

  @Override
  public Severity getDefaultSeverity() {
    return severity;
  }

  /**
   * Check using AST. Subclasses should override this method.
   *
   * @param cu the parsed CompilationUnit
   * @param filePath the file path for reporting
   * @return list of findings
   */
  public abstract List<BrittleFinding> checkAst(CompilationUnit cu, String filePath);

  /**
   * Legacy regex-based check. Not supported for AST-only rules.
   *
   * <p>This method exists for backward compatibility with the interface, but AST-based rules
   * require a parsed {@link CompilationUnit}. The adapter should call {@link #checkAst} instead.
   */
  @Override
  public List<BrittleFinding> check(
      final String filePath, final String content, final List<String> lines) {
    throw new UnsupportedOperationException(
        "AST-based rule requires CompilationUnit; use checkAst() instead.");
  }

  /**
   * Helper to create a finding from a MethodCallExpr.
   *
   * @param expr the method call expression
   * @param filePath the file path
   * @param message the finding message
   * @return a BrittleFinding
   */
  protected BrittleFinding createFinding(
      final MethodCallExpr expr, final String filePath, final String message) {
    final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
    final String evidence = expr.toString();
    return new BrittleFinding(getRuleId(), severity, filePath, lineNumber, message, evidence);
  }

  /**
   * Helper to create a finding from an ObjectCreationExpr.
   *
   * @param expr the object creation expression
   * @param filePath the file path
   * @param message the finding message
   * @return a BrittleFinding
   */
  protected BrittleFinding createFinding(
      final ObjectCreationExpr expr, final String filePath, final String message) {
    final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
    final String evidence = expr.toString();
    return new BrittleFinding(getRuleId(), severity, filePath, lineNumber, message, evidence);
  }

  protected static boolean isShadowedByEnclosingTypeMethod(final MethodCallExpr expr) {
    if (expr.getScope().isPresent()) {
      return false;
    }
    final String methodName = expr.getNameAsString();
    final NodeList<Expression> arguments = expr.getArguments();
    Node current = expr;
    while (current != null) {
      if (current instanceof TypeDeclaration<?> typeDeclaration
          && hasApplicableMethodNamed(typeDeclaration.getMethodsByName(methodName), arguments)) {
        return true;
      }
      if (current instanceof ObjectCreationExpr objectCreationExpr
          && hasAnonymousMethodNamed(objectCreationExpr, methodName, arguments)) {
        return true;
      }
      current = current.getParentNode().orElse(null);
    }
    return false;
  }

  private static boolean hasAnonymousMethodNamed(
      final ObjectCreationExpr objectCreationExpr,
      final String methodName,
      final NodeList<Expression> arguments) {
    return objectCreationExpr.getAnonymousClassBody().stream()
        .flatMap(List::stream)
        .filter(BodyDeclaration::isMethodDeclaration)
        .map(BodyDeclaration::asMethodDeclaration)
        .filter(methodDeclaration -> methodName.equals(methodDeclaration.getNameAsString()))
        .anyMatch(methodDeclaration -> isPotentiallyApplicable(methodDeclaration, arguments));
  }

  private static boolean hasApplicableMethodNamed(
      final List<MethodDeclaration> methodDeclarations, final NodeList<Expression> arguments) {
    return methodDeclarations.stream()
        .anyMatch(methodDeclaration -> isPotentiallyApplicable(methodDeclaration, arguments));
  }

  private static boolean isPotentiallyApplicable(
      final MethodDeclaration methodDeclaration, final NodeList<Expression> arguments) {
    final int argumentCount = arguments.size();
    final int parameterCount = methodDeclaration.getParameters().size();
    final boolean hasVarArgs =
        parameterCount > 0 && methodDeclaration.getParameter(parameterCount - 1).isVarArgs();
    if (hasVarArgs) {
      if (argumentCount < parameterCount - 1) {
        return false;
      }
      for (int index = 0; index < parameterCount - 1; index++) {
        if (!isArgumentPotentiallyCompatible(
            methodDeclaration.getParameter(index), arguments.get(index))) {
          return false;
        }
      }
      final Type elementType =
          extractVarArgElementType(methodDeclaration.getParameter(parameterCount - 1));
      for (int index = parameterCount - 1; index < argumentCount; index++) {
        if (!isExpressionPotentiallyCompatible(elementType, arguments.get(index))) {
          return false;
        }
      }
      return true;
    }
    if (parameterCount != argumentCount) {
      return false;
    }
    for (int index = 0; index < parameterCount; index++) {
      if (!isArgumentPotentiallyCompatible(
          methodDeclaration.getParameter(index), arguments.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isArgumentPotentiallyCompatible(
      final Parameter parameter, final Expression expression) {
    return isExpressionPotentiallyCompatible(parameter.getType(), expression);
  }

  private static Type extractVarArgElementType(final Parameter parameter) {
    final Type parameterType = parameter.getType();
    if (parameterType.isArrayType()) {
      return parameterType.asArrayType().getComponentType();
    }
    return parameterType;
  }

  private static boolean isExpressionPotentiallyCompatible(
      final Type parameterType, final Expression expression) {
    final InferredExpressionType inferredExpressionType = inferExpressionType(expression);
    return inferredExpressionType == null
        || inferredExpressionType.isPotentiallyCompatibleWith(parameterType);
  }

  private static InferredExpressionType inferExpressionType(final Expression expression) {
    if (expression.isEnclosedExpr()) {
      return inferExpressionType(expression.asEnclosedExpr().getInner());
    }
    if (expression.isCastExpr()) {
      return null;
    }
    if (expression.isNullLiteralExpr()) {
      return InferredExpressionType.NULL;
    }
    if (expression.isBooleanLiteralExpr()) {
      return InferredExpressionType.BOOLEAN;
    }
    if (expression.isStringLiteralExpr() || expression.isTextBlockLiteralExpr()) {
      return InferredExpressionType.STRING;
    }
    if (expression.isCharLiteralExpr()) {
      return InferredExpressionType.CHARACTER;
    }
    if (expression.isLongLiteralExpr()) {
      return InferredExpressionType.LONG;
    }
    if (expression.isDoubleLiteralExpr()) {
      return InferredExpressionType.DOUBLE;
    }
    if (expression.isIntegerLiteralExpr()) {
      return InferredExpressionType.INTEGER;
    }
    if (expression.isClassExpr()) {
      return InferredExpressionType.CLASS_LITERAL;
    }
    if (expression.isUnaryExpr()) {
      return inferUnaryExpressionType(expression.asUnaryExpr());
    }
    return null;
  }

  private static InferredExpressionType inferUnaryExpressionType(final UnaryExpr unaryExpr) {
    return switch (unaryExpr.getOperator()) {
      case MINUS, PLUS -> inferExpressionType(unaryExpr.getExpression());
      case LOGICAL_COMPLEMENT -> InferredExpressionType.BOOLEAN;
      default -> null;
    };
  }

  private enum InferredExpressionType {
    NULL,
    BOOLEAN,
    STRING,
    CHARACTER,
    INTEGER,
    LONG,
    DOUBLE,
    CLASS_LITERAL;

    private boolean isPotentiallyCompatibleWith(final Type parameterType) {
      if (parameterType.isPrimitiveType()) {
        return isCompatibleWithPrimitive(parameterType.asPrimitiveType());
      }
      final String simpleName = simpleTypeName(parameterType);
      if (simpleName.isEmpty() || "Object".equals(simpleName)) {
        return true;
      }
      return switch (this) {
        case NULL -> true;
        case BOOLEAN -> "Boolean".equals(simpleName);
        case STRING -> "String".equals(simpleName) || "CharSequence".equals(simpleName);
        case CHARACTER -> "Character".equals(simpleName);
        case INTEGER ->
            isOneOf(simpleName, "Byte", "Short", "Integer", "Long", "Float", "Double", "Number");
        case LONG -> isOneOf(simpleName, "Long", "Float", "Double", "Number");
        case DOUBLE -> isOneOf(simpleName, "Double", "Number");
        case CLASS_LITERAL -> "Class".equals(simpleName);
      };
    }

    private boolean isCompatibleWithPrimitive(final PrimitiveType primitiveType) {
      return switch (this) {
        case NULL -> false;
        case BOOLEAN -> primitiveType.getType() == PrimitiveType.Primitive.BOOLEAN;
        case STRING, CLASS_LITERAL -> false;
        case CHARACTER -> primitiveType.getType() == PrimitiveType.Primitive.CHAR;
        case INTEGER ->
            isOneOf(
                primitiveType.asString(),
                "byte",
                "short",
                "char",
                "int",
                "long",
                "float",
                "double");
        case LONG -> isOneOf(primitiveType.asString(), "long", "float", "double");
        case DOUBLE -> "double".equals(primitiveType.asString());
      };
    }
  }

  private static boolean isOneOf(final String value, final String... candidates) {
    for (final String candidate : candidates) {
      if (candidate.equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static String simpleTypeName(final Type type) {
    if (type.isClassOrInterfaceType()) {
      return type.asClassOrInterfaceType().getName().asString();
    }
    if (type.isArrayType()) {
      return simpleTypeName(type.asArrayType().getComponentType()) + "[]";
    }
    if (type.isPrimitiveType()) {
      return type.asPrimitiveType().asString();
    }
    return "";
  }

  /**
   * Base visitor for collecting method call findings.
   *
   * <p>Subclasses can extend this to add specific detection logic.
   */
  protected abstract static class MethodCallCollector
      extends VoidVisitorAdapter<List<BrittleFinding>> {

    protected final String filePath;

    protected final Severity severity;

    protected final RuleId ruleId;

    protected MethodCallCollector(
        final String filePath, final Severity severity, final RuleId ruleId) {
      this.filePath = filePath;
      this.severity = severity;
      this.ruleId = ruleId;
    }

    protected BrittleFinding createFinding(final MethodCallExpr expr, final String message) {
      final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
      return new BrittleFinding(ruleId, severity, filePath, lineNumber, message, expr.toString());
    }

    protected BrittleFinding createFinding(final ObjectCreationExpr expr, final String message) {
      final int lineNumber = expr.getBegin().map(pos -> pos.line).orElse(-1);
      return new BrittleFinding(ruleId, severity, filePath, lineNumber, message, expr.toString());
    }
  }
}
