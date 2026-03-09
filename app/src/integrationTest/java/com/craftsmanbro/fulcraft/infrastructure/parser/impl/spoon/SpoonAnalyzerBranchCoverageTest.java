package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath.ClasspathResolver;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import spoon.Launcher;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

class SpoonAnalyzerBranchCoverageTest {

  @TempDir Path tempDir;

  @Test
  void importAsStringAndExceptionLocationHandleEdgeCases() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();

    assertEquals(
        MethodInfo.UNKNOWN,
        invokePrivate(
            analyzer, "importAsString", new Class<?>[] {CtImport.class}, new Object[] {null}));

    CtImport importDecl = mock(CtImport.class);
    when(importDecl.toString()).thenReturn("import javax.xml.bind.JAXBContext;");
    assertEquals(
        "javax.xml.bind.JAXBContext",
        invokePrivate(
            analyzer,
            "importAsString",
            new Class<?>[] {CtImport.class},
            new Object[] {importDecl}));

    Exception withTrace = new IllegalStateException("boom");
    withTrace.setStackTrace(new StackTraceElement[] {new StackTraceElement("A", "b", "A.java", 7)});
    assertEquals(
        "A.b:7",
        invokePrivate(
            analyzer,
            "getExceptionLocation",
            new Class<?>[] {Exception.class},
            new Object[] {withTrace}));

    Exception noTrace = new RuntimeException("x");
    noTrace.setStackTrace(new StackTraceElement[0]);
    assertEquals(
        "unknown location",
        invokePrivate(
            analyzer,
            "getExceptionLocation",
            new Class<?>[] {Exception.class},
            new Object[] {noTrace}));
  }

  @Test
  void resolvePathsHandlesNullEmptyRelativeAndAbsolute() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Path root = tempDir.toAbsolutePath();
    Path absolute = root.resolve("external/lib").toAbsolutePath();

    assertEquals(
        List.of(),
        invokePrivate(
            analyzer,
            "resolvePaths",
            new Class<?>[] {Path.class, List.class},
            new Object[] {root, null}));
    assertEquals(
        List.of(),
        invokePrivate(
            analyzer,
            "resolvePaths",
            new Class<?>[] {Path.class, List.class},
            new Object[] {root, List.of()}));

    List<Path> resolved =
        invokePrivate(
            analyzer,
            "resolvePaths",
            new Class<?>[] {Path.class, List.class},
            new Object[] {root, List.of("src/main/../main/java", absolute.toString())});

    assertEquals(root.resolve("src/main/java").normalize(), resolved.get(0));
    assertEquals(absolute.normalize(), resolved.get(1));
  }

  @Test
  void setFilePathCoversNullPositionAndFallbackRelativization() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    ClassInfo classInfo = new ClassInfo();
    CtType<Object> noPosition = mock(CtType.class);
    when(noPosition.getPosition()).thenReturn(null);

    assertNull(
        invokePrivate(
            analyzer,
            "setFilePath",
            new Class<?>[] {CtType.class, ClassInfo.class, Path.class, Path.class},
            new Object[] {noPosition, classInfo, tempDir.resolve("src/main/java"), tempDir}));
    assertEquals(MethodInfo.UNKNOWN, classInfo.getFilePath());

    CtType<Object> noCompilationUnit = mock(CtType.class);
    SourcePosition position = mock(SourcePosition.class);
    when(noCompilationUnit.getPosition()).thenReturn(position);
    when(position.getCompilationUnit()).thenReturn(null);
    ClassInfo noCu = new ClassInfo();
    assertNull(
        invokePrivate(
            analyzer,
            "setFilePath",
            new Class<?>[] {CtType.class, ClassInfo.class, Path.class, Path.class},
            new Object[] {noCompilationUnit, noCu, tempDir.resolve("src/main/java"), tempDir}));
    assertEquals(MethodInfo.UNKNOWN, noCu.getFilePath());

    CtType<Object> noFile = mock(CtType.class);
    SourcePosition noFilePos = mock(SourcePosition.class);
    CompilationUnit noFileCu = mock(CompilationUnit.class);
    when(noFile.getPosition()).thenReturn(noFilePos);
    when(noFilePos.getCompilationUnit()).thenReturn(noFileCu);
    when(noFileCu.getFile()).thenReturn(null);
    ClassInfo noFileInfo = new ClassInfo();
    assertNull(
        invokePrivate(
            analyzer,
            "setFilePath",
            new Class<?>[] {CtType.class, ClassInfo.class, Path.class, Path.class},
            new Object[] {noFile, noFileInfo, tempDir.resolve("src/main/java"), tempDir}));
    assertEquals(MethodInfo.UNKNOWN, noFileInfo.getFilePath());

    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Real.java",
            """
                        package com.example;
                        public class Real {}
                        """);
    CtType<?> real = SpoonTestUtils.getType(launcher, "com.example.Real");

    Path srcRoot = tempDir.resolve("src/main/java");
    ClassInfo srcBased = new ClassInfo();
    Path relativeFromSrc =
        invokePrivate(
            analyzer,
            "setFilePath",
            new Class<?>[] {CtType.class, ClassInfo.class, Path.class, Path.class},
            new Object[] {real, srcBased, srcRoot, tempDir});
    assertEquals(Path.of("com/example/Real.java"), relativeFromSrc);
    assertEquals(Path.of("com/example/Real.java").toString(), srcBased.getFilePath());

    ClassInfo rootBased = new ClassInfo();
    Path relativeFromRoot =
        invokePrivate(
            analyzer,
            "setFilePath",
            new Class<?>[] {CtType.class, ClassInfo.class, Path.class, Path.class},
            new Object[] {real, rootBased, tempDir.resolve("other-root"), tempDir});
    assertEquals(Path.of("src/main/java/com/example/Real.java"), relativeFromRoot);
    assertEquals(
        Path.of("src/main/java/com/example/Real.java").toString(), rootBased.getFilePath());
  }

  @Test
  void configureClasspathCoversOffAutoStrictAndFailureFallbacks() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();

    Config explicitOff = baseConfig("OFF", false, true);
    Launcher offLauncher = new Launcher();
    assertEquals(
        "NO_CLASSPATH",
        invokePrivate(
            analyzer,
            "configureClasspath",
            new Class<?>[] {Launcher.class, Config.class, Path.class},
            new Object[] {offLauncher, explicitOff, tempDir}));
    assertTrue(offLauncher.getEnvironment().getNoClasspath());

    Config spoonOff = baseConfig(null, true, false);
    Launcher spoonOffLauncher = new Launcher();
    assertEquals(
        "NO_CLASSPATH",
        invokePrivate(
            analyzer,
            "configureClasspath",
            new Class<?>[] {Launcher.class, Config.class, Path.class},
            new Object[] {spoonOffLauncher, spoonOff, tempDir}));
    assertTrue(spoonOffLauncher.getEnvironment().getNoClasspath());

    Config auto = baseConfig("AUTO", false, true);
    Path classpathJar = tempDir.resolve("lib/a.jar");
    Files.createDirectories(classpathJar.getParent());
    Files.createFile(classpathJar);
    ClasspathResolutionResult withClasspath =
        new ClasspathResolutionResult(List.of(classpathJar), "gradle", List.of());
    try (MockedStatic<ClasspathResolver> resolver = Mockito.mockStatic(ClasspathResolver.class)) {
      resolver
          .when(() -> ClasspathResolver.resolveCompileClasspath(any(Path.class), any(Config.class)))
          .thenReturn(withClasspath);
      Launcher withClasspathLauncher = new Launcher();
      String mode =
          invokePrivate(
              analyzer,
              "configureClasspath",
              new Class<?>[] {Launcher.class, Config.class, Path.class},
              new Object[] {withClasspathLauncher, auto, tempDir});
      assertTrue(mode.startsWith("WITH_CLASSPATH"));
      assertFalse(withClasspathLauncher.getEnvironment().getNoClasspath());
      assertNotNull(withClasspathLauncher.getEnvironment().getSourceClasspath());
      assertEquals(1, withClasspathLauncher.getEnvironment().getSourceClasspath().length);
    }

    ClasspathResolutionResult emptyClasspath =
        new ClasspathResolutionResult(List.of(), "gradle", List.of());
    try (MockedStatic<ClasspathResolver> resolver = Mockito.mockStatic(ClasspathResolver.class)) {
      resolver
          .when(() -> ClasspathResolver.resolveCompileClasspath(any(Path.class), any(Config.class)))
          .thenReturn(emptyClasspath);
      Launcher fallbackLauncher = new Launcher();
      assertEquals(
          "NO_CLASSPATH",
          invokePrivate(
              analyzer,
              "configureClasspath",
              new Class<?>[] {Launcher.class, Config.class, Path.class},
              new Object[] {fallbackLauncher, auto, tempDir}));
      assertTrue(fallbackLauncher.getEnvironment().getNoClasspath());
    }

    Config strict = baseConfig("STRICT", false, true);
    try (MockedStatic<ClasspathResolver> resolver = Mockito.mockStatic(ClasspathResolver.class)) {
      resolver
          .when(() -> ClasspathResolver.resolveCompileClasspath(any(Path.class), any(Config.class)))
          .thenReturn(emptyClasspath);
      IllegalStateException strictEmpty =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokePrivate(
                      analyzer,
                      "configureClasspath",
                      new Class<?>[] {Launcher.class, Config.class, Path.class},
                      new Object[] {new Launcher(), strict, tempDir}));
      assertTrue(strictEmpty.getMessage().contains("[STRICT MODE]"));
    }

    try (MockedStatic<ClasspathResolver> resolver = Mockito.mockStatic(ClasspathResolver.class)) {
      resolver
          .when(() -> ClasspathResolver.resolveCompileClasspath(any(Path.class), any(Config.class)))
          .thenThrow(new RuntimeException("resolver-failed"));
      IllegalStateException strictFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokePrivate(
                      analyzer,
                      "configureClasspath",
                      new Class<?>[] {Launcher.class, Config.class, Path.class},
                      new Object[] {new Launcher(), strict, tempDir}));
      assertTrue(strictFailure.getMessage().contains("resolution failed"));
    }

    try (MockedStatic<ClasspathResolver> resolver = Mockito.mockStatic(ClasspathResolver.class)) {
      resolver
          .when(() -> ClasspathResolver.resolveCompileClasspath(any(Path.class), any(Config.class)))
          .thenThrow(new RuntimeException("auto-failed"));
      Launcher autoFallbackLauncher = new Launcher();
      assertEquals(
          "NO_CLASSPATH",
          invokePrivate(
              analyzer,
              "configureClasspath",
              new Class<?>[] {Launcher.class, Config.class, Path.class},
              new Object[] {autoFallbackLauncher, auto, tempDir}));
      assertTrue(autoFallbackLauncher.getEnvironment().getNoClasspath());
    }
  }

  @Test
  void registerMethodHandlesGuardBranchesAndContextPopulation() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    AnalysisContext context = new AnalysisContext();
    CtExecutable<Object> executable = mock(CtExecutable.class);
    when(executable.toString()).thenReturn("void run() {}");
    when(executable.getBody()).thenReturn(null);

    MethodInfo noSignature = new MethodInfo();
    noSignature.setName("run");
    noSignature.setSignature(null);
    assertNull(
        invokePrivate(
            analyzer,
            "registerMethod",
            new Class<?>[] {
              AnalysisContext.class, MethodInfo.class, CtExecutable.class, String.class
            },
            new Object[] {context, noSignature, executable, "com.example.Sample"}));

    MethodInfo noClass = new MethodInfo();
    noClass.setName("run");
    noClass.setSignature("run()");
    assertNull(
        invokePrivate(
            analyzer,
            "registerMethod",
            new Class<?>[] {
              AnalysisContext.class, MethodInfo.class, CtExecutable.class, String.class
            },
            new Object[] {context, noClass, executable, null}));

    MethodInfo nullKeyInfo = new MethodInfo();
    nullKeyInfo.setName("run");
    nullKeyInfo.setSignature("run()");
    try (MockedStatic<DependencyGraphBuilder> methodKey =
        Mockito.mockStatic(DependencyGraphBuilder.class)) {
      methodKey
          .when(() -> DependencyGraphBuilder.methodKey("com.example.Sample", "run()"))
          .thenReturn(null);
      assertNull(
          invokePrivate(
              analyzer,
              "registerMethod",
              new Class<?>[] {
                AnalysisContext.class, MethodInfo.class, CtExecutable.class, String.class
              },
              new Object[] {context, nullKeyInfo, executable, "com.example.Sample"}));
    }

    MethodInfo nullVisibility = new MethodInfo();
    nullVisibility.setName("save");
    nullVisibility.setSignature("save()");
    String keyWithNullVisibility =
        invokePrivate(
            analyzer,
            "registerMethod",
            new Class<?>[] {
              AnalysisContext.class, MethodInfo.class, CtExecutable.class, String.class
            },
            new Object[] {context, nullVisibility, executable, "com.example.Sample"});
    assertEquals("com.example.Sample#save()", keyWithNullVisibility);
    assertFalse(context.getMethodVisibility().containsKey(keyWithNullVisibility));
    assertEquals(Boolean.FALSE, context.getMethodHasBody().get(keyWithNullVisibility));
    assertFalse(context.getMethodCodeHash().containsKey(keyWithNullVisibility));

    MethodInfo fullInfo = new MethodInfo();
    fullInfo.setName("run");
    fullInfo.setSignature("run(int)");
    fullInfo.setVisibility("public");
    CtExecutable<Object> withBody = mock(CtExecutable.class);
    spoon.reflect.code.CtBlock<Object> body = mock(spoon.reflect.code.CtBlock.class);
    when(withBody.getBody()).thenReturn(body);
    when(body.toString()).thenReturn("{ return; }");
    when(withBody.toString()).thenReturn("void run(int x) { return; }");

    String fullKey =
        invokePrivate(
            analyzer,
            "registerMethod",
            new Class<?>[] {
              AnalysisContext.class, MethodInfo.class, CtExecutable.class, String.class
            },
            new Object[] {context, fullInfo, withBody, "com.example.Sample"});
    assertEquals("com.example.Sample#run(int)", fullKey);
    assertEquals("public", context.getMethodVisibility().get(fullKey));
    assertEquals(Boolean.TRUE, context.getMethodHasBody().get(fullKey));
    assertNotNull(context.getMethodCodeHash().get(fullKey));
    assertTrue(context.getIncomingCounts().containsKey(fullKey));
  }

  @Test
  void privateHelpersCoverExtendsImportsLocConstructorsAndAnonymousNames() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();

    CtType<Object> type = mock(CtType.class);
    when(type.getSuperclass()).thenReturn(null);
    assertEquals(
        List.of(),
        invokePrivate(
            analyzer, "collectExtendsTypes", new Class<?>[] {CtType.class}, new Object[] {type}));

    CtTypeReference<Object> superRef = mock(CtTypeReference.class);
    org.mockito.Mockito.doReturn(superRef).when(type).getSuperclass();
    when(superRef.getSimpleName()).thenReturn(null);
    assertEquals(
        List.of(),
        invokePrivate(
            analyzer, "collectExtendsTypes", new Class<?>[] {CtType.class}, new Object[] {type}));
    when(superRef.getSimpleName()).thenReturn("BaseClass");
    assertEquals(
        List.of("BaseClass"),
        invokePrivate(
            analyzer, "collectExtendsTypes", new Class<?>[] {CtType.class}, new Object[] {type}));

    CtType<Object> noPositionType = mock(CtType.class);
    when(noPositionType.getPosition()).thenReturn(null);
    assertEquals(
        List.of(),
        invokePrivate(
            analyzer,
            "collectImports",
            new Class<?>[] {CtType.class},
            new Object[] {noPositionType}));

    Launcher importLauncher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/ImportsSample.java",
            """
                        package com.example;
                        import a.b.C;
                        public class ImportsSample {}
                        """);
    CtType<?> importsType = SpoonTestUtils.getType(importLauncher, "com.example.ImportsSample");
    assertEquals(
        List.of("a.b.C"),
        invokePrivate(
            analyzer, "collectImports", new Class<?>[] {CtType.class}, new Object[] {importsType}));

    CtType<Object> locType = mock(CtType.class);
    SourcePosition validPos = mock(SourcePosition.class);
    when(locType.getPosition()).thenReturn(validPos);
    when(validPos.isValidPosition()).thenReturn(true);
    when(validPos.getLine()).thenReturn(3);
    when(validPos.getEndLine()).thenReturn(7);
    assertEquals(
        5,
        ((Integer)
                invokePrivate(
                    analyzer,
                    "calculateLoc",
                    new Class<?>[] {CtType.class},
                    new Object[] {locType}))
            .intValue());
    when(validPos.isValidPosition()).thenReturn(false);
    assertEquals(
        0,
        ((Integer)
                invokePrivate(
                    analyzer,
                    "calculateLoc",
                    new Class<?>[] {CtType.class},
                    new Object[] {locType}))
            .intValue());

    CtClass<?> classType = mock(CtClass.class);
    CtEnum<?> enumType = mock(CtEnum.class);
    spoon.reflect.declaration.CtConstructor<?> c1 =
        mock(spoon.reflect.declaration.CtConstructor.class);
    spoon.reflect.declaration.CtConstructor<?> c2 =
        mock(spoon.reflect.declaration.CtConstructor.class);
    Mockito.doReturn(java.util.Set.of(c1)).when(classType).getConstructors();
    Mockito.doReturn(java.util.Set.of(c2)).when(enumType).getConstructors();
    assertEquals(
        1,
        ((java.util.Collection<?>)
                invokePrivate(
                    analyzer,
                    "getConstructors",
                    new Class<?>[] {CtType.class},
                    new Object[] {classType}))
            .size());
    assertEquals(
        2,
        ((java.util.Collection<?>)
                invokePrivate(
                    analyzer,
                    "getConstructors",
                    new Class<?>[] {CtType.class},
                    new Object[] {enumType}))
            .size());
    assertEquals(
        0,
        ((java.util.Collection<?>)
                invokePrivate(
                    analyzer,
                    "getConstructors",
                    new Class<?>[] {CtType.class},
                    new Object[] {type}))
            .size());

    CtExecutable<Object> noBody = mock(CtExecutable.class);
    when(noBody.getBody()).thenReturn(null);
    assertFalse(
        ((Boolean)
                invokePrivate(
                    analyzer,
                    "hasLoops",
                    new Class<?>[] {CtExecutable.class},
                    new Object[] {noBody}))
            .booleanValue());
    assertFalse(
        ((Boolean)
                invokePrivate(
                    analyzer,
                    "hasConditionals",
                    new Class<?>[] {CtExecutable.class},
                    new Object[] {noBody}))
            .booleanValue());

    Launcher methodLauncher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Flow.java",
            """
                        package com.example;
                        class Flow {
                          void flow(int x) {
                            for (int i = 0; i < 1; i++) {}
                            if (x > 0) {}
                            switch (x) { case 1 -> { } default -> { } }
                            int y = x > 0 ? 1 : 2;
                          }
                        }
                        """);
    CtExecutable<?> withElements =
        SpoonTestUtils.getType(methodLauncher, "com.example.Flow").getMethodsByName("flow").get(0);
    assertTrue(
        ((Boolean)
                invokePrivate(
                    analyzer,
                    "hasLoops",
                    new Class<?>[] {CtExecutable.class},
                    new Object[] {withElements}))
            .booleanValue());
    assertTrue(
        ((Boolean)
                invokePrivate(
                    analyzer,
                    "hasConditionals",
                    new Class<?>[] {CtExecutable.class},
                    new Object[] {withElements}))
            .booleanValue());

    CtClass<Object> anonymous = mock(CtClass.class);
    CtType<Object> parent = mock(CtType.class);
    SourcePosition anonPos = mock(SourcePosition.class);
    when(anonymous.getParent(CtType.class)).thenReturn(parent);
    when(parent.getQualifiedName()).thenReturn("com.example.Owner");
    when(anonymous.getPosition()).thenReturn(anonPos);
    when(anonPos.isValidPosition()).thenReturn(true);
    when(anonPos.getLine()).thenReturn(12);
    assertEquals(
        "com.example.Owner$anonymous@12",
        invokePrivate(
            analyzer,
            "buildAnonymousFqn",
            new Class<?>[] {CtClass.class},
            new Object[] {anonymous}));

    CtClass<Object> fallbackAnonymous = mock(CtClass.class);
    when(fallbackAnonymous.getParent(CtType.class)).thenThrow(new RuntimeException("boom"));
    SourcePosition invalidPos = mock(SourcePosition.class);
    when(fallbackAnonymous.getPosition()).thenReturn(invalidPos);
    when(invalidPos.isValidPosition()).thenReturn(false);
    assertEquals(
        "anonymous$anonymous@-1",
        invokePrivate(
            analyzer,
            "buildAnonymousFqn",
            new Class<?>[] {CtClass.class},
            new Object[] {fallbackAnonymous}));
  }

  private static <T> T invokePrivate(
      Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return (T) method.invoke(target, args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }

  private static Config baseConfig(
      String classpathMode, boolean spoonNoClasspath, boolean withClasspathConfig) {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    Config.AnalysisConfig analysis = new Config.AnalysisConfig();
    Config.AnalysisConfig.SpoonConfig spoon = new Config.AnalysisConfig.SpoonConfig();
    spoon.setNoClasspath(spoonNoClasspath);
    analysis.setSpoon(spoon);
    if (withClasspathConfig) {
      Config.AnalysisConfig.ClasspathConfig classpath = new Config.AnalysisConfig.ClasspathConfig();
      if (classpathMode != null) {
        classpath.setMode(classpathMode);
      }
      analysis.setClasspath(classpath);
    } else {
      analysis.setClasspath(null);
    }
    config.setAnalysis(analysis);
    return config;
  }
}
