package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyGraphBuilderTest {

  @Test
  void collectsMethodAndConstructorCallsAndIncomingCounts() {
    String source =
        """
        package com.example;

        public class Caller {
          void caller() {
            callee();
            callee();
            new Helper("seed");
            send("id-1", "Order is being processed");
          }

          void callee() {}
          void send(String id, String message) {}
        }

        class Helper {
          Helper(String seed) {}
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    ClassOrInterfaceDeclaration callerType = cu.getClassByName("Caller").orElseThrow();
    MethodDeclaration caller = callerType.getMethodsByName("caller").get(0);

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();
    String callerKey =
        DependencyGraphBuilder.methodKey("com.example.Caller", caller.getSignature().asString());

    builder.collectCalledMethods(caller, callerKey, "com.example.Caller", context);

    Set<String> calls = context.getCallGraph().get(callerKey);
    assertNotNull(calls);

    String calleeKey = DependencyGraphBuilder.methodKey("com.example.Caller", "callee(0)");
    String ctorKey = DependencyGraphBuilder.methodKey("Helper", "Helper(1)");
    String sendKey = DependencyGraphBuilder.methodKey("com.example.Caller", "send(2)");

    assertTrue(calls.contains(calleeKey));
    assertTrue(calls.contains(ctorKey));
    assertTrue(calls.contains(sendKey));
    assertEquals(2, context.getIncomingCounts().get(calleeKey));
    assertEquals(1, context.getIncomingCounts().get(ctorKey));
    assertEquals(1, context.getIncomingCounts().get(sendKey));

    assertEquals(ResolutionStatus.UNRESOLVED, context.getCallStatuses(callerKey).get(calleeKey));
    assertEquals(
        Set.of("\"seed\""),
        Set.copyOf(
            context.getCallArgumentLiterals(callerKey).getOrDefault(ctorKey, java.util.List.of())));
    assertEquals(
        Set.of("\"id-1\"", "\"Order is being processed\""),
        Set.copyOf(
            context.getCallArgumentLiterals(callerKey).getOrDefault(sendKey, java.util.List.of())));
  }
}
