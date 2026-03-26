package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.github.javaparser.ast.Node;
import java.util.Optional;

/**
 * Type-safe AST traversal utilities.
 *
 * <p>JavaParser's {@link Node#findAncestor(Class[])} uses a varargs generic parameter ({@code
 * Class<N>...}), which inherently triggers "unchecked generic array creation" warnings at every
 * call site. This utility provides a single-class overload that avoids the varargs signature
 * entirely, keeping callers warning-free.
 */
public final class AstUtils {

  private AstUtils() {
    // utility class
  }

  /**
   * Walks the parent chain of {@code node} and returns the first ancestor that is an instance of
   * {@code ancestorType}.
   *
   * @param node the starting node (not included in the search)
   * @param ancestorType the class to match
   * @param <N> the ancestor node type
   * @return an {@link Optional} containing the first matching ancestor, or empty
   */
  public static <N extends Node> Optional<N> findAncestor(
      final Node node, final Class<N> ancestorType) {
    if (node == null || ancestorType == null) {
      return Optional.empty();
    }
    Node current = node.getParentNode().orElse(null);
    while (current != null) {
      if (ancestorType.isInstance(current)) {
        return Optional.of(ancestorType.cast(current));
      }
      current = current.getParentNode().orElse(null);
    }
    return Optional.empty();
  }
}
