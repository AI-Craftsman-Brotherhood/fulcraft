package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

class DependencyGraphBuilderTest {

  @TempDir Path tempDir;

  @Test
  void collectsMethodAndConstructorCallsAndIncomingCounts() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Caller.java",
            """
            package com.example;

            public class Caller {
                void caller() {
                    callee();
                    callee();
                    new Helper();
                }

                void callee() {}
            }

            class Helper {
                Helper() {}
            }
            """);

    CtType<?> callerType = SpoonTestUtils.getType(launcher, "com.example.Caller");
    CtMethod<?> caller = callerType.getMethodsByName("caller").get(0);
    CtMethod<?> callee = callerType.getMethodsByName("callee").get(0);
    CtType<?> helperType = SpoonTestUtils.getType(launcher, "com.example.Helper");
    CtConstructor<?> helperCtor =
        ((spoon.reflect.declaration.CtClass<?>) helperType).getConstructors().iterator().next();

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();
    String callerKey =
        DependencyGraphBuilder.methodKey(callerType.getQualifiedName(), caller.getSignature());

    builder.collectCalledMethods(caller, callerKey, context);

    Set<String> calls = context.getCallGraph().get(callerKey);
    assertNotNull(calls);

    String calleeKey =
        DependencyGraphBuilder.methodKey(callerType.getQualifiedName(), callee.getSignature());
    String ctorKey =
        DependencyGraphBuilder.methodKey(helperType.getQualifiedName(), helperCtor.getSignature());

    assertTrue(calls.contains(calleeKey));
    assertTrue(calls.contains(ctorKey));
    assertEquals(2, context.getIncomingCounts().get(calleeKey));
    assertEquals(1, context.getIncomingCounts().get(ctorKey));
  }

  @Test
  void collectsMethodReferencesStatusesAndArgumentLiterals() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Caller.java",
            """
            package com.example;

            import java.util.function.Function;

            public class Caller {
                void caller() {
                    callee();
                    new Helper("seed");
                    take("duplicate", -1, +2);
                    take("duplicate", -1, +2);
                    Function<String, String> formatter = String::trim;
                }

                void callee() {}

                void take(Object first, Object second, Object third) {}
            }

            class Helper {
                Helper(String seed) {}
            }
            """);

    CtType<?> callerType = SpoonTestUtils.getType(launcher, "com.example.Caller");
    CtMethod<?> caller = callerType.getMethodsByName("caller").get(0);

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();
    String callerKey =
        DependencyGraphBuilder.methodKey(callerType.getQualifiedName(), caller.getSignature());

    builder.collectCalledMethods(caller, callerKey, context);

    Set<String> calls = context.getCallGraph().get(callerKey);
    assertNotNull(calls);
    String trimKey =
        calls.stream().filter(call -> call.contains("#trim(")).findFirst().orElseThrow();
    assertTrue(calls.stream().anyMatch(call -> call.contains("#take(")));

    Map<String, ResolutionStatus> statuses = context.getCallStatuses(callerKey);
    assertNotNull(statuses);
    assertEquals(ResolutionStatus.RESOLVED, statuses.get(trimKey));

    Map<String, List<String>> literalsByCall = context.getCallArgumentLiterals(callerKey);
    assertNotNull(literalsByCall);

    String takeKey =
        literalsByCall.keySet().stream()
            .filter(call -> call.contains("#take("))
            .findFirst()
            .orElseThrow();
    assertEquals(
        Set.of("\"duplicate\"", "-1", "+2"), Set.copyOf(literalsByCall.get(takeKey)));
    assertTrue(
        literalsByCall.values().stream().anyMatch(literals -> literals.contains("\"seed\"")));
  }

  @Test
  void ignoresBlankCurrentMethodKey() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
            package com.example;

            public class Sample {
                void caller() {
                    callee();
                }

                void callee() {}
            }
            """);
    CtMethod<?> caller =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("caller").get(0);

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();

    builder.collectCalledMethods(caller, " ", context);

    assertTrue(context.getCallGraph().isEmpty());
    assertTrue(context.getIncomingCounts().isEmpty());
  }

  @Test
  void methodKeyUsesUnknownWhenClassNameIsBlank() {
    assertEquals("unknown#run()", DependencyGraphBuilder.methodKey("  ", "run()"));
  }

  @Test
  void throwsWhenRequiredArgumentsAreNull() {
    DependencyGraphBuilder builder = new DependencyGraphBuilder();

    assertThrows(
        NullPointerException.class,
        () -> builder.collectCalledMethods(null, "com.example.Sample#a()", new AnalysisContext()));
    assertThrows(
        NullPointerException.class,
        () -> builder.collectCalledMethods(mockExecutable(), "com.example.Sample#a()", null));
  }

  @Test
  void usesFallbackSignaturesWhenReferencesAreMissing() {
    CtExecutable<Object> executable = mock(CtExecutable.class);
    CtInvocation<Object> invocation = mock(CtInvocation.class);
    CtConstructorCall<Object> constructorWithType = mock(CtConstructorCall.class);
    CtConstructorCall<Object> constructorWithoutType = mock(CtConstructorCall.class);
    CtTypeReference<Object> helperType = mock(CtTypeReference.class);
    CtExecutableReference<Object> ctorReference = mock(CtExecutableReference.class);

    when(invocation.getExecutable()).thenReturn(null);
    when(invocation.toString()).thenReturn("dynamicCall()");

    when(constructorWithType.getExecutable()).thenReturn(ctorReference);
    when(ctorReference.getSignature()).thenReturn("Helper()");
    when(constructorWithType.getType()).thenReturn(helperType);
    when(helperType.getQualifiedName()).thenReturn("com.example.Helper");

    when(constructorWithoutType.getExecutable()).thenReturn(null);
    when(constructorWithoutType.getType()).thenReturn(null);

    when(executable.getElements(any(TypeFilter.class)))
        .thenAnswer(
            invocationArg -> {
              TypeFilter<?> filter = invocationArg.getArgument(0);
              if (CtInvocation.class.equals(filter.getType())) {
                return new ArrayList<>(java.util.List.of(invocation));
              }
              if (CtConstructorCall.class.equals(filter.getType())) {
                return new ArrayList<>(
                    java.util.List.of(constructorWithType, constructorWithoutType));
              }
              return new ArrayList<>();
            });

    DependencyGraphBuilder builder = new DependencyGraphBuilder();
    AnalysisContext context = new AnalysisContext();
    String callerKey = "com.example.Caller#run()";

    builder.collectCalledMethods(executable, callerKey, context);

    Set<String> calls = context.getCallGraph().get(callerKey);
    assertNotNull(calls);
    assertTrue(calls.contains("unknown#dynamicCall()"));
    assertTrue(calls.contains("com.example.Helper#Helper()"));
    assertTrue(calls.contains("unknown#ctor()"));
  }

  private CtMethod<?> mockExecutable() {
    return mock(CtMethod.class);
  }
}
