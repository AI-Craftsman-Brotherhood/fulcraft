package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context to hold analysis results and intermediate states.
 *
 * <p>This class serves as a shared mutable data holder during the static analysis phase. It uses
 * concurrent collections to support parallel analysis processing.
 *
 * <p><strong>Thread Safety:</strong> All maps are thread-safe {@link ConcurrentHashMap} instances.
 * Callers may safely read and write to these maps concurrently.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.context.AnalysisContext} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public class AnalysisContext {

  /** Metadata for each method, keyed by method signature. */
  private final Map<String, MethodInfo> methodInfos = new ConcurrentHashMap<>();

  /** Call graph representation, mapping caller signature to set of callee signatures. */
  private final Map<String, Set<String>> callGraph = new ConcurrentHashMap<>();

  /** Call graph resolution status per caller signature and callee signature. */
  private final Map<String, Map<String, ResolutionStatus>> callGraphStatuses =
      new ConcurrentHashMap<>();

  /** Captured literal argument hints for each call edge (caller -> callee -> literals). */
  private final Map<String, Map<String, List<String>>> callGraphArgumentLiterals =
      new ConcurrentHashMap<>();

  /** Count of incoming calls for each method signature. */
  private final Map<String, Integer> incomingCounts = new ConcurrentHashMap<>();

  /** Flag indicating if a method has a body (is not abstract/interface), keyed by signature. */
  private final Map<String, Boolean> methodHasBody = new ConcurrentHashMap<>();

  /** Visibility modifier (public, private, etc.) for each method signature. */
  private final Map<String, String> methodVisibility = new ConcurrentHashMap<>();

  /** Fully qualified class name for each method signature. */
  private final Map<String, String> methodClass = new ConcurrentHashMap<>();

  /** Hash of the method body code for change detection. */
  private final Map<String, String> methodCodeHash = new ConcurrentHashMap<>();

  /** Source label for the call graph (e.g., javaparser, spoon). */
  private volatile String callGraphSource = "analysis";

  /** Whether call graph entries should be treated as resolved. */
  private volatile boolean callGraphResolved;

  public Map<String, MethodInfo> getMethodInfos() {
    return methodInfos;
  }

  public Map<String, Set<String>> getCallGraph() {
    return callGraph;
  }

  public Set<String> getOrCreateCallGraphEntry(final String methodKey) {
    return callGraph.computeIfAbsent(methodKey, k -> ConcurrentHashMap.newKeySet());
  }

  public void recordCallStatus(
      final String callerKey, final String calleeKey, final ResolutionStatus status) {
    if (callerKey == null || calleeKey == null || status == null) {
      return;
    }
    callGraphStatuses
        .computeIfAbsent(callerKey, k -> new ConcurrentHashMap<>())
        .merge(calleeKey, status, AnalysisContext::preferMoreSpecificStatus);
  }

  public Map<String, ResolutionStatus> getCallStatuses(final String callerKey) {
    return callGraphStatuses.get(callerKey);
  }

  public void recordCallArgumentLiterals(
      final String callerKey, final String calleeKey, final List<String> argumentLiterals) {
    if (callerKey == null
        || calleeKey == null
        || argumentLiterals == null
        || argumentLiterals.isEmpty()) {
      return;
    }
    final List<String> normalized = normalizeArgumentLiterals(argumentLiterals);
    if (normalized.isEmpty()) {
      return;
    }
    callGraphArgumentLiterals
        .computeIfAbsent(callerKey, k -> new ConcurrentHashMap<>())
        .merge(calleeKey, normalized, AnalysisContext::mergeArgumentLiteralLists);
  }

  public Map<String, List<String>> getCallArgumentLiterals(final String callerKey) {
    return callGraphArgumentLiterals.get(callerKey);
  }

  private static List<String> normalizeArgumentLiterals(final List<String> literals) {
    final LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (final String literal : literals) {
      if (literal == null || literal.isBlank()) {
        continue;
      }
      normalized.add(literal.strip());
    }
    return new ArrayList<>(normalized);
  }

  private static List<String> mergeArgumentLiteralLists(
      final List<String> existing, final List<String> incoming) {
    final LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (existing != null) {
      merged.addAll(existing);
    }
    if (incoming != null) {
      merged.addAll(incoming);
    }
    return new ArrayList<>(merged);
  }

  private static ResolutionStatus preferMoreSpecificStatus(
      final ResolutionStatus existing, final ResolutionStatus incoming) {
    if (existing == null) {
      return incoming;
    }
    if (incoming == null) {
      return existing;
    }
    return statusPriority(existing) >= statusPriority(incoming) ? existing : incoming;
  }

  private static int statusPriority(final ResolutionStatus status) {
    return switch (status) {
      case RESOLVED -> 3;
      case AMBIGUOUS -> 2;
      case UNRESOLVED -> 1;
    };
  }

  public Map<String, Integer> getIncomingCounts() {
    return incomingCounts;
  }

  public Map<String, Boolean> getMethodHasBody() {
    return methodHasBody;
  }

  public Map<String, String> getMethodVisibility() {
    return methodVisibility;
  }

  public Map<String, String> getMethodClass() {
    return methodClass;
  }

  public Map<String, String> getMethodCodeHash() {
    return methodCodeHash;
  }

  public String getCallGraphSource() {
    return callGraphSource;
  }

  public void setCallGraphSource(final String callGraphSource) {
    this.callGraphSource = callGraphSource;
  }

  public boolean isCallGraphResolved() {
    return callGraphResolved;
  }

  public void setCallGraphResolved(final boolean callGraphResolved) {
    this.callGraphResolved = callGraphResolved;
  }
}
