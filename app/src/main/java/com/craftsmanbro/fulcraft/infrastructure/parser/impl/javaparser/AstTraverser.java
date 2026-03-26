package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.metrics.impl.MetricsCalculator;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.AstUtils;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.ResultBuilder;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to traverse AST and populate AnalysisResult and AnalysisContext. Extracted from
 * JavaParserAnalyzer to satisfy SRP and reduce complexity.
 */
class AstTraverser {

  private final DependencyGraphBuilder dependencyGraphBuilder;

  AstTraverser(final DependencyGraphBuilder dependencyGraphBuilder) {
    this.dependencyGraphBuilder = dependencyGraphBuilder;
  }

  void traverse(
      final CompilationUnit cu,
      final AnalysisResult result,
      final Path srcRoot,
      final Path path,
      final List<String> importStrings,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    analyzeClasses(cu, result, srcRoot, path, importStrings, removedApiImports, context);
    analyzeAnonymousClasses(cu, result, srcRoot, path, importStrings, removedApiImports, context);
  }

  private void analyzeClasses(
      final CompilationUnit cu,
      final AnalysisResult result,
      final Path srcRoot,
      final Path path,
      final List<String> importStrings,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    cu.findAll(ClassOrInterfaceDeclaration.class)
        .forEach(
            c -> {
              final ClassInfo classInfo = buildClassInfo(c, srcRoot, path, importStrings);
              analyzeConstructors(c, classInfo, removedApiImports, context);
              analyzeMethods(c, classInfo, removedApiImports, context);
              analyzeFields(c, classInfo);
              classInfo.setMethodCount(classInfo.getMethods().size());
              result.getClasses().add(classInfo);
            });
  }

  private ClassInfo buildClassInfo(
      final ClassOrInterfaceDeclaration c,
      final Path srcRoot,
      final Path path,
      final List<String> imports) {
    final ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(c.getFullyQualifiedName().orElse(c.getNameAsString()));
    classInfo.setFilePath(srcRoot.relativize(path).toString());
    classInfo.setLoc(c.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0));
    classInfo.setInterface(c.isInterface());
    classInfo.setAbstract(c.isAbstract());
    classInfo.setAnonymous(false);
    classInfo.setNestedClass(c.isNestedType());
    classInfo.setHasNestedClasses(
        c.getMembers().stream()
            .anyMatch(
                m ->
                    m instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
                        || m instanceof com.github.javaparser.ast.body.EnumDeclaration
                        || m instanceof com.github.javaparser.ast.body.RecordDeclaration));
    classInfo.setExtendsTypes(
        c.getExtendedTypes().stream()
            .map(com.github.javaparser.ast.nodeTypes.NodeWithSimpleName::getNameAsString)
            .toList());
    classInfo.setImplementsTypes(
        c.getImplementedTypes().stream()
            .map(com.github.javaparser.ast.nodeTypes.NodeWithSimpleName::getNameAsString)
            .toList());
    classInfo.setFields(new ArrayList<>());
    classInfo.setAnnotations(
        c.getAnnotations().stream()
            .map(com.github.javaparser.ast.nodeTypes.NodeWithName::getNameAsString)
            .toList());
    classInfo.setImports(imports);
    classInfo.setMethods(new ArrayList<>());
    return classInfo;
  }

  private void analyzeConstructors(
      final ClassOrInterfaceDeclaration c,
      final ClassInfo classInfo,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    c.getConstructors()
        .forEach(
            cons -> {
              final MethodInfo methodInfo = buildMethodInfo(cons, removedApiImports);
              final String methodKey =
                  registerMethod(context, methodInfo, cons, classInfo.getFqn());
              dependencyGraphBuilder.collectCalledMethods(
                  cons, methodKey, classInfo.getFqn(), context);
              classInfo.addMethod(methodInfo);
            });
  }

  private void analyzeMethods(
      final ClassOrInterfaceDeclaration c,
      final ClassInfo classInfo,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    c.getMethods()
        .forEach(
            m -> {
              final MethodInfo methodInfo = buildMethodInfo(m, removedApiImports);
              final String methodKey = registerMethod(context, methodInfo, m, classInfo.getFqn());
              dependencyGraphBuilder.collectCalledMethods(
                  m, methodKey, classInfo.getFqn(), context);
              classInfo.addMethod(methodInfo);
            });
  }

  private void analyzeFields(final ClassOrInterfaceDeclaration c, final ClassInfo classInfo) {
    // Extract package name from the class FQN
    final String packageName = extractPackageName(classInfo.getFqn());
    final List<String> imports = classInfo.getImports();
    for (final FieldDeclaration field : c.getFields()) {
      final String visibility =
          field.getAccessSpecifier().asString().toLowerCase(java.util.Locale.ROOT);
      final boolean isStatic = field.isStatic();
      final boolean isFinal = field.isFinal();
      for (final VariableDeclarator declarator : field.getVariables()) {
        final String typeName = resolveTypeFqn(declarator, packageName, imports);
        final FieldInfo fi =
            ResultBuilder.fieldInfo(
                declarator.getNameAsString(), typeName, visibility, isStatic, isFinal);
        classInfo.addField(fi);
      }
    }
    // Analyze fields for mock hints (DI detection, interface detection, naming
    // heuristics)
    MockHintAnalyzer.analyzeFields(c, classInfo.getFields());
  }

  /**
   * Resolves the fully qualified name (FQN) of a field type. Falls back to package-based inference
   * if symbol resolution fails.
   */
  private String resolveTypeFqn(
      final VariableDeclarator declarator, final String packageName, final List<String> imports) {
    try {
      final var resolvedType = declarator.getType().resolve();
      return resolvedType.describe();
    } catch (Exception e) {
      // Fallback: derive FQN from imports or package
      final String simpleName = declarator.getType().asString();
      return deriveTypeFqn(simpleName, packageName, imports);
    }
  }

  /**
   * Derives the FQN for a type when symbol resolution fails. Strategy: check imports first, then
   * assume same package for non-primitive types.
   */
  private String deriveTypeFqn(
      final String simpleName, final String packageName, final List<String> imports) {
    // Handle generic types: extract base type (e.g., "List<String>" -> "List")
    final String baseType =
        simpleName.contains("<") ? simpleName.substring(0, simpleName.indexOf('<')) : simpleName;
    // If it already looks like a fully qualified name (e.g., "com.foo.Bar"), keep
    // as-is.
    if (isLikelyFullyQualified(baseType)) {
      return simpleName;
    }
    // Primitives and java.lang types: keep as-is
    if (isPrimitiveOrStandardType(baseType)) {
      return simpleName;
    }
    // Check imports for matching type
    final String importMatch = findImportMatch(baseType, imports);
    if (importMatch != null) {
      return buildGenericFqn(importMatch, simpleName);
    }
    // Not imported: assume same package
    if (packageName != null && !packageName.isEmpty()) {
      return buildGenericFqn(packageName + "." + baseType, simpleName);
    }
    return simpleName;
  }

  /**
   * Finds an import statement matching the given base type. Returns the matching import FQN
   * (without generic parameters) or null if not found.
   */
  private String findImportMatch(final String baseType, final List<String> imports) {
    if (imports == null) {
      return null;
    }
    for (final String imp : imports) {
      if (imp.startsWith("static ")) {
        continue;
      }
      if (imp.endsWith("." + baseType)) {
        return imp;
      }
      // Wildcard imports (e.g., "java.util.*") are skipped - too ambiguous
    }
    return null;
  }

  /**
   * Builds the full FQN for a type, preserving generic parameters if present. Example:
   * buildGenericFqn("java.util.List", "List<String>") -> "java.util.List<String>"
   */
  private String buildGenericFqn(final String baseFqn, final String simpleName) {
    if (simpleName.contains("<")) {
      return baseFqn + simpleName.substring(simpleName.indexOf('<'));
    }
    return baseFqn;
  }

  /** Checks if a type is a primitive, boxed primitive, or common java.lang type. */
  private boolean isPrimitiveOrStandardType(final String typeName) {
    return switch (typeName) {
      case "boolean",
              "byte",
              "char",
              "short",
              "int",
              "long",
              "float",
              "double",
              "void",
              "Boolean",
              "Byte",
              "Character",
              "Short",
              "Integer",
              "Long",
              "Float",
              "Double",
              "Void",
              "String",
              "Object",
              "Class",
              "Enum",
              "Throwable",
              "Exception",
              "RuntimeException",
              "Error",
              "Number",
              "StringBuilder",
              "StringBuffer" ->
          true;
      default -> false;
    };
  }

  private boolean isLikelyFullyQualified(final String baseType) {
    if (baseType.isEmpty()) {
      return false;
    }
    return baseType.contains(".") && Character.isLowerCase(baseType.charAt(0));
  }

  /** Extracts the package name from a fully qualified class name. */
  private String extractPackageName(final String fqn) {
    if (fqn == null || !fqn.contains(".")) {
      return "";
    }
    return fqn.substring(0, fqn.lastIndexOf('.'));
  }

  private MethodInfo buildMethodInfo(
      final ConstructorDeclaration cons, final RemovedApiDetector.RemovedApiImportInfo imports) {
    return ResultBuilder.methodInfo()
        .name(cons.getNameAsString())
        .signature(cons.getSignature().asString())
        .loc(cons.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0))
        .visibility(cons.getAccessSpecifier().asString().toLowerCase(java.util.Locale.ROOT))
        .cyclomaticComplexity(MetricsCalculator.calculateComplexity(cons))
        .usesRemovedApis(RemovedApiChecker.usesRemovedApis(cons, imports))
        .parameterCount(cons.getParameters().size())
        .maxNestingDepth(MetricsCalculator.calculateMaxNestingDepth(cons))
        .thrownExceptions(ExceptionCollector.collectThrownExceptions(cons))
        .annotations(
            cons.getAnnotations().stream()
                .map(com.github.javaparser.ast.nodeTypes.NodeWithName::getNameAsString)
                .toList())
        .build();
  }

  private MethodInfo buildMethodInfo(
      final MethodDeclaration m, final RemovedApiDetector.RemovedApiImportInfo imports) {
    final MethodInfo methodInfo =
        ResultBuilder.methodInfo()
            .name(m.getNameAsString())
            .signature(m.getSignature().asString())
            .loc(m.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0))
            .visibility(m.getAccessSpecifier().asString().toLowerCase(java.util.Locale.ROOT))
            .cyclomaticComplexity(MetricsCalculator.calculateComplexity(m))
            .usesRemovedApis(RemovedApiChecker.usesRemovedApis(m, imports))
            .parameterCount(m.getParameters().size())
            .maxNestingDepth(MetricsCalculator.calculateMaxNestingDepth(m))
            .thrownExceptions(ExceptionCollector.collectThrownExceptions(m))
            .annotations(
                m.getAnnotations().stream()
                    .map(com.github.javaparser.ast.nodeTypes.NodeWithName::getNameAsString)
                    .toList())
            .isStatic(m.isStatic())
            .build();
    Logger.debug(
        "[STATIC-FLAG][JP] " + m.getSignature().asString() + " isStatic=" + methodInfo.isStatic());
    return methodInfo;
  }

  private void analyzeAnonymousClasses(
      final CompilationUnit cu,
      final AnalysisResult result,
      final Path srcRoot,
      final Path path,
      final List<String> importStrings,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    cu.findAll(ObjectCreationExpr.class, oce -> oce.getAnonymousClassBody().isPresent())
        .forEach(
            oce -> {
              final ClassInfo anonInfo = buildAnonymousClassInfo(oce, srcRoot, path, importStrings);
              analyzeAnonymousMethods(oce, anonInfo, removedApiImports, context);
              anonInfo.setMethodCount(anonInfo.getMethods().size());
              result.getClasses().add(anonInfo);
            });
  }

  private ClassInfo buildAnonymousClassInfo(
      final ObjectCreationExpr oce,
      final Path srcRoot,
      final Path path,
      final List<String> imports) {
    final ClassInfo anonInfo = new ClassInfo();
    anonInfo.setFilePath(srcRoot.relativize(path).toString());
    anonInfo.setLoc(oce.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0));
    anonInfo.setInterface(false);
    anonInfo.setAbstract(false);
    anonInfo.setAnonymous(true);
    anonInfo.setExtendsTypes(new ArrayList<>());
    anonInfo.setImplementsTypes(new ArrayList<>());
    anonInfo.setFields(new ArrayList<>());
    anonInfo.setAnnotations(new ArrayList<>());
    anonInfo.setImports(imports);
    anonInfo.setMethods(new ArrayList<>());
    final int line = oce.getBegin().map(p -> p.line).orElse(-1);
    final String enclosing =
        AstUtils.findAncestor(oce, ClassOrInterfaceDeclaration.class)
            .map(cls -> cls.getFullyQualifiedName().orElse(cls.getNameAsString()))
            .orElse("anonymous");
    anonInfo.setFqn(String.format("%s$anonymous@%d", enclosing, line));
    anonInfo.addExtendsType(oce.getType().getNameWithScope());
    return anonInfo;
  }

  private void analyzeAnonymousMethods(
      final ObjectCreationExpr oce,
      final ClassInfo anonInfo,
      final RemovedApiDetector.RemovedApiImportInfo removedApiImports,
      final AnalysisContext context) {
    oce.getAnonymousClassBody()
        .ifPresent(
            body -> {
              body.stream()
                  .filter(MethodDeclaration.class::isInstance)
                  .map(MethodDeclaration.class::cast)
                  .forEach(
                      m -> {
                        final MethodInfo methodInfo = buildMethodInfo(m, removedApiImports);
                        final String methodKey =
                            registerMethod(context, methodInfo, m, anonInfo.getFqn());
                        dependencyGraphBuilder.collectCalledMethods(
                            m, methodKey, anonInfo.getFqn(), context);
                        anonInfo.addMethod(methodInfo);
                      });
              body.stream()
                  .filter(ConstructorDeclaration.class::isInstance)
                  .map(ConstructorDeclaration.class::cast)
                  .forEach(
                      cons -> {
                        final MethodInfo methodInfo = buildMethodInfo(cons, removedApiImports);
                        final String methodKey =
                            registerMethod(context, methodInfo, cons, anonInfo.getFqn());
                        dependencyGraphBuilder.collectCalledMethods(
                            cons, methodKey, anonInfo.getFqn(), context);
                        anonInfo.addMethod(methodInfo);
                      });
            });
  }

  private String registerMethod(
      final AnalysisContext context,
      final MethodInfo methodInfo,
      final Node node,
      final String classFqn) {
    final String key = DependencyGraphBuilder.methodKey(classFqn, methodInfo.getSignature());
    methodInfo.setCalledMethods(new ArrayList<>());
    methodInfo.setPartOfCycle(false);
    methodInfo.setDeadCode(false);
    methodInfo.setDuplicate(false);
    methodInfo.setDuplicateGroup(null);
    methodInfo.setCodeHash(CodeHasher.computeCodeHash(node).orElse(null));
    methodInfo.setSourceCode(node.toString());
    context.getMethodInfos().put(key, methodInfo);
    context.getMethodVisibility().put(key, methodInfo.getVisibility());
    context.getMethodHasBody().put(key, CodeHasher.hasBody(node));
    context.getMethodClass().put(key, classFqn);
    if (methodInfo.getCodeHash() != null) {
      context.getMethodCodeHash().put(key, methodInfo.getCodeHash());
    }
    context.getOrCreateCallGraphEntry(key);
    context.getIncomingCounts().putIfAbsent(key, 0);
    return key;
  }
}
