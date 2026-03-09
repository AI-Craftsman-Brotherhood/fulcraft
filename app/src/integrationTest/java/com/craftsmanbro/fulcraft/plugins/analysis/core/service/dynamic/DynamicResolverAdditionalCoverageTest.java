package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolverAdditionalCoverageTest {

  @TempDir Path tempDir;

  @Test
  void privateSymbolRegistration_handlesNullAndValidEntries() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    resolver.setExternalConfigValues(null);
    resolver.setExternalConfigValues(Map.of("key", "value"));

    ClassInfo nullFqnClass = new ClassInfo();
    nullFqnClass.setFqn(null);
    nullFqnClass.setFields(new ArrayList<>(Arrays.asList(null, field(null), field("id"))));
    nullFqnClass.setMethods(
        List.of(method("first", null, 0), method("second", "second()", 0), method("third", "", 0)));

    ClassInfo validClass = new ClassInfo();
    validClass.setFqn("com.example.Valid");
    validClass.setFields(List.of(field("name")));
    validClass.setMethods(List.of(method("run", "run()", 0)));

    invokePrivate(resolver, "registerClassName", new Class<?>[] {ClassInfo.class}, nullFqnClass);
    invokePrivate(resolver, "registerClassName", new Class<?>[] {ClassInfo.class}, validClass);
    invokePrivate(resolver, "registerFieldSymbols", new Class<?>[] {ClassInfo.class}, nullFqnClass);
    invokePrivate(
        resolver, "registerMethodSymbols", new Class<?>[] {ClassInfo.class}, nullFqnClass);

    Set<String> knownClasses = (Set<String>) getFieldValue(resolver, "knownClasses");
    Set<String> knownFields = (Set<String>) getFieldValue(resolver, "knownFields");
    Set<String> knownMethods = (Set<String>) getFieldValue(resolver, "knownMethods");
    Map<String, String> externalConfigValues =
        (Map<String, String>) getFieldValue(resolver, "externalConfigValues");

    assertThat(knownClasses).contains("com.example.Valid");
    assertThat(knownFields).contains("null#id");
    assertThat(knownMethods)
        .contains("null#first", "null#second", "null#second()", "null#third", "null#");
    assertThat(externalConfigValues).containsEntry("key", "value");
  }

  @Test
  void privateBuildKnownSymbolsAndClassMaps_coverNullAndParentVariants() throws Exception {
    DynamicResolver resolver = new DynamicResolver();

    ClassInfo child = new ClassInfo();
    child.setFqn("com.example.Child");
    child.setImplementsTypes(List.of("java.util.List", "Runnable"));
    child.setExtendsTypes(List.of("com.example.Base", "BaseSimple"));
    child.setMethods(List.of(method("m", "m()", 0)));

    ClassInfo nullFqn = new ClassInfo();
    nullFqn.setFqn(null);
    nullFqn.setMethods(List.of(method("x", "x()", 0)));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(new ArrayList<>(Arrays.asList(null, nullFqn, child)));

    invokePrivate(resolver, "buildKnownSymbols", new Class<?>[] {AnalysisResult.class}, result);
    invokePrivate(resolver, "buildClassMaps", new Class<?>[] {AnalysisResult.class}, result);

    Set<String> knownClasses = (Set<String>) getFieldValue(resolver, "knownClasses");
    Map<String, List<String>> interfaceToImplementations =
        (Map<String, List<String>>) getFieldValue(resolver, "interfaceToImplementations");

    assertThat(knownClasses).contains("com.example.Child").doesNotContainNull();
    assertThat(interfaceToImplementations)
        .containsEntry("java.util.List", List.of("com.example.Child"))
        .containsEntry("List", List.of("com.example.Child"))
        .containsEntry("Runnable", List.of("com.example.Child"))
        .containsEntry("com.example.Base", List.of("com.example.Child"))
        .containsEntry("Base", List.of("com.example.Child"))
        .containsEntry("BaseSimple", List.of("com.example.Child"));
  }

  @Test
  void privateConstantRegistration_marksAmbiguousSimpleNames() throws Exception {
    DynamicResolver resolver = new DynamicResolver();

    invokePrivate(
        resolver,
        "registerStaticStringConstant",
        new Class<?>[] {String.class, String.class, String.class},
        "com.example.Constants",
        "ID",
        "alpha");
    invokePrivate(
        resolver,
        "registerEnumStringConstant",
        new Class<?>[] {String.class, String.class, String.class},
        "com.example.Mode",
        "ID",
        "beta");
    invokePrivate(
        resolver,
        "registerStaticStringConstant",
        new Class<?>[] {String.class, String.class, String.class},
        null,
        "IGNORED",
        "noop");
    invokePrivate(
        resolver,
        "registerSimpleConstant",
        new Class<?>[] {String.class, String.class, Map.class},
        "ID",
        "gamma",
        new java.util.HashMap<String, String>());

    Map<String, String> staticByFqn =
        (Map<String, String>) getFieldValue(resolver, "staticStringConstantsByFqn");
    Map<String, String> enumByFqn =
        (Map<String, String>) getFieldValue(resolver, "enumStringConstantsByFqn");
    Map<String, String> staticBySimple =
        (Map<String, String>) getFieldValue(resolver, "staticStringConstantsBySimple");
    Map<String, String> enumBySimple =
        (Map<String, String>) getFieldValue(resolver, "enumStringConstantsBySimple");
    Set<String> ambiguous = (Set<String>) getFieldValue(resolver, "ambiguousSimpleConstants");

    assertThat(staticByFqn).containsEntry("com.example.Constants.ID", "alpha");
    assertThat(enumByFqn).containsEntry("com.example.Mode.ID", "beta");
    assertThat(staticBySimple).doesNotContainKey("ID");
    assertThat(enumBySimple).doesNotContainKey("ID");
    assertThat(ambiguous).contains("ID");
  }

  @Test
  void privateResolveSourceFile_checksDirectSrcMainAndFqnLookup() throws Exception {
    DynamicResolver resolver = new DynamicResolver();

    Path direct = tempDir.resolve("direct/Foo.java");
    Files.createDirectories(direct.getParent());
    Files.writeString(direct, "class Foo {}");

    Path srcMainFile = tempDir.resolve("src/main/java/com/example/SrcMain.java");
    Files.createDirectories(srcMainFile.getParent());
    Files.writeString(srcMainFile, "package com.example; class SrcMain {}");

    Path fqnFile = tempDir.resolve("src/main/java/com/example/FromFqn.java");
    Files.createDirectories(fqnFile.getParent());
    Files.writeString(fqnFile, "package com.example; class FromFqn {}");

    assertThat(
            invokePrivate(
                resolver,
                "resolveSourceFile",
                new Class<?>[] {Path.class, String.class},
                tempDir,
                "direct/Foo.java"))
        .isEqualTo(direct);
    assertThat(
            invokePrivate(
                resolver,
                "resolveSourceFile",
                new Class<?>[] {Path.class, String.class},
                tempDir,
                "com/example/SrcMain.java"))
        .isEqualTo(srcMainFile);
    assertThat(
            invokePrivate(
                resolver,
                "resolveSourceFile",
                new Class<?>[] {Path.class, String.class},
                tempDir,
                "com.example.FromFqn"))
        .isEqualTo(fqnFile);
    assertThat(
            invokePrivate(
                resolver,
                "resolveSourceFile",
                new Class<?>[] {Path.class, String.class},
                tempDir,
                "missing.File"))
        .isNull();
  }

  @Test
  void privateReadProvidersFromFile_filtersCommentsAndHandlesIoException() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    Path missing = tempDir.resolve("META-INF/services/missing.Service");
    Path serviceFile = tempDir.resolve("META-INF/services/test.Service");
    Files.createDirectories(serviceFile.getParent());
    Files.write(
        serviceFile,
        List.of("  # comment", "com.example.FirstImpl  ", "", "com.example.SecondImpl", "   "));

    Path directory = tempDir.resolve("META-INF/services/dir");
    Files.createDirectories(directory);

    assertThat(
            invokePrivate(resolver, "readProvidersFromFile", new Class<?>[] {Path.class}, missing))
        .isEqualTo(Collections.emptyList());
    assertThat(
            invokePrivate(
                resolver, "readProvidersFromFile", new Class<?>[] {Path.class}, serviceFile))
        .isEqualTo(List.of("com.example.FirstImpl", "com.example.SecondImpl"));
    assertThat(
            invokePrivate(
                resolver, "readProvidersFromFile", new Class<?>[] {Path.class}, directory))
        .isEqualTo(Collections.emptyList());
  }

  @Test
  void resolve_getFieldAndGetDeclaredField_coversFieldResolutionPath() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    createSourceFile(
        "src/main/java/com/example/FieldAccessCaller.java",
        "package com.example;",
        "class FieldAccessCaller {",
        "  void test() {",
        "    try {",
        "      Target.class.getField(\"existing\");",
        "      Target.class.getDeclaredField(\"missing\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    FieldInfo existing = field("existing");
    FieldInfo unnamed = field(null);
    ClassInfo caller =
        classInfo("com.example.FieldAccessCaller", "com/example/FieldAccessCaller.java");
    ClassInfo target = classInfo("com.example.Target", null);
    target.setFields(new ArrayList<>(Arrays.asList(existing, unnamed)));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(caller, target));

    resolver.resolve(result, tempDir);

    List<DynamicResolution> fieldResolutions =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.FIELD_RESOLVE.equals(r.subtype()))
            .toList();

    assertThat(fieldResolutions).hasSize(2);
    DynamicResolution verified =
        fieldResolutions.stream()
            .filter(r -> "existing".equals(r.resolvedMethodSig()))
            .findFirst()
            .orElseThrow();
    DynamicResolution unverified =
        fieldResolutions.stream()
            .filter(r -> "missing".equals(r.resolvedMethodSig()))
            .findFirst()
            .orElseThrow();

    assertThat(verified.evidence()).containsEntry("verified", "true");
    assertThat(verified.confidence()).isEqualTo(1.0);
    assertThat(unverified.evidence()).containsEntry("verified", "false");
    assertThat(unverified.confidence()).isCloseTo(0.8, within(0.0001));
  }

  @Test
  void resolve_withCandidateEnumeration_emitsExperimentalClassCandidates() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    createSourceFile(
        "src/main/java/com/example/ClassCandidateCaller.java",
        "package com.example;",
        "class ClassCandidateCaller {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"Type\");",
        "      Class.forName(\"com.unknown.Type\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    ClassInfo caller =
        classInfo("com.example.ClassCandidateCaller", "com/example/ClassCandidateCaller.java");
    ClassInfo candidate = classInfo("com.example.Type", null);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(caller, candidate));

    resolver.resolve(result, tempDir, false, 20, false, true);

    List<DynamicResolution> experimental =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.EXPERIMENTAL_CANDIDATES.equals(r.subtype()))
            .toList();
    DynamicResolution literal =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.CLASS_FORNAME_LITERAL.equals(r.subtype()))
            .findFirst()
            .orElseThrow();

    assertThat(experimental).hasSize(2);
    assertThat(experimental.get(0).candidates()).contains("com.example.Type");
    assertThat(experimental.get(1).candidates()).contains("com.example.Type");
    assertThat(literal.resolvedClassFqn()).isEqualTo("com.unknown.Type");
    assertThat(literal.confidence()).isCloseTo(0.8, within(0.0001));
  }

  @Test
  void resolve_withCandidateEnumeration_emitsExperimentalMethodCandidates() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    createSourceFile(
        "src/main/java/com/example/MethodCandidateCaller.java",
        "package com.example;",
        "class MethodCandidateCaller {",
        "  void test() {",
        "    try {",
        "      Target.class.getMethod(\"missing\", String.class);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    ClassInfo caller =
        classInfo("com.example.MethodCandidateCaller", "com/example/MethodCandidateCaller.java");
    ClassInfo target = classInfo("com.example.Target", null);
    target.setMethods(
        List.of(
            method("foo", "foo(String)", 1), method("bar", "bar(String)", 1), method("", "()", 0)));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(caller, target));

    resolver.resolve(result, tempDir, false, 20, false, true);

    DynamicResolution candidateResolution =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.EXPERIMENTAL_CANDIDATES.equals(r.subtype()))
            .findFirst()
            .orElseThrow();
    DynamicResolution methodResolution =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.METHOD_RESOLVE.equals(r.subtype()))
            .findFirst()
            .orElseThrow();

    assertThat(candidateResolution.candidates()).containsExactly("bar", "foo");
    assertThat(candidateResolution.evidence()).containsEntry("method_literal", "missing");
    assertThat(methodResolution.reasonCode()).isEqualTo(DynamicReasonCode.TARGET_METHOD_MISSING);
    assertThat(methodResolution.evidence()).containsEntry("verified", "false");
  }

  @Test
  void getAverageConfidenceAndTrustLevelCounts_handleEmptyAndFilledResolutions() throws Exception {
    DynamicResolver resolver = new DynamicResolver();
    assertThat(resolver.getAverageConfidence()).isEqualTo(0.0);
    assertThat(resolver.countByTrustLevel()).isEmpty();

    List<DynamicResolution> resolutions =
        (List<DynamicResolution>) getFieldValue(resolver, "resolutions");
    resolutions.add(
        DynamicResolution.builder()
            .subtype(DynamicResolution.CLASS_FORNAME_LITERAL)
            .confidence(1.0)
            .trustLevel(TrustLevel.HIGH)
            .build());
    resolutions.add(
        DynamicResolution.builder()
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.6)
            .trustLevel(TrustLevel.MEDIUM)
            .build());

    assertThat(resolver.getAverageConfidence()).isCloseTo(0.8, within(0.0001));
    assertThat(resolver.countByTrustLevel()).containsEntry("HIGH", 1L).containsEntry("MEDIUM", 1L);
  }

  private Path createSourceFile(String relativePath, String... lines) throws Exception {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, List.of(lines));
    return file;
  }

  private static ClassInfo classInfo(String fqn, String filePath) {
    ClassInfo info = new ClassInfo();
    info.setFqn(fqn);
    info.setFilePath(filePath);
    info.setMethods(Collections.emptyList());
    return info;
  }

  private static MethodInfo method(String name, String signature, int paramCount) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    method.setParameterCount(paramCount);
    return method;
  }

  private static FieldInfo field(String name) {
    FieldInfo field = new FieldInfo();
    field.setName(name);
    return field;
  }

  private static Object invokePrivate(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private static Object getFieldValue(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }
}
