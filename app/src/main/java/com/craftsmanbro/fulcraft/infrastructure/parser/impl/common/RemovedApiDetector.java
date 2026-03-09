package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper for removed API detection (e.g., javax.xml.bind).
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.service.detector.RemovedApiDetector}
 * moved to the infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class RemovedApiDetector {

  public static final List<String> REMOVED_API_PACKAGES =
      List.of("javax.xml.bind", "javax.xml.ws", "javax.activation");

  private RemovedApiDetector() {
    throw new IllegalStateException(
        MessageSource.getMessage("analysis.removed_api_detector.error.utility_class"));
  }

  public static RemovedApiImportInfo fromImports(final List<String> imports) {
    final RemovedApiImportInfo info = new RemovedApiImportInfo();
    if (imports == null) {
      return info;
    }
    for (final String rawImport : imports) {
      processImport(info, rawImport);
    }
    return info;
  }

  private static void processImport(final RemovedApiImportInfo info, final String rawImport) {
    if (rawImport == null || rawImport.isBlank()) {
      return;
    }
    String cleaned = rawImport.replace(";", "").trim();
    boolean isStaticImport = false;
    if (cleaned.startsWith("static ")) {
      isStaticImport = true;
      cleaned = cleaned.substring("static ".length()).trim();
    }
    checkPkgAndAdd(info, cleaned, isStaticImport);
  }

  private static void checkPkgAndAdd(
      final RemovedApiImportInfo info, final String cleaned, final boolean isStaticImport) {
    for (final String pkg : REMOVED_API_PACKAGES) {
      if (cleaned.startsWith(pkg)) {
        info.addImportedQualifiedName(cleaned);
        if (cleaned.endsWith(".*")) {
          info.addWildcardPackage(pkg);
        } else if (!isStaticImport) {
          addSimpleName(info, cleaned);
        }
        return;
      }
    }
  }

  private static void addSimpleName(final RemovedApiImportInfo info, final String cleaned) {
    final String simple = extractSimpleName(cleaned);
    if (!simple.isEmpty() && !"*".equals(simple)) {
      info.addImportedSimpleName(simple);
    }
  }

  public static boolean matchesTypeName(final String typeName, final RemovedApiImportInfo info) {
    if (typeName == null) {
      return false;
    }
    final RemovedApiImportInfo safeInfo = info != null ? info : new RemovedApiImportInfo();
    if (containsRemovedApiInGenerics(typeName, safeInfo)) {
      return true;
    }
    final String normalized = normalizeTypeName(typeName);
    if (normalized.isEmpty()) {
      return false;
    }
    for (final String pkg : REMOVED_API_PACKAGES) {
      if (normalized.startsWith(pkg)) {
        return true;
      }
    }
    final String simple = simpleName(normalized);
    if (safeInfo.containsSimpleName(simple)) {
      return true;
    }
    if (normalized.contains(".")) {
      final String outer = normalized.substring(0, normalized.indexOf('.'));
      if (safeInfo.containsSimpleName(outer)) {
        return true;
      }
      return safeInfo.hasWildcardMatchingPrefix(normalized);
    }
    return false;
  }

  public static boolean matchesQualifiedName(
      final String qualifiedName, final RemovedApiImportInfo info) {
    if (qualifiedName == null || qualifiedName.isBlank()) {
      return false;
    }
    final RemovedApiImportInfo safeInfo = info != null ? info : new RemovedApiImportInfo();
    if (containsRemovedApiInGenerics(qualifiedName, safeInfo)) {
      return true;
    }
    final String normalized = normalizeTypeName(qualifiedName);
    if (normalized.isEmpty()) {
      return false;
    }
    for (final String pkg : REMOVED_API_PACKAGES) {
      if (normalized.startsWith(pkg)) {
        return true;
      }
    }
    final java.util.Optional<String> pkgName = packageName(normalized);
    if (pkgName.isPresent() && safeInfo.hasWildcardMatchingPrefix(pkgName.get())) {
      return true;
    }
    return matchesTypeName(normalized, safeInfo);
  }

  public static boolean matchesPackageName(
      final String packageName, final RemovedApiImportInfo info) {
    if (packageName == null || packageName.isBlank()) {
      return false;
    }
    final RemovedApiImportInfo safeInfo = info != null ? info : new RemovedApiImportInfo();
    for (final String pkg : REMOVED_API_PACKAGES) {
      if (packageName.startsWith(pkg)) {
        return true;
      }
    }
    return safeInfo.hasWildcardMatchingPrefix(packageName);
  }

  public static String normalizeTypeName(final String raw) {
    if (raw == null) {
      return "";
    }
    String name = raw.trim();
    final int genericStart = name.indexOf('<');
    if (genericStart >= 0) {
      name = name.substring(0, genericStart);
    }
    if (name.startsWith("? extends ")) {
      name = name.substring("? extends ".length()).trim();
    }
    if (name.startsWith("? super ")) {
      name = name.substring("? super ".length()).trim();
    }
    if (name.endsWith("...")) {
      name = name.substring(0, name.length() - 3);
    }
    while (name.endsWith("[]")) {
      name = name.substring(0, name.length() - 2);
    }
    if (name.startsWith("java.lang.")) {
      name = name.substring("java.lang.".length());
    }
    return name.trim();
  }

  private static String extractSimpleName(final String qualifiedName) {
    String cleaned = qualifiedName;
    if (cleaned.endsWith(".*")) {
      cleaned = cleaned.substring(0, cleaned.length() - 2);
    }
    if (cleaned.contains(".")) {
      return cleaned.substring(cleaned.lastIndexOf('.') + 1);
    }
    return cleaned;
  }

  private static String simpleName(final String qualifiedName) {
    if (qualifiedName.contains(".")) {
      return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }
    return qualifiedName;
  }

  private static java.util.Optional<String> packageName(final String qualifiedName) {
    final int idx = qualifiedName.lastIndexOf('.');
    if (idx > 0) {
      return java.util.Optional.of(qualifiedName.substring(0, idx));
    }
    return java.util.Optional.empty();
  }

  private static boolean containsRemovedApiInGenerics(
      final String raw, final RemovedApiImportInfo info) {
    final List<String> args = extractGenericArguments(raw);
    if (args.isEmpty()) {
      return false;
    }
    for (final String arg : args) {
      if (matchesTypeName(arg, info)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> extractGenericArguments(final String raw) {
    final int start = raw.indexOf('<');
    if (start < 0) {
      return List.of();
    }
    final List<String> args = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int depth = 0;
    for (int i = start + 1; i < raw.length(); i++) {
      final char c = raw.charAt(i);
      if (c == '<') {
        depth++;
        current.append(c);
      } else if (c == '>') {
        if (depth == 0) {
          args.add(current.toString().trim());
          break;
        }
        depth--;
        current.append(c);
      } else if (c == ',' && depth == 0) {
        args.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    return args;
  }

  /** Holds import information related to removed APIs. */
  public static class RemovedApiImportInfo {

    private final Set<String> importedSimpleNames = new HashSet<>();

    private final Set<String> importedQualifiedNames = new HashSet<>();

    private final Set<String> wildcardPackages = new HashSet<>();

    public Set<String> getImportedSimpleNames() {
      return Set.copyOf(importedSimpleNames);
    }

    public Set<String> getImportedQualifiedNames() {
      return Set.copyOf(importedQualifiedNames);
    }

    public Set<String> getWildcardPackages() {
      return Set.copyOf(wildcardPackages);
    }

    void addImportedSimpleName(final String name) {
      importedSimpleNames.add(name);
    }

    void addImportedQualifiedName(final String name) {
      importedQualifiedNames.add(name);
    }

    void addWildcardPackage(final String pkg) {
      wildcardPackages.add(pkg);
    }

    boolean containsSimpleName(final String name) {
      return importedSimpleNames.contains(name);
    }

    boolean hasWildcardMatchingPrefix(final String prefix) {
      return wildcardPackages.stream().anyMatch(prefix::startsWith);
    }
  }
}
