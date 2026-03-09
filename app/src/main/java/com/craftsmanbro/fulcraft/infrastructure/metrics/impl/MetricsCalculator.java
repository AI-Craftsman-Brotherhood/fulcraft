package com.craftsmanbro.fulcraft.infrastructure.metrics.impl;

import com.craftsmanbro.fulcraft.infrastructure.metrics.contract.CodeMetricsPort;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtDo;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtExecutable;

/**
 * Utility class for calculating code metrics such as Cyclomatic Complexity and Nesting Depth for
 * different AST models (JavaParser, JDT, Spoon).
 */
public final class MetricsCalculator implements CodeMetricsPort {

  private static final MetricsCalculator INSTANCE = new MetricsCalculator();

  private MetricsCalculator() {
    // Utility class
  }

  public static CodeMetricsPort port() {
    return INSTANCE;
  }

  // region JavaParser
  /**
   * Calculates Cyclomatic Complexity for a JavaParser Node. Simple Cyclomatic Complexity: 1 +
   * number of branching points.
   */
  public static int calculateComplexity(final Node node) {
    return INSTANCE.complexityOf(node);
  }

  @Override
  public int complexityOf(final Node node) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Node cannot be null"));
    final var complexity = new AtomicInteger(1);
    node.walk(
        n -> {
          if (n instanceof SwitchStmt sw) {
            final int labels =
                sw.getEntries().stream()
                    .mapToInt(entry -> Math.max(1, entry.getLabels().size()))
                    .sum();
            complexity.addAndGet(Math.max(1, labels));
          } else if (n instanceof IfStmt) {
            complexity.incrementAndGet();
          } else if (n instanceof ForStmt) {
            complexity.incrementAndGet();
          } else if (n instanceof ForEachStmt) {
            complexity.incrementAndGet();
          } else if (n instanceof WhileStmt) {
            complexity.incrementAndGet();
          } else if (n instanceof DoStmt) {
            complexity.incrementAndGet();
          } else if (n instanceof CatchClause) {
            complexity.incrementAndGet();
          } else if (n instanceof ConditionalExpr) {
            complexity.incrementAndGet();
          }
        });
    return complexity.get();
  }

  /** Calculates Maximum Nesting Depth for a JavaParser Node. */
  public static int calculateMaxNestingDepth(final Node node) {
    return INSTANCE.nestingDepthOf(node);
  }

  @Override
  public int nestingDepthOf(final Node node) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "Node cannot be null"));
    final var maxDepth = new AtomicInteger(0);
    walkWithDepthJavaParser(node, 0, maxDepth);
    return maxDepth.get();
  }

  private static void walkWithDepthJavaParser(
      final Node node, final int currentDepth, final AtomicInteger maxDepth) {
    int nextDepth = currentDepth;
    // Increase depth for control structures using instanceof checks
    if (node instanceof IfStmt
        || node instanceof ForStmt
        || node instanceof ForEachStmt
        || node instanceof WhileStmt
        || node instanceof DoStmt
        || node instanceof SwitchStmt
        || node instanceof CatchClause
        || node instanceof ConditionalExpr) {
      nextDepth = currentDepth + 1;
      updateMaxDepth(maxDepth, nextDepth);
    }
    final int depthForChildren = nextDepth;
    node.getChildNodes()
        .forEach(child -> walkWithDepthJavaParser(child, depthForChildren, maxDepth));
  }

  // endregion
  // region JDT
  /** Calculates Cyclomatic Complexity for a JDT MethodDeclaration. */
  public static int calculateComplexity(final MethodDeclaration node) {
    return INSTANCE.complexityOf(node);
  }

  @Override
  public int complexityOf(final MethodDeclaration node) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "MethodDeclaration cannot be null"));
    final var complexity = new AtomicInteger(1);
    node.accept(
        new org.eclipse.jdt.core.dom.ASTVisitor() {

          @Override
          public boolean visit(final IfStatement node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final ForStatement node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final EnhancedForStatement node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final WhileStatement node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final DoStatement node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final org.eclipse.jdt.core.dom.CatchClause node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final ConditionalExpression node) {
            complexity.incrementAndGet();
            return true;
          }

          @Override
          public boolean visit(final org.eclipse.jdt.core.dom.SwitchCase node) {
            final int labels = node.isDefault() ? 1 : Math.max(1, node.expressions().size());
            complexity.addAndGet(labels);
            return true;
          }
        });
    return complexity.get();
  }

  /** Calculates Maximum Nesting Depth for a JDT MethodDeclaration. */
  public static int calculateMaxNestingDepth(final MethodDeclaration node) {
    return INSTANCE.nestingDepthOf(node);
  }

  @Override
  public int nestingDepthOf(final MethodDeclaration node) {
    Objects.requireNonNull(
        node,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "MethodDeclaration cannot be null"));
    if (node.getBody() == null) {
      return 0;
    }
    final var maxDepth = new AtomicInteger(0);
    final var currentDepth = new AtomicInteger(0);
    node.getBody()
        .accept(
            new org.eclipse.jdt.core.dom.ASTVisitor() {
              @Override
              public void preVisit(final ASTNode visitedNode) {
                if (isControlStructureJdt(visitedNode)) {
                  final int nextDepth = currentDepth.incrementAndGet();
                  updateMaxDepth(maxDepth, nextDepth);
                }
              }

              @Override
              public void postVisit(final ASTNode visitedNode) {
                if (isControlStructureJdt(visitedNode)) {
                  currentDepth.decrementAndGet();
                }
              }
            });
    return maxDepth.get();
  }

  private static boolean isControlStructureJdt(final ASTNode node) {
    return node instanceof IfStatement
        || node instanceof ForStatement
        || node instanceof EnhancedForStatement
        || node instanceof WhileStatement
        || node instanceof DoStatement
        || node instanceof org.eclipse.jdt.core.dom.SwitchStatement
        || node instanceof org.eclipse.jdt.core.dom.CatchClause
        || node instanceof ConditionalExpression;
  }

  // endregion
  // region Spoon
  /** Calculates Cyclomatic Complexity for a Spoon Executable. */
  public static int calculateComplexity(final CtExecutable<?> executable) {
    return INSTANCE.complexityOf(executable);
  }

  @Override
  public int complexityOf(final CtExecutable<?> executable) {
    Objects.requireNonNull(
        executable,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "CtExecutable cannot be null"));
    if (executable.getBody() == null) {
      return 1;
    }
    final long branchCount =
        executable
            .getBody()
            .getElements(
                e ->
                    e instanceof CtIf
                        || e instanceof CtFor
                        || e instanceof CtForEach
                        || e instanceof CtWhile
                        || e instanceof CtDo
                        || e instanceof CtCatch
                        || e instanceof CtConditional<?>)
            .size();
    final int switchBranchCount =
        executable.getBody().getElements(e -> e instanceof CtSwitch<?>).stream()
            .mapToInt(
                sw -> {
                  final var ctSwitch = (CtSwitch<?>) sw;
                  return Math.max(
                      1,
                      ctSwitch.getCases().stream()
                          .mapToInt(c -> Math.max(1, caseLabelCount(c)))
                          .sum());
                })
            .sum();
    return (int) branchCount + switchBranchCount + 1;
  }

  /** Calculates Maximum Nesting Depth for a Spoon Executable. */
  public static int calculateMaxNestingDepth(final CtExecutable<?> executable) {
    return INSTANCE.nestingDepthOf(executable);
  }

  @Override
  public int nestingDepthOf(final CtExecutable<?> executable) {
    Objects.requireNonNull(
        executable,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "CtExecutable cannot be null"));
    if (executable.getBody() == null) {
      return 0;
    }
    final var maxDepth = new AtomicInteger(0);
    walkWithDepthSpoon(executable.getBody(), 0, maxDepth);
    return maxDepth.get();
  }

  private static void walkWithDepthSpoon(
      final spoon.reflect.declaration.CtElement element,
      final int currentDepth,
      final AtomicInteger maxDepth) {
    int nextDepth = currentDepth;
    if (element instanceof CtIf
        || element instanceof CtFor
        || element instanceof CtForEach
        || element instanceof CtWhile
        || element instanceof CtDo
        || element instanceof CtSwitch<?>
        || element instanceof CtCatch
        || element instanceof CtConditional<?>) {
      nextDepth = currentDepth + 1;
      updateMaxDepth(maxDepth, nextDepth);
    }
    final int depthForChildren = nextDepth;
    element
        .getDirectChildren()
        .forEach(child -> walkWithDepthSpoon(child, depthForChildren, maxDepth));
  }

  // endregion
  private static void updateMaxDepth(final AtomicInteger maxDepth, final int nextDepth) {
    if (nextDepth > maxDepth.get()) {
      maxDepth.set(nextDepth);
    }
  }

  private static int caseLabelCount(final spoon.reflect.code.CtCase<?> ctCase) {
    if (ctCase.getCaseExpressions() == null || ctCase.getCaseExpressions().isEmpty()) {
      return 1;
    }
    return ctCase.getCaseExpressions().size();
  }
}
