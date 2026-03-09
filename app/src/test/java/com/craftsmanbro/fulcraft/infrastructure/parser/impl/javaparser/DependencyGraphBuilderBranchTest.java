package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyGraphBuilderBranchTest {

  @Test
  void collectCalledMethods_tracksMethodReferenceAndLiteralBranches() {
    String longLiteral = "x".repeat(140);
    String source =
        """
                package com.example;

                class Demo {
                  void caller() {
                    helper();
                    "abc".trim();
                    unknown.work();
                    new String("seed");
                    java.util.function.Function<String, String> f = String::trim;
                    java.util.function.Function<Object, String> f2 = UnknownType::work;
                    take("duplicate");
                    take("duplicate");
                    take(-1);
                    take(+2);
                    take((("short")));
                    take("%s");
                  }

                  void helper() {}
                  void take(Object x) {}
                }
                """
            .formatted(longLiteral);

    CompilationUnit cu = StaticJavaParser.parse(source);
    MethodDeclaration caller =
        cu.getClassByName("Demo").orElseThrow().getMethodsByName("caller").getFirst();

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();
    String callerKey =
        DependencyGraphBuilder.methodKey("com.example.Demo", caller.getSignature().asString());

    builder.collectCalledMethods(caller, callerKey, "com.example.Demo", context);

    Set<String> calls = context.getCallGraph().get(callerKey);
    assertNotNull(calls);
    assertTrue(calls.stream().anyMatch(k -> k.contains("trim(")));
    assertTrue(calls.stream().anyMatch(k -> k.contains("work(")));
    assertTrue(calls.stream().anyMatch(k -> k.contains("(?)")));

    Map<String, ResolutionStatus> statuses = context.getCallStatuses(callerKey);
    assertNotNull(statuses);
    assertTrue(statuses.containsValue(ResolutionStatus.UNRESOLVED));

    Map<String, List<String>> allLiterals = context.getCallArgumentLiterals(callerKey);
    assertNotNull(allLiterals);
    String takeKey =
        allLiterals.keySet().stream().filter(k -> k.contains("#take(")).findFirst().orElseThrow();
    List<String> literals = allLiterals.get(takeKey);

    assertTrue(literals.contains("\"duplicate\""));
    assertTrue(literals.contains("-1"));
    assertTrue(literals.contains("+2"));
    assertTrue(literals.contains("\"short\""));
    assertEquals(1, literals.stream().filter("\"duplicate\""::equals).count());
    assertTrue(literals.stream().anyMatch(v -> v.length() == 120 && v.endsWith("...")));
  }

  @Test
  void privateLiteralHelpers_handleNullEnclosedUnaryAndLongInputs() throws Exception {
    DependencyGraphBuilder builder = new DependencyGraphBuilder();

    Method extractArgumentLiterals =
        DependencyGraphBuilder.class.getDeclaredMethod("extractArgumentLiterals", NodeList.class);
    extractArgumentLiterals.setAccessible(true);

    List<String> fromNull =
        (List<String>) extractArgumentLiterals.invoke(builder, new Object[] {null});
    List<String> fromEmpty =
        (List<String>) extractArgumentLiterals.invoke(builder, new NodeList<Expression>());

    assertTrue(fromNull.isEmpty());
    assertTrue(fromEmpty.isEmpty());

    Method extractLiteralArgument =
        DependencyGraphBuilder.class.getDeclaredMethod("extractLiteralArgument", Expression.class);
    extractLiteralArgument.setAccessible(true);

    assertNull(extractLiteralArgument.invoke(builder, new Object[] {null}));
    assertEquals(
        "42", extractLiteralArgument.invoke(builder, StaticJavaParser.parseExpression("((42))")));
    assertEquals(
        "-9", extractLiteralArgument.invoke(builder, StaticJavaParser.parseExpression("-9")));
    assertEquals(
        "+7", extractLiteralArgument.invoke(builder, StaticJavaParser.parseExpression("+7")));
    assertNull(extractLiteralArgument.invoke(builder, StaticJavaParser.parseExpression("value")));
    assertNull(
        extractLiteralArgument.invoke(builder, StaticJavaParser.parseExpression("-(value)")));

    Method normalizeLiteralSnippet =
        DependencyGraphBuilder.class.getDeclaredMethod("normalizeLiteralSnippet", String.class);
    normalizeLiteralSnippet.setAccessible(true);

    assertEquals("", normalizeLiteralSnippet.invoke(builder, new Object[] {null}));
    assertEquals("", normalizeLiteralSnippet.invoke(builder, "   "));
    String normalized =
        (String) normalizeLiteralSnippet.invoke(builder, "\"" + "y".repeat(150) + "\"");
    assertEquals(120, normalized.length());
    assertTrue(normalized.endsWith("..."));

    Method resolveScopeType =
        DependencyGraphBuilder.class.getDeclaredMethod("resolveScopeType", Expression.class);
    resolveScopeType.setAccessible(true);

    Object nullScope = resolveScopeType.invoke(builder, new Object[] {null});
    assertNull(readRecordValue(nullScope, "name"));
    assertEquals(false, readRecordValue(nullScope, "resolved"));

    Expression resolvedExpression = mock(Expression.class);
    ResolvedType resolvedType = mock(ResolvedType.class);
    ResolvedReferenceType resolvedReference = mock(ResolvedReferenceType.class);
    when(resolvedExpression.calculateResolvedType()).thenReturn(resolvedType);
    when(resolvedType.isReferenceType()).thenReturn(true);
    when(resolvedType.asReferenceType()).thenReturn(resolvedReference);
    when(resolvedReference.getQualifiedName()).thenReturn("java.lang.String");

    Object resolvedScope = resolveScopeType.invoke(builder, resolvedExpression);
    assertEquals("java.lang.String", readRecordValue(resolvedScope, "name"));
    assertEquals(true, readRecordValue(resolvedScope, "resolved"));

    Expression unresolvedExpression = mock(Expression.class);
    when(unresolvedExpression.calculateResolvedType())
        .thenThrow(new RuntimeException("unresolved"));
    when(unresolvedExpression.toString()).thenReturn("unknownVar");

    Object unresolvedScope = resolveScopeType.invoke(builder, unresolvedExpression);
    assertEquals("unknownVar", readRecordValue(unresolvedScope, "name"));
    assertEquals(false, readRecordValue(unresolvedScope, "resolved"));
  }

  private static Object readRecordValue(Object record, String accessor) throws Exception {
    Method method = record.getClass().getDeclaredMethod(accessor);
    method.setAccessible(true);
    return method.invoke(record);
  }
}
