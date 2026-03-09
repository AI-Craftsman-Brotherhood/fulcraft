package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.AstUtils;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicReasonCode;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionRuleId;
import com.craftsmanbro.fulcraft.plugins.analysis.model.TrustLevel;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves dynamic features where the target can be determined statically using AST analysis.
 * Replaces previous Regex-based implementation.
 */
public class DynamicResolver {

  private final List<DynamicResolution> resolutions =
      Collections.synchronizedList(new ArrayList<>());

  private final Set<String> knownMethods = new HashSet<>();

  private final Set<String> knownFields = new HashSet<>();

  private final Set<String> knownClasses = new HashSet<>();

  private final Map<String, ClassInfo> fqnToClassInfo = new HashMap<>();

  private final Map<String, List<String>> interfaceToImplementations = new HashMap<>();

  private final Map<String, String> staticStringConstantsByFqn = new HashMap<>();

  private final Map<String, String> staticStringConstantsBySimple = new HashMap<>();

  private final Map<String, String> enumStringConstantsByFqn = new HashMap<>();

  private final Map<String, String> enumStringConstantsBySimple = new HashMap<>();

  private final Map<String, Set<String>> enumPublicStringFieldsByFqn = new HashMap<>();

  private final Map<String, String> enumSimpleNameToFqn = new HashMap<>();

  private final Set<String> ambiguousSimpleConstants = new HashSet<>();

  private final Set<Path> processedSourceFiles = new HashSet<>();

  private ProjectSymbolIndex projectSymbolIndex;

  private Map<String, String> externalConfigValues = Map.of();

  // Inter-procedural resolution config
  private boolean enableInterproceduralResolution;

  private int callsiteLimit = 20;

  private boolean debugMode;

  private boolean enableCandidateEnumeration;

  // Constants for inter-procedural resolution
  private static final double INTERPROCEDURAL_SINGLE_CONFIDENCE = 0.7;

  private static final double EXPERIMENTAL_CANDIDATE_CONFIDENCE = 0.3;

  private static final String PATTERN_CLASS_FORNAME = "Class.forName";

  private static final String EVIDENCE_PATTERN = "pattern";

  private static final String EVIDENCE_PROVENANCE = "provenance";

  private static final String EVIDENCE_CANDIDATE_COUNT = "candidate_count";

  private static final String EVIDENCE_TRUNCATED = "truncated";

  private static final String EVIDENCE_LITERAL = "literal";

  private static final String EVIDENCE_TARGET_CLASS = "target_class";

  private static final String EVIDENCE_METHOD_LITERAL = "method_literal";

  private static final String EVIDENCE_FIELD_LITERAL = "field_literal";

  private static final String EVIDENCE_PARAM_COUNT = "param_count";

  private static final String EVIDENCE_ARITY_MATCH = "arity_match";

  private static final String EVIDENCE_OVERLOAD_COUNT = "overload_count";

  private static final String EVIDENCE_VERIFIED = "verified";

  private static final String EVIDENCE_SOURCE_METHOD = "source_method";

  private static final String EVIDENCE_SERVICE_CLASS = "service_class";

  private static final String EVIDENCE_PROVIDERS_FOUND = "providers_found";

  private static final String EVIDENCE_EXPLICIT_SPI = "explicit_spi";

  private static final String PROVENANCE_BRANCH_CANDIDATES = "branch_candidates";

  private static final String PROVENANCE_INTERPROCEDURAL_CANDIDATES = "interprocedural_candidates";

  private static final String PROVENANCE_INTERPROCEDURAL_SINGLE = "interprocedural_single";

  private static final String PROVENANCE_EXPERIMENTAL = "experimental_candidates";

  private static final String PROVENANCE_LITERAL = "literal";

  private static final String PROVENANCE_INFERRED = "inferred";

  private static final String LOG_RESOLUTION_PREFIX = "[DynamicResolver] Resolution at ";

  private static final String LOG_TYPE_PREFIX = "  Type: ";

  private static final String LOG_CANDIDATES_PREFIX = "  Candidates: ";

  private static final String LOG_TRUNCATED_SUFFIX = "... (truncated)";

  private static final String LOG_CONFIDENCE_PREFIX = "  Confidence: ";

  private static final String LOG_REASON_CODE_PREFIX = "  ReasonCode: ";

  private static final String LOG_RESOLVED_PREFIX = "  Resolved: ";

  private static final String LOG_OVERLOADS_PREFIX = "  Overloads: ";

  private static final String LOG_SERVICE_PREFIX = "  Service: ";

  private static final String LOG_PROVIDERS_PREFIX = "  Providers: ";

  // Lightweight parser (no symbol solver needed for basic literal detection)
  private final JavaParser javaParser = new JavaParser();

  /** Resolve dynamic features from analysis result (default: inter-procedural OFF). */
  public void resolve(final AnalysisResult result, final Path projectRoot) {
    resolve(result, projectRoot, false, 20, false, false);
  }

  public void setProjectSymbolIndex(final ProjectSymbolIndex projectSymbolIndex) {
    this.projectSymbolIndex = projectSymbolIndex;
  }

  public void setExternalConfigValues(final Map<String, String> externalConfigValues) {
    this.externalConfigValues =
        externalConfigValues == null ? Map.of() : new HashMap<>(externalConfigValues);
  }

  /** Resolve dynamic features with inter-procedural resolution config. */
  public void resolve(
      final AnalysisResult result,
      final Path projectRoot,
      final boolean enableInterprocedural,
      final int callsiteLimitValue) {
    resolve(result, projectRoot, enableInterprocedural, callsiteLimitValue, false, false);
  }

  /** Resolve dynamic features with inter-procedural resolution config and debug mode. */
  public void resolve(
      final AnalysisResult result,
      final Path projectRoot,
      final boolean enableInterprocedural,
      final int callsiteLimitValue,
      final boolean debugMode) {
    resolve(result, projectRoot, enableInterprocedural, callsiteLimitValue, debugMode, false);
  }

  /**
   * Resolve dynamic features with inter-procedural resolution config, debug mode, and experimental
   * candidate enumeration.
   */
  public void resolve(
      final AnalysisResult result,
      final Path projectRoot,
      final boolean enableInterprocedural,
      final int callsiteLimitValue,
      final boolean debugMode,
      final boolean enableCandidateEnumeration) {
    resetState();
    this.enableInterproceduralResolution = enableInterprocedural;
    this.callsiteLimit = callsiteLimitValue;
    this.debugMode = debugMode;
    this.enableCandidateEnumeration = enableCandidateEnumeration;
    // Build known methods/classes set for verification
    buildKnownSymbols(result);
    // Build lookup maps for SPI resolution
    buildClassMaps(result);
    // Scan source files using AST
    for (final ClassInfo classInfo : result.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      resolveFromClass(classInfo, projectRoot);
    }
    Logger.debug(
        MessageSource.getMessage(
            "analysis.dynamic_resolver.resolved_summary",
            resolutions.size(),
            enableInterproceduralResolution ? "ON" : "OFF"));
  }

  private void resetState() {
    // Clear per-run state to keep reused resolver instances deterministic.
    resolutions.clear();
    knownMethods.clear();
    knownFields.clear();
    knownClasses.clear();
    fqnToClassInfo.clear();
    interfaceToImplementations.clear();
    staticStringConstantsByFqn.clear();
    staticStringConstantsBySimple.clear();
    enumStringConstantsByFqn.clear();
    enumStringConstantsBySimple.clear();
    enumPublicStringFieldsByFqn.clear();
    enumSimpleNameToFqn.clear();
    ambiguousSimpleConstants.clear();
    processedSourceFiles.clear();
  }

  private void buildKnownSymbols(final AnalysisResult result) {
    for (final ClassInfo classInfo : result.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      registerClassSymbols(classInfo);
    }
  }

  private void registerClassSymbols(final ClassInfo classInfo) {
    registerClassName(classInfo);
    registerFieldSymbols(classInfo);
    registerMethodSymbols(classInfo);
  }

  private void registerClassName(final ClassInfo classInfo) {
    if (classInfo.getFqn() != null) {
      knownClasses.add(classInfo.getFqn());
    }
  }

  private void registerFieldSymbols(final ClassInfo classInfo) {
    for (final FieldInfo field : classInfo.getFields()) {
      if (field == null || field.getName() == null) {
        continue;
      }
      knownFields.add(classInfo.getFqn() + "#" + field.getName());
    }
  }

  private void registerMethodSymbols(final ClassInfo classInfo) {
    for (final MethodInfo method : classInfo.getMethods()) {
      final String sig = classInfo.getFqn() + "#" + method.getName();
      knownMethods.add(sig);
      if (method.getSignature() != null) {
        knownMethods.add(classInfo.getFqn() + "#" + method.getSignature());
      }
    }
  }

  private void buildClassMaps(final AnalysisResult result) {
    fqnToClassInfo.clear();
    interfaceToImplementations.clear();
    for (final ClassInfo info : result.getClasses()) {
      if (info == null || info.getFqn() == null) {
        continue;
      }
      fqnToClassInfo.put(info.getFqn(), info);
      // Map interfaces/superclasses to this implementation
      final List<String> parents = new ArrayList<>();
      parents.addAll(info.getImplementsTypes());
      parents.addAll(info.getExtendsTypes());
      for (final String parent : parents) {
        final String parentKey = parent;
        // Note: parent might be SimpleName, but we store as is.
        // Matching logic will handle simple/FQN discrepancies.
        interfaceToImplementations
            .computeIfAbsent(parentKey, k -> new ArrayList<>())
            .add(info.getFqn());
        // Also register simple name variant if parent is FQN
        if (parent.contains(".")) {
          final String simple = parent.substring(parent.lastIndexOf('.') + 1);
          interfaceToImplementations
              .computeIfAbsent(simple, k -> new ArrayList<>())
              .add(info.getFqn());
        }
      }
    }
  }

  private void registerStaticStringConstant(
      final String ownerFqn, final String fieldName, final String value) {
    if (ownerFqn == null || fieldName == null || value == null) {
      return;
    }
    final String fqnKey = ownerFqn + "." + fieldName;
    staticStringConstantsByFqn.put(fqnKey, value);
    registerSimpleConstant(fieldName, value, staticStringConstantsBySimple);
  }

  private void registerEnumStringConstant(
      final String enumFqn, final String constantName, final String value) {
    if (enumFqn == null || constantName == null || value == null) {
      return;
    }
    final String fqnKey = enumFqn + "." + constantName;
    enumStringConstantsByFqn.put(fqnKey, value);
    registerSimpleConstant(constantName, value, enumStringConstantsBySimple);
  }

  private void registerSimpleConstant(
      final String name, final String value, final Map<String, String> targetMap) {
    if (ambiguousSimpleConstants.contains(name)) {
      return;
    }
    if (staticStringConstantsBySimple.containsKey(name)
        || enumStringConstantsBySimple.containsKey(name)) {
      staticStringConstantsBySimple.remove(name);
      enumStringConstantsBySimple.remove(name);
      ambiguousSimpleConstants.add(name);
      return;
    }
    targetMap.put(name, value);
  }

  private void resolveFromClass(final ClassInfo classInfo, final Path projectRoot) {
    final String filePath = classInfo.getFilePath();
    if (filePath == null) {
      return;
    }
    // Try to read source file
    final Path sourceFile = resolveSourceFile(projectRoot, filePath);
    if (sourceFile == null || !Files.exists(sourceFile)) {
      return;
    }
    try {
      final com.github.javaparser.ParseResult<CompilationUnit> parseResult =
          javaParser.parse(sourceFile);
      if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
        final CompilationUnit cu = parseResult.getResult().get();
        final DynamicVisitor visitor =
            new DynamicVisitor(filePath, classInfo.getFqn(), projectRoot);
        if (processedSourceFiles.add(sourceFile)) {
          visitor.collectStaticConstants(cu);
        }
        // First pass: collect intra-class call sites for inter-procedural resolution
        if (enableInterproceduralResolution) {
          cu.findAll(ClassOrInterfaceDeclaration.class)
              .forEach(visitor::collectIntraClassCallSites);
        }
        // Second pass: resolve dynamic features
        cu.accept(visitor, null);
      } else {
        Logger.debug(
            MessageSource.getMessage("analysis.dynamic_resolver.parse_failed", sourceFile));
      }
    } catch (java.io.IOException e) {
      Logger.debug(
          MessageSource.getMessage("analysis.dynamic_resolver.read_source_failed", sourceFile));
    }
  }

  private Path resolveSourceFile(final Path projectRoot, final String filePath) {
    // Try direct path
    final Path direct = projectRoot.resolve(filePath);
    if (Files.exists(direct)) {
      return direct;
    }
    // Try src/main/java prefix
    final Path srcMain = projectRoot.resolve("src/main/java").resolve(filePath);
    if (Files.exists(srcMain)) {
      return srcMain;
    }
    // Try converting package to path
    final String pathFromFqn = filePath.replace('.', '/') + ".java";
    final Path fromFqn = projectRoot.resolve("src/main/java").resolve(pathFromFqn);
    if (Files.exists(fromFqn)) {
      return fromFqn;
    }
    return null;
  }

  private class DynamicVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {

    private final String filePath;

    private final String classFqn;

    private final Path projectRoot;

    // Track local string constants: variableName -> single value
    private final Map<String, String> localConstants = new HashMap<>();

    // Track local Class references: variableName -> resolved class name/FQN
    private final Map<String, String> localClassTargets = new HashMap<>();

    // Track branch candidates: variableName -> set of possible values from
    // if/switch
    private final Map<String, Set<String>> branchCandidates = new HashMap<>();

    // Track variables that exceeded candidate limit
    private final Set<String> truncatedVariables = new HashSet<>();

    // Inter-procedural resolution: track call sites within this class
    // Key: "methodName#paramCount", Value: list of call sites
    private final Map<String, List<CallSiteInfo>> intraClassCallSites = new HashMap<>();

    // Track method parameters for the current method: paramName -> paramIndex
    private final Map<String, Integer> methodParamNames = new HashMap<>();

    // Current method name and param count for context
    private String currentMethodName;

    private int currentMethodParamCount;

    private String currentMethodSignature;

    // Record for call site information
    private record CallSiteInfo(MethodCallExpr callExpr, List<Expression> arguments, int line) {}

    private record CallSiteSlice(List<CallSiteInfo> sites, boolean truncated) {}

    // Constants for branch resolution
    private static final int MAX_CANDIDATES = 8;

    private static final int MAX_EXPR_DEPTH = 10;

    private static final double BRANCH_CONFIDENCE = 0.6;

    private static final int MAX_INTERPROCEDURAL_DEPTH = 1;

    public DynamicVisitor(final String filePath, final String classFqn, final Path projectRoot) {
      this.filePath = filePath;
      this.classFqn = classFqn;
      this.projectRoot = projectRoot;
    }

    private void collectStaticConstants(final CompilationUnit cu) {
      collectStaticStringConstants(cu);
      collectEnumStringConstants(cu);
    }

    private void collectStaticStringConstants(final CompilationUnit cu) {
      for (final com.github.javaparser.ast.body.FieldDeclaration fieldDecl :
          cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
        if (isStaticStringConstantField(fieldDecl)) {
          final String ownerFqn = resolveFieldOwnerFqn(fieldDecl, cu);
          if (ownerFqn != null && !ownerFqn.isBlank()) {
            registerFieldStringConstants(ownerFqn, fieldDecl);
          }
        }
      }
    }

    private boolean isStaticStringConstantField(
        final com.github.javaparser.ast.body.FieldDeclaration fieldDecl) {
      if (!fieldDecl.isPublic() || !fieldDecl.isStatic() || !fieldDecl.isFinal()) {
        return false;
      }
      return isStringType(fieldDecl.getElementType().asString());
    }

    private String resolveFieldOwnerFqn(
        final com.github.javaparser.ast.body.FieldDeclaration fieldDecl, final CompilationUnit cu) {
      final var owner =
          AstUtils.findAncestor(fieldDecl, com.github.javaparser.ast.body.TypeDeclaration.class)
              .orElse(null);
      return resolveTypeFqn(owner, cu);
    }

    private void registerFieldStringConstants(
        final String ownerFqn, final com.github.javaparser.ast.body.FieldDeclaration fieldDecl) {
      for (final var var : fieldDecl.getVariables()) {
        final var initializer = var.getInitializer();
        if (initializer.isPresent()) {
          final String literalValue = resolveLiteralConcat(initializer.get(), 0);
          if (literalValue != null) {
            registerStaticStringConstant(ownerFqn, var.getNameAsString(), literalValue);
          }
        }
      }
    }

    private void collectEnumStringConstants(final CompilationUnit cu) {
      for (final com.github.javaparser.ast.body.EnumDeclaration enumDecl :
          cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class)) {
        final String enumFqn = resolveTypeFqn(enumDecl, cu);
        if (enumFqn == null || enumFqn.isBlank()) {
          continue;
        }
        enumSimpleNameToFqn.put(enumDecl.getNameAsString(), enumFqn);
        final Map<String, EnumStringFieldInfo> stringFields = collectEnumStringFields(enumDecl);
        enumPublicStringFieldsByFqn.put(enumFqn, extractPublicFieldNames(stringFields));
        final Map<String, Integer> fieldParamBindings =
            collectEnumConstructorFieldBindings(enumDecl, stringFields.keySet());
        final String toStringFieldName = resolveEnumToStringField(enumDecl, stringFields.keySet());
        for (final var entry : enumDecl.getEntries()) {
          final String value =
              resolveEnumEntryStringValue(
                  entry, stringFields, fieldParamBindings, toStringFieldName);
          if (value != null) {
            registerEnumStringConstant(enumFqn, entry.getNameAsString(), value);
          }
        }
      }
    }

    private record EnumStringFieldInfo(boolean isPublic) {}

    private Map<String, EnumStringFieldInfo> collectEnumStringFields(
        final com.github.javaparser.ast.body.EnumDeclaration enumDecl) {
      final Map<String, EnumStringFieldInfo> fields = new HashMap<>();
      for (final com.github.javaparser.ast.body.FieldDeclaration fieldDecl : enumDecl.getFields()) {
        if (!isStringType(fieldDecl.getElementType().asString())) {
          continue;
        }
        final boolean isPublic = fieldDecl.isPublic();
        for (final var var : fieldDecl.getVariables()) {
          fields.put(var.getNameAsString(), new EnumStringFieldInfo(isPublic));
        }
      }
      return fields;
    }

    private Set<String> extractPublicFieldNames(
        final Map<String, EnumStringFieldInfo> stringFields) {
      final Set<String> names = new HashSet<>();
      for (final Map.Entry<String, EnumStringFieldInfo> entry : stringFields.entrySet()) {
        if (entry.getValue().isPublic()) {
          names.add(entry.getKey());
        }
      }
      return names;
    }

    private Map<String, Integer> collectEnumConstructorFieldBindings(
        final com.github.javaparser.ast.body.EnumDeclaration enumDecl,
        final Set<String> fieldNames) {
      final Map<String, Integer> fieldParamIndex = new HashMap<>();
      final Set<String> ambiguousFields = new HashSet<>();
      final String enumName = enumDecl.getNameAsString();
      for (final var ctor : enumDecl.getConstructors()) {
        final Map<String, Integer> stringParamIndex = collectStringParamIndex(ctor);
        for (final var assign : ctor.findAll(com.github.javaparser.ast.expr.AssignExpr.class)) {
          updateEnumFieldBinding(
              fieldParamIndex, ambiguousFields, fieldNames, enumName, stringParamIndex, assign);
        }
      }
      return fieldParamIndex;
    }

    private Map<String, Integer> collectStringParamIndex(
        final com.github.javaparser.ast.body.ConstructorDeclaration ctor) {
      final Map<String, Integer> stringParamIndex = new HashMap<>();
      for (int i = 0; i < ctor.getParameters().size(); i++) {
        final var param = ctor.getParameters().get(i);
        if (isStringType(param.getType().asString())) {
          stringParamIndex.put(param.getNameAsString(), i);
        }
      }
      return stringParamIndex;
    }

    private void updateEnumFieldBinding(
        final Map<String, Integer> fieldParamIndex,
        final Set<String> ambiguousFields,
        final Set<String> fieldNames,
        final String enumName,
        final Map<String, Integer> stringParamIndex,
        final com.github.javaparser.ast.expr.AssignExpr assign) {
      final String fieldName = extractFieldName(assign.getTarget(), enumName);
      if (fieldName == null || !fieldNames.contains(fieldName)) {
        return;
      }
      if (!assign.getValue().isNameExpr()) {
        return;
      }
      final String paramName = assign.getValue().asNameExpr().getNameAsString();
      final Integer paramIndex = stringParamIndex.get(paramName);
      if (paramIndex == null || ambiguousFields.contains(fieldName)) {
        return;
      }
      final Integer existing = fieldParamIndex.get(fieldName);
      if (existing != null && !existing.equals(paramIndex)) {
        fieldParamIndex.remove(fieldName);
        ambiguousFields.add(fieldName);
        return;
      }
      fieldParamIndex.put(fieldName, paramIndex);
    }

    private String resolveEnumToStringField(
        final com.github.javaparser.ast.body.EnumDeclaration enumDecl,
        final Set<String> fieldNames) {
      for (final var method : enumDecl.getMethodsByName("toString")) {
        final String fieldName = extractEnumToStringFieldName(method, enumDecl.getNameAsString());
        if (fieldName != null && fieldNames.contains(fieldName)) {
          return fieldName;
        }
      }
      return null;
    }

    private String extractEnumToStringFieldName(
        final com.github.javaparser.ast.body.MethodDeclaration method,
        final String enumSimpleName) {
      if (!method.getParameters().isEmpty()) {
        return null;
      }
      if (!isStringType(method.getType().asString())) {
        return null;
      }
      final var bodyOpt = method.getBody();
      if (bodyOpt.isEmpty()) {
        return null;
      }
      final var returns = bodyOpt.get().findAll(com.github.javaparser.ast.stmt.ReturnStmt.class);
      if (returns.size() != 1) {
        return null;
      }
      final var returnExpr = returns.get(0).getExpression().orElse(null);
      if (returnExpr == null) {
        return null;
      }
      return extractFieldName(returnExpr, enumSimpleName);
    }

    private String resolveEnumEntryStringValue(
        final com.github.javaparser.ast.body.EnumConstantDeclaration entry,
        final Map<String, EnumStringFieldInfo> stringFields,
        final Map<String, Integer> fieldParamBindings,
        final String toStringFieldName) {
      if (entry.getArguments().isEmpty()) {
        return null;
      }
      if (toStringFieldName != null) {
        final String value =
            resolveEnumEntryValueForField(entry, fieldParamBindings, toStringFieldName);
        if (value != null) {
          return value;
        }
      }
      for (final Map.Entry<String, EnumStringFieldInfo> fieldEntry : stringFields.entrySet()) {
        if (!fieldEntry.getValue().isPublic()) {
          continue;
        }
        final String value =
            resolveEnumEntryValueForField(entry, fieldParamBindings, fieldEntry.getKey());
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    private String resolveEnumEntryValueForField(
        final com.github.javaparser.ast.body.EnumConstantDeclaration entry,
        final Map<String, Integer> fieldParamBindings,
        final String fieldName) {
      final Integer argIndex = fieldParamBindings.get(fieldName);
      if (argIndex == null) {
        return null;
      }
      if (argIndex >= entry.getArguments().size()) {
        return null;
      }
      return resolveLiteralConcat(entry.getArgument(argIndex), 0);
    }

    private String extractFieldName(final Expression expr, final String ownerSimpleName) {
      if (expr == null) {
        return null;
      }
      if (expr.isNameExpr()) {
        return expr.asNameExpr().getNameAsString();
      }
      if (expr.isFieldAccessExpr()) {
        final var fieldAccess = expr.asFieldAccessExpr();
        if (fieldAccess.getScope().isThisExpr()) {
          return fieldAccess.getNameAsString();
        }
        if (fieldAccess.getScope().isNameExpr()
            && fieldAccess.getScope().asNameExpr().getNameAsString().equals(ownerSimpleName)) {
          return fieldAccess.getNameAsString();
        }
      }
      if (expr.isEnclosedExpr()) {
        return extractFieldName(expr.asEnclosedExpr().getInner(), ownerSimpleName);
      }
      return null;
    }

    private String resolveLiteralConcat(final Expression expr, final int depth) {
      if (expr == null || depth > MAX_EXPR_DEPTH) {
        return null;
      }
      if (expr.isStringLiteralExpr()) {
        return expr.asStringLiteralExpr().asString();
      }
      if (expr.isEnclosedExpr()) {
        return resolveLiteralConcat(expr.asEnclosedExpr().getInner(), depth + 1);
      }
      if (expr.isBinaryExpr()) {
        final var binary = expr.asBinaryExpr();
        if (binary.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS) {
          final String left = resolveLiteralConcat(binary.getLeft(), depth + 1);
          if (left == null) {
            return null;
          }
          final String right = resolveLiteralConcat(binary.getRight(), depth + 1);
          if (right == null) {
            return null;
          }
          return left + right;
        }
      }
      return null;
    }

    private boolean isStringType(final String typeName) {
      return "String".equals(typeName) || "java.lang.String".equals(typeName);
    }

    private String resolveTypeFqn(
        final com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl,
        final CompilationUnit cu) {
      if (typeDecl == null) {
        return null;
      }
      final var fqn = typeDecl.getFullyQualifiedName();
      if (fqn.isPresent()) {
        return fqn.get();
      }
      final String pkg =
          cu.getPackageDeclaration()
              .map(com.github.javaparser.ast.nodeTypes.NodeWithName::getNameAsString)
              .orElse("");
      if (pkg.isEmpty()) {
        return typeDecl.getNameAsString();
      }
      return pkg + "." + typeDecl.getNameAsString();
    }

    /**
     * First pass: collect all method call sites within this class for inter-procedural analysis.
     * Should be called before visiting for resolution.
     */
    public void collectIntraClassCallSites(
        final com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl) {
      if (!enableInterproceduralResolution) {
        return;
      }
      // Find all method calls that are unqualified (same class) or explicitly
      // this.method()
      classDecl
          .findAll(MethodCallExpr.class)
          .forEach(
              mce -> {
                final java.util.Optional<Expression> scope = mce.getScope();
                // Only consider unqualified calls (implicitly same class) or this.method()
                if (scope.isEmpty() || (scope.get().isThisExpr())) {
                  final String methodName = mce.getNameAsString();
                  final int argCount = mce.getArguments().size();
                  final String key = methodName + "#" + argCount;
                  final int line = mce.getBegin().map(p -> p.line).orElse(-1);
                  intraClassCallSites
                      .computeIfAbsent(key, k -> new ArrayList<>())
                      .add(new CallSiteInfo(mce, new ArrayList<>(mce.getArguments()), line));
                }
              });
      Logger.debug(
          "[DynamicResolver] Collected "
              + intraClassCallSites.size()
              + " method signatures with call sites in "
              + classFqn);
    }

    @Override
    public void visit(final com.github.javaparser.ast.body.VariableDeclarator n, final Void arg) {
      super.visit(n, arg);
      // Ignore class/enum fields here; handled separately as static constants.
      if (AstUtils.findAncestor(n, com.github.javaparser.ast.body.FieldDeclaration.class)
          .isPresent()) {
        return;
      }
      // Simple constant propagation: String s = "literal"; or "a" + "b";
      if (n.getInitializer().isPresent()) {
        final String name = n.getNameAsString();
        final Expression init = n.getInitializer().get();
        trackLocalStringConstant(name, init);
        trackLocalClassTarget(name, init);
      }
    }

    @Override
    public void visit(final com.github.javaparser.ast.expr.AssignExpr n, final Void arg) {
      super.visit(n, arg);
      if (!n.getTarget().isNameExpr()) {
        return;
      }
      final String variableName = n.getTarget().asNameExpr().getNameAsString();
      trackLocalStringConstant(variableName, n.getValue());
      trackLocalClassTarget(variableName, n.getValue());
    }

    @Override
    public void visit(final com.github.javaparser.ast.body.MethodDeclaration n, final Void arg) {
      // Track method context for inter-procedural resolution
      final String prevMethodName = currentMethodName;
      final int prevParamCount = currentMethodParamCount;
      final String prevMethodSignature = currentMethodSignature;
      final Map<String, Integer> prevParamNames = new HashMap<>(methodParamNames);
      final Map<String, String> prevClassTargets = new HashMap<>(localClassTargets);
      currentMethodName = n.getNameAsString();
      currentMethodParamCount = n.getParameters().size();
      currentMethodSignature = n.getSignature().asString();
      methodParamNames.clear();
      localClassTargets.clear();
      // Track String parameters only
      for (int i = 0; i < n.getParameters().size(); i++) {
        final var param = n.getParameters().get(i);
        final String typeName = param.getType().asString();
        if ("String".equals(typeName) || "java.lang.String".equals(typeName)) {
          methodParamNames.put(param.getNameAsString(), i);
        }
      }
      super.visit(n, arg);
      // Restore previous context (for nested classes etc)
      currentMethodName = prevMethodName;
      currentMethodParamCount = prevParamCount;
      currentMethodSignature = prevMethodSignature;
      methodParamNames.clear();
      methodParamNames.putAll(prevParamNames);
      localClassTargets.clear();
      localClassTargets.putAll(prevClassTargets);
    }

    @Override
    public void visit(final com.github.javaparser.ast.stmt.IfStmt n, final Void arg) {
      // Collect assignments from both branches BEFORE visiting children
      collectIfBranchAssignments(n);
      super.visit(n, arg);
    }

    @Override
    public void visit(final com.github.javaparser.ast.stmt.SwitchStmt n, final Void arg) {
      // Collect assignments from all cases BEFORE visiting children
      collectSwitchBranchAssignments(n);
      super.visit(n, arg);
    }

    private void collectIfBranchAssignments(final com.github.javaparser.ast.stmt.IfStmt ifStmt) {
      final Map<String, Set<String>> branchValues = new HashMap<>();
      // Collect from then-branch
      collectAssignmentsFromStatement(ifStmt.getThenStmt(), branchValues);
      // Collect from else-branch if present
      ifStmt
          .getElseStmt()
          .ifPresent(
              elseStmt -> {
                collectAssignmentsFromStatement(elseStmt, branchValues);
              });
      // Merge into branchCandidates only if there are at least 2 values for a
      // variable
      mergeBranchValues(branchValues);
    }

    private void collectSwitchBranchAssignments(
        final com.github.javaparser.ast.stmt.SwitchStmt switchStmt) {
      final Map<String, Set<String>> branchValues = new HashMap<>();
      for (final com.github.javaparser.ast.stmt.SwitchEntry entry : switchStmt.getEntries()) {
        for (final com.github.javaparser.ast.stmt.Statement stmt : entry.getStatements()) {
          collectAssignmentsFromStatement(stmt, branchValues);
        }
      }
      // Merge into branchCandidates only if there are at least 2 values for a
      // variable
      mergeBranchValues(branchValues);
    }

    private void collectAssignmentsFromStatement(
        final com.github.javaparser.ast.stmt.Statement stmt,
        final Map<String, Set<String>> branchValues) {
      // Find all assignments: varName = "literal" or varName = expr
      stmt.findAll(com.github.javaparser.ast.expr.AssignExpr.class)
          .forEach(
              assign -> {
                if (assign.getTarget().isNameExpr()) {
                  final String varName = assign.getTarget().asNameExpr().getNameAsString();
                  final ResolutionResult res = resolveStringExpression(assign.getValue());
                  if (res != null && res.value() != null) {
                    branchValues
                        .computeIfAbsent(varName, k -> new LinkedHashSet<>())
                        .add(res.value());
                  }
                }
              });
      // Also handle variable declarations within blocks
      stmt.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)
          .forEach(
              decl -> {
                if (decl.getInitializer().isPresent()) {
                  final String varName = decl.getNameAsString();
                  final ResolutionResult res = resolveStringExpression(decl.getInitializer().get());
                  if (res != null && res.value() != null) {
                    branchValues
                        .computeIfAbsent(varName, k -> new LinkedHashSet<>())
                        .add(res.value());
                  }
                }
              });
    }

    private void mergeBranchValues(final Map<String, Set<String>> branchValues) {
      for (final Map.Entry<String, Set<String>> entry : branchValues.entrySet()) {
        final String varName = entry.getKey();
        final Set<String> newValues = entry.getValue();
        if (newValues.size() >= 2 || branchCandidates.containsKey(varName)) {
          final Set<String> existing =
              branchCandidates.computeIfAbsent(varName, k -> new LinkedHashSet<>());
          for (final String val : newValues) {
            if (existing.size() >= MAX_CANDIDATES) {
              truncatedVariables.add(varName);
              Logger.debug(
                  "[DynamicResolver] Truncating branch candidates for variable: " + varName);
              break;
            }
            existing.add(val);
          }
          // Remove from single-value map as it's now multi-candidate
          localConstants.remove(varName);
        }
      }
    }

    @Override
    public void visit(final MethodCallExpr n, final Void arg) {
      // Visit children first
      super.visit(n, arg);
      final String methodName = n.getNameAsString();
      // 1. Class.forName("FQN")
      if ("forName".equals(methodName)) {
        final java.util.Optional<Expression> scope = n.getScope();
        if (scope.isPresent()
            && "Class".equals(scope.get().toString())
            && !n.getArguments().isEmpty()) {
          // Check arguments
          resolveClassForName(n);
        }
      }
      // 2. getMethod / getDeclaredMethod
      if (("getMethod".equals(methodName) || "getDeclaredMethod".equals(methodName))) {
        resolveGetMethod(n, methodName);
      }
      // 3. getField / getDeclaredField
      if (("getField".equals(methodName) || "getDeclaredField".equals(methodName))) {
        resolveGetField(n, methodName);
      }
      // 3. ServiceLoader.load
      if ("load".equals(methodName)) {
        final java.util.Optional<Expression> scope = n.getScope();
        if (scope.isPresent() && "ServiceLoader".equals(scope.get().toString())) {
          resolveServiceLoader(n);
        }
      }
    }

    // Helper class to carry value, provenance and resolution rule
    private record ResolutionResult(
        String value, boolean isLiteral, List<String> candidates, ResolutionRuleId ruleId) {

      static ResolutionResult single(
          final String value, final boolean isLiteral, final ResolutionRuleId ruleId) {
        return new ResolutionResult(value, isLiteral, null, ruleId);
      }

      static ResolutionResult multi(final List<String> candidates) {
        return new ResolutionResult(null, false, candidates, null);
      }

      boolean hasMultipleCandidates() {
        return candidates != null && candidates.size() >= 2;
      }
    }

    // Helper to resolve String literals, concatenation, or variable lookup
    private ResolutionResult resolveStringExpression(final Expression expr) {
      return resolveStringExpression(expr, 0);
    }

    private ResolutionResult resolveStringExpression(final Expression expr, final int depth) {
      if (expr == null || depth > MAX_EXPR_DEPTH) {
        return null;
      }
      ResolutionResult result = resolveLiteralExpression(expr);
      if (result != null) {
        return result;
      }
      result = resolveFieldAccessExpression(expr);
      if (result != null) {
        return result;
      }
      result = resolveNameExpression(expr);
      if (result != null) {
        return result;
      }
      result = resolveBinaryConcatExpression(expr, depth);
      if (result != null) {
        return result;
      }
      return resolveMethodCallExpression(expr, depth);
    }

    private ResolutionResult resolveLiteralExpression(final Expression expr) {
      if (!expr.isStringLiteralExpr()) {
        return null;
      }
      return ResolutionResult.single(
          expr.asStringLiteralExpr().asString(), true, ResolutionRuleId.LITERAL);
    }

    private ResolutionResult resolveFieldAccessExpression(final Expression expr) {
      if (!expr.isFieldAccessExpr()) {
        return null;
      }
      final String resolved = resolveStaticFieldReference(expr.asFieldAccessExpr());
      if (resolved == null) {
        return null;
      }
      return ResolutionResult.single(resolved, true, ResolutionRuleId.STATIC_FIELD);
    }

    private ResolutionResult resolveNameExpression(final Expression expr) {
      if (!expr.isNameExpr()) {
        return null;
      }
      final String varName = expr.asNameExpr().getNameAsString();
      if (branchCandidates.containsKey(varName)) {
        final List<String> candidates = new ArrayList<>(branchCandidates.get(varName));
        return ResolutionResult.multi(candidates);
      }
      final String val = localConstants.get(varName);
      if (val != null) {
        return ResolutionResult.single(val, false, ResolutionRuleId.LOCAL_CONST);
      }
      final String staticVal = resolveStaticConstantBySimpleName(varName);
      if (staticVal == null) {
        return null;
      }
      final ResolutionRuleId ruleId =
          enumStringConstantsBySimple.containsKey(varName)
              ? ResolutionRuleId.ENUM_CONST
              : ResolutionRuleId.STATIC_FIELD;
      return ResolutionResult.single(staticVal, true, ruleId);
    }

    private ResolutionResult resolveBinaryConcatExpression(final Expression expr, final int depth) {
      if (!expr.isBinaryExpr()) {
        return null;
      }
      final com.github.javaparser.ast.expr.BinaryExpr be = expr.asBinaryExpr();
      if (be.getOperator() != com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS) {
        return null;
      }
      final ResolutionResult left = resolveStringExpression(be.getLeft(), depth + 1);
      final ResolutionResult right = resolveStringExpression(be.getRight(), depth + 1);
      if (left != null && right != null && left.value() != null && right.value() != null) {
        return ResolutionResult.single(
            left.value() + right.value(), false, ResolutionRuleId.BINARY_CONCAT);
      }
      return null;
    }

    private ResolutionResult resolveMethodCallExpression(final Expression expr, final int depth) {
      if (!expr.isMethodCallExpr()) {
        return null;
      }
      final MethodCallExpr mce = expr.asMethodCallExpr();
      final ResolutionResult configResult = resolveExternalConfigLookup(mce, depth + 1);
      if (configResult != null) {
        return configResult;
      }
      if ("toString".equals(mce.getNameAsString()) && mce.getArguments().isEmpty()) {
        final ResolutionResult sbResult = resolveStringBuilderChain(mce, depth + 1);
        if (sbResult != null) {
          return sbResult;
        }
      }
      if ("format".equals(mce.getNameAsString())) {
        final ResolutionResult fmtResult = resolveStringFormat(mce, depth + 1);
        if (fmtResult != null) {
          return fmtResult;
        }
      }
      final ResolutionResult concatResult = resolveStringConcat(mce, depth + 1);
      if (concatResult != null) {
        return concatResult;
      }
      final ResolutionResult joinResult = resolveStringJoin(mce, depth + 1);
      if (joinResult != null) {
        return joinResult;
      }
      return resolveStringValueOf(mce);
    }

    private ResolutionResult resolveExternalConfigLookup(
        final MethodCallExpr mce, final int depth) {
      if (externalConfigValues == null || externalConfigValues.isEmpty()) {
        return null;
      }
      final String name = mce.getNameAsString();
      if (!("get".equals(name) || "getProperty".equals(name) || "getString".equals(name))) {
        return null;
      }
      if (mce.getArguments().size() != 1) {
        return null;
      }
      final ResolutionResult keyResult = resolveStringExpression(mce.getArgument(0), depth);
      if (keyResult == null || keyResult.value() == null) {
        return null;
      }
      final String value = externalConfigValues.get(keyResult.value());
      if (value == null || value.isBlank()) {
        return null;
      }
      return ResolutionResult.single(value, false, ResolutionRuleId.EXTERNAL_CONFIG);
    }

    private ResolutionResult resolveStringConcat(final MethodCallExpr mce, final int depth) {
      if (!"concat".equals(mce.getNameAsString())) {
        return null;
      }
      if (mce.getArguments().size() != 1 || mce.getScope().isEmpty()) {
        return null;
      }
      final ResolutionResult scopeResult = resolveStringExpression(mce.getScope().get(), depth);
      final ResolutionResult argResult = resolveStringExpression(mce.getArgument(0), depth);
      if (scopeResult == null || argResult == null) {
        return null;
      }
      if (scopeResult.value() == null || argResult.value() == null) {
        return null;
      }
      return ResolutionResult.single(
          scopeResult.value() + argResult.value(), false, ResolutionRuleId.STRING_CONCAT_METHOD);
    }

    private ResolutionResult resolveStringJoin(final MethodCallExpr mce, final int depth) {
      if (!"join".equals(mce.getNameAsString())) {
        return null;
      }
      if (mce.getScope().isEmpty() || !"String".equals(mce.getScope().get().toString())) {
        return null;
      }
      if (mce.getArguments().size() < 2) {
        return null;
      }
      final ResolutionResult delimiterResult = resolveStringExpression(mce.getArgument(0), depth);
      if (delimiterResult == null || delimiterResult.value() == null) {
        return null;
      }
      final List<String> parts = new ArrayList<>();
      for (int i = 1; i < mce.getArguments().size(); i++) {
        final ResolutionResult argResult = resolveStringExpression(mce.getArgument(i), depth);
        if (argResult == null || argResult.value() == null) {
          return null;
        }
        parts.add(argResult.value());
      }
      return ResolutionResult.single(
          String.join(delimiterResult.value(), parts), false, ResolutionRuleId.STRING_JOIN);
    }

    private ResolutionResult resolveStringValueOf(final MethodCallExpr mce) {
      if (!"valueOf".equals(mce.getNameAsString())) {
        return null;
      }
      if (mce.getScope().isEmpty() || !"String".equals(mce.getScope().get().toString())) {
        return null;
      }
      if (mce.getArguments().size() != 1) {
        return null;
      }
      final Expression arg = mce.getArgument(0);
      if (arg.isStringLiteralExpr()) {
        return ResolutionResult.single(
            arg.asStringLiteralExpr().asString(), true, ResolutionRuleId.STRING_VALUEOF);
      }
      if (arg.isCharLiteralExpr()) {
        return ResolutionResult.single(
            String.valueOf(arg.asCharLiteralExpr().asChar()),
            true,
            ResolutionRuleId.STRING_VALUEOF);
      }
      if (arg.isIntegerLiteralExpr()) {
        final String value = arg.asIntegerLiteralExpr().getValue().replace("_", "");
        return ResolutionResult.single(value, true, ResolutionRuleId.STRING_VALUEOF);
      }
      if (arg.isLongLiteralExpr()) {
        String value = arg.asLongLiteralExpr().getValue().replace("_", "");
        if (value.endsWith("L") || value.endsWith("l")) {
          value = value.substring(0, value.length() - 1);
        }
        return ResolutionResult.single(value, true, ResolutionRuleId.STRING_VALUEOF);
      }
      if (arg.isBooleanLiteralExpr()) {
        return ResolutionResult.single(
            String.valueOf(arg.asBooleanLiteralExpr().getValue()),
            true,
            ResolutionRuleId.STRING_VALUEOF);
      }
      if (arg.isDoubleLiteralExpr()) {
        final String value = normalizeFloatingLiteral(arg.asDoubleLiteralExpr().getValue());
        if (value == null) {
          return null;
        }
        return ResolutionResult.single(value, true, ResolutionRuleId.STRING_VALUEOF);
      }
      return null;
    }

    private String normalizeFloatingLiteral(final String rawValue) {
      if (rawValue == null || rawValue.isEmpty()) {
        return null;
      }
      String cleaned = rawValue.replace("_", "");
      boolean isFloat = false;
      if (cleaned.endsWith("f") || cleaned.endsWith("F")) {
        isFloat = true;
        cleaned = cleaned.substring(0, cleaned.length() - 1);
      } else if (cleaned.endsWith("d") || cleaned.endsWith("D")) {
        cleaned = cleaned.substring(0, cleaned.length() - 1);
      }
      try {
        if (isFloat) {
          return String.valueOf(Float.parseFloat(cleaned));
        }
        return String.valueOf(Double.parseDouble(cleaned));
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    private String resolveStaticFieldReference(
        final com.github.javaparser.ast.expr.FieldAccessExpr expr) {
      final String scopeText = expr.getScope().toString();
      final String fieldName = expr.getNameAsString();
      final String directKey = scopeText + "." + fieldName;
      final String direct = resolveStaticConstantByFqn(directKey);
      if (direct != null) {
        return direct;
      }
      final String enumValue = resolveStaticConstantByFqn(scopeText);
      if (enumValue != null) {
        final String enumFqn = extractEnumFqn(scopeText);
        if (enumFqn != null) {
          final Set<String> publicFields = enumPublicStringFieldsByFqn.get(enumFqn);
          if (publicFields != null && publicFields.contains(fieldName)) {
            return enumValue;
          }
        }
      }
      final String scopeFqn = resolveScopeToClassFqn(scopeText);
      if (scopeFqn == null || scopeFqn.isBlank()) {
        return null;
      }
      return resolveStaticConstantByFqn(scopeFqn + "." + fieldName);
    }

    private String resolveStaticConstantByFqn(final String fqnKey) {
      final String val = staticStringConstantsByFqn.get(fqnKey);
      if (val != null) {
        return val;
      }
      return enumStringConstantsByFqn.get(fqnKey);
    }

    private String resolveStaticConstantBySimpleName(final String name) {
      if (ambiguousSimpleConstants.contains(name)) {
        return null;
      }
      if (staticStringConstantsBySimple.containsKey(name)) {
        return staticStringConstantsBySimple.get(name);
      }
      return enumStringConstantsBySimple.get(name);
    }

    private String resolveScopeToClassFqn(final String scopeText) {
      if (scopeText == null || scopeText.isBlank()) {
        return null;
      }
      if (fqnToClassInfo.containsKey(scopeText)) {
        return scopeText;
      }
      if (classFqn != null && (classFqn.equals(scopeText) || classFqn.endsWith("." + scopeText))) {
        return classFqn;
      }
      final String packageName = extractPackage(classFqn);
      if (packageName != null && !packageName.isBlank()) {
        final String inPackage = packageName + "." + scopeText;
        if (fqnToClassInfo.containsKey(inPackage)) {
          return inPackage;
        }
      }
      final String enumFqn = enumSimpleNameToFqn.get(scopeText);
      if (enumFqn != null) {
        return enumFqn;
      }
      final ClassInfo classInfo = fqnToClassInfo.get(classFqn);
      if (classInfo != null) {
        for (final String imp : classInfo.getImports()) {
          if (imp != null && (imp.endsWith("." + scopeText) || imp.equals(scopeText))) {
            return imp;
          }
        }
      }
      return null;
    }

    private String extractPackage(final String fqn) {
      if (fqn == null) {
        return null;
      }
      final int lastDot = fqn.lastIndexOf('.');
      if (lastDot <= 0) {
        return "";
      }
      return fqn.substring(0, lastDot);
    }

    private String extractEnumFqn(final String enumConstantKey) {
      if (enumConstantKey == null) {
        return null;
      }
      final int lastDot = enumConstantKey.lastIndexOf('.');
      if (lastDot <= 0) {
        return null;
      }
      return enumConstantKey.substring(0, lastDot);
    }

    /**
     * Resolve String.format pattern: - String.format("com.%s.Impl", x) where x is resolvable
     *
     * <p>Safety constraints (MVP): - First arg (format) must be a string literal - Only %s
     * specifiers are supported - All format arguments must be resolvable
     * (literal/variable/concatenation) - Returns null if unresolvable (no false positives)
     */
    private ResolutionResult resolveStringFormat(final MethodCallExpr mce, final int depth) {
      // Check scope is "String"
      final java.util.Optional<Expression> scope = mce.getScope();
      if (scope.isEmpty() || !"String".equals(scope.get().toString())) {
        return null;
      }
      final com.github.javaparser.ast.NodeList<Expression> args = mce.getArguments();
      if (args.isEmpty()) {
        return null;
      }
      // First argument must be a string literal (format string)
      final Expression formatExpr = args.get(0);
      if (!formatExpr.isStringLiteralExpr()) {
        // Format is variable - unsupported
        return null;
      }
      final String formatString = formatExpr.asStringLiteralExpr().asString();
      // Count %s specifiers and reject unsupported specifiers
      int specifierCount = 0;
      int index = 0;
      while ((index = formatString.indexOf('%', index)) != -1) {
        if (index + 1 >= formatString.length()) {
          // Malformed format
          return null;
        }
        final char specifier = formatString.charAt(index + 1);
        if (specifier == 's') {
          specifierCount++;
          index += 2;
        } else if (specifier == '%') {
          // Escaped %%, skip
          index += 2;
        } else {
          // Unsupported specifier (%d, %f, etc.)
          return null;
        }
      }
      // Check argument count matches specifier count
      // Exclude format string
      final int argCount = args.size() - 1;
      if (argCount != specifierCount) {
        // Mismatch
        return null;
      }
      // Resolve all format arguments
      final List<String> resolvedArgs = new ArrayList<>();
      boolean allLiterals = true;
      for (int i = 1; i < args.size(); i++) {
        final ResolutionResult argResult = resolveStringExpression(args.get(i), depth);
        if (argResult == null || argResult.value() == null) {
          // Cannot resolve this argument
          return null;
        }
        if (argResult.hasMultipleCandidates()) {
          // Branch candidates - too complex for MVP
          return null;
        }
        if (!argResult.isLiteral()) {
          allLiterals = false;
        }
        resolvedArgs.add(argResult.value());
      }
      // Build the formatted string
      final StringBuilder result = new StringBuilder();
      int argIndex = 0;
      index = 0;
      while (index < formatString.length()) {
        final int percentPos = formatString.indexOf('%', index);
        if (percentPos == -1) {
          result.append(formatString.substring(index));
          break;
        }
        result.append(formatString, index, percentPos);
        final char specifier = formatString.charAt(percentPos + 1);
        if (specifier == 's') {
          result.append(resolvedArgs.get(argIndex++));
          index = percentPos + 2;
        } else if (specifier == '%') {
          result.append('%');
          index = percentPos + 2;
        } else {
          // Should not reach here due to earlier check
          return null;
        }
      }
      // Return result with appropriate confidence
      // All literals: 1.0 (but will be reduced by verification if class unknown)
      // Otherwise: 0.8 (inferred)
      return ResolutionResult.single(
          result.toString(), allLiterals, ResolutionRuleId.STRING_FORMAT);
    }

    /**
     * Resolve StringBuilder append chain pattern: - new
     * StringBuilder().append("a").append("b").toString() -> "ab" - new
     * StringBuilder("a").append("b").toString() -> "ab"
     *
     * <p>Safety constraints (to avoid false positives): - Only handles direct chains starting with
     * new StringBuilder() - Does NOT handle: loops, conditionals, builder variable reuse - Returns
     * null if any append argument cannot be resolved
     */
    private ResolutionResult resolveStringBuilderChain(
        final MethodCallExpr toStringExpr, final int depth) {
      // Collect append arguments by traversing the chain backwards
      final List<String> parts = new ArrayList<>();
      Expression current = toStringExpr.getScope().orElse(null);
      while (current != null) {
        if (current.isMethodCallExpr()) {
          final MethodCallExpr mce = current.asMethodCallExpr();
          if ("append".equals(mce.getNameAsString()) && mce.getArguments().size() == 1) {
            // Resolve the append argument
            final ResolutionResult argResult = resolveStringExpression(mce.getArgument(0), depth);
            if (argResult == null || argResult.value() == null) {
              // Cannot resolve this append argument - fail safely
              return null;
            }
            if (argResult.hasMultipleCandidates()) {
              // Branch candidates in append - too complex, skip
              return null;
            }
            // prepend since we're going backwards
            parts.add(0, argResult.value());
            current = mce.getScope().orElse(null);
          } else {
            // Unexpected method in chain
            return null;
          }
        } else if (current.isObjectCreationExpr()) {
          final var oce = current.asObjectCreationExpr();
          final String typeName = oce.getType().getNameAsString();
          if ("StringBuilder".equals(typeName) || "StringBuffer".equals(typeName)) {
            // Check for initial value: new StringBuilder("initial")
            if (!oce.getArguments().isEmpty()) {
              final Expression initArg = oce.getArgument(0);
              final ResolutionResult initResult = resolveStringExpression(initArg, depth);
              if (initResult != null
                  && initResult.value() != null
                  && !initResult.hasMultipleCandidates()) {
                parts.add(0, initResult.value());
              } else if (initArg.isStringLiteralExpr()) {
                parts.add(0, initArg.asStringLiteralExpr().asString());
              } else {
                // Cannot resolve initial value
                return null;
              }
            }
            // Successfully found the start of the chain
            if (parts.isEmpty()) {
              // No content to resolve
              return null;
            }
            final String concatenated = String.join("", parts);
            return ResolutionResult.single(
                concatenated,
                false, // inferred, not
                ResolutionRuleId.STRING_BUILDER);
            // literal
          } else {
            // Not a StringBuilder
            return null;
          }
        } else {
          // Unexpected expression type (e.g., variable holding StringBuilder)
          // This is the "builder variable reuse" case - we skip it
          return null;
        }
      }
      return null;
    }

    private boolean isTruncated(final String varName) {
      return truncatedVariables.contains(varName);
    }

    private void resolveClassForName(final MethodCallExpr n) {
      final Expression arg = n.getArgument(0);
      ResolutionResult res = resolveStringExpression(arg);
      final int lineNum = n.getBegin().map(pos -> pos.line).orElse(-1);
      // If resolution failed and arg is a method parameter, try inter-procedural
      // resolution
      if (res == null && enableInterproceduralResolution && arg.isNameExpr()) {
        final String varName = arg.asNameExpr().getNameAsString();
        if (methodParamNames.containsKey(varName)) {
          res = resolveFromCallSites(varName);
        }
      }
      if (res == null) {
        return;
      }
      // Handle branch candidates (multiple possible values)
      if (res.hasMultipleCandidates()) {
        handleMultivalueCandidates(res, arg, lineNum);
        return;
      }
      // Single value case (original logic or inter-procedural single)
      final String literal = res.value();
      if (literal == null) {
        return;
      }
      if (literal.contains(".")) {
        handleFqnResolution(literal, arg, lineNum, res);
      } else if (enableCandidateEnumeration) {
        emitClassCandidates(lineNum, literal, PATTERN_CLASS_FORNAME);
      }
    }

    private void handleMultivalueCandidates(
        final ResolutionResult res, final Expression arg, final int lineNum) {
      final List<String> validCandidates =
          res.candidates().stream().filter(c -> c.contains(".")).distinct().sorted().toList();
      if (validCandidates.isEmpty()) {
        return;
      }
      if (validCandidates.size() == 1) {
        // Single valid candidate from multi-candidate resolution
        checkAndHandleInterprocedural(arg, lineNum, validCandidates.get(0));
        return;
      }
      createBranchCandidatesResolution(validCandidates, arg, lineNum);
    }

    private void createBranchCandidatesResolution(
        final List<String> validCandidates, final Expression arg, final int lineNum) {
      final String varName = arg.isNameExpr() ? arg.asNameExpr().getNameAsString() : null;
      final boolean truncated = varName != null && isTruncated(varName);
      // Check if this comes from inter-procedural resolution
      final boolean isInterprocedural = varName != null && methodParamNames.containsKey(varName);
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_PATTERN, PATTERN_CLASS_FORNAME);
      if (isInterprocedural) {
        evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_INTERPROCEDURAL_CANDIDATES);
      } else {
        evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_BRANCH_CANDIDATES);
      }
      evidence.put(EVIDENCE_CANDIDATE_COUNT, String.valueOf(validCandidates.size()));
      if (truncated) {
        evidence.put(EVIDENCE_TRUNCATED, "true");
      }
      if (isInterprocedural) {
        evidence.put(EVIDENCE_SOURCE_METHOD, currentMethodName + "#" + currentMethodParamCount);
      }
      // Determine reason code for unresolved cases
      final DynamicReasonCode reasonCode;
      if (truncated) {
        reasonCode = DynamicReasonCode.CANDIDATE_LIMIT_EXCEEDED;
      } else {
        reasonCode = DynamicReasonCode.AMBIGUOUS_CANDIDATES;
      }
      final double confidence = BRANCH_CONFIDENCE;
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.BRANCH_CANDIDATES)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .candidates(validCandidates)
              .confidence(confidence)
              .trustLevel(TrustLevel.fromConfidence(confidence))
              .reasonCode(reasonCode)
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      // Debug logging
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.BRANCH_CANDIDATES);
        Logger.debug(LOG_CANDIDATES_PREFIX + formatCandidatesForLog(validCandidates));
        Logger.debug(LOG_CONFIDENCE_PREFIX + resolution.confidence());
        Logger.debug(LOG_REASON_CODE_PREFIX + reasonCode);
      }
    }

    private void handleFqnResolution(
        final String literal, final Expression arg, final int lineNum, final ResolutionResult res) {
      // Check if this is inter-procedural resolution (from method parameter)
      if (checkAndHandleInterprocedural(arg, lineNum, literal, res.ruleId())) {
        return;
      }
      // Non-inter-procedural: existing logic
      // Base confidence: 1.0 for literals, 0.8 for inferred/variables
      final double baseConfidence = res.isLiteral() ? 1.0 : 0.8;
      // Verification penalty: -0.2 if not found in known classes
      final boolean verified = isKnownClass(literal);
      if (!verified && enableCandidateEnumeration) {
        emitClassCandidates(lineNum, literal, PATTERN_CLASS_FORNAME);
      }
      final double penalty = verified ? 0.0 : 0.2;
      final double confidence = Math.max(0.0, baseConfidence - penalty);
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.CLASS_FORNAME_LITERAL)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .resolvedClassFqn(literal)
              .confidence(confidence)
              .trustLevel(TrustLevel.fromConfidence(confidence))
              .ruleId(res.ruleId())
              .evidence(
                  Map.of(
                      EVIDENCE_LITERAL,
                      literal,
                      EVIDENCE_PATTERN,
                      PATTERN_CLASS_FORNAME,
                      EVIDENCE_PROVENANCE,
                      res.isLiteral() ? PROVENANCE_LITERAL : PROVENANCE_INFERRED))
              .build();
      resolutions.add(resolution);
      // Debug logging
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.CLASS_FORNAME_LITERAL);
        Logger.debug(LOG_RESOLVED_PREFIX + literal);
        Logger.debug(LOG_CONFIDENCE_PREFIX + confidence);
      }
    }

    private boolean checkAndHandleInterprocedural(
        final Expression arg,
        final int lineNum,
        final String value,
        final ResolutionRuleId ruleId) {
      final String varName = arg.isNameExpr() ? arg.asNameExpr().getNameAsString() : null;
      if (varName != null && methodParamNames.containsKey(varName)) {
        addInterproceduralSingleResolution(
            lineNum, value, ruleId != null ? ruleId : ResolutionRuleId.INTERPROCEDURAL);
        return true;
      }
      return false;
    }

    private boolean checkAndHandleInterprocedural(
        final Expression arg, final int lineNum, final String value) {
      return checkAndHandleInterprocedural(arg, lineNum, value, null);
    }

    /** Add a resolution for inter-procedural single-value case. */
    private void addInterproceduralSingleResolution(
        final int lineNum, final String resolvedFqn, final ResolutionRuleId ruleId) {
      final boolean verified = isKnownClass(resolvedFqn);
      final double confidence =
          verified
              ? INTERPROCEDURAL_SINGLE_CONFIDENCE
              : Math.max(0.0, INTERPROCEDURAL_SINGLE_CONFIDENCE - 0.1);
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_PATTERN, PATTERN_CLASS_FORNAME);
      evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_INTERPROCEDURAL_SINGLE);
      evidence.put(EVIDENCE_SOURCE_METHOD, currentMethodName + "#" + currentMethodParamCount);
      evidence.put(EVIDENCE_VERIFIED, String.valueOf(verified));
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.INTERPROCEDURAL_SINGLE)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .resolvedClassFqn(resolvedFqn)
              .confidence(confidence)
              .trustLevel(TrustLevel.fromConfidence(confidence))
              .ruleId(ruleId)
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      // Debug logging
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.INTERPROCEDURAL_SINGLE);
        Logger.debug(LOG_RESOLVED_PREFIX + resolvedFqn);
        Logger.debug(LOG_CONFIDENCE_PREFIX + confidence);
      }
    }

    /**
     * Resolve a method parameter by looking at call sites within the same class. Returns null if
     * cannot resolve, or ResolutionResult with single/multiple values.
     */
    private ResolutionResult resolveFromCallSites(final String paramName) {
      return resolveFromCallSites(paramName, 0);
    }

    private ResolutionResult resolveFromCallSites(final String paramName, final int depth) {
      if (currentMethodName == null) {
        return null;
      }
      final Integer paramIndex = methodParamNames.get(paramName);
      if (paramIndex == null) {
        return null;
      }
      final String methodKey = currentMethodName + "#" + currentMethodParamCount;
      final CallSiteSlice slice = limitCallSites(intraClassCallSites.get(methodKey));
      if (slice.sites().isEmpty()) {
        return null;
      }
      final Set<String> resolvedValues =
          resolveValuesFromCallSites(slice.sites(), paramIndex, depth, true);
      if (resolvedValues.isEmpty()) {
        return null;
      }
      if (slice.truncated()) {
        truncatedVariables.add(paramName);
      }
      if (resolvedValues.size() == 1) {
        return ResolutionResult.single(
            resolvedValues.iterator().next(), false, ResolutionRuleId.INTERPROCEDURAL);
      } else {
        return ResolutionResult.multi(new ArrayList<>(resolvedValues));
      }
    }

    private ResolutionResult resolveFromCallerParam(
        final MethodCallExpr callExpr, final String paramName) {
      if (callExpr == null || paramName == null || paramName.isBlank()) {
        return null;
      }
      final var caller =
          AstUtils.findAncestor(callExpr, com.github.javaparser.ast.body.MethodDeclaration.class)
              .orElse(null);
      if (caller == null) {
        return null;
      }
      final Integer paramIndex = resolveStringParamIndex(caller, paramName);
      if (paramIndex == null) {
        return null;
      }
      final String callerKey = caller.getNameAsString() + "#" + caller.getParameters().size();
      final String currentKey = currentMethodName + "#" + currentMethodParamCount;
      if (callerKey.equals(currentKey)) {
        return null;
      }
      final CallSiteSlice slice = limitCallSites(intraClassCallSites.get(callerKey));
      if (slice.sites().isEmpty()) {
        return null;
      }
      final Set<String> resolvedValues =
          resolveValuesFromCallSites(slice.sites(), paramIndex, 0, false);
      if (resolvedValues.isEmpty()) {
        return null;
      }
      if (resolvedValues.size() == 1) {
        return ResolutionResult.single(
            resolvedValues.iterator().next(), false, ResolutionRuleId.INTERPROCEDURAL);
      }
      return ResolutionResult.multi(new ArrayList<>(resolvedValues));
    }

    private CallSiteSlice limitCallSites(final List<CallSiteInfo> callSites) {
      if (callSites == null || callSites.isEmpty()) {
        return new CallSiteSlice(List.of(), false);
      }
      final boolean truncated = callSites.size() > callsiteLimit;
      final List<CallSiteInfo> sitesToProcess =
          truncated ? callSites.subList(0, callsiteLimit) : callSites;
      return new CallSiteSlice(sitesToProcess, truncated);
    }

    private Set<String> resolveValuesFromCallSites(
        final List<CallSiteInfo> sitesToProcess,
        final int paramIndex,
        final int depth,
        final boolean allowChained) {
      final Set<String> resolvedValues = new LinkedHashSet<>();
      for (final CallSiteInfo site : sitesToProcess) {
        if (paramIndex >= site.arguments().size()) {
          continue;
        }
        final String value = resolveValueFromCallSite(site, paramIndex, depth, allowChained);
        if (value != null) {
          resolvedValues.add(value);
        }
      }
      return resolvedValues;
    }

    private String resolveValueFromCallSite(
        final CallSiteInfo site,
        final int paramIndex,
        final int depth,
        final boolean allowChained) {
      final Expression argExpr = site.arguments().get(paramIndex);
      final ResolutionResult argRes = resolveStringExpression(argExpr);
      if (argRes != null) {
        return argRes.value();
      }
      if (!allowChained || depth >= MAX_INTERPROCEDURAL_DEPTH || !argExpr.isNameExpr()) {
        return null;
      }
      final ResolutionResult chained =
          resolveFromCallerParam(site.callExpr(), argExpr.asNameExpr().getNameAsString());
      return chained != null ? chained.value() : null;
    }

    private Integer resolveStringParamIndex(
        final com.github.javaparser.ast.body.MethodDeclaration caller, final String paramName) {
      for (int i = 0; i < caller.getParameters().size(); i++) {
        final var param = caller.getParameters().get(i);
        if (param.getNameAsString().equals(paramName) && isStringType(param.getType().asString())) {
          return i;
        }
      }
      return null;
    }

    private List<String> normalizeCandidates(final List<String> candidates) {
      if (candidates == null || candidates.isEmpty()) {
        return List.of();
      }
      return candidates.stream()
          .filter(c -> c != null && !c.isEmpty())
          .distinct()
          .sorted()
          .toList();
    }

    private boolean handleBranchCandidatesResolution(
        final ResolutionResult res,
        final Expression arg,
        final int lineNum,
        final String targetClass,
        final String patternName) {
      if (!res.hasMultipleCandidates()) {
        return false;
      }
      final List<String> validCandidates = normalizeCandidates(res.candidates());
      if (validCandidates.size() < 2) {
        return true;
      }
      final boolean truncated = isTruncatedVariable(arg);
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_PATTERN, patternName);
      evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_BRANCH_CANDIDATES);
      evidence.put(EVIDENCE_TARGET_CLASS, targetClass != null ? targetClass : "unknown");
      evidence.put(EVIDENCE_CANDIDATE_COUNT, String.valueOf(validCandidates.size()));
      if (truncated) {
        evidence.put(EVIDENCE_TRUNCATED, "true");
      }
      final DynamicReasonCode reasonCode =
          truncated
              ? DynamicReasonCode.CANDIDATE_LIMIT_EXCEEDED
              : DynamicReasonCode.AMBIGUOUS_CANDIDATES;
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.BRANCH_CANDIDATES)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .resolvedClassFqn(targetClass)
              .candidates(validCandidates)
              .confidence(BRANCH_CONFIDENCE)
              .trustLevel(TrustLevel.fromConfidence(BRANCH_CONFIDENCE))
              .reasonCode(reasonCode)
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.BRANCH_CANDIDATES);
        Logger.debug(LOG_CANDIDATES_PREFIX + formatCandidatesForLog(validCandidates));
        Logger.debug(LOG_CONFIDENCE_PREFIX + resolution.confidence());
        Logger.debug(LOG_REASON_CODE_PREFIX + reasonCode);
      }
      return true;
    }

    private boolean isTruncatedVariable(final Expression arg) {
      final String varName = arg.isNameExpr() ? arg.asNameExpr().getNameAsString() : null;
      return varName != null && isTruncated(varName);
    }

    private String formatCandidatesForLog(final List<String> candidates) {
      if (candidates.size() > 10) {
        return candidates.subList(0, 10) + LOG_TRUNCATED_SUFFIX;
      }
      return candidates.toString();
    }

    private record MethodVerification(
        boolean nameExists, boolean arityMatches, int overloadCount) {}

    private MethodVerification verifyMethod(
        final String targetClass,
        final ClassInfo targetInfo,
        final String methodName,
        final int requestedParamCount) {
      boolean nameExists = false;
      boolean arityMatches = false;
      int overloadCount = 0;
      if (targetInfo != null) {
        final List<MethodInfo> methods =
            targetInfo.getMethods().stream().filter(m -> methodName.equals(m.getName())).toList();
        nameExists = !methods.isEmpty();
        overloadCount = methods.size();
        if (requestedParamCount >= 0) {
          arityMatches =
              methods.stream().anyMatch(m -> m.getParameterCount() == requestedParamCount);
        }
      } else {
        if (projectSymbolIndex != null) {
          nameExists = projectSymbolIndex.hasMethod(targetClass, methodName);
          if (requestedParamCount >= 0) {
            arityMatches =
                projectSymbolIndex.hasMethodArity(targetClass, methodName, requestedParamCount);
          }
          overloadCount = projectSymbolIndex.getMethodOverloadCount(targetClass, methodName);
        }
        if (!nameExists) {
          final String suffix = "#" + methodName;
          nameExists = knownMethods.stream().anyMatch(m -> m.endsWith(suffix));
        }
      }
      return new MethodVerification(nameExists, arityMatches, overloadCount);
    }

    private void resolveGetMethod(final MethodCallExpr n, final String patternName) {
      if (n.getArguments().isEmpty()) {
        return;
      }
      final Expression arg = n.getArgument(0);
      final ResolutionResult res = resolveStringExpression(arg);
      if (res == null) {
        return;
      }
      final int lineNum = n.getBegin().map(pos -> pos.line).orElse(-1);
      final String targetClass = extractTargetClass(n);
      if (handleBranchCandidatesResolution(res, arg, lineNum, targetClass, patternName)) {
        return;
      }
      // Single value case (original logic)
      final String methodName = res.value();
      if (methodName == null) {
        return;
      }
      final int requestedParamCount = resolveRequestedParamCount(n);
      final ClassInfo targetInfo = resolveClassInfo(targetClass);
      final MethodVerification verification =
          verifyMethod(targetClass, targetInfo, methodName, requestedParamCount);
      final boolean arityMismatch = isMethodArityMismatch(verification, requestedParamCount);
      if (enableCandidateEnumeration && (arityMismatch || !verification.nameExists())) {
        emitMethodCandidates(
            lineNum, targetClass, targetInfo, requestedParamCount, methodName, patternName);
      }
      final double confidence = computeMethodConfidence(res, verification, arityMismatch);
      final DynamicReasonCode reasonCode =
          determineMethodReasonCode(targetClass, verification, arityMismatch);
      final Map<String, String> evidence =
          buildMethodEvidence(
              methodName, targetClass, patternName, verification, requestedParamCount);
      final DynamicResolution resolution =
          buildMethodResolution(
              lineNum, targetClass, methodName, confidence, reasonCode, res.ruleId(), evidence);
      resolutions.add(resolution);
      logMethodResolution(
          lineNum, methodName, confidence, verification.overloadCount(), reasonCode);
    }

    private boolean isMethodArityMismatch(
        final MethodVerification verification, final int requestedParamCount) {
      return verification.nameExists() && requestedParamCount >= 0 && !verification.arityMatches();
    }

    private double computeMethodConfidence(
        final ResolutionResult res,
        final MethodVerification verification,
        final boolean arityMismatch) {
      // Base confidence: 1.0 for literals, 0.8 for inferred
      final double baseConfidence = res.isLiteral() ? 1.0 : 0.8;
      final double penalty = computeMethodPenalty(verification, arityMismatch);
      return Math.max(0.0, baseConfidence - penalty);
    }

    private double computeMethodPenalty(
        final MethodVerification verification, final boolean arityMismatch) {
      if (arityMismatch) {
        return 0.1;
      }
      if (verification.nameExists()) {
        return 0.0;
      }
      return 0.2;
    }

    private DynamicReasonCode determineMethodReasonCode(
        final String targetClass,
        final MethodVerification verification,
        final boolean arityMismatch) {
      if (verification == null) {
        return null;
      }
      if (arityMismatch) {
        return DynamicReasonCode.TARGET_METHOD_MISSING;
      }
      if (verification.nameExists()) {
        return null;
      }
      if (isKnownClass(targetClass)) {
        return DynamicReasonCode.TARGET_METHOD_MISSING;
      }
      return DynamicReasonCode.TARGET_CLASS_UNRESOLVED;
    }

    private Map<String, String> buildMethodEvidence(
        final String methodName,
        final String targetClass,
        final String patternName,
        final MethodVerification verification,
        final int requestedParamCount) {
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_METHOD_LITERAL, methodName);
      evidence.put(EVIDENCE_TARGET_CLASS, targetClass != null ? targetClass : "unknown");
      evidence.put(EVIDENCE_PATTERN, patternName);
      evidence.put(EVIDENCE_VERIFIED, String.valueOf(verification.nameExists()));
      if (requestedParamCount >= 0) {
        evidence.put(EVIDENCE_PARAM_COUNT, String.valueOf(requestedParamCount));
        evidence.put(EVIDENCE_ARITY_MATCH, String.valueOf(verification.arityMatches()));
      }
      if (verification.overloadCount() > 1) {
        evidence.put(EVIDENCE_OVERLOAD_COUNT, String.valueOf(verification.overloadCount()));
      }
      return evidence;
    }

    private DynamicResolution buildMethodResolution(
        final int lineNum,
        final String targetClass,
        final String methodName,
        final double confidence,
        final DynamicReasonCode reasonCode,
        final ResolutionRuleId ruleId,
        final Map<String, String> evidence) {
      return DynamicResolution.builder()
          .subtype(DynamicResolution.METHOD_RESOLVE)
          .filePath(filePath)
          .classFqn(classFqn)
          .methodSig(currentMethodSignature)
          .lineStart(lineNum)
          .resolvedClassFqn( // Can be null
              targetClass)
          .resolvedMethodSig(methodName)
          .confidence(confidence)
          .trustLevel(TrustLevel.fromConfidence(confidence))
          .reasonCode(reasonCode)
          .ruleId(ruleId)
          .evidence(evidence)
          .build();
    }

    private void logMethodResolution(
        final int lineNum,
        final String methodName,
        final double confidence,
        final int overloadCount,
        final DynamicReasonCode reasonCode) {
      if (!debugMode) {
        return;
      }
      Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
      Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.METHOD_RESOLVE);
      Logger.debug(LOG_RESOLVED_PREFIX + methodName);
      Logger.debug(LOG_CONFIDENCE_PREFIX + confidence);
      if (reasonCode != null) {
        Logger.debug(LOG_REASON_CODE_PREFIX + reasonCode);
      }
      if (overloadCount > 1) {
        Logger.debug(LOG_OVERLOADS_PREFIX + overloadCount);
      }
    }

    private void resolveGetField(final MethodCallExpr n, final String patternName) {
      if (n.getArguments().isEmpty()) {
        return;
      }
      final Expression arg = n.getArgument(0);
      final ResolutionResult res = resolveStringExpression(arg);
      if (res == null) {
        return;
      }
      final int lineNum = n.getBegin().map(pos -> pos.line).orElse(-1);
      final String targetClass = extractTargetClass(n);
      if (handleBranchCandidatesResolution(res, arg, lineNum, targetClass, patternName)) {
        return;
      }
      final String fieldName = res.value();
      if (fieldName == null) {
        return;
      }
      final double baseConfidence = res.isLiteral() ? 1.0 : 0.8;
      final boolean exists = fieldExists(targetClass, fieldName);
      final double penalty = exists ? 0.0 : 0.2;
      final double confidence = Math.max(0.0, baseConfidence - penalty);
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_FIELD_LITERAL, fieldName);
      evidence.put(EVIDENCE_TARGET_CLASS, targetClass != null ? targetClass : "unknown");
      evidence.put(EVIDENCE_PATTERN, patternName);
      evidence.put(EVIDENCE_VERIFIED, String.valueOf(exists));
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.FIELD_RESOLVE)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .resolvedClassFqn(targetClass)
              .resolvedMethodSig(fieldName)
              .confidence(confidence)
              .trustLevel(TrustLevel.fromConfidence(confidence))
              .ruleId(res.ruleId())
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.FIELD_RESOLVE);
        Logger.debug(LOG_RESOLVED_PREFIX + fieldName);
        Logger.debug(LOG_CONFIDENCE_PREFIX + confidence);
      }
    }

    private int resolveRequestedParamCount(final MethodCallExpr n) {
      if (n.getArguments().size() <= 1) {
        return 0;
      }
      if (n.getArguments().size() > 2) {
        return n.getArguments().size() - 1;
      }
      final Expression paramArg = n.getArgument(1);
      if (paramArg.isArrayCreationExpr()) {
        final com.github.javaparser.ast.expr.ArrayCreationExpr arrayExpr =
            paramArg.asArrayCreationExpr();
        if (arrayExpr.getInitializer().isPresent()) {
          return arrayExpr.getInitializer().get().getValues().size();
        }
        return -1;
      }
      if (paramArg.isArrayInitializerExpr()) {
        return paramArg.asArrayInitializerExpr().getValues().size();
      }
      if (paramArg.isClassExpr()) {
        return 1;
      }
      if (paramArg.isNullLiteralExpr()) {
        return -1;
      }
      return -1;
    }

    private ClassInfo resolveClassInfo(final String targetClass) {
      if (targetClass == null) {
        return null;
      }
      final ClassInfo direct = fqnToClassInfo.get(targetClass);
      if (direct != null) {
        return direct;
      }
      for (final Map.Entry<String, ClassInfo> entry : fqnToClassInfo.entrySet()) {
        final String fqn = entry.getKey();
        if (fqn.equals(targetClass) || fqn.endsWith("." + targetClass)) {
          return entry.getValue();
        }
      }
      return null;
    }

    private boolean fieldExists(final String targetClass, final String fieldName) {
      if (fieldName == null || fieldName.isBlank()) {
        return false;
      }
      final ClassInfo targetInfo = resolveClassInfo(targetClass);
      if (targetInfo != null) {
        return targetInfo.getFields().stream().anyMatch(field -> fieldName.equals(field.getName()));
      }
      if (projectSymbolIndex != null) {
        return projectSymbolIndex.hasField(targetClass, fieldName);
      }
      final String suffix = "#" + fieldName;
      return knownFields.stream().anyMatch(f -> f.endsWith(suffix));
    }

    private void emitClassCandidates(
        final int lineNum, final String literal, final String patternName) {
      final List<String> candidates = enumerateClassCandidates(literal);
      if (candidates.isEmpty()) {
        return;
      }
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_PATTERN, patternName);
      evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_EXPERIMENTAL);
      if (literal != null && !literal.isBlank()) {
        evidence.put(EVIDENCE_LITERAL, literal);
      }
      evidence.put(EVIDENCE_CANDIDATE_COUNT, String.valueOf(candidates.size()));
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.EXPERIMENTAL_CANDIDATES)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .candidates(candidates)
              .confidence(EXPERIMENTAL_CANDIDATE_CONFIDENCE)
              .trustLevel(TrustLevel.fromConfidence(EXPERIMENTAL_CANDIDATE_CONFIDENCE))
              .reasonCode(DynamicReasonCode.AMBIGUOUS_CANDIDATES)
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.EXPERIMENTAL_CANDIDATES);
        Logger.debug(LOG_CANDIDATES_PREFIX + candidates);
        Logger.debug(LOG_CONFIDENCE_PREFIX + resolution.confidence());
      }
    }

    private void emitMethodCandidates(
        final int lineNum,
        final String targetClass,
        final ClassInfo targetInfo,
        final int requestedParamCount,
        final String methodName,
        final String patternName) {
      final List<String> candidates = enumerateMethodCandidates(targetInfo, requestedParamCount);
      if (candidates.isEmpty()) {
        return;
      }
      final Map<String, String> evidence = new LinkedHashMap<>();
      evidence.put(EVIDENCE_PATTERN, patternName);
      evidence.put(EVIDENCE_PROVENANCE, PROVENANCE_EXPERIMENTAL);
      evidence.put(EVIDENCE_TARGET_CLASS, targetClass != null ? targetClass : "unknown");
      evidence.put(EVIDENCE_METHOD_LITERAL, methodName);
      evidence.put(EVIDENCE_CANDIDATE_COUNT, String.valueOf(candidates.size()));
      if (requestedParamCount >= 0) {
        evidence.put(EVIDENCE_PARAM_COUNT, String.valueOf(requestedParamCount));
      }
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.EXPERIMENTAL_CANDIDATES)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .candidates(candidates)
              .confidence(EXPERIMENTAL_CANDIDATE_CONFIDENCE)
              .trustLevel(TrustLevel.fromConfidence(EXPERIMENTAL_CANDIDATE_CONFIDENCE))
              .reasonCode(DynamicReasonCode.AMBIGUOUS_CANDIDATES)
              .evidence(evidence)
              .build();
      resolutions.add(resolution);
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.EXPERIMENTAL_CANDIDATES);
        Logger.debug(LOG_CANDIDATES_PREFIX + candidates);
        Logger.debug(LOG_CONFIDENCE_PREFIX + resolution.confidence());
      }
    }

    private List<String> enumerateClassCandidates(final String literal) {
      if (literal == null || literal.isBlank()) {
        return List.of();
      }
      final int lastDot = literal.lastIndexOf('.');
      final String simpleName = lastDot >= 0 ? literal.substring(lastDot + 1) : literal;
      if (simpleName.isBlank()) {
        return List.of();
      }
      final List<String> candidates = new ArrayList<>();
      if (projectSymbolIndex != null) {
        candidates.addAll(projectSymbolIndex.findClassCandidates(simpleName, MAX_CANDIDATES));
      }
      if (candidates.size() < MAX_CANDIDATES) {
        knownClasses.stream()
            .filter(name -> name.endsWith("." + simpleName) || name.equals(simpleName))
            .sorted()
            .limit(MAX_CANDIDATES - candidates.size())
            .forEach(candidates::add);
      }
      return candidates.stream().distinct().limit(MAX_CANDIDATES).toList();
    }

    private boolean isKnownClass(final String className) {
      if (className == null || className.isBlank()) {
        return false;
      }
      if (projectSymbolIndex != null && projectSymbolIndex.hasClass(className)) {
        return true;
      }
      return knownClasses.contains(className);
    }

    private List<String> enumerateMethodCandidates(
        final ClassInfo targetInfo, final int requestedParamCount) {
      if (targetInfo == null) {
        return List.of();
      }
      return targetInfo.getMethods().stream()
          .filter(method -> method.getName() != null && !method.getName().isBlank())
          .filter(
              method ->
                  requestedParamCount < 0 || method.getParameterCount() == requestedParamCount)
          .map(MethodInfo::getName)
          .distinct()
          .sorted()
          .limit(MAX_CANDIDATES)
          .toList();
    }

    private void trackLocalStringConstant(final String variableName, final Expression expression) {
      final ResolutionResult result = resolveStringExpression(expression);
      if (result != null && result.value() != null) {
        localConstants.put(variableName, result.value());
      } else {
        localConstants.remove(variableName);
      }
    }

    private void trackLocalClassTarget(final String variableName, final Expression expression) {
      final String resolved = resolveClassTargetExpression(expression);
      if (resolved != null && !resolved.isBlank()) {
        localClassTargets.put(variableName, resolved);
      } else {
        localClassTargets.remove(variableName);
      }
    }

    private String resolveClassTargetExpression(final Expression expression) {
      if (expression == null) {
        return null;
      }
      if (expression.isEnclosedExpr()) {
        return resolveClassTargetExpression(expression.asEnclosedExpr().getInner());
      }
      if (expression.isClassExpr()) {
        return normalizeClassTarget(expression.asClassExpr().getType().asString());
      }
      if (expression.isNameExpr()) {
        return normalizeClassTarget(
            localClassTargets.get(expression.asNameExpr().getNameAsString()));
      }
      if (expression.isMethodCallExpr()) {
        final MethodCallExpr call = expression.asMethodCallExpr();
        if ("forName".equals(call.getNameAsString())
            && call.getScope().isPresent()
            && "Class".equals(call.getScope().get().toString())
            && !call.getArguments().isEmpty()) {
          final ResolutionResult resolvedClass = resolveStringExpression(call.getArgument(0));
          if (resolvedClass != null && resolvedClass.value() != null) {
            return normalizeClassTarget(resolvedClass.value());
          }
        }
      }
      return null;
    }

    private String normalizeClassTarget(final String className) {
      if (className == null || className.isBlank()) {
        return null;
      }
      final String resolved = resolveScopeToClassFqn(className);
      return resolved == null || resolved.isBlank() ? className : resolved;
    }

    private String extractTargetClass(final MethodCallExpr n) {
      return n.getScope().map(this::resolveClassTargetExpression).orElse(null);
    }

    private void resolveServiceLoader(final MethodCallExpr n) {
      if (n.getArguments().isEmpty()) {
        return;
      }
      final Expression arg = n.getArgument(0);
      if (!arg.isClassExpr()) {
        return;
      }
      final String serviceName = arg.asClassExpr().getType().asString();
      final int lineNum = n.getBegin().map(pos -> pos.line).orElse(-1);
      resolveServiceLoaderForService(serviceName, lineNum);
    }

    private void resolveServiceLoaderForService(final String serviceName, final int lineNum) {
      final String serviceFqn = resolveServiceFqn(serviceName);
      final List<String> spiProviders = findServiceProviders(projectRoot, serviceFqn);
      final Set<String> candidates = new HashSet<>(spiProviders);
      resolveCandidatesFromMap(candidates, serviceName, serviceFqn);
      final List<String> concreteCandidates = filterConcreteCandidates(candidates);
      final ServiceResolution serviceResolution =
          evaluateServiceCandidates(spiProviders, concreteCandidates);
      final DynamicResolution resolution =
          DynamicResolution.builder()
              .subtype(DynamicResolution.SERVICELOADER_PROVIDERS)
              .filePath(filePath)
              .classFqn(classFqn)
              .methodSig(currentMethodSignature)
              .lineStart(lineNum)
              .resolvedClassFqn(serviceFqn)
              .providers(concreteCandidates)
              .confidence(serviceResolution.confidence())
              .trustLevel(TrustLevel.fromConfidence(serviceResolution.confidence()))
              .reasonCode(serviceResolution.reasonCode())
              .evidence(
                  Map.of(
                      EVIDENCE_SERVICE_CLASS,
                      serviceFqn,
                      EVIDENCE_PROVIDERS_FOUND,
                      String.valueOf(concreteCandidates.size()),
                      EVIDENCE_EXPLICIT_SPI,
                      String.valueOf(!spiProviders.isEmpty())))
              .build();
      resolutions.add(resolution);
      if (debugMode) {
        Logger.debug(LOG_RESOLUTION_PREFIX + filePath + ":" + lineNum);
        Logger.debug(LOG_TYPE_PREFIX + DynamicResolution.SERVICELOADER_PROVIDERS);
        Logger.debug(LOG_SERVICE_PREFIX + serviceFqn);
        Logger.debug(LOG_PROVIDERS_PREFIX + formatCandidatesForLog(concreteCandidates));
        Logger.debug(LOG_CONFIDENCE_PREFIX + serviceResolution.confidence());
        if (serviceResolution.reasonCode() != null) {
          Logger.debug(LOG_REASON_CODE_PREFIX + serviceResolution.reasonCode());
        }
      }
    }

    private String resolveServiceFqn(final String serviceName) {
      if (serviceName.contains(".")) {
        return serviceName;
      }
      for (final String known : fqnToClassInfo.keySet()) {
        if (known.endsWith("." + serviceName) || known.equals(serviceName)) {
          return known;
        }
      }
      return serviceName;
    }

    private List<String> filterConcreteCandidates(final Set<String> candidates) {
      return candidates.stream()
          .filter(this::isConcreteImplementation)
          .distinct()
          .sorted()
          .toList();
    }

    private boolean isConcreteImplementation(final String fqn) {
      final ClassInfo info = fqnToClassInfo.get(fqn);
      if (info == null) {
        return true;
      }
      return !info.isInterface() && !info.isAbstract();
    }

    private record ServiceResolution(double confidence, DynamicReasonCode reasonCode) {}

    private ServiceResolution evaluateServiceCandidates(
        final List<String> spiProviders, final List<String> concreteCandidates) {
      if (!spiProviders.isEmpty()) {
        return new ServiceResolution(1.0, null);
      }
      if (concreteCandidates.size() == 1) {
        return new ServiceResolution(1.0, null);
      }
      if (concreteCandidates.size() > 1) {
        return new ServiceResolution(0.8, DynamicReasonCode.AMBIGUOUS_CANDIDATES);
      }
      return new ServiceResolution(0.3, DynamicReasonCode.UNRESOLVED_DEPENDENCY);
    }

    private void resolveCandidatesFromMap(
        final Set<String> candidates, final String serviceName, final String serviceFqn) {
      // Look up by simple name first (as our map keys might be simple or FQN)
      if (interfaceToImplementations.containsKey(serviceName)) {
        candidates.addAll(interfaceToImplementations.get(serviceName));
      }
      // Also check by FQN if different
      if (!serviceName.equals(serviceFqn) && interfaceToImplementations.containsKey(serviceFqn)) {
        candidates.addAll(interfaceToImplementations.get(serviceFqn));
      }
    }
  }

  // Copied helper from previous implementation
  private List<String> findServiceProviders(final Path projectRoot, final String serviceClass) {
    final List<Path> searchPaths =
        List.of(
            projectRoot.resolve("src/main/resources/META-INF/services"),
            projectRoot.resolve("resources/META-INF/services"),
            projectRoot.resolve("META-INF/services"));
    for (final Path searchPath : searchPaths) {
      final Path serviceFile = searchPath.resolve(serviceClass);
      final List<String> providers = readProvidersFromFile(serviceFile);
      if (!providers.isEmpty()) {
        return providers;
      }
    }
    return Collections.emptyList();
  }

  private List<String> readProvidersFromFile(final Path serviceFile) {
    if (!Files.exists(serviceFile)) {
      return Collections.emptyList();
    }
    final List<String> providers = new ArrayList<>();
    try {
      final List<String> lines = Files.readAllLines(serviceFile);
      for (final String line : lines) {
        final String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          providers.add(trimmed);
        }
      }
    } catch (java.io.IOException e) {
      Logger.debug(
          MessageSource.getMessage(
              "analysis.dynamic_resolver.read_service_file_failed", serviceFile));
    }
    return providers;
  }

  /** Returns all resolutions. */
  public List<DynamicResolution> getResolutions() {
    return new ArrayList<>(resolutions);
  }

  /** Calculate average confidence. */
  public double getAverageConfidence() {
    if (resolutions.isEmpty()) {
      return 0.0;
    }
    return resolutions.stream().mapToDouble(DynamicResolution::confidence).average().orElse(0.0);
  }

  /** Count resolutions by subtype. */
  public Map<String, Long> countBySubtype() {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final DynamicResolution r : resolutions) {
      counts.merge(r.subtype(), 1L, (a, b) -> a + b);
    }
    return counts;
  }

  /** Count resolutions by trust level. */
  public Map<String, Long> countByTrustLevel() {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final DynamicResolution r : resolutions) {
      final TrustLevel level = r.trustLevel();
      if (level == null) {
        continue;
      }
      counts.merge(level.name(), 1L, (a, b) -> a + b);
    }
    return counts;
  }
}
