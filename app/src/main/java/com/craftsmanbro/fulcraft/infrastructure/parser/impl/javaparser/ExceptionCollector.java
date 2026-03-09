package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Helper for collecting thrown exceptions from JavaParser AST nodes. */
public final class ExceptionCollector {

  private ExceptionCollector() {}

  /**
   * Collects all thrown exception types from a method or constructor node.
   *
   * @param node The method or constructor node to analyze. Must not be null.
   * @return A list of thrown exception type names. Returns directly thrown types and explicitly
   *     declared throws.
   */
  public static List<String> collectThrownExceptions(final Node node) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "node must not be null"));
    final var exceptions = new LinkedHashSet<String>();
    if (node instanceof MethodDeclaration md) {
      md.getThrownExceptions().forEach(t -> exceptions.add(t.asString()));
    } else if (node instanceof ConstructorDeclaration cd) {
      cd.getThrownExceptions().forEach(t -> exceptions.add(t.asString()));
    }
    node.findAll(ThrowStmt.class)
        .forEach(
            ts -> {
              final var typeName = resolveThrowType(ts);
              if (!typeName.isBlank()) {
                exceptions.add(typeName);
              }
            });
    return List.copyOf(exceptions);
  }

  /** Resolves the type of a throw statement. */
  public static String resolveThrowType(final ThrowStmt throwStmt) {
    Objects.requireNonNull(
        throwStmt,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "throwStmt must not be null"));
    final var expression = throwStmt.getExpression();
    if (expression == null) {
      return "";
    }
    if (expression instanceof ObjectCreationExpr oce) {
      return oce.getType().getNameWithScope();
    }
    try {
      final var type = expression.calculateResolvedType();
      if (type.isReferenceType()) {
        return type.asReferenceType().getQualifiedName();
      }
      return type.describe();
    } catch (Exception e) {
      // Best-effort: symbol resolution may fail without a full project classpath.
      com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger.debug(
          "Failed to resolve throw type: " + e.getMessage());
    }
    return expression.toString();
  }
}
