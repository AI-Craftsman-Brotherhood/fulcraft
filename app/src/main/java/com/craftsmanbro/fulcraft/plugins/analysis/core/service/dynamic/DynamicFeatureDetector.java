package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects dynamic features (Reflection, Proxy, DI, etc.) from analysis results. Provides static
 * detection without runtime analysis.
 */
public class DynamicFeatureDetector {

  private final List<DynamicFeatureEvent> events = Collections.synchronizedList(new ArrayList<>());

  private final Map<String, Integer> annotationCounts = new ConcurrentHashMap<>();

  // Common class FQN constants
  private static final String CLASS_FQN = "java.lang.Class";

  // Detection patterns for method call signatures
  private static final List<DetectionRule> DETECTION_RULES =
      List.of( // Reflection
          new DetectionRule(
              CLASS_FQN,
              "forName",
              DynamicFeatureType.REFLECTION,
              "CLASS_FORNAME",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              CLASS_FQN,
              "getMethod",
              DynamicFeatureType.REFLECTION,
              "GET_METHOD",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              CLASS_FQN,
              "getDeclaredMethod",
              DynamicFeatureType.REFLECTION,
              "GET_DECLARED_METHOD",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              CLASS_FQN,
              "getField",
              DynamicFeatureType.REFLECTION,
              "GET_FIELD",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              CLASS_FQN,
              "getDeclaredField",
              DynamicFeatureType.REFLECTION,
              "GET_DECLARED_FIELD",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              CLASS_FQN,
              "newInstance",
              DynamicFeatureType.REFLECTION,
              "CLASS_NEWINSTANCE",
              DynamicFeatureSeverity.HIGH),
          new DetectionRule(
              "java.lang.reflect.Method",
              "invoke",
              DynamicFeatureType.REFLECTION,
              "METHOD_INVOKE",
              DynamicFeatureSeverity.HIGH),
          new DetectionRule(
              "java.lang.reflect.Constructor",
              "newInstance",
              DynamicFeatureType.REFLECTION,
              "CONSTRUCTOR_NEWINSTANCE",
              DynamicFeatureSeverity.HIGH),
          new DetectionRule(
              "java.lang.reflect.Field",
              "get",
              DynamicFeatureType.REFLECTION,
              "FIELD_GET",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              "java.lang.reflect.Field",
              "set",
              DynamicFeatureType.REFLECTION,
              "FIELD_SET",
              DynamicFeatureSeverity.MEDIUM), // Proxy
          new DetectionRule(
              "java.lang.reflect.Proxy",
              "newProxyInstance",
              DynamicFeatureType.PROXY,
              "PROXY_NEW",
              DynamicFeatureSeverity.HIGH), // ClassLoader
          new DetectionRule(
              "java.lang.ClassLoader",
              "loadClass",
              DynamicFeatureType.CLASSLOADER,
              "LOADCLASS",
              DynamicFeatureSeverity.HIGH),
          new DetectionRule(
              "java.lang.ClassLoader",
              "defineClass",
              DynamicFeatureType.CLASSLOADER,
              "DEFINECLASS",
              DynamicFeatureSeverity.HIGH),
          new DetectionRule(
              "java.lang.Thread",
              "getContextClassLoader",
              DynamicFeatureType.CLASSLOADER,
              "CONTEXT_CLASSLOADER",
              DynamicFeatureSeverity.MEDIUM), // ServiceLoader
          new DetectionRule(
              "java.util.ServiceLoader",
              "load",
              DynamicFeatureType.SERVICELOADER,
              "SERVICELOADER_LOAD",
              DynamicFeatureSeverity.MEDIUM), // DI - Spring
          new DetectionRule(
              "org.springframework.context.ApplicationContext",
              "getBean",
              DynamicFeatureType.DI,
              "SPRING_GETBEAN",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              "org.springframework.beans.factory.BeanFactory",
              "getBean",
              DynamicFeatureType.DI,
              "SPRING_GETBEAN",
              DynamicFeatureSeverity.MEDIUM), // Serialization - Jackson
          new DetectionRule(
              "tools.jackson.databind.ObjectMapper",
              "readValue",
              DynamicFeatureType.SERIALIZATION,
              "JACKSON_READVALUE",
              DynamicFeatureSeverity.MEDIUM),
          new DetectionRule(
              "tools.jackson.databind.ObjectMapper",
              "convertValue",
              DynamicFeatureType.SERIALIZATION,
              "JACKSON_CONVERTVALUE",
              DynamicFeatureSeverity.MEDIUM), // MethodHandles
          new DetectionRule(
              "java.lang.invoke.MethodHandles",
              "lookup",
              DynamicFeatureType.INVOKEDYNAMIC,
              "METHODHANDLES_LOOKUP",
              DynamicFeatureSeverity.HIGH));

  // DI annotation patterns
  private static final Set<String> DI_ANNOTATIONS =
      Set.of(
          "javax.inject.Inject",
          "jakarta.inject.Inject",
          "javax.inject.Named",
          "jakarta.inject.Named",
          "org.springframework.beans.factory.annotation.Autowired",
          "org.springframework.stereotype.Component",
          "org.springframework.stereotype.Service",
          "org.springframework.stereotype.Repository",
          "org.springframework.stereotype.Controller",
          "org.springframework.context.annotation.Bean",
          "org.springframework.context.annotation.Configuration",
          "org.springframework.context.annotation.ComponentScan");

  private static final Set<String> DI_ANNOTATION_SIMPLE_NAMES = buildSimpleNames(DI_ANNOTATIONS);

  // Lombok annotations
  private static final Set<String> LOMBOK_ANNOTATIONS =
      Set.of(
          "lombok.Getter",
          "lombok.Setter",
          "lombok.Data",
          "lombok.Builder",
          "lombok.AllArgsConstructor",
          "lombok.NoArgsConstructor",
          "lombok.RequiredArgsConstructor",
          "lombok.ToString",
          "lombok.EqualsAndHashCode",
          "lombok.Value",
          "lombok.With",
          "lombok.Slf4j",
          "lombok.Log");

  private static final Set<String> LOMBOK_ANNOTATION_SIMPLE_NAMES =
      buildSimpleNames(LOMBOK_ANNOTATIONS);

  /** Detects dynamic features from analyzed classes. */
  public void detectFromAnalysisResult(final List<ClassInfo> classes, final Path projectRoot) {
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null) {
        continue;
      }
      final String filePath = classInfo.getFilePath();
      final String classFqn = classInfo.getFqn();
      // Collect annotations
      collectAnnotations(classInfo);
      // Check methods
      for (final MethodInfo method : classInfo.getMethods()) {
        if (method == null) {
          continue;
        }
        detectFromMethod(method, filePath, classFqn, projectRoot);
      }
    }
  }

  private void collectAnnotations(final ClassInfo classInfo) {
    final String filePath = classInfo.getFilePath();
    final String classFqn = classInfo.getFqn();
    collectClassAnnotations(classInfo.getAnnotations(), filePath, classFqn);
    collectMethodAnnotations(classInfo.getMethods(), filePath, classFqn);
  }

  private void collectClassAnnotations(
      final List<String> annotations, final String filePath, final String classFqn) {
    for (final String annotation : annotations) {
      recordAnnotation(annotation, filePath, classFqn, null);
    }
  }

  private void collectMethodAnnotations(
      final List<MethodInfo> methods, final String filePath, final String classFqn) {
    for (final MethodInfo method : methods) {
      if (method == null) {
        continue;
      }
      recordMethodAnnotations(method, filePath, classFqn);
    }
  }

  private void recordMethodAnnotations(
      final MethodInfo method, final String filePath, final String classFqn) {
    final String methodSig = method.getSignature();
    for (final String annotation : method.getAnnotations()) {
      recordAnnotation(annotation, filePath, classFqn, methodSig);
    }
  }

  private void recordAnnotation(
      final String annotation,
      final String filePath,
      final String classFqn,
      final String methodSig) {
    if (annotation == null || annotation.isBlank()) {
      return;
    }
    annotationCounts.compute(annotation, (key, value) -> value == null ? 1 : value + 1);
    checkDiAnnotation(annotation, filePath, classFqn, methodSig, -1);
  }

  private void checkDiAnnotation(
      final String annotation,
      final String filePath,
      final String classFqn,
      final String methodSig,
      final int line) {
    if (annotation == null || annotation.isBlank()) {
      return;
    }
    final String simpleName = simpleNameOf(annotation);
    if (simpleName == null || simpleName.isBlank()) {
      return;
    }
    final boolean qualified = annotation.indexOf('.') >= 0;
    if (DI_ANNOTATIONS.contains(annotation)
        || (!qualified && DI_ANNOTATION_SIMPLE_NAMES.contains(simpleName))) {
      events.add(
          DynamicFeatureEvent.builder()
              .featureType(DynamicFeatureType.DI)
              .featureSubtype("INJECT_ANNOTATION")
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(methodSig)
              .lineStart(line)
              .snippet("@" + simpleName)
              .evidence(Map.of("annotation", annotation))
              .severity(DynamicFeatureSeverity.LOW)
              .build());
    } else if (LOMBOK_ANNOTATIONS.contains(annotation)
        || (!qualified && LOMBOK_ANNOTATION_SIMPLE_NAMES.contains(simpleName))) {
      events.add(
          DynamicFeatureEvent.builder()
              .featureType(DynamicFeatureType.ANNOTATION)
              .featureSubtype("LOMBOK")
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(methodSig)
              .lineStart(line)
              .snippet("@" + simpleName)
              .evidence(Map.of("annotation", annotation))
              .severity(DynamicFeatureSeverity.LOW)
              .build());
    }
  }

  private void detectFromMethod(
      final MethodInfo method,
      final String filePath,
      final String classFqn,
      final Path projectRoot) {
    for (final CalledMethodRef ref : method.getCalledMethodRefs()) {
      if (ref == null) {
        continue;
      }
      detectFromMethodRef(ref, method.getSignature(), filePath, classFqn, projectRoot);
    }
  }

  private void detectFromMethodRef(
      final CalledMethodRef ref,
      final String methodSig,
      final String filePath,
      final String classFqn,
      final Path projectRoot) {
    final String raw = ref.getRaw();
    final String resolvedFqn = ref.getResolved();
    for (final DetectionRule rule : DETECTION_RULES) {
      if (matchesRule(rule, raw, resolvedFqn)) {
        final DynamicFeatureEvent event =
            createEventFromRule(rule, raw, resolvedFqn, filePath, classFqn, methodSig, projectRoot);
        events.add(event);
        // One match per ref
        break;
      }
    }
  }

  private DynamicFeatureEvent createEventFromRule(
      final DetectionRule rule,
      final String raw,
      final String resolvedFqn,
      final String filePath,
      final String classFqn,
      final String methodSig,
      final Path projectRoot) {
    // MethodInfo doesn't have line info, use -1 as placeholder
    final int refLine = -1;
    final String snippet = extractSnippet(projectRoot, filePath, refLine);
    return DynamicFeatureEvent.builder()
        .featureType(rule.type)
        .featureSubtype(rule.subtype)
        .filePath(filePath)
        .classFqn(classFqn)
        .methodSig(methodSig)
        .lineStart(refLine)
        .lineEnd(refLine)
        .snippet(snippet != null ? snippet : raw)
        .evidence(
            Map.of(
                "called_method_raw",
                raw != null ? raw : "",
                "resolved_fqn",
                resolvedFqn != null ? resolvedFqn : ""))
        .severity(rule.severity)
        .build();
  }

  private boolean matchesRule(
      final DetectionRule rule, final String raw, final String resolvedFqn) {
    final String expectedPattern = rule.classPattern + "#" + rule.methodPattern;
    // Check resolved FQN - must start with exact class pattern and contain
    // #methodName
    if (resolvedFqn != null && resolvedFqn.startsWith(expectedPattern)) {
      return true;
    }
    // Check raw pattern - require full Class#method pattern
    if (raw != null) {
      final int separatorIndex = raw.indexOf('#');
      if (separatorIndex >= 0) {
        final String rawClass = raw.substring(0, separatorIndex);
        final String rawSignature = raw.substring(separatorIndex + 1);
        final String simpleClass =
            rule.classPattern.substring(rule.classPattern.lastIndexOf('.') + 1);
        final boolean classMatches =
            rawClass.equals(rule.classPattern) || rawClass.equals(simpleClass);
        return classMatches && rawSignature.startsWith(rule.methodPattern);
      }
      // Build expected pattern: SimpleClassName#methodName
      final String simpleClass =
          rule.classPattern.substring(rule.classPattern.lastIndexOf('.') + 1);
      final String fullPattern = simpleClass + "#" + rule.methodPattern;
      return raw.startsWith(expectedPattern) || raw.startsWith(fullPattern);
    }
    return false;
  }

  private String extractSnippet(final Path projectRoot, final String filePath, final int line) {
    if (line <= 0 || filePath == null) {
      return null;
    }
    try {
      Path file = projectRoot.resolve(filePath);
      if (!Files.exists(file)) {
        file = projectRoot.resolve("src/main/java").resolve(filePath);
      }
      if (Files.exists(file)) {
        final List<String> lines = Files.readAllLines(file);
        if (line <= lines.size()) {
          return lines.get(line - 1).trim();
        }
      }
    } catch (IOException e) {
      Logger.debug(
          MessageSource.getMessage("analysis.dynamic_feature.snippet_read_failed", filePath, line));
    }
    return null;
  }

  private static Set<String> buildSimpleNames(final Set<String> fqns) {
    final Set<String> simpleNames = new HashSet<>();
    for (final String fqn : fqns) {
      final String simpleName = simpleNameOf(fqn);
      if (simpleName != null && !simpleName.isBlank()) {
        simpleNames.add(simpleName);
      }
    }
    return Set.copyOf(simpleNames);
  }

  private static String simpleNameOf(final String name) {
    if (name == null) {
      return null;
    }
    final String normalized = name.startsWith("@") ? name.substring(1) : name;
    final int lastDot = normalized.lastIndexOf('.');
    return lastDot > 0 ? normalized.substring(lastDot + 1) : normalized;
  }

  /** Clears any previously detected events and annotation counts. */
  public void reset() {
    events.clear();
    annotationCounts.clear();
  }

  /** Returns all detected events. */
  public List<DynamicFeatureEvent> getEvents() {
    return new ArrayList<>(events);
  }

  /** Returns annotation counts. */
  public Map<String, Integer> getAnnotationCounts() {
    return new HashMap<>(annotationCounts);
  }

  /** Calculates the dynamic score based on severity weights. Score = HIGH*3 + MEDIUM*2 + LOW*1 */
  public int calculateDynamicScore() {
    return events.stream().mapToInt(e -> e.severity().getWeight()).sum();
  }

  /** Returns events grouped by type. */
  public Map<DynamicFeatureType, Long> countByType() {
    final Map<DynamicFeatureType, Long> counts = new EnumMap<>(DynamicFeatureType.class);
    for (final DynamicFeatureEvent e : events) {
      counts.merge(e.featureType(), 1L, (a, b) -> a + b);
    }
    return counts;
  }

  /** Returns events grouped by severity. */
  public Map<DynamicFeatureSeverity, Long> countBySeverity() {
    final Map<DynamicFeatureSeverity, Long> counts = new EnumMap<>(DynamicFeatureSeverity.class);
    for (final DynamicFeatureEvent e : events) {
      counts.merge(e.severity(), 1L, (a, b) -> a + b);
    }
    return counts;
  }

  /** Returns top files by event count. */
  public List<Map.Entry<String, Long>> getTopFiles(final int limit) {
    if (limit <= 0) {
      return List.of();
    }
    final Map<String, Long> fileCounts = new HashMap<>();
    for (final DynamicFeatureEvent e : events) {
      if (e.filePath() != null) {
        fileCounts.merge(e.filePath(), 1L, (a, b) -> a + b);
      }
    }
    return fileCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .toList();
  }

  /** Returns top subtypes by count. */
  public List<Map.Entry<String, Long>> getTopSubtypes(final int limit) {
    if (limit <= 0) {
      return List.of();
    }
    final Map<String, Long> subtypeCounts = new HashMap<>();
    for (final DynamicFeatureEvent e : events) {
      if (e.featureSubtype() != null) {
        subtypeCounts.merge(e.featureSubtype(), 1L, (a, b) -> a + b);
      }
    }
    return subtypeCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .toList();
  }

  /** Returns top annotations by count. */
  public List<Map.Entry<String, Integer>> getTopAnnotations(final int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return annotationCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .toList();
  }

  // Internal detection rule
  private record DetectionRule(
      String classPattern,
      String methodPattern,
      DynamicFeatureType type,
      String subtype,
      DynamicFeatureSeverity severity) {}
}
