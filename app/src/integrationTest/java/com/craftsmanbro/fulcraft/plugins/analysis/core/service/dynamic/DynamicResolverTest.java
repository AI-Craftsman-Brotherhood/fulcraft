package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolverTest {

  private DynamicResolver resolver;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    resolver = new DynamicResolver();
  }

  @Test
  void resolve_classForName_literal() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/Foo.java",
        "package com.example;",
        "class Foo {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.Foo", "com/example/Foo.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Bar");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    assertThat(res.confidence()).isEqualTo(0.8); // Not known class
  }

  @Test
  void resolve_getMethod_literal() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/Foo.java",
        "package com.example;",
        "class Foo {",
        "  void test() {",
        "    try {",
        "      Integer.class.getMethod(\"parseInt\", String.class);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.Foo", "com/example/Foo.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedMethodSig()).isEqualTo("parseInt");
    assertThat(res.resolvedClassFqn()).isEqualTo("Integer");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.METHOD_RESOLVE);
    assertThat(res.reasonCode()).isEqualTo(DynamicReasonCode.TARGET_CLASS_UNRESOLVED);
  }

  @Test
  void resolve_getMethod_knownTargetClassButMissingMethod_setsTargetMethodMissingReasonCode()
      throws Exception {
    createSourceFile(
        "src/main/java/com/example/DynamicSample.java",
        "package com.example;",
        "class CustomerService {",
        "  public void addCustomer(String customerId) {}",
        "}",
        "class DynamicSample {",
        "  void test() {",
        "    try {",
        "      CustomerService.class.getMethod(\"processCustomer\", String.class);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    MethodInfo addCustomer = new MethodInfo();
    addCustomer.setName("addCustomer");
    addCustomer.setSignature("public void addCustomer(String customerId)");
    addCustomer.setParameterCount(1);

    ClassInfo caller = new ClassInfo();
    caller.setFqn("com.example.DynamicSample");
    caller.setFilePath("com/example/DynamicSample.java");
    caller.setMethods(Collections.emptyList());

    ClassInfo target = new ClassInfo();
    target.setFqn("com.example.CustomerService");
    target.setFilePath("com/example/DynamicSample.java");
    target.setMethods(List.of(addCustomer));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(caller, target));

    resolver.resolve(result, tempDir);

    DynamicResolution methodResolution =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.METHOD_RESOLVE.equals(r.subtype()))
            .findFirst()
            .orElseThrow();

    assertThat(methodResolution.reasonCode()).isEqualTo(DynamicReasonCode.TARGET_METHOD_MISSING);
    assertThat(methodResolution.evidence().get("verified")).isEqualTo("false");
  }

  @Test
  void resolve_getMethod_unknownTargetClass_setsTargetClassUnresolvedReasonCode() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DynamicSample.java",
        "package com.example;",
        "class DynamicSample {",
        "  void test() {",
        "    try {",
        "      Class<?> clazz = Class.forName(\"com.example.UnknownService\");",
        "      clazz.getMethod(\"processCustomer\", String.class);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DynamicSample", "com/example/DynamicSample.java");

    resolver.resolve(result, tempDir);

    DynamicResolution methodResolution =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.METHOD_RESOLVE.equals(r.subtype()))
            .findFirst()
            .orElseThrow();

    assertThat(methodResolution.reasonCode()).isEqualTo(DynamicReasonCode.TARGET_CLASS_UNRESOLVED);
    assertThat(methodResolution.evidence().get("verified")).isEqualTo("false");
  }

  @Test
  void resolve_getDeclaredMethod_withClassVariableResolvesTargetClass() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ReflectVarTest.java",
        "package com.example;",
        "class Util {",
        "  void truncate(String value, int length) {}",
        "}",
        "class ReflectVarTest {",
        "  void test() {",
        "    try {",
        "      Class<?> clazz = Class.forName(\"com.example.Util\");",
        "      clazz.getDeclaredMethod(\"truncate\", String.class, int.class);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    MethodInfo truncate = new MethodInfo();
    truncate.setName("truncate");
    truncate.setSignature("truncate(String, int)");
    truncate.setParameterCount(2);

    ClassInfo caller = new ClassInfo();
    caller.setFqn("com.example.ReflectVarTest");
    caller.setFilePath("com/example/ReflectVarTest.java");
    caller.setMethods(Collections.emptyList());

    ClassInfo target = new ClassInfo();
    target.setFqn("com.example.Util");
    target.setFilePath("com/example/ReflectVarTest.java");
    target.setMethods(List.of(truncate));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(caller, target));

    resolver.resolve(result, tempDir);

    DynamicResolution methodResolution =
        resolver.getResolutions().stream()
            .filter(r -> DynamicResolution.METHOD_RESOLVE.equals(r.subtype()))
            .findFirst()
            .orElseThrow();

    assertThat(methodResolution.resolvedClassFqn()).isEqualTo("com.example.Util");
    assertThat(methodResolution.evidence().get("target_class")).isEqualTo("com.example.Util");
    assertThat(methodResolution.evidence().get("verified")).isEqualTo("true");
    assertThat(methodResolution.evidence().get("arity_match")).isEqualTo("true");
    assertThat(methodResolution.confidence()).isEqualTo(1.0);
  }

  @Test
  void resolve_ServiceLoader_load() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/Loader.java",
        "package com.example;",
        "import java.util.ServiceLoader;",
        "class Loader {",
        "  void load() {",
        "    ServiceLoader.load(MyService.class);",
        "  }",
        "}");

    // Create service file
    Path metaInf = tempDir.resolve("src/main/resources/META-INF/services");
    Files.createDirectories(metaInf);
    Files.writeString(metaInf.resolve("MyService"), "com.example.MyServiceImpl");

    AnalysisResult result = createAnalysisResult("com.example.Loader", "com/example/Loader.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("MyService");
    assertThat(res.providers()).containsExactly("com.example.MyServiceImpl");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.SERVICELOADER_PROVIDERS);
  }

  @Test
  void resolve_ServiceLoader_projectCandidates() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/Loader.java",
        "package com.example;",
        "import java.util.ServiceLoader;",
        "class Loader {",
        "  void load() {",
        "    ServiceLoader.load(MyService.class);",
        "  }",
        "}");

    // Mock AnalysisResult with Interface and Implementation info
    ClassInfo interfaceInfo = new ClassInfo();
    interfaceInfo.setFqn("com.example.MyService");
    interfaceInfo.setInterface(true);

    ClassInfo implInfo = new ClassInfo();
    implInfo.setFqn("com.example.MyServiceImpl");
    implInfo.setImplementsTypes(List.of("com.example.MyService"));
    implInfo.setInterface(false);
    implInfo.setAbstract(false);

    // Also add Loader class
    ClassInfo loaderInfo = new ClassInfo();
    loaderInfo.setFqn("com.example.Loader");
    loaderInfo.setFilePath("src/main/java/com/example/Loader.java");
    loaderInfo.setMethods(Collections.emptyList());

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(interfaceInfo, implInfo, loaderInfo));

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.MyService");
    assertThat(res.providers()).containsExactly("com.example.MyServiceImpl");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.SERVICELOADER_PROVIDERS);
    assertThat(res.confidence()).isEqualTo(1.0); // Single candidate found
  }

  @Test
  void resolve_classForName_variable() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/VarTest.java",
        "package com.example;",
        "class VarTest {",
        "  void test() {",
        "    try {",
        "      String className = \"com.example.Dynamic\";",
        "      Class.forName(className);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.VarTest", "com/example/VarTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Dynamic");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    // Variable (0.8) - Unknown Penalty (0.2) = 0.6
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
  }

  @Test
  void resolve_classForName_concatenation() throws Exception {
    // Given
    createSourceFile(
        "src/main/java/com/example/ConcatTest.java",
        "package com.example;",
        "class ConcatTest {",
        "  void test() {",
        "    try {",
        "      String pkg = \"com.example.\";",
        "      Class.forName(pkg + \"Concat\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ConcatTest", "com/example/ConcatTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Concat");
    // Variable/Expr (0.8) - Unknown Penalty (0.2) = 0.6
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
  }

  @Test
  void resolve_classForName_staticConstant() throws Exception {
    createSourceFile(
        "src/main/java/com/example/StaticConstTest.java",
        "package com.example;",
        "class StaticConstTest {",
        "  public static final String PREFIX = \"com.x.\";",
        "  void test() {",
        "    try {",
        "      Class.forName(PREFIX + \"Foo\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.StaticConstTest", "com/example/StaticConstTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.x.Foo");
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
  }

  @Test
  void resolve_classForName_staticConstant_requiresPublic() throws Exception {
    createSourceFile(
        "src/main/java/com/example/StaticConstHiddenTest.java",
        "package com.example;",
        "class StaticConstHiddenTest {",
        "  static final String PREFIX = \"com.x.\";",
        "  void test() {",
        "    try {",
        "      Class.forName(PREFIX + \"Foo\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult(
            "com.example.StaticConstHiddenTest", "com/example/StaticConstHiddenTest.java");

    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }

  @Test
  void resolve_classForName_staticConstant_literalConcat() throws Exception {
    createSourceFile(
        "src/main/java/com/example/StaticConstConcatTest.java",
        "package com.example;",
        "class StaticConstConcatTest {",
        "  public static final String PREFIX = \"com.\" + \"x.\";",
        "  void test() {",
        "    try {",
        "      Class.forName(PREFIX + \"Foo\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult(
            "com.example.StaticConstConcatTest", "com/example/StaticConstConcatTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.x.Foo");
  }

  @Test
  void resolve_classForName_enumNameCall_isUnresolved() throws Exception {
    createSourceFile(
        "src/main/java/com/example/EnumUser.java",
        "package com.example;",
        "enum Role {",
        "  ADMIN(\"admin\"), USER(\"user\");",
        "  private final String id;",
        "  Role(String id) { this.id = id; }",
        "}",
        "class EnumUser {",
        "  void test() {",
        "    try {",
        "      Class.forName(Role.ADMIN.name());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.EnumUser", "com/example/EnumUser.java");

    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }

  @Test
  void resolve_classForName_enumToStringField() throws Exception {
    createSourceFile(
        "src/main/java/com/example/EnumToStringUser.java",
        "package com.example;",
        "enum Role {",
        "  ADMIN(\"admin\"), USER(\"user\");",
        "  private final String id;",
        "  Role(String id) { this.id = id; }",
        "  @Override public String toString() { return id; }",
        "}",
        "class EnumToStringUser {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.\" + Role.ADMIN);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.EnumToStringUser", "com/example/EnumToStringUser.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.admin");
  }

  @Test
  void resolve_classForName_stringConcatCall() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ConcatCallTest.java",
        "package com.example;",
        "class ConcatCallTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.x.\".concat(\"Foo\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ConcatCallTest", "com/example/ConcatCallTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.x.Foo");
  }

  @Test
  void resolve_classForName_stringJoinCall() throws Exception {
    createSourceFile(
        "src/main/java/com/example/JoinCallTest.java",
        "package com.example;",
        "class JoinCallTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.join(\".\", \"com\", \"x\", \"Foo\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.JoinCallTest", "com/example/JoinCallTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.x.Foo");
  }

  @Test
  void resolve_classForName_stringValueOfCalls() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfTest.java",
        "package com.example;",
        "class ValueOfTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.\" + String.valueOf('x'));",
        "      Class.forName(\"com.\" + String.valueOf(123));",
        "      Class.forName(\"com.\" + String.valueOf(true));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfTest", "com/example/ValueOfTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(3);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.x");
    assertThat(resolutions.get(1).resolvedClassFqn()).isEqualTo("com.123");
    assertThat(resolutions.get(2).resolvedClassFqn()).isEqualTo("com.true");
  }

  @Test
  void resolve_classForName_stringValueOfFloatingLiterals() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfFloatTest.java",
        "package com.example;",
        "class ValueOfFloatTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.\" + String.valueOf(1.25f));",
        "      Class.forName(\"com.\" + String.valueOf(2.5d));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfFloatTest", "com/example/ValueOfFloatTest.java");

    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(2);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.1.25");
    assertThat(resolutions.get(1).resolvedClassFqn()).isEqualTo("com.2.5");
  }

  @Test
  void resolve_classForName_stringConcatUnresolvedArgument() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ConcatParamTest.java",
        "package com.example;",
        "class ConcatParamTest {",
        "  void test(String suffix) {",
        "    try {",
        "      Class.forName(\"a\".concat(suffix));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ConcatParamTest", "com/example/ConcatParamTest.java");

    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }

  @Test
  void resolve_classForName_stringJoinUnresolvedArgument() throws Exception {
    createSourceFile(
        "src/main/java/com/example/JoinParamTest.java",
        "package com.example;",
        "class JoinParamTest {",
        "  void test(String part) {",
        "    try {",
        "      Class.forName(String.join(\".\", \"a\", part));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.JoinParamTest", "com/example/JoinParamTest.java");

    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }

  @Test
  void resolve_classForName_stringValueOfUnresolvedArgument() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfParamTest.java",
        "package com.example;",
        "class ValueOfParamTest {",
        "  void test(int value) {",
        "    try {",
        "      Class.forName(String.valueOf(value));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfParamTest", "com/example/ValueOfParamTest.java");

    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }

  private Path createSourceFile(String relativePath, String... lines) throws Exception {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, List.of(lines));
    return file;
  }

  private AnalysisResult createAnalysisResult(String fqn, String filePath) {
    ClassInfo info = new ClassInfo();
    info.setFqn(fqn);
    info.setFilePath(filePath);
    info.setMethods(Collections.emptyList());

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(info));
    return result;
  }

  @Test
  void resolve_classForName_ifElseBranch() throws Exception {
    // Given: if (cond) cn = "a.A"; else cn = "b.B"; Class.forName(cn);
    createSourceFile(
        "src/main/java/com/example/BranchTest.java",
        "package com.example;",
        "class BranchTest {",
        "  void test(boolean cond) {",
        "    try {",
        "      String cn;",
        "      if (cond) {",
        "        cn = \"com.example.A\";",
        "      } else {",
        "        cn = \"com.example.B\";",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.BranchTest", "com/example/BranchTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(res.candidates()).containsExactly("com.example.A", "com.example.B");
    assertThat(res.confidence()).isLessThanOrEqualTo(0.6);
    assertThat(res.evidence().get("provenance")).isEqualTo("branch_candidates");
  }

  @Test
  void resolve_classForName_switchBranch() throws Exception {
    // Given: switch(x) { case 1: cn="a.A"; break; case 2: cn="b.B"; break; }
    createSourceFile(
        "src/main/java/com/example/SwitchTest.java",
        "package com.example;",
        "class SwitchTest {",
        "  void test(int x) {",
        "    try {",
        "      String cn;",
        "      switch (x) {",
        "        case 1: cn = \"com.example.One\"; break;",
        "        case 2: cn = \"com.example.Two\"; break;",
        "        default: cn = \"com.example.Default\"; break;",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SwitchTest", "com/example/SwitchTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(res.candidates())
        .containsExactly("com.example.Default", "com.example.One", "com.example.Two");
    assertThat(res.confidence()).isLessThanOrEqualTo(0.6);
  }

  @Test
  void resolve_classForName_branchExceedsLimit() throws Exception {
    // Given: switch with >10 cases assigning different classes
    StringBuilder switchCases = new StringBuilder();
    for (int i = 1; i <= 12; i++) {
      switchCases
          .append("        case ")
          .append(i)
          .append(": cn = \"com.example.Class")
          .append(i)
          .append("\"; break;\n");
    }

    createSourceFile(
        "src/main/java/com/example/ManyBranchTest.java",
        "package com.example;",
        "class ManyBranchTest {",
        "  void test(int x) {",
        "    try {",
        "      String cn;",
        "      switch (x) {",
        switchCases.toString().trim(),
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ManyBranchTest", "com/example/ManyBranchTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    // Should be truncated to 10
    assertThat(res.candidates().size()).isLessThanOrEqualTo(10);
    assertThat(res.evidence().get("truncated")).isEqualTo("true");
  }

  @Test
  void resolve_getMethod_ifElseBranch() throws Exception {
    // Given: if (cond) methodName = "foo"; else methodName = "bar";
    // getMethod(methodName);
    createSourceFile(
        "src/main/java/com/example/MethodBranchTest.java",
        "package com.example;",
        "class MethodBranchTest {",
        "  void test(boolean cond) {",
        "    try {",
        "      String methodName;",
        "      if (cond) {",
        "        methodName = \"foo\";",
        "      } else {",
        "        methodName = \"bar\";",
        "      }",
        "      Integer.class.getMethod(methodName);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.MethodBranchTest", "com/example/MethodBranchTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(res.candidates()).containsExactly("bar", "foo");
    assertThat(res.confidence()).isLessThanOrEqualTo(0.6);
    assertThat(res.evidence().get("pattern")).isEqualTo("getMethod");
  }

  // =============================================
  // StringBuilder Append Chain Resolution Tests
  // =============================================

  @Test
  void resolve_classForName_stringBuilderChain() throws Exception {
    // Given: new StringBuilder().append("com.x.").append("Foo").toString()
    createSourceFile(
        "src/main/java/com/example/SbTest.java",
        "package com.example;",
        "class SbTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(new StringBuilder().append(\"com.x.\").append(\"Foo\").toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.SbTest", "com/example/SbTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.x.Foo");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    // Inferred (0.8) - Unknown class penalty (0.2) = 0.6
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
  }

  @Test
  void resolve_classForName_stringBuilderWithInitial() throws Exception {
    // Given: new StringBuilder("com.x.").append("Bar").toString()
    createSourceFile(
        "src/main/java/com/example/SbInitTest.java",
        "package com.example;",
        "class SbInitTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(new StringBuilder(\"com.x.\").append(\"Bar\").toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SbInitTest", "com/example/SbInitTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.x.Bar");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
  }

  @Test
  void resolve_classForName_stringBuilderWithVariable() throws Exception {
    // Given: String pkg = "com.x."; new StringBuilder(pkg).append("Baz").toString()
    createSourceFile(
        "src/main/java/com/example/SbVarTest.java",
        "package com.example;",
        "class SbVarTest {",
        "  void test() {",
        "    try {",
        "      String pkg = \"com.x.\";",
        "      Class.forName(new StringBuilder(pkg).append(\"Baz\").toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SbVarTest", "com/example/SbVarTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.x.Baz");
  }

  @Test
  void resolve_classForName_stringBuilderReused_notResolved() throws Exception {
    // Given: StringBuilder variable is reused - should NOT be resolved
    createSourceFile(
        "src/main/java/com/example/SbReuseTest.java",
        "package com.example;",
        "class SbReuseTest {",
        "  void test() {",
        "    try {",
        "      StringBuilder sb = new StringBuilder(\"com.\");",
        "      sb.append(\"x.\");",
        "      sb.append(\"Reuse\");",
        "      Class.forName(sb.toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SbReuseTest", "com/example/SbReuseTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (builder variable reuse is not supported)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_stringBuilderUnresolved() throws Exception {
    // Given: append argument is a method call - cannot resolve
    createSourceFile(
        "src/main/java/com/example/SbUnresolvedTest.java",
        "package com.example;",
        "class SbUnresolvedTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(new StringBuilder(\"com.\").append(getPackage()).toString());",
        "    } catch (Exception e) {}",
        "  }",
        "  String getPackage() { return \"x.\"; }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SbUnresolvedTest", "com/example/SbUnresolvedTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (unresolvable append argument)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_stringBufferChain() throws Exception {
    // Given: StringBuffer is also supported
    createSourceFile(
        "src/main/java/com/example/SbufTest.java",
        "package com.example;",
        "class SbufTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(new StringBuffer().append(\"com.y.\").append(\"Buf\").toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SbufTest", "com/example/SbufTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.y.Buf");
  }

  // =============================================
  // String.format Resolution Tests
  // =============================================

  @Test
  void resolve_classForName_stringFormat_literal() throws Exception {
    // Given: String.format("com.%s.Impl", "example")
    createSourceFile(
        "src/main/java/com/example/FmtLitTest.java",
        "package com.example;",
        "class FmtLitTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%s.Impl\", \"example\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtLitTest", "com/example/FmtLitTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Impl");
    assertThat(res.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    // Literal (1.0) - Unknown class penalty (0.2) = 0.8
    assertThat(res.confidence()).isCloseTo(0.8, offset(0.001));
  }

  @Test
  void resolve_classForName_stringFormat_variable() throws Exception {
    // Given: String pkg = "example"; String.format("com.%s.Impl", pkg)
    createSourceFile(
        "src/main/java/com/example/FmtVarTest.java",
        "package com.example;",
        "class FmtVarTest {",
        "  void test() {",
        "    try {",
        "      String pkg = \"example\";",
        "      Class.forName(String.format(\"com.%s.Impl\", pkg));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtVarTest", "com/example/FmtVarTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Impl");
    // Variable (0.8) - Unknown class penalty (0.2) = 0.6
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
  }

  @Test
  void resolve_classForName_stringFormat_multipleArgs() throws Exception {
    // Given: String.format("com.%s.%sImpl", "x", "Y")
    createSourceFile(
        "src/main/java/com/example/FmtMultiTest.java",
        "package com.example;",
        "class FmtMultiTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%s.%sImpl\", \"x\", \"Y\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtMultiTest", "com/example/FmtMultiTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.x.YImpl");
  }

  @Test
  void resolve_classForName_stringFormat_concatenatedArg() throws Exception {
    // Given: String.format("com.%s.Impl", "a" + "b")
    createSourceFile(
        "src/main/java/com/example/FmtConcatTest.java",
        "package com.example;",
        "class FmtConcatTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%s.Impl\", \"a\" + \"b\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtConcatTest", "com/example/FmtConcatTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.ab.Impl");
  }

  @Test
  void resolve_classForName_stringFormat_formatVariable_notResolved() throws Exception {
    // Given: String fmt = "..."; String.format(fmt, x) - should NOT resolve
    createSourceFile(
        "src/main/java/com/example/FmtVarFmtTest.java",
        "package com.example;",
        "class FmtVarFmtTest {",
        "  void test() {",
        "    try {",
        "      String fmt = \"com.%s.Impl\";",
        "      Class.forName(String.format(fmt, \"x\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtVarFmtTest", "com/example/FmtVarFmtTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (format is variable)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_stringFormat_percentD_notResolved() throws Exception {
    // Given: String.format("com.%d.Impl", 123) - should NOT resolve
    createSourceFile(
        "src/main/java/com/example/FmtPercentDTest.java",
        "package com.example;",
        "class FmtPercentDTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%d.Impl\", 123));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtPercentDTest", "com/example/FmtPercentDTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (unsupported specifier)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_stringFormat_unresolvedArg_notResolved() throws Exception {
    // Given: String.format("com.%s.Impl", getPackage()) - should NOT resolve
    createSourceFile(
        "src/main/java/com/example/FmtUnresolvedTest.java",
        "package com.example;",
        "class FmtUnresolvedTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%s.Impl\", getPackage()));",
        "    } catch (Exception e) {}",
        "  }",
        "  String getPackage() { return \"x\"; }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.FmtUnresolvedTest", "com/example/FmtUnresolvedTest.java");

    // When
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (unresolvable argument)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  // =============================================
  // 1-Hop Inter-Procedural Resolution Tests
  // =============================================

  @Test
  void resolve_classForName_interProcedural_singleCallSite() throws Exception {
    // Given: void load(String cn){ Class.forName(cn); } + caller: load("a.A")
    createSourceFile(
        "src/main/java/com/example/InterProc1.java",
        "package com.example;",
        "class InterProc1 {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void caller() {",
        "    load(\"com.example.Target\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.InterProc1", "com/example/InterProc1.java");

    // When: Enable inter-procedural resolution
    resolver.resolve(result, tempDir, true, 20);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.INTERPROCEDURAL_SINGLE);
    assertThat(res.resolvedClassFqn()).isEqualTo("com.example.Target");
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001)); // 0.7 - 0.1 (unverified)
    assertThat(res.evidence().get("provenance")).isEqualTo("interprocedural_single");
    assertThat(res.evidence().get("source_method")).isEqualTo("load#1");
  }

  @Test
  void resolve_classForName_interProcedural_multipleCallSites() throws Exception {
    // Given: load(\"a.A\") and load(\"b.B\") -> Class.forName(cn)
    createSourceFile(
        "src/main/java/com/example/InterProcMulti.java",
        "package com.example;",
        "class InterProcMulti {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void caller1() {",
        "    load(\"com.example.First\");",
        "  }",
        "  void caller2() {",
        "    load(\"com.example.Second\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.InterProcMulti", "com/example/InterProcMulti.java");

    // When
    resolver.resolve(result, tempDir, true, 20);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(res.candidates()).containsExactly("com.example.First", "com.example.Second");
    assertThat(res.confidence()).isCloseTo(0.6, offset(0.001));
    assertThat(res.evidence().get("provenance")).isEqualTo("interprocedural_candidates");
  }

  @Test
  void resolve_classForName_interProcedural_callSiteLimitExceeded() throws Exception {
    // Given: More than limit (3) call sites
    StringBuilder callers = new StringBuilder();
    for (int i = 1; i <= 5; i++) {
      callers.append("  void caller").append(i).append("() {\n");
      callers.append("    load(\"com.example.Class").append(i).append("\");\n");
      callers.append("  }\n");
    }

    createSourceFile(
        "src/main/java/com/example/InterProcLimit.java",
        "package com.example;",
        "class InterProcLimit {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        callers.toString().trim(),
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.InterProcLimit", "com/example/InterProcLimit.java");

    // When: Set limit to 3 (should truncate)
    resolver.resolve(result, tempDir, true, 3);

    // Then
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);

    DynamicResolution res = resolutions.get(0);
    assertThat(res.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    // Should only have 3 candidates (limit applied)
    assertThat(res.candidates().size()).isLessThanOrEqualTo(3);
    assertThat(res.evidence().get("truncated")).isEqualTo("true");
  }

  @Test
  void resolve_classForName_interProcedural_featureDisabled() throws Exception {
    // Given: Same setup as single call site test
    createSourceFile(
        "src/main/java/com/example/InterProcOff.java",
        "package com.example;",
        "class InterProcOff {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void caller() {",
        "    load(\"com.example.Target\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.InterProcOff", "com/example/InterProcOff.java");

    // When: Inter-procedural resolution is OFF (default)
    resolver.resolve(result, tempDir);

    // Then: Should NOT resolve (feature off)
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_interProcedural_overloadMatch() throws Exception {
    // Given: Overloaded methods - should match by param count
    createSourceFile(
        "src/main/java/com/example/InterProcOverload.java",
        "package com.example;",
        "class InterProcOverload {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void load(String cn, boolean init) {",
        "    // Different overload, should not match",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void caller1() {",
        "    load(\"com.example.Single\");  // Matches load(String)",
        "  }",
        "  void caller2() {",
        "    load(\"com.example.Init\", true);  // Matches load(String, boolean)",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.InterProcOverload", "com/example/InterProcOverload.java");

    // When
    resolver.resolve(result, tempDir, true, 20);

    // Then: Should have 2 resolutions, one for each overload
    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(2);

    // Find the single-param resolution
    DynamicResolution singleParam =
        resolutions.stream()
            .filter(r -> "load#1".equals(r.evidence().get("source_method")))
            .findFirst()
            .orElse(null);
    assertThat(singleParam).isNotNull();
    assertThat(singleParam.resolvedClassFqn()).isEqualTo("com.example.Single");
    assertThat(singleParam.subtype()).isEqualTo(DynamicResolution.INTERPROCEDURAL_SINGLE);

    // Find the two-param resolution
    DynamicResolution twoParam =
        resolutions.stream()
            .filter(r -> "load#2".equals(r.evidence().get("source_method")))
            .findFirst()
            .orElse(null);
    assertThat(twoParam).isNotNull();
    assertThat(twoParam.resolvedClassFqn()).isEqualTo("com.example.Init");
  }

  // =============================================
  // Reason Code Tests
  // =============================================

  @Test
  void resolve_branchCandidates_setsAmbiguousCandidatesReasonCode() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ReasonCodeTest.java",
        "package com.example;",
        "class ReasonCodeTest {",
        "  void test(boolean cond) {",
        "    try {",
        "      String cn;",
        "      if (cond) {",
        "        cn = \"com.example.A\";",
        "      } else {",
        "        cn = \"com.example.B\";",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ReasonCodeTest", "com/example/ReasonCodeTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolutions.get(0).reasonCode())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode
                .AMBIGUOUS_CANDIDATES);
  }

  @Test
  void resolve_candidateLimitExceeded_setsCandidateLimitExceededReasonCode() throws Exception {
    StringBuilder switchCases = new StringBuilder();
    for (int i = 1; i <= 12; i++) {
      switchCases
          .append("        case ")
          .append(i)
          .append(": cn = \"com.example.Class")
          .append(i)
          .append("\"; break;\n");
    }

    createSourceFile(
        "src/main/java/com/example/LimitTest.java",
        "package com.example;",
        "class LimitTest {",
        "  void test(int x) {",
        "    try {",
        "      String cn;",
        "      switch (x) {",
        switchCases.toString().trim(),
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.LimitTest", "com/example/LimitTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolutions.get(0).reasonCode())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode
                .CANDIDATE_LIMIT_EXCEEDED);
    assertThat(resolutions.get(0).evidence().get("truncated")).isEqualTo("true");
  }

  @Test
  void resolve_successfulResolution_hasNullReasonCode() throws Exception {
    createSourceFile(
        "src/main/java/com/example/SuccessTest.java",
        "package com.example;",
        "class SuccessTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.SuccessTest", "com/example/SuccessTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    assertThat(resolutions.get(0).reasonCode()).isNull();
  }

  @Test
  void resolve_getMethodBranch_setsAmbiguousCandidatesReasonCode() throws Exception {
    createSourceFile(
        "src/main/java/com/example/MethodReasonTest.java",
        "package com.example;",
        "class MethodReasonTest {",
        "  void test(boolean cond) {",
        "    try {",
        "      String methodName;",
        "      if (cond) {",
        "        methodName = \"foo\";",
        "      } else {",
        "        methodName = \"bar\";",
        "      }",
        "      Integer.class.getMethod(methodName);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.MethodReasonTest", "com/example/MethodReasonTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolutions.get(0).reasonCode())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode
                .AMBIGUOUS_CANDIDATES);
  }

  // =============================================
  // Debug Mode Tests
  // =============================================

  @Test
  void resolve_withDebugMode_producesResolutions() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugTest.java",
        "package com.example;",
        "class DebugTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DebugTest", "com/example/DebugTest.java");
    resolver.resolve(result, tempDir, false, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Bar");
  }

  @Test
  void resolve_withoutDebugMode_existingBehaviorUnchanged() throws Exception {
    createSourceFile(
        "src/main/java/com/example/NoDebugTest.java",
        "package com.example;",
        "class NoDebugTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.NoDebugTest", "com/example/NoDebugTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Bar");
  }

  @Test
  void resolve_withDebugMode_branchCandidates_producesCorrectResolution() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugBranchTest.java",
        "package com.example;",
        "class DebugBranchTest {",
        "  void test(boolean cond) {",
        "    try {",
        "      String cn;",
        "      if (cond) {",
        "        cn = \"com.example.A\";",
        "      } else {",
        "        cn = \"com.example.B\";",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DebugBranchTest", "com/example/DebugBranchTest.java");
    resolver.resolve(result, tempDir, false, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolutions.get(0).candidates())
        .containsExactlyInAnyOrder("com.example.A", "com.example.B");
    assertThat(resolutions.get(0).reasonCode())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode
                .AMBIGUOUS_CANDIDATES);
  }

  @Test
  void resolve_withDebugMode_methodResolve_producesCorrectResolution() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugMethodTest.java",
        "package com.example;",
        "class DebugMethodTest {",
        "  void test() throws Exception {",
        "    Integer.class.getMethod(\"intValue\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DebugMethodTest", "com/example/DebugMethodTest.java");
    resolver.resolve(result, tempDir, false, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.METHOD_RESOLVE);
    assertThat(resolutions.get(0).resolvedMethodSig()).isEqualTo("intValue");
  }

  @Test
  void resolve_withDebugMode_serviceLoader_producesCorrectResolution() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugServiceTest.java",
        "package com.example;",
        "import java.util.ServiceLoader;",
        "class DebugServiceTest {",
        "  void test() {",
        "    ServiceLoader.load(Runnable.class);",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DebugServiceTest", "com/example/DebugServiceTest.java");
    resolver.resolve(result, tempDir, false, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.SERVICELOADER_PROVIDERS);
  }

  @Test
  void resolve_withDebugMode_interprocedural_producesCorrectResolution() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugInterProcTest.java",
        "package com.example;",
        "class DebugInterProcTest {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void caller() {",
        "    load(\"com.example.Target\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult(
            "com.example.DebugInterProcTest", "com/example/DebugInterProcTest.java");

    resolver.resolve(result, tempDir, true, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.INTERPROCEDURAL_SINGLE);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Target");
    assertThat(resolutions.get(0).evidence().get("provenance")).isEqualTo("interprocedural_single");
  }

  @Test
  void resolve_interprocedural_chainedCall_resolvesFromCallerArgument() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ChainInterProcTest.java",
        "package com.example;",
        "class ChainInterProcTest {",
        "  void load(String cn) {",
        "    try {",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "  void forward(String name) {",
        "    load(name);",
        "  }",
        "  void caller() {",
        "    forward(\"com.example.Target\");",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult(
            "com.example.ChainInterProcTest", "com/example/ChainInterProcTest.java");

    resolver.resolve(result, tempDir, true, 20, true);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.INTERPROCEDURAL_SINGLE);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Target");
  }

  @Test
  void resolve_externalConfigValue_resolvesClassName() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ConfigRef.java",
        "package com.example;",
        "class ConfigRef {",
        "  java.util.Map<String, String> config = new java.util.HashMap<>();",
        "  void test() {",
        "    try {",
        "      String base = config.get(\"myClass\");",
        "      Class.forName(base + \".Impl\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ConfigRef", "com/example/ConfigRef.java");

    resolver.setExternalConfigValues(java.util.Map.of("myClass", "com.example.Target"));
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Target.Impl");
  }

  @Test
  void resolve_debugModeDoesNotChangeResolutionContent() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DebugContentTest.java",
        "package com.example;",
        "class DebugContentTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Foo\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result1 =
        createAnalysisResult("com.example.DebugContentTest", "com/example/DebugContentTest.java");
    AnalysisResult result2 =
        createAnalysisResult("com.example.DebugContentTest", "com/example/DebugContentTest.java");

    DynamicResolver resolverOff = new DynamicResolver();
    resolverOff.resolve(result1, tempDir, false, 20, false);

    createSourceFile(
        "src/main/java/com/example/DebugContentTest.java",
        "package com.example;",
        "class DebugContentTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Foo\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    DynamicResolver resolverOn = new DynamicResolver();
    resolverOn.resolve(result2, tempDir, false, 20, true);

    var resOff = resolverOff.getResolutions();
    var resOn = resolverOn.getResolutions();

    assertThat(resOff).hasSize(1);
    assertThat(resOn).hasSize(1);

    assertThat(resOn.get(0).subtype()).isEqualTo(resOff.get(0).subtype());
    assertThat(resOn.get(0).resolvedClassFqn()).isEqualTo(resOff.get(0).resolvedClassFqn());
  }

  // =============================================
  // RuleId Tracking Tests
  // =============================================

  @Test
  void resolve_classForName_literal_hasLiteralRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleLit.java",
        "package com.example;",
        "class RuleLit {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.RuleLit", "com/example/RuleLit.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.LITERAL);
  }

  @Test
  void resolve_classForName_variable_hasLocalConstRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleVar.java",
        "package com.example;",
        "class RuleVar {",
        "  void test() {",
        "    try {",
        "      String cn = \"com.example.Foo\";",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.RuleVar", "com/example/RuleVar.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.LOCAL_CONST);
  }

  @Test
  void resolve_classForName_concatenation_hasBinaryConcatRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleConcat.java",
        "package com.example;",
        "class RuleConcat {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.\" + \"example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.RuleConcat", "com/example/RuleConcat.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.BINARY_CONCAT);
  }

  @Test
  void resolve_classForName_stringFormat_hasStringFormatRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleFmt.java",
        "package com.example;",
        "class RuleFmt {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.format(\"com.%s.Impl\", \"example\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.RuleFmt", "com/example/RuleFmt.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_FORMAT);
  }

  @Test
  void resolve_classForName_stringBuilder_hasStringBuilderRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleSb.java",
        "package com.example;",
        "class RuleSb {",
        "  void test() {",
        "    try {",
        "      Class.forName(new StringBuilder().append(\"com.x.\").append(\"Foo\").toString());",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.RuleSb", "com/example/RuleSb.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_BUILDER);
  }

  @Test
  void resolve_classForName_stringJoin_hasStringJoinRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleJoin.java",
        "package com.example;",
        "class RuleJoin {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.join(\".\", \"com\", \"x\", \"Foo\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.RuleJoin", "com/example/RuleJoin.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_JOIN);
  }

  @Test
  void resolve_classForName_stringConcat_hasStringConcatMethodRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleConcatMethod.java",
        "package com.example;",
        "class RuleConcatMethod {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.x.\".concat(\"Foo\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.RuleConcatMethod", "com/example/RuleConcatMethod.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_CONCAT_METHOD);
  }

  @Test
  void resolve_classForName_branchCandidates_hasNullRuleId() throws Exception {
    createSourceFile(
        "src/main/java/com/example/RuleBranch.java",
        "package com.example;",
        "class RuleBranch {",
        "  void test(boolean cond) {",
        "    try {",
        "      String cn;",
        "      if (cond) {",
        "        cn = \"com.example.A\";",
        "      } else {",
        "        cn = \"com.example.B\";",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.RuleBranch", "com/example/RuleBranch.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolutions.get(0).ruleId()).isNull();
  }

  @Test
  void resolve_classForName_stringValueOf_literal() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfTest.java",
        "package com.example;",
        "class ValueOfTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.ID\" + String.valueOf(123));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfTest", "com/example/ValueOfTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.ID123");
    // The resolution happens via binary concat of "com.example.ID" + String.valueOf
    // result
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.BINARY_CONCAT);
  }

  @Test
  void resolve_classForName_stringValueOf_boolean() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfBoolTest.java",
        "package com.example;",
        "class ValueOfBoolTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Is\" + String.valueOf(true));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfBoolTest", "com/example/ValueOfBoolTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Istrue");
  }

  @Test
  void resolve_classForName_stringValueOf_stringLiteral() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfStringTest.java",
        "package com.example;",
        "class ValueOfStringTest {",
        "  void test() {",
        "    try {",
        "      Class.forName(String.valueOf(\"com.example.Direct\"));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfStringTest", "com/example/ValueOfStringTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Direct");
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_VALUEOF);
  }

  @Test
  void resolve_classForName_stringValueOf_object_notResolved() throws Exception {
    createSourceFile(
        "src/main/java/com/example/ValueOfObjTest.java",
        "package com.example;",
        "class ValueOfObjTest {",
        "  void test() {",
        "    try {",
        "      Object o = new Object();",
        "      Class.forName(\"com.example.\" + String.valueOf(o));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.ValueOfObjTest", "com/example/ValueOfObjTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).isEmpty();
  }

  @Test
  void resolve_classForName_nestedStringOperations() throws Exception {
    createSourceFile(
        "src/main/java/com/example/NestedTest.java",
        "package com.example;",
        "class NestedTest {",
        "  void test() {",
        "    try {",
        "      // \"com.\" + join(\".\", \"example\", \"Nested\")",
        "      Class.forName(\"com.\".concat(String.join(\".\", \"example\", \"Nested\")));",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.NestedTest", "com/example/NestedTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    assertThat(resolutions.get(0).resolvedClassFqn()).isEqualTo("com.example.Nested");
    assertThat(resolutions.get(0).ruleId())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId.STRING_CONCAT_METHOD);
  }

  @Test
  void resolve_classForName_branchCandidates_truncatedByLimit() throws Exception {
    createSourceFile(
        "src/main/java/com/example/TruncateTest.java",
        "package com.example;",
        "class TruncateTest {",
        "  void test(int choice) {",
        "    try {",
        "      String cn = null;",
        "      switch (choice) {",
        "        case 0: cn = \"com.example.C0\"; break;",
        "        case 1: cn = \"com.example.C1\"; break;",
        "        case 2: cn = \"com.example.C2\"; break;",
        "        case 3: cn = \"com.example.C3\"; break;",
        "        case 4: cn = \"com.example.C4\"; break;",
        "        case 5: cn = \"com.example.C5\"; break;",
        "        case 6: cn = \"com.example.C6\"; break;",
        "        case 7: cn = \"com.example.C7\"; break;",
        "        case 8: cn = \"com.example.C8\"; break;",
        "        case 9: cn = \"com.example.C9\"; break;",
        "        case 10: cn = \"com.example.C10\"; break;",
        "        case 11: cn = \"com.example.C11\"; break;",
        "        default: cn = \"com.example.Cx\";",
        "      }",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.TruncateTest", "com/example/TruncateTest.java");
    resolver.resolve(result, tempDir);

    List<DynamicResolution> resolutions = resolver.getResolutions();
    assertThat(resolutions).hasSize(1);
    DynamicResolution resolution = resolutions.get(0);
    assertThat(resolution.subtype()).isEqualTo(DynamicResolution.BRANCH_CANDIDATES);
    assertThat(resolution.reasonCode())
        .isEqualTo(
            com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode
                .CANDIDATE_LIMIT_EXCEEDED);
    assertThat(resolution.candidates()).isNotEmpty();
    assertThat(resolution.candidates().size()).isLessThanOrEqualTo(8);
  }

  @Test
  void resolve_classForName_recursionDepthExceeded_returnsUnresolved() throws Exception {
    createSourceFile(
        "src/main/java/com/example/DepthTest.java",
        "package com.example;",
        "class DepthTest {",
        "  void test() {",
        "    try {",
        "      String cn = \"com.example.\" + \"A\" + \"B\" + \"C\" + \"D\" + \"E\"",
        "          + \"F\" + \"G\" + \"H\" + \"I\" + \"J\" + \"K\";",
        "      Class.forName(cn);",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result =
        createAnalysisResult("com.example.DepthTest", "com/example/DepthTest.java");
    resolver.resolve(result, tempDir);

    assertThat(resolver.getResolutions()).isEmpty();
  }
}
