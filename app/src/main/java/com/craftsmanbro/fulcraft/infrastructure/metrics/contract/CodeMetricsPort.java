package com.craftsmanbro.fulcraft.infrastructure.metrics.contract;

import com.craftsmanbro.fulcraft.infrastructure.metrics.model.CodeMetrics;
import com.github.javaparser.ast.Node;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import spoon.reflect.declaration.CtExecutable;

/** Contract for calculating code metrics across supported AST representations. */
public interface CodeMetricsPort {

  int complexityOf(Node node);

  int nestingDepthOf(Node node);

  int complexityOf(MethodDeclaration node);

  int nestingDepthOf(MethodDeclaration node);

  int complexityOf(CtExecutable<?> executable);

  int nestingDepthOf(CtExecutable<?> executable);

  default CodeMetrics calculateFor(final Node node) {
    return new CodeMetrics(complexityOf(node), nestingDepthOf(node));
  }

  default CodeMetrics calculateFor(final MethodDeclaration node) {
    return new CodeMetrics(complexityOf(node), nestingDepthOf(node));
  }

  default CodeMetrics calculateFor(final CtExecutable<?> executable) {
    return new CodeMetrics(complexityOf(executable), nestingDepthOf(executable));
  }
}
