package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
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

/** Tests for MarkdownDocumentGenerator. */
class MarkdownDocumentGeneratorTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @TempDir Path tempDir;

  private MarkdownDocumentGenerator generator;
  private Config config;

  @BeforeEach
  void setUp() {
    generator = new MarkdownDocumentGenerator();
    config = Config.createDefault();
  }

  @Test
  void generate_shouldCreateMarkdownFiles() throws IOException {
    // Given
    AnalysisResult result = createTestAnalysisResult();

    // When
    int count = generator.generate(result, tempDir, config);

    // Then
    assertThat(count).isEqualTo(1);
    Path outputFile = tempDir.resolve("src/main/java/com/example/TestClass.md");
    assertThat(Files.exists(outputFile)).isTrue();

    String content = Files.readString(outputFile);
    assertThat(content).contains("# TestClass");
    assertThat(content).contains("## クラス概要");
    assertThat(content).contains("## メソッド一覧");
  }

  @Test
  void generate_shouldRemoveLegacyFlatMarkdownFile() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    Path legacyFlatPath = tempDir.resolve("com_example_TestClass.md");
    Files.createFile(legacyFlatPath);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("src/main/java/com/example/TestClass.md"))).isTrue();
    assertThat(Files.exists(legacyFlatPath)).isFalse();
  }

  @Test
  void generate_shouldKeepLegacyFlatPathWhenSourcePathIsMissing() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    result.getClasses().get(0).setFilePath(null);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("com_example_TestClass.md"))).isTrue();
  }

  @Test
  void generateClassDocument_shouldIncludeClassSummary() {
    // Given
    ClassInfo classInfo = createTestClassInfo();

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("# TestClass");
    assertThat(markdown).contains("## クラス概要");
    assertThat(markdown).contains("com.example");
    assertThat(markdown).contains("TestClass.java");
  }

  @Test
  void generateClassDocument_shouldFallbackFilePathWhenMissing() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setFilePath(null);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| ファイル | `該当なし` |");
    assertThat(markdown).doesNotContain("| ファイル | `null` |");
  }

  @Test
  void generateClassDocument_shouldIncludeFieldsSection() {
    // Given
    ClassInfo classInfo = createTestClassInfo();

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("## フィールド一覧");
    assertThat(markdown).contains("testField");
    assertThat(markdown).contains("String");
  }

  @Test
  void generateClassDocument_shouldIncludeMethodsSection() {
    // Given
    ClassInfo classInfo = createTestClassInfo();

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("## メソッド一覧");
    assertThat(markdown).contains("**見どころ**:");
    assertThat(markdown).contains("testMethod");
    assertThat(markdown).contains("public void testMethod()");
  }

  @Test
  void generateClassDocument_shouldIncludeSourceCodeSectionWhenAvailable() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setSourceCode(
        """
        public void testMethod() {
          int total = 1 + 2;
          System.out.println(total);
        }
        """);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("**ソースコード**:");
    assertThat(markdown).contains("```java");
    assertThat(markdown).contains("int total = 1 + 2;");
  }

  @Test
  void generateClassDocument_shouldIncludeMethodContractSection() {
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setFqn("com.example.OrderService");
    MethodInfo method = classInfo.getMethods().get(0);
    method.setName("processOrder");
    method.setSignature("public boolean processOrder(String orderId)");
    method.setParameterCount(1);
    method.setCalledMethods(List.of("com.example.OrderRepository#save(com.example.Order)"));

    BranchSummary summary = new BranchSummary();
    GuardSummary guard = new GuardSummary();
    guard.setType(GuardType.FAIL_GUARD);
    guard.setCondition("orderId == null || orderId.isBlank()");
    summary.setGuards(List.of(guard));
    method.setBranchSummary(summary);

    RepresentativePath success = new RepresentativePath();
    success.setId("path-1");
    success.setDescription("Main success path");
    success.setExpectedOutcomeHint("success");
    RepresentativePath earlyReturn = new RepresentativePath();
    earlyReturn.setId("path-2");
    earlyReturn.setDescription("Early return path");
    earlyReturn.setRequiredConditions(List.of("orderId == null || orderId.isBlank()"));
    earlyReturn.setExpectedOutcomeHint("early-return");
    method.setRepresentativePaths(List.of(success, earlyReturn));
    method.setThrownExceptions(List.of("java.lang.IllegalStateException"));

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown).contains("**メソッド契約**:");
    assertThat(markdown).contains("| 入力 | String orderId |");
    assertThat(markdown).contains("| 出力 | boolean |");
    assertThat(markdown).contains("| 事前条件 | orderId == null \\|\\| orderId.isBlank() |");
    assertThat(markdown).contains("OrderRepository#save");
    assertThat(markdown).contains("IllegalStateException");
  }

  @Test
  void generateClassDocument_shouldDeriveContractOutputFromSourceWhenSignatureIsShort() {
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setFqn("com.example.OrderService");
    MethodInfo method = classInfo.getMethods().get(0);
    method.setName("processOrder");
    method.setSignature("processOrder(String)");
    method.setParameterCount(1);
    method.setSourceCode(
        """
        public boolean processOrder(String orderId) {
          return true;
        }
        """);

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown).contains("| 入力 | String orderId |");
    assertThat(markdown).contains("| 出力 | boolean |");
  }

  @Test
  void generateClassDocument_shouldFallbackContractInputsWhenSignatureIsUnparseable() {
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setSignature("processOrder");
    method.setParameterCount(2);
    method.setSourceCode(null);

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown).contains("| 入力 | arg0 / arg1 |");
  }

  @Test
  void generateClassDocument_shouldPrioritizeBusinessDependenciesInContract() {
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setCalledMethods(
        List.of(
            "java.util.Optional#get()",
            "com.example.OrderRepository#save(com.example.Order)",
            "java.lang.String#trim()",
            "com.example.CustomException#CustomException(java.lang.String)",
            "com.example.PaymentGateway#charge(com.example.Order)"));

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown)
        .contains("| 依存呼び出し | OrderRepository#save / PaymentGateway#charge / Optional#get |");
    assertThat(markdown).doesNotContain("依存呼び出し | CustomException#CustomException");
  }

  @Test
  void generateClassDocument_shouldIncludeTestViewpointsSection() {
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setCyclomaticComplexity(18);
    method.setDeadCode(true);
    method.setDuplicate(true);

    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    path.setDescription("Boundary path");
    path.setRequiredConditions(List.of("amount == null"));
    path.setExpectedOutcomeHint("boundary");
    method.setRepresentativePaths(List.of(path));
    method.setThrownExceptions(List.of("java.lang.IllegalArgumentException"));

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown).contains("**テスト観点**:");
    assertThat(markdown).contains("パス: Boundary path / 条件: amount == null / 期待: boundary");
    assertThat(markdown).contains("複雑度が高いため、分岐組み合わせを重点的に検証する。");
    assertThat(markdown).contains("到達性を確認し、意図的未使用なら明示する。");
    assertThat(markdown).contains("例外シナリオとメッセージを検証する。");
  }

  @Test
  void generateClassDocument_shouldUseTopLevelGuardsForContractPreconditions() {
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setSignature("public Customer findCustomerById(String id)");
    method.setSourceCode(
        """
        public Customer findCustomerById(String id) {
          if (id == null || id.isBlank()) {
            return null;
          }
          for (Customer customer : customers) {
            if (customer.getId().equals(id)) {
              return customer;
            }
          }
          return null;
        }
        """);

    GuardSummary topLevel = new GuardSummary();
    topLevel.setType(GuardType.FAIL_GUARD);
    topLevel.setCondition("id == null || id.isBlank()");
    topLevel.setLocation("2:5");
    GuardSummary nested = new GuardSummary();
    nested.setType(GuardType.FAIL_GUARD);
    nested.setCondition("customer.getId().equals(id)");
    nested.setLocation("6:9");
    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(topLevel, nested));
    method.setBranchSummary(summary);

    String markdown = generator.generateClassDocument(classInfo);

    assertThat(markdown).contains("| 事前条件 | id == null \\|\\| id.isBlank() |");
  }

  @Test
  void generateClassDocument_shouldStripCommentedRegionsFromSourceCode() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setSourceCode(
        """
        /**
         * implementation note
         */
        public void testMethod() {
          // should be excluded
          int total = 1 + 2;
          /*
           * old implementation
           * return;
           */
          System.out.println(total);
        }
        """);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("int total = 1 + 2;");
    assertThat(markdown).contains("System.out.println(total);");
    assertThat(markdown).doesNotContain("implementation note");
    assertThat(markdown).doesNotContain("should be excluded");
    assertThat(markdown).doesNotContain("old implementation");
  }

  @Test
  void generateClassDocument_shouldExcludeImplicitDefaultConstructor() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo implicitCtor = new MethodInfo();
    implicitCtor.setName("TestClass");
    implicitCtor.setSignature("public TestClass()");
    implicitCtor.setVisibility("public");
    implicitCtor.setLoc(0);
    implicitCtor.setCyclomaticComplexity(1);
    implicitCtor.setMaxNestingDepth(0);
    implicitCtor.setParameterCount(0);
    implicitCtor.setSourceCode("""
        TestClass() {
        }
        """);
    classInfo.setMethods(List.of(classInfo.getMethods().get(0), implicitCtor));
    classInfo.setMethodCount(2);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| メソッド数 | 1 |");
    assertThat(markdown).doesNotContain("[`TestClass()`]");
    assertThat(markdown).doesNotContain("### TestClass()");
  }

  @Test
  void generateClassDocument_shouldKeepExplicitDefaultConstructor() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo explicitCtor = new MethodInfo();
    explicitCtor.setName("TestClass");
    explicitCtor.setSignature("public TestClass()");
    explicitCtor.setVisibility("public");
    explicitCtor.setLoc(3);
    explicitCtor.setCyclomaticComplexity(1);
    explicitCtor.setMaxNestingDepth(0);
    explicitCtor.setParameterCount(0);
    explicitCtor.setSourceCode("""
        public TestClass() {
        }
        """);
    classInfo.setMethods(List.of(classInfo.getMethods().get(0), explicitCtor));
    classInfo.setMethodCount(2);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| メソッド数 | 2 |");
    assertThat(markdown).contains("[`TestClass()`]");
    assertThat(markdown).contains("### TestClass()");
  }

  @Test
  void generateClassDocument_shouldDeduplicateMethodsWithQualifiedSignatureVariants() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo simple = new MethodInfo();
    simple.setName("load");
    simple.setSignature("public void load(String id)");
    simple.setVisibility("public");
    simple.setLoc(5);
    simple.setCyclomaticComplexity(1);
    simple.setMaxNestingDepth(0);
    simple.setParameterCount(1);
    simple.setSourceCode("public void load(String id) {}");

    MethodInfo qualified = new MethodInfo();
    qualified.setName("load");
    qualified.setSignature("public void load(java.lang.String id)");
    qualified.setVisibility("public");
    qualified.setLoc(5);
    qualified.setCyclomaticComplexity(1);
    qualified.setMaxNestingDepth(0);
    qualified.setParameterCount(1);
    qualified.setSourceCode("public void load(java.lang.String id) {}");

    classInfo.setMethods(List.of(qualified, simple));
    classInfo.setMethodCount(2);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| メソッド数 | 1 |");
    assertThat(markdown).contains("[`load(String id)`]");
    assertThat(markdown).doesNotContain("load(java.lang.String id)");
  }

  @Test
  void generateClassDocument_shouldEscapePipesInRepresentativePathTable() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    path.setDescription("Early return");
    path.setRequiredConditions(List.of("orderId == null || orderId.isBlank()"));
    path.setExpectedOutcomeHint("error | warning");
    method.setRepresentativePaths(List.of(path));

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("orderId == null \\|\\| orderId.isBlank()");
    assertThat(markdown).contains("error \\| warning");
  }

  @Test
  void generateClassDocument_shouldRenderBranchesAsTable() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    BranchSummary summary = new BranchSummary();
    GuardSummary guard = new GuardSummary();
    guard.setType(GuardType.FAIL_GUARD);
    guard.setCondition("orderId == null || orderId.isBlank()");
    guard.setMessageLiteral("Order ID is required");
    summary.setGuards(List.of(guard));
    summary.setSwitches(List.of("paymentMethod"));
    summary.setPredicates(List.of("amount == null"));
    method.setBranchSummary(summary);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("**分岐/ガード**:");
    assertThat(markdown).contains("**見どころ**: ガード条件と注記を見て");
    assertThat(markdown).contains("| 型 | 条件 | 注記 |");
    assertThat(markdown).contains("| fail-guard | orderId == null \\|\\| orderId.isBlank()");
    assertThat(markdown).contains("| switch | paymentMethod |");
    assertThat(markdown).contains("| predicate | amount == null |");
  }

  @Test
  void generateClassDocument_shouldRenderRepresentativePathsWithTypeColumn() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    path.setDescription("Early return path");
    path.setRequiredConditions(List.of("orderId == null || orderId.isBlank()"));
    path.setExpectedOutcomeHint("early-return");
    method.setRepresentativePaths(List.of(path));

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| ID | 型 | 条件 | 説明 | 注記 |");
    assertThat(markdown)
        .contains("| path-1 | early-return | orderId == null \\|\\| orderId.isBlank()");
  }

  @Test
  void generateClassDocument_shouldRenderCalledMethodsAsReadableTable() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo method = classInfo.getMethods().get(0);
    method.setCalledMethods(
        List.of(
            "com.example.legacy.PaymentService.PaymentRepository#save(com.example.legacy.PaymentService.Payment)",
            "java.util.Optional#get()"));

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("**呼び出しメソッド**:");
    assertThat(markdown).contains("**見どころ**: 呼び出し先とシグネチャから");
    assertThat(markdown).contains("| # | 呼び出し先 | メソッド | シグネチャ |");
    assertThat(markdown).contains("| 1 | PaymentRepository | save | save(Payment) |");
    assertThat(markdown).contains("| 2 | Optional | get | get() |");
  }

  @Test
  void generateClassDocument_shouldUseSignatureBasedAnchors() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    MethodInfo overload = new MethodInfo();
    overload.setName("testMethod");
    overload.setSignature("public void testMethod(int value)");
    overload.setVisibility("public");
    overload.setLoc(12);
    overload.setCyclomaticComplexity(1);
    overload.setMaxNestingDepth(1);
    overload.setParameterCount(1);
    classInfo.setMethods(List.of(classInfo.getMethods().get(0), overload));

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("(#testmethod)");
    assertThat(markdown).contains("(#testmethod-int-value)");
  }

  @Test
  void generateClassDocument_shouldShowInterfaceBadge() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setInterface(true);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("**種別**: インターフェース");
  }

  @Test
  void generateClassDocument_shouldShowAbstractBadge() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setAbstract(true);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("**種別**: 抽象クラス");
  }

  @Test
  void generateClassDocument_shouldShowInheritance() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setExtendsTypes(List.of("BaseClass"));
    classInfo.setImplementsTypes(List.of("Runnable", "Serializable"));

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("| 継承 | `BaseClass` |");
    assertThat(markdown).contains("| 実装 | `Runnable, Serializable` |");
  }

  @Test
  void generateClassDocument_shouldShowDeadCodeWarning() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.setDeadCode(true);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("⚠️ **警告**: デッドコードの可能性があります");
  }

  @Test
  void generateClassDocument_shouldUseCandidateLabelForDeadCodeFlag() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    classInfo.getMethods().get(0).setDeadCode(true);

    // When
    String markdown = generator.generateClassDocument(classInfo);

    // Then
    assertThat(markdown).contains("デッドコード候補");
  }

  @Test
  void getFormat_shouldReturnMarkdown() {
    assertThat(generator.getFormat()).isEqualTo("markdown");
  }

  @Test
  void getFileExtension_shouldReturnMd() {
    assertThat(generator.getFileExtension()).isEqualTo(".md");
  }

  private AnalysisResult createTestAnalysisResult() {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(createTestClassInfo()));
    return result;
  }

  private ClassInfo createTestClassInfo() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("src/main/java/com/example/TestClass.java");
    classInfo.setLoc(50);
    classInfo.setMethodCount(2);

    // Add a field
    FieldInfo field = new FieldInfo();
    field.setName("testField");
    field.setType("String");
    field.setVisibility("private");
    classInfo.setFields(List.of(field));

    // Add a method
    MethodInfo method = new MethodInfo();
    method.setName("testMethod");
    method.setSignature("public void testMethod()");
    method.setVisibility("public");
    method.setLoc(10);
    method.setCyclomaticComplexity(3);
    method.setMaxNestingDepth(1);
    method.setParameterCount(0);
    classInfo.setMethods(List.of(method));

    return classInfo;
  }
}
