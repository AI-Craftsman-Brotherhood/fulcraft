package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Detects excessive mock usage in tests using AST analysis.
 *
 * <p>Counts occurrences of:
 *
 * <ul>
 *   <li>Mockito.mock(...) or mock(...) (weight: 1)
 *   <li>Mockito.spy(...) or spy(...) (weight: 2, stronger indicator of over-mocking)
 *   <li>@Mock annotations (weight: 1)
 *   <li>@Spy annotations (weight: 2)
 *   <li>mockStatic(...) (weight: 2, optional)
 * </ul>
 *
 * <p>Excessive mocking indicates tests that are too isolated from real behavior, making them
 * fragile and hard to maintain. Tests with many mocks often test mocking behavior rather than
 * actual code.
 */
public class OverMockRule extends AbstractJavaParserBrittleRule {

  private static final int SPY_WEIGHT = 2;

  private static final int STATIC_MOCK_WEIGHT = 2;

  private final int warnThreshold;

  private final int failThreshold;

  private final boolean countStaticMocks;

  private final boolean countStubs;

  public OverMockRule() {
    this(3, 6, false, false);
  }

  public OverMockRule(final int warnThreshold, final int failThreshold) {
    this(warnThreshold, failThreshold, false, false);
  }

  /**
   * Create an OverMockRule with custom thresholds and counting options.
   *
   * @param warnThreshold Mock count above which to warn
   * @param failThreshold Mock count above which to fail
   * @param countStaticMocks Whether to include static mocks in the count
   * @param countStubs Whether to include external stubs (WireMock, H2) in the count
   */
  public OverMockRule(
      final int warnThreshold,
      final int failThreshold,
      final boolean countStaticMocks,
      final boolean countStubs) {
    super(Severity.WARNING);
    this.warnThreshold = warnThreshold;
    this.failThreshold = failThreshold;
    this.countStaticMocks = countStaticMocks;
    this.countStubs = countStubs;
  }

  @Override
  public RuleId getRuleId() {
    return RuleId.OVER_MOCK;
  }

  @Override
  public List<BrittleFinding> checkAst(final CompilationUnit cu, final String filePath) {
    final List<BrittleFinding> findings = new ArrayList<>();
    final MockCounter counter = new MockCounter(countStaticMocks, countStubs);
    cu.accept(counter, null);
    final int weightedTotal = counter.getWeightedTotal();
    if (weightedTotal > failThreshold) {
      // Exceed fail threshold -> ERROR
      findings.add(
          new BrittleFinding(
              getRuleId(),
              Severity.ERROR,
              filePath, // File-level finding
              -1,
              String.format(
                  "Excessive mocking: %s (weighted: %d, threshold: %d)",
                  formatCounts(counter), weightedTotal, failThreshold),
              formatEvidence(counter)));
    } else if (weightedTotal > warnThreshold) {
      // Between warn and fail threshold -> WARNING
      findings.add(
          new BrittleFinding(
              getRuleId(),
              Severity.WARNING,
              filePath,
              -1,
              String.format(
                  "High mock count: %s (weighted: %d, warn threshold: %d)",
                  formatCounts(counter), weightedTotal, warnThreshold),
              formatEvidence(counter)));
    }
    return findings;
  }

  private String formatEvidence(final MockCounter counter) {
    String evidence =
        String.format(
            "mock: %d, spy: %d, static: %d",
            counter.mockCount.get(), counter.spyCount.get(), counter.staticMockCount.get());
    if (countStubs) {
      evidence += String.format(", stub: %d", counter.stubCount.get());
    }
    return evidence;
  }

  private String formatCounts(final MockCounter counter) {
    String counts =
        String.format(
            "%d mocks, %d spies, %d static",
            counter.mockCount.get(), counter.spyCount.get(), counter.staticMockCount.get());
    if (countStubs) {
      counts += String.format(", %d stubs", counter.stubCount.get());
    }
    return counts;
  }

  /** Visitor that counts mock-related method calls and annotations. */
  private static class MockCounter extends VoidVisitorAdapter<Void> {

    private final AtomicInteger mockCount = new AtomicInteger(0);

    private final AtomicInteger spyCount = new AtomicInteger(0);

    private final AtomicInteger staticMockCount = new AtomicInteger(0);

    private final AtomicInteger stubCount = new AtomicInteger(0);

    private final boolean countStaticMocks;

    private final boolean countStubs;

    /** Mock method names to detect. */
    private static final Set<String> MOCK_METHODS = Set.of("mock");

    /** Spy method names to detect. */
    private static final Set<String> SPY_METHODS = Set.of("spy");

    /** Static mock method names to detect. */
    private static final Set<String> STATIC_MOCK_METHODS = Set.of("mockStatic", "mockConstruction");

    private static final String MOCKITO_SIMPLE_NAME = "Mockito";

    /** Stub-related class names (WireMock, embedded DBs). */
    private static final Set<String> STUB_CLASSES =
        Set.of("WireMockRule", "WireMockExtension", "EmbeddedDatabase");

    MockCounter(final boolean countStaticMocks, final boolean countStubs) {
      this.countStaticMocks = countStaticMocks;
      this.countStubs = countStubs;
    }

    @Override
    public void visit(final MethodCallExpr expr, final Void arg) {
      super.visit(expr, arg);
      final String methodName = expr.getNameAsString();
      final String scope = expr.getScope().map(Object::toString).orElse("");
      if (isShadowedByEnclosingTypeMethod(expr)) {
        return;
      }
      // Check for Mockito.mock() or static import mock()
      if (MOCK_METHODS.contains(methodName) && isMockitoScope(scope)) {
        mockCount.incrementAndGet();
        return;
      }
      // Check for Mockito.spy() or static import spy()
      if (SPY_METHODS.contains(methodName) && isMockitoScope(scope)) {
        spyCount.incrementAndGet();
        return;
      }
      // Check for Mockito.mockStatic() or static import mockStatic()
      if (countStaticMocks && STATIC_MOCK_METHODS.contains(methodName) && isMockitoScope(scope)) {
        staticMockCount.incrementAndGet();
      }
    }

    @Override
    public void visit(final FieldDeclaration field, final Void arg) {
      super.visit(field, arg);
      countAnnotations(field.getAnnotations());
    }

    @Override
    public void visit(final Parameter parameter, final Void arg) {
      super.visit(parameter, arg);
      countAnnotations(parameter.getAnnotations());
    }

    @Override
    public void visit(final ObjectCreationExpr expr, final Void arg) {
      super.visit(expr, arg);
      if (countStubs) {
        final String typeName = expr.getType().getNameAsString();
        if (STUB_CLASSES.contains(typeName)) {
          stubCount.incrementAndGet();
        }
      }
    }

    int getWeightedTotal() {
      int total = mockCount.get() + (spyCount.get() * SPY_WEIGHT);
      if (countStaticMocks) {
        total += staticMockCount.get() * STATIC_MOCK_WEIGHT;
      }
      if (countStubs) {
        total += stubCount.get();
      }
      return total;
    }

    private void countAnnotations(final NodeList<AnnotationExpr> annotations) {
      if (annotations == null || annotations.isEmpty()) {
        return;
      }
      for (final AnnotationExpr annotation : annotations) {
        final String name = annotation.getNameAsString();
        final String simpleName = simpleName(name);
        if ("Mock".equals(simpleName)) {
          mockCount.incrementAndGet();
        } else if ("Spy".equals(simpleName)) {
          spyCount.incrementAndGet();
        }
      }
    }

    private static String simpleName(final String name) {
      if (name == null || name.isBlank()) {
        return "";
      }
      final int lastDot = name.lastIndexOf('.');
      return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    private static boolean isMockitoScope(final String scope) {
      return scope.isEmpty()
          || MOCKITO_SIMPLE_NAME.equals(scope)
          || scope.endsWith(MOCKITO_SIMPLE_NAME);
    }
  }
}
