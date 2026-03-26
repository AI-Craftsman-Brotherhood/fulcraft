package com.craftsmanbro.fulcraft.infrastructure.formatter.impl;

import com.craftsmanbro.fulcraft.infrastructure.formatter.contract.TestCodeFormattingPort;
import com.craftsmanbro.fulcraft.infrastructure.formatter.model.TestCodeFormattingProfile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic formatter for generated Java test code. Ensures consistent import order, member
 * order, and whitespace.
 */
public class TestCodeFormatter implements TestCodeFormattingPort {

  private static final Pattern RECORD_CONTEXT_PATTERN = Pattern.compile("\\brecord\\b");

  private final JavaParser javaParser;

  private final TestCodeFormattingProfile defaultProfile;

  public TestCodeFormatter() {
    this(TestCodeFormattingProfile.deterministicDefaults());
  }

  public TestCodeFormatter(final TestCodeFormattingProfile defaultProfile) {
    final ParserConfiguration configuration = new ParserConfiguration();
    // BLEEDING_EDGE supports the latest language features including Java 21+
    configuration.setLanguageLevel(LanguageLevel.BLEEDING_EDGE);
    this.javaParser = new JavaParser(configuration);
    this.defaultProfile =
        defaultProfile == null ? TestCodeFormattingProfile.deterministicDefaults() : defaultProfile;
  }

  /**
   * Formats the given Java code string deterministically.
   *
   * @param code The raw Java code.
   * @return The formatted Java code.
   */
  @Override
  public String format(final String code) {
    return format(code, defaultProfile);
  }

  @Override
  public String format(final String code, final TestCodeFormattingProfile profile) {
    if (code == null || code.isBlank()) {
      return code;
    }
    final TestCodeFormattingProfile effectiveProfile =
        profile == null ? TestCodeFormattingProfile.deterministicDefaults() : profile;
    final ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
    if (!parseResult.isSuccessful()) {
      // If parsing fails, return original code to avoid losing data
      return code;
    }
    final Optional<CompilationUnit> parsedCompilationUnit = parseResult.getResult();
    if (parsedCompilationUnit.isEmpty()) {
      return code;
    }
    final CompilationUnit cu = parsedCompilationUnit.get();
    if (effectiveProfile.sortImports()) {
      sortImports(cu);
    }
    if (effectiveProfile.sortMembers()) {
      sortMembers(cu);
    }
    return print(cu, effectiveProfile);
  }

  private void sortImports(final CompilationUnit cu) {
    final NodeList<ImportDeclaration> imports = cu.getImports();
    if (imports == null || imports.isEmpty()) {
      return;
    }
    final List<ImportDeclaration> sortedImports =
        imports.stream().sorted(Comparator.comparing(this::getImportSortKey)).toList();
    cu.setImports(new NodeList<>(sortedImports));
  }

  private String getImportSortKey(final ImportDeclaration imp) {
    final String name = imp.getNameAsString();
    if (imp.isStatic()) {
      return "0:" + name;
    }
    if (name.startsWith("java.")) {
      return "1:" + name;
    }
    if (name.startsWith("javax.")) {
      return "2:" + name;
    }
    if (name.startsWith("org.")) {
      return "3:" + name;
    }
    if (name.startsWith("com.")) {
      return "4:" + name;
    }
    return "5:" + name;
  }

  private void sortMembers(final CompilationUnit cu) {
    for (final TypeDeclaration<?> type : cu.getTypes()) {
      if (type instanceof ClassOrInterfaceDeclaration classDecl) {
        sortClassMembers(classDecl);
      }
    }
  }

  private void sortClassMembers(final ClassOrInterfaceDeclaration classDecl) {
    final List<BodyDeclaration<?>> members = classDecl.getMembers();
    // Sort logic:
    // 1. Fields
    // 2. Constructors
    // 3. @BeforeAll
    // 4. @BeforeEach
    // 5. @AfterEach
    // 6. @AfterAll
    // 7. @Test methods (sorted by name)
    // 8. Other methods (helpers, private)
    // 9. Inner Classes
    final List<BodyDeclaration<?>> sortedMembers =
        members.stream()
            .sorted(Comparator.comparingInt(this::getMemberRank).thenComparing(this::getMemberName))
            .toList();
    classDecl.setMembers(new NodeList<>(sortedMembers));
    for (final BodyDeclaration<?> member : sortedMembers) {
      if (member instanceof ClassOrInterfaceDeclaration innerClass) {
        sortClassMembers(innerClass);
      }
    }
  }

  private int getMemberRank(final BodyDeclaration<?> member) {
    if (member instanceof FieldDeclaration) {
      return 1;
    }
    if (member instanceof ConstructorDeclaration) {
      return 2;
    }
    if (member instanceof MethodDeclaration method) {
      if (hasAnnotation(method, "BeforeAll")) {
        return 3;
      }
      if (hasAnnotation(method, "BeforeEach")) {
        return 4;
      }
      if (hasAnnotation(method, "AfterEach")) {
        return 5;
      }
      if (hasAnnotation(method, "AfterAll")) {
        return 6;
      }
      if (hasAnnotation(method, "Test") || hasAnnotation(method, "ParameterizedTest")) {
        return 7;
      }
      // Helper methods
      return 8;
    }
    if (member instanceof ClassOrInterfaceDeclaration) {
      return 9;
    }
    return 10;
  }

  private String getMemberName(final BodyDeclaration<?> member) {
    if (member instanceof MethodDeclaration method) {
      return method.getNameAsString();
    }
    if (member instanceof FieldDeclaration field && !field.getVariables().isEmpty()) {
      return field.getVariable(0).getNameAsString();
    }
    if (member instanceof ClassOrInterfaceDeclaration clazz) {
      return clazz.getNameAsString();
    }
    return "";
  }

  private boolean hasAnnotation(final MethodDeclaration method, final String annotationName) {
    return method.getAnnotations().stream()
        .map(NodeWithName::getNameAsString)
        .anyMatch(name -> name.equals(annotationName) || name.endsWith("." + annotationName));
  }

  private String print(final CompilationUnit cu, final TestCodeFormattingProfile profile) {
    // Use default printing for now to avoid compilation issues with ConfigOption
    String printed = cu.toString();
    if (profile.normalizeEmptyBraces()) {
      printed = normalizeEmptyBraces(printed);
    }
    if (profile.keepEmptyRecordCompactBraces() && hasEmptyRecordDeclarations(cu)) {
      printed = compactEmptyRecordBraces(printed);
    }
    if (profile.ensureTrailingNewline()) {
      return printed.endsWith("\n") ? printed : printed + "\n";
    }
    while (printed.endsWith("\n")) {
      printed = printed.substring(0, printed.length() - 1);
    }
    return printed;
  }

  private boolean hasEmptyRecordDeclarations(final CompilationUnit cu) {
    return cu.findAll(RecordDeclaration.class).stream()
        .anyMatch(recordDecl -> recordDecl.getMembers().isEmpty());
  }

  private String normalizeEmptyBraces(final String source) {
    final StringBuilder normalized = new StringBuilder(source.length());
    int index = 0;
    while (index < source.length()) {
      if (startsLineComment(source, index)) {
        index = appendLineComment(source, index, normalized);
        continue;
      }
      if (startsBlockComment(source, index)) {
        index = appendBlockComment(source, index, normalized);
        continue;
      }
      if (startsTextBlock(source, index)) {
        index = appendTextBlock(source, index, normalized);
        continue;
      }

      final char current = source.charAt(index);
      if (current == '"' || current == '\'') {
        index = appendQuotedLiteral(source, index, normalized, current);
        continue;
      }
      if (current == '{') {
        final int emptyBraceEnd = findEmptyBraceEnd(source, index + 1);
        if (emptyBraceEnd >= 0) {
          normalized.append("{ }");
          index = emptyBraceEnd + 1;
          continue;
        }
      }
      normalized.append(current);
      index++;
    }
    return normalized.toString();
  }

  private String compactEmptyRecordBraces(final String source) {
    final StringBuilder compacted = new StringBuilder(source.length());
    final StringBuilder codeContext = new StringBuilder();
    int index = 0;
    while (index < source.length()) {
      if (startsLineComment(source, index)) {
        index = appendLineComment(source, index, compacted);
        continue;
      }
      if (startsBlockComment(source, index)) {
        index = appendBlockComment(source, index, compacted);
        continue;
      }
      if (startsTextBlock(source, index)) {
        index = appendTextBlock(source, index, compacted);
        continue;
      }

      final char current = source.charAt(index);
      if (current == '"' || current == '\'') {
        index = appendQuotedLiteral(source, index, compacted, current);
        continue;
      }
      if (startsNormalizedEmptyBracePair(source, index)
          && RECORD_CONTEXT_PATTERN.matcher(codeContext).find()) {
        compacted.append("{}");
        codeContext.setLength(0);
        index += 3;
        continue;
      }
      compacted.append(current);
      updateCodeContext(codeContext, current);
      index++;
    }
    return compacted.toString();
  }

  private boolean startsLineComment(final String source, final int index) {
    return index + 1 < source.length()
        && source.charAt(index) == '/'
        && source.charAt(index + 1) == '/';
  }

  private boolean startsBlockComment(final String source, final int index) {
    return index + 1 < source.length()
        && source.charAt(index) == '/'
        && source.charAt(index + 1) == '*';
  }

  private boolean startsTextBlock(final String source, final int index) {
    return index + 2 < source.length()
        && source.charAt(index) == '"'
        && source.charAt(index + 1) == '"'
        && source.charAt(index + 2) == '"';
  }

  private boolean startsNormalizedEmptyBracePair(final String source, final int index) {
    return index + 2 < source.length()
        && source.charAt(index) == '{'
        && source.charAt(index + 1) == ' '
        && source.charAt(index + 2) == '}';
  }

  private int findEmptyBraceEnd(final String source, final int index) {
    int cursor = index;
    while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
      cursor++;
    }
    return cursor < source.length() && source.charAt(cursor) == '}' ? cursor : -1;
  }

  private int appendLineComment(
      final String source, final int startIndex, final StringBuilder output) {
    int index = startIndex;
    while (index < source.length()) {
      final char current = source.charAt(index);
      output.append(current);
      index++;
      if (current == '\n') {
        break;
      }
    }
    return index;
  }

  private int appendBlockComment(
      final String source, final int startIndex, final StringBuilder output) {
    int index = startIndex;
    while (index < source.length()) {
      final char current = source.charAt(index);
      output.append(current);
      index++;
      if (current == '*' && index < source.length() && source.charAt(index) == '/') {
        output.append('/');
        return index + 1;
      }
    }
    return index;
  }

  private int appendTextBlock(
      final String source, final int startIndex, final StringBuilder output) {
    output.append("\"\"\"");
    int index = startIndex + 3;
    while (index < source.length()) {
      if (startsTextBlock(source, index)) {
        output.append("\"\"\"");
        return index + 3;
      }
      final char current = source.charAt(index);
      output.append(current);
      if (current == '\\' && index + 1 < source.length()) {
        output.append(source.charAt(index + 1));
        index += 2;
        continue;
      }
      index++;
    }
    return index;
  }

  private int appendQuotedLiteral(
      final String source, final int startIndex, final StringBuilder output, final char delimiter) {
    output.append(delimiter);
    int index = startIndex + 1;
    while (index < source.length()) {
      final char current = source.charAt(index);
      output.append(current);
      if (current == '\\' && index + 1 < source.length()) {
        output.append(source.charAt(index + 1));
        index += 2;
        continue;
      }
      index++;
      if (current == delimiter) {
        break;
      }
    }
    return index;
  }

  private void updateCodeContext(final StringBuilder codeContext, final char current) {
    if (current == '{' || current == '}' || current == ';') {
      codeContext.setLength(0);
      return;
    }
    codeContext.append(current);
    if (codeContext.length() > 256) {
      codeContext.delete(0, codeContext.length() - 256);
    }
  }
}
