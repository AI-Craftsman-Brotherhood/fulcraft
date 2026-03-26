package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared helpers for analysis post-processing.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.PostProcessingUtils} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class PostProcessingUtils {

  /**
   * Method names on java.lang / java.nio types that are common false positives when Spoon cannot
   * resolve the receiver type (reported as {@code unknown#methodName}).
   */
  private static final Set<String> KNOWN_JDK_METHOD_NAMES =
      Set.of(
          "isBlank",
          "isEmpty",
          "toString",
          "hashCode",
          "equals",
          "intValue",
          "longValue",
          "doubleValue",
          "floatValue",
          "booleanValue",
          "normalize",
          "toAbsolutePath",
          "resolve",
          "getParent",
          "name",
          "ordinal",
          "compareTo",
          "valueOf");

  /**
   * Method name prefixes typical of fluent builder APIs (Resilience4j, HttpRequest, etc.) that
   * produce false positives when the builder type is unresolved.
   */
  private static final Set<String> KNOWN_BUILDER_METHOD_NAMES =
      Set.of(
          "build",
          "create",
          "ignoreExceptions",
          "retryOnException",
          "retryOnResult",
          "timeoutDuration",
          "maxAttempts",
          "waitDuration",
          "uri",
          "header",
          "method",
          "version",
          "maxTokens",
          "temperature",
          "topP");

  private PostProcessingUtils() {}

  public static List<CalledMethodRef> buildCalledMethodRefs(
      final List<String> calls, final String source, final boolean resolved) {
    return buildCalledMethodRefs(calls, source, null, resolved, null);
  }

  public static List<CalledMethodRef> buildCalledMethodRefs(
      final List<String> calls,
      final String source,
      final Map<String, ResolutionStatus> statuses,
      final boolean defaultResolved) {
    return buildCalledMethodRefs(calls, source, statuses, defaultResolved, null);
  }

  public static List<CalledMethodRef> buildCalledMethodRefs(
      final List<String> calls,
      final String source,
      final Map<String, ResolutionStatus> statuses,
      final boolean defaultResolved,
      final Map<String, List<String>> callArgumentLiterals) {
    final List<CalledMethodRef> refs = new ArrayList<>();
    for (final String call : calls) {
      final CalledMethodRef ref = new CalledMethodRef();
      ref.setRaw(call);
      ref.setSource(source);
      final ResolutionStatus status = resolveStatus(statuses, call);
      applyResolution(ref, call, status, defaultResolved);
      ref.setArgumentLiterals(resolveArgumentLiterals(callArgumentLiterals, call));
      refs.add(ref);
    }
    return refs;
  }

  private static ResolutionStatus resolveStatus(
      final Map<String, ResolutionStatus> statuses, final String call) {
    if (statuses == null || call == null) {
      return null;
    }
    return statuses.get(call);
  }

  private static List<String> resolveArgumentLiterals(
      final Map<String, List<String>> callArgumentLiterals, final String call) {
    if (callArgumentLiterals == null || call == null) {
      return List.of();
    }
    final List<String> literals = callArgumentLiterals.get(call);
    if (literals == null || literals.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> deduped = new LinkedHashSet<>();
    for (final String literal : literals) {
      if (literal == null || literal.isBlank()) {
        continue;
      }
      deduped.add(literal.strip());
    }
    return deduped.isEmpty() ? List.of() : new ArrayList<>(deduped);
  }

  private static void applyResolution(
      final CalledMethodRef ref,
      final String call,
      final ResolutionStatus status,
      final boolean defaultResolved) {
    final boolean isResolved = isResolved(call, status, defaultResolved);
    if (isResolved) {
      setResolvedFields(ref, call, status);
    } else {
      setUnresolvedFields(ref, call, status);
    }
  }

  private static boolean isResolved(
      final String call, final ResolutionStatus status, final boolean defaultResolved) {
    final boolean resolved = status != null ? ResolutionStatus.isResolved(status) : defaultResolved;
    return resolved && call != null && !isUnknownCall(call);
  }

  private static void setResolvedFields(
      final CalledMethodRef ref, final String call, final ResolutionStatus status) {
    ref.setResolved(call);
    ref.setStatus(status != null ? status : ResolutionStatus.RESOLVED);
    ref.setConfidence(1.0);
  }

  private static void setUnresolvedFields(
      final CalledMethodRef ref, final String call, final ResolutionStatus status) {
    ref.setResolved(null);
    ref.setStatus(status != null ? status : ResolutionStatus.UNRESOLVED);
    ref.setConfidence(0.3);
    if (call != null && !call.isBlank()) {
      ref.setCandidates(List.of(call));
    }
  }

  private static boolean isUnknownCall(final String call) {
    if (StringUtils.isBlank(call)) {
      return true;
    }
    final String classPart = extractClassPart(call);
    return StringUtils.isBlank(classPart) || MethodInfo.UNKNOWN.equals(classPart);
  }

  private static String extractClassPart(final String call) {
    final int separatorIndex = call.indexOf(MethodKeyUtil.SEPARATOR);
    return separatorIndex >= 0 ? call.substring(0, separatorIndex) : call;
  }

  public static void logUnresolvedCalls(
      final String methodKey,
      final List<CalledMethodRef> refs,
      final AtomicInteger counter,
      final int maxWarnings) {
    if (refs.isEmpty()) {
      return;
    }
    for (final CalledMethodRef ref : refs) {
      if (shouldSkipLog(ref)) {
        continue;
      }
      if (isSuppressed(counter, maxWarnings)) {
        return;
      }
      logSingleWarning(methodKey, ref);
    }
  }

  private static boolean shouldSkipLog(final CalledMethodRef ref) {
    if (ref == null || !ResolutionStatus.isUnresolved(ref.getStatus())) {
      return true;
    }
    return isKnownFalsePositive(ref.getRaw());
  }

  /**
   * Returns true if the unresolved call is a known false positive. These arise when Spoon cannot
   * resolve the receiver type (e.g. Lombok-generated getters, builder chains) and reports the call
   * as {@code unknown#methodName(...)}.
   */
  private static boolean isKnownFalsePositive(final String raw) {
    if (StringUtils.isBlank(raw)) {
      return false;
    }
    if (!raw.startsWith(MethodInfo.UNKNOWN + MethodKeyUtil.SEPARATOR)) {
      return false;
    }
    final String methodPart = raw.substring(raw.indexOf(MethodKeyUtil.SEPARATOR) + 1);
    final String methodName = extractMethodName(methodPart);
    return KNOWN_JDK_METHOD_NAMES.contains(methodName)
        || KNOWN_BUILDER_METHOD_NAMES.contains(methodName);
  }

  private static String extractMethodName(final String methodPart) {
    final int parenIndex = methodPart.indexOf('(');
    return parenIndex >= 0 ? methodPart.substring(0, parenIndex) : methodPart;
  }

  private static boolean isSuppressed(final AtomicInteger counter, final int maxWarnings) {
    final int currentCount = counter.incrementAndGet();
    if (currentCount > maxWarnings) {
      if (currentCount == maxWarnings + 1) {
        Logger.warn(MessageSource.getMessage("analysis.type_resolution.suppressed", maxWarnings));
      }
      return true;
    }
    return false;
  }

  private static void logSingleWarning(final String methodKey, final CalledMethodRef ref) {
    String className = methodKey;
    String signature = "unknown";
    if (methodKey != null && methodKey.contains(MethodKeyUtil.SEPARATOR)) {
      final String[] parts = methodKey.split(MethodKeyUtil.SEPARATOR, 2);
      className = parts[0];
      signature = parts.length > 1 ? parts[1] : "unknown";
    }
    final List<String> candidates = getCandidates(ref);
    Logger.warn(
        MessageSource.getMessage(
            "analysis.type_resolution.unresolved_call",
            className,
            signature,
            ref.getRaw(),
            candidates));
  }

  private static List<String> getCandidates(final CalledMethodRef ref) {
    if (!ref.getCandidates().isEmpty()) {
      return ref.getCandidates();
    }
    return ref.getRaw() != null ? List.of(ref.getRaw()) : List.of();
  }

  /** Marks methods with identical code hashes as duplicates. */
  public static void markDuplicates(final AnalysisContext context, final int minGroupSize) {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "context must not be null"));
    final var hashToMethods = new HashMap<String, List<String>>();
    for (final Map.Entry<String, String> entry : context.getMethodCodeHash().entrySet()) {
      final String hash = entry.getValue();
      if (StringUtils.isBlank(hash)) {
        continue;
      }
      hashToMethods.computeIfAbsent(hash, k -> new ArrayList<>()).add(entry.getKey());
    }
    for (final Map.Entry<String, List<String>> entry : hashToMethods.entrySet()) {
      final List<String> methods = entry.getValue();
      if (methods.size() < minGroupSize) {
        continue;
      }
      for (final String key : methods) {
        final var methodInfo = context.getMethodInfos().get(key);
        if (methodInfo != null) {
          methodInfo.setDuplicate(true);
          methodInfo.setDuplicateGroup(entry.getKey());
        }
      }
    }
  }
}
