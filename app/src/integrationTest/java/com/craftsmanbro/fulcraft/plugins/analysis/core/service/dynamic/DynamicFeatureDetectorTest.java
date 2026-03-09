package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicFeatureDetectorTest {

  @TempDir Path tempDir;

  @Test
  void detectFromAnalysisResult_collectsEventsAndCounts() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("src/main/java/com/example/TestClass.java");
    classInfo.setAnnotations(List.of("org.springframework.stereotype.Service"));

    MethodInfo method1 = new MethodInfo();
    method1.setSignature("foo()");
    method1.setAnnotations(List.of("lombok.Getter"));
    List<CalledMethodRef> refs = new java.util.ArrayList<>();
    refs.add(refWithRaw("Class#forName"));
    refs.add(refWithRaw("Proxy#newProxyInstance"));
    refs.add(null);
    method1.setCalledMethodRefs(refs);

    MethodInfo method2 = new MethodInfo();
    method2.setSignature("bar()");
    method2.setCalledMethodRefs(
        List.of(refWithResolved("java.util.ServiceLoader#load(Ljava/lang/Class;)")));

    classInfo.setMethods(List.of(method1, method2));

    DynamicFeatureDetector detector = new DynamicFeatureDetector();
    detector.detectFromAnalysisResult(List.of(classInfo), tempDir);

    List<DynamicFeatureEvent> events = detector.getEvents();
    assertThat(events).hasSize(5);

    DynamicFeatureEvent reflection =
        events.stream()
            .filter(e -> "CLASS_FORNAME".equals(e.featureSubtype()))
            .findFirst()
            .orElseThrow();
    assertThat(reflection.featureType()).isEqualTo(DynamicFeatureType.REFLECTION);
    assertThat(reflection.snippet()).isEqualTo("Class#forName");
    assertThat(reflection.evidence())
        .containsEntry("called_method_raw", "Class#forName")
        .containsEntry("resolved_fqn", "");

    Map<DynamicFeatureType, Long> byType = detector.countByType();
    assertThat(byType)
        .containsEntry(DynamicFeatureType.REFLECTION, 1L)
        .containsEntry(DynamicFeatureType.PROXY, 1L)
        .containsEntry(DynamicFeatureType.SERVICELOADER, 1L)
        .containsEntry(DynamicFeatureType.DI, 1L)
        .containsEntry(DynamicFeatureType.ANNOTATION, 1L);

    Map<DynamicFeatureSeverity, Long> bySeverity = detector.countBySeverity();
    assertThat(bySeverity)
        .containsEntry(DynamicFeatureSeverity.LOW, 2L)
        .containsEntry(DynamicFeatureSeverity.MEDIUM, 2L)
        .containsEntry(DynamicFeatureSeverity.HIGH, 1L);

    assertThat(detector.calculateDynamicScore()).isEqualTo(9);

    Map<String, Integer> annotationCounts = detector.getAnnotationCounts();
    assertThat(annotationCounts)
        .containsEntry("org.springframework.stereotype.Service", 1)
        .containsEntry("lombok.Getter", 1);

    List<Map.Entry<String, Long>> topFiles = detector.getTopFiles(1);
    assertThat(topFiles).hasSize(1);
    assertThat(topFiles.get(0).getKey()).isEqualTo("src/main/java/com/example/TestClass.java");
    assertThat(topFiles.get(0).getValue()).isEqualTo(5L);

    Map<String, Long> subtypeCounts = toMap(detector.getTopSubtypes(10));
    assertThat(subtypeCounts.keySet())
        .contains(
            "CLASS_FORNAME", "PROXY_NEW", "SERVICELOADER_LOAD", "INJECT_ANNOTATION", "LOMBOK");

    List<Map.Entry<String, Integer>> topAnnotations = detector.getTopAnnotations(1);
    assertThat(topAnnotations).hasSize(1);
    assertThat(topAnnotations.get(0).getValue()).isEqualTo(1);
    assertThat(List.of("org.springframework.stereotype.Service", "lombok.Getter"))
        .contains(topAnnotations.get(0).getKey());

    assertThat(detector.getTopFiles(0)).isEmpty();
    assertThat(detector.getTopSubtypes(0)).isEmpty();

    detector.reset();
    assertThat(detector.getEvents()).isEmpty();
    assertThat(detector.getAnnotationCounts()).isEmpty();
  }

  @Test
  void detectFromAnalysisResult_handlesSimpleAnnotationNamesAndUnmatchedRefs() {
    ClassInfo classWithoutFilePath = new ClassInfo();
    classWithoutFilePath.setFqn("com.example.NoFile");
    classWithoutFilePath.setAnnotations(
        new ArrayList<>(Arrays.asList("Inject", "Inject", "@Getter", "", null, "custom.Ann")));

    MethodInfo noisyMethod = new MethodInfo();
    noisyMethod.setSignature("noisy()");
    noisyMethod.setAnnotations(
        new ArrayList<>(Arrays.asList("@Autowired", "Log", " ", null, "unknown.Annotation")));
    noisyMethod.setCalledMethodRefs(
        new ArrayList<>(
            Arrays.asList(null, refWithRaw("NoMatch#noop"), refWithRaw("ClassforName"))));
    classWithoutFilePath.setMethods(new ArrayList<>(Arrays.asList(null, noisyMethod)));

    ClassInfo classWithFilePath = new ClassInfo();
    classWithFilePath.setFqn("com.example.WithFile");
    classWithFilePath.setFilePath("src/main/java/com/example/WithFile.java");
    MethodInfo dynamicMethod = new MethodInfo();
    dynamicMethod.setSignature("run()");
    dynamicMethod.setCalledMethodRefs(
        List.of(
            refWithRaw("Class#forName"),
            refWithResolved("java.lang.ClassLoader#loadClass(Ljava/lang/String;)"),
            refWithRaw("Other#call")));
    classWithFilePath.setMethods(List.of(dynamicMethod));

    DynamicFeatureDetector detector = new DynamicFeatureDetector();
    detector.detectFromAnalysisResult(
        new ArrayList<>(Arrays.asList(null, classWithoutFilePath, classWithFilePath)), tempDir);

    Map<String, Integer> annotationCounts = detector.getAnnotationCounts();
    assertThat(annotationCounts)
        .containsEntry("Inject", 2)
        .containsEntry("@Getter", 1)
        .containsEntry("@Autowired", 1)
        .containsEntry("Log", 1);
    assertThat(annotationCounts).doesNotContainKey("");

    Map<String, Long> subtypeCounts = toMap(detector.getTopSubtypes(20));
    assertThat(subtypeCounts.keySet())
        .contains("INJECT_ANNOTATION", "LOMBOK", "CLASS_FORNAME", "LOADCLASS");

    List<Map.Entry<String, Long>> topFiles = detector.getTopFiles(10);
    assertThat(topFiles).hasSize(1);
    assertThat(topFiles.get(0).getKey()).isEqualTo("src/main/java/com/example/WithFile.java");

    assertThat(detector.getTopAnnotations(0)).isEmpty();
  }

  @Test
  void privateHelpers_extractSnippetAndSimpleNames() throws Exception {
    DynamicFeatureDetector detector = new DynamicFeatureDetector();

    Path directFile = tempDir.resolve("Direct.java");
    Files.writeString(directFile, "line1\n  line2  \n");

    Path fallbackDir = tempDir.resolve("src/main/java");
    Files.createDirectories(fallbackDir);
    Path fallbackFile = fallbackDir.resolve("Fallback.java");
    Files.writeString(fallbackFile, "first\n second \n");

    assertThat(
            invokeDetector(
                detector,
                "extractSnippet",
                new Class<?>[] {Path.class, String.class, int.class},
                tempDir,
                null,
                1))
        .isNull();
    assertThat(
            invokeDetector(
                detector,
                "extractSnippet",
                new Class<?>[] {Path.class, String.class, int.class},
                tempDir,
                "Direct.java",
                0))
        .isNull();
    assertThat(
            invokeDetector(
                detector,
                "extractSnippet",
                new Class<?>[] {Path.class, String.class, int.class},
                tempDir,
                "Direct.java",
                2))
        .isEqualTo("line2");
    assertThat(
            invokeDetector(
                detector,
                "extractSnippet",
                new Class<?>[] {Path.class, String.class, int.class},
                tempDir,
                "Fallback.java",
                2))
        .isEqualTo("second");
    assertThat(
            invokeDetector(
                detector,
                "extractSnippet",
                new Class<?>[] {Path.class, String.class, int.class},
                tempDir,
                "Fallback.java",
                20))
        .isNull();

    assertThat(invokeDetectorStatic("simpleNameOf", new Class<?>[] {String.class}, (Object) null))
        .isNull();
    assertThat(invokeDetectorStatic("simpleNameOf", new Class<?>[] {String.class}, "@Autowired"))
        .isEqualTo("Autowired");
    assertThat(invokeDetectorStatic("simpleNameOf", new Class<?>[] {String.class}, "a.b.C"))
        .isEqualTo("C");
    assertThat(invokeDetectorStatic("simpleNameOf", new Class<?>[] {String.class}, "Simple"))
        .isEqualTo("Simple");

    Set<String> simpleNames =
        (Set<String>)
            invokeDetectorStatic(
                "buildSimpleNames",
                new Class<?>[] {Set.class},
                new HashSet<>(Arrays.asList("org.example.Foo", "@Bar", "Plain", "", null)));
    assertThat(simpleNames).contains("Foo", "Bar", "Plain");
    assertThat(simpleNames).doesNotContain("");
  }

  @Test
  void privateMatchesRule_supportsResolvedAndRawPatterns() throws Exception {
    DynamicFeatureDetector detector = new DynamicFeatureDetector();
    Object rule =
        newDetectionRule(
            "java.lang.Class",
            "forName",
            DynamicFeatureType.REFLECTION,
            "CLASS_FORNAME",
            DynamicFeatureSeverity.MEDIUM);

    assertThat(
            invokeMatchesRule(detector, rule, null, "java.lang.Class#forName(Ljava/lang/String;)"))
        .isTrue();
    assertThat(invokeMatchesRule(detector, rule, "Class#forName", null)).isTrue();
    assertThat(invokeMatchesRule(detector, rule, "java.lang.Class#forName", null)).isTrue();
    assertThat(invokeMatchesRule(detector, rule, "Other#forName", null)).isFalse();
    assertThat(invokeMatchesRule(detector, rule, "ClassforName", null)).isFalse();
    assertThat(invokeMatchesRule(detector, rule, null, null)).isFalse();
  }

  private static CalledMethodRef refWithRaw(String raw) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw(raw);
    return ref;
  }

  private static CalledMethodRef refWithResolved(String resolved) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setResolved(resolved);
    return ref;
  }

  private static Map<String, Long> toMap(List<Map.Entry<String, Long>> entries) {
    Map<String, Long> map = new HashMap<>();
    for (Map.Entry<String, Long> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  private static Object invokeDetector(
      DynamicFeatureDetector detector, String methodName, Class<?>[] paramTypes, Object... args)
      throws Exception {
    Method method = DynamicFeatureDetector.class.getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    return method.invoke(detector, args);
  }

  private static Object invokeDetectorStatic(
      String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
    Method method = DynamicFeatureDetector.class.getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  private static Object newDetectionRule(
      String classPattern,
      String methodPattern,
      DynamicFeatureType type,
      String subtype,
      DynamicFeatureSeverity severity)
      throws Exception {
    Class<?> ruleClass =
        Class.forName(
            "com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicFeatureDetector$DetectionRule");
    Constructor<?> constructor =
        ruleClass.getDeclaredConstructor(
            String.class,
            String.class,
            DynamicFeatureType.class,
            String.class,
            DynamicFeatureSeverity.class);
    constructor.setAccessible(true);
    return constructor.newInstance(classPattern, methodPattern, type, subtype, severity);
  }

  private static boolean invokeMatchesRule(
      DynamicFeatureDetector detector, Object rule, String raw, String resolvedFqn)
      throws Exception {
    Method method =
        DynamicFeatureDetector.class.getDeclaredMethod(
            "matchesRule", rule.getClass(), String.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(detector, rule, raw, resolvedFqn);
  }
}
