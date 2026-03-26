package com.craftsmanbro.fulcraft.plugins.document.core.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for TestLinkResolver. */
class TestLinkResolverTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  private TestLinkResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new TestLinkResolver();
  }

  @Test
  void registerAndRetrieveClassTest() {
    // Given
    String classFqn = "com.example.Foo";
    TestLinkResolver.TestLink testLink =
        new TestLinkResolver.TestLink("FooTest", null, "src/test/java/com/example/FooTest.java");

    // When
    resolver.registerClassTest(classFqn, testLink);
    List<TestLinkResolver.TestLink> links = resolver.getTestsForClass(classFqn);

    // Then
    assertThat(links).hasSize(1);
    assertThat(links.get(0).testClassName()).isEqualTo("FooTest");
  }

  @Test
  void registerAndRetrieveMethodTest() {
    // Given
    String classFqn = "com.example.Foo";
    String methodName = "bar";
    TestLinkResolver.TestLink testLink =
        new TestLinkResolver.TestLink(
            "FooTest", "testBar", "src/test/java/com/example/FooTest.java");

    // When
    resolver.registerMethodTest(classFqn, methodName, testLink);
    List<TestLinkResolver.TestLink> links = resolver.getTestsForMethod(classFqn, methodName);

    // Then
    assertThat(links).hasSize(1);
    assertThat(links.get(0).testMethodName()).isEqualTo("testBar");
  }

  @Test
  void getTestsForClass_shouldReturnEmptyListWhenNotFound() {
    List<TestLinkResolver.TestLink> links = resolver.getTestsForClass("unknown.Class");
    assertThat(links).isEmpty();
  }

  @Test
  void getTestsForMethod_shouldReturnEmptyListWhenNotFound() {
    List<TestLinkResolver.TestLink> links =
        resolver.getTestsForMethod("unknown.Class", "unknownMethod");
    assertThat(links).isEmpty();
  }

  @Test
  void inferTestLinks_forClass_shouldFollowNamingConvention() {
    // Given
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Calculator");
    classInfo.setFilePath("Calculator.java");

    // When
    List<TestLinkResolver.TestLink> links = resolver.inferTestLinks(classInfo);

    // Then - implementation generates both CalculatorTest and CalculatorTests
    // conventions
    assertThat(links).hasSize(2);
    assertThat(links.get(0).testClassName()).isEqualTo("CalculatorTest");
    assertThat(links.get(0).relativePath()).contains("com/example/CalculatorTest.java");
    assertThat(links.get(1).testClassName()).isEqualTo("CalculatorTests");
  }

  @Test
  void inferTestLinks_forMethod_shouldFollowNamingConvention() {
    // Given
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Calculator");
    classInfo.setFilePath("Calculator.java");

    MethodInfo method = new MethodInfo();
    method.setName("add");
    method.setSignature("public int add(int a, int b)");

    // When
    List<TestLinkResolver.TestLink> links = resolver.inferTestLinks(classInfo, method);

    // Then
    assertThat(links).hasSize(2); // CalculatorTest#testAdd and CalculatorAddTest
    assertThat(links.get(0).testClassName()).isEqualTo("CalculatorTest");
    assertThat(links.get(0).testMethodName()).isEqualTo("testAdd");
    assertThat(links.get(1).testClassName()).isEqualTo("CalculatorAddTest");
  }

  @Test
  void testLink_toMarkdownLink_withMethod() {
    TestLinkResolver.TestLink link =
        new TestLinkResolver.TestLink("FooTest", "testBar", "path/to/FooTest.java");

    String mdLink = link.toMarkdownLink();

    assertThat(mdLink).isEqualTo("[FooTest#testBar](path/to/FooTest.java)");
  }

  @Test
  void testLink_toMarkdownLink_withoutMethod() {
    TestLinkResolver.TestLink link =
        new TestLinkResolver.TestLink("FooTest", null, "path/to/FooTest.java");

    String mdLink = link.toMarkdownLink();

    assertThat(mdLink).isEqualTo("[FooTest](path/to/FooTest.java)");
  }

  @Test
  void testLink_toHtmlLink() {
    TestLinkResolver.TestLink link =
        new TestLinkResolver.TestLink("FooTest", "testBar", "path/to/FooTest.java");

    String htmlLink = link.toHtmlLink();

    assertThat(htmlLink).isEqualTo("<a href=\"path/to/FooTest.java\">FooTest#testBar</a>");
  }

  @Test
  void generateTestLinksSection_shouldFormatAsMarkdownTable() {
    // Given
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Calculator");
    classInfo.setFilePath("Calculator.java");

    // When
    String section = resolver.generateTestLinksSection(classInfo, true);

    // Then
    assertThat(section).contains("## 関連テスト");
    assertThat(section).contains("| テストクラス | リンク |");
    assertThat(section).contains("CalculatorTest");
  }

  @Test
  void generateTestLinksSection_shouldReturnEmptyWhenInferenceDisabledAndNoRegisteredLinks() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Empty");
    classInfo.setFilePath("Empty.java");

    String section = resolver.generateTestLinksSection(classInfo, false);

    assertThat(section).isEmpty();
  }

  @Test
  void generateTestLinksSection_shouldUseRegisteredLinksWithoutInference() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("Service.java");
    resolver.registerClassTest(
        "com.example.Service",
        new TestLinkResolver.TestLink(
            "CustomServiceTest", null, "src/test/java/com/example/CustomServiceTest.java"));

    String section = resolver.generateTestLinksSection(classInfo, true);

    assertThat(section).contains("CustomServiceTest");
    assertThat(section).doesNotContain("ServiceTests");
  }

  @Test
  void generateMethodTestLinks_shouldIncludeInferredLinks() {
    // Given
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("Service.java");

    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("public void process()");

    // When
    String links = resolver.generateMethodTestLinks(classInfo, method, true);

    // Then
    assertThat(links).contains("**関連テスト**:");
    assertThat(links).contains("ServiceTest#testProcess");
  }

  @Test
  void generateMethodTestLinks_shouldReturnEmptyWhenInferenceDisabledAndNoRegisteredLinks() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("Service.java");
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("public void process()");

    String links = resolver.generateMethodTestLinks(classInfo, method, false);

    assertThat(links).isEmpty();
  }

  @Test
  void generateMethodTestLinks_shouldPreferRegisteredLinksOverInferredLinks() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("Service.java");
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("public void process()");
    resolver.registerMethodTest(
        "com.example.Service",
        "process",
        new TestLinkResolver.TestLink(
            "ServiceProcessContractTest",
            "verifiesProcessContract",
            "src/test/java/com/example/ServiceProcessContractTest.java"));

    String links = resolver.generateMethodTestLinks(classInfo, method, true);

    assertThat(links).contains("ServiceProcessContractTest#verifiesProcessContract");
    assertThat(links).doesNotContain("ServiceTest#testProcess");
  }

  @Test
  void resolverWithTestOutputRoot_shouldUseCustomPath() {
    // Given
    Path customPath = Path.of("/custom/test/path");
    TestLinkResolver customResolver = new TestLinkResolver(customPath);
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Foo");
    classInfo.setFilePath("Foo.java");

    // When
    List<TestLinkResolver.TestLink> links = customResolver.inferTestLinks(classInfo);

    // Then
    assertThat(links.get(0).relativePath()).startsWith("/custom/test/path");
  }

  @Test
  void registerTaskLink_shouldUseGeneratedTestPathAndRegisterClassAndMethod() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");
    task.setTestClassName("FooTest");

    GenerationTaskResult result = new GenerationTaskResult();
    result.setGeneratedTestFile(Path.of("build/tests/FooTest.java"));

    resolver.registerTaskLink(task, result);

    List<TestLinkResolver.TestLink> classLinks = resolver.getTestsForClass("com.example.Foo");
    assertThat(classLinks).hasSize(1);
    assertThat(classLinks.get(0).relativePath()).isEqualTo("build/tests/FooTest.java");

    List<TestLinkResolver.TestLink> methodLinks =
        resolver.getTestsForMethod("com.example.Foo", "bar");
    assertThat(methodLinks).hasSize(1);
    assertThat(methodLinks.get(0).testMethodName()).isNull();
  }

  @Test
  void registerTaskLink_shouldNormalizeGeneratedTestPath() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");
    task.setTestClassName("FooTest");

    GenerationTaskResult result = new GenerationTaskResult();
    result.setGeneratedTestFile(Path.of("build/tests/../tests/FooTest.java"));

    resolver.registerTaskLink(task, result);

    List<TestLinkResolver.TestLink> classLinks = resolver.getTestsForClass("com.example.Foo");
    assertThat(classLinks).hasSize(1);
    assertThat(classLinks.get(0).relativePath()).isEqualTo("build/tests/FooTest.java");
  }

  @Test
  void registerTaskLink_shouldNotRegisterMethodLinkWhenMethodNameIsBlank() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setMethodName(" ");
    task.setTestClassName("FooTest");

    resolver.registerTaskLink(task, null);

    assertThat(resolver.getTestsForClass("com.example.Foo")).hasSize(1);
    assertThat(resolver.getTestsForMethod("com.example.Foo", " ")).isEmpty();
  }

  @Test
  void registerTaskLink_shouldRelativizeGeneratedPathWhenLinkBaseProvided() {
    TestLinkResolver customResolver = new TestLinkResolver(null, Path.of("/workspace"));
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setTestClassName("FooTest");

    GenerationTaskResult result = new GenerationTaskResult();
    result.setGeneratedTestFile(Path.of("/workspace/src/test/java/com/example/FooTest.java"));

    customResolver.registerTaskLink(task, result);

    List<TestLinkResolver.TestLink> classLinks = customResolver.getTestsForClass("com.example.Foo");
    assertThat(classLinks).hasSize(1);
    assertThat(classLinks.get(0).relativePath())
        .isEqualTo("src/test/java/com/example/FooTest.java");
  }

  @Test
  void registerTaskLink_shouldKeepRelativeGeneratedPathWhenLinkBaseProvided() {
    TestLinkResolver customResolver = new TestLinkResolver(null, Path.of("/workspace"));
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setTestClassName("FooTest");

    GenerationTaskResult result = new GenerationTaskResult();
    result.setGeneratedTestFile(Path.of("build/generated/FooTest.java"));

    customResolver.registerTaskLink(task, result);

    List<TestLinkResolver.TestLink> classLinks = customResolver.getTestsForClass("com.example.Foo");
    assertThat(classLinks).hasSize(1);
    assertThat(classLinks.get(0).relativePath()).isEqualTo("build/generated/FooTest.java");
  }

  @Test
  void registerTaskLink_shouldFallbackToInferredPathWhenResultMissing() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");
    task.setTestClassName("FooTest");

    resolver.registerTaskLink(task, null);

    List<TestLinkResolver.TestLink> classLinks = resolver.getTestsForClass("com.example.Foo");
    assertThat(classLinks).hasSize(1);
    assertThat(classLinks.get(0).relativePath())
        .isEqualTo("src/test/java/com/example/FooTest.java");
  }

  @Test
  void registerTaskLink_shouldIgnoreWhenClassFqnMissing() {
    TaskRecord task = new TaskRecord();
    task.setTestClassName("FooTest");

    resolver.registerTaskLink(task, null);

    assertThat(resolver.getTestsForClass(null)).isEmpty();
  }

  @Test
  void registerTaskLink_shouldIgnoreWhenTestClassNameMissing() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");

    resolver.registerTaskLink(task, null);

    assertThat(resolver.getTestsForClass("com.example.Foo")).isEmpty();
  }

  @Test
  void registerTaskLink_shouldThrowWhenTaskIsNull() {
    assertThatThrownBy(() -> resolver.registerTaskLink(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("task");
  }

  @Test
  void inferTestLinks_shouldHandleDefaultPackage() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("Foo");
    classInfo.setFilePath("Foo.java");

    List<TestLinkResolver.TestLink> links = resolver.inferTestLinks(classInfo);

    assertThat(links).hasSize(2);
    assertThat(links.get(0).relativePath()).isEqualTo("src/test/java/FooTest.java");
  }

  @Test
  void inferTestLinks_shouldNormalizeDefaultPackageMarker() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("(default).Foo");
    classInfo.setFilePath("Foo.java");

    List<TestLinkResolver.TestLink> links = resolver.inferTestLinks(classInfo);

    assertThat(links).hasSize(2);
    assertThat(links.get(0).relativePath()).isEqualTo("src/test/java/FooTest.java");
  }
}
