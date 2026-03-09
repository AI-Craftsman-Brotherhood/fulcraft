package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathUtils;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.PathExcluder;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisError;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * JavaParser-based implementation of AnalysisPort. Orchestrates file analysis and delegates to
 * helper classes.
 */
public class JavaParserAnalyzer implements AnalysisPort {

  private static final String ENGINE_NAME = "javaparser";

  private static final String SPAN_NAME = "JavaParserAnalyzer.analyze";

  private static final String ERR_PROJECT_ROOT_NOT_FOUND =
      "Project root directory not found: %s%nPlease verify the --project-root or -p argument.";

  private static final String ERR_SOURCE_DIR_NOT_FOUND =
      "Source directory not found in project: %s%nTried: src/main/java, src, app/src/main/java,"
          + " project root%nPlease verify the project structure.";

  private static final String MSG_TEST_SOURCE_SKIPPED =
      "Detected test source directory (skipped): ";

  private static final String MSG_TEST_SOURCE_INCLUDED =
      "Detected test source directory (included): ";

  private static final String MSG_USING_SOURCE_DIR = "Using source directory: ";

  private static final String ERR_ANALYSIS_FAILED = "Analysis failed";

  private static final String ERR_UNKNOWN = "Unknown error";

  private final DependencyGraphBuilder dependencyGraphBuilder;

  private final Tracer tracer;

  private final AstTraverser astTraverser;

  private final SourcePathResolver sourcePathResolver;

  public JavaParserAnalyzer(final Tracer tracer) {
    this(tracer, new DependencyGraphBuilder(), new SourcePathResolver());
  }

  // Allow injection for testing
  public JavaParserAnalyzer(
      final Tracer tracer,
      final DependencyGraphBuilder dependencyGraphBuilder,
      final SourcePathResolver sourcePathResolver) {
    this.tracer = Objects.requireNonNull(tracer);
    this.dependencyGraphBuilder = Objects.requireNonNull(dependencyGraphBuilder);
    this.sourcePathResolver = Objects.requireNonNull(sourcePathResolver);
    this.astTraverser = new AstTraverser(this.dependencyGraphBuilder);
  }

  @Override
  public AnalysisResult analyze(final Path projectRoot, final Config config) throws IOException {
    final String projectId = config.getProject().getId();
    final String commitHash = config.getProject().getCommit();
    final List<String> excludePaths = config.getProject().getExcludePaths();
    final List<String> includePaths = config.getProject().getIncludePaths();
    final Span span = tracer.spanBuilder(SPAN_NAME).startSpan();
    try (Scope scope = span.makeCurrent()) {
      Objects.requireNonNull(
          scope,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "telemetry scope"));
      final AnalysisResult result = initializeResult(projectId, commitHash);
      final AnalysisContext context = new AnalysisContext();
      context.setCallGraphSource(ENGINE_NAME);
      context.setCallGraphResolved(false);
      final Path rootPath = validateProjectRoot(projectRoot);
      final Path srcPath = resolveSourcePath(rootPath, config);
      final boolean excludeTests =
          config.getAnalysis() == null || config.getAnalysis().getExcludeTests();
      final List<Path> excludeRoots =
          PathExcluder.buildExcludeRoots(rootPath, excludePaths, excludeTests);
      final List<Path> includeRoots = resolvePaths(rootPath, srcPath, includePaths);
      logTestSourceDirectory(rootPath, config);
      final JavaParser javaParser = createParser(srcPath, rootPath, config);
      final List<Path> javaFiles = collectJavaFiles(srcPath, excludeRoots, includeRoots);
      processFiles(javaFiles, javaParser, result, rootPath, context, span);
      com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CommonPostProcessor
          .finalizePostProcessing(result, context);
      return com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.ModelFreezer
          .freezeAnalysisResult(result);
    } catch (IOException | RuntimeException e) {
      handleError(span, e);
      throw e;
    } catch (Exception e) {
      handleError(span, e);
      throw new IllegalStateException(ERR_ANALYSIS_FAILED, e);
    } finally {
      span.end();
    }
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

  private List<Path> resolvePaths(
      final Path rootPath, final Path srcPath, final List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return List.of();
    }
    return paths.stream()
        .map(
            p -> {
              Path path = java.nio.file.Paths.get(p);
              if (!path.isAbsolute()) {
                final Path projectResolved = rootPath.resolve(path);
                if (Files.exists(projectResolved)) {
                  path = projectResolved;
                } else {
                  // Try resolving against source root (e.g. for package paths)
                  final Path srcResolved = srcPath.resolve(path);
                  if (Files.exists(srcResolved)) {
                    path = srcResolved;
                  } else {
                    // Fallback to project-relative
                    path = projectResolved;
                  }
                }
              }
              return path.normalize();
            })
        .toList();
  }

  private AnalysisResult initializeResult(final String projectId, final String commitHash) {
    final AnalysisResult result = new AnalysisResult();
    result.setProjectId(projectId);
    result.setCommitHash(commitHash);
    result.setClasses(Collections.synchronizedList(new ArrayList<>()));
    result.setAnalysisErrors(Collections.synchronizedList(new ArrayList<>()));
    return result;
  }

  private Path validateProjectRoot(final Path projectRoot) throws IOException {
    final Path rootPath = projectRoot.toAbsolutePath();
    if (!Files.exists(rootPath)) {
      throw new IOException(String.format(ERR_PROJECT_ROOT_NOT_FOUND, rootPath));
    }
    return rootPath;
  }

  private Path resolveSourcePath(final Path rootPath, final Config config) throws IOException {
    final var srcDirs = sourcePathResolver.resolve(rootPath, config);
    final var srcPathOptional = srcDirs.mainSource();
    if (srcPathOptional.isEmpty()) {
      throw new IOException(String.format(ERR_SOURCE_DIR_NOT_FOUND, rootPath.toAbsolutePath()));
    }
    final Path srcPath = srcPathOptional.get();
    final String srcPathString = srcPath.toAbsolutePath().toString();
    Logger.infoOnce("analysis.source_dir:" + srcPathString, MSG_USING_SOURCE_DIR + srcPathString);
    return srcPath;
  }

  private void logTestSourceDirectory(final Path rootPath, final Config config) {
    final var srcDirs = sourcePathResolver.resolve(rootPath, config);
    final var testSourceOptional = srcDirs.testSource();
    final boolean excludeTests =
        config == null || config.getAnalysis() == null || config.getAnalysis().getExcludeTests();
    testSourceOptional.ifPresent(
        path -> {
          final String testPathString = path.toAbsolutePath().toString();
          final String message =
              (excludeTests ? MSG_TEST_SOURCE_SKIPPED : MSG_TEST_SOURCE_INCLUDED) + testPathString;
          Logger.infoOnce(
              "analysis.test_source:"
                  + (excludeTests ? "skipped" : "included")
                  + ":"
                  + testPathString,
              message);
        });
  }

  private JavaParser createParser(final Path srcPath, final Path projectRoot, final Config config) {
    final CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
    combinedTypeSolver.add(new ReflectionTypeSolver());
    combinedTypeSolver.add(
        new com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver(
            srcPath));
    // Add dependency JARs for improved type resolution
    addDependencyJarsToTypeSolver(combinedTypeSolver, projectRoot, config);
    final com.github.javaparser.symbolsolver.JavaSymbolSolver symbolSolver =
        new com.github.javaparser.symbolsolver.JavaSymbolSolver(combinedTypeSolver);
    final ParserConfiguration parserConfiguration = new ParserConfiguration();
    parserConfiguration.setSymbolResolver(symbolSolver);
    return new JavaParser(parserConfiguration);
  }

  private void addDependencyJarsToTypeSolver(
      final CombinedTypeSolver typeSolver, final Path projectRoot, final Config config) {
    String classpathMode = "AUTO";
    if (config != null && config.getAnalysis() != null) {
      classpathMode = config.getAnalysis().getClasspathMode();
    }
    if ("OFF".equalsIgnoreCase(classpathMode)) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Classpath mode OFF - skipping dependency JAR resolution"));
      return;
    }
    final com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult
        resolution =
            com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath.ClasspathResolver
                .resolveCompileClasspath(projectRoot, config);
    com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath.ClasspathResolutionLogger
        .logAttempts(resolution);
    if (resolution.isEmpty()) {
      final String message =
          "Classpath resolution returned empty. External dependencies will not be resolved.";
      if ("STRICT".equalsIgnoreCase(classpathMode)) {
        throw new IllegalStateException(
            "[STRICT MODE] " + message + " Set 'analysis.classpath.mode: AUTO' to continue.");
      }
      return;
    }
    int addedCount = 0;
    for (final Path jar : resolution.getEntries()) {
      try {
        typeSolver.add(
            new com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver(jar));
        addedCount++;
      } catch (Exception e) {
        Logger.debug(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Failed to add JAR to type solver: " + jar + " - " + e.getMessage()));
      }
    }
    if (addedCount > 0) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Added " + addedCount + " dependency JARs to type solver"));
    } else {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Resolved classpath entries but failed to add any JARs to type solver"));
    }
  }

  private List<Path> collectJavaFiles(
      final Path srcPath, final List<Path> excludeRoots, final List<Path> includeRoots)
      throws IOException {
    try (Stream<Path> paths = Files.walk(srcPath)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(PathUtils.JAVA_EXTENSION))
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

  private void processFiles(
      final List<Path> javaFiles,
      final JavaParser javaParser,
      final AnalysisResult result,
      final Path projectRoot,
      final AnalysisContext context,
      final Span span) {
    final int totalFiles = javaFiles.size();
    final java.util.concurrent.atomic.AtomicInteger processed =
        new java.util.concurrent.atomic.AtomicInteger(0);
    span.setAttribute("files.total", totalFiles);
    final long startTime = System.currentTimeMillis();
    final ParserConfiguration config = javaParser.getParserConfiguration();
    javaFiles.stream()
        .forEachOrdered(
            path -> {
              final int current = processed.incrementAndGet();
              final String relativePath = projectRoot.relativize(path).toString();
              synchronized (Logger.class) {
                Logger.progressBar(current, totalFiles, relativePath, startTime);
              }
              analyzeFile(path, new JavaParser(config), result, projectRoot, context);
            });
    Logger.progressCompleteOnce(totalFiles);
  }

  private void analyzeFile(
      final Path path,
      final JavaParser javaParser,
      final AnalysisResult result,
      final Path projectRoot,
      final AnalysisContext context) {
    try {
      final ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
      final java.util.Optional<CompilationUnit> cuOptional = parseResult.getResult();
      if (!parseResult.isSuccessful() || cuOptional.isEmpty()) {
        addParseError(result, projectRoot, path, parseResult);
        return;
      }
      final CompilationUnit cu = cuOptional.get();
      final List<String> importStrings = toImportStrings(cu);
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports =
          RemovedApiDetector.fromImports(importStrings);
      astTraverser.traverse(
          cu, result, projectRoot, path, importStrings, removedApiImports, context);
    } catch (Exception e) {
      // Enhanced failure logging with exception class for diagnostics
      final String msg =
          String.format(
              "[%s] %s",
              e.getClass().getSimpleName(), Objects.toString(e.getMessage(), e.toString()));
      addError(result, projectRoot, path, msg);
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JavaParser] Failed to analyze file: " + path + " - " + msg));
    }
  }

  private void addParseError(
      final AnalysisResult result,
      final Path projectRoot,
      final Path path,
      final ParseResult<?> parseResult) {
    final String filePath = projectRoot.relativize(path).toString();
    final String message =
        parseResult.isSuccessful()
            ? "Parsed successfully but no compilation unit was produced."
            : parseResult.getProblems().toString();
    final AnalysisError error = new AnalysisError(filePath, message, null);
    result.getAnalysisErrors().add(error);
  }

  private void addError(
      final AnalysisResult result, final Path projectRoot, final Path path, final String message) {
    final String filePath = projectRoot.relativize(path).toString();
    final AnalysisError error = new AnalysisError(filePath, message, null);
    result.getAnalysisErrors().add(error);
  }

  private List<String> toImportStrings(final CompilationUnit cu) {
    return cu.getImports().stream()
        .map(
            i -> {
              final StringBuilder name = new StringBuilder();
              if (i.isStatic()) {
                name.append("static ");
              }
              name.append(i.getNameAsString());
              if (i.isAsterisk()) {
                name.append(".*");
              }
              return name.toString();
            })
        .toList();
  }

  private void handleError(final Span span, final Exception e) {
    if (e != null) {
      span.recordException(e);
      final String errorMessage = e.getMessage() != null ? e.getMessage() : ERR_UNKNOWN;
      span.setStatus(StatusCode.ERROR, Objects.requireNonNull(errorMessage));
    } else {
      span.setStatus(StatusCode.ERROR, ERR_UNKNOWN + " (null exception)");
    }
  }
}
