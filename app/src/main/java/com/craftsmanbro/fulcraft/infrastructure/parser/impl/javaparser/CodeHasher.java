package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CodeHashing;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.Optional;

/** Helper for computing code hashes from JavaParser AST nodes. */
public final class CodeHasher {

  private CodeHasher() {}

  /** Computes a SHA-256 hash of the method/constructor body. Returns empty if there is no body. */
  public static Optional<String> computeCodeHash(final Node node) {
    final String bodyString = getBodyString(node);
    if (bodyString.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(CodeHashing.hashNormalized(bodyString));
  }

  /** Checks if a node has a body (implementation). */
  public static boolean hasBody(final Node node) {
    if (node instanceof MethodDeclaration md) {
      return md.getBody().isPresent();
    }
    if (node instanceof ConstructorDeclaration cd) {
      return cd.getBody() != null;
    }
    return false;
  }

  private static String getBodyString(final Node node) {
    if (node instanceof MethodDeclaration md) {
      return md.getBody().map(Object::toString).orElse("");
    }
    if (node instanceof ConstructorDeclaration cd) {
      return cd.getBody() != null ? cd.getBody().toString() : "";
    }
    return "";
  }
}
