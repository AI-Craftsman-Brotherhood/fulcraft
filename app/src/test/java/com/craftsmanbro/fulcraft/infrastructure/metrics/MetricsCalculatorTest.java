package com.craftsmanbro.fulcraft.infrastructure.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.metrics.contract.CodeMetricsPort;
import com.craftsmanbro.fulcraft.infrastructure.metrics.impl.MetricsCalculator;
import com.craftsmanbro.fulcraft.infrastructure.metrics.model.CodeMetrics;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import org.junit.jupiter.api.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

/** Tests for {@link MetricsCalculator} across JavaParser, JDT, and Spoon AST models. */
class MetricsCalculatorTest {

  // --- JavaParser Complexity tests ---

  @Test
  void calculateComplexity_withNullNode_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> MetricsCalculator.calculateComplexity((com.github.javaparser.ast.Node) null));
  }

  @Test
  void calculateComplexity_withSimpleMethod_returnsOne() {
    MethodDeclaration method =
        parseMethod(
            """
                void simple() {
                    int x = 1;
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(1, complexity);
  }

  @Test
  void calculateComplexity_withIfStatement_returnsTwo() {
    MethodDeclaration method =
        parseMethod(
            """
                void withIf() {
                    if (true) {
                        int x = 1;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withIfElseChain_incrementsForEachBranch() {
    MethodDeclaration method =
        parseMethod(
            """
                void withIfElse() {
                    if (true) {
                        int x = 1;
                    } else if (false) {
                        int y = 2;
                    } else {
                        int z = 3;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + 2 if statements = 3
    assertEquals(3, complexity);
  }

  @Test
  void calculateComplexity_withForLoop_incrementsComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void withFor() {
                    for (int i = 0; i < 10; i++) {
                        int x = i;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withForEachLoop_incrementsComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void withForEach(java.util.List<String> list) {
                    for (String s : list) {
                        System.out.println(s);
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withWhileLoop_incrementsComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void withWhile() {
                    while (true) {
                        break;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withDoWhileLoop_incrementsComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void withDoWhile() {
                    do {
                        int x = 1;
                    } while (false);
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withTryCatch_incrementsForCatch() {
    MethodDeclaration method =
        parseMethod(
            """
                void withTryCatch() {
                    try {
                        int x = 1;
                    } catch (Exception e) {
                        int y = 2;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withTernary_incrementsComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void withTernary() {
                    int x = true ? 1 : 2;
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withSwitch_incrementsPerCase() {
    MethodDeclaration method =
        parseMethod(
            """
                void withSwitch(int value) {
                    switch (value) {
                        case 1:
                            break;
                        case 2:
                            break;
                        default:
                            break;
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + switch (3 cases)
    assertEquals(4, complexity);
  }

  @Test
  void calculateComplexity_withEmptySwitch_stillCountsOneBranch() {
    MethodDeclaration method =
        parseMethod(
            """
                void withSwitch(int value) {
                    switch (value) {
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + empty switch fallback branch(1)
    assertEquals(2, complexity);
  }

  @Test
  void calculateComplexity_withNestedStructures_accumulatesComplexity() {
    MethodDeclaration method =
        parseMethod(
            """
                void nested() {
                    if (true) {
                        for (int i = 0; i < 10; i++) {
                            while (false) {
                                int x = 1;
                            }
                        }
                    }
                }
                """);

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + 1 if + 1 for + 1 while = 4
    assertEquals(4, complexity);
  }

  // --- JavaParser Nesting Depth tests ---

  @Test
  void calculateMaxNestingDepth_withNullNode_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> MetricsCalculator.calculateMaxNestingDepth((com.github.javaparser.ast.Node) null));
  }

  @Test
  void calculateMaxNestingDepth_withSimpleMethod_returnsZero() {
    MethodDeclaration method =
        parseMethod(
            """
                void simple() {
                    int x = 1;
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(0, depth);
  }

  @Test
  void calculateMaxNestingDepth_withSingleIf_returnsOne() {
    MethodDeclaration method =
        parseMethod(
            """
                void withIf() {
                    if (true) {
                        int x = 1;
                    }
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(1, depth);
  }

  @Test
  void calculateMaxNestingDepth_withNestedIfs_returnsCorrectDepth() {
    MethodDeclaration method =
        parseMethod(
            """
                void nestedIfs() {
                    if (true) {
                        if (false) {
                            if (true) {
                                int x = 1;
                            }
                        }
                    }
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(3, depth);
  }

  @Test
  void calculateMaxNestingDepth_withMixedStructures_returnsMaxDepth() {
    MethodDeclaration method =
        parseMethod(
            """
                void mixed() {
                    if (true) {
                        for (int i = 0; i < 10; i++) {
                            while (false) {
                                int x = 1;
                            }
                        }
                    }
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(3, depth);
  }

  @Test
  void calculateMaxNestingDepth_withSequentialStructures_returnsSingleDepth() {
    MethodDeclaration method =
        parseMethod(
            """
                void sequential() {
                    if (true) {
                        int x = 1;
                    }
                    for (int i = 0; i < 10; i++) {
                        int y = 2;
                    }
                    while (false) {
                        int z = 3;
                    }
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    // Sequential structures don't nest deeper than 1
    assertEquals(1, depth);
  }

  @Test
  void calculateMaxNestingDepth_withTryCatchNesting_countsDepth() {
    MethodDeclaration method =
        parseMethod(
            """
                void tryCatchNesting() {
                    try {
                        int x = 1;
                    } catch (Exception e) {
                        if (true) {
                            int y = 2;
                        }
                    }
                }
                """);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    // catch (1) + if (2) = 2
    assertEquals(2, depth);
  }

  // --- JDT tests ---

  @Test
  void calculateComplexityJdt_withNullMethod_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            MetricsCalculator.calculateComplexity(
                (org.eclipse.jdt.core.dom.MethodDeclaration) null));
  }

  @Test
  void calculateMaxNestingDepthJdt_withNullMethod_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            MetricsCalculator.calculateMaxNestingDepth(
                (org.eclipse.jdt.core.dom.MethodDeclaration) null));
  }

  @Test
  void calculateComplexityJdt_withMockedControlStructures_countsBranches() {
    org.eclipse.jdt.core.dom.MethodDeclaration method =
        mock(org.eclipse.jdt.core.dom.MethodDeclaration.class);

    doAnswer(
            invocation -> {
              org.eclipse.jdt.core.dom.ASTVisitor visitor = invocation.getArgument(0);
              visitor.visit(mock(org.eclipse.jdt.core.dom.IfStatement.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.ForStatement.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.EnhancedForStatement.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.WhileStatement.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.DoStatement.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.CatchClause.class));
              visitor.visit(mock(org.eclipse.jdt.core.dom.ConditionalExpression.class));

              org.eclipse.jdt.core.dom.SwitchCase multiLabelCase =
                  mock(org.eclipse.jdt.core.dom.SwitchCase.class);
              when(multiLabelCase.isDefault()).thenReturn(false);
              when(multiLabelCase.expressions())
                  .thenReturn(
                      List.of(
                          mock(org.eclipse.jdt.core.dom.NumberLiteral.class),
                          mock(org.eclipse.jdt.core.dom.NumberLiteral.class)));
              visitor.visit(multiLabelCase);

              org.eclipse.jdt.core.dom.SwitchCase defaultCase =
                  mock(org.eclipse.jdt.core.dom.SwitchCase.class);
              when(defaultCase.isDefault()).thenReturn(true);
              when(defaultCase.expressions()).thenReturn(List.of());
              visitor.visit(defaultCase);
              return null;
            })
        .when(method)
        .accept(any(org.eclipse.jdt.core.dom.ASTVisitor.class));

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + (if/for/enhanced-for/while/do/catch/ternary) + switch labels(2 + 1)
    assertEquals(11, complexity);
  }

  @Test
  void calculateMaxNestingDepthJdt_withMethodWithoutBody_returnsZero() {
    org.eclipse.jdt.core.dom.MethodDeclaration method =
        mock(org.eclipse.jdt.core.dom.MethodDeclaration.class);
    when(method.getBody()).thenReturn(null);

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(0, depth);
  }

  @Test
  void calculateMaxNestingDepthJdt_withMockedTree_returnsTwo() {
    org.eclipse.jdt.core.dom.MethodDeclaration method =
        mock(org.eclipse.jdt.core.dom.MethodDeclaration.class);
    org.eclipse.jdt.core.dom.Block rootBlock = mock(org.eclipse.jdt.core.dom.Block.class);
    when(method.getBody()).thenReturn(rootBlock);

    org.eclipse.jdt.core.dom.IfStatement ifStatement =
        mock(org.eclipse.jdt.core.dom.IfStatement.class);
    org.eclipse.jdt.core.dom.WhileStatement whileStatement =
        mock(org.eclipse.jdt.core.dom.WhileStatement.class);
    org.eclipse.jdt.core.dom.ForStatement forStatement =
        mock(org.eclipse.jdt.core.dom.ForStatement.class);
    org.eclipse.jdt.core.dom.EnhancedForStatement enhancedForStatement =
        mock(org.eclipse.jdt.core.dom.EnhancedForStatement.class);
    org.eclipse.jdt.core.dom.DoStatement doStatement =
        mock(org.eclipse.jdt.core.dom.DoStatement.class);
    org.eclipse.jdt.core.dom.SwitchStatement switchStatement =
        mock(org.eclipse.jdt.core.dom.SwitchStatement.class);
    org.eclipse.jdt.core.dom.ConditionalExpression conditionalExpression =
        mock(org.eclipse.jdt.core.dom.ConditionalExpression.class);
    org.eclipse.jdt.core.dom.CatchClause catchClause =
        mock(org.eclipse.jdt.core.dom.CatchClause.class);
    org.eclipse.jdt.core.dom.Statement leafStatement =
        mock(org.eclipse.jdt.core.dom.Statement.class);

    org.eclipse.jdt.core.dom.Block whileBody = mock(org.eclipse.jdt.core.dom.Block.class);
    when(whileBody.statements()).thenReturn(List.of(leafStatement));
    when(whileStatement.getBody()).thenReturn(whileBody);

    when(forStatement.getBody()).thenReturn(leafStatement);
    when(ifStatement.getThenStatement()).thenReturn(whileStatement);
    when(ifStatement.getElseStatement()).thenReturn(forStatement);

    when(enhancedForStatement.getBody()).thenReturn(leafStatement);
    when(doStatement.getBody()).thenReturn(leafStatement);
    when(switchStatement.statements()).thenReturn(List.of(leafStatement));

    org.eclipse.jdt.core.dom.Expression thenExpression =
        mock(org.eclipse.jdt.core.dom.Expression.class);
    org.eclipse.jdt.core.dom.Expression elseExpression =
        mock(org.eclipse.jdt.core.dom.Expression.class);
    when(conditionalExpression.getThenExpression()).thenReturn(thenExpression);
    when(conditionalExpression.getElseExpression()).thenReturn(elseExpression);

    when(rootBlock.statements())
        .thenReturn(
            List.of(
                ifStatement,
                enhancedForStatement,
                doStatement,
                switchStatement,
                conditionalExpression,
                catchClause,
                leafStatement));

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(2, depth);
  }

  @Test
  void calculateMaxNestingDepthJdt_withCatchBody_countsNestedStatements() {
    org.eclipse.jdt.core.dom.MethodDeclaration method =
        mock(org.eclipse.jdt.core.dom.MethodDeclaration.class);
    org.eclipse.jdt.core.dom.Block body = mock(org.eclipse.jdt.core.dom.Block.class);
    org.eclipse.jdt.core.dom.CatchClause catchClause =
        mock(org.eclipse.jdt.core.dom.CatchClause.class);
    org.eclipse.jdt.core.dom.IfStatement ifStatement =
        mock(org.eclipse.jdt.core.dom.IfStatement.class);
    when(method.getBody()).thenReturn(body);
    doAnswer(
            invocation -> {
              org.eclipse.jdt.core.dom.ASTVisitor visitor = invocation.getArgument(0);
              visitor.preVisit(body);
              visitor.preVisit(catchClause);
              visitor.preVisit(ifStatement);
              visitor.postVisit(ifStatement);
              visitor.postVisit(catchClause);
              visitor.postVisit(body);
              return null;
            })
        .when(body)
        .accept(any(org.eclipse.jdt.core.dom.ASTVisitor.class));

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(2, depth);
  }

  @Test
  void calculateMaxNestingDepthJdt_withNestedConditionalExpressions_countsExpressionTree() {
    org.eclipse.jdt.core.dom.MethodDeclaration method =
        mock(org.eclipse.jdt.core.dom.MethodDeclaration.class);
    org.eclipse.jdt.core.dom.Block body = mock(org.eclipse.jdt.core.dom.Block.class);
    org.eclipse.jdt.core.dom.ConditionalExpression outerConditional =
        mock(org.eclipse.jdt.core.dom.ConditionalExpression.class);
    org.eclipse.jdt.core.dom.ConditionalExpression innerConditional =
        mock(org.eclipse.jdt.core.dom.ConditionalExpression.class);
    when(method.getBody()).thenReturn(body);
    doAnswer(
            invocation -> {
              org.eclipse.jdt.core.dom.ASTVisitor visitor = invocation.getArgument(0);
              visitor.preVisit(body);
              visitor.preVisit(outerConditional);
              visitor.preVisit(innerConditional);
              visitor.postVisit(innerConditional);
              visitor.postVisit(outerConditional);
              visitor.postVisit(body);
              return null;
            })
        .when(body)
        .accept(any(org.eclipse.jdt.core.dom.ASTVisitor.class));

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(2, depth);
  }

  // --- Spoon tests ---

  @Test
  void calculateComplexitySpoon_withNullExecutable_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            MetricsCalculator.calculateComplexity(
                (spoon.reflect.declaration.CtExecutable<?>) null));
  }

  @Test
  void calculateComplexitySpoon_withMethodWithoutBody_returnsOne() {
    CtMethod<?> method =
        parseSpoonMethod(
            """
                package com.example;

                abstract class Sample {
                    abstract void noBody();
                }
                """,
            "noBody");

    int complexity = MetricsCalculator.calculateComplexity(method);

    assertEquals(1, complexity);
  }

  @Test
  void calculateComplexitySpoon_withControlFlowAndSwitchLabels_countsBranches() {
    CtMethod<?> method =
        parseSpoonMethod(
            """
                package com.example;

                class Sample {
                    int sample(int value, java.util.List<Integer> values) {
                        int result = value > 0 ? 1 : 2;
                        for (Integer v : values) {
                            if (v > 0) {
                                result += v;
                            }
                        }
                        switch (value) {
                            case 1, 2:
                                result++;
                                break;
                            default:
                                result--;
                                break;
                        }
                        return result;
                    }
                }
                """,
            "sample");

    int complexity = MetricsCalculator.calculateComplexity(method);

    // 1 base + ternary(1) + for-each(1) + if(1) + switch labels(2 + 1)
    assertEquals(7, complexity);
  }

  @Test
  void calculateMaxNestingDepthSpoon_withNullExecutable_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            MetricsCalculator.calculateMaxNestingDepth(
                (spoon.reflect.declaration.CtExecutable<?>) null));
  }

  @Test
  void calculateMaxNestingDepthSpoon_withMethodWithoutBody_returnsZero() {
    CtMethod<?> method =
        parseSpoonMethod(
            """
                package com.example;

                abstract class Sample {
                    abstract void noBody();
                }
                """,
            "noBody");

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(0, depth);
  }

  @Test
  void calculateMaxNestingDepthSpoon_withNestedControlFlow_returnsDepthThree() {
    CtMethod<?> method =
        parseSpoonMethod(
            """
                package com.example;

                class Sample {
                    void nested() {
                        if (true) {
                            for (int i = 0; i < 10; i++) {
                                while (false) {
                                    int x = i;
                                }
                            }
                        }
                    }
                }
                """,
            "nested");

    int depth = MetricsCalculator.calculateMaxNestingDepth(method);

    assertEquals(3, depth);
  }

  @Test
  void port_exposesContractForJavaParserMethods() {
    MethodDeclaration method =
        parseMethod(
            """
                void sample() {
                    if (true) {
                        int x = 1;
                    }
                }
                """);
    CodeMetricsPort port = MetricsCalculator.port();

    assertEquals(2, port.complexityOf(method));
    assertEquals(1, port.nestingDepthOf(method));
  }

  @Test
  void port_calculateFor_returnsCodeMetricsModel() {
    MethodDeclaration method =
        parseMethod(
            """
                void simple() {
                    int x = 1;
                }
                """);

    CodeMetrics metrics = MetricsCalculator.port().calculateFor(method);

    assertEquals(1, metrics.cyclomaticComplexity());
    assertEquals(0, metrics.maxNestingDepth());
  }

  // --- Helper methods ---

  private MethodDeclaration parseMethod(String methodCode) {
    String classCode = "class TestClass {\n" + methodCode + "\n}";
    CompilationUnit cu = StaticJavaParser.parse(classCode);
    return cu.findFirst(MethodDeclaration.class)
        .orElseThrow(() -> new IllegalArgumentException("No method found in code"));
  }

  private CtMethod<?> parseSpoonMethod(String sourceCode, String methodName) {
    Launcher launcher = new Launcher();
    launcher.addInputResource(new VirtualFile(sourceCode, "Sample.java"));
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setComplianceLevel(17);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.buildModel();

    CtType<?> type = launcher.getFactory().Type().get("com.example.Sample");
    if (type == null) {
      throw new IllegalArgumentException("Type not found: com.example.Sample");
    }
    return type.getMethodsByName(methodName).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No method found in code: " + methodName));
  }
}
