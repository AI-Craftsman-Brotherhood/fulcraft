package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

class SpoonHelperTest {

  @TempDir Path tempDir;

  @Test
  void collectsTypesFromSameFile() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Target.java",
            """
            package com.example;

            public class Target {
                public Target(Service service, int count) {}
                public interface NestedApi {}
            }

            interface Api {}

            abstract class Base {
                Base(Dep dep) {}
            }

            enum Mode {
                A;

                Mode() {}

                Mode(Config config) {}
            }

            class Service {}

            class Dep {}

            class Config {}
            """);
    CtType<?> target = SpoonTestUtils.getType(launcher, "com.example.Target");
    CtType<?> mode = SpoonTestUtils.getType(launcher, "com.example.Mode");

    Set<String> interfaces = SpoonHelper.collectInterfaceNames(target);
    assertEquals(Set.of("Api", "NestedApi"), interfaces);

    Set<String> abstractClasses = SpoonHelper.collectAbstractClassNames(target);
    assertEquals(Set.of("Base"), abstractClasses);

    Set<String> targetParams = SpoonHelper.collectConstructorParamTypes(target);
    assertTrue(targetParams.contains("Service"));
    assertTrue(targetParams.contains("int"));

    Set<String> enumParams = SpoonHelper.collectConstructorParamTypes(mode);
    assertTrue(enumParams.contains("Config"));

    CtType<?> api = SpoonTestUtils.getType(launcher, "com.example.Api");
    assertEquals(Set.of(), SpoonHelper.collectConstructorParamTypes(api));
  }

  @Test
  void handlesTypeWithoutCompilationUnitPosition() {
    CtType<Object> detached = mock(CtType.class);
    when(detached.getPosition()).thenReturn(null);
    when(detached.getNestedTypes()).thenReturn(Set.of());

    assertEquals(Set.of(), SpoonHelper.collectInterfaceNames(detached));
    assertEquals(Set.of(), SpoonHelper.collectAbstractClassNames(detached));
  }

  @Test
  void collectConstructorParamTypesSkipsNullParamTypes() {
    CtClass<Object> type = mock(CtClass.class);
    CtConstructor<Object> constructor = mock(CtConstructor.class);
    CtParameter<Object> nullTypeParam = mock(CtParameter.class);
    CtParameter<Object> normalParam = mock(CtParameter.class);
    CtTypeReference<Object> paramType = mock(CtTypeReference.class);

    when(type.getConstructors()).thenReturn(Set.of(constructor));
    when(constructor.getParameters()).thenReturn(List.of(nullTypeParam, normalParam));
    when(nullTypeParam.getType()).thenReturn(null);
    doReturn(paramType).when(normalParam).getType();
    when(paramType.getSimpleName()).thenReturn("Dependency");

    assertEquals(Set.of("Dependency"), SpoonHelper.collectConstructorParamTypes(type));
  }

  @Test
  void collectsSameFileTypesEvenWhenDeclaredTypesContainNullOrDuplicates() {
    CtType<Object> target = mock(CtType.class);
    spoon.reflect.cu.SourcePosition position = mock(spoon.reflect.cu.SourcePosition.class);
    CompilationUnit compilationUnit = mock(CompilationUnit.class);
    CtType<Object> nested = mock(CtType.class);

    when(target.getPosition()).thenReturn(position);
    when(position.getCompilationUnit()).thenReturn(compilationUnit);
    when(compilationUnit.getDeclaredTypes()).thenReturn(java.util.Arrays.asList(null, target));
    when(target.getNestedTypes()).thenReturn(Set.of(target, nested));
    when(target.isInterface()).thenReturn(false);
    when(nested.getNestedTypes()).thenReturn(Set.of());
    when(nested.isInterface()).thenReturn(true);
    when(nested.getSimpleName()).thenReturn("NestedIface");

    assertEquals(Set.of("NestedIface"), SpoonHelper.collectInterfaceNames(target));
  }
}
