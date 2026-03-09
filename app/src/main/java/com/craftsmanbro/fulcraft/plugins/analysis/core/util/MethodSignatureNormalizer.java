package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.plugins.analysis.core.model.MethodId;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Normalizes method signatures into deterministic {@link MethodId} values. */
public class MethodSignatureNormalizer {

  private static final Map<String, String> SIMPLE_TO_FQN = simpleTypeMap();

  public MethodId toMethodId(final String declaringClassFqn, final MethodInfo methodInfo) {
    return toMethodId(declaringClassFqn, methodInfo, null);
  }

  /**
   * Creates a MethodId with awareness of nested types. When knownNestedTypes is provided, simple
   * type names matching nested types will be resolved as inner types of the declaring class.
   */
  public MethodId toMethodId(
      final String declaringClassFqn,
      final MethodInfo methodInfo,
      final Set<String> knownNestedTypes) {
    Objects.requireNonNull(
        methodInfo,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "methodInfo"));
    final String methodName = resolveMethodName(methodInfo.getName(), methodInfo.getSignature());
    final ParsedSignature parsed = parseSignature(methodInfo.getSignature());
    final List<String> parameters =
        parsed.parameterTypes().stream()
            .map(param -> canonicalizeType(param, declaringClassFqn, knownNestedTypes))
            .toList();
    final String returnType =
        parsed
            .returnType()
            .map(rt -> canonicalizeType(rt, declaringClassFqn, knownNestedTypes))
            .orElse(null);
    return new MethodId(
        canonicalizeClassName(declaringClassFqn), methodName, parameters, returnType);
  }

  private String resolveMethodName(final String providedName, final String signature) {
    if (providedName != null && !providedName.isBlank()) {
      return providedName.trim();
    }
    if (signature == null || signature.isBlank()) {
      return "";
    }
    final int parenIndex = signature.indexOf('(');
    final String beforeParen = parenIndex >= 0 ? signature.substring(0, parenIndex) : signature;
    final String[] tokens = beforeParen.trim().split("[\\s#\\.]+");
    if (tokens.length == 0) {
      return "";
    }
    return tokens[tokens.length - 1];
  }

  private ParsedSignature parseSignature(final String signature) {
    if (signature == null) {
      return ParsedSignature.of(List.of(), null);
    }
    final int openParen = signature.indexOf('(');
    final int closeParen = signature.lastIndexOf(')');
    if (openParen < 0 || closeParen < openParen) {
      return ParsedSignature.of(List.of(), null);
    }
    final String paramsSection = signature.substring(openParen + 1, closeParen);
    final List<String> params = splitParameters(paramsSection);
    String tail = signature.substring(closeParen + 1).trim();
    if (tail.startsWith(":")) {
      tail = tail.substring(1).trim();
    }
    final String returnType = tail.isBlank() ? null : tail;
    return ParsedSignature.of(params, returnType);
  }

  private List<String> splitParameters(final String paramsSection) {
    if (paramsSection == null || paramsSection.isBlank()) {
      return List.of();
    }
    final List<String> params = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int genericDepth = 0;
    for (final char c : paramsSection.toCharArray()) {
      if (c == '<') {
        genericDepth++;
        current.append(c);
        continue;
      }
      if (c == '>') {
        genericDepth = Math.max(0, genericDepth - 1);
        current.append(c);
        continue;
      }
      if (c == ',' && genericDepth == 0) {
        params.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      params.add(current.toString().trim());
    }
    return params;
  }

  private String canonicalizeType(
      final String rawType,
      final String declaringClassFqn,
      final Set<String> knownNestedTypes) {
    if (rawType == null || rawType.isBlank()) {
      return "";
    }
    String withoutGenerics =
        stripGenerics(rawType).replaceAll("\\bextends\\b", "").replaceAll("\\bsuper\\b", "").trim();
    withoutGenerics = withoutGenerics.replace("...", "[]");
    int arrayDims = 0;
    while (withoutGenerics.endsWith("[]")) {
      arrayDims++;
      withoutGenerics = withoutGenerics.substring(0, withoutGenerics.length() - 2).trim();
    }
    String normalized = withoutGenerics.replace("$", ".").trim();
    normalized = normalized.replace("?", "").replace("@", "");
    final String baseType = normalized;
    final String fqn = toFqn(baseType, declaringClassFqn, knownNestedTypes);
    if (arrayDims > 0) {
      return fqn + "[]".repeat(arrayDims);
    }
    return fqn;
  }

  private String stripGenerics(final String type) {
    final StringBuilder result = new StringBuilder();
    int depth = 0;
    for (final char c : type.toCharArray()) {
      if (c == '<') {
        depth++;
        continue;
      }
      if (c == '>') {
        depth = Math.max(0, depth - 1);
        continue;
      }
      if (depth == 0) {
        result.append(c);
      }
    }
    return result.toString();
  }

  private String toFqn(
      final String typeName,
      final String declaringClassFqn,
      final Set<String> knownNestedTypes) {
    if (typeName == null || typeName.isBlank()) {
      return "";
    }
    final String cleaned = typeName.trim();
    if (isPrimitive(cleaned)) {
      return cleaned.toLowerCase(Locale.ROOT);
    }
    if (cleaned.contains(".")) {
      return canonicalizeClassName(cleaned);
    }
    final String mapped = SIMPLE_TO_FQN.get(cleaned);
    if (mapped != null) {
      return mapped;
    }
    // Check if this is a known nested type of the declaring class
    if (knownNestedTypes != null && knownNestedTypes.contains(cleaned)) {
      return declaringClassFqn + "." + cleaned;
    }
    final String packageName = packageNameOf(declaringClassFqn);
    if (!packageName.isBlank()) {
      return packageName + "." + cleaned;
    }
    return cleaned;
  }

  private boolean isPrimitive(final String type) {
    final String lower = type.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "byte", "short", "int", "long", "float", "double", "boolean", "char", "void" -> true;
      default -> false;
    };
  }

  private String packageNameOf(final String declaringClassFqn) {
    if (declaringClassFqn == null) {
      return "";
    }
    final int lastDot = declaringClassFqn.lastIndexOf('.');
    if (lastDot <= 0) {
      return "";
    }
    return declaringClassFqn.substring(0, lastDot);
  }

  private String canonicalizeClassName(final String className) {
    if (className == null) {
      return "";
    }
    return className.trim().replace('$', '.');
  }

  private static Map<String, String> simpleTypeMap() {
    final Map<String, String> map = new LinkedHashMap<>();
    map.put("String", "java.lang.String");
    map.put("Boolean", "java.lang.Boolean");
    map.put("Byte", "java.lang.Byte");
    map.put("Short", "java.lang.Short");
    map.put("Integer", "java.lang.Integer");
    map.put("Long", "java.lang.Long");
    map.put("Float", "java.lang.Float");
    map.put("Double", "java.lang.Double");
    map.put("Character", "java.lang.Character");
    map.put("Object", "java.lang.Object");
    map.put("Throwable", "java.lang.Throwable");
    map.put("Exception", "java.lang.Exception");
    map.put("RuntimeException", "java.lang.RuntimeException");
    map.put("Error", "java.lang.Error");
    map.put("List", "java.util.List");
    map.put("Map", "java.util.Map");
    map.put("Set", "java.util.Set");
    map.put("Collection", "java.util.Collection");
    map.put("Iterable", "java.lang.Iterable");
    map.put("Iterator", "java.util.Iterator");
    map.put("Optional", "java.util.Optional");
    map.put("Queue", "java.util.Queue");
    map.put("Deque", "java.util.Deque");
    map.put("LocalDate", "java.time.LocalDate");
    map.put("LocalDateTime", "java.time.LocalDateTime");
    map.put("LocalTime", "java.time.LocalTime");
    map.put("Instant", "java.time.Instant");
    map.put("Duration", "java.time.Duration");
    map.put("Period", "java.time.Period");
    map.put("Date", "java.util.Date");
    map.put("BigDecimal", "java.math.BigDecimal");
    map.put("BigInteger", "java.math.BigInteger");
    map.put("UUID", "java.util.UUID");
    return Map.copyOf(map);
  }

  private record ParsedSignature(
      List<String> parameterTypes, Optional<String> returnType) {

    static ParsedSignature of(final List<String> parameterTypes, final String returnType) {
      final List<String> params = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
      return new ParsedSignature(params, toOptional(returnType));
    }

    private static Optional<String> toOptional(final String returnType) {
      if (returnType == null || returnType.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(returnType);
    }
  }
}
