package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.AstUtils;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts a compact branch summary and representative paths from method source to guide test
 * generation.
 */
public class BranchSummaryExtractor {

  private static final int DEFAULT_PATH_LIMIT = 16;

  private static final String PATH_ID_PREFIX = "path-";

  private static final Pattern EMPTY_OR_BLANK_CALL_PATTERN =
      Pattern.compile("\\b(?:isEmpty|isBlank)\\s*\\(", Pattern.CASE_INSENSITIVE);

  private static final Pattern FAILURE_KEYWORD_PATTERN =
      Pattern.compile("\\b(?:invalid|error|fail)\\b", Pattern.CASE_INSENSITIVE);

  public BranchSummaryResult compute(final MethodInfo method) {
    if (method == null || method.getSourceCode() == null || method.getSourceCode().isBlank()) {
      return new BranchSummaryResult(
          null, List.of(), false, "Missing source_code for branch summary extraction");
    }
    final ParseAttempt parseAttempt = parseMethod(method.getSourceCode());
    final Optional<MethodDeclaration> optionalMethodDeclaration = parseAttempt.methodDeclaration();
    if (optionalMethodDeclaration.isEmpty()) {
      if (isTrivialMethod(method)) {
        final BranchSummary summary = new BranchSummary();
        method.setBranchSummary(summary);
        method.setRepresentativePaths(List.of());
        return new BranchSummaryResult(
            summary,
            method.getRepresentativePaths(),
            parseAttempt.usedFallback(),
            parseAttempt.errorMessage());
      }
      return new BranchSummaryResult(
          null, List.of(), parseAttempt.usedFallback(), parseAttempt.errorMessage());
    }
    final MethodDeclaration methodDeclaration = optionalMethodDeclaration.get();
    final BranchSummary summary = new BranchSummary();
    final RepresentativePathBuilder pathBuilder = new RepresentativePathBuilder(DEFAULT_PATH_LIMIT);
    collectIfStatements(methodDeclaration, summary, pathBuilder);
    collectSwitches(methodDeclaration, summary, pathBuilder);
    collectPredicates(methodDeclaration, summary, pathBuilder);
    pathBuilder.addSuccessPathIfNeeded();
    method.setBranchSummary(summary);
    method.setRepresentativePaths(pathBuilder.build());
    return new BranchSummaryResult(
        summary,
        method.getRepresentativePaths(),
        parseAttempt.usedFallback(),
        parseAttempt.errorMessage());
  }

  private ParseAttempt parseMethod(final String source) {
    final JavaParser parser =
        new JavaParser(
            new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
    final com.github.javaparser.ParseResult<com.github.javaparser.ast.body.BodyDeclaration<?>>
        result = parser.parseBodyDeclaration(source);
    if (result.isSuccessful() && result.getResult().isPresent()) {
      final com.github.javaparser.ast.body.BodyDeclaration<?> bodyDeclaration =
          result.getResult().get();
      if (bodyDeclaration instanceof MethodDeclaration md) {
        return new ParseAttempt(Optional.of(md), false, null);
      }
      if (bodyDeclaration instanceof ConstructorDeclaration cd) {
        return new ParseAttempt(Optional.of(convertConstructor(cd)), false, null);
      }
    }
    final String parseErrorMessage =
        result.getProblems().isEmpty()
            ? "Unable to parse method for branch summary"
            : result.getProblems().toString();
    // Fallback: wrap in dummy class to parse full method text
    final String wrapped = "class Dummy { " + source + " }";
    final Optional<MethodDeclaration> method =
        parser.parse(wrapped).getResult().flatMap(cu -> cu.findFirst(MethodDeclaration.class));
    if (method.isPresent()) {
      return new ParseAttempt(Optional.of(method.get()), true, parseErrorMessage);
    }
    final Optional<ConstructorDeclaration> ctor =
        parser.parse(wrapped).getResult().flatMap(cu -> cu.findFirst(ConstructorDeclaration.class));
    if (ctor.isPresent()) {
      return new ParseAttempt(Optional.of(convertConstructor(ctor.get())), true, parseErrorMessage);
    }
    return new ParseAttempt(Optional.empty(), false, parseErrorMessage);
  }

  private MethodDeclaration convertConstructor(final ConstructorDeclaration ctor) {
    final MethodDeclaration synthetic = new MethodDeclaration();
    synthetic.setName(ctor.getName());
    synthetic.setBody(ctor.getBody().clone());
    final com.github.javaparser.ast.NodeList<com.github.javaparser.ast.body.Parameter> params =
        new com.github.javaparser.ast.NodeList<>();
    ctor.getParameters().forEach(p -> params.add(p.clone()));
    synthetic.setParameters(params);
    synthetic.setType("void");
    return synthetic;
  }

  private void collectIfStatements(
      final MethodDeclaration md,
      final BranchSummary summary,
      final RepresentativePathBuilder pathBuilder) {
    final List<GuardSummary> guards = new ArrayList<>();
    md.findAll(IfStmt.class).stream()
        .sorted(Comparator.comparing(this::positionKey))
        .forEach(
            ifStmt -> {
              final Optional<GuardResult> thenGuard =
                  buildGuardResult(ifStmt, ifStmt.getThenStmt(), false);
              final Optional<GuardResult> elseGuard =
                  ifStmt
                      .getElseStmt()
                      .flatMap(elseStmt -> buildGuardResult(ifStmt, elseStmt, true));
              thenGuard.ifPresent(
                  gr -> {
                    guards.add(gr.summary());
                    addPathForGuard(pathBuilder, gr);
                  });
              elseGuard.ifPresent(
                  gr -> {
                    guards.add(gr.summary());
                    addPathForGuard(pathBuilder, gr);
                  });
            });
    summary.setGuards(dedupGuards(guards));
  }

  private String positionKey(final Node node) {
    if (node == null) {
      return "";
    }
    return node.getBegin()
        .map(pos -> String.format("%08d:%04d", pos.line, pos.column))
        .orElse(node.toString());
  }

  private void collectSwitches(
      final MethodDeclaration md,
      final BranchSummary summary,
      final RepresentativePathBuilder pathBuilder) {
    final Set<String> switches = new LinkedHashSet<>();
    md.findAll(com.github.javaparser.ast.stmt.SwitchStmt.class)
        .forEach(sw -> collectSwitch(sw.getSelector(), sw.getEntries(), switches, pathBuilder));
    md.findAll(com.github.javaparser.ast.expr.SwitchExpr.class)
        .forEach(sw -> collectSwitch(sw.getSelector(), sw.getEntries(), switches, pathBuilder));
    summary.setSwitches(new ArrayList<>(switches));
  }

  private void collectSwitch(
      final Expression selector,
      final com.github.javaparser.ast.NodeList<com.github.javaparser.ast.stmt.SwitchEntry> entries,
      final Set<String> switches,
      final RepresentativePathBuilder pathBuilder) {
    final List<String> labels = new ArrayList<>();
    for (final com.github.javaparser.ast.stmt.SwitchEntry entry : entries) {
      if (entry.getLabels().isEmpty()) {
        labels.add("default");
      } else {
        entry.getLabels().forEach(l -> labels.add(l.toString()));
      }
    }
    final String selectorText = selector.toString();
    switches.add(selectorText + ": " + String.join("/", labels));
    labels.forEach(label -> pathBuilder.addSwitchPath(selectorText, label));
  }

  private void collectPredicates(
      final MethodDeclaration md,
      final BranchSummary summary,
      final RepresentativePathBuilder pathBuilder) {
    final Set<String> predicates = new LinkedHashSet<>();
    final Set<String> parameterNames = collectParameterNames(md);
    for (final Expression condition : collectConditionalExpressions(md)) {
      final String normalizedCondition = normalize(condition);
      if (!normalizedCondition.isBlank()) {
        predicates.add(normalizedCondition);
      }
    }
    final List<BinaryExpr> comparisons =
        md.findAll(BinaryExpr.class).stream()
            .filter(this::isComparison)
            .sorted(Comparator.comparing(this::positionKey).thenComparing(Object::toString))
            .toList();
    for (final BinaryExpr comparison : comparisons) {
      final String normalizedPredicate = normalize(comparison);
      predicates.add(normalizedPredicate);
      if (!shouldCreateBoundaryRepresentativePath(
          comparison, normalizedPredicate, parameterNames)) {
        continue;
      }
      pathBuilder.addBoundaryPath(normalizedPredicate);
    }
    summary.setPredicates(new ArrayList<>(predicates));
  }

  private List<Expression> collectConditionalExpressions(final MethodDeclaration md) {
    final List<Expression> expressions = new ArrayList<>();
    if (md == null) {
      return expressions;
    }
    md.findAll(IfStmt.class).stream()
        .sorted(Comparator.comparing(this::positionKey))
        .forEach(ifStmt -> expressions.add(ifStmt.getCondition()));
    md.findAll(WhileStmt.class).stream()
        .sorted(Comparator.comparing(this::positionKey))
        .forEach(whileStmt -> expressions.add(whileStmt.getCondition()));
    md.findAll(DoStmt.class).stream()
        .sorted(Comparator.comparing(this::positionKey))
        .forEach(doStmt -> expressions.add(doStmt.getCondition()));
    md.findAll(ForStmt.class).stream()
        .sorted(Comparator.comparing(this::positionKey))
        .map(ForStmt::getCompare)
        .flatMap(Optional::stream)
        .forEach(expressions::add);
    return expressions;
  }

  private Set<String> collectParameterNames(final MethodDeclaration md) {
    final Set<String> names = new LinkedHashSet<>();
    if (md == null) {
      return names;
    }
    for (final com.github.javaparser.ast.body.Parameter parameter : md.getParameters()) {
      if (parameter != null && parameter.getNameAsString() != null) {
        final String name = parameter.getNameAsString().strip();
        if (!name.isBlank()) {
          names.add(name);
        }
      }
    }
    return names;
  }

  private boolean shouldCreateBoundaryRepresentativePath(
      final BinaryExpr predicateExpr, final String predicate, final Set<String> parameterNames) {
    if (predicate == null || predicate.isBlank()) {
      return false;
    }
    if (isPositiveNonNullPredicate(predicate)) {
      return false;
    }
    if (parameterNames == null || parameterNames.isEmpty()) {
      return false;
    }
    if (!referencesInputParameter(predicateExpr, parameterNames)) {
      return false;
    }
    return !isLoopConditionPredicate(predicateExpr);
  }

  private boolean referencesInputParameter(
      final BinaryExpr predicateExpr, final Set<String> parameterNames) {
    if (predicateExpr == null || parameterNames == null || parameterNames.isEmpty()) {
      return false;
    }
    return predicateExpr.findAll(NameExpr.class).stream()
        .map(NameExpr::getNameAsString)
        .map(name -> name == null ? "" : name.strip())
        .anyMatch(name -> !name.isEmpty() && parameterNames.contains(name));
  }

  private boolean isLoopConditionPredicate(final BinaryExpr predicateExpr) {
    if (predicateExpr == null) {
      return false;
    }
    final boolean inFor =
        AstUtils.findAncestor(predicateExpr, ForStmt.class)
            .flatMap(ForStmt::getCompare)
            .map(compare -> isWithinExpression(predicateExpr, compare))
            .orElse(false);
    if (inFor) {
      return true;
    }
    final boolean inWhile =
        AstUtils.findAncestor(predicateExpr, WhileStmt.class)
            .map(stmt -> isWithinExpression(predicateExpr, stmt.getCondition()))
            .orElse(false);
    if (inWhile) {
      return true;
    }
    return AstUtils.findAncestor(predicateExpr, DoStmt.class)
        .map(stmt -> isWithinExpression(predicateExpr, stmt.getCondition()))
        .orElse(false);
  }

  private boolean isWithinExpression(final Expression target, final Expression root) {
    if (target == null || root == null) {
      return false;
    }
    Node current = target;
    while (current != null) {
      if (isSameAstNode(current, root)) {
        return true;
      }
      current = current.getParentNode().orElse(null);
    }
    return false;
  }

  private boolean isPositiveNonNullPredicate(final String predicate) {
    if (predicate == null || predicate.isBlank()) {
      return false;
    }
    final String normalized = predicate.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    final boolean positiveNonNull = normalized.contains("!= null") || normalized.contains("!=null");
    if (!positiveNonNull) {
      return false;
    }
    return !normalized.contains("== null")
        && !normalized.contains("==null")
        && !normalized.contains(" is null")
        && !normalized.contains("cannot be null")
        && !normalized.contains("must not be null");
  }

  private boolean isComparison(final BinaryExpr binaryExpr) {
    return switch (binaryExpr.getOperator()) {
      case EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS -> true;
      default -> false;
    };
  }

  private String normalize(final Expression expression) {
    return expression.toString().replaceAll("\\s+", " ").trim();
  }

  private List<GuardSummary> dedupGuards(final List<GuardSummary> guards) {
    final java.util.LinkedHashMap<String, GuardSummary> deduped = new java.util.LinkedHashMap<>();
    for (final GuardSummary guard : guards) {
      final String key =
          guard.getType() + "|" + guard.getCondition() + "|" + guard.getMessageLiteral();
      deduped.putIfAbsent(key, guard);
    }
    return new ArrayList<>(deduped.values());
  }

  private Optional<GuardResult> buildGuardResult(
      final IfStmt ifStmt,
      final com.github.javaparser.ast.stmt.Statement block,
      final boolean fromElse) {
    final GuardDetection detection = detectGuard(block, ifStmt, isInLoop(ifStmt));
    final String condition =
        fromElse
            ? normalize(negateExpression(ifStmt.getCondition()))
            : normalize(ifStmt.getCondition());
    final boolean throwExit = detection.hasThrow;
    boolean earlyExit = throwExit;
    if (detection.hasReturn) {
      if (detection.type == null) {
        if (!isLikelyFailureCondition(condition)) {
          return Optional.empty();
        }
        detection.type = GuardType.FAIL_GUARD;
      }
      earlyExit = true;
    }
    if (detection.type == null && detection.hasThrow) {
      detection.type = GuardType.FAIL_GUARD;
    }
    if (!detection.hasEffect()) {
      return Optional.empty();
    }
    final GuardSummary summary = new GuardSummary();
    summary.setCondition(condition);
    summary.setEffects(new ArrayList<>(detection.effects));
    summary.setType(detection.type);
    summary.setMessageLiteral(detection.message);
    summary.setLocation(locationOf(ifStmt));
    return Optional.of(new GuardResult(summary, earlyExit, throwExit));
  }

  private GuardDetection detectGuard(
      final com.github.javaparser.ast.stmt.Statement stmt,
      final IfStmt parentIf,
      final boolean inLoop) {
    final GuardDetection detection = new GuardDetection();
    final boolean setsFailure = detectsFailureAssignment(stmt, parentIf);
    final Optional<String> messageLiteral = extractMessageLiteral(stmt, parentIf);
    final boolean hasMessage = messageLiteral.isPresent();
    final boolean hasContinue =
        !stmt.findAll(
                com.github.javaparser.ast.stmt.ContinueStmt.class,
                n -> belongsToParentIf(n, parentIf))
            .isEmpty();
    final boolean hasBreak =
        !stmt.findAll(
                com.github.javaparser.ast.stmt.BreakStmt.class, n -> belongsToParentIf(n, parentIf))
            .isEmpty();
    classifyLoopGuard(detection, inLoop, hasBreak, hasContinue, setsFailure, hasMessage);
    applyFailureEffects(detection, setsFailure, hasMessage, messageLiteral);
    detectReturnOrThrow(detection, stmt, parentIf);
    return detection;
  }

  private boolean detectsFailureAssignment(
      final com.github.javaparser.ast.stmt.Statement stmt, final IfStmt parentIf) {
    return stmt
        .findAll(
            com.github.javaparser.ast.expr.AssignExpr.class, n -> belongsToParentIf(n, parentIf))
        .stream()
        .anyMatch(
            a ->
                "result.success".equals(a.getTarget().toString())
                    && a.getValue().isBooleanLiteralExpr()
                    && !a.getValue().asBooleanLiteralExpr().getValue());
  }

  private Optional<String> extractMessageLiteral(
      final com.github.javaparser.ast.stmt.Statement stmt, final IfStmt parentIf) {
    return stmt
        .findAll(
            com.github.javaparser.ast.expr.MethodCallExpr.class,
            n -> belongsToParentIf(n, parentIf))
        .stream()
        .filter(mc -> "add".equals(mc.getNameAsString()))
        .filter(mc -> mc.getScope().map(Expression::toString).orElse("").contains("errorMessages"))
        .filter(mc -> !mc.getArguments().isEmpty())
        .map(mc -> mc.getArgument(0))
        .filter(Expression::isStringLiteralExpr)
        .map(Expression::asStringLiteralExpr)
        .map(com.github.javaparser.ast.expr.StringLiteralExpr::asString)
        .findFirst();
  }

  private void classifyLoopGuard(
      final GuardDetection detection,
      final boolean inLoop,
      final boolean hasBreak,
      final boolean hasContinue,
      final boolean setsFailure,
      final boolean hasMessage) {
    if (inLoop && hasBreak && (setsFailure || hasMessage)) {
      detection.type = GuardType.LOOP_GUARD_BREAK;
      detection.effects.add("break");
    } else if (inLoop && hasContinue) {
      detection.type = GuardType.LOOP_GUARD_CONTINUE;
      detection.effects.add("continue");
    }
  }

  private void applyFailureEffects(
      final GuardDetection detection,
      final boolean setsFailure,
      final boolean hasMessage,
      final Optional<String> messageLiteral) {
    if (setsFailure) {
      detection.effects.add("success=false");
      if (detection.type == null) {
        detection.type = GuardType.FAIL_GUARD;
      }
    }
    if (hasMessage) {
      detection.effects.add("addErrorMessage");
      detection.message = messageLiteral.orElse(null);
      detection.type = detection.type == null ? GuardType.MESSAGE_GUARD : detection.type;
    }
  }

  private void detectReturnOrThrow(
      final GuardDetection detection,
      final com.github.javaparser.ast.stmt.Statement stmt,
      final IfStmt parentIf) {
    detection.hasReturn =
        !stmt.findAll(
                com.github.javaparser.ast.stmt.ReturnStmt.class,
                n -> belongsToParentIf(n, parentIf))
            .isEmpty();
    detection.hasThrow =
        !stmt.findAll(
                com.github.javaparser.ast.stmt.ThrowStmt.class, n -> belongsToParentIf(n, parentIf))
            .isEmpty();
    if (detection.type == null && detection.hasThrow) {
      detection.type = GuardType.FAIL_GUARD;
    }
  }

  private boolean isLikelyFailureCondition(final String condition) {
    if (condition == null || condition.isBlank()) {
      return false;
    }
    final String normalized = condition.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return normalized.contains("==null")
        || normalized.contains("<=0")
        || normalized.contains("<0")
        || normalized.contains("!isvalid")
        || EMPTY_OR_BLANK_CALL_PATTERN.matcher(condition).find()
        || FAILURE_KEYWORD_PATTERN.matcher(condition).find();
  }

  private void addPathForGuard(
      final RepresentativePathBuilder pathBuilder, final GuardResult guardResult) {
    final GuardSummary guard = guardResult.summary();
    if (guard.getType() != null && guard.getType().isRepresentativePathGuard()) {
      pathBuilder.addGuardPath(guardResult);
    }
  }

  private String locationOf(final NodeWithRange<?> node) {
    return node.getBegin().map(pos -> pos.line + ":" + pos.column).orElse(null);
  }

  private boolean isInLoop(final IfStmt stmt) {
    return AstUtils.findAncestor(stmt, ForStmt.class).isPresent()
        || AstUtils.findAncestor(stmt, com.github.javaparser.ast.stmt.ForEachStmt.class).isPresent()
        || AstUtils.findAncestor(stmt, WhileStmt.class).isPresent()
        || AstUtils.findAncestor(stmt, DoStmt.class).isPresent();
  }

  private Expression negateExpression(final Expression expr) {
    if (expr instanceof BinaryExpr be) {
      return negateBinary(be);
    }
    if (expr instanceof UnaryExpr ue && ue.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
      return ue.getExpression();
    }
    return new UnaryExpr(expr.clone(), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
  }

  private Expression negateBinary(final BinaryExpr binaryExpr) {
    final BinaryExpr.Operator op = binaryExpr.getOperator();
    final Expression left = binaryExpr.getLeft().clone();
    final Expression right = binaryExpr.getRight().clone();
    return switch (op) {
      case EQUALS -> new BinaryExpr(left, right, BinaryExpr.Operator.NOT_EQUALS);
      case NOT_EQUALS -> new BinaryExpr(left, right, BinaryExpr.Operator.EQUALS);
      case GREATER -> new BinaryExpr(left, right, BinaryExpr.Operator.LESS_EQUALS);
      case GREATER_EQUALS -> new BinaryExpr(left, right, BinaryExpr.Operator.LESS);
      case LESS -> new BinaryExpr(left, right, BinaryExpr.Operator.GREATER_EQUALS);
      case LESS_EQUALS -> new BinaryExpr(left, right, BinaryExpr.Operator.GREATER);
      case AND ->
          new BinaryExpr(negateExpression(left), negateExpression(right), BinaryExpr.Operator.OR);
      case OR ->
          new BinaryExpr(negateExpression(left), negateExpression(right), BinaryExpr.Operator.AND);
      default -> new UnaryExpr(binaryExpr.clone(), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    };
  }

  private boolean belongsToParentIf(final Node node, final IfStmt parentIf) {
    if (parentIf == null) {
      return true;
    }
    final Optional<IfStmt> ancestorIf = AstUtils.findAncestor(node, IfStmt.class);
    return ancestorIf.isEmpty() || isSameAstNode(ancestorIf.get(), parentIf);
  }

  private boolean isSameAstNode(final Node left, final Node right) {
    if (left == null || right == null) {
      return false;
    }
    if (!left.getClass().equals(right.getClass())) {
      return false;
    }
    if (left.getRange().isPresent() && right.getRange().isPresent()) {
      return left.getRange().get().equals(right.getRange().get());
    }
    return Objects.equals(left.getTokenRange(), right.getTokenRange())
        && Objects.equals(
            left.getParentNode().flatMap(Node::getRange),
            right.getParentNode().flatMap(Node::getRange));
  }

  private boolean isTrivialMethod(final MethodInfo method) {
    final String src = method.getSourceCode();
    if (src == null || src.isBlank()) {
      return false;
    }
    if (method.getCyclomaticComplexity() > 1) {
      return false;
    }
    final String lower = src.toLowerCase(Locale.ROOT);
    return !(lower.contains("if ")
        || lower.contains("if(")
        || lower.contains("switch")
        || lower.contains("for(")
        || lower.contains("while(")
        || lower.contains("do{")
        || lower.contains("do "));
  }

  private static final class GuardDetection {

    GuardType type;

    final List<String> effects = new ArrayList<>();

    String message;

    boolean hasReturn;

    boolean hasThrow;

    boolean hasEffect() {
      return type != null;
    }
  }

  private record GuardResult(GuardSummary summary, boolean earlyExit, boolean throwExit) {}

  private record ParseAttempt(
      Optional<MethodDeclaration> methodDeclaration, boolean usedFallback, String errorMessage) {}

  private static final class RepresentativePathBuilder {

    private static final String FAILURE_HINT = "failure";

    private final int limit;

    private int counter = 1;

    private final List<RepresentativePath> guardPaths = new ArrayList<>();

    private final List<RepresentativePath> switchPaths = new ArrayList<>();

    private final List<RepresentativePath> boundaryPaths = new ArrayList<>();

    private final Set<String> representedConditions = new LinkedHashSet<>();

    private RepresentativePath successPath;

    RepresentativePathBuilder(final int limit) {
      this.limit = limit;
    }

    void addGuardPath(final GuardResult guardResult) {
      final GuardSummary guard = guardResult.summary();
      final RepresentativePath path = new RepresentativePath();
      path.setId(PATH_ID_PREFIX + counter++);
      if (guardResult.throwExit()) {
        path.setDescription("Exception throw path");
        path.setExpectedOutcomeHint(FAILURE_HINT);
      } else if (guardResult.earlyExit()) {
        path.setDescription("Early return path");
        path.setExpectedOutcomeHint("early-return");
      } else if (guard.getMessageLiteral() != null) {
        path.setDescription("Validation failure: " + guard.getMessageLiteral());
        path.setExpectedOutcomeHint(FAILURE_HINT);
      } else if (guard.getType() == GuardType.LOOP_GUARD_BREAK) {
        path.setDescription("Loop guard break");
        path.setExpectedOutcomeHint("loop-break");
      } else if (guard.getType() == GuardType.LOOP_GUARD_CONTINUE) {
        path.setDescription("Loop guard continue");
        path.setExpectedOutcomeHint("loop-continue");
      } else {
        path.setDescription("Validation guard");
        path.setExpectedOutcomeHint(FAILURE_HINT);
      }
      final String condition = guard.getCondition();
      path.setRequiredConditions(List.of(condition));
      registerCondition(condition);
      guardPaths.add(path);
    }

    void addSwitchPath(final String selector, final String label) {
      final String condition = selector + " == " + label;
      final RepresentativePath path = new RepresentativePath();
      path.setId(PATH_ID_PREFIX + counter++);
      path.setDescription("Switch case " + selector + "=" + label);
      path.setRequiredConditions(List.of(condition));
      path.setExpectedOutcomeHint("case-" + label);
      registerCondition(condition);
      switchPaths.add(path);
    }

    void addBoundaryPath(final String predicate) {
      if (isConditionAlreadyRepresented(predicate)) {
        return;
      }
      final RepresentativePath path = new RepresentativePath();
      path.setId(PATH_ID_PREFIX + counter++);
      path.setDescription("Boundary condition " + predicate);
      path.setRequiredConditions(List.of(predicate));
      path.setExpectedOutcomeHint("boundary");
      registerCondition(predicate);
      boundaryPaths.add(path);
    }

    void addSuccessPathIfNeeded() {
      if (successPath != null) {
        return;
      }
      if (!hasSpecificScenarioPath()) {
        return;
      }
      successPath = new RepresentativePath();
      successPath.setId(PATH_ID_PREFIX + counter++);
      successPath.setDescription("Main success path");
      successPath.setExpectedOutcomeHint("success");
    }

    List<RepresentativePath> build() {
      final List<RepresentativePath> ordered = new ArrayList<>();
      addWithinLimit(ordered, guardPaths);
      if (ordered.size() < limit && successPath != null) {
        ordered.add(successPath);
      }
      addWithinLimit(ordered, switchPaths);
      addWithinLimit(ordered, boundaryPaths);
      return ordered;
    }

    private void addWithinLimit(
        final List<RepresentativePath> ordered, final List<RepresentativePath> candidates) {
      for (final RepresentativePath candidate : candidates) {
        if (ordered.size() >= limit) {
          return;
        }
        ordered.add(candidate);
      }
    }

    private boolean isConditionAlreadyRepresented(final String condition) {
      final String canonical = canonicalizeCondition(condition);
      return !canonical.isBlank() && representedConditions.contains(canonical);
    }

    private void registerCondition(final String condition) {
      final String canonical = canonicalizeCondition(condition);
      if (!canonical.isBlank()) {
        representedConditions.add(canonical);
      }
    }

    private boolean hasSpecificScenarioPath() {
      return !guardPaths.isEmpty() || !switchPaths.isEmpty() || !boundaryPaths.isEmpty();
    }

    private String canonicalizeCondition(final String condition) {
      if (condition == null || condition.isBlank()) {
        return "";
      }
      return condition.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
  }
}
