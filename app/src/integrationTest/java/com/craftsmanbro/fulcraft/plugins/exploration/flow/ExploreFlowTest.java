package com.craftsmanbro.fulcraft.plugins.exploration.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class ExploreFlowTest {

  @TempDir Path tempDir;

  @Test
  void generate_writesArtifactsAndReturnsSummary() throws Exception {
    ExploreFlow flow = new ExploreFlow();
    RunContext context = new RunContext(tempDir, baseConfig(), "run-explore");
    AnalysisResult analysisResult = sampleAnalysisResult();

    ExploreFlow.Result result = flow.generate(analysisResult, context);

    assertThat(result.outputDirectory()).exists();
    assertThat(result.snapshotFile()).exists();
    assertThat(result.indexFile()).exists();
    assertThat(result.classCount()).isEqualTo(2);
    assertThat(result.packageCount()).isEqualTo(2);
    assertThat(result.methodCount()).isEqualTo(2);
    String html = Files.readString(result.indexFile());
    String snapshot = Files.readString(result.snapshotFile());
    assertThat(html).contains("EXPLORE 3D Metro").contains("id=\"metro-canvas\"");
    assertThat(snapshot)
        .contains("\"nodes\"")
        .contains("\"edges\"")
        .contains("\"from\" : \"com.example.Service\"")
        .contains("\"to\" : \"com.example.helper.Helper\"");
  }

  @Test
  void generate_filtersNestedAndTestClasses_andKeepsServiceUtilityRoles() throws Exception {
    ExploreFlow flow = new ExploreFlow();
    RunContext context = new RunContext(tempDir, baseConfig(), "run-explore-roles");
    AnalysisResult analysisResult = new AnalysisResult("test-project");

    ClassInfo legacyClass = new ClassInfo();
    legacyClass.setFqn("com.example.legacy.LegacyFacade");
    legacyClass.setFilePath("src/main/java/com/example/legacy/LegacyFacade.java");
    MethodInfo legacyMethod = new MethodInfo();
    legacyMethod.setName("handle");
    legacyMethod.setCyclomaticComplexity(3);
    legacyClass.setMethods(List.of(legacyMethod));

    ClassInfo serviceClass = new ClassInfo();
    serviceClass.setFqn("com.example.legacy.service.OrderService");
    serviceClass.setFilePath("src/main/java/com/example/legacy/service/OrderService.java");
    MethodInfo serviceMethod = new MethodInfo();
    serviceMethod.setName("process");
    serviceMethod.setCyclomaticComplexity(4);
    serviceClass.setMethods(List.of(serviceMethod));

    ClassInfo utilityClass = new ClassInfo();
    utilityClass.setFqn("com.example.legacy.util.StringUtil");
    utilityClass.setFilePath("src/main/java/com/example/legacy/util/StringUtil.java");
    MethodInfo utilityMethod = new MethodInfo();
    utilityMethod.setName("trim");
    utilityMethod.setCyclomaticComplexity(2);
    utilityClass.setMethods(List.of(utilityMethod));

    ClassInfo nestedClass = new ClassInfo();
    nestedClass.setFqn("com.example.legacy.LegacyFacade.InnerState");
    nestedClass.setNestedClass(true);
    nestedClass.setFilePath("src/main/java/com/example/legacy/LegacyFacade.java");
    MethodInfo nestedMethod = new MethodInfo();
    nestedMethod.setName("nested");
    nestedMethod.setCyclomaticComplexity(1);
    nestedClass.setMethods(List.of(nestedMethod));

    ClassInfo testClass = new ClassInfo();
    testClass.setFqn("com.example.legacy.service.OrderServiceTest");
    testClass.setFilePath("src/test/java/com/example/legacy/service/OrderServiceTest.java");
    MethodInfo testMethod = new MethodInfo();
    testMethod.setName("testProcess");
    testMethod.setCyclomaticComplexity(1);
    testClass.setMethods(List.of(testMethod));

    analysisResult.setClasses(
        List.of(legacyClass, serviceClass, utilityClass, nestedClass, testClass));

    ExploreFlow.Result result = flow.generate(analysisResult, context);

    assertThat(result.classCount()).isEqualTo(3);
    assertThat(result.packageCount()).isEqualTo(3);

    String snapshot = Files.readString(result.snapshotFile());
    assertThat(snapshot)
        .contains("\"id\" : \"com.example.legacy.LegacyFacade\"")
        .contains("\"packageRole\" : \"legacy\"")
        .contains("\"id\" : \"com.example.legacy.service.OrderService\"")
        .contains("\"packageRole\" : \"service\"")
        .contains("\"packageCluster\" : \"service\"")
        .contains("\"id\" : \"com.example.legacy.util.StringUtil\"")
        .contains("\"packageRole\" : \"utility\"")
        .contains("\"packageCluster\" : \"util\"")
        .doesNotContain("LegacyFacade.InnerState")
        .doesNotContain("OrderServiceTest");
  }

  @Test
  void generate_resolvesDependenciesAndBuildsLibraryAndLinkData() throws Exception {
    ExploreFlow flow = new ExploreFlow();
    RunContext context = new RunContext(tempDir, baseConfig(), "run-explore-branch-heavy");
    Path runRoot =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).runRoot();
    prepareLinkedArtifacts(runRoot);
    AnalysisResult analysisResult = complexAnalysisResult();

    ExploreFlow.Result result = flow.generate(analysisResult, context);

    JsonNode snapshot = JsonMapperFactory.create().readTree(result.snapshotFile().toFile());
    assertThat(result.classCount()).isEqualTo(12);
    assertThat(result.packageCount()).isEqualTo(9);
    assertThat(result.methodCount()).isGreaterThan(10);
    assertThat(snapshot.path("topComplexity").asText())
        .contains("com.example.alpha.entry.ApiController#handle");

    JsonNode edges = snapshot.path("edges");
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.service.OrderService"))
        .isTrue();
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.data.OrderRepository"))
        .isTrue();
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.entry.OrderService"))
        .isTrue();
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.domain.OrderModel"))
        .isTrue();
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.data.OrderRepository.InnerContract"))
        .isTrue();
    assertThat(
            hasEdge(
                edges,
                "com.example.alpha.entry.ApiController",
                "com.example.alpha.entry.ApiController"))
        .isFalse();

    JsonNode apiNode = nodeById(snapshot.path("nodes"), "com.example.alpha.entry.ApiController");
    assertThat(apiNode).isNotNull();
    assertThat(stringValues(apiNode.path("detailLinks"), "label"))
        .containsExactlyInAnyOrder("Analysis JSON", "Class Detail HTML", "Class Detail Markdown");
    assertThat(stringValues(apiNode.path("externalLibraries"), null))
        .contains("tools", "org.apache", "io.vertx", "guava");

    JsonNode standalone = nodeById(snapshot.path("nodes"), "Standalone");
    assertThat(standalone).isNotNull();
    assertThat(standalone.path("packageName").asText()).isEqualTo("(default)");
    assertThat(standalone.path("packageRole").asText()).isEqualTo("default");
    assertThat(standalone.path("packageCluster").asText()).isEqualTo("default");

    assertThat(stringValues(snapshot.path("nodes"), "packageRole"))
        .contains("service", "utility", "domain", "infra", "core", "default");
    assertThat(stringValues(snapshot.path("externalLibraries"), "name"))
        .contains("tools", "org.apache", "io.vertx", "guava");
    assertThat(stringValues(snapshot.path("reportLinks"), "label"))
        .contains(
            "Analysis Visual Report",
            "Detailed Report (HTML)",
            "Quality Report (Markdown)",
            "Report JSON")
        .doesNotContain("Detailed Report (Markdown)");
  }

  @Test
  void generate_excludesImplicitDefaultConstructorFromMethodCount() throws Exception {
    ExploreFlow flow = new ExploreFlow();
    RunContext context = new RunContext(tempDir, baseConfig(), "run-explore-ctor-filter");
    AnalysisResult analysisResult = new AnalysisResult("test-project");

    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CtorSample");

    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("CtorSample");
    implicitConstructor.setSignature("CtorSample()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    MethodInfo businessMethod = new MethodInfo();
    businessMethod.setName("work");
    businessMethod.setCyclomaticComplexity(4);
    businessMethod.setLoc(8);

    classInfo.setMethods(List.of(implicitConstructor, businessMethod));
    analysisResult.setClasses(List.of(classInfo));

    ExploreFlow.Result result = flow.generate(analysisResult, context);

    assertThat(result.methodCount()).isEqualTo(1);
    JsonNode snapshot = JsonMapperFactory.create().readTree(result.snapshotFile().toFile());
    JsonNode node = nodeById(snapshot.path("nodes"), "com.example.CtorSample");
    assertThat(node).isNotNull();
    assertThat(node.path("methodCount").asInt()).isEqualTo(1);
  }

  @Test
  void generate_throwsIllegalStateWhenEmbeddedJsonSerializationFails() {
    JsonServicePort delegate = new DefaultJsonService();
    JsonServicePort failingService =
        new JsonServicePort() {
          @Override
          public void writeToFile(java.nio.file.Path file, Object value) throws IOException {
            delegate.writeToFile(file, value);
          }

          @Override
          public void writeToFileCompact(java.nio.file.Path file, Object value) throws IOException {
            delegate.writeToFileCompact(file, value);
          }

          @Override
          public <T> T readFromFile(java.nio.file.Path file, Class<T> type) throws IOException {
            return delegate.readFromFile(file, type);
          }

          @Override
          public java.util.LinkedHashMap<String, Object> readMapFromFile(java.nio.file.Path file)
              throws IOException {
            return delegate.readMapFromFile(file);
          }

          @Override
          public java.util.LinkedHashMap<String, Object> readMapFromString(String json)
              throws IOException {
            return delegate.readMapFromString(json);
          }

          @Override
          public String toJson(Object value) throws IOException {
            throw new IOException("boom");
          }

          @Override
          public String toJsonPretty(Object value) throws IOException {
            return delegate.toJsonPretty(value);
          }

          @Override
          public <T> T fromJson(String json, Class<T> type) throws IOException {
            return delegate.fromJson(json, type);
          }

          @Override
          public <T> T convert(Object source, Class<T> type) {
            return delegate.convert(source, type);
          }
        };

    ExploreFlow flow = new ExploreFlow(failingService);
    RunContext context = new RunContext(tempDir, baseConfig(), "run-explore-embed-error");

    assertThatThrownBy(() -> flow.generate(sampleAnalysisResult(), context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to render embedded JSON");
  }

  @Test
  void privateHelpers_coverAdditionalBranchConditions() throws Exception {
    ExploreFlow flow = new ExploreFlow();

    Set<String> classFqns =
        new LinkedHashSet<>(
            List.of(
                "com.acme.Type",
                "com.acme.pkg.Beta",
                "com.acme.pkg.Beta.Inner",
                "com.acme.util.Helper",
                "com.other.util.Helper"));

    assertThat(
            invokeString(
                flow,
                "resolveImportDependency",
                new Class<?>[] {String.class, String.class, Set.class},
                "static com.acme.pkg.Beta.run",
                "com.acme",
                classFqns))
        .isEqualTo("com.acme.pkg.Beta");
    assertThat(
            invokeString(
                flow,
                "resolveImportDependency",
                new Class<?>[] {String.class, String.class, Set.class},
                "static noDot",
                "com.acme",
                classFqns))
        .isNull();
    assertThat(
            invokeString(
                flow, "uniqueSuffixMatch", new Class<?>[] {String.class, Set.class}, "", classFqns))
        .isNull();
    assertThat(
            invokeString(
                flow,
                "uniqueSuffixMatch",
                new Class<?>[] {String.class, Set.class},
                "Missing",
                classFqns))
        .isNull();

    assertThat(
            invokeString(
                flow,
                "sanitizeTypeRef",
                new Class<?>[] {String.class},
                "class com.acme.Type.class"))
        .isEqualTo("com.acme.Type");
    assertThat(invokeString(flow, "sanitizeTypeRef", new Class<?>[] {String.class}, "<>")).isNull();
    assertThat(
            invokeString(
                flow,
                "sanitizeTypeRef",
                new Class<?>[] {String.class},
                "new com.acme.Generic<java.lang.String>[]"))
        .isEqualTo("com.acme.Generic");
    assertThat(
            invokeString(
                flow, "removeGenerics", new Class<?>[] {String.class}, "Map<String,List<Integer>>"))
        .isEqualTo("Map");

    assertThat(invokeString(flow, "normalizeFqn", new Class<?>[] {String.class}, " ")).isNull();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isLikelyTestPath",
                    new Class<?>[] {String.class},
                    "/tmp/project/src/test/java/Foo.java"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isLikelyTestPath",
                    new Class<?>[] {String.class},
                    "src/test/java/Foo.java"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow, "isLikelyTestFqn", new Class<?>[] {String.class}, "com.example.UnitTest"))
        .isTrue();

    assertThat(invokeString(flow, "packageNameOf", new Class<?>[] {String.class}, "MyType.Inner"))
        .isEqualTo("MyType");
    assertThat(
            invokeString(
                flow, "packageNameOf", new Class<?>[] {String.class}, "com..example.Service"))
        .isEqualTo("com.example");
    assertThat(invokeString(flow, "classNameOf", new Class<?>[] {String.class}, "Type."))
        .isEqualTo("Type.");
    assertThat(
            (Integer)
                invokePrivate(flow, "packageDepth", new Class<?>[] {String.class}, "(default)"))
        .isZero();

    assertThat(invokeString(flow, "packageRoleOf", new Class<?>[] {String.class}, ""))
        .isEqualTo("default");
    for (String token :
        List.of(
            "test",
            "tests",
            "spec",
            "fixture",
            "mock",
            "orderservice",
            "application",
            "usecase",
            "util",
            "utils",
            "common",
            "helper",
            "controller",
            "api",
            "web",
            "rest",
            "endpoint",
            "repository",
            "repo",
            "dao",
            "persistence",
            "domain",
            "model",
            "entity",
            "dto",
            "vo",
            "infra",
            "infrastructure",
            "config",
            "bootstrap",
            "legacy",
            "deprecated-module")) {
      assertThat(invokeString(flow, "roleFromPackageToken", new Class<?>[] {String.class}, token))
          .isNotNull();
    }
    assertThat(invokeString(flow, "roleFromPackageToken", new Class<?>[] {String.class}, "other"))
        .isNull();

    Set<String> emptyPrefixInput = new LinkedHashSet<>();
    emptyPrefixInput.add(null);
    emptyPrefixInput.add("");
    emptyPrefixInput.add("(default)");
    assertThat(
            invokeStringList(
                flow, "commonPackagePrefixTokens", new Class<?>[] {Set.class}, emptyPrefixInput))
        .isEmpty();
    assertThat(
            invokeStringList(
                flow,
                "commonPackagePrefixTokens",
                new Class<?>[] {Set.class},
                Set.of("com.alpha.one", "org.beta.two")))
        .isEmpty();
    assertThat(
            invokeStringList(
                flow,
                "commonPackagePrefixTokens",
                new Class<?>[] {Set.class},
                Set.of("com.alpha.beta.one", "com.alpha.beta.two", "com.alpha.beta.three")))
        .containsExactly("com", "alpha", "beta");

    assertThat(
            invokeString(
                flow,
                "packageClusterOf",
                new Class<?>[] {String.class, List.class},
                null,
                List.of()))
        .isEqualTo("default");
    assertThat(
            invokeString(
                flow,
                "packageClusterOf",
                new Class<?>[] {String.class, List.class},
                "com.alpha.beta.gamma",
                List.of("com", "alpha")))
        .isEqualTo("beta");
    assertThat(
            invokeString(
                flow,
                "packageClusterOf",
                new Class<?>[] {String.class, List.class},
                "com.alpha.beta",
                List.of("com", "alpha", "beta")))
        .isEqualTo("beta");
    assertThat(
            invokeString(
                flow,
                "packageClusterOf",
                new Class<?>[] {String.class, List.class},
                "com.alpha",
                List.of("com", "alpha")))
        .isEqualTo("alpha");
    assertThat(
            invokeString(
                flow,
                "packageClusterOf",
                new Class<?>[] {String.class, List.class},
                "solo",
                List.of("solo")))
        .isEqualTo("solo");

    Set<String> internalRoots = new LinkedHashSet<>(List.of("com.acme.pkg", "org.demo"));
    assertThat(
            invokeString(
                flow,
                "normalizeImportType",
                new Class<?>[] {String.class},
                "static com.acme.pkg.Beta.run"))
        .isEqualTo("com.acme.pkg.Beta");
    assertThat(
            invokeString(flow, "normalizeImportType", new Class<?>[] {String.class}, "io.vertx.*"))
        .isEqualTo("io.vertx");
    assertThat(invokeString(flow, "normalizeImportType", new Class<?>[] {String.class}, "   "))
        .isNull();

    assertThat(
            (Boolean)
                invokePrivate(
                    flow, "isJdkOrRuntimeType", new Class<?>[] {String.class}, "javax.ws.rs"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow, "isJdkOrRuntimeType", new Class<?>[] {String.class}, "jakarta.inject"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isJdkOrRuntimeType",
                    new Class<?>[] {String.class},
                    "kotlin.collections"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow, "isJdkOrRuntimeType", new Class<?>[] {String.class}, "sun.misc"))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow, "isJdkOrRuntimeType", new Class<?>[] {String.class}, "com.sun.proxy"))
        .isTrue();

    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isInternalType",
                    new Class<?>[] {String.class, String.class, Set.class, Set.class},
                    "com.acme.pkg.Beta",
                    "com.acme.pkg.beta",
                    classFqns,
                    internalRoots))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isInternalType",
                    new Class<?>[] {String.class, String.class, Set.class, Set.class},
                    "com.acme.pkg",
                    "com.acme.pkg",
                    classFqns,
                    internalRoots))
        .isTrue();
    assertThat(
            (Boolean)
                invokePrivate(
                    flow,
                    "isInternalType",
                    new Class<?>[] {String.class, String.class, Set.class, Set.class},
                    "external.lib.Type",
                    "external.lib.type",
                    classFqns,
                    internalRoots))
        .isFalse();

    assertThat(
            invokeString(
                flow,
                "resolveExternalLibrary",
                new Class<?>[] {String.class, Set.class, Set.class},
                " ",
                classFqns,
                internalRoots))
        .isNull();
    assertThat(
            invokeString(
                flow,
                "resolveExternalLibrary",
                new Class<?>[] {String.class, Set.class, Set.class},
                "java.util.List",
                classFqns,
                internalRoots))
        .isNull();
    assertThat(
            invokeString(
                flow,
                "resolveExternalLibrary",
                new Class<?>[] {String.class, Set.class, Set.class},
                "com.acme.pkg.Beta",
                classFqns,
                internalRoots))
        .isNull();
    assertThat(
            invokeString(
                flow,
                "resolveExternalLibrary",
                new Class<?>[] {String.class, Set.class, Set.class},
                "org.apache.commons.lang3.StringUtils",
                classFqns,
                internalRoots))
        .isEqualTo("org.apache");

    assertThat(
            invokeString(
                flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "net.foo.bar"))
        .isEqualTo("net.foo");
    assertThat(
            invokeString(
                flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "io.vertx.core"))
        .isEqualTo("io.vertx");
    assertThat(
            invokeString(
                flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "dev.langchain.sdk"))
        .isEqualTo("dev.langchain");
    assertThat(
            invokeString(
                flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "edu.umd.findbugs"))
        .isEqualTo("edu.umd");
    assertThat(invokeString(flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "single"))
        .isEqualTo("single");
    assertThat(
            invokeString(
                flow, "externalLibraryKeyOf", new Class<?>[] {String.class}, "vendor.product.lib"))
        .isEqualTo("vendor");

    assertThat(
            invokeString(
                flow,
                "buildAnalysisJsonRelativePath",
                new Class<?>[] {String.class},
                (Object) null))
        .isNull();
    assertThat(
            invokeString(
                flow, "buildAnalysisJsonRelativePath", new Class<?>[] {String.class}, "Standalone"))
        .isEqualTo("../analysis/analysis_Standalone.json");
    assertThat(
            invokeString(
                flow,
                "buildAnalysisJsonRelativePath",
                new Class<?>[] {String.class},
                "com.acme.Type"))
        .isEqualTo("../analysis/com/acme/analysis_Type.json");

    Path runRoot = tempDir.resolve("private-links-run");
    ClassInfo classInfo = classInfo("com.acme.Type", 12, 3);
    ClassInfo noLinksClass = classInfo("com.acme.NoLinks", 8, 2);
    writeFile(runRoot.resolve("analysis/com/acme/analysis_Type.json"), "{}");
    writeFile(runRoot.resolve("docs/src/main/java/com/acme/Type.html"), "<html/>");
    writeFile(runRoot.resolve("docs/src/main/java/com/acme/Type.md"), "# md");

    assertThat(
            (List<?>)
                invokePrivate(
                    flow,
                    "buildClassDetailLinks",
                    new Class<?>[] {Path.class, ClassInfo.class, String.class},
                    runRoot,
                    noLinksClass,
                    null))
        .isEmpty();
    assertThat(
            (List<?>)
                invokePrivate(
                    flow,
                    "buildClassDetailLinks",
                    new Class<?>[] {Path.class, ClassInfo.class, String.class},
                    runRoot,
                    classInfo,
                    "com.acme.Type"))
        .hasSize(3);

    List<Object> links = new ArrayList<>();
    invokePrivate(
        flow,
        "addReportLinkIfExists",
        new Class<?>[] {List.class, Path.class, Path.class, String.class},
        links,
        runRoot.resolve("explore"),
        null,
        "Null target");
    Path fileWithSpace = runRoot.resolve("report/report file.html");
    writeFile(fileWithSpace, "<html/>");
    invokePrivate(
        flow,
        "addReportLinkIfExists",
        new Class<?>[] {List.class, Path.class, Path.class, String.class},
        links,
        runRoot.resolve("explore"),
        fileWithSpace,
        "Spaced report");
    assertThat(links).hasSize(1);
    assertThat(links.getFirst().toString()).contains("report%20file.html");

    assertThat(invokeString(flow, "escapeHtml", new Class<?>[] {String.class}, (Object) null))
        .isEmpty();
    assertThat(invokeString(flow, "escapeHtml", new Class<?>[] {String.class}, "a&<>'\""))
        .isEqualTo("a&amp;&lt;&gt;&#39;&quot;");

    ClassInfo riskClass = classInfo("com.acme.Risk", 220, 14);
    assertThat(
            (Integer)
                invokePrivate(
                    flow,
                    "riskScore",
                    new Class<?>[] {ClassInfo.class, int.class, int.class},
                    riskClass,
                    14,
                    0))
        .isGreaterThanOrEqualTo(8);
  }

  private Config baseConfig() {
    Config config = Config.createDefault();
    config.getProject().setId("test-project");
    return config;
  }

  private AnalysisResult sampleAnalysisResult() {
    AnalysisResult result = new AnalysisResult("test-project");

    ClassInfo serviceClass = new ClassInfo();
    serviceClass.setFqn("com.example.Service");
    MethodInfo sumMethod = new MethodInfo();
    sumMethod.setName("sum");
    sumMethod.setCyclomaticComplexity(4);
    sumMethod.setCalledMethods(List.of("com.example.helper.Helper#parse"));
    serviceClass.setMethods(List.of(sumMethod));

    ClassInfo helperClass = new ClassInfo();
    helperClass.setFqn("com.example.helper.Helper");
    MethodInfo parseMethod = new MethodInfo();
    parseMethod.setName("parse");
    parseMethod.setCyclomaticComplexity(6);
    helperClass.setMethods(List.of(parseMethod));

    result.setClasses(List.of(serviceClass, helperClass));
    return result;
  }

  private AnalysisResult complexAnalysisResult() {
    AnalysisResult result = new AnalysisResult("test-project");

    ClassInfo apiController = new ClassInfo();
    apiController.setFqn("com.example.alpha.entry.ApiController");
    apiController.setFilePath("src/main/java/com/example/alpha/entry/ApiController.java");
    apiController.setLoc(180);
    apiController.setImports(
        List.of(
            "com.example.alpha.service.OrderService",
            "static com.example.alpha.data.OrderRepository.findById",
            "com.example.alpha.service.*",
            "tools.jackson.databind.ObjectMapper",
            "org.apache.commons.lang3.StringUtils",
            "io.vertx.*",
            "Guava",
            "java.util.List",
            "static noDot"));
    apiController.setExtendsTypes(
        new ArrayList<>(List.of(" ", "com.example.alpha.domain.OrderModel.Inner")));
    apiController.setImplementsTypes(
        List.of(
            "OrderService",
            "com.example.alpha.data.OrderRepository$InnerContract",
            "external.pkg.UniqueHelper"));

    MethodInfo handle = new MethodInfo();
    handle.setName("handle");
    handle.setCyclomaticComplexity(18);
    CalledMethodRef resolvedCall = new CalledMethodRef();
    resolvedCall.setResolved("com.example.alpha.service.OrderService#process");
    CalledMethodRef methodRefCall = new CalledMethodRef();
    methodRefCall.setResolved("external.pkg.UniqueHelper::assist");
    CalledMethodRef unknownCall = new CalledMethodRef();
    unknownCall.setResolved("Unknown#call");
    List<CalledMethodRef> calledMethodRefs = new ArrayList<>();
    calledMethodRefs.add(resolvedCall);
    calledMethodRefs.add(methodRefCall);
    calledMethodRefs.add(unknownCall);
    calledMethodRefs.add(null);
    handle.setCalledMethodRefs(calledMethodRefs);

    MethodInfo legacyCalls = new MethodInfo();
    legacyCalls.setName("legacyCalls");
    legacyCalls.setCyclomaticComplexity(5);
    legacyCalls.setCalledMethods(
        List.of(
            "com.example.alpha.data.OrderRepository#load",
            "com.example.alpha.entry.ApiController#self",
            "HelperOne#debug",
            "com.example.alpha.data.OrderRepository.InnerContract#touch"));
    List<MethodInfo> apiMethods = new ArrayList<>();
    apiMethods.add(handle);
    apiMethods.add(legacyCalls);
    apiMethods.add(null);
    apiController.setMethods(apiMethods);

    ClassInfo entryOrderService = classInfo("com.example.alpha.entry.OrderService", 20, 2);
    ClassInfo orderService = classInfo("com.example.alpha.service.OrderService", 80, 10);
    MethodInfo serviceMethod = new MethodInfo();
    serviceMethod.setName("process");
    serviceMethod.setCyclomaticComplexity(10);
    serviceMethod.setCalledMethods(List.of("OrderRepository#find", "UniqueHelper#assist"));
    orderService.setMethods(List.of(serviceMethod));

    ClassInfo repository = classInfo("com.example.alpha.data.OrderRepository", 65, 6);
    ClassInfo repositoryInner =
        classInfo("com.example.alpha.data.OrderRepository.InnerContract", 20, 3);
    ClassInfo uniqueHelper = classInfo("com.example.alpha.util.UniqueHelper", 12, 2);
    ClassInfo helperOneAlpha = classInfo("com.example.alpha.util.HelperOne", 11, 2);
    ClassInfo helperOneBeta = classInfo("com.example.beta.util.HelperOne", 13, 2);
    ClassInfo domainModel = classInfo("com.example.alpha.domain.OrderModel", 30, 4);
    ClassInfo infraConfig = classInfo("com.example.alpha.infra.RuntimeConfig", 28, 4);
    ClassInfo coreEngine = classInfo("com.example.alpha.core.Engine", 22, 3);

    ClassInfo standalone = classInfo("Standalone", 9, 1);
    standalone.setFilePath("Standalone.java");

    ClassInfo nullFqn = classInfo(null, 5, 1);
    nullFqn.setFilePath("src/main/java/com/example/alpha/misc/Unknown.java");

    ClassInfo nested = classInfo("com.example.alpha.entry.ApiController$Nested", 5, 1);
    nested.setNestedClass(true);

    ClassInfo testByPath = classInfo("com.example.alpha.entry.PathOnly", 7, 1);
    testByPath.setFilePath("src/test/java/com/example/alpha/entry/PathOnly.java");

    ClassInfo testByFqn = classInfo("com.example.alpha.test.FqnOnly", 8, 1);
    testByFqn.setFilePath("src/main/java/com/example/alpha/test/FqnOnly.java");

    List<ClassInfo> classes =
        new ArrayList<>(
            List.of(
                apiController,
                entryOrderService,
                orderService,
                repository,
                repositoryInner,
                uniqueHelper,
                helperOneAlpha,
                helperOneBeta,
                domainModel,
                infraConfig,
                coreEngine,
                standalone,
                nullFqn,
                nested,
                testByPath,
                testByFqn));
    classes.add(null);
    result.setClasses(classes);
    return result;
  }

  private ClassInfo classInfo(String fqn, int loc, int complexity) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    if (fqn != null && fqn.contains(".")) {
      classInfo.setFilePath("src/main/java/" + fqn.replace('.', '/') + ".java");
    }
    classInfo.setLoc(loc);
    MethodInfo method = new MethodInfo();
    method.setName("m");
    method.setCyclomaticComplexity(complexity);
    classInfo.setMethods(List.of(method));
    return classInfo;
  }

  private void prepareLinkedArtifacts(Path runRoot) throws IOException {
    writeFile(runRoot.resolve("report/analysis_visual.html"), "<html>visual</html>");
    writeFile(runRoot.resolve("report/report.html"), "<html>report</html>");
    writeFile(runRoot.resolve("analysis/quality_report.md"), "# quality");
    writeFile(runRoot.resolve("report/report.json"), "{\"ok\":true}");

    writeFile(
        runRoot.resolve("analysis/com/example/alpha/entry/analysis_ApiController.json"),
        "{\"class\":\"ApiController\"}");
    writeFile(
        runRoot.resolve("docs/src/main/java/com/example/alpha/entry/ApiController.html"),
        "<html>doc</html>");
    writeFile(
        runRoot.resolve("docs/src/main/java/com/example/alpha/entry/ApiController.md"),
        "# class detail");
  }

  private void writeFile(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }

  private JsonNode nodeById(JsonNode nodes, String id) {
    for (JsonNode node : nodes) {
      if (id.equals(node.path("id").asText())) {
        return node;
      }
    }
    return null;
  }

  private boolean hasEdge(JsonNode edges, String from, String to) {
    for (JsonNode edge : edges) {
      if (from.equals(edge.path("from").asText()) && to.equals(edge.path("to").asText())) {
        return true;
      }
    }
    return false;
  }

  private List<String> stringValues(JsonNode nodes, String fieldName) {
    List<String> values = new ArrayList<>();
    for (JsonNode node : nodes) {
      if (fieldName == null) {
        values.add(node.asText());
      } else {
        values.add(node.path(fieldName).asText());
      }
    }
    return values;
  }

  private Object invokePrivate(
      ExploreFlow flow, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = ExploreFlow.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(flow, args);
  }

  private String invokeString(
      ExploreFlow flow, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    return (String) invokePrivate(flow, methodName, parameterTypes, args);
  }

  private List<String> invokeStringList(
      ExploreFlow flow, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    return (List<String>) invokePrivate(flow, methodName, parameterTypes, args);
  }
}
