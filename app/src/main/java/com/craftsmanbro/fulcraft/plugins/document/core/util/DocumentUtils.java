package com.craftsmanbro.fulcraft.plugins.document.core.util;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared utility methods for document generators.
 *
 * <p>Provides common functionality used by multiple DocumentGenerator implementations to avoid code
 * duplication.
 */
public final class DocumentUtils {

  private static final String DEFAULT_PACKAGE = "(default)";

  private DocumentUtils() {
    // Utility class, no instantiation
  }

  /**
   * Gets the simple name from a fully qualified name.
   *
   * @param fqn the fully qualified name
   * @return the simple class name
   */
  public static String getSimpleName(final String fqn) {
    if (fqn == null) {
      return MessageSource.getMessage("document.value.unknown");
    }
    final int lastDot = fqn.lastIndexOf('.');
    return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
  }

  /**
   * Gets the package name from a fully qualified name.
   *
   * @param fqn the fully qualified name
   * @return the package name
   */
  public static String getPackageName(final String fqn) {
    if (fqn == null || fqn.isBlank()) {
      return "";
    }
    final String normalized = fqn.strip().replace('$', '.');
    final String[] tokens = normalized.split("\\.");
    if (tokens.length <= 1) {
      return DEFAULT_PACKAGE;
    }
    int typeTokenIndex = -1;
    for (int i = 0; i < tokens.length; i++) {
      final String token = tokens[i];
      if (token != null && !token.isBlank() && Character.isUpperCase(token.charAt(0))) {
        typeTokenIndex = i;
        break;
      }
    }
    if (typeTokenIndex > 0) {
      return String.join(".", java.util.Arrays.copyOfRange(tokens, 0, typeTokenIndex));
    }
    final int lastDot = normalized.lastIndexOf('.');
    return lastDot >= 0 ? normalized.substring(0, lastDot) : DEFAULT_PACKAGE;
  }

  /**
   * Gets package name primarily from source file path, then falls back to FQN parsing.
   *
   * @param classInfo class metadata
   * @return package name
   */
  public static String getPackageName(final ClassInfo classInfo) {
    if (classInfo == null) {
      return "";
    }
    final String fromPath = derivePackageNameFromFilePath(classInfo.getFilePath());
    if (!fromPath.isBlank()) {
      return fromPath;
    }
    return getPackageName(classInfo.getFqn());
  }

  /**
   * Formats a package name for display.
   *
   * @param packageName the package name
   * @return the localized display name for the package
   */
  public static String formatPackageNameForDisplay(final String packageName) {
    if (packageName == null || packageName.isBlank() || DEFAULT_PACKAGE.equals(packageName)) {
      return MessageSource.getMessage("document.value.default_package");
    }
    return packageName;
  }

  /**
   * Translates Java visibility modifiers to Japanese labels.
   *
   * @param visibility the visibility modifier
   * @return the Japanese label
   */
  public static String translateVisibility(final String visibility) {
    if (visibility == null) {
      return MessageSource.getMessage("document.value.empty");
    }
    return switch (visibility.toLowerCase(java.util.Locale.ROOT)) {
      case "public" -> MessageSource.getMessage("document.visibility.public");
      case "private" -> MessageSource.getMessage("document.visibility.private");
      case "protected" -> MessageSource.getMessage("document.visibility.protected");
      case "package-private", "package_private", "" ->
          MessageSource.getMessage("document.visibility.package_private");
      default -> visibility;
    };
  }

  /**
   * Gets the complexity label for a cyclomatic complexity value.
   *
   * @param complexity the cyclomatic complexity
   * @return the complexity label
   */
  public static String getComplexityLabel(final int complexity) {
    if (complexity <= 5) {
      return MessageSource.getMessage("document.complexity.low");
    }
    if (complexity <= 10) {
      return MessageSource.getMessage("document.complexity.medium");
    }
    if (complexity <= 20) {
      return MessageSource.getMessage("document.complexity.high");
    }
    return MessageSource.getMessage("document.complexity.very_high");
  }

  /**
   * Formats complexity with its label.
   *
   * @param complexity the cyclomatic complexity
   * @return the formatted complexity string
   */
  public static String formatComplexity(final int complexity) {
    return complexity + " (" + getComplexityLabel(complexity) + ")";
  }

  /**
   * Builds a class type description.
   *
   * @param classInfo the class information
   * @return the class type description
   */
  public static String buildClassType(final ClassInfo classInfo) {
    if (classInfo.isInterface()) {
      return MessageSource.getMessage("document.class_type.interface");
    } else if (classInfo.isAbstract()) {
      return MessageSource.getMessage("document.class_type.abstract");
    } else {
      return MessageSource.getMessage("document.class_type.class");
    }
  }

  /**
   * Generates an anchor ID for a class based on its FQN.
   *
   * @param fqn the fully qualified name
   * @return the anchor ID
   */
  public static String generateClassAnchor(final String fqn) {
    if (fqn == null) {
      return "unknown";
    }
    return fqn.replace('.', '-').toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Generates a file name from a class FQN.
   *
   * @param fqn the fully qualified name
   * @param extension the file extension (including dot)
   * @return the file name
   */
  public static String generateFileName(final String fqn, final String extension) {
    if (fqn == null) {
      return "unknown" + extension;
    }
    return fqn.replace('.', '_') + extension;
  }

  /**
   * Generates a report-relative file path aligned with source folder structure when file path is
   * available.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code src/main/java/com/example/Foo.java -> src/main/java/com/example/Foo.html}
   *   <li>{@code src/main/java/com/example/Foo.java + com.example.Foo.Bar ->
   *       src/main/java/com/example/Foo_Bar.html}
   * </ul>
   *
   * <p>Falls back to {@link #generateFileName(String, String)} when source path is unavailable.
   *
   * @param classInfo class metadata
   * @param extension target extension (including dot)
   * @return report-relative path using forward slashes
   */
  public static String generateSourceAlignedReportPath(
      final ClassInfo classInfo, final String extension) {
    if (classInfo == null) {
      return generateFileName(null, extension);
    }
    return generateSourceAlignedReportPath(classInfo.getFqn(), classInfo.getFilePath(), extension);
  }

  /**
   * Generates a report-relative file path aligned with source folder structure when source file
   * path is available.
   *
   * @param fqn class fully qualified name
   * @param sourceFilePath source file path from analysis
   * @param extension target extension (including dot)
   * @return report-relative path using forward slashes
   */
  public static String generateSourceAlignedReportPath(
      final String fqn, final String sourceFilePath, final String extension) {
    final String safeExtension = extension != null ? extension : "";
    final String fallback = generateFileName(fqn, safeExtension);
    if (sourceFilePath == null || sourceFilePath.isBlank()) {
      return fallback;
    }
    final String normalized = sourceFilePath.replace('\\', '/').trim();
    if (normalized.isBlank()
        || MethodInfo.UNKNOWN.equalsIgnoreCase(normalized)
        || "/".equals(normalized)) {
      return fallback;
    }
    final String noDrivePrefix =
        normalized.matches("^[A-Za-z]:/.*") ? normalized.substring(3) : normalized;
    final List<String> segments = new ArrayList<>();
    for (final String raw : noDrivePrefix.split("/")) {
      if (raw == null || raw.isBlank() || ".".equals(raw)) {
        continue;
      }
      if ("..".equals(raw)) {
        return fallback;
      }
      segments.add(sanitizePathSegment(raw));
    }
    if (segments.isEmpty()) {
      return fallback;
    }
    final String sourceFile = segments.get(segments.size() - 1);
    final String baseName = removeExtension(sourceFile);
    final String nestedSuffix = resolveNestedSuffix(fqn, baseName);
    final String outputFile =
        nestedSuffix.isBlank()
            ? sanitizePathSegment(baseName) + safeExtension
            : sanitizePathSegment(baseName) + "_" + nestedSuffix + safeExtension;
    segments.set(segments.size() - 1, outputFile);
    return String.join("/", segments);
  }

  private static String resolveNestedSuffix(final String fqn, final String baseName) {
    if (fqn == null || fqn.isBlank() || baseName == null || baseName.isBlank()) {
      return "";
    }
    final String[] tokens = fqn.replace('$', '.').split("\\.");
    int baseIndex = -1;
    for (int i = 0; i < tokens.length; i++) {
      if (baseName.equals(tokens[i])) {
        baseIndex = i;
        break;
      }
    }
    if (baseIndex < 0 || baseIndex >= tokens.length - 1) {
      return "";
    }
    final List<String> nested = new ArrayList<>();
    for (int i = baseIndex + 1; i < tokens.length; i++) {
      if (tokens[i] != null && !tokens[i].isBlank()) {
        nested.add(sanitizePathSegment(tokens[i]));
      }
    }
    return String.join("_", nested);
  }

  private static String removeExtension(final String fileName) {
    final int dot = fileName.lastIndexOf('.');
    if (dot <= 0) {
      return fileName;
    }
    return fileName.substring(0, dot);
  }

  private static String sanitizePathSegment(final String segment) {
    if (segment == null || segment.isBlank()) {
      return "_";
    }
    return segment.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  /**
   * Builds fields information as a formatted string.
   *
   * @param fields the list of fields
   * @return the formatted fields information
   */
  public static String buildFieldsInfo(final List<FieldInfo> fields) {
    if (fields == null || fields.isEmpty()) {
      return MessageSource.getMessage("document.value.no_fields");
    }
    final StringBuilder sb = new StringBuilder();
    for (final FieldInfo field : fields) {
      sb.append("- `")
          .append(field.getName())
          .append("`: ")
          .append(field.getType())
          .append(" (")
          .append(translateVisibility(field.getVisibility()))
          .append(")\n");
    }
    return sb.toString();
  }

  /**
   * Builds methods summary information.
   *
   * @param methods the list of methods
   * @return the formatted methods summary
   */
  public static String buildMethodsSummary(final List<MethodInfo> methods) {
    if (methods == null || methods.isEmpty()) {
      return MessageSource.getMessage("document.value.no_methods");
    }
    final StringBuilder sb = new StringBuilder();
    for (final MethodInfo method : methods) {
      sb.append("- `").append(method.getName()).append("`: ");
      sb.append(MessageSource.getMessage("document.label.complexity"))
          .append(" ")
          .append(method.getCyclomaticComplexity());
      sb.append(", ")
          .append(MessageSource.getMessage("document.label.lines"))
          .append(" ")
          .append(method.getLoc());
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Escapes HTML special characters in a string.
   *
   * @param text the text to escape
   * @return the escaped text
   */
  public static String escapeHtml(final String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * Strips Java comment regions (block/Javadoc/line comments) from a source snippet.
   *
   * @param text source text that may contain comments
   * @return source text with comment-only regions removed
   */
  public static String stripCommentedRegions(final String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final char ch = text.charAt(i);
      final char next = (i + 1 < length) ? text.charAt(i + 1) : '\0';
      if (ch == '"') {
        i = skipString(text, i, sb);
      } else if (ch == '\'') {
        i = skipChar(text, i, sb);
      } else if (ch == '/' && next == '/') {
        i = handleLineComment(text, i, sb);
      } else if (ch == '/' && next == '*') {
        i = skipBlockComment(text, i);
      } else {
        sb.append(ch);
        i++;
      }
    }
    return sb.toString().replaceAll("(?m)^[ \\t]+$", "").strip();
  }

  private static int handleLineComment(final String text, final int i, final StringBuilder sb) {
    int nextIndex = skipLineComment(text, i);
    if (nextIndex < text.length()
        && (text.charAt(nextIndex) == '\n' || text.charAt(nextIndex) == '\r')) {
      sb.append(text.charAt(nextIndex));
      nextIndex++;
    }
    return nextIndex;
  }

  private static int skipString(final String text, final int start, final StringBuilder sb) {
    sb.append('"');
    int i = start + 1;
    final int length = text.length();
    while (i < length) {
      final char c = text.charAt(i);
      sb.append(c);
      if (c == '\\') {
        if (i + 1 < length) {
          sb.append(text.charAt(++i));
        }
      } else if (c == '"') {
        return i + 1;
      }
      i++;
    }
    return i;
  }

  private static int skipChar(final String text, final int start, final StringBuilder sb) {
    sb.append('\'');
    int i = start + 1;
    final int length = text.length();
    while (i < length) {
      final char c = text.charAt(i);
      sb.append(c);
      if (c == '\\') {
        if (i + 1 < length) {
          sb.append(text.charAt(++i));
        }
      } else if (c == '\'') {
        return i + 1;
      }
      i++;
    }
    return i;
  }

  private static int skipLineComment(final String text, final int start) {
    int i = start + 2;
    final int length = text.length();
    while (i < length) {
      final char c = text.charAt(i);
      if (c == '\n' || c == '\r') {
        // Return index of the newline character
        return i;
      }
      i++;
    }
    // Reached end of text without finding newline
    return i;
  }

  private static int skipBlockComment(final String text, final int start) {
    int i = start + 2;
    final int length = text.length();
    while (i < length - 1) {
      if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
        return i + 2;
      }
      i++;
    }
    // If block comment is unclosed, consume till end of text
    return length;
  }

  /**
   * Filters method list for specification use.
   *
   * <p>Removes only implicit default constructors and private non-constructor methods while
   * deduplicating semantically identical signatures (e.g., {@code String} vs {@code
   * java.lang.String}).
   *
   * <p>This method intentionally keeps accessor-like methods to avoid specification/document
   * coverage gaps for DTO/data-holder classes.
   *
   * @param classInfo class metadata
   * @return filtered methods preserving input order as much as possible
   */
  public static List<MethodInfo> filterMethodsForSpecification(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getMethods().isEmpty()) {
      return List.of();
    }
    final String classSimpleName = getSimpleName(classInfo.getFqn());
    final Map<String, MethodInfo> deduplicated = new LinkedHashMap<>();
    for (final MethodInfo method : classInfo.getMethods()) {
      if (method == null || isImplicitDefaultConstructor(method, classSimpleName)) {
        continue;
      }
      final boolean constructor = isConstructor(method, classSimpleName);
      if (constructor || !isPrivateMethod(method)) {
        final String key = buildMethodDedupKey(method, classSimpleName);
        mergeMethodCandidate(deduplicated, key, method);
      }
    }
    return new ArrayList<>(deduplicated.values());
  }

  private static void mergeMethodCandidate(
      final Map<String, MethodInfo> target, final String key, final MethodInfo candidate) {
    if (target == null || key == null || candidate == null) {
      return;
    }
    final MethodInfo current = target.get(key);
    if (current == null || shouldReplaceMethod(current, candidate)) {
      target.put(key, candidate);
    }
  }

  private static boolean shouldReplaceMethod(final MethodInfo current, final MethodInfo candidate) {
    final int currentScore = methodInformationScore(current);
    final int candidateScore = methodInformationScore(candidate);
    if (candidateScore != currentScore) {
      return candidateScore > currentScore;
    }
    return signatureReadabilityScore(candidate) > signatureReadabilityScore(current);
  }

  private static int methodInformationScore(final MethodInfo method) {
    if (method == null) {
      return 0;
    }
    int score = signatureReadabilityScore(method) * 2;
    if (method.getSourceCode() != null && !method.getSourceCode().isBlank()) {
      score += 2;
    }
    if (!method.getAnnotations().isEmpty()) {
      score++;
    }
    if (!method.getCalledMethods().isEmpty()) {
      score++;
    }
    if (!method.getThrownExceptions().isEmpty()) {
      score++;
    }
    if (method.getBranchSummary() != null) {
      score++;
    }
    if (!method.getRepresentativePaths().isEmpty()) {
      score++;
    }
    if (method.getLoc() > 0) {
      score++;
    }
    return score;
  }

  private static int signatureReadabilityScore(final MethodInfo method) {
    if (method == null || method.getSignature() == null || method.getSignature().isBlank()) {
      return 0;
    }
    final String signature = method.getSignature();
    int score = 0;
    if (!signature.contains("$")) {
      score++;
    }
    if (!signature.contains("java.lang.")) {
      score++;
    }
    // Check for "package.Type" pattern without expensive regex
    if (signature.indexOf('.') == -1) {
      score++;
    }
    return score;
  }

  private static String buildMethodDedupKey(final MethodInfo method, final String classSimpleName) {
    final String methodName = resolveMethodName(method, classSimpleName);
    final List<String> parameterTypes = extractParameterTypes(method);
    return methodName + "(" + String.join(",", parameterTypes).toLowerCase(Locale.ROOT) + ")";
  }

  private static String resolveMethodName(final MethodInfo method, final String classSimpleName) {
    if (isConstructor(method, classSimpleName)) {
      return classSimpleName;
    }
    if (method != null && method.getName() != null && !method.getName().isBlank()) {
      return method.getName().strip();
    }
    if (method == null || method.getSignature() == null || method.getSignature().isBlank()) {
      return "";
    }
    final String signature = method.getSignature().strip().replace('$', '.');
    final int parenIndex = signature.indexOf('(');
    if (parenIndex <= 0) {
      return signature;
    }
    String candidate = signature.substring(0, parenIndex).trim();
    final int spaceIndex = candidate.lastIndexOf(' ');
    if (spaceIndex >= 0 && spaceIndex + 1 < candidate.length()) {
      candidate = candidate.substring(spaceIndex + 1);
    }
    final int dotIndex = candidate.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < candidate.length()) {
      candidate = candidate.substring(dotIndex + 1);
    }
    return candidate;
  }

  private static List<String> extractParameterTypes(final MethodInfo method) {
    if (method == null || method.getSignature() == null || method.getSignature().isBlank()) {
      return unknownParameterTypes(method == null ? 0 : method.getParameterCount());
    }
    final String signature = method.getSignature().strip().replace('$', '.');
    final int openParen = signature.indexOf('(');
    final int closeParen =
        signature.endsWith(")") ? signature.length() - 1 : signature.lastIndexOf(')');
    if (openParen < 0 || closeParen <= openParen) {
      return unknownParameterTypes(method.getParameterCount());
    }
    final String parameterSection = signature.substring(openParen + 1, closeParen).trim();
    if (parameterSection.isBlank()) {
      return List.of();
    }
    final List<String> parameters = splitTopLevelCsv(parameterSection);
    final List<String> normalized = new ArrayList<>();
    for (final String parameter : parameters) {
      final String normalizedParameter = normalizeParameterType(parameter);
      if (!normalizedParameter.isBlank()) {
        normalized.add(normalizedParameter);
      }
    }
    if (normalized.isEmpty()) {
      return unknownParameterTypes(method.getParameterCount());
    }
    return normalized;
  }

  private static List<String> unknownParameterTypes(final int parameterCount) {
    if (parameterCount <= 0) {
      return List.of();
    }
    final List<String> placeholders = new ArrayList<>();
    for (int i = 0; i < parameterCount; i++) {
      placeholders.add("arg" + i);
    }
    return placeholders;
  }

  private static List<String> splitTopLevelCsv(final String value) {
    final List<String> tokens = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return tokens;
    }
    final StringBuilder current = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      final char ch = value.charAt(i);
      if (ch == '<') {
        depth++;
      } else if (ch == '>' && depth > 0) {
        depth--;
      } else if (ch == ',' && depth == 0) {
        tokens.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    if (!current.isEmpty()) {
      tokens.add(current.toString().trim());
    }
    return tokens;
  }

  private static String normalizeParameterType(final String parameter) {
    if (parameter == null || parameter.isBlank()) {
      return "";
    }
    String normalized = parameter.strip().replace('$', '.');
    normalized = normalized.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
    normalized = normalized.replaceAll("\\b(final|volatile|transient)\\b\\s*", "");
    final int lastSpace = normalized.lastIndexOf(' ');
    if (lastSpace > 0 && lastSpace + 1 < normalized.length()) {
      final String tail = normalized.substring(lastSpace + 1);
      if (isLikelyParameterName(tail)) {
        normalized = normalized.substring(0, lastSpace).trim();
      }
    }
    normalized = simplifyQualifiedTypes(normalized);
    return normalized.replaceAll("\\s+", "");
  }

  private static boolean isLikelyParameterName(final String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    if (!Character.isLowerCase(token.charAt(0))) {
      return false;
    }
    for (int i = 0; i < token.length(); i++) {
      final char ch = token.charAt(i);
      if (!Character.isLetterOrDigit(ch) && ch != '_') {
        return false;
      }
    }
    return true;
  }

  private static String simplifyQualifiedTypes(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    final int length = value.length();
    int lastStart = 0;
    for (int i = 0; i < length; i++) {
      final char ch = value.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != '.') {
        if (i > lastStart) {
          sb.append(simplifyToken(value.substring(lastStart, i)));
        }
        sb.append(ch);
        lastStart = i + 1;
      }
    }
    if (lastStart < length) {
      sb.append(simplifyToken(value.substring(lastStart)));
    }
    return sb.toString();
  }

  private static String simplifyToken(final String token) {
    if (!token.contains(".")) {
      return token;
    }
    final int lastDot = token.lastIndexOf('.');
    if (lastDot > 0 && lastDot < token.length() - 1) {
      return token.substring(lastDot + 1);
    }
    return token;
  }

  private static boolean isImplicitDefaultConstructor(
      final MethodInfo method, final String classSimpleName) {
    return MethodSemantics.isImplicitDefaultConstructor(method, classSimpleName);
  }

  private static boolean isPrivateMethod(final MethodInfo method) {
    if (method == null || method.getVisibility() == null) {
      return false;
    }
    return "private".equalsIgnoreCase(method.getVisibility().strip());
  }

  private static boolean isConstructor(final MethodInfo method, final String classSimpleName) {
    return MethodSemantics.isConstructor(method, classSimpleName);
  }

  private static String derivePackageNameFromFilePath(final String filePath) {
    if (filePath == null || filePath.isBlank()) {
      return "";
    }
    final String normalized = filePath.replace('\\', '/').strip();
    if (normalized.isBlank()
        || MethodInfo.UNKNOWN.equalsIgnoreCase(normalized)
        || "/".equals(normalized)) {
      return "";
    }
    final String[] anchors = {
      "/src/main/java/", "/src/test/java/", "/src/main/kotlin/", "/src/test/kotlin/"
    };
    for (final String anchor : anchors) {
      final int idx = normalized.indexOf(anchor);
      if (idx >= 0) {
        final String tail = normalized.substring(idx + anchor.length());
        final int slash = tail.lastIndexOf('/');
        if (slash <= 0) {
          return DEFAULT_PACKAGE;
        }
        final String pkgPath = tail.substring(0, slash).strip();
        if (pkgPath.isEmpty()) {
          return DEFAULT_PACKAGE;
        }
        return pkgPath.replace('/', '.');
      }
    }
    return "";
  }
}
