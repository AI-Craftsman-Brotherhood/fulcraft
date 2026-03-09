package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generates dependency and call graph diagrams from analysis results.
 *
 * <p>Supports both Mermaid and PlantUML diagram formats. Diagrams show class dependencies,
 * inheritance relationships, and method call graphs.
 */
public class DiagramDocumentGenerator implements DocumentGenerator {

  private static final String FORMAT = "diagram";

  // Diagrams embedded in Markdown
  private static final String EXTENSION = ".md";

  private static final String MERMAID_FENCE_START = "```mermaid\n";

  private static final String MERMAID_FENCE_END = "```\n";

  private static final String PLANTUML_FENCE_START = "```plantuml\n@startuml\n";

  private static final String PLANTUML_FENCE_END = "@enduml\n```\n";

  private static final String MERMAID_CLASS_PREFIX = "    class ";

  private static final String MERMAID_NODE_SUFFIX = "\"]\n";

  private static final String UNKNOWN_VALUE = "unknown";

  /** Supported diagram formats. */
  public enum DiagramFormat {
    MERMAID,
    PLANTUML
  }

  private final DiagramFormat diagramFormat;

  public DiagramDocumentGenerator() {
    this(DiagramFormat.MERMAID);
  }

  public DiagramDocumentGenerator(final DiagramFormat format) {
    this.diagramFormat = format;
  }

  /**
   * Creates a generator with format from config.
   *
   * @param config the configuration
   * @return the diagram generator
   */
  public static DiagramDocumentGenerator fromConfig(final Config config) {
    if (config.getDocs() != null
        && "plantuml".equalsIgnoreCase(config.getDocs().getDiagramFormat())) {
      return new DiagramDocumentGenerator(DiagramFormat.PLANTUML);
    }
    return new DiagramDocumentGenerator(DiagramFormat.MERMAID);
  }

  @Override
  public int generate(final AnalysisResult result, final Path outputDir, final Config config)
      throws IOException {
    Files.createDirectories(outputDir);
    int count = 0;
    // Generate class dependency diagram
    final String classDiagram = generateClassDependencyDiagram(result);
    final Path classDiagramFile = outputDir.resolve("class_dependencies" + EXTENSION);
    Files.writeString(classDiagramFile, classDiagram, StandardCharsets.UTF_8);
    count++;
    // Generate inheritance diagram
    final String inheritanceDiagram = generateInheritanceDiagram(result);
    final Path inheritanceFile = outputDir.resolve("inheritance_hierarchy" + EXTENSION);
    Files.writeString(inheritanceFile, inheritanceDiagram, StandardCharsets.UTF_8);
    count++;
    // Generate call graphs for each class
    for (final ClassInfo classInfo : result.getClasses()) {
      if (classInfo.getMethods().isEmpty()) {
        continue;
      }
      final String callGraph = generateMethodCallGraph(classInfo);
      final String fileName =
          DocumentUtils.generateFileName(classInfo.getFqn(), "_calls" + EXTENSION);
      final Path outputFile = outputDir.resolve(fileName);
      Files.writeString(outputFile, callGraph, StandardCharsets.UTF_8);
      count++;
    }
    Logger.info(MessageSource.getMessage("report.docs.diagram.generated", count));
    return count;
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
   * Generates a class dependency diagram showing relationships between classes.
   *
   * @param result the analysis result
   * @return the diagram content
   */
  public String generateClassDependencyDiagram(final AnalysisResult result) {
    final StringBuilder sb = new StringBuilder();
    sb.append("# ").append(msg("document.diagram.class_dependencies.title")).append("\n\n");
    sb.append(msg("document.diagram.class_dependencies.description")).append("\n\n");
    if (diagramFormat == DiagramFormat.MERMAID) {
      sb.append(MERMAID_FENCE_START).append("graph TD\n");
      appendMermaidClassDependencies(sb, result);
      sb.append(MERMAID_FENCE_END);
    } else {
      sb.append(PLANTUML_FENCE_START);
      appendPlantUmlClassDependencies(sb, result);
      sb.append(PLANTUML_FENCE_END);
    }
    return sb.toString();
  }

  /**
   * Generates an inheritance hierarchy diagram.
   *
   * @param result the analysis result
   * @return the diagram content
   */
  public String generateInheritanceDiagram(final AnalysisResult result) {
    final StringBuilder sb = new StringBuilder();
    sb.append("# ").append(msg("document.diagram.inheritance.title")).append("\n\n");
    sb.append(msg("document.diagram.inheritance.description")).append("\n\n");
    if (diagramFormat == DiagramFormat.MERMAID) {
      sb.append(MERMAID_FENCE_START).append("classDiagram\n");
      appendMermaidInheritance(sb, result);
      sb.append(MERMAID_FENCE_END);
    } else {
      sb.append(PLANTUML_FENCE_START);
      appendPlantUmlInheritance(sb, result);
      sb.append(PLANTUML_FENCE_END);
    }
    return sb.toString();
  }

  /**
   * Generates a method call graph for a single class.
   *
   * @param classInfo the class information
   * @return the diagram content
   */
  public String generateMethodCallGraph(final ClassInfo classInfo) {
    final StringBuilder sb = new StringBuilder();
    final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    sb.append("# ").append(msg("document.diagram.method_calls.title", simpleName)).append("\n\n");
    sb.append(msg("document.diagram.method_calls.description", simpleName)).append("\n\n");
    if (diagramFormat == DiagramFormat.MERMAID) {
      sb.append(MERMAID_FENCE_START).append("graph LR\n");
      appendMermaidMethodCalls(sb, classInfo);
      sb.append(MERMAID_FENCE_END);
    } else {
      sb.append(PLANTUML_FENCE_START);
      appendPlantUmlMethodCalls(sb, classInfo);
      sb.append(PLANTUML_FENCE_END);
    }
    return sb.toString();
  }

  // Mermaid diagram generation methods
  private void appendMermaidClassDependencies(final StringBuilder sb, final AnalysisResult result) {
    final Map<String, String> classIds = appendMermaidClassNodes(sb, result);
    sb.append("\n");
    appendMermaidClassEdges(sb, result, classIds);
    appendMermaidClassStyle(sb);
  }

  private Map<String, String> appendMermaidClassNodes(
      final StringBuilder sb, final AnalysisResult result) {
    final Map<String, String> classIds = new LinkedHashMap<>();
    int idCounter = 0;
    // Create IDs for all classes
    for (final ClassInfo classInfo : result.getClasses()) {
      final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
      final String id = "C" + (idCounter++);
      classIds.put(classInfo.getFqn(), id);
      sb.append("    ").append(id).append("[\"").append(simpleName).append(MERMAID_NODE_SUFFIX);
    }
    return classIds;
  }

  private void appendMermaidClassEdges(
      final StringBuilder sb, final AnalysisResult result, final Map<String, String> classIds) {
    // Add dependency edges from method calls
    final Set<String> edges = new HashSet<>();
    for (final ClassInfo classInfo : result.getClasses()) {
      appendMermaidEdgesForClass(sb, classIds, edges, classInfo);
    }
  }

  private void appendMermaidEdgesForClass(
      final StringBuilder sb,
      final Map<String, String> classIds,
      final Set<String> edges,
      final ClassInfo classInfo) {
    final String sourceId = classIds.get(classInfo.getFqn());
    for (final MethodInfo method : classInfo.getMethods()) {
      appendMermaidEdgesForMethod(sb, classIds, edges, sourceId, method);
    }
  }

  private void appendMermaidEdgesForMethod(
      final StringBuilder sb,
      final Map<String, String> classIds,
      final Set<String> edges,
      final String sourceId,
      final MethodInfo method) {
    for (final String calledMethod : method.getCalledMethods()) {
      final String edge = buildMermaidDependencyEdge(sourceId, calledMethod, classIds);
      if (edge != null && edges.add(edge)) {
        sb.append("    ").append(edge).append("\n");
      }
    }
  }

  private String buildMermaidDependencyEdge(
      final String sourceId, final String calledMethod, final Map<String, String> classIds) {
    final String targetClass = extractClassName(calledMethod);
    if (targetClass == null) {
      return null;
    }
    final String targetId = classIds.get(targetClass);
    if (targetId == null || sourceId.equals(targetId)) {
      return null;
    }
    return sourceId + "-->" + targetId;
  }

  private void appendMermaidClassStyle(final StringBuilder sb) {
    sb.append("\n    classDef default fill:#1a1a2e,stroke:#e94560,color:#fff\n");
  }

  private void appendMermaidInheritance(final StringBuilder sb, final AnalysisResult result) {
    final Set<String> declared = new HashSet<>();
    for (final ClassInfo classInfo : result.getClasses()) {
      final String simpleName = sanitizeDiagramId(DocumentUtils.getSimpleName(classInfo.getFqn()));
      // Declare class with type annotation
      if (!declared.contains(simpleName)) {
        if (classInfo.isInterface()) {
          sb.append(MERMAID_CLASS_PREFIX).append(simpleName).append(" {\n");
          sb.append("        <<interface>>\n");
          sb.append("    }\n");
        } else if (classInfo.isAbstract()) {
          sb.append(MERMAID_CLASS_PREFIX).append(simpleName).append(" {\n");
          sb.append("        <<abstract>>\n");
          sb.append("    }\n");
        } else {
          sb.append(MERMAID_CLASS_PREFIX).append(simpleName).append("\n");
        }
        declared.add(simpleName);
      }
      // Inheritance relationships
      for (final String extendsType : classInfo.getExtendsTypes()) {
        final String parentName = sanitizeDiagramId(DocumentUtils.getSimpleName(extendsType));
        sb.append("    ").append(parentName).append(" <|-- ").append(simpleName).append("\n");
      }
      // Implementation relationships
      for (final String implementsType : classInfo.getImplementsTypes()) {
        final String interfaceName = sanitizeDiagramId(DocumentUtils.getSimpleName(implementsType));
        sb.append("    ").append(interfaceName).append(" <|.. ").append(simpleName).append("\n");
      }
    }
  }

  private void appendMermaidMethodCalls(final StringBuilder sb, final ClassInfo classInfo) {
    final String className = sanitizeDiagramId(DocumentUtils.getSimpleName(classInfo.getFqn()));
    final Set<String> nodes = new HashSet<>();
    final Set<String> edges = new HashSet<>();
    final Map<String, String> idsBySignature = new LinkedHashMap<>();
    final Map<String, Set<String>> idsByName = new LinkedHashMap<>();
    appendMermaidMethodNodes(sb, classInfo, className, nodes, idsBySignature, idsByName);
    appendMermaidMethodEdges(sb, classInfo, className, nodes, edges, idsBySignature, idsByName);
    appendMermaidMethodStyle(sb);
  }

  private void appendMermaidMethodNodes(
      final StringBuilder sb,
      final ClassInfo classInfo,
      final String className,
      final Set<String> nodes,
      final Map<String, String> idsBySignature,
      final Map<String, Set<String>> idsByName) {
    for (final MethodInfo method : classInfo.getMethods()) {
      final String signatureKey = buildMethodSignatureKey(method);
      final String methodId = buildMethodId(className, signatureKey);
      idsBySignature.put(signatureKey, methodId);
      idsByName.computeIfAbsent(method.getName(), k -> new HashSet<>()).add(methodId);
      if (nodes.add(methodId)) {
        appendMermaidNode(sb, methodId, buildMethodDisplayName(method));
      }
    }
  }

  private void appendMermaidMethodEdges(
      final StringBuilder sb,
      final ClassInfo classInfo,
      final String className,
      final Set<String> nodes,
      final Set<String> edges,
      final Map<String, String> idsBySignature,
      final Map<String, Set<String>> idsByName) {
    for (final MethodInfo method : classInfo.getMethods()) {
      final String methodId = resolveMermaidMethodId(className, method, idsBySignature);
      appendMermaidEdgesForMethodCall(
          sb, nodes, edges, idsBySignature, idsByName, method, methodId);
    }
  }

  private String resolveMermaidMethodId(
      final String className, final MethodInfo method, final Map<String, String> idsBySignature) {
    final String signatureKey = buildMethodSignatureKey(method);
    return idsBySignature.getOrDefault(signatureKey, buildMethodId(className, signatureKey));
  }

  private void appendMermaidEdgesForMethodCall(
      final StringBuilder sb,
      final Set<String> nodes,
      final Set<String> edges,
      final Map<String, String> idsBySignature,
      final Map<String, Set<String>> idsByName,
      final MethodInfo method,
      final String methodId) {
    for (final String called : method.getCalledMethods()) {
      appendMermaidEdgesForCalledMethod(
          sb, nodes, edges, idsBySignature, idsByName, methodId, called);
    }
  }

  private void appendMermaidEdgesForCalledMethod(
      final StringBuilder sb,
      final Set<String> nodes,
      final Set<String> edges,
      final Map<String, String> idsBySignature,
      final Map<String, Set<String>> idsByName,
      final String methodId,
      final String called) {
    final String calledKey = extractMethodSignatureKey(called);
    final Set<String> targetIds = resolveCalledMethodIds(calledKey, idsBySignature, idsByName);
    for (final String calledId : targetIds) {
      appendMermaidCalledNodeIfAbsent(sb, nodes, calledId, called);
      appendMermaidEdgeIfAbsent(sb, edges, methodId, calledId);
    }
  }

  private void appendMermaidCalledNodeIfAbsent(
      final StringBuilder sb, final Set<String> nodes, final String calledId, final String called) {
    if (nodes.add(calledId)) {
      final String calledMethodName = LlmDocumentTextUtils.extractMethodName(called);
      appendMermaidNode(
          sb,
          calledId,
          calledMethodName.isBlank() ? msg("document.value.unknown") : calledMethodName);
    }
  }

  private void appendMermaidEdgeIfAbsent(
      final StringBuilder sb,
      final Set<String> edges,
      final String methodId,
      final String calledId) {
    final String edge = methodId + "-->" + calledId;
    if (edges.add(edge)) {
      sb.append("    ").append(edge).append("\n");
    }
  }

  private void appendMermaidMethodStyle(final StringBuilder sb) {
    sb.append("\n    classDef default fill:#16213e,stroke:#0f3460,color:#fff\n");
  }

  private void appendMermaidNode(final StringBuilder sb, final String nodeId, final String label) {
    sb.append("    ").append(nodeId).append("[\"").append(label).append(MERMAID_NODE_SUFFIX);
  }

  // PlantUML diagram generation methods
  private void appendPlantUmlClassDependencies(
      final StringBuilder sb, final AnalysisResult result) {
    final Map<String, Set<String>> packages = collectPlantUmlPackages(result);
    appendPlantUmlPackages(sb, packages);
    appendPlantUmlDependencyEdges(sb, result);
  }

  private Map<String, Set<String>> collectPlantUmlPackages(final AnalysisResult result) {
    final Map<String, Set<String>> packages = new LinkedHashMap<>();
    for (final ClassInfo classInfo : result.getClasses()) {
      final String packageName =
          DocumentUtils.formatPackageNameForDisplay(
              DocumentUtils.getPackageName(classInfo.getFqn()));
      final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
      packages.computeIfAbsent(packageName, k -> new HashSet<>()).add(simpleName);
    }
    return packages;
  }

  private void appendPlantUmlPackages(
      final StringBuilder sb, final Map<String, Set<String>> packages) {
    for (final Map.Entry<String, Set<String>> entry : packages.entrySet()) {
      sb.append("package \"").append(entry.getKey()).append("\" {\n");
      for (final String className : entry.getValue()) {
        sb.append("  class ").append(className).append("\n");
      }
      sb.append("}\n\n");
    }
  }

  private void appendPlantUmlDependencyEdges(final StringBuilder sb, final AnalysisResult result) {
    final Set<String> edges = new HashSet<>();
    for (final ClassInfo classInfo : result.getClasses()) {
      appendPlantUmlEdgesForClass(sb, edges, classInfo);
    }
  }

  private void appendPlantUmlEdgesForClass(
      final StringBuilder sb, final Set<String> edges, final ClassInfo classInfo) {
    final String sourceName = DocumentUtils.getSimpleName(classInfo.getFqn());
    for (final MethodInfo method : classInfo.getMethods()) {
      appendPlantUmlEdgesForMethod(sb, edges, sourceName, method);
    }
  }

  private void appendPlantUmlEdgesForMethod(
      final StringBuilder sb,
      final Set<String> edges,
      final String sourceName,
      final MethodInfo method) {
    for (final String calledMethod : method.getCalledMethods()) {
      final String edge = buildPlantUmlDependencyEdge(sourceName, calledMethod);
      if (edge != null && edges.add(edge)) {
        sb.append(edge).append("\n");
      }
    }
  }

  private String buildPlantUmlDependencyEdge(final String sourceName, final String calledMethod) {
    final String targetClass = extractSimpleClassName(calledMethod);
    if (targetClass == null || targetClass.equals(sourceName)) {
      return null;
    }
    return sourceName + " --> " + targetClass;
  }

  private void appendPlantUmlInheritance(final StringBuilder sb, final AnalysisResult result) {
    for (final ClassInfo classInfo : result.getClasses()) {
      final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
      if (classInfo.isInterface()) {
        sb.append("interface ").append(simpleName).append("\n");
      } else if (classInfo.isAbstract()) {
        sb.append("abstract class ").append(simpleName).append("\n");
      } else {
        sb.append("class ").append(simpleName).append("\n");
      }
      for (final String extendsType : classInfo.getExtendsTypes()) {
        final String parentName = DocumentUtils.getSimpleName(extendsType);
        sb.append(parentName).append(" <|-- ").append(simpleName).append("\n");
      }
      for (final String implementsType : classInfo.getImplementsTypes()) {
        final String interfaceName = DocumentUtils.getSimpleName(implementsType);
        sb.append(interfaceName).append(" <|.. ").append(simpleName).append("\n");
      }
    }
  }

  private void appendPlantUmlMethodCalls(final StringBuilder sb, final ClassInfo classInfo) {
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final Map<String, String> idsBySignature = new LinkedHashMap<>();
    final Map<String, Set<String>> idsByName = new LinkedHashMap<>();
    sb.append("title ")
        .append(msg("document.diagram.method_calls.plantuml_title", className))
        .append("\n\n");
    for (final MethodInfo method : classInfo.getMethods()) {
      final String signatureKey = buildMethodSignatureKey(method);
      final String methodId = sanitizeDiagramId(signatureKey);
      idsBySignature.put(signatureKey, methodId);
      idsByName.computeIfAbsent(method.getName(), k -> new HashSet<>()).add(methodId);
      sb.append("rectangle \"")
          .append(buildMethodDisplayName(method))
          .append("\" as ")
          .append(methodId)
          .append("\n");
    }
    sb.append("\n");
    final Set<String> edges = new HashSet<>();
    for (final MethodInfo method : classInfo.getMethods()) {
      final String methodId = sanitizeDiagramId(buildMethodSignatureKey(method));
      for (final String called : method.getCalledMethods()) {
        final String calledKey = extractMethodSignatureKey(called);
        final Set<String> targetIds = resolveCalledMethodIds(calledKey, idsBySignature, idsByName);
        for (final String calledId : targetIds) {
          final String edge = methodId + " --> " + calledId;
          if (edges.add(edge)) {
            sb.append(edge).append("\n");
          }
        }
      }
    }
  }

  // Utility methods
  private String sanitizeDiagramId(final String name) {
    if (name == null) {
      return UNKNOWN_VALUE;
    }
    return name.replaceAll("\\W", "_");
  }

  private String extractClassName(final String methodRef) {
    if (methodRef == null) {
      return null;
    }
    // Format: com.example.ClassName#methodName or com.example.ClassName.methodName
    final int hashIndex = methodRef.indexOf('#');
    if (hashIndex > 0) {
      return methodRef.substring(0, hashIndex);
    }
    final int lastDot = methodRef.lastIndexOf('.');
    if (lastDot > 0) {
      // Check if the part after last dot looks like a method name (lowercase start)
      final String lastPart = methodRef.substring(lastDot + 1);
      if (!lastPart.isEmpty() && Character.isLowerCase(lastPart.charAt(0))) {
        return methodRef.substring(0, lastDot);
      }
    }
    return null;
  }

  private String extractSimpleClassName(final String methodRef) {
    final String fqn = extractClassName(methodRef);
    return fqn != null ? DocumentUtils.getSimpleName(fqn) : null;
  }

  private String extractMethodSignatureKey(final String methodRef) {
    if (methodRef == null || methodRef.isBlank()) {
      return UNKNOWN_VALUE;
    }
    final int hashIndex = methodRef.indexOf('#');
    final String methodPart =
        hashIndex >= 0 && hashIndex < methodRef.length() - 1
            ? methodRef.substring(hashIndex + 1)
            : methodRef.substring(methodRef.lastIndexOf('.') + 1);
    final int parenIndex = methodPart.indexOf('(');
    if (parenIndex >= 0) {
      final int closeIndex = methodPart.indexOf(')', parenIndex);
      final String name = methodPart.substring(0, parenIndex);
      final String params =
          closeIndex > parenIndex ? methodPart.substring(parenIndex + 1, closeIndex) : "";
      return name + "(" + normalizeParamList(params) + ")";
    }
    return methodPart;
  }

  private String buildMethodSignatureKey(final MethodInfo method) {
    return extractSignatureKey(method.getSignature(), method.getName());
  }

  private String buildMethodDisplayName(final MethodInfo method) {
    return extractSignatureDisplayName(method.getSignature(), method.getName());
  }

  private String extractSignatureKey(final String signature, final String fallbackName) {
    if (signature == null || signature.isBlank()) {
      return fallbackName != null ? fallbackName : UNKNOWN_VALUE;
    }
    final int parenIndex = signature.indexOf('(');
    if (parenIndex <= 0) {
      return fallbackName != null ? fallbackName : signature;
    }
    int nameStart = parenIndex - 1;
    while (nameStart >= 0 && Character.isJavaIdentifierPart(signature.charAt(nameStart))) {
      nameStart--;
    }
    final String name = signature.substring(nameStart + 1, parenIndex);
    final int closeIndex = signature.indexOf(')', parenIndex);
    final String params =
        closeIndex > parenIndex ? signature.substring(parenIndex + 1, closeIndex).trim() : "";
    return name + "(" + normalizeParamList(params) + ")";
  }

  private String extractSignatureDisplayName(final String signature, final String fallbackName) {
    if (signature == null || signature.isBlank()) {
      return fallbackName != null ? fallbackName : msg("document.value.unknown");
    }
    final int parenIndex = signature.indexOf('(');
    if (parenIndex <= 0) {
      return fallbackName != null ? fallbackName : signature;
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

  private String buildMethodId(final String className, final String signatureKey) {
    return className + "_" + sanitizeDiagramId(signatureKey);
  }

  private String normalizeParamList(final String params) {
    if (params == null || params.isBlank()) {
      return "";
    }
    return params.replaceAll("\\s+", "");
  }

  private Set<String> resolveCalledMethodIds(
      final String calledKey,
      final Map<String, String> idsBySignature,
      final Map<String, Set<String>> idsByName) {
    if (calledKey == null || calledKey.isBlank()) {
      return Set.of();
    }
    if (calledKey.contains("(")) {
      final String id = idsBySignature.get(calledKey);
      if (id != null) {
        return Set.of(id);
      }
      final String fallbackName = extractNameFromSignatureKey(calledKey);
      return idsByName.getOrDefault(fallbackName, Set.of());
    }
    return idsByName.getOrDefault(calledKey, Set.of());
  }

  private String extractNameFromSignatureKey(final String signatureKey) {
    final int parenIndex = signatureKey.indexOf('(');
    if (parenIndex > 0) {
      return signatureKey.substring(0, parenIndex);
    }
    return signatureKey;
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
