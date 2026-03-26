package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates human-readable Markdown documentation from analysis results.
 *
 * <p>This is separate from the test generation pipeline and provides a developer-friendly view of
 * the codebase structure.
 */
public class MarkdownDocumentGenerator implements DocumentGenerator {

  private static final String FORMAT = "markdown";

  private static final String EXTENSION = ".md";

  private static final String TABLE_CELL_END = "` |\n";

  private static final String KEY_TABLE_VALUE = "document.table.value";

  private static final String KEY_LABEL_LINES = "document.label.lines";

  private static final String KEY_VALUE_EMPTY = "document.value.empty";

  private static final String WARNING_PREFIX = "> ⚠️ ";

  private static final String SECTION_TITLE_SUFFIX = "**:\n";

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private static final int CALLED_METHODS_PREVIEW_LIMIT = 10;

  private static final int LIST_PREVIEW_LIMIT = 8;

  private static final int CONDITION_PREVIEW_LIMIT = 3;

  private static final int CALL_PARAMETER_PREVIEW_LIMIT = 4;

  private static final String CODE_FENCE = "```";

  private static final String KEY_TABLE_TYPE = "document.table.type";

  private static final String KEY_TABLE_NOTES = "document.table.notes";

  private static final String KEY_LIST_MORE_TABLE = "document.list.more.table";

  private static final String KEY_VALUE_UNKNOWN = "document.value.unknown";

  private static final String GUIDE_PREFIX_KEY = "document.guide.label";

  private static final Set<String> CORE_LIBRARY_OWNERS =
      Set.of(
          "String",
          "Object",
          "Optional",
          "List",
          "Set",
          "Map",
          "Collection",
          "Iterable",
          "BigDecimal",
          "LocalDateTime",
          "LocalDate",
          "Instant");

  @Override
  public int generate(final AnalysisResult result, final Path outputDir, final Config config)
      throws IOException {
    return generateAll(result, outputDir);
  }

  @Override
  public String getFormat() {
    return FORMAT;
  }

  @Override
  public String getFileExtension() {
    return EXTENSION;
  }

  /**
   * Generates Markdown documentation for all classes in the analysis result.
   *
   * @param result the analysis result
   * @param outputDir the directory to write documentation files
   * @return the number of files generated
   * @throws IOException if writing fails
   */
  public int generateAll(final AnalysisResult result, final Path outputDir) throws IOException {
    Files.createDirectories(outputDir);
    int count = 0;
    for (final ClassInfo classInfo : result.getClasses()) {
      final String markdown = generateClassDocument(classInfo);
      final String relativePath =
          DocumentUtils.generateSourceAlignedReportPath(classInfo, EXTENSION);
      final Path outputFile = outputDir.resolve(relativePath);
      final Path parent = outputFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(outputFile, markdown, StandardCharsets.UTF_8);
      removeLegacyFlatOutput(outputDir, classInfo, outputFile);
      count++;
    }
    return count;
  }

  private void removeLegacyFlatOutput(
      final Path outputDir, final ClassInfo classInfo, final Path resolvedOutputFile)
      throws IOException {
    final String legacyName = DocumentUtils.generateFileName(classInfo.getFqn(), EXTENSION);
    final Path legacyPath = outputDir.resolve(legacyName);
    if (legacyPath.equals(resolvedOutputFile)) {
      return;
    }
    Files.deleteIfExists(legacyPath);
  }

  /**
   * Generates Markdown documentation for a single class.
   *
   * @param classInfo the class information
   * @return the Markdown content
   */
  public String generateClassDocument(final ClassInfo classInfo) {
    final StringBuilder sb = new StringBuilder();
    final List<MethodInfo> methods = DocumentUtils.filterMethodsForSpecification(classInfo);
    final String classSimpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    // Header
    sb.append("# ").append(classSimpleName).append("\n\n");
    // Analysis summary
    generateAnalysisSummary(sb, classInfo, methods);
    // Class summary
    generateClassSummary(sb, classInfo, methods);
    // Fields
    if (!classInfo.getFields().isEmpty()) {
      generateFieldsSection(sb, classInfo.getFields());
    }
    // Methods
    if (!methods.isEmpty()) {
      generateMethodsSection(sb, methods, classSimpleName);
    }
    return sb.toString();
  }

  private void generateAnalysisSummary(
      final StringBuilder sb, final ClassInfo classInfo, final List<MethodInfo> methods) {
    final MethodStats stats = MethodStats.from(methods);
    sb.append("## ").append(msg("document.md.section.analysis_summary")).append("\n\n");
    appendSectionGuide(sb, "document.guide.analysis_summary");
    sb.append("| ")
        .append(msg("document.table.metric"))
        .append(" | ")
        .append(msg(KEY_TABLE_VALUE))
        .append(" |\n");
    sb.append("|------|----|\n");
    sb.append("| ")
        .append(msg("document.label.type"))
        .append(" | ")
        .append(DocumentUtils.buildClassType(classInfo))
        .append(" |\n");
    sb.append("| ")
        .append(msg(KEY_LABEL_LINES))
        .append(" | ")
        .append(msg("document.value.lines", classInfo.getLoc()))
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.fields"))
        .append(" | ")
        .append(classInfo.getFields().size())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.methods"))
        .append(" | ")
        .append(methods.size())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.high_complexity_threshold"))
        .append(" | ")
        .append(stats.highComplexityCount)
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.dead_code_candidates"))
        .append(" | ")
        .append(stats.deadCodeCount)
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.duplicate_code_candidates"))
        .append(" | ")
        .append(stats.duplicateCount)
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.removed_api_usage"))
        .append(" | ")
        .append(stats.removedApiCount)
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.cycle_involvement"))
        .append(" | ")
        .append(stats.cycleCount)
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.brittle_signals"))
        .append(" | ")
        .append(stats.brittleCount)
        .append(" |\n\n");
  }

  private void generateClassSummary(
      final StringBuilder sb, final ClassInfo classInfo, final List<MethodInfo> methods) {
    sb.append("## ").append(msg("document.md.section.class_overview")).append("\n\n");
    appendSectionGuide(sb, "document.guide.class_overview");
    appendClassBadges(sb, classInfo);
    sb.append("| ")
        .append(msg("document.table.item"))
        .append(" | ")
        .append(msg(KEY_TABLE_VALUE))
        .append(" |\n");
    sb.append("|------|----|\n");
    sb.append("| ")
        .append(msg("document.label.package"))
        .append(" | `")
        .append(DocumentUtils.formatPackageNameForDisplay(DocumentUtils.getPackageName(classInfo)))
        .append(TABLE_CELL_END);
    sb.append("| ")
        .append(msg("document.label.file"))
        .append(" | `")
        .append(nullSafe(classInfo.getFilePath()))
        .append(TABLE_CELL_END);
    sb.append("| ")
        .append(msg(KEY_LABEL_LINES))
        .append(" | ")
        .append(msg("document.value.lines", classInfo.getLoc()))
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.methods"))
        .append(" | ")
        .append(methods.size())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.fields"))
        .append(" | ")
        .append(classInfo.getFields().size())
        .append(" |\n");
    // Inheritance
    if (!classInfo.getExtendsTypes().isEmpty()) {
      sb.append("| ")
          .append(msg("document.label.extends"))
          .append(" | `")
          .append(String.join(", ", classInfo.getExtendsTypes()))
          .append(TABLE_CELL_END);
    }
    if (!classInfo.getImplementsTypes().isEmpty()) {
      sb.append("| ")
          .append(msg("document.label.implements"))
          .append(" | `")
          .append(String.join(", ", classInfo.getImplementsTypes()))
          .append(TABLE_CELL_END);
    }
    // Annotations
    if (!classInfo.getAnnotations().isEmpty()) {
      sb.append("| ").append(msg("document.label.annotations")).append(" | ");
      for (final String annotation : classInfo.getAnnotations()) {
        sb.append("`@").append(annotation).append("` ");
      }
      sb.append("|\n");
    }
    sb.append("\n");
    // Warnings
    if (classInfo.isDeadCode()) {
      sb.append(WARNING_PREFIX).append(msg("document.warning.dead_code")).append("\n\n");
    }
  }

  private void appendClassBadges(final StringBuilder sb, final ClassInfo classInfo) {
    final List<String> badges = new ArrayList<>();
    sb.append("**")
        .append(msg("document.label.type"))
        .append("**: ")
        .append(DocumentUtils.buildClassType(classInfo))
        .append("\n\n");
    if (classInfo.isNestedClass()) {
      badges.add(msg("document.badge.nested"));
    }
    if (classInfo.isAnonymous()) {
      badges.add(msg("document.badge.anonymous"));
    }
    if (classInfo.hasNestedClasses()) {
      badges.add(msg("document.badge.has_inner"));
    }
    if (!badges.isEmpty()) {
      sb.append("**")
          .append(msg("document.label.attributes"))
          .append("**: ")
          .append(String.join(" / ", badges))
          .append("\n\n");
    }
  }

  private void generateFieldsSection(final StringBuilder sb, final List<FieldInfo> fields) {
    sb.append("## ").append(msg("document.md.section.fields")).append("\n\n");
    appendSectionGuide(sb, "document.guide.fields");
    sb.append("| ")
        .append(msg("document.table.name"))
        .append(" | ")
        .append(msg(KEY_TABLE_TYPE))
        .append(" | ")
        .append(msg("document.table.visibility"))
        .append(" |\n");
    sb.append("|------|-----|--------|\n");
    for (final FieldInfo field : fields) {
      sb.append("| `").append(field.getName()).append("` ");
      sb.append("| `").append(field.getType()).append("` ");
      sb.append("| ")
          .append(DocumentUtils.translateVisibility(field.getVisibility()))
          .append(" |\n");
    }
    sb.append("\n");
  }

  private void generateMethodsSection(
      final StringBuilder sb, final List<MethodInfo> methods, final String classSimpleName) {
    sb.append("## ").append(msg("document.md.section.methods")).append("\n\n");
    appendSectionGuide(sb, "document.guide.methods");
    // Summary table
    sb.append("| ")
        .append(msg("document.table.method"))
        .append(" | ")
        .append(msg("document.table.visibility"))
        .append(" | ")
        .append(msg("document.table.lines"))
        .append(" | ")
        .append(msg("document.table.complexity"))
        .append(" | ")
        .append(msg(KEY_TABLE_NOTES))
        .append(" |\n");
    sb.append("|----------|--------|------|--------|------|\n");
    final java.util.Map<String, Integer> anchorCounts = new java.util.HashMap<>();
    final java.util.List<String> anchors = new java.util.ArrayList<>();
    final java.util.List<String> anchorTexts = new java.util.ArrayList<>();
    for (int i = 0; i < methods.size(); i++) {
      final MethodInfo method = methods.get(i);
      final String anchorText = buildMethodAnchorText(method);
      final String anchor = buildAnchorId(anchorText, anchorCounts, i);
      anchors.add(anchor);
      anchorTexts.add(anchorText);
    }
    for (int i = 0; i < methods.size(); i++) {
      final MethodInfo method = methods.get(i);
      final String anchor = anchors.get(i);
      final String linkText = anchorTexts.get(i);
      sb.append("| [`").append(linkText).append("`](#").append(anchor).append(") ");
      sb.append("| ").append(DocumentUtils.translateVisibility(method.getVisibility())).append(" ");
      sb.append("| ").append(method.getLoc()).append(" ");
      sb.append("| ")
          .append(DocumentUtils.formatComplexity(method.getCyclomaticComplexity()))
          .append(" ");
      sb.append("| ").append(buildMethodFlags(method)).append(" |\n");
    }
    sb.append("\n---\n\n");
    // Detailed method documentation
    for (int i = 0; i < methods.size(); i++) {
      generateMethodDetail(sb, methods.get(i), anchorTexts.get(i), classSimpleName);
    }
  }

  private void generateMethodDetail(
      final StringBuilder sb,
      final MethodInfo method,
      final String headingText,
      final String classSimpleName) {
    sb.append("### ").append(headingText).append("\n\n");
    appendMethodOverview(sb, method);
    // Signature
    sb.append("```java\n");
    sb.append(nullSafe(method.getSignature())).append("\n");
    sb.append("```\n\n");
    appendMethodContract(sb, method, classSimpleName);
    appendMethodAnnotations(sb, method);
    appendMethodSourceCode(sb, method);
    // Metrics table
    sb.append("| ")
        .append(msg("document.table.metrics"))
        .append(" | ")
        .append(msg(KEY_TABLE_VALUE))
        .append(" |\n");
    sb.append("|------------|----|\n");
    sb.append("| ")
        .append(msg(KEY_LABEL_LINES))
        .append(" | ")
        .append(method.getLoc())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.complexity"))
        .append(" | ")
        .append(method.getCyclomaticComplexity())
        .append(" (")
        .append(DocumentUtils.getComplexityLabel(method.getCyclomaticComplexity()))
        .append(") |\n");
    sb.append("| ")
        .append(msg("document.label.nesting_depth"))
        .append(" | ")
        .append(method.getMaxNestingDepth())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.parameters"))
        .append(" | ")
        .append(method.getParameterCount())
        .append(" |\n");
    sb.append("| ")
        .append(msg("document.label.usage_count"))
        .append(" | ")
        .append(method.getUsageCount())
        .append(" |\n");
    if (method.getDynamicFeatureTotal() > 0) {
      sb.append("| ")
          .append(msg("document.label.dynamic_resolution"))
          .append(" | ")
          .append(
              msg(
                  "document.dynamic_resolution.format",
                  method.getDynamicFeatureHigh(),
                  method.getDynamicFeatureMedium(),
                  method.getDynamicFeatureLow()))
          .append(" |\n");
    }
    // Structural indicators
    appendStructureIndicators(sb, method);
    sb.append("\n");
    appendQualitySignals(sb, method);
    appendBranchSummary(sb, method);
    appendRepresentativePaths(sb, method);
    // Dependencies
    appendCalledMethods(sb, method);
    // Thrown exceptions
    appendExceptions(sb, method);
    appendTestViewpoints(sb, method);
  }

  private void appendMethodOverview(final StringBuilder sb, final MethodInfo method) {
    final List<String> parts = new ArrayList<>();
    parts.add(DocumentUtils.translateVisibility(method.getVisibility()));
    if (method.isStatic()) {
      parts.add(msg("document.summary.static"));
    }
    if (method.isPartOfCycle()) {
      parts.add(msg("document.summary.cycle"));
    }
    if (method.isUsesRemovedApis()) {
      parts.add(msg("document.summary.removed_api"));
    }
    if (method.getParameterCount() > 0) {
      parts.add(msg("document.summary.parameters", method.getParameterCount()));
    }
    if (method.getUsageCount() > 0) {
      parts.add(msg("document.summary.calls", method.getUsageCount()));
    }
    sb.append("**")
        .append(msg("document.label.summary"))
        .append("**: ")
        .append(String.join(" / ", parts))
        .append("\n\n");
  }

  private void appendMethodAnnotations(final StringBuilder sb, final MethodInfo method) {
    if (method.getAnnotations().isEmpty()) {
      return;
    }
    sb.append("**").append(msg("document.label.annotations")).append("**: ");
    for (final String annotation : method.getAnnotations()) {
      sb.append("`@").append(annotation).append("` ");
    }
    sb.append("\n\n");
  }

  private void appendMethodSourceCode(final StringBuilder sb, final MethodInfo method) {
    final String sourceCode = normalizeSourceCode(method.getSourceCode());
    if (sourceCode == null) {
      return;
    }
    final String signature = normalizeCodeBlock(method.getSignature());
    if (sourceCode.equals(signature)) {
      return;
    }
    sb.append("**").append(msg("document.md.section.source_code")).append("**:\n\n");
    appendSectionGuide(sb, "document.guide.source_code");
    sb.append(CODE_FENCE).append("java\n");
    sb.append(sourceCode).append("\n");
    sb.append(CODE_FENCE).append("\n\n");
  }

  private void appendMethodContract(
      final StringBuilder sb, final MethodInfo method, final String classSimpleName) {
    final SignatureInfo signatureInfo = parseSignatureInfo(method, classSimpleName);
    final List<String> preconditions = collectPreconditions(method);
    final List<String> postconditions = collectPostconditions(method, signatureInfo);
    final List<String> errorBoundaries = collectErrorBoundaries(method);
    final List<String> dependencies = collectDependencySummaries(method);
    sb.append("**").append(msg("document.md.section.contract")).append(SECTION_TITLE_SUFFIX);
    appendSectionGuide(sb, "document.guide.contract");
    sb.append("| ")
        .append(msg("document.table.item"))
        .append(" | ")
        .append(msg(KEY_TABLE_VALUE))
        .append(" |\n");
    sb.append("|------|------|\n");
    appendContractRow(
        sb,
        msg("document.label.inputs"),
        joinContractValues(signatureInfo.inputs(), msg("document.value.none")));
    appendContractRow(sb, msg("document.label.output"), signatureInfo.output());
    appendContractRow(
        sb,
        msg("document.label.preconditions"),
        joinContractValues(preconditions, msg("document.value.none")));
    appendContractRow(
        sb,
        msg("document.label.postconditions"),
        joinContractValues(postconditions, msg("document.value.unknown")));
    appendContractRow(
        sb,
        msg("document.label.error_boundary"),
        joinContractValues(errorBoundaries, msg("document.value.none")));
    appendContractRow(
        sb,
        msg("document.label.dependencies"),
        joinContractValues(dependencies, msg("document.value.none")));
    sb.append("\n");
  }

  private void appendContractRow(final StringBuilder sb, final String item, final String value) {
    sb.append("| ")
        .append(escapeMarkdownTableCell(item))
        .append(" | ")
        .append(escapeMarkdownTableCell(value))
        .append(" |\n");
  }

  private SignatureInfo parseSignatureInfo(final MethodInfo method, final String classSimpleName) {
    final String signature = method.getSignature();
    final SignatureInfo fromSignature =
        parseSignatureInfoFromDeclaration(signature, method, classSimpleName);
    final SignatureInfo fromSource = parseSignatureInfoFromSource(method, classSimpleName);
    if (fromSource == null) {
      return fromSignature;
    }
    List<String> mergedInputs = fromSignature.inputs();
    if (shouldUseSourceInputs(mergedInputs, fromSource.inputs())) {
      mergedInputs = fromSource.inputs();
    }
    String mergedOutput = fromSignature.output();
    if (msg("document.value.unknown").equals(mergedOutput)) {
      mergedOutput = fromSource.output();
    }
    final boolean constructor = fromSignature.constructor() || fromSource.constructor();
    return new SignatureInfo(mergedInputs, mergedOutput, constructor);
  }

  private SignatureInfo parseSignatureInfoFromSource(
      final MethodInfo method, final String classSimpleName) {
    final String declaration =
        extractMethodDeclaration(method == null ? null : method.getSourceCode());
    if (declaration == null || declaration.isBlank()) {
      return null;
    }
    return parseSignatureInfoFromDeclaration(declaration, method, classSimpleName);
  }

  private String extractMethodDeclaration(final String sourceCode) {
    final String stripped = DocumentUtils.stripCommentedRegions(sourceCode);
    if (stripped == null || stripped.isBlank()) {
      return null;
    }
    final StringBuilder declaration = new StringBuilder();
    for (final String line : stripped.split("\\R")) {
      if (line == null || line.isBlank()) {
        continue;
      }
      final String trimmed = line.trim();
      declaration.append(trimmed).append(' ');
      if (trimmed.contains("{") || trimmed.endsWith(")")) {
        break;
      }
    }
    if (declaration.isEmpty()) {
      return null;
    }
    String candidate = declaration.toString().trim();
    final int braceIndex = candidate.indexOf('{');
    if (braceIndex >= 0) {
      candidate = candidate.substring(0, braceIndex).trim();
    }
    return candidate.contains("(") ? candidate : null;
  }

  private SignatureInfo parseSignatureInfoFromDeclaration(
      final String declaration, final MethodInfo method, final String classSimpleName) {
    if (declaration == null || declaration.isBlank()) {
      return new SignatureInfo(fallbackInputs(method), msg("document.value.unknown"), false);
    }
    final String normalized = declaration.strip().replace('$', '.');
    final int openParen = normalized.indexOf('(');
    final int closeParen = normalized.lastIndexOf(')');
    if (openParen < 0 || closeParen <= openParen) {
      return new SignatureInfo(fallbackInputs(method), msg("document.value.unknown"), false);
    }
    final String beforeParen = normalized.substring(0, openParen).trim();
    final String parameterSection = normalized.substring(openParen + 1, closeParen).trim();
    final List<String> inputs = parseInputDescriptors(parameterSection, method.getParameterCount());
    final String methodName = extractDeclaredMethodName(beforeParen);
    final boolean constructor = isConstructorSignature(methodName, classSimpleName);
    final String output =
        constructor ? msg("document.value.constructor_output") : extractReturnType(beforeParen);
    return new SignatureInfo(inputs, output, constructor);
  }

  private boolean isPlaceholderInputs(final List<String> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return false;
    }
    for (final String input : inputs) {
      if (input == null || !input.matches("arg\\d+")) {
        return false;
      }
    }
    return true;
  }

  private boolean shouldUseSourceInputs(
      final List<String> fromSignature, final List<String> fromSource) {
    if (fromSource == null || fromSource.isEmpty()) {
      return false;
    }
    if (fromSignature == null || fromSignature.isEmpty() || isPlaceholderInputs(fromSignature)) {
      return true;
    }
    return !hasNamedInputs(fromSignature) && hasNamedInputs(fromSource);
  }

  private boolean hasNamedInputs(final List<String> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return false;
    }
    for (final String input : inputs) {
      if (input != null && input.matches(".*\\s+[a-z_][A-Za-z0-9_]*$")) {
        return true;
      }
    }
    return false;
  }

  private List<String> parseInputDescriptors(
      final String parameterSection, final int fallbackParameterCount) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return List.of();
    }
    final List<String> parameters = LlmDocumentTextUtils.splitTopLevelCsv(parameterSection);
    final List<String> inputs = new ArrayList<>();
    for (final String parameter : parameters) {
      final String normalized = normalizeParameterDescriptor(parameter);
      if (!normalized.isBlank()) {
        inputs.add(normalized);
      }
    }
    if (!inputs.isEmpty()) {
      return inputs;
    }
    return fallbackInputPlaceholders(fallbackParameterCount);
  }

  private List<String> fallbackInputs(final MethodInfo method) {
    return fallbackInputPlaceholders(method == null ? 0 : method.getParameterCount());
  }

  private List<String> fallbackInputPlaceholders(final int parameterCount) {
    if (parameterCount <= 0) {
      return List.of();
    }
    final List<String> placeholders = new ArrayList<>();
    for (int i = 0; i < parameterCount; i++) {
      placeholders.add("arg" + i);
    }
    return placeholders;
  }

  private String normalizeParameterDescriptor(final String parameter) {
    if (parameter == null || parameter.isBlank()) {
      return "";
    }
    String normalized = parameter.strip().replace('$', '.');
    normalized = normalized.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
    normalized = normalized.replaceAll("\\b(final|volatile|transient)\\b\\s*", "");
    normalized = simplifyQualifiedNames(normalized);
    return normalized.replaceAll("\\s+", " ").trim();
  }

  private String extractDeclaredMethodName(final String beforeParen) {
    if (beforeParen == null || beforeParen.isBlank()) {
      return "";
    }
    final String compact = beforeParen.strip();
    final int lastSpace = compact.lastIndexOf(' ');
    if (lastSpace >= 0 && lastSpace + 1 < compact.length()) {
      return compact.substring(lastSpace + 1).trim();
    }
    return compact;
  }

  private boolean isConstructorSignature(final String methodName, final String classSimpleName) {
    if (methodName == null
        || methodName.isBlank()
        || classSimpleName == null
        || classSimpleName.isBlank()) {
      return false;
    }
    String simpleName = methodName;
    final int dotIndex = simpleName.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < simpleName.length()) {
      simpleName = simpleName.substring(dotIndex + 1);
    }
    return classSimpleName.equals(simpleName);
  }

  private String extractReturnType(final String beforeParen) {
    if (beforeParen == null || beforeParen.isBlank()) {
      return msg("document.value.unknown");
    }
    final String methodName = extractDeclaredMethodName(beforeParen);
    final int methodStart = beforeParen.lastIndexOf(methodName);
    if (methodStart <= 0) {
      return msg("document.value.unknown");
    }
    String left = beforeParen.substring(0, methodStart).trim();
    left = stripMethodModifiers(left);
    left = left.replaceAll("^<[^>]+>\\s*", "");
    left = simplifyQualifiedNames(left).trim();
    return left.isBlank() ? msg("document.value.unknown") : left;
  }

  private String stripMethodModifiers(final String value) {
    String current = value == null ? "" : value;
    while (true) {
      final String updated =
          current.replaceFirst(
              "^(public|private|protected|static|final|abstract|default|synchronized|native|strictfp)\\s+",
              "");
      if (updated.equals(current)) {
        return current;
      }
      current = updated;
    }
  }

  private String simplifyQualifiedNames(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String current = value;
    while (true) {
      final String updated =
          current.replaceAll(
              "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)+([A-Za-z_][A-Za-z0-9_]*)", "$1");
      if (updated.equals(current)) {
        return updated;
      }
      current = updated;
    }
  }

  private List<String> collectPreconditions(final MethodInfo method) {
    final java.util.Set<String> values = new java.util.LinkedHashSet<>();
    final BranchSummary summary = method.getBranchSummary();
    if (summary != null && hasItems(summary.getGuards())) {
      for (final GuardSummary guard : summary.getGuards()) {
        if (guard == null || guard.getCondition() == null || guard.getCondition().isBlank()) {
          continue;
        }
        final GuardType type = guard.getType();
        if ((type == GuardType.FAIL_GUARD
                || type == GuardType.MESSAGE_GUARD
                || type == GuardType.LEGACY)
            && isTopLevelGuard(guard)) {
          values.add(guard.getCondition().strip());
        }
      }
    }
    return new ArrayList<>(values);
  }

  private boolean isTopLevelGuard(final GuardSummary guard) {
    if (guard == null || guard.getLocation() == null || guard.getLocation().isBlank()) {
      return true;
    }
    final String location = guard.getLocation().strip();
    final int separator = location.indexOf(':');
    if (separator < 0 || separator + 1 >= location.length()) {
      return true;
    }
    try {
      final int column = Integer.parseInt(location.substring(separator + 1).trim());
      return column <= 5;
    } catch (NumberFormatException e) {
      return true;
    }
  }

  private List<String> collectPostconditions(
      final MethodInfo method, final SignatureInfo signatureInfo) {
    final java.util.Set<String> values = new java.util.LinkedHashSet<>();
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      final String pathType = resolvePathType(path);
      if (!"success".equalsIgnoreCase(pathType)) {
        continue;
      }
      final String description = nullSafe(path.getDescription());
      final String expected = nullSafe(path.getExpectedOutcomeHint());
      if (!msg("document.value.na").equals(description)
          || !msg("document.value.na").equals(expected)) {
        values.add(msg("document.contract.path_result", description, expected));
      }
    }
    if (values.isEmpty()) {
      if (signatureInfo.constructor()) {
        values.add(msg("document.contract.constructor_postcondition"));
      } else if (!"void".equalsIgnoreCase(signatureInfo.output())
          && !msg("document.value.unknown").equals(signatureInfo.output())) {
        values.add(msg("document.contract.return_postcondition", signatureInfo.output()));
      }
    }
    return new ArrayList<>(values);
  }

  private List<String> collectErrorBoundaries(final MethodInfo method) {
    final java.util.Set<String> values = new java.util.LinkedHashSet<>();
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      final String type = resolvePathType(path).toLowerCase(Locale.ROOT);
      if (!type.contains("early") && !type.contains("boundary") && !type.contains("error")) {
        continue;
      }
      final String condition = formatRequiredConditions(path.getRequiredConditions());
      final String expected = nullSafe(path.getExpectedOutcomeHint());
      values.add(msg("document.contract.error_boundary_case", condition, expected));
    }
    for (final String exception : deduplicateExceptions(method.getThrownExceptions())) {
      values.add(msg("document.contract.exception_case", exception));
    }
    return new ArrayList<>(values);
  }

  private List<String> collectDependencySummaries(final MethodInfo method) {
    final List<String> calledMethods = deduplicateCalledMethods(method.getCalledMethods());
    final List<String> prioritized = new ArrayList<>();
    final List<String> fallback = new ArrayList<>();
    for (final String calledMethod : calledMethods) {
      final CalledMethodView view = parseCalledMethod(calledMethod);
      if (isExceptionConstructor(view)) {
        continue;
      }
      final String token = view.owner() + "#" + view.method();
      if (isCoreLibraryOwner(view.owner())) {
        fallback.add(token);
      } else {
        prioritized.add(token);
      }
    }
    final List<String> dependencies = new ArrayList<>();
    for (final String token : prioritized) {
      if (dependencies.size() >= CONDITION_PREVIEW_LIMIT) {
        break;
      }
      dependencies.add(token);
    }
    for (final String token : fallback) {
      if (dependencies.size() >= CONDITION_PREVIEW_LIMIT) {
        break;
      }
      dependencies.add(token);
    }
    return dependencies;
  }

  private boolean isExceptionConstructor(final CalledMethodView view) {
    if (view == null) {
      return false;
    }
    return view.owner().equals(view.method()) && view.owner().endsWith("Exception");
  }

  private boolean isCoreLibraryOwner(final String owner) {
    return owner != null && CORE_LIBRARY_OWNERS.contains(owner);
  }

  private String joinContractValues(final List<String> values, final String fallback) {
    if (values == null || values.isEmpty()) {
      return fallback;
    }
    final List<String> normalized = new ArrayList<>();
    for (final String value : values) {
      if (value != null && !value.isBlank()) {
        normalized.add(value.strip());
      }
    }
    if (normalized.isEmpty()) {
      return fallback;
    }
    final int displayCount = Math.min(normalized.size(), CONDITION_PREVIEW_LIMIT);
    final List<String> display = new ArrayList<>(normalized.subList(0, displayCount));
    if (normalized.size() > CONDITION_PREVIEW_LIMIT) {
      display.add(msg("document.list.more.inline", normalized.size() - CONDITION_PREVIEW_LIMIT));
    }
    return String.join(" / ", display);
  }

  private void appendStructureIndicators(final StringBuilder sb, final MethodInfo method) {
    if (method.hasLoops() || method.hasConditionals()) {
      sb.append("| ").append(msg("document.label.structure")).append(" | ");
      if (method.hasLoops()) {
        sb.append(msg("document.structure.loop")).append(" ");
      }
      if (method.hasConditionals()) {
        sb.append(msg("document.structure.conditional")).append(" ");
      }
      sb.append("|\n");
    }
  }

  private void appendQualitySignals(final StringBuilder sb, final MethodInfo method) {
    final List<String> signals = new ArrayList<>();
    if (method.isDeadCode()) {
      signals.add(msg("document.signal.dead_code"));
    }
    if (method.isDuplicate()) {
      final String group = nullSafe(method.getDuplicateGroup());
      signals.add(msg("document.signal.duplicate", group));
    }
    if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
      signals.add(msg("document.signal.high_complexity"));
    }
    if (method.isUsesRemovedApis()) {
      signals.add(msg("document.signal.removed_api"));
    }
    if (method.isPartOfCycle()) {
      signals.add(msg("document.signal.cycle"));
    }
    if (method.isBrittle()) {
      signals.add(
          msg("document.signal.brittle", formatBrittlenessSignals(method.getBrittlenessSignals())));
    }
    if (!signals.isEmpty()) {
      sb.append("**")
          .append(msg("document.md.section.quality_signals"))
          .append(SECTION_TITLE_SUFFIX);
      appendSectionGuide(sb, "document.guide.quality_signals");
      for (final String signal : signals) {
        sb.append("- ").append(signal).append("\n");
      }
      sb.append("\n");
    }
    if (method.isDeadCode()) {
      sb.append(WARNING_PREFIX).append(msg("document.warning.dead_code")).append("\n\n");
    }
    if (method.isDuplicate()) {
      sb.append(WARNING_PREFIX)
          .append(msg("document.warning.duplicate", nullSafe(method.getDuplicateGroup())))
          .append("\n\n");
    }
    if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
      sb.append("> 🔴 ").append(msg("document.warning.high_complexity")).append("\n\n");
    }
  }

  private String formatBrittlenessSignals(final List<BrittlenessSignal> signals) {
    if (signals == null || signals.isEmpty()) {
      return msg(KEY_VALUE_EMPTY);
    }
    final List<String> tokens = new ArrayList<>();
    for (final BrittlenessSignal signal : signals) {
      if (signal != null) {
        tokens.add(signal.token());
      }
    }
    return tokens.isEmpty() ? msg(KEY_VALUE_EMPTY) : String.join(", ", tokens);
  }

  private void appendBranchSummary(final StringBuilder sb, final MethodInfo method) {
    final BranchSummary summary = method.getBranchSummary();
    if (summary == null || isBranchSummaryEmpty(summary)) {
      return;
    }
    sb.append("**").append(msg("document.md.section.branches")).append(SECTION_TITLE_SUFFIX);
    appendSectionGuide(sb, "document.guide.branches");
    appendBranchSummaryTable(sb, summary);
    sb.append("\n");
  }

  private boolean isBranchSummaryEmpty(final BranchSummary summary) {
    return !hasItems(summary.getGuards())
        && !hasItems(summary.getSwitches())
        && !hasItems(summary.getPredicates());
  }

  private boolean hasItems(final List<?> items) {
    return items != null && !items.isEmpty();
  }

  private void appendBranchSummaryTable(final StringBuilder sb, final BranchSummary summary) {
    final List<BranchRow> rows = buildBranchRows(summary);
    sb.append("| ")
        .append(msg(KEY_TABLE_TYPE))
        .append(" | ")
        .append(msg("document.table.condition"))
        .append(" | ")
        .append(msg(KEY_TABLE_NOTES))
        .append(" |\n");
    sb.append("|------|------|------|\n");
    final int displayCount = Math.min(rows.size(), LIST_PREVIEW_LIMIT);
    for (int i = 0; i < displayCount; i++) {
      final BranchRow row = rows.get(i);
      sb.append("| ")
          .append(escapeMarkdownTableCell(row.type()))
          .append(" | ")
          .append(escapeMarkdownTableCell(row.condition()))
          .append(" | ")
          .append(escapeMarkdownTableCell(row.notes()))
          .append(" |\n");
    }
    if (rows.size() > LIST_PREVIEW_LIMIT) {
      sb.append("| ... | ... | ")
          .append(msg(KEY_LIST_MORE_TABLE, rows.size() - LIST_PREVIEW_LIMIT))
          .append(" |\n");
    }
  }

  private List<BranchRow> buildBranchRows(final BranchSummary summary) {
    final List<BranchRow> rows = new ArrayList<>();
    if (summary == null) {
      return rows;
    }
    if (hasItems(summary.getGuards())) {
      for (final GuardSummary guard : summary.getGuards()) {
        final String type = formatGuardType(guard);
        final String condition = guard == null ? nullSafe(null) : nullSafe(guard.getCondition());
        final String notes = formatGuardNotes(guard);
        rows.add(new BranchRow(type, condition, notes));
      }
    }
    if (hasItems(summary.getSwitches())) {
      for (final String switchExpr : summary.getSwitches()) {
        rows.add(
            new BranchRow(
                msg("document.label.switch"), nullSafe(switchExpr), msg(KEY_VALUE_EMPTY)));
      }
    }
    if (hasItems(summary.getPredicates())) {
      for (final String predicate : summary.getPredicates()) {
        rows.add(
            new BranchRow(
                msg("document.label.predicate"), nullSafe(predicate), msg(KEY_VALUE_EMPTY)));
      }
    }
    return rows;
  }

  private String formatGuardType(final GuardSummary guard) {
    if (guard == null || guard.getType() == null || guard.getType() == GuardType.LEGACY) {
      return msg("document.label.guard");
    }
    return guard.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private String formatGuardNotes(final GuardSummary guard) {
    if (guard == null) {
      return msg(KEY_VALUE_EMPTY);
    }
    final List<String> notes = new ArrayList<>();
    if (guard.getMessageLiteral() != null && !guard.getMessageLiteral().isBlank()) {
      notes.add(
          msg("document.label.message_short") + ": \"" + guard.getMessageLiteral().strip() + "\"");
    }
    if (hasItems(guard.getEffects())) {
      notes.add(String.join(", ", guard.getEffects()));
    }
    if (guard.getLocation() != null && !guard.getLocation().isBlank()) {
      notes.add(guard.getLocation().strip());
    }
    return notes.isEmpty() ? msg(KEY_VALUE_EMPTY) : String.join(" / ", notes);
  }

  private void appendRepresentativePaths(final StringBuilder sb, final MethodInfo method) {
    final List<RepresentativePath> paths = method.getRepresentativePaths();
    if (paths == null || paths.isEmpty()) {
      return;
    }
    sb.append("**").append(msg("document.md.section.representative_paths")).append("**:\n\n");
    appendSectionGuide(sb, "document.guide.representative_paths");
    sb.append("| ")
        .append(msg("document.table.id"))
        .append(" | ")
        .append(msg(KEY_TABLE_TYPE))
        .append(" | ")
        .append(msg("document.table.condition"))
        .append(" | ")
        .append(msg("document.table.description"))
        .append(" | ")
        .append(msg(KEY_TABLE_NOTES))
        .append(" |\n");
    sb.append("|----|------|------|------|------|\n");
    int count = 0;
    for (final RepresentativePath path : paths) {
      if (count >= LIST_PREVIEW_LIMIT) {
        sb.append("| ... | ... | ... | ... | ")
            .append(msg(KEY_LIST_MORE_TABLE, paths.size() - LIST_PREVIEW_LIMIT))
            .append(" |\n");
        break;
      }
      final String conditions =
          formatRequiredConditions(path == null ? null : path.getRequiredConditions());
      sb.append("| ")
          .append(escapeMarkdownTableCell(nullSafe(path == null ? null : path.getId())))
          .append(" | ")
          .append(escapeMarkdownTableCell(resolvePathType(path)))
          .append(" | ")
          .append(escapeMarkdownTableCell(conditions))
          .append(" | ")
          .append(escapeMarkdownTableCell(nullSafe(path == null ? null : path.getDescription())))
          .append(" | ")
          .append(
              escapeMarkdownTableCell(
                  nullSafe(path == null ? null : path.getExpectedOutcomeHint())))
          .append(" |\n");
      count++;
    }
    sb.append("\n");
  }

  private void appendCalledMethods(final StringBuilder sb, final MethodInfo method) {
    final List<String> calledMethods = deduplicateCalledMethods(method.getCalledMethods());
    if (!calledMethods.isEmpty()) {
      sb.append("**")
          .append(msg("document.md.section.called_methods"))
          .append(SECTION_TITLE_SUFFIX);
      appendSectionGuide(sb, "document.guide.called_methods");
      sb.append("| # | ")
          .append(msg("document.table.owner"))
          .append(" | ")
          .append(msg("document.table.method"))
          .append(" | ")
          .append(msg("document.table.signature"))
          .append(" |\n");
      sb.append("|---|------|------|-----------|\n");
      final int displayCount = Math.min(calledMethods.size(), CALLED_METHODS_PREVIEW_LIMIT);
      for (int i = 0; i < displayCount; i++) {
        final CalledMethodView view = parseCalledMethod(calledMethods.get(i));
        sb.append("| ")
            .append(i + 1)
            .append(" | ")
            .append(escapeMarkdownTableCell(view.owner()))
            .append(" | ")
            .append(escapeMarkdownTableCell(view.method()))
            .append(" | ")
            .append(escapeMarkdownTableCell(view.signature()))
            .append(" |\n");
      }
      if (calledMethods.size() > CALLED_METHODS_PREVIEW_LIMIT) {
        sb.append("| ... | ... | ... | ")
            .append(msg(KEY_LIST_MORE_TABLE, calledMethods.size() - CALLED_METHODS_PREVIEW_LIMIT))
            .append(" |\n");
      }
      sb.append("\n");
    }
  }

  private List<String> deduplicateCalledMethods(final List<String> calledMethods) {
    if (calledMethods == null || calledMethods.isEmpty()) {
      return List.of();
    }
    final Map<String, String> deduplicated = new LinkedHashMap<>();
    for (final String called : calledMethods) {
      final CalledMethodView view = parseCalledMethod(called);
      final String key =
          (view.owner() + "#" + view.method() + "|" + view.signature()).toLowerCase(Locale.ROOT);
      deduplicated.putIfAbsent(key, called);
    }
    return new ArrayList<>(deduplicated.values());
  }

  private CalledMethodView parseCalledMethod(final String called) {
    final String normalized = normalizeCallReference(called);
    final int hashIndex = normalized.indexOf('#');
    if (hashIndex < 0) {
      return new CalledMethodView(msg(KEY_VALUE_UNKNOWN), normalized, normalized);
    }
    final String owner = simplifyTypeName(normalized.substring(0, hashIndex));
    final String rawSignature = normalized.substring(hashIndex + 1).trim();
    final String methodName = extractMethodName(rawSignature);
    final String signature = simplifyCallSignature(rawSignature);
    return new CalledMethodView(owner, methodName, signature);
  }

  private String normalizeCallReference(final String called) {
    if (called == null || called.isBlank()) {
      return msg(KEY_VALUE_UNKNOWN);
    }
    return called.strip().replace('$', '.');
  }

  private String extractMethodName(final String rawSignature) {
    final String methodName = LlmDocumentTextUtils.extractMethodName(rawSignature);
    return methodName.isBlank() ? msg(KEY_VALUE_UNKNOWN) : methodName;
  }

  private String simplifyCallSignature(final String rawSignature) {
    if (rawSignature == null || rawSignature.isBlank()) {
      return msg(KEY_VALUE_UNKNOWN);
    }
    final int parenIndex = rawSignature.indexOf('(');
    final int closeIndex = rawSignature.lastIndexOf(')');
    if (parenIndex < 0 || closeIndex <= parenIndex) {
      return rawSignature;
    }
    final String methodName = extractMethodName(rawSignature);
    final String argsPart = rawSignature.substring(parenIndex + 1, closeIndex).trim();
    final List<String> args = LlmDocumentTextUtils.splitTopLevelCsv(argsPart);
    final List<String> simplified = new ArrayList<>();
    final int max = Math.min(args.size(), CALL_PARAMETER_PREVIEW_LIMIT);
    for (int i = 0; i < max; i++) {
      simplified.add(simplifyTypeName(args.get(i)));
    }
    if (args.size() > CALL_PARAMETER_PREVIEW_LIMIT) {
      simplified.add("...");
    }
    return methodName + "(" + String.join(", ", simplified) + ")";
  }

  private String simplifyTypeName(final String value) {
    if (value == null || value.isBlank()) {
      return msg(KEY_VALUE_UNKNOWN);
    }
    String normalized = value.strip().replace('$', '.');
    normalized = stripGenericTypes(normalized);
    if (normalized.contains(" ")) {
      normalized = normalized.substring(normalized.lastIndexOf(' ') + 1);
    }
    final int dot = normalized.lastIndexOf('.');
    if (dot >= 0 && dot + 1 < normalized.length()) {
      normalized = normalized.substring(dot + 1);
    }
    return normalized.isBlank() ? msg(KEY_VALUE_UNKNOWN) : normalized;
  }

  private String stripGenericTypes(final String value) {
    String current = value;
    while (current.contains("<") && current.contains(">")) {
      final String updated = current.replaceAll("<[^<>]*>", "");
      if (updated.equals(current)) {
        break;
      }
      current = updated;
    }
    return current;
  }

  private String formatRequiredConditions(final List<String> requiredConditions) {
    if (!hasItems(requiredConditions)) {
      return msg(KEY_VALUE_EMPTY);
    }
    final List<String> conditions = new ArrayList<>();
    final int displayCount = Math.min(requiredConditions.size(), CONDITION_PREVIEW_LIMIT);
    for (int i = 0; i < displayCount; i++) {
      conditions.add(nullSafe(requiredConditions.get(i)));
    }
    if (requiredConditions.size() > CONDITION_PREVIEW_LIMIT) {
      conditions.add(
          msg("document.list.more.inline", requiredConditions.size() - CONDITION_PREVIEW_LIMIT));
    }
    return String.join(" / ", conditions);
  }

  private String resolvePathType(final RepresentativePath path) {
    if (path == null) {
      return msg(KEY_VALUE_EMPTY);
    }
    final String expected = path.getExpectedOutcomeHint();
    if (expected != null && !expected.isBlank()) {
      return expected.strip();
    }
    final String description = path.getDescription();
    if (description == null || description.isBlank()) {
      return msg(KEY_VALUE_EMPTY);
    }
    final String lower = description.toLowerCase(Locale.ROOT);
    if (lower.contains("boundary")) {
      return "boundary";
    }
    if (lower.contains("early return")) {
      return "early-return";
    }
    if (lower.contains("success")) {
      return "success";
    }
    return msg(KEY_VALUE_EMPTY);
  }

  private void appendExceptions(final StringBuilder sb, final MethodInfo method) {
    final List<String> exceptions = deduplicateExceptions(method.getThrownExceptions());
    if (!exceptions.isEmpty()) {
      sb.append("**").append(msg("document.md.section.exceptions")).append(SECTION_TITLE_SUFFIX);
      appendSectionGuide(sb, "document.guide.exceptions");
      for (final String exception : exceptions) {
        sb.append("- `").append(exception).append("`\n");
      }
      sb.append("\n");
    }
  }

  private List<String> deduplicateExceptions(final List<String> thrownExceptions) {
    if (thrownExceptions == null || thrownExceptions.isEmpty()) {
      return List.of();
    }
    final Map<String, String> deduplicated = new LinkedHashMap<>();
    for (final String thrown : thrownExceptions) {
      if (thrown == null || thrown.isBlank()) {
        continue;
      }
      final String simplified = simplifyTypeName(thrown);
      final String key = simplified.toLowerCase(Locale.ROOT);
      deduplicated.putIfAbsent(key, simplified);
    }
    return new ArrayList<>(deduplicated.values());
  }

  private void appendTestViewpoints(final StringBuilder sb, final MethodInfo method) {
    final List<String> viewpoints = collectTestViewpoints(method);
    if (viewpoints.isEmpty()) {
      return;
    }
    sb.append("**").append(msg("document.md.section.test_viewpoints")).append(SECTION_TITLE_SUFFIX);
    appendSectionGuide(sb, "document.guide.test_viewpoints");
    for (final String viewpoint : viewpoints) {
      sb.append("- ").append(viewpoint).append("\n");
    }
    sb.append("\n");
  }

  private List<String> collectTestViewpoints(final MethodInfo method) {
    final List<String> viewpoints = new ArrayList<>();
    if (method == null) {
      return viewpoints;
    }
    int pathCount = 0;
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      if (pathCount >= LIST_PREVIEW_LIMIT) {
        viewpoints.add(
            msg(
                "document.list.more.inline",
                method.getRepresentativePaths().size() - LIST_PREVIEW_LIMIT));
        break;
      }
      final String condition = formatRequiredConditions(path.getRequiredConditions());
      final String expected = nullSafe(path.getExpectedOutcomeHint());
      final String description = nullSafe(path.getDescription());
      viewpoints.add(
          msg("document.contract.test_viewpoint_path", description, condition, expected));
      pathCount++;
    }
    if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
      viewpoints.add(msg("document.contract.test_viewpoint_high_complexity"));
    }
    if (method.isDeadCode()) {
      viewpoints.add(msg("document.contract.test_viewpoint_dead_code"));
    }
    if (method.isDuplicate()) {
      viewpoints.add(msg("document.contract.test_viewpoint_duplicate"));
    }
    if (!method.getThrownExceptions().isEmpty()) {
      viewpoints.add(msg("document.contract.test_viewpoint_exceptions"));
    }
    return viewpoints;
  }

  private String buildMethodFlags(final MethodInfo method) {
    final List<String> flags = new ArrayList<>();
    if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
      flags.add(msg("document.flag.high_complexity"));
    }
    if (method.isDeadCode()) {
      flags.add(msg("document.flag.dead_code"));
    }
    if (method.isDuplicate()) {
      flags.add(msg("document.flag.duplicate"));
    }
    if (method.isUsesRemovedApis()) {
      flags.add(msg("document.flag.removed_api"));
    }
    if (method.isPartOfCycle()) {
      flags.add(msg("document.flag.cycle"));
    }
    if (method.isBrittle()) {
      flags.add(msg("document.flag.brittle"));
    }
    return flags.isEmpty() ? msg(KEY_VALUE_EMPTY) : String.join(" / ", flags);
  }

  private String buildMethodAnchorText(final MethodInfo method) {
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return method.getName() != null ? method.getName() : msg(KEY_VALUE_UNKNOWN);
    }
    final int parenIndex = signature.indexOf('(');
    if (parenIndex <= 0) {
      return method.getName() != null ? method.getName() : msg(KEY_VALUE_UNKNOWN);
    }
    int nameStart = parenIndex - 1;
    while (nameStart >= 0 && Character.isJavaIdentifierPart(signature.charAt(nameStart))) {
      nameStart--;
    }
    final String name = signature.substring(nameStart + 1, parenIndex);
    final int closeIndex = signature.indexOf(')', parenIndex);
    final String params =
        closeIndex > parenIndex ? signature.substring(parenIndex + 1, closeIndex).trim() : "";
    return name + "(" + params + ")";
  }

  private String buildAnchorId(
      final String anchorText, final java.util.Map<String, Integer> counts, final int index) {
    final String base = anchorText == null ? "" : anchorText.toLowerCase(java.util.Locale.ROOT);
    String slug = base.replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("^-+", "");
    slug = slug.replaceAll("-+$", "");
    if (slug.isEmpty()) {
      slug = "method-" + index;
    }
    final int current = counts.getOrDefault(slug, 0);
    final int count = current + 1;
    counts.put(slug, count);
    return count == 1 ? slug : slug + "-" + count;
  }

  private String nullSafe(final String value) {
    return value != null && !value.isBlank() ? value : msg("document.value.na");
  }

  private void appendSectionGuide(final StringBuilder sb, final String guideKey) {
    sb.append("**")
        .append(msg(GUIDE_PREFIX_KEY))
        .append("**: ")
        .append(msg(guideKey))
        .append("\n\n");
  }

  private String escapeMarkdownTableCell(final String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("|", "\\|");
  }

  private String normalizeCodeBlock(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip().replace(CODE_FENCE, "``\\`");
  }

  private String normalizeSourceCode(final String value) {
    return normalizeCodeBlock(DocumentUtils.stripCommentedRegions(value));
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private static final class MethodStats {

    private final int highComplexityCount;

    private final int deadCodeCount;

    private final int duplicateCount;

    private final int removedApiCount;

    private final int cycleCount;

    private final int brittleCount;

    private MethodStats(
        final int highComplexityCount,
        final int deadCodeCount,
        final int duplicateCount,
        final int removedApiCount,
        final int cycleCount,
        final int brittleCount) {
      this.highComplexityCount = highComplexityCount;
      this.deadCodeCount = deadCodeCount;
      this.duplicateCount = duplicateCount;
      this.removedApiCount = removedApiCount;
      this.cycleCount = cycleCount;
      this.brittleCount = brittleCount;
    }

    private static MethodStats from(final List<MethodInfo> methods) {
      final MethodStatsAccumulator accumulator = new MethodStatsAccumulator();
      if (methods == null || methods.isEmpty()) {
        return accumulator.toStats();
      }
      for (final MethodInfo method : methods) {
        accumulator.accept(method);
      }
      return accumulator.toStats();
    }

    private static final class MethodStatsAccumulator {

      private int highComplexity;

      private int deadCode;

      private int duplicate;

      private int removedApi;

      private int cycle;

      private int brittle;

      private void accept(final MethodInfo method) {
        if (method == null) {
          return;
        }
        if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
          highComplexity++;
        }
        if (method.isDeadCode()) {
          deadCode++;
        }
        if (method.isDuplicate()) {
          duplicate++;
        }
        if (method.isUsesRemovedApis()) {
          removedApi++;
        }
        if (method.isPartOfCycle()) {
          cycle++;
        }
        if (method.isBrittle()) {
          brittle++;
        }
      }

      private MethodStats toStats() {
        return new MethodStats(highComplexity, deadCode, duplicate, removedApi, cycle, brittle);
      }
    }
  }

  private record BranchRow(String type, String condition, String notes) {}

  private record CalledMethodView(String owner, String method, String signature) {}

  private record SignatureInfo(List<String> inputs, String output, boolean constructor) {}
}
