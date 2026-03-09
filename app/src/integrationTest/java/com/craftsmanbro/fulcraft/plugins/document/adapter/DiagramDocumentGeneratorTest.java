package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for DiagramDocumentGenerator. */
class DiagramDocumentGeneratorTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @TempDir Path tempDir;

  private Config config;

  @BeforeEach
  void setUp() {
    config = Config.createDefault();
    config.setDocs(new Config.DocsConfig());
  }

  @Test
  void generate_shouldCreateDiagramFiles() throws IOException {
    // Given
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    AnalysisResult result = createTestAnalysisResult();

    // When
    int count = generator.generate(result, tempDir, config);

    // Then
    assertThat(count).isGreaterThanOrEqualTo(2); // At least class deps + inheritance
    assertThat(Files.exists(tempDir.resolve("class_dependencies.md"))).isTrue();
    assertThat(Files.exists(tempDir.resolve("inheritance_hierarchy.md"))).isTrue();
  }

  @Test
  void generate_shouldSkipMethodCallGraphWhenClassHasNoMethods() throws IOException {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    AnalysisResult result = new AnalysisResult();
    ClassInfo emptyClass = new ClassInfo();
    emptyClass.setFqn("com.example.EmptyClass");
    emptyClass.setFilePath("src/main/java/com/example/EmptyClass.java");
    emptyClass.setMethods(List.of());
    result.setClasses(List.of(emptyClass));

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(2);
    assertThat(Files.exists(tempDir.resolve("com_example_EmptyClass_calls.md"))).isFalse();
  }

  @Test
  void generateClassDependencyDiagram_mermaid_shouldContainNodes() {
    // Given
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.MERMAID);
    AnalysisResult result = createTestAnalysisResult();

    // When
    String diagram = generator.generateClassDependencyDiagram(result);

    // Then
    assertThat(diagram).contains("# クラス依存関係図");
    assertThat(diagram).contains("```mermaid");
    assertThat(diagram).contains("graph TD");
    assertThat(diagram).contains("TestClass");
  }

  @Test
  void generateClassDependencyDiagram_shouldDeduplicateAndIgnoreSelfDependencyEdges() {
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.MERMAID);
    AnalysisResult result = new AnalysisResult();

    ClassInfo source = new ClassInfo();
    source.setFqn("com.example.Source");
    MethodInfo sourceMethod = new MethodInfo();
    sourceMethod.setName("run");
    sourceMethod.setSignature("void run()");
    sourceMethod.setCalledMethods(
        List.of(
            "com.example.Target#doWork()",
            "com.example.Target#doWork()",
            "com.example.Source#run()",
            "localOnlyCall"));
    source.setMethods(List.of(sourceMethod));

    ClassInfo target = new ClassInfo();
    target.setFqn("com.example.Target");
    target.setMethods(List.of());

    result.setClasses(List.of(source, target));

    String diagram = generator.generateClassDependencyDiagram(result);

    assertThat(countOccurrences(diagram, "C0-->C1")).isEqualTo(1);
    assertThat(diagram).doesNotContain("C0-->C0");
  }

  @Test
  void generateClassDependencyDiagram_plantuml_shouldContainNodes() {
    // Given
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.PLANTUML);
    AnalysisResult result = createTestAnalysisResult();

    // When
    String diagram = generator.generateClassDependencyDiagram(result);

    // Then
    assertThat(diagram).contains("# クラス依存関係図");
    assertThat(diagram).contains("```plantuml");
    assertThat(diagram).contains("@startuml");
    assertThat(diagram).contains("@enduml");
  }

  @Test
  void generateInheritanceDiagram_shouldShowInheritanceRelationships() {
    // Given
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    AnalysisResult result = createAnalysisResultWithInheritance();

    // When
    String diagram = generator.generateInheritanceDiagram(result);

    // Then
    assertThat(diagram).contains("# 継承階層図");
    assertThat(diagram).contains("classDiagram");
    assertThat(diagram).contains("<|--"); // Inheritance arrow
  }

  @Test
  void generateMethodCallGraph_shouldShowMethodRelationships() {
    // Given
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    ClassInfo classInfo = createClassWithMethodCalls();

    // When
    String diagram = generator.generateMethodCallGraph(classInfo);

    // Then
    assertThat(diagram).contains("メソッド呼び出しグラフ");
    assertThat(diagram).contains("```mermaid");
    assertThat(diagram).contains("graph LR");
    assertThat(diagram).contains("methodA");
    assertThat(diagram).contains("helperMethod");
  }

  @Test
  void generateMethodCallGraph_plantuml_shouldUsePlantUmlSyntax() {
    DiagramDocumentGenerator generator =
        new DiagramDocumentGenerator(DiagramDocumentGenerator.DiagramFormat.PLANTUML);
    ClassInfo classInfo = createClassWithMethodCalls();

    String diagram = generator.generateMethodCallGraph(classInfo);

    assertThat(diagram).contains("```plantuml");
    assertThat(diagram).contains("@startuml");
    assertThat(diagram).contains("rectangle \"methodA()\"");
    assertThat(diagram).contains("-->");
    assertThat(diagram).contains("@enduml");
  }

  @Test
  void fromConfig_shouldReturnMermaidByDefault() {
    // When
    DiagramDocumentGenerator generator = DiagramDocumentGenerator.fromConfig(config);

    // Then
    assertThat(generator.getFormat()).isEqualTo("diagram");
    // Generator uses Mermaid by default
  }

  @Test
  void fromConfig_shouldReturnMermaidWhenDocsConfigIsMissing() {
    config.setDocs(null);

    DiagramDocumentGenerator generator = DiagramDocumentGenerator.fromConfig(config);
    String diagram = generator.generateClassDependencyDiagram(createTestAnalysisResult());

    assertThat(diagram).contains("```mermaid");
  }

  @Test
  void fromConfig_shouldReturnPlantUmlWhenConfigured() {
    // Given
    config.getDocs().setDiagramFormat("plantuml");

    // When
    DiagramDocumentGenerator generator = DiagramDocumentGenerator.fromConfig(config);

    // Then
    String diagram = generator.generateClassDependencyDiagram(createTestAnalysisResult());
    assertThat(diagram).contains("@startuml");
  }

  @Test
  void getFormat_shouldReturnDiagram() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    assertThat(generator.getFormat()).isEqualTo("diagram");
  }

  @Test
  void getFileExtension_shouldReturnMd() {
    DiagramDocumentGenerator generator = new DiagramDocumentGenerator();
    assertThat(generator.getFileExtension()).isEqualTo(".md");
  }

  private AnalysisResult createTestAnalysisResult() {
    AnalysisResult result = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("TestClass.java");
    classInfo.setLoc(50);

    MethodInfo method = new MethodInfo();
    method.setName("testMethod");
    method.setSignature("public void testMethod()");
    method.setCalledMethods(List.of("com.example.Helper#doSomething"));
    classInfo.setMethods(List.of(method));

    result.setClasses(List.of(classInfo));
    return result;
  }

  private AnalysisResult createAnalysisResultWithInheritance() {
    AnalysisResult result = new AnalysisResult();

    ClassInfo parentClass = new ClassInfo();
    parentClass.setFqn("com.example.ParentClass");
    parentClass.setFilePath("ParentClass.java");
    parentClass.setLoc(30);

    ClassInfo childClass = new ClassInfo();
    childClass.setFqn("com.example.ChildClass");
    childClass.setFilePath("ChildClass.java");
    childClass.setLoc(40);
    childClass.setExtendsTypes(List.of("com.example.ParentClass"));
    childClass.setImplementsTypes(List.of("Runnable"));

    result.setClasses(List.of(parentClass, childClass));
    return result;
  }

  private ClassInfo createClassWithMethodCalls() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Service");
    classInfo.setFilePath("Service.java");
    classInfo.setLoc(100);

    MethodInfo methodA = new MethodInfo();
    methodA.setName("methodA");
    methodA.setSignature("public void methodA()");
    methodA.setCalledMethods(List.of("helperMethod", "utilMethod"));

    MethodInfo helperMethod = new MethodInfo();
    helperMethod.setName("helperMethod");
    helperMethod.setSignature("private void helperMethod()");
    helperMethod.setCalledMethods(List.of());

    MethodInfo utilMethod = new MethodInfo();
    utilMethod.setName("utilMethod");
    utilMethod.setSignature("private String utilMethod()");
    utilMethod.setCalledMethods(List.of("helperMethod"));

    classInfo.setMethods(List.of(methodA, helperMethod, utilMethod));
    return classInfo;
  }

  private int countOccurrences(String text, String token) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }
}
