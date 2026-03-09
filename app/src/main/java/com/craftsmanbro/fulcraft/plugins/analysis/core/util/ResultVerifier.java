package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ResultVerifier {

  private static final String AND_PREFIX = "  ... and ";

  private static final String MORE_SUFFIX = " more";

  private static final String INLINE_AND_PREFIX = " ... and ";

  private static final int INLINE_LIST_LIMIT = 5;

  private static final int PRINT_LIST_LIMIT = 10;

  private final Tracer tracer;

  public ResultVerifier(final Tracer tracer) {
    this.tracer = Objects.requireNonNull(tracer);
  }

  public void verify(final AnalysisResult primary, final AnalysisResult secondary) {
    final Span span = tracer.spanBuilder("ResultVerifier.verify").startSpan();
    try (Scope scope = span.makeCurrent()) {
      Objects.requireNonNull(
          scope,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "analysis.common.error.argument_null", "telemetry scope"));
      final int primaryClasses = getClassCount(primary);
      final int secondaryClasses = getClassCount(secondary);
      span.setAttribute("classes.primary", primaryClasses);
      span.setAttribute("classes.secondary", secondaryClasses);
      final int primaryMethods = getMethodCount(primary);
      final int secondaryMethods = getMethodCount(secondary);
      span.setAttribute("methods.primary", primaryMethods);
      span.setAttribute("methods.secondary", secondaryMethods);
      printHeader(primaryClasses, secondaryClasses, primaryMethods, secondaryMethods);
      compareAndPrintDetails(primary, secondary, span);
      Logger.stdout(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "analysis.common.log.message", ""));
      Logger.stdout(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "analysis.common.log.message", "------------------------------------"));
    } catch (RuntimeException e) {
      span.recordException(e);
      final String errorMessage = e.getMessage();
      span.setStatus(
          io.opentelemetry.api.trace.StatusCode.ERROR,
          errorMessage != null ? errorMessage : "error");
      throw e;
    } finally {
      span.end();
    }
  }

  private int getClassCount(final AnalysisResult result) {
    return result != null ? result.getClasses().size() : 0;
  }

  private int getMethodCount(final AnalysisResult result) {
    return result != null
        ? result.getClasses().stream().mapToInt(ClassInfo::getMethodCount).sum()
        : 0;
  }

  private void printHeader(
      final int primaryClasses,
      final int secondaryClasses,
      final int primaryMethods,
      final int secondaryMethods) {
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage("analysis.common.log.message", ""));
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.log.message", "--- Analysis Verification Report ---"));
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.log.message", "Metric\t\tJavaParser\tSpoon"));
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.log.message", "------\t\t----------\t-----"));
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.log.message", "Classes\t\t" + primaryClasses + "\t\t" + secondaryClasses));
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.log.message", "Methods\t\t" + primaryMethods + "\t\t" + secondaryMethods));
  }

  private void compareAndPrintDetails(
      final AnalysisResult primary, final AnalysisResult secondary, final Span span) {
    final Map<String, ClassInfo> primaryMap = toClassMap(primary);
    final Map<String, ClassInfo> secondaryMap = toClassMap(secondary);
    final Set<String> allClasses = new HashSet<>();
    allClasses.addAll(primaryMap.keySet());
    allClasses.addAll(secondaryMap.keySet());
    final List<String> onlyInPrimary = new ArrayList<>();
    final List<String> onlyInSecondary = new ArrayList<>();
    final List<String> methodCountMismatches = new ArrayList<>();
    final List<String> methodsOnlyInPrimary = new ArrayList<>();
    final List<String> methodsOnlyInSecondary = new ArrayList<>();
    final List<String> fieldsOnlyInPrimary = new ArrayList<>();
    final List<String> fieldsOnlyInSecondary = new ArrayList<>();
    final List<String> classMetadataMismatches = new ArrayList<>();
    for (final String fqn : allClasses) {
      classifyDifference(
          fqn, primaryMap, secondaryMap, onlyInPrimary, onlyInSecondary, methodCountMismatches);
      final ClassInfo primaryClassInfo = primaryMap.get(fqn);
      final ClassInfo secondaryClassInfo = secondaryMap.get(fqn);
      if (primaryClassInfo == null || secondaryClassInfo == null) {
        continue;
      }
      collectMethodDiffs(
          fqn, primaryClassInfo, secondaryClassInfo, methodsOnlyInPrimary, methodsOnlyInSecondary);
      collectFieldDiffs(
          fqn, primaryClassInfo, secondaryClassInfo, fieldsOnlyInPrimary, fieldsOnlyInSecondary);
      collectClassMetadataDiffs(
          fqn, primaryClassInfo, secondaryClassInfo, classMetadataMismatches);
    }
    printList("[!] Classes only in JavaParser", onlyInPrimary);
    printList("[!] Classes only in Spoon", onlyInSecondary);
    printList("[!] Method count mismatches", methodCountMismatches);
    printList("[!] Methods only in JavaParser", methodsOnlyInPrimary);
    printList("[!] Methods only in Spoon", methodsOnlyInSecondary);
    printList("[!] Fields only in JavaParser", fieldsOnlyInPrimary);
    printList("[!] Fields only in Spoon", fieldsOnlyInSecondary);
    printList("[!] Class metadata mismatches", classMetadataMismatches);
    span.setAttribute("diff.only_in_primary", onlyInPrimary.size());
    span.setAttribute("diff.only_in_secondary", onlyInSecondary.size());
    span.setAttribute("diff.method_count_mismatches", methodCountMismatches.size());
    span.setAttribute("diff.methods_only_in_primary", methodsOnlyInPrimary.size());
    span.setAttribute("diff.methods_only_in_secondary", methodsOnlyInSecondary.size());
    span.setAttribute("diff.fields_only_in_primary", fieldsOnlyInPrimary.size());
    span.setAttribute("diff.fields_only_in_secondary", fieldsOnlyInSecondary.size());
    span.setAttribute("diff.class_metadata_mismatches", classMetadataMismatches.size());
  }

  private Map<String, ClassInfo> toClassMap(final AnalysisResult result) {
    if (result == null) {
      return Collections.emptyMap();
    }
    return result.getClasses().stream()
        .collect(
            Collectors.toMap(
                ClassInfo::getFqn, Function.identity(), (a, b) -> a));
  }

  private void classifyDifference(
      final String fqn,
      final Map<String, ClassInfo> primaryMap,
      final Map<String, ClassInfo> secondaryMap,
      final List<String> onlyInPrimary,
      final List<String> onlyInSecondary,
      final List<String> methodCountMismatches) {
    if (!secondaryMap.containsKey(fqn)) {
      onlyInPrimary.add(fqn);
      return;
    }
    if (!primaryMap.containsKey(fqn)) {
      onlyInSecondary.add(fqn);
      return;
    }

    final ClassInfo primaryClassInfo = primaryMap.get(fqn);
    final ClassInfo secondaryClassInfo = secondaryMap.get(fqn);
    if (primaryClassInfo.getMethodCount() != secondaryClassInfo.getMethodCount()) {
      methodCountMismatches.add(
          String.format(
              "%s (JP: %d, Spoon: %d)",
              fqn, primaryClassInfo.getMethodCount(), secondaryClassInfo.getMethodCount()));
    }
  }

  private void printList(final String title, final List<String> items) {
    if (items.isEmpty()) {
      return;
    }
    Logger.stdout(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage("analysis.common.log.message", ""));
    Logger.stdout(title + " (" + items.size() + "):");
    items.stream()
        .limit(PRINT_LIST_LIMIT)
        .forEach(
            item ->
                Logger.stdout(
                    com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                        "analysis.common.log.message", "  - " + item)));
    if (items.size() > PRINT_LIST_LIMIT) {
      Logger.stdout(AND_PREFIX + (items.size() - PRINT_LIST_LIMIT) + MORE_SUFFIX);
    }
  }

  private void collectMethodDiffs(
      final String fqn,
      final ClassInfo primary,
      final ClassInfo secondary,
      final List<String> methodsOnlyInPrimary,
      final List<String> methodsOnlyInSecondary) {
    final Set<String> primaryMethods = toMethodKeys(primary);
    final Set<String> secondaryMethods = toMethodKeys(secondary);
    final Set<String> onlyInPrimary = difference(primaryMethods, secondaryMethods);
    final Set<String> onlyInSecondary = difference(secondaryMethods, primaryMethods);
    if (!onlyInPrimary.isEmpty()) {
      methodsOnlyInPrimary.add(formatDiff(fqn, "Spoon", onlyInPrimary));
    }
    if (!onlyInSecondary.isEmpty()) {
      methodsOnlyInSecondary.add(formatDiff(fqn, "JavaParser", onlyInSecondary));
    }
  }

  private void collectFieldDiffs(
      final String fqn,
      final ClassInfo primary,
      final ClassInfo secondary,
      final List<String> fieldsOnlyInPrimary,
      final List<String> fieldsOnlyInSecondary) {
    final Set<String> primaryFields = toFieldKeys(primary);
    final Set<String> secondaryFields = toFieldKeys(secondary);
    final Set<String> onlyInPrimary = difference(primaryFields, secondaryFields);
    final Set<String> onlyInSecondary = difference(secondaryFields, primaryFields);
    if (!onlyInPrimary.isEmpty()) {
      fieldsOnlyInPrimary.add(formatDiff(fqn, "Spoon", onlyInPrimary));
    }
    if (!onlyInSecondary.isEmpty()) {
      fieldsOnlyInSecondary.add(formatDiff(fqn, "JavaParser", onlyInSecondary));
    }
  }

  private void collectClassMetadataDiffs(
      final String fqn,
      final ClassInfo primary,
      final ClassInfo secondary,
      final List<String> classMetadataMismatches) {
    if (!asSet(primary.getExtendsTypes()).equals(asSet(secondary.getExtendsTypes()))) {
      classMetadataMismatches.add(
          formatDualListDiff(
              fqn, "extends", primary.getExtendsTypes(), secondary.getExtendsTypes()));
    }
    if (!asSet(primary.getImplementsTypes()).equals(asSet(secondary.getImplementsTypes()))) {
      classMetadataMismatches.add(
          formatDualListDiff(
              fqn, "implements", primary.getImplementsTypes(), secondary.getImplementsTypes()));
    }
    if (!asSet(primary.getAnnotations()).equals(asSet(secondary.getAnnotations()))) {
      classMetadataMismatches.add(
          formatDualListDiff(
              fqn, "annotations", primary.getAnnotations(), secondary.getAnnotations()));
    }
  }

  private Set<String> toMethodKeys(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getMethods().isEmpty()) {
      return Collections.emptySet();
    }
    return classInfo.getMethods().stream()
        .map(
            methodInfo -> {
              if (methodInfo.getSignature() != null && !methodInfo.getSignature().isBlank()) {
                return methodInfo.getSignature();
              }
              if (methodInfo.getName() != null && !methodInfo.getName().isBlank()) {
                return methodInfo.getName();
              }
              if (methodInfo.getMethodId() != null && !methodInfo.getMethodId().isBlank()) {
                return methodInfo.getMethodId();
              }
              return "<unknown>";
            })
        .collect(Collectors.toCollection(java.util.TreeSet::new));
  }

  private Set<String> toFieldKeys(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getFields().isEmpty()) {
      return Collections.emptySet();
    }
    return classInfo.getFields().stream()
        .map(
            fieldInfo ->
                (fieldInfo.getName() != null && !fieldInfo.getName().isBlank())
                    ? fieldInfo.getName() : fieldInfo.getType())
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(java.util.TreeSet::new));
  }

  private Set<String> difference(final Set<String> left, final Set<String> right) {
    if (left.isEmpty()) {
      return Collections.emptySet();
    }
    final Set<String> diff = new TreeSet<>(left);
    diff.removeAll(right);
    return diff;
  }

  private Set<String> asSet(final List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptySet();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(java.util.TreeSet::new));
  }

  private String formatDiff(final String fqn, final String label, final Set<String> items) {
    return String.format("%s missing in %s (%d): %s", fqn, label, items.size(), inlineList(items));
  }

  private String formatDualListDiff(
      final String fqn,
      final String category,
      final List<String> primary,
      final List<String> secondary) {
    return String.format(
        "%s %s differ (JP: %s, Spoon: %s)",
        fqn, category, inlineList(asSet(primary)), inlineList(asSet(secondary)));
  }

  private String inlineList(final Set<String> items) {
    if (items.isEmpty()) {
      return "<none>";
    }
    final List<String> sorted = items.stream().sorted().toList();
    if (sorted.size() <= INLINE_LIST_LIMIT) {
      return String.join(", ", sorted);
    }
    final List<String> head = sorted.subList(0, INLINE_LIST_LIMIT);
    return String.join(", ", head)
        + INLINE_AND_PREFIX
        + (sorted.size() - INLINE_LIST_LIMIT)
        + MORE_SUFFIX;
  }
}
