package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CodeHashing;
import spoon.reflect.declaration.CtExecutable;

/** Helper for computing code hashes from Spoon AST elements. */
public final class CodeHasher {

  private CodeHasher() {}

  /** Computes a SHA-256 hash of the executable body. Returns null if there is no body. */
  public static String computeCodeHash(final CtExecutable<?> executable) {
    if (executable == null) {
      return null;
    }
    final var bodyElement = executable.getBody();
    if (bodyElement == null) {
      return null;
    }
    final String body = bodyElement.toString();
    if (body.isBlank()) {
      return null;
    }
    return CodeHashing.hashNormalized(body);
  }
}
