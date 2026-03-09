package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.GraphAnalyzer;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MethodKeyUtil;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public final class DependencyGraphBuilder {

  /**
   * Generates a unique key for a method based on its class and signature.
   *
   * @param classFqn The fully qualified class name.
   * @param signature The method signature.
   * @return The unique key.
   */
  public static String methodKey(final String classFqn, final String signature) {
    return MethodKeyUtil.methodKey(classFqn, signature, false);
  }

  /**
   * Collects method calls from a given AST node and updates the analysis context.
   *
   * @param node The AST node to traverse.
   * @param currentMethodKey The key of the method currently being analyzed.
   * @param currentClassFqn The fully qualified name of the class currently being analyzed.
   * @param context The analysis context to update.
   */
  public void collectCalledMethods(
      final Node node,
      final String currentMethodKey,
      final String currentClassFqn,
      final AnalysisContext context) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "node must not be null"));
    Objects.requireNonNull(
        currentMethodKey,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "currentMethodKey must not be null"));
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context must not be null"));
    final var calls = context.getOrCreateCallGraphEntry(currentMethodKey);
    node.walk(
        n -> {
          if (n instanceof MethodCallExpr mce) {
            final var target = resolveMethodCall(mce, currentClassFqn);
            addEdge(target, currentMethodKey, context, calls);
          } else if (n instanceof ObjectCreationExpr oce) {
            final var target = resolveConstructorCall(oce, currentClassFqn);
            addEdge(target, currentMethodKey, context, calls);
          } else if (n instanceof MethodReferenceExpr mre) {
            final var target = resolveMethodReference(mre, currentClassFqn);
            addEdge(target, currentMethodKey, context, calls);
          }
        });
  }

  private void addEdge(
      final ResolvedCall target,
      final String callerKey,
      final AnalysisContext context,
      final Set<String> calls) {
    if (target == null || StringUtils.isBlank(target.signature())) {
      return;
    }
    // 'calls' is already a set from context.callGraph associated with the caller
    calls.add(target.signature());
    context.getIncomingCounts().merge(target.signature(), 1, (a, b) -> a + b);
    context.recordCallStatus(callerKey, target.signature(), target.status());
    context.recordCallArgumentLiterals(callerKey, target.signature(), target.argumentLiterals());
  }

  private ResolvedCall resolveMethodCall(final MethodCallExpr mce, final String currentClassFqn) {
    try {
      final var resolved = mce.resolve();
      final var declaring =
          resolved.getPackageName().isEmpty()
              ? resolved.getClassName()
              : resolved.getPackageName() + "." + resolved.getClassName();
      return new ResolvedCall(
          methodKey(declaring, resolved.getSignature()),
          ResolutionStatus.RESOLVED,
          extractArgumentLiterals(mce.getArguments()));
    } catch (RuntimeException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to resolve method call '" + mce + "': " + e.getMessage()));
    }
    final var scopeType = resolveScopeType(mce.getScope().orElse(null));
    final var signature = mce.getNameAsString() + "(" + mce.getArguments().size() + ")";
    final var classPart =
        StringUtils.isNotBlank(scopeType.name()) ? scopeType.name() : currentClassFqn;
    final ResolutionStatus status =
        scopeType.resolved() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
    return new ResolvedCall(
        methodKey(classPart, signature), status, extractArgumentLiterals(mce.getArguments()));
  }

  private ResolvedCall resolveConstructorCall(
      final ObjectCreationExpr oce, final String currentClassFqn) {
    try {
      final var resolved = oce.resolve();
      final var declaring = resolved.declaringType().getQualifiedName();
      return new ResolvedCall(
          methodKey(declaring, resolved.getSignature()),
          ResolutionStatus.RESOLVED,
          extractArgumentLiterals(oce.getArguments()));
    } catch (RuntimeException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Falling back to type-based constructor resolution for '" + oce + "'"));
    }
    try {
      final var resolvedType = oce.getType().resolve();
      final var className = resolvedType.describe();
      final var signature =
          oce.getType().getNameWithScope() + "(" + oce.getArguments().size() + ")";
      return new ResolvedCall(
          methodKey(className, signature),
          ResolutionStatus.RESOLVED,
          extractArgumentLiterals(oce.getArguments()));
    } catch (RuntimeException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to resolve constructor call '" + oce + "': " + e.getMessage()));
    }
    var className = oce.getType().getNameWithScope();
    final var signature = className + "(" + oce.getArguments().size() + ")";
    if (StringUtils.isBlank(className)) {
      className = currentClassFqn;
    }
    return new ResolvedCall(
        methodKey(className, signature),
        ResolutionStatus.UNRESOLVED,
        extractArgumentLiterals(oce.getArguments()));
  }

  private ResolvedCall resolveMethodReference(
      final MethodReferenceExpr mre, final String currentClassFqn) {
    try {
      final var resolved = mre.resolve();
      final var declaring =
          resolved.getPackageName().isEmpty()
              ? resolved.getClassName()
              : resolved.getPackageName() + "." + resolved.getClassName();
      return new ResolvedCall(
          methodKey(declaring, resolved.getSignature()), ResolutionStatus.RESOLVED, List.of());
    } catch (RuntimeException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to resolve method reference '" + mre + "': " + e.getMessage()));
    }
    final var scopeType = resolveScopeType(mre.getScope());
    // Heuristic: create a signature with wildcard args since we can't easily infer
    // count
    final var signature = mre.getIdentifier() + "(?)";
    final var classPart =
        StringUtils.isNotBlank(scopeType.name()) ? scopeType.name() : currentClassFqn;
    final ResolutionStatus status =
        scopeType.resolved() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
    return new ResolvedCall(methodKey(classPart, signature), status, List.of());
  }

  private List<String> extractArgumentLiterals(final NodeList<Expression> arguments) {
    if (arguments == null || arguments.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> literals = new LinkedHashSet<>();
    for (final Expression argument : arguments) {
      final String literal = extractLiteralArgument(argument);
      if (literal == null || literal.isBlank()) {
        continue;
      }
      literals.add(literal);
    }
    if (literals.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(literals);
  }

  private String extractLiteralArgument(final Expression argument) {
    if (argument == null) {
      return null;
    }
    Expression current = argument;
    while (current.isEnclosedExpr()) {
      current = current.asEnclosedExpr().getInner();
    }
    if (current.isLiteralExpr()) {
      return normalizeLiteralSnippet(current.toString());
    }
    if (current.isUnaryExpr()) {
      final UnaryExpr unary = current.asUnaryExpr();
      if ((unary.getOperator() == UnaryExpr.Operator.MINUS
              || unary.getOperator() == UnaryExpr.Operator.PLUS)
          && unary.getExpression() != null
          && unary.getExpression().isLiteralExpr()) {
        return normalizeLiteralSnippet(unary.toString());
      }
    }
    return null;
  }

  private String normalizeLiteralSnippet(final String literal) {
    if (literal == null || literal.isBlank()) {
      return "";
    }
    final String normalized = literal.replaceAll("\\s+", " ").strip();
    if (normalized.length() <= 120) {
      return normalized;
    }
    return normalized.substring(0, 117) + "...";
  }

  private ResolvedType resolveScopeType(final Expression scope) {
    if (scope == null) {
      return new ResolvedType(null, false);
    }
    try {
      final var t = scope.calculateResolvedType();
      if (t.isReferenceType()) {
        return new ResolvedType(t.asReferenceType().getQualifiedName(), true);
      }
      return new ResolvedType(t.describe(), true);
    } catch (Exception e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to resolve scope type '" + scope + "': " + e.getMessage()));
      return new ResolvedType(scope.toString(), false);
    }
  }

  /**
   * Detects cycles in the call graph and marks methods involved in cycles. Uses Tarjan's strongly
   * connected components algorithm.
   *
   * @param context The analysis context containing the call graph.
   */
  public void markCycles(final AnalysisContext context) {
    GraphAnalyzer.markCycles(context);
  }

  private record ResolvedCall(
      String signature, ResolutionStatus status, List<String> argumentLiterals) {}

  private record ResolvedType(String name, boolean resolved) {}
}
