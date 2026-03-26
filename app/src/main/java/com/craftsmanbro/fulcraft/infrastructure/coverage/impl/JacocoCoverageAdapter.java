package com.craftsmanbro.fulcraft.infrastructure.coverage.impl;

import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.xml.contract.XmlSecurityFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.xml.impl.DefaultXmlSecurityFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Coverage loader for JaCoCo XML reports. */
public class JacocoCoverageAdapter implements CoverageLoaderPort {

  private static final String TAG_PACKAGE = "package";

  private static final String TAG_CLASS = "class";

  private static final String TAG_METHOD = "method";

  private static final String TAG_COUNTER = "counter";

  private static final String ATTR_NAME = "name";

  private static final String ATTR_DESC = "desc";

  private static final String ATTR_TYPE = "type";

  private static final String ATTR_MISSED = "missed";

  private static final String ATTR_COVERED = "covered";

  private final Path reportPath;

  private final XmlSecurityFactoryPort xmlSecurityFactory;

  private final Object loadLock = new Object();

  private volatile boolean loaded;

  private volatile boolean available;

  private Map<String, CoverageStats> classCoverage = Map.of();

  private Map<MethodKey, CoverageStats> methodCoverageByFqn = Map.of();

  private Map<MethodKey, CoverageStats> methodCoverageBySimple = Map.of();

  public JacocoCoverageAdapter(final Path reportPath) {
    this(reportPath, new DefaultXmlSecurityFactory());
  }

  public JacocoCoverageAdapter(
      final Path reportPath, final XmlSecurityFactoryPort xmlSecurityFactory) {
    this.reportPath =
        Objects.requireNonNull(
            reportPath,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "reportPath"));
    this.xmlSecurityFactory =
        Objects.requireNonNull(
            xmlSecurityFactory,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "xmlSecurityFactory"));
  }

  @Override
  public boolean isAvailable() {
    loadIfNeeded();
    return available;
  }

  @Override
  public double getLineCoverage(final String classFqn) {
    loadIfNeeded();
    if (!available || classFqn == null) {
      return -1;
    }
    final CoverageStats stats = resolveClassCoverage(classFqn);
    return stats != null ? stats.lineCoveragePercent() : -1;
  }

  @Override
  public double getBranchCoverage(final String classFqn) {
    loadIfNeeded();
    if (!available || classFqn == null) {
      return -1;
    }
    final CoverageStats stats = resolveClassCoverage(classFqn);
    return stats != null ? stats.branchCoveragePercent() : -1;
  }

  @Override
  public double getMethodCoverage(final String classFqn, final String methodSignature) {
    loadIfNeeded();
    if (!available || classFqn == null || methodSignature == null || methodSignature.isBlank()) {
      return -1;
    }
    final ParsedMethodSignature parsed = ParsedMethodSignature.parse(methodSignature);
    if (parsed.name().isEmpty()) {
      return -1;
    }
    final CoverageStats stats = resolveMethodCoverage(classFqn, parsed);
    if (stats == null) {
      return -1;
    }
    final double lineCoverage = stats.lineCoveragePercent();
    if (lineCoverage >= 0) {
      return lineCoverage;
    }
    return stats.branchCoveragePercent();
  }

  private void loadIfNeeded() {
    if (loaded) {
      return;
    }
    synchronized (loadLock) {
      if (loaded) {
        return;
      }
      loaded = true;
      if (!Files.exists(reportPath)) {
        available = false;
        return;
      }
      try {
        parseReport();
        available = true;
      } catch (IOException e) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Failed to parse JaCoCo report: " + e.getMessage()));
        available = false;
      }
    }
  }

  private void parseReport() throws IOException {
    final Document doc = parseXml(reportPath);
    final Element root = doc.getDocumentElement();
    root.normalize();
    final Map<String, CoverageStats> classMap = new HashMap<>();
    final Map<MethodKey, CoverageStats> methodFqnMap = new HashMap<>();
    final Map<MethodKey, CoverageStats> methodSimpleMap = new HashMap<>();
    final NodeList packages = root.getElementsByTagName(TAG_PACKAGE);
    for (int i = 0; i < packages.getLength(); i++) {
      final Node pkgNode = packages.item(i);
      if (pkgNode instanceof Element pkgElement) {
        parsePackage(pkgElement, classMap, methodFqnMap, methodSimpleMap);
      }
    }
    this.classCoverage = classMap;
    this.methodCoverageByFqn = methodFqnMap;
    this.methodCoverageBySimple = methodSimpleMap;
  }

  private Document parseXml(final Path path) throws IOException {
    try {
      final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(path.toFile());
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to parse JaCoCo report: " + e.getMessage()),
          e);
    }
  }

  private void parsePackage(
      final Element pkgElement,
      final Map<String, CoverageStats> classMap,
      final Map<MethodKey, CoverageStats> methodFqnMap,
      final Map<MethodKey, CoverageStats> methodSimpleMap) {
    final NodeList children = pkgElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node node = children.item(i);
      if (node instanceof Element element && TAG_CLASS.equals(element.getTagName())) {
        parseClass(element, classMap, methodFqnMap, methodSimpleMap);
      }
    }
  }

  private void parseClass(
      final Element classElement,
      final Map<String, CoverageStats> classMap,
      final Map<MethodKey, CoverageStats> methodFqnMap,
      final Map<MethodKey, CoverageStats> methodSimpleMap) {
    final String className = classElement.getAttribute(ATTR_NAME);
    if (className == null || className.isBlank()) {
      return;
    }
    final String classFqn = className.replace('/', '.');
    final CoverageStats classStats = CoverageStats.fromCounters(classElement);
    classMap.put(classFqn, classStats);
    final NodeList children = classElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node node = children.item(i);
      if (node instanceof Element element && TAG_METHOD.equals(element.getTagName())) {
        parseMethod(classFqn, element, methodFqnMap, methodSimpleMap);
      }
    }
  }

  private void parseMethod(
      final String classFqn,
      final Element methodElement,
      final Map<MethodKey, CoverageStats> methodFqnMap,
      final Map<MethodKey, CoverageStats> methodSimpleMap) {
    final String methodName = methodElement.getAttribute(ATTR_NAME);
    final String desc = methodElement.getAttribute(ATTR_DESC);
    if (methodName == null || methodName.isBlank() || desc == null || desc.isBlank()) {
      return;
    }
    final CoverageStats methodStats = CoverageStats.fromCounters(methodElement);
    final DescriptorSignature signature = DescriptorSignature.parse(desc);
    final MethodKey fqnKey = new MethodKey(classFqn, methodName, signature.parameterTypesFqn());
    methodFqnMap.putIfAbsent(fqnKey, methodStats);
    final MethodKey simpleKey =
        new MethodKey(classFqn, methodName, signature.parameterTypesSimple());
    methodSimpleMap.putIfAbsent(simpleKey, methodStats);
  }

  private CoverageStats resolveClassCoverage(final String classFqn) {
    for (final String candidate : classNameCandidates(classFqn)) {
      final CoverageStats stats = classCoverage.get(candidate);
      if (stats != null) {
        return stats;
      }
    }
    return null;
  }

  private CoverageStats resolveMethodCoverage(
      final String classFqn, final ParsedMethodSignature parsed) {
    for (final String candidate : classNameCandidates(classFqn)) {
      final MethodKey fqnKey = new MethodKey(candidate, parsed.name(), parsed.parameterTypesFqn());
      CoverageStats stats = methodCoverageByFqn.get(fqnKey);
      if (stats != null) {
        return stats;
      }
      final MethodKey simpleKey =
          new MethodKey(candidate, parsed.name(), parsed.parameterTypesSimple());
      stats = methodCoverageBySimple.get(simpleKey);
      if (stats != null) {
        return stats;
      }
    }
    return null;
  }

  private List<String> classNameCandidates(final String classFqn) {
    if (classFqn == null || classFqn.isBlank()) {
      return List.of();
    }
    final String trimmed = classFqn.trim();
    final LinkedHashSet<String> candidates = new LinkedHashSet<>();
    candidates.add(trimmed);
    if (trimmed.indexOf('$') >= 0) {
      candidates.add(trimmed.replace('$', '.'));
    }
    final String[] segments = trimmed.split("\\.");
    for (int packageSegmentCount = 1;
        packageSegmentCount < segments.length;
        packageSegmentCount++) {
      final StringBuilder candidate = new StringBuilder();
      for (int i = 0; i < packageSegmentCount; i++) {
        if (i > 0) {
          candidate.append('.');
        }
        candidate.append(segments[i]);
      }
      candidate.append('.');
      for (int i = packageSegmentCount; i < segments.length; i++) {
        if (i > packageSegmentCount) {
          candidate.append('$');
        }
        candidate.append(segments[i]);
      }
      candidates.add(candidate.toString());
    }
    return List.copyOf(candidates);
  }

  private static String simpleTypeName(final String type) {
    String trimmed = type.trim();
    final StringBuilder arraySuffix = new StringBuilder();
    while (trimmed.endsWith("[]")) {
      arraySuffix.append("[]");
      trimmed = trimmed.substring(0, trimmed.length() - 2);
    }
    final int separatorIndex = Math.max(trimmed.lastIndexOf('.'), trimmed.lastIndexOf('$'));
    final String base = separatorIndex >= 0 ? trimmed.substring(separatorIndex + 1) : trimmed;
    return base + arraySuffix;
  }

  private static String stripAnnotations(final String value) {
    final StringBuilder result = new StringBuilder();
    int index = 0;
    while (index < value.length()) {
      final char current = value.charAt(index);
      if (current != '@') {
        result.append(current);
        index++;
        continue;
      }

      index++;
      while (index < value.length() && isAnnotationNameCharacter(value.charAt(index))) {
        index++;
      }
      if (index < value.length() && value.charAt(index) == '(') {
        int depth = 1;
        index++;
        while (index < value.length() && depth > 0) {
          final char annotationChar = value.charAt(index);
          if (annotationChar == '(') {
            depth++;
          } else if (annotationChar == ')') {
            depth--;
          }
          index++;
        }
      }
      while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
        index++;
      }
      if (!result.isEmpty() && !Character.isWhitespace(result.charAt(result.length() - 1))) {
        result.append(' ');
      }
    }
    return result.toString();
  }

  private static boolean isAnnotationNameCharacter(final char value) {
    return Character.isJavaIdentifierPart(value) || value == '.';
  }

  private static boolean isIgnoredSignatureToken(final String value) {
    return "final".equals(value) || "extends".equals(value) || "super".equals(value);
  }

  private record MethodKey(String classFqn, String methodName, List<String> parameterTypes) {}

  private record CoverageStats(
      int lineCovered, int lineMissed, int branchCovered, int branchMissed) {

    static CoverageStats fromCounters(final Element parent) {
      int lineCovered = 0;
      int lineMissed = 0;
      int branchCovered = 0;
      int branchMissed = 0;
      final NodeList counters = parent.getChildNodes();
      for (int i = 0; i < counters.getLength(); i++) {
        final Node node = counters.item(i);
        if (node instanceof Element counter && TAG_COUNTER.equals(counter.getTagName())) {
          final String type = counter.getAttribute(ATTR_TYPE);
          final int covered = parseInt(counter.getAttribute(ATTR_COVERED));
          final int missed = parseInt(counter.getAttribute(ATTR_MISSED));
          if ("LINE".equalsIgnoreCase(type)) {
            lineCovered += covered;
            lineMissed += missed;
          } else if ("BRANCH".equalsIgnoreCase(type)) {
            branchCovered += covered;
            branchMissed += missed;
          }
        }
      }
      return new CoverageStats(lineCovered, lineMissed, branchCovered, branchMissed);
    }

    double lineCoveragePercent() {
      final int total = lineCovered + lineMissed;
      if (total <= 0) {
        return -1;
      }
      return (lineCovered * 100.0) / total;
    }

    double branchCoveragePercent() {
      final int total = branchCovered + branchMissed;
      if (total <= 0) {
        return -1;
      }
      return (branchCovered * 100.0) / total;
    }

    private static int parseInt(final String value) {
      if (value == null || value.isBlank()) {
        return 0;
      }
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
  }

  private record DescriptorSignature(
      List<String> parameterTypesFqn, List<String> parameterTypesSimple) {

    static DescriptorSignature parse(final String descriptor) {
      if (descriptor == null || descriptor.isBlank()) {
        return new DescriptorSignature(List.of(), List.of());
      }
      final int start = descriptor.indexOf('(');
      final int end = descriptor.indexOf(')');
      if (start < 0 || end < 0 || end <= start) {
        return new DescriptorSignature(List.of(), List.of());
      }
      final List<String> fqnTypes = new ArrayList<>();
      final List<String> simpleTypes = new ArrayList<>();
      int index = start + 1;
      while (index < end) {
        final ParsedType parsed = parseDescriptorType(descriptor, index);
        fqnTypes.add(parsed.fqnType());
        simpleTypes.add(parsed.simpleType());
        index = parsed.nextIndex();
      }
      return new DescriptorSignature(fqnTypes, simpleTypes);
    }

    private static ParsedType parseDescriptorType(final String descriptor, final int index) {
      int cursor = index;
      int arrayDepth = 0;
      while (cursor < descriptor.length() && descriptor.charAt(cursor) == '[') {
        arrayDepth++;
        cursor++;
      }
      final String baseType;
      final String simpleType;
      final char c = descriptor.charAt(cursor);
      if (c == 'L') {
        int end = descriptor.indexOf(';', cursor);
        if (end < 0) {
          end = descriptor.length();
        }
        final String raw = descriptor.substring(cursor + 1, end).replace('/', '.');
        baseType = raw;
        simpleType = simpleTypeName(raw);
        cursor = end + 1;
      } else {
        baseType =
            switch (c) {
              case 'B' -> "byte";
              case 'C' -> "char";
              case 'D' -> "double";
              case 'F' -> "float";
              case 'I' -> "int";
              case 'J' -> "long";
              case 'S' -> "short";
              case 'Z' -> "boolean";
              case 'V' -> "void";
              default -> "";
            };
        simpleType = baseType;
        cursor++;
      }
      final String suffix = "[]".repeat(Math.max(0, arrayDepth));
      return new ParsedType(baseType + suffix, simpleType + suffix, cursor);
    }

    private record ParsedType(String fqnType, String simpleType, int nextIndex) {}
  }

  private record ParsedMethodSignature(
      String name, List<String> parameterTypesFqn, List<String> parameterTypesSimple) {

    static ParsedMethodSignature parse(final String signature) {
      if (signature == null || signature.isBlank()) {
        return new ParsedMethodSignature("", List.of(), List.of());
      }
      final int openParen = signature.indexOf('(');
      final int closeParen = signature.lastIndexOf(')');
      if (openParen < 0 || closeParen < openParen) {
        return new ParsedMethodSignature("", List.of(), List.of());
      }
      final String namePart = signature.substring(0, openParen).trim();
      final String[] tokens = namePart.split("[\\s#\\.]+");
      final String methodName = tokens.length == 0 ? "" : tokens[tokens.length - 1];
      final String paramsSection = signature.substring(openParen + 1, closeParen);
      final List<String> rawParams = splitParameters(paramsSection);
      final List<String> fqnParams = new ArrayList<>();
      final List<String> simpleParams = new ArrayList<>();
      for (final String raw : rawParams) {
        final String normalized = normalizeType(raw);
        if (!normalized.isBlank()) {
          fqnParams.add(normalized);
          simpleParams.add(toSimpleName(normalized));
        }
      }
      return new ParsedMethodSignature(methodName, fqnParams, simpleParams);
    }

    private static List<String> splitParameters(final String paramsSection) {
      if (paramsSection == null || paramsSection.isBlank()) {
        return List.of();
      }
      final List<String> params = new ArrayList<>();
      final StringBuilder current = new StringBuilder();
      int genericDepth = 0;
      for (final char c : paramsSection.toCharArray()) {
        if (c == '<') {
          genericDepth++;
          current.append(c);
        } else if (c == '>') {
          genericDepth = Math.max(0, genericDepth - 1);
          current.append(c);
        } else if (c == ',' && genericDepth == 0) {
          params.add(current.toString().trim());
          current.setLength(0);
        } else {
          current.append(c);
        }
      }
      if (!current.isEmpty()) {
        params.add(current.toString().trim());
      }
      return params;
    }

    private static String normalizeType(final String rawType) {
      if (rawType == null || rawType.isBlank()) {
        return "";
      }
      final String cleaned =
          stripAnnotations(stripGenerics(rawType)).replace("...", "[]").replace("?", " ").trim();
      final String[] tokens = cleaned.split("\\s+");
      for (final String token : tokens) {
        if (token.isBlank() || isIgnoredSignatureToken(token)) {
          continue;
        }
        return token.trim();
      }
      return "";
    }

    private static String stripGenerics(final String value) {
      final StringBuilder result = new StringBuilder();
      int depth = 0;
      for (final char c : value.toCharArray()) {
        if (c == '<') {
          depth++;
        } else if (c == '>') {
          depth = Math.max(0, depth - 1);
        } else if (depth == 0) {
          result.append(c);
        }
      }
      return result.toString();
    }

    private static String toSimpleName(final String type) {
      return simpleTypeName(type);
    }
  }
}
