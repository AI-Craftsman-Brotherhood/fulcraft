package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.PromptRedactionService;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LlmDocumentGeneratorTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @Test
  void generateDetailedDocument_stripsCodeFences() {
    CapturingLlmClient client =
        new CapturingLlmClient(
            """
            ```markdown
            # TestClass 詳細設計
            ## 1. 目的と責務（事実）
            ## 2. クラス外部仕様
            ## 3. メソッド仕様
            ## 4. 要注意事項
            - なし
            ## 5. 改善提案（任意）
            ## 6. 未確定事項（解析情報不足）
            ```
            """);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("TestClass.java");

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(result).contains("# TestClass 詳細設計");
    assertThat(result).doesNotContain("```");
  }

  @Test
  void generateDetailedDocument_usesFallbackFilePath() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath(null);

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("ファイルパス: 該当なし");
    assertThat(client.lastPrompt).doesNotContain("ファイルパス: null");
  }

  @Test
  void generateDetailedDocument_rejectsNullConfig() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");

    assertThatThrownBy(() -> generator.generateDetailedDocument(classInfo, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("llmConfig");
  }

  @Test
  void generateDetailedDocument_buildsSpecificationOrientedPromptWithAnalysisFacts() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService");
    classInfo.setFilePath("src/main/java/com/example/OrderService.java");
    classInfo.setLoc(120);
    classInfo.setExtendsTypes(List.of("BaseService"));
    classInfo.setImplementsTypes(List.of("OrderUseCase"));

    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder(String orderId)");
    method.setVisibility("public");
    method.setLoc(40);
    method.setCyclomaticComplexity(18);
    method.setMaxNestingDepth(3);
    method.setParameterCount(1);
    method.setUsageCount(5);
    method.setHasConditionals(true);
    method.setHasLoops(true);
    method.setCalledMethods(List.of("com.example.OrderRepository#save(com.example.Order)"));
    method.setThrownExceptions(List.of("java.lang.IllegalArgumentException"));
    method.setSourceCode("if (orderId == null) { throw new IllegalArgumentException(); }");

    GuardSummary guard = new GuardSummary();
    guard.setType(GuardType.FAIL_GUARD);
    guard.setCondition("orderId == null || orderId.isBlank()");
    BranchSummary branchSummary = new BranchSummary();
    branchSummary.setGuards(List.of(guard));
    method.setBranchSummary(branchSummary);

    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    path.setDescription("Invalid input path");
    path.setExpectedOutcomeHint("error");
    method.setRepresentativePaths(List.of(path));

    classInfo.setMethods(List.of(method));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("## 出力フォーマット（厳守）");
    assertThat(client.lastPrompt).contains("## 4. 要注意事項");
    assertThat(client.lastPrompt).contains("分岐/ガード");
    assertThat(client.lastPrompt).contains("代表パス");
    assertThat(client.lastPrompt).contains("ソースコード");
    assertThat(client.lastPrompt).contains("path-1");
  }

  @Test
  void generateDetailedDocument_includesCalledMethodArgumentLiteralsInPromptFacts() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService");
    classInfo.setFilePath("src/main/java/com/example/OrderService.java");

    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public boolean processOrder(String orderId)");
    method.setVisibility("public");
    method.setLoc(10);
    method.setCyclomaticComplexity(2);
    method.setParameterCount(1);
    method.setSourceCode(
        """
            public boolean processOrder(String orderId) {
                notificationService.sendNotification(orderId, "Order is being processed");
                return true;
            }
            """);

    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw(
        "com.example.NotificationService#sendNotification(java.lang.String, java.lang.String)");
    ref.setResolved(
        "com.example.NotificationService#sendNotification(java.lang.String, java.lang.String)");
    ref.setArgumentLiterals(List.of("\"Order is being processed\""));
    method.setCalledMethodRefs(List.of(ref));
    classInfo.setMethods(List.of(method));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt)
        .contains(
            "com.example.NotificationService#sendNotification(java.lang.String, java.lang.String) [arg_literals: \"Order is being processed\"]");
  }

  @Test
  void generateDetailedDocument_excludesImplicitDefaultConstructorFromPrompt() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setMethodCount(2);

    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setLoc(5);
    method.setCyclomaticComplexity(1);
    method.setParameterCount(0);

    MethodInfo implicitCtor = new MethodInfo();
    implicitCtor.setName("TestClass");
    implicitCtor.setSignature("public TestClass()");
    implicitCtor.setVisibility("public");
    implicitCtor.setLoc(0);
    implicitCtor.setParameterCount(0);
    implicitCtor.setCyclomaticComplexity(1);
    implicitCtor.setSourceCode("TestClass() {}");

    classInfo.setMethods(List.of(method, implicitCtor));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("- メソッド数: 1");
    assertThat(client.lastPrompt).contains("#### doWork");
    assertThat(client.lastPrompt).doesNotContain("#### TestClass");
    assertThat(client.lastPrompt).doesNotContain("public TestClass()");
  }

  @Test
  void generateDetailedDocument_excludesPrivateMethodsFromPrompt() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");

    MethodInfo publicMethod = new MethodInfo();
    publicMethod.setName("doWork");
    publicMethod.setSignature("public void doWork()");
    publicMethod.setVisibility("public");
    publicMethod.setLoc(5);
    publicMethod.setCyclomaticComplexity(1);
    publicMethod.setParameterCount(0);

    MethodInfo privateMethod = new MethodInfo();
    privateMethod.setName("validateInternal");
    privateMethod.setSignature("private boolean validateInternal(String id)");
    privateMethod.setVisibility("private");
    privateMethod.setLoc(8);
    privateMethod.setCyclomaticComplexity(2);
    privateMethod.setParameterCount(1);

    classInfo.setMethods(List.of(publicMethod, privateMethod));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("- メソッド数: 1");
    assertThat(client.lastPrompt).contains("#### doWork");
    assertThat(client.lastPrompt).doesNotContain("#### validateInternal");
    assertThat(client.lastPrompt).doesNotContain("private boolean validateInternal");
  }

  @Test
  void generateDetailedDocument_usesSourceBasedPackageForNestedClass() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.PaymentGateway");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");
    classInfo.setNestedClass(true);

    MethodInfo method = new MethodInfo();
    method.setName("charge");
    method.setSignature("GatewayResponse charge(String customerId, BigDecimal amount)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("- パッケージ: com.example.legacy");
    assertThat(client.lastPrompt).doesNotContain("com.example.legacy.PaymentService");
  }

  @Test
  void generateDetailedDocument_stripsCommentedSourcePreviewFromPrompt() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");

    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setLoc(8);
    method.setCyclomaticComplexity(2);
    method.setParameterCount(0);
    method.setSourceCode(
        """
            /**
             * spec_comment_marker
             */
            public void doWork() {
              // line_comment_marker
              int total = 1 + 2;
              /* block_comment_marker */
              System.out.println(total);
            }
            """);
    classInfo.setMethods(List.of(method));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("int total = 1 + 2;");
    assertThat(client.lastPrompt).contains("System.out.println(total);");
    assertThat(client.lastPrompt).doesNotContain("spec_comment_marker");
    assertThat(client.lastPrompt).doesNotContain("line_comment_marker");
    assertThat(client.lastPrompt).doesNotContain("block_comment_marker");
  }

  @Test
  void generateDetailedDocument_deduplicatesQualifiedMethodSignatureVariants() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setMethodCount(2);

    MethodInfo simple = new MethodInfo();
    simple.setName("load");
    simple.setSignature("public void load(String id)");
    simple.setVisibility("public");
    simple.setLoc(5);
    simple.setCyclomaticComplexity(1);
    simple.setParameterCount(1);
    simple.setSourceCode("public void load(String id) {}");

    MethodInfo qualified = new MethodInfo();
    qualified.setName("load");
    qualified.setSignature("public void load(java.lang.String id)");
    qualified.setVisibility("public");
    qualified.setLoc(5);
    qualified.setCyclomaticComplexity(1);
    qualified.setParameterCount(1);
    qualified.setSourceCode("public void load(java.lang.String id) {}");

    classInfo.setMethods(List.of(qualified, simple));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("- メソッド数: 1");
    assertThat(client.lastPrompt).contains("`public void load(String id)`");
    assertThat(client.lastPrompt).doesNotContain("load(java.lang.String id)");
  }

  @Test
  void generateDetailedDocument_includesDynamicResolutionEvidenceInPrompt() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicService");

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public Object resolve()");
    method.setVisibility("public");
    method.setParameterCount(0);
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicService")
                .methodSig("public Object resolve()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(
                    List.of(
                        "com.example.legacy.CustomerService#processCustomer(String)",
                        "com.example.legacy.CustomerService#processCustomer(Object)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.8)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.lastPrompt).contains("dynamic_resolutions");
    assertThat(client.lastPrompt).contains("verified=false");
    assertThat(client.lastPrompt).contains("status=UNCONFIRMED");
    assertThat(client.lastPrompt).contains("processCustomer");
  }

  @Test
  void generateDetailedDocument_retriesWhenMethodHeadingCountIsInvalid() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        ### 3.2 unexpectedMethod
        #### 3.2.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        ## 6. 未確定事項（解析情報不足）
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("前回出力に整合性エラー");
    assertThat(result).contains("### 3.1 doWork");
    assertThat(result).doesNotContain("### 3.2 unexpectedMethod");
  }

  @Test
  void generateDetailedDocument_retriesWhenExternalMethodExistenceIsAsserted() {
    String invalidResponse =
        """
        # Sample Detailed Design
        ## 1. Purpose and Responsibilities (Facts)
        ## 2. External Class Specification
        - Class Name: `Sample`
        - Package: `com.example`
        - File Path: `src/main/java/com/example/Sample.java`
        - Class Type: class
        - Extends: None
        - Implements: None
        ## 3. Method Specifications
        ### 3.1 doWork
        #### 3.1.1 Inputs/Outputs
        #### 3.1.2 Preconditions
        - The method `processCustomer` with parameter `(String)` must be declared in `CustomerService`.
        ## 4. Cautions
        None
        ## 5. Recommendations (Optional)
        ## 6. Open Questions (Insufficient Analysis Data)
        """;
    String validResponse =
        """
        # Sample Detailed Design
        ## 1. Purpose and Responsibilities (Facts)
        ## 2. External Class Specification
        - Class Name: `Sample`
        - Package: `com.example`
        - File Path: `src/main/java/com/example/Sample.java`
        - Class Type: class
        - Extends: None
        - Implements: None
        ## 3. Method Specifications
        ### 3.1 doWork
        #### 3.1.1 Inputs/Outputs
        #### 3.1.2 Preconditions
        - None
        ## 4. Cautions
        None
        ## 5. Recommendations (Optional)
        None
        ## 6. Open Questions (Insufficient Analysis Data)
        - `processCustomer` is uncertain and needs verification.
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).contains("uncertain");
    assertThat(result).contains("processCustomer");
  }

  @Test
  void generateDetailedDocument_retriesWhenUncertainDynamicMethodAppearsInMethodSections() {
    String invalidResponse =
        """
        # DynamicSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/DynamicSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - processCustomerメソッドが取得される。
        #### 3.1.4 正常フロー
        - Class.forName でクラスをロードする。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `java.lang.Class#forName`
        #### 3.1.7 テスト観点
        - 取得結果を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # DynamicSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/DynamicSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - なし
        #### 3.1.4 正常フロー
        - Class.forName でクラスをロードする。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `java.lang.Class#forName`
        #### 3.1.7 テスト観点
        - 取得結果を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - 動的解決候補メソッド `processCustomer` の実在確認が必要。
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");
    classInfo.setFilePath("src/main/java/com/example/DynamicSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public Object doWork()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicSample")
                .methodSig("public Object doWork()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.8)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("未確定な動的解決候補が本文に記載されています");
    String methodSection = extractSection(result, "## 3. メソッド仕様", "## 4.");
    assertThat(methodSection).doesNotContain("processCustomer");
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
  }

  @Test
  void generateDetailedDocument_fallsBackWhenRetryStillInvalid() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - doWork の複雑度は 7
        ## 5. 改善提案（任意）
        ## 6. 未確定事項（解析情報不足）
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setCyclomaticComplexity(3);
    method.setCalledMethods(List.of("com.example.repo.OrderRepository#findById(java.lang.String)"));
    method.setThrownExceptions(List.of("java.lang.IllegalArgumentException"));
    BranchSummary branchSummary = new BranchSummary();
    GuardSummary guardSummary = new GuardSummary();
    guardSummary.setType(GuardType.FAIL_GUARD);
    guardSummary.setCondition("id == null");
    branchSummary.setGuards(List.of(guardSummary));
    method.setBranchSummary(branchSummary);
    RepresentativePath invalidPath = new RepresentativePath();
    invalidPath.setId("path-invalid");
    invalidPath.setDescription("invalid request");
    invalidPath.setExpectedOutcomeHint("error");
    invalidPath.setRequiredConditions(List.of("id == null"));
    method.setRepresentativePaths(List.of(invalidPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).contains("### 3.1 doWork");
    assertThat(result).contains("## 5. 改善提案（任意）");
    assertThat(result).contains("- なし");
    assertThat(result).contains("#### 3.1.2 事前条件\n- なし");
    assertThat(result).doesNotContain("id == null");
    assertThat(result).contains("[path-invalid] invalid request -> error");
    assertThat(result).contains("例外: `java.lang.IllegalArgumentException`");
  }

  @Test
  void generateDetailedDocument_skipsLlmForInterfaceAndBuildsConservativeSpec() {
    CapturingLlmClient client = new CapturingLlmClient("should-not-be-used");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.service.OrderRepository");
    classInfo.setInterface(true);
    classInfo.setFilePath("src/main/java/com/example/legacy/service/OrderRepository.java");

    MethodInfo method = new MethodInfo();
    method.setName("findById");
    method.setSignature("Order findById(String id)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isZero();
    assertThat(result).contains("## 3. メソッド仕様");
    assertThat(result).contains("実装クラス依存");
    assertThat(result).contains("なし（宣言のみ）");
    assertThat(result).contains("### 3.1 findById");
  }

  @Test
  void generateDetailedDocument_excludesPrivateSelfCallsFromPrompt() {
    CapturingLlmClient client = new CapturingLlmClient("OK");
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService");

    MethodInfo charge = new MethodInfo();
    charge.setName("charge");
    charge.setSignature("public void charge(String customerId)");
    charge.setVisibility("public");
    charge.setCalledMethods(
        List.of(
            "com.example.legacy.PaymentService#isValidPaymentMethod(java.lang.String)",
            "com.example.legacy.gateway.PaymentGateway#charge(java.lang.String)"));

    MethodInfo privateHelper = new MethodInfo();
    privateHelper.setName("isValidPaymentMethod");
    privateHelper.setSignature("private boolean isValidPaymentMethod(String method)");
    privateHelper.setVisibility("private");

    classInfo.setMethods(List.of(charge, privateHelper));

    generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.prompts.getFirst()).contains("PaymentGateway#charge(java.lang.String)");
    assertThat(client.prompts.getFirst()).doesNotContain("isValidPaymentMethod");
  }

  @Test
  void generateDetailedDocument_sanitizesValidationReasonsInFallbackOpenQuestions() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        - processCustomer メソッドは存在する
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");

    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicSample")
                .methodSig("public void doWork()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(
                    List.of(
                        "com.example.legacy.CustomerService#processCustomer(String)",
                        "com.example.legacy.CustomerService#processCustomer(Object)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
    assertThat(result).doesNotContain("未確定な動的解決を断定しています");
  }

  @Test
  void generateDetailedDocument_fallbackExcludesUncertainDynamicMethodFromMethodSections() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 unexpectedMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicFallbackSample");
    classInfo.setFilePath("src/main/java/com/example/DynamicFallbackSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public Object resolve()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicFallbackSample")
                .methodSig("public Object resolve()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));

    RepresentativePath uncertainPath = new RepresentativePath();
    uncertainPath.setId("path-1");
    uncertainPath.setDescription("Reflective call to processCustomer");
    uncertainPath.setExpectedOutcomeHint("success");
    uncertainPath.setRequiredConditions(List.of("processCustomer method is resolved"));
    method.setRepresentativePaths(List.of(uncertainPath));

    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String methodSection = extractSection(result, "## 3. メソッド仕様", "## 4.");
    assertThat(methodSection).doesNotContain("processCustomer");
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
  }

  @Test
  void generateDetailedDocument_fallbackRewritesSuccessOutcomeForUncertainDynamicMethod() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 unexpectedMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicFallbackSample");
    classInfo.setFilePath("src/main/java/com/example/DynamicFallbackSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public Object resolve()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicFallbackSample")
                .methodSig("public Object resolve()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));

    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-1");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(successPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).contains("実在確認後に確定");
    assertThat(postconditionSection).doesNotContain("結果: success");
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-1");
    assertThat(normalFlowSection).doesNotContain("-> success");
    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).doesNotContain("（success）");
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
  }

  @Test
  void generateDetailedDocument_retriesWhenUncertainDynamicMethodSectionsAssertSuccess() {
    String invalidResponse =
        """
        # DynamicFallbackSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicFallbackSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/DynamicFallbackSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 resolve
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 期待結果: success
        #### 3.1.4 正常フロー
        - 反射呼び出し結果を返す -> success
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 代表パスの結果（success）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # DynamicFallbackSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicFallbackSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/DynamicFallbackSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 resolve
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 反射呼び出し結果は解決候補の実在確認後に確定する。
        #### 3.1.4 正常フロー
        - 反射呼び出しを実行し、解決候補メソッドの確認後に結果を確定する。
        #### 3.1.5 異常・境界
        - 反射解決失敗時は例外経路となる。
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 解決候補メソッドの実在確認後に挙動を検証する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - 動的解決候補メソッド `processCustomer` の実在確認が必要。
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicFallbackSample");
    classInfo.setFilePath("src/main/java/com/example/DynamicFallbackSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("public Object resolve()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicFallbackSample")
                .methodSig("public Object resolve()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("未確定な動的解決を含むメソッドで事後条件に success 断定");
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).doesNotContain("success");
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).doesNotContain("-> success");
    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).doesNotContain("success");
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
  }

  @Test
  void generateDetailedDocument_retriesWhenOpenQuestionsTreatKnownMethodAsUnresolved() {
    String invalidResponse =
        """
        # PaymentService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentService`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 processPayment
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - isValidPaymentMethodメソッドの存在と詳細
        """;
    String validResponse =
        """
        # PaymentService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentService`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 processPayment
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo processPayment = new MethodInfo();
    processPayment.setName("processPayment");
    processPayment.setSignature("public PaymentResult processPayment(String orderId)");
    processPayment.setVisibility("public");

    MethodInfo privateHelper = new MethodInfo();
    privateHelper.setName("isValidPaymentMethod");
    privateHelper.setSignature("private boolean isValidPaymentMethod(String method)");
    privateHelper.setVisibility("private");

    classInfo.setMethods(List.of(processPayment, privateHelper));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("未確定事項で既知メソッドを未確認扱いしています");
    assertThat(result).doesNotContain("isValidPaymentMethodメソッドの存在と詳細");
  }

  @Test
  void generateDetailedDocument_retriesWhenOpenQuestionsUseGenericTemplate() {
    String invalidResponse =
        """
        # DynamicSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - `dynamic_resolutions`で`verified=false`または`confidence<1.0`の情報は未確定であるため、該当するメソッドやクラスの存在は断定できない。
        """;
    String validResponse =
        """
        # DynamicSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - 動的解決候補メソッド `processCustomer` の実在確認が必要。
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");

    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicSample")
                .methodSig("public void doWork()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(
                    List.of(
                        "com.example.legacy.CustomerService#processCustomer(String)",
                        "com.example.legacy.CustomerService#processCustomer(Object)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.8)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("未確定事項に汎用テンプレート文が含まれています");
    assertThat(result).contains("動的解決候補メソッド `processCustomer` の実在確認が必要。");
    assertThat(result).doesNotContain("dynamic_resolutions");
  }

  @Test
  void generateDetailedDocument_repairsMissingStructureBeforeValidation() throws Exception {
    String malformedResponse =
        """
        # ComplexInvoiceService 詳細設計
        ## 1. 目的と責務（事実）
        - 請求計算を行う。
        ## 2. クラス外部仕様
        - 概要: 請求計算サービス
        ## 3. メソッド仕様
        ### 3.1 wrongMethod
        #### 3.1.1 入出力
        - 入力/出力: `wrongMethod()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - なし
        #### 3.1.4 正常フロー
        - なし
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - `dynamic_resolutions`で`verified=false`または`confidence<1.0`の情報は未確定であるため、該当するメソッドやクラスの存在は断定できない。
        """;
    CapturingLlmClient client = new CapturingLlmClient(malformedResponse, malformedResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.ComplexInvoiceService");
    classInfo.setFilePath("src/main/java/com/example/ComplexInvoiceService.java");

    MethodInfo method = new MethodInfo();
    method.setName("calculateInvoice");
    method.setSignature("public void calculateInvoice(String processingDate)");
    method.setVisibility("public");
    method.setParameterCount(1);
    method.setSourceCode(
        """
            public void calculateInvoice(String processingDate) {
              Preconditions.checkNotNull(processingDate);
            }
            """);
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-structure-repair");

    int generated = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(
            outputDir.resolve("src/main/java/com/example/ComplexInvoiceService_detail.md"));

    assertThat(generated).isEqualTo(1);
    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("- クラス名: `ComplexInvoiceService`");
    assertThat(result).contains("- パッケージ: `com.example`");
    assertThat(result).contains("- ファイルパス: `src/main/java/com/example/ComplexInvoiceService.java`");
    assertThat(result).contains("### 3.1 calculateInvoice");
    assertThat(result).contains("processingDate != null");
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）");
    assertThat(result).contains("- なし");
    assertThat(result).doesNotContain("dynamic_resolutions");
  }

  @Test
  void generateDetailedDocument_repairsUnsupportedNoArgPreconditionsBeforeValidation()
      throws Exception {
    String malformedResponse =
        """
        # Customer 詳細設計
        ## 1. 目的と責務（事実）
        - 顧客情報を提供する。
        ## 2. クラス外部仕様
        - クラス名: `Customer`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Customer.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 resolveCustomerId
        #### 3.1.1 入出力
        - 入力/出力: `resolveCustomerId()`
        #### 3.1.2 事前条件
        - 実行環境が初期化済みであること
        #### 3.1.3 事後条件
        - 戻り値としてIDを返す
        #### 3.1.4 正常フロー
        - フィールドからIDを返す
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 戻り値が期待どおりか確認する
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(malformedResponse, malformedResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Customer");
    classInfo.setFilePath("src/main/java/com/example/Customer.java");

    MethodInfo method = new MethodInfo();
    method.setName("resolveCustomerId");
    method.setSignature("public String resolveCustomerId()");
    method.setVisibility("public");
    method.setParameterCount(0);
    method.setSourceCode(
        """
            public String resolveCustomerId() {
              return id;
            }
            """);
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-noarg-repair");

    int generated = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(outputDir.resolve("src/main/java/com/example/Customer_detail.md"));

    assertThat(generated).isEqualTo(1);
    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("### 3.1 resolveCustomerId");
    assertThat(result).contains("#### 3.1.2 事前条件");
    assertThat(result).contains("- なし");
    assertThat(result).doesNotContain("実行環境が初期化済みであること");
  }

  @Test
  void generate_filtersKnownCrossClassMethodsFromFallbackOpenQuestions() throws Exception {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 unexpectedMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo dynamicClass = new ClassInfo();
    dynamicClass.setFqn("com.example.legacy.DynamicTestPatterns");
    dynamicClass.setFilePath("src/main/java/com/example/legacy/DynamicTestPatterns.java");

    MethodInfo loadClassByName = new MethodInfo();
    loadClassByName.setName("loadClassByName");
    loadClassByName.setSignature("public Object loadClassByName() throws Exception");
    loadClassByName.setVisibility("public");
    loadClassByName.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.legacy.DynamicTestPatterns")
                .methodSig("public Object loadClassByName() throws Exception")
                .resolvedMethodSig("com.example.legacy.util.StringUtil#truncate(String,int)")
                .candidates(List.of("com.example.legacy.util.StringUtil#truncate(String,int)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.8)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    dynamicClass.setMethods(List.of(loadClassByName));

    ClassInfo utilClass = new ClassInfo();
    utilClass.setFqn("com.example.legacy.util.StringUtil");
    utilClass.setFilePath("src/main/java/com/example/legacy/util/StringUtil.java");
    MethodInfo truncate = new MethodInfo();
    truncate.setName("truncate");
    truncate.setSignature("public static String truncate(String str, int length)");
    truncate.setVisibility("public");
    utilClass.setMethods(List.of(truncate));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(dynamicClass, utilClass));

    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-known-methods");

    generator.generate(analysis, outputDir, config);

    Path dynamicDoc =
        outputDir.resolve("src/main/java/com/example/legacy/DynamicTestPatterns_detail.md");
    String result = Files.readString(dynamicDoc);

    assertThat(result).contains("## 6. 未確定事項（解析情報不足）");
    assertThat(result).contains("- なし");
    assertThat(result).doesNotContain("truncate");
  }

  @Test
  void generate_countsOnlySuccessfullyWrittenDocumentsWhenClassGenerationFails() throws Exception {
    LlmDocumentGenerator generator =
        new LlmDocumentGenerator(new AlwaysFailingLlmClient(), new PromptRedactionService());

    ClassInfo interfaceClass = new ClassInfo();
    interfaceClass.setFqn("com.example.ContractApi");
    interfaceClass.setInterface(true);
    MethodInfo interfaceMethod = new MethodInfo();
    interfaceMethod.setName("execute");
    interfaceMethod.setSignature("void execute()");
    interfaceMethod.setVisibility("public");
    interfaceClass.setMethods(List.of(interfaceMethod));

    ClassInfo concreteClass = new ClassInfo();
    concreteClass.setFqn("com.example.OrderService");
    MethodInfo concreteMethod = new MethodInfo();
    concreteMethod.setName("execute");
    concreteMethod.setSignature("public void execute()");
    concreteMethod.setVisibility("public");
    concreteClass.setMethods(List.of(concreteMethod));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(interfaceClass, concreteClass));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-count");

    int generatedCount = generator.generate(analysis, outputDir, config);

    assertThat(generatedCount).isEqualTo(1);
    assertThat(Files.exists(outputDir.resolve("com_example_ContractApi_detail.md"))).isTrue();
    assertThat(Files.exists(outputDir.resolve("com_example_OrderService_detail.md"))).isFalse();
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsPreferFailurePaths() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.InvoiceSample");

    MethodInfo method = new MethodInfo();
    method.setName("calculate");
    method.setSignature("public void calculate(String id, String regionCode)");
    method.setVisibility("public");

    RepresentativePath pathSuccess = new RepresentativePath();
    pathSuccess.setId("path-success");
    pathSuccess.setDescription("switch case JP");
    pathSuccess.setExpectedOutcomeHint("case-\"JP\"");
    pathSuccess.setRequiredConditions(List.of("regionCode == \"JP\""));

    RepresentativePath pathFailure = new RepresentativePath();
    pathFailure.setId("path-failure");
    pathFailure.setDescription("validation failure");
    pathFailure.setExpectedOutcomeHint("failure");
    pathFailure.setRequiredConditions(List.of("id == null"));

    method.setRepresentativePaths(List.of(pathSuccess, pathFailure));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).doesNotContain("静的解析結果");
    assertThat(result).doesNotContain("代表パス");
    assertThat(result).contains("分岐 `");
    assertThat(result).contains("id != null");
    assertThat(result).doesNotContain("id == null");
    assertThat(result).doesNotContain("regionCode == \"JP\"");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsIncludeSourceGuavaChecks() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderProcessingService");

    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder(String orderId, double amount, String currency)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public void processOrder(String orderId, double amount, String currency) {
                Preconditions.checkNotNull(orderId, "Order ID cannot be null");
                Preconditions.checkArgument(!orderId.isEmpty(), "Order ID cannot be empty");
                Preconditions.checkArgument(amount > 0, "Amount must be positive: %s", amount);
                Preconditions.checkNotNull(currency, "Currency cannot be null");
                Preconditions.checkArgument(supportedCurrencies.contains(currency), "Unsupported currency: %s", currency);
            }
            """);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).contains("orderId != null");
    assertThat(result).contains("!orderId.isEmpty()");
    assertThat(result).doesNotContain("!!orderId.isEmpty()");
    assertThat(result).doesNotContain("!!!orderId.isEmpty()");
    assertThat(result).contains("amount > 0");
    assertThat(result).contains("currency != null");
    assertThat(result).contains("supportedCurrencies.contains(currency)");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsNormalizeFailureConditions() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.ComplexInvoiceService");

    MethodInfo method = new MethodInfo();
    method.setName("calculateInvoice");
    method.setSignature(
        "public void calculateInvoice(SimpleCustomerData customer, List<Item> items)");
    method.setVisibility("public");

    GuardSummary customerGuard = new GuardSummary();
    customerGuard.setType(GuardType.FAIL_GUARD);
    customerGuard.setCondition("customer == null");

    GuardSummary itemGuard = new GuardSummary();
    itemGuard.setType(GuardType.FAIL_GUARD);
    itemGuard.setCondition("items == null || items.isEmpty()");

    GuardSummary loopGuard = new GuardSummary();
    loopGuard.setType(GuardType.LOOP_GUARD_CONTINUE);
    loopGuard.setCondition("item.getQuantity() <= 0");

    BranchSummary branchSummary = new BranchSummary();
    branchSummary.setGuards(List.of(customerGuard, itemGuard, loopGuard));
    method.setBranchSummary(branchSummary);

    RepresentativePath validationPath = new RepresentativePath();
    validationPath.setId("path-invalid");
    validationPath.setDescription("validation failure");
    validationPath.setExpectedOutcomeHint("failure");
    validationPath.setRequiredConditions(List.of("regionCode == null || regionCode.isEmpty()"));

    RepresentativePath loopPath = new RepresentativePath();
    loopPath.setId("path-loop");
    loopPath.setDescription("Loop guard continue");
    loopPath.setExpectedOutcomeHint("loop-continue");
    loopPath.setRequiredConditions(List.of("item.getQuantity() <= 0"));

    method.setRepresentativePaths(List.of(validationPath, loopPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(result).contains("customer != null");
    assertThat(result).contains("items != null");
    assertThat(result).contains("!items.isEmpty()");
    assertThat(result).contains("regionCode != null");
    assertThat(result).contains("!regionCode.isEmpty()");
    assertThat(result).doesNotContain("customer == null");
    assertThat(result).doesNotContain("items == null || items.isEmpty()");
    assertThat(result).doesNotContain("regionCode == null || regionCode.isEmpty()");
    assertThat(result).doesNotContain("item.getQuantity() <= 0");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsExcludeFailureSideRuntimeGuards() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.PaymentFallbackSample");

    MethodInfo method = new MethodInfo();
    method.setName("processPayment");
    method.setSignature("public void processPayment(String orderId, String paymentMethod)");
    method.setVisibility("public");

    GuardSummary nullGuard = new GuardSummary();
    nullGuard.setType(GuardType.FAIL_GUARD);
    nullGuard.setCondition("orderId == null");

    GuardSummary validationGuard = new GuardSummary();
    validationGuard.setType(GuardType.FAIL_GUARD);
    validationGuard.setCondition("!isValidPaymentMethod(paymentMethod)");

    GuardSummary gatewayGuard = new GuardSummary();
    gatewayGuard.setType(GuardType.FAIL_GUARD);
    gatewayGuard.setCondition("!gatewayResponse.isSuccess()");

    GuardSummary duplicateGuard = new GuardSummary();
    duplicateGuard.setType(GuardType.FAIL_GUARD);
    duplicateGuard.setCondition(
        "existingPayment.isPresent() && existingPayment.get().getStatus().equals(\"COMPLETED\")");

    BranchSummary branchSummary = new BranchSummary();
    branchSummary.setGuards(List.of(nullGuard, validationGuard, gatewayGuard, duplicateGuard));
    method.setBranchSummary(branchSummary);

    RepresentativePath earlyReturnPath = new RepresentativePath();
    earlyReturnPath.setId("path-early");
    earlyReturnPath.setDescription("Early return path");
    earlyReturnPath.setExpectedOutcomeHint("early-return");

    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-success");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");

    method.setRepresentativePaths(List.of(earlyReturnPath, successPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection).contains("orderId != null");
    assertThat(preconditionSection).contains("isValidPaymentMethod(paymentMethod)");
    assertThat(preconditionSection).doesNotContain("!gatewayResponse.isSuccess()");
    assertThat(preconditionSection).doesNotContain("existingPayment.isPresent()");
    assertThat(preconditionSection).doesNotContain("getStatus().equals");

    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-success");
    assertThat(normalFlowSection).doesNotContain("path-early");

    String errorBoundarySection = extractSection(result, "#### 3.1.5 異常・境界", "#### 3.1.6");
    assertThat(errorBoundarySection).contains("path-early");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsExcludeInternalLoopConditionsOnNoArgMethod() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerFlowSample");

    MethodInfo method = new MethodInfo();
    method.setName("getActiveCustomers");
    method.setSignature("public java.util.List<Customer> getActiveCustomers()");
    method.setVisibility("public");

    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-success");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");

    RepresentativePath boundaryPath = new RepresentativePath();
    boundaryPath.setId("path-boundary");
    boundaryPath.setDescription("Boundary condition customer.getBalance() > 0");
    boundaryPath.setExpectedOutcomeHint("boundary");
    boundaryPath.setRequiredConditions(List.of("customer.getBalance() > 0"));

    method.setRepresentativePaths(List.of(successPath, boundaryPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection).contains("- なし");
    assertThat(preconditionSection).doesNotContain("customer.getBalance() > 0");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsExcludeComplexMatchConditionsFromPath() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerLookupSample");

    MethodInfo method = new MethodInfo();
    method.setName("findCustomerById");
    method.setSignature("public Customer findCustomerById(String id)");
    method.setVisibility("public");

    GuardSummary idGuard = new GuardSummary();
    idGuard.setType(GuardType.FAIL_GUARD);
    idGuard.setCondition("id == null || id.isEmpty()");

    GuardSummary matchGuard = new GuardSummary();
    matchGuard.setType(GuardType.LEGACY);
    matchGuard.setCondition("customer.getId().equals(id)");

    BranchSummary branchSummary = new BranchSummary();
    branchSummary.setGuards(List.of(idGuard, matchGuard));
    method.setBranchSummary(branchSummary);

    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-success");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");
    successPath.setRequiredConditions(List.of("customer.getId().equals(id)"));

    method.setRepresentativePaths(List.of(successPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection).contains("id != null");
    assertThat(preconditionSection).contains("!id.isEmpty()");
    assertThat(preconditionSection).doesNotContain("customer.getId().equals(id)");
  }

  @Test
  void generateDetailedDocument_fallbackNormalFlowExcludesFailureRepresentativePaths() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.InvoiceFlowSample");

    MethodInfo method = new MethodInfo();
    method.setName("calculate");
    method.setSignature("public void calculate(String id)");
    method.setVisibility("public");

    RepresentativePath pathFailure = new RepresentativePath();
    pathFailure.setId("path-failure");
    pathFailure.setDescription("Validation failure: id is invalid");
    pathFailure.setExpectedOutcomeHint("failure");
    pathFailure.setRequiredConditions(List.of("id == null"));

    RepresentativePath pathSuccess = new RepresentativePath();
    pathSuccess.setId("path-success");
    pathSuccess.setDescription("Main success path");
    pathSuccess.setExpectedOutcomeHint("success");

    method.setRepresentativePaths(List.of(pathFailure, pathSuccess));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-success");
    assertThat(normalFlowSection).doesNotContain("path-failure");

    String errorBoundarySection = extractSection(result, "#### 3.1.5 異常・境界", "#### 3.1.6");
    assertThat(errorBoundarySection).contains("path-failure");
  }

  @Test
  void generateDetailedDocument_fallbackNormalFlowExcludesBoundaryRepresentativePaths() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderFlowSample");

    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("public void process(String id)");
    method.setVisibility("public");

    RepresentativePath pathSuccess = new RepresentativePath();
    pathSuccess.setId("path-success");
    pathSuccess.setDescription("Main success path");
    pathSuccess.setExpectedOutcomeHint("success");

    RepresentativePath pathBoundary = new RepresentativePath();
    pathBoundary.setId("path-boundary");
    pathBoundary.setDescription("Boundary condition amount > 0");
    pathBoundary.setExpectedOutcomeHint("boundary");

    method.setRepresentativePaths(List.of(pathSuccess, pathBoundary));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-success");
    assertThat(normalFlowSection).doesNotContain("path-boundary");

    String errorBoundarySection = extractSection(result, "#### 3.1.5 異常・境界", "#### 3.1.6");
    assertThat(errorBoundarySection).contains("path-boundary");
  }

  @Test
  void generateDetailedDocument_fallbackSkipsPositiveNonNullBoundaryPath() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.PaymentSample");

    MethodInfo method = new MethodInfo();
    method.setName("refund");
    method.setSignature("public void refund(BigDecimal refundAmount)");
    method.setVisibility("public");

    RepresentativePath pathSuccess = new RepresentativePath();
    pathSuccess.setId("path-success");
    pathSuccess.setDescription("Main success path");
    pathSuccess.setExpectedOutcomeHint("success");

    RepresentativePath pathRefundSpecified = new RepresentativePath();
    pathRefundSpecified.setId("path-refund-specified");
    pathRefundSpecified.setDescription("Boundary condition refundAmount != null");
    pathRefundSpecified.setExpectedOutcomeHint("boundary");
    pathRefundSpecified.setRequiredConditions(List.of("refundAmount != null"));

    method.setRepresentativePaths(List.of(pathSuccess, pathRefundSpecified));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).doesNotContain("path-refund-specified");
    assertThat(postconditionSection).doesNotContain("refundAmount != null");

    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-success");
    assertThat(normalFlowSection).doesNotContain("path-refund-specified");
    assertThat(normalFlowSection).doesNotContain("refundAmount != null");

    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).doesNotContain("path-refund-specified");
    assertThat(testViewpointSection).doesNotContain("refundAmount != null");

    String errorBoundarySection = extractSection(result, "#### 3.1.5 異常・境界", "#### 3.1.6");
    assertThat(errorBoundarySection).doesNotContain("path-refund-specified");
  }

  @Test
  void generateDetailedDocument_fallbackDoesNotDuplicateSwitchFactsWhenRepresentativePathsExist() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.InvoicePolicyService");

    MethodInfo method = new MethodInfo();
    method.setName("resolvePolicy");
    method.setSignature("public String resolvePolicy(String regionCode)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public String resolvePolicy(String regionCode) {
                switch (regionCode) {
                    case "US_NY":
                        return "US";
                    case "EU_DE":
                        return "EU";
                    default:
                        return "GLOBAL";
                }
            }
            """);

    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-1");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");

    RepresentativePath switchUsNyPath = new RepresentativePath();
    switchUsNyPath.setId("path-2");
    switchUsNyPath.setDescription("Switch case regionCode=\"US_NY\"");
    switchUsNyPath.setExpectedOutcomeHint("case-\"US_NY\"");
    switchUsNyPath.setRequiredConditions(List.of("regionCode == \"US_NY\""));

    RepresentativePath switchEuDePath = new RepresentativePath();
    switchEuDePath.setId("path-3");
    switchEuDePath.setDescription("Switch case regionCode=\"EU_DE\"");
    switchEuDePath.setExpectedOutcomeHint("case-\"EU_DE\"");
    switchEuDePath.setRequiredConditions(List.of("regionCode == \"EU_DE\""));

    RepresentativePath switchDefaultPath = new RepresentativePath();
    switchDefaultPath.setId("path-4");
    switchDefaultPath.setDescription("Switch case regionCode=default");
    switchDefaultPath.setExpectedOutcomeHint("case-default");
    switchDefaultPath.setRequiredConditions(List.of("regionCode == default"));

    method.setRepresentativePaths(
        List.of(successPath, switchUsNyPath, switchEuDePath, switchDefaultPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).contains("path-2");
    assertThat(postconditionSection).contains("path-3");
    assertThat(postconditionSection).contains("path-4");
    assertThat(postconditionSection).doesNotContain("switch-regioncode-");

    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).contains("path-2");
    assertThat(testViewpointSection).contains("path-3");
    assertThat(testViewpointSection).contains("path-4");
    assertThat(testViewpointSection).doesNotContain("switch-regioncode-");
  }

  @Test
  void generateDetailedDocument_fallbackRelabelsFailureFactoryMainSuccessPath() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentResult");

    MethodInfo method = new MethodInfo();
    method.setName("failure");
    method.setSignature("public static PaymentResult failure(String errorMessage)");
    method.setVisibility("public");

    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    path.setDescription("Main success path");
    path.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(path));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).contains("path-1: 主返却パス");
    assertThat(postconditionSection).doesNotContain("Main success path");

    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("[path-1] 主返却パス -> failure結果オブジェクトを返却");
    assertThat(normalFlowSection).doesNotContain("Main success path");

    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).contains("path-1: 主返却パス");
    assertThat(testViewpointSection).doesNotContain("Main success path");
  }

  @Test
  void generateDetailedDocument_fallbackTestViewpointsCoverAllRepresentativePaths() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.PathCoverageSample");

    MethodInfo method = new MethodInfo();
    method.setName("coverPaths");
    method.setSignature("public void coverPaths(String orderId)");
    method.setVisibility("public");

    List<RepresentativePath> paths = new ArrayList<>();
    for (int i = 1; i <= 8; i++) {
      RepresentativePath path = new RepresentativePath();
      path.setId("path-" + i);
      path.setDescription("Main success path " + i);
      path.setExpectedOutcomeHint("success");
      paths.add(path);
    }
    method.setRepresentativePaths(paths);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    for (int i = 1; i <= 8; i++) {
      assertThat(testViewpointSection).contains("path-" + i);
    }
  }

  @Test
  void generateDetailedDocument_fallbackDependenciesDeduplicateCallsAndSkipImplicitConstructors() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.ComplexInvoiceService");

    MethodInfo method = new MethodInfo();
    method.setName("calculateInvoice");
    method.setSignature("public void calculateInvoice()");
    method.setVisibility("public");
    method.setCalledMethods(
        List.of(
            "com.example.legacy.ComplexInvoiceService$CalculationResult#com.example.legacy.ComplexInvoiceService$CalculationResult()",
            "com.example.legacy.ComplexInvoiceService.CalculationResult#CalculationResult()",
            "com.google.common.base.Preconditions#checkArgument(boolean, java.lang.Object)",
            "com.google.common.base.Preconditions#checkArgument(boolean,java.lang.Object)",
            "java.util.List#add(E)",
            "java.util.List#add(java.lang.Object)",
            "java.lang.Class#getMethod(java.lang.String, java.lang.Class<?>...)",
            "java.lang.Class#getMethod(java.lang.String, java.lang.Class[])",
            "java.math.BigDecimal#BigDecimal(java.lang.String)"));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String dependencySection = extractSection(result, "#### 3.1.6 依存呼び出し", "#### 3.1.7");
    assertThat(dependencySection)
        .contains("com.google.common.base.Preconditions#checkArgument(boolean, java.lang.Object)");
    assertThat(dependencySection).doesNotContain("checkArgument(boolean,java.lang.Object)");
    assertThat(dependencySection).contains("java.util.List#add(E)");
    assertThat(dependencySection).doesNotContain("java.util.List#add(java.lang.Object)");
    assertThat(dependencySection)
        .contains("java.lang.Class#getMethod(java.lang.String, java.lang.Class<?>...)");
    assertThat(dependencySection)
        .doesNotContain("java.lang.Class#getMethod(java.lang.String, java.lang.Class[])");
    assertThat(dependencySection).contains("java.math.BigDecimal#BigDecimal(java.lang.String)");
    assertThat(dependencySection).doesNotContain("CalculationResult#CalculationResult()");
    assertThat(dependencySection).doesNotContain("CalculationResult#com.example.legacy");
  }

  @Test
  void generateDetailedDocument_fallbackIncludesFieldInventoryInExternalSpec() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.CustomerProfile");

    FieldInfo field = new FieldInfo();
    field.setName("customerId");
    field.setType("String");
    field.setVisibility("private");
    classInfo.setFields(List.of(field));

    MethodInfo method = new MethodInfo();
    method.setName("loadProfile");
    method.setSignature("public void loadProfile(String id)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String externalSection = extractSection(result, "## 2. クラス外部仕様", "## 3.");
    assertThat(externalSection).contains("- フィールド一覧:");
    assertThat(externalSection).contains("- `customerId`: String (非公開)");
  }

  @Test
  void generateDetailedDocument_normalizesPackageAndFilePathFormatting() {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: com.example
        - ファイルパス: src/main/java/com/example/Sample.java
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        #### 3.1.5 異常・境界
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("- パッケージ: `com.example`");
    assertThat(result).contains("- ファイルパス: `src/main/java/com/example/Sample.java`");
  }

  @Test
  void generateDetailedDocument_normalizesClassTypeLabelToCanonicalMetadata() {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラスの種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        #### 3.1.5 異常・境界
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("- クラス種別: 通常クラス");
    assertThat(result).doesNotContain("- クラスの種別:");
  }

  @Test
  void generateDetailedDocument_normalizesStandaloneNoneToBulletInSections4To6() {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        #### 3.1.5 異常・境界
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        ## 4. 要注意事項
        なし
        ## 5. 改善提案（任意）
        なし
        ## 6. 未確定事項（解析情報不足）
        なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("## 4. 要注意事項\n- なし");
    assertThat(result).contains("## 5. 改善提案（任意）\n- なし");
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）\n- なし");
    assertThat(result).doesNotContain("## 4. 要注意事項\nなし");
    assertThat(result).doesNotContain("## 5. 改善提案（任意）\nなし");
    assertThat(result).doesNotContain("## 6. 未確定事項（解析情報不足）\nなし");
  }

  @Test
  void generateDetailedDocument_normalizesOpenQuestionsNoneSentenceToBullet() {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        #### 3.1.5 異常・境界
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        メソッドが存在しないため、現時点で未確定事項はなし。
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）\n- なし");
    assertThat(result).doesNotContain("現時点で未確定事項はなし");
  }

  @Test
  void generate_addsMethodLevelOpenQuestionWhenExcerptGapExistsInMainSections() throws Exception {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        - サンプルクラスの挙動を記述する。
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力/出力: `public void doWork()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - Details beyond the provided excerpt are not fully available.
        #### 3.1.4 正常フロー
        - 処理を実行する。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public void doWork() {
                // no-op
            }
            """);
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-analysis-gap-open-question");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(outputDir.resolve("src/main/java/com/example/Sample_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）");
    assertThat(result).contains("メソッド `doWork`");
    assertThat(result).doesNotContain("## 6. 未確定事項（解析情報不足）\n- なし");
  }

  @Test
  void generate_addsMethodLevelOpenQuestionWhenSourceExcerptShownVariantExists() throws Exception {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        - サンプルクラスの挙動を記述する。
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力/出力: `public void doWork()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - A loop guard indicates break (the exact downstream effects are not fully shown in the provided source excerpt)
        #### 3.1.4 正常フロー
        - 処理を実行する。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public void doWork() {
                // no-op
            }
            """);
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-analysis-gap-open-question-shown");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(outputDir.resolve("src/main/java/com/example/Sample_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）");
    assertThat(result).contains("メソッド `doWork`");
    assertThat(result).doesNotContain("## 6. 未確定事項（解析情報不足）\n- なし");
  }

  @Test
  void generateDetailedDocument_retriesWhenMethodlessClassHasNonNoneOpenQuestion() {
    String invalidResponse =
        """
        # CalculationResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `CalculationResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/ComplexInvoiceService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        - メソッドなし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - メソッドが存在しないため、`processCustomer` の実在確認が必要。
        """;
    String validResponse =
        """
        # CalculationResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `CalculationResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/ComplexInvoiceService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        - メソッドなし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.CalculationResult");
    classInfo.setFilePath("src/main/java/com/example/legacy/ComplexInvoiceService.java");
    classInfo.setMethods(List.of());

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("メソッドなしクラスの未確定事項");
    assertThat(result).contains("## 6. 未確定事項（解析情報不足）\n- なし");
    assertThat(result).doesNotContain("processCustomer");
  }

  @Test
  void generateDetailedDocument_retriesWhenExternalSpecMetadataIsMissing() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: なし
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - なし
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: なし
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - なし
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("クラス外部仕様に必須項目が不足しています");
    assertThat(result).contains("- クラス名: `Sample`");
    assertThat(result).contains("- 継承: なし");
    assertThat(result).contains("- 実装インターフェース: なし");
  }

  @Test
  void generateDetailedDocument_retriesWhenPreconditionsAreVague() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderIdが有効であること。
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `java.util.Objects#requireNonNull(java.lang.Object)`
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        - !orderId.isBlank()
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `java.util.Objects#requireNonNull(java.lang.Object)`
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("事前条件が抽象的です");
    assertThat(result).contains("orderId != null");
    assertThat(result).doesNotContain("有効であること");
  }

  @Test
  void generateDetailedDocument_retriesWhenPreconditionsContainFailureSideChecks() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        - !gatewayResponse.isSuccess()
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-2] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        - `com.example.OrderRepository#findById(java.lang.String)`
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Main success path` の結果（success）を確認する。
        - 分岐 `path-2: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-2] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        - `com.example.OrderRepository#findById(java.lang.String)`
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Main success path` の結果（success）を確認する。
        - 分岐 `path-2: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("事前条件に失敗系判定が混在しています");
    assertThat(result).doesNotContain("!gatewayResponse.isSuccess()");
    assertThat(result).contains("orderId != null");
  }

  @Test
  void generateDetailedDocument_retriesWhenFailureFactoryPostconditionUsesSuccessWording() {
    String invalidResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 failure
        #### 3.1.1 入出力
        - 入力: `String errorMessage`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 代表パス[path-1] の期待結果: success
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 代表パス `path-1` の結果（success）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 failure
        #### 3.1.1 入出力
        - 入力: `String errorMessage`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 代表パス[path-1] の期待結果: failure結果オブジェクトを返却
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> failure結果オブジェクトを返却
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 代表パス `path-1` の結果（failure結果オブジェクトを返却）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentResult");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo method = new MethodInfo();
    method.setName("failure");
    method.setSignature("public static PaymentResult failure(String errorMessage)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("failure系ファクトリの事後条件が success 表現");
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).contains("failure結果オブジェクトを返却");
    assertThat(postconditionSection).doesNotContain("期待結果: success");
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).doesNotContain("-> success");
    String testViewpointSection = extractSection(result, "#### 3.1.7 テスト観点", "## 4.");
    assertThat(testViewpointSection).doesNotContain("success");
  }

  @Test
  void generateDetailedDocument_retriesWhenFailureFactoryNormalFlowUsesSuccessWording() {
    String invalidResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 failure
        #### 3.1.1 入出力
        - 入力: `String errorMessage`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 代表パス[path-1] の期待結果: failure結果オブジェクトを返却
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 代表パス `path-1` の結果（failure結果オブジェクトを返却）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 failure
        #### 3.1.1 入出力
        - 入力: `String errorMessage`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 代表パス[path-1] の期待結果: failure結果オブジェクトを返却
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> failure結果オブジェクトを返却
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 代表パス `path-1` の結果（failure結果オブジェクトを返却）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentResult");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo method = new MethodInfo();
    method.setName("failure");
    method.setSignature("public static PaymentResult failure(String errorMessage)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("failure系ファクトリの正常フローが success 表現");
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).doesNotContain("-> success");
  }

  @Test
  void
      generateDetailedDocument_retriesWhenPreconditionsAssertUnsupportedNonNullWithoutSourceEvidence() {
    String invalidResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 success
        #### 3.1.1 入出力
        - 入力: `String transactionId`, `BigDecimal amount`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - `transactionId`はnullではないこと。
        - `amount`はnullではないこと。
        #### 3.1.3 事後条件
        - `PaymentResult`インスタンスを返す。
        #### 3.1.4 正常フロー
        - `PaymentResult`を生成して返す。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `PaymentResult#PaymentResult(boolean, java.lang.String, java.math.BigDecimal, java.lang.String)`
        #### 3.1.7 テスト観点
        - 正常系の戻り値を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # PaymentResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 success
        #### 3.1.1 入出力
        - 入力: `String transactionId`, `BigDecimal amount`
        - 出力: `PaymentResult`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - `PaymentResult`インスタンスを返す。
        #### 3.1.4 正常フロー
        - `PaymentResult`を生成して返す。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `PaymentResult#PaymentResult(boolean, java.lang.String, java.math.BigDecimal, java.lang.String)`
        #### 3.1.7 テスト観点
        - 正常系の戻り値を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentResult");
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo method = new MethodInfo();
    method.setName("success");
    method.setSignature(
        "public static PaymentResult success(String transactionId, BigDecimal amount)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public static PaymentResult success(String transactionId, BigDecimal amount) {
                return new PaymentResult(true, transactionId, amount, null);
            }
            """);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("null 非許容断定");
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection).contains("- なし");
    assertThat(preconditionSection).doesNotContain("nullではない");
  }

  @Test
  void
      generateDetailedDocument_retriesWhenNoArgPreconditionsContainReflectionExistenceConstraints() {
    String invalidResponse =
        """
        # DynamicTestPatterns 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicTestPatterns`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/DynamicTestPatterns.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 loadClassByName
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - クラス`com.example.legacy.CustomerService`が存在すること。
        #### 3.1.3 事後条件
        - クラスインスタンスを返す。
        #### 3.1.4 正常フロー
        - `Class.forName`でクラスをロードする。
        #### 3.1.5 異常・境界
        - クラス解決に失敗した場合は例外を送出する。
        #### 3.1.6 依存呼び出し
        - `java.lang.Class#forName(java.lang.String)`
        #### 3.1.7 テスト観点
        - クラス解決失敗時の例外を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # DynamicTestPatterns 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `DynamicTestPatterns`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/DynamicTestPatterns.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 loadClassByName
        #### 3.1.1 入出力
        - 入力: なし
        - 出力: `Object`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - クラスインスタンスを返す。
        #### 3.1.4 正常フロー
        - `Class.forName`でクラスをロードする。
        #### 3.1.5 異常・境界
        - クラス解決に失敗した場合は例外を送出する。
        #### 3.1.6 依存呼び出し
        - `java.lang.Class#forName(java.lang.String)`
        #### 3.1.7 テスト観点
        - クラス解決失敗時の例外を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.DynamicTestPatterns");
    classInfo.setFilePath("src/main/java/com/example/legacy/DynamicTestPatterns.java");

    MethodInfo method = new MethodInfo();
    method.setName("loadClassByName");
    method.setSignature("public Object loadClassByName() throws Exception");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public Object loadClassByName() throws Exception {
                Class<?> clazz = Class.forName("com.example.legacy.CustomerService");
                return clazz.getDeclaredConstructor().newInstance();
            }
            """);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("引数なしメソッドの事前条件");
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection).contains("- なし");
    assertThat(preconditionSection).doesNotContain("存在すること");
  }

  @Test
  void generateDetailedDocument_retriesWhenNormalFlowContainsEarlyReturn() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - [path-1] Early return path -> early-return
        - [path-2] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-3] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        - `com.example.OrderRepository#findById(java.lang.String)`
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Early return path` の結果（early-return）を確認する。
        - 分岐 `path-2: Main success path` の結果（success）を確認する。
        - 分岐 `path-3: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - [path-2] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-1] Early return path -> early-return
        - [path-3] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        - `com.example.OrderRepository#findById(java.lang.String)`
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Early return path` の結果（early-return）を確認する。
        - 分岐 `path-2: Main success path` の結果（success）を確認する。
        - 分岐 `path-3: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("正常フローに early-return/failure/boundary が混在しています");
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("path-2");
    assertThat(normalFlowSection).doesNotContain("path-1");
  }

  @Test
  void generateDetailedDocument_retriesWhenDependenciesContainAmbiguousPlaceholder() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - 他
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - `com.example.OrderRepository#findById(java.lang.String)`
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("依存呼び出しに曖昧なプレースホルダがあります");
    assertThat(result).doesNotContain("\n- 他\n");
    assertThat(result).contains("OrderRepository#findById");
  }

  @Test
  void generateDetailedDocument_normalizesDependencyReferencesToBackticks() {
    String llmResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        - 入力: `String orderId`
        - 出力: なし
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 実行される
        #### 3.1.4 正常フロー
        - 実行する
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - OrderRepository#findById
        - PaymentGateway#charge(java.lang.String)
        #### 3.1.7 テスト観点
        - 実行確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    classInfo.setFilePath("src/main/java/com/example/Sample.java");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("- `OrderRepository#findById`");
    assertThat(result).contains("- `PaymentGateway#charge(java.lang.String)`");
    assertThat(result).doesNotContain("\n- OrderRepository#findById\n");
  }

  @Test
  void generateDetailedDocument_retriesWhenNormalAndErrorShareSamePathId() {
    String invalidResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-1] Main success path -> success
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Main success path` の結果（success）を確認する。
        - 分岐 `path-2: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # Sample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `Sample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/Sample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-2] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Main success path` の結果（success）を確認する。
        - 分岐 `path-2: Validation failure` の結果（failure）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Sample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("正常フローと異常・境界で同一代表パスが重複しています");
    assertThat(result).contains("[path-2] Validation failure -> failure");
  }

  @Test
  void generateDetailedDocument_retriesWhenPathOutcomeDiffersBetweenPostconditionsAndNormalFlow() {
    String invalidResponse =
        """
        # RefundSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `RefundSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/RefundSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 refund
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        - 分岐 `path-7: Boundary condition refundAmount != null` の結果: boundary
        #### 3.1.4 正常フロー
        - [path-7] refundAmount != null -> success
        #### 3.1.5 異常・境界
        - 解析情報なし
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        - 分岐 `path-7: refundAmount != null` の結果（success）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # RefundSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `RefundSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/RefundSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 refund
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        - 分岐 `path-7: refundAmount != null` の結果: success
        #### 3.1.4 正常フロー
        - [path-7] refundAmount != null -> success
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        - 分岐 `path-7: refundAmount != null` の結果（success）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.RefundSample");
    MethodInfo method = new MethodInfo();
    method.setName("refund");
    method.setSignature("public void refund(BigDecimal refundAmount)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("同一代表パスの結果が事後条件と正常フローで不一致です");
    assertThat(result).contains("分岐 `path-7: refundAmount != null` の結果: success");
  }

  @Test
  void generateDetailedDocument_repairsTestViewpointsWhenRepresentativePathsAreMissing()
      throws Exception {
    String invalidResponse =
        """
        # CoverageSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `CoverageSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/CoverageSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 doWork
        #### 3.1.1 入出力
        #### 3.1.2 事前条件
        #### 3.1.3 事後条件
        - 分岐 `path-1: Main success path` の結果: success
        - 分岐 `path-2: Validation failure` の結果: failure
        #### 3.1.4 正常フロー
        - [path-1] Main success path -> success
        #### 3.1.5 異常・境界
        - [path-2] Validation failure -> failure
        #### 3.1.6 依存呼び出し
        #### 3.1.7 テスト観点
        - 分岐 `path-1: Main success path` の結果（success）を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CoverageSample");
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature("public void doWork()");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-path-coverage-repair");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result = Files.readString(outputDir.resolve("com_example_CoverageSample_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    assertThat(client.invocationCount).isEqualTo(1);
    assertThat(result).contains("分岐 `path-1: Main success path` の結果（success）を確認する。");
    assertThat(result).contains("分岐 `path-2: Validation failure` の結果（failure）を確認する。");
  }

  @Test
  void generateDetailedDocument_retriesWhenNestedMetadataIsInconsistent() {
    String invalidResponse =
        """
        # PaymentGateway 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentGateway`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        - ネストクラス: あり
        - 親クラス: なし
        ## 3. メソッド仕様
        ### 3.1 charge
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # PaymentGateway 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `PaymentGateway`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        - ネストクラス: あり
        - 親クラス: PaymentService
        ## 3. メソッド仕様
        ### 3.1 charge
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.PaymentGateway");
    classInfo.setNestedClass(true);
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo method = new MethodInfo();
    method.setName("charge");
    method.setSignature("public void charge(String customerId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("親クラスが未設定");
    assertThat(result).contains("親クラス: PaymentService");
    assertThat(result).doesNotContain("親クラス: なし");
  }

  @Test
  void generateDetailedDocument_retriesWhenOpenQuestionsContradictNestedFacts() {
    String invalidResponse =
        """
        # RefundResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `RefundResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        - ネストクラス: あり
        - 親クラス: PaymentService
        ## 3. メソッド仕様
        ### 3.1 success
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - RefundResultクラスがPaymentServiceクラス内のネストクラスであるかどうかは未確定。
        """;
    String validResponse =
        """
        # RefundResult 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `RefundResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        - ネストクラス: あり
        - 親クラス: PaymentService
        ## 3. メソッド仕様
        ### 3.1 success
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.PaymentService.RefundResult");
    classInfo.setNestedClass(true);
    classInfo.setFilePath("src/main/java/com/example/legacy/PaymentService.java");

    MethodInfo method = new MethodInfo();
    method.setName("success");
    method.setSignature("public static RefundResult success(String id, BigDecimal amount)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("未確定事項にネスト属性の矛盾記述があります");
    assertThat(result).doesNotContain("ネストクラスであるかどうかは未確定");
  }

  @Test
  void generateDetailedDocument_fallbackPreconditionsAddImplicitNonNullFromSourceDereference() {
    String invalidResponse =
        """
        # Broken 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 anotherMethod
        #### 3.1.1 入出力
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, invalidResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.InvoiceService");
    classInfo.setFilePath("src/main/java/com/example/InvoiceService.java");

    MethodInfo method = new MethodInfo();
    method.setName("calculateInvoice");
    method.setSignature(
        "public Invoice calculateInvoice(String customerId, java.time.LocalDate processingDate)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public Invoice calculateInvoice(String customerId, java.time.LocalDate processingDate) {
                java.time.LocalDate normalized = processingDate.minusDays(1);
                return new Invoice(customerId, normalized);
            }
            """);
    RepresentativePath successPath = new RepresentativePath();
    successPath.setId("path-success");
    successPath.setDescription("Main success path");
    successPath.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(successPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    String preconditionSection = extractSection(result, "#### 3.1.2 事前条件", "#### 3.1.3");
    assertThat(preconditionSection.toLowerCase(Locale.ROOT)).contains("processingdate != null");
  }

  @Test
  void generateDetailedDocument_retriesWhenSwitchCaseCoverageIsMissing() {
    String invalidResponse =
        """
        # InvoicePolicyService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `InvoicePolicyService`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/InvoicePolicyService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 resolvePolicy
        #### 3.1.1 入出力
        - 入力: `String regionCode`
        - 出力: `String`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - ポリシー文字列を返却する。
        #### 3.1.4 正常フロー
        - 入力に応じてポリシーを決定する。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 戻り値を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # InvoicePolicyService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `InvoicePolicyService`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/InvoicePolicyService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 resolvePolicy
        #### 3.1.1 入出力
        - 入力: `String regionCode`
        - 出力: `String`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - regionCode が `US_NY` の case では `case-"US_NY"` を返却する。
        - regionCode が `EU_DE` の case では `case-"EU_DE"` を返却する。
        - regionCode の default では `default` を返却する。
        #### 3.1.4 正常フロー
        - switch(regionCode) に従って結果を返却する。
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - `US_NY` / `EU_DE` / default の分岐結果を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.InvoicePolicyService");
    classInfo.setFilePath("src/main/java/com/example/InvoicePolicyService.java");

    MethodInfo method = new MethodInfo();
    method.setName("resolvePolicy");
    method.setSignature("public String resolvePolicy(String regionCode)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public String resolvePolicy(String regionCode) {
                switch (regionCode) {
                    case "US_NY":
                        return "US";
                    case "EU_DE":
                        return "EU";
                    default:
                        return "GLOBAL";
                }
            }
            """);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("switch-case の網羅が不足しています");
    assertThat(result).contains("case-\"US_NY\"");
    assertThat(result).contains("case-\"EU_DE\"");
    assertThat(result).contains("default");
  }

  @Test
  void generateDetailedDocument_retriesWhenThrowOnlyMethodUsesEarlyReturnWording() {
    String invalidResponse =
        """
        # CriticalConfigService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `CriticalConfigService`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/CriticalConfigService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 loadCriticalConfig
        #### 3.1.1 入出力
        - 入力: `String path`
        - 出力: `ConfigData`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 代表経路の結果は early-return となる。
        #### 3.1.4 正常フロー
        - 設定ロード処理を実行する。
        #### 3.1.5 異常・境界
        - 例外を送出する可能性がある。
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - early-return の結果を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # CriticalConfigService 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `CriticalConfigService`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/CriticalConfigService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 loadCriticalConfig
        #### 3.1.1 入出力
        - 入力: `String path`
        - 出力: `ConfigData`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 失敗時は例外が送出される。
        #### 3.1.4 正常フロー
        - 設定ロード処理を実行する。
        #### 3.1.5 異常・境界
        - 設定未検出時は例外を送出する。
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 例外送出条件を確認する。
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(invalidResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CriticalConfigService");
    classInfo.setFilePath("src/main/java/com/example/CriticalConfigService.java");

    MethodInfo method = new MethodInfo();
    method.setName("loadCriticalConfig");
    method.setSignature("public ConfigData loadCriticalConfig(String path)");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public ConfigData loadCriticalConfig(String path) {
                throw new IllegalStateException("config not found");
            }
            """);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("early-return 表現は不整合");
    assertThat(result).doesNotContain("early-return");
  }

  @Test
  void generateDetailedDocument_enrichesBlankNormalFlowFromRepresentativePaths() {
    // Improvement 3: When LLM returns blank normal flow but analysis data has paths
    String thinResponse =
        """
        # EnrichSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 process
        #### 3.1.1 入出力
        - 入力: `String input`
        - 出力: `String`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - なし
        #### 3.1.4 正常フロー
        #### 3.1.5 異常・境界
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 動作確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(thinResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.EnrichSample");
    classInfo.setFilePath("src/main/java/com/example/EnrichSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("public String process(String input)");
    method.setVisibility("public");

    RepresentativePath normalPath = new RepresentativePath();
    normalPath.setId("path-1");
    normalPath.setDescription("Main success path");
    normalPath.setExpectedOutcomeHint("success");
    method.setRepresentativePaths(List.of(normalPath));
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    // The normal flow section should NOT contain the generic placeholder since
    // representative paths exist
    String normalFlowSection = extractSection(result, "#### 3.1.4", "#### 3.1.5");
    assertThat(normalFlowSection.strip()).isNotEqualTo("解析情報なし");
    assertThat(normalFlowSection.strip()).isNotEmpty();
  }

  @Test
  void generateDetailedDocument_retriesWhenThinContentDespiteAnalysis() {
    // Improvement 2: Thinness detection – method has branches but fallback-only
    // content
    String thinResponse =
        """
        # ThinSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `ThinSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/ThinSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 compute
        #### 3.1.1 入出力
        - 入力: `int value`
        - 出力: `int`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 結果を返す
        #### 3.1.4 正常フロー
        - 解析情報なし
        #### 3.1.5 異常・境界
        - 解析情報なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 結果確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    String validResponse =
        """
        # ThinSample 詳細設計
        ## 1. 目的と責務（事実）
        ## 2. クラス外部仕様
        - クラス名: `ThinSample`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/ThinSample.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 compute
        #### 3.1.1 入出力
        - 入力: `int value`
        - 出力: `int`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 結果を返す
        #### 3.1.4 正常フロー
        - value > 0 の場合、正の結果を返す
        - value <= 0 の場合、ゼロを返す
        #### 3.1.5 異常・境界
        - value が境界値0の場合を確認
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 結果確認
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(thinResponse, validResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.ThinSample");
    classInfo.setFilePath("src/main/java/com/example/ThinSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("compute");
    method.setSignature("public int compute(int value)");
    method.setVisibility("public");

    // Set up branch summary with >= 2 branches to trigger thinness detection
    BranchSummary branchSummary = new BranchSummary();
    GuardSummary guard1 = new GuardSummary();
    guard1.setCondition("value > 0");
    guard1.setType(GuardType.FAIL_GUARD);
    GuardSummary guard2 = new GuardSummary();
    guard2.setCondition("value <= 0");
    guard2.setType(GuardType.FAIL_GUARD);
    branchSummary.setGuards(List.of(guard1, guard2));
    branchSummary.setSwitches(List.of());
    branchSummary.setPredicates(List.of());
    method.setBranchSummary(branchSummary);
    classInfo.setMethods(List.of(method));

    String result = generator.generateDetailedDocument(classInfo, new Config.LlmConfig());

    // Should retry because thin content was detected despite having branches
    assertThat(client.invocationCount).isEqualTo(2);
    assertThat(client.prompts.get(1)).contains("フォールバックのみ");
    assertThat(result).doesNotContain("解析情報なし");
  }

  @Test
  void generateDetailedDocument_repairsOrderedListAfterFilteringUncertainMethodLines()
      throws Exception {
    String malformedResponse =
        """
        # DynamicSample 詳細設計
        ## 1. 目的と責務（事実）
        - 動的呼び出しを扱う。
        ## 2. クラス外部仕様
        - 概要: reflection sample
        ## 3. メソッド仕様
        ### 3.1 invokeMethod
        #### 3.1.1 入出力
        - 入力/出力: `invokeMethod(Object)`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 戻り値を返す
        #### 3.1.4 正常フロー
        1. Resolve processCustomer by reflection.
        2. Return the resolved Method.
        3. Return invocation result.
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(malformedResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.DynamicSample");
    classInfo.setFilePath("src/main/java/com/example/DynamicSample.java");

    MethodInfo method = new MethodInfo();
    method.setName("invokeMethod");
    method.setSignature("public Object invokeMethod(Object target)");
    method.setVisibility("public");
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.DynamicSample")
                .methodSig("public Object invokeMethod(Object target)")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.8)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-ordered-list-repair");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(outputDir.resolve("src/main/java/com/example/DynamicSample_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    assertThat(client.invocationCount).isEqualTo(1);
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("1. Return the resolved Method.");
    assertThat(normalFlowSection).contains("2. Return invocation result.");
    assertThat(normalFlowSection).doesNotContain("- 2.");
    assertThat(normalFlowSection).doesNotContain("processCustomer");
  }

  @Test
  void generateDetailedDocument_normalizesOrderedListWithoutUncertainMethodFiltering()
      throws Exception {
    String malformedResponse =
        """
        # OrderService 詳細設計
        ## 1. 目的と責務（事実）
        - 受注処理を担う。
        ## 2. クラス外部仕様
        - クラス名: `OrderService`
        - パッケージ: `com.example`
        - ファイルパス: `src/main/java/com/example/OrderService.java`
        - クラス種別: 通常クラス
        - 継承: なし
        - 実装インターフェース: なし
        ## 3. メソッド仕様
        ### 3.1 processOrder
        #### 3.1.1 入出力
        - 入力/出力: `processOrder(String orderId)`
        #### 3.1.2 事前条件
        - orderId != null
        #### 3.1.3 事後条件
        - 処理結果を返す
        #### 3.1.4 正常フロー
        2. Validate order state.
        3. Persist order.
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - 正常系を確認する
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(malformedResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService");
    classInfo.setFilePath("src/main/java/com/example/OrderService.java");

    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public String processOrder(String orderId)");
    method.setVisibility("public");
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-ordered-list-no-uncertain");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(outputDir.resolve("src/main/java/com/example/OrderService_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    assertThat(client.invocationCount).isEqualTo(1);
    String normalFlowSection = extractSection(result, "#### 3.1.4 正常フロー", "#### 3.1.5");
    assertThat(normalFlowSection).contains("1. Validate order state.");
    assertThat(normalFlowSection).contains("2. Persist order.");
    assertThat(normalFlowSection).doesNotContain("3. Persist order.");
  }

  @Test
  void
      generate_structuralRepairUsesFallbackPostconditionsBeforeSignatureFallbackForUncertainDynamicMethod()
          throws Exception {
    String llmResponse =
        """
        # DynamicTestPatterns 詳細設計
        ## 1. 目的と責務（事実）
        - なし
        ## 2. クラス外部仕様
        ## 3. メソッド仕様
        ### 3.1 getMethodByLiteral
        #### 3.1.1 入出力
        - 入力/出力: `public java.lang.reflect.Method getMethodByLiteral()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 解析情報なし
        #### 3.1.4 正常フロー
        - processCustomer を動的解決して結果を返す
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    CapturingLlmClient client = new CapturingLlmClient(llmResponse);
    LlmDocumentGenerator generator = new LlmDocumentGenerator(client, new PromptRedactionService());

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.legacy.DynamicTestPatterns");
    classInfo.setFilePath("src/main/java/com/example/legacy/DynamicTestPatterns.java");

    MethodInfo method = new MethodInfo();
    method.setName("getMethodByLiteral");
    method.setSignature("public java.lang.reflect.Method getMethodByLiteral()");
    method.setVisibility("public");
    method.setSourceCode(
        """
            public java.lang.reflect.Method getMethodByLiteral() throws Exception {
                return CustomerService.class.getMethod("processCustomer", String.class);
            }
            """);
    method.setDynamicResolutions(
        List.of(
            DynamicResolution.builder()
                .classFqn("com.example.legacy.DynamicTestPatterns")
                .methodSig("public java.lang.reflect.Method getMethodByLiteral()")
                .resolvedMethodSig("com.example.legacy.CustomerService#processCustomer(String)")
                .candidates(List.of("com.example.legacy.CustomerService#processCustomer(String)"))
                .subtype(DynamicResolution.METHOD_RESOLVE)
                .confidence(0.7)
                .trustLevel(TrustLevel.MEDIUM)
                .evidence(java.util.Map.of("verified", "false"))
                .build()));
    RepresentativePath uncertainPath = new RepresentativePath();
    uncertainPath.setId("path-1");
    uncertainPath.setDescription("Reflective call to processCustomer");
    uncertainPath.setExpectedOutcomeHint("success");
    uncertainPath.setRequiredConditions(List.of("processCustomer method is resolved"));
    method.setRepresentativePaths(List.of(uncertainPath));
    classInfo.setMethods(List.of(method));

    AnalysisResult analysis = new AnalysisResult();
    analysis.setClasses(List.of(classInfo));
    Config config = new Config();
    config.setLlm(new Config.LlmConfig());
    Path outputDir = Files.createTempDirectory("llm-doc-postcondition-fallback");

    int generatedCount = generator.generate(analysis, outputDir, config);
    String result =
        Files.readString(
            outputDir.resolve("src/main/java/com/example/legacy/DynamicTestPatterns_detail.md"));

    assertThat(generatedCount).isEqualTo(1);
    String postconditionSection = extractSection(result, "#### 3.1.3 事後条件", "#### 3.1.4");
    assertThat(postconditionSection).contains("動的解決候補の実在確認後に結果を確定する。");
    assertThat(postconditionSection).doesNotContain("シグネチャに準拠");
  }

  private String extractSection(String document, String startHeading, String endHeading) {
    int start = document.indexOf(startHeading);
    if (start < 0) {
      return "";
    }
    int sectionStart = document.indexOf('\n', start);
    if (sectionStart < 0) {
      return "";
    }
    int end = document.indexOf(endHeading, sectionStart);
    if (end < 0) {
      end = document.length();
    }
    return document.substring(sectionStart + 1, end);
  }

  private static final class CapturingLlmClient implements LlmClientPort {
    private final List<String> responses;
    private final List<String> prompts = new ArrayList<>();
    private int invocationCount;
    private String lastPrompt;

    private CapturingLlmClient(String... responses) {
      this.responses = List.of(responses);
    }

    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      this.lastPrompt = prompt;
      this.prompts.add(prompt);
      this.invocationCount++;
      if (responses.isEmpty()) {
        return "";
      }
      int index = Math.min(invocationCount - 1, responses.size() - 1);
      return responses.get(index);
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("test", Set.of(), Optional.empty());
    }
  }

  private static final class AlwaysFailingLlmClient implements LlmClientPort {
    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      throw new IllegalStateException("simulated llm outage");
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("test", Set.of(), Optional.empty());
    }
  }
}
