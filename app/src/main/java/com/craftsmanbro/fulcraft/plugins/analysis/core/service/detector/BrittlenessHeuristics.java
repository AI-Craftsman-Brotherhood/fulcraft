package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic detector for non-deterministic signals in method bodies.
 *
 * <p>This is a lightweight static check based on source text and resolved call strings.
 */
public class BrittlenessHeuristics {

  private static final List<Pattern> TIME_PATTERNS =
      List.of(
          Pattern.compile("\\bLocalDateTime\\s*\\.\\s*now\\s*\\("),
          Pattern.compile("\\bInstant\\s*\\.\\s*now\\s*\\("),
          Pattern.compile("\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\("),
          Pattern.compile("\\bSystem\\s*\\.\\s*nanoTime\\s*\\("));

  private static final List<Pattern> RANDOM_PATTERNS =
      List.of(
          Pattern.compile("\\bnew\\s+Random\\s*\\("),
          Pattern.compile("\\bThreadLocalRandom\\s*\\.\\s*current\\s*\\("),
          Pattern.compile("\\bUUID\\s*\\.\\s*randomUUID\\s*\\("));

  private static final List<Pattern> ENV_PATTERNS =
      List.of(
          Pattern.compile("\\bSystem\\s*\\.\\s*getenv\\s*\\("),
          Pattern.compile("\\bSystem\\s*\\.\\s*getProperty\\s*\\("));

  private static final List<Pattern> CONCURRENCY_PATTERNS =
      List.of(
          Pattern.compile("\\bExecutors\\s*\\.\\s*newFixedThreadPool\\s*\\("),
          Pattern.compile("\\.\\s*parallelStream\\s*\\("));

  private static final List<Pattern> IO_PATTERNS =
      List.of(
          Pattern.compile("\\bFiles\\s*\\.\\s*readAllLines\\s*\\("),
          Pattern.compile("\\bFiles\\s*\\.\\s*readAllBytes\\s*\\("),
          Pattern.compile("\\bFiles\\s*\\.\\s*newInputStream\\s*\\("),
          Pattern.compile("\\bnew\\s+Socket\\s*\\("),
          Pattern.compile("\\bnew\\s+ServerSocket\\s*\\("));

  private static final Set<String> TIME_CALL_TOKENS =
      Set.of("localdatetime.now", "instant.now", "system.currenttimemillis", "system.nanotime");

  private static final Set<String> RANDOM_CALL_TOKENS =
      Set.of("random", "threadlocalrandom.current", "uuid.randomuuid");

  private static final Set<String> ENV_CALL_TOKENS = Set.of("system.getenv", "system.getproperty");

  private static final Set<String> CONCURRENCY_CALL_TOKENS =
      Set.of("executors.newfixedthreadpool", "parallelstream", "thread.start");

  private static final Set<String> IO_CALL_TOKENS =
      Set.of(
          "files.readalllines",
          "files.readallbytes",
          "files.newinputstream",
          "socket",
          "serversocket");

  public boolean apply(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "result"));
    boolean anyDetected = false;
    for (final ClassInfo cls : result.getClasses()) {
      if (cls == null) {
        continue;
      }
      for (final MethodInfo method : cls.getMethods()) {
        if (method == null) {
          continue;
        }
        final EnumSet<BrittlenessSignal> signals = detectSignals(method);
        method.setBrittlenessSignals(new ArrayList<>(signals));
        method.setBrittle(!signals.isEmpty());
        if (!signals.isEmpty()) {
          anyDetected = true;
        }
      }
    }
    return anyDetected;
  }

  public EnumSet<BrittlenessSignal> detectSignals(final MethodInfo method) {
    Objects.requireNonNull(
        method,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "method"));
    final EnumSet<BrittlenessSignal> signals = EnumSet.noneOf(BrittlenessSignal.class);
    final String source = method.getSourceCode();
    final List<String> calledMethods = method.getCalledMethods();
    if (containsPattern(source, TIME_PATTERNS) || containsCall(calledMethods, TIME_CALL_TOKENS)) {
      signals.add(BrittlenessSignal.TIME);
    }
    if (containsPattern(source, RANDOM_PATTERNS)
        || containsCall(calledMethods, RANDOM_CALL_TOKENS)) {
      signals.add(BrittlenessSignal.RANDOM);
    }
    if (containsPattern(source, ENV_PATTERNS) || containsCall(calledMethods, ENV_CALL_TOKENS)) {
      signals.add(BrittlenessSignal.ENVIRONMENT);
    }
    if (containsConcurrency(source, calledMethods)) {
      signals.add(BrittlenessSignal.CONCURRENCY);
    }
    if (containsPattern(source, IO_PATTERNS) || containsCall(calledMethods, IO_CALL_TOKENS)) {
      signals.add(BrittlenessSignal.IO);
    }
    if (containsCollectionOrderRisk(source)) {
      signals.add(BrittlenessSignal.COLLECTION_ORDER);
    }
    return signals;
  }

  private boolean containsConcurrency(final String source, final List<String> calledMethods) {
    if (containsPattern(source, CONCURRENCY_PATTERNS)
        || containsCall(calledMethods, CONCURRENCY_CALL_TOKENS)) {
      return true;
    }
    if (source == null || source.isBlank()) {
      return false;
    }
    return source.contains("Thread") && source.contains(".start(");
  }

  private boolean containsCollectionOrderRisk(final String source) {
    if (source == null || source.isBlank()) {
      return false;
    }
    final boolean hasHashCollection = source.contains("HashSet") || source.contains("HashMap");
    final boolean hasToString = source.contains(".toString(") || source.contains("String.valueOf(");
    return hasHashCollection && hasToString;
  }

  private boolean containsPattern(final String source, final List<Pattern> patterns) {
    if (source == null || source.isBlank()) {
      return false;
    }
    for (final Pattern pattern : patterns) {
      if (pattern.matcher(source).find()) {
        return true;
      }
    }
    return false;
  }

  private boolean containsCall(final List<String> calledMethods, final Set<String> tokens) {
    if (calledMethods == null || calledMethods.isEmpty()) {
      return false;
    }
    for (final String call : calledMethods) {
      if (call == null || call.isBlank()) {
        continue;
      }
      final String lowered = call.toLowerCase(Locale.ROOT).replace('#', '.');
      for (final String token : tokens) {
        if (lowered.contains(token)) {
          return true;
        }
      }
    }
    return false;
  }
}
