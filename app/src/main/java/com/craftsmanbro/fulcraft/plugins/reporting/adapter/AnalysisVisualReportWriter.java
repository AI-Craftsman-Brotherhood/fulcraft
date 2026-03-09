package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.impl.CoverageLoaderAdapterFactory;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates a visual HTML report for analysis results using a template.
 *
 * <p>The output includes a circular relationship map and summary lists.
 */
public class AnalysisVisualReportWriter {

  private static final String OUTPUT_FILENAME = "analysis_visual.html";

  private static final String REPORT_MARKDOWN_FILENAME = "report.md";

  private static final List<String> RAW_REPORT_CANDIDATE_FILENAMES =
      List.of("report.md", "report.html", "report.json", "report.yaml", "report.yml");

  private static final int EDGE_LIMIT = 200;

  private static final int CLASS_EDGE_LIMIT_PER_CLASS = 12;

  private static final int FILE_EDGE_LIMIT_PER_FILE = 12;

  private static final int EXTERNAL_EDGE_LIMIT_PER_PACKAGE = 6;

  private static final int EXTERNAL_EDGE_LIMIT_PER_FILE = 6;

  private final JsonServicePort jsonService;

  public AnalysisVisualReportWriter() {
    this.jsonService = new DefaultJsonService();
  }

  /**
   * Writes a visual report into the given output directory.
   *
   * @param result analysis result
   * @param context run context
   * @param outputDir report output directory
   * @param config configuration
   * @return the output file path
   * @throws ReportWriteException on template or IO failure
   */
  public Path writeReport(
      final AnalysisResult result,
      final RunContext context,
      final Path outputDir,
      final Config config)
      throws ReportWriteException {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "context must not be null"));
    Objects.requireNonNull(
        outputDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "outputDir must not be null"));
    try {
      final VisualReportData data = buildData(result, context, config);
      final String json = jsonService.toJson(data);
      final String safeJson = json.replace("</", "<\\/");
      final String markdownJson = jsonService.toJson(loadReportMarkdown(outputDir));
      final String safeMarkdownJson = markdownJson.replace("</", "<\\/");
      final String rawReportHref = resolveRawReportHref(outputDir);
      final String template = loadTemplate(config);
      final String content =
          template
              .replace("{{TITLE}}", escapeHtml(buildTitle(context)))
              .replace("{{INITIAL_LOCALE}}", escapeHtml(resolveInitialLocaleTag()))
              .replace("{{GENERATED_AT}}", escapeHtml(data.generatedAt))
              .replace("{{DATA_JSON}}", safeJson)
              .replace("{{REPORT_MARKDOWN_JSON}}", safeMarkdownJson)
              .replace("{{REPORT_RAW_HREF}}", escapeHtml(rawReportHref));
      Files.createDirectories(outputDir);
      final Path outputPath = outputDir.resolve(OUTPUT_FILENAME);
      Files.writeString(outputPath, content, StandardCharsets.UTF_8);
      return outputPath;
    } catch (IOException e) {
      throw new ReportWriteException(MessageSource.getMessage("report.visual.write_failed"), e);
    }
  }

  private VisualReportData buildData(
      final AnalysisResult result, final RunContext context, final Config config) {
    final List<ClassInfo> classes = resolveClasses(result);
    final ClassAggregationData classData = collectClassAggregations(classes, context, config);
    final Map<String, Double> packageCoverageRates = toCoverageRates(classData.packageCoverage);
    final Map<String, Double> fileCoverageRates = toCoverageRates(classData.fileCoverage);
    final List<PackageNode> packages =
        buildPackageNodes(classData.packageAgg, packageCoverageRates);
    final List<FileNode> files = buildFileNodes(classData.fileAgg, fileCoverageRates);
    final EdgeData edgeData =
        buildEdges(
            classes, classData.classNodes.keySet(), classData.simpleNameMap, classData.classToFile);
    final List<Edge> packageEdges = buildEdgeList(edgeData.packageEdges, EDGE_LIMIT);
    final List<Edge> classEdges =
        buildLimitedEdgeList(edgeData.classEdges, CLASS_EDGE_LIMIT_PER_CLASS);
    final List<Edge> fileEdges = buildLimitedEdgeList(edgeData.fileEdges, FILE_EDGE_LIMIT_PER_FILE);
    final List<Edge> externalEdges =
        buildLimitedEdgeList(edgeData.externalEdges, EXTERNAL_EDGE_LIMIT_PER_PACKAGE);
    final List<Edge> fileExternalEdges =
        buildLimitedEdgeList(edgeData.fileExternalEdges, EXTERNAL_EDGE_LIMIT_PER_FILE);
    final List<ExternalLibrary> externalLibraries =
        buildExternalLibraries(edgeData.externalLibraries);
    final Summary summary = buildSummary(classData);
    final int complexityThreshold = resolveComplexityThreshold(config);
    return new VisualReportData(
        resolveProjectId(context),
        context.getRunId(),
        Instant.now().toString(),
        summary,
        packages,
        files,
        new ArrayList<>(classData.classNodes.values()),
        packageEdges,
        classEdges,
        fileEdges,
        externalEdges,
        fileExternalEdges,
        externalLibraries,
        complexityThreshold);
  }

  private List<ClassInfo> resolveClasses(final AnalysisResult result) {
    if (result == null || result.getClasses() == null) {
      return List.of();
    }
    return result.getClasses();
  }

  private ClassAggregationData collectClassAggregations(
      final List<ClassInfo> classes, final RunContext context, final Config config) {
    final Map<String, ClassNode> classNodes = new LinkedHashMap<>();
    final Map<String, PackageAggregation> packageAgg = new LinkedHashMap<>();
    final Map<String, FileAggregation> fileAgg = new LinkedHashMap<>();
    final Map<String, String> classToFile = new HashMap<>();
    final Map<String, CoverageAggregate> packageCoverage = new HashMap<>();
    final Map<String, CoverageAggregate> fileCoverage = new HashMap<>();
    final ClassAggregationMaps aggregationMaps =
        new ClassAggregationMaps(classNodes, packageAgg, fileAgg, classToFile);
    final Map<String, String> simpleNameMap = buildSimpleNameMap(classes);
    final CoverageLoaderPort coverageLoader = resolveCoverageLoader(context, config);
    final ClassCoverageState coverageState =
        new ClassCoverageState(coverageLoader, packageCoverage, fileCoverage);
    final ClassTotals totals = new ClassTotals();
    for (final ClassInfo classInfo : classes) {
      if (!isValidClassInfo(classInfo)) {
        continue;
      }
      aggregateClass(classInfo, context, aggregationMaps, coverageState, totals);
    }
    return new ClassAggregationData(
        classNodes,
        packageAgg,
        fileAgg,
        classToFile,
        packageCoverage,
        fileCoverage,
        simpleNameMap,
        totals.totalClasses,
        totals.totalMethods,
        totals.totalLoc,
        totals.totalComplexity,
        totals.maxComplexity);
  }

  private List<PackageNode> buildPackageNodes(
      final Map<String, PackageAggregation> packageAgg,
      final Map<String, Double> packageCoverageRates) {
    final List<PackageNode> packages = new ArrayList<>();
    for (final PackageAggregation aggregation : packageAgg.values()) {
      final Double coverage = packageCoverageRates.get(aggregation.packageName);
      packages.add(aggregation.toNode(coverage));
    }
    return packages;
  }

  private List<FileNode> buildFileNodes(
      final Map<String, FileAggregation> fileAgg, final Map<String, Double> fileCoverageRates) {
    final List<FileNode> files = new ArrayList<>();
    for (final FileAggregation aggregation : fileAgg.values()) {
      final Double coverage = fileCoverageRates.get(aggregation.filePath);
      files.add(aggregation.toNode(coverage));
    }
    return files;
  }

  private EdgeData buildEdges(
      final List<ClassInfo> classes,
      final Set<String> internalClasses,
      final Map<String, String> simpleNameMap,
      final Map<String, String> classToFile) {
    final Map<String, Map<String, Integer>> packageEdges = new HashMap<>();
    final Map<String, Map<String, Integer>> classEdges = new HashMap<>();
    final Map<String, Map<String, Integer>> fileEdges = new HashMap<>();
    final Map<String, Map<String, Integer>> externalEdges = new HashMap<>();
    final Map<String, Map<String, Integer>> fileExternalEdges = new HashMap<>();
    final Map<String, Integer> externalLibraries = new HashMap<>();
    final EdgeLookupContext lookupContext =
        new EdgeLookupContext(internalClasses, simpleNameMap, classToFile);
    final EdgeAggregationMaps edgeAggregationMaps =
        new EdgeAggregationMaps(
            packageEdges,
            classEdges,
            fileEdges,
            externalEdges,
            fileExternalEdges,
            externalLibraries);
    for (final ClassInfo classInfo : classes) {
      final EdgeSource source = resolveEdgeSource(classInfo, classToFile);
      if (source == null) {
        continue;
      }
      for (final MethodInfo method : classInfo.getMethods()) {
        for (final CalledMethodRef ref : method.getCalledMethodRefs()) {
          handleCalledMethodRef(ref, source, lookupContext, edgeAggregationMaps);
        }
      }
    }
    return new EdgeData(
        packageEdges, classEdges, fileEdges, externalEdges, fileExternalEdges, externalLibraries);
  }

  private List<Edge> buildEdgeList(
      final Map<String, Map<String, Integer>> edges, final int maxEdges) {
    final List<Edge> edgeList = new ArrayList<>();
    for (final Map.Entry<String, Map<String, Integer>> entry : edges.entrySet()) {
      for (final Map.Entry<String, Integer> target : entry.getValue().entrySet()) {
        edgeList.add(new Edge(entry.getKey(), target.getKey(), target.getValue()));
      }
    }
    edgeList.sort(Comparator.comparingInt(Edge::weight).reversed());
    if (maxEdges > 0 && edgeList.size() > maxEdges) {
      return new ArrayList<>(edgeList.subList(0, maxEdges));
    }
    return edgeList;
  }

  private List<Edge> buildLimitedEdgeList(
      final Map<String, Map<String, Integer>> edges, final int perSourceLimit) {
    final List<Edge> edgeList = new ArrayList<>();
    for (final Map.Entry<String, Map<String, Integer>> entry : edges.entrySet()) {
      final List<Map.Entry<String, Integer>> sorted =
          entry.getValue().entrySet().stream()
              .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
              .limit(perSourceLimit)
              .toList();
      for (final Map.Entry<String, Integer> target : sorted) {
        edgeList.add(new Edge(entry.getKey(), target.getKey(), target.getValue()));
      }
    }
    return edgeList;
  }

  private List<ExternalLibrary> buildExternalLibraries(
      final Map<String, Integer> externalLibraries) {
    final List<ExternalLibrary> externalList = new ArrayList<>();
    externalLibraries.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(entry -> externalList.add(new ExternalLibrary(entry.getKey(), entry.getValue())));
    return externalList;
  }

  private Summary buildSummary(final ClassAggregationData classData) {
    final double avgComplexity =
        classData.totalMethods > 0
            ? (double) classData.totalComplexity / classData.totalMethods
            : 0.0;
    return new Summary(
        classData.totalClasses,
        classData.totalMethods,
        classData.totalLoc,
        classData.totalComplexity,
        avgComplexity,
        classData.maxComplexity);
  }

  private String buildTitle(final RunContext context) {
    final String projectId = resolveProjectId(context);
    return MessageSource.getMessage("report.visual.title", projectId);
  }

  private String resolveProjectId(final RunContext context) {
    if (context.getConfig() != null
        && context.getConfig().getProject() != null
        && context.getConfig().getProject().getId() != null
        && !context.getConfig().getProject().getId().isBlank()) {
      return context.getConfig().getProject().getId();
    }
    return MessageSource.getMessage("report.value.unknown_word");
  }

  private int resolveComplexityThreshold(final Config config) {
    if (config == null || config.getSelectionRules() == null) {
      return 0;
    }
    final Integer maxCyclomatic = config.getSelectionRules().getComplexity().getMaxCyclomatic();
    return maxCyclomatic != null ? maxCyclomatic : 0;
  }

  private CoverageLoaderPort resolveCoverageLoader(final RunContext context, final Config config) {
    if (context == null || config == null || context.getProjectRoot() == null) {
      return null;
    }
    final CoverageLoaderPort loader =
        CoverageLoaderAdapterFactory.createPort(context.getProjectRoot(), config);
    if (loader == null || !loader.isAvailable()) {
      return null;
    }
    return loader;
  }

  private ComplexitySummary summarizeClassComplexity(final ClassInfo classInfo) {
    int methodCount = 0;
    int complexitySum = 0;
    int complexityMax = 0;
    for (final MethodInfo method :
        MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo)) {
      if (method == null) {
        continue;
      }
      methodCount++;
      final int complexity = method.getCyclomaticComplexity();
      complexitySum += complexity;
      complexityMax = Math.max(complexityMax, complexity);
    }
    final double avg = methodCount > 0 ? (double) complexitySum / methodCount : 0.0;
    return new ComplexitySummary(methodCount, complexitySum, avg, complexityMax);
  }

  private String resolveTargetClass(
      final CalledMethodRef ref,
      final Set<String> internalClasses,
      final Map<String, String> simpleNameMap) {
    if (ref == null) {
      return null;
    }
    String candidate = ref.getResolved();
    if (candidate == null || candidate.isBlank()) {
      candidate = ref.getRaw();
    }
    if (candidate == null || candidate.isBlank()) {
      return null;
    }
    String cleaned = candidate;
    final int paren = cleaned.indexOf('(');
    if (paren > 0) {
      cleaned = cleaned.substring(0, paren);
    }
    final int hash = cleaned.indexOf('#');
    if (hash > 0) {
      cleaned = cleaned.substring(0, hash);
    }
    final int colon = cleaned.indexOf("::");
    if (colon > 0) {
      cleaned = cleaned.substring(0, colon);
    }
    final String normalized = cleaned.replace('$', '.');
    if (internalClasses.contains(cleaned)) {
      return cleaned;
    }
    if (internalClasses.contains(normalized)) {
      return normalized;
    }
    if (!cleaned.contains(".") && simpleNameMap.containsKey(cleaned)) {
      return simpleNameMap.get(cleaned);
    }
    if (!normalized.contains(".") && simpleNameMap.containsKey(normalized)) {
      return simpleNameMap.get(normalized);
    }
    return cleaned;
  }

  private boolean isValidClassInfo(final ClassInfo classInfo) {
    return classInfo != null && classInfo.getFqn() != null;
  }

  private String normalizePackageName(final String packageName) {
    return packageName == null ? "" : packageName;
  }

  private void aggregateClass(
      final ClassInfo classInfo,
      final RunContext context,
      final ClassAggregationMaps aggregationMaps,
      final ClassCoverageState coverageState,
      final ClassTotals totals) {
    final String fqn = classInfo.getFqn();
    final String packageName = normalizePackageName(DocumentUtils.getPackageName(fqn));
    final String simpleName = DocumentUtils.getSimpleName(fqn);
    final ComplexitySummary summary = summarizeClassComplexity(classInfo);
    final int loc = classInfo.getLoc();
    totals.addClass(summary, loc);
    final String filePath =
        addFileAggregation(classInfo, context, fqn, packageName, summary, loc, aggregationMaps);
    aggregationMaps
        .classNodes()
        .put(
            fqn,
            new ClassNode(
                fqn,
                simpleName,
                packageName,
                loc,
                summary.methodCount,
                summary.complexitySum,
                summary.complexityAvg,
                summary.complexityMax,
                DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html"),
                filePath));
    aggregationMaps
        .packageAgg()
        .computeIfAbsent(packageName, PackageAggregation::new)
        .addClass(summary, loc);
    applyCoverage(
        coverageState.coverageLoader(),
        fqn,
        loc,
        packageName,
        filePath,
        coverageState.packageCoverage(),
        coverageState.fileCoverage());
  }

  private String addFileAggregation(
      final ClassInfo classInfo,
      final RunContext context,
      final String fqn,
      final String packageName,
      final ComplexitySummary summary,
      final int loc,
      final ClassAggregationMaps aggregationMaps) {
    final String filePath = normalizeFilePath(classInfo.getFilePath(), context);
    if (filePath == null || filePath.isBlank()) {
      return null;
    }
    aggregationMaps.classToFile().put(fqn, filePath);
    aggregationMaps
        .fileAgg()
        .computeIfAbsent(
            filePath, key -> new FileAggregation(key, extractFileName(key), packageName))
        .addClass(summary, loc, packageName);
    return filePath;
  }

  private void applyCoverage(
      final CoverageLoaderPort coverageLoader,
      final String fqn,
      final int loc,
      final String packageName,
      final String filePath,
      final Map<String, CoverageAggregate> packageCoverage,
      final Map<String, CoverageAggregate> fileCoverage) {
    if (coverageLoader == null) {
      return;
    }
    final double coverage = coverageLoader.getLineCoverage(fqn);
    if (coverage < 0) {
      return;
    }
    final int weight = Math.max(1, loc);
    packageCoverage
        .computeIfAbsent(packageName, key -> new CoverageAggregate())
        .addCoverage(coverage, weight);
    if (filePath != null && !filePath.isBlank()) {
      fileCoverage
          .computeIfAbsent(filePath, key -> new CoverageAggregate())
          .addCoverage(coverage, weight);
    }
  }

  private EdgeSource resolveEdgeSource(
      final ClassInfo classInfo, final Map<String, String> classToFile) {
    if (!isValidClassInfo(classInfo)) {
      return null;
    }
    final String fromClass = classInfo.getFqn();
    final String fromPackage = normalizePackageName(DocumentUtils.getPackageName(fromClass));
    final String fromFile = classToFile.get(fromClass);
    return new EdgeSource(fromClass, fromPackage, fromFile);
  }

  private void handleCalledMethodRef(
      final CalledMethodRef ref,
      final EdgeSource source,
      final EdgeLookupContext lookupContext,
      final EdgeAggregationMaps edgeAggregationMaps) {
    final String targetClass =
        resolveTargetClass(ref, lookupContext.internalClasses(), lookupContext.simpleNameMap());
    if (targetClass == null) {
      return;
    }
    if (lookupContext.internalClasses().contains(targetClass)) {
      addInternalEdges(
          source,
          targetClass,
          lookupContext.classToFile(),
          edgeAggregationMaps.packageEdges(),
          edgeAggregationMaps.classEdges(),
          edgeAggregationMaps.fileEdges());
      return;
    }
    addExternalEdges(
        source,
        targetClass,
        edgeAggregationMaps.externalEdges(),
        edgeAggregationMaps.fileExternalEdges(),
        edgeAggregationMaps.externalLibraries());
  }

  private void addInternalEdges(
      final EdgeSource source,
      final String targetClass,
      final Map<String, String> classToFile,
      final Map<String, Map<String, Integer>> packageEdges,
      final Map<String, Map<String, Integer>> classEdges,
      final Map<String, Map<String, Integer>> fileEdges) {
    if (!source.fromClass().equals(targetClass)) {
      incrementEdge(classEdges, source.fromClass(), targetClass);
    }
    final String targetFile = classToFile.get(targetClass);
    if (source.fromFile() != null && targetFile != null && !source.fromFile().equals(targetFile)) {
      incrementEdge(fileEdges, source.fromFile(), targetFile);
    }
    final String toPackage = DocumentUtils.getPackageName(targetClass);
    if (!source.fromPackage().equals(toPackage)) {
      incrementEdge(packageEdges, source.fromPackage(), toPackage);
    }
  }

  private void addExternalEdges(
      final EdgeSource source,
      final String targetClass,
      final Map<String, Map<String, Integer>> externalEdges,
      final Map<String, Map<String, Integer>> fileExternalEdges,
      final Map<String, Integer> externalLibraries) {
    final String library = extractLibraryKey(targetClass);
    if (library == null || isStandardLibrary(library)) {
      return;
    }
    externalLibraries.merge(library, 1, (a, b) -> a + b);
    incrementEdge(externalEdges, source.fromPackage(), library);
    if (source.fromFile() != null && !source.fromFile().isBlank()) {
      incrementEdge(fileExternalEdges, source.fromFile(), library);
    }
  }

  private Map<String, String> buildSimpleNameMap(final List<ClassInfo> classes) {
    final Map<String, String> map = new HashMap<>();
    final Set<String> duplicates = new HashSet<>();
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null || classInfo.getFqn() == null) {
        continue;
      }
      final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
      if (map.containsKey(simpleName)) {
        duplicates.add(simpleName);
      } else {
        map.put(simpleName, classInfo.getFqn());
      }
    }
    for (final String duplicate : duplicates) {
      map.remove(duplicate);
    }
    return map;
  }

  private String extractLibraryKey(final String className) {
    if (className == null) {
      return null;
    }
    final String cleaned = className.trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    final String[] parts = cleaned.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    return parts[0] + "." + parts[1];
  }

  private boolean isStandardLibrary(final String key) {
    return key.startsWith("java")
        || key.startsWith("javax")
        || key.startsWith("jdk")
        || key.startsWith("sun")
        || key.startsWith("com.sun")
        || key.startsWith("org.w3c")
        || key.startsWith("org.xml");
  }

  private void incrementEdge(
      final Map<String, Map<String, Integer>> edges, final String from, final String to) {
    edges.computeIfAbsent(from, key -> new HashMap<>()).merge(to, 1, (a, b) -> a + b);
  }

  private String loadTemplate(final Config config) throws IOException {
    final String path = resolveTemplatePath(config);
    final String normalizedPath = normalizeResourcePath(path);
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(normalizedPath)) {
      if (input == null) {
        throw new IOException(MessageSource.getMessage("report.visual.template_not_found", path));
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String resolveTemplatePath(final Config config) {
    if (config != null && config.getAnalysis() != null) {
      final String custom = config.getAnalysis().getVisualReportTemplate();
      if (custom != null && !custom.isBlank()) {
        return custom;
      }
    }
    return AnalysisConfig.DEFAULT_VISUAL_REPORT_TEMPLATE;
  }

  private String normalizeResourcePath(final String path) {
    if (path == null) {
      return "";
    }
    final String trimmed = path.trim();
    if (trimmed.startsWith("/")) {
      return trimmed.substring(1);
    }
    return trimmed;
  }

  private String loadReportMarkdown(final Path outputDir) {
    if (outputDir == null) {
      return "";
    }
    final Path markdownPath = outputDir.resolve(REPORT_MARKDOWN_FILENAME);
    if (!Files.isRegularFile(markdownPath)) {
      return "";
    }
    try {
      return Files.readString(markdownPath, StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      return "";
    }
  }

  private String resolveRawReportHref(final Path outputDir) {
    if (outputDir == null) {
      return REPORT_MARKDOWN_FILENAME;
    }
    for (final String filename : RAW_REPORT_CANDIDATE_FILENAMES) {
      if (Files.isRegularFile(outputDir.resolve(filename))) {
        return filename;
      }
    }
    return REPORT_MARKDOWN_FILENAME;
  }

  private String escapeHtml(final String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private String resolveInitialLocaleTag() {
    final var locale = MessageSource.getLocale();
    if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
      return "en";
    }
    return locale.getLanguage();
  }

  private record VisualReportData(
      String projectId,
      String runId,
      String generatedAt,
      Summary summary,
      List<PackageNode> packages,
      List<FileNode> files,
      List<ClassNode> classes,
      List<Edge> packageEdges,
      List<Edge> classEdges,
      List<Edge> fileEdges,
      List<Edge> externalEdges,
      List<Edge> fileExternalEdges,
      List<ExternalLibrary> externalLibraries,
      int complexityThreshold) {}

  private record Summary(
      int totalClasses,
      int totalMethods,
      int totalLoc,
      int complexitySum,
      double complexityAvg,
      int complexityMax) {}

  private record PackageNode(
      String id,
      String shortName,
      int classCount,
      int methodCount,
      int loc,
      int complexitySum,
      double complexityAvg,
      int complexityMax,
      Double lineCoverage) {}

  private record ClassNode(
      String id,
      String simpleName,
      String packageName,
      int loc,
      int methodCount,
      int complexitySum,
      double complexityAvg,
      int complexityMax,
      String detailLink,
      String filePath) {}

  private record FileNode(
      String id,
      String name,
      String path,
      String packageName,
      int classCount,
      int methodCount,
      int loc,
      int complexitySum,
      double complexityAvg,
      int complexityMax,
      Double lineCoverage) {}

  private record Edge(String from, String to, int weight) {}

  private record ExternalLibrary(String id, int callCount) {}

  private record EdgeSource(String fromClass, String fromPackage, String fromFile) {}

  private record ComplexitySummary(
      int methodCount, int complexitySum, double complexityAvg, int complexityMax) {}

  private record ClassAggregationMaps(
      Map<String, ClassNode> classNodes,
      Map<String, PackageAggregation> packageAgg,
      Map<String, FileAggregation> fileAgg,
      Map<String, String> classToFile) {}

  private record ClassCoverageState(
      CoverageLoaderPort coverageLoader,
      Map<String, CoverageAggregate> packageCoverage,
      Map<String, CoverageAggregate> fileCoverage) {}

  private record ClassAggregationData(
      Map<String, ClassNode> classNodes,
      Map<String, PackageAggregation> packageAgg,
      Map<String, FileAggregation> fileAgg,
      Map<String, String> classToFile,
      Map<String, CoverageAggregate> packageCoverage,
      Map<String, CoverageAggregate> fileCoverage,
      Map<String, String> simpleNameMap,
      int totalClasses,
      int totalMethods,
      int totalLoc,
      int totalComplexity,
      int maxComplexity) {}

  private record EdgeData(
      Map<String, Map<String, Integer>> packageEdges,
      Map<String, Map<String, Integer>> classEdges,
      Map<String, Map<String, Integer>> fileEdges,
      Map<String, Map<String, Integer>> externalEdges,
      Map<String, Map<String, Integer>> fileExternalEdges,
      Map<String, Integer> externalLibraries) {}

  private record EdgeLookupContext(
      Set<String> internalClasses,
      Map<String, String> simpleNameMap,
      Map<String, String> classToFile) {}

  private record EdgeAggregationMaps(
      Map<String, Map<String, Integer>> packageEdges,
      Map<String, Map<String, Integer>> classEdges,
      Map<String, Map<String, Integer>> fileEdges,
      Map<String, Map<String, Integer>> externalEdges,
      Map<String, Map<String, Integer>> fileExternalEdges,
      Map<String, Integer> externalLibraries) {}

  private static final class PackageAggregation {

    private final String packageName;

    private int classCount;

    private int methodCount;

    private int loc;

    private int complexitySum;

    private int complexityMax;

    private PackageAggregation(final String packageName) {
      this.packageName = packageName;
    }

    private void addClass(final ComplexitySummary summary, final int classLoc) {
      classCount++;
      methodCount += summary.methodCount;
      loc += classLoc;
      complexitySum += summary.complexitySum;
      complexityMax = Math.max(complexityMax, summary.complexityMax);
    }

    private PackageNode toNode(final Double lineCoverage) {
      final double avg = methodCount > 0 ? (double) complexitySum / methodCount : 0.0;
      String shortName = packageName;
      final int lastDot = packageName.lastIndexOf('.');
      if (lastDot >= 0 && lastDot + 1 < packageName.length()) {
        shortName = packageName.substring(lastDot + 1);
      }
      return new PackageNode(
          packageName,
          shortName,
          classCount,
          methodCount,
          loc,
          complexitySum,
          avg,
          complexityMax,
          lineCoverage);
    }
  }

  private static final class FileAggregation {

    private final String filePath;

    private final String fileName;

    private String packageName;

    private int classCount;

    private int methodCount;

    private int loc;

    private int complexitySum;

    private int complexityMax;

    private FileAggregation(
        final String filePath, final String fileName, final String packageName) {
      this.filePath = filePath;
      this.fileName = fileName;
      this.packageName = packageName;
    }

    private void addClass(
        final ComplexitySummary summary, final int classLoc, final String pkgName) {
      classCount++;
      methodCount += summary.methodCount;
      loc += classLoc;
      complexitySum += summary.complexitySum;
      complexityMax = Math.max(complexityMax, summary.complexityMax);
      if ((packageName == null || packageName.isBlank()) && pkgName != null) {
        packageName = pkgName;
      }
    }

    private FileNode toNode(final Double lineCoverage) {
      final double avg = methodCount > 0 ? (double) complexitySum / methodCount : 0.0;
      return new FileNode(
          filePath,
          fileName,
          filePath,
          packageName == null ? "" : packageName,
          classCount,
          methodCount,
          loc,
          complexitySum,
          avg,
          complexityMax,
          lineCoverage);
    }
  }

  private static final class CoverageAggregate {

    private double weightedSum;

    private int totalWeight;

    private void addCoverage(final double coveragePercent, final int weight) {
      final int safeWeight = Math.max(1, weight);
      weightedSum += coveragePercent * safeWeight;
      totalWeight += safeWeight;
    }

    private Double average() {
      if (totalWeight == 0) {
        return null;
      }
      return weightedSum / totalWeight;
    }
  }

  private Map<String, Double> toCoverageRates(final Map<String, CoverageAggregate> aggregates) {
    final Map<String, Double> rates = new HashMap<>();
    for (final Map.Entry<String, CoverageAggregate> entry : aggregates.entrySet()) {
      final Double avg = entry.getValue().average();
      if (avg != null) {
        rates.put(entry.getKey(), avg);
      }
    }
    return rates;
  }

  private String normalizeFilePath(final String filePath, final RunContext context) {
    if (filePath == null) {
      return null;
    }
    final String trimmed = filePath.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    try {
      Path path = Path.of(trimmed);
      final Path root = context.getProjectRoot();
      if (path.isAbsolute() && root != null) {
        path = relativizePath(path, root);
      }
      return path.normalize().toString();
    } catch (RuntimeException e) {
      return trimmed;
    }
  }

  private String extractFileName(final String filePath) {
    if (filePath == null || filePath.isBlank()) {
      return MessageSource.getMessage("report.value.unknown_word");
    }
    try {
      final Path path = Path.of(filePath);
      final Path fileName = path.getFileName();
      return fileName != null ? fileName.toString() : filePath;
    } catch (RuntimeException e) {
      return filePath;
    }
  }

  private Path relativizePath(final Path path, final Path root) {
    try {
      if (path.startsWith(root)) {
        return root.relativize(path);
      }
    } catch (IllegalArgumentException ignored) {
      // leave as-is when roots differ
    }
    return path;
  }

  private static final class ClassTotals {

    private int totalClasses;

    private int totalMethods;

    private int totalLoc;

    private int totalComplexity;

    private int maxComplexity;

    private void addClass(final ComplexitySummary summary, final int classLoc) {
      totalClasses++;
      totalMethods += summary.methodCount;
      totalLoc += classLoc;
      totalComplexity += summary.complexitySum;
      maxComplexity = Math.max(maxComplexity, summary.complexityMax);
    }
  }
}
