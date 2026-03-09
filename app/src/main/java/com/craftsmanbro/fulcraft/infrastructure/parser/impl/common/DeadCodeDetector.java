package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Detects unused code (dead code) in the analyzed codebase.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.service.detector.DeadCodeDetector} moved
 * to the infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public class DeadCodeDetector {

  private static final Set<String> ENTRY_POINT_SUFFIXES =
      Set.of("Mapping", "Scheduled", "PostConstruct", "Test");

  private static final Set<String> ENTRY_POINT_ANNOTATIONS = Set.of("Override", "Test");

  public void markDeadCode(final AnalysisContext context) {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context must not be null"));
    Objects.requireNonNull(
        context.getMethodInfos(),
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context.getMethodInfos() must not be null"));
    final Map<String, Integer> effectiveIncomingCounts = buildEffectiveIncomingCounts(context);
    for (final var entry : context.getMethodInfos().entrySet()) {
      final var key = entry.getKey();
      final var mi = entry.getValue();
      final int incoming = effectiveIncomingCounts.getOrDefault(key, 0);
      mi.setUsageCount(incoming);
      final boolean isPublic = isPublicOrProtected(mi);
      final boolean hasBody = context.getMethodHasBody().getOrDefault(key, true);
      final boolean isEntryPoint = isEntryPoint(mi);
      final boolean isDead = incoming == 0 && !isPublic && hasBody && !isEntryPoint;
      mi.setDeadCode(isDead);
    }
  }

  private Map<String, Integer> buildEffectiveIncomingCounts(final AnalysisContext context) {
    final Map<String, Integer> effective = new HashMap<>(context.getIncomingCounts());
    final Map<String, MethodInfo> methodInfos = context.getMethodInfos();
    final MethodIndex index = MethodIndex.from(methodInfos);
    for (final Map.Entry<String, Integer> incomingEntry : context.getIncomingCounts().entrySet()) {
      final String incomingKey = incomingEntry.getKey();
      final int incomingCount = incomingEntry.getValue() == null ? 0 : incomingEntry.getValue();
      if (incomingCount <= 0 || methodInfos.containsKey(incomingKey)) {
        continue;
      }
      final ParsedMethodRef ref = ParsedMethodRef.fromKey(incomingKey);
      if (ref == null || ref.methodName().isBlank()) {
        continue;
      }
      final List<String> candidates = index.resolveCandidates(ref);
      if (candidates.size() != 1) {
        continue;
      }
      effective.merge(candidates.get(0), incomingCount, (a, b) -> a + b);
    }
    return effective;
  }

  public void markDeadClasses(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        result.getClasses(),
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "result.getClasses() must not be null"));
    for (final var cls : result.getClasses()) {
      if (cls.isInterface()) {
        cls.setDeadCode(false);
        continue;
      }
      final boolean allDead =
          !cls.getMethods().isEmpty() && cls.getMethods().stream().allMatch(MethodInfo::isDeadCode);
      cls.setDeadCode(allDead);
    }
  }

  private boolean isPublicOrProtected(final MethodInfo mi) {
    final var visibility = mi.getVisibility();
    return "public".equals(visibility) || "protected".equals(visibility);
  }

  private boolean isEntryPoint(final MethodInfo mi) {
    return mi.getAnnotations().stream()
        .filter(Objects::nonNull)
        .anyMatch(
            a ->
                ENTRY_POINT_ANNOTATIONS.contains(a)
                    || ENTRY_POINT_SUFFIXES.stream().anyMatch(a::endsWith));
  }

  private record ParsedMethodRef(
      String classFqn,
      String simpleClassName,
      String methodName,
      int parameterCount,
      List<String> normalizedParameterTypes) {

    private static final int UNKNOWN_ARITY = -1;

    private static ParsedMethodRef fromKey(final String key) {
      if (key == null || key.isBlank()) {
        return null;
      }
      final String raw = key.strip();
      final int sep = raw.indexOf(MethodKeyUtil.SEPARATOR);
      final String classPart = sep >= 0 ? raw.substring(0, sep) : "";
      final String signature = sep >= 0 ? raw.substring(sep + 1) : raw;
      final ParsedSignature parsed = ParsedSignature.parse(signature);
      if (parsed == null) {
        return null;
      }
      final String normalizedClass = normalizeClassName(classPart);
      final String simpleClass = simpleClassName(normalizedClass);
      return new ParsedMethodRef(
          normalizedClass,
          simpleClass,
          parsed.methodName(),
          parsed.parameterCount(),
          parsed.normalizedParameterTypes());
    }

    private static String normalizeClassName(final String className) {
      if (className == null || className.isBlank()) {
        return "";
      }
      return className.strip().replace('$', '.');
    }

    private static String simpleClassName(final String className) {
      if (className == null || className.isBlank()) {
        return "";
      }
      final int dot = className.lastIndexOf('.');
      return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private record ParsedSignature(
        String methodName, int parameterCount, List<String> normalizedParameterTypes) {

      private static ParsedSignature parse(final String signature) {
        if (signature == null || signature.isBlank()) {
          return null;
        }
        final String normalized = signature.strip().replace('$', '.');
        final int open = normalized.indexOf('(');
        final int close = normalized.lastIndexOf(')');
        if (open < 0 || close < open) {
          return new ParsedSignature(normalizeMethodName(normalized), UNKNOWN_ARITY, List.of());
        }
        final String namePart = normalized.substring(0, open).trim();
        final String methodName = normalizeMethodName(namePart);
        final String params = normalized.substring(open + 1, close).trim();
        if (params.isBlank()) {
          return new ParsedSignature(methodName, 0, List.of());
        }
        final List<String> tokens = splitTopLevelCsv(params);
        if (tokens.size() == 1 && tokens.get(0).matches("\\d+")) {
          return new ParsedSignature(methodName, Integer.parseInt(tokens.get(0)), List.of());
        }
        if (tokens.size() == 1 && "?".equals(tokens.get(0))) {
          return new ParsedSignature(methodName, UNKNOWN_ARITY, List.of());
        }
        final List<String> normalizedTypes = new ArrayList<>();
        for (final String token : tokens) {
          normalizedTypes.add(normalizeTypeToken(token));
        }
        return new ParsedSignature(methodName, normalizedTypes.size(), normalizedTypes);
      }

      private static String normalizeMethodName(final String rawName) {
        if (rawName == null || rawName.isBlank()) {
          return "";
        }
        String trimmed = rawName.strip();
        final int dot = trimmed.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < trimmed.length()) {
          trimmed = trimmed.substring(dot + 1);
        }
        return trimmed;
      }

      private static List<String> splitTopLevelCsv(final String value) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
          final char ch = value.charAt(i);
          if (ch == '<') {
            depth++;
          } else if (ch == '>' && depth > 0) {
            depth--;
          } else if (ch == ',' && depth == 0) {
            tokens.add(current.toString().trim());
            current.setLength(0);
            continue;
          }
          current.append(ch);
        }
        if (!current.isEmpty()) {
          tokens.add(current.toString().trim());
        }
        return tokens;
      }

      private static String normalizeTypeToken(final String token) {
        if (token == null || token.isBlank()) {
          return "";
        }
        String normalized = token.strip().replace('$', '.');
        normalized = normalized.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
        normalized = normalized.replaceAll("\\bfinal\\b\\s*", "");
        final int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace + 1 < normalized.length()) {
          final String tail = normalized.substring(lastSpace + 1);
          if (isLikelyParameterName(tail)) {
            normalized = normalized.substring(0, lastSpace).trim();
          }
        }
        normalized =
            normalized.replaceAll(
                "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)+([A-Za-z_][A-Za-z0-9_]*)", "$1");
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
      }

      private static boolean isLikelyParameterName(final String token) {
        if (token == null || token.isBlank()) {
          return false;
        }
        if (!Character.isLowerCase(token.charAt(0))) {
          return false;
        }
        for (int i = 0; i < token.length(); i++) {
          final char ch = token.charAt(i);
          if (!Character.isLetterOrDigit(ch) && ch != '_') {
            return false;
          }
        }
        return true;
      }
    }
  }

  private static final class MethodIndex {

    private final Map<String, MethodInfo> methodInfos;

    private final Map<LookupKey, List<String>> byExactClass = new HashMap<>();

    private final Map<LookupKey, List<String>> bySimpleClass = new HashMap<>();

    private MethodIndex(final Map<String, MethodInfo> methodInfos) {
      this.methodInfos = methodInfos;
    }

    private static MethodIndex from(final Map<String, MethodInfo> methodInfos) {
      final MethodIndex index = new MethodIndex(methodInfos);
      for (final Map.Entry<String, MethodInfo> entry : methodInfos.entrySet()) {
        final ParsedMethodRef ref = ParsedMethodRef.fromKey(entry.getKey());
        if (ref == null || ref.methodName().isBlank()) {
          continue;
        }
        final LookupKey exactKey =
            new LookupKey(
                ref.classFqn().toLowerCase(Locale.ROOT),
                ref.methodName().toLowerCase(Locale.ROOT),
                ref.parameterCount());
        index
            .byExactClass
            .computeIfAbsent(exactKey, ignored -> new ArrayList<>())
            .add(entry.getKey());
        if (!ref.simpleClassName().isBlank()) {
          final LookupKey simpleKey =
              new LookupKey(
                  ref.simpleClassName().toLowerCase(Locale.ROOT),
                  ref.methodName().toLowerCase(Locale.ROOT),
                  ref.parameterCount());
          index
              .bySimpleClass
              .computeIfAbsent(simpleKey, ignored -> new ArrayList<>())
              .add(entry.getKey());
        }
      }
      return index;
    }

    private List<String> resolveCandidates(final ParsedMethodRef incoming) {
      final int arity = incoming.parameterCount();
      final String methodName = incoming.methodName().toLowerCase(Locale.ROOT);
      final String classFqn = incoming.classFqn().toLowerCase(Locale.ROOT);
      final String simpleClass = incoming.simpleClassName().toLowerCase(Locale.ROOT);
      List<String> candidates = new ArrayList<>();
      if (!classFqn.isBlank() && arity >= 0) {
        candidates.addAll(
            byExactClass.getOrDefault(new LookupKey(classFqn, methodName, arity), List.of()));
      }
      if (candidates.isEmpty() && !simpleClass.isBlank() && arity >= 0) {
        candidates.addAll(
            bySimpleClass.getOrDefault(new LookupKey(simpleClass, methodName, arity), List.of()));
      }
      if (candidates.size() > 1 && !incoming.normalizedParameterTypes().isEmpty()) {
        candidates = filterByParameterTypes(candidates, incoming.normalizedParameterTypes());
      }
      return candidates;
    }

    private List<String> filterByParameterTypes(
        final List<String> candidates, final List<String> normalizedParameterTypes) {
      final List<String> filtered = new ArrayList<>();
      for (final String key : candidates) {
        final MethodInfo method = methodInfos.get(key);
        if (method == null || method.getSignature() == null || method.getSignature().isBlank()) {
          continue;
        }
        final ParsedMethodRef parsed = ParsedMethodRef.fromKey(key);
        if (parsed == null) {
          continue;
        }
        if (parsed.normalizedParameterTypes().equals(normalizedParameterTypes)) {
          filtered.add(key);
        }
      }
      return filtered;
    }
  }

  private record LookupKey(String className, String methodName, int arity) {}
}
