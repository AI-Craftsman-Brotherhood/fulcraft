package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import java.util.Objects;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

/** Helper for detecting usage of removed APIs in Spoon AST elements. */
public final class RemovedApiChecker {

  private RemovedApiChecker() {}

  /** Checks if an executable uses any removed APIs. */
  public static boolean usesRemovedApis(
      final CtExecutable<?> executable, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    Objects.requireNonNull(
        executable,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "executable must not be null"));
    final var safeInfo =
        importInfo != null ? importInfo : new RemovedApiDetector.RemovedApiImportInfo();
    return !executable.getElements(e -> isRemovedApiElement(e, safeInfo)).isEmpty();
  }

  /** Checks if an element represents usage of a removed API. */
  public static boolean isRemovedApiElement(
      final CtElement element, final RemovedApiDetector.RemovedApiImportInfo info) {
    if (element == null) {
      return false;
    }
    if (element instanceof CtTypeReference) {
      return matchesRemovedApiType((CtTypeReference<?>) element, info);
    }
    if (element instanceof CtInvocation) {
      return matchesRemovedApiInvocation((CtInvocation<?>) element, info);
    }
    if (element instanceof CtFieldAccess) {
      return matchesRemovedApiFieldAccess((CtFieldAccess<?>) element, info);
    }
    if (element instanceof CtAnnotation) {
      final CtAnnotation<?> ann = (CtAnnotation<?>) element;
      return matchesRemovedApiType(ann.getAnnotationType(), info);
    }
    return false;
  }

  private static boolean matchesRemovedApiInvocation(
      final CtInvocation<?> invocation, final RemovedApiDetector.RemovedApiImportInfo info) {
    final var exec = invocation.getExecutable();
    if (exec != null && matchesRemovedApiType(exec.getDeclaringType(), info)) {
      return true;
    }
    final var target = invocation.getTarget();
    return target != null && matchesRemovedApiType(target.getType(), info);
  }

  private static boolean matchesRemovedApiFieldAccess(
      final CtFieldAccess<?> fieldAccess, final RemovedApiDetector.RemovedApiImportInfo info) {
    final var variable = fieldAccess.getVariable();
    if (variable != null && matchesRemovedApiType(variable.getDeclaringType(), info)) {
      return true;
    }
    final var target = fieldAccess.getTarget();
    return target != null && matchesRemovedApiType(target.getType(), info);
  }

  private static boolean matchesRemovedApiType(
      final CtTypeReference<?> ref, final RemovedApiDetector.RemovedApiImportInfo info) {
    if (ref == null) {
      return false;
    }
    final var qualified = ref.getQualifiedName();
    if (qualified != null && RemovedApiDetector.matchesQualifiedName(qualified, info)) {
      return true;
    }
    final var asString = SafeSpoonPrinter.safeToString(ref, ref.getSimpleName());
    if (RemovedApiDetector.matchesTypeName(asString, info)) {
      return true;
    }
    if (ref instanceof CtWildcardReference wildcard
        && wildcard.getBoundingType() != null
        && matchesRemovedApiType(wildcard.getBoundingType(), info)) {
      return true;
    }
    for (final var arg : ref.getActualTypeArguments()) {
      if (matchesRemovedApiType(arg, info)) {
        return true;
      }
    }
    return false;
  }
}
