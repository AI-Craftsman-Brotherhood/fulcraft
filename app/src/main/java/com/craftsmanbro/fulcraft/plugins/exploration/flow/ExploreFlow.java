package com.craftsmanbro.fulcraft.plugins.exploration.flow;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Produces 3D exploration artifacts from analysis results. */
public class ExploreFlow {

  private static final String OUTPUT_DIR_NAME = "explore";

  private static final String SNAPSHOT_FILE_NAME = "explore_snapshot.json";

  private static final String INDEX_FILE_NAME = "index.html";

  private static final String DEFAULT_PACKAGE_NAME = "(default)";

  private final JsonServicePort jsonService;

  public ExploreFlow() {
    this(new DefaultJsonService());
  }

  ExploreFlow(final JsonServicePort jsonService) {
    this.jsonService =
        Objects.requireNonNull(
            jsonService,
            MessageSource.getMessage("explore.common.error.argument_null", "jsonService"));
  }

  public Result generate(final AnalysisResult analysisResult, final RunContext context)
      throws IOException {
    Objects.requireNonNull(
        analysisResult,
        MessageSource.getMessage("explore.common.error.argument_null", "analysisResult"));
    Objects.requireNonNull(
        context, MessageSource.getMessage("explore.common.error.argument_null", "context"));
    final Path runRoot =
        RunPaths.from(context.getConfig(), context.getProjectRoot(), context.getRunId()).runRoot();
    final Path outputDirectory = runRoot.resolve(OUTPUT_DIR_NAME);
    Files.createDirectories(outputDirectory);
    final Snapshot snapshot = buildSnapshot(analysisResult, context.getRunId(), runRoot);
    final Path snapshotFile = outputDirectory.resolve(SNAPSHOT_FILE_NAME);
    final Path indexFile = outputDirectory.resolve(INDEX_FILE_NAME);
    jsonService.writeToFile(snapshotFile, snapshot);
    Files.writeString(indexFile, renderHtml(snapshot), StandardCharsets.UTF_8);
    return new Result(
        outputDirectory,
        indexFile,
        snapshotFile,
        snapshot.totalClasses(),
        snapshot.totalPackages(),
        snapshot.totalMethods());
  }

  private Snapshot buildSnapshot(
      final AnalysisResult analysisResult, final String runId, final Path runRoot) {
    final Map<String, ClassInfo> classesByFqn = collectClassesByFqn(analysisResult);
    final Map<String, Integer> packageClassCounts = new LinkedHashMap<>();
    int classCount = 0;
    int methodCount = 0;
    int maxClassComplexity = 1;
    TopComplexity topComplexity = null;
    for (final Map.Entry<String, ClassInfo> entry : classesByFqn.entrySet()) {
      classCount++;
      final String classFqn = entry.getKey();
      final ClassInfo classInfo = entry.getValue();
      final String packageName = packageNameOf(classFqn);
      packageClassCounts.merge(packageName, 1, (a, b) -> a + b);
      final List<MethodInfo> methodsForMetrics = methodsForMetrics(classInfo);
      methodCount += methodsForMetrics.size();
      int classComplexity = 1;
      for (final MethodInfo methodInfo : methodsForMetrics) {
        if (methodInfo == null) {
          continue;
        }
        classComplexity = Math.max(classComplexity, methodInfo.getCyclomaticComplexity());
        final TopComplexity candidate =
            new TopComplexity(classFqn, methodInfo.getName(), methodInfo.getCyclomaticComplexity());
        if (topComplexity == null || candidate.score() > topComplexity.score()) {
          topComplexity = candidate;
        }
      }
      maxClassComplexity = Math.max(maxClassComplexity, classComplexity);
    }
    final int finalMaxClassComplexity = maxClassComplexity;
    final List<String> projectPackagePrefix =
        commonPackagePrefixTokens(packageClassCounts.keySet());
    final ExternalLibrarySummary externalLibrarySummary = collectExternalLibraries(classesByFqn);
    final List<NodeData> nodes = new ArrayList<>();
    classesByFqn.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              final String classFqn = entry.getKey();
              final ClassInfo classInfo = entry.getValue();
              final String packageName = packageNameOf(classFqn);
              final int classMethodCount = countMethodsForMetrics(classInfo);
              final int complexity = classComplexity(classInfo);
              nodes.add(
                  new NodeData(
                      classFqn,
                      classNameOf(classFqn),
                      packageName,
                      packageRoleOf(packageName),
                      packageClusterOf(packageName, projectPackagePrefix),
                      classInfo.getLoc(),
                      classMethodCount,
                      complexity,
                      riskScore(classInfo, complexity, finalMaxClassComplexity),
                      buildClassDetailLinks(runRoot, classInfo, classFqn),
                      externalLibrarySummary.librariesByClass().getOrDefault(classFqn, List.of())));
            });
    final List<EdgeData> edges = buildEdges(classesByFqn);
    final List<PackageData> packageData = new ArrayList<>();
    packageClassCounts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                packageData.add(
                    new PackageData(
                        entry.getKey(),
                        packageRoleOf(entry.getKey()),
                        packageClusterOf(entry.getKey(), projectPackagePrefix),
                        packageDepth(entry.getKey()),
                        entry.getValue())));
    final String topComplexityLabel =
        topComplexity == null
            ? "N/A"
            : topComplexity.classFqn()
                + "#"
                + topComplexity.methodName()
                + " (CC="
                + topComplexity.score()
                + ")";
    return new Snapshot(
        runId,
        Instant.now().toString(),
        classCount,
        packageData.size(),
        methodCount,
        topComplexityLabel,
        packageData,
        nodes,
        edges,
        collectReportLinks(runRoot),
        externalLibrarySummary.libraries());
  }

  private Map<String, ClassInfo> collectClassesByFqn(final AnalysisResult analysisResult) {
    final Map<String, ClassInfo> classesByFqn = new LinkedHashMap<>();
    for (final ClassInfo classInfo : analysisResult.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      if (!shouldIncludeClass(classInfo)) {
        continue;
      }
      final String classFqn = normalizeFqn(classInfo.getFqn());
      if (classFqn == null) {
        continue;
      }
      classesByFqn.put(classFqn, classInfo);
    }
    return classesByFqn;
  }

  private List<EdgeData> buildEdges(final Map<String, ClassInfo> classesByFqn) {
    final Map<String, Integer> edgeWeights = new LinkedHashMap<>();
    final Set<String> classFqns = classesByFqn.keySet();
    for (final Map.Entry<String, ClassInfo> entry : classesByFqn.entrySet()) {
      final String fromClass = entry.getKey();
      final ClassInfo classInfo = entry.getValue();
      final String sourcePackage = packageNameOf(fromClass);
      final Set<String> dependencies = collectDependencies(classInfo, sourcePackage, classFqns);
      for (final String toClass : dependencies) {
        if (toClass == null || toClass.equals(fromClass)) {
          continue;
        }
        final String edgeKey = fromClass + "->" + toClass;
        edgeWeights.merge(edgeKey, 1, (a, b) -> a + b);
      }
    }
    final List<EdgeData> edges = new ArrayList<>();
    for (final Map.Entry<String, Integer> entry : edgeWeights.entrySet()) {
      final String key = entry.getKey();
      final int separatorIndex = key.indexOf("->");
      if (separatorIndex < 0) {
        continue;
      }
      edges.add(
          new EdgeData(
              key.substring(0, separatorIndex),
              key.substring(separatorIndex + 2),
              entry.getValue()));
    }
    edges.sort(Comparator.comparing(EdgeData::from).thenComparing(EdgeData::to));
    return edges;
  }

  private Set<String> collectDependencies(
      final ClassInfo classInfo, final String sourcePackage, final Set<String> classFqns) {
    final Set<String> dependencies = new LinkedHashSet<>();
    for (final String importRef : classInfo.getImports()) {
      final String resolved = resolveImportDependency(importRef, sourcePackage, classFqns);
      if (resolved != null) {
        dependencies.add(resolved);
      }
    }
    for (final String extendsType : classInfo.getExtendsTypes()) {
      final String resolved = resolveReferencedType(extendsType, sourcePackage, classFqns);
      if (resolved != null) {
        dependencies.add(resolved);
      }
    }
    for (final String implementsType : classInfo.getImplementsTypes()) {
      final String resolved = resolveReferencedType(implementsType, sourcePackage, classFqns);
      if (resolved != null) {
        dependencies.add(resolved);
      }
    }
    for (final MethodInfo methodInfo : classInfo.getMethods()) {
      if (methodInfo == null) {
        continue;
      }
      for (final CalledMethodRef callRef : methodInfo.getCalledMethodRefs()) {
        if (callRef == null) {
          continue;
        }
        final String resolved =
            resolveOwnerFromCall(callRef.getResolved(), callRef.getRaw(), sourcePackage, classFqns);
        if (resolved != null) {
          dependencies.add(resolved);
        }
      }
      for (final String legacyCall : methodInfo.getCalledMethods()) {
        final String resolved =
            resolveOwnerFromCall(legacyCall, legacyCall, sourcePackage, classFqns);
        if (resolved != null) {
          dependencies.add(resolved);
        }
      }
    }
    return dependencies;
  }

  private String resolveImportDependency(
      final String importRef, final String sourcePackage, final Set<String> classFqns) {
    if (importRef == null || importRef.isBlank()) {
      return null;
    }
    String trimmed = importRef.trim();
    boolean isStaticImport = false;
    if (trimmed.startsWith("static ")) {
      isStaticImport = true;
      trimmed = trimmed.substring("static ".length()).trim();
    }
    if (trimmed.endsWith(".*")) {
      return null;
    }
    final String resolved = resolveReferencedType(trimmed, sourcePackage, classFqns);
    if (resolved != null) {
      return resolved;
    }
    if (isStaticImport) {
      final int lastDotIndex = trimmed.lastIndexOf('.');
      if (lastDotIndex > 0) {
        return resolveReferencedType(trimmed.substring(0, lastDotIndex), sourcePackage, classFqns);
      }
    }
    return null;
  }

  private String resolveOwnerFromCall(
      final String primaryCandidate,
      final String fallbackCandidate,
      final String sourcePackage,
      final Set<String> classFqns) {
    String owner = parseCallOwner(primaryCandidate);
    if (owner == null) {
      owner = parseCallOwner(fallbackCandidate);
    }
    if (owner == null) {
      return null;
    }
    return resolveReferencedType(owner, sourcePackage, classFqns);
  }

  private String parseCallOwner(final String callExpression) {
    if (callExpression == null || callExpression.isBlank()) {
      return null;
    }
    String candidate = callExpression.trim();
    final int hashIndex = candidate.indexOf('#');
    if (hashIndex >= 0) {
      candidate = candidate.substring(0, hashIndex);
    }
    final int methodRefIndex = candidate.indexOf("::");
    if (methodRefIndex >= 0) {
      candidate = candidate.substring(0, methodRefIndex);
    }
    final int parenIndex = candidate.indexOf('(');
    if (parenIndex >= 0) {
      candidate = candidate.substring(0, parenIndex);
    }
    candidate = sanitizeTypeRef(candidate);
    if (candidate == null) {
      return null;
    }
    if (candidate.startsWith("Unknown") || candidate.startsWith("unknown")) {
      return null;
    }
    return candidate;
  }

  private String resolveReferencedType(
      final String typeRef, final String sourcePackage, final Set<String> classFqns) {
    final String cleaned = sanitizeTypeRef(typeRef);
    if (cleaned == null) {
      return null;
    }
    if (classFqns.contains(cleaned)) {
      return cleaned;
    }
    final String normalizedInner = cleaned.replace('$', '.');
    if (classFqns.contains(normalizedInner)) {
      return normalizedInner;
    }
    if (!cleaned.contains(".")) {
      if (!DEFAULT_PACKAGE_NAME.equals(sourcePackage)) {
        final String samePackageCandidate = sourcePackage + "." + cleaned;
        if (classFqns.contains(samePackageCandidate)) {
          return samePackageCandidate;
        }
      }
      return uniqueSuffixMatch(cleaned, classFqns);
    }
    String stripped = cleaned;
    while (stripped.contains(".")) {
      if (classFqns.contains(stripped)) {
        return stripped;
      }
      stripped = stripped.substring(0, stripped.lastIndexOf('.'));
    }
    final String simpleName = cleaned.substring(cleaned.lastIndexOf('.') + 1);
    return uniqueSuffixMatch(simpleName, classFqns);
  }

  private String uniqueSuffixMatch(final String simpleName, final Set<String> classFqns) {
    if (simpleName == null || simpleName.isBlank()) {
      return null;
    }
    String matched = null;
    for (final String classFqn : classFqns) {
      if (classFqn.equals(simpleName) || classFqn.endsWith('.' + simpleName)) {
        if (matched != null) {
          return null;
        }
        matched = classFqn;
      }
    }
    return matched;
  }

  private String sanitizeTypeRef(final String typeRef) {
    if (typeRef == null || typeRef.isBlank()) {
      return null;
    }
    String cleaned = typeRef.trim();
    cleaned = cleaned.replace("? extends ", "").replace("? super ", "").trim();
    cleaned = cleaned.replace("new ", "").replace("class ", "").trim();
    while (cleaned.endsWith("[]")) {
      cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
    }
    cleaned = removeGenerics(cleaned);
    if (cleaned.endsWith(".class")) {
      cleaned = cleaned.substring(0, cleaned.length() - 6).trim();
    }
    if (cleaned.contains(" ")) {
      final String[] parts = cleaned.split("\\s+");
      cleaned = parts[parts.length - 1];
    }
    if (cleaned.isBlank()) {
      return null;
    }
    return cleaned;
  }

  private String removeGenerics(final String value) {
    final StringBuilder builder = new StringBuilder(value.length());
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (c == '<') {
        depth++;
        continue;
      }
      if (c == '>') {
        depth = Math.max(0, depth - 1);
        continue;
      }
      if (depth == 0) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private int classComplexity(final ClassInfo classInfo) {
    int maxComplexity = 1;
    for (final MethodInfo methodInfo : methodsForMetrics(classInfo)) {
      if (methodInfo == null) {
        continue;
      }
      maxComplexity = Math.max(maxComplexity, methodInfo.getCyclomaticComplexity());
    }
    return maxComplexity;
  }

  private int riskScore(
      final ClassInfo classInfo, final int complexity, final int maxClassComplexity) {
    final double complexityFactor =
        maxClassComplexity <= 0 ? 0 : (complexity / (double) maxClassComplexity) * 62.0;
    final double locFactor = Math.min(22.0, Math.max(0, classInfo.getLoc()) / 18.0);
    final double methodsFactor =
        Math.min(16.0, Math.max(0, countMethodsForMetrics(classInfo)) * 1.5);
    final int risk =
        (int) Math.round(Math.min(100.0, complexityFactor + locFactor + methodsFactor));
    return Math.max(8, risk);
  }

  private List<MethodInfo> methodsForMetrics(final ClassInfo classInfo) {
    return MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo);
  }

  private int countMethodsForMetrics(final ClassInfo classInfo) {
    return MethodSemantics.countMethodsExcludingImplicitDefaultConstructors(classInfo);
  }

  private String normalizeFqn(final String fqn) {
    if (fqn == null || fqn.isBlank()) {
      return null;
    }
    return fqn.trim();
  }

  private boolean shouldIncludeClass(final ClassInfo classInfo) {
    if (classInfo.isNestedClass()) {
      return false;
    }
    if (isLikelyTestPath(classInfo.getFilePath())) {
      return false;
    }
    final String classFqn = normalizeFqn(classInfo.getFqn());
    return classFqn == null || !isLikelyTestFqn(classFqn);
  }

  private boolean isLikelyTestPath(final String filePath) {
    if (filePath == null || filePath.isBlank()) {
      return false;
    }
    final String normalized = filePath.replace('\\', '/').toLowerCase(Locale.ROOT);
    return normalized.contains("/src/test/") || normalized.startsWith("src/test/");
  }

  private boolean isLikelyTestFqn(final String classFqn) {
    final String lower = classFqn.toLowerCase(Locale.ROOT);
    return lower.contains(".test.") || lower.endsWith("test");
  }

  private String packageNameOf(final String fqn) {
    if (fqn == null || fqn.isBlank()) {
      return DEFAULT_PACKAGE_NAME;
    }
    final String[] tokens = fqn.split("\\.");
    if (tokens.length <= 1) {
      return DEFAULT_PACKAGE_NAME;
    }
    final List<String> packageTokens =
        Arrays.stream(tokens)
            .filter(token -> token != null && !token.isBlank())
            .takeWhile(token -> !Character.isUpperCase(token.charAt(0)))
            .toList();
    if (!packageTokens.isEmpty()) {
      return String.join(".", packageTokens);
    }
    final int index = fqn.lastIndexOf('.');
    if (index > 0) {
      return fqn.substring(0, index);
    }
    return DEFAULT_PACKAGE_NAME;
  }

  private String classNameOf(final String fqn) {
    if (fqn == null || fqn.isBlank()) {
      return "Unknown";
    }
    final int index = fqn.lastIndexOf('.');
    if (index < 0 || index == fqn.length() - 1) {
      return fqn;
    }
    return fqn.substring(index + 1);
  }

  private int packageDepth(final String packageName) {
    if (packageName == null || packageName.isBlank() || DEFAULT_PACKAGE_NAME.equals(packageName)) {
      return 0;
    }
    return packageName.split("\\.").length;
  }

  private String packageRoleOf(final String packageName) {
    if (packageName == null || packageName.isBlank() || DEFAULT_PACKAGE_NAME.equals(packageName)) {
      return "default";
    }
    final String[] tokens = packageName.toLowerCase(Locale.ROOT).split("\\.");
    String deferredRole = null;
    for (int i = tokens.length - 1; i >= 0; i--) {
      final String role = roleFromPackageToken(tokens[i]);
      if (role == null) {
        continue;
      }
      if ("legacy".equals(role)) {
        deferredRole = role;
        continue;
      }
      return role;
    }
    if (deferredRole != null) {
      return deferredRole;
    }
    return "core";
  }

  private String roleFromPackageToken(final String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    if ("test".equals(token)
        || "tests".equals(token)
        || "spec".equals(token)
        || "fixture".equals(token)
        || "mock".equals(token)) {
      return "test";
    }
    if (token.contains("service") || "application".equals(token) || "usecase".equals(token)) {
      return "service";
    }
    if (token.contains("util")
        || "utils".equals(token)
        || "common".equals(token)
        || "helper".equals(token)) {
      return "utility";
    }
    if ("controller".equals(token)
        || "api".equals(token)
        || "web".equals(token)
        || "rest".equals(token)
        || "endpoint".equals(token)) {
      return "entry";
    }
    if ("repository".equals(token)
        || "repo".equals(token)
        || "dao".equals(token)
        || "persistence".equals(token)) {
      return "data";
    }
    if ("domain".equals(token)
        || "model".equals(token)
        || "entity".equals(token)
        || "dto".equals(token)
        || "vo".equals(token)) {
      return "domain";
    }
    if ("infra".equals(token)
        || "infrastructure".equals(token)
        || "config".equals(token)
        || "bootstrap".equals(token)) {
      return "infra";
    }
    if ("legacy".equals(token) || token.contains("deprecated")) {
      return "legacy";
    }
    return null;
  }

  private List<String> commonPackagePrefixTokens(final Set<String> packageNames) {
    final List<String[]> tokenized = new ArrayList<>();
    for (final String packageName : packageNames) {
      if (packageName == null
          || packageName.isBlank()
          || DEFAULT_PACKAGE_NAME.equals(packageName)) {
        continue;
      }
      tokenized.add(packageName.split("\\."));
    }
    if (tokenized.isEmpty()) {
      return List.of();
    }
    final String[] base = tokenized.get(0);
    int commonLength = base.length;
    for (int i = 1; i < tokenized.size(); i++) {
      final String[] current = tokenized.get(i);
      commonLength = Math.min(commonLength, current.length);
      int j = 0;
      while (j < commonLength && base[j].equals(current[j])) {
        j++;
      }
      commonLength = j;
      if (commonLength == 0) {
        break;
      }
    }
    final int prefixLength = Math.min(commonLength, 3);
    if (prefixLength <= 0) {
      return List.of();
    }
    final List<String> result = new ArrayList<>(prefixLength);
    for (int i = 0; i < prefixLength; i++) {
      result.add(base[i]);
    }
    return result;
  }

  private String packageClusterOf(
      final String packageName, final List<String> packagePrefixTokens) {
    if (packageName == null || packageName.isBlank() || DEFAULT_PACKAGE_NAME.equals(packageName)) {
      return "default";
    }
    final String[] tokens = packageName.split("\\.");
    final int startIndex = packagePrefixTokens != null ? packagePrefixTokens.size() : 0;
    if (startIndex < tokens.length) {
      return tokens[startIndex].toLowerCase(Locale.ROOT);
    }
    if (tokens.length >= 3) {
      return tokens[2].toLowerCase(Locale.ROOT);
    }
    if (tokens.length >= 2) {
      return tokens[tokens.length - 1].toLowerCase(Locale.ROOT);
    }
    return tokens[0].toLowerCase(Locale.ROOT);
  }

  private ExternalLibrarySummary collectExternalLibraries(
      final Map<String, ClassInfo> classesByFqn) {
    final Set<String> classFqns = classesByFqn.keySet();
    final Set<String> internalRoots = deriveInternalRoots(classFqns);
    final Map<String, ExternalLibraryAccumulator> libraries = new LinkedHashMap<>();
    final Map<String, List<String>> librariesByClass = new LinkedHashMap<>();
    for (final Map.Entry<String, ClassInfo> entry : classesByFqn.entrySet()) {
      final String classFqn = entry.getKey();
      final ClassInfo classInfo = entry.getValue();
      final Set<String> classLibraries = new LinkedHashSet<>();
      for (final String importRef : classInfo.getImports()) {
        final String library = resolveExternalLibrary(importRef, classFqns, internalRoots);
        if (library == null) {
          continue;
        }
        classLibraries.add(library);
        libraries.computeIfAbsent(library, key -> new ExternalLibraryAccumulator()).add(classFqn);
      }
      final List<String> sortedClassLibraries = classLibraries.stream().sorted().toList();
      librariesByClass.put(classFqn, sortedClassLibraries);
    }
    final List<ExternalLibraryData> libraryData =
        libraries.entrySet().stream()
            .map(
                entry ->
                    new ExternalLibraryData(
                        entry.getKey(),
                        entry.getValue().classCount(),
                        entry.getValue().importCount()))
            .sorted(
                Comparator.comparingInt(ExternalLibraryData::classCount)
                    .reversed()
                    .thenComparingInt(ExternalLibraryData::importCount)
                    .reversed()
                    .thenComparing(ExternalLibraryData::name))
            .limit(40)
            .toList();
    return new ExternalLibrarySummary(libraryData, librariesByClass);
  }

  private Set<String> deriveInternalRoots(final Set<String> classFqns) {
    final Set<String> roots = new LinkedHashSet<>();
    for (final String classFqn : classFqns) {
      final String packageName = packageNameOf(classFqn);
      if (packageName == null
          || packageName.isBlank()
          || DEFAULT_PACKAGE_NAME.equals(packageName)) {
        continue;
      }
      final String[] tokens = packageName.split("\\.");
      if (tokens.length >= 3) {
        roots.add((tokens[0] + "." + tokens[1] + "." + tokens[2]).toLowerCase(Locale.ROOT));
      } else if (tokens.length >= 2) {
        roots.add((tokens[0] + "." + tokens[1]).toLowerCase(Locale.ROOT));
      } else {
        roots.add(tokens[0].toLowerCase(Locale.ROOT));
      }
    }
    return roots;
  }

  private String resolveExternalLibrary(
      final String importRef, final Set<String> classFqns, final Set<String> internalRoots) {
    final String importType = normalizeImportType(importRef);
    if (importType == null) {
      return null;
    }
    final String lowerType = importType.toLowerCase(Locale.ROOT);
    if (isJdkOrRuntimeType(lowerType)) {
      return null;
    }
    if (isInternalType(importType, lowerType, classFqns, internalRoots)) {
      return null;
    }
    return externalLibraryKeyOf(lowerType);
  }

  private String normalizeImportType(final String importRef) {
    if (importRef == null || importRef.isBlank()) {
      return null;
    }
    String normalized = importRef.trim();
    if (normalized.startsWith("static ")) {
      normalized = normalized.substring("static ".length()).trim();
      final int lastDot = normalized.lastIndexOf('.');
      if (lastDot > 0) {
        normalized = normalized.substring(0, lastDot);
      }
    }
    if (normalized.endsWith(".*")) {
      normalized = normalized.substring(0, normalized.length() - 2);
    }
    return normalized.isBlank() ? null : normalized;
  }

  private boolean isJdkOrRuntimeType(final String lowerType) {
    return lowerType.startsWith("java.")
        || lowerType.startsWith("javax.")
        || lowerType.startsWith("jakarta.")
        || lowerType.startsWith("kotlin.")
        || lowerType.startsWith("sun.")
        || lowerType.startsWith("com.sun.");
  }

  private boolean isInternalType(
      final String importType,
      final String lowerType,
      final Set<String> classFqns,
      final Set<String> internalRoots) {
    if (classFqns.contains(importType)) {
      return true;
    }
    for (final String classFqn : classFqns) {
      if (classFqn.startsWith(importType + ".")) {
        return true;
      }
    }
    for (final String root : internalRoots) {
      if (lowerType.equals(root) || lowerType.startsWith(root + ".")) {
        return true;
      }
    }
    return false;
  }

  private String externalLibraryKeyOf(final String lowerType) {
    final String[] tokens = lowerType.split("\\.");
    if (tokens.length == 0) {
      return lowerType;
    }
    if (tokens.length == 1) {
      return tokens[0];
    }
    final String head = tokens[0];
    if ("com".equals(head)
        || "org".equals(head)
        || "net".equals(head)
        || "io".equals(head)
        || "dev".equals(head)
        || "edu".equals(head)) {
      return head + "." + tokens[1];
    }
    return head;
  }

  private List<LinkData> buildClassDetailLinks(
      final Path runRoot, final ClassInfo classInfo, final String classFqn) {
    final List<LinkData> links = new ArrayList<>();
    final String analysisRelative = buildAnalysisJsonRelativePath(classFqn);
    if (analysisRelative != null) {
      final Path analysisPath = runRoot.resolve("explore").resolve(analysisRelative).normalize();
      if (Files.exists(analysisPath)) {
        links.add(new LinkData("Analysis JSON", analysisRelative));
      }
    }
    final String sourceAlignedHtml =
        DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html");
    if (sourceAlignedHtml != null && !sourceAlignedHtml.isBlank()) {
      final String docsHtmlRelative = "../docs/" + sourceAlignedHtml;
      final Path docsHtmlPath = runRoot.resolve("docs").resolve(sourceAlignedHtml).normalize();
      if (Files.exists(docsHtmlPath)) {
        links.add(new LinkData("Class Detail HTML", docsHtmlRelative));
      }
    }
    final String sourceAlignedMd = DocumentUtils.generateSourceAlignedReportPath(classInfo, ".md");
    if (sourceAlignedMd != null && !sourceAlignedMd.isBlank()) {
      final String docsMdRelative = "../docs/" + sourceAlignedMd;
      final Path docsMdPath = runRoot.resolve("docs").resolve(sourceAlignedMd).normalize();
      if (Files.exists(docsMdPath)) {
        links.add(new LinkData("Class Detail Markdown", docsMdRelative));
      }
    }
    return links;
  }

  private String buildAnalysisJsonRelativePath(final String classFqn) {
    if (classFqn == null || classFqn.isBlank()) {
      return null;
    }
    final String packageName = packageNameOf(classFqn);
    final String className = classNameOf(classFqn);
    final String packagePath =
        (packageName == null || packageName.isBlank() || DEFAULT_PACKAGE_NAME.equals(packageName))
            ? ""
            : packageName.replace('.', '/');
    if (packagePath.isBlank()) {
      return "../analysis/analysis_" + className + ".json";
    }
    return "../analysis/" + packagePath + "/analysis_" + className + ".json";
  }

  private List<LinkData> collectReportLinks(final Path runRoot) {
    final List<LinkData> links = new ArrayList<>();
    final Path exploreDir = runRoot.resolve(OUTPUT_DIR_NAME);
    addReportLinkIfExists(
        links,
        exploreDir,
        runRoot.resolve("report").resolve("analysis_visual.html"),
        "Analysis Visual Report");
    addReportLinkIfExists(
        links,
        exploreDir,
        runRoot.resolve("report").resolve("report.html"),
        "Detailed Report (HTML)");
    addReportLinkIfExists(
        links,
        exploreDir,
        runRoot.resolve("report").resolve("report.md"),
        "Detailed Report (Markdown)");
    addReportLinkIfExists(
        links,
        exploreDir,
        runRoot.resolve("analysis").resolve("quality_report.md"),
        "Quality Report (Markdown)");
    addReportLinkIfExists(
        links, exploreDir, runRoot.resolve("report").resolve("report.json"), "Report JSON");
    addReportLinkIfExists(
        links, exploreDir, runRoot.resolve("report").resolve("report.yaml"), "Report YAML");
    addReportLinkIfExists(
        links, exploreDir, runRoot.resolve("report").resolve("report.yml"), "Report YML");
    return links;
  }

  private void addReportLinkIfExists(
      final List<LinkData> links, final Path baseDir, final Path targetPath, final String label) {
    if (targetPath == null || !Files.exists(targetPath)) {
      return;
    }
    final String relativePath =
        baseDir.relativize(targetPath).toString().replace('\\', '/').replace(" ", "%20");
    links.add(new LinkData(label, relativePath));
  }

  private String renderHtml(final Snapshot snapshot) {
    final String template =
        """
            <!doctype html>
            <html lang="__LOCALE__">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>__TITLE__</title>
              <style>
                :root {
                  color-scheme: dark;
                  --bg-start: #0d1627;
                  --bg-mid: #162c4a;
                  --bg-end: #7d8796;
                  --panel: rgba(21, 34, 58, 0.76);
                  --line: rgba(150, 187, 231, 0.34);
                  --text: #edf4ff;
                  --muted: #bfd1ea;
                  --accent: #82b8ec;
                  --warn: #d57986;
                }

                * {
                  box-sizing: border-box;
                }

                html,
                body {
                  margin: 0;
                  height: 100%;
                  font-family: "Noto Sans JP", "Hiragino Kaku Gothic ProN", "Yu Gothic", "Meiryo", sans-serif;
                  color: var(--text);
                  background:
                    radial-gradient(circle at 74% 52%, rgba(188, 209, 236, 0.2), transparent 42%),
                    radial-gradient(circle at 17% 18%, rgba(98, 131, 180, 0.2), transparent 46%),
                    linear-gradient(152deg, var(--bg-start), var(--bg-mid) 52%, var(--bg-end));
                }

                body::before {
                  content: "";
                  position: fixed;
                  inset: 0;
                  pointer-events: none;
                  background-image: radial-gradient(rgba(200, 219, 247, 0.11) 1px, transparent 1px);
                  background-size: 22px 22px;
                  opacity: 0.13;
                }

                .workspace {
                  position: relative;
                  z-index: 1;
                  display: grid;
                  grid-template-rows: auto minmax(0, 1fr);
                  height: 100vh;
                }

                .workspace-tabs-wrap {
                  padding: 10px 14px 0;
                }

                .workspace-tabs {
                  display: inline-flex;
                  gap: 6px;
                  border: 1px solid rgba(132, 176, 225, 0.36);
                  border-radius: 12px;
                  background: rgba(23, 37, 61, 0.72);
                  padding: 6px;
                }

                .workspace-tab {
                  border: 1px solid transparent;
                  border-radius: 9px;
                  background: transparent;
                  color: var(--muted);
                  padding: 8px 14px;
                  font-size: 12px;
                  letter-spacing: 0.04em;
                  text-transform: uppercase;
                  cursor: pointer;
                }

                .workspace-tab.is-active {
                  border-color: rgba(151, 193, 236, 0.64);
                  background: rgba(50, 77, 117, 0.88);
                  color: var(--text);
                }

                .workspace-panel {
                  min-height: 0;
                  display: none;
                }

                .workspace-panel.is-active {
                  display: block;
                }

                .layout {
                  position: relative;
                  display: grid;
                  grid-template-columns: 300px minmax(0, 1fr) 320px;
                  gap: 14px;
                  height: 100%;
                  padding: 14px;
                }

                .panel {
                  border: 1px solid var(--line);
                  border-radius: 14px;
                  background: var(--panel);
                  box-shadow: 0 9px 24px rgba(6, 13, 25, 0.28);
                  overflow: auto;
                  padding: 14px;
                }

                .stage {
                  position: relative;
                  border: 1px solid var(--line);
                  border-radius: 14px;
                  overflow: hidden;
                  background: rgba(20, 30, 48, 0.24);
                }

                #metro-canvas {
                  width: 100%;
                  height: 100%;
                  display: block;
                }

                .kicker {
                  font-size: 11px;
                  letter-spacing: 0.16em;
                  text-transform: uppercase;
                  color: var(--muted);
                }

                h1 {
                  margin: 8px 0 0;
                  font-size: 24px;
                  line-height: 1.2;
                }

                .subtitle {
                  margin-top: 10px;
                  font-size: 12px;
                  line-height: 1.55;
                  color: var(--muted);
                }

                .group {
                  margin-top: 14px;
                  border: 1px solid rgba(129, 173, 224, 0.28);
                  border-radius: 11px;
                  padding: 12px;
                  background: rgba(24, 39, 67, 0.56);
                }

                .group-title {
                  margin: 0 0 10px;
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.13em;
                  color: var(--muted);
                }

                .stats {
                  display: grid;
                  grid-template-columns: repeat(3, minmax(0, 1fr));
                  gap: 10px;
                }

                .stat {
                  border: 1px solid rgba(133, 178, 229, 0.26);
                  border-radius: 10px;
                  background: rgba(30, 48, 81, 0.62);
                  padding: 10px;
                }

                .stat-label {
                  font-size: 10px;
                  color: var(--muted);
                  text-transform: uppercase;
                  letter-spacing: 0.11em;
                }

                .stat-value {
                  margin-top: 5px;
                  font-size: 20px;
                  font-weight: 700;
                  color: var(--accent);
                }

                .detail-title {
                  margin-top: 4px;
                  font-size: 18px;
                  font-weight: 700;
                  line-height: 1.35;
                }

                .detail-label {
                  margin-top: 10px;
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.11em;
                  color: var(--muted);
                }

                .detail-score {
                  margin-top: 5px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  min-width: 84px;
                  border-radius: 999px;
                  border: 1px solid rgba(132, 176, 225, 0.42);
                  background: rgba(36, 57, 92, 0.84);
                  padding: 6px 12px;
                  font-size: 12px;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: 0.08em;
                }

                .detail-score.score-high {
                  border-color: rgba(213, 121, 134, 0.65);
                  color: #ffdbe2;
                  background: rgba(88, 32, 44, 0.85);
                }

                .detail-score.score-medium {
                  border-color: rgba(233, 191, 112, 0.62);
                  color: #ffedcf;
                  background: rgba(82, 62, 28, 0.82);
                }

                .detail-score.score-low {
                  border-color: rgba(122, 198, 145, 0.58);
                  color: #dcffe9;
                  background: rgba(34, 72, 50, 0.82);
                }

                .detail-list {
                  margin-top: 8px;
                  padding-left: 0;
                  list-style: none;
                }

                .detail-list li {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 10px;
                  border-bottom: 1px solid rgba(132, 176, 225, 0.2);
                  padding: 6px 0;
                  font-size: 12px;
                  color: var(--muted);
                }

                .detail-list li:last-child {
                  border-bottom: none;
                }

                .detail-value {
                  color: var(--text);
                  font-weight: 600;
                  text-align: right;
                  max-width: 60%;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                }

                .detail-pill {
                  border: 1px solid rgba(132, 176, 225, 0.38);
                  border-radius: 999px;
                  padding: 2px 8px;
                  font-size: 10px;
                  text-transform: uppercase;
                  letter-spacing: 0.06em;
                  color: var(--text);
                  background: rgba(35, 56, 90, 0.72);
                  white-space: nowrap;
                }

                .detail-list li.detail-doc-item {
                  display: block;
                }

                .detail-link {
                  color: var(--accent);
                  text-decoration: none;
                  border-bottom: 1px dashed rgba(130, 184, 236, 0.6);
                }

                .detail-link:hover {
                  color: #b3d5f7;
                  border-bottom-color: rgba(179, 213, 247, 0.88);
                }

                dl {
                  margin: 0;
                }

                dt {
                  margin-top: 8px;
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.11em;
                  color: var(--muted);
                }

                dd {
                  margin: 4px 0 0;
                  font-size: 13px;
                  line-height: 1.45;
                }

                ul {
                  margin: 0;
                  padding-left: 18px;
                }

                li {
                  margin-bottom: 6px;
                  font-size: 12px;
                  color: var(--muted);
                }

                button {
                  width: 100%;
                  border: 1px solid rgba(132, 176, 225, 0.42);
                  border-radius: 9px;
                  background: rgba(36, 57, 92, 0.84);
                  color: var(--text);
                  padding: 8px 10px;
                  font-size: 12px;
                  font-family: inherit;
                  cursor: pointer;
                  transition: border-color 0.2s ease, background 0.2s ease;
                }

                button:hover {
                  border-color: rgba(156, 196, 238, 0.72);
                  background: rgba(50, 75, 116, 0.9);
                }

                .camera-controls {
                  display: grid;
                  gap: 8px;
                }

                .camera-zoom {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 8px;
                }

                .hint {
                  margin-top: 8px;
                  font-size: 11px;
                  line-height: 1.45;
                  color: var(--muted);
                }

                .hud {
                  position: absolute;
                  top: 12px;
                  left: 12px;
                  right: 12px;
                  display: flex;
                  gap: 10px;
                  pointer-events: none;
                  z-index: 2;
                }

                .hud-card {
                  pointer-events: auto;
                  border: 1px solid rgba(140, 180, 225, 0.28);
                  border-radius: 10px;
                  background: rgba(32, 51, 83, 0.66);
                  padding: 8px 11px;
                  min-width: 140px;
                }

                .hud-label {
                  font-size: 10px;
                  text-transform: uppercase;
                  letter-spacing: 0.13em;
                  color: var(--muted);
                }

                .hud-value {
                  margin-top: 3px;
                  font-size: 14px;
                  font-weight: 600;
                }

                #boot-error {
                  position: absolute;
                  inset: 80px 18px auto 18px;
                  border: 1px solid rgba(213, 121, 134, 0.75);
                  border-radius: 11px;
                  background: rgba(35, 11, 17, 0.9);
                  color: #ffe3e8;
                  font-size: 12px;
                  line-height: 1.5;
                  padding: 12px;
                  z-index: 3;
                }

                .report-shell {
                  height: 100%;
                  border-top: 1px solid rgba(132, 176, 225, 0.24);
                  margin-top: 8px;
                  padding: 14px;
                  display: grid;
                  grid-template-rows: auto auto minmax(0, 1fr);
                  gap: 10px;
                }

                .report-head {
                  display: flex;
                  align-items: flex-start;
                  justify-content: space-between;
                  gap: 14px;
                }

                .report-kicker {
                  font-size: 11px;
                  text-transform: uppercase;
                  letter-spacing: 0.12em;
                  color: var(--muted);
                }

                .report-title {
                  margin: 6px 0 0;
                  font-size: 24px;
                  line-height: 1.2;
                }

                .report-subtitle {
                  margin: 8px 0 0;
                  color: var(--muted);
                  font-size: 12px;
                  line-height: 1.5;
                }

                .report-raw-link {
                  border: 1px solid rgba(132, 176, 225, 0.42);
                  border-radius: 10px;
                  color: var(--text);
                  text-decoration: none;
                  padding: 8px 12px;
                  font-size: 12px;
                  background: rgba(36, 57, 92, 0.84);
                  white-space: nowrap;
                }

                .report-raw-link:hover {
                  border-color: rgba(156, 196, 238, 0.72);
                  background: rgba(50, 75, 116, 0.9);
                }

                .report-status {
                  border: 1px solid rgba(132, 176, 225, 0.22);
                  border-radius: 10px;
                  background: rgba(24, 39, 67, 0.46);
                  padding: 10px 12px;
                  color: var(--muted);
                  font-size: 12px;
                  line-height: 1.5;
                }

                .report-links {
                  margin: 0;
                  padding-left: 18px;
                }

                .report-links a {
                  color: var(--accent);
                  text-decoration: none;
                }

                .report-links a:hover {
                  color: #b3d5f7;
                }

                .report-frame {
                  width: 100%;
                  height: 100%;
                  min-height: 360px;
                  border: 1px solid rgba(132, 176, 225, 0.22);
                  border-radius: 12px;
                  background: rgba(18, 29, 49, 0.56);
                }

                @media (max-width: 1240px) {
                  .workspace {
                    height: auto;
                    min-height: 100vh;
                  }

                  .layout {
                    grid-template-columns: 1fr;
                    height: auto;
                    min-height: 0;
                  }
                  .panel {
                    max-height: none;
                  }
                  .stage {
                    min-height: 68vh;
                  }

                  .report-shell {
                    grid-template-rows: auto auto auto;
                  }

                  .report-head {
                    flex-direction: column;
                  }
                }
              </style>
            </head>
            <body>
              <div class="workspace">
                <div class="workspace-tabs-wrap">
                  <div class="workspace-tabs" id="workspace-tabs" role="tablist" aria-label="Explore views">
                    <button
                      type="button"
                      class="workspace-tab is-active"
                      id="tab-visual"
                      role="tab"
                      aria-selected="true"
                      aria-controls="panel-visual"
                      tabindex="0"
                      data-tab="visual"
                    >
                      Explore 3D
                    </button>
                    <button
                      type="button"
                      class="workspace-tab"
                      id="tab-report"
                      role="tab"
                      aria-selected="false"
                      aria-controls="panel-report"
                      tabindex="-1"
                      data-tab="report"
                    >
                      Reporting
                    </button>
                  </div>
                </div>

                <section
                  class="workspace-panel is-active"
                  id="panel-visual"
                  role="tabpanel"
                  aria-labelledby="tab-visual"
                >
                  <div class="layout">
                <aside class="panel">
                  <div class="kicker">Explore / Function Metro</div>
                  <h1>Dependency Gear Tree</h1>
                  <div class="subtitle">
                    __SUBTITLE__
                  </div>

                  <section class="group">
                    <h2 class="group-title">Run Summary</h2>
                    <div class="stats">
                      <article class="stat">
                        <div class="stat-label">Classes</div>
                        <div class="stat-value" id="stat-classes">__CLASS_COUNT__</div>
                      </article>
                      <article class="stat">
                        <div class="stat-label">Packages</div>
                        <div class="stat-value" id="stat-packages">__PACKAGE_COUNT__</div>
                      </article>
                      <article class="stat">
                        <div class="stat-label">Methods</div>
                        <div class="stat-value" id="stat-methods">__METHOD_COUNT__</div>
                      </article>
                    </div>
                    <dl>
                      <dt>Run ID</dt>
                      <dd>__RUN_ID__</dd>
                      <dt>Generated</dt>
                      <dd>__GENERATED_AT__</dd>
                      <dt>Top Complexity</dt>
                      <dd>__TOP_COMPLEXITY__</dd>
                    </dl>
                  </section>

                  <section class="group">
                    <h2 class="group-title">How To Read</h2>
                    <ul>
                      <li>__LEGEND_GEAR__</li>
                      <li>__LEGEND_ROLE__</li>
                      <li>__LEGEND_PACKAGE__</li>
                      <li>__LEGEND_EDGE__</li>
                      <li>__LEGEND_INTERACTION__</li>
                    </ul>
                  </section>

                  <section class="group">
                    <h2 class="group-title">Camera Controls</h2>
                    <div class="camera-controls">
                      <button id="btn-reset-camera" type="button">Reset View (R)</button>
                      <button id="btn-focus-selected" type="button">Focus Selected (F)</button>
                      <div class="camera-zoom">
                        <button id="btn-zoom-in" type="button">Zoom In (+)</button>
                        <button id="btn-zoom-out" type="button">Zoom Out (-)</button>
                      </div>
                    </div>
                    <div class="hint">
                      __HINT_PACKAGE_LAYOUT__
                    </div>
                  </section>

                  <section class="group">
                    <h2 class="group-title">Package Roles</h2>
                    <ul id="package-role-summary"></ul>
                  </section>

                  <section class="group">
                    <h2 class="group-title">Package Clusters</h2>
                    <ul id="package-cluster-summary"></ul>
                  </section>
                </aside>

                <main class="stage">
                  <div class="hud">
                    <article class="hud-card">
                      <div class="hud-label">Root Class</div>
                      <div class="hud-value" id="hud-root">-</div>
                    </article>
                    <article class="hud-card">
                      <div class="hud-label">Visible Nodes</div>
                      <div class="hud-value" id="hud-nodes">0</div>
                    </article>
                    <article class="hud-card">
                      <div class="hud-label">Visible Links</div>
                      <div class="hud-value" id="hud-links">0</div>
                    </article>
                  </div>
                  <canvas id="metro-canvas"></canvas>
                  <div id="boot-error" hidden></div>
                </main>

                <aside class="panel">
                  <section class="group">
                    <h2 class="group-title">Selected Class</h2>
                    <div class="detail-title" id="detail-title">-</div>
                    <div class="detail-label">Leverage Score</div>
                    <div class="detail-score" id="detail-score">-</div>
                    <div class="detail-label">Metrics</div>
                    <ul class="detail-list" id="detail-metrics"></ul>
                    <div class="detail-label">Class Detail Reports</div>
                    <ul class="detail-list" id="detail-docs"></ul>
                    <div class="detail-label">Reasons</div>
                    <ul class="detail-list" id="detail-reasons"></ul>
                    <div class="detail-label">Connected Nodes</div>
                    <ul class="detail-list" id="detail-connections"></ul>
                  </section>

                  <section class="group">
                    <h2 class="group-title">External Libraries (Project)</h2>
                    <ul id="external-library-list"></ul>
                  </section>
                </aside>
                  </div>
                </section>

                <section class="workspace-panel" id="panel-report" role="tabpanel" aria-labelledby="tab-report" hidden>
                  <div class="report-shell">
                    <div class="report-head">
                      <div>
                        <div class="report-kicker">Explore / Reporting</div>
                        <h1 class="report-title">Run Reports</h1>
                        <p class="report-subtitle">
                          __REPORT_SUBTITLE__
                        </p>
                      </div>
                      <a class="report-raw-link" id="report-raw-link" href="#" target="_blank" rel="noopener noreferrer">
                        Open Raw Report
                      </a>
                    </div>
                    <div class="report-status" id="report-status"></div>
                    <iframe class="report-frame" id="report-frame" title="Report preview"></iframe>
                  </div>
                </section>
              </div>

              <script id="snapshot-data" type="application/json">__SNAPSHOT_JSON__</script>
              <script type="module">
                const snapshot = JSON.parse(document.getElementById("snapshot-data").textContent || "{}");
                const packages = Array.isArray(snapshot.packages) ? snapshot.packages : [];
                const nodes = Array.isArray(snapshot.nodes) ? snapshot.nodes : [];
                const edges = Array.isArray(snapshot.edges) ? snapshot.edges : [];
                const reportLinks = Array.isArray(snapshot.reportLinks) ? snapshot.reportLinks : [];
                const externalLibraries = Array.isArray(snapshot.externalLibraries) ? snapshot.externalLibraries : [];

                const bootError = document.getElementById("boot-error");
                function showBootError(message) {
                  bootError.hidden = false;
                  bootError.textContent = message;
                }

                if (nodes.length === 0) {
                  showBootError("__ERROR_NO_DATA__");
                  throw new Error();
                }

                let THREE;
                let OrbitControls;
                try {
                  THREE = await import("https://esm.sh/three@0.160.0");
                  ({ OrbitControls } = await import(
                    "https://esm.sh/three@0.160.0/examples/jsm/controls/OrbitControls.js"
                  ));
                } catch (error) {
                  showBootError(
                    "__ERROR_THREEJS_LOAD_FAILED__ " +
                      String(error && error.message ? error.message : error)
                  );
                  throw error;
                }

                const canvas = document.getElementById("metro-canvas");
                const stage = canvas.parentElement;

                const hudRoot = document.getElementById("hud-root");
                const hudNodes = document.getElementById("hud-nodes");
                const hudLinks = document.getElementById("hud-links");

                const detailTitle = document.getElementById("detail-title");
                const detailScore = document.getElementById("detail-score");
                const detailMetrics = document.getElementById("detail-metrics");
                const detailDocs = document.getElementById("detail-docs");
                const detailReasons = document.getElementById("detail-reasons");
                const detailConnections = document.getElementById("detail-connections");
                const packageRoleSummary = document.getElementById("package-role-summary");
                const packageClusterSummary = document.getElementById("package-cluster-summary");
                const externalLibraryList = document.getElementById("external-library-list");
                const btnResetCamera = document.getElementById("btn-reset-camera");
                const btnFocusSelected = document.getElementById("btn-focus-selected");
                const btnZoomIn = document.getElementById("btn-zoom-in");
                const btnZoomOut = document.getElementById("btn-zoom-out");
                const tabVisualButton = document.getElementById("tab-visual");
                const tabReportButton = document.getElementById("tab-report");
                const panelVisual = document.getElementById("panel-visual");
                const panelReport = document.getElementById("panel-report");
                const reportStatus = document.getElementById("report-status");
                const reportFrame = document.getElementById("report-frame");
                const reportRawLink = document.getElementById("report-raw-link");

                function hashCode(text) {
                  let hash = 0;
                  for (let i = 0; i < text.length; i += 1) {
                    hash = (hash << 5) - hash + text.charCodeAt(i);
                    hash |= 0;
                  }
                  return Math.abs(hash);
                }

                function mapRange(value, min, max, outMin, outMax) {
                  if (max <= min) {
                    return (outMin + outMax) * 0.5;
                  }
                  const t = (value - min) / (max - min);
                  return outMin + (outMax - outMin) * THREE.MathUtils.clamp(t, 0, 1);
                }

                function activateTab(tabName) {
                  const showVisual = tabName !== "report";
                  panelVisual.hidden = !showVisual;
                  panelReport.hidden = showVisual;
                  panelVisual.classList.toggle("is-active", showVisual);
                  panelReport.classList.toggle("is-active", !showVisual);
                  tabVisualButton.classList.toggle("is-active", showVisual);
                  tabReportButton.classList.toggle("is-active", !showVisual);
                  tabVisualButton.setAttribute("aria-selected", showVisual ? "true" : "false");
                  tabVisualButton.tabIndex = showVisual ? 0 : -1;
                  tabReportButton.setAttribute("aria-selected", showVisual ? "false" : "true");
                  tabReportButton.tabIndex = showVisual ? -1 : 0;
                  if (showVisual) {
                    requestAnimationFrame(() => resize());
                  }
                }

                function findReportPreviewLink(items) {
                  const validItems = items.filter((item) => item && typeof item.href === "string" && item.href.length > 0);
                  return (
                    validItems.find((item) => item.href.endsWith(".html")) ||
                    validItems.find((item) => item.href.endsWith(".md")) ||
                    validItems[0] ||
                    null
                  );
                }

                function initReportTab() {
                  if (!reportStatus || !reportFrame || !reportRawLink) {
                    return;
                  }

                  reportStatus.innerHTML = "";
                  if (!reportLinks.length) {
                    reportStatus.textContent =
                      "__REPORT_NO_ARTIFACTS__";
                    reportFrame.hidden = true;
                    reportRawLink.href = "#";
                    reportRawLink.setAttribute("aria-disabled", "true");
                    return;
                  }

                  const listTitle = document.createElement("div");
                  listTitle.textContent = "Available report artifacts:";
                  reportStatus.appendChild(listTitle);

                  const list = document.createElement("ul");
                  list.className = "report-links";
                  reportLinks.forEach((item) => {
                    if (!item || !item.href) {
                      return;
                    }
                    const li = document.createElement("li");
                    const link = document.createElement("a");
                    link.href = item.href;
                    link.textContent = item.label || item.href;
                    link.target = "_blank";
                    link.rel = "noopener noreferrer";
                    li.appendChild(link);
                    list.appendChild(li);
                  });
                  reportStatus.appendChild(list);

                  const preview = findReportPreviewLink(reportLinks);
                  if (preview && preview.href) {
                    reportFrame.hidden = false;
                    reportFrame.src = preview.href;
                    reportRawLink.href = preview.href;
                    reportRawLink.removeAttribute("aria-disabled");
                  } else {
                    reportFrame.hidden = true;
                    reportRawLink.href = "#";
                    reportRawLink.setAttribute("aria-disabled", "true");
                  }
                }

                const ROLE_COLORS = {
                  service: 0x5f9fd8,
                  utility: 0x71be92,
                  legacy: 0xd5a06f,
                  entry: 0xc691db,
                  data: 0xd98682,
                  domain: 0x8ea6da,
                  infra: 0x99a7ba,
                  test: 0xc8c06f,
                  core: 0x7fa7cf,
                  default: 0x90a0b6,
                };

                const ROLE_LABELS = {
                  service: "Service",
                  utility: "Utility",
                  legacy: "Legacy",
                  entry: "Entry",
                  data: "Data",
                  domain: "Domain",
                  infra: "Infra",
                  test: "Test",
                  core: "Core",
                  default: "Default",
                };

                function normalizeRoleName(roleName) {
                  if (!roleName || typeof roleName !== "string") {
                    return "core";
                  }
                  const key = roleName.trim().toLowerCase();
                  if (key === "util" || key === "utils") {
                    return "utility";
                  }
                  if (key === "repository" || key === "repo" || key === "dao") {
                    return "data";
                  }
                  if (key === "legacy" || key === "deprecated") {
                    return "legacy";
                  }
                  return ROLE_COLORS[key] ? key : "core";
                }

                function detectRoleFromPackage(packageName) {
                  if (!packageName || packageName === "(default)") {
                    return "default";
                  }
                  const tokens = packageName.toLowerCase().split(".");
                  let deferredRole = null;
                  for (let i = tokens.length - 1; i >= 0; i -= 1) {
                    const role = roleFromPackageToken(tokens[i]);
                    if (!role) {
                      continue;
                    }
                    if (role === "legacy") {
                      deferredRole = "legacy";
                      continue;
                    }
                    return role;
                  }
                  if (deferredRole) return deferredRole;
                  return "core";
                }

                function roleFromPackageToken(token) {
                  if (!token) return null;
                  if (token === "test" || token === "tests" || token === "spec" || token === "fixture" || token === "mock") {
                    return "test";
                  }
                  if (token.includes("service") || token === "application" || token === "usecase") return "service";
                  if (token.includes("util") || token === "utils" || token === "common" || token === "helper") return "utility";
                  if (token === "controller" || token === "api" || token === "web" || token === "rest" || token === "endpoint") {
                    return "entry";
                  }
                  if (token === "repository" || token === "repo" || token === "dao" || token === "persistence") {
                    return "data";
                  }
                  if (token === "domain" || token === "model" || token === "entity" || token === "dto" || token === "vo") {
                    return "domain";
                  }
                  if (token === "infra" || token === "infrastructure" || token === "config" || token === "bootstrap") {
                    return "infra";
                  }
                  if (token === "legacy" || token.includes("deprecated")) return "legacy";
                  return null;
                }

                function detectClusterFromPackage(packageName) {
                  if (!packageName || packageName === "(default)") {
                    return "default";
                  }
                  const tokens = packageName.split(".");
                  const lowerTokens = tokens.map((token) => token.toLowerCase());
                  const keyword = lowerTokens.find((token) =>
                    ["service", "util", "utility", "legacy", "domain", "repository", "infra", "controller"].includes(token)
                  );
                  if (keyword) {
                    return keyword;
                  }
                  if (
                    tokens.length >= 3 &&
                    ["com", "org", "net", "io", "dev", "edu"].includes(lowerTokens[0])
                  ) {
                    return lowerTokens[2];
                  }
                  if (tokens.length >= 2) {
                    return lowerTokens[1];
                  }
                  return lowerTokens[0];
                }

                function packageColor(packageName, packageRole = null) {
                  const role = packageRole ? normalizeRoleName(packageRole) : detectRoleFromPackage(packageName);
                  const base = new THREE.Color(ROLE_COLORS[role] || ROLE_COLORS.core);
                  const jitter = ((hashCode(packageName) % 17) - 8) * 0.0035;
                  const hsl = {};
                  base.getHSL(hsl);
                  return new THREE.Color().setHSL(
                    (hsl.h + jitter + 1) % 1,
                    THREE.MathUtils.clamp(hsl.s + 0.03, 0.25, 0.74),
                    THREE.MathUtils.clamp(hsl.l + 0.01, 0.35, 0.7)
                  );
                }

                function drawRoundedRect(ctx, x, y, width, height, radius) {
                  const r = Math.min(radius, width * 0.5, height * 0.5);
                  ctx.beginPath();
                  ctx.moveTo(x + r, y);
                  ctx.lineTo(x + width - r, y);
                  ctx.quadraticCurveTo(x + width, y, x + width, y + r);
                  ctx.lineTo(x + width, y + height - r);
                  ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
                  ctx.lineTo(x + r, y + height);
                  ctx.quadraticCurveTo(x, y + height, x, y + height - r);
                  ctx.lineTo(x, y + r);
                  ctx.quadraticCurveTo(x, y, x + r, y);
                  ctx.closePath();
                }

                function createLabelSprite(node, color) {
                  const canvas = document.createElement("canvas");
                  canvas.width = 480;
                  canvas.height = 156;
                  const ctx = canvas.getContext("2d");
                  if (!ctx) {
                    return null;
                  }

                  const tint = color.clone().lerp(new THREE.Color(0xffffff), 0.2);
                  const [r, g, b] = tint.toArray().map((v) => Math.round(v * 255));

                  drawRoundedRect(ctx, 20, 18, canvas.width - 40, canvas.height - 36, 18);
                  ctx.fillStyle = "rgba(12, 19, 32, 0.82)";
                  ctx.fill();
                  ctx.lineWidth = 3;
                  ctx.strokeStyle = `rgba(${r}, ${g}, ${b}, 0.86)`;
                  ctx.stroke();

                  const className = node.className.length > 24 ? `${node.className.slice(0, 23)}…` : node.className;
                  const roleLabel = ROLE_LABELS[normalizeRoleName(node.packageRole)] || "Core";
                  const packageLine = `[${roleLabel}] ${node.packageName}`;
                  const packageName = packageLine.length > 34 ? `${packageLine.slice(0, 33)}…` : packageLine;

                  ctx.textAlign = "center";
                  ctx.fillStyle = "#e9f2ff";
                  ctx.font = "bold 38px 'Noto Sans JP', sans-serif";
                  ctx.fillText(className, canvas.width * 0.5, 78);
                  ctx.fillStyle = "#b8cae2";
                  ctx.font = "26px 'Noto Sans JP', sans-serif";
                  ctx.fillText(packageName, canvas.width * 0.5, 118);

                  const texture = new THREE.CanvasTexture(canvas);
                  texture.colorSpace = THREE.SRGBColorSpace;
                  texture.minFilter = THREE.LinearFilter;
                  texture.magFilter = THREE.LinearFilter;

                  const material = new THREE.SpriteMaterial({
                    map: texture,
                    transparent: true,
                    opacity: 0.9,
                    depthWrite: false,
                    depthTest: false,
                  });

                  const sprite = new THREE.Sprite(material);
                  const width = THREE.MathUtils.clamp(Math.max(className.length, packageName.length) * 0.19, 3.3, 7.4);
                  sprite.scale.set(width, 1.28, 1);
                  sprite.center.set(0.5, 0);
                  sprite.renderOrder = 12;
                  return sprite;
                }

                function createRoleBadgeSprite(roleName, color) {
                  const rawRole = String(roleName || "");
                  const role = normalizeRoleName(rawRole);
                  const rawLower = rawRole.toLowerCase();
                  const labelText =
                    role === "core" && rawLower && rawLower !== "core"
                      ? rawRole
                      : ROLE_LABELS[role] || rawRole || "Core";

                  const canvas = document.createElement("canvas");
                  canvas.width = 280;
                  canvas.height = 92;
                  const ctx = canvas.getContext("2d");
                  if (!ctx) {
                    return null;
                  }

                  const tint = color.clone().lerp(new THREE.Color(0xffffff), 0.26);
                  const [r, g, b] = tint.toArray().map((v) => Math.round(v * 255));

                  drawRoundedRect(ctx, 10, 10, canvas.width - 20, canvas.height - 20, 16);
                  ctx.fillStyle = "rgba(12, 20, 34, 0.80)";
                  ctx.fill();
                  ctx.lineWidth = 2.5;
                  ctx.strokeStyle = `rgba(${r}, ${g}, ${b}, 0.88)`;
                  ctx.stroke();

                  ctx.textAlign = "center";
                  ctx.fillStyle = "#e8f2ff";
                  ctx.font = "bold 30px 'Noto Sans JP', sans-serif";
                  ctx.fillText(labelText, canvas.width * 0.5, 56);

                  const texture = new THREE.CanvasTexture(canvas);
                  texture.colorSpace = THREE.SRGBColorSpace;
                  texture.minFilter = THREE.LinearFilter;
                  texture.magFilter = THREE.LinearFilter;

                  const material = new THREE.SpriteMaterial({
                    map: texture,
                    transparent: true,
                    opacity: 0.86,
                    depthWrite: false,
                    depthTest: false,
                  });
                  const sprite = new THREE.Sprite(material);
                  sprite.scale.set(3.8, 1.25, 1);
                  sprite.center.set(0.5, 0.5);
                  sprite.renderOrder = 10;
                  return sprite;
                }

                function createGearGeometry(outerRadius, thickness, teethCount, holeRatio = 0.3) {
                  const teeth = Math.max(8, teethCount);
                  const rootRadius = outerRadius * 0.78;
                  const toothArc = (Math.PI * 2) / teeth;
                  const shape = new THREE.Shape();

                  for (let i = 0; i < teeth; i += 1) {
                    const a0 = i * toothArc;
                    const a1 = a0 + toothArc * 0.24;
                    const a2 = a0 + toothArc * 0.76;
                    const a3 = a0 + toothArc;

                    const p0 = new THREE.Vector2(Math.cos(a0) * rootRadius, Math.sin(a0) * rootRadius);
                    const p1 = new THREE.Vector2(Math.cos(a1) * outerRadius, Math.sin(a1) * outerRadius);
                    const p2 = new THREE.Vector2(Math.cos(a2) * outerRadius, Math.sin(a2) * outerRadius);
                    const p3 = new THREE.Vector2(Math.cos(a3) * rootRadius, Math.sin(a3) * rootRadius);

                    if (i === 0) {
                      shape.moveTo(p0.x, p0.y);
                    } else {
                      shape.lineTo(p0.x, p0.y);
                    }
                    shape.lineTo(p1.x, p1.y);
                    shape.lineTo(p2.x, p2.y);
                    shape.lineTo(p3.x, p3.y);
                  }
                  shape.closePath();

                  const innerHole = new THREE.Path();
                  innerHole.absarc(0, 0, outerRadius * holeRatio, 0, Math.PI * 2, true);
                  shape.holes.push(innerHole);

                  const geometry = new THREE.ExtrudeGeometry(shape, {
                    depth: thickness,
                    bevelEnabled: false,
                    curveSegments: 24,
                    steps: 1,
                  });
                  geometry.rotateX(-Math.PI / 2);
                  geometry.center();
                  return geometry;
                }

                const nodeMap = new Map(nodes.map((node) => [node.id, node]));
                const outgoing = new Map(nodes.map((node) => [node.id, []]));
                const incoming = new Map(nodes.map((node) => [node.id, 0]));
                const incomingRefs = new Map(nodes.map((node) => [node.id, []]));
                const neighbors = new Map(nodes.map((node) => [node.id, new Set()]));
                const packageMetaMap = new Map(
                  packages
                    .filter((pkg) => pkg && typeof pkg.name === "string")
                    .map((pkg) => {
                      const role = normalizeRoleName(pkg.role || detectRoleFromPackage(pkg.name));
                      const cluster = (pkg.cluster || detectClusterFromPackage(pkg.name)).toLowerCase();
                      const depth =
                        Number.isFinite(pkg.depth) && pkg.depth >= 0
                          ? pkg.depth
                          : pkg.name === "(default)"
                            ? 0
                            : pkg.name.split(".").length;
                      const classCount =
                        Number.isFinite(pkg.classCount) && pkg.classCount >= 0 ? pkg.classCount : 0;
                      return [
                        pkg.name,
                        {
                          name: pkg.name,
                          role,
                          cluster,
                          depth,
                          classCount,
                        },
                      ];
                    })
                );

                const normalizedEdges = [];
                for (const edge of edges) {
                  if (!nodeMap.has(edge.from) || !nodeMap.has(edge.to) || edge.from === edge.to) {
                    continue;
                  }
                  normalizedEdges.push(edge);
                  outgoing.get(edge.from).push(edge.to);
                  incoming.set(edge.to, (incoming.get(edge.to) || 0) + 1);
                  incomingRefs.get(edge.to).push(edge.from);
                  neighbors.get(edge.from).add(edge.to);
                  neighbors.get(edge.to).add(edge.from);
                }

                hudNodes.textContent = String(nodes.length);
                hudLinks.textContent = String(normalizedEdges.length);

                let rootId = nodes[0].id;
                let bestRootScore = Number.NEGATIVE_INFINITY;
                for (const node of nodes) {
                  const outDegree = outgoing.get(node.id).length;
                  const inDegree = incoming.get(node.id) || 0;
                  const score = outDegree * 2 - inDegree + node.complexity * 0.02;
                  if (score > bestRootScore) {
                    bestRootScore = score;
                    rootId = node.id;
                  }
                }
                hudRoot.textContent = rootId;

                const depthByNode = new Map();
                depthByNode.set(rootId, 0);
                const queue = [rootId];
                while (queue.length > 0) {
                  const current = queue.shift();
                  const currentDepth = depthByNode.get(current) || 0;
                  for (const next of outgoing.get(current) || []) {
                    if (!depthByNode.has(next)) {
                      depthByNode.set(next, currentDepth + 1);
                      queue.push(next);
                    }
                  }
                }

                let fallbackDepth = 1;
                for (const node of nodes) {
                  if (!depthByNode.has(node.id)) {
                    depthByNode.set(node.id, fallbackDepth);
                    fallbackDepth += 1;
                  }
                }

                const packageBuckets = new Map();
                for (const node of nodes) {
                  const pkg = node.packageName || "(default)";
                  if (!packageBuckets.has(pkg)) {
                    packageBuckets.set(pkg, []);
                  }
                  packageBuckets.get(pkg).push(node);
                }

                for (const [pkg, bucket] of packageBuckets.entries()) {
                  if (packageMetaMap.has(pkg)) {
                    continue;
                  }
                  packageMetaMap.set(pkg, {
                    name: pkg,
                    role: normalizeRoleName(detectRoleFromPackage(pkg)),
                    cluster: detectClusterFromPackage(pkg),
                    depth: pkg === "(default)" ? 0 : pkg.split(".").length,
                    classCount: bucket.length,
                  });
                }

                const roleStats = new Map();
                for (const meta of packageMetaMap.values()) {
                  const role = normalizeRoleName(meta.role);
                  if (!roleStats.has(role)) {
                    roleStats.set(role, { packageCount: 0, classCount: 0 });
                  }
                  const summary = roleStats.get(role);
                  summary.packageCount += 1;
                  summary.classCount += Math.max(0, meta.classCount);
                }
                if (packageRoleSummary) {
                  const roleOrder = ["service", "utility", "legacy", "entry", "data", "domain", "infra", "core", "test", "default"];
                  const entries = [...roleStats.entries()].sort((a, b) => {
                    const ai = roleOrder.indexOf(a[0]);
                    const bi = roleOrder.indexOf(b[0]);
                    if (ai !== bi) {
                      const left = ai < 0 ? 999 : ai;
                      const right = bi < 0 ? 999 : bi;
                      return left - right;
                    }
                    return b[1].classCount - a[1].classCount;
                  });
                  packageRoleSummary.innerHTML = entries
                    .map(([role, stats]) => {
                      const label = ROLE_LABELS[role] || role;
                      return `<li>${label}: ${stats.packageCount} pkg / ${stats.classCount} class</li>`;
                    })
                    .join("");
                }

                const clusterStats = new Map();
                for (const meta of packageMetaMap.values()) {
                  const cluster = (meta.cluster || "default").toLowerCase();
                  if (!clusterStats.has(cluster)) {
                    clusterStats.set(cluster, { packageCount: 0, classCount: 0 });
                  }
                  const summary = clusterStats.get(cluster);
                  summary.packageCount += 1;
                  summary.classCount += Math.max(0, meta.classCount);
                }
                if (packageClusterSummary) {
                  const entries = [...clusterStats.entries()].sort((a, b) => b[1].classCount - a[1].classCount);
                  packageClusterSummary.innerHTML = entries
                    .map(([cluster, stats]) => `<li>${cluster}: ${stats.packageCount} pkg / ${stats.classCount} class</li>`)
                    .join("");
                }

                if (externalLibraryList) {
                  externalLibraryList.innerHTML = externalLibraries
                    .slice(0, 12)
                    .map((lib) => {
                      const classCount = Number.isFinite(lib.classCount) ? lib.classCount : 0;
                      const importCount = Number.isFinite(lib.importCount) ? lib.importCount : 0;
                      return `<li>${lib.name} (${classCount} classes / ${importCount} imports)</li>`;
                    })
                    .join("");
                }

                const packageNames = [...packageBuckets.keys()].sort();
                const packageCenters = new Map();
                const rootGroups = new Map();
                const rootCenters = new Map();
                for (const pkg of packageNames) {
                  const root = (packageMetaMap.get(pkg)?.cluster || "core").toLowerCase();
                  if (!rootGroups.has(root)) {
                    rootGroups.set(root, []);
                  }
                  rootGroups.get(root).push(pkg);
                }

                const rootRoleOrder = [
                  "service",
                  "util",
                  "utility",
                  "legacy",
                  "entry",
                  "data",
                  "domain",
                  "infra",
                  "core",
                  "test",
                  "default",
                ];
                const rootNames = [...rootGroups.keys()].sort((a, b) => {
                  const ai = rootRoleOrder.indexOf(a);
                  const bi = rootRoleOrder.indexOf(b);
                  const left = ai < 0 ? 999 : ai;
                  const right = bi < 0 ? 999 : bi;
                  if (left !== right) {
                    return left - right;
                  }
                  return a.localeCompare(b);
                });
                const clusterDistanceScale = 1.08;
                const rootRingRadius = Math.max(12, rootNames.length * 5.2 * clusterDistanceScale);
                rootNames.forEach((rootName, rootIndex) => {
                  const rootAngle = (rootIndex / Math.max(1, rootNames.length)) * Math.PI * 2;
                  const rootCenter = {
                    x: Math.cos(rootAngle) * rootRingRadius,
                    z: Math.sin(rootAngle) * rootRingRadius * 0.68,
                  };
                  rootCenters.set(rootName, rootCenter);

                  const children = [...(rootGroups.get(rootName) || [])].sort((a, b) => a.localeCompare(b));
                  if (children.length === 1) {
                    packageCenters.set(children[0], rootCenter);
                    return;
                  }

                  children.forEach((pkg, childIndex) => {
                    const depth = Math.max(0, (packageMetaMap.get(pkg)?.depth || 0) - 1);
                    const localRadius = 2.8 + depth * 2.3 + Math.floor(childIndex / 6) * 1.5;
                    const phase = (hashCode(pkg) % 360) * (Math.PI / 180);
                    const localAngle = phase + (childIndex / Math.max(1, children.length)) * Math.PI * 2;
                    packageCenters.set(pkg, {
                      x: rootCenter.x + Math.cos(localAngle) * localRadius,
                      z: rootCenter.z + Math.sin(localAngle) * localRadius * 0.8,
                    });
                  });
                });

                const positionByNode = new Map();
                const packageRenderRadius = new Map();
                const DEPTH_LIFT_STEP = 0.34;
                const DEPTH_LIFT_MAX = 3.4;
                const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

                for (const [pkg, bucket] of packageBuckets.entries()) {
                  bucket.sort((a, b) => a.id.localeCompare(b.id));
                  const center = packageCenters.get(pkg);
                  const packageDepth = Math.max(0, (packageMetaMap.get(pkg)?.depth || 0) - 1);
                  const packageRadius =
                    2.9 +
                    Math.min(8.8, Math.sqrt(bucket.length) * 2.05 + Math.max(0, bucket.length - 1) * 0.16) +
                    packageDepth * 0.46;
                  packageRenderRadius.set(pkg, packageRadius);
                  const phase = (hashCode(pkg) % 360) * (Math.PI / 180);

                  bucket.forEach((node, index) => {
                    const ratio = (index + 0.5) / Math.max(1, bucket.length);
                    const radial = packageRadius * 0.9 * Math.sqrt(ratio);
                    const angle = phase + index * GOLDEN_ANGLE;
                    const depth = depthByNode.get(node.id) || 0;
                    const x = center.x + Math.cos(angle) * radial;
                    const z = center.z + Math.sin(angle) * radial;
                    const depthLift = Math.min(DEPTH_LIFT_MAX, Math.max(0, depth) * DEPTH_LIFT_STEP);
                    const y = 1.0 + mapRange(node.risk, 0, 100, 0.8, 5.8) + depthLift;
                    positionByNode.set(node.id, new THREE.Vector3(x, y, z));
                  });
                }

                const renderer = new THREE.WebGLRenderer({
                  canvas,
                  antialias: true,
                  powerPreference: "high-performance",
                });
                renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
                renderer.outputColorSpace = THREE.SRGBColorSpace;
                renderer.toneMapping = THREE.NeutralToneMapping;
                renderer.toneMappingExposure = 1.0;
                renderer.setClearColor(0x8e97a4, 1);

                const scene = new THREE.Scene();
                scene.background = new THREE.Color(0x929ba8);
                scene.fog = null;

                const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 500);
                const cameraHomePosition = new THREE.Vector3(34, 28, 34);
                const cameraHomeTarget = new THREE.Vector3(0, 4, -16);
                camera.position.copy(cameraHomePosition);

                const controls = new OrbitControls(camera, renderer.domElement);
                controls.enableDamping = true;
                controls.dampingFactor = 0.07;
                controls.target.copy(cameraHomeTarget);
                controls.minDistance = 4.5;
                controls.maxDistance = 190;
                controls.maxPolarAngle = Math.PI * 0.49;

                const ambientLight = new THREE.AmbientLight(0x8ea6c4, 0.66);
                const keyLight = new THREE.DirectionalLight(0x8cb4de, 0.56);
                keyLight.position.set(32, 40, 24);
                const fillLight = new THREE.DirectionalLight(0x6d8eb8, 0.32);
                fillLight.position.set(-26, 18, -20);
                scene.add(ambientLight, keyLight, fillLight);

                const world = new THREE.Group();
                const packageLayer = new THREE.Group();
                const edgeLayer = new THREE.Group();
                const nodeLayer = new THREE.Group();
                world.add(packageLayer, edgeLayer, nodeLayer);
                scene.add(world);

                const grid = new THREE.GridHelper(140, 28, 0x8090a5, 0x70819a);
                grid.position.y = -0.15;
                grid.material.opacity = 0.2;
                grid.material.transparent = true;
                scene.add(grid);

                for (const [rootName, center] of rootCenters.entries()) {
                  const ringRadius = 3.8 + Math.min(7.2, (rootGroups.get(rootName) || []).length * 1.15);
                  const rootColor = packageColor(rootName, rootName).multiplyScalar(0.52);
                  const rootRing = new THREE.Mesh(
                    new THREE.TorusGeometry(ringRadius, 0.12, 10, 58),
                    new THREE.MeshBasicMaterial({
                      color: rootColor,
                      transparent: true,
                      opacity: 0.34,
                    })
                  );
                  rootRing.rotation.x = Math.PI / 2;
                  rootRing.position.set(center.x, 0.08, center.z);
                  packageLayer.add(rootRing);

                  const roleBadge = createRoleBadgeSprite(rootName, rootColor);
                  if (roleBadge) {
                    roleBadge.position.set(center.x, 0.5, center.z);
                    packageLayer.add(roleBadge);
                  }
                }

                for (const [pkg, bucket] of packageBuckets.entries()) {
                  const center = packageCenters.get(pkg);
                  const ringRadius = packageRenderRadius.get(pkg) || (1.8 + Math.min(5.4, bucket.length * 0.36));
                  const ringColor = packageColor(pkg, packageMetaMap.get(pkg)?.role || "core").multiplyScalar(0.72);
                  const ring = new THREE.Mesh(
                    new THREE.TorusGeometry(ringRadius, 0.08, 10, 50),
                    new THREE.MeshBasicMaterial({
                      color: ringColor,
                      transparent: true,
                      opacity: 0.28,
                    })
                  );
                  ring.rotation.x = Math.PI / 2;
                  ring.position.set(center.x, 0.1, center.z);
                  packageLayer.add(ring);
                }

                const pointer = new THREE.Vector2();
                const raycaster = new THREE.Raycaster();
                const pickTargets = [];
                const nodeEntries = new Map();
                const edgeEntries = [];
                const spinnerEntries = [];

                const maxComplexity = Math.max(...nodes.map((node) => Math.max(1, node.complexity)), 1);

                for (const node of nodes) {
                  const position = positionByNode.get(node.id);
                  const group = new THREE.Group();
                  group.position.copy(position);
                  group.userData.nodeId = node.id;
                  group.userData.targetScale = 1;

                  const baseColor = packageColor(node.packageName, node.packageRole);
                  const riskTint = new THREE.Color(0xd57986);
                  const nodeColor = baseColor.clone().lerp(riskTint, node.risk / 140);

                  const gearCount = node.complexity >= Math.max(10, Math.round(maxComplexity * 0.62)) ? 2 : 1;
                  const meshes = [];
                  for (let index = 0; index < gearCount; index += 1) {
                    const radius =
                      mapRange(node.methodCount, 1, 12, 0.9, 1.45) + (gearCount === 2 ? index * 0.42 : 0);
                    const thickness = 0.42;
                    const teeth = 10 + Math.round(mapRange(node.complexity, 1, maxComplexity, 2, 12));
                    const geometry = createGearGeometry(radius, thickness, teeth);
                    const material = new THREE.MeshStandardMaterial({
                      color: nodeColor,
                      roughness: 0.88,
                      metalness: 0.05,
                    });
                    const mesh = new THREE.Mesh(geometry, material);
                    mesh.position.y = index * 0.52;
                    mesh.rotation.y = index * 0.4;
                    mesh.userData.nodeId = node.id;
                    mesh.userData.baseColor = nodeColor.clone();
                    group.add(mesh);
                    meshes.push(mesh);
                    pickTargets.push(mesh);

                    spinnerEntries.push({
                      mesh,
                      speed: (0.0024 + node.complexity * 0.00011) * (index % 2 === 0 ? 1 : -1),
                    });
                  }

                  const label = createLabelSprite(node, nodeColor);
                  if (label) {
                    label.position.set(0, gearCount * 0.58 + 0.5, 0);
                    group.add(label);
                  }

                  nodeLayer.add(group);
                  nodeEntries.set(node.id, {
                    node,
                    group,
                    meshes,
                    label,
                  });
                }

                for (const edge of normalizedEdges) {
                  const from = positionByNode.get(edge.from);
                  const to = positionByNode.get(edge.to);
                  if (!from || !to) {
                    continue;
                  }

                  const distance = from.distanceTo(to);
                  const control = from.clone().lerp(to, 0.5);
                  control.y += 0.8 + distance * 0.11;

                  const curve = new THREE.QuadraticBezierCurve3(from, control, to);
                  const points = curve.getPoints(20);
                  const geometry = new THREE.BufferGeometry().setFromPoints(points);

                  const samePackage = nodeMap.get(edge.from)?.packageName === nodeMap.get(edge.to)?.packageName;
                  const baseColor = samePackage ? 0x95b9df : 0xbd95df;
                  const material = new THREE.LineBasicMaterial({
                    color: baseColor,
                    transparent: true,
                    opacity: 0.3,
                  });
                  const line = new THREE.Line(geometry, material);
                  edgeLayer.add(line);
                  edgeEntries.push({
                    edge,
                    line,
                    baseColor,
                  });
                }

                let hoveredId = null;
                let selectedId = rootId;
                const focusState = {
                  active: false,
                  target: new THREE.Vector3(),
                  camera: new THREE.Vector3(),
                };

                function toScoreClass(level) {
                  return `score-${String(level || "Low").toLowerCase()}`;
                }

                function classifyRiskLevel(riskScore) {
                  const risk = Number.isFinite(riskScore) ? riskScore : 0;
                  if (risk >= 70) {
                    return "High";
                  }
                  if (risk >= 40) {
                    return "Medium";
                  }
                  return "Low";
                }

                function formatConnectedLabel(nodeId) {
                  const target = nodeMap.get(nodeId);
                  return target ? target.className : nodeId;
                }

                function appendMetric(label, value) {
                  const li = document.createElement("li");
                  const left = document.createElement("span");
                  left.textContent = label;
                  const right = document.createElement("span");
                  right.className = "detail-value";
                  right.textContent = value;
                  li.appendChild(left);
                  li.appendChild(right);
                  detailMetrics.appendChild(li);
                }

                function updateDetails(nodeId) {
                  const entry = nodeEntries.get(nodeId);
                  if (!entry) {
                    return;
                  }
                  const node = entry.node;
                  const classExternalLibs = Array.isArray(node.externalLibraries) ? node.externalLibraries : [];
                  const outgoingRefs = outgoing.get(node.id) || [];
                  const incomingNodeRefs = incomingRefs.get(node.id) || [];
                  const depth = depthByNode.get(node.id) || 0;
                  const riskLevel = classifyRiskLevel(node.risk);
                  const roleLabel = ROLE_LABELS[normalizeRoleName(node.packageRole)] || node.packageRole || "Core";

                  detailTitle.textContent = `Class: ${node.className}`;
                  detailScore.textContent = riskLevel;
                  detailScore.className = `detail-score ${toScoreClass(riskLevel)}`;

                  detailMetrics.innerHTML = "";
                  appendMetric("Package", node.packageName || "(default)");
                  appendMetric("Role", roleLabel);
                  appendMetric("Cluster", node.packageCluster || "-");
                  appendMetric("LOC", String(node.loc));
                  appendMetric("Methods", String(node.methodCount));
                  appendMetric("Complexity Max", `CC ${node.complexity}`);
                  appendMetric("Risk Score", `${node.risk} / 100`);
                  appendMetric("Dependency Depth", String(depth));
                  appendMetric("Outgoing deps", String(outgoingRefs.length));
                  appendMetric("Incoming deps", String(incomingNodeRefs.length));
                  appendMetric("External libs", String(classExternalLibs.length));

                  detailDocs.innerHTML = "";
                  const detailLinks = Array.isArray(node.detailLinks) ? node.detailLinks : [];
                  if (!detailLinks.length) {
                    const li = document.createElement("li");
                    li.className = "detail-doc-item";
                    li.textContent = "No class detail reports";
                    detailDocs.appendChild(li);
                  } else {
                    detailLinks.forEach((detail) => {
                      if (!detail || !detail.href) {
                        return;
                      }
                      const li = document.createElement("li");
                      li.className = "detail-doc-item";
                      const link = document.createElement("a");
                      link.className = "detail-link";
                      link.href = detail.href;
                      link.textContent = detail.label || detail.href;
                      link.title = detail.href;
                      link.target = "_blank";
                      link.rel = "noopener noreferrer";
                      li.appendChild(link);
                      detailDocs.appendChild(li);
                    });
                  }

                  detailReasons.innerHTML = "";
                  const reasons = [];
                  if (node.risk >= 70) {
                    reasons.push("High leverage hotspot from combined complexity and dependency impact.");
                  } else if (node.risk >= 40) {
                    reasons.push("Moderate leverage hotspot requiring periodic review.");
                  } else {
                    reasons.push("Lower leverage hotspot compared with other classes.");
                  }
                  if (node.complexity >= Math.max(10, Math.round(maxComplexity * 0.62))) {
                    reasons.push("Complexity is in the upper tier of this run.");
                  }
                  if (outgoingRefs.length >= 3) {
                    reasons.push("Depends on multiple classes, so change ripple can expand.");
                  }
                  if (incomingNodeRefs.length >= 3) {
                    reasons.push("Referenced by many classes; regression impact can be broad.");
                  }
                  if (depth >= 3) {
                    reasons.push("Located deep in dependency layers from the root class.");
                  }
                  if (classExternalLibs.length > 0) {
                    const suffix = classExternalLibs.length > 3 ? " ..." : "";
                    reasons.push(`Uses external libraries: ${classExternalLibs.slice(0, 3).join(", ")}${suffix}`);
                  }
                  reasons.forEach((reason) => {
                    const li = document.createElement("li");
                    li.textContent = reason;
                    detailReasons.appendChild(li);
                  });

                  detailConnections.innerHTML = "";
                  const connections = [];
                  outgoingRefs.forEach((targetId) => {
                    const targetNode = nodeMap.get(targetId);
                    connections.push({
                      label: `OUT -> ${formatConnectedLabel(targetId)}`,
                      level: classifyRiskLevel(targetNode?.risk),
                    });
                  });
                  incomingNodeRefs.forEach((sourceId) => {
                    const sourceNode = nodeMap.get(sourceId);
                    connections.push({
                      label: `IN <- ${formatConnectedLabel(sourceId)}`,
                      level: classifyRiskLevel(sourceNode?.risk),
                    });
                  });
                  connections.sort((a, b) => a.label.localeCompare(b.label));

                  if (connections.length === 0) {
                    const li = document.createElement("li");
                    li.textContent = "No connected nodes";
                    detailConnections.appendChild(li);
                  } else {
                    connections.slice(0, 12).forEach((connection) => {
                      const li = document.createElement("li");
                      const name = document.createElement("span");
                      name.textContent = connection.label;
                      const pill = document.createElement("span");
                      pill.className = "detail-pill";
                      pill.textContent = connection.level;
                      li.appendChild(name);
                      li.appendChild(pill);
                      detailConnections.appendChild(li);
                    });
                  }
                }

                function queueCameraMove(nextTarget, nextCamera, immediate = false) {
                  if (immediate) {
                    controls.target.copy(nextTarget);
                    camera.position.copy(nextCamera);
                    controls.update();
                    focusState.active = false;
                    return;
                  }
                  focusState.target.copy(nextTarget);
                  focusState.camera.copy(nextCamera);
                  focusState.active = true;
                }

                function queueFocusToNode(nodeId, immediate = false) {
                  const entry = nodeEntries.get(nodeId);
                  if (!entry) {
                    return;
                  }

                  const focusPoint = entry.group.position.clone();
                  focusPoint.y += 1.3;

                  const direction = camera.position.clone().sub(controls.target);
                  if (direction.lengthSq() < 0.0001) {
                    direction.set(1, 0.52, 1);
                  }
                  direction.normalize();

                  const baseDistance = THREE.MathUtils.clamp(10 + entry.node.complexity * 0.22, 8.5, 18.5);
                  const desiredCamera = focusPoint.clone().add(direction.multiplyScalar(baseDistance));
                  desiredCamera.y += 2.2;
                  queueCameraMove(focusPoint, desiredCamera, immediate);
                }

                function resetCameraView(immediate = false) {
                  queueCameraMove(cameraHomeTarget, cameraHomePosition, immediate);
                }

                function zoomByFactor(factor) {
                  const direction = camera.position.clone().sub(controls.target);
                  if (direction.lengthSq() < 0.000001) {
                    return;
                  }
                  const currentDistance = direction.length();
                  const nextDistance = THREE.MathUtils.clamp(
                    currentDistance * factor,
                    controls.minDistance,
                    controls.maxDistance
                  );
                  direction.normalize().multiplyScalar(nextDistance);
                  const nextCamera = controls.target.clone().add(direction);
                  queueCameraMove(controls.target.clone(), nextCamera, false);
                }

                function applyVisualState() {
                  const selectedNeighbors = selectedId ? neighbors.get(selectedId) || new Set() : new Set();

                  for (const [nodeId, entry] of nodeEntries.entries()) {
                    const isSelected = nodeId === selectedId;
                    const isHovered = nodeId === hoveredId;
                    const isNeighbor = selectedNeighbors.has(nodeId);
                    const dimmed = selectedId && !isSelected && !isNeighbor;

                    entry.group.userData.targetScale = isSelected ? 1.22 : isHovered ? 1.12 : isNeighbor ? 1.06 : 1;

                    for (const mesh of entry.meshes) {
                      const material = mesh.material;
                      const baseColor = mesh.userData.baseColor;
                      const nextColor = baseColor.clone();
                      if (isSelected) {
                        nextColor.offsetHSL(0, 0.02, 0.08);
                      } else if (isHovered) {
                        nextColor.offsetHSL(0, 0.015, 0.04);
                      } else if (dimmed) {
                        nextColor.multiplyScalar(0.5);
                      }
                      material.color.copy(nextColor);
                    }

                    if (entry.label) {
                      entry.label.material.opacity = dimmed ? 0.38 : isSelected ? 0.96 : 0.82;
                    }
                  }

                  for (const entry of edgeEntries) {
                    const related = selectedId && (entry.edge.from === selectedId || entry.edge.to === selectedId);
                    entry.line.material.color.setHex(related ? 0xf1c37a : entry.baseColor);
                    entry.line.material.opacity = selectedId ? (related ? 0.9 : 0.05) : 0.3;
                  }
                }

                function updatePointer(clientX, clientY) {
                  const rect = canvas.getBoundingClientRect();
                  pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
                  pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
                  raycaster.setFromCamera(pointer, camera);
                  const intersections = raycaster.intersectObjects(pickTargets, false);
                  const nextHovered = intersections.length > 0 ? intersections[0].object.userData.nodeId : null;
                  if (nextHovered !== hoveredId) {
                    hoveredId = nextHovered;
                    applyVisualState();
                  }
                }

                canvas.addEventListener("pointermove", (event) => {
                  updatePointer(event.clientX, event.clientY);
                });

                canvas.addEventListener("pointerleave", () => {
                  hoveredId = null;
                  applyVisualState();
                });

                canvas.addEventListener("click", (event) => {
                  updatePointer(event.clientX, event.clientY);
                  if (hoveredId) {
                    selectedId = hoveredId;
                    updateDetails(selectedId);
                    queueFocusToNode(selectedId);
                    applyVisualState();
                  }
                });

                btnResetCamera?.addEventListener("click", () => {
                  resetCameraView();
                });

                btnFocusSelected?.addEventListener("click", () => {
                  if (selectedId) {
                    queueFocusToNode(selectedId);
                  }
                });

                btnZoomIn?.addEventListener("click", () => {
                  zoomByFactor(0.8);
                });

                btnZoomOut?.addEventListener("click", () => {
                  zoomByFactor(1.25);
                });

                tabVisualButton?.addEventListener("click", () => {
                  activateTab("visual");
                });

                tabReportButton?.addEventListener("click", () => {
                  activateTab("report");
                });

                window.addEventListener("keydown", (event) => {
                  const tagName = event.target && event.target.tagName ? event.target.tagName.toUpperCase() : "";
                  if (["INPUT", "TEXTAREA", "SELECT", "BUTTON"].includes(tagName)) {
                    return;
                  }

                  if (event.key === "r" || event.key === "R") {
                    event.preventDefault();
                    resetCameraView();
                  } else if (event.key === "f" || event.key === "F") {
                    event.preventDefault();
                    if (selectedId) {
                      queueFocusToNode(selectedId);
                    }
                  } else if (event.key === "+" || event.key === "=") {
                    event.preventDefault();
                    zoomByFactor(0.8);
                  } else if (event.key === "-" || event.key === "_") {
                    event.preventDefault();
                    zoomByFactor(1.25);
                  }
                });

                controls.addEventListener("start", () => {
                  focusState.active = false;
                });

                function resize() {
                  const rect = stage.getBoundingClientRect();
                  const width = Math.max(320, rect.width);
                  const height = Math.max(260, rect.height);
                  renderer.setSize(width, height, false);
                  camera.aspect = width / height;
                  camera.updateProjectionMatrix();
                }

                window.addEventListener("resize", resize);
                resize();

                initReportTab();
                activateTab("visual");
                updateDetails(selectedId);
                queueFocusToNode(selectedId, true);
                applyVisualState();

                const tempScale = new THREE.Vector3();
                function animate() {
                  if (focusState.active) {
                    controls.target.lerp(focusState.target, 0.14);
                    camera.position.lerp(focusState.camera, 0.12);
                    if (
                      controls.target.distanceToSquared(focusState.target) < 0.0005 &&
                      camera.position.distanceToSquared(focusState.camera) < 0.002
                    ) {
                      focusState.active = false;
                    }
                  }
                  controls.update();

                  for (const spinner of spinnerEntries) {
                    spinner.mesh.rotation.y += spinner.speed;
                  }

                  for (const entry of nodeEntries.values()) {
                    const target = entry.group.userData.targetScale || 1;
                    tempScale.set(target, target, target);
                    entry.group.scale.lerp(tempScale, 0.12);
                  }

                  renderer.render(scene, camera);
                  requestAnimationFrame(animate);
                }

                animate();
              </script>
            </body>
            </html>
            """;
    return template
        .replace("__LOCALE__", escapeHtml(resolveLocaleTag()))
        .replace("__TITLE__", escapeHtml(MessageSource.getMessage("explore.html.title")))
        .replace("__SUBTITLE__", escapeHtml(MessageSource.getMessage("explore.html.subtitle")))
        .replace(
            "__LEGEND_GEAR__", escapeHtml(MessageSource.getMessage("explore.html.legend.gear")))
        .replace(
            "__LEGEND_ROLE__", escapeHtml(MessageSource.getMessage("explore.html.legend.role")))
        .replace(
            "__LEGEND_PACKAGE__",
            escapeHtml(MessageSource.getMessage("explore.html.legend.package")))
        .replace(
            "__LEGEND_EDGE__", escapeHtml(MessageSource.getMessage("explore.html.legend.edge")))
        .replace(
            "__LEGEND_INTERACTION__",
            escapeHtml(MessageSource.getMessage("explore.html.legend.interaction")))
        .replace(
            "__HINT_PACKAGE_LAYOUT__",
            escapeHtml(MessageSource.getMessage("explore.html.hint.package_layout")))
        .replace(
            "__REPORT_SUBTITLE__",
            escapeHtml(MessageSource.getMessage("explore.html.report.subtitle")))
        .replace(
            "__ERROR_NO_DATA__",
            escapeHtml(MessageSource.getMessage("explore.html.error.no_data")))
        .replace(
            "__ERROR_THREEJS_LOAD_FAILED__",
            escapeHtml(MessageSource.getMessage("explore.html.error.threejs_load_failed")))
        .replace(
            "__REPORT_NO_ARTIFACTS__",
            escapeHtml(MessageSource.getMessage("explore.html.report.no_artifacts")))
        .replace("__RUN_ID__", escapeHtml(snapshot.runId()))
        .replace("__GENERATED_AT__", escapeHtml(snapshot.generatedAt()))
        .replace("__TOP_COMPLEXITY__", escapeHtml(snapshot.topComplexity()))
        .replace("__CLASS_COUNT__", String.valueOf(snapshot.totalClasses()))
        .replace("__PACKAGE_COUNT__", String.valueOf(snapshot.totalPackages()))
        .replace("__METHOD_COUNT__", String.valueOf(snapshot.totalMethods()))
        .replace("__SNAPSHOT_JSON__", toEmbeddedJson(snapshot));
  }

  private String toEmbeddedJson(final Object value) {
    try {
      return jsonService.toJson(value).replace("</", "<\\/");
    } catch (IOException e) {
      throw new IllegalStateException(
          MessageSource.getMessage("explore.flow.error.embedded_json_render_failed"), e);
    }
  }

  private String resolveLocaleTag() {
    final Locale locale = MessageSource.getLocale();
    if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
      return "en";
    }
    return locale.getLanguage();
  }

  private String escapeHtml(final String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public record Result(
      Path outputDirectory,
      Path indexFile,
      Path snapshotFile,
      int classCount,
      int packageCount,
      int methodCount) {}

  private record Snapshot(
      String runId,
      String generatedAt,
      int totalClasses,
      int totalPackages,
      int totalMethods,
      String topComplexity,
      List<PackageData> packages,
      List<NodeData> nodes,
      List<EdgeData> edges,
      List<LinkData> reportLinks,
      List<ExternalLibraryData> externalLibraries) {}

  private record NodeData(
      String id,
      String className,
      String packageName,
      String packageRole,
      String packageCluster,
      int loc,
      int methodCount,
      int complexity,
      int risk,
      List<LinkData> detailLinks,
      List<String> externalLibraries) {}

  private record PackageData(String name, String role, String cluster, int depth, int classCount) {}

  private record LinkData(String label, String href) {}

  private record ExternalLibraryData(String name, int classCount, int importCount) {}

  private record ExternalLibrarySummary(
      List<ExternalLibraryData> libraries, Map<String, List<String>> librariesByClass) {}

  private static final class ExternalLibraryAccumulator {

    private final Set<String> classes = new LinkedHashSet<>();

    private int importCount;

    void add(final String classFqn) {
      if (classFqn != null) {
        classes.add(classFqn);
      }
      importCount++;
    }

    int classCount() {
      return classes.size();
    }

    int importCount() {
      return importCount;
    }
  }

  private record EdgeData(String from, String to, int weight) {}

  private record TopComplexity(String classFqn, String methodName, int score) {}
}
