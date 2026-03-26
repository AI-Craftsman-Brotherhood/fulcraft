package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;

/** Helper for collecting thrown exceptions from Spoon AST elements. */
public final class ExceptionCollector {

  private ExceptionCollector() {}

  /** Collects all thrown exception types from an executable. */
  public static List<String> collectThrownExceptions(final CtExecutable<?> executable) {
    Objects.requireNonNull(executable);
    final Set<String> exceptions = new LinkedHashSet<>();
    // 1. Exceptions declared in the method signature
    for (final var typeRef : executable.getThrownTypes()) {
      addIfValid(exceptions, resolveName(typeRef));
    }
    // 2. Explicit throw statements in the body
    executable.getElements(CtThrow.class::isInstance).stream()
        .map(CtThrow.class::cast)
        .forEach(thr -> addIfValid(exceptions, resolveThrowType(thr)));
    return List.copyOf(exceptions);
  }

  /** Resolves the type of a throw expression. */
  public static String resolveThrowType(final CtThrow thr) {
    final var expr = thr.getThrownExpression();
    if (expr == null) {
      return null;
    }
    try {
      final var name = resolveName(expr.getType());
      if (StringUtils.isNotBlank(name)) {
        return name;
      }
    } catch (Exception ignored) {
      // Ignore Spoon type-resolution failures.
    }
    return SafeSpoonPrinter.safeToString(expr);
  }

  private static String resolveName(final CtTypeReference<?> ref) {
    if (ref == null) {
      return null;
    }
    var name = ref.getQualifiedName();
    if (StringUtils.isBlank(name)) {
      name = ref.getSimpleName();
    }
    return name;
  }

  private static void addIfValid(final Set<String> exceptions, final String name) {
    if (StringUtils.isNotBlank(name)) {
      exceptions.add(name);
    }
  }
}
