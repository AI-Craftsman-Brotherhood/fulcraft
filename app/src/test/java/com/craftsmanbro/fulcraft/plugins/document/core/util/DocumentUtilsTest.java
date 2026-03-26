package com.craftsmanbro.fulcraft.plugins.document.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for DocumentUtils. */
class DocumentUtilsTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @Test
  void getSimpleName_shouldExtractClassNameFromFqn() {
    assertThat(DocumentUtils.getSimpleName("com.example.TestClass")).isEqualTo("TestClass");
    assertThat(DocumentUtils.getSimpleName("SimpleClass")).isEqualTo("SimpleClass");
    assertThat(DocumentUtils.getSimpleName(null)).isEqualTo("不明");
  }

  @Test
  void getPackageName_shouldExtractPackageFromFqn() {
    assertThat(DocumentUtils.getPackageName("com.example.TestClass")).isEqualTo("com.example");
    assertThat(DocumentUtils.getPackageName("SimpleClass")).isEqualTo("(default)");
    assertThat(DocumentUtils.getPackageName((String) null)).isEqualTo("");
  }

  @Test
  void getPackageName_shouldHandleNestedClassFqn() {
    assertThat(DocumentUtils.getPackageName("com.example.legacy.PaymentService.PaymentGateway"))
        .isEqualTo("com.example.legacy");
  }

  @Test
  void getPackageName_shouldHandleDollarSeparatedNestedClassFqn() {
    assertThat(DocumentUtils.getPackageName("com.example.Outer$Inner")).isEqualTo("com.example");
  }

  @Test
  void getPackageName_classInfo_shouldPreferSourcePathForNestedClass() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.PaymentGateway");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    assertThat(DocumentUtils.getPackageName(classInfo)).isEqualTo("com.example.legacy");
  }

  @Test
  void getPackageName_classInfo_shouldDeriveFromKotlinSourcePath() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("ignored.Fallback");
    classInfo.setFilePath("/tmp/project/src/test/kotlin/com/example/document/SampleSpec.kt");

    assertThat(DocumentUtils.getPackageName(classInfo)).isEqualTo("com.example.document");
  }

  @Test
  void getPackageName_classInfo_shouldFallbackToFqnWhenFilePathIsUnknown() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.PaymentGateway");
    classInfo.setFilePath(MethodInfo.UNKNOWN);

    assertThat(DocumentUtils.getPackageName(classInfo)).isEqualTo("com.example.legacy");
  }

  @Test
  void formatPackageNameForDisplay_shouldReturnDefaultLabel() {
    assertThat(DocumentUtils.formatPackageNameForDisplay(null)).isEqualTo("デフォルトパッケージ");
    assertThat(DocumentUtils.formatPackageNameForDisplay("")).isEqualTo("デフォルトパッケージ");
    assertThat(DocumentUtils.formatPackageNameForDisplay("   ")).isEqualTo("デフォルトパッケージ");
    assertThat(DocumentUtils.formatPackageNameForDisplay("(default)")).isEqualTo("デフォルトパッケージ");
  }

  @Test
  void formatPackageNameForDisplay_shouldReturnPackageNameWhenProvided() {
    assertThat(DocumentUtils.formatPackageNameForDisplay("com.example")).isEqualTo("com.example");
  }

  @Test
  void translateVisibility_shouldReturnJapaneseLabels() {
    assertThat(DocumentUtils.translateVisibility("public")).isEqualTo("公開");
    assertThat(DocumentUtils.translateVisibility("private")).isEqualTo("非公開");
    assertThat(DocumentUtils.translateVisibility("protected")).isEqualTo("保護");
    assertThat(DocumentUtils.translateVisibility("package-private")).isEqualTo("パッケージ");
    assertThat(DocumentUtils.translateVisibility("")).isEqualTo("パッケージ");
    assertThat(DocumentUtils.translateVisibility(null)).isEqualTo("-");
  }

  @Test
  void getComplexityLabel_shouldReturnCorrectLabels() {
    assertThat(DocumentUtils.getComplexityLabel(1)).isEqualTo("低");
    assertThat(DocumentUtils.getComplexityLabel(5)).isEqualTo("低");
    assertThat(DocumentUtils.getComplexityLabel(6)).isEqualTo("中");
    assertThat(DocumentUtils.getComplexityLabel(10)).isEqualTo("中");
    assertThat(DocumentUtils.getComplexityLabel(11)).isEqualTo("高");
    assertThat(DocumentUtils.getComplexityLabel(20)).isEqualTo("高");
    assertThat(DocumentUtils.getComplexityLabel(21)).isEqualTo("非常に高");
  }

  @Test
  void formatComplexity_shouldIncludeLabelInParentheses() {
    assertThat(DocumentUtils.formatComplexity(5)).isEqualTo("5 (低)");
    assertThat(DocumentUtils.formatComplexity(15)).isEqualTo("15 (高)");
  }

  @Test
  void buildClassType_shouldReturnCorrectType() {
    ClassInfo classInfo = new ClassInfo();

    classInfo.setInterface(true);
    assertThat(DocumentUtils.buildClassType(classInfo)).isEqualTo("インターフェース");

    classInfo.setInterface(false);
    classInfo.setAbstract(true);
    assertThat(DocumentUtils.buildClassType(classInfo)).isEqualTo("抽象クラス");

    classInfo.setAbstract(false);
    assertThat(DocumentUtils.buildClassType(classInfo)).isEqualTo("通常クラス");
  }

  @Test
  void generateClassAnchor_shouldCreateValidAnchor() {
    assertThat(DocumentUtils.generateClassAnchor("com.example.TestClass"))
        .isEqualTo("com-example-testclass");
    assertThat(DocumentUtils.generateClassAnchor(null)).isEqualTo("unknown");
  }

  @Test
  void generateFileName_shouldCreateValidFileName() {
    assertThat(DocumentUtils.generateFileName("com.example.TestClass", ".md"))
        .isEqualTo("com_example_TestClass.md");
    assertThat(DocumentUtils.generateFileName(null, ".html")).isEqualTo("unknown.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldUseSourceFolderStructure() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("src/main/java/com/example/TestClass.java");

    assertThat(DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html"))
        .isEqualTo("src/main/java/com/example/TestClass.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldAppendNestedClassSuffix() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService.Result");
    classInfo.setFilePath("src/main/java/com/example/OrderService.java");

    assertThat(DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html"))
        .isEqualTo("src/main/java/com/example/OrderService_Result.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldFallbackWhenFilePathMissing() {
    assertThat(
            DocumentUtils.generateSourceAlignedReportPath("com.example.TestClass", null, ".html"))
        .isEqualTo("com_example_TestClass.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldFallbackWhenPathContainsTraversal() {
    assertThat(
            DocumentUtils.generateSourceAlignedReportPath(
                "com.example.OrderService.Result",
                "src/main/java/com/example/../OrderService.java",
                ".html"))
        .isEqualTo("com_example_OrderService_Result.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldNormalizeWindowsDriveAndSanitizeSegments() {
    assertThat(
            DocumentUtils.generateSourceAlignedReportPath(
                "com.example.OrderService.Result",
                "C:\\repo root\\src\\main\\java\\com\\example\\OrderService.java",
                ".html"))
        .isEqualTo("repo_root/src/main/java/com/example/OrderService_Result.html");
  }

  @Test
  void generateSourceAlignedReportPath_shouldFallbackWhenSourcePathIsUnknownMarker() {
    assertThat(
            DocumentUtils.generateSourceAlignedReportPath(
                "com.example.TestClass", MethodInfo.UNKNOWN, ".html"))
        .isEqualTo("com_example_TestClass.html");
  }

  @Test
  void buildFieldsInfo_shouldFormatFields() {
    FieldInfo field1 = new FieldInfo();
    field1.setName("name");
    field1.setType("String");
    field1.setVisibility("private");

    FieldInfo field2 = new FieldInfo();
    field2.setName("age");
    field2.setType("int");
    field2.setVisibility("protected");

    String info = DocumentUtils.buildFieldsInfo(List.of(field1, field2));

    // Implementation uses Japanese visibility labels
    assertThat(info).contains("- `name`: String (非公開)");
    assertThat(info).contains("- `age`: int (保護)");
  }

  @Test
  void buildFieldsInfo_shouldReturnPlaceholderForEmptyList() {
    assertThat(DocumentUtils.buildFieldsInfo(List.of())).isEqualTo("フィールドなし");
    assertThat(DocumentUtils.buildFieldsInfo(null)).isEqualTo("フィールドなし");
  }

  @Test
  void buildMethodsSummary_shouldFormatMethods() {
    MethodInfo method1 = new MethodInfo();
    method1.setName("doWork");
    method1.setCyclomaticComplexity(3);
    method1.setLoc(12);

    MethodInfo method2 = new MethodInfo();
    method2.setName("calculate");
    method2.setCyclomaticComplexity(8);
    method2.setLoc(25);

    String summary = DocumentUtils.buildMethodsSummary(List.of(method1, method2));

    assertThat(summary).contains("- `doWork`: 複雑度 3, 行数 12");
    assertThat(summary).contains("- `calculate`: 複雑度 8, 行数 25");
  }

  @Test
  void buildMethodsSummary_shouldReturnPlaceholderForEmptyList() {
    assertThat(DocumentUtils.buildMethodsSummary(List.of())).isEqualTo("メソッドなし");
    assertThat(DocumentUtils.buildMethodsSummary(null)).isEqualTo("メソッドなし");
  }

  @Test
  void escapeHtml_shouldEscapeSpecialCharacters() {
    assertThat(DocumentUtils.escapeHtml("<script>alert('xss')</script>"))
        .isEqualTo("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    assertThat(DocumentUtils.escapeHtml("a & b")).isEqualTo("a &amp; b");
    assertThat(DocumentUtils.escapeHtml("\"quoted\"")).isEqualTo("&quot;quoted&quot;");
    assertThat(DocumentUtils.escapeHtml(null)).isEqualTo("");
  }

  @Test
  void stripCommentedRegions_shouldRemoveBlockAndLineComments() {
    String source =
        """
        /**
         * description
         */
        public void doWork() {
          // temporary workaround
          int total = 1 + 2;
          /*
           * old path
           * return;
           */
          System.out.println(total);
        }
        """;

    String stripped = DocumentUtils.stripCommentedRegions(source);

    assertThat(stripped).contains("public void doWork() {");
    assertThat(stripped).contains("int total = 1 + 2;");
    assertThat(stripped).contains("System.out.println(total);");
    assertThat(stripped).doesNotContain("description");
    assertThat(stripped).doesNotContain("temporary workaround");
    assertThat(stripped).doesNotContain("old path");
  }

  @Test
  void stripCommentedRegions_shouldReturnEmptyStringForNullOrBlank() {
    assertThat(DocumentUtils.stripCommentedRegions(null)).isEmpty();
    assertThat(DocumentUtils.stripCommentedRegions("   \n\t")).isEmpty();
  }

  @Test
  void stripCommentedRegions_shouldRemoveInlineCommentsButKeepStringLiterals() {
    String source =
        """
        public String buildMessage(String name) {
          String marker = "http://craftsmann-bro.com//path";
          int total = 1 + 2; // inline note
          char slash = '/';
          return marker + ":" + total; // trailing
        }
        """;

    String stripped = DocumentUtils.stripCommentedRegions(source);

    assertThat(stripped).contains("String marker = \"http://craftsmann-bro.com//path\";");
    assertThat(stripped).contains("int total = 1 + 2;");
    assertThat(stripped).contains("return marker + \":\" + total;");
    assertThat(stripped).doesNotContain("inline note");
    assertThat(stripped).doesNotContain("trailing");
  }

  @Test
  void stripCommentedRegions_shouldPreserveEscapedQuotesAndChars() {
    String source =
        """
        public String buildMessage() {
          String message = "He said \\\"//keep\\\"";
          char slash = '/';
          return message + slash; // remove me
        }
        """;

    String stripped = DocumentUtils.stripCommentedRegions(source);

    assertThat(stripped).contains("String message = \"He said \\\"//keep\\\"\";");
    assertThat(stripped).contains("char slash = '/';");
    assertThat(stripped).contains("return message + slash;");
    assertThat(stripped).doesNotContain("remove me");
  }

  @Test
  void stripCommentedRegions_shouldHandleUnclosedBlockComment() {
    String source = """
        int total = 10;
        /* unfinished block
        """;

    String stripped = DocumentUtils.stripCommentedRegions(source);

    assertThat(stripped).isEqualTo("int total = 10;");
  }

  @Test
  void filterMethodsForSpecification_shouldDeduplicateQualifiedSignatureVariants() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService");

    MethodInfo simple = new MethodInfo();
    simple.setName("load");
    simple.setSignature("public void load(String id)");
    simple.setParameterCount(1);
    simple.setLoc(5);
    simple.setSourceCode("public void load(String id) {}");

    MethodInfo qualified = new MethodInfo();
    qualified.setName("load");
    qualified.setSignature("public void load(java.lang.String id)");
    qualified.setParameterCount(1);
    qualified.setLoc(5);
    qualified.setSourceCode("public void load(java.lang.String id) {}");

    classInfo.setMethods(List.of(qualified, simple));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).getSignature()).isEqualTo("public void load(String id)");
  }

  @Test
  void filterMethodsForSpecification_shouldExcludeImplicitDefaultConstructorOnly() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");

    MethodInfo implicitCtor = new MethodInfo();
    implicitCtor.setName("TestClass");
    implicitCtor.setSignature("public TestClass()");
    implicitCtor.setParameterCount(0);
    implicitCtor.setLoc(0);

    MethodInfo explicitCtor = new MethodInfo();
    explicitCtor.setName("TestClass");
    explicitCtor.setSignature("public TestClass()");
    explicitCtor.setParameterCount(0);
    explicitCtor.setLoc(2);
    explicitCtor.setSourceCode("public TestClass() {}");

    classInfo.setMethods(List.of(implicitCtor, explicitCtor));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).getLoc()).isEqualTo(2);
  }

  @Test
  void filterMethodsForSpecification_shouldExcludePrivateMethods() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");

    MethodInfo publicMethod = new MethodInfo();
    publicMethod.setName("doWork");
    publicMethod.setSignature("public void doWork()");
    publicMethod.setVisibility("public");
    publicMethod.setParameterCount(0);
    publicMethod.setLoc(3);

    MethodInfo privateMethod = new MethodInfo();
    privateMethod.setName("helper");
    privateMethod.setSignature("private void helper()");
    privateMethod.setVisibility("private");
    privateMethod.setParameterCount(0);
    privateMethod.setLoc(2);

    classInfo.setMethods(List.of(publicMethod, privateMethod));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).getName()).isEqualTo("doWork");
  }

  @Test
  void filterMethodsForSpecification_shouldRetainPrivateConstructor() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.PaymentResult");

    MethodInfo privateConstructor = new MethodInfo();
    privateConstructor.setName("PaymentResult");
    privateConstructor.setSignature("private PaymentResult(boolean success)");
    privateConstructor.setVisibility("private");
    privateConstructor.setParameterCount(1);
    privateConstructor.setLoc(4);
    privateConstructor.setSourceCode(
        "private PaymentResult(boolean success) { this.success = success; }");

    MethodInfo factoryMethod = new MethodInfo();
    factoryMethod.setName("failure");
    factoryMethod.setSignature("public static PaymentResult failure(String errorMessage)");
    factoryMethod.setVisibility("public");
    factoryMethod.setParameterCount(1);
    factoryMethod.setLoc(5);

    classInfo.setMethods(List.of(privateConstructor, factoryMethod));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered)
        .extracting(MethodInfo::getName)
        .containsExactly("PaymentResult", "failure");
  }

  @Test
  void filterMethodsForSpecification_shouldRetainAccessorCoverageForDataHolderClass() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerRecord");

    FieldInfo idField = new FieldInfo();
    idField.setName("customerId");
    idField.setType("String");

    FieldInfo statusField = new FieldInfo();
    statusField.setName("status");
    statusField.setType("String");
    classInfo.setFields(List.of(idField, statusField));

    MethodInfo constructor = new MethodInfo();
    constructor.setName("CustomerRecord");
    constructor.setSignature("private CustomerRecord(String customerId, String status)");
    constructor.setVisibility("private");
    constructor.setParameterCount(2);
    constructor.setLoc(3);
    constructor.setSourceCode(
        "private CustomerRecord(String customerId, String status) { this.customerId = customerId; this.status = status; }");

    MethodInfo getter = new MethodInfo();
    getter.setName("getCustomerId");
    getter.setSignature("public String getCustomerId()");
    getter.setVisibility("public");
    getter.setParameterCount(0);
    getter.setLoc(1);
    getter.setCyclomaticComplexity(1);
    getter.setSourceCode("public String getCustomerId() { return customerId; }");

    MethodInfo setter = new MethodInfo();
    setter.setName("setCustomerId");
    setter.setSignature("public void setCustomerId(String customerId)");
    setter.setVisibility("public");
    setter.setParameterCount(1);
    setter.setLoc(2);
    setter.setCyclomaticComplexity(1);
    setter.setSourceCode(
        "public void setCustomerId(String customerId) { this.customerId = customerId; }");

    MethodInfo statusGetter = new MethodInfo();
    statusGetter.setName("getStatus");
    statusGetter.setSignature("public String getStatus()");
    statusGetter.setVisibility("public");
    statusGetter.setParameterCount(0);
    statusGetter.setLoc(1);
    statusGetter.setCyclomaticComplexity(1);
    statusGetter.setSourceCode("public String getStatus() { return status; }");

    classInfo.setMethods(List.of(constructor, getter, setter, statusGetter));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered)
        .extracting(MethodInfo::getName)
        .containsExactly("CustomerRecord", "getCustomerId", "setCustomerId", "getStatus");
  }

  @Test
  void filterMethodsForSpecification_shouldRetainTrivialAccessorLikeMethods() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo getter = new MethodInfo();
    getter.setName("getCustomerId");
    getter.setSignature("public String getCustomerId()");
    getter.setVisibility("public");
    getter.setParameterCount(0);
    getter.setLoc(1);
    getter.setCyclomaticComplexity(1);
    getter.setSourceCode("public String getCustomerId() { return customerId; }");

    MethodInfo setter = new MethodInfo();
    setter.setName("setCustomerId");
    setter.setSignature("public void setCustomerId(String customerId)");
    setter.setVisibility("public");
    setter.setParameterCount(1);
    setter.setLoc(2);
    setter.setCyclomaticComplexity(1);
    setter.setSourceCode(
        "public void setCustomerId(String customerId) { this.customerId = customerId; }");

    MethodInfo businessMethod = new MethodInfo();
    businessMethod.setName("loadFromRepository");
    businessMethod.setSignature("public Customer loadFromRepository(String id)");
    businessMethod.setVisibility("public");
    businessMethod.setParameterCount(1);
    businessMethod.setLoc(8);
    businessMethod.setCyclomaticComplexity(2);

    classInfo.setMethods(List.of(getter, setter, businessMethod));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered)
        .extracting(MethodInfo::getName)
        .containsExactly("getCustomerId", "setCustomerId", "loadFromRepository");
  }

  @Test
  void filterMethodsForSpecification_shouldRetainAccessorWhenBehavioralSignalsExist() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo conditionalGetter = new MethodInfo();
    conditionalGetter.setName("getPrimaryAddress");
    conditionalGetter.setSignature("public String getPrimaryAddress()");
    conditionalGetter.setVisibility("public");
    conditionalGetter.setParameterCount(0);
    conditionalGetter.setLoc(9);
    conditionalGetter.setCyclomaticComplexity(3);
    conditionalGetter.setHasConditionals(true);
    conditionalGetter.setSourceCode(
        """
        public String getPrimaryAddress() {
          if (address == null) {
            throw new IllegalStateException("address is required");
          }
          return address.trim();
        }
        """);

    classInfo.setMethods(List.of(conditionalGetter));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).extracting(MethodInfo::getName).containsExactly("getPrimaryAddress");
  }

  @Test
  void filterMethodsForSpecification_shouldIgnoreSyntheticMainReturnPathForAccessorSignal() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo getter = new MethodInfo();
    getter.setName("getCustomerId");
    getter.setSignature("public String getCustomerId()");
    getter.setVisibility("public");
    getter.setParameterCount(0);
    getter.setLoc(1);
    getter.setCyclomaticComplexity(1);
    getter.setSourceCode("public String getCustomerId() { return customerId; }");
    RepresentativePath synthetic = new RepresentativePath();
    synthetic.setId("path-1");
    synthetic.setDescription("Main success path");
    synthetic.setExpectedOutcomeHint("success");
    getter.setRepresentativePaths(List.of(synthetic));

    classInfo.setMethods(List.of(getter));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).extracting(MethodInfo::getName).containsExactly("getCustomerId");
  }

  @Test
  void filterMethodsForSpecification_shouldNotTreatSetupMethodAsAccessor() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo setup = new MethodInfo();
    setup.setName("setup");
    setup.setSignature("public void setup(java.util.Map<String, String> config)");
    setup.setVisibility("public");
    setup.setParameterCount(1);
    setup.setLoc(3);
    setup.setCyclomaticComplexity(1);
    setup.setSourceCode("public void setup(Map<String, String> config) { this.config = config; }");

    classInfo.setMethods(List.of(setup));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).extracting(MethodInfo::getName).containsExactly("setup");
  }

  @Test
  void filterMethodsForSpecification_shouldPreferRicherMethodForDuplicateSignature() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo readable = new MethodInfo();
    readable.setName("normalize");
    readable.setSignature("public void normalize(String id)");
    readable.setVisibility("public");
    readable.setParameterCount(1);
    readable.setLoc(1);

    MethodInfo rich = new MethodInfo();
    rich.setName("normalize");
    rich.setSignature("public void normalize(java.lang.String id)");
    rich.setVisibility("public");
    rich.setParameterCount(1);
    rich.setLoc(6);
    rich.setSourceCode("public void normalize(String id) { this.id = id.trim(); }");
    rich.setAnnotations(List.of("Deprecated"));
    rich.setCalledMethods(List.of("com.example.CustomerData#trim"));
    rich.setThrownExceptions(List.of("java.io.IOException"));
    RepresentativePath representativePath = new RepresentativePath();
    representativePath.setId("path-rich");
    rich.setRepresentativePaths(List.of(representativePath));

    classInfo.setMethods(List.of(readable, rich));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).getSignature())
        .isEqualTo("public void normalize(java.lang.String id)");
    assertThat(filtered.get(0).getSourceCode()).contains("trim");
  }

  @Test
  void filterMethodsForSpecification_shouldDeduplicateGenericParameterVariants() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerData");

    MethodInfo qualified = new MethodInfo();
    qualified.setName("register");
    qualified.setSignature(
        "public void register(final java.util.Map<java.lang.String, java.lang.Integer> values)");
    qualified.setVisibility("public");
    qualified.setParameterCount(1);
    qualified.setLoc(2);

    MethodInfo simplified = new MethodInfo();
    simplified.setName("register");
    simplified.setSignature("public void register(Map<String, Integer> values)");
    simplified.setVisibility("public");
    simplified.setParameterCount(1);
    simplified.setLoc(2);

    classInfo.setMethods(List.of(qualified, simplified));

    List<MethodInfo> filtered = DocumentUtils.filterMethodsForSpecification(classInfo);

    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).getSignature())
        .isEqualTo("public void register(Map<String, Integer> values)");
  }
}
