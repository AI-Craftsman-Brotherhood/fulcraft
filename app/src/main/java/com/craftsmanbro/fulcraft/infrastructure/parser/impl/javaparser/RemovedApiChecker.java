package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedWildcard;
import java.util.function.Supplier;

/** Helper for detecting usage of removed APIs in JavaParser AST nodes. */
public final class RemovedApiChecker {

  private RemovedApiChecker() {}

  /** Checks if a node uses any removed APIs. */
  public static boolean usesRemovedApis(
      final Node node, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    final var safeInfo =
        importInfo != null ? importInfo : new RemovedApiDetector.RemovedApiImportInfo();
    return node.stream().anyMatch(n -> isRemovedApiUsage(n, safeInfo));
  }

  /** Checks if a specific node represents usage of a removed API. */
  public static boolean isRemovedApiUsage(
      final Node node, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    if (node instanceof Type t) {
      return matchesTypeOrResolved(t.asString(), t::resolve, importInfo);
    }
    if (node instanceof ObjectCreationExpr oce) {
      final Type type = oce.getType();
      return matchesTypeOrResolved(type.asString(), type::resolve, importInfo);
    }
    if (node instanceof MethodCallExpr mce) {
      return matchesMethodCall(mce, importInfo);
    }
    if (node instanceof FieldAccessExpr fae) {
      return matchesFieldAccess(fae, importInfo);
    }
    if (node instanceof NameExpr ne) {
      return RemovedApiDetector.matchesTypeName(ne.getNameAsString(), importInfo)
          || matchesResolvedValue(ne, importInfo);
    }
    if (node instanceof AnnotationExpr ae) {
      return matchesTypeOrResolved(ae.getNameAsString(), ae::calculateResolvedType, importInfo);
    }
    if (node instanceof ClassExpr ce) {
      final Type type = ce.getType();
      return matchesTypeOrResolved(type.asString(), type::resolve, importInfo);
    }
    return false;
  }

  private static boolean matchesTypeOrResolved(
      final String typeName,
      final Supplier<ResolvedType> resolver,
      final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    return RemovedApiDetector.matchesTypeName(typeName, importInfo)
        || matchesResolvedType(resolver, importInfo);
  }

  private static boolean matchesMethodCall(
      final MethodCallExpr mce, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    final var scopeTypeName = mce.getScope().map(Object::toString).orElse(null);
    if (scopeTypeName != null && RemovedApiDetector.matchesTypeName(scopeTypeName, importInfo)) {
      return true;
    }
    try {
      final var resolved = mce.resolve();
      return RemovedApiDetector.matchesPackageName(resolved.getPackageName(), importInfo)
          || RemovedApiDetector.matchesQualifiedName(resolved.getQualifiedName(), importInfo);
    } catch (Exception ignored) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Resolution failed: " + ignored.getMessage()));
      return false;
    }
  }

  private static boolean matchesFieldAccess(
      final FieldAccessExpr fae, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    if (matchesTypeOrResolved(fae.getScope().toString(), fae::calculateResolvedType, importInfo)) {
      return true;
    }
    return matchesResolvedType(fae.getScope()::calculateResolvedType, importInfo);
  }

  private static boolean matchesResolvedValue(
      final NameExpr expr, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    try {
      final var resolved = expr.resolve();
      return matchesResolvedType(resolved::getType, importInfo);
    } catch (Exception ignored) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Resolution failed: " + ignored.getMessage()));
      return false;
    }
  }

  private static boolean matchesResolvedType(
      final Supplier<ResolvedType> resolver,
      final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    try {
      final var resolved = resolver.get();
      return matchesResolvedType(resolved, importInfo);
    } catch (Exception ignored) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Type resolution failed during removed API check: " + ignored.getMessage()));
    }
    return false;
  }

  private static boolean matchesResolvedType(
      final ResolvedType resolved, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    if (resolved == null) {
      return false;
    }
    if (resolved.isArray()) {
      return matchesResolvedType(resolved.asArrayType().getComponentType(), importInfo);
    }
    if (resolved.isReferenceType()) {
      return matchesReferenceTypeRecursively(resolved.asReferenceType(), importInfo);
    }
    if (resolved.isWildcard()) {
      return matchesWildcardType(resolved.asWildcard(), importInfo);
    }
    return false;
  }

  private static boolean matchesReferenceTypeRecursively(
      final ResolvedReferenceType ref, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    return matchesReferenceType(ref, importInfo)
        || ref.typeParametersValues().stream()
            .anyMatch(arg -> matchesResolvedType(arg, importInfo));
  }

  private static boolean matchesWildcardType(
      final ResolvedWildcard wildcard, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    return wildcard.isBounded() && matchesResolvedType(wildcard.getBoundedType(), importInfo);
  }

  private static boolean matchesReferenceType(
      final ResolvedReferenceType ref, final RemovedApiDetector.RemovedApiImportInfo importInfo) {
    if (ref == null) {
      return false;
    }
    final var qualifiedName = ref.getQualifiedName();
    if (qualifiedName != null && !qualifiedName.isBlank()) {
      if (RemovedApiDetector.matchesQualifiedName(qualifiedName, importInfo)) {
        return true;
      }
      final var lastDot = qualifiedName.lastIndexOf('.');
      if (lastDot > 0) {
        final var pkg = qualifiedName.substring(0, lastDot);
        if (RemovedApiDetector.matchesPackageName(pkg, importInfo)) {
          return true;
        }
      }
    }
    final var described = ref.describe();
    return described != null
        && !described.isBlank()
        && (RemovedApiDetector.matchesQualifiedName(described, importInfo)
            || RemovedApiDetector.matchesTypeName(described, importInfo));
  }
}
