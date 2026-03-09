package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.metrics.impl.MetricsCalculator;
import com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.ModelFreezer;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.PathExcluder;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.ResultBuilder;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisError;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

/**
 * Spoon-based implementation of AnalysisPort. Orchestrates file analysis and delegates to helper
 * classes.
 */
public class SpoonAnalyzer implements AnalysisPort {

  private static final String ENGINE_NAME = "spoon";

  private static final String VISIBILITY_PACKAGE_PRIVATE = "package_private";

  private static final String NO_CLASSPATH_MODE = "NO_CLASSPATH";

  private final DependencyGraphBuilder dependencyGraphBuilder;

  private final SourcePathResolver sourcePathResolver;

  public SpoonAnalyzer() {
    this(new DependencyGraphBuilder(), new SourcePathResolver());
  }

  SpoonAnalyzer(
      final DependencyGraphBuilder dependencyGraphBuilder,
      final SourcePathResolver sourcePathResolver) {
    this.dependencyGraphBuilder = Objects.requireNonNull(dependencyGraphBuilder);
    this.sourcePathResolver = Objects.requireNonNull(sourcePathResolver);
  }

  private static final class ProgressTracker {

    private final int totalFiles;

    private final Set<Path> progressedFiles = new java.util.HashSet<>();

    private int processedFiles;

    private final long startTime;

    private ProgressTracker(final int totalFiles, final long startTime) {
      this.totalFiles = totalFiles;
      this.startTime = startTime;
    }

    private void maybeProgress(final Path relativePath) {
      if (relativePath == null) {
        return;
      }
      if (!progressedFiles.add(relativePath)) {
        return;
      }
      processedFiles++;
      Logger.progressBar(processedFiles, totalFiles, relativePath.toString(), startTime);
    }
  }

  @Override
  public AnalysisResult analyze(final Path projectRoot, final Config config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config must not be null"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectRoot must not be null"));
    final String projectId = config.getProject().getId();
    final String commitHash = config.getProject().getCommit();
    final AnalysisResult result = initializeResult(projectId, commitHash);
    final AnalysisContext context = new AnalysisContext();
    context.setCallGraphSource(ENGINE_NAME);
    context.setCallGraphResolved(true);
    final Path rootPath = projectRoot.toAbsolutePath();
    if (!Files.exists(rootPath)) {
      addError(result, "Project root directory not found: " + rootPath.toAbsolutePath());
      return ModelFreezer.freezeAnalysisResult(result);
    }
    final var srcDirs = sourcePathResolver.resolve(rootPath, config);
    final var srcPathOptional = srcDirs.mainSource();
    if (srcPathOptional.isEmpty()) {
      addError(
          result,
          "Source directory not found in project: "
              + rootPath.toAbsolutePath()
              + ". Tried: src/main/java, src, app/src/main/java, project root");
      return ModelFreezer.freezeAnalysisResult(result);
    }
    final Path srcPath = srcPathOptional.get();
    final String srcPathString = srcPath.toAbsolutePath().toString();
    Logger.infoOnce(
        "analysis.source_dir:" + srcPathString, "Using source directory: " + srcPathString);
    try {
      runSpoonAnalysis(rootPath, srcDirs, srcPath, result, context, config);
    } catch (Exception e) {
      String errorMsg = e.getMessage();
      if (errorMsg == null) {
        errorMsg = e.getClass().getSimpleName() + " at " + getExceptionLocation(e);
      }
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Spoon analysis failed: " + errorMsg),
          e);
      addError(result, "Spoon fatal: " + errorMsg);
    }
    return ModelFreezer.freezeAnalysisResult(result);
  }

  @Override
  public String getEngineName() {
    return ENGINE_NAME;
  }

  @Override
  public boolean supports(final Path projectRoot) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) {
      return false;
    }
    return Files.isDirectory(projectRoot.resolve("src/main/java"))
        || Files.isDirectory(projectRoot.resolve("app/src/main/java"))
        || Files.isDirectory(projectRoot.resolve("src"));
  }

  private List<Path> resolvePaths(final Path rootPath, final List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return List.of();
    }
    return paths.stream()
        .map(
            p -> {
              Path path = java.nio.file.Paths.get(p);
              if (!path.isAbsolute()) {
                path = rootPath.resolve(path);
              }
              return path.normalize();
            })
        .toList();
  }

  private AnalysisResult initializeResult(final String projectId, final String commitHash) {
    final AnalysisResult result = new AnalysisResult();
    result.setProjectId(projectId);
    result.setCommitHash(commitHash);
    result.setClasses(new ArrayList<>());
    result.setAnalysisErrors(new ArrayList<>());
    return result;
  }

  private void addError(final AnalysisResult result, final String message) {
    final AnalysisError error = new AnalysisError(MethodInfo.UNKNOWN, message, null);
    result.getAnalysisErrors().add(error);
  }

  private void runSpoonAnalysis(
      final Path rootPath,
      final SourcePathResolver.SourceDirectories srcDirs,
      final Path srcPath,
      final AnalysisResult result,
      final AnalysisContext context,
      final Config config)
      throws java.io.IOException {
    final Path srcRootPath = srcPath.toAbsolutePath();
    final List<String> excludePaths = config.getProject().getExcludePaths();
    final List<String> includePaths = config.getProject().getIncludePaths();
    final boolean excludeTests =
        config.getAnalysis() == null || config.getAnalysis().getExcludeTests();
    final List<Path> excludeRoots =
        PathExcluder.buildExcludeRoots(rootPath, excludePaths, excludeTests);
    final List<Path> includeRoots = resolvePaths(rootPath, includePaths);
    logTestSourceDirectory(srcDirs, excludeRoots);
    final List<Path> javaFiles = listJavaFiles(srcPath, excludeRoots, includeRoots);
    final long startTime = System.currentTimeMillis();
    final ProgressTracker progress = new ProgressTracker(javaFiles.size(), startTime);
    final CtModel model = buildModel(javaFiles, config, rootPath);
    processNamedTypes(model, result, context, srcRootPath, rootPath, progress);
    processAnonymousTypes(model, result, context, srcRootPath, rootPath, progress);
    com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CommonPostProcessor
        .finalizePostProcessing(result, context);
    Logger.progressCompleteOnce(javaFiles.size());
  }

  private void logTestSourceDirectory(
      final SourcePathResolver.SourceDirectories srcDirs, final List<Path> excludeRoots) {
    srcDirs
        .testSource()
        .ifPresent(
            path -> {
              final Path testPath = path.toAbsolutePath();
              if (!PathExcluder.isExcluded(testPath, excludeRoots)) {
                final String testPathString = testPath.toString();
                Logger.infoOnce(
                    "analysis.test_source:included:" + testPathString,
                    "Detected test source directory (included): " + testPathString);
              }
            });
  }

  private List<Path> listJavaFiles(
      final Path srcPath, final List<Path> excludeRoots, final List<Path> includeRoots)
      throws java.io.IOException {
    try (java.util.stream.Stream<Path> paths = Files.walk(srcPath)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(
              p ->
                  p.toString()
                      .endsWith(
                          com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathUtils
                              .JAVA_EXTENSION))
          .map(Path::toAbsolutePath)
          .map(Path::normalize)
          .filter(p -> !PathExcluder.isExcluded(p, excludeRoots))
          .filter(p -> isIncluded(p, includeRoots))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  private boolean isIncluded(final Path path, final List<Path> includeRoots) {
    if (includeRoots.isEmpty()) {
      return true;
    }
    for (final Path root : includeRoots) {
      if (path.startsWith(root)) {
        return true;
      }
    }
    return false;
  }

  private CtModel buildModel(
      final List<Path> javaFiles, final Config config, final Path projectRoot) {
    final Launcher launcher = new Launcher();
    for (final Path file : javaFiles) {
      launcher.addInputResource(file.toString());
    }
    final String effectiveMode = configureClasspath(launcher, config, projectRoot);
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "[Spoon] Effective mode: " + effectiveMode));
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.getEnvironment().setComplianceLevel(17);
    launcher.getEnvironment().setIgnoreSyntaxErrors(true);
    launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
    // Configure encoding from analysis.source_charset (default: UTF-8)
    String encoding = "UTF-8";
    if (config.getAnalysis() != null) {
      encoding = config.getAnalysis().getSourceCharset();
    }
    launcher.getEnvironment().setEncoding(java.nio.charset.Charset.forName(encoding));
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "[Spoon] Using encoding: " + encoding));
    try {
      launcher.buildModel();
    } catch (Exception e) {
      // Enhanced failure logging with file context
      final String errorMsg =
          String.format(
              "[Spoon] Model build failed.%n"
                  + "  Exception: %s%n"
                  + "  Message: %s%n"
                  + "  Files analyzed: %d%n"
                  + "  Hint: Check encoding settings (current: %s) or file syntax.",
              e.getClass().getName(), e.getMessage(), javaFiles.size(), encoding);
      Logger.error(errorMsg);
      throw new IllegalStateException(errorMsg, e);
    }
    return launcher.getModel();
  }

  private String configureClasspath(
      final Launcher launcher, final Config config, final Path projectRoot) {
    String classpathMode = "AUTO";
    if (config.getAnalysis() != null) {
      classpathMode = config.getAnalysis().getClasspathMode();
      if (config.getAnalysis().getClasspath() == null
          && config.getAnalysis().isNoClasspathEnabled()) {
        classpathMode = "OFF";
      }
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "[Spoon] Classpath mode: " + classpathMode));
    if ("OFF".equalsIgnoreCase(classpathMode)) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "[Spoon] Classpath mode OFF - using noClasspath"));
      launcher.getEnvironment().setNoClasspath(true);
      return NO_CLASSPATH_MODE;
    }
    final com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult
        resolution;
    try {
      resolution =
          com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath.ClasspathResolver
              .resolveCompileClasspath(projectRoot, config);
    } catch (Exception e) {
      if (isStrictMode(classpathMode)) {
        throw new IllegalStateException(
            "[STRICT MODE] Classpath resolution failed: "
                + e.getMessage()
                + ". "
                + "Set 'analysis.classpath.mode: AUTO' to fallback to noClasspath mode.",
            e);
      }
      Logger.warn(
          "[Spoon] Failed to resolve classpath: "
              + e.getMessage()
              + ". Falling back to no-classpath mode");
      launcher.getEnvironment().setNoClasspath(true);
      return NO_CLASSPATH_MODE;
    }
    com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath.ClasspathResolutionLogger
        .logAttempts(resolution);
    if (resolution.isEmpty()) {
      if (isStrictMode(classpathMode)) {
        throw new IllegalStateException(
            "[STRICT MODE] Classpath resolution returned empty. "
                + "Set 'analysis.classpath.mode: AUTO' to fallback to noClasspath mode.");
      }
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[Spoon] No classpath resolved, falling back to no-classpath mode"));
      launcher.getEnvironment().setNoClasspath(true);
      return NO_CLASSPATH_MODE;
    }
    final String[] cp = resolution.getEntries().stream().map(Path::toString).toArray(String[]::new);
    launcher.getEnvironment().setSourceClasspath(cp);
    launcher.getEnvironment().setNoClasspath(false);
    final int count = resolution.getEntries().size();
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "[Spoon] Classpath configured: " + count + " entries"));
    return "WITH_CLASSPATH (" + count + " entries)";
  }

  private boolean isStrictMode(final String classpathMode) {
    return "STRICT".equalsIgnoreCase(classpathMode);
  }

  private void processNamedTypes(
      final CtModel model,
      final AnalysisResult result,
      final AnalysisContext context,
      final Path srcRootPath,
      final Path rootPath,
      final ProgressTracker progress) {
    for (final CtType<?> type : model.getAllTypes()) {
      if (type.isAnonymous()) {
        continue;
      }
      final ClassInfo classInfo =
          analyzeType(type, type.getQualifiedName(), srcRootPath, rootPath, context, progress);
      result.getClasses().add(classInfo);
    }
  }

  private void processAnonymousTypes(
      final CtModel model,
      final AnalysisResult result,
      final AnalysisContext context,
      final Path srcRootPath,
      final Path rootPath,
      final ProgressTracker progress) {
    final List<spoon.reflect.code.CtNewClass<?>> anonNewClasses =
        model.getElements(ct -> ct.getAnonymousClass() != null);
    for (final spoon.reflect.code.CtNewClass<?> newClass : anonNewClasses) {
      final CtClass<?> anonClass = newClass.getAnonymousClass();
      if (anonClass == null) {
        continue;
      }
      final ClassInfo classInfo =
          analyzeAnonymousClass(anonClass, srcRootPath, rootPath, context, progress);
      result.getClasses().add(classInfo);
    }
  }

  private ClassInfo analyzeAnonymousClass(
      final CtClass<?> anonClass,
      final Path srcRootPath,
      final Path rootPath,
      final AnalysisContext context,
      final ProgressTracker progress) {
    final String fqn = buildAnonymousFqn(anonClass);
    final ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setInterface(false);
    classInfo.setAbstract(false);
    classInfo.setAnonymous(true);
    classInfo.setNestedClass(anonClass.getDeclaringType() != null);
    classInfo.setHasNestedClasses(!anonClass.getNestedTypes().isEmpty());
    return analyzeTypeCommon(anonClass, classInfo, srcRootPath, rootPath, context, progress);
  }

  private ClassInfo analyzeType(
      final CtType<?> type,
      final String fqn,
      final Path srcRootPath,
      final Path rootPath,
      final AnalysisContext context,
      final ProgressTracker progress) {
    final ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setInterface(type.isInterface());
    classInfo.setAbstract(type.isAbstract());
    classInfo.setAnonymous(type.isAnonymous());
    classInfo.setNestedClass(type.getDeclaringType() != null);
    classInfo.setHasNestedClasses(!type.getNestedTypes().isEmpty());
    return analyzeTypeCommon(type, classInfo, srcRootPath, rootPath, context, progress);
  }

  private ClassInfo analyzeTypeCommon(
      final CtType<?> type,
      final ClassInfo classInfo,
      final Path srcRootPath,
      final Path rootPath,
      final AnalysisContext context,
      final ProgressTracker progress) {
    classInfo.setExtendsTypes(collectExtendsTypes(type));
    classInfo.setImplementsTypes(
        type.getSuperInterfaces().stream().map(CtTypeReference::getSimpleName).toList());
    classInfo.setAnnotations(
        type.getAnnotations().stream().map(a -> a.getAnnotationType().getSimpleName()).toList());
    classInfo.setFields(new ArrayList<>());
    final Path relativePathForProgress = setFilePath(type, classInfo, srcRootPath, rootPath);
    progress.maybeProgress(relativePathForProgress);
    classInfo.setImports(collectImports(type));
    final RemovedApiDetector.RemovedApiImportInfo removedApiImports =
        RemovedApiDetector.fromImports(classInfo.getImports());
    classInfo.setLoc(calculateLoc(type));
    classInfo.setMethods(new ArrayList<>());
    addConstructorMethods(type, classInfo, removedApiImports, context);
    addDeclaredMethods(type, classInfo, removedApiImports, context);
    addDeclaredFields(type, classInfo);
    classInfo.setMethodCount(classInfo.getMethods().size());
    return classInfo;
  }

  private List<String> collectExtendsTypes(final CtType<?> type) {
    final List<String> extendsTypes = new ArrayList<>();
    final CtTypeReference<?> superRef = type.getSuperclass();
    if (superRef != null && superRef.getSimpleName() != null) {
      extendsTypes.add(superRef.getSimpleName());
    }
    return extendsTypes;
  }

  private Path setFilePath(
      final CtType<?> type,
      final ClassInfo classInfo,
      final Path srcRootPath,
      final Path rootPath) {
    if (type.getPosition() == null || type.getPosition().getCompilationUnit() == null) {
      classInfo.setFilePath(MethodInfo.UNKNOWN);
      return null;
    }
    final java.io.File file = type.getPosition().getCompilationUnit().getFile();
    if (file == null) {
      classInfo.setFilePath(MethodInfo.UNKNOWN);
      return null;
    }
    final Path absoluteFilePath = file.toPath().toAbsolutePath();
    final Path base = srcRootPath != null ? srcRootPath : rootPath;
    if (absoluteFilePath.startsWith(base)) {
      classInfo.setFilePath(base.relativize(absoluteFilePath).toString());
      return base.relativize(absoluteFilePath);
    }
    classInfo.setFilePath(rootPath.relativize(absoluteFilePath).toString());
    return rootPath.relativize(absoluteFilePath);
  }

  private List<String> collectImports(final CtType<?> type) {
    final spoon.reflect.declaration.CtCompilationUnit cu =
        type.getPosition() != null ? type.getPosition().getCompilationUnit() : null;
    if (cu == null) {
      return List.of();
    }
    return cu.getImports().stream().map(this::importAsString).toList();
  }

  private int calculateLoc(final CtType<?> type) {
    if (type.getPosition() != null && type.getPosition().isValidPosition()) {
      return type.getPosition().getEndLine() - type.getPosition().getLine() + 1;
    }
    return 0;
  }

  private void addConstructorMethods(
      final CtType<?> type,
      final ClassInfo classInfo,
      final RemovedApiDetector.RemovedApiImportInfo imports,
      final AnalysisContext context) {
    for (final CtConstructor<?> ctor : getConstructors(type)) {
      final MethodInfo methodInfo = buildMethodInfo(ctor, imports);
      final String methodKey = registerMethod(context, methodInfo, ctor, classInfo.getFqn());
      dependencyGraphBuilder.collectCalledMethods(ctor, methodKey, context);
      classInfo.addMethod(methodInfo);
    }
  }

  private java.util.Collection<CtConstructor<?>> getConstructors(final CtType<?> type) {
    final List<CtConstructor<?>> constructors = new ArrayList<>();
    if (type instanceof CtClass<?> ctClass) {
      constructors.addAll(ctClass.getConstructors());
    }
    if (type instanceof spoon.reflect.declaration.CtEnum<?> ctEnum) {
      constructors.addAll(ctEnum.getConstructors());
    }
    return constructors;
  }

  private void addDeclaredMethods(
      final CtType<?> type,
      final ClassInfo classInfo,
      final RemovedApiDetector.RemovedApiImportInfo imports,
      final AnalysisContext context) {
    for (final spoon.reflect.declaration.CtMethod<?> method : type.getMethods()) {
      final MethodInfo methodInfo = buildMethodInfo(method, imports);
      final String methodKey = registerMethod(context, methodInfo, method, classInfo.getFqn());
      dependencyGraphBuilder.collectCalledMethods(method, methodKey, context);
      classInfo.addMethod(methodInfo);
    }
  }

  private void addDeclaredFields(final CtType<?> type, final ClassInfo classInfo) {
    final Set<String> interfaceNames = SpoonHelper.collectInterfaceNames(type);
    final Set<String> abstractClassNames = SpoonHelper.collectAbstractClassNames(type);
    final Set<String> constructorParamTypes = SpoonHelper.collectConstructorParamTypes(type);
    for (final spoon.reflect.declaration.CtField<?> field : type.getFields()) {
      final String visibility = visibilityOf(field.getVisibility());
      final com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo fieldInfo =
          ResultBuilder.fieldInfo(
              field.getSimpleName(),
              field.getType() != null ? field.getType().getSimpleName() : MethodInfo.UNKNOWN,
              visibility,
              field.hasModifier(ModifierKind.STATIC),
              field.hasModifier(ModifierKind.FINAL));
      final String mockHint =
          com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MockHintStrategy
              .determineMockHint(
                  fieldInfo, interfaceNames, abstractClassNames, constructorParamTypes);
      fieldInfo.setMockHint(mockHint);
      classInfo.addField(fieldInfo);
    }
  }

  private MethodInfo buildMethodInfo(
      final CtExecutable<?> executable, final RemovedApiDetector.RemovedApiImportInfo imports) {
    final int loc =
        executable.getPosition() != null && executable.getPosition().isValidPosition()
            ? executable.getPosition().getEndLine() - executable.getPosition().getLine() + 1
            : 0;
    ModifierKind visibilityKind = null;
    boolean isStatic = false;
    if (executable instanceof spoon.reflect.declaration.CtModifiable modifiable) {
      visibilityKind = modifiable.getVisibility();
      isStatic = modifiable.hasModifier(ModifierKind.STATIC);
    }
    final String visibility = visibilityOf(visibilityKind);
    final List<String> annotations =
        executable.getAnnotations().stream()
            .map(a -> a.getAnnotationType().getSimpleName())
            .toList();
    final MethodInfo methodInfo =
        ResultBuilder.methodInfo()
            .name(executable.getSimpleName())
            .signature(executable.getSignature())
            .loc(loc)
            .visibility(visibility)
            .cyclomaticComplexity(MetricsCalculator.calculateComplexity(executable))
            .usesRemovedApis(RemovedApiChecker.usesRemovedApis(executable, imports))
            .parameterCount(executable.getParameters().size())
            .maxNestingDepth(MetricsCalculator.calculateMaxNestingDepth(executable))
            .thrownExceptions(ExceptionCollector.collectThrownExceptions(executable))
            .annotations(annotations)
            .hasLoops(hasLoops(executable))
            .hasConditionals(hasConditionals(executable))
            .isStatic(isStatic)
            .build();
    Logger.debug(
        "[STATIC-FLAG][SP] " + executable.getSignature() + " isStatic=" + methodInfo.isStatic());
    return methodInfo;
  }

  private boolean hasLoops(final CtExecutable<?> executable) {
    if (executable.getBody() == null) {
      return false;
    }
    return !executable.getElements(e -> e instanceof spoon.reflect.code.CtLoop).isEmpty();
  }

  private boolean hasConditionals(final CtExecutable<?> executable) {
    if (executable.getBody() == null) {
      return false;
    }
    // CtIf, CtSwitch, CtConditional (ternary), CtCase (usually covered by Switch
    // but safe to include)
    return !executable
        .getElements(
            e ->
                e instanceof spoon.reflect.code.CtIf
                    || e instanceof spoon.reflect.code.CtSwitch
                    || e instanceof spoon.reflect.code.CtConditional)
        .isEmpty();
  }

  private String visibilityOf(final ModifierKind visibility) {
    if (visibility == null) {
      return VISIBILITY_PACKAGE_PRIVATE;
    }
    return visibility.toString().toLowerCase(java.util.Locale.ROOT);
  }

  private String registerMethod(
      final AnalysisContext context,
      final MethodInfo methodInfo,
      final CtExecutable<?> executable,
      final String classFqn) {
    final String signature = methodInfo.getSignature();
    // Guard against null signatures from interface methods or abstract methods
    if (signature == null || classFqn == null) {
      Logger.debug(
          "Skipping method registration due to null signature or classFqn: "
              + classFqn
              + "#"
              + (methodInfo.getName() != null ? methodInfo.getName() : "unknown"));
      return null;
    }
    final String key = DependencyGraphBuilder.methodKey(classFqn, signature);
    if (key == null) {
      Logger.debug(
          "Skipping method registration due to null key for: " + classFqn + "#" + signature);
      return null;
    }
    methodInfo.setCalledMethods(new ArrayList<>());
    methodInfo.setPartOfCycle(false);
    methodInfo.setDeadCode(false);
    methodInfo.setDuplicate(false);
    methodInfo.setDuplicateGroup(null);
    // Compute code hash - may return null for abstract/interface methods
    final String codeHash = CodeHasher.computeCodeHash(executable);
    methodInfo.setCodeHash(codeHash);
    methodInfo.setSourceCode(executable.toString());
    context.getMethodInfos().put(key, methodInfo);
    // ConcurrentHashMap doesn't allow null values, so guard against them
    final String visibility = methodInfo.getVisibility();
    if (visibility != null) {
      context.getMethodVisibility().put(key, visibility);
    }
    context.getMethodHasBody().put(key, executable.getBody() != null);
    if (codeHash != null) {
      context.getMethodCodeHash().put(key, codeHash);
    }
    context.getOrCreateCallGraphEntry(key);
    context.getIncomingCounts().putIfAbsent(key, 0);
    return key;
  }

  private String buildAnonymousFqn(final CtClass<?> anonClass) {
    String enclosing = "anonymous";
    try {
      final CtType<?> parent = anonClass.getParent(CtType.class);
      if (parent != null
          && parent.getQualifiedName() != null
          && !parent.getQualifiedName().isBlank()) {
        enclosing = parent.getQualifiedName();
      }
    } catch (Exception e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to resolve enclosing type for anonymous class: " + e.getMessage()));
    }
    int line = -1;
    if (anonClass.getPosition() != null && anonClass.getPosition().isValidPosition()) {
      line = anonClass.getPosition().getLine();
    }
    return String.format("%s$anonymous@%d", enclosing, line);
  }

  private String importAsString(final spoon.reflect.declaration.CtImport imp) {
    if (imp == null) {
      return MethodInfo.UNKNOWN;
    }
    String raw = imp.toString().trim();
    if (raw.startsWith("import")) {
      raw = raw.substring("import".length()).trim();
    }
    if (raw.endsWith(";")) {
      raw = raw.substring(0, raw.length() - 1).trim();
    }
    return raw;
  }

  /** Gets the location where the exception occurred from the stack trace. */
  private String getExceptionLocation(final Exception e) {
    final StackTraceElement[] stackTrace = e.getStackTrace();
    if (stackTrace.length == 0) {
      return "unknown location";
    }
    final StackTraceElement first = stackTrace[0];
    return first.getClassName() + "." + first.getMethodName() + ":" + first.getLineNumber();
  }
  /**
   * Classifies classpath resolution errors into reason categories. Used for logging and summary
   * output.
   */
}
